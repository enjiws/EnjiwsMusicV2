package com.enjiws.music.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.enjiws.music.MainActivity
import com.enjiws.music.data.Catalog
import com.enjiws.music.data.Http
import com.enjiws.music.data.Prefs
import com.enjiws.music.data.Store
import com.enjiws.music.data.StreamResolver
import com.enjiws.music.data.Track
import com.enjiws.music.data.UiBus
import com.enjiws.music.data.friendlyError
import com.enjiws.music.data.isNetworkError
import com.enjiws.music.data.isSameSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Pemutar hidup mandiri dari Activity. Seluruh logika antrean (lanjut otomatis, radio, retry,
 * simpan antrean, sleep timer) ada di sini, jadi tetap jalan saat aplikasi ditutup/layar mati.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var extending = false
    private var retriedId: String? = null
    private var failStreak = 0
    private var lastRecordedId: String? = null
    private val saveRunnable = Runnable { saveQueue() }

    // Mengubah enjiws://resolve?... jadi URL audio asli tepat saat dimuat (di thread loader).
    private val resolver = ResolvingDataSource.Resolver { spec: DataSpec ->
        if (spec.uri.scheme != RESOLVE_SCHEME) return@Resolver spec
        val r = try {
            StreamResolver.resolveBlocking(trackFromResolveUri(spec.uri))
        } catch (e: Exception) {
            throw IOException(friendlyError(e), e)
        }
        spec.withUri(Uri.parse(r.url))
    }

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.UA)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSource = DefaultDataSource.Factory(this, ResolvingDataSource.Factory(http, resolver))

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            // Mulai bunyi setelah 1 dtk audio terisi (bawaan 2,5 dtk).
            .setLoadControl(
                DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 60_000, 1_000, 2_000).build()
            )
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true // audio focus dikelola ExoPlayer
            )
            .setHandleAudioBecomingNoisy(true) // pause saat headset/Bluetooth lepas
            .setWakeMode(C.WAKE_MODE_NETWORK)  // jaga CPU + Wi-Fi saat streaming dengan layar mati
            .build()

        restoreQueue()
        player.addListener(playerListener)
        SleepTimer.attach(player)
        AudioFx.attach(player.audioSessionId)

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Sedang memutar -> lanjut di background. Sedang jeda -> tutup service.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            player.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(saveRunnable)
        saveQueue()
        scope.cancel()
        SleepTimer.attach(null)
        AudioFx.release()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- listener

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            retriedId = null
            maybeExtendQueue()
            prefetchNext()
            scheduleSave()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                failStreak = 0
                val item = player.currentMediaItem
                if (item != null && item.mediaId != lastRecordedId) {
                    lastRecordedId = item.mediaId
                    item.toTrack()?.let { Store.recordPlay(it) }
                }
                maybeExtendQueue()
                prefetchNext()
            }
            scheduleSave()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = scheduleSave()

        override fun onAudioSessionIdChanged(audioSessionId: Int) = AudioFx.attach(audioSessionId)

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                SleepTimer.onEndOfTrackReached()
            }
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    private fun handleError(error: PlaybackException) {
        val item = player.currentMediaItem
        val track = item?.toTrack()
        val network = isNetworkError(error)

        // URL stream kedaluwarsa / ditolak: resolve ulang sekali, lanjut dari posisi yang sama.
        if (item != null && track != null && !network && retriedId != item.mediaId) {
            retriedId = item.mediaId
            StreamResolver.invalidate(track)
            player.prepare()
            return
        }
        if (network) {
            UiBus.toast("Koneksi terputus. Ketuk putar untuk mencoba lagi.")
            return
        }
        failStreak++
        UiBus.toast("Tidak bisa memutar “${track?.title.orEmpty()}”: ${friendlyError(error.cause ?: error)}")
        if (failStreak < 5 && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    /** Resolve URL lagu berikutnya di latar belakang, jadi pindah lagu tidak menunggu lagi. */
    private fun prefetchNext() {
        val ni = player.nextMediaItemIndex
        if (ni == C.INDEX_UNSET) return
        val t = player.getMediaItemAt(ni).toTrack() ?: return
        if (t.isLocal || !t.canStream) return
        scope.launch(Dispatchers.IO) { runCatching { StreamResolver.resolve(t) } }
    }

    // ------------------------------------------------------------ radio otomatis

    /** Saat lagu terakhir antrean diputar, tambahkan lagu terkait supaya musik tidak berhenti. */
    private fun maybeExtendQueue() {
        if (!Prefs.autoplay || extending || !player.playWhenReady) return
        if (player.hasNextMediaItem() || player.repeatMode != Player.REPEAT_MODE_OFF) return
        val cur = player.currentMediaItem?.toTrack() ?: return
        if (cur.isLocal || cur.isPreview) return
        extending = true
        scope.launch {
            try {
                val existing = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toTrack() }
                val rel = Catalog.related(cur)
                    .filter { r -> r.canStream && existing.none { isSameSong(it, r) } }
                    .take(20)
                if (rel.isNotEmpty() && player.currentMediaItem?.mediaId == cur.id) {
                    player.addMediaItems(rel.map { it.toMediaItem() })
                }
            } catch (_: Exception) {
                // Rekomendasi gagal: antrean berhenti secara normal.
            } finally {
                extending = false
            }
        }
    }

    // ----------------------------------------------------------- simpan antrean

    private fun scheduleSave() {
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 2_000L)
    }

    private fun saveQueue() {
        if (!::player.isInitialized) return
        val tracks = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toTrack() }
        val arr = JSONArray().also { a -> tracks.forEach { a.put(it.toJson()) } }
        getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("items", arr.toString())
            .putInt("index", player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong("pos", player.currentPosition.coerceAtLeast(0))
            .apply()
    }

    private fun restoreQueue() {
        val sp = getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString("items", null) ?: return
        val tracks = runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> Track.fromJson(o) } }
        }.getOrDefault(emptyList())
        if (tracks.isEmpty()) return
        // Hanya dimuat, tidak di-prepare: tidak ada akses jaringan sampai pengguna menekan putar.
        player.setMediaItems(
            tracks.map { it.toMediaItem() },
            sp.getInt("index", 0).coerceIn(0, tracks.size - 1),
            sp.getLong("pos", 0L)
        )
    }

    private companion object {
        const val QUEUE_PREFS = "enjiws_queue_v2"
    }
}

/** Sleep timer: hitung mundur dengan fade-out 10 detik terakhir, atau berhenti di akhir lagu. */
object SleepTimer {
    val remainingMs = MutableStateFlow<Long?>(null)
    val untilEndOfTrack = MutableStateFlow(false)

    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var endAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            val left = endAt - SystemClock.elapsedRealtime()
            if (left <= 0) { fire(); return }
            remainingMs.value = left
            if (left < 10_000) player?.volume = (left / 10_000f).coerceIn(0f, 1f)
            handler.postDelayed(this, 500)
        }
    }

    fun attach(p: ExoPlayer?) {
        if (p == null) cancel()
        player = p
    }

    fun start(minutes: Int) {
        cancel()
        endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        remainingMs.value = minutes * 60_000L
        handler.post(tick)
    }

    fun startEndOfTrack() {
        cancel()
        player?.pauseAtEndOfMediaItems = true
        untilEndOfTrack.value = true
    }

    fun cancel() {
        handler.removeCallbacks(tick)
        remainingMs.value = null
        player?.volume = 1f
        if (untilEndOfTrack.value) {
            player?.pauseAtEndOfMediaItems = false
            untilEndOfTrack.value = false
        }
    }

    internal fun onEndOfTrackReached() {
        if (!untilEndOfTrack.value) return
        player?.pauseAtEndOfMediaItems = false
        untilEndOfTrack.value = false
    }

    private fun fire() {
        player?.pause()
        cancel()
    }
}

/** Equalizer sistem yang dipasang pada audio session ExoPlayer. */
object AudioFx {
    private var eq: Equalizer? = null
    val ready = MutableStateFlow(false)

    val bands: Int get() = eq?.numberOfBands?.toInt() ?: 0
    val minLevel: Int get() = eq?.bandLevelRange?.getOrNull(0)?.toInt() ?: -1500
    val maxLevel: Int get() = eq?.bandLevelRange?.getOrNull(1)?.toInt() ?: 1500

    fun attach(sessionId: Int) {
        release()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        AddonHub.attach(sessionId)
        runCatching {
            val e = Equalizer(0, sessionId)
            val saved = Prefs.eqLevels
            if (saved.size == e.numberOfBands.toInt()) {
                saved.forEachIndexed { i, l -> e.setBandLevel(i.toShort(), l.toShort()) }
            }
            e.enabled = Prefs.eqEnabled
            eq = e
            ready.value = true
        }
    }

    fun release() {
        AddonHub.releaseAll()
        runCatching { eq?.release() }
        eq = null
        ready.value = false
    }

    fun freqLabel(band: Int): String {
        val hz = (eq?.getCenterFreq(band.toShort()) ?: 0) / 1000
        return if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
    }

    fun level(band: Int): Int = eq?.getBandLevel(band.toShort())?.toInt() ?: 0

    fun setLevel(band: Int, level: Int) {
        runCatching { eq?.setBandLevel(band.toShort(), level.toShort()) }
    }

    fun commitLevels() = Prefs.updateEqLevels((0 until bands).map { level(it) })

    fun presets(): List<String> {
        val e = eq ?: return emptyList()
        return (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
    }

    fun usePreset(index: Int) {
        runCatching { eq?.usePreset(index.toShort()) }
        commitLevels()
    }

    fun setEnabled(on: Boolean) {
        runCatching { eq?.enabled = on }
        Prefs.updateEqEnabled(on)
    }
}

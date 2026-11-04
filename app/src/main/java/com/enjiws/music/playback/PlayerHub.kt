package com.enjiws.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.enjiws.music.data.Track
import com.enjiws.music.data.UiBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Gambaran status pemutar yang siap dipakai UI. */
data class NowState(
    val connected: Boolean = false,
    val track: Track? = null,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val durationMs: Long = 0L
)

/**
 * Jembatan UI <-> PlaybackService. UI hanya memanggil fungsi di sini dan membaca [state];
 * tidak ada lagi polling status tiap 250-500 ms, semua didorong oleh listener pemutar.
 */
object PlayerHub {
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private val trackCache = HashMap<String, Track>()

    private val _state = MutableStateFlow(NowState())
    val state: StateFlow<NowState> = _state

    fun connect(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val app = context.applicationContext
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) {
                UiBus.toast("Pemutar gagal terhubung")
                return@addListener
            }
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh(c)
            })
            refresh(c)
            pending.toList().forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending += block
    }

    private fun trackAt(c: MediaController, i: Int): Track? {
        val item = c.getMediaItemAt(i)
        return trackCache.getOrPut(item.mediaId) { item.toTrack() ?: return null }
    }

    private fun refresh(c: MediaController) {
        val n = c.mediaItemCount
        val idx = c.currentMediaItemIndex
        val queue = (0 until n).mapNotNull { trackAt(c, it) }
        _state.value = NowState(
            connected = true,
            track = if (n > 0 && idx in 0 until n) trackAt(c, idx) else null,
            queue = queue,
            index = if (n > 0) idx else -1,
            isPlaying = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            speed = c.playbackParameters.speed,
            durationMs = c.duration.coerceAtLeast(0L)
        )
    }

    /** Posisi saat ini (ms). Dibaca langsung dari pemutar, hanya oleh UI yang sedang tampil. */
    fun position(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    // ------------------------------------------------------------- memutar

    /** Putar [tracks] mulai dari [startIndex]. Daftar panjang dipotong jadi jendela supaya IPC ringan. */
    fun play(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean? = null) {
        if (tracks.isEmpty()) return
        val start = startIndex.coerceIn(0, tracks.size - 1)
        val from = (start - 50).coerceAtLeast(0)
        val to = (start + 250).coerceAtMost(tracks.size)
        val window = tracks.subList(from, to)
        withController { c ->
            if (shuffle != null) c.shuffleModeEnabled = shuffle
            c.setMediaItems(window.map { it.toMediaItem() }, start - from, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        play(tracks, tracks.indices.random(), shuffle = true)
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun skipTo(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) = withController { it.playbackParameters = PlaybackParameters(speed) }

    // -------------------------------------------------------------- antrean

    fun playNext(track: Track) {
        val c = controller
        if (c == null || c.mediaItemCount == 0) { play(listOf(track)); return }
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
        UiBus.toast("Diputar berikutnya")
    }

    fun addToQueue(track: Track) {
        val c = controller
        if (c == null || c.mediaItemCount == 0) { play(listOf(track)); return }
        c.addMediaItem(track.toMediaItem())
        UiBus.toast("Ditambahkan ke antrean")
    }

    fun removeAt(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount && index != c.currentMediaItemIndex) c.removeMediaItem(index)
    }

    /** Pindahkan lagu di antrean dari posisi [from] ke [to]. */
    fun move(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) c.moveMediaItem(from, to)
    }

    fun clearUpcoming() = withController { c ->
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
    }
}

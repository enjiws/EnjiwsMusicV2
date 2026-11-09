package com.enjiws.music.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.enjiws.music.data.Prefs

/**
 * Satu-satunya pengatur volume pemutar: ReplayGain (dari tag file) x fade sleep timer.
 * ReplayGain hanya meredam (tidak menguatkan) supaya tidak clipping.
 */
@OptIn(UnstableApi::class)
object PlayerVolume {
    private var player: ExoPlayer? = null
    private var replay = 1f
    private var trackGain: Double? = null
    private var albumGain: Double? = null

    /** Fade dari sleep timer (1 = normal). */
    var fade = 1f

    /** Gain ReplayGain yang sedang dipakai (dB), untuk ditampilkan di Signal Path. */
    var activeGainDb by mutableStateOf<Double?>(null); private set

    fun attach(p: ExoPlayer?) {
        player = p
        fade = 1f
        apply()
    }

    fun apply() {
        player?.volume = (replay * fade).coerceIn(0f, 1f)
    }

    /** Baca tag ReplayGain (Vorbis/FLAC, ID3 TXXX, MP4) dari lagu yang sedang diputar. */
    fun onTracks(tracks: Tracks) {
        var t: Double? = null
        var a: Double? = null
        for (g in tracks.groups) {
            if (g.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSelected(i)) continue
                val md = g.getTrackFormat(i).metadata ?: continue
                for (j in 0 until md.length()) {
                    val pair: Pair<String, String> = when (val e = md.get(j)) {
                        is VorbisComment -> e.key to e.value
                        is TextInformationFrame -> (if (e.id == "TXXX") e.description.orEmpty() else "") to e.values.firstOrNull().orEmpty()
                        is InternalFrame -> e.description.orEmpty() to e.text.orEmpty()
                        else -> continue
                    }
                    when (pair.first.uppercase()) {
                        "REPLAYGAIN_TRACK_GAIN" -> t = parse(pair.second) ?: t
                        "REPLAYGAIN_ALBUM_GAIN" -> a = parse(pair.second) ?: a
                    }
                }
            }
        }
        trackGain = t
        albumGain = a
        recompute()
    }

    /** Hitung ulang (dipanggil juga saat mode ReplayGain diganti di Pengaturan). */
    fun recompute() {
        val g = when (Prefs.replayGain) {
            1 -> trackGain ?: albumGain
            2 -> albumGain ?: trackGain
            else -> null
        }
        activeGainDb = g
        replay = if (g == null) 1f else Math.pow(10.0, g / 20.0).toFloat().coerceAtMost(1f)
        apply()
    }

    private fun parse(s: String): Double? = Regex("""-?\d+(\.\d+)?""").find(s)?.value?.toDoubleOrNull()
}

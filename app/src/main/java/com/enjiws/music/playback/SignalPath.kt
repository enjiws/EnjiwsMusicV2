package com.enjiws.music.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.enjiws.music.data.AudioInfo
import com.enjiws.music.data.Prefs
import com.enjiws.music.data.Track
import com.enjiws.music.data.khz

data class PathStep(val label: String, val title: String, val detail: String = "", val note: String = "")

/** Menyusun jalur sinyal dari sumber sampai output, memakai data asli (bukan tebakan) sejauh bisa dibaca. */
object SignalPath {

    fun steps(context: Context, track: Track, info: AudioInfo?, speed: Float): List<PathStep> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val out = mutableListOf<PathStep>()

        val source = when {
            track.isLocal -> "File di perangkat"
            track.subId.isNotBlank() -> "Server pribadi (Subsonic)"
            track.soundcloudUrl.isNotBlank() -> "SoundCloud (stream)"
            track.isPreview -> "Pratinjau Apple Music"
            track.youtubeUrl.isNotBlank() || track.pipedId.isNotBlank() -> "YouTube (stream)"
            else -> track.source.ifBlank { "Online" }
        }
        val sourceNote = when {
            info == null -> ""
            info.hiRes -> "Hi-Res Lossless"
            info.lossless -> "Lossless"
            else -> "Lossy: sebagian detail sudah dibuang dari sumbernya"
        }
        val bitNote = if (info != null && info.lossless && info.bitDepth == 0) " Bit depth tidak terbaca dari file." else ""
        out += PathStep("SUMBER", source, info?.detail ?: "Membaca info…", sourceNote + bitNote)

        out += PathStep("DECODER", "Media3 ExoPlayer", if (Prefs.floatOutput) "Decoder bawaan perangkat, output 32-bit float" else "Decoder bawaan perangkat, hasilnya PCM")

        val dsp = buildList {
            if (Prefs.eqEnabled) add("Equalizer")
            PlayerVolume.activeGainDb?.let { add("ReplayGain ${"%.1f".format(it)} dB") }
            AddonHub.all.filter { it.enabled }.forEach { add(it.title) }
            if (speed != 1f) add("Kecepatan ${speed}x")
        }
        out += PathStep(
            "PEMROSESAN",
            if (dsp.isEmpty()) "Tidak ada, jalur bersih" else dsp.joinToString(" · "),
            "",
            if (dsp.isEmpty()) "" else "Efek aktif mengubah sinyal, jadi bukan bit-perfect."
        )

        val nativeRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
        val srcRate = info?.sampleRateHz ?: 0
        val mixerNote = when {
            nativeRate == 0 -> ""
            srcRate == 0 -> "Sample rate sumber belum diketahui."
            srcRate != nativeRate ->
                "Di-resample ${khz(srcRate)} → ${khz(nativeRate)} kHz oleh Android (kecuali mode bit-perfect USB di Android 14+)."
            else -> "Sample rate sama, tidak di-resample."
        }
        out += PathStep("MIXER ANDROID", if (nativeRate > 0) "${khz(nativeRate)} kHz" else "Tidak diketahui", "", mixerNote + (if (Prefs.bitPerfect) " Mode bit-perfect USB diminta (eksperimental)." else ""))

        val (device, deviceNote) = outputDevice(am)
        out += PathStep("OUTPUT", device, "", deviceNote)
        return out
    }

    /** Perangkat output yang tersambung (perkiraan; Android tidak memberi tahu rute persisnya ke aplikasi). */
    private fun outputDevice(am: AudioManager): Pair<String, String> {
        val devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        fun find(vararg types: Int): AudioDeviceInfo? = devs.firstOrNull { it.type in types }
        find(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)?.let {
            return "USB: ${it.productName}" to "Jalur digital USB, mendukung sample rate tinggi."
        }
        find(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)?.let {
            return "Bluetooth: ${it.productName}" to "Kualitas ditentukan codec Bluetooth (SBC/AAC/LDAC) yang tidak terbaca aplikasi."
        }
        find(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET)?.let {
            return "Headphone kabel" to "Dikonversi ke analog oleh DAC bawaan HP."
        }
        return "Speaker perangkat" to ""
    }
}

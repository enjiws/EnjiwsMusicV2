package com.enjiws.music.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.annotation.RequiresApi
import com.enjiws.music.data.khz

/** Mode bit-perfect ke DAC USB (Android 14+). Eksperimental: tidak bisa diuji di semua perangkat. */
object BitPerfect {
    fun apply(context: Context, on: Boolean): String {
        if (Build.VERSION.SDK_INT < 34) return "Butuh Android 14 atau lebih baru."
        return applyApi34(context, on)
    }

    @RequiresApi(34)
    private fun applyApi34(context: Context, on: Boolean): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val device = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            ?: return "DAC USB tidak terdeteksi. Sambungkan DAC lalu coba lagi."
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        return try {
            if (!on) {
                am.clearPreferredMixerAttributes(attrs, device)
                "Bit-perfect dimatikan."
            } else {
                val mixer = am.getSupportedMixerAttributes(device)
                    .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
                    .maxByOrNull { it.format.sampleRate }
                    ?: return "DAC ini tidak mendukung mode bit-perfect."
                if (am.setPreferredMixerAttributes(attrs, device, mixer)) {
                    "Bit-perfect diminta (${khz(mixer.format.sampleRate)} kHz). Berlaku bila format lagu sama dengan format DAC."
                } else {
                    "Sistem menolak mode bit-perfect."
                }
            }
        } catch (e: Exception) {
            "Gagal: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}

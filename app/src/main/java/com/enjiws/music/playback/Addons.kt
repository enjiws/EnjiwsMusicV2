package com.enjiws.music.playback

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.enjiws.music.data.Prefs

/**
 * Dasar semua addon efek audio. Menambah efek baru = satu objek kecil di bawah ini
 * lalu daftarkan di [AddonHub.all]. Pengaturannya tersimpan otomatis.
 */
abstract class AudioAddon(
    val id: String,
    val title: String,
    val description: String,
    val maxLevel: Int,
    defaultLevel: Int,
    val levelLabel: (Int) -> String
) {
    var enabled by mutableStateOf(Prefs.getBool("addon.$id.on", false)); private set
    var level by mutableIntStateOf(Prefs.getInt("addon.$id.level", defaultLevel)); private set
    /** False kalau perangkat menolak efek ini (diketahui saat pertama dipasang ke pemutar). */
    var supported by mutableStateOf(true); private set

    private var effect: AudioEffect? = null

    protected abstract fun create(sessionId: Int): AudioEffect
    protected abstract fun applyLevel(effect: AudioEffect, level: Int)

    fun attach(sessionId: Int) {
        release()
        if (sessionId <= 0) return
        try {
            val e = create(sessionId)
            applyLevel(e, level)
            e.enabled = enabled
            effect = e
            supported = true
        } catch (t: Throwable) {
            supported = false
        }
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
    }

    fun updateEnabled(on: Boolean) {
        enabled = on
        Prefs.putBool("addon.$id.on", on)
        runCatching { effect?.enabled = on }
    }

    fun updateLevel(v: Int) {
        level = v
        runCatching { effect?.let { applyLevel(it, v) } }
    }

    fun commitLevel() = Prefs.putInt("addon.$id.level", level)
}

object BassBoostAddon : AudioAddon("bass", "Bass Boost", "Perkuat nada rendah.", 1000, 500, { "${it / 10}%" }) {
    override fun create(sessionId: Int): AudioEffect = BassBoost(0, sessionId)
    override fun applyLevel(effect: AudioEffect, level: Int) { (effect as BassBoost).setStrength(level.toShort()) }
}

object VirtualizerAddon : AudioAddon("virtualizer", "Virtualizer", "Efek suara surround yang lebih lebar (terasa paling jelas di earphone).", 1000, 500, { "${it / 10}%" }) {
    override fun create(sessionId: Int): AudioEffect = Virtualizer(0, sessionId)
    override fun applyLevel(effect: AudioEffect, level: Int) { (effect as Virtualizer).setStrength(level.toShort()) }
}

object LoudnessAddon : AudioAddon("loudness", "Loudness Boost", "Naikkan volume keseluruhan. Hati-hati: terlalu tinggi bisa membuat suara pecah.", 1500, 600, { "+${it / 100} dB" }) {
    override fun create(sessionId: Int): AudioEffect = LoudnessEnhancer(sessionId)
    override fun applyLevel(effect: AudioEffect, level: Int) { (effect as LoudnessEnhancer).setTargetGain(level) }
}

object AddonHub {
    val all: List<AudioAddon> = listOf(BassBoostAddon, VirtualizerAddon, LoudnessAddon)
    fun attach(sessionId: Int) = all.forEach { it.attach(sessionId) }
    fun releaseAll() = all.forEach { it.release() }
}

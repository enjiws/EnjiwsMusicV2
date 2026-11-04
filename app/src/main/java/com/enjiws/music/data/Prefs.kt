package com.enjiws.music.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Pengaturan aplikasi. State-nya Compose, jadi UI langsung ikut berubah. */
object Prefs {
    private lateinit var sp: SharedPreferences

    /** 0 = ikuti sistem, 1 = gelap, 2 = terang */
    var themeMode by mutableIntStateOf(1); private set
    var dynamicColor by mutableStateOf(false); private set
    var autoplay by mutableStateOf(true); private set
    var dataSaver by mutableStateOf(false); private set
    var sources by mutableStateOf(setOf(Source.YouTube, Source.SoundCloud, Source.Audius)); private set
    var eqEnabled by mutableStateOf(false); private set
    var eqLevels by mutableStateOf<List<Int>>(emptyList()); private set

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.getSharedPreferences("enjiws_settings", Context.MODE_PRIVATE)
        themeMode = sp.getInt("theme", 1)
        dynamicColor = sp.getBoolean("dynamic", false)
        autoplay = sp.getBoolean("autoplay", true)
        dataSaver = sp.getBoolean("datasaver", false)
        eqEnabled = sp.getBoolean("eq_on", false)
        eqLevels = sp.getString("eq_levels", "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }
        sp.getString("sources", null)?.let { raw ->
            val parsed = raw.split(",").mapNotNull { n -> Source.values().firstOrNull { it.name == n } }.toSet()
            if (parsed.isNotEmpty()) sources = parsed
        }
    }

    fun setThemeMode(v: Int) { themeMode = v; sp.edit().putInt("theme", v).apply() }
    fun setDynamicColor(v: Boolean) { dynamicColor = v; sp.edit().putBoolean("dynamic", v).apply() }
    fun setAutoplay(v: Boolean) { autoplay = v; sp.edit().putBoolean("autoplay", v).apply() }
    fun setDataSaver(v: Boolean) { dataSaver = v; sp.edit().putBoolean("datasaver", v).apply() }
    fun setEqEnabled(v: Boolean) { eqEnabled = v; sp.edit().putBoolean("eq_on", v).apply() }
    fun setEqLevels(v: List<Int>) { eqLevels = v; sp.edit().putString("eq_levels", v.joinToString(",")).apply() }

    fun toggleSource(s: Source) {
        val next = if (s in sources) sources - s else sources + s
        if (next.isEmpty()) return // minimal satu sumber harus aktif
        sources = next
        sp.edit().putString("sources", next.joinToString(",") { it.name }).apply()
    }
}

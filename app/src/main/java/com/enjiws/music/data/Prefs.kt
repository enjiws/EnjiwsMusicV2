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
    var sources by mutableStateOf(setOf(Source.YouTube)); private set
    var eqEnabled by mutableStateOf(false); private set
    var eqLevels by mutableStateOf<List<Int>>(emptyList()); private set
    /** API key Claude milik pengguna untuk Mood AI. Disimpan hanya di HP ini. */
    var apiKey by mutableStateOf(""); private set
    /** Server musik pribadi (Subsonic/Navidrome). Disimpan hanya di HP ini. */
    var serverUrl by mutableStateOf(""); private set
    var serverUser by mutableStateOf(""); private set
    var serverPass by mutableStateOf(""); private set
    /** Audio engine: ReplayGain 0 = mati, 1 = lagu, 2 = album. */
    var replayGain by mutableIntStateOf(0); private set
    var floatOutput by mutableStateOf(false); private set
    var bitPerfect by mutableStateOf(false); private set

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.getSharedPreferences("enjiws_settings", Context.MODE_PRIVATE)
        themeMode = sp.getInt("theme", 1)
        dynamicColor = sp.getBoolean("dynamic", false)
        autoplay = sp.getBoolean("autoplay", true)
        dataSaver = sp.getBoolean("datasaver", false)
        eqEnabled = sp.getBoolean("eq_on", false)
        apiKey = sp.getString("api_key", "").orEmpty()
        serverUrl = sp.getString("srv_url", "").orEmpty()
        serverUser = sp.getString("srv_user", "").orEmpty()
        serverPass = sp.getString("srv_pass", "").orEmpty()
        replayGain = sp.getInt("replaygain", 0)
        floatOutput = sp.getBoolean("float_out", false)
        bitPerfect = sp.getBoolean("bit_perfect", false)
        eqLevels = sp.getString("eq_levels", "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }
    }

    fun updateThemeMode(v: Int) { themeMode = v; sp.edit().putInt("theme", v).apply() }
    fun updateDynamicColor(v: Boolean) { dynamicColor = v; sp.edit().putBoolean("dynamic", v).apply() }
    fun updateAutoplay(v: Boolean) { autoplay = v; sp.edit().putBoolean("autoplay", v).apply() }
    fun updateDataSaver(v: Boolean) { dataSaver = v; sp.edit().putBoolean("datasaver", v).apply() }
    fun updateEqEnabled(v: Boolean) { eqEnabled = v; sp.edit().putBoolean("eq_on", v).apply() }
    fun updateEqLevels(v: List<Int>) { eqLevels = v; sp.edit().putString("eq_levels", v.joinToString(",")).apply() }

    // Penyimpanan generik untuk addon.
    fun getBool(key: String, def: Boolean = false): Boolean = sp.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) { sp.edit().putBoolean(key, v).apply() }
    fun getInt(key: String, def: Int = 0): Int = sp.getInt(key, def)
    fun putInt(key: String, v: Int) { sp.edit().putInt(key, v).apply() }

    fun updateServerUrl(v: String) { serverUrl = v.trim(); sp.edit().putString("srv_url", serverUrl).apply() }
    fun updateServerUser(v: String) { serverUser = v.trim(); sp.edit().putString("srv_user", serverUser).apply() }
    fun updateServerPass(v: String) { serverPass = v; sp.edit().putString("srv_pass", serverPass).apply() }

    fun updateReplayGain(v: Int) { replayGain = v; sp.edit().putInt("replaygain", v).apply() }
    fun updateFloatOutput(v: Boolean) { floatOutput = v; sp.edit().putBoolean("float_out", v).apply() }
    fun updateBitPerfect(v: Boolean) { bitPerfect = v; sp.edit().putBoolean("bit_perfect", v).apply() }

    fun updateApiKey(v: String) { apiKey = v.trim(); sp.edit().putString("api_key", apiKey).apply() }

    fun toggleSource(s: Source) {
        val next = if (s in sources) sources - s else sources + s
        if (next.isEmpty()) return // minimal satu sumber harus aktif
        sources = next
        sp.edit().putString("sources", next.joinToString(",") { it.name }).apply()
    }
}

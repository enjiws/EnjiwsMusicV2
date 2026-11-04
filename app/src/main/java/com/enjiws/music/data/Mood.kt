package com.enjiws.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class MoodResult(
    val mood: String,
    val emoji: String,
    val reply: String,
    val queries: List<String>,
    val viaAi: Boolean
)

/** Menebak mood dari teks bebas. Pakai Claude kalau ada API key, kalau tidak (atau gagal) pakai kata kunci lokal. */
object MoodAi {
    // Ganti kalau mau model lain.
    private const val MODEL = "claude-haiku-5-5"

    private const val SYSTEM = "Kamu pengenal mood untuk aplikasi musik. Dari teks pengguna (bahasa apa pun), " +
        "tebak mood-nya dan sarankan pencarian lagu. Balas HANYA JSON valid tanpa teks lain, dengan bentuk: " +
        "{\"mood\":\"satu atau dua kata Indonesia\",\"emoji\":\"satu emoji\"," +
        "\"reply\":\"satu kalimat empatik bahasa Indonesia, maksimal 20 kata\"," +
        "\"queries\":[\"tiga kueri pencarian lagu YouTube yang cocok dan spesifik (genre, judul, atau artis)\"]}"

    suspend fun analyze(text: String): MoodResult = withContext(Dispatchers.IO) {
        val key = Prefs.apiKey.trim()
        if (key.isNotBlank()) {
            try {
                return@withContext viaClaude(text, key)
            } catch (e: Exception) {
                UiBus.toast("AI gagal (${friendlyError(e)}), memakai deteksi lokal")
            }
        }
        local(text)
    }

    private fun viaClaude(text: String, key: String): MoodResult {
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 400)
            .put("system", SYSTEM)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text.take(500))))
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        Http.client.newCall(req).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                throw IOException(
                    when (r.code) {
                        401 -> "API key tidak valid"
                        429 -> "batas pemakaian API tercapai"
                        else -> "HTTP ${r.code}"
                    }
                )
            }
            val reply = JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text")
            val json = JSONObject(reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1))
            val qs = json.optJSONArray("queries")
            val queries = (0 until (qs?.length() ?: 0)).map { qs!!.getString(it) }.filter { it.isNotBlank() }
            if (queries.isEmpty()) throw IOException("jawaban AI tidak berisi kueri")
            return MoodResult(
                mood = json.optString("mood").ifBlank { "Campur aduk" },
                emoji = json.optString("emoji").ifBlank { "🎵" },
                reply = json.optString("reply"),
                queries = queries,
                viaAi = true
            )
        }
    }

    private class Rule(val mood: String, val emoji: String, val reply: String, val words: List<String>, val queries: List<String>)

    private val rules = listOf(
        Rule("Galau", "😢", "Nggak apa-apa merasa begini. Ini lagu yang bisa menemani.",
            listOf("sedih", "galau", "patah hati", "nangis", "kecewa", "kangen", "putus", "sad", "broken", "lonely", "sendiri"),
            listOf("lagu galau indonesia", "sad songs playlist", "lagu sedih patah hati")),
        Rule("Emosi", "😤", "Keluarkan saja lewat musik yang keras.",
            listOf("marah", "kesal", "emosi", "benci", "angry", "mad", "sebel"),
            listOf("lagu rock keras", "angry rock songs", "metal workout")),
        Rule("Semangat", "💪", "Gas! Ini yang bikin tenaga naik.",
            listOf("semangat", "olahraga", "gym", "lari", "workout", "energi", "motivasi", "pumped", "bersemangat"),
            listOf("lagu semangat olahraga", "workout motivation songs", "upbeat pop hits")),
        Rule("Fokus", "🎧", "Waktunya masuk mode fokus.",
            listOf("fokus", "belajar", "kerja", "ngoding", "konsentrasi", "study", "tugas", "skripsi", "lembur"),
            listOf("lofi study beats", "instrumental fokus belajar", "deep focus music")),
        Rule("Santai", "😌", "Tarik napas dulu, ini yang pas untuk rileks.",
            listOf("santai", "tenang", "rileks", "chill", "ngantuk", "tidur", "capek", "lelah", "tired", "relax", "penat"),
            listOf("lagu santai chill", "acoustic chill indonesia", "lofi chill beats")),
        Rule("Romantis", "💕", "Lagi hangat di hati ya.",
            listOf("cinta", "jatuh cinta", "romantis", "sayang", "love", "baper", "gebetan", "pacar"),
            listOf("lagu romantis indonesia", "love songs playlist", "lagu cinta terbaik")),
        Rule("Bahagia", "😄", "Senang ikut merasakannya!",
            listOf("senang", "bahagia", "happy", "gembira", "seru", "asik", "good", "ceria", "bersyukur"),
            listOf("lagu ceria bahagia", "happy upbeat songs", "feel good hits"))
    )

    private fun local(text: String): MoodResult {
        val t = text.lowercase()
        val rule = rules.firstOrNull { r -> r.words.any { t.contains(it) } }
        return if (rule != null) MoodResult(rule.mood, rule.emoji, rule.reply, rule.queries, viaAi = false)
        else MoodResult("Netral", "🎵", "Belum kebaca moodnya, ini lagu yang lagi enak didengar.",
            listOf("lagu hits terbaru indonesia", "top hits playlist"), viaAi = false)
    }
}

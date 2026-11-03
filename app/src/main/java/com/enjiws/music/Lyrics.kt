package com.enjiws.music

data class LyricLine(
    val timeMs: Long,
    val text: String
)

object LyricsParser {
    fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\](.*)")

        lrcContent.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msStr = match.groupValues[3]
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                val time = (min * 60 + sec) * 1000 + ms
                val text = match.groupValues[4].trim()
                lines.add(LyricLine(time, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}

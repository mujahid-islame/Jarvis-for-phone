package com.jarvis.gensoftlab.util

import java.util.regex.Pattern

object DurationParser {
    private const val DEFAULT_DURATION_MS = 180000L // 3 minutes
    private const val MAX_DURATION_MS = 600000L     // 10 minutes

    /**
     * Parses duration from user transcript.
     * Handles English and Bengali numerals/units.
     */
    fun parse(text: String): Long {
        if (text.isBlank()) return DEFAULT_DURATION_MS

        val cleanText = convertBengaliDigitsToEnglish(text.lowercase())
        
        // Match patterns like "2 minute", "3 মিনিট", "5 min", "120 sec", "৩ মিনিট"
        val pattern = Pattern.compile("(\\d+)\\s*(minute|min|মিনিট|second|sec|সেকেন্ড)")
        val matcher = pattern.matcher(cleanText)

        if (matcher.find()) {
            val value = matcher.group(1)?.toLong() ?: 0L
            val unit = matcher.group(2) ?: ""

            val durationMs = when {
                unit.contains("minute") || unit.contains("min") || unit.contains("মিনিট") -> {
                    value * 60 * 1000L
                }
                unit.contains("second") || unit.contains("sec") || unit.contains("সেকেন্ড") -> {
                    value * 1000L
                }
                else -> DEFAULT_DURATION_MS
            }
            return durationMs.coerceIn(30000L, MAX_DURATION_MS)
        }

        return DEFAULT_DURATION_MS
    }

    private fun convertBengaliDigitsToEnglish(input: String): String {
        val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val englishDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        var output = input
        for (i in bengaliDigits.indices) {
            output = output.replace(bengaliDigits[i], englishDigits[i])
        }
        return output
    }
}

package com.example.scoring

import java.util.Locale

/**
 * Normalizes player names by stripping extra internal/external whitespace,
 * converting to lowercase, and performing fuzzy matching/distance checks.
 */
object PlayerNameNormalizer {

    @JvmStatic
    fun normalize(name: String?): String {
        if (name.isNullOrBlank()) return ""
        return name.trim().replace("\\s+".toRegex(), " ").lowercase(Locale.ROOT)
    }

    @JvmStatic
    fun canonicalNameKey(name: String?): String {
        if (name.isNullOrBlank()) return ""
        return name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    }

    @JvmStatic
    fun canonicalJerseyKey(jersey: String?): String {
        if (jersey.isNullOrBlank()) return "0"
        val trimmed = jersey.trim().replace("^0+".toRegex(), "")
        return if (trimmed.isEmpty()) "0" else trimmed
    }

    @JvmStatic
    fun areExactPlayerMatches(name1: String?, jersey1: String?, name2: String?, jersey2: String?): Boolean {
        val nameMatch = canonicalNameKey(name1) == canonicalNameKey(name2)
        val jerseyMatch = canonicalJerseyKey(jersey1) == canonicalJerseyKey(jersey2)
        return nameMatch && jerseyMatch
    }

    @JvmStatic
    fun areNamesMatching(name1: String?, name2: String?): Boolean {
        return canonicalNameKey(name1) == canonicalNameKey(name2)
    }

    @JvmStatic
    fun isFuzzyMatch(name1: String?, name2: String?): Boolean {
        val n1 = normalize(name1)
        val n2 = normalize(name2)
        if (n1 == n2) return true
        if (n1.isEmpty() || n2.isEmpty()) return false

        val dist = levenshteinDistance(n1, n2)
        val maxLength = maxOf(n1.length, n2.length)
        return when {
            maxLength <= 4 -> dist == 0
            maxLength <= 8 -> dist <= 1
            else -> dist <= 2
        }
    }

    @JvmStatic
    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}

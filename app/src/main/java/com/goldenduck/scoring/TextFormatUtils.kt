package com.goldenduck.scoring

object TextFormatUtils {

    /**
     * Safely unescapes network JSON line breaks and fixes missing spaces between words and numbers
     * WITHOUT corrupting Markdown syntax (tables, bold tags, bullet lists).
     */
    fun cleanHumanReadableText(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""

        // 1. Remove leading "null" or "nullnull" artifacts
        val text = rawText.replace(Regex("""^(?:null\s*)+""", RegexOption.IGNORE_CASE), "").trim()

        // 2. Unescape escaped literal newlines and tabs from JSON/stream responses
        var clean = text.replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\r\n", "\n")

        // 3. Fix missing space between word and number (e.g. "across31" -> "across 31", "every5.4" -> "every 5.4")
        clean = clean.replace(Regex("""([a-zA-Z]{2,})([0-9]+(?:\.[0-9]+)?)"""), "$1 $2")
        clean = clean.replace(Regex("""([0-9]+(?:\.[0-9]+)?)([a-zA-Z]{2,})"""), "$1 $2")

        // 4. Fix missing space after parenthesis before word/number
        clean = clean.replace(Regex("""(?<=\))(?=[A-Za-z0-9])"""), " ")

        // 5. Fix missing space after colon (even with bold tags) before word/number (e.g. "**Runs:**67" -> "**Runs:** 67")
        clean = clean.replace(Regex("""(:\**)(?=[A-Za-z0-9\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF])"""), "$1 ")

        // 6. Unstick sentences missing newline after a period (e.g. "matches.Sourav" -> "matches.\n\nSourav")
        clean = clean.replace(Regex("""([a-z0-9?!:])\.([A-Z])"""), "$1.\n\n$2")

        // 7. Repair glued bullet points / hyphens following sentences (e.g. "sixes.- Strike-rate" -> "sixes.\n\n• Strike-rate")
        clean = clean.replace(Regex("""([a-zA-Z0-9?!%])\s*\.\s*-\s*"""), "$1.\n\n• ")

        // 8. Separate Quick Take / Key Takeaway section headings onto their own paragraph
        clean = clean.replace(Regex("""(?<=\S)\s*(Quick Take|Key Takeaway|Takeaway|Analysis)\s*:\s*""", RegexOption.IGNORE_CASE), "\n\n$1: ")

        // 9. Fix missing spaces between emojis and subsequent text (e.g. "🚀You" -> "🚀 You")
        clean = clean.replace(Regex("""([\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF])([A-Za-z0-9])"""), "$1 $2")

        // 10. Repair collapsed Markdown table rows that lack line breaks (e.g. "| Stat | Value | |---|---|")
        clean = clean.replace(Regex("""(?<=\|)[ \t]*(?=\|)"""), "\n")

        return clean.trim()
    }
}

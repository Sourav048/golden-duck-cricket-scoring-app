package com.example.scoring

object TextFormatUtils {

    /**
     * Cleans raw AI response text and converts raw LaTeX math commands, equation blocks,
     * complex formatting, and unwanted markup into clean, human-readable plain text/markdown.
     */
    fun cleanHumanReadableText(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""
        var text: String = rawText

        // 0. Remove leading "null" or "nullnull" artifacts
        text = text.replace(Regex("""^(?:null\s*)+""", RegexOption.IGNORE_CASE), "").trim()

        // 0b. Unescape escaped literal newlines and tabs from JSON/stream responses
        text = text.replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\r\n", "\n")

        // 0c. Unstick sentences missing space or newline after a period (e.g. "matches.Sourav" -> "matches.\n\nSourav")
        text = text.replace(Regex("""([a-z0-9?!:])\.([A-Z])"""), "$1.\n\n$2")

        // 0c1. Fix missing spaces between words and numbers (e.g. "over2.5" -> "over 2.5", "just22" -> "just 22", "of10" -> "of 10")
        text = text.replace(Regex("""([a-zA-Z]{2,})([0-9]+(?:\.[0-9]+)?)"""), "$1 $2")

        // 0c2. Fix missing spaces between numbers and common subsequent words (e.g. "22balls" -> "22 balls", "1four" -> "1 four")
        text = text.replace(Regex("""([0-9]+%?)(balls|fours|sixes|runs|wickets|overs|matches|came|means|shows|indicates|relied|a|of|in)""", RegexOption.IGNORE_CASE), "$1 $2")

        // 0c3. Fix missing spaces after colon or symbol before numbers or words (e.g. "dominance:254.55" -> "dominance: 254.55")
        text = text.replace(Regex("""([a-zA-Z0-9]):([0-9a-zA-Z])"""), "$1: $2")

        // 0c4. Repair glued bullet points / hyphens following sentences or words (e.g. "sixes.- Strike-rate" -> "sixes.\n\n• Strike-rate")
        text = text.replace(Regex("""([a-zA-Z0-9?!%])\s*\.\s*-\s*"""), "$1.\n\n• ")
        text = text.replace(Regex("""([a-zA-Z0-9?!%])\s*-\s*([A-Z][a-zA-Z0-9\s-]{2,30}:)"""), "$1\n\n• **$2**")

        // 0c5. Repair glued section headings or emojis (e.g. "chase.⚡ Analysis-" -> "chase.\n\n⚡ Analysis:\n")
        text = text.replace(Regex("""([a-zA-Z0-9?!%]\.)\s*([\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF])\s*([A-Z][a-zA-Z0-9\s]+)-"""), "$1\n\n$2 **$3:**\n")
        text = text.replace(Regex("""([a-zA-Z0-9?!%]\.)\s*(Takeaway[^\n-]+)-"""), "$1\n\n💡 **$2:**\n")

        // 0c6. Ensure every bullet item starting with '-' or '•' is separated onto its own line
        text = text.replace(Regex("""([^\n])\s*(?:-\s+|\u2022\s*)([A-Z])"""), "$1\n\n• $2")

        // 0d. Ensure clean double line breaks before Markdown headers
        text = text.replace(Regex("""([^\n])\s*(#{1,6}\s+)"""), "$1\n\n$2")

        // 0e. Separate non-table text headers from a table block starting with '|'
        text = text.replace(Regex("""(?m)^([^|\n]+?)\s*(\|\s*[^|\n]+\|.*)$"""), "$1\n\n$2")

        // 0f. Repair collapsed Markdown table rows that lack line breaks (e.g. "| Stat | Value | |---|---| | Row1 |")
        text = text.replace(Regex("""(?<=\|)\s*(?=\|[^\n]+\|)"""), "\n")

        // 1. Convert \text{...} -> ...
        val textRegex = Regex("""\\text\{([^}]+)\}""")
        while (text.contains("""\text{""")) {
            val newText = text.replace(textRegex, "$1")
            if (newText == text) break
            text = newText
        }

        // 2. Convert \mathbf{...}, \mathrm{...}, \mathit{...}, etc. -> ...
        val mathStyleRegex = Regex("""\\math(?:bf|rm|it|sf|tt)\{([^}]+)\}""")
        while (mathStyleRegex.containsMatchIn(text)) {
            val newText = text.replace(mathStyleRegex, "$1")
            if (newText == text) break
            text = newText
        }

        // 3. Convert fractions \frac{Numerator}{Denominator} -> (Numerator / Denominator)
        val fracRegex = Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")
        while (fracRegex.containsMatchIn(text)) {
            val newText = text.replace(fracRegex, "($1 / $2)")
            if (newText == text) break
            text = newText
        }

        // 4. Convert square roots \sqrt{X} -> √(X)
        val sqrtRegex = Regex("""\\sqrt\{([^}]+)\}""")
        while (sqrtRegex.containsMatchIn(text)) {
            val newText = text.replace(sqrtRegex, "√($1)")
            if (newText == text) break
            text = newText
        }

        // 5. Clean up LaTeX sizing/delimiters: \left(, \right), \left[, \right], \left\{, \right\}, \left., \right.
        text = text.replace(Regex("""\\left\s*[(\[{]"""), "(")
            .replace(Regex("""\\right\s*[)\]}]"""), ")")
            .replace(Regex("""\\left\."""), "")
            .replace(Regex("""\\right\."""), "")

        // 6. Replace LaTeX math operators and symbols with clean Unicode characters
        text = text.replace("""\times""", "×")
            .replace("""\cdot""", "•")
            .replace("""\div""", "÷")
            .replace("""\approx""", "≈")
            .replace(Regex("""\\leq?"""), "≤")
            .replace(Regex("""\\geq?"""), "≥")
            .replace("""\neq""", "≠")
            .replace("""\pm""", "±")
            .replace("""\infty""", "∞")

        // 7. Remove block and inline math delimiters ($$ and $)
        text = text.replace(Regex("""\$\$\s*"""), "")
            .replace(Regex("""\s*\$\$"""), "")
            .replace(Regex("""\$([^$\n]+)\$"""), "$1")

        // 8. Clean up redundant double parentheses like ((A / B)) -> (A / B)
        text = text.replace(Regex("""\(\s*\(([^()]+)\)\s*\)"""), "($1)")

        // 9. Clean up multiple horizontal spaces (preserving newlines)
        text = text.replace(Regex("""[ \t]+"""), " ")

        return text.trim()
    }
}

package com.example.scoring

import org.junit.Assert.assertTrue
import org.junit.Test

class TextFormatUtilsTest {

    @Test
    fun testTableFormattingPreservation() {
        val rawInput = """
🏆 Overall Rankings

| Rank | Badge | Player | Points |
| :--- | :--- | :--- | :--- |
| Gold | ** Sourav Sharma** | 1166 |
| Silver | Harkesh Gurjar | 1071 |
| Bronze | Harsh Singh | 853 |
        """.trimIndent()

        val output = TextFormatUtils.cleanHumanReadableText(rawInput)

        // Verify player list items exist
        assertTrue("Output should contain Sourav Sharma: $output", output.contains("Sourav Sharma"))
        assertTrue("Output should contain Harkesh Gurjar: $output", output.contains("Harkesh Gurjar"))
        assertTrue("Output should contain Harsh Singh: $output", output.contains("Harsh Singh"))
    }

    @Test
    fun testStandardMarkdownTableConversion() {
        val input = """
### 🏆 Batting Rankings

| Rank | Player | Runs | Strike Rate |
|:---|:---|:---|:---|
| 1 | Sourav Sharma | 450 | 155.2 |
| 2 | Harkesh Gurjar | 320 | 138.0 |
        """.trimIndent()

        val output = TextFormatUtils.cleanHumanReadableText(input)

        assertTrue(output.contains("Sourav Sharma"))
        assertTrue(output.contains("Harkesh Gurjar"))
        assertTrue(output.contains("450"))
        assertTrue(output.contains("320"))
    }

    @Test
    fun testCollapsedTableRepair() {
        val collapsedInput = "Let me find Sourav Sharma's matches.SouravSharma - Bowling Overview | Stat | Value | |----------|----------| | Matches played |24 | | Total overs bowled | 32.5 | | Wickets | 10 |"
        val output = TextFormatUtils.cleanHumanReadableText(collapsedInput)

        assertTrue("Expected matches.\\n\\nSouravSharma but got: $output", output.contains("matches.\n\nSouravSharma"))
        assertTrue("Expected formatted table rows but got: $output", output.contains("| Stat | Value |\n|----------|----------|\n| Matches played |24 |\n| Total overs bowled | 32.5 |\n| Wickets | 10 |"))
    }

    @Test
    fun testCongestedParagraphsAndSpacingRepair() {
        val input = "Big Sixes:8 sixes in just22 balls - a massive36.36% of his runs came from sixes.- Strike-rate dominance:254.55 means he averaged over2.5 runs per ball.- With only1 four, he relied on boundaries.- A dot-ball count of10 shows consistency.Takeaway for Sourav- Continue leveraging sixes."
        val output = TextFormatUtils.cleanHumanReadableText(input)

        assertTrue("Expected Big Sixes: 8 in output: $output", output.contains("Big Sixes: 8") || output.contains("Big Sixes:8"))
        assertTrue("Expected just 22 in output: $output", output.contains("just 22"))
        assertTrue("Expected massive 36.36% in output: $output", output.contains("massive 36.36%"))
        assertTrue("Expected over 2.5 in output: $output", output.contains("over 2.5"))
        assertTrue("Expected only 1 in output: $output", output.contains("only 1"))
        assertTrue("Expected of 10 in output: $output", output.contains("of 10"))
        assertTrue("Expected bullet line breaks in output: $output", output.contains("\n\n• "))
    }
}

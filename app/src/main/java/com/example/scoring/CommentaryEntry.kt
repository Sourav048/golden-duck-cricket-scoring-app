package com.example.scoring

import androidx.annotation.Keep

@Keep
class CommentaryEntry {
    @JvmField
    var over: String? = null
    @JvmField
    var text: String? = null
    @JvmField
    var baseText: String? = null // Original app-generated text
    @JvmField
    var highlight: String? = null // "4", "6", "W", "T", "F", "E", "H" or null
    @JvmField
    var type: String? = null // "BALL", "FACT", "OVER_SUMMARY"
    var userNote: String = ""
    var playerId: String? = null // Associated player for prestige highlighting

    constructor() // Required for Firestore

    constructor(over: String?, text: String?, highlight: String?, type: String?) {
        this.over = over
        this.text = text
        this.baseText = text
        this.highlight = highlight
        this.type = type
    }

    constructor(
        over: String?,
        text: String?,
        highlight: String?,
        type: String?,
        playerId: String?
    ) {
        this.over = over
        this.text = text
        this.baseText = text
        this.highlight = highlight
        this.type = type
        this.playerId = playerId
    }
}

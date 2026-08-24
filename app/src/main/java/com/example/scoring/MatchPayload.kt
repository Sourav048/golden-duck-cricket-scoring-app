package com.example.scoring

import androidx.annotation.Keep

/**
 * Container for heavy match data used during Cloud Sync.
 * Kept as a separate class for robust GSON serialization.
 */
@Keep
class MatchPayload {
    var b1: List<Ball?>? = null
    var b2: List<Ball?>? = null
    var c1: List<CommentaryEntry?>? = null
    var c2: List<CommentaryEntry?>? = null
    var cj: List<CommentaryEntry?>? = null
    var p1: List<PartnershipEvent?>? = null
    var p2: List<PartnershipEvent?>? = null
    var f1: List<FowEvent?>? = null
    var f2: List<FowEvent?>? = null
    
    // Phase 5.1: Include Player Match Stats in the bundle
    var stats: List<PlayerMatchStatEntity?>? = null
}

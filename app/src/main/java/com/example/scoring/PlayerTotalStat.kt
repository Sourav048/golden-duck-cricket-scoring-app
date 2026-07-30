package com.example.scoring

/**
 * Lightweight projection returned by aggregate queries in StatsDao.
 * Room maps the query columns directly onto these public fields.
 */
class PlayerTotalStat {
    @JvmField
    var playerId: String? = null
    @JvmField
    var playerName: String? = null
    @JvmField
    var photoUri: String? = null // Added for leaderboard photos
    @JvmField
    var total: Int = 0
}

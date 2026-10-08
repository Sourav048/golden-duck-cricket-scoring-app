package com.example.scoring

data class PlayerLeagueStats(
    val playerName: String,
    var totalRuns: Int = 0,
    var totalBalls: Int = 0,
    var fours: Int = 0,
    var sixes: Int = 0,
    var wicketsTaken: Int = 0,
    var runsConceded: Int = 0,
    var ballsBowled: Int = 0,
    var highestScore: Int = 0,
    var bestWickets: Int = 0,
    var bestRunsConceded: Int = 999,
    var matchesPlayed: Int = 0,
    var fifties: Int = 0,
    var thirties: Int = 0
)

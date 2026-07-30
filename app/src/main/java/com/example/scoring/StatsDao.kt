package com.example.scoring

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStat(stat: PlayerMatchStatEntity)

    @Query("SELECT * FROM player_match_stats WHERE playerId = :playerId ORDER BY matchId ASC")
    fun getStatsByPlayer(playerId: String?): List<PlayerMatchStatEntity?>?

    @Query("SELECT * FROM player_match_stats WHERE matchId = :matchId")
    fun getStatsByMatch(matchId: String?): List<PlayerMatchStatEntity?>?

    @Query("DELETE FROM player_match_stats WHERE matchId = :mId")
    fun deleteStatsByMatch(mId: String?)

    @Query("SELECT * FROM player_match_stats")
    fun getAllStats(): List<PlayerMatchStatEntity?>?

    @Query("DELETE FROM player_match_stats")
    fun deleteAllStats()

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.runsScored) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostRuns(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, CAST((SUM(s.runsScored) * 10000.0 / SUM(s.ballsFaced)) AS INTEGER) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName HAVING SUM(s.ballsFaced) > 0 ORDER BY total DESC LIMIT 10")
    fun getBestStrikeRate(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.sixes) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostSixes(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.fours) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostFours(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.runsScored >= 80 AND s.runsScored < 100 GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostEighties(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, MAX(s.runsScored) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getHighestScores(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.runsScored >= 50 GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostFifties(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.runsScored >= 30 GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostThirties(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.runsScored = 0 AND s.isOut = 1 GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostDucks(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.wicketsTaken) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostWickets(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, (MAX(s.wicketsTaken * 1000 + (1000 - s.runsConceded))) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getBestBowlingInnings(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, CAST((SUM(s.runsConceded) * 10000.0 / SUM(s.wicketsTaken)) AS INTEGER) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName HAVING SUM(s.wicketsTaken) > 0 ORDER BY total ASC LIMIT 10")
    fun getBestBowlingAverage(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, CAST((SUM(s.runsConceded) * 60000.0 / SUM(s.ballsBowled)) AS INTEGER) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName HAVING SUM(s.ballsBowled) > 0 ORDER BY total ASC LIMIT 10")
    fun getBestEconomy(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.hattricks) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostHattricks(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.wicketsTaken >= 5 GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostFiveWicketHauls(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.wicketsTaken >= 3 GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostThreeWicketHauls(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.wicketsTaken >= 2 GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostTwoWicketHauls(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.sixesConceded) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostSixesConceded(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, (SUM(s.runsScored) + SUM(s.sixes)*2 + SUM(s.fours) + SUM(s.wicketsTaken)*25 + SUM(s.maidens)*15 + SUM(s.dotBalls)*2) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getOverallRankings(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, (SUM(s.runsScored) + SUM(s.sixes)*2 + SUM(s.fours)) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getBattingRankings(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, (SUM(s.wicketsTaken)*25 + SUM(s.maidens)*15 + SUM(s.dotBalls)*2) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getBowlingRankings(): List<PlayerTotalStat?>?
    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.catches) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getMostCatches(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.stumpings) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getMostStumpings(): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.runOuts) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getMostRunOuts(): List<PlayerTotalStat?>?
}

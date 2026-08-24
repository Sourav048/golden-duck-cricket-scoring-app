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

    @Query("SELECT s.* FROM player_match_stats s JOIN players p ON s.playerId = p.id WHERE p.globalId = :globalId")
    fun getStatsByGlobalId(globalId: String): List<PlayerMatchStatEntity?>?

    @Query("SELECT * FROM player_match_stats WHERE matchId = :matchId")
    fun getStatsByMatch(matchId: String?): List<PlayerMatchStatEntity?>?

    @Query("DELETE FROM player_match_stats WHERE matchId = :mId")
    fun deleteStatsByMatch(mId: String?)

    @Query("SELECT * FROM player_match_stats")
    fun getAllStats(): List<PlayerMatchStatEntity?>?

    @Query("DELETE FROM player_match_stats")
    fun deleteAllStats()

    @Query("UPDATE player_match_stats SET playerName = :newName WHERE playerId = :pId")
    fun updatePlayerNameInStats(pId: String, newName: String)

    @Query("UPDATE player_match_stats SET gullyId = :newGId WHERE gullyId = 'local'")
    fun migrateLocalStats(newGId: String)

    @Query("UPDATE player_match_stats SET playerId = :newId WHERE playerId = :oldId")
    fun updatePlayerIdInStats(oldId: String, newId: String)

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.runsScored) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostRuns(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, CAST((SUM(s.runsScored) * 10000.0 / SUM(s.ballsFaced)) AS INTEGER) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName HAVING SUM(s.ballsFaced) > 0 ORDER BY total DESC LIMIT 10")
    fun getBestStrikeRate(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.sixes) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostSixes(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.fours) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostFours(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.runsScored >= 80 AND s.runsScored < 100 AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostEighties(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, MAX(s.runsScored) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getHighestScores(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.runsScored >= 50 AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostFifties(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.runsScored >= 30 AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostThirties(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.runsScored = 0 AND s.isOut = 1 AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostDucks(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.wicketsTaken) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostWickets(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, (MAX(s.wicketsTaken * 1000 + (1000 - s.runsConceded))) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getBestBowlingInnings(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, CAST((SUM(s.runsConceded) * 100.0 / SUM(s.wicketsTaken)) AS INTEGER) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName HAVING SUM(s.wicketsTaken) >= 3 ORDER BY total ASC LIMIT 10")
    fun getBestBowlingAverage(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, CAST((SUM(s.runsConceded) * 600.0 / SUM(s.ballsBowled)) AS INTEGER) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName HAVING SUM(s.ballsBowled) > 0 ORDER BY total ASC LIMIT 10")
    fun getBestEconomy(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.hattricks) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostHattricks(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.wicketsTaken >= 5 AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostFiveWicketHauls(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.wicketsTaken >= 3 AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostThreeWicketHauls(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.wicketsTaken >= 2 AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostTwoWicketHauls(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.sixesConceded) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName ORDER BY total DESC LIMIT 10")
    fun getMostSixesConceded(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, (SUM(s.runsScored) + SUM(s.sixes)*2 + SUM(s.fours) + SUM(s.wicketsTaken)*25 + SUM(s.maidens)*15 + SUM(s.dotBalls)*2 + SUM(s.catches)*8 + SUM(s.stumpings)*12 + SUM(s.runOuts)*12) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getOverallRankings(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, (SUM(s.runsScored) + SUM(s.sixes)*2 + SUM(s.fours)) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getBattingRankings(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, (SUM(s.wicketsTaken)*25 + SUM(s.maidens)*15 + SUM(s.dotBalls)*2) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getBowlingRankings(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.catches) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getMostCatches(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.stumpings) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getMostStumpings(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, s.playerName, p.photoUri, SUM(s.runOuts) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId, s.playerName HAVING total > 0 ORDER BY total DESC LIMIT 10")
    fun getMostRunOuts(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT SUM(runsScored) FROM player_match_stats s JOIN players p ON s.playerId = p.id WHERE p.globalId = :gId OR p.id = :gId")
    fun getGlobalTotalRuns(gId: String): Int?

    @Query("SELECT SUM(wicketsTaken) FROM player_match_stats s JOIN players p ON s.playerId = p.id WHERE p.globalId = :gId OR p.id = :gId")
    fun getGlobalTotalWickets(gId: String): Int?

    @Query("SELECT COUNT(*) FROM player_match_stats s JOIN players p ON s.playerId = p.id WHERE p.globalId = :gId OR p.id = :gId")
    fun getGlobalTotalMatches(gId: String): Int

    @Query("SELECT SUM(runsScored) FROM player_match_stats WHERE playerId = :pId")
    fun getTotalRuns(pId: String): Int?

    @Query("SELECT SUM(wicketsTaken) FROM player_match_stats WHERE playerId = :pId")
    fun getTotalWickets(pId: String): Int?

    @Query("SELECT COUNT(*) FROM player_match_stats WHERE playerId = :pId")
    fun getTotalMatches(pId: String): Int
}
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

    @Query("DELETE FROM player_match_stats WHERE gullyId = :gId")
    fun deleteAllStatsByGully(gId: String)

    @Query("UPDATE player_match_stats SET playerName = :newName WHERE playerId = :pId")
    fun updatePlayerNameInStats(pId: String, newName: String)

    @Query("UPDATE player_match_stats SET gullyId = :newGId WHERE gullyId = 'local'")
    fun migrateLocalStats(newGId: String)

    @Query("UPDATE player_match_stats SET playerId = :newId WHERE playerId = :oldId")
    fun updatePlayerIdInStats(oldId: String, newId: String)

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, SUM(s.runsScored) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostRuns(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, CAST((SUM(s.runsScored) * 10000.0 / SUM(s.ballsFaced)) AS INTEGER) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId HAVING SUM(s.ballsFaced) >= 30 ORDER BY total DESC")
    fun getBestStrikeRate(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, SUM(s.sixes) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostSixes(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, SUM(s.fours) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostFours(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.runsScored >= 80 AND s.runsScored < 100 AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostEighties(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, MAX(s.runsScored) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getHighestScores(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.runsScored >= 50 AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostFifties(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.runsScored >= 30 AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostThirties(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.runsScored = 0 AND s.isOut = 1 AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostDucks(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, SUM(s.wicketsTaken) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostWickets(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, (MAX(s.wicketsTaken * 100000 + (99999 - s.runsConceded))) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getBestBowlingInnings(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, CAST((SUM(s.runsConceded) * 100.0 / SUM(s.wicketsTaken)) AS INTEGER) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId HAVING SUM(s.wicketsTaken) >= 3 AND SUM(s.ballsBowled) >= 30 ORDER BY total ASC")
    fun getBestBowlingAverage(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, CAST((SUM(s.runsConceded) * 600.0 / SUM(s.ballsBowled)) AS INTEGER) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId HAVING SUM(s.ballsBowled) >= 30 ORDER BY total ASC")
    fun getBestEconomy(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, SUM(s.hattricks) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostHattricks(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.wicketsTaken >= 5 AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostFiveWicketHauls(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.wicketsTaken >= 3 AND s.wicketsTaken < 5 AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostThreeWicketHauls(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, COUNT(*) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.wicketsTaken = 2 AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostTwoWicketHauls(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, SUM(s.sixesConceded) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId ORDER BY total DESC")
    fun getMostSixesConceded(gId: String): List<PlayerTotalStat?>?

    @Query("UPDATE player_match_stats SET playerId = (SELECT p.id FROM players p WHERE LOWER(TRIM(p.name)) = LOWER(TRIM(player_match_stats.playerName)) LIMIT 1) WHERE (playerId IS NULL OR playerId = '' OR playerId NOT IN (SELECT id FROM players)) AND EXISTS (SELECT 1 FROM players p WHERE LOWER(TRIM(p.name)) = LOWER(TRIM(player_match_stats.playerName)))")
    fun fixOrphanMatchStats(): Int

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, (SUM(s.runsScored) + SUM(s.sixes)*4 + SUM(s.fours)*2 + SUM(CASE WHEN s.runsScored >= 100 THEN 50 WHEN s.runsScored >= 80 THEN 40 WHEN s.runsScored >= 50 THEN 25 WHEN s.runsScored >= 30 THEN 10 ELSE 0 END) + SUM(s.wicketsTaken)*25 + SUM(s.maidens)*15 + SUM(s.dotBalls)*2 + SUM(s.catches)*8 + SUM(s.stumpings)*12 + SUM(s.runOuts)*12) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId HAVING total > 0 ORDER BY total DESC")
    fun getOverallRankings(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, (SUM(s.runsScored) + SUM(s.sixes)*4 + SUM(s.fours)*2 + SUM(CASE WHEN s.runsScored >= 100 THEN 50 WHEN s.runsScored >= 80 THEN 40 WHEN s.runsScored >= 50 THEN 25 WHEN s.runsScored >= 30 THEN 10 ELSE 0 END)) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId HAVING total > 0 ORDER BY total DESC")
    fun getBattingRankings(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, (SUM(s.wicketsTaken)*25 + SUM(s.maidens)*15 + SUM(s.dotBalls)*2) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId HAVING total > 0 ORDER BY total DESC")
    fun getBowlingRankings(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, SUM(s.catches) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId HAVING total > 0 ORDER BY total DESC")
    fun getMostCatches(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, SUM(s.stumpings) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId HAVING total > 0 ORDER BY total DESC")
    fun getMostStumpings(gId: String): List<PlayerTotalStat?>?

    @Query("SELECT s.playerId, COALESCE(p.name, s.playerName) as playerName, p.photoUri as photoUri, SUM(s.runOuts) as total FROM player_match_stats s LEFT JOIN players p ON s.playerId = p.id WHERE s.playerName NOT IN ('FIELD', 'PENALTY', 'RETIRED') AND s.gullyId = :gId GROUP BY s.playerId HAVING total > 0 ORDER BY total DESC")
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

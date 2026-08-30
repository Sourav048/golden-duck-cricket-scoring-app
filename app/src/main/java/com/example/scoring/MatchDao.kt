package com.example.scoring

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMatch(match: MatchEntity): Long

    @Delete
    fun deleteMatch(match: MatchEntity)

    @Update
    fun updateMatch(match: MatchEntity)

    @Query("SELECT * FROM matches ORDER BY playedAt DESC")
    fun getAllMatches(): List<MatchEntity?>?

    @Query("SELECT * FROM matches WHERE gullyId = :gId ORDER BY playedAt DESC")
    fun getAllMatchesByGully(gId: String): List<MatchEntity?>?

    @Query("SELECT * FROM matches WHERE id = :id")
    fun getMatchById(id: String?): MatchEntity?

    @Query("SELECT * FROM matches WHERE id = :id")
    fun getMatchByIdLive(id: String): LiveData<MatchEntity?>

    @Query("SELECT * FROM matches WHERE isFinished = :finished AND isAbandoned = :abandoned ORDER BY playedAt DESC")
    fun getMatchesByStatus(finished: Boolean, abandoned: Boolean): List<MatchEntity?>?

    @Query("SELECT * FROM matches WHERE isFinished = :finished AND isAbandoned = :abandoned AND gullyId = :gId ORDER BY playedAt DESC")
    fun getMatchesByStatusByGully(finished: Boolean, abandoned: Boolean, gId: String): List<MatchEntity?>?

    @Query("SELECT * FROM matches WHERE isFinished = :finished AND isAbandoned = :abandoned AND gullyId = :gId ORDER BY playedAt DESC")
    fun getMatchesByStatusByGullyLive(finished: Boolean, abandoned: Boolean, gId: String): LiveData<List<MatchEntity?>?>

    @Query("SELECT * FROM matches WHERE isAbandoned = 1 ORDER BY playedAt DESC")
    fun getAbandonedMatches(): List<MatchEntity?>?

    @Query("SELECT * FROM matches WHERE isAbandoned = 1 AND gullyId = :gId ORDER BY playedAt DESC")
    fun getAbandonedMatchesByGully(gId: String): List<MatchEntity?>?

    @Query("SELECT * FROM matches WHERE isAbandoned = 1 AND gullyId = :gId ORDER BY playedAt DESC")
    fun getAbandonedMatchesByGullyLive(gId: String): LiveData<List<MatchEntity?>?>

    @Query("SELECT DISTINCT venue FROM matches WHERE (gullyId = :gId) AND venue IS NOT NULL AND venue != ''")
    fun getUniqueVenues(gId: String): List<String>?

    @Query("UPDATE matches SET gullyId = :newGId WHERE gullyId = 'local'")
    fun migrateLocalMatches(newGId: String)

    @Query("DELETE FROM matches")
    fun deleteAllMatches()
}

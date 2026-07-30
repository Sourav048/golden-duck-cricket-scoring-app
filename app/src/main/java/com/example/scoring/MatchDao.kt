package com.example.scoring

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

    @Query("SELECT * FROM matches WHERE id = :id")
    fun getMatchById(id: String?): MatchEntity?

    @Query("SELECT * FROM matches WHERE isFinished = :finished AND isAbandoned = :abandoned ORDER BY playedAt DESC")
    fun getMatchesByStatus(finished: Boolean, abandoned: Boolean): List<MatchEntity?>?

    @Query("SELECT * FROM matches WHERE isAbandoned = 1 ORDER BY playedAt DESC")
    fun getAbandonedMatches(): List<MatchEntity?>?

    @Query("DELETE FROM matches")
    fun deleteAllMatches()
}

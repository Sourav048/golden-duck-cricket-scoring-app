package com.example.scoring

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PlayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlayer(player: PlayerEntity): Long

    @Update
    fun updatePlayer(player: PlayerEntity)

    @Delete
    fun deletePlayer(player: PlayerEntity)

    @Query("SELECT * FROM players ORDER BY createdAt ASC")
    fun getAllPlayers(): List<PlayerEntity?>?

    @Query("SELECT * FROM players WHERE id = :id")
    fun getPlayerById(id: String?): PlayerEntity?

    @Query("SELECT * FROM players WHERE TRIM(name) = TRIM(:name) COLLATE NOCASE LIMIT 1")
    fun getPlayerByName(name: String?): PlayerEntity?

    @Query("SELECT * FROM players WHERE jerseyNumber = :jersey")
    fun getPlayersByJersey(jersey: String?): List<PlayerEntity?>?

    @Query("DELETE FROM players")
    fun deleteAllPlayers()
}

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

    @Query("SELECT * FROM players WHERE gullyId = :gId ORDER BY createdAt ASC")
    fun getAllPlayersByGully(gId: String): List<PlayerEntity?>?

    @Query("SELECT * FROM players WHERE id = :id")
    fun getPlayerById(id: String?): PlayerEntity?

    @Query("SELECT * FROM players WHERE TRIM(name) = TRIM(:name) COLLATE NOCASE LIMIT 1")
    fun getPlayerByName(name: String?): PlayerEntity?

    @Query("SELECT * FROM players WHERE TRIM(name) = TRIM(:name) AND gullyId = :gId COLLATE NOCASE LIMIT 1")
    fun getPlayerByNameByGully(name: String?, gId: String): PlayerEntity?

    @Query("SELECT * FROM players WHERE jerseyNumber = :jersey")
    fun getPlayersByJersey(jersey: String?): List<PlayerEntity?>?

    @Query("SELECT * FROM players WHERE jerseyNumber = :jersey AND gullyId = :gId")
    fun getPlayersByJerseyByGully(jersey: String?, gId: String): List<PlayerEntity?>?

    @Query("DELETE FROM players")
    fun deleteAllPlayers()

    @Query("SELECT * FROM players WHERE TRIM(name) = TRIM(:name) AND jerseyNumber = :jersey AND gullyId = :gId COLLATE NOCASE LIMIT 1")
    fun getPlayerByNameAndJersey(name: String?, jersey: String?, gId: String): PlayerEntity?

    @Query("SELECT * FROM players WHERE TRIM(name) = TRIM(:name) AND jerseyNumber = :jersey COLLATE NOCASE LIMIT 1")
    fun getPlayerByNameAndJerseyAnyGully(name: String?, jersey: String?): PlayerEntity?

    @Query("SELECT * FROM players WHERE globalId = :gId LIMIT 1")
    fun getPlayerByGlobalId(gId: String?): PlayerEntity?

    @Query("SELECT * FROM players WHERE globalId IS NOT NULL")
    fun getAllGlobalPlayers(): List<PlayerEntity>

    @Query("UPDATE players SET name = :newName WHERE id = :id")
    fun updatePlayerName(id: String, newName: String)

    @Query("UPDATE players SET gullyId = :newGId WHERE gullyId = 'local'")
    fun migrateLocalPlayers(newGId: String)
}

package com.example.scoring

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface DraftMatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDraft(draft: DraftMatchEntity): Long

    @Delete
    fun deleteDraft(draft: DraftMatchEntity)

    @Update
    fun updateDraft(draft: DraftMatchEntity)

    @Query("SELECT * FROM draft_matches WHERE gullyId = :gId")
    fun getAllDrafts(gId: String): List<DraftMatchEntity?>?

    @Query("SELECT * FROM draft_matches WHERE gullyId = :gId")
    fun getAllDraftsLive(gId: String): LiveData<List<DraftMatchEntity?>?>

    @Query("SELECT * FROM draft_matches WHERE id = :id")
    fun getDraftById(id: String?): DraftMatchEntity?

    @Query("DELETE FROM draft_matches WHERE gullyId = :gId")
    fun deleteAllDraftsByGully(gId: String)
}

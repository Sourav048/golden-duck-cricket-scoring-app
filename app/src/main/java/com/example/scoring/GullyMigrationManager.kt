package com.example.scoring

import android.content.Context
import android.util.Log
import com.example.scoring.AppDatabase.Companion.getInstance

/**
 * Handles the migration of records from "Local Mode" to a newly joined Gully.
 */
object GullyMigrationManager {
    private const val TAG = "MigrationManager"

    interface MigrationCallback {
        fun onSuccess(count: Int)
        fun onFailure(error: String)
    }

    /**
     * Finds all "local" records and re-tags them with the target Gully ID.
     * Then pushes them all to the Cloud.
     */
    fun migrateLocalToGully(context: Context, targetGId: String, callback: MigrationCallback) {
        if (targetGId == "local" || targetGId.isEmpty()) {
            callback.onFailure("Invalid target League ID")
            return
        }

        AppDatabase.ioExecutor.execute {
            try {
                val db = getInstance(context)
                
                val localPlayers = db.playerDao().getAllPlayersByGully("local") ?: emptyList()
                val localMatches = db.matchDao().getAllMatchesByGully("local") ?: emptyList()
                
                if (localPlayers.isEmpty() && localMatches.isEmpty()) {
                    callback.onSuccess(0)
                    return@execute
                }

                db.runInTransaction {
                    // 1. SMART PLAYER MERGE
                    localPlayers.filterNotNull().forEach { localP ->
                        val existingInGully = db.playerDao().getPlayerByNameAndJersey(localP.name, localP.jerseyNumber, targetGId)
                        
                        if (existingInGully != null) {
                            // MATCH FOUND: Re-wire all local stats and match maps to use the GULLY player's ID
                            val oldId = localP.id
                            val newId = existingInGully.id
                            
                            // A. Update individual match stat records
                            db.statsDao().updatePlayerIdInStats(oldId, newId)
                            
                            // B. Deep Payload Surgery (Updating the hidden cloud files)
                            val matchesToUpdate = db.matchDao().getAllMatchesByGully("local") ?: emptyList()
                            matchesToUpdate.filterNotNull().forEach { match ->
                                var matchNeedsSaving = false
                                
                                // 1. Update the visible Metadata Map
                                val updatedMap = HashMap(match.nameToIdMap ?: emptyMap())
                                match.nameToIdMap?.forEach { (name, id) ->
                                    if (id == oldId) {
                                        updatedMap[name] = newId
                                        matchNeedsSaving = true
                                    }
                                }
                                match.nameToIdMap = updatedMap

                                // 2. Update the Compressed Payload (The "Heavy" part)
                                match.compressedPayload?.let { payloadStr ->
                                    val payload = MatchCompressor.decompressMatchData(payloadStr, MatchPayload::class.java)
                                    if (payload != null) {
                                        var payloadChanged = false
                                        payload.stats?.forEach { s ->
                                            if (s?.playerId == oldId) {
                                                s.playerId = newId
                                                payloadChanged = true
                                            }
                                        }
                                        if (payloadChanged) {
                                            match.compressedPayload = MatchCompressor.compressMatchData(payload)
                                            matchNeedsSaving = true
                                        }
                                    }
                                }

                                if (matchNeedsSaving) {
                                    db.matchDao().insertMatch(match)
                                }
                            }
                            
                            // C. Delete the redundant local profile
                            db.playerDao().deletePlayer(localP)
                        } else {
                            // NO MATCH: Simply move the player to the new Gully
                            localP.gullyId = targetGId
                            db.playerDao().insertPlayer(localP)
                        }
                    }

                    // 2. MOVE REMAINING LOCAL DATA
                    db.matchDao().migrateLocalMatches(targetGId)
                    db.statsDao().migrateLocalStats(targetGId)
                }

                // 3. PUSH EVERYTHING TO CLOUD
                val migratedMatches = db.matchDao().getAllMatchesByGully(targetGId) ?: emptyList()
                migratedMatches.filterNotNull().forEach {
                    GullySyncManager.performSyncMatchToCloudInternal(targetGId, it)
                }
                
                // Final Player Sync
                db.playerDao().getAllPlayersByGully(targetGId)?.filterNotNull()?.forEach {
                    GullySyncManager.syncPlayerToCloud(targetGId, it)
                }

                RankingRegistry.refresh(context, null)
                callback.onSuccess(localMatches.size)

            } catch (e: Exception) {
                Log.e(TAG, "Migration failed: ${e.message}")
                callback.onFailure(e.message ?: "Unknown Error")
            }
        }
    }

    /**
     * Checks if there is any local data available to migrate.
     */
    fun hasLocalData(context: Context, onResult: (Boolean) -> Unit) {
        AppDatabase.ioExecutor.execute {
            val db = getInstance(context)
            val pCount = db.playerDao().getAllPlayersByGully("local")?.size ?: 0
            val mCount = db.matchDao().getAllMatchesByGully("local")?.size ?: 0
            onResult(pCount > 0 || mCount > 0)
        }
    }
}

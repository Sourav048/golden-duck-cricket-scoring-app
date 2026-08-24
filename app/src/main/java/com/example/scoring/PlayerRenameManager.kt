package com.example.scoring

import android.content.Context
import android.util.Log
import com.example.scoring.AppDatabase.Companion.getInstance

/**
 * The "Global Rename Engine".
 * Ensures that editing a player's name propagates through all past matches and the cloud.
 */
object PlayerRenameManager {
    private const val TAG = "RenameEngine"

    fun renamePlayer(context: Context, player: PlayerEntity, oldNameRaw: String, newName: String, onComplete: () -> Unit) {
        val oldNameTrimmed = oldNameRaw.trim()
        val playerId = player.id
        val gId = player.gullyId
        
        AppDatabase.ioExecutor.execute {
            try {
                val db = getInstance(context)
                
                // 1. Update Player Profile
                db.playerDao().updatePlayerName(playerId, newName)
                player.name = newName
                GullySyncManager.syncPlayerToCloud(gId, player)

                // 2. Update all PlayerMatchStat records (Match by ID, not name)
                db.statsDao().updatePlayerNameInStats(playerId, newName)

                // 3. Update all Matches this player participated in
                val playerStats = db.statsDao().getStatsByPlayer(playerId) ?: emptyList()
                val matchIds = playerStats.mapNotNull { it?.matchId }.distinct()

                for (mId in matchIds) {
                    val match = db.matchDao().getMatchById(mId) ?: continue
                    
                    // A. Team Names & Innings Teams
                    if (match.teamAName?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.teamAName = newName
                    if (match.teamBName?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.teamBName = newName
                    if (match.firstInningsTeam?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.firstInningsTeam = newName
                    if (match.secondInningsTeam?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.secondInningsTeam = newName

                    // B. Squad Lists
                    match.teamANames = match.teamANames?.map { if (it?.trim().equals(oldNameTrimmed, ignoreCase = true)) newName else it }
                    match.teamBNames = match.teamBNames?.map { if (it?.trim().equals(oldNameTrimmed, ignoreCase = true)) newName else it }
                    
                    // C. Mappings
                    val newPhotoMap = HashMap<String?, String?>()
                    match.photoMap?.forEach { (k, v) -> 
                        val newKey = if (k?.trim().equals(oldNameTrimmed, ignoreCase = true)) newName else k
                        newPhotoMap[newKey] = v 
                    }
                    match.photoMap = newPhotoMap

                    val newIdMap = HashMap<String?, String?>()
                    match.nameToIdMap?.forEach { (k, v) -> 
                        val newKey = if (k?.trim().equals(oldNameTrimmed, ignoreCase = true)) newName else k
                        newIdMap[newKey] = v 
                    }
                    match.nameToIdMap = newIdMap

                    // D. Resumption State
                    if (match.currentStrikerName?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.currentStrikerName = newName
                    if (match.currentNonStrikerName?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.currentNonStrikerName = newName
                    if (match.currentBowlerName?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.currentBowlerName = newName
                    if (match.playerOfTheMatchName?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.playerOfTheMatchName = newName

                    // E. JSON Blobs (The regex-based fuzzy swap)
                    renameInJson(match, oldNameTrimmed, newName)

                    // 4. Save and Sync Match
                    db.matchDao().insertMatch(match)
                    GullySyncManager.performSyncMatchToCloudInternal(gId, match)
                }

                Log.d(TAG, "Renamed '$oldNameTrimmed' to '$newName' in ${matchIds.size} matches.")
                onComplete()

            } catch (e: Exception) {
                Log.e(TAG, "Rename failed: ${e.message}")
            }
        }
    }

    private fun renameInJson(match: MatchEntity, oldName: String, newName: String) {
        val pattern = "(?i)\\b" + Regex.escape(oldName) + "\\b"
        val regex = Regex(pattern)

        // 1. BALLS
        fun updateBalls(list: List<Ball?>?): List<Ball?>? {
            return list?.map { b ->
                if (b == null) return@map null
                if (b.batsmanName?.trim().equals(oldName, ignoreCase = true)) b.batsmanName = newName
                if (b.bowlerName?.trim().equals(oldName, ignoreCase = true)) b.bowlerName = newName
                if (b.nonStrikerName?.trim().equals(oldName, ignoreCase = true)) b.nonStrikerName = newName
                if (b.outPlayerName?.trim().equals(oldName, ignoreCase = true)) b.outPlayerName = newName
                if (b.fielderName != null) {
                    b.fielderName = b.fielderName?.replace(regex, newName)
                }
                b
            }
        }
        match.ballsJson1 = updateBalls(match.ballsJson1)
        match.ballsJson2 = updateBalls(match.ballsJson2)

        // 2. COMMENTARY
        fun updateComm(list: List<CommentaryEntry?>?): List<CommentaryEntry?>? {
            return list?.map { c ->
                if (c == null) return@map null
                c.text = c.text?.replace(regex, newName)
                c.baseText = c.baseText?.replace(regex, newName)
                c
            }
        }
        match.commentaryJson1 = updateComm(match.commentaryJson1)
        match.commentaryJson2 = updateComm(match.commentaryJson2)
        match.commentaryJson = updateComm(match.commentaryJson)

        // 3. FALL OF WICKETS
        fun updateFow(list: List<FowEvent?>?): List<FowEvent?>? {
            return list?.map { f ->
                if (f == null) return@map null
                if (f.playerName?.trim().equals(oldName, ignoreCase = true)) f.playerName = newName
                f
            }
        }
        match.fowJson1 = updateFow(match.fowJson1)
        match.fowJson2 = updateFow(match.fowJson2)

        // 4. PARTNERSHIPS
        fun updatePship(list: List<PartnershipEvent?>?): List<PartnershipEvent?>? {
            return list?.map { p ->
                if (p == null) return@map null
                if (p.batter1?.trim().equals(oldName, ignoreCase = true)) p.batter1 = newName
                if (p.batter2?.trim().equals(oldName, ignoreCase = true)) p.batter2 = newName
                p
            }
        }
        match.pshipJson1 = updatePship(match.pshipJson1)
        match.pshipJson2 = updatePship(match.pshipJson2)
    }
}

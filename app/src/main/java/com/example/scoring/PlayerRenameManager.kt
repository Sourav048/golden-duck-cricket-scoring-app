package com.example.scoring

import android.content.Context
import android.util.Log
import com.example.scoring.AppDatabase.Companion.getInstance

/**
 * The "Global Rename Engine".
 * Ensures that editing a player's name propagates through all past matches and the cloud,
 * including fielder/keeper names in dismissal strings.
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

                // 2. Update player_match_stats table for this playerId
                db.statsDao().updatePlayerNameInStats(playerId, newName)

                // 3. Find ALL matches in database that might involve this player or oldName
                val allMatches = db.matchDao().getAllMatches()?.filterNotNull() ?: emptyList()
                val targetMatches = allMatches.filter { match ->
                    (match.nameToIdMap?.containsValue(playerId) == true) ||
                    (match.nameToIdMap?.containsKey(oldNameTrimmed) == true) ||
                    (match.teamANames?.any { it?.trim().equals(oldNameTrimmed, ignoreCase = true) } == true) ||
                    (match.teamBNames?.any { it?.trim().equals(oldNameTrimmed, ignoreCase = true) } == true) ||
                    containsNameInMatch(match, oldNameTrimmed)
                }

                val pattern = "(?i)\\b" + Regex.escape(oldNameTrimmed) + "\\b"
                val regex = Regex(pattern)

                for (match in targetMatches) {
                    val mId = match.id

                    // A. Team Names & Innings Teams
                    if (match.teamAName?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.teamAName = newName
                    if (match.teamBName?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.teamBName = newName
                    if (match.firstInningsTeam?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.firstInningsTeam = newName
                    if (match.secondInningsTeam?.trim().equals(oldNameTrimmed, ignoreCase = true)) match.secondInningsTeam = newName

                    // B. Squad Lists
                    match.teamANames = match.teamANames?.map { if (it?.trim().equals(oldNameTrimmed, ignoreCase = true)) newName else it }?.distinct()
                    match.teamBNames = match.teamBNames?.map { if (it?.trim().equals(oldNameTrimmed, ignoreCase = true)) newName else it }?.distinct()

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

                    // Update dismissalInfo inside player_match_stats for this match
                    val matchStats = db.statsDao().getStatsByMatch(mId) ?: emptyList()
                    for (stat in matchStats.filterNotNull()) {
                        var modified = false
                        if (stat.playerName?.trim().equals(oldNameTrimmed, ignoreCase = true)) {
                            stat.playerName = newName
                            modified = true
                        }
                        if (stat.dismissalInfo?.contains(oldNameTrimmed, ignoreCase = true) == true) {
                            stat.dismissalInfo = stat.dismissalInfo?.replace(regex, newName)
                            modified = true
                        }
                        if (modified) {
                            db.statsDao().insertStat(stat)
                        }
                    }

                    GullySyncManager.performSyncMatchToCloudInternal(gId, match)
                }

                // Clean up any lingering historical dismissal strings across all matches
                sanitizeAllMatchDismissals(db)

                Log.d(TAG, "Renamed '$oldNameTrimmed' to '$newName' in ${targetMatches.size} matches.")
                onComplete()

            } catch (e: Exception) {
                Log.e(TAG, "Rename failed: ${e.message}")
            }
        }
    }

    private fun containsNameInMatch(match: MatchEntity, name: String): Boolean {
        fun ballsContain(list: List<Ball?>?): Boolean {
            return list?.any { b ->
                b != null && (
                    b.batsmanName?.trim().equals(name, ignoreCase = true) ||
                    b.bowlerName?.trim().equals(name, ignoreCase = true) ||
                    b.nonStrikerName?.trim().equals(name, ignoreCase = true) ||
                    b.outPlayerName?.trim().equals(name, ignoreCase = true) ||
                    b.fielderName?.contains(name, ignoreCase = true) == true ||
                    b.dismissalInfo?.contains(name, ignoreCase = true) == true
                )
            } == true
        }
        return ballsContain(match.ballsJson1) || ballsContain(match.ballsJson2)
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
                if (b.dismissalInfo != null) {
                    b.dismissalInfo = b.dismissalInfo?.replace(regex, newName)
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

    /**
     * Sanitizes dismissalInfo strings across all matches in Room DB to ensure that if a player
     * was renamed in the past or via cloud sync, any lingering old name in dismissal descriptions
     * is updated to match the player's current profile name / fielderName.
     */
    fun sanitizeAllMatchDismissals(db: AppDatabase) {
        val allPlayers = db.playerDao().getAllPlayers() ?: emptyList()
        val playerMap = allPlayers.filterNotNull().associateBy { it.id }

        val allMatches = db.matchDao().getAllMatches()?.filterNotNull() ?: emptyList()
        for (match in allMatches) {
            var matchChanged = false
            val nameToId = match.nameToIdMap ?: emptyMap()

            fun sanitizeBalls(balls: List<Ball?>?): List<Ball?>? {
                return balls?.map { b ->
                    if (b == null || !b.isWicket || b.dismissalInfo.isNullOrEmpty()) return@map b

                    var dInfo = b.dismissalInfo!!
                    var ballChanged = false

                    // If fielderName is present, ensure dismissalInfo uses b.fielderName
                    if (!b.fielderName.isNullOrBlank()) {
                        val fielderName = b.fielderName!!.trim()

                        // Handle "c <oldFielder> b <bowler>"
                        if (dInfo.startsWith("c ", ignoreCase = true) && !dInfo.startsWith("c & b", ignoreCase = true)) {
                            val currentFielderInDesc = dInfo.substringAfter("c ").substringBefore(" b ").trim()
                            if (currentFielderInDesc.isNotEmpty() && !currentFielderInDesc.equals(fielderName, ignoreCase = true)) {
                                val pattern = "(?i)\\b" + Regex.escape(currentFielderInDesc) + "\\b"
                                dInfo = dInfo.replace(Regex(pattern), fielderName)
                                ballChanged = true
                            }
                        }
                        // Handle "st <oldKeeper> b <bowler>"
                        else if (dInfo.startsWith("st ", ignoreCase = true)) {
                            val currentKeeperInDesc = dInfo.substringAfter("st ").substringBefore(" b ").trim()
                            if (currentKeeperInDesc.isNotEmpty() && !currentKeeperInDesc.equals(fielderName, ignoreCase = true)) {
                                val pattern = "(?i)\\b" + Regex.escape(currentKeeperInDesc) + "\\b"
                                dInfo = dInfo.replace(Regex(pattern), fielderName)
                                ballChanged = true
                            }
                        }
                        // Handle "Run Out (<oldFielder>)"
                        else if (dInfo.contains("Run Out (", ignoreCase = true)) {
                            val detail = dInfo.substringAfter("(").substringBefore(")").trim()
                            if (detail.isNotEmpty() && !detail.equals(fielderName, ignoreCase = true) && !detail.contains("/")) {
                                val pattern = "(?i)\\b" + Regex.escape(detail) + "\\b"
                                dInfo = dInfo.replace(Regex(pattern), fielderName)
                                ballChanged = true
                            }
                        }
                    }

                    // Check if any old name mapped in nameToId corresponds to a renamed player
                    nameToId.forEach { (name, id) ->
                        if (!name.isNullOrBlank() && !id.isNullOrBlank()) {
                            val player = playerMap[id]
                            if (player != null && player.name.isNotBlank()) {
                                val currentName = player.name.trim()
                                if (!name.trim().equals(currentName, ignoreCase = true) && dInfo.contains(name.trim(), ignoreCase = true)) {
                                    val pattern = "(?i)\\b" + Regex.escape(name.trim()) + "\\b"
                                    dInfo = dInfo.replace(Regex(pattern), currentName)
                                    ballChanged = true
                                }
                            }
                        }
                    }

                    if (ballChanged) {
                        b.dismissalInfo = dInfo
                        matchChanged = true
                    }
                    b
                }
            }

            match.ballsJson1 = sanitizeBalls(match.ballsJson1)
            match.ballsJson2 = sanitizeBalls(match.ballsJson2)

            if (matchChanged) {
                db.matchDao().insertMatch(match)
                // Sync player_match_stats
                val stats = db.statsDao().getStatsByMatch(match.id) ?: emptyList()
                val ballsMap = (match.ballsJson1.orEmpty() + match.ballsJson2.orEmpty())
                    .filterNotNull()
                    .filter { it.isWicket && !it.dismissalInfo.isNullOrEmpty() }
                    .associateBy { it.outPlayerName?.trim() }

                for (s in stats.filterNotNull()) {
                    val b = ballsMap[s.playerName?.trim()]
                    if (b != null && b.dismissalInfo != s.dismissalInfo) {
                        s.dismissalInfo = b.dismissalInfo
                        db.statsDao().insertStat(s)
                    }
                }
            }
        }
    }
}

package com.example.scoring

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source

/**
 * The Heart of the Gully Network.
 * Handles secure authentication and real-time syncing between Local Room and Cloud Firestore.
 */
object GullySyncManager {
    private const val TAG = "GullySync"
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var playerListener: ListenerRegistration? = null
    private var matchListener: ListenerRegistration? = null
    private var draftListener: ListenerRegistration? = null
    private var gullyExistenceListener: ListenerRegistration? = null

    interface SyncCallback {
        fun onSuccess(message: String)
        fun onFailure(error: String)
    }

    /**
     * Registers a brand new Gully in the Cloud.
     */
    fun createGully(id: String, pass: String, callback: SyncCallback) {
        val gullyRef = db.collection("gullies").document(id)
        
        gullyRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                callback.onFailure("League ID '$id' is already taken. Try another name.")
            } else {
                val data = hashMapOf(
                    "id" to id,
                    "passcode" to pass, 
                    "createdAt" to System.currentTimeMillis(),
                    "memberCount" to 1
                )
                gullyRef.set(data).addOnSuccessListener {
                    LeagueNotificationManager.subscribeToLeague(id)
                    callback.onSuccess("League '$id' created successfully!")
                }.addOnFailureListener { e ->
                    callback.onFailure("Cloud error: ${e.message}")
                }
            }
        }.addOnFailureListener { e ->
            callback.onFailure("Network error: ${e.message}")
        }
    }

    /**
     * Verifies passcode and links phone to an existing Gully.
     * Uses Source.SERVER to skip potentially slow local cache for instant verification.
     */
    fun joinGully(id: String, pass: String, callback: SyncCallback) {
        db.collection("gullies").document(id).get(Source.SERVER).addOnSuccessListener { doc ->
            if (doc.exists()) {
                val cloudPass = doc.getString("passcode")
                if (cloudPass == pass) {
                    LeagueNotificationManager.subscribeToLeague(id)
                    callback.onSuccess("Joined League '$id'")
                } else {
                    callback.onFailure("Incorrect Passcode for '$id'")
                }
            } else {
                callback.onFailure("League '$id' not found.")
            }
        }.addOnFailureListener { e ->
            callback.onFailure("Connection error: ${e.message}")
        }
    }

    /**
     * The "Bridge": Starts real-time observation of cloud data.
     */
    fun startSync(context: Context, gullyId: String) {
        stopSync() 
        val appContext = context.applicationContext
        
        // Ensure user is subscribed to this league's notifications
        LeagueNotificationManager.subscribeToLeague(gullyId)
        
        AppDatabase.ioExecutor.execute {
            val localDb = AppDatabase.getInstance(appContext)

            // 1. Listen for Players
            playerListener = db.collection("gullies").document(gullyId).collection("players")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener
                    
                    try {
                        val docs = snapshots?.documents ?: return@addSnapshotListener
                        AppDatabase.ioExecutor.execute {
                            try {
                                docs.forEach { doc ->
                                    if (!doc.exists()) return@forEach
                                    val cloudPlayer = doc.toObject(PlayerEntity::class.java)
                                    if (cloudPlayer != null) {
                                        val local = localDb.playerDao().getPlayerById(cloudPlayer.id)
                                        
                                        if (local == null || cloudPlayer.lastSyncedAt > local.lastSyncedAt) {
                                            // PHOTO SYNC: Reconstruct local image from cloud data ONLY if data is new
                                            if (!cloudPlayer.photoBase64.isNullOrEmpty()) {
                                                val isPathInvalid = local == null || local.photoUri.isEmpty() || !PhotoUtils.isValidInternalPath(local.photoUri)
                                                if (isPathInvalid) {
                                                    val savedPath = PhotoUtils.base64ToPath(appContext, cloudPlayer.photoBase64, cloudPlayer.id)
                                                    if (savedPath != null) cloudPlayer.photoUri = savedPath
                                                } else {
                                                    cloudPlayer.photoUri = local.photoUri
                                                }
                                            }

                                            cloudPlayer.gullyId = gullyId
                                            localDb.playerDao().insertPlayer(cloudPlayer)
                                            RankingRegistry.refresh(appContext, null)
                                        }
                                    }
                                }
                            } catch (ex: Exception) { Log.e(TAG, "Player sync loop failed: ${ex.message}") }
                        }
                    } catch (ex: Exception) { Log.e(TAG, "Fatal Player sync error: ${ex.message}") }
                }

            // 2. Listen for Matches (Distributed Deletion + Sync)
            matchListener = db.collection("gullies").document(gullyId).collection("matches")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener
                    
                    try {
                        val changes = snapshots?.documentChanges ?: return@addSnapshotListener
                        AppDatabase.ioExecutor.execute {
                            try {
                                changes.forEach { dc ->
                                    val doc = dc.document
                                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                        val matchId = doc.id
                                        val local = localDb.matchDao().getMatchById(matchId)
                                        if (local != null) {
                                            localDb.matchDao().deleteMatch(local)
                                            localDb.statsDao().deleteStatsByMatch(matchId)
                                            RankingRegistry.refresh(appContext, null)
                                        }
                                        return@forEach
                                    }

                                    val cloudMatch = doc.toObject(MatchEntity::class.java)
                                    if (cloudMatch != null) {
                                        val local = localDb.matchDao().getMatchById(cloudMatch.id)
                                        
                                        // Only decompress and update if the cloud version is newer
                                        if (local == null || cloudMatch.lastSyncedAt > local.lastSyncedAt) {
                                            try {
                                                cloudMatch.compressedPayload?.let { payloadStr ->
                                                    val payload = MatchCompressor.decompressMatchData(payloadStr, MatchPayload::class.java)
                                                    if (payload != null) {
                                                        cloudMatch.ballsJson1 = payload.b1; cloudMatch.ballsJson2 = payload.b2
                                                        cloudMatch.commentaryJson1 = payload.c1; cloudMatch.commentaryJson2 = payload.c2
                                                        cloudMatch.commentaryJson = payload.cj
                                                        cloudMatch.pshipJson1 = payload.p1; cloudMatch.pshipJson2 = payload.p2
                                                        cloudMatch.fowJson1 = payload.f1; cloudMatch.fowJson2 = payload.f2
                                                        payload.stats?.forEach { s -> if (s != null) localDb.statsDao().insertStat(s) }
                                                    }
                                                }
                                            } catch (ex: Exception) { Log.e(TAG, "Match payload sync error: ${ex.message}") }

                                            cloudMatch.gullyId = gullyId
                                            localDb.matchDao().insertMatch(cloudMatch)
                                            // Only refresh rankings on match sync if it's finished to avoid heavy background thrashing during live play
                                            if (cloudMatch.isFinished) {
                                                RankingRegistry.refresh(appContext, null)
                                            }
                                        }
                                    }
                                }
                            } catch (ex: Exception) { Log.e(TAG, "Match sync loop failed: ${ex.message}") }
                        }
                    } catch (ex: Exception) { Log.e(TAG, "Fatal Match sync error: ${ex.message}") }
                }

            // 3. Listen for Drafts (The Hand-off Feature)
            draftListener = db.collection("gullies").document(gullyId).collection("drafts")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener
                    
                    try {
                        val changes = snapshots?.documentChanges ?: return@addSnapshotListener
                        AppDatabase.ioExecutor.execute {
                            try {
                                changes.forEach { dc ->
                                    val doc = dc.document
                                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                        val draft = localDb.draftDao().getDraftById(doc.id)
                                        if (draft != null) localDb.draftDao().deleteDraft(draft)
                                        return@forEach
                                    }

                                    val cloudDraft = doc.toObject(DraftMatchEntity::class.java)
                                    if (cloudDraft != null) {
                                        val local = localDb.draftDao().getDraftById(cloudDraft.id)
                                        if (local == null || cloudDraft.lastSyncedAt > local.lastSyncedAt) {
                                            cloudDraft.gullyId = gullyId
                                            localDb.draftDao().insertDraft(cloudDraft)
                                        }
                                    }
                                }
                            } catch (ex: Exception) { Log.e(TAG, "Draft sync loop failed: ${ex.message}") }
                        }
                    } catch (ex: Exception) { Log.e(TAG, "Fatal Draft sync error: ${ex.message}") }
                }
                
            Log.d(TAG, "Sync Engine Started for: $gullyId")

            // 4. Watch for Gully Document Deletion (The "Death Watcher")
            gullyExistenceListener = db.collection("gullies").document(gullyId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        // This happens if the gully is deleted or permissions changed
                        Log.e(TAG, "Gully access lost: ${e.message}")
                        handleGullyLost(appContext)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null && !snapshot.exists()) {
                        Log.w(TAG, "Gully document was deleted in console.")
                        handleGullyLost(appContext)
                    }
                }
        }
    }

    private fun handleGullyLost(context: Context) {
        val currentId = getCurrentGullyId(context) ?: return
        
        stopSync()
        // 1. Remove from Recent Switchboard automatically
        LeagueNotificationManager.unsubscribeFromLeague(currentId)
        GullyHistoryManager.removeGully(context, currentId)
        
        // 2. Revert to Local Mode in Prefs
        context.getSharedPreferences("gully_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("current_gully_id")
            .apply()
            
        // 3. Notify user on Main Thread
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "League '$currentId' was deleted. Switched to Local Mode.", Toast.LENGTH_LONG).show()
        }
    }

    fun stopSync() {
        playerListener?.remove()
        matchListener?.remove()
        draftListener?.remove()
        gullyExistenceListener?.remove()
        playerListener = null
        matchListener = null
        draftListener = null
        gullyExistenceListener = null
    }

    /**
     * Pushes a local change to the Cloud.
     * Incorporates "Universal Global ID" logic and Identity Merging.
     */
    fun syncPlayerToCloud(gullyId: String, player: PlayerEntity) {
        if (gullyId == "local" || gullyId.isEmpty()) return

        val context = ScoringApp.instance?.applicationContext ?: return
        val localDb = AppDatabase.getInstance(context)

        AppDatabase.ioExecutor.execute {
            player.gullyId = gullyId
            player.lastSyncedAt = System.currentTimeMillis()

            // 1. SMART IDENTITY CHECK: Search for an existing global identity by Name + Jersey
            db.collection("global_players")
                .whereEqualTo("name", player.name)
                .whereEqualTo("jersey", player.jerseyNumber)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshots ->
                    val existingDoc = snapshots.documents.firstOrNull()
                    val existingGlobalId = existingDoc?.getString("universalId")

                    // 2. IDENTITY ADOPTION: If a global record exists with a different ID, adopt it
                    if (existingGlobalId != null && existingGlobalId != player.globalId) {
                        Log.d(TAG, "Identity Match Found: ${player.name} adopting Global ID $existingGlobalId")
                        player.globalId = existingGlobalId
                        AppDatabase.ioExecutor.execute {
                            localDb.playerDao().updatePlayer(player)
                        }
                    }

                    // Proceed with standard sync using the potentially updated globalId
                    performInternalPlayerSync(gullyId, player, localDb)
                }
                .addOnFailureListener {
                    // Fallback to standard sync if search fails
                    performInternalPlayerSync(gullyId, player, localDb)
                }
        }
    }

    private fun performInternalPlayerSync(gullyId: String, player: PlayerEntity, localDb: AppDatabase) {
        val context = ScoringApp.instance?.applicationContext ?: return
        
        AppDatabase.ioExecutor.execute {
            // Ensure globalId is set (Fallback)
            val finalGlobalId = player.globalId ?: player.id
            if (player.globalId == null) player.globalId = finalGlobalId

            // CRITICAL FIX: Fetch global-aware totals (Across all local gullies this device knows about)
            val localRuns = localDb.statsDao().getGlobalTotalRuns(finalGlobalId) ?: 0
            val localWickets = localDb.statsDao().getGlobalTotalWickets(finalGlobalId) ?: 0
            val localMatches = localDb.statsDao().getGlobalTotalMatches(finalGlobalId)

            // FORCE REFRESH: Always rebuild base64 during upload
            if (player.photoUri.isNotEmpty()) {
                player.photoBase64 = PhotoUtils.pathToBase64(context, player.photoUri)
            } else {
                player.photoBase64 = null
            }

            // --- STEP 1: UPDATE GLOBAL DIRECTORY ---
            val docId = player.globalId ?: player.id
            val globalRef = db.collection("global_players").document(docId)

            val profileUpdate = hashMapOf(
                "name" to player.name,
                "jersey" to player.jerseyNumber,
                "universalId" to docId,
                "lastUpdated" to System.currentTimeMillis()
            )

            // ALWAYS try to include photo if we have it locally
            if (!player.photoBase64.isNullOrEmpty()) {
                profileUpdate["photoBase64"] = player.photoBase64!!
            }

            globalRef.set(profileUpdate, SetOptions.merge()).addOnSuccessListener {
                // Now handle origin and stats with a second merged set to be safe
                val statsAndOrigin = hashMapOf<String, Any>()
                
                globalRef.get().addOnSuccessListener { doc ->
                    if (!doc.exists() || doc.getString("originGully").isNullOrEmpty()) {
                        statsAndOrigin["originGully"] = player.originGully ?: gullyId
                    }
                    
                    val cloudRuns = (doc.get("totalRuns") as? Number)?.toLong() ?: 0L
                    if (localRuns > cloudRuns || !doc.exists()) {
                        statsAndOrigin["totalRuns"] = localRuns.toLong()
                        statsAndOrigin["totalWickets"] = localWickets.toLong()
                        statsAndOrigin["totalMatches"] = localMatches.toLong()
                    }
                    
                    if (statsAndOrigin.isNotEmpty()) {
                        globalRef.set(statsAndOrigin, SetOptions.merge())
                    }
                }

                // --- STEP 2: SAVE TO SPECIFIC GULLY ---
                db.collection("gullies").document(gullyId)
                    .collection("players").document(player.id)
                    .set(player, SetOptions.merge())
                    .addOnFailureListener { Log.e(TAG, "Player sync failed: ${it.message}") }
                
            }.addOnFailureListener { e ->
                Log.e(TAG, "Global profile set failed: ${e.message}")
            }
        }
    }

    /**
     * Additive Stat Update: Increments global totals via Firestore atomicity.
     * This prevents overwriting stats from other gullies.
     */
    fun incrementGlobalPlayerStats(globalId: String, matchRuns: Int, matchWickets: Int) {
        if (globalId.isEmpty()) return
        
        val globalRef = db.collection("global_players").document(globalId)
        val increments = hashMapOf(
            "totalRuns" to FieldValue.increment(matchRuns.toLong()),
            "totalWickets" to FieldValue.increment(matchWickets.toLong()),
            "totalMatches" to FieldValue.increment(1),
            "lastUpdated" to System.currentTimeMillis()
        )

        globalRef.set(increments, SetOptions.merge()).addOnFailureListener { e ->
            Log.e(TAG, "Stat increment failed for $globalId: ${e.message}")
        }
    }

    /**
     * Searches for players globally by name prefix.
     */
    fun searchGlobalPlayers(query: String, callback: (List<PlayerEntity>) -> Unit) {
        val q = query.trim()
        if (q.isEmpty()) {
            callback(emptyList())
            return
        }

        db.collection("global_players")
            .whereGreaterThanOrEqualTo("name", q)
            .whereLessThanOrEqualTo("name", q + "\uf8ff")
            .limit(40) // Increased limit to allow for client-side deduplication
            .get()
            .addOnSuccessListener { snapshots ->
                val list = snapshots.documents.mapNotNull { doc ->
                    try {
                        val p = PlayerEntity()
                        p.id = doc.getString("universalId") ?: doc.id
                        p.name = doc.getString("name") ?: "Unknown Player"
                        p.jerseyNumber = doc.getString("jersey") ?: "0"
                        p.photoBase64 = doc.getString("photoBase64")
                        
                        // FIX: Better handling of originGully (No more "Global Network" placeholder)
                        p.originGully = doc.getString("originGully") ?: doc.getString("origin")
                        
                        // CRITICAL FIX: Safe numeric conversion from Firestore (Handles Long and Double)
                        p.totalRuns = (doc.get("totalRuns") as? Number)?.toLong() ?: 0L
                        p.totalWickets = (doc.get("totalWickets") as? Number)?.toLong() ?: 0L
                        p.totalMatches = (doc.get("totalMatches") as? Number)?.toLong() ?: 0L
                        
                        p.globalId = p.id
                        p
                    } catch (e: Exception) {
                        Log.e(TAG, "Search mapping error: ${e.message}")
                        null
                    }
                }

                // --- CLIENT-SIDE DEDUPLICATION ---
                // If multiple documents exist for the same name/jersey, show only the one with the most stats
                val deduplicated = list.groupBy { "${it.name.lowercase().trim()}#${it.jerseyNumber.trim()}" }
                    .map { (_, group) ->
                        group.sortedWith(compareByDescending<PlayerEntity> { it.totalRuns }
                            .thenByDescending { it.totalMatches }
                            .thenByDescending { !it.originGully.isNullOrEmpty() && it.originGully != "Unknown" }
                        ).first()
                    }

                callback(deduplicated)
            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }

    /**
     * Pushes a local change to the Cloud.
     * Incorporates "Universal Global ID" logic and Phase 5 Compression.
     */
    fun syncMatchToCloud(gullyId: String, match: MatchEntity) {
        if (gullyId == "local" || gullyId.isEmpty()) return
        
        AppDatabase.ioExecutor.execute {
            performSyncMatchToCloudInternal(gullyId, match)
        }
    }

    /**
     * Internal synchronous version of match sync.
     */
    fun performSyncMatchToCloudInternal(gullyId: String, match: MatchEntity) {
        try {
            val context = ScoringApp.instance?.applicationContext ?: return
            val localDb = AppDatabase.getInstance(context)

            // 1. Prepare a "Cloud-Friendly" copy
            val cloudCopy = MatchEntity().apply {
                this.id = match.id
                this.gullyId = match.gullyId
                this.lastSyncedAt = System.currentTimeMillis()
                this.teamAName = match.teamAName
                this.teamBName = match.teamBName
                this.venue = match.venue
                this.totalOvers = match.totalOvers
                this.ballType = match.ballType
                this.teamAPlayerCount = match.teamAPlayerCount
                this.teamBPlayerCount = match.teamBPlayerCount
                this.playedAt = match.playedAt
                this.result = match.result
                this.isFinished = match.isFinished
                this.isAbandoned = match.isAbandoned
                this.tossWinner = match.tossWinner
                this.tossDecision = match.tossDecision
                this.firstInningsTeam = match.firstInningsTeam
                this.firstInningsRuns = match.firstInningsRuns
                this.firstInningsWickets = match.firstInningsWickets
                this.firstInningsRetiredHurtCount = match.firstInningsRetiredHurtCount
                this.secondInningsTeam = match.secondInningsTeam
                this.secondInningsRuns = match.secondInningsRuns
                this.secondInningsWickets = match.secondInningsWickets
                this.secondInningsRetiredHurtCount = match.secondInningsRetiredHurtCount
                this.playerOfTheMatchName = match.playerOfTheMatchName
                this.matchMargin = match.matchMargin
                this.revisedTarget = match.revisedTarget
                this.revisedOvers = match.revisedOvers
                this.teamANames = match.teamANames
                this.teamBNames = match.teamBNames
                this.photoMap = match.photoMap
                this.nameToIdMap = match.nameToIdMap
                this.isLive = match.isLive
                this.lastScorerPulse = match.lastScorerPulse
                this.isSharedOver = match.isSharedOver
                this.ruleRunsOnWide = match.ruleRunsOnWide
                this.ruleFreeHit = match.ruleFreeHit
                this.ruleRunsOnBye = match.ruleRunsOnBye
                this.ruleOverthrow = match.ruleOverthrow
                this.ruleEveryPlayerBats = match.ruleEveryPlayerBats
                
                // Resumption State
                this.isFreeHitActive = match.isFreeHitActive
                this.currentBowlerInSpell = match.currentBowlerInSpell
                this.currentStrikerName = match.currentStrikerName
                this.currentNonStrikerName = match.currentNonStrikerName
                this.currentBowlerName = match.currentBowlerName
                this.nextBatsmanIdx = match.nextBatsmanIdx
                this.isTeamABatting = match.isTeamABatting
                this.strikerEntryTime = match.strikerEntryTime
                this.nonStrikerEntryTime = match.nonStrikerEntryTime

                this.firstInningsStartTime = match.firstInningsStartTime
                this.firstInningsEndTime = match.firstInningsEndTime
                this.secondInningsStartTime = match.secondInningsStartTime
                this.secondInningsEndTime = match.secondInningsEndTime
            }

            // 2. Fetch Match Stats
            val stats = localDb.statsDao().getStatsByMatch(match.id) ?: emptyList()

            // 3. Compress Heavy Lists into the Payload
            val payload = MatchPayload().apply {
                b1 = match.ballsJson1; b2 = match.ballsJson2
                c1 = match.commentaryJson1; c2 = match.commentaryJson2
                cj = match.commentaryJson
                p1 = match.pshipJson1; p2 = match.pshipJson2
                f1 = match.fowJson1; f2 = match.fowJson2
                this.stats = stats
            }
            cloudCopy.compressedPayload = MatchCompressor.compressMatchData(payload)

            // 4. Push to Firestore
            db.collection("gullies").document(gullyId)
                .collection("matches").document(cloudCopy.id)
                .set(cloudCopy, SetOptions.merge())
                .addOnFailureListener { Log.e(TAG, "Match sync failed: ${it.message}") }
                
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync match to cloud: ${e.message}")
        }
    }

    /**
     * Performs a lightweight update of just the heartbeat fields.
     * Prevents re-compressing the entire match payload for every 20s pulse.
     */
    fun syncMatchPulseToCloud(gullyId: String, matchId: String, lastPulse: Long) {
        if (gullyId == "local" || gullyId.isEmpty()) return
        
        val pulseUpdate = hashMapOf(
            "lastScorerPulse" to lastPulse,
            "isLive" to true,
            "lastSyncedAt" to System.currentTimeMillis()
        )

        db.collection("gullies").document(gullyId)
            .collection("matches").document(matchId)
            .update(pulseUpdate as Map<String, Any>)
            .addOnFailureListener { Log.e(TAG, "Pulse sync failed for $matchId: ${it.message}") }
    }

    fun deleteMatchFromCloud(gullyId: String, matchId: String) {
        if (gullyId == "local" || gullyId.isEmpty()) return
        db.collection("gullies").document(gullyId).collection("matches").document(matchId).delete()
    }

    fun syncDraftToCloud(gullyId: String, draft: DraftMatchEntity) {
        if (gullyId == "local" || gullyId.isEmpty()) return
        draft.gullyId = gullyId
        draft.lastSyncedAt = System.currentTimeMillis()
        db.collection("gullies").document(gullyId).collection("drafts").document(draft.id).set(draft, SetOptions.merge())
    }

    fun deleteDraftFromCloud(gullyId: String, draftId: String) {
        if (gullyId == "local" || gullyId.isEmpty()) return
        db.collection("gullies").document(gullyId).collection("drafts").document(draftId).delete()
    }

    /**
     * Sends a league-wide notification when a match starts.
     */
    fun sendMatchStartNotification(context: Context, match: MatchEntity) {
        val gId = match.gullyId
        if (gId == "local" || gId.isEmpty()) return

        val title = gId
        val body = if (!match.venue.isNullOrEmpty()) {
            "Live match in ${match.venue}"
        } else {
            "Match started between ${match.teamAName} vs ${match.teamBName}"
        }

        // Use OneSignal to trigger the notification
        LeagueNotificationManager.sendLeagueNotification(gId, title, body, match.id)
    }

    /**
     * Sends a live score update notification that replaces the previous one.
     */
    fun sendLiveScoreUpdate(context: Context, match: MatchEntity) {
        val gId = match.gullyId
        if (gId == "local" || gId.isEmpty()) return

        var title = ""
        var body = ""

        if (match.isFinished) {
            title = "Match Finished"
            body = match.result?.uppercase() ?: "COMPLETED"
        } else {
            val isSecondInnings = match.secondInningsTeam != null
            val i2BallsList = match.ballsJson2?.filterNotNull() ?: emptyList()
            val i2Legal = i2BallsList.count { it.type == BallType.NORMAL || it.type == BallType.BYE || it.type == BallType.LEG_BYE }
            
            // Detect Innings Break: 2nd innings team is set, but no balls bowled yet
            val isInningsBreak = isSecondInnings && i2Legal == 0
            
            if (isInningsBreak) {
                val target = match.revisedTarget ?: (match.firstInningsRuns + 1)
                val totalBalls = match.totalOvers * 6
                title = "Live: Innings Break"
                body = "Target: $target Runs in $totalBalls Balls for ${match.secondInningsTeam}"
            } else if (isSecondInnings) {
                val i2Overs = "${i2Legal / 6}.${i2Legal % 6}"
                val target = match.revisedTarget ?: (match.firstInningsRuns + 1)
                title = "Live: ${match.secondInningsTeam} - ${match.secondInningsRuns}/${match.secondInningsWickets} ($i2Overs/${match.totalOvers})"
                body = "TARGET: $target Runs"
            } else {
                val i1BallsList = match.ballsJson1?.filterNotNull() ?: emptyList()
                val i1Legal = i1BallsList.count { it.type == BallType.NORMAL || it.type == BallType.BYE || it.type == BallType.LEG_BYE }
                val i1Overs = "${i1Legal / 6}.${i1Legal % 6}"
                
                // Calculate PAR (Projected Score) based on Current Run Rate
                val crr = if (i1Legal > 0) (match.firstInningsRuns.toDouble() / i1Legal) * 6.0 else 0.0
                val projected = (crr * match.totalOvers).toInt()
                
                title = "Live: ${match.firstInningsTeam ?: match.teamAName} - ${match.firstInningsRuns}/${match.firstInningsWickets} ($i1Overs/${match.totalOvers})"
                body = "PAR: $projected Runs"
            }
        }

        LeagueNotificationManager.sendLeagueNotification(gId, title, body, match.id)
    }

    /**
     * Sends a notification for a player milestone (runs/wickets).
     */
    fun sendMilestoneNotification(context: Context, matchId: String, gullyId: String, playerName: String, milestone: String) {
        if (gullyId == "local" || gullyId.isEmpty()) return

        val title = "$gullyId - Milestone!"
        val body = "$playerName $milestone!"

        LeagueNotificationManager.sendLeagueNotification(gullyId, title, body, matchId)
    }

    fun getCurrentGullyId(context: Context?): String? {
        if (context == null) return null
        return context.getSharedPreferences("gully_prefs", Context.MODE_PRIVATE)
            .getString("current_gully_id", null)
    }
}

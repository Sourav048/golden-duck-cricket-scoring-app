package com.example.scoring

import android.content.Context

/**
 * Interface to unify data access for fragments between Live Scoring and Match History.
 */
interface ScoringProvider {
    val match: Match?
    fun getPlayerFromCache(name: String?): Player?
    val commentary: MutableList<CommentaryEntry?>?
    val teamANames: ArrayList<String?>?
    val teamBNames: ArrayList<String?>?
    val photoMap: MutableMap<String?, String?>?
    val nameToIdMap: MutableMap<String?, String?>?

    // Live UI Metrics
    val striker: Player?
    val nonStriker: Player?
    val bowler: Player?
    val pshipRuns: Int
    val pshipBalls: Int
    val overBallsList: MutableList<String?>?

    // Rule Queries
    val isRuleRunsOnWide: Boolean
    val isRuleRunsOnBye: Boolean
    val isRuleFreeHit: Boolean
    val isRuleOverthrow: Boolean
    val isFreeHitActive: Boolean
    val isFinished: Boolean
    val isAbandoned: Boolean

    // Commands/Actions
    fun playBall(runs: Int, type: BallType?, isWicket: Boolean)
    fun handleWide()
    fun handleNoBall()
    fun handleBye()
    fun handleLegBye()
    fun handleOverthrow()
    fun handleWicketFlow()
    fun handlePenaltyFlow()
    fun saveMatchToDatabase()
    fun saveMatchToDatabase(isFinished: Boolean, isAbandoned: Boolean, isLive: Boolean)
    fun saveMatchToDatabase(
        isFinished: Boolean,
        isAbandoned: Boolean,
        isLive: Boolean,
        isMilestone: Boolean
    )

    fun startNextInnings()
    fun updateUI()
    fun undoBall()
    fun showRetiredPlayerSelection(isHurt: Boolean)
    fun showBatsmanSelectionDialog(isStrikerReplacing: Boolean)
    fun showBowlerSelectionDialog(onDone: Runnable?, cancelable: Boolean)
    fun handleBowlerChangeMidOver()
    fun updateUIProvider()
    fun handleApplyDLS()
    fun playLottie(assetName: String?)
    val isScorer: Boolean
    fun calculateMatchResult(): String?
    fun addPlayerToTeam(
        teamName: String?,
        playerName: String?,
        playerId: String?,
        photoUri: String?
    )
    fun showEditCommentaryDialog(context: Context, entry: CommentaryEntry, pos: Int)
}

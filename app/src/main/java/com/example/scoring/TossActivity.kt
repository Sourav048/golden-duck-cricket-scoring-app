package com.example.scoring

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import java.util.Random

class TossActivity : BaseActivity() {
    private var toggleWinner: MaterialButtonToggleGroup? = null
    private var toggleDecision: MaterialButtonToggleGroup? = null
    private var toggleCallerTeam: MaterialButtonToggleGroup? = null
    private var toggleHeadsTails: MaterialButtonToggleGroup? = null
    private var tvCoinResult: TextView? = null
    private var teamAName: String? = null
    private var teamBName: String? = null
    private var venue: String? = null
    private var teamANames: ArrayList<String?>? = null
    private var teamBNames: ArrayList<String?>? = null
    private var teamAPhotos: ArrayList<String?>? = null
    private var teamBPhotos: ArrayList<String?>? = null
    private var teamAIds: ArrayList<String?>? = null
    private var teamBIds: ArrayList<String?>? = null
    private var overs = 0
    private var ballType: String? = null
    private var ruleRunsOnWide = false
    private var ruleFreeHit = false
    private var ruleRunsOnBye = false
    private var ruleOverthrow = false
    private var ruleEveryPlayerBats = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_toss)

        val draftId = intent.getStringExtra("draftId")
        if (draftId != null) {
            AppDatabase.ioExecutor.execute {
                val draft = AppDatabase.getInstance(this).draftDao().getDraftById(draftId)
                if (draft != null) {
                    teamAName = draft.teamAName
                    teamBName = draft.teamBName
                    venue = draft.venue
                    overs = draft.overs
                    ballType = draft.ballType
                    ruleRunsOnWide = draft.ruleRunsOnWide
                    ruleFreeHit = draft.ruleFreeHit
                    ruleRunsOnBye = draft.ruleRunsOnBye
                    ruleOverthrow = draft.ruleOverthrow
                    ruleEveryPlayerBats = draft.ruleEveryPlayerBats
                    teamANames = draft.teamANames?.let { ArrayList(it) }
                    teamBNames = draft.teamBNames?.let { ArrayList(it) }
                    teamAPhotos = draft.teamAPhotos?.let { ArrayList(it) }
                    teamBPhotos = draft.teamBPhotos?.let { ArrayList(it) }
                    teamAIds = draft.teamAIds?.let { ArrayList(it) }
                    teamBIds = draft.teamBIds?.let { ArrayList(it) }

                    runOnUiThread { setupUI() }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@TossActivity, "Match setup not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        } else {
            finish()
        }
    }

    private fun setupUI() {
        toggleWinner = findViewById(R.id.toggleTossWinner)
        toggleDecision = findViewById(R.id.toggleDecision)
        toggleCallerTeam = findViewById(R.id.toggleCallerTeam)
        toggleHeadsTails = findViewById(R.id.toggleHeadsTails)
        tvCoinResult = findViewById(R.id.tvCoinResult)

        findViewById<MaterialButton>(R.id.btnCallTeamA).text = teamAName
        findViewById<MaterialButton>(R.id.btnCallTeamB).text = teamBName
        findViewById<MaterialButton>(R.id.btnTossTeamA).text = teamAName
        findViewById<MaterialButton>(R.id.btnTossTeamB).text = teamBName

        findViewById<View>(R.id.btnFlipCoin).setOnClickListener { flipCoin() }
        findViewById<View>(R.id.btnContinue).setOnClickListener { startRoleSelection() }
    }

    private fun flipCoin() {
        val callerId = toggleCallerTeam?.checkedButtonId ?: -1
        val callChoiceId = toggleHeadsTails?.checkedButtonId ?: -1

        if (callerId == -1 || callChoiceId == -1) {
            Toast.makeText(this, "Select team and call (Heads/Tails) first", Toast.LENGTH_SHORT).show()
            return
        }

        val callIsHeads = (callChoiceId == R.id.btnCallHeads)
        val callerName = if (callerId == R.id.btnCallTeamA) teamAName else teamBName

        val coinIsHeads = Random().nextBoolean()
        val resultText = if (coinIsHeads) "HEADS" else "TAILS"

        tvCoinResult?.animate()?.scaleX(0f)?.setDuration(150)?.withEndAction {
            tvCoinResult?.text = resultText
            tvCoinResult?.animate()?.scaleX(1.2f)?.scaleY(1.2f)?.setDuration(150)?.withEndAction {
                tvCoinResult?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(100)?.start()
                val callerWon = (callIsHeads == coinIsHeads)
                val winner = if (callerWon) callerName else (if (callerName == teamAName) teamBName else teamAName)

                Toast.makeText(this, "$winner won the toss!", Toast.LENGTH_LONG).show()

                if (winner == teamAName) toggleWinner?.check(R.id.btnTossTeamA)
                else toggleWinner?.check(R.id.btnTossTeamB)
            }?.start()
        }?.start()
    }

    private fun startRoleSelection() {
        val winnerId = toggleWinner?.checkedButtonId ?: -1
        val decisionId = toggleDecision?.checkedButtonId ?: -1

        if (winnerId == -1 || decisionId == -1) {
            Toast.makeText(this, "Please select toss winner and decision", Toast.LENGTH_SHORT).show()
            return
        }

        val teamAWon = (winnerId == R.id.btnTossTeamA)
        val choseBat = (decisionId == R.id.btnDecisionBat)
        val battingTeam = if (teamAWon == choseBat) teamAName else teamBName

        val battingPlayers = (if (battingTeam == teamAName) teamANames else teamBNames) ?: return
        val bowlingPlayers = (if (battingTeam == teamAName) teamBNames else teamANames) ?: return

        val tossWinner = if (teamAWon) teamAName else teamBName
        val tossDecision = if (choseBat) "Bat" else "Field"

        if (battingPlayers.size == 1 && bowlingPlayers.size == 1) {
            launchMainActivity(battingPlayers[0], null, bowlingPlayers[0], battingTeam, tossWinner, tossDecision)
            return
        }

        showPlayerSelectionDialog("Select Striker", battingPlayers, false) { striker ->
            val remaining = ArrayList(battingPlayers).apply { remove(striker) }
            if (remaining.isEmpty()) {
                showPlayerSelectionDialog("Select Bowler", bowlingPlayers, false) { bowler ->
                    launchMainActivity(striker, null, bowler, battingTeam, tossWinner, tossDecision)
                }
            } else {
                showPlayerSelectionDialog("Select Non-Striker (Optional)", remaining, true) { nonStriker ->
                    showPlayerSelectionDialog("Select Bowler", bowlingPlayers, false) { bowler ->
                        launchMainActivity(striker, nonStriker, bowler, battingTeam, tossWinner, tossDecision)
                    }
                }
            }
        }
    }

    private fun launchMainActivity(
        striker: String?,
        nonStriker: String?,
        bowler: String?,
        battingTeam: String?,
        tossWinner: String?,
        tossDecision: String?
    ) {
        val draftId = intent.getStringExtra("draftId")
        val intentNext = Intent(this, MainActivity::class.java).apply {
            putExtra("draftId", draftId)
            putExtra("strikerName", striker)
            putExtra("nonStrikerName", nonStriker)
            putExtra("bowlerName", bowler)
            putExtra("battingTeam", battingTeam)
            putExtra("tossWinner", tossWinner)
            putExtra("tossDecision", tossDecision)
            
            // Pass all team details
            putStringArrayListExtra("teamANames", teamANames)
            putStringArrayListExtra("teamBNames", teamBNames)
            putStringArrayListExtra("teamAPhotos", teamAPhotos)
            putStringArrayListExtra("teamBPhotos", teamBPhotos)
            putStringArrayListExtra("teamAIds", teamAIds)
            putStringArrayListExtra("teamBIds", teamBIds)
        }
        startActivity(intentNext)
        finish()
    }

    private fun interface SelectionCallback {
        fun onSelected(name: String?)
    }

    private fun showPlayerSelectionDialog(
        title: String,
        players: List<String?>,
        optional: Boolean,
        callback: SelectionCallback
    ) {
        val list = ArrayList(players)
        if (optional) list.add(0, "None (Play Alone)")
        val items = list.filterNotNull().toTypedArray()
        showDynamicDialog {
            setTitle(title)
            setItems(items) { _, w ->
                val s = items[w]
                callback.onSelected(if (optional && "None (Play Alone)" == s) null else s)
            }
            setCancelable(false)
        }
    }
}

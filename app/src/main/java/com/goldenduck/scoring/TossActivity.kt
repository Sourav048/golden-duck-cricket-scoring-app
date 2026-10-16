package com.goldenduck.scoring

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

    private var tossCallerTeam: String? = null
    private var tossCallerChoice: String? = null
    private var tossResultText: String? = null
    private var tossWinner: String? = null
    private var tossDecision: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("teamAName", teamAName)
        outState.putString("teamBName", teamBName)
        outState.putString("venue", venue)
        outState.putInt("overs", overs)
        outState.putString("ballType", ballType)
        outState.putBoolean("ruleRunsOnWide", ruleRunsOnWide)
        outState.putBoolean("ruleFreeHit", ruleFreeHit)
        outState.putBoolean("ruleRunsOnBye", ruleRunsOnBye)
        outState.putBoolean("ruleOverthrow", ruleOverthrow)
        outState.putBoolean("ruleEveryPlayerBats", ruleEveryPlayerBats)
        outState.putStringArrayList("teamANames", teamANames)
        outState.putStringArrayList("teamBNames", teamBNames)
        outState.putStringArrayList("teamAPhotos", teamAPhotos)
        outState.putStringArrayList("teamBPhotos", teamBPhotos)
        outState.putStringArrayList("teamAIds", teamAIds)
        outState.putStringArrayList("teamBIds", teamBIds)

        outState.putString("tossCallerTeam", tossCallerTeam)
        outState.putString("tossCallerChoice", tossCallerChoice)
        outState.putString("tossResultText", tossResultText)
        outState.putString("tossWinner", tossWinner)
        outState.putString("tossDecision", tossDecision)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_toss)

        if (savedInstanceState != null) {
            teamAName = savedInstanceState.getString("teamAName")
            teamBName = savedInstanceState.getString("teamBName")
            venue = savedInstanceState.getString("venue")
            overs = savedInstanceState.getInt("overs", 20)
            ballType = savedInstanceState.getString("ballType")
            ruleRunsOnWide = savedInstanceState.getBoolean("ruleRunsOnWide")
            ruleFreeHit = savedInstanceState.getBoolean("ruleFreeHit")
            ruleRunsOnBye = savedInstanceState.getBoolean("ruleRunsOnBye")
            ruleOverthrow = savedInstanceState.getBoolean("ruleOverthrow")
            ruleEveryPlayerBats = savedInstanceState.getBoolean("ruleEveryPlayerBats")
            teamANames = savedInstanceState.getStringArrayList("teamANames")
            teamBNames = savedInstanceState.getStringArrayList("teamBNames")
            teamAPhotos = savedInstanceState.getStringArrayList("teamAPhotos")
            teamBPhotos = savedInstanceState.getStringArrayList("teamBPhotos")
            teamAIds = savedInstanceState.getStringArrayList("teamAIds")
            teamBIds = savedInstanceState.getStringArrayList("teamBIds")

            tossCallerTeam = savedInstanceState.getString("tossCallerTeam")
            tossCallerChoice = savedInstanceState.getString("tossCallerChoice")
            tossResultText = savedInstanceState.getString("tossResultText")
            tossWinner = savedInstanceState.getString("tossWinner")
            tossDecision = savedInstanceState.getString("tossDecision")

            setupUI()
            return
        }

        val draftId = intent.getStringExtra("draftId")
        val directA = intent.getStringExtra("teamA") ?: intent.getStringExtra("teamAName")
        val directB = intent.getStringExtra("teamB") ?: intent.getStringExtra("teamBName")
        val directANames = intent.getStringArrayListExtra("teamANames")
        val directBNames = intent.getStringArrayListExtra("teamBNames")

        if (!directA.isNullOrEmpty() && !directB.isNullOrEmpty() && !directANames.isNullOrEmpty() && !directBNames.isNullOrEmpty()) {
            teamAName = directA
            teamBName = directB
            venue = intent.getStringExtra("venue")
            overs = intent.getIntExtra("overs", 20)
            ballType = intent.getStringExtra("ballType")
            ruleRunsOnWide = intent.getBooleanExtra("ruleRunsOnWide", true)
            ruleFreeHit = intent.getBooleanExtra("ruleFreeHit", true)
            ruleRunsOnBye = intent.getBooleanExtra("ruleRunsOnBye", true)
            ruleOverthrow = intent.getBooleanExtra("ruleOverthrow", true)
            ruleEveryPlayerBats = intent.getBooleanExtra("ruleEveryPlayerBats", true)
            teamANames = directANames
            teamBNames = directBNames
            teamAPhotos = intent.getStringArrayListExtra("teamAPhotos")
            teamBPhotos = intent.getStringArrayListExtra("teamBPhotos")
            teamAIds = intent.getStringArrayListExtra("teamAIds")
            teamBIds = intent.getStringArrayListExtra("teamBIds")

            if (draftId != null) {
                AppDatabase.ioExecutor.execute {
                    val draft = AppDatabase.getInstance(this).draftDao().getDraftById(draftId)
                    if (draft != null) {
                        tossCallerTeam = draft.tossCallerTeam
                        tossCallerChoice = draft.tossCallerChoice
                        tossResultText = draft.tossResultText
                        tossWinner = draft.tossWinner
                        tossDecision = draft.tossDecision
                    }
                    runOnUiThread { setupUI() }
                }
            } else {
                setupUI()
            }
            return
        }

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

                    tossCallerTeam = draft.tossCallerTeam
                    tossCallerChoice = draft.tossCallerChoice
                    tossResultText = draft.tossResultText
                    tossWinner = draft.tossWinner
                    tossDecision = draft.tossDecision

                    runOnUiThread { setupUI() }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@TossActivity, "Match setup not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        } else {
            Toast.makeText(this, "Match setup not found", Toast.LENGTH_SHORT).show()
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

        findViewById<View>(R.id.btnBackToss)?.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<View>(R.id.btnFlipCoin).setOnClickListener { flipCoin() }
        findViewById<View>(R.id.btnContinue).setOnClickListener { startRoleSelection() }

        restoreTossStateUI()

        val listener = MaterialButtonToggleGroup.OnButtonCheckedListener { _, _, _ -> saveTossStateToDraft() }
        toggleCallerTeam?.addOnButtonCheckedListener(listener)
        toggleHeadsTails?.addOnButtonCheckedListener(listener)
        toggleWinner?.addOnButtonCheckedListener(listener)
        toggleDecision?.addOnButtonCheckedListener(listener)
    }

    private fun restoreTossStateUI() {
        if (tossCallerTeam == teamAName) toggleCallerTeam?.check(R.id.btnCallTeamA)
        else if (tossCallerTeam == teamBName) toggleCallerTeam?.check(R.id.btnCallTeamB)

        if (tossCallerChoice == "Heads") toggleHeadsTails?.check(R.id.btnCallHeads)
        else if (tossCallerChoice == "Tails") toggleHeadsTails?.check(R.id.btnCallTails)

        if (!tossResultText.isNullOrEmpty()) {
            tvCoinResult?.text = tossResultText
        }

        if (tossWinner == teamAName) toggleWinner?.check(R.id.btnTossTeamA)
        else if (tossWinner == teamBName) toggleWinner?.check(R.id.btnTossTeamB)

        if (tossDecision == "Bat") toggleDecision?.check(R.id.btnDecisionBat)
        else if (tossDecision == "Field") toggleDecision?.check(R.id.btnDecisionBowl)
    }

    private fun saveTossStateToDraft() {
        val dId = intent.getStringExtra("draftId") ?: return
        val callerId = toggleCallerTeam?.checkedButtonId ?: -1
        val callChoiceId = toggleHeadsTails?.checkedButtonId ?: -1
        val winnerId = toggleWinner?.checkedButtonId ?: -1
        val decisionId = toggleDecision?.checkedButtonId ?: -1

        tossCallerTeam = if (callerId == R.id.btnCallTeamA) teamAName else if (callerId == R.id.btnCallTeamB) teamBName else null
        tossCallerChoice = if (callChoiceId == R.id.btnCallHeads) "Heads" else if (callChoiceId == R.id.btnCallTails) "Tails" else null
        tossResultText = tvCoinResult?.text?.toString()
        tossWinner = if (winnerId == R.id.btnTossTeamA) teamAName else if (winnerId == R.id.btnTossTeamB) teamBName else null
        tossDecision = if (decisionId == R.id.btnDecisionBat) "Bat" else if (decisionId == R.id.btnDecisionBowl) "Field" else null

        val cTeam = tossCallerTeam
        val cChoice = tossCallerChoice
        val resText = tossResultText
        val tWin = tossWinner
        val tDec = tossDecision

        AppDatabase.ioExecutor.execute {
            val db = AppDatabase.getInstance(this)
            val draft = db.draftDao().getDraftById(dId) ?: return@execute
            draft.tossCallerTeam = cTeam
            draft.tossCallerChoice = cChoice
            draft.tossResultText = resText
            draft.tossWinner = tWin
            draft.tossDecision = tDec
            db.draftDao().updateDraft(draft)
        }
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

                saveTossStateToDraft()
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

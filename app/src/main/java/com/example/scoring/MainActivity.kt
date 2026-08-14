package com.example.scoring

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.toColorInt
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.scoring.AppDatabase.Companion.getInstance
import com.example.scoring.RankingRegistry.applyPrestige
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.tabs.TabLayoutMediator.TabConfigurationStrategy
import kotlin.math.floor
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID

class MainActivity : BaseActivity(), ScoringProvider {
    private var scoreText: TextView? = null
    private var oversText: TextView? = null
    private var tvCurrentTeamName: TextView? = null
    private var tvAnalysisLabel: TextView? = null
    private var tvRateValue: TextView? = null
    private var tvRateLabel: TextView? = null
    private var tvFinalResultBanner: TextView? = null
    private var layoutLiveHeader: View? = null
    private var viewPager: ViewPager2? = null
    private var tabLayout: TabLayout? = null

    override var match: Match? = null
        private set
    override var striker: Player? = null
        private set
    override var nonStriker: Player? = null
        private set
    override var bowler: Player? = null
        private set

    private var teamAName: String? = null
    private var teamBName: String? = null
    private var ballType: String? = null
    private var overs = 0
    override var isRuleRunsOnWide: Boolean = false
        private set
    override var isRuleFreeHit: Boolean = false
        private set
    override var isRuleRunsOnBye: Boolean = false
        private set
    override var isRuleOverthrow: Boolean = false
        private set
    private var ruleEveryPlayerBats = false

    override var teamANames: ArrayList<String?>? = null
    override var teamBNames: ArrayList<String?>? = null
    private var teamAPhotos: ArrayList<String?>? = null
    private var teamBPhotos: ArrayList<String?>? = null
    private var teamAIds: ArrayList<String?>? = null
    private var teamBIds: ArrayList<String?>? = null
    private val playerStatCache: MutableMap<String?, Player> = HashMap()
    override val nameToIdMap: MutableMap<String?, String?> = HashMap()
    override val photoMap: MutableMap<String?, String?> = HashMap()
    override val commentary: MutableList<CommentaryEntry?> = ArrayList()

    private var nextBatsmanIdx = 2
    private var teamABatting = true
    override var isFreeHitActive: Boolean = false
        private set
    private var isOverCompleteDialogShowing = false

    private var overRuns = 0
    private var overBowlerRuns = 0
    private var overWickets = 0
    override val overBallsList: MutableList<String?> = ArrayList()
    private var currentBowlerInSpell: String? = null

    private var liveFragment: LiveScoringFragment? = null
    private var scorecardFragment: ScorecardFragment? = null
    private var commentaryFragment: CommentaryFragment? = null
    private var oversFragment: OversFragment? = null
    private var squadFragment: SquadFragment? = null

    private var currentMatchId: String? = null
    private var viewModel: ScoringViewModel? = null
    private var db: AppDatabase? = null
    private var isLiveScoringActive = true
    override var isScorer: Boolean = true
        private set
    override var isFinished: Boolean = false
        private set
    override var isAbandoned: Boolean = false
        private set

    override val pshipRuns: Int
        get() = match?.currentInnings?.currentPshipRuns ?: 0

    override val pshipBalls: Int
        get() = match?.currentInnings?.currentPshipBalls ?: 0

    // CELEBRATION COLORS
    private var colorsWicket: IntArray = intArrayOf()
    private var colorsSix: IntArray = intArrayOf()
    private var colorsFour: IntArray = intArrayOf()
    private var colorsMilestone: IntArray = intArrayOf()
    private var colorsMatchWin: IntArray = intArrayOf()

    override fun onResume() {
        super.onResume()
        if (isLiveScoringActive && match != null && !isFinished && !isAbandoned) {
            val now = System.currentTimeMillis()
            
            // Resume the batting clock for active players
            striker?.let { if (it.entryTime == 0L && !it.isOut) it.entryTime = now }
            nonStriker?.let { if (it.entryTime == 0L && !it.isOut) it.entryTime = now }

            val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val dateStr = sdfDate.format(Date(now)).uppercase()
            val timeStr = sdfTime.format(Date(now))
            val msg = getString(R.string.match_resumed_msg, timeStr, dateStr)
            match?.currentInnings?.addCommentary("FACT", msg, null, "FACT")
            match?.currentInnings?.commentary?.get(0)?.let { commentary.add(0, it) }
        }
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        if (isLiveScoringActive && (match != null)) {
            updateNotOutMins(match?.currentInnings, keepActive = false)

            if (!isFinished && !isAbandoned) {
                val now = System.currentTimeMillis()
                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val dateStr = sdfDate.format(Date(now)).uppercase()
                val timeStr = sdfTime.format(Date(now))
                val msg = getString(R.string.match_paused_msg, timeStr, dateStr)
                match?.currentInnings?.addCommentary("FACT", msg, null, "FACT")
                match?.currentInnings?.commentary?.get(0)?.let { commentary.add(0, it) }
            }
            
            saveMatchToDatabase(isFinished, isAbandoned, isLive = true)
        }
    }

    private fun showExitConfirmationDialog() {
        showDynamicDialog {
            setTitle(getString(R.string.exit_match_title))
            setMessage(getString(R.string.exit_match_msg))
            setPositiveButton("Exit") { _, _ ->
                updateNotOutMins(match?.currentInnings, keepActive = false)
                saveMatchToDatabase(isFinished = false, isAbandoned = false, isLive = true)
                finish()
            }
            setNegativeButton("Cancel", null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        syncToViewModel()
        currentMatchId?.let { outState.putString("matchId", it) }
        outState.putStringArrayList("teamANames", teamANames)
        outState.putStringArrayList("teamBNames", teamBNames)
        outState.putBoolean("isFinished", isFinished)
        outState.putBoolean("isAbandoned", isAbandoned)
        outState.putBoolean("isScorer", isScorer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isLiveScoringActive && !isFinished && !isAbandoned) {
                    showExitConfirmationDialog()
                } else {
                    isEnabled = false
                    this@MainActivity.onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        db = getInstance(this)
        viewModel = ViewModelProvider(this)[ScoringViewModel::class.java]

        initConfettiColors()
        bindUI()
        initData(savedInstanceState)
        setupTabs()
    }

    private fun initConfettiColors() {
        colorsWicket = intArrayOf(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0),
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0)
        )
        colorsSix = intArrayOf(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0),
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary, 0)
        )
        colorsFour = intArrayOf(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary, 0)
        )
        colorsMilestone = intArrayOf(-0x1, -0x100, -0xbb92)
        colorsMatchWin = intArrayOf(-0xff01, -0xff01, -0xff0001, -0xff0100)
    }

    private fun bindUI() {
        scoreText = findViewById(R.id.scoreText)
        oversText = findViewById(R.id.oversText)
        tvCurrentTeamName = findViewById(R.id.tvCurrentTeamName)
        tvAnalysisLabel = findViewById(R.id.tvAnalysisLabel)
        tvRateValue = findViewById(R.id.tvRateValue)
        tvRateLabel = findViewById(R.id.tvRateLabel)
        tvFinalResultBanner = findViewById(R.id.tvFinalResultBanner)
        layoutLiveHeader = findViewById(R.id.layoutLiveHeader)
        viewPager = findViewById(R.id.viewPagerScoring)
        tabLayout = findViewById(R.id.tabLayoutScoring)
    }

    private fun syncToViewModel() {
        viewModel?.let { vm ->
            vm.match.value = match
            vm.striker.value = striker
            vm.nonStriker.value = nonStriker
            vm.bowler.value = bowler
            vm.commentary.value = commentary
            vm.overBalls.value = overBallsList
            vm.nextBatsmanIdx = nextBatsmanIdx
            vm.teamABatting = teamABatting
            vm.isFreeHitActive = isFreeHitActive
            vm.currentBowlerInSpell = currentBowlerInSpell
            vm.overRuns = overRuns
            vm.overBowlerRuns = overBowlerRuns
            vm.overWickets = overWickets
            vm.playerStatCache = playerStatCache

            vm.teamANames = teamANames
            vm.teamBNames = teamBNames
            vm.nameToIdMap = nameToIdMap
            vm.photoMap = photoMap
            vm.isFinished = isFinished
            vm.isAbandoned = isAbandoned
            vm.isScorer = isScorer
        }
    }

    private fun initData(savedInstanceState: Bundle?) {
        // 1. Try restoring from ViewModel (handles config changes like theme/rotation)
        if (savedInstanceState != null && viewModel?.match?.value != null) {
            viewModel?.let { vm ->
                this.match = vm.match.value
                this.striker = vm.striker.value
                this.nonStriker = vm.nonStriker.value
                this.bowler = vm.bowler.value
                
                this.commentary.clear()
                this.commentary.addAll(vm.commentary.value ?: ArrayList())
                
                this.overBallsList.clear()
                this.overBallsList.addAll(vm.overBalls.value ?: ArrayList())
                
                this.nextBatsmanIdx = vm.nextBatsmanIdx
                this.teamABatting = vm.teamABatting
                this.isFreeHitActive = vm.isFreeHitActive
                this.currentBowlerInSpell = vm.currentBowlerInSpell
                this.overRuns = vm.overRuns
                this.overBowlerRuns = vm.overBowlerRuns
                this.overWickets = vm.overWickets
                
                this.playerStatCache.clear()
                this.playerStatCache.putAll(vm.playerStatCache)
                
                this.teamANames = vm.teamANames
                this.teamBNames = vm.teamBNames
                this.nameToIdMap.clear()
                this.nameToIdMap.putAll(vm.nameToIdMap)
                this.photoMap.clear()
                this.photoMap.putAll(vm.photoMap)
                this.isFinished = vm.isFinished
                this.isAbandoned = vm.isAbandoned
                this.isScorer = vm.isScorer

                // Finalize restoration
                this.currentMatchId = savedInstanceState.getString("matchId")
                this.teamAName = match?.teamA
                this.teamBName = match?.teamB
                this.overs = match?.totalOvers ?: 0
                this.isRuleRunsOnWide = match?.ruleRunsOnWide ?: true
                this.isRuleFreeHit = match?.ruleFreeHit ?: true
                this.isRuleRunsOnBye = match?.ruleRunsOnBye ?: true
                this.isRuleOverthrow = match?.ruleOverthrow ?: true
                this.ruleEveryPlayerBats = match?.ruleEveryPlayerBats ?: true

                updateUI()
                return
            }
        }

        // 2. Try restoring from saved state (handles theme/rotation if VM was cleared)
        val intent = intent ?: return
        currentMatchId = intent.getStringExtra("matchId")
        if (currentMatchId != null) {
            resumeMatch(currentMatchId)
            return
        }

        val draftId = intent.getStringExtra("draftId")
        if (draftId != null) {
            loadFromDraft(draftId, intent)
            return
        }

        // Direct intent handling (fallback)
        teamAName = intent.getStringExtra("teamA") ?: "Team A"
        teamBName = intent.getStringExtra("teamB") ?: "Team B"
        ballType = intent.getStringExtra("ballType")
        overs = intent.getIntExtra("overs", 10)
        isRuleRunsOnWide = intent.getBooleanExtra("ruleRunsOnWide", true)
        isRuleFreeHit = intent.getBooleanExtra("ruleFreeHit", true)
        isRuleRunsOnBye = intent.getBooleanExtra("ruleRunsOnBye", true)
        isRuleOverthrow = intent.getBooleanExtra("ruleOverthrow", true)
        ruleEveryPlayerBats = intent.getBooleanExtra("ruleEveryPlayerBats", true)

        teamANames = intent.getStringArrayListExtra("teamANames")
        teamBNames = intent.getStringArrayListExtra("teamBNames")
        teamAPhotos = intent.getStringArrayListExtra("teamAPhotos")
        teamBPhotos = intent.getStringArrayListExtra("teamBPhotos")
        teamAIds = intent.getStringArrayListExtra("teamAIds")
        teamBIds = intent.getStringArrayListExtra("teamBIds")

        completeInitialization(intent)
    }

    private fun loadFromDraft(draftId: String, intent: Intent) {
        AppDatabase.ioExecutor.execute {
            val draft = db?.draftDao()?.getDraftById(draftId)
            runOnUiThread {
                if (draft == null) {
                    finish()
                    return@runOnUiThread
                }
                teamAName = draft.teamAName
                teamBName = draft.teamBName
                overs = draft.overs
                ballType = draft.ballType
                isRuleRunsOnWide = draft.ruleRunsOnWide
                isRuleFreeHit = draft.ruleFreeHit
                isRuleRunsOnBye = draft.ruleRunsOnBye
                isRuleOverthrow = draft.ruleOverthrow
                ruleEveryPlayerBats = draft.ruleEveryPlayerBats

                teamANames = draft.teamANames?.let { names -> ArrayList(names.map { it?.trim() }) }
                teamBNames = draft.teamBNames?.let { names -> ArrayList(names.map { it?.trim() }) }
                teamAPhotos = draft.teamAPhotos?.let { ArrayList(it) }
                teamBPhotos = draft.teamBPhotos?.let { ArrayList(it) }
                teamAIds = draft.teamAIds?.let { ArrayList(it) }
                teamBIds = draft.teamBIds?.let { ArrayList(it) }

                completeInitialization(intent)
            }
        }
    }

    private fun completeInitialization(intent: Intent) {
        // Ensure names are not null and trimmed
        val currentA = ArrayList(teamANames?.mapNotNull { it?.trim() } ?: emptyList())
        val currentB = ArrayList(teamBNames?.mapNotNull { it?.trim() } ?: emptyList())
        teamANames = currentA
        teamBNames = currentB

        // Build mappings
        teamAIds?.let { ids ->
            for (i in currentA.indices) {
                val name = currentA[i]
                if (name != null && i < ids.size) nameToIdMap[name] = ids[i]
            }
        }
        teamAPhotos?.let { photos ->
            for (i in currentA.indices) {
                val name = currentA[i]
                if (name != null && i < photos.size) photoMap[name] = photos[i]
            }
        }
        teamBIds?.let { ids ->
            for (i in currentB.indices) {
                val name = currentB[i]
                if (name != null && i < ids.size) nameToIdMap[name] = ids[i]
            }
        }
        teamBPhotos?.let { photos ->
            for (i in currentB.indices) {
                val name = currentB[i]
                if (name != null && i < photos.size) photoMap[name] = photos[i]
            }
        }

        val aSize = currentA.size.coerceAtLeast(1)
        val bSize = currentB.size.coerceAtLeast(1)

        match = Match(teamAName, teamBName, overs, aSize, bSize).apply {
            this.ballType = this@MainActivity.ballType
            ruleRunsOnWide = isRuleRunsOnWide
            ruleFreeHit = isRuleFreeHit
            ruleRunsOnBye = isRuleRunsOnBye
            ruleOverthrow = isRuleOverthrow
            ruleEveryPlayerBats = this@MainActivity.ruleEveryPlayerBats
        }
        
        viewModel?.match?.value = match

        val tossWinner = intent.getStringExtra("tossWinner")
        val tossDecision = intent.getStringExtra("tossDecision")
        match?.tossWinner = tossWinner
        match?.tossDecision = tossDecision

        teamABatting = if (tossWinner == teamAName) {
            tossDecision == "Bat"
        } else {
            tossDecision == "Field"
        }

        if (teamABatting) {
            match?.startFirstInnings(teamAName ?: "Red Team", teamBName)
        } else {
            match?.startFirstInnings(teamBName ?: "Blue Team", teamAName)
        }

        val strikerName = intent.getStringExtra("strikerName")
        val nonStrikerName = intent.getStringExtra("nonStrikerName")
        val bowlerName = intent.getStringExtra("bowlerName")

        strikerName?.let { striker = getPlayerFromCache(it) }
        nonStrikerName?.let { nonStriker = getPlayerFromCache(it) }
        bowlerName?.let { bowler = getPlayerFromCache(it) }

        if (striker == null || bowler == null) {
            initPlayers()
        } else {
            // Hard fix: Ensure initial players have entry time set
            val now = System.currentTimeMillis()
            striker?.let { if (it.entryTime == 0L) it.entryTime = now }
            nonStriker?.let { if (it.entryTime == 0L) it.entryTime = now }
        }

        val now = System.currentTimeMillis()
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dateStr = sdfDate.format(Date(now)).uppercase()
        val timeStr = sdfTime.format(Date(now))
        
        val startMsg = "Match Started at $timeStr on $dateStr"
        match?.currentInnings?.addCommentary("0.0", startMsg, null, "FACT")
        
        match?.currentInnings?.addCommentary("0.0", "Here is the squad:", null, "FACT")
        val teamAStr = "${match?.teamA}: ${currentA.asSequence().filterNotNull().joinToString(", ")}"
        val teamBStr = "${match?.teamB}: ${currentB.asSequence().filterNotNull().joinToString(", ")}"
        match?.currentInnings?.addCommentary("0.0", teamAStr, null, "FACT")
        match?.currentInnings?.addCommentary("0.0", teamBStr, null, "FACT")

        val tossMsg = "Toss: ${match?.tossWinner} won the toss and opted to ${match?.tossDecision}"
        match?.currentInnings?.addCommentary("0.0", tossMsg, null, "FACT")
        
        match?.currentInnings?.addCommentary("0.0", "FIRST INNINGS", null, "FACT")
        
        val introMsg = if (nonStriker != null) {
            "${striker?.name} and ${nonStriker?.name} are at the crease. ${striker?.name} is on strike. ${bowler?.name} will open the attack"
        } else {
            "${striker?.name} is at the crease and will be on strike. ${bowler?.name} will open the attack"
        }
        match?.currentInnings?.addCommentary("0.0", introMsg, null, "FACT")
        currentBowlerInSpell = bowler?.name

        // Copy everything to activity's local commentary list
        match?.currentInnings?.commentary?.let { 
            commentary.clear()
            commentary.addAll(it) 
        }

        updateUI()
    }

    private fun syncUnifiedCommentary() {
        val m = match ?: return
        commentary.clear()
        
        // Add 2nd innings first (most recent)
        m.secondInnings?.let { commentary.addAll(it.commentary.filterNotNull()) }
        
        // Add 1st innings
        m.firstInnings?.let { commentary.addAll(it.commentary.filterNotNull()) }
        
        recalculateMatchState()
    }

    private fun resumeMatch(matchId: String?) {
        AppDatabase.ioExecutor.execute {
            val me = db!!.matchDao().getMatchById(matchId)
            if (me == null) {
                runOnUiThread {
                    Toast.makeText(this, "Match not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@execute
            }

            val m = me.toMatch()
            val stats = db!!.statsDao().getStatsByMatch(matchId) ?: emptyList()

            runOnUiThread {
                this.match = m
                this.teamAName = m.teamA
                this.teamBName = m.teamB
                this.overs = m.totalOvers
                this.isRuleRunsOnWide = m.ruleRunsOnWide
                this.isRuleFreeHit = m.ruleFreeHit
                this.isRuleRunsOnBye = m.ruleRunsOnBye
                this.isRuleOverthrow = m.ruleOverthrow
                this.ruleEveryPlayerBats = m.ruleEveryPlayerBats
                this.isFinished = me.isFinished
                this.isAbandoned = me.isAbandoned
                this.ballType = m.ballType

                teamANames = ArrayList(me.teamANames?.mapNotNull { it?.trim() } ?: emptyList())
                teamBNames = ArrayList(me.teamBNames?.mapNotNull { it?.trim() } ?: emptyList())
                photoMap.putAll(me.photoMap?.filterKeys { it != null }?.map { it.key!!.trim() to it.value }?.toMap() ?: emptyMap())
                nameToIdMap.putAll(me.nameToIdMap?.filterKeys { it != null }?.map { it.key!!.trim() to it.value }?.toMap() ?: emptyMap())

                this.playerStatCache.clear()
                for (s in stats) {
                    if (s == null) continue
                    val p = s.toPlayer()
                
                // Restore entry time from database to keep the clock running correctly
                if (p.name?.trim() == me.currentStrikerName?.trim()) {
                    p.entryTime = me.strikerEntryTime
                } else if (p.name?.trim() == me.currentNonStrikerName?.trim()) {
                    p.entryTime = me.nonStrikerEntryTime
                } else {
                    p.entryTime = 0
                }
                            // Safety check for dismissal info restoration
                    if (p.isOut && (p.dismissalInfo == "not out" || p.dismissalInfo.isNullOrEmpty())) {
                        p.dismissalInfo = "out"
                    }
                    
                    p.name?.trim()?.let { playerStatCache[it] = p }
                }

                val firstTeam = m.firstInnings?.battingTeam ?: m.teamA
                teamABatting = (firstTeam == m.teamA)
                m.secondInnings?.let { teamABatting = (it.battingTeam == m.teamA) }

                val current = m.currentInnings

                syncUnifiedCommentary()

                // Restore active state
                this.nextBatsmanIdx = me.nextBatsmanIdx
                this.isFreeHitActive = me.isFreeHitActive
                this.currentBowlerInSpell = me.currentBowlerInSpell

                if (me.currentStrikerName != null) {
                    striker = getPlayerFromCache(me.currentStrikerName)
                    nonStriker = if (me.currentNonStrikerName != null) getPlayerFromCache(me.currentNonStrikerName) else null
                    bowler = if (me.currentBowlerName != null) getPlayerFromCache(me.currentBowlerName) else null
                } else if (current != null && current.balls.isNotEmpty()) {
                    val last = current.balls[current.balls.size - 1]
                    striker = getPlayerFromCache(last.batsmanName)
                    bowler = getPlayerFromCache(last.bowlerName)

                    for (i in current.balls.indices.reversed()) {
                        val b = current.balls[i]
                        if (b.batsmanName != null && b.batsmanName != last.batsmanName) {
                            nonStriker = getPlayerFromCache(b.batsmanName)
                            break
                        }
                    }
                }

                if (striker == null || bowler == null) {
                    initPlayers()
                } else {
                    // Update entry times to 'now' to resume the clock correctly for ACTIVE players only
                    val now = System.currentTimeMillis()
                    if (current != null && !current.isComplete) {
                        striker?.entryTime = now
                        nonStriker?.entryTime = now
                    }
                }

                reconstructCurrentOverState()
                recalculateMatchState()
                updateUI()
            }
        }
    }

    private fun reconstructCurrentOverState(showLastCompletedIfBoundary: Boolean = false) {
        val innings = match?.currentInnings ?: return
        overBallsList.clear()
        overRuns = 0
        overWickets = 0
        overBowlerRuns = 0

        val totalLegalBalls = innings.legalBalls
        val legalBallsInOver = totalLegalBalls % 6
        
        // How many legal balls to skip from the beginning of match to reach the start of THIS over?
        val legalBallsToSkip = if (legalBallsInOver == 0 && showLastCompletedIfBoundary) {
            (totalLegalBalls - 6).coerceAtLeast(0)
        } else {
            totalLegalBalls - legalBallsInOver
        }

        val targetLegalCount = if (legalBallsInOver == 0 && showLastCompletedIfBoundary) 6 else legalBallsInOver

        var legalBallsFound = 0
        var skipCount = 0
        val currentOverBalls = mutableListOf<Ball>()

        for (b in innings.balls) {
            val isLegal = (b.type == BallType.NORMAL || b.type == BallType.BYE || b.type == BallType.LEG_BYE)
            
            if (skipCount < legalBallsToSkip) {
                if (isLegal) skipCount++
                continue
            }
            
            // Now we are in the balls for the current/target over
            currentOverBalls.add(b)
            if (isLegal) legalBallsFound++
            
            if (targetLegalCount > 0 && legalBallsFound == targetLegalCount) {
                break
            }
            
            // If targetLegalCount is 0, we only want trailing extras
            if (targetLegalCount == 0 && isLegal) {
                currentOverBalls.removeAt(currentOverBalls.size - 1)
                break
            }
        }

        // Calculate stats and display strings for the reconstructed over
        currentOverBalls.forEach { b ->
            overRuns += b.runs
            if (b.type == BallType.NORMAL || b.type == BallType.WIDE || b.type == BallType.NO_BALL) {
                overBowlerRuns += b.runs
            }
            if (b.isWicket) overWickets++
            
            val widePenalty = if (isRuleRunsOnWide) 1 else 0
            val nbPenalty = if (isRuleRunsOnWide) 1 else 0
            val event = when {
                b.isWicket -> {
                    val runsRan = when (b.type) {
                        BallType.WIDE -> b.runs - widePenalty
                        BallType.NO_BALL -> b.runs - nbPenalty
                        else -> b.runs
                    }
                    if (runsRan <= 0) "W" else "${runsRan}W"
                }
                b.type == BallType.WIDE -> {
                    val runsRan = b.runs - widePenalty
                    if (runsRan <= 0) "WD" else "${runsRan}WD"
                }
                b.type == BallType.NO_BALL -> {
                    val runsRan = b.runs - nbPenalty
                    if (runsRan <= 0) "NB" else "${runsRan}NB"
                }
                else -> b.runs.toString()
            }
            overBallsList.add(event)
        }
    }

    private fun setupTabs() {
        val adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> MatchInfoFragment()
                    1 -> LiveScoringFragment().also { liveFragment = it }
                    2 -> ScorecardFragment().also { scorecardFragment = it }
                    3 -> CommentaryFragment().also { commentaryFragment = it }
                    4 -> OversFragment().also { oversFragment = it }
                    else -> SquadFragment().also { squadFragment = it }
                }
            }
            override fun getItemCount(): Int = 6
        }

        viewPager?.adapter = adapter
        val tabs = tabLayout ?: return
        val pager = viewPager ?: return
        
        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = when (position) {
                0 -> "Info"
                1 -> "Live"
                2 -> "Scorecard"
                3 -> "Commentary"
                4 -> "Overs"
                else -> "Squad"
            }
        }.attach()
        
        // Make "Live" (index 1) the default tab
        viewPager?.setCurrentItem(1, false)
    }

    override fun getPlayerFromCache(name: String?): Player? {
        if (name == null) return null
        val trimmedName = name.trim()
        if (!playerStatCache.containsKey(trimmedName)) {
            val p = Player(trimmedName)
            p.id = nameToIdMap[trimmedName]
            playerStatCache[trimmedName] = p
        }
        return playerStatCache[trimmedName]
    }

    private fun initPlayers() {
        val battingList = if (teamABatting) teamANames else teamBNames
        val bowlingList = if (teamABatting) teamBNames else teamANames
        striker = getPlayerFromCache(getSafe(battingList, 0, "Player 1"))
        val nsName = getSafe(battingList, 1, null)
        nonStriker = if (nsName != null) getPlayerFromCache(nsName) else null
        bowler = getPlayerFromCache(getSafe(bowlingList, 0, "Bowler 1"))
        nextBatsmanIdx = 2

        val startTime = System.currentTimeMillis()
        striker?.let { if (it.entryTime == 0L) it.entryTime = startTime }
        nonStriker?.let { if (it.entryTime == 0L) it.entryTime = startTime }
    }

    private fun getSafe(list: List<String?>?, index: Int, default: String?): String? {
        if (list == null || index < 0 || index >= list.size) return default
        return list[index] ?: default
    }

    override fun playBall(runs: Int, type: BallType?, isWicket: Boolean) {
        val batRuns = if (type == null || type == BallType.NORMAL) runs else 0
        playBallWithPlayer(runs, type ?: BallType.NORMAL, isWicket, null, null, batRuns)
    }

    private fun playBallWithPlayer(
        runs: Int,
        type: BallType,
        isWicket: Boolean,
        facingBatsman: Player?,
        fielder: String?,
        manualBatRuns: Int,
        outPlayer: Player? = null,
        isStrikerReplacingOverride: Boolean? = null
    ) {
        if (isOverCompleteDialogShowing) return
        val innings = match?.currentInnings
        if (innings == null || innings.isComplete) return

        val batsmanForRecord = facingBatsman ?: striker
        if (batsmanForRecord == null || bowler == null) return

        // Identify the "other" batsman at the crease relative to the one who faced the ball.
        // This is necessary because if a swap happened immediately (on odd runs),
        // the activity's 'nonStriker' might now be the person who faced the ball.
        val currentNonStrikerForRecord = if (batsmanForRecord == striker) nonStriker else striker

        val actualOut = outPlayer ?: batsmanForRecord
        bowler?.let { b ->
            innings.addBall(runs, type, isWicket, batsmanForRecord, b, fielder, manualBatRuns, outPlayer, currentNonStrikerForRecord)
        } ?: return

        if (isWicket && fielder != null) {
            val fielders = fielder.split(" / ")
            fielders.forEach { fName ->
                val f = getPlayerFromCache(fName.trim())
                if (f != null) {
                    val dInfo = actualOut.dismissalInfo
                    if (dInfo.contains("Caught", ignoreCase = true) || dInfo.startsWith("c ", ignoreCase = true) || dInfo.contains("c & b", ignoreCase = true)) {
                        f.catches++
                    } else if (dInfo.contains("Stumped", ignoreCase = true) || dInfo.startsWith("st ", ignoreCase = true)) {
                        f.stumpings++
                    } else if (dInfo.contains("Run Out", ignoreCase = true)) {
                        f.runOuts++
                    }
                }
            }
        }

        // Bowling Stats Sync
        if (runs == 6 && (type == BallType.NORMAL || type == BallType.NO_BALL)) {
            bowler?.sixesConceded = (bowler?.sixesConceded ?: 0) + 1
        }

        // Check for new bowler attack message
        if (bowler?.name != currentBowlerInSpell) {
            currentBowlerInSpell = bowler?.name
            val attackMsg = "$currentBowlerInSpell comes to the attack"
            innings.addCommentary(innings.oversDisplay, attackMsg, null, "FACT")
            innings.commentary[0]?.let { commentary.add(0, it) }
        }

        val event: String = when {
            isWicket -> {
                val widePenalty = if (isRuleRunsOnWide) 1 else 0
                val nbPenalty = if (isRuleRunsOnWide) 1 else 0
                val runsRan = when (type) {
                    BallType.WIDE -> runs - widePenalty
                    BallType.NO_BALL -> runs - nbPenalty
                    else -> runs
                }
                if (runsRan <= 0) "W" else "${runsRan}W"
            }
            type == BallType.BYE -> {
                if (runs == 0) "." else "${runs}B"
            }
            type == BallType.LEG_BYE -> {
                if (runs == 0) "." else "${runs}LB"
            }
            type == BallType.WIDE -> {
                val runsRan = runs - (if (isRuleRunsOnWide) 1 else 0)
                if (runsRan <= 0) "WD" else "${runsRan}WD"
            }
            type == BallType.NO_BALL -> {
                val nbPenalty = if (isRuleRunsOnWide) 1 else 0
                val runsRanOnNB = runs - nbPenalty
                if (runsRanOnNB <= 0) "NB" else "${runsRanOnNB}NB"
            }
            type == BallType.PENALTY -> "P$runs"
            else -> runs.toString()
        }

        val bowlerName = bowler?.name ?: "Bowler"
        val batsmanName = batsmanForRecord.name ?: "Batsman"

        val desc = when {
            isWicket -> {
                val prefix = when (type) {
                    BallType.BYE -> if (runs > 0) "$runs BYES, " else "BYES, "
                    BallType.LEG_BYE -> if (runs > 0) "$runs LEG BYES, " else "LEG BYES, "
                    BallType.WIDE -> {
                        val runsRan = runs - (if (isRuleRunsOnWide) 1 else 0)
                        if (runsRan == 0) "WIDE, " else "$runsRan WIDE, "
                    }
                    BallType.NO_BALL -> {
                        val nbPenalty = if (isRuleRunsOnWide) 1 else 0
                        val runsRan = runs - nbPenalty
                        if (runsRan <= 0) "1 NO BALL, " else "$runsRan NO BALL, "
                    }
                    else -> ""
                }

                val info = actualOut.dismissalInfo
                val dismissalDesc = when {
                    info.startsWith("b ") -> "BOWLED OUT"
                    info.startsWith("c ") -> {
                        val f = info.substringAfter("c ").substringBefore(" b ")
                        if (info.startsWith("c & b")) "CAUGHT OUT, Caught by $bowlerName"
                        else "CAUGHT OUT, Caught by $f"
                    }
                    info.contains("Run Out") -> {
                        val detail = info.substringAfter("(").substringBefore(")")
                        if (detail.contains("/")) {
                            val t = detail.substringBefore("/")
                            val s = detail.substringAfter("/")
                            "RUN OUT, $t threw the ball and $s did the job"
                        } else {
                            "RUN OUT, Run Out by $detail"
                        }
                    }
                    info.contains("Hit Out Of The Ground") -> "OUT, HIT OUT OF THE GROUND"
                    info.startsWith("st ") -> {
                        val k = info.substringAfter("st ").substringBefore(" b ")
                        "STUMPED OUT, Stumped by $k"
                    }
                    info.startsWith("LBW") -> "OUT, LBW"
                    info.startsWith("Hit Wicket") -> "OUT, HIT WICKET"
                    info.contains("Obstructed") -> "OUT, Obstructing the Field, ${outPlayer?.name ?: batsmanName} obstructed the field"
                    else -> info
                }
                prefix + dismissalDesc
            }
            type == BallType.WIDE -> {
                val runsRan = runs - (if (isRuleRunsOnWide) 1 else 0)
                if (runsRan == 0) "WIDE" else "$runsRan WIDES"
            }
            type == BallType.NO_BALL -> {
                val nbPenalty = if (isRuleRunsOnWide) 1 else 0
                if (manualBatRuns > 0) "$manualBatRuns NO BALL"
                else if (runs > nbPenalty) "${runs - nbPenalty} NO BALL"
                else "NO BALL"
            }
            type == BallType.BYE -> {
                if (runs > 0) "BYES, $runs" else "No Run"
            }
            type == BallType.LEG_BYE -> {
                if (runs > 0) "LEG BYES, $runs" else "No Run"
            }
            type == BallType.PENALTY -> "PENALTY EXTRAS"
            runs == 6 -> "SIX"
            runs == 4 -> "FOUR"
            runs == 1 -> "1 Run"
            runs == 0 -> "No Run"
            else -> "$runs Run(s)"
        }

        val comment = "$bowlerName to $batsmanName, $desc"
        val badgeText = when {
            isWicket -> {
                val widePenalty = if (isRuleRunsOnWide) 1 else 0
                val nbPenalty = if (isRuleRunsOnWide) 1 else 0
                val runsRan = when (type) {
                    BallType.WIDE -> runs - widePenalty
                    BallType.NO_BALL -> runs - nbPenalty
                    else -> runs
                }
                if (runsRan <= 0) "W" else "${runsRan}W"
            }
            type == BallType.BYE -> {
                if (runs == 0) "." else "${runs}B"
            }
            type == BallType.LEG_BYE -> {
                if (runs == 0) "." else "${runs}LB"
            }
            type == BallType.WIDE -> {
                val runsRan = runs - (if (isRuleRunsOnWide) 1 else 0)
                if (runsRan <= 0) "WD" else "${runsRan}WD"
            }
            type == BallType.NO_BALL -> {
                val nbPenalty = if (isRuleRunsOnWide) 1 else 0
                val runsRanOnNB = runs - nbPenalty
                if (runsRanOnNB <= 0) "NB" else "${runsRanOnNB}NB"
            }
            type == BallType.PENALTY -> "P$runs"
            else -> runs.toString()
        }

        innings.addCommentary(innings.oversDisplay, comment, badgeText, "BALL")
        innings.commentary.firstOrNull()?.let { commentary.add(0, it) }
        overBallsList.add(event)
        overRuns += runs
        if (type == BallType.NORMAL || type == BallType.WIDE || type == BallType.NO_BALL) {
            overBowlerRuns += runs
        }
        if (isWicket) overWickets++

        recalculateMatchState()

        // Team Milestones (50, 100, 150...)
        val total = innings.totalRuns
        val prevTotal = total - runs
        if (total >= 50) {
            val milestone = (total / 50) * 50
            if (prevTotal < milestone) {
                triggerConfetti(colorsMilestone)
            }
        }

        // Handle Second Innings Target Reminders (50%, 80%, 95%)
        if (innings === match?.secondInnings) {
            val totalOvers = match?.totalOvers ?: 20
            val totalBalls = totalOvers * 6
            val currentBalls = innings.legalBalls
            val trigger50 = (totalBalls * 0.5).toInt()
            val trigger80 = (totalBalls * 0.8).toInt()
            val trigger95 = (totalBalls * 0.95).toInt()

            if ((currentBalls == trigger50 || currentBalls == trigger80 || currentBalls == trigger95) && currentBalls > 0) {
                val target = match?.target ?: 0
                val needed = target - innings.totalRuns
                val ballsLeft = totalBalls - currentBalls
                val wicketsLeft = innings.maxWickets - innings.totalWickets
                if (needed > 0 && ballsLeft >= 0) {
                    val reminder = "Target: $needed Runs in $ballsLeft Balls with $wicketsLeft Wickets remaining"
                    innings.addCommentary(innings.oversDisplay, reminder, null, "FACT")
                    innings.commentary.getOrNull(0)?.let { commentary.add(0, it) }
                }
            }
        }

        if (!isWicket && type == BallType.NORMAL) {
            if (runs % 2 != 0) swapBatsmen()
        }
        // WIDE and NO_BALL swap is now handled immediately when runs are confirmed
        // to ensure wicket dialogs reflect the correct player positions.

        if (isWicket) {
            playLottie("wicket")
            triggerConfetti(colorsWicket)
            
            // Check for Hat-tricks and Wicket Hauls
            val bowlerObj = bowler
            if (bowlerObj != null) {
                val w = bowlerObj.wicketsTaken
                if (w == 3 || w == 5) {
                    val haulMsg = "$w WICKET HAUL for ${bowlerObj.name}!"
                    innings.addCommentary("FACT", haulMsg, "W", "FACT")
                    innings.commentary.firstOrNull()?.let { commentary.add(0, it) }
                    triggerConfetti(colorsMatchWin)
                }
                
                // Hat-ttrick check (3 consecutive bowler-credited wickets)
                var streak = 0
                var bowlerBallsCount = 0
                var fourthBallWasWicket = false
                for (i in innings.balls.indices.reversed()) {
                    val b = innings.balls[i]
                    if (b.bowlerName == bowlerObj.name) {
                        bowlerBallsCount++
                        val isBowlerWicket = b.isWicket && 
                                            !b.dismissalInfo.toString().contains("Run Out", ignoreCase = true) &&
                                            !b.dismissalInfo.toString().contains("Obstruct", ignoreCase = true)
                        
                        if (bowlerBallsCount <= 3) {
                            if (isBowlerWicket) {
                                streak++
                            } else {
                                break
                            }
                        } else {
                            fourthBallWasWicket = isBowlerWicket
                            break
                        }
                    }
                }
                if (streak == 3 && !fourthBallWasWicket) {
                    bowlerObj.hattricks++
                    val hatMsg = "!!! HAT-TRICK !!! ${bowlerObj.name} is on fire!"
                    innings.addCommentary("FACT", hatMsg, "W", "FACT")
                    innings.commentary.firstOrNull()?.let { commentary.add(0, it) }
                    triggerConfetti(colorsMatchWin)
                }
            }
        } else if (runs == 6) {
            playLottie("six")
            triggerConfetti(colorsSix)
        } else if (runs == 4) {
            playLottie("four")
            triggerConfetti(colorsFour)
        }

        // Batting Milestones (30, 50, 80, 100)
        if (!isWicket) {
            val r = batsmanForRecord.runsScored
            val prevR = r - runs
            val milestone = when {
                r >= 100 && prevR < 100 -> "HUNDRED! A magnificent century for ${batsmanForRecord.name}!" to "H"
                r >= 80 && prevR < 80 -> "Superb innings! 80 up for ${batsmanForRecord.name}!" to "E"
                r >= 50 && prevR < 50 -> "HALF-CENTURY! Well played, ${batsmanForRecord.name}!" to "F"
                r >= 30 && prevR < 30 -> "Solid 30! ${batsmanForRecord.name} is looking good." to "T"
                else -> null
            }
            milestone?.let { (msg, tag) ->
                innings.addCommentary("FACT", msg, tag, "FACT")
                innings.commentary.firstOrNull()?.let { commentary.add(0, it) }
                triggerConfetti(colorsMilestone)
            }
        }

        isFreeHitActive = (type == BallType.NO_BALL)

        val isOverComplete = innings.legalBalls % 6 == 0 && (type == BallType.NORMAL || type == BallType.BYE || type == BallType.LEG_BYE)

        if (isOverComplete) {
            handleOverCompletion(innings, !innings.isComplete)
        }
        
        val isInningsComplete = checkInningsCompletion(innings)

        if (!isInningsComplete) {
            if (isWicket && !isAbandoned) {
                val isStrikerOut = isStrikerReplacingOverride ?: ((outPlayer ?: facingBatsman ?: striker) == striker)
                showBatsmanSelectionDialog(isStrikerOut)
            }
        }

        updateUI()
        saveMatchToDatabase(isFinished, isAbandoned, true)
    }

    private fun swapBatsmen() {
        if (nonStriker == null) return
        val temp = striker
        striker = nonStriker
        nonStriker = temp
    }

    private fun recalculateMatchState() {
        syncToViewModel()
        viewModel?.let {
            it.updateScore(match?.currentInnings?.totalRuns ?: 0, match?.currentInnings?.totalWickets ?: 0)
            it.updateCommentary(commentary)
            it.updateOverBalls(overBallsList)
        }
    }

    private fun checkInningsCompletion(innings: Innings): Boolean {
        if (innings.isComplete) {
            isFreeHitActive = false // Reset status on completion
            updateNotOutMins(innings, keepActive = false)
            if (innings === match?.firstInnings) {
                match?.firstInningsEndTime = System.currentTimeMillis()
                showFirstInningsCompleteDialog(innings)
            } else {
                isFinished = true
                match?.secondInningsEndTime = System.currentTimeMillis()
                showMatchFinishedDialog()
            }
            return true
        }
        return false
    }

    private fun updateNotOutMins(innings: Innings?, keepActive: Boolean = false) {
        if (innings == null) return
        val now = System.currentTimeMillis()
        
        // Helper to update a single player
        fun updatePlayerTime(p: Player?) {
            if (p == null || p.entryTime == 0L) return
            p.minutesPlayed += ((now - p.entryTime) / 1000).toInt()
            p.entryTime = if (keepActive) now else 0L
        }

        updatePlayerTime(striker)
        updatePlayerTime(nonStriker)
        
        // Force update any other player in these innings who might have an entryTime
        val teamNames = if (innings.battingTeam == teamAName) teamANames else teamBNames
        teamNames?.forEach { name ->
            val p = getPlayerFromCache(name)
            if (p != null && p != striker && p != nonStriker) {
                updatePlayerTime(p)
            }
        }
    }

    private fun handleOverCompletion(innings: Innings, showDialog: Boolean) {
        updateNotOutMins(innings, keepActive = true)
        val overNum = innings.legalBalls / 6
        val matchScore = "${innings.totalRuns}-${innings.totalWickets}"

        val sbSummaryBalls = StringBuilder()
        overBallsList.forEach { b -> sbSummaryBalls.append(b).append(" ") }
        val ballListStrRecon = sbSummaryBalls.toString().trim()

        var isMaiden = false
        if (this.overBowlerRuns == 0) {
            isMaiden = true
            
            // Only credit individual stats and show personal commentary if NOT a shared over
            if (match?.isSharedOver == false && bowler != null) {
                bowler?.let { b ->
                    b.maidens++
                    val maidenMsg = "MAIDEN OVER! Splendid bowling by ${b.name}."
                    innings.addCommentary("FACT", maidenMsg, null, "FACT")
                    innings.commentary.firstOrNull()?.let { commentary.add(0, it) }
                }
            }
        }

        val encodedText = StringBuilder()
            .append(overNum).append("|")
            .append(matchScore).append("|")
            .append(ballListStrRecon).append("|")
            .append(striker?.name ?: "-").append("|")
            .append(striker?.runsScored ?: 0).append("|")
            .append(striker?.ballsFaced ?: 0).append("|")
            .append(nonStriker?.name ?: "-").append("|")
            .append(nonStriker?.runsScored ?: 0).append("|")
            .append(nonStriker?.ballsFaced ?: 0).append("|")
            .append(bowler?.name ?: "-").append("|")
            .append(bowler?.oversBowledDisplay ?: "0.0").append("|")
            .append(bowler?.maidens ?: 0).append("|")
            .append(bowler?.runsConceded ?: 0).append("|")
            .append(bowler?.wicketsTaken ?: 0).append("|")
            .append(overRuns).append("|")
            .append(if (isMaiden) "1" else "0")
            .toString()

        innings.addCommentary(innings.oversDisplay, encodedText, null, "OVER_SUMMARY")
        innings.commentary.firstOrNull()?.let { commentary.add(0, it) }

        if (!showDialog) return

        isOverCompleteDialogShowing = true
        
        val view = layoutInflater.inflate(R.layout.dialog_over_summary, null)
        view.findViewById<TextView>(R.id.tvOverLabel).text = getString(R.string.over_label, overNum)
        view.findViewById<TextView>(R.id.tvScoreLabel).text = matchScore
        view.findViewById<TextView>(R.id.tvOverRuns).text = overRuns.toString()
        
        if (isMaiden) {
            view.findViewById<View>(R.id.tvMaidenBadge).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvOverLabel).setTextColor("#B71C1C".toColorInt())
        }

        val ballsContainer = view.findViewById<LinearLayout>(R.id.layoutBallsContainer)
        overBallsList.forEach { b ->
            if (b != null) {
                ballsContainer.addView(createCircleBallView(this, b, false))
            }
        }

        val battersContainer = view.findViewById<LinearLayout>(R.id.layoutBatters)
        fun addRow(p: Player?) {
            if (p == null) return
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
            }
            val name = TextView(this).apply {
                text = p.name
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val stats = TextView(this).apply {
                text = getString(R.string.batter_stats_format, p.runsScored, p.ballsFaced)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            }
            row.addView(name)
            row.addView(stats)
            battersContainer.addView(row)
        }
        addRow(striker)
        addRow(nonStriker)

        view.findViewById<TextView>(R.id.tvBowlerName).text = bowler?.name
        view.findViewById<TextView>(R.id.tvBowlerStats).text = "${bowler?.oversBowledDisplay}-${bowler?.maidens}-${bowler?.runsConceded}-${bowler?.wicketsTaken}"

        showDynamicDialog {
            setView(view)
            setCancelable(false)
            setPositiveButton(getString(R.string.next_over)) { _, _ ->
                isOverCompleteDialogShowing = false
                overRuns = 0
                overBowlerRuns = 0
                overWickets = 0
                overBallsList.clear()
                swapBatsmen()
                if (!isFinished && !isAbandoned) {
                    val bowlingTeam = if (teamABatting) teamBNames else teamANames
                    if (bowlingTeam != null && bowlingTeam.filterNotNull().size > 1) {
                        showBowlerSelectionDialog(null, false)
                    } else {
                        updateUI()
                    }
                }
            }
        }
    }

    private fun showFirstInningsCompleteDialog(innings: Innings) {
        val score = "${innings.totalRuns}/${innings.totalWickets}"
        val oversStr = innings.oversDisplay
        val teamName = innings.battingTeam ?: "Batting Team"
        val scoreMsg = "$teamName scored $score Runs in $oversStr Overs"
        
        val matchOvers = match?.totalOvers ?: 0
        val targetRuns = match?.target ?: 0
        val totalBalls = matchOvers * 6
        val targetMsg = "Target: $targetRuns Runs in $totalBalls Balls"
        
        innings.addCommentary(innings.oversDisplay, scoreMsg, null, "FACT")
        innings.addCommentary(innings.oversDisplay, targetMsg, null, "FACT")
        syncUnifiedCommentary()

        val view = layoutInflater.inflate(R.layout.dialog_innings_complete, null)
        view.findViewById<TextView>(R.id.tvInningsCompleteScore).text = scoreMsg
        view.findViewById<TextView>(R.id.tvInningsCompleteTarget).text = targetMsg

        val dialog = ThemeManager.createDynamicBuilder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<View>(R.id.btnNextInningsDialog).setOnClickListener {
            dialog.dismiss()
            startNextInnings()
        }
        view.findViewById<View>(R.id.btnDismissDialog).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        ThemeManager.colorizeDialog(dialog)
    }

    private fun showMatchFinishedDialog() {
        match?.secondInningsEndTime = System.currentTimeMillis()
        val result = calculateMatchResult()?.uppercase() ?: "MATCH FINISHED"
        tvFinalResultBanner?.text = result
        tvFinalResultBanner?.visibility = View.VISIBLE

        val curInn = match?.currentInnings
        curInn?.addCommentary(curInn.oversDisplay, "MATCH OVER", null, "FACT")
        curInn?.addCommentary(curInn.oversDisplay, result, null, "FACT")

        val now = System.currentTimeMillis()
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dateStr = sdfDate.format(Date(now)).uppercase()
        val timeStr = sdfTime.format(Date(now))
        val endMsg = "Match Ended at $timeStr on $dateStr"
        curInn?.addCommentary(curInn.oversDisplay, endMsg, null, "FACT")
        
        syncUnifiedCommentary()

        val view = layoutInflater.inflate(R.layout.dialog_match_finished, null)
        val tvResult = view.findViewById<TextView>(R.id.tvMatchFinishedResult)
        val btnToast = view.findViewById<MaterialButton>(R.id.btnToastWinner)
        
        tvResult.text = result

        // Determine Toast button text
        val btnText = when {
            result.contains("WON BY") -> {
                val winningTeam = result.substringBefore(" WON BY").trim()
                "TOAST FOR $winningTeam"
            }
            result.contains("MATCH TIED") -> "TOAST FOR BOTH TEAMS"
            result.contains("ABANDONED") -> "DISMISS"
            else -> "DISMISS"
        }
        btnToast.text = btnText

        val dialog = ThemeManager.createDynamicBuilder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        btnToast.setOnClickListener {
            if (btnText.startsWith("TOAST")) {
                triggerConfetti(colorsMatchWin)
            }
            dialog.dismiss()
        }

        dialog.show()
        ThemeManager.colorizeDialog(dialog)
    }

    override fun calculateMatchResult(): String? {
        if (isAbandoned) return "MATCH ABANDONED"
        val m = match ?: return null
        val i1 = m.firstInnings ?: return null
        
        val i2 = m.secondInnings
        
        // If match is still active, show progress status
        if (!isFinished) {
            if (!i1.isComplete) return "IN-PROGRESS"
            if (i2 == null) return "FIRST INNINGS OVER"
            if (!i2.isComplete) return "IN-PROGRESS"
        }

        // If we reach here, either the match is naturally complete OR was manually finished
        if (i2 == null) {
            return "MATCH ABANDONED"
        }

        return when {
            i2.totalRuns >= m.target -> {
                val wickets = (i2.maxWickets - i2.totalWickets)
                "${i2.battingTeam} WON BY $wickets WICKETS".uppercase()
            }
            i2.totalRuns == (m.target - 1) -> "MATCH TIED"
            else -> {
                // Team 1 wins if Team 2 hasn't reached the target
                val runs = (m.target - 1) - i2.totalRuns
                "${i1.battingTeam} WON BY $runs RUNS".uppercase()
            }
        }
    }

    private fun triggerConfetti(colors: IntArray) {
        findViewById<View?>(android.R.id.content)?.let {
            CommonConfetti.rainingConfetti(it as ViewGroup, colors).oneShot()
        }
    }

    override fun showBowlerSelectionDialog(onDone: Runnable?, cancelable: Boolean) {
        val bowlingTeam = if (teamABatting) teamBNames else teamANames
        if (bowlingTeam == null) return

        val currentBowler = bowler?.name?.trim() ?: ""
        val allNames = bowlingTeam.mapNotNull { it?.trim() }
        
        val isShared = match?.isSharedOver ?: false
        val filteredNames = if (allNames.size > 1 && !isShared) {
            allNames.filter { !it.equals(currentBowler, ignoreCase = true) }
        } else {
            allNames
        }

        if (filteredNames.isEmpty()) {
            // Fallback if filtering somehow removes everyone
            updateUI()
            onDone?.run()
            return
        }

        val namesArray = filteredNames.toTypedArray()

        showDynamicDialog {
            setTitle(getString(R.string.select_bowler))
            setItems(namesArray) { d, w ->
                val selectedName = namesArray[w]
                bowler = getPlayerFromCache(selectedName)

                // If selecting for a new over (0 balls) and flag was set, reset it
                if (match?.currentInnings?.legalBalls?.let { it % 6 == 0 } == true) {
                    match?.isSharedOver = false
                }

                updateUI()
                onDone?.run()
            }
            setCancelable(cancelable)
        }
    }

    override fun handleBowlerChangeMidOver() {
        val innings = match?.currentInnings ?: return
        if (innings.legalBalls % 6 == 0) {
            // Start of over, just show normal dialog
            showBowlerSelectionDialog(null, true)
            return
        }

        showDynamicDialog {
            setTitle("Change Bowler Mid-Over?")
            setMessage("Do you want to change the bowler in the middle of this over?")
            setPositiveButton("Yes") { _, _ ->
                match?.isSharedOver = true
                showBowlerSelectionDialog(null, true)
            }
            setNegativeButton("Dismiss", null)
        }
    }

    private fun showQuickRunsDialog(
        title: String,
        options: IntArray,
        allowWicket: Boolean,
        onRunsSelected: (Int) -> Unit,
        onWicketSelected: (() -> Unit)? = null
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        val btnContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        options.forEach { run ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = run.toString()
                layoutParams = LinearLayout.LayoutParams(0, 140, 1f).apply { setMargins(8, 0, 8, 0) }
                setOnClickListener {
                    onRunsSelected(run)
                }
            }
            btnContainer.addView(btn)
        }
        layout.addView(btnContainer)

        val manualInput = EditText(this).apply {
            hint = "Custom Runs"
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
        }
        layout.addView(manualInput)

        val dialog = showDynamicDialog {
            setTitle(title)
            setView(layout)
            setPositiveButton("Add Manual") { _, _ ->
                val r = manualInput.text.toString().toIntOrNull() ?: 0
                onRunsSelected(r)
            }
            setNeutralButton("Cancel", null)

            if (allowWicket && onWicketSelected != null) {
                setNegativeButton("WICKET") { _, _ -> onWicketSelected() }
            }
        }

        dialog.apply {
            // Color the Wicket button Red
            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.RED)
        }
    }

    override fun handleWide() {
        val baseExtra = if (isRuleRunsOnWide) 1 else 0
        val originalStriker = striker
        
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 24)
        }
        
        val label = TextView(this).apply {
            text = getString(R.string.quick_add_runs_label)
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }
        linearLayout.addView(label)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val fastRuns = intArrayOf(0, 1, 2, 3, 4)
        
        fastRuns.forEach { r ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(0, 130, 1.0f).apply {
                    setMargins(6, 0, 6, 0)
                }
                text = r.toString()
                setPadding(0, 0, 0, 0)
                cornerRadius = 65
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    if (r % 2 != 0) swapBatsmen()
                    showWicketOnWideDialog(baseExtra + r, originalStriker)
                }
            }
            btnRow.addView(btn)
        }
        linearLayout.addView(btnRow)

        val manualLabel = TextView(this).apply {
            text = "Manual Runs"
            textSize = 14f
            setPadding(0, 32, 0, 12)
        }
        linearLayout.addView(manualLabel)

        val etRuns = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("0")
            setSelection(1)
        }
        linearLayout.addView(etRuns)

        dialogRef[0] = ThemeManager.createDynamicBuilder(this)
            .setTitle("Wide: Runs Ran?")
            .setView(linearLayout)
            .setPositiveButton("Confirm") { _, _ ->
                val input = etRuns.text.toString()
                val r = if (input.isEmpty()) 0 else input.toInt()
                if (r % 2 != 0) swapBatsmen()
                showWicketOnWideDialog(baseExtra + r, originalStriker)
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialogRef[0]?.show()
        ThemeManager.colorizeDialog(dialogRef[0]!!)
    }

    private fun showWicketOnWideDialog(wideRuns: Int, facingBatsman: Player?) {
        val widePenalty = if (isRuleRunsOnWide) 1 else 0
        val runsRan = wideRuns - widePenalty
        
        val optionsList = mutableListOf("None", "Run Out")
        if (runsRan == 0) {
            optionsList.add("Stumped")
        }
        optionsList.add("Obstructing Field")
        if (runsRan == 0) {
            optionsList.add("Hit Wicket")
        }
        
        val options = optionsList.toTypedArray()

        showDynamicDialog {
            setTitle("Wicket on this Wide?")
            setItems(options) { _, w ->
                when (options[w]) {
                    "None" -> playBallWithPlayer(wideRuns, BallType.WIDE, false, facingBatsman, null, 0)
                    "Run Out" -> showRunOutFlowOnWide(wideRuns, facingBatsman)
                    "Stumped" -> showStumpedFlowOnWide(wideRuns, facingBatsman)
                    "Obstructing Field" -> showObstructingFieldFlowOnWide(wideRuns, facingBatsman)
                    "Hit Wicket" -> {
                        val dStr = "Hit Wicket b ${bowler?.name ?: "Bowler"}"
                        commitWicket(striker, facingBatsman, dStr, BallType.WIDE, wideRuns, false, null, 0, true)
                    }
                }
            }
            setCancelable(false)
        }
    }

    private fun showStumpedFlowOnWide(wideRuns: Int, facingBatsman: Player?) {
        val bowlingTeam = if (teamABatting) teamBNames else teamANames
        val bowlerName = bowler?.name ?: ""
        val options = bowlingTeam?.filterNotNull()?.filter { it != bowlerName } ?: emptyList()
        val array = options.toTypedArray()

        showDynamicDialog {
            setTitle("Who is the Wicketkeeper?")
            setItems(array) { _, w ->
                val keeper = array[w]
                val outP = striker ?: return@setItems
                val bName = bowler?.name ?: "Bowler"
                val dStr = "st $keeper b $bName"
                commitWicket(outP, facingBatsman, dStr, BallType.WIDE, wideRuns, false, keeper, 0, true)
            }
            setCancelable(false)
        }
    }


    override fun handleNoBall() {
        if (isRuleRunsOnBye) {
            val options = arrayOf("Off Bat", "Byes", "Leg-Byes")
            showDynamicDialog {
                setTitle("Runs From?")
                setItems(options) { _, w ->
                    showNoBallRunsInput(options[w])
                }
            }
        } else {
            showNoBallRunsInput("Off Bat")
        }
    }

    private fun showNoBallRunsInput(runType: String) {
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val label = TextView(this).apply {
            text = "Quick Add Runs ($runType):"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        linearLayout.addView(label)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val nbPenalty = if (isRuleRunsOnWide) 1 else 0
        val fastRuns = if (runType == "Off Bat") {
            intArrayOf(0, 1, 2, 3, 4, 6)
        } else {
            intArrayOf(1, 2, 3, 4, 6)
        }
        
        fastRuns.forEach { r ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    setMargins(4, 0, 4, 0)
                }
                text = r.toString()
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    val originalStriker = striker
                    if (r % 2 != 0) swapBatsmen()
                    showWicketOnNoBallDialog(nbPenalty + r, runType, r, originalStriker)
                }
            }
            btnRow.addView(btn)
        }
        linearLayout.addView(btnRow)

        val manualLabel = TextView(this).apply {
            text = "Or Manual runs ran"
            textSize = 14f
            setPadding(0, 24, 0, 8)
        }
        linearLayout.addView(manualLabel)

        val etRuns = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Or Manual runs ran"
        }
        linearLayout.addView(etRuns)

        dialogRef[0] = showDynamicDialog {
            setTitle("No-Ball: Runs $runType?")
            setView(linearLayout)
            setPositiveButton("Confirm") { _, _ ->
                val originalStriker = striker
                val input = etRuns.text.toString()
                val r = if (input.isEmpty()) 0 else input.toInt()
                if (r % 2 != 0) swapBatsmen()
                showWicketOnNoBallDialog(nbPenalty + r, runType, r, originalStriker)
            }
            setNegativeButton("Cancel", null)
        }
    }

    private fun showWicketOnNoBallDialog(totalRuns: Int, runType: String, runsRan: Int, facingBatsman: Player?) {
        val options = arrayOf("None", "Run Out", "Obstructing Field")
        showDynamicDialog {
            setTitle("Wicket on this No-Ball?")
            setItems(options) { _, w ->
                val manualBatRuns = if ("Off Bat" == runType) runsRan else 0
                
                when (w) {
                    0 -> playBallWithPlayer(totalRuns, BallType.NO_BALL, false, facingBatsman, null, manualBatRuns)
                    1 -> showRunOutFlowOnNoBall(totalRuns, manualBatRuns, facingBatsman)
                    2 -> showObstructingFieldFlowOnNoBall(totalRuns, manualBatRuns, facingBatsman)
                }
            }
            setCancelable(false)
        }
    }

    override fun handleBye() {
        if (isRuleRunsOnBye) {
            showByesFlow()
        } else {
            Toast.makeText(this, "Byes are disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showByesFlow() {
        val originalStriker = striker
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val label = TextView(this).apply {
            text = "Runs on this Byes?"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        linearLayout.addView(label)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val fastRuns = intArrayOf(1, 2, 3, 4, 6)
        
        fastRuns.forEach { r ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    setMargins(4, 0, 4, 0)
                }
                text = r.toString()
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    if (r % 2 != 0) swapBatsmen()
                    showWicketOnByeDialog(r, originalStriker)
                }
            }
            btnRow.addView(btn)
        }
        linearLayout.addView(btnRow)

        val manualLabel = TextView(this).apply {
            text = "Manual Runs"
            textSize = 14f
            setPadding(0, 24, 0, 8)
        }
        linearLayout.addView(manualLabel)

        val etRuns = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Manual Runs"
        }
        linearLayout.addView(etRuns)

        dialogRef[0] = ThemeManager.createDynamicBuilder(this)
            .setTitle("Byes")
            .setView(linearLayout)
            .setPositiveButton("Next", null)
            .setNegativeButton("Cancel", null)
            .create()
        
        dialogRef[0]?.show()
        ThemeManager.colorizeDialog(dialogRef[0]!!)

        dialogRef[0]?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val input = etRuns.text.toString()
            val r = if (input.isEmpty()) 0 else input.toIntOrNull() ?: 0
            if (r < 1) {
                etRuns.error = "Minimum 1 run required for Byes"
            } else {
                dialogRef[0]?.dismiss()
                if (r % 2 != 0) swapBatsmen()
                showWicketOnByeDialog(r, originalStriker)
            }
        }
    }

    private fun showWicketOnByeDialog(runs: Int, facingBatsman: Player?) {
        val options = arrayOf("None", "Run Out", "Obstructing the Field")
        showDynamicDialog {
            setTitle("Wickets on this Bye?")
            setItems(options) { _, w ->
                when (w) {
                    0 -> playBallWithPlayer(runs, BallType.BYE, false, facingBatsman, null, 0)
                    1 -> showRunOutFlowForBye(runs, facingBatsman)
                    2 -> showObstructingFieldFlowForBye(runs, facingBatsman)
                }
            }
            setCancelable(false)
        }
    }

    private fun showRunOutFlowForBye(runs: Int, facingBatsman: Player?) {
        val onFielderSelected: (Player, String, String) -> Unit = { outP, thrower, breaker ->
            val performCommit: (Boolean) -> Unit = { isStrikerEnd ->
                // Slot Correction (only for 2-batter mode)
                if (nonStriker != null) {
                    if (isStrikerEnd && outP == nonStriker) {
                        swapBatsmen()
                    } else if (!isStrikerEnd && outP == striker) {
                        swapBatsmen()
                    }
                }

                val dStr = if (thrower == "None" && breaker == "None") {
                    "Run Out"
                } else if (thrower == "None") {
                    "Run Out ($breaker)"
                } else if (breaker == "None") {
                    "Run Out ($thrower)"
                } else {
                    "Run Out ($thrower / $breaker)"
                }
                
                val fielderForRecord = if (breaker != "None") breaker else if (thrower != "None") thrower else null
                commitWicket(outP, facingBatsman ?: striker!!, dStr, BallType.BYE, runs, true, fielderForRecord, 0, isStrikerEnd)
            }

            if (nonStriker == null) {
                performCommit(true)
            } else {
                showDynamicDialog {
                    setTitle("Which end did the Run Out Happened?")
                    setItems(arrayOf("Striker's End", "Non-Striker's End")) { _, end ->
                        performCommit(end == 0)
                    }
                    setCancelable(false)
                }
            }
        }

        val startFielderChain: (Player) -> Unit = { outP ->
            showFielderSelection("Fielder Involved (Thrower)?", true) { thrower ->
                val excludeList = if (thrower != "None") listOf(thrower) else emptyList()
                val allowNoneForBreaker = (thrower != "None")
                showFielderSelection("Fielder Involved (Stump Breaker)?", allowNoneForBreaker, excludeList) { breaker ->
                    onFielderSelected(outP, thrower, breaker)
                }
            }
        }

        if (nonStriker == null) {
            striker?.let { startFielderChain(it) }
        } else {
            showOutPlayerSelection("Who is Run Out?") { outP ->
                if (outP != null) startFielderChain(outP)
            }
        }
    }

    private fun showObstructingFieldFlowForBye(runs: Int, facingBatsman: Player?) {
        val performCommit: (Player, Boolean) -> Unit = { outP, isStrikerEnd ->
            // Slot Correction
            if (nonStriker != null) {
                if (isStrikerEnd && outP == nonStriker) {
                    swapBatsmen()
                } else if (!isStrikerEnd && outP == striker) {
                    swapBatsmen()
                }
            }
            commitWicket(outP, facingBatsman ?: striker!!, "Obstructing the field", BallType.BYE, runs, false, null, 0, isStrikerEnd)
        }

        if (nonStriker == null) {
            striker?.let { performCommit(it, true) }
        } else {
            showOutPlayerSelection("Which batsman Obstructed the field?") { outP ->
                if (outP == null) return@showOutPlayerSelection
                showDynamicDialog {
                    setTitle("Which end did the Obstruction happened?")
                    setItems(arrayOf("Striker's End", "Non-Striker's End")) { _, end ->
                        performCommit(outP, end == 0)
                    }
                    setCancelable(false)
                }
            }
        }
    }

    override fun handleLegBye() {
        if (isRuleRunsOnBye) {
            showLegByesFlow()
        } else {
            Toast.makeText(this, "Leg-Byes are disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLegByesFlow() {
        val originalStriker = striker
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val label = TextView(this).apply {
            text = "Runs on this Leg Byes?"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        linearLayout.addView(label)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val fastRuns = intArrayOf(1, 2, 3, 4, 6)
        
        fastRuns.forEach { r ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    setMargins(4, 0, 4, 0)
                }
                text = r.toString()
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    if (r % 2 != 0) swapBatsmen()
                    showWicketOnLegByeDialog(r, originalStriker)
                }
            }
            btnRow.addView(btn)
        }
        linearLayout.addView(btnRow)

        val manualLabel = TextView(this).apply {
            text = "Manual Runs"
            textSize = 14f
            setPadding(0, 24, 0, 8)
        }
        linearLayout.addView(manualLabel)

        val etRuns = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Manual Runs"
        }
        linearLayout.addView(etRuns)

        dialogRef[0] = ThemeManager.createDynamicBuilder(this)
            .setTitle("Leg Byes")
            .setView(linearLayout)
            .setPositiveButton("Next", null)
            .setNegativeButton("Cancel", null)
            .create()
        
        dialogRef[0]?.show()
        ThemeManager.colorizeDialog(dialogRef[0]!!)

        dialogRef[0]?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val input = etRuns.text.toString()
            val r = if (input.isEmpty()) 0 else input.toIntOrNull() ?: 0
            if (r < 1) {
                etRuns.error = "Minimum 1 run required for Leg Byes"
            } else {
                dialogRef[0]?.dismiss()
                if (r % 2 != 0) swapBatsmen()
                showWicketOnLegByeDialog(r, originalStriker)
            }
        }
    }

    private fun showWicketOnLegByeDialog(runs: Int, facingBatsman: Player?) {
        val options = arrayOf("None", "Run Out", "Obstructing the Field")
        showDynamicDialog {
            setTitle("Wickets on this Leg Bye?")
            setItems(options) { _, w ->
                when (w) {
                    0 -> playBallWithPlayer(runs, BallType.LEG_BYE, false, facingBatsman, null, 0)
                    1 -> showRunOutFlowForLegBye(runs, facingBatsman)
                    2 -> showObstructingFieldFlowForLegBye(runs, facingBatsman)
                }
            }
            setCancelable(false)
        }
    }

    private fun showRunOutFlowForLegBye(runs: Int, facingBatsman: Player?) {
        val onFielderSelected: (Player, String, String) -> Unit = { outP, thrower, breaker ->
            val performCommit: (Boolean) -> Unit = { isStrikerEnd ->
                // Slot Correction (only for 2-batter mode)
                if (nonStriker != null) {
                    if (isStrikerEnd && outP == nonStriker) {
                        swapBatsmen()
                    } else if (!isStrikerEnd && outP == striker) {
                        swapBatsmen()
                    }
                }

                val dStr = if (thrower == "None" && breaker == "None") {
                    "Run Out"
                } else if (thrower == "None") {
                    "Run Out ($breaker)"
                } else if (breaker == "None") {
                    "Run Out ($thrower)"
                } else {
                    "Run Out ($thrower / $breaker)"
                }
                
                val fielderForRecord = if (breaker != "None") breaker else if (thrower != "None") thrower else null
                commitWicket(outP, facingBatsman ?: striker!!, dStr, BallType.LEG_BYE, runs, true, fielderForRecord, 0, isStrikerEnd)
            }

            if (nonStriker == null) {
                performCommit(true)
            } else {
                showDynamicDialog {
                    setTitle("Which end the Run Out Happened?")
                    setItems(arrayOf("Striker's End", "Non-Striker's End")) { _, end ->
                        performCommit(end == 0)
                    }
                    setCancelable(false)
                }
            }
        }

        val startFielderChain: (Player) -> Unit = { outP ->
            showFielderSelection("Fielder Involved (Thrower)?", true) { thrower ->
                val excludeList = if (thrower != "None") listOf(thrower) else emptyList()
                val allowNoneForBreaker = (thrower != "None")
                showFielderSelection("Fielder Involved (Stump Breaker)?", allowNoneForBreaker, excludeList) { breaker ->
                    onFielderSelected(outP, thrower, breaker)
                }
            }
        }

        if (nonStriker == null) {
            striker?.let { startFielderChain(it) }
        } else {
            showOutPlayerSelection("Who is Run Out?") { outP ->
                if (outP != null) startFielderChain(outP)
            }
        }
    }

    private fun showObstructingFieldFlowForLegBye(runs: Int, facingBatsman: Player?) {
        val performCommit: (Player, Boolean) -> Unit = { outP, isStrikerEnd ->
            // Slot Correction
            if (nonStriker != null) {
                if (isStrikerEnd && outP == nonStriker) {
                    swapBatsmen()
                } else if (!isStrikerEnd && outP == striker) {
                    swapBatsmen()
                }
            }
            commitWicket(outP, facingBatsman ?: striker!!, "Obstructing the field", BallType.LEG_BYE, runs, false, null, 0, isStrikerEnd)
        }

        if (nonStriker == null) {
            striker?.let { performCommit(it, true) }
        } else {
            showOutPlayerSelection("Which batsman Obstructed the field?") { outP ->
                if (outP == null) return@showOutPlayerSelection
                showDynamicDialog {
                    setTitle("Which end did the Obstruction happened?")
                    setItems(arrayOf("Striker's End", "Non-Striker's End")) { _, end ->
                        performCommit(outP, end == 0)
                    }
                    setCancelable(false)
                }
            }
        }
    }

    override fun handleOverthrow() {
        val innings = match?.currentInnings
        if (innings == null) {
            Toast.makeText(this, "Match not started", Toast.LENGTH_SHORT).show()
            return
        }
        //Standalone recording for overthrows - no pre-recorded ball required
        showOverthrowStep1Dialog()
    }

    private fun showOverthrowStep1Dialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 24, 64, 24)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val fastRuns = intArrayOf(0, 1, 2, 3)
        fastRuns.forEach { r ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(0, 130, 1.0f).apply { setMargins(6, 0, 6, 0) }
                text = r.toString()
                cornerRadius = 65
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    showOverthrowStep2Dialog(r)
                }
            }
            btnRow.addView(btn)
        }
        container.addView(btnRow)

        val etRuns = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Manual Runs"
            setPadding(0, 32, 0, 12)
        }
        container.addView(etRuns)

        // Unified Custom Title for Step 1
        val tvTitle = TextView(this).apply {
            text = "Runs completed before throw?"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(64, 48, 64, 0)
            setTextColor(ThemeManager.getThemeColor(this@MainActivity, com.google.android.material.R.attr.colorOnSurface))
        }

        dialogRef[0] = ThemeManager.createDynamicBuilder(this)
            .setCustomTitle(tvTitle)
            .setView(container)
            .setPositiveButton("Next") { _, _ ->
                val r = etRuns.text.toString().toIntOrNull() ?: 0
                showOverthrowStep2Dialog(r)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialogRef[0]?.show()
        ThemeManager.colorizeDialog(dialogRef[0]!!)
    }

    private fun showOverthrowStep2Dialog(beforeRuns: Int) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 24, 64, 24)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val fastRuns = intArrayOf(1, 2, 3, 4)
        fastRuns.forEach { r ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(0, 130, 1.0f).apply { setMargins(6, 0, 6, 0) }
                text = r.toString()
                cornerRadius = 65
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    applyOverthrowLogic(beforeRuns, r)
                }
            }
            btnRow.addView(btn)
        }
        container.addView(btnRow)

        val etRuns = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Manual Runs"
            setPadding(0, 32, 0, 12)
        }
        container.addView(etRuns)

        // Unified Custom Title for Step 2
        val tvTitle = TextView(this).apply {
            text = "Runs resulting from overthrow?"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(64, 48, 64, 0)
            setTextColor(ThemeManager.getThemeColor(this@MainActivity, com.google.android.material.R.attr.colorOnSurface))
        }

        dialogRef[0] = ThemeManager.createDynamicBuilder(this)
            .setCustomTitle(tvTitle)
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val r = etRuns.text.toString().toIntOrNull() ?: 0
                applyOverthrowLogic(beforeRuns, r)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialogRef[0]?.show()
        ThemeManager.colorizeDialog(dialogRef[0]!!)
    }

    private fun applyOverthrowLogic(before: Int, after: Int) {
        val totalNewRuns = before + after
        if (totalNewRuns == 0 && after == 0) return 

        if (totalNewRuns > 10) {
            Toast.makeText(this, "High run count entered: $totalNewRuns. Please verify.", Toast.LENGTH_SHORT).show()
        }

        // Record a NEW legal delivery
        playBall(totalNewRuns, BallType.NORMAL, false)
        
        // Update the commentary entry that was just created by playBall to include breakdown
        if (commentary.isNotEmpty()) {
            val entry = commentary[0]
            if (entry?.type == "BALL") {
                entry.text = "${entry.text} (incl. $after overthrows)"
            }
        }
        
        saveMatchToDatabase(isFinished, isAbandoned, true)
    }


    override fun handleWicketFlow() {
        val items = if (isFreeHitActive) {
            arrayOf("Run Out", "Obstructing the Field")
        } else {
            arrayOf(
                "Bowled Out", 
                "Caught Out", 
                "Run Out", 
                "Hit Out of The Ground", 
                "Stumped", 
                "LBW", 
                "Hit Wicket", 
                "Obstructing the Field",
            )
        }

        showDynamicDialog {
            setTitle("Wicket Type")
            setItems(items) { d, w ->
                val type = items[w]
                val bName = bowler?.name ?: "Bowler"
                when (type) {
                    "Bowled Out" -> commitWicket(striker, striker, "b $bName")
                    "Caught Out" -> showFielderSelection("Who caught it?", false) { fielder ->
                        val dStr = if (fielder == bName) "c & b $bName" else "c $fielder b $bName"
                        commitWicket(striker, striker, dStr, fielder = fielder)
                    }
                    "Run Out" -> showRunOutFlow()
                    "Hit Out of The Ground" -> commitWicket(striker, striker, "Hit Out Of The Ground b $bName")
                    "Stumped" -> showFielderSelection("Who is the Wicketkeeper?", false, exclude = listOfNotNull(bowler?.name)) { keeper ->
                        commitWicket(striker, striker, "st $keeper b $bName", fielder = keeper)
                    }
                    "LBW" -> commitWicket(striker, striker, "LBW b $bName")
                    "Hit Wicket" -> commitWicket(striker, striker, "Hit Wicket b $bName")
                    "Obstructing the Field", -> showObstructingFieldFlow()
                }
            }
        }
    }

    private fun showRunOutFlow() {
        if (!isRuleRunsOnBye) {
            showRunOutRunsInput("Off Bat", BallType.NORMAL)
            return
        }

        val options = arrayOf("Off Bat", "Byes", "Leg-Byes")
        showDynamicDialog {
            setTitle("Runs From?")
            setItems(options) { _, w ->
                val bt = when (options[w]) {
                    "Leg-Byes" -> BallType.LEG_BYE
                    "Byes" -> BallType.BYE
                    else -> BallType.NORMAL
                }
                showRunOutRunsInput(options[w], bt)
            }
        }
    }

    private fun showRunOutRunsInput(runType: String, bt: BallType) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 32 }
        }

        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val quickRuns = if (runType == "Off Bat") {
            intArrayOf(0, 1, 2, 3, 4)
        } else {
            intArrayOf(1, 2, 3, 4)
        }
        
        quickRuns.forEach { r ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(0, 130, 1.0f).apply {
                    setMargins(6, 0, 6, 0)
                }
                text = r.toString()
                cornerRadius = 65
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    processRunOutWithRuns(r, bt)
                }
            }
            btnRow.addView(btn)
        }
        container.addView(btnRow)

        val manualLabel = TextView(this).apply {
            text = "Or Manual runs ran"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        container.addView(manualLabel)

        val etRuns = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Or Manual runs ran"
        }
        container.addView(etRuns)

        dialogRef[0] = ThemeManager.createDynamicBuilder(this)
            .setTitle("Runs $runType before Run Out?")
            .setView(container)
            .setPositiveButton("Next", null)
            .setNegativeButton("Cancel", null)
            .create()
        
        dialogRef[0]?.show()
        ThemeManager.colorizeDialog(dialogRef[0]!!)

        dialogRef[0]?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val runs = etRuns.text.toString().toIntOrNull() ?: 0
            if ((runType == "Byes" || runType == "Leg-Byes") && runs < 1) {
                etRuns.error = "Minimum 1 run required for $runType"
            } else {
                dialogRef[0]?.dismiss()
                processRunOutWithRuns(runs, bt)
            }
        }
    }

    private fun processRunOutWithRuns(runs: Int, bt: BallType) {
        val originalStriker = striker
        if (runs % 2 != 0) swapBatsmen()

        val onFielderSelected: (Player, String, String) -> Unit = { outP, thrower, breaker ->
            val performCommit: (Boolean) -> Unit = { isStrikerEnd ->
                // Slot Correction (only for 2-batter mode)
                if (nonStriker != null) {
                    if (isStrikerEnd && outP == nonStriker) {
                        swapBatsmen()
                    } else if (!isStrikerEnd && outP == striker) {
                        swapBatsmen()
                    }
                }

                val dStr = when {
                    thrower == "None" && breaker == "None" -> "Run Out"
                    thrower == "None" -> "Run Out($breaker)"
                    breaker == "None" -> "Run Out($thrower)"
                    else -> "Run Out($thrower/$breaker)"
                }
                val fielderForRecord = when {
                    breaker != "None" && thrower != "None" -> "$thrower / $breaker"
                    breaker != "None" -> breaker
                    thrower != "None" -> thrower
                    else -> null
                }
                val manualBat = if (bt == BallType.NORMAL) runs else 0
                commitWicket(outP, originalStriker, dStr, bt, runs, true, fielderForRecord, manualBat, isStrikerEnd)
            }

            if (nonStriker == null) {
                // Single batter mode: auto-select Striker's end
                performCommit(true)
            } else {
                showDynamicDialog {
                    setTitle("Which end did the run out happen?")
                    setItems(arrayOf("Striker's End", "Non-Striker's End")) { _, end ->
                        performCommit(end == 0)
                    }
                    setCancelable(false)
                }
            }
        }

        val startFielderChain: (Player) -> Unit = { outP ->
            showFielderSelection("Fielder Involved (Thrower)?", true) { thrower ->
                val excludeList = if (thrower != "None") listOf(thrower) else emptyList()
                val allowNoneForBreaker = (thrower != "None")
                showFielderSelection("Fielder Involved (Stump Breaker)?", allowNoneForBreaker, excludeList) { breaker ->
                    onFielderSelected(outP, thrower, breaker)
                }
            }
        }

        if (nonStriker == null) {
            // Single batter mode: Striker is the only one who can be out
            striker?.let { startFielderChain(it) }
        } else {
            showOutPlayerSelection("Who is Run Out?") { outP ->
                if (outP != null) startFielderChain(outP)
            }
        }
    }

    private fun showObstructingFieldFlow() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 32 }
        }

        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val quickRuns = intArrayOf(0, 1, 2, 3, 4)
        
        quickRuns.forEach { r ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(0, 130, 1.0f).apply {
                    setMargins(6, 0, 6, 0)
                }
                text = r.toString()
                cornerRadius = 65
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    processObstructingFieldWithRuns(r)
                }
            }
            btnRow.addView(btn)
        }
        container.addView(btnRow)

        val manualLabel = TextView(this).apply {
            text = "Or Manual runs ran"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        container.addView(manualLabel)

        val etRuns = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("0")
            hint = "Or Manual runs ran"
        }
        container.addView(etRuns)

        dialogRef[0] = ThemeManager.createDynamicBuilder(this)
            .setTitle("Runs completed before obstruction?")
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val runs = etRuns.text.toString().toIntOrNull() ?: 0
                processObstructingFieldWithRuns(runs)
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialogRef[0]?.show()
        ThemeManager.colorizeDialog(dialogRef[0]!!)
    }

    private fun processObstructingFieldWithRuns(runs: Int) {
        val originalStriker = striker
        if (runs % 2 != 0) swapBatsmen()

        if (nonStriker == null) {
            // Single batter mode: Striker is the only one who can be out at Striker's End
            striker?.let { outP ->
                commitWicket(outP, originalStriker, "Obstructed the field", BallType.NORMAL, runs, false, null, runs, true)
            }
        } else {
            showOutPlayerSelection("Who obstructed the field?") { outP ->
                if (outP == null) return@showOutPlayerSelection
                showDynamicDialog {
                    setTitle("Which end did the obstruction happen?")
                    setItems(arrayOf("Striker's End", "Non-Striker's End")) { _, end ->
                        val isStrikerEnd = (end == 0)
                        
                        // Slot Correction
                        if (isStrikerEnd && outP == nonStriker) {
                            swapBatsmen()
                        } else if (!isStrikerEnd && outP == striker) {
                            swapBatsmen()
                        }
                        
                        commitWicket(outP, originalStriker, "Obstructed the field", BallType.NORMAL, runs, false, null, runs, isStrikerEnd)
                    }
                    setCancelable(false)
                }
            }
        }
    }

    private fun commitWicket(
        p: Player?,
        facingBatsman: Player?,
        type: String,
        bt: BallType = BallType.NORMAL,
        runs: Int = 0,
        isRO: Boolean = false,
        fielder: String? = null,
        batRuns: Int = 0,
        isStrikerReplacingOverride: Boolean? = null
    ) {
        if (p == null) return
        
        p.isOut = true
        p.dismissalInfo = type
        if (!isRO && bowler != null) {
            bowler?.wicketsTaken = (bowler?.wicketsTaken ?: 0) + 1
        }
        
        playBallWithPlayer(runs, bt, true, facingBatsman, fielder, batRuns, p, isStrikerReplacingOverride)
    }

    private fun showRunOutFlowOnWide(wideRuns: Int, facingBatsman: Player?) {
        val onFielderSelected: (Player, String, String) -> Unit = { outP, thrower, breaker ->
            val performCommit: (Boolean) -> Unit = { isStrikerEnd ->
                // Slot Correction (only for 2-batter mode)
                if (nonStriker != null) {
                    if (isStrikerEnd && outP == nonStriker) {
                        swapBatsmen()
                    } else if (!isStrikerEnd && outP == striker) {
                        swapBatsmen()
                    }
                }

                val fielder = when {
                    thrower == "None" && breaker == "None" -> "Fielder"
                    thrower != "None" && breaker == "None" -> thrower
                    thrower == "None" && breaker != "None" -> breaker
                    thrower == breaker -> thrower
                    else -> "$thrower / $breaker"
                }
                
                outP.isOut = true
                outP.dismissalInfo = "Run Out ($fielder)"
                
                playBallWithPlayer(wideRuns, BallType.WIDE, true, facingBatsman, fielder, 0, outP, isStrikerEnd)
            }

            if (nonStriker == null) {
                performCommit(true)
            } else {
                showDynamicDialog {
                    setTitle("Which end did the Run Out happen?")
                    setItems(arrayOf("Striker's End", "Non-Striker's End")) { _, endIdx ->
                        performCommit(endIdx == 0)
                    }
                    setCancelable(false)
                }
            }
        }

        val startFielderChain: (Player) -> Unit = { outP ->
            showFielderSelection("Fielder Involved (Thrower)", true) { thrower ->
                val excludeList = if (thrower != "None") listOf(thrower) else emptyList()
                val allowNoneForBreaker = (thrower != "None")
                showFielderSelection("Fielder Involved (Stump Breaker)", allowNoneForBreaker, excludeList) { breaker ->
                    onFielderSelected(outP, thrower, breaker)
                }
            }
        }

        if (nonStriker == null) {
            striker?.let { startFielderChain(it) }
        } else {
            showOutPlayerSelection("Who is Run Out?") { outP ->
                if (outP != null) startFielderChain(outP)
            }
        }
    }

    private fun showObstructingFieldFlowOnWide(wideRuns: Int, facingBatsman: Player?) {
        val performCommit: (Player, Boolean) -> Unit = { outP, isStrikerEnd ->
            // Slot Correction
            if (nonStriker != null) {
                if (isStrikerEnd && outP == nonStriker) {
                    swapBatsmen()
                } else if (!isStrikerEnd && outP == striker) {
                    swapBatsmen()
                }
            }
            outP.isOut = true
            outP.dismissalInfo = "Obstructed The Field"
            playBallWithPlayer(wideRuns, BallType.WIDE, true, facingBatsman, null, 0, outP, isStrikerEnd)
        }

        if (nonStriker == null) {
            striker?.let { performCommit(it, true) }
        } else {
            showOutPlayerSelection("Who obstructed the field?") { outP ->
                if (outP == null) return@showOutPlayerSelection
                showDynamicDialog {
                    setTitle("Which End Did the Obstruction happen?")
                    setItems(arrayOf("Striker's End", "Non-Striker's End")) { _, endIdx ->
                        performCommit(outP, endIdx == 0)
                    }
                    setCancelable(false)
                }
            }
        }
    }

    private fun showRunOutFlowOnNoBall(totalRuns: Int, batRuns: Int, facingBatsman: Player?) {
        val onFielderSelected: (Player, String, String) -> Unit = { outP, thrower, breaker ->
            val performCommit: (Boolean) -> Unit = { isStrikerEnd ->
                // Slot Correction (only for 2-batter mode)
                if (nonStriker != null) {
                    if (isStrikerEnd && outP == nonStriker) {
                        swapBatsmen()
                    } else if (!isStrikerEnd && outP == striker) {
                        swapBatsmen()
                    }
                }

                val dStr = if (thrower == "None" && breaker == "None") {
                    "run out"
                } else if (thrower == "None") {
                    "run out ($breaker)"
                } else if (breaker == "None") {
                    "run out ($thrower)"
                } else {
                    "run out ($thrower/$breaker)"
                }
                
                val fielderForRecord = if (breaker != "None") breaker else if (thrower != "None") thrower else null
                commitWicket(outP, facingBatsman, dStr, BallType.NO_BALL, totalRuns, true, fielderForRecord, batRuns, isStrikerEnd)
            }

            if (nonStriker == null) {
                performCommit(true)
            } else {
                showDynamicDialog {
                    setTitle("Which end did the run out happen?")
                    setItems(arrayOf("Striker's End", "Non-Striker's End")) { _, end ->
                        performCommit(end == 0)
                    }
                    setCancelable(false)
                }
            }
        }

        val startFielderChain: (Player) -> Unit = { outP ->
            showFielderSelection("Fielder Involved (Thrower)?", true) { thrower ->
                val excludeList = if (thrower != "None") listOf(thrower) else emptyList()
                val allowNoneForBreaker = (thrower != "None")
                showFielderSelection("Fielder Involved (Stump Breaker)?", allowNoneForBreaker, excludeList) { breaker ->
                    onFielderSelected(outP, thrower, breaker)
                }
            }
        }

        if (nonStriker == null) {
            striker?.let { startFielderChain(it) }
        } else {
            showOutPlayerSelection("Who is Run Out?") { outP ->
                if (outP != null) startFielderChain(outP)
            }
        }
    }

    private fun showObstructingFieldFlowOnNoBall(totalRuns: Int, batRuns: Int, facingBatsman: Player?) {
        val performCommit: (Player, Boolean) -> Unit = { outP, isStrikerEnd ->
            // Slot Correction
            if (nonStriker != null) {
                if (isStrikerEnd && outP == nonStriker) {
                    swapBatsmen()
                } else if (!isStrikerEnd && outP == striker) {
                    swapBatsmen()
                }
            }
            commitWicket(outP, facingBatsman, "Obstructing Field", BallType.NO_BALL, totalRuns, false, null, batRuns, isStrikerEnd)
        }

        if (nonStriker == null) {
            striker?.let { performCommit(it, true) }
        } else {
            showOutPlayerSelection("Who obstructed the field?") { outP ->
                if (outP == null) return@showOutPlayerSelection
                showDynamicDialog {
                    setTitle("Which end did the obstruction happen?")
                    setItems(arrayOf("Striker's End", "Non-Striker's End")) { _, end ->
                        performCommit(outP, end == 0)
                    }
                    setCancelable(false)
                }
            }
        }
    }


    private fun promptRunsAndOutPlayer(title: String, bt: BallType, callback: (Int, Player, String) -> Unit) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setText("0")

        showDynamicDialog {
            setTitle("Runs completed before out?")
            setView(input)
            setPositiveButton("Next") { d, w ->
                val runs = input.text.toString().toIntOrNull() ?: 0
                showOutPlayerSelection("Who is out?") { outP ->
                    if (outP == null) return@showOutPlayerSelection
                    showFielderSelection("Fielder?", true) { fielder ->
                        callback(runs, outP, fielder)
                    }
                }
            }
        }
    }

    private fun showOutPlayerSelection(title: String, callback: (Player?) -> Unit) {
        val activePlayers = listOfNotNull(striker, nonStriker)
        if (activePlayers.isEmpty()) {
            callback(null)
            return
        }

        val items = activePlayers.map { p ->
            val role = if (p == striker) "(Striker)" else "(Non-Striker)"
            "${p.name} $role"
        }.toTypedArray()

        showDynamicDialog {
            setTitle(title)
            setItems(items) { _, w ->
                callback(activePlayers[w])
            }
        }
    }

    private fun showFielderSelection(title: String, allowNone: Boolean, exclude: List<String> = emptyList(), callback: (String) -> Unit) {
        val bowlingTeam = if (teamABatting) teamBNames else teamANames
        val names = ArrayList<String>()
        if (allowNone) {
            names.add("None")
        }
        bowlingTeam?.filterNotNull()?.let { allNames ->
            names.addAll(allNames.filter { it !in exclude })
        }

        val array = names.toTypedArray()

        showDynamicDialog {
            setTitle(title)
            setItems(array) { d, w -> callback(array[w]) }
        }
    }

    override fun showBatsmanSelectionDialog(isStrikerReplacing: Boolean) {
        val battingTeam = if (teamABatting) teamANames else teamBNames
        
        val available = battingTeam?.mapNotNull { it?.trim() }?.filter { name ->
            val p = getPlayerFromCache(name)
            p != null && !p.isOut && name != striker?.name && name != nonStriker?.name 
        } ?: emptyList()

        if (available.isEmpty()) {
            // Handle "Last Man Stand" transition
            if (isStrikerReplacing && nonStriker != null && !nonStriker!!.isOut) {
                striker = nonStriker
                nonStriker = null
                updateUI()
                return
            }
            if (!isStrikerReplacing && striker != null && !striker!!.isOut) {
                nonStriker = null
                updateUI()
                return
            }

            Toast.makeText(this, "No more batsmen available", Toast.LENGTH_SHORT).show()
            return
        }

        val options = ArrayList(available)
        // Add "None" if we have a partner at the crease OR if we are picking a non-striker
        val hasPartnerAtCrease = if (isStrikerReplacing) (nonStriker != null && !nonStriker!!.isOut) else (striker != null && !striker!!.isOut)
        val canPlayAlone = !isStrikerReplacing || hasPartnerAtCrease
        
        if (canPlayAlone) {
            options.add(0, "None (Play Alone)")
        }

        val array = options.toTypedArray()

        showDynamicDialog {
            setTitle(if (isStrikerReplacing) "Select Striker" else "Select Non-Striker")
            setItems(array) { d, w ->
                val selection = array[w]
                if (selection == "None (Play Alone)") {
                    if (isStrikerReplacing) {
                        striker = nonStriker
                        nonStriker = null
                        // After promoting NS to Striker, ask if we want a new NS
                        showBatsmanSelectionDialog(false)
                        return@setItems
                    } else {
                        nonStriker = null
                    }
                } else {
                    val p = getPlayerFromCache(selection)
                    if (p?.dismissalInfo == "retired hurt") {
                        match?.currentInnings?.playerReturned(p)
                        p.dismissalInfo = "not out"
                    }
                    p?.entryTime = System.currentTimeMillis()
                    if (isStrikerReplacing) {
                        striker = p
                        if (nonStriker == null) {
                            showBatsmanSelectionDialog(false)
                            return@setItems
                        }
                    } else {
                        nonStriker = p
                    }
                }
                updateUI()
            }
            setCancelable(false)
        }
    }

    override fun showRetiredPlayerSelection(isHurt: Boolean) {
        val activePlayers = listOfNotNull(striker, nonStriker)
        if (activePlayers.size == 1) {
            commitRetired(activePlayers[0], isHurt)
            return
        }

        val title = if (isHurt) "Who is retired hurt?" else "Who is retired out?"
        showOutPlayerSelection(title) { outP ->
            if (outP == null) return@showOutPlayerSelection
            commitRetired(outP, isHurt)
        }
    }

    private fun commitRetired(p: Player, isHurt: Boolean) {
        val wasStriker = (p == striker)
        val info = if (isHurt) "retired hurt" else "retired out"
        val msg = if (isHurt) getString(R.string.retired_hurt_msg, p.name) else getString(R.string.retired_out_msg, p.name)

        // Record the event in the Innings (adds dummy ball for Undo)
        // Pass the current bowler's name to avoid the "FIELD" bowler bug
        match?.currentInnings?.recordRetired(p, !isHurt, info, striker?.name, nonStriker?.name, bowler?.name)
        
        // Add commentary
        match?.currentInnings?.addCommentary("FACT", msg, null, "FACT")
        syncUnifiedCommentary()

        // Clear the slot
        if (wasStriker) striker = null else nonStriker = null
        
        updateUI()

        val battingTeam = if (teamABatting) teamANames else teamBNames
        val available = battingTeam?.filterNotNull()?.filter { name ->
            val player = getPlayerFromCache(name)
            player != null && !player.isOut && name != striker?.name && name != nonStriker?.name
        } ?: emptyList()

        if (available.isEmpty() && striker == null && nonStriker == null) {
            // No more batsmen at all -> Trigger completion flow
            checkInningsCompletion(match?.currentInnings!!)
        } else {
            showBatsmanSelectionDialog(wasStriker)
        }
        
        saveMatchToDatabase(isFinished, isAbandoned, true)
    }

    override fun handlePenaltyFlow() {
        if (match == null) return
        val options = arrayOf("Award to Batting Team (Fielding Offence)", "Award to Fielding Team (Batting Offence)")

        showDynamicDialog {
            setTitle("Penalty Award")
            setItems(options) { _, which ->
                val isFieldingOffence = (which == 0)
                showPenaltyRunsInput(isFieldingOffence)
            }
        }
    }

    private fun showPenaltyRunsInput(isFieldingOffence: Boolean) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val quickAddLabel = TextView(this).apply {
            text = "Quick Add Penalty:"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        container.addView(quickAddLabel)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val penaltyOptions = intArrayOf(1, 5, 10)
        
        penaltyOptions.forEach { r ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    setMargins(4, 0, 4, 0)
                }
                text = r.toString()
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    applyPenalty(r, isFieldingOffence)
                    dialogRef[0]?.dismiss()
                }
            }
            btnRow.addView(btn)
        }
        container.addView(btnRow)

        val customLabel = TextView(this).apply {
            text = "Custom Value:"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        container.addView(customLabel)

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("5")
        }
        container.addView(input)

        val offendingTeam = if (isFieldingOffence) match?.currentInnings?.bowlingTeam else match?.currentInnings?.battingTeam

        dialogRef[0] = showDynamicDialog {
            setTitle("Penalty Runs for $offendingTeam")
            setView(container)
            setPositiveButton("Apply") { _, _ ->
                val r = input.text.toString().toIntOrNull() ?: 5
                applyPenalty(r, isFieldingOffence)
            }
            setNegativeButton("Cancel", null)
        }
    }

    private fun applyPenalty(runs: Int, isFieldingOffence: Boolean) {
        val m = match ?: return
        val currentInn = m.currentInnings ?: return
        val isFirstInnings = (m.secondInnings == null)

        if (isFieldingOffence) {
            // Award to current batting team
            currentInn.addPenalty(runs)
            val msg = "Penalty of $runs runs awarded to ${currentInn.battingTeam}."
            currentInn.addCommentary(currentInn.oversDisplay, msg, "P$runs", "FACT")
            commentary.add(0, currentInn.commentary[0]!!)
        } else {
            // Batting team offence -> Award to fielding team
            if (isFirstInnings) {
                // Awarded at start of next innings
                m.pendingPenaltyForSecondInnings += runs
                val msg = "Penalty of $runs runs awarded to ${currentInn.bowlingTeam}. These runs will be added at the start of the next innings."
                currentInn.addCommentary(currentInn.oversDisplay, msg, "P$runs", "FACT")
                commentary.add(0, currentInn.commentary[0]!!)
            } else {
                // Second innings -> Award to first innings team
                val i1 = m.firstInnings ?: return
                i1.addPenalty(runs)
                
                // Update target for second innings
                currentInn.target = (currentInn.target ?: 0) + runs
                
                val msg = "Penalty of $runs runs awarded to ${i1.battingTeam}. The target for ${currentInn.battingTeam} has increased to ${currentInn.target}."
                currentInn.addCommentary(currentInn.oversDisplay, msg, "P$runs", "FACT")
                commentary.add(0, currentInn.commentary[0]!!)
            }
        }
        updateUI()
    }

    override fun handleApplyDLS() {
        val m = match ?: return
        val minOvers = DLSUtility.getMinimumOvers(m.totalOvers)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }
        
        val label = TextView(this).apply {
            text = "Revised Total Overs for Match:"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        val oInput = EditText(this).apply {
            hint = "e.g. $minOvers"
            inputType = InputType.TYPE_CLASS_NUMBER
            // Suggest 60% of original overs, but not less than minOvers
            val suggestion = floor(m.totalOvers * 0.6).toInt().coerceAtLeast(minOvers)
            setText(suggestion.toString())
        }

        val minLabel = TextView(this).apply {
            text = "Min overs for result: $minOvers"
            textSize = 12f
            alpha = 0.7f
            setPadding(0, 8, 0, 0)
        }
        
        container.addView(label)
        container.addView(oInput)
        container.addView(minLabel)

        showDynamicDialog {
            setTitle("Apply DLS Adjustment")
            setView(container)
            setPositiveButton("Calculate") { _, _ ->
                val revisedOvers = oInput.text.toString().toIntOrNull() ?: m.totalOvers
                showDLSExplanationDialog(revisedOvers)
            }
            setNegativeButton("Cancel", null)
        }
    }

    private fun showDLSExplanationDialog(revisedOvers: Int) {
        val m = match ?: return
        val i1 = m.firstInnings ?: return
        val currentInn = m.currentInnings ?: return
        val isFirstInnings = (m.secondInnings == null)
        val minOvers = DLSUtility.getMinimumOvers(m.totalOvers)
        
        // --- DLS LOGIC CALCULATION ---
        val totalMatchOvers = m.totalOvers
        val i1OversBowled = i1.legalBalls / 6
        
        // r1: Resources Team 1 actually had (100% minus what they lost to rain)
        val r1 = (100.0 - m.resourcesLostTeam1).coerceAtLeast(1.0) 
        
        // r2: Resources available to Team 2 for their revised innings
        val r2 = DLSUtility.getResource(revisedOvers, 0, totalMatchOvers)
        
        val team1FinalScore = i1.totalRuns
        val calculatedTarget = DLSUtility.calculateRevisedTarget(team1FinalScore, r1, r2)
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }

        val summaryText = "Resources Used by ${i1.battingTeam}: ${String.format(Locale.US, "%.1f%%", r1)}\n" +
                         "Resources Available to ${currentInn.battingTeam}: ${String.format(Locale.US, "%.1f%%", r2)}\n" +
                         "Match length: $revisedOvers overs"
        
        val tvSummary = TextView(this).apply {
            text = summaryText
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }
        container.addView(tvSummary)

        // Editable Target
        val targetLabel = TextView(this).apply { text = "Revised Target:"; textSize = 12f }
        val etTarget = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(calculatedTarget.toString())
            setPadding(0, 8, 0, 24)
            visibility = if (isFirstInnings) View.GONE else View.VISIBLE
        }
        if (isFirstInnings) targetLabel.visibility = View.GONE
        
        container.addView(targetLabel)
        container.addView(etTarget)

        // Editable Overs
        val oversLabel = TextView(this).apply { text = "Match Overs (Revised):"; textSize = 12f }
        val etOvers = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(revisedOvers.toString())
            setPadding(0, 8, 0, 16)
        }
        container.addView(oversLabel)
        container.addView(etOvers)

        if (revisedOvers < minOvers) {
            val tvWarning = TextView(this).apply {
                text = "WARNING: Below minimum overs ($minOvers) required for a result."
                setTextColor(android.graphics.Color.RED)
                textSize = 12f
                setPadding(0, 8, 0, 0)
            }
            container.addView(tvWarning)
        }

        showDynamicDialog {
            setTitle("DLS Calculation Result")
            setView(container)
            setPositiveButton("Accept & Apply") { _, _ ->
                val finalTarget = etTarget.text.toString().toIntOrNull() ?: calculatedTarget
                val finalOvers = etOvers.text.toString().toIntOrNull() ?: revisedOvers

                if (isFirstInnings) {
                    i1.revisedMaxOvers = finalOvers
                    
                    // Calculate resource loss for Team 1
                    val currentOversLeft = (totalMatchOvers - i1OversBowled).coerceAtLeast(0)
                    val revisedOversLeft = (finalOvers - i1OversBowled).coerceAtLeast(0)
                    
                    val resStop = DLSUtility.getResource(currentOversLeft, i1.totalWickets, totalMatchOvers)
                    val resRestart = DLSUtility.getResource(revisedOversLeft, i1.totalWickets, totalMatchOvers)
                    
                    m.resourcesLostTeam1 += (resStop - resRestart).coerceAtLeast(0.0)
                    
                    // If the new limit is reached, end the innings and lock the target
                    if (i1.legalBalls >= (finalOvers * 6)) {
                        m.revisedTarget = finalTarget
                        checkInningsCompletion(i1)
                    } else {
                        // Innings continues, target will be calculated at start of 2nd innings
                        m.revisedTarget = null
                    }
                } else {
                    currentInn.revisedMaxOvers = finalOvers
                    currentInn.revisedTarget = finalTarget
                }
                
                val msg = if (isFirstInnings) {
                    "DLS Applied: 1st Innings revised to $finalOvers overs."
                } else {
                    "DLS Applied: Match revised to $finalOvers overs. Target: $finalTarget."
                }
                currentInn.addCommentary(currentInn.oversDisplay, msg, null, "FACT")
                
                updateUI()
                Toast.makeText(this@MainActivity, "DLS Applied successfully", Toast.LENGTH_SHORT).show()
            }
            setNegativeButton("Reject", null)
        }
    }

    override fun undoBall() {
        val innings = match?.currentInnings ?: return
        if (innings.balls.isEmpty()) return
        
        val last = innings.balls.last()
        val facingP = getPlayerFromCache(last.batsmanName) ?: return
        val ballBowler = getPlayerFromCache(last.bowlerName)
        val outP = if ((last.isWicket || last.dismissalInfo?.contains("retired") == true) && last.outPlayerName != null) {
            getPlayerFromCache(last.outPlayerName)
        } else null
        
        // Revert Fielding Stats before removing the ball record
        if (last.isWicket && !last.fielderName.isNullOrEmpty()) {
            val fielders = last.fielderName!!.split(" / ")
            val dInfo = last.dismissalInfo ?: ""
            fielders.forEach { fName ->
                val f = getPlayerFromCache(fName.trim())
                if (f != null) {
                    if (dInfo.contains("Caught", ignoreCase = true) || dInfo.startsWith("c ", ignoreCase = true) || dInfo.contains("c & b", ignoreCase = true)) {
                        if (f.catches > 0) f.catches--
                    } else if (dInfo.contains("Stumped", ignoreCase = true) || dInfo.startsWith("st ", ignoreCase = true)) {
                        if (f.stumpings > 0) f.stumpings--
                    } else if (dInfo.contains("Run Out", ignoreCase = true)) {
                        if (f.runOuts > 0) f.runOuts--
                    }
                }
            }
        }

        // Revert Bowling Milestones
        if (ballBowler != null) {
            if (last.runs == 6 && (last.type == BallType.NORMAL || last.type == BallType.NO_BALL)) {
                if (ballBowler.sixesConceded > 0) ballBowler.sixesConceded--
            }
            // Maiden Reversal: If this was the end of a maiden over
            if (innings.legalBalls > 0 && innings.legalBalls % 6 == 0 && overBowlerRuns == last.runs) {
                if (ballBowler.maidens > 0) ballBowler.maidens--
            }
            // Hat-trick Reversal (Check if last 3 balls were wickets by this bowler)
            var streak = 0
            for (i in innings.balls.indices.reversed()) {
                val b = innings.balls[i]
                if (b.bowlerName == ballBowler.name && b.isWicket) streak++ else break
            }
            if (streak == 3) {
                if (ballBowler.hattricks > 0) ballBowler.hattricks--
            }
        }

        innings.undoLastBall(facingP, ballBowler, outP)

        // Restore strikers and active bowler to their EXACT positions before the ball
        striker = getPlayerFromCache(last.batsmanName)
        nonStriker = getPlayerFromCache(last.nonStrikerName)

        // If undoing the first ball of a new over, or crossing back into an over
        bowler = ballBowler

        innings.removeLastBallCommentary()
        syncUnifiedCommentary()

        if (overBallsList.isNotEmpty()) {
            overBallsList.removeAt(overBallsList.size - 1)
            overRuns -= last.runs
            if (last.type == BallType.NORMAL || last.type == BallType.WIDE || last.type == BallType.NO_BALL) {
                overBowlerRuns -= last.runs
            }
            if (last.isWicket) overWickets--
        }

        // If footer is now empty but match has history, reconstruct the previous over
        if (overBallsList.isEmpty() && innings.balls.isNotEmpty()) {
            reconstructCurrentOverState(showLastCompletedIfBoundary = true)
        }
        
        recalculateMatchState()
        updateUI()
    }

    override fun saveMatchToDatabase() {
        saveMatchToDatabase(true, false, false)
        finish()
    }

    override fun saveMatchToDatabase(isFinished: Boolean, isAbandoned: Boolean, isLive: Boolean) {
        saveMatchToDatabase(isFinished, isAbandoned, isLive, false)
    }

    override fun saveMatchToDatabase(
        isFinished: Boolean,
        isAbandoned: Boolean,
        isLive: Boolean,
        isMilestone: Boolean
    ) {
        val m = match ?: return
        
        if (isFinished) {
            this.isFinished = true
            updateNotOutMins(m.currentInnings)
        }
        if (isAbandoned) {
            if (!this.isAbandoned) {
                m.currentInnings?.addCommentary(m.currentInnings?.oversDisplay, getString(R.string.match_abandoned_msg), null, "FACT")
                syncUnifiedCommentary()
            }
            this.isAbandoned = true
            updateNotOutMins(m.currentInnings, keepActive = false)
        }

        if (this.isAbandoned) {
            if (m.secondInnings != null) {
                m.secondInningsEndTime = System.currentTimeMillis()
            } else {
                m.firstInningsEndTime = System.currentTimeMillis()
            }
        }

        if (currentMatchId == null) {
            currentMatchId = UUID.randomUUID().toString()
        }
        val matchId = currentMatchId!!

        val currentTeamA = ArrayList(teamANames ?: emptyList())
        val currentTeamB = ArrayList(teamBNames ?: emptyList())
        val currentPhotos = HashMap(photoMap)
        val currentIds = HashMap(nameToIdMap)
        val entity = MatchEntity.fromMatch(m).apply {
            this.id = matchId
            this.isFinished = isFinished
            this.isAbandoned = isAbandoned
            this.isLive = isLive
            this.teamANames = currentTeamA
            this.teamBNames = currentTeamB
            this.photoMap = currentPhotos
            this.nameToIdMap = currentIds
            this.playedAt = System.currentTimeMillis()
            this.result = calculateMatchResult()
            this.commentaryJson = ArrayList(this@MainActivity.commentary)
            
            // Persist resumption state
            this.currentStrikerName = striker?.name
            this.currentNonStrikerName = nonStriker?.name
            this.currentBowlerName = bowler?.name
            this.nextBatsmanIdx = this@MainActivity.nextBatsmanIdx
            this.isTeamABatting = this@MainActivity.teamABatting
            this.isFreeHitActive = this@MainActivity.isFreeHitActive
            this.currentBowlerInSpell = this@MainActivity.currentBowlerInSpell
            this.strikerEntryTime = striker?.entryTime ?: 0
            this.nonStrikerEntryTime = nonStriker?.entryTime ?: 0
        }
        val now = System.currentTimeMillis()
        val finalStatsList = mutableListOf<Player>()
        
        // 1. Start with the players in our cache (those who batted/bowled)
        playerStatCache.values.forEach { p ->
            val pCopy = p.copy()
            if (!pCopy.isOut && (pCopy.entryTime > 0)) {
                pCopy.minutesPlayed += ((now - pCopy.entryTime) / 1000).toInt()
                pCopy.entryTime = 0 
            }
            finalStatsList.add(pCopy)
        }

        // 2. Add any squad members who aren't in the cache yet (bench players/abandoned matches)
        val allSquadNames = LinkedHashSet<String>()
        teamANames?.filterNotNull()?.forEach { allSquadNames.add(it.trim()) }
        teamBNames?.filterNotNull()?.forEach { allSquadNames.add(it.trim()) }

        allSquadNames.forEach { name ->
            if (finalStatsList.none { it.name == name }) {
                val p = getPlayerFromCache(name) ?: Player(name).apply { this.id = nameToIdMap[name] }
                finalStatsList.add(p)
            }
        }

        // Automatic POTM Calculation if finished
        var bestPlayer: String? = entity.playerOfTheMatchName
        if (isFinished && !isAbandoned) {
            var maxPoints = -1.0
            for (p in finalStatsList) {
                val points = p.runsScored + (p.wicketsTaken * 25.0) + (p.sixes * 2.0) + p.fours.toDouble()
                if (points > maxPoints) {
                    maxPoints = points
                    bestPlayer = p.name
                }
            }
        }
        entity.playerOfTheMatchName = bestPlayer

        AppDatabase.ioExecutor.execute {
            try {
                db?.runInTransaction {
                    db?.matchDao()?.insertMatch(entity)
                    for (p in finalStatsList) {
                        val teamName = if (teamANames?.contains(p.name) == true) teamAName else teamBName
                        val s = PlayerMatchStatEntity.fromPlayer(p, matchId, teamName)
                        db?.statsDao()?.insertStat(s)
                    }
                }
                // Refresh global rankings after database save
                RankingRegistry.refresh(applicationContext, null)
            } catch (e: Exception) {
                Log.e("SAVE_MATCH", "Failed to save match: ${e.message}")
            }
        }
    }

    override fun startNextInnings() {
        updateNotOutMins(match?.firstInnings)
        match?.startSecondInnings()
        teamABatting = !teamABatting
        isFreeHitActive = false
        promptSecondInningsPlayers()
    }

    private fun promptSecondInningsPlayers() {
        val battingTeam = if (teamABatting) teamANames else teamBNames
        val bowlingTeam = if (teamABatting) teamBNames else teamANames
        if (battingTeam == null || bowlingTeam == null) return

        val batters = battingTeam.filterNotNull()
        val bowlers = bowlingTeam.filterNotNull()

        if (batters.isEmpty() || bowlers.isEmpty()) {
            initPlayers()
            finalizeInningsStart()
            return
        }

        val battersArr = batters.toTypedArray()

        showDynamicDialog {
            setTitle(getString(R.string.select_striker))
            setItems(battersArr) { _, w ->
                val sName = battersArr[w]
                striker = getPlayerFromCache(sName)
                striker?.entryTime = System.currentTimeMillis()

                val remaining = batters.filter { it != sName }.toMutableList()
                remaining.add(0, "None (Play Alone)")
                
                val remainingArr = remaining.toTypedArray()

                showDynamicDialog {
                    setTitle(getString(R.string.select_non_striker))
                    setItems(remainingArr) { _, w2 ->
                        val nsName = remainingArr[w2]
                        if (nsName == "None (Play Alone)") {
                            nonStriker = null
                        } else {
                            nonStriker = getPlayerFromCache(nsName)
                            nonStriker?.entryTime = System.currentTimeMillis()
                        }
                        promptBowlerForSecondInnings(bowlers)
                    }
                    setCancelable(false)
                }
            }
            setCancelable(false)
        }
    }

    private fun promptBowlerForSecondInnings(bowlers: List<String>) {
        val bowlersArr = bowlers.toTypedArray()

        showDynamicDialog {
            setTitle("Select Bowler")
            setItems(bowlersArr) { _, w ->
                val bName = bowlersArr[w]
                bowler = getPlayerFromCache(bName)
                finalizeInningsStart()
            }
            setCancelable(false)
        }
    }

    private fun finalizeInningsStart() {
        overRuns = 0
        overBowlerRuns = 0
        overWickets = 0
        overBallsList.clear()
        
        val innings = match?.currentInnings
        if (innings === match?.secondInnings) {
            innings?.addCommentary("0.0", "SECOND INNINGS", null, "FACT")
            
        val introMsg = if (nonStriker != null) {
            "${striker?.name} and ${nonStriker?.name} are at the crease. ${striker?.name} is on strike. ${bowler?.name} will open the attack"
        } else {
            "${striker?.name} is at the crease and will be on strike. ${bowler?.name} will open the attack"
        }
        innings?.addCommentary("0.0", introMsg, null, "FACT")
            
            currentBowlerInSpell = bowler?.name
            syncUnifiedCommentary()
        }

        match?.currentInnings?.let { current ->
            if (current.partnerships.isEmpty()) {
                current.startNewPartnership(striker?.name, nonStriker?.name ?: "N/A")
            }
        }

        updateUI()
        saveMatchToDatabase(false, false, true)
    }

    override fun updateUI() {
        val innings = match?.currentInnings ?: return
        val battingTeam = innings.battingTeam

        tvCurrentTeamName?.text = battingTeam?.uppercase()
        scoreText?.text = "${innings.totalRuns}/${innings.totalWickets}"

        val oversLimit = innings.revisedMaxOvers ?: innings.maxOvers
        oversText?.text = "Overs: ${innings.oversDisplay} / $oversLimit"

        val crr = innings.currentRunRate
        tvRateValue?.text = String.format(Locale.US, "%.2f", crr)

        val result = calculateMatchResult()
        val isDecided = (result != null && result != "IN-PROGRESS") || isFinished || isAbandoned

        if (isDecided) {
            layoutLiveHeader?.visibility = View.GONE
            tvFinalResultBanner?.visibility = View.VISIBLE
            tvFinalResultBanner?.text = result?.uppercase()
        } else {
            layoutLiveHeader?.visibility = View.VISIBLE
            tvFinalResultBanner?.visibility = View.GONE

            if (innings === match?.secondInnings) {
                tvRateLabel?.text = "RRR"
                val target = match?.target ?: 0
                val needed = target - innings.totalRuns
                val ballsLeft = (oversLimit * 6) - innings.legalBalls
                
                if (ballsLeft >= 0) {
                    val rrr = if (ballsLeft > 0) (needed.toDouble() / ballsLeft) * 6 else 0.0
                    tvRateValue?.text = String.format(Locale.US, "%.2f", rrr)
                    tvAnalysisLabel?.text = "$needed RUNS NEEDED IN $ballsLeft BALLS"
                } else {
                    tvAnalysisLabel?.text = "INNINGS OVER"
                }
            } else {
                tvRateLabel?.text = "CRR"
                val projected = (crr * oversLimit).toInt()
                tvAnalysisLabel?.text = "Projected Score at CRR: $projected"
            }
        }

        for (f in supportFragmentManager.fragments) {
            when (f) {
                is LiveScoringFragment -> f.updateUI(striker, nonStriker, bowler, innings, pshipRuns, pshipBalls)
                is ScorecardFragment -> f.updateUI()
                is CommentaryFragment -> f.updateUI()
                is OversFragment -> f.updateUI()
                is SquadFragment -> f.updateUI()
                is MatchInfoFragment -> f.updateUI(match)
            }
        }
        
        viewModel?.let {
            it.striker.value = striker
            it.nonStriker.value = nonStriker
            it.bowler.value = bowler
            it.updateScore(innings.totalRuns, innings.totalWickets)
            it.updateCommentary(commentary)
            it.updateOverBalls(ArrayList(overBallsList))
        }
    }

    override fun updateUIProvider() {
        updateUI()
    }

    override fun playLottie(assetName: String?) {
        liveFragment?.playCelebration(assetName)
    }

    override fun addPlayerToTeam(
        teamName: String?,
        playerName: String?,
        playerId: String?,
        photoUri: String?
    ) {
        if (teamName == null || playerName == null) return
        val isTeamA = (teamName == match?.teamA)
        val targetNames = if (isTeamA) teamANames else teamBNames
        
        if (targetNames?.contains(playerName) == true) {
            Toast.makeText(this, "$playerName already in squad", Toast.LENGTH_SHORT).show()
            return
        }
        
        targetNames?.add(playerName)
        if (playerId != null) nameToIdMap[playerName] = playerId
        if (photoUri != null) photoMap[playerName] = photoUri

        // Persist the player if they are new or have a new photo
        AppDatabase.ioExecutor.execute {
            val ctx = applicationContext ?: return@execute
            val db = getInstance(ctx)
            
            val trimmedName = playerName.trim()
            val existing = if (playerId != null) {
                db.playerDao().getPlayerById(playerId)
            } else {
                db.playerDao().getPlayerByName(trimmedName)
            }

            if (existing == null) {
                val newP = PlayerEntity(trimmedName, "0", photoUri)
                db.playerDao().insertPlayer(newP)
                runOnUiThread { nameToIdMap[playerName] = newP.id }
            } else {
                if (!photoUri.isNullOrEmpty() && photoUri != existing.photoUri) {
                    existing.photoUri = photoUri
                    db.playerDao().insertPlayer(existing)
                }
                runOnUiThread { 
                    nameToIdMap[playerName] = existing.id
                    // Update photo map if a new photo was added during scoring
                    if (!photoUri.isNullOrEmpty()) photoMap[playerName] = photoUri
                }
            }
        }
        
        updateUI()
    }

    override fun showEditCommentaryDialog(context: Context, entry: CommentaryEntry, pos: Int) {
        // Backward compatibility: If baseText is missing, try to extract it from existing text
        if (entry.baseText == null && !entry.text.isNullOrEmpty()) {
            val text = entry.text!!
            if (entry.userNote.isNotEmpty() && text.endsWith(entry.userNote)) {
                entry.baseText = text.substring(0, text.length - entry.userNote.length).trimEnd().trimEnd(',')
            } else {
                entry.baseText = text
            }
        }

        val input = EditText(context).apply {
            hint = "Add Moment..."
            setText(entry.userNote)
            setSelection(entry.userNote.length)
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count == 1 && s?.get(start) == '@') {
                    showMentionSelection(context, input, start)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
            addView(input)
        }

        val dialog = ThemeManager.createDynamicBuilder(context)
            .setTitle("Add Moment! ${entry.over}")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newUserNote = input.text.toString().trim()
                entry.userNote = newUserNote
                
                // Reconstruct the display text
                val base = entry.baseText ?: ""
                if (newUserNote.isNotEmpty()) {
                    entry.text = "$base, $newUserNote"
                } else {
                    entry.text = base
                }
                
                commentaryFragment?.updateUI()
                saveMatchToDatabase(isFinished, isAbandoned, true)
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
        ThemeManager.colorizeDialog(dialog)
    }

    private fun showMentionSelection(context: Context, input: EditText, mentionIndex: Int) {
        val names = getSortedPlayerNames()
        val array = names.toTypedArray()
        
        val dialog = ThemeManager.createDynamicBuilder(context)
            .setTitle("Mention Player")
            .setItems(array) { _, w ->
                val selectedName = array[w]
                val currentText = input.text
                // Replace '@' with selectedName
                currentText.replace(mentionIndex, mentionIndex + 1, selectedName)
                input.setSelection(mentionIndex + selectedName.length)
            }
            .create()
        
        dialog.show()
        ThemeManager.colorizeDialog(dialog)
    }

    private fun getSortedPlayerNames(): List<String> {
        val names = LinkedHashSet<String>()
        striker?.name?.let { names.add(it) }
        nonStriker?.name?.let { names.add(it) }
        bowler?.name?.let { names.add(it) }

        val battingTeamName = match?.currentInnings?.battingTeam
        
        val battingTeam = if (battingTeamName == match?.teamA) teamANames else teamBNames
        val bowlingTeam = if (battingTeamName == match?.teamA) teamBNames else teamANames

        // Bowling team first (excluding already added)
        bowlingTeam?.mapNotNull { it }?.forEach { names.add(it) }
        // Batting team next (excluding already added)
        battingTeam?.mapNotNull { it }?.forEach { names.add(it) }

        return names.toList()
    }

    private fun createCircleBallView(ctx: Context, b: String, isCommentary: Boolean): View {
        val sizeDip = if (isCommentary) 22f else 24f
        val marginDip = if (isCommentary) 6f else 8f
        
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeDip, ctx.resources.displayMetrics).toInt()
        val frame = FrameLayout(ctx).apply {
            val lp = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, marginDip, ctx.resources.displayMetrics).toInt(), 0) }
            layoutParams = lp
        }
        
        val bgColor = when {
            b.endsWith("W") -> "#B71C1C".toColorInt()
            b == "6" -> "#2E7D32".toColorInt()
            b == "4" -> "#EF6C00".toColorInt()
            b.endsWith("WD") || b.endsWith("Wd") -> "#FFB300".toColorInt()
            b.endsWith("NB") -> "#5E35B1".toColorInt()
            b.endsWith("LB") -> "#3949AB".toColorInt()
            b.endsWith("B") -> "#546E7A".toColorInt()
            else -> ThemeManager.getThemeColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant)
        }

        val bg = View(ctx).apply {
            background = androidx.appcompat.content.res.AppCompatResources.getDrawable(ctx, R.drawable.circle_bg_grey)
            backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
        }
        val tv = TextView(ctx).apply {
            tag = "custom_color"
            text = b
            setTextColor(ThemeManager.getContrastColor(bgColor))
            
            // Standardize text size and prevent wrapping
            val baseSize = if (isCommentary) 10f else 11f
            textSize = if (b.length > 2) baseSize - 1.5f else baseSize
            
            setSingleLine(true)
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            if (b == "0" || b == ".") text = "•"
        }
        frame.addView(bg)
        frame.addView(tv)
        return frame
    }

    class CommentaryAdapter(
        private val data: MutableList<CommentaryEntry?>,
        private val provider: ScoringProvider?
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {


        override fun getItemViewType(position: Int): Int {
            val entry = data[position]
            return if (entry?.type == "OVER_SUMMARY") 1 else 0
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 1) {
                SummaryHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_commentary_over_summary, parent, false)
                )
            } else {
                StandardHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_commentary, parent, false)
                )
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val entry = data[position] ?: return

            if (holder is SummaryHolder) {
                bindSummary(holder, entry)
                return
            }

            val standard = holder as StandardHolder

            if ("FACT" == entry.type || "OVER_SUMMARY" == entry.type) {
                standard.itemView.setOnClickListener(null)
                standard.layoutBall.visibility = View.GONE
                standard.tvBallText.text = entry.text
                standard.tvBallText.setTypeface(null, Typeface.BOLD)
                standard.tvBallText.alpha = 0.9f
                
                val text = entry.text ?: ""
                val isHeader = text.contains("FIRST INNINGS") || text.contains("SECOND INNINGS") || text.contains("MATCH OVER")
                
                if (isHeader) {
                    standard.tvBallText.gravity = android.view.Gravity.CENTER
                    standard.tvBallText.layoutParams = (standard.tvBallText.layoutParams as ViewGroup.MarginLayoutParams).apply {
                        marginStart = 0
                    }
                    val baseText = text.replace("-", "").trim()
                    standard.tvBallText.text = "――――― $baseText ―――――"
                    standard.tvBallText.setTextColor(ThemeManager.getSeedColor(standard.itemView.context))
                } else {
                    standard.tvBallText.gravity = android.view.Gravity.START
                    standard.tvBallText.layoutParams = (standard.tvBallText.layoutParams as ViewGroup.MarginLayoutParams).apply {
                        marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, standard.itemView.resources.displayMetrics).toInt()
                    }
                }
            } else {
                // Reset styling for recycled views
                standard.tvBallText.gravity = android.view.Gravity.START
                standard.tvBallText.layoutParams = (standard.tvBallText.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, standard.itemView.resources.displayMetrics).toInt()
                }
                val tv = TypedValue()
                val defaultColor = if (standard.itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true)) tv.data else android.graphics.Color.GRAY
                standard.tvBallText.setTextColor(defaultColor)

                standard.itemView.setOnClickListener { v ->
                    provider?.showEditCommentaryDialog(v.context, entry, standard.adapterPosition)
                }
                standard.layoutBall.visibility = View.VISIBLE
                standard.tvOver.text = entry.over
                standard.tvBallText.text = entry.text
                val h = entry.highlight ?: ""
                standard.tvBallEvent.text = h

                standard.tvBallText.setTypeface(null, Typeface.NORMAL)
                standard.tvBallText.alpha = 1.0f

                val iconLayout = standard.itemView.findViewById<View>(R.id.layoutCommIcon)
                val bgColor = when {
                    entry.highlight?.endsWith("W") == true -> "#B71C1C".toColorInt()
                    entry.highlight == "6" -> "#2E7D32".toColorInt()
                    entry.highlight == "4" -> "#EF6C00".toColorInt()
                    entry.highlight?.endsWith("WD") == true -> "#FFB300".toColorInt()
                    entry.highlight?.endsWith("NB") == true -> "#5E35B1".toColorInt()
                    entry.highlight?.endsWith("LB") == true -> "#3949AB".toColorInt()
                    entry.highlight?.endsWith("B") == true -> "#546E7A".toColorInt()
                    "T" == entry.highlight || "F" == entry.highlight || "E" == entry.highlight || "H" == entry.highlight -> 
                        ThemeManager.getThemeColor(standard.itemView.context, com.google.android.material.R.attr.colorTertiary)
                    else -> ThemeManager.getThemeColor(standard.itemView.context, com.google.android.material.R.attr.colorSurfaceVariant)
                }
                
                iconLayout.backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
                standard.tvBallEvent.setTextColor(ThemeManager.getContrastColor(bgColor))
            }
        }

        private fun bindSummary(holder: SummaryHolder, entry: CommentaryEntry) {
            val text = entry.text ?: return
            try {
                // Data format: OVER|SCORE|BALLS|B1_NAME|B1_R|B1_B|B2_NAME|B2_R|B2_B|BOWLER|O|M|R|W|OVER_RUNS|IS_MAIDEN
                if (!text.contains("|")) {
                    holder.tvOverNum.text = "Over Summary"
                    holder.tvMatchScore.text = ""
                    holder.tvTotalRuns.text = text
                    return
                }

                val parts = text.split('|')
                if (parts.size < 15) return

                holder.tvOverNum.text = "Over ${parts[0]}"
                holder.tvMatchScore.text = parts[1]
                holder.tvTotalRuns.text = parts[14]
                
                // Maiden Check
                if (parts.size > 15 && parts[15] == "1") {
                    holder.tvMaidenBadge.visibility = View.VISIBLE
                    holder.tvOverNum.setTextColor("#B71C1C".toColorInt())
                } else {
                    holder.tvMaidenBadge.visibility = View.GONE
                    holder.tvOverNum.setTextColor(ThemeManager.getSeedColor(holder.itemView.context))
                }

                // Render Balls
                holder.layoutBalls.removeAllViews()
                val balls = parts[2].trim().split(" ").filter { it.isNotEmpty() }
                balls.forEach { b ->
                    val act = holder.itemView.context as? MainActivity
                    if (act != null) {
                        holder.layoutBalls.addView(act.createCircleBallView(holder.itemView.context, b, true))
                    } else {
                        // Fallback if context is not MainActivity
                        holder.layoutBalls.addView(createDefaultSummaryBallView(holder.itemView.context, b))
                    }
                }

                holder.layoutBatters.removeAllViews()
                addSummaryBatterRow(holder.layoutBatters, parts[3], parts[4], parts[5])
                if (parts[6] != "null" && parts[6].isNotEmpty() && parts[6] != "-" && parts[6] != "Unknown") {
                    addSummaryBatterRow(holder.layoutBatters, parts[6], parts[7], parts[8])
                }

                holder.tvBowlerName.text = parts[9]
                holder.tvBowlerStats.text = "${parts[10]}-${parts[11]}-${parts[12]}-${parts[13]}"

            } catch (_: Exception) {
                holder.tvOverNum.text = "Summary"
                holder.tvMatchScore.text = ""
            }
        }

        private fun createDefaultSummaryBallView(ctx: Context, b: String): View {
            val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22f, ctx.resources.displayMetrics).toInt()
            val frame = FrameLayout(ctx).apply {
                val lp = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 6, 0) }
                layoutParams = lp
            }
            
            val bgColor = when {
                b.endsWith("W") -> "#B71C1C".toColorInt()
                b == "6" -> "#2E7D32".toColorInt()
                b == "4" -> "#EF6C00".toColorInt()
                b.endsWith("WD") || b.endsWith("Wd") -> "#FFB300".toColorInt()
                b.endsWith("NB") -> "#5E35B1".toColorInt()
                b.endsWith("LB") -> "#3949AB".toColorInt()
                b.endsWith("B") -> "#546E7A".toColorInt()
                else -> ThemeManager.getThemeColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant)
            }

            val bg = View(ctx).apply {
                background = androidx.appcompat.content.res.AppCompatResources.getDrawable(ctx, R.drawable.circle_bg_grey)
                backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
            }
            val tv = TextView(ctx).apply {
                tag = "custom_color"
                text = b
                setTextColor(ThemeManager.getContrastColor(bgColor))
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                if (b == "0" || b == ".") text = "•"
            }
            frame.addView(bg)
            frame.addView(tv)
            return frame
        }

        private fun addSummaryBatterRow(container: LinearLayout, name: String, r: String, b: String) {
            val ctx = container.context
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 2, 0, 2)
            }
            val tvName = TextView(ctx).apply {
                text = name
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ThemeManager.getSeedColor(ctx))
            }
            val tvStats = TextView(ctx).apply {
                text = "$r ($b)"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            }
            row.addView(tvName)
            row.addView(tvStats)
            container.addView(row)
        }

        override fun getItemCount(): Int = data.size

        class StandardHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvOver: TextView = v.findViewById(R.id.tvCommOver)
            val tvBallEvent: TextView = v.findViewById(R.id.tvCommIconText)
            val tvBallText: TextView = v.findViewById(R.id.tvCommText)
            val layoutBall: View = v.findViewById(R.id.layoutCommLeftColumn)
        }

        class SummaryHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvOverNum: TextView = v.findViewById(R.id.tvSummaryOverNum)
            val tvMaidenBadge: TextView = v.findViewById(R.id.tvSummaryMaidenBadge)
            val tvMatchScore: TextView = v.findViewById(R.id.tvSummaryMatchScore)
            val layoutBalls: LinearLayout = v.findViewById(R.id.layoutSummaryBalls)
            val tvTotalRuns: TextView = v.findViewById(R.id.tvSummaryTotalRuns)
            val layoutBatters: LinearLayout = v.findViewById(R.id.layoutSummaryBatters)
            val tvBowlerName: TextView = v.findViewById(R.id.tvSummaryBowlerName)
            val tvBowlerStats: TextView = v.findViewById(R.id.tvSummaryBowlerStats)
        }
    }
}

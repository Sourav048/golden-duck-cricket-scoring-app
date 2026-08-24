package com.example.scoring

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.scoring.AppDatabase.Companion.getInstance
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MatchDetailsActivity : BaseActivity(), ScoringProvider {
    private var viewModel: ScoringViewModel? = null
    private var viewPager: ViewPager2? = null
    private var tabLayout: TabLayout? = null
    private var scoreText: TextView? = null
    private var oversText: TextView? = null
    private var tvCurrentTeamName: TextView? = null
    private var tvAnalysisLabel: TextView? = null
    private var tvRateValue: TextView? = null
    private var tvRateLabel: TextView? = null
    private var layoutLiveHeader: View? = null
    private var currentMatchId: String? = null

    // CELEBRATION COLORS — mirrored from MainActivity
    private var colorsWicket: IntArray = intArrayOf()
    private var colorsSix: IntArray = intArrayOf()
    private var colorsFour: IntArray = intArrayOf()
    private var colorsMilestone: IntArray = intArrayOf()
    private var colorsMatchWin: IntArray = intArrayOf()

    // Track ball count so we only fire confetti on genuinely new balls
    private var lastKnownBallCount: Int = -1

    override var match: Match? = null
        private set
    private var matchEntity: MatchEntity? = null
    private var statsEntities: List<PlayerMatchStatEntity?>? = null
    private var db: AppDatabase? = null
    private val playerStatCache: MutableMap<String?, Player> = HashMap()
    override var striker: Player? = null
    override var nonStriker: Player? = null
    override var bowler: Player? = null
    override var isRuleRunsOnWide: Boolean = false
    override var isRuleFreeHit: Boolean = false
    override var isRuleRunsOnBye: Boolean = false
    override var isRuleOverthrow: Boolean = false
    override var teamANames: ArrayList<String?>? = null
    override var teamBNames: ArrayList<String?>? = null
    override val nameToIdMap: MutableMap<String?, String?> = HashMap()
    override val photoMap: MutableMap<String?, String?> = HashMap()
    override val commentary: MutableList<CommentaryEntry?> = ArrayList()
    override val overBallsList: MutableList<String?> = ArrayList()
    override var isScorer: Boolean = false
    override var isFinished: Boolean = false
    override var isAbandoned: Boolean = false
    override var isFreeHitActive: Boolean = false
    override val pshipRuns: Int = 0
    override val pshipBalls: Int = 0

    // Broadcast Dialog State
    private var lastDialogOverNum: Int = -1
    private var lastDialogInningsNum: Int = 0
    private var isMatchFinishedDialogShown: Boolean = false
    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private var currentActiveDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = getInstance(this)
        viewModel = ViewModelProvider(this)[ScoringViewModel::class.java]
        viewPager = findViewById(R.id.viewPagerScoring)
        tabLayout = findViewById(R.id.tabLayoutScoring)
        
        scoreText = findViewById(R.id.scoreText)
        oversText = findViewById(R.id.oversText)
        tvCurrentTeamName = findViewById(R.id.tvCurrentTeamName)
        tvAnalysisLabel = findViewById(R.id.tvAnalysisLabel)
        tvRateValue = findViewById(R.id.tvRateValue)
        tvRateLabel = findViewById(R.id.tvRateLabel)
        layoutLiveHeader = findViewById(R.id.layoutLiveHeader)

        // Init confetti colors (same palette as scorer)
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
        colorsMatchWin  = intArrayOf(-0xff01, -0xff01, -0xff0001, -0xff0100)

        // Hide scoring header parts initially
        tvAnalysisLabel?.visibility = View.GONE
        tvRateValue?.visibility = View.GONE
        tvRateLabel?.visibility = View.GONE
        findViewById<View>(R.id.cardProjected)?.visibility = View.GONE
        findViewById<View>(R.id.layoutMiddleRate)?.visibility = View.GONE

        val matchId = intent.getStringExtra("matchId")
        if (matchId != null) {
            currentMatchId = matchId
            loadMatch(matchId)
        } else {
            finish()
        }
    }

    private fun loadMatch(id: String) {
        AppDatabase.ioExecutor.execute {
            val entity = db?.matchDao()?.getMatchById(id)
            if (entity == null) {
                runOnUiThread {
                    Toast.makeText(this, "Match details not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@execute
            }

            val m = entity.toMatch()
            val stats = db?.statsDao()?.getStatsByMatch(id) ?: emptyList()

            // Initialize these on background thread so sanitize() logic works correctly
            this.match = m
            this.isFinished = entity.isFinished
            this.isAbandoned = entity.isAbandoned

            runOnUiThread {
                this.matchEntity = entity
                this.statsEntities = stats
                
                val currentA = ArrayList<String?>()
                entity.teamANames?.forEach { it?.let { name -> currentA.add(name.trim()) } }
                val currentB = ArrayList<String?>()
                entity.teamBNames?.forEach { it?.let { name -> currentB.add(name.trim()) } }
                
                teamANames = currentA
                teamBNames = currentB
                
                photoMap.putAll(entity.photoMap?.filterKeys { it != null }?.mapKeys { it.key!!.trim() } ?: emptyMap())
                nameToIdMap.putAll(entity.nameToIdMap?.filterKeys { it != null }?.mapKeys { it.key!!.trim() } ?: emptyMap())

                playerStatCache.clear()
                for (s in stats) {
                    if (s == null) continue
                    val p = s.toPlayer()
                    p.entryTime = 0
                    val trimmed = p.name?.trim() ?: continue
                    playerStatCache[trimmed] = p
                    
                    // ID-FIRST SQUAD RECONSTRUCTION: Prevent duplicates by matching by ID
                    val pId = s.playerId
                    val sTeam = s.teamName?.trim()
                    
                    // If this ID is already in the list under a DIFFERENT name (old name), swap it!
                    fun resolveSquadName(id: String?, list: MutableList<String?>) {
                        if (id == null) return
                        val index = list.indexOfFirst { name -> nameToIdMap[name?.trim()] == id }
                        if (index != -1) {
                            val oldSquadName = list[index]
                            if (oldSquadName != trimmed) {
                                list[index] = trimmed // UNIVERSAL NAME SWAP
                            }
                        } else {
                            list.add(trimmed)
                        }
                    }

                    if (sTeam.equals(entity.teamAName?.trim(), ignoreCase = true)) {
                        resolveSquadName(pId, currentA)
                    } else if (sTeam.equals(entity.teamBName?.trim(), ignoreCase = true)) {
                        resolveSquadName(pId, currentB)
                    }
                }
                
                if (currentA.isEmpty() || currentB.isEmpty()) {
                    m.firstInnings?.balls?.forEach { b ->
                        b.batsmanName?.trim()?.let { if (!currentA.contains(it) && !currentB.contains(it)) currentA.add(it) }
                        b.bowlerName?.trim()?.let { if (!currentA.contains(it) && !currentB.contains(it)) currentA.add(it) }
                    }
                }

                // Update header
                val now = System.currentTimeMillis()
                val diff = now - entity.lastScorerPulse
                val isPulseActive = diff in -60000..120000
                val isLiveMatch = entity.isLive && isPulseActive && !entity.isFinished && !entity.isAbandoned
                
                if (isLiveMatch) {
                    layoutLiveHeader?.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.tvFinalResultBanner)?.visibility = View.GONE
                    
                    // Populate ViewModel for LiveScoringFragment
                    val s = getPlayerFromCache(entity.currentStrikerName)
                    val ns = getPlayerFromCache(entity.currentNonStrikerName)
                    val b = getPlayerFromCache(entity.currentBowlerName)
                    
                    viewModel?.apply {
                        this.teamANames = currentA
                        this.teamBNames = currentB
                        this.nameToIdMap.putAll(entity.nameToIdMap ?: emptyMap())
                        this.photoMap.putAll(entity.photoMap ?: emptyMap())
                        this.isScorer = false
                        this.isFinished = false
                        this.isAbandoned = false
                        update(m, s, ns, b, this@MatchDetailsActivity.commentary, ArrayList(overBallsList))
                    }
                } else {
                    layoutLiveHeader?.visibility = View.GONE
                    findViewById<TextView>(R.id.tvFinalResultBanner)?.apply {
                        visibility = View.VISIBLE
                        val baseResult = entity.result?.uppercase() ?: "MATCH COMPLETED"
                        val isPlaceholder = baseResult == "IN-PROGRESS" || baseResult == "LIVE" || baseResult == "PAUSED" || baseResult == "MATCH COMPLETED" || baseResult == "MATCH FINISHED"
                        text = if (entity.isFinished && isPlaceholder) {
                            setTextColor(ThemeManager.getThemeColor(context, com.google.android.material.R.attr.colorOnPrimary))
                            calculateMatchResult()?.uppercase() ?: baseResult
                        } else if (!entity.isFinished && !entity.isAbandoned) {
                            if (entity.isLive && isPulseActive) {
                                setTextColor(context.getColor(R.color.card_red))
                                "LIVE"
                            } else {
                                setTextColor(ThemeManager.getThemeColor(context, com.google.android.material.R.attr.colorOnPrimary))
                                "IN-PROGRESS"
                            }
                        } else {
                            setTextColor(ThemeManager.getThemeColor(context, com.google.android.material.R.attr.colorOnPrimary))
                            baseResult
                        }
                        gravity = android.view.Gravity.CENTER
                    }
                }
            }

            // Reconstruct and sanitize commentary (on background thread)
            this.commentary.clear()
            
            fun sanitize(list: List<CommentaryEntry?>?): List<CommentaryEntry> {
                val matchResult = calculateMatchResult()?.uppercase()
                return list?.filterNotNull()?.map { entry ->
                    var newText = entry.text
                        ?.replace(" and null", "")
                        ?.replace("null and ", "")
                        ?.replace("null", "Unknown")
                    
                    val textUpper = newText?.uppercase()
                    val isPlaceholder = textUpper == "IN-PROGRESS" || textUpper == "LIVE" || textUpper == "PAUSED" || textUpper == "MATCH COMPLETED" || textUpper == "MATCH FINISHED"
                    if (entity.isFinished && isPlaceholder) {
                        newText = matchResult ?: "MATCH COMPLETED"
                    }
                    
                    CommentaryEntry(entry.over, newText, entry.highlight, entry.type).apply {
                        this.baseText = newText
                    }
                } ?: emptyList()
            }

            val isHistorical = entity.isFinished || entity.isAbandoned
            if (!entity.commentaryJson.isNullOrEmpty()) {
                val list = sanitize(entity.commentaryJson)
                commentary.addAll(if (isHistorical) list.asReversed() else list)
            } else {
                val list2 = sanitize(entity.commentaryJson2)
                val list1 = sanitize(entity.commentaryJson1)
                if (isHistorical) {
                    commentary.addAll(list1.asReversed())
                    commentary.addAll(list2.asReversed())
                } else {
                    commentary.addAll(list2)
                    commentary.addAll(list1)
                }
            }

            runOnUiThread {
                setupTabs()
                updateUI()
                
                // Start observing Room for live updates (viewer mode only).
                // Every time the Firestore sync writes a new ball to Room, this fires
                // and pushes the updated match into ScoringViewModel so LiveScoringFragment
                // and all other fragments refresh automatically — no navigation needed.
                // ALWAYS start observing if the match is not finished.
                // This ensures that if a "Paused" match becomes "Live" while the viewer
                // is on this screen, the UI will wake up and show the Live tab/updates immediately.
                val isPotentiallyLive = !(matchEntity?.isFinished ?: false) &&
                        !(matchEntity?.isAbandoned ?: false) &&
                        !isScorer
                if (isPotentiallyLive) {
                    startLiveObserver(id)
                }
                
                // AUTO-REPAIR: If this is an old match missing cloud stats, sync it now
                val gId = GullySyncManager.getCurrentGullyId(this)
                if (gId != null && entity.compressedPayload == null) {
                    GullySyncManager.syncMatchToCloud(gId, entity)
                }
            }
        }
    }

    private fun triggerConfetti(colors: IntArray) {
        findViewById<View?>(android.R.id.content)?.let {
            CommonConfetti.rainingConfetti(it as ViewGroup, colors).oneShot()
        }
    }

    private fun startLiveObserver(matchId: String) {
        db?.matchDao()?.getMatchByIdLive(matchId)?.observe(this) { entity ->
            // Ignore if we become the scorer
            if (isScorer || entity == null) return@observe
            
            // If match has ended and we already showed the final dialog, ignore updates
            if ((entity.isFinished || entity.isAbandoned) && isMatchFinishedDialogShown) return@observe
            
            val now = System.currentTimeMillis()
            val diff = now - entity.lastScorerPulse
            val isPulseActive = diff in -60000..120000
            val isActuallyLive = entity.isLive && isPulseActive
            
            // If it's not live, show the banner instead of an empty space
            if (!isActuallyLive) {
                layoutLiveHeader?.visibility = View.GONE
                findViewById<TextView>(R.id.tvFinalResultBanner)?.apply {
                    visibility = View.VISIBLE
                    text = if (entity.isFinished) (entity.result ?: "MATCH COMPLETED") else "IN-PROGRESS"
                    setTextColor(ThemeManager.getThemeColor(context, com.google.android.material.R.attr.colorOnPrimary))
                }
            } else {
                layoutLiveHeader?.visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvFinalResultBanner)?.visibility = View.GONE
            }

            // Rebuild the Match object from the updated entity (background thread)
            AppDatabase.ioExecutor.execute {
                val m = entity.toMatch()
                val stats = db?.statsDao()?.getStatsByMatch(matchId) ?: emptyList()

                runOnUiThread {
                    this.match = m
                    this.matchEntity = entity
                    this.statsEntities = stats
                    this.isFinished = entity.isFinished
                    this.isAbandoned = entity.isAbandoned

                    // Rebuild player cache with fresh stats
                    playerStatCache.clear()
                    for (s in stats) {
                        if (s == null) continue
                        val p = s.toPlayer()
                        p.entryTime = 0
                        p.name?.trim()?.let { playerStatCache[it] = p }
                    }

                    // Rebuild commentary
                    commentary.clear()
                    val isHistorical = entity.isFinished || entity.isAbandoned
                    val raw = if (!entity.commentaryJson.isNullOrEmpty()) {
                        val list = entity.commentaryJson
                            ?.filterNotNull()
                            ?.map { CommentaryEntry(it.over, it.text, it.highlight, it.type) }
                            ?: emptyList()
                        if (isHistorical) list.asReversed() else list
                    } else {
                        val c1 = entity.commentaryJson1?.filterNotNull() ?: emptyList()
                        val c2 = entity.commentaryJson2?.filterNotNull() ?: emptyList()
                        if (isHistorical) {
                            (c1.asReversed() + c2.asReversed()).map { CommentaryEntry(it.over, it.text, it.highlight, it.type) }
                        } else {
                            (c2 + c1).map { CommentaryEntry(it.over, it.text, it.highlight, it.type) }
                        }
                    }
                    commentary.addAll(raw)

                    // Reconstruct over-ball display list
                    overBallsList.clear()
                    val innings = m.currentInnings
                    if (innings != null && innings.balls.isNotEmpty()) {
                        val allBalls = innings.balls
                        val totalLegal = innings.legalBalls
                        
                        // If the very last ball completed an over (6, 12, 18...), 
                        // we keep showing that completed over.
                        // The moment the NEXT ball (even a wide) starts, 
                        // totalLegal/6 will point to the new over and we'll skip the old ones.
                        val isOverJustFinished = totalLegal > 0 && totalLegal % 6 == 0 && 
                                (allBalls.lastOrNull()?.let { 
                                    it.type == BallType.NORMAL || it.type == BallType.BYE || it.type == BallType.LEG_BYE 
                                } ?: false)
                        
                        val legalToSkip = if (isOverJustFinished) {
                            totalLegal - 6
                        } else {
                            (totalLegal / 6) * 6
                        }
                        
                        var skippedLegal = 0
                        for (b in allBalls) {
                            val isLegal = b.type == BallType.NORMAL || b.type == BallType.BYE || b.type == BallType.LEG_BYE
                            if (skippedLegal < legalToSkip) {
                                if (isLegal) skippedLegal++
                                continue
                            }

                            val widePenalty = if (isRuleRunsOnWide) 1 else 0
                            val nbPenalty = if (isRuleRunsOnWide) 1 else 0
                            val event = when {
                                b.isWicket -> if (b.runs > (if (b.type == BallType.WIDE) widePenalty else if (b.type == BallType.NO_BALL) nbPenalty else 0)) "${b.runs}W" else "W"
                                b.type == BallType.WIDE -> "WD${if (b.runs > widePenalty) b.runs - widePenalty else ""}".trimEnd()
                                b.type == BallType.NO_BALL -> "NB${if (b.runs > nbPenalty) b.runs - nbPenalty else ""}".trimEnd()
                                b.type == BallType.BYE -> "B${b.runs}"
                                b.type == BallType.LEG_BYE -> "LB${b.runs}"
                                b.runs == 0 -> "0"
                                else -> b.runs.toString()
                            }
                            overBallsList.add(event)
                        }
                    }

                    // Push fresh data into ScoringViewModel — LiveScoringFragment observes this
                    this.striker = getPlayerFromCache(entity.currentStrikerName)
                    this.nonStriker = getPlayerFromCache(entity.currentNonStrikerName)
                    this.bowler = getPlayerFromCache(entity.currentBowlerName)
                    viewModel?.update(m, this.striker, this.nonStriker, this.bowler, commentary, ArrayList(overBallsList))

                    // Refresh header score and all child fragments
                    updateUI()

                    // ── VIEWER BROADCAST DIALOGS ──────────────────────────────────
                    if (!isScorer) {
                        val currentInn = m.currentInnings
                        if (currentInn != null) {
                            // 1. Match Finished
                            if (entity.isFinished && !isMatchFinishedDialogShown) {
                                isMatchFinishedDialogShown = true
                                showViewerMatchFinished()
                            }
                            // 2. First Innings Complete (Only show for 1st innings)
                            else if (m.secondInnings == null && currentInn.isComplete && lastDialogInningsNum < 1) {
                                lastDialogInningsNum = 1
                                showViewerInningsComplete(currentInn)
                            }
                            // 3. Over Summary
                            else if (currentInn.legalBalls > 0 && currentInn.legalBalls % 6 == 0 && !currentInn.isComplete) {
                                val overNum = currentInn.legalBalls / 6
                                if (lastDialogOverNum < overNum) {
                                    lastDialogOverNum = overNum
                                    
                                    val matchScore = "${currentInn.totalRuns}-${currentInn.totalWickets}"
                                    var oRuns = 0
                                    var legalInThisOver = 0
                                    val ballsInOver = mutableListOf<String>()
                                    for (i in currentInn.balls.indices.reversed()) {
                                        val ball = currentInn.balls[i]
                                        val isLegal = ball.type == BallType.NORMAL || ball.type == BallType.BYE || ball.type == BallType.LEG_BYE
                                        
                                        val widePenalty = if (isRuleRunsOnWide) 1 else 0
                                        val nbPenalty = if (isRuleRunsOnWide) 1 else 0
                                        val event = when {
                                            ball.isWicket -> if (ball.runs > (if (ball.type == BallType.WIDE) widePenalty else if (ball.type == BallType.NO_BALL) nbPenalty else 0)) "${ball.runs}W" else "W"
                                            ball.type == BallType.WIDE -> "WD${if (ball.runs > widePenalty) ball.runs - widePenalty else ""}".trimEnd()
                                            ball.type == BallType.NO_BALL -> "NB${if (ball.runs > nbPenalty) ball.runs - nbPenalty else ""}".trimEnd()
                                            ball.type == BallType.BYE -> "B${ball.runs}"
                                            ball.type == BallType.LEG_BYE -> "LB${ball.runs}"
                                            ball.runs == 0 -> "0"
                                            else -> ball.runs.toString()
                                        }
                                        ballsInOver.add(0, event)
                                        oRuns += ball.runs
                                        if (isLegal) legalInThisOver++
                                        if (legalInThisOver == 6) break
                                    }
                                    showViewerOverSummary(overNum, matchScore, oRuns, ballsInOver)
                                }
                            }
                        }
                    }
                    // ── end viewer dialogs ──────────────────────────────────────────

                    // ── CONFETTI for live viewer ─────────────────────────────────────
                    // Only fire on a genuinely new ball (total ball count increased)
                    val totalBalls = m.firstInnings?.balls?.size?.plus(m.secondInnings?.balls?.size ?: 0)
                        ?: m.currentInnings?.balls?.size ?: 0
                    if (totalBalls > lastKnownBallCount && lastKnownBallCount >= 0) {
                        val currentInnings = m.currentInnings
                        val lastBall = currentInnings?.balls?.lastOrNull()
                        if (lastBall != null) {
                            val runs = lastBall.runs
                            val isWicket = lastBall.isWicket

                            when {
                                isWicket -> {
                                    triggerConfetti(colorsWicket)

                                    // Wicket haul (3 or 5 wickets for same bowler)
                                    val bowlerName = lastBall.bowlerName
                                    if (bowlerName != null) {
                                        val bowlerWickets = currentInnings.balls.count { b ->
                                            b.bowlerName == bowlerName && b.isWicket &&
                                            !b.dismissalInfo.toString().contains("Run Out", ignoreCase = true) &&
                                            !b.dismissalInfo.toString().contains("Obstruct", ignoreCase = true)
                                        }
                                        if (bowlerWickets == 3 || bowlerWickets == 5) {
                                            triggerConfetti(colorsMatchWin)
                                        }

                                        // Hat-trick: last 3 bowler-credited balls all wickets
                                        val bowlerBalls = currentInnings.balls.filter { it.bowlerName == bowlerName }
                                        if (bowlerBalls.size >= 3) {
                                            val last3 = bowlerBalls.takeLast(3)
                                            val allWickets = last3.all { b ->
                                                b.isWicket &&
                                                !b.dismissalInfo.toString().contains("Run Out", ignoreCase = true) &&
                                                !b.dismissalInfo.toString().contains("Obstruct", ignoreCase = true)
                                            }
                                            // Only fire if the 4th-last was NOT a wicket (true hat-trick start)
                                            val fourthLast = bowlerBalls.getOrNull(bowlerBalls.size - 4)
                                            val fourthWasWicket = fourthLast?.isWicket == true &&
                                                !fourthLast.dismissalInfo.toString().contains("Run Out", ignoreCase = true) &&
                                                !fourthLast.dismissalInfo.toString().contains("Obstruct", ignoreCase = true)
                                            if (allWickets && !fourthWasWicket) {
                                                triggerConfetti(colorsMatchWin)
                                            }
                                        }
                                    }
                                }
                                runs == 6 -> triggerConfetti(colorsSix)
                                runs == 4 -> triggerConfetti(colorsFour)
                            }

                            // Team milestone (50, 100, 150 …)
                            if (!isWicket) {
                                val total = currentInnings.totalRuns
                                val prevTotal = total - runs
                                if (total >= 50) {
                                    val milestone = (total / 50) * 50
                                    if (prevTotal < milestone) triggerConfetti(colorsMilestone)
                                }
                            }

                            // Batting milestone (30, 50, 80, 100) for the batter who faced this ball
                            if (!isWicket && lastBall.type == BallType.NORMAL) {
                                val batsmanName = lastBall.batsmanName?.trim()
                                if (batsmanName != null) {
                                    val batterRuns = currentInnings.balls
                                        .filter { it.batsmanName?.trim() == batsmanName && it.type == BallType.NORMAL && !it.isWicket }
                                        .sumOf { it.runs }
                                    val prevBatterRuns = batterRuns - runs
                                    val batMilestone = when {
                                        batterRuns >= 100 && prevBatterRuns < 100 -> true
                                        batterRuns >= 80  && prevBatterRuns < 80  -> true
                                        batterRuns >= 50  && prevBatterRuns < 50  -> true
                                        batterRuns >= 30  && prevBatterRuns < 30  -> true
                                        else -> false
                                    }
                                    if (batMilestone) triggerConfetti(colorsMilestone)
                                }
                            }
                        }
                    }
                    if (lastKnownBallCount < 0) lastKnownBallCount = totalBalls  // seed on first update
                    else lastKnownBallCount = totalBalls
                    // ── end confetti ─────────────────────────────────────────────────
                }
            }
        }
    }

    private fun setupTabs() {
        if (viewPager?.adapter != null) return

        val currentMatch = matchEntity
        val currentStats = statsEntities?.toMutableList()
        
        val now = System.currentTimeMillis()
        val diff = now - (currentMatch?.lastScorerPulse ?: 0L)
        val isPulseActive = diff in -60000..120000
        val isLive = currentMatch?.isLive == true && isPulseActive && !currentMatch.isFinished && !currentMatch.isAbandoned

        viewPager?.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                if (isLive) {
                    return when (position) {
                        0 -> MatchInfoFragment()
                        1 -> LiveScoringFragment()
                        2 -> ScorecardFragment()
                        3 -> CommentaryFragment()
                        4 -> OversFragment()
                        else -> SquadFragment()
                    }
                }
                return when (position) {
                    0 -> MatchInfoFragment()
                    1 -> MatchSummaryFragment().apply { setData(currentMatch, currentStats) }
                    2 -> ScorecardFragment()
                    3 -> CommentaryFragment()
                    4 -> OversFragment()
                    else -> SquadFragment()
                }
            }
            override fun getItemCount(): Int = if (isLive) 6 else 6
            override fun getItemId(position: Int): Long = position.toLong()
            override fun containsItem(itemId: Long): Boolean = itemId in 0..5
        }

        viewPager?.offscreenPageLimit = 5

        val tabs = tabLayout ?: return
        val pager = viewPager ?: return

        TabLayoutMediator(tabs, pager) { tab, position ->
            if (isLive) {
                tab.text = when (position) {
                    0 -> "Info"
                    1 -> "Live"
                    2 -> "Scorecard"
                    3 -> "Commentary"
                    4 -> "Overs"
                    else -> "Squad"
                }
            } else {
                tab.text = when (position) {
                    0 -> "Info"
                    1 -> "Summary"
                    2 -> "Scorecard"
                    3 -> "Commentary"
                    4 -> "Overs"
                    else -> "Squad"
                }
            }
        }.attach()

        if (isLive) {
            viewPager?.setCurrentItem(1, false) // open on Live tab
        }
    }

    override fun updateUI() {
        val m = match ?: return
        val innings = m.currentInnings ?: return
        val battingTeam = innings.battingTeam

        tvCurrentTeamName?.text = battingTeam?.uppercase()
        scoreText?.text = "${innings.totalRuns}/${innings.totalWickets}"

        val oversLimit = innings.revisedMaxOvers ?: innings.maxOvers
        oversText?.text = "Overs: ${innings.oversDisplay} / $oversLimit"

        val crr = innings.currentRunRate
        tvRateValue?.text = String.format(java.util.Locale.US, "%.2f", crr)

        // Show CRR/RRR and Projected/Needed label for live viewer (same as scorer)
        tvRateValue?.visibility = View.VISIBLE
        tvRateLabel?.visibility = View.VISIBLE
        findViewById<View>(R.id.layoutMiddleRate)?.visibility = View.VISIBLE
        findViewById<View>(R.id.cardProjected)?.visibility = View.VISIBLE

        if (innings === m.secondInnings) {
            tvRateLabel?.text = "RRR"
            val target = m.target
            val needed = target - innings.totalRuns
            val ballsLeft = (oversLimit * 6) - innings.legalBalls
            if (ballsLeft >= 0) {
                val rrr = if (ballsLeft > 0) (needed.toDouble() / ballsLeft) * 6 else 0.0
                tvRateValue?.text = String.format(java.util.Locale.US, "%.2f", rrr)
                tvAnalysisLabel?.text = "$needed RUNS NEEDED IN $ballsLeft BALLS"
            } else {
                tvAnalysisLabel?.text = "INNINGS OVER"
            }
        } else {
            tvRateLabel?.text = "CRR"
            val projected = (crr * oversLimit).toInt()
            tvAnalysisLabel?.text = "Projected Score at CRR: $projected"
        }
        tvAnalysisLabel?.visibility = View.VISIBLE

        for (f in supportFragmentManager.fragments) {
            when (f) {
                is InningsScorecardFragment -> f.updateUI()
                is ScorecardFragment -> f.updateUI()
                is MatchInfoFragment -> f.updateUI(match)
                is CommentaryFragment -> f.updateUI()
                is MatchSummaryFragment -> f.setData(matchEntity, statsEntities?.toMutableList())
                is OversFragment -> f.updateUI()
                is SquadFragment -> f.updateUI()
            }
        }
    }

    override fun updateUIProvider() { updateUI() }

    private fun showViewerOverSummary(overNum: Int, matchScore: String, overRuns: Int, ballList: List<String>) {
        if (isFinishing || isDestroyed) return
        
        val view = layoutInflater.inflate(R.layout.dialog_over_summary, null)
        
        val ordinal = when {
            overNum % 100 in 11..13 -> "${overNum}th"
            overNum % 10 == 1 -> "${overNum}st"
            overNum % 10 == 2 -> "${overNum}nd"
            overNum % 10 == 3 -> "${overNum}rd"
            else -> "${overNum}th"
        }
        view.findViewById<TextView>(R.id.tvOverCompleteTitle).text = "$ordinal Over Complete"

        view.findViewById<TextView>(R.id.tvOverLabel).text = getString(R.string.over_label, overNum)
        view.findViewById<TextView>(R.id.tvScoreLabel).text = matchScore
        view.findViewById<TextView>(R.id.tvOverRuns).text = overRuns.toString()

        val isMaiden = overRuns == 0
        if (isMaiden) {
            view.findViewById<View>(R.id.tvMaidenBadge).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvOverLabel).setTextColor("#B71C1C".toColorInt())
        }

        val ballsContainer = view.findViewById<LinearLayout>(R.id.layoutBallsContainer)
        ballList.forEach { b ->
            ballsContainer.addView(createCircleBallView(this, b, false))
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

        view.findViewById<TextView>(R.id.tvBowlerName).text = bowler?.name ?: "Bowler"
        view.findViewById<TextView>(R.id.tvBowlerStats).text = if (bowler != null) {
            "${bowler?.oversBowledDisplay}-${bowler?.maidens}-${bowler?.runsConceded}-${bowler?.wicketsTaken}"
        } else {
            "0.0-0-0-0"
        }

        currentActiveDialog?.dismiss()
        currentActiveDialog = showDynamicDialog {
            setView(view)
            setCancelable(true)
        }
        
        // Setup the "Next Over" button as "Dismiss" for viewers
        view.findViewById<MaterialButton>(R.id.btnNextOverDialog).apply {
            text = "Dismiss"
            setOnClickListener { currentActiveDialog?.dismiss() }
        }

        autoDismissHandler.removeCallbacksAndMessages(null)
        autoDismissHandler.postDelayed({
            currentActiveDialog?.dismiss()
        }, 15000)
    }

    private fun showViewerInningsComplete(innings: Innings) {
        if (isFinishing || isDestroyed) return

        val score = "${innings.totalRuns}/${innings.totalWickets}"
        val oversStr = innings.oversDisplay
        val teamName = innings.battingTeam ?: "Batting Team"
        val scoreMsg = "$teamName scored $score Runs in $oversStr Overs"
        
        val matchOvers = match?.totalOvers ?: 0
        val targetRuns = match?.target ?: 0
        val totalBalls = matchOvers * 6
        val targetMsg = if (match?.secondInnings == null) {
            "Target: $targetRuns Runs in $totalBalls Balls"
        } else {
            calculateMatchResult()?.uppercase() ?: "Innings Complete"
        }

        val view = layoutInflater.inflate(R.layout.dialog_innings_complete, null)
        val titleTv = view.findViewById<TextView>(R.id.tvInningsCompleteTitle)
        if (match?.secondInnings == null) {
            titleTv.text = "FIRST INNINGS COMPLETE"
        } else {
            titleTv.text = "SECOND INNINGS COMPLETE"
        }
        
        view.findViewById<TextView>(R.id.tvInningsCompleteScore).text = scoreMsg
        view.findViewById<TextView>(R.id.tvInningsCompleteTarget).text = targetMsg
        
        val btnNext = view.findViewById<MaterialButton>(R.id.btnNextInningsDialog)
        btnNext.text = "Dismiss"
        btnNext.setOnClickListener { currentActiveDialog?.dismiss() }
        
        view.findViewById<View>(R.id.btnDismissDialog).visibility = View.GONE

        currentActiveDialog?.dismiss()
        currentActiveDialog = showDynamicDialog {
            setView(view)
            setCancelable(true)
        }

        autoDismissHandler.removeCallbacksAndMessages(null)
        autoDismissHandler.postDelayed({
            currentActiveDialog?.dismiss()
        }, 20000)
    }

    private fun showViewerMatchFinished() {
        if (isFinishing || isDestroyed) return
        val result = matchEntity?.result?.uppercase() ?: calculateMatchResult()?.uppercase() ?: "MATCH FINISHED"

        val view = layoutInflater.inflate(R.layout.dialog_match_finished, null)
        view.findViewById<TextView>(R.id.tvMatchFinishedResult).text = result
        
        val btnExit = view.findViewById<MaterialButton>(R.id.btnToastWinner)
        btnExit.text = "Exit Match"
        btnExit.setOnClickListener {
            currentActiveDialog?.dismiss()
            
            // Set flag to open 'Completed' tab when returning to MatchHistoryActivity
            getSharedPreferences("match_history_prefs", MODE_PRIVATE).edit()
                .putInt("preferred_tab", 1) // 1 is Completed tab
                .apply()
                
            finish()
        }

        currentActiveDialog?.dismiss()
        currentActiveDialog = showDynamicDialog {
            setView(view)
            setCancelable(false)
        }

        autoDismissHandler.removeCallbacksAndMessages(null)
        autoDismissHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                btnExit.performClick()
            }
        }, 10000) // Auto-exit after 10 seconds as requested
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
            b.contains("WD", ignoreCase = true) -> "#FFB300".toColorInt()
            b.contains("NB", ignoreCase = true) -> "#5E35B1".toColorInt()
            b.contains("LB", ignoreCase = true) -> "#3949AB".toColorInt()
            b.contains("B", ignoreCase = true) && !b.contains("NB", ignoreCase = true) -> "#546E7A".toColorInt()
            else -> ThemeManager.getThemeColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant)
        }

        val bg = View(ctx).apply {
            background = androidx.appcompat.content.res.AppCompatResources.getDrawable(ctx, R.drawable.circle_bg_grey)
            backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
        }
        val tv = TextView(ctx).apply {
            text = b
            setTextColor(ThemeManager.getContrastColor(bgColor))
            textSize = if (text.length > 2) 8f else 10f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        frame.addView(bg)
        frame.addView(tv)
        return frame
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
    override fun playBall(runs: Int, type: BallType?, isWicket: Boolean) {}
    override fun showBowlerSelectionDialog(onDone: Runnable?, cancelable: Boolean) {}
    override fun handleBowlerChangeMidOver() {}
    override fun showBatsmanSelectionDialog(isStrikerReplacing: Boolean) {}
    override fun showRetiredPlayerSelection(isHurt: Boolean) {}
    override fun handleWicketFlow() {}
    override fun handleWide() {}
    override fun handleNoBall() {}
    override fun handleBye() {}
    override fun handleLegBye() {}
    override fun handlePenaltyFlow() {}
    override fun handleOverthrow() {}
    override fun handleApplyDLS() {}
    override fun undoBall() {}
    override fun saveMatchToDatabase() {}
    override fun saveMatchToDatabase(isFinished: Boolean, isAbandoned: Boolean, isLive: Boolean) {}
    override fun saveMatchToDatabase(isFinished: Boolean, isAbandoned: Boolean, isLive: Boolean, isMilestone: Boolean) {}
    override fun startNextInnings() {}
    override fun calculateMatchResult(): String? {
        if (isAbandoned) return "MATCH ABANDONED"
        val m = match ?: return null
        val i1 = m.firstInnings ?: return null
        
        val i2 = m.secondInnings
        
        if (!isFinished) {
            if (!i1.isComplete) return "LIVE"
            if (i2 == null) return "First Innings over. Target: ${m.target}"
            if (!i2.isComplete) return "LIVE"
        }

        if (i2 == null) {
            return "${i1.battingTeam} scored ${i1.totalRuns}/${i1.totalWickets}".uppercase()
        }

        return when {
            i2.totalRuns >= m.target -> {
                val wickets = (i2.maxWickets - i2.totalWickets)
                "${i2.battingTeam} WON BY $wickets WICKETS".uppercase()
            }
            i2.totalRuns == (m.target - 1) -> "MATCH TIED"
            else -> {
                val runs = (m.target - 1) - i2.totalRuns
                "${i1.battingTeam} WON BY $runs RUNS".uppercase()
            }
        }
    }
    override fun playLottie(assetName: String?) {}
    override fun addPlayerToTeam(teamName: String?, playerName: String?, playerId: String?, photoUri: String?, jerseyNumber: String?) {}
    override fun showEditCommentaryDialog(context: android.content.Context, entry: CommentaryEntry, pos: Int) {}
}

package com.example.scoring

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.airbnb.lottie.LottieAnimationView
import com.example.scoring.RankingRegistry.applyPrestige
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import java.util.Locale
import kotlin.math.max

class LiveScoringFragment : Fragment() {
    private var tvStrikerName: TextView? = null
    private var tvNonStrikerName: TextView? = null
    private var tvBowlerName: TextView? = null
    private var tvExtras: TextView? = null
    private var tvPartnership: TextView? = null
    private var tvWinProbLabel: TextView? = null
    private var recentBallsLayout: LinearLayout? = null
    private var ivStriker: ShapeableImageView? = null
    private var ivNonStriker: ShapeableImageView? = null
    private var ivBowler: ShapeableImageView? = null
    private var pbWinProb: ProgressBar? = null
    private var cardStriker: View? = null
    private var cardNonStriker: View? = null
    private var cardBowler: View? = null
    private var lottieCelebration: LottieAnimationView? = null
    private var lastProbA = -1

    private var rvCommentary: androidx.recyclerview.widget.RecyclerView? = null
    private var commentaryAdapter: MainActivity.CommentaryAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_live, container, false)

        cardStriker = v.findViewById(R.id.cardStriker)
        cardNonStriker = v.findViewById(R.id.cardNonStriker)
        cardBowler = v.findViewById(R.id.cardBowler)

        tvStrikerName = v.findViewById(R.id.tvStrikerName)
        tvNonStrikerName = v.findViewById(R.id.tvNonStrikerName)
        tvBowlerName = v.findViewById(R.id.tvBowlerName)
        ivStriker = v.findViewById(R.id.ivStrikerPhoto)
        ivNonStriker = v.findViewById(R.id.ivNonStrikerPhoto)
        ivBowler = v.findViewById(R.id.ivBowlerPhoto)

        tvExtras = v.findViewById(R.id.tvExtras)
        tvPartnership = v.findViewById(R.id.tvPartnership)

        tvWinProbLabel = v.findViewById(R.id.tvWinProbLabel)
        pbWinProb = v.findViewById(R.id.pbWinProb)
        recentBallsLayout = v.findViewById(R.id.recentBallsContainer)
        lottieCelebration = v.findViewById(R.id.lottieCelebration)

        rvCommentary = v.findViewById(R.id.rvCommentary)
        rvCommentary?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)

        setupButtons(v)

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewModel =
            ViewModelProvider(requireActivity())[ScoringViewModel::class.java]

        viewModel.match.observe(viewLifecycleOwner, Observer { m: Match? ->
            val innings = m?.currentInnings ?: return@Observer
            updateUI(
                viewModel.striker.value,
                viewModel.nonStriker.value,
                viewModel.bowler.value,
                innings,
                innings.currentPshipRuns,
                innings.currentPshipBalls
            )
        })

        val act = activity as? ScoringProvider
        if (act?.isScorer == false) {
            viewModel.commentary.observe(viewLifecycleOwner, Observer {
                updateCommentaryUI()
            })
        }

        viewModel.striker.observe(viewLifecycleOwner, Observer { s: Player? ->
            val innings = viewModel.match.value?.currentInnings
            if (innings != null) updateUI(
                s,
                viewModel.nonStriker.value,
                viewModel.bowler.value,
                innings,
                innings.currentPshipRuns,
                innings.currentPshipBalls
            )
        })

        viewModel.nonStriker.observe(viewLifecycleOwner, Observer { ns: Player? ->
            val innings = viewModel.match.value?.currentInnings
            if (innings != null) updateUI(
                viewModel.striker.value,
                ns,
                viewModel.bowler.value,
                innings,
                innings.currentPshipRuns,
                innings.currentPshipBalls
            )
        })

        viewModel.bowler.observe(viewLifecycleOwner, Observer { b: Player? ->
            val innings = viewModel.match.value?.currentInnings
            if (innings != null) updateUI(
                viewModel.striker.value,
                viewModel.nonStriker.value,
                b,
                innings,
                innings.currentPshipRuns,
                innings.currentPshipBalls
            )
        })

        viewModel.overBalls.observe(viewLifecycleOwner, Observer { balls: MutableList<String?>? ->
            recentBallsLayout?.removeAllViews()
            balls?.forEach { event ->
                if (event != null) {
                    val ballView = createRecentBallView(event)
                    recentBallsLayout?.addView(ballView)
                }
            }
        })

        if (act?.isScorer == false) {
            updateCommentaryUI()
        }
        act?.updateUI()
    }

    private fun showMatchMoreMenu(v: View?) {
        val popup = PopupMenu(context, v)
        popup.menu.add("Retired Hurt")
        popup.menu.add("Retired Out")
        popup.menu.add("Penalty")
        popup.menu.add("Apply DLS")
        popup.menu.add("Resume Later")
        popup.menu.add("Abandon the Match")

        popup.setOnMenuItemClickListener { item: MenuItem? ->
            val act = activity as? MainActivity ?: return@setOnMenuItemClickListener false

            val title = item?.title?.toString()
            when (title) {
                "Retired Hurt" -> act.showRetiredPlayerSelection(true)
                "Retired Out" -> act.showRetiredPlayerSelection(false)
                "Penalty" -> act.handlePenaltyFlow()
                "Apply DLS" -> act.handleApplyDLS()
                "Resume Later" -> {
                    act.saveMatchToDatabase(
                        isFinished = false,
                        isAbandoned = false,
                        isLive = false,
                        isMilestone = false
                    )
                    act.finish()
                }
                "Abandon the Match" -> {
                    val ctx = context ?: return@setOnMenuItemClickListener false
                    val dialog = ThemeManager.createDynamicBuilder(ctx)
                        .setTitle("Abandon Match?")
                        .setMessage("This will stop the match and mark it as Abandoned. All player statistics recorded so far will be saved.")
                        .setPositiveButton("Abandon Match") { _, _ ->
                            act.saveMatchToDatabase(
                                isFinished = true,
                                isAbandoned = true,
                                isLive = false
                            )
                            act.finish()
                        }
                        .setNegativeButton("Cancel", null)
                        .create()
                    dialog.show()
                    ThemeManager.colorizeDialog(dialog)
                }
            }
            true
        }
        popup.show()
    }

    private fun setupButtons(v: View) {
        val act = activity as? ScoringProvider ?: return

        v.findViewById<View>(R.id.btn0).setOnClickListener {
            act.playBall(0, BallType.NORMAL, false)
            lockScoring(null)
        }
        v.findViewById<View>(R.id.btn1).setOnClickListener {
            act.playBall(1, BallType.NORMAL, false)
            lockScoring(null)
        }
        v.findViewById<View>(R.id.btn2).setOnClickListener {
            act.playBall(2, BallType.NORMAL, false)
            lockScoring(null)
        }
        v.findViewById<View>(R.id.btn3).setOnClickListener {
            act.playBall(3, BallType.NORMAL, false)
            lockScoring(null)
        }
        v.findViewById<View>(R.id.btn4).setOnClickListener {
            act.playBall(4, BallType.NORMAL, false)
            lockScoring(null)
        }
        v.findViewById<View>(R.id.btn6).setOnClickListener {
            act.playBall(6, BallType.NORMAL, false)
            lockScoring(null)
        }

        v.findViewById<View>(R.id.btnWide).setOnClickListener {
            act.handleWide()
            lockScoring(null)
        }
        v.findViewById<View>(R.id.btnNoBall).setOnClickListener {
            act.handleNoBall()
            lockScoring(null)
        }
        v.findViewById<View>(R.id.btnBye).setOnClickListener {
            act.handleBye()
            lockScoring(null)
        }
        v.findViewById<View>(R.id.btnLegBye).setOnClickListener {
            act.handleLegBye()
            lockScoring(null)
        }

        v.findViewById<View>(R.id.btnWicket).setOnClickListener {
            act.handleWicketFlow()
            lockScoring(null)
        }

        v.findViewById<View>(R.id.btnUndo).setOnClickListener { act.undoBall() }
        v.findViewById<View>(R.id.btnOverthrow).setOnClickListener { act.handleOverthrow() }
        v.findViewById<View>(R.id.btnMatchMore).setOnClickListener { view -> showMatchMoreMenu(view) }

        v.findViewById<View>(R.id.cardBowler).setOnClickListener {
            if (act.isScorer) act.handleBowlerChangeMidOver()
        }

        v.findViewById<View>(R.id.btnFinishMatch).setOnClickListener {
            val context = context ?: return@setOnClickListener
            val dialog = ThemeManager.createDynamicBuilder(context)
                .setTitle("Finish Match?")
                .setMessage("Are you sure you want to finalize the scorecard? This will end the match permanently.")
                .setPositiveButton("Finish and Save") { _, _ -> act.saveMatchToDatabase() }
                .setNegativeButton("Cancel", null)
                .create()
            dialog.show()
            ThemeManager.colorizeDialog(dialog)
        }
        v.findViewById<View>(R.id.btnNextInnings).setOnClickListener { act.startNextInnings() }

        val btnScoreGuard = v.findViewById<MaterialButton>(R.id.btnScoreGuard)
        btnScoreGuard.setOnClickListener {
            val provider = activity as? ScoringProvider
            if (provider != null) {
                val result = provider.calculateMatchResult()
                // If a winner is declared, or it's a tie, the match is over -> LOCK GUARD
                if (result != null && result != "IN-PROGRESS" && result != "LIVE" && !result.contains("FIRST INNINGS OVER")) {
                    return@setOnClickListener
                }
            }
            btnScoreGuard.visibility = View.GONE
        }

        // VIEWER MODE: Hide the entire scoring controls block in one shot
        if (!act.isScorer) {
            v.findViewById<View>(R.id.layoutScoringControls)?.visibility = View.GONE
            v.findViewById<View>(R.id.rvCommentary)?.visibility = View.VISIBLE
            
            // Adjust ScrollView to WRAP_CONTENT so it takes only as much space as needed (Cards + Footer)
            // This leaves the remaining space (weight 1) to the RecyclerView
            val mainScroll = v.findViewById<View>(R.id.mainScrollView)
            val lp = mainScroll.layoutParams as? LinearLayout.LayoutParams
            if (lp != null) {
                lp.height = LinearLayout.LayoutParams.WRAP_CONTENT
                lp.weight = 0f
                mainScroll.layoutParams = lp
            }
        }

        // Initialize label dynamically during setup if data is already available
        val current = act.match?.currentInnings
        if (current != null) {
            lockScoring(null)
        } else {
            btnScoreGuard.visibility = View.GONE
        }

        applyScoringButtonColors(v)
    }

    private fun applyScoringButtonColors(v: View) {
        val ctx = context ?: return
        val colorWide = "#FFB300".toColorInt() // Amber
        val colorWicket = "#B71C1C".toColorInt() // Red
        val colorSix = "#2E7D32".toColorInt() // Deep Green
        val colorFour = "#EF6C00".toColorInt() // Orange
        val colorNB = "#5E35B1".toColorInt() // Purple
        val colorB = "#546E7A".toColorInt() // Slate
        val colorLB = "#3949AB".toColorInt() // Indigo
        val neutral = ThemeManager.getThemeColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant)

        fun colorBtn(id: Int, color: Int) {
            val btn = v.findViewById<MaterialButton>(id) ?: return
            btn.backgroundTintList = ColorStateList.valueOf(color)
            btn.setTextColor(ThemeManager.getContrastColor(color))
            btn.strokeColor = ColorStateList.valueOf(color)
        }

        colorBtn(R.id.btn0, neutral)
        colorBtn(R.id.btn1, neutral)
        colorBtn(R.id.btn2, neutral)
        colorBtn(R.id.btn3, neutral)
        colorBtn(R.id.btn4, colorFour)
        colorBtn(R.id.btn6, colorSix)
        colorBtn(R.id.btnWide, colorWide)
        colorBtn(R.id.btnNoBall, colorNB)
        colorBtn(R.id.btnBye, colorB)
        colorBtn(R.id.btnLegBye, colorLB)
        colorBtn(R.id.btnWicket, colorWicket)
    }

    override fun onResume() {
        super.onResume()
        val act = activity as? ScoringProvider
        act?.updateUI()
    }


    fun lockScoring(label: String?) {
        val provider = activity as? ScoringProvider
        val v = view
        if (v == null || provider == null) return

        if (!provider.isScorer) return

        val btnScoreGuard = v.findViewById<MaterialButton>(R.id.btnScoreGuard) ?: return
        val innings = provider.match?.currentInnings ?: return

        val oversLimit = innings.revisedMaxOvers ?: innings.maxOvers
        val isFirstBall = innings.balls.isEmpty()
        val isLastBall = (oversLimit > 0 && innings.legalBalls >= (oversLimit * 6) - 1) ||
                         (innings.target != null && innings.totalRuns >= innings.target!! - 1)

        val isGenericLabel = label == null || label == "NEXT BALL" || label == "FIRST BALL" || label == "FINAL BALL"

        // PRIORITY: If a specific non-generic label is provided (like a match result), use it.
        val finalLabel = if (!isGenericLabel) {
            label
        } else if (provider.isFreeHitActive) {
            "FREE HIT"
        } else if (isFirstBall) {
            "FIRST BALL"
        } else if (isLastBall) {
            "FINAL BALL"
        } else {
            "NEXT BALL"
        }

        btnScoreGuard.text = finalLabel
        btnScoreGuard.visibility = View.VISIBLE

        if ("FREE HIT" == finalLabel) {
            btnScoreGuard.backgroundTintList = ColorStateList.valueOf(-0x39d7d8)
            btnScoreGuard.setTextColor(-0x1)
        } else {
            btnScoreGuard.backgroundTintList = null
            btnScoreGuard.setTextColor(-0x1)
        }
    }

    fun updateUI(
        striker: Player?,
        nonStriker: Player?,
        bowler: Player?,
        innings: Innings?,
        pRuns: Int,
        pBalls: Int
    ) {
        val v = view ?: return
        if (innings == null) return

        applyScoringButtonColors(v)
        
        updateBatterBox(striker, cardStriker!!, true)
        updateBatterBox(nonStriker, cardNonStriker!!, false)

        if (bowler != null) {
            tvBowlerName?.text = bowler.name
            applyPrestige(bowler.id, tvBowlerName, null)

            setUnit(cardBowler!!, R.id.statBowlerO, "O", bowler.oversBowledDisplay)
            setUnit(cardBowler!!, R.id.statBowlerM, "M", bowler.maidens.toString())
            setUnit(cardBowler!!, R.id.statBowlerR, "R", bowler.runsConceded.toString())
            setUnit(cardBowler!!, R.id.statBowlerW, "W", bowler.wicketsTaken.toString())
            val eco =
                if (bowler.ballsBowled > 0) (bowler.runsConceded.toDouble() / bowler.ballsBowled) * 6 else 0.0
            setUnit(cardBowler!!, R.id.statBowlerECO, "ECO", String.format(Locale.US, "%.1f", eco))
        } else {
            tvBowlerName?.setText(R.string.select_bowler)
            setUnit(cardBowler!!, R.id.statBowlerO, "O", "0.0")
            setUnit(cardBowler!!, R.id.statBowlerM, "M", "0")
            setUnit(cardBowler!!, R.id.statBowlerR, "R", "0")
            setUnit(cardBowler!!, R.id.statBowlerW, "W", "0")
            setUnit(cardBowler!!, R.id.statBowlerECO, "ECO", "0.0")
        }

        tvExtras?.text = innings.extras.toString()
        tvPartnership?.text = String.format(Locale.US, "%d(%d)", pRuns, pBalls)

        var prob = 50
        val act = activity as? ScoringProvider
        val m = act?.match
        if (m != null) {
            val teamADiff = m.teamAPlayerCount - m.teamBPlayerCount
            val initialImbalance = teamADiff * 2

            if (m.secondInnings == null) {
                if (innings.legalBalls > 0) {
                    val crr = innings.currentRunRate
                    val progress = innings.legalBalls.toDouble() / max(m.totalOvers * 6, 1)
                    val crrBonus = (crr - 7.5) * 5
                    val expectedWicketRatio = progress * 0.8
                    val actualWicketRatio = innings.totalWickets.toDouble() / max(innings.maxWickets, 1)
                    val wicketPressure = (actualWicketRatio - expectedWicketRatio) * 40
                    prob = 50 + crrBonus.toInt() - wicketPressure.toInt()
                }
            } else {
                val target = m.target
                val current = innings.totalRuns
                val needed = target - current
                val ballsLeft = (m.totalOvers * 6) - innings.legalBalls
                val totalBalls = m.totalOvers * 6
                val wicketsLeft = max(innings.maxWickets - innings.totalWickets, 0)
                val maxW = max(innings.maxWickets, 1)

                if (current >= target) prob = 100
                else if (wicketsLeft <= 0 || (ballsLeft <= 0)) prob = 0
                else {
                    val rrr = needed.toDouble() / max(ballsLeft, 1) * 6
                    val ballsProgress = (totalBalls - ballsLeft).toDouble() / max(totalBalls, 1)
                    val rrrWeight = 6.0 + (ballsProgress * 6.0)
                    val rrrPressure = (rrr - 8.0) * rrrWeight
                    val wicketResRatio = wicketsLeft.toDouble() / maxW
                    val ballsResRatio = ballsLeft.toDouble() / max(totalBalls, 1)
                    val resourceGapBonus = (wicketResRatio - ballsResRatio) * 35
                    prob = 50 - rrrPressure.toInt() + resourceGapBonus.toInt()
                }
            }

            val isTeamABatting = innings.battingTeam == m.teamA
            if (isTeamABatting) prob += initialImbalance
            else prob -= initialImbalance

            if (prob > 98) prob = 98
            if (prob < 2) prob = 2

            val probA = if (isTeamABatting) prob else (100 - prob)
            val probB = 100 - probA

            tvWinProbLabel?.text = String.format(Locale.US, "%s %d%% - %d%% %s", m.teamA, probA, probB, m.teamB)

            if (lastProbA != probA) {
                pbWinProb?.let {
                    val animation = ObjectAnimator.ofInt(it, "progress", it.progress, probA)
                    animation.duration = 800
                    animation.interpolator = DecelerateInterpolator()
                    animation.start()
                }
                lastProbA = probA
            }
        }

        updateExtraButtons()

        v.findViewById<View>(R.id.btnOverthrow).visibility = if (act?.isRuleOverthrow == true) View.VISIBLE else View.GONE

        if (act != null) {
            val res = act.calculateMatchResult()?.uppercase()
            val isDecided = res != null && res != "IN-PROGRESS" && res != "LIVE" && !res.contains("FIRST INNINGS OVER")
            v.findViewById<View>(R.id.btnMatchMore)?.isEnabled = !isDecided
            
            // "Finish Match" only appears AFTER a result is declared
            v.findViewById<View>(R.id.btnFinishMatch).visibility = if (isDecided) View.VISIBLE else View.GONE
        }

        val btnNextInnings = v.findViewById<View>(R.id.btnNextInnings) as? MaterialButton
        val isInningsComplete = innings.isComplete
        val isFirstInnings = act?.match?.secondInnings == null
        val isMatchComplete = innings.isComplete && !isFirstInnings

        // Awaiting Players: 2nd innings started, but no players selected yet.
        val isAwaitingPlayers = !isFirstInnings && innings.balls.isEmpty() && striker == null
        // Manual Fix: 2nd innings has 0 balls, but players exist (potentially incorrect "bled" state).
        val isManualFixPossible = !isFirstInnings && innings.balls.isEmpty() && striker != null

        if ((isInningsComplete && isFirstInnings) || isAwaitingPlayers) {
            btnNextInnings?.visibility = View.VISIBLE
            btnNextInnings?.text = getString(R.string.btn_start_2nd_innings)
            setButtonsEnabled(false)
            if (isFirstInnings) {
                lockScoring("FIRST INNINGS COMPLETE")
            } else {
                lockScoring("START SECOND INNINGS")
            }
        } else {
            // If we have players but 0 balls, show the button as a "Reset" option but enable scoring
            if (isManualFixPossible) {
                btnNextInnings?.visibility = View.VISIBLE
                btnNextInnings?.text = "Reset 2nd Innings Players"
                setButtonsEnabled(true)
                if (act?.isScorer == true) lockScoring(null)
            } else {
                btnNextInnings?.visibility = View.GONE
                if (isMatchComplete) {
                    setButtonsEnabled(false)
                    lockScoring(act?.calculateMatchResult()?.uppercase())
                } else {
                    setButtonsEnabled(true)
                    if (act?.isScorer == true) lockScoring(null)
                }
            }
        }
    }

    private fun createRecentBallView(event: String): View {
        val ctx = context ?: return View(context)
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22f, resources.displayMetrics).toInt()
        
        val frame = FrameLayout(ctx).apply {
            val lp = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 6, 0) }
            layoutParams = lp
        }

        // 1. Determine Background Color
        val colorWicket = "#B71C1C".toColorInt()
        val colorSix = "#2E7D32".toColorInt()
        val colorFour = "#EF6C00".toColorInt()
        val colorWide = "#FFB300".toColorInt()
        val colorNB = "#5E35B1".toColorInt()
        val colorB = "#546E7A".toColorInt()
        val colorLB = "#3949AB".toColorInt()
        val neutral = ThemeManager.getThemeColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant)

        val bgColor = when {
            event.endsWith("W") || event == "W" -> colorWicket
            event == "6" -> colorSix
            event == "4" -> colorFour
            event.contains("WD") -> colorWide
            event.contains("NB") -> colorNB
            event.contains("B") -> colorB
            event.contains("LB") -> colorLB
            else -> neutral
        }

        // 2. Rounded Square Background
        val bg = View(ctx).apply {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics)
                setColor(bgColor)
            }
            background = shape
        }

        // 3. Text Label
        val tv = TextView(ctx).apply {
            text = when (event) {
                "0" -> "•"
                else -> event
            }
            setTextColor(ThemeManager.getContrastColor(bgColor))
            textSize = if (text.length > 2) 8f else 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            isSingleLine = true
            includeFontPadding = false
        }

        frame.addView(bg)
        frame.addView(tv)
        return frame
    }

    fun playCelebration(resName: String?) {
        val lottie = lottieCelebration ?: return

        val resId = when (resName) {
            "wicket" -> R.raw.wicket
            "six" -> R.raw.six
            "four" -> R.raw.four
            else -> 0
        }

        if (resId != 0) {
            lottie.setAnimation(resId)
            lottie.visibility = View.VISIBLE
            lottie.playAnimation()
            lottie.addAnimatorListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    lottie.visibility = View.GONE
                }
            })
        }
    }

    private fun updateBatterBox(p: Player?, card: View, isStriker: Boolean) {
        val nameTv = card.findViewById<TextView>(if (isStriker) R.id.tvStrikerName else R.id.tvNonStrikerName)
        if (p == null) {
            if (isStriker) {
                nameTv.setText(R.string.select_striker)
            } else {
                nameTv.text = "-"
            }
            setUnit(card, if (isStriker) R.id.statStrikerR else R.id.statNonStrikerR, "R", "0")
            setUnit(card, if (isStriker) R.id.statStrikerB else R.id.statNonStrikerB, "B", "0")
            setUnit(card, if (isStriker) R.id.statStriker4s else R.id.statNonStriker4s, "4s", "0")
            setUnit(card, if (isStriker) R.id.statStriker6s else R.id.statNonStriker6s, "6s", "0")
            setUnit(card, if (isStriker) R.id.statStrikerSR else R.id.statNonStrikerSR, "SR", "0.0")
            return
        }
        val context = context
        if (context != null) {
            val strikerMark = if (isStriker) "*" else ""
            nameTv.text = context.getString(R.string.player_name_display, p.name + strikerMark)
        } else {
            nameTv.text = if (isStriker) "${p.name}*" else p.name
        }
        applyPrestige(p.id, nameTv, null)

        setUnit(card, if (isStriker) R.id.statStrikerR else R.id.statNonStrikerR, "R", p.runsScored.toString())
        setUnit(card, if (isStriker) R.id.statStrikerB else R.id.statNonStrikerB, "B", p.ballsFaced.toString())
        setUnit(card, if (isStriker) R.id.statStriker4s else R.id.statNonStriker4s, "4s", p.fours.toString())
        setUnit(card, if (isStriker) R.id.statStriker6s else R.id.statNonStriker6s, "6s", p.sixes.toString())
        val sr = if (p.ballsFaced > 0) (p.runsScored.toDouble() / p.ballsFaced) * 100 else 0.0
        setUnit(card, if (isStriker) R.id.statStrikerSR else R.id.statNonStrikerSR, "SR", String.format(Locale.US, "%.1f", sr))
    }

    private fun updateExtraButtons() {
        val act = activity as? MainActivity
        val v = view ?: return

        v.findViewById<View>(R.id.btnWide).isEnabled = true
        v.findViewById<View>(R.id.btnNoBall).isEnabled = true
        v.findViewById<View>(R.id.btnBye).isEnabled = act?.isRuleRunsOnBye == true
        v.findViewById<View>(R.id.btnLegBye).isEnabled = act?.isRuleRunsOnBye == true
    }

    private fun setUnit(parent: View, unitId: Int, label: String?, value: String?) {
        val unit = parent.findViewById<View>(unitId)
        (unit.findViewById<View>(R.id.tvUnitLabel) as TextView).text = label
        (unit.findViewById<View>(R.id.tvUnitValue) as TextView).text = value
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        val v = view ?: return
        val ids = intArrayOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn6,
            R.id.btnWide, R.id.btnNoBall, R.id.btnBye, R.id.btnLegBye,
            R.id.btnWicket, R.id.btnOverthrow
        )
        for (id in ids) {
            v.findViewById<View>(id)?.isEnabled = enabled
        }
        v.findViewById<View>(R.id.btnUndo)?.isEnabled = true
    }

    private fun updateCommentaryUI() {
        if (!isAdded || rvCommentary == null || activity == null) return
        val act = activity as? ScoringProvider
        if (act == null || act.isScorer) return

        if (act.commentary != null) {
            if (commentaryAdapter == null) {
                commentaryAdapter = MainActivity.CommentaryAdapter(act.commentary!!, act)
                rvCommentary?.adapter = commentaryAdapter
            } else {
                commentaryAdapter?.notifyDataSetChanged()
            }
        }
    }
}

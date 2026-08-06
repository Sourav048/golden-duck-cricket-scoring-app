package com.example.scoring

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TeamOversListFragment : Fragment() {
    private var inningsIndex = 0
    private var rv: RecyclerView? = null
    private var tvEmpty: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inningsIndex = arguments?.getInt("inningsIndex") ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_team_overs_list, container, false)
        rv = v.findViewById(R.id.rvTeamOvers)
        tvEmpty = v.findViewById(R.id.tvEmptyOvers)
        rv?.layoutManager = LinearLayoutManager(context)
        updateUI()
        return v
    }

    fun updateUI() {
        if (!isAdded || (rv == null) || (activity == null)) return
        val act = activity as? ScoringProvider ?: return
        val m = act.match ?: return

        // Determine which innings to show based on index
        val targetInnings = if (inningsIndex == 0) m.firstInnings else m.secondInnings

        if (targetInnings == null || targetInnings.balls.isEmpty()) {
            tvEmpty?.visibility = View.VISIBLE
            rv?.adapter = null
            return
        }

        tvEmpty?.visibility = View.GONE
        val overs = processOvers(targetInnings, targetInnings.isComplete)
        
        // Logical Order:
        // Live Mode (Scorer is active): Newest top
        // Completed Mode: Newest bottom (Chronological)
        val finalOvers = if (act.isScorer) overs.asReversed() else overs

        rv?.adapter = OversAdapter(finalOvers)
    }

    private fun processOvers(innings: Innings, inningsComplete: Boolean): List<OverSummary> {
        val summaries = mutableListOf<OverSummary>()
        var currentOverBalls = mutableListOf<Ball>()
        var legalBallsInOver = 0
        var runsAtEnd = 0
        var wicketsAtEnd = 0

        for (i in innings.balls.indices) {
            val ball = innings.balls[i]
            currentOverBalls.add(ball)
            runsAtEnd += ball.runs
            if (ball.isWicket) wicketsAtEnd++

            if (ball.type == BallType.NORMAL || ball.type == BallType.BYE || ball.type == BallType.LEG_BYE) {
                legalBallsInOver++
            }

            if (legalBallsInOver == 6) {
                summaries.add(
                    OverSummary(
                        overNum = summaries.size + 1,
                        scoreAtEnd = "$runsAtEnd-$wicketsAtEnd",
                        bowlerName = getActualBowler(innings, i),
                        batterNames = extractBatterNames(currentOverBalls),
                        balls = currentOverBalls.toList(),
                        totalOverRuns = calculateOverRuns(currentOverBalls)
                    )
                )
                currentOverBalls = mutableListOf()
                legalBallsInOver = 0
            }
        }

        // Add currently incomplete over if any
        if (currentOverBalls.isNotEmpty()) {
            summaries.add(
                OverSummary(
                    overNum = summaries.size + 1,
                    scoreAtEnd = "$runsAtEnd-$wicketsAtEnd",
                    bowlerName = getActualBowler(innings, innings.balls.size - 1),
                    batterNames = extractBatterNames(currentOverBalls),
                    balls = currentOverBalls.toList(),
                    totalOverRuns = calculateOverRuns(currentOverBalls),
                    isIncomplete = !inningsComplete
                )
            )
        }

        return summaries
    }

    private fun getActualBowler(innings: Innings, currentIndex: Int): String {
        // Look at the ball at currentIndex. If it's not "FIELD", return it.
        val currentBall = innings.balls.getOrNull(currentIndex)
        if (currentBall != null && currentBall.bowlerName != "FIELD" && currentBall.bowlerName != "PENALTY") {
            return currentBall.bowlerName ?: "Bowler"
        }
        
        // If it is "FIELD", search backwards for the most recent valid bowler name in this innings
        for (j in currentIndex downTo 0) {
            val b = innings.balls[j]
            if (b.bowlerName != null && b.bowlerName != "FIELD" && b.bowlerName != "PENALTY") {
                return b.bowlerName!!
            }
        }
        
        // If still not found, search forwards (in case retirement happened before first ball)
        for (j in currentIndex until innings.balls.size) {
            val b = innings.balls[j]
            if (b.bowlerName != null && b.bowlerName != "FIELD" && b.bowlerName != "PENALTY") {
                return b.bowlerName!!
            }
        }

        return currentBall?.bowlerName ?: "Bowler"
    }

    private fun extractBatterNames(balls: List<Ball>): String {
        val names = LinkedHashSet<String>()
        balls.forEach { b ->
            b.batsmanName?.let { names.add(it) }
        }
        val list = names.toList()
        return when {
            list.isEmpty() -> "-"
            list.size == 1 -> list[0]
            else -> "${list[0]} & ${list[1]}" // Just show top 2 participants
        }
    }

    private fun calculateOverRuns(balls: List<Ball>): Int {
        return balls.sumOf { it.runs }
    }

    private data class OverSummary(
        val overNum: Int,
        val scoreAtEnd: String,
        val bowlerName: String,
        val batterNames: String,
        val balls: List<Ball>,
        val totalOverRuns: Int,
        val isIncomplete: Boolean = false
    )

    private inner class OversAdapter(private val overs: List<OverSummary>) :
        RecyclerView.Adapter<OversAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_over_summary_row, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val o = overs[position]
            holder.tvOverLabel.text = getString(R.string.over_label, o.overNum)
            holder.tvScore.text = o.scoreAtEnd
            holder.tvNarrative.text = getString(R.string.over_narrative_format, o.bowlerName, o.batterNames)
            holder.tvTotalRuns.text = o.totalOverRuns.toString()

            holder.layoutBalls.removeAllViews()
            o.balls.forEach { b ->
                val frame = createBallView(b)
                holder.layoutBalls.addView(frame)
            }
        }

        private fun createBallView(b: Ball): View {
            val ctx = requireContext()
            val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics).toInt()
            val frame = FrameLayout(ctx).apply {
                val lp = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 8, 0) }
                layoutParams = lp
            }

            val bgColor = when {
                b.isWicket || b.dismissalInfo?.contains("retired", ignoreCase = true) == true -> "#B71C1C".toColorInt()
                b.runs == 6 -> "#2E7D32".toColorInt()
                b.runs == 4 -> "#EF6C00".toColorInt()
                b.type == BallType.WIDE -> "#FFB300".toColorInt()
                b.type == BallType.NO_BALL -> "#5E35B1".toColorInt()
                b.type == BallType.BYE -> "#546E7A".toColorInt()
                b.type == BallType.LEG_BYE -> "#3949AB".toColorInt()
                else -> ThemeManager.getThemeColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant)
            }

            val bg = View(ctx).apply {
                background = androidx.appcompat.content.res.AppCompatResources.getDrawable(ctx, R.drawable.circle_bg_grey)
                backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
            }
            
            val tv = TextView(ctx).apply {
                tag = "custom_color"
                val provider = activity as? ScoringProvider
                val match = provider?.match
                val widePenalty = if (match?.ruleRunsOnWide == false) 0 else 1
                
                text = when {
                    b.dismissalInfo == "retired hurt" -> "RH"
                    b.dismissalInfo == "retired out" -> "RO"
                    b.isWicket -> {
                        val runsRan = when (b.type) {
                            BallType.WIDE -> b.runs - widePenalty
                            BallType.NO_BALL -> b.runs - 1
                            else -> b.runs
                        }
                        if (runsRan <= 0) "W" else "${runsRan}W"
                    }
                    b.type == BallType.BYE -> {
                        if (b.runs == 0) "•" else "${b.runs}B"
                    }
                    b.type == BallType.LEG_BYE -> {
                        if (b.runs == 0) "•" else "${b.runs}LB"
                    }
                    b.type == BallType.WIDE -> {
                        val runsRan = b.runs - widePenalty
                        if (runsRan <= 0) "WD" else "${runsRan}WD"
                    }
                    b.type == BallType.NO_BALL -> {
                        val nbPenalty = if (match?.ruleRunsOnWide == false) 0 else 1
                        val runsRan = b.runs - nbPenalty
                        if (runsRan <= 0) "NB" else "${runsRan}NB"
                    }
                    b.runs == 0 && b.type == BallType.NORMAL -> "•"
                    else -> b.runs.toString()
                }
                
                setTextColor(ThemeManager.getContrastColor(bgColor))
                
                // Styling to prevent wrapping
                val baseSize = 10f
                textSize = if (text.length > 2) baseSize - 1.5f else baseSize
                isSingleLine = true
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }

            frame.addView(bg)
            frame.addView(tv)
            return frame
        }

        override fun getItemCount(): Int = overs.size

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvOverLabel: TextView = v.findViewById(R.id.tvOverLabel)
            val tvScore: TextView = v.findViewById(R.id.tvOverScore)
            val tvNarrative: TextView = v.findViewById(R.id.tvOverNarrative)
            val layoutBalls: LinearLayout = v.findViewById(R.id.layoutOverBalls)
            val tvTotalRuns: TextView = v.findViewById(R.id.tvOverRuns)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(inningsIndex: Int): TeamOversListFragment {
            val f = TeamOversListFragment()
            val b = Bundle()
            b.putInt("inningsIndex", inningsIndex)
            f.arguments = b
            return f
        }
    }
}

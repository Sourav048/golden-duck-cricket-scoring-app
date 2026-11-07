package com.goldenduck.scoring

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.PopupWindow
import android.widget.TextView
import java.lang.ref.WeakReference

data class StatExplanation(
    val title: String,
    val description: String,
    val formula: String? = null
)

object StatTooltipHelper {

    private val STAT_EXPLANATIONS = mapOf(
        // ── BATTING STANDARD ──
        R.id.boxBatMatches to StatExplanation(
            "MATCHES",
            "Total number of career matches this player has participated in."
        ),
        R.id.boxBatInnings to StatExplanation(
            "INNINGS",
            "Total number of matches where the player walked out to bat."
        ),
        R.id.boxBatRuns to StatExplanation(
            "RUNS SCORED",
            "Aggregate career runs scored off the bat."
        ),
        R.id.boxBatHighest to StatExplanation(
            "HIGHEST SCORE",
            "Highest individual score achieved in a single innings."
        ),
        R.id.boxBatAvg to StatExplanation(
            "BATTING AVERAGE",
            "Average runs scored before each dismissal.",
            "Formula: Total Runs ÷ (Innings − Not Outs)"
        ),
        R.id.boxBatSR to StatExplanation(
            "STRIKE RATE",
            "Average runs scored per 100 deliveries faced.",
            "Formula: (Total Runs ÷ Balls Faced) × 100"
        ),
        R.id.boxBatFours to StatExplanation(
            "FOURS (4s)",
            "Total boundaries struck along the ground crossing the rope."
        ),
        R.id.boxBatSixes to StatExplanation(
            "SIXES (6s)",
            "Total maximums struck clearing the boundary rope on the full."
        ),
        R.id.boxBatNotOut to StatExplanation(
            "NOT OUT",
            "Number of innings where the batsman remained undefeated till innings end."
        ),
        R.id.boxBatDucks to StatExplanation(
            "DUCKS (0)",
            "Number of innings dismissed for zero runs."
        ),
        R.id.boxBat30s to StatExplanation(
            "THIRTIES (30s)",
            "Innings where the batsman scored between 30 and 49 runs."
        ),
        R.id.boxBat50s to StatExplanation(
            "FIFTIES (50s)",
            "Half-centuries scored (50 to 79 runs in an innings)."
        ),
        R.id.boxBat80s to StatExplanation(
            "EIGHTIES (80s)",
            "Near-century milestone knocks scored (80 to 99 runs)."
        ),
        R.id.boxBat100s to StatExplanation(
            "CENTURIES (100s)",
            "Triple-figure milestone innings scored (100+ runs)."
        ),

        // ── BATTING ADVANCED ──
        R.id.boxBatBalls to StatExplanation(
            "BALLS FACED",
            "Total individual deliveries faced by the batsman across all matches."
        ),
        R.id.boxBatMinsPlayed to StatExplanation(
            "MINS PLAYED",
            "Total cumulative minutes spent batting at the crease across all matches."
        ),
        R.id.boxBatAvgCreaseTime to StatExplanation(
            "AVG CREASE TIME",
            "Average minutes spent batting in the middle per innings.",
            "Formula: Total Batting Minutes ÷ Innings"
        ),
        R.id.boxBatRunsPerInnings to StatExplanation(
            "RUNS / INNINGS",
            "True run output per game, independent of not-outs.",
            "Formula: Total Runs ÷ Innings"
        ),
        R.id.boxBatBallsPerInnings to StatExplanation(
            "BALLS / INNINGS",
            "Average balls faced per innings, measuring crease resistance.",
            "Formula: Balls Faced ÷ Innings"
        ),
        R.id.boxBatTotalBoundaries to StatExplanation(
            "TOTAL BOUNDARIES",
            "Combined count of all career boundary fours and sixes hit.",
            "Formula: Fours + Sixes"
        ),
        R.id.boxBatBoundaryRuns to StatExplanation(
            "BOUNDARY RUNS",
            "Total runs accumulated purely from boundary shots.",
            "Formula: (4s × 4) + (6s × 6)"
        ),
        R.id.boxBatBoundaryPercent to StatExplanation(
            "BOUNDARY %",
            "Percentage of total career runs that came from boundaries.",
            "Formula: (Boundary Runs ÷ Total Runs) × 100"
        ),
        R.id.boxBatBallsPerBoundary to StatExplanation(
            "BALLS / BOUNDARY",
            "Average deliveries faced between each 4 or 6 hit.",
            "Formula: Balls Faced ÷ (Fours + Sixes)"
        ),
        R.id.boxBatBoundaryFreq to StatExplanation(
            "BOUNDARY FREQ %",
            "Percentage of all deliveries faced that were hit for a boundary.",
            "Formula: (Total Boundaries ÷ Balls Faced) × 100"
        ),
        R.id.boxBatRunningRuns to StatExplanation(
            "RUNNING RUNS",
            "Career runs scored through singles, doubles, and triples.",
            "Formula: Total Runs − Boundary Runs"
        ),
        R.id.boxBatStrikeRotation to StatExplanation(
            "STRIKE ROTATION %",
            "Percentage of total runs earned by running between the wickets.",
            "Formula: (Running Runs ÷ Total Runs) × 100"
        ),
        R.id.boxBatDotPercent to StatExplanation(
            "DOT BALL % (BATTING)",
            "Percentage of deliveries faced where no run was scored.",
            "Formula: (Batting Dot Balls ÷ Balls Faced) × 100"
        ),
        R.id.boxBatBoundaryRatio to StatExplanation(
            "4s / 6s RATIO",
            "Ratio of boundary fours struck relative to sixes hit.",
            "Formula: Fours Hit ÷ Sixes Hit"
        ),
        R.id.boxBatConversionRate to StatExplanation(
            "CONVERSION RATE %",
            "Percentage of 50+ knocks successfully converted into centuries (100+).",
            "Formula: [100s ÷ (50s + 100s)] × 100"
        ),
        R.id.boxBatTeamWinPct to StatExplanation(
            "TEAM WIN %",
            "Percentage of matches won by the team when this player was in the playing XI.",
            "Formula: (Matches Won ÷ Total Matches) × 100"
        ),

        // ── BOWLING STANDARD ──
        R.id.boxBowlMatches to StatExplanation(
            "MATCHES",
            "Total matches played with bowling opportunities."
        ),
        R.id.boxBowlInnings to StatExplanation(
            "BOWLING INNINGS",
            "Total matches where the player bowled at least one delivery."
        ),
        R.id.boxBowlWickets to StatExplanation(
            "WICKETS",
            "Total career dismissals credited to the bowler."
        ),
        R.id.boxBowlOvers to StatExplanation(
            "OVERS BOWLED",
            "Completed 6-ball overs and extra deliveries (Overs.Balls)."
        ),
        R.id.boxBowlEco to StatExplanation(
            "ECONOMY RATE",
            "Average runs conceded per 6-ball over. Lower is better.",
            "Formula: (Runs Conceded ÷ Balls Bowled) × 6"
        ),
        R.id.boxBowlAvg to StatExplanation(
            "BOWLING AVERAGE",
            "Average runs conceded for each wicket taken. Lower is better.",
            "Formula: Runs Conceded ÷ Wickets"
        ),
        R.id.boxBowlBBI to StatExplanation(
            "BEST FIGURE (BBI)",
            "Best single-match performance (Most Wickets for Fewest Runs)."
        ),
        R.id.boxBowlMaidens to StatExplanation(
            "MAIDENS",
            "Complete 6-ball overs delivered without conceding any runs off the bat."
        ),
        R.id.boxBowl2w to StatExplanation(
            "2W HAULS",
            "Number of matches where the bowler took 2 wickets."
        ),
        R.id.boxBowl3w to StatExplanation(
            "3W HAULS",
            "Number of matches where the bowler took 3 wickets."
        ),
        R.id.boxBowl4w to StatExplanation(
            "4W HAULS",
            "Number of matches where the bowler took 4 wickets."
        ),
        R.id.boxBowl5w to StatExplanation(
            "5W HAULS",
            "Matches taking a fifer (5 or more wickets in a single match)."
        ),
        R.id.boxBowlHattricks to StatExplanation(
            "HAT-TRICKS",
            "Taking 3 consecutive wickets on 3 successive deliveries."
        ),
        R.id.boxBowlSixesConceded to StatExplanation(
            "6s CONCEDED",
            "Total maximums hit off this bowler's deliveries."
        ),

        // ── BOWLING ADVANCED ──
        R.id.boxBowlBalls to StatExplanation(
            "BALLS BOWLED",
            "Total individual deliveries bowled across entire career."
        ),
        R.id.boxBowlRunsConceded to StatExplanation(
            "RUNS GIVEN",
            "Total career runs conceded while bowling."
        ),
        R.id.boxBowlWktsPerMatch to StatExplanation(
            "WKTS / MATCH",
            "Average wickets taken per match appearance.",
            "Formula: Total Wickets ÷ Matches"
        ),
        R.id.boxBowlStrikeRate to StatExplanation(
            "BOWLING STRIKE RATE",
            "Deliveries bowled per wicket taken. Lower means faster breakthroughs.",
            "Formula: Balls Bowled ÷ Wickets"
        ),
        R.id.boxBowlDotBalls to StatExplanation(
            "DOT BALLS",
            "Total deliveries bowled where no run was scored."
        ),
        R.id.boxBowlDotPercent to StatExplanation(
            "DOT BALL %",
            "Percentage of all deliveries bowled that were scoreless dots.",
            "Formula: (Dot Balls ÷ Balls Bowled) × 100"
        ),
        R.id.boxBowlDotsPerOver to StatExplanation(
            "DOTS / OVER",
            "Average number of dot balls bowled in each 6-ball over.",
            "Formula: Dot Balls ÷ (Balls Bowled ÷ 6)"
        ),
        R.id.boxBowlExtras to StatExplanation(
            "EXTRAS CONCEDED",
            "Total illegal deliveries conceded (Wides + No-Balls)."
        ),
        R.id.boxBowlWides to StatExplanation(
            "WIDES GIVEN",
            "Total deliveries bowled outside the legal batting reach."
        ),
        R.id.boxBowlNoBalls to StatExplanation(
            "NO-BALLS GIVEN",
            "Deliveries overstepping the crease or bowled above waist height."
        ),
        R.id.boxBowlBallsPerSix to StatExplanation(
            "BALLS / 6 GIVEN",
            "Deliveries bowled on average before conceding a six.",
            "Formula: Balls Bowled ÷ Sixes Conceded"
        ),
        R.id.boxBowlBoundaryConcededPct to StatExplanation(
            "BOUNDARY GIVEN %",
            "Percentage of deliveries bowled that were hit for a boundary.",
            "Formula: (Boundary Balls ÷ Balls Bowled) × 100"
        ),
        R.id.boxBowlWktsPerInns to StatExplanation(
            "WKTS / INNINGS",
            "Average dismissals claimed per bowling outing.",
            "Formula: Total Wickets ÷ Bowling Innings"
        ),
        R.id.boxBowlBoundaryRuns to StatExplanation(
            "BOUNDARY RUNS GIVEN",
            "Total runs conceded specifically from boundary fours and sixes."
        ),
        R.id.boxBowlTeamWinPct to StatExplanation(
            "TEAM WIN %",
            "Percentage of matches won by the team when this player was in the playing XI.",
            "Formula: (Matches Won ÷ Total Matches) × 100"
        ),

        // ── FIELDING STANDARD ──
        R.id.boxFieldMatches to StatExplanation(
            "MATCHES",
            "Total career matches played in the field."
        ),
        R.id.boxFieldCatches to StatExplanation(
            "CATCHES",
            "Total clean catches taken in the outfield or behind the stumps."
        ),
        R.id.boxFieldStumpings to StatExplanation(
            "STUMPINGS",
            "Wicket-keeping dismissals removing bails while batter is out of crease."
        ),
        R.id.boxFieldRunOuts to StatExplanation(
            "RUN OUTS",
            "Direct-hit or relayed dismissals breaking stumps while batsmen run."
        ),

        // ── FIELDING ADVANCED ──
        R.id.boxFieldTotalDismissals to StatExplanation(
            "TOTAL DISMISSALS",
            "Combined fielding contributions across all formats.",
            "Formula: Catches + Stumpings + Run Outs"
        ),
        R.id.boxFieldDismissalsPerMatch to StatExplanation(
            "DISMISSALS / MATCH",
            "Average fielding dismissals contributed per match.",
            "Formula: Total Dismissals ÷ Matches"
        ),
        R.id.boxFieldImpactPts to StatExplanation(
            "FIELDING IMPACT PTS",
            "Unified fantasy fielding contribution points.",
            "Formula: (Catches × 10) + (Run Outs × 15) + (Stumpings × 15)"
        ),
        R.id.boxFieldCatchesPerMatch to StatExplanation(
            "CATCHES / MATCH",
            "Average catches held per match appearance.",
            "Formula: Catches ÷ Matches"
        ),
        R.id.boxFieldCatchShare to StatExplanation(
            "CATCH SHARE %",
            "Percentage of career fielding dismissals coming from catches.",
            "Formula: (Catches ÷ Total Dismissals) × 100"
        ),
        R.id.boxFieldMatchesPerCatch to StatExplanation(
            "MATCHES / CATCH",
            "Average matches played between each catch taken.",
            "Formula: Matches ÷ Catches"
        ),
        R.id.boxFieldRunOutsPerMatch to StatExplanation(
            "RUN OUTS / MATCH",
            "Average run outs executed per match.",
            "Formula: Run Outs ÷ Matches"
        ),
        R.id.boxFieldRunOutShare to StatExplanation(
            "RUN OUT SHARE %",
            "Percentage of career fielding dismissals coming from run outs.",
            "Formula: (Run Outs ÷ Total Dismissals) × 100"
        ),
        R.id.boxFieldMatchesPerRO to StatExplanation(
            "MATCHES / RUN OUT",
            "Average matches played between each direct run out.",
            "Formula: Matches ÷ Run Outs"
        )
    )

    private const val AUTO_DISMISS_DELAY_MS = 3000L

    private var activePopupRef: WeakReference<PopupWindow>? = null
    private var activeBubbleViewRef: WeakReference<View>? = null
    private var activeAnchorRef: WeakReference<View>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    private fun cancelDismissTimer() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
    }

    private fun scheduleAutoDismiss() {
        cancelDismissTimer()
        val r = Runnable {
            dismissCurrent(animate = true)
        }
        dismissRunnable = r
        mainHandler.postDelayed(r, AUTO_DISMISS_DELAY_MS)
    }

    fun dismissCurrent(animate: Boolean = false) {
        cancelDismissTimer()
        val popup = activePopupRef?.get()
        val bubble = activeBubbleViewRef?.get()
        activePopupRef = null
        activeBubbleViewRef = null
        activeAnchorRef = null

        if (popup == null) return

        if (animate && bubble != null && bubble.isAttachedToWindow) {
            bubble.animate()?.cancel()
            bubble.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(250)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    try {
                        popup.dismiss()
                    } catch (_: Exception) {}
                }
                .start()
        } else {
            bubble?.animate()?.cancel()
            try {
                popup.dismiss()
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachTooltips(pageView: View) {
        STAT_EXPLANATIONS.forEach { (boxId, explanation) ->
            val box = pageView.findViewById<View>(boxId) ?: return@forEach
            box.isClickable = true
            box.isLongClickable = true

            var isHolding = false

            box.setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                showTooltipBubble(v.context, v, explanation)
                scheduleAutoDismiss()
            }

            box.setOnLongClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                isHolding = true
                showTooltipBubble(v.context, v, explanation)
                cancelDismissTimer()
                true
            }

            box.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val currentAnchor = activeAnchorRef?.get()
                        val currentPopup = activePopupRef?.get()
                        if (currentPopup?.isShowing == true && currentAnchor == v) {
                            cancelDismissTimer()
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val currentAnchor = activeAnchorRef?.get()
                        val currentPopup = activePopupRef?.get()
                        if (isHolding) {
                            isHolding = false
                            scheduleAutoDismiss()
                        } else if (currentPopup?.isShowing == true && currentAnchor == v) {
                            scheduleAutoDismiss()
                        }
                    }
                }
                false
            }
        }
    }

    private fun showTooltipBubble(context: Context, anchor: View, info: StatExplanation) {
        val currentAnchor = activeAnchorRef?.get()
        val currentPopup = activePopupRef?.get()

        if (currentPopup?.isShowing == true && currentAnchor == anchor) {
            cancelDismissTimer()
            return
        }

        dismissCurrent(animate = false)

        val bubbleView = LayoutInflater.from(context).inflate(R.layout.dialog_stat_tooltip_bubble, null)
        bubbleView.findViewById<TextView>(R.id.tvBubbleTitle)?.text = info.title
        bubbleView.findViewById<TextView>(R.id.tvBubbleDescription)?.text = info.description

        val tvFormula = bubbleView.findViewById<TextView>(R.id.tvBubbleFormula)
        if (!info.formula.isNullOrBlank()) {
            tvFormula?.text = info.formula
            tvFormula?.visibility = View.VISIBLE
        } else {
            tvFormula?.visibility = View.GONE
        }

        bubbleView.alpha = 0f
        bubbleView.scaleX = 0.92f
        bubbleView.scaleY = 0.92f

        val popup = PopupWindow(
            bubbleView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isClippingEnabled = true
            elevation = 16f
            setTouchInterceptor { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    dismissCurrent(animate = true)
                    true
                } else {
                    false
                }
            }
        }

        activePopupRef = WeakReference(popup)
        activeBubbleViewRef = WeakReference(bubbleView)
        activeAnchorRef = WeakReference(anchor)

        popup.setOnDismissListener {
            cancelDismissTimer()
            if (activePopupRef?.get() == popup) {
                activePopupRef = null
                activeBubbleViewRef = null
                activeAnchorRef = null
            }
        }

        bubbleView.measure(
            View.MeasureSpec.makeMeasureSpec(context.resources.displayMetrics.widthPixels, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = bubbleView.measuredWidth
        val popupHeight = bubbleView.measuredHeight

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]

        val screenWidth = context.resources.displayMetrics.widthPixels
        val margin = (12 * context.resources.displayMetrics.density).toInt()

        // Center horizontally above or below the anchor
        var posX = anchorX + (anchor.width - popupWidth) / 2
        if (posX < margin) posX = margin
        if (posX + popupWidth > screenWidth - margin) {
            posX = screenWidth - margin - popupWidth
        }

        // Place above if room, else place below
        val posY = if (anchorY - popupHeight - margin > margin) {
            anchorY - popupHeight - (6 * context.resources.displayMetrics.density).toInt()
        } else {
            anchorY + anchor.height + (6 * context.resources.displayMetrics.density).toInt()
        }

        try {
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, posX, posY)
            bubbleView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } catch (_: Exception) {
            // Anchor might be detached or screen destroyed
        }
    }
}

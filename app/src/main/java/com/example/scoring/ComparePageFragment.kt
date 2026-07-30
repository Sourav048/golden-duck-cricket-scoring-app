package com.example.scoring

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Fragment responsible for rendering either Overall or H2H stats comparison.
 */
class ComparePageFragment : Fragment() {
    private var isOverall = false

    // UI References
    private var tvGuidance: View? = null
    private var statsContainer: View? = null
    private var containerMatches: LinearLayout? = null
    private var containerBatting: LinearLayout? = null
    private var containerBowling: LinearLayout? = null
    private var containerFielding: LinearLayout? = null
    private var containerH2H_1: LinearLayout? = null
    private var containerH2H_2: LinearLayout? = null
    private var layoutOverall: View? = null
    private var layoutH2H: View? = null
    private var h2hHeader1: View? = null
    private var h2hHeader2: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isOverall = arguments?.getBoolean("overall") ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, b: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_player_compare_page, container, false)

        tvGuidance = v.findViewById(R.id.tvCompareGuidance)
        statsContainer = v.findViewById(R.id.compareStatsContainer)
        layoutOverall = v.findViewById(R.id.layoutOverallContainer)
        layoutH2H = v.findViewById(R.id.layoutH2HContainer)
        containerMatches = v.findViewById(R.id.containerMatches)
        containerBatting = v.findViewById(R.id.containerBatting)
        containerBowling = v.findViewById(R.id.containerBowling)
        containerFielding = v.findViewById(R.id.containerFielding)
        containerH2H_1 = v.findViewById(R.id.containerH2H_1)
        containerH2H_2 = v.findViewById(R.id.containerH2H_2)
        h2hHeader1 = v.findViewById(R.id.h2h_header_1)
        h2hHeader2 = v.findViewById(R.id.h2h_header_2)

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUI()
    }

    fun updateUI() {
        val act = activity as? PlayerCompareActivity ?: return

        val p1 = act.p1
        val p2 = act.p2

        if (p1 == null || p2 == null) {
            tvGuidance?.visibility = View.VISIBLE
            statsContainer?.visibility = View.GONE

            if (tvGuidance is TextView) {
                val tv = TypedValue()
                if (context?.theme?.resolveAttribute(
                        com.google.android.material.R.attr.colorOnSurface,
                        tv,
                        true
                    ) == true
                ) {
                    (tvGuidance as TextView).setTextColor(tv.data)
                }
            }
            return
        }

        tvGuidance?.visibility = View.GONE
        statsContainer?.visibility = View.VISIBLE

        val inflater = layoutInflater

        if (isOverall) {
            layoutOverall?.visibility = View.VISIBLE
            layoutH2H?.visibility = View.GONE

            containerMatches?.removeAllViews()
            containerBatting?.removeAllViews()
            containerBowling?.removeAllViews()
            containerFielding?.removeAllViews()

            val c1 = act.s1_overall
            val c2 = act.s2_overall

            if (c1 == null || c2 == null) return

            containerMatches?.let {
                act.addCompareHeader(inflater, it, "Matches")
                act.addCompareRow(inflater, it, "MATCH PLAYED", c1.matches.toDouble(), c2.matches.toDouble(), true)
            }

            containerBatting?.let {
                act.addCompareHeader(inflater, it, "Batting")
                act.addCompareRow(inflater, it, "INNINGS", c1.batInns.toDouble(), c2.batInns.toDouble(), true)
                act.addCompareRow(inflater, it, "RUNS SCORED", c1.runs.toDouble(), c2.runs.toDouble(), true)
                act.addCompareRow(inflater, it, "BALLS FACED", c1.balls.toDouble(), c2.balls.toDouble(), true)
                act.addCompareRow(inflater, it, "HIGHEST SCORE", c1.highest.toDouble(), c2.highest.toDouble(), true)
                act.addCompareRow(inflater, it, "BATTING AVG.", c1.avg, c2.avg, true)
                act.addCompareRow(inflater, it, "NOT OUTs", c1.no.toDouble(), c2.no.toDouble(), true)
                act.addCompareRow(inflater, it, "BATTING SR", c1.sr, c2.sr, true)
                act.addCompareRow(inflater, it, "100s", c1.f100.toDouble(), c2.f100.toDouble(), true)
                act.addCompareRow(inflater, it, "80s", c1.f80.toDouble(), c2.f80.toDouble(), true)
                act.addCompareRow(inflater, it, "50s", c1.f50.toDouble(), c2.f50.toDouble(), true)
                act.addCompareRow(inflater, it, "30s", c1.f30.toDouble(), c2.f30.toDouble(), true)
                act.addCompareRow(inflater, it, "4s", c1.fours.toDouble(), c2.fours.toDouble(), true)
                act.addCompareRow(inflater, it, "6s", c1.sixes.toDouble(), c2.sixes.toDouble(), true)
            }

            containerBowling?.let {
                act.addCompareHeader(inflater, it, "Bowling")
                act.addCompareRow(inflater, it, "INNINGS", c1.bowlInns.toDouble(), c2.bowlInns.toDouble(), true)
                act.addCompareRow(inflater, it, "RUNS CONCEDED", c1.rCon.toDouble(), c2.rCon.toDouble(), false)
                act.addCompareRow(inflater, it, "WICKETS", c1.wickets.toDouble(), c2.wickets.toDouble(), true)
                act.addCompareRow(inflater, it, "BEST BOWLING", c1.bbiW.toDouble(), c2.bbiW.toDouble(), true)
                act.addCompareRow(inflater, it, "BOWLING AVG.", c1.bAvg, c2.bAvg, false)
                act.addCompareRow(inflater, it, "ECONOMY", c1.eco, c2.eco, false)
                act.addCompareRow(inflater, it, "BOWLING SR", c1.bSR, c2.bSR, false)
                act.addCompareRow(inflater, it, "2 WKTs HAUL", c1.w2.toDouble(), c2.w2.toDouble(), true)
                act.addCompareRow(inflater, it, "3 WKTs HAUL", c1.w3.toDouble(), c2.w3.toDouble(), true)
                act.addCompareRow(inflater, it, "5 WKTs HAUL", c1.w5.toDouble(), c2.w5.toDouble(), true)
            }

            containerFielding?.let {
                act.addCompareHeader(inflater, it, "Fielding")
                act.addCompareRow(inflater, it, "CATCHES", c1.catches.toDouble(), c2.catches.toDouble(), true)
                act.addCompareRow(inflater, it, "STUMPINGs", c1.stumpings.toDouble(), c2.stumpings.toDouble(), true)
                act.addCompareRow(inflater, it, "RUN OUTs", c1.ro.toDouble(), c2.ro.toDouble(), true)
            }
        } else {
            layoutOverall?.visibility = View.GONE
            layoutH2H?.visibility = View.VISIBLE

            containerH2H_1?.removeAllViews()
            containerH2H_2?.removeAllViews()

            val h = act.h2h_all ?: return

            // Section 1 Header: P1 As a Batter vs P2 As a Bowler
            h2hHeader1?.let {
                (it.findViewById<View>(R.id.tvH2HLeftRole) as TextView).text = "${p1.name} (As a Batter) 🏏"
                (it.findViewById<View>(R.id.tvH2HRightRole) as TextView).text = "🥎 ${p2.name} (As a Bowler)"
                (it.findViewById<View>(R.id.tvH2HInningsBar) as TextView).text = "${h.sharedInns} INNINGS"
            }

            containerH2H_1?.let {
                act.addCompareRow(inflater, it, "RUNS SCORED / BALLS BOWL", h.r1.toDouble(), h.bb2.toDouble(), true, false)
                act.addCompareRow(inflater, it, "BATTING SR / ECONOMY", h.sr1, h.eco2, true, false)
                act.addCompareRow(inflater, it, "WICKETS TAKEN", 0.0, h.w2.toDouble(), true)
                act.addCompareRow(inflater, it, "BATTING AVG / BOWLING SR", h.avg1, h.bsr2, true, false)
                act.addCompareRow(inflater, it, "CATCHES TAKEN", h.c1.toDouble(), h.c2.toDouble(), true)
                act.addCompareRow(inflater, it, "STUMPINGS", h.s1.toDouble(), h.s2.toDouble(), true)
                act.addCompareRow(inflater, it, "RUN OUTs", h.ro1.toDouble(), h.ro2.toDouble(), true)
            }

            // Section 2 Header: P1 As a Bowler vs P2 As a Batter
            h2hHeader2?.let {
                (it.findViewById<View>(R.id.tvH2HLeftRole) as TextView).text = "${p1.name} (As a Bowler) 🥎"
                (it.findViewById<View>(R.id.tvH2HRightRole) as TextView).text = "🏏 ${p2.name} (As a Batter)"
                (it.findViewById<View>(R.id.tvH2HInningsBar) as TextView).text = "${h.sharedInns} INNINGS"
            }

            containerH2H_2?.let {
                act.addCompareRow(inflater, it, "BALLS BOWL / RUNS SCORED", h.bb1.toDouble(), h.r2.toDouble(), true, false)
                act.addCompareRow(inflater, it, "ECONOMY / BATTING SR", h.eco1, h.sr2, false, false)
                act.addCompareRow(inflater, it, "WICKETS TAKEN", h.w1.toDouble(), 0.0, true)
                act.addCompareRow(inflater, it, "BOWLING SR / BATTING AVG", h.bsr1, h.avg2, false, false)
                act.addCompareRow(inflater, it, "CATCHES TAKEN", h.c1.toDouble(), h.c2.toDouble(), true)
                act.addCompareRow(inflater, it, "STUMPINGS", h.s1.toDouble(), h.s2.toDouble(), true)
                act.addCompareRow(inflater, it, "RUN OUTs", h.ro1.toDouble(), h.ro2.toDouble(), true)
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(overall: Boolean): ComparePageFragment {
            val f = ComparePageFragment()
            val b = Bundle()
            b.putBoolean("overall", overall)
            f.arguments = b
            return f
        }
    }
}

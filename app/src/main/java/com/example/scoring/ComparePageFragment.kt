package com.example.scoring

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlin.math.max
import kotlin.math.min

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
    private var radarChart: RadarChart? = null
    private var layoutRadarContainer: View? = null
    private var viewLegendColor1: View? = null
    private var viewLegendColor2: View? = null
    private var tvLegendName1: TextView? = null
    private var tvLegendName2: TextView? = null

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
        radarChart = v.findViewById(R.id.radarChart)
        layoutRadarContainer = v.findViewById(R.id.layoutRadarContainer)
        viewLegendColor1 = v.findViewById(R.id.viewLegendColor1)
        viewLegendColor2 = v.findViewById(R.id.viewLegendColor2)
        tvLegendName1 = v.findViewById(R.id.tvLegendName1)
        tvLegendName2 = v.findViewById(R.id.tvLegendName2)

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

            setupRadarChart(c1, c2, p1.name ?: "P1", p2.name ?: "P2")

            containerMatches?.let {
                act.addCompareHeader(inflater, it, "Matches")
                act.addCompareRow(inflater, it, "MATCH PLAYED", c1.matches.toDouble(), c2.matches.toDouble(), true)
            }

            containerBatting?.let {
                act.addCompareHeader(inflater, it, "Batting")
                act.addCompareRow(inflater, it, "INNINGS", c1.batInns.toDouble(), c2.batInns.toDouble(), true)
                act.addCompareRow(inflater, it, "RUNS SCORED", c1.runs.toDouble(), c2.runs.toDouble(), true)
                act.addCompareRow(inflater, it, "BOUNDARY %", c1.boundaryPct, c2.boundaryPct, true)
                act.addCompareRow(inflater, it, "BALLS FACED", c1.balls.toDouble(), c2.balls.toDouble(), true)
                act.addCompareRow(inflater, it, "HIGHEST SCORE", c1.highest.toDouble(), c2.highest.toDouble(), true)
                act.addCompareRow(inflater, it, "SETTING AVG (1st)", c1.settingAvg, c2.settingAvg, true)
                act.addCompareRow(inflater, it, "CHASING AVG (2nd)", c1.chasingAvg, c2.chasingAvg, true)
                act.addCompareRow(inflater, it, "BATTING AVG.", c1.avg, c2.avg, true)
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

            // Recent Form Section
            val r1 = act.s1_recent
            val r2 = act.s2_recent
            if (r1 != null && r2 != null) {
                val layoutRecent = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 32
                    }
                }
                containerFielding?.parent?.let { (it as ViewGroup).addView(layoutRecent) }
                
                act.addCompareHeader(inflater, layoutRecent, "Recent Form (Last 5 Games)")
                act.addCompareRow(inflater, layoutRecent, "AVG (LAST 5)", r1.avg, r2.avg, true)
                act.addCompareRow(inflater, layoutRecent, "SR (LAST 5)", r1.sr, r2.sr, true)
                act.addCompareRow(inflater, layoutRecent, "WKTS (LAST 5)", r1.wickets.toDouble(), r2.wickets.toDouble(), true)
                act.addCompareRow(inflater, layoutRecent, "ECO (LAST 5)", r1.eco, r2.eco, false)
            }
        } else {
            layoutOverall?.visibility = View.GONE
            layoutH2H?.visibility = View.VISIBLE

            containerH2H_1?.removeAllViews()
            containerH2H_2?.removeAllViews()

            val h = act.h2h_all ?: return

            // Section 1 Header: P1 As a Batter vs P2 As a Bowler
            h2hHeader1?.let {
                (it.findViewById<View>(R.id.tvH2HLeftRole) as TextView).text = "As Batter (🏏 ${p1.name})"
                (it.findViewById<View>(R.id.tvH2HRightRole) as TextView).text = "As Bowler (🥎 ${p2.name})"
                (it.findViewById<View>(R.id.tvH2HInningsBar) as TextView).text = "${h.sharedMatches} MATCHES AGAINST"
                
                val badgeTv = it.findViewById<TextView>(R.id.tvH2HBadge)
                val isBunny = h.out1by2 >= 3
                val isOwner = h.sr1v2 >= 170.0 && h.r1v2 >= 30
                
                when {
                    isBunny && isOwner -> {
                        badgeTv.text = "⚔️ FIERCE RIVALRY: Bunny vs Owner"
                        badgeTv.visibility = View.VISIBLE
                    }
                    isBunny -> {
                        badgeTv.text = "🐰 ${p1.name} is ${p2.name}'s Bunny"
                        badgeTv.visibility = View.VISIBLE
                    }
                    isOwner -> {
                        badgeTv.text = "👑 ${p1.name} OWNS ${p2.name}"
                        badgeTv.visibility = View.VISIBLE
                    }
                    else -> badgeTv.visibility = View.GONE
                }
            }

            containerH2H_1?.let {
                // Section 1: P1 (Batting) vs P2 (Bowling)
                act.addCompareRow(inflater, it, "RUNS SCORED / BALLS BOWL", h.r1v2.toDouble(), h.b1v2.toDouble(), true, false)
                act.addCompareRow(inflater, it, "BATTING SR / ECONOMY", h.sr1v2, h.eco2v1, true, false)
                act.addCompareRow(inflater, it, "WICKETS TAKEN", 0.0, h.out1by2.toDouble(), true)
                act.addCompareRow(inflater, it, "BATTING AVG / BOWLING SR", h.avg1v2, h.bsr2v1, true, false)
                act.addCompareRow(inflater, it, "CATCHES TAKEN", h.catches1of2.toDouble(), h.catches2of1.toDouble(), true)
                act.addCompareRow(inflater, it, "STUMPINGS", h.stumps1of2.toDouble(), h.stumps2of1.toDouble(), true)
                act.addCompareRow(inflater, it, "RUN OUTs", h.ro1of2.toDouble(), h.ro2of1.toDouble(), true)
            }

            // Section 2 Header: P1 As a Bowler vs P2 As a Batter
            h2hHeader2?.let {
                (it.findViewById<View>(R.id.tvH2HLeftRole) as TextView).text = "As Bowler (🥎 ${p1.name})"
                (it.findViewById<View>(R.id.tvH2HRightRole) as TextView).text = "As Batter (🏏 ${p2.name})"
                (it.findViewById<View>(R.id.tvH2HInningsBar) as TextView).text = "${h.sharedMatches} MATCHES AGAINST"
                
                val badgeTv = it.findViewById<TextView>(R.id.tvH2HBadge)
                val isBunny = h.out2by1 >= 3
                val isOwner = h.sr2v1 >= 170.0 && h.r2v1 >= 30
                
                when {
                    isBunny && isOwner -> {
                        badgeTv.text = "⚔️ FIERCE RIVALRY: Bunny vs Owner"
                        badgeTv.visibility = View.VISIBLE
                    }
                    isBunny -> {
                        badgeTv.text = "🐰 ${p2.name} is ${p1.name}'s Bunny"
                        badgeTv.visibility = View.VISIBLE
                    }
                    isOwner -> {
                        badgeTv.text = "👑 ${p2.name} OWNS ${p1.name}"
                        badgeTv.visibility = View.VISIBLE
                    }
                    else -> badgeTv.visibility = View.GONE
                }
            }

            containerH2H_2?.let {
                // Section 2: P1 (Bowling) vs P2 (Batting)
                act.addCompareRow(inflater, it, "BALLS BOWL / RUNS SCORED", h.b2v1.toDouble(), h.r2v1.toDouble(), false, false)
                act.addCompareRow(inflater, it, "ECONOMY / BATTING SR", h.eco1v2, h.sr2v1, false, false)
                act.addCompareRow(inflater, it, "WICKETS TAKEN", h.out2by1.toDouble(), 0.0, true)
                act.addCompareRow(inflater, it, "BOWLING SR / BATTING AVG", h.bsr1v2, h.avg2v1, false, false)
                act.addCompareRow(inflater, it, "CATCHES TAKEN", h.catches1of2.toDouble(), h.catches2of1.toDouble(), true)
                act.addCompareRow(inflater, it, "STUMPINGS", h.stumps1of2.toDouble(), h.stumps2of1.toDouble(), true)
                act.addCompareRow(inflater, it, "RUN OUTs", h.ro1of2.toDouble(), h.ro2of1.toDouble(), true)
            }
        }
    }

    private fun setupRadarChart(c1: PlayerCompareActivity.CareerStats, c2: PlayerCompareActivity.CareerStats, n1: String, n2: String) {
        val chart = radarChart ?: return
        layoutRadarContainer?.visibility = View.VISIBLE
        
        // Colors for high contrast
        val color1 = if (ThemeManager.isDarkMode(requireContext())) 0xFF80CBC4.toInt() else 0xFF00695C.toInt() 
        val color2 = if (ThemeManager.isDarkMode(requireContext())) 0xFFFFB74D.toInt() else 0xFF757575.toInt()

        // Update Custom Legend
        viewLegendColor1?.backgroundTintList = android.content.res.ColorStateList.valueOf(color1)
        viewLegendColor2?.backgroundTintList = android.content.res.ColorStateList.valueOf(color2)
        tvLegendName1?.text = n1
        tvLegendName2?.text = n2

        // Data Normalization (0-100 scale)
        fun normalize(c: PlayerCompareActivity.CareerStats): List<RadarEntry> {
            val power = (min(max(0.0, c.sr), 200.0) / 200.0 * 100.0).toFloat()
            val consistency = (min(max(0.0, c.avg), 50.0) / 50.0 * 100.0).toFloat()
            // Bowling SR: lower is better. 0 is elite, 30+ is poor.
            val lethality = if (c.bSR > 0) (max(0.0, 30.0 - c.bSR) / 30.0 * 100.0).toFloat() else 0f
            // Economy: lower is better. 4 is elite, 12 is poor.
            val control = (max(0.0, 12.0 - c.eco) / 12.0 * 100.0).toFloat()
            // Fielding: Catches/RO per match. 0.5 per match is very good.
            val fieldTotal = (c.catches + c.ro).toDouble()
            val reflexes = (min(max(0.0, fieldTotal), (c.matches * 0.5)) / (max(1.0, c.matches * 0.5)) * 100.0).toFloat()

            return listOf(
                RadarEntry(power),
                RadarEntry(consistency),
                RadarEntry(lethality),
                RadarEntry(control),
                RadarEntry(reflexes)
            )
        }

        val entries1 = normalize(c1)
        val entries2 = normalize(c2)

        val set1 = RadarDataSet(entries1, n1).apply {
            // Player 1: Teal (Light) or Light Teal (Dark)
            val color1_local = if (ThemeManager.isDarkMode(requireContext())) 0xFF80CBC4.toInt() else 0xFF00695C.toInt() 
            setColor(color1_local)
            fillColor = color1_local
            setDrawFilled(true)
            fillAlpha = 150
            lineWidth = 3f
            isDrawHighlightCircleEnabled = true
            setDrawHighlightIndicators(false)
        }

        val set2 = RadarDataSet(entries2, n2).apply {
            // Player 2: Orange/Amber (Dark) or Dark Grey (Light)
            val color2_local = if (ThemeManager.isDarkMode(requireContext())) 0xFFFFB74D.toInt() else 0xFF757575.toInt()
            setColor(color2_local)
            fillColor = color2_local
            setDrawFilled(true)
            fillAlpha = 150
            lineWidth = 3f
            isDrawHighlightCircleEnabled = true
            setDrawHighlightIndicators(false)
        }

        chart.data = RadarData(set1, set2).apply {
            setValueTextSize(0f)
            setDrawValues(false)
        }

        val labels = arrayOf("Strike Rate", "Average", "Bowling SR", "Economy", "Fielding")
        chart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            textSize = 10f
            textColor = ThemeManager.getThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurface)
            yOffset = 0f
            xOffset = 0f
        }

        // Web Styling
        chart.webColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        chart.webColorInner = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        chart.webLineWidth = 1.5f
        chart.webLineWidthInner = 1f
        chart.webAlpha = 100

        chart.yAxis.apply {
            setDrawLabels(false)
            axisMinimum = 0f
            axisMaximum = 100f
        }

        chart.legend.isEnabled = false
        chart.description.isEnabled = false
        chart.invalidate()
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

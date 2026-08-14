package com.example.scoring

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scoring.RankingRegistry.applyPrestige
import java.util.Locale

class InningsScorecardFragment : Fragment() {
    private var isFirstInnings = false
    private var tvTeamName: TextView? = null
    private var tvScore: TextView? = null
    private var tvExtras: TextView? = null
    private var tvTotal: TextView? = null
    private var tvYetToBatLabel: TextView? = null
    private var tvYetToBat: TextView? = null
    private var rvBatters: RecyclerView? = null
    private var rvBowlers: RecyclerView? = null
    private var rvFow: RecyclerView? = null
    private var rvPartnerships: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isFirstInnings = arguments?.getBoolean("first") ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val v = inflater.inflate(R.layout.fragment_innings_scorecard, container, false)
        tvTeamName = v.findViewById(R.id.tvInningsTeamName)
        tvScore = v.findViewById(R.id.tvInningsScore)
        tvExtras = v.findViewById(R.id.tvExtrasValue)
        tvTotal = v.findViewById(R.id.tvTotalValue)
        tvYetToBatLabel = v.findViewById(R.id.tvYetToBatLabel)
        tvYetToBat = v.findViewById(R.id.tvYetToBat)

        rvBatters = v.findViewById(R.id.rvBatters)
        rvBowlers = v.findViewById(R.id.rvBowlers)
        rvFow = v.findViewById(R.id.rvFow)
        rvPartnerships = v.findViewById(R.id.rvPartnerships)

        rvBatters?.layoutManager = LinearLayoutManager(context)
        rvBowlers?.layoutManager = LinearLayoutManager(context)
        rvFow?.layoutManager = LinearLayoutManager(context)
        rvPartnerships?.layoutManager = LinearLayoutManager(context)

        updateUI()
        return v
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    fun updateUI() {
        if (!isAdded || view == null || activity == null) return
        val act = activity as? ScoringProvider ?: return
        val match = act.match ?: return

        val battingTeamName: String?
        val bowlingTeamName: String?
        val innings = if (isFirstInnings) match.firstInnings else match.secondInnings
        val actIsFinished = act.isFinished || act.isAbandoned

        if (innings != null) {
            battingTeamName = innings.battingTeam
            bowlingTeamName = innings.bowlingTeam

            tvTeamName?.text = battingTeamName?.uppercase()

            val maxO: Int = innings.revisedMaxOvers ?: innings.maxOvers
            val scoreStr = String.format(
                Locale.US, "%d/%d (%s/%d Ov)",
                innings.totalRuns, innings.totalWickets, innings.oversDisplay, maxO,
            )

            tvScore?.text = scoreStr

            val extrasDetail = String.format(
                Locale.US, "%d (b %d, lb %d, w %d, nb %d, p %d)",
                innings.extras, innings.byes, innings.legByes,
                innings.wides, innings.noBalls, innings.penalties,
            )
            tvExtras?.text = extrasDetail

            val rr = innings.currentRunRate
            val label = "CRR"


            // Format: 1-0 (0.0 Overs, CRR: 0.00)
            val totalDetail = String.format(
                Locale.US, "%d-%d (%s Overs, %s: %.2f)",
                innings.totalRuns, innings.totalWickets, innings.oversDisplay, label, rr,
            )
            tvTotal?.text = totalDetail
        } else {
            // Innings not started
            if (isFirstInnings) {
                battingTeamName = match.teamA // Fallback
                bowlingTeamName = match.teamB
            } else {
                // If 1st innings exists, 2nd innings batting team is 1st innings bowling team
                if (match.firstInnings != null) {
                    battingTeamName = match.firstInnings!!.bowlingTeam
                    bowlingTeamName = match.firstInnings!!.battingTeam
                } else {
                    battingTeamName = match.teamB
                    bowlingTeamName = match.teamA
                }
            }
            tvTeamName?.text = battingTeamName?.uppercase()
            tvScore?.setText(R.string.innings_will_start)
            tvExtras?.text = "0"
            tvTotal?.text = "0-0"
        }

        // --- Batters Section ---
        val teamA = (match.teamA ?: "").trim()
        val isTeamA = battingTeamName?.trim()?.equals(teamA, ignoreCase = true) == true
        
        val battingOrder: ArrayList<String?>? = if (isTeamA) act.teamANames else act.teamBNames
        val playedBatters: MutableList<Player> = ArrayList()
        val yetToBat: MutableList<String?> = ArrayList()

        val isViewingCurrentInnings = (innings === match.currentInnings) && (act.isScorer)
        val activeStriker = if (isViewingCurrentInnings) act.striker else null
        val activeNonStriker = if (isViewingCurrentInnings) act.nonStriker else null

        battingOrder?.let {
            for (name in it) {
                if (name == null) continue
                val p = act.getPlayerFromCache(name) ?: continue

                // Players who are currently at the crease should definitely show up
                val isAtCrease = (activeStriker != null && p.name == activeStriker.name) ||
                                 (activeNonStriker != null && p.name == activeNonStriker.name)
                
                // Detection for "Played":
                // 1. Faced a ball
                // 2. Is out
                // 3. Is currently at the crease (or was at the crease at the end)
                // 4. Has dismissal info set (e.g. retired hurt)
                val hasPlayed = (p.ballsFaced > 0) || p.isOut || isAtCrease || (p.dismissalInfo != "not out")

                if (hasPlayed) playedBatters.add(p)
                else yetToBat.add(name)
            }
        }

        // FIX: Sort played batters chronologically based on their entry into partnerships
        if ((innings != null) && (innings.partnerships.isNotEmpty())) {
            val entryOrder = mutableListOf<String>()
            for (p in innings.partnerships) {
                p.batter1?.let { if (!entryOrder.contains(it) && it != "N/A" && it != "-") entryOrder.add(it) }
                p.batter2?.let { if (!entryOrder.contains(it) && it != "N/A" && it != "-") entryOrder.add(it) }
            }
            
            try {
                playedBatters.sortWith { p1, p2 ->
                    val name1 = p1.name ?: ""
                    val name2 = p2.name ?: ""
                    
                    val idx1 = entryOrder.indexOf(name1)
                    val idx2 = entryOrder.indexOf(name2)
                    
                    if (idx1 != -1 && idx2 != -1) {
                        idx1.compareTo(idx2)
                    } else if (idx1 != -1) {
                        -1 // idx2 is -1, so p1 comes first
                    } else if (idx2 != -1) {
                        1 // idx1 is -1, so p2 comes first
                    } else {
                        // Both -1? Use original squad order as tie-breaker
                        val sIdx1 = battingOrder?.indexOf(name1) ?: 0
                        val sIdx2 = battingOrder?.indexOf(name2) ?: 0
                        sIdx1.compareTo(sIdx2)
                    }
                }
            } catch (e: Exception) {
                Log.e("SCORECARD", "Sort failed: " + e.message)
            }
        }

        val activeStrikerName = activeStriker?.name
        val activeNonStrikerName = activeNonStriker?.name
        
        rvBatters?.adapter = ScorecardBatterAdapter(playedBatters, activeStrikerName, activeNonStrikerName)

        // --- Yet to Bat ---
        tvYetToBatLabel?.text = if (actIsFinished) "Did Not Bat" else "Yet to bat"

        val ytbStr = StringBuilder()
        for (i in yetToBat.indices) {
            ytbStr.append(yetToBat[i])
            if (i < (yetToBat.size - 1)) ytbStr.append(", ")
        }
        tvYetToBat?.text = if (ytbStr.isNotEmpty()) ytbStr.toString() else "-"

        // --- Bowlers Section ---
        val isBowlingTeamA = bowlingTeamName?.trim()?.equals(teamA, ignoreCase = true) == true
        val bowlingTeamList: ArrayList<String?>? = if (isBowlingTeamA) act.teamANames else act.teamBNames
        val playedBowlers: MutableList<Player> = ArrayList()
        val activeBowler = act.bowler

        bowlingTeamList?.let {
            for (name in it) {
                val p = act.getPlayerFromCache(name)
                val isActive = (activeBowler != null) && (name == activeBowler.name)
                
                // Show bowler if they are currently active OR if they have any recorded stats 
                // (including illegal balls like Wides/No-Balls)
                val hasBowled = p != null && (p.ballsBowled > 0 || p.runsConceded > 0 || 
                                p.wicketsTaken > 0 || p.widesConceded > 0 || p.noBallsConceded > 0)
                                
                if (p != null && (hasBowled || isActive)) playedBowlers.add(p)
            }
        }
        rvBowlers?.adapter = ScorecardBowlerAdapter(playedBowlers)

        // --- Fall of Wickets ---
        val fowData: MutableList<Array<String?>?> = ArrayList()
        innings?.let {
            for (event in it.fallOfWickets) {
                fowData.add(
                    arrayOf(
                        event!!.playerName,
                        (event.scoreAtWicket.toString() + "-" + event.wicketNumber),
                        ("(" + event.over + ")"),
                    ),
                )
            }
        }
        rvFow?.adapter = FowAdapter(fowData)

        // --- Partnerships ---
        val pshipData: MutableList<PartnershipItem> = ArrayList()
        innings?.let {
            for (i in it.partnerships.indices) {
                val event = it.partnerships[i]
                
                // Filter: Show the active (last) partnership OR any that have runs/balls recorded.
                // This prevents "placeholder" rows from appearing when players are being swapped.
                val isActive = (i == it.partnerships.size - 1)
                val hasData = (event.runs > 0 || event.balls > 0)
                
                if (isActive || hasData) {
                    pshipData.add(
                        PartnershipItem(
                            event.batter1, event.batter2, event.runs, event.balls,
                            event.b1Runs, event.b1Balls, event.b2Runs, event.b2Balls,
                        ),
                    )
                }
            }
        }
        rvPartnerships?.adapter = PartnershipAdapter(pshipData)
    }

    internal class PartnershipItem(
        var b1: String?,
        var b2: String?,
        var r: Int,
        var b: Int,
        var b1r: Int,
        var b1b: Int,
        var b2r: Int,
        var b2b: Int,
    )

    internal class PartnershipAdapter(private val data: MutableList<PartnershipItem>) :
        RecyclerView.Adapter<PartnershipAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_scorecard_partnership, parent, false),
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = data[position]
            
            val name1 = if (item.b1.isNullOrEmpty()) "-" else item.b1
            val name2 = if (item.b2.isNullOrEmpty() || item.b2 == "N/A") "N/A" else item.b2

            holder.b1.text = name1
            holder.b1Stats.text = String.format(Locale.US, " %d(%d)", item.b1r, item.b1b)

            holder.b2.text = name2
            if (name2 == "N/A") {
                holder.b2Stats.visibility = View.GONE
            } else {
                holder.b2Stats.visibility = View.VISIBLE
                holder.b2Stats.text = String.format(Locale.US, " %d(%d)", item.b2r, item.b2b)
            }

            holder.total.text = String.format(Locale.US, "%d(%d)", item.r, item.b)
        }

        override fun getItemCount(): Int {
            return data.size
        }

        internal class Holder(v: View) : RecyclerView.ViewHolder(v) {
            var b1: TextView = v.findViewById(R.id.tvPshipBatter1)
            var b1Stats: TextView = v.findViewById(R.id.tvPshipB1Stats)
            var b2: TextView = v.findViewById(R.id.tvPshipBatter2)
            var b2Stats: TextView = v.findViewById(R.id.tvPshipB2Stats)
            var total: TextView = v.findViewById(R.id.tvPshipTotal)
        }
    }

    internal class FowAdapter(private val data: MutableList<Array<String?>?>) :
        RecyclerView.Adapter<FowAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_scorecard_fow, parent, false),
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = data[position] ?: return
            holder.player.text = item[0]
            holder.score.text = item[1]
            holder.over.text = item[2]
        }

        override fun getItemCount(): Int {
            return data.size
        }

        internal class Holder(v: View) : RecyclerView.ViewHolder(v) {
            var player: TextView = v.findViewById(R.id.tvFowPlayer)
            var score: TextView = v.findViewById(R.id.tvFowScore)
            var over: TextView = v.findViewById(R.id.tvFowOver)
        }
    }

    internal class ScorecardBatterAdapter(
        private val data: MutableList<Player>,
        private val strikerName: String?,
        private val nonStrikerName: String?
    ) :
        RecyclerView.Adapter<ScorecardBatterAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_scorecard_batter, parent, false),
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val p = data[position]
            holder.name.text = p.name
            applyPrestige(p.id, holder.name, null)
            
            val isAtCrease = (p.name != null) && (p.name == strikerName || p.name == nonStrikerName)
            val status = when {
                p.isOut -> p.dismissalInfo
                isAtCrease -> "not out"
                p.dismissalInfo == "retired hurt" -> "retired hurt"
                else -> "not out"
            }
            holder.dismissal.text = status
            holder.r.text = p.runsScored.toString()
            holder.b.text = p.ballsFaced.toString()
            holder.fours.text = p.fours.toString()
            holder.sixes.text = p.sixes.toString()
            val sr = if (p.ballsFaced > 0) (p.runsScored.toDouble() / p.ballsFaced) * 100 else 0.0
            holder.sr.text = String.format(Locale.US, "%.1f", sr)

            // Format duration: e.g. 2m 23s (Now using milestone-synced static value)
            val totalSecs = p.minutesPlayed
            val m = totalSecs / 60
            val s = totalSecs % 60
            holder.mins.text = String.format(Locale.US, "%dm%ds", m, s)
        }

        override fun getItemCount(): Int {
            return data.size
        }

        internal class Holder(v: View) : RecyclerView.ViewHolder(v) {
            var name: TextView = v.findViewById(R.id.tvScorecardName)
            var dismissal: TextView = v.findViewById(R.id.tvScorecardDismissal)
            var r: TextView = v.findViewById(R.id.tvScorecardR)
            var b: TextView = v.findViewById(R.id.tvScorecardB)
            var fours: TextView = v.findViewById(R.id.tvScorecard4s)
            var sixes: TextView = v.findViewById(R.id.tvScorecard6s)
            var sr: TextView = v.findViewById(R.id.tvScorecardSR)
            var mins: TextView = v.findViewById(R.id.tvScorecardMins)
        }
    }

    internal class ScorecardBowlerAdapter(private val data: MutableList<Player>) :
        RecyclerView.Adapter<ScorecardBowlerAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_scorecard_bowler, parent, false),
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val p = data[position]
            holder.name.text = p.name
            applyPrestige(p.id, holder.name, null)
            holder.o.text = p.oversBowledDisplay
            holder.m.text = p.maidens.toString()
            holder.r.text = p.runsConceded.toString()
            holder.w.text = p.wicketsTaken.toString()
            holder.nb.text = p.noBallsConceded.toString()
            holder.wd.text = p.widesConceded.toString()
            val eco =
                if (p.ballsBowled > 0) (p.runsConceded.toDouble() / p.ballsBowled) * 6 else 0.0
            holder.eco.text = String.format(Locale.US, "%.2f", eco)
        }

        override fun getItemCount(): Int {
            return data.size
        }

        internal class Holder(v: View) : RecyclerView.ViewHolder(v) {
            var name: TextView = v.findViewById(R.id.tvScorecardBowlName)
            var o: TextView = v.findViewById(R.id.tvScorecardBowlO)
            var m: TextView = v.findViewById(R.id.tvScorecardBowlM)
            var r: TextView = v.findViewById(R.id.tvScorecardBowlR)
            var w: TextView = v.findViewById(R.id.tvScorecardBowlW)
            var nb: TextView = v.findViewById(R.id.tvScorecardBowlNB)
            var wd: TextView = v.findViewById(R.id.tvScorecardBowlWD)
            var eco: TextView = v.findViewById(R.id.tvScorecardBowlEco)
        }
    }

    companion object {
        fun newInstance(first: Boolean): InningsScorecardFragment {
            val f = InningsScorecardFragment()
            val b = Bundle()
            b.putBoolean("first", first)
            f.arguments = b
            return f
        }
    }
}

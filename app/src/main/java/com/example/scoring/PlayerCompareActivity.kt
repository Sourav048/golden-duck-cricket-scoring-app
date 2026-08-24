package com.example.scoring

import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.scoring.AppDatabase.Companion.getInstance
import com.example.scoring.ComparePageFragment.Companion.newInstance
import com.example.scoring.RankingRegistry.applyPrestige
import com.example.scoring.RankingRegistry.refresh
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.util.Locale
import kotlin.math.abs

class PlayerCompareActivity : BaseActivity() {
    private var db: AppDatabase? = null
    private var allPlayers: MutableList<PlayerEntity?>? = null

    var p1: PlayerEntity? = null
    var p2: PlayerEntity? = null
    var s1_overall: CareerStats? = null
    var s2_overall: CareerStats? = null
    var s1_recent: CareerStats? = null
    var s2_recent: CareerStats? = null
    var h2h_all: H2HStats? = null

    private var tvName1: TextView? = null
    private var tvName2: TextView? = null
    private var tvPlaceholder: TextView? = null
    private var ivPhoto1: ShapeableImageView? = null
    private var ivPhoto2: ShapeableImageView? = null
    private var viewPager: ViewPager2? = null
    private var tabLayout: TabLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_compare)

        db = getInstance(this)

        tvName1 = findViewById(R.id.tvCompareName1)
        tvName2 = findViewById(R.id.tvCompareName2)
        ivPhoto1 = findViewById(R.id.ivComparePhoto1)
        ivPhoto2 = findViewById(R.id.ivComparePhoto2)
        tvPlaceholder = findViewById(R.id.tvVSLabel)
        viewPager = findViewById(R.id.viewPagerCompare)
        tabLayout = findViewById(R.id.tabLayoutCompare)

        refresh(this, null)

        setupSwipeNavigation()

        findViewById<View>(R.id.layoutSelectPlayer1).setOnClickListener { showPlayerPicker(1) }
        findViewById<View>(R.id.layoutSelectPlayer2).setOnClickListener { showPlayerPicker(2) }
        findViewById<View>(R.id.btnDoneCompare).setOnClickListener { finish() }

        updatePlayerSlot(1, null)
        updatePlayerSlot(2, null)

        loadAllPlayers()
    }

    private fun setupSwipeNavigation() {
        viewPager?.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(pos: Int): Fragment = newInstance(pos == 0)
            override fun getItemCount(): Int = 2
        }

        tabLayout?.let { tl ->
            viewPager?.let { vp ->
                TabLayoutMediator(tl, vp) { tab, pos ->
                    tab.text = if (pos == 0) "Overall" else "vs Each Other"
                }.attach()
            }
        }

        viewPager?.visibility = View.VISIBLE
        tabLayout?.visibility = View.GONE
    }

    private fun loadAllPlayers() {
        val gId = GullySyncManager.getCurrentGullyId(this) ?: "local"
        AppDatabase.ioExecutor.execute {
            allPlayers = db?.playerDao()?.getAllPlayersByGully(gId)?.toMutableList()
        }
    }

    private fun showPlayerPicker(slot: Int) {
        val players = allPlayers
        if (players.isNullOrEmpty()) return
        val names = players.map { it?.name ?: "Unknown" }.toTypedArray()
        showDynamicDialog {
            setTitle("Select Player $slot")
            setItems(names) { d, w ->
                val selected = players[w]
                if (slot == 1) p1 = selected else p2 = selected
                updatePlayerSlot(slot, selected)
            }
        }
    }

    private fun updatePlayerSlot(slot: Int, player: PlayerEntity?) {
        val name = if (slot == 1) tvName1 else tvName2
        val photo = if (slot == 1) ivPhoto1 else ivPhoto2

        val contrastColor = TypedValue().let { tv ->
            val attr = if (slot == 1) com.google.android.material.R.attr.colorOnPrimary 
                      else com.google.android.material.R.attr.colorOnSecondary
            if (theme.resolveAttribute(attr, tv, true)) tv.data else Color.WHITE
        }

        // Reset formatting
        name?.setShadowLayer(0f, 0f, 0f, 0)
        photo?.strokeWidth = 0f
        photo?.strokeColor = null

        if (player == null) {
            name?.text = "Select Player"
            name?.setTextColor(contrastColor)
            photo?.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }

        name?.text = player.name
        applyPrestige(player.id, name, photo, contrastColor)
        
        // Final sanity check: if ranking system didn't color it, force contrastColor
        if (!RankingRegistry.isPrestigeColor(name?.currentTextColor ?: 0)) {
            name?.setTextColor(contrastColor)
        }

        if (!player.photoUri.isNullOrEmpty()) {
            try {
                photo?.setImageURI(Uri.parse(player.photoUri))
            } catch (e: Exception) {
                photo?.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            photo?.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        fetchData()
    }

    private fun fetchData() {
        val player1 = p1
        val player2 = p2
        if (player1 == null || player2 == null) return
        
        AppDatabase.ioExecutor.execute {
            val stats1 = db?.statsDao()?.getStatsByPlayer(player1.id)?.filterNotNull() ?: emptyList()
            val stats2 = db?.statsDao()?.getStatsByPlayer(player2.id)?.filterNotNull() ?: emptyList()
            
            // Map match dates and details for sorting and clutch logic
            val matchDao = db?.matchDao()
            val matchMap = mutableMapOf<String, MatchEntity>()
            
            val allSharedMatchIds = (stats1.map { it.matchId } + stats2.map { it.matchId }).filterNotNull().distinct()
            for (mId in allSharedMatchIds) {
                matchDao?.getMatchById(mId)?.let { matchMap[mId] = it }
            }
            
            fun getRecentStats(playerStats: List<PlayerMatchStatEntity>): CareerStats {
                val sorted = playerStats.sortedByDescending { ps ->
                    matchMap[ps.matchId]?.playedAt ?: 0L
                }
                return CareerStats(sorted.take(5).toMutableList(), matchMap)
            }

            s1_overall = CareerStats(stats1.toMutableList(), matchMap)
            s2_overall = CareerStats(stats2.toMutableList(), matchMap)
            s1_recent = getRecentStats(stats1)
            s2_recent = getRecentStats(stats2)
            
            // Find shared matches where they were on OPPOSITE teams
            val oppositionMatches = mutableListOf<Pair<PlayerMatchStatEntity, PlayerMatchStatEntity>>()
            val sharedMatchIds = stats1.map { it.matchId }.intersect(stats2.map { it.matchId }.toSet())
            
            val matchEntities = mutableListOf<MatchEntity>()
            
            for (mId in sharedMatchIds) {
                if (mId == null) continue
                val s1 = stats1.find { it.matchId == mId }
                val s2 = stats2.find { it.matchId == mId }
                
                if (s1 != null && s2 != null && s1.teamName != s2.teamName) {
                    oppositionMatches.add(s1 to s2)
                    matchMap[mId]?.let { matchEntities.add(it) }
                }
            }
            
            h2h_all = H2HStats(oppositionMatches, matchEntities, player1.name ?: "", player2.name ?: "")
            
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                viewPager?.visibility = View.VISIBLE
                tabLayout?.visibility = View.VISIBLE
                for (f in supportFragmentManager.fragments) {
                    if (f is ComparePageFragment) f.updateUI()
                }
            }
        }
    }

    fun addCompareHeader(
        inflater: LayoutInflater,
        container: LinearLayout,
        title: String
    ) {
        val header = inflater.inflate(R.layout.item_compare_header, container, false)
        header.findViewById<TextView>(R.id.tvCompareHeaderTitle).text = title
        container.addView(header)
    }

    fun addCompareRow(
        inflater: LayoutInflater,
        container: LinearLayout,
        label: String,
        v1: Double,
        v2: Double,
        higherIsBetter: Boolean,
        enableHighlight: Boolean = true
    ) {
        val row = inflater.inflate(R.layout.item_compare_row, container, false)
        val tvV1 = row.findViewById<TextView>(R.id.tvCompareVal1)
        val tvV2 = row.findViewById<TextView>(R.id.tvCompareVal2)
        row.findViewById<TextView>(R.id.tvCompareLabel).text = label
        
        val sv1 = if (v1 == v1.toLong().toDouble()) String.format(Locale.US, "%d", v1.toLong()) else String.format(Locale.US, "%.1f", v1)
        val sv2 = if (v2 == v2.toLong().toDouble()) String.format(Locale.US, "%d", v2.toLong()) else String.format(Locale.US, "%.1f", v2)
        
        tvV1.text = sv1
        tvV2.text = sv2

        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val winnerTextColor = if (isDarkMode) Color.BLACK else Color.WHITE
        
        if (enableHighlight && abs(v1 - v2) > 0.001) {
            var skipComp = false
            if (label.contains("ECONOMY") || label.contains("AVG") || label.contains("SR") || label.contains("HAUL")) {
                if (v1 == 0.0 || v2 == 0.0) skipComp = true
            }

            if (!skipComp) {
                val v1Wins = if (higherIsBetter) (v1 > v2) else (v1 < v2)
                if (v1Wins) {
                    tvV1.setBackgroundResource(R.drawable.rounded_stat_bg_winner)
                    tvV1.setTextColor(winnerTextColor)
                    tvV1.animate().scaleX(1.1f).scaleY(1.1f).setDuration(300).start()
                } else {
                    tvV2.setBackgroundResource(R.drawable.rounded_stat_bg_winner)
                    tvV2.setTextColor(winnerTextColor)
                    tvV2.animate().scaleX(1.1f).scaleY(1.1f).setDuration(300).start()
                }
            }
        }
        container.addView(row)
    }

    class CareerStats internal constructor(stats: MutableList<PlayerMatchStatEntity>, matchMap: Map<String, MatchEntity>? = null) {
        var matches: Int
        var batInns: Int = 0
        var runs: Int = 0
        var balls: Int
        var highest: Int = 0
        var no: Int = 0
        var f100: Int = 0
        var f80: Int = 0
        var f50: Int = 0
        var f30: Int = 0
        var fours: Int = 0
        var sixes: Int = 0
        var bowlInns: Int = 0
        var wickets: Int = 0
        var rCon: Int
        var w2: Int = 0
        var w3: Int = 0
        var w5: Int = 0
        var catches: Int = 0
        var stumpings: Int = 0
        var ro: Int = 0
        var bbiW: Int = 0
        var bbiR: Int = 0
        var avg: Double
        var sr: Double
        var eco: Double
        var bAvg: Double
        var bSR: Double
        var boundaryPct: Double
        var settingAvg: Double = 0.0
        var chasingAvg: Double = 0.0

        init {
            val uniqueMatchIds = mutableSetOf<String?>()
            var bBalls = 0
            var bowlBalls = 0
            var rConceded = 0
            
            var sRuns = 0; var sOuts = 0
            var cRuns = 0; var cOuts = 0

            for (s in stats) {
                uniqueMatchIds.add(s.matchId)
                if (s.runsScored > 0 || s.ballsFaced > 0 || s.isOut) {
                    batInns++
                    runs += s.runsScored
                    bBalls += s.ballsFaced
                    fours += s.fours
                    sixes += s.sixes
                    if (!s.isOut) no++
                    if (s.runsScored > highest) highest = s.runsScored
                    if (s.runsScored >= 100) f100++ else if (s.runsScored >= 80) f80++ else if (s.runsScored >= 50) f50++ else if (s.runsScored >= 30) f30++
                
                    // "Clutch" calculation (Setting vs Chasing)
                    val m = matchMap?.get(s.matchId)
                    if (m != null) {
                        if (s.teamName == m.secondInningsTeam) {
                            cRuns += s.runsScored
                            if (s.isOut) cOuts++
                        } else {
                            sRuns += s.runsScored
                            if (s.isOut) sOuts++
                        }
                    }
                }
                if (s.ballsBowled > 0) {
                    bowlInns++
                    wickets += s.wicketsTaken
                    rConceded += s.runsConceded
                    bowlBalls += s.ballsBowled
                    if (s.wicketsTaken >= 5) w5++ else if (s.wicketsTaken >= 3) w3++ else if (s.wicketsTaken >= 2) w2++
                    if (s.wicketsTaken > bbiW || (s.wicketsTaken == bbiW && s.runsConceded < bbiR)) {
                        bbiW = s.wicketsTaken
                        bbiR = s.runsConceded
                    }
                }
                catches += s.catches
                stumpings += s.stumpings
                ro += s.runOuts
            }
            matches = uniqueMatchIds.size
            balls = bBalls
            rCon = rConceded
            avg = if (batInns - no > 0) runs.toDouble() / (batInns - no) else runs.toDouble()
            sr = if (balls > 0) (runs.toDouble() / balls) * 100 else 0.0
            eco = if (bowlBalls > 0) (rConceded.toDouble() / bowlBalls) * 6.0 else 0.0
            bAvg = if (wickets > 0) rConceded.toDouble() / wickets else 0.0
            bSR = if (wickets > 0) bowlBalls.toDouble() / wickets else 0.0
            boundaryPct = if (runs > 0) ((fours * 4 + sixes * 6).toDouble() / runs) * 100.0 else 0.0
            
            settingAvg = if (sOuts > 0) sRuns.toDouble() / sOuts else sRuns.toDouble()
            chasingAvg = if (cOuts > 0) cRuns.toDouble() / cOuts else cRuns.toDouble()
        }
    }

    class H2HStats internal constructor(
        oppositionMatches: List<Pair<PlayerMatchStatEntity, PlayerMatchStatEntity>>,
        matchEntities: List<MatchEntity>,
        name1: String,
        name2: String
    ) {
        var sharedMatches: Int = oppositionMatches.size
        var p1Wins: Int = 0
        var p2Wins: Int = 0
        
        // --- MATCHUP 1: P1 Batting vs P2 Bowling ---
        var r1v2 = 0      // P1 Runs off P2
        var b1v2 = 0      // P1 Balls faced from P2
        var out1by2 = 0   // P1 Dismissals by P2
        var sr1v2 = 0.0
        var avg1v2 = 0.0
        var eco2v1 = 0.0  // P2 Economy against P1
        var bsr2v1 = 0.0  // P2 Bowling SR against P1

        // --- MATCHUP 2: P2 Batting vs P1 Bowling ---
        var r2v1 = 0      // P2 Runs off P1
        var b2v1 = 0      // P2 Balls faced from P1
        var out2by1 = 0   // P2 Dismissals by P1
        var sr2v1 = 0.0
        var avg2v1 = 0.0
        var eco1v2 = 0.0  // P1 Economy against P2
        var bsr1v2 = 0.0  // P1 Bowling SR against P2
        
        var catches1of2 = 0; var ro1of2 = 0; var stumps1of2 = 0
        var catches2of1 = 0; var ro2of1 = 0; var stumps2of1 = 0

        init {
            val n1 = name1.trim().lowercase()
            val n2 = name2.trim().lowercase()

            for (pair in oppositionMatches) {
                val s1Obj = pair.first
                val s2Obj = pair.second
                val me = matchEntities.find { it.id == s1Obj.matchId } ?: continue

                // 1. Team Victory Count
                val resultText = me.result?.uppercase() ?: ""
                val t1Name = s1Obj.teamName?.uppercase() ?: ""
                val t2Name = s2Obj.teamName?.uppercase() ?: ""
                
                if (t1Name.isNotEmpty() && resultText.contains("$t1Name WON")) p1Wins++
                else if (t2Name.isNotEmpty() && resultText.contains("$t2Name WON")) p2Wins++

                // 2. Ball-by-Ball Interaction Analysis
                val allBalls = mutableListOf<Ball>()
                me.ballsJson1?.let { allBalls.addAll(it.filterNotNull()) }
                me.ballsJson2?.let { allBalls.addAll(it.filterNotNull()) }

                for (ball in allBalls) {
                    val bat = ball.batsmanName?.trim()?.lowercase() ?: ""
                    val bowl = ball.bowlerName?.trim()?.lowercase() ?: ""
                    val out = ball.outPlayerName?.trim()?.lowercase() ?: ""
                    val fielders = ball.fielderName?.trim()?.lowercase()?.split("/")?.map { it.trim() } ?: emptyList()
                    val info = ball.dismissalInfo?.lowercase() ?: ""

                    // DUEL 1: P1 Batting vs P2 Bowling
                    if (bat == n1 && bowl == n2) {
                        r1v2 += ball.batRuns
                        if (ball.type != BallType.WIDE) b1v2++
                        if (ball.isWicket && out == n1) {
                            if (!info.contains("run out") && !info.contains("obstructing")) out1by2++
                        }
                    }

                    // DUEL 2: P2 Batting vs P1 Bowling
                    if (bat == n2 && bowl == n1) {
                        r2v1 += ball.batRuns
                        if (ball.type != BallType.WIDE) b2v1++
                        if (ball.isWicket && out == n2) {
                            if (!info.contains("run out") && !info.contains("obstructing")) out2by1++
                        }
                    }

                    // Fielding Interactions
                    if (ball.isWicket) {
                        // Successes for P1 against P2
                        if (out == n2) {
                            if (fielders.contains(n1)) {
                                if (info.contains("catch") || info.startsWith("c ")) catches1of2++
                                if (info.contains("run out")) ro1of2++
                                if (info.contains("st ") || info.contains("stumped")) stumps1of2++
                            }
                            if (bowl == n1 && info.contains("c & b")) catches1of2++
                        }
                        
                        // Successes for P2 against P1
                        if (out == n1) {
                            if (fielders.contains(n2)) {
                                if (info.contains("catch") || info.startsWith("c ")) catches2of1++
                                if (info.contains("run out")) ro2of1++
                                if (info.contains("st ") || info.contains("stumped")) stumps2of1++
                            }
                            if (bowl == n2 && info.contains("c & b")) catches2of1++
                        }
                    }
                }
            }

            // Calculate Duels Metrics
            sr1v2 = if (b1v2 > 0) (r1v2.toDouble() / b1v2) * 100.0 else 0.0
            avg1v2 = if (out1by2 > 0) r1v2.toDouble() / out1by2 else r1v2.toDouble()
            eco2v1 = if (b1v2 > 0) (r1v2.toDouble() / b1v2) * 6.0 else 0.0
            bsr2v1 = if (out1by2 > 0) b1v2.toDouble() / out1by2 else 0.0

            sr2v1 = if (b2v1 > 0) (r2v1.toDouble() / b2v1) * 100.0 else 0.0
            avg2v1 = if (out2by1 > 0) r2v1.toDouble() / out2by1 else r2v1.toDouble()
            eco1v2 = if (b2v1 > 0) (r2v1.toDouble() / b2v1) * 6.0 else 0.0
            bsr1v2 = if (out2by1 > 0) b2v1.toDouble() / out2by1 else 0.0
        }
    }
}

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
        AppDatabase.ioExecutor.execute {
            allPlayers = db?.playerDao()?.getAllPlayers()?.toMutableList()
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
            val raw1 = db?.statsDao()?.getStatsByPlayer(player1.id)?.filterNotNull() ?: emptyList()
            val raw2 = db?.statsDao()?.getStatsByPlayer(player2.id)?.filterNotNull() ?: emptyList()
            
            s1_overall = CareerStats(raw1.toMutableList())
            s2_overall = CareerStats(raw2.toMutableList())
            
            val p1MatchIds = raw1.map { it.matchId }.toSet()
            val shared1 = raw1.filter { raw2.any { r2 -> r2.matchId == it.matchId } }
            val shared2 = raw2.filter { p1MatchIds.contains(it.matchId) }
            
            h2h_all = H2HStats(shared1.toMutableList(), shared2.toMutableList())
            
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

    class CareerStats internal constructor(stats: MutableList<PlayerMatchStatEntity>) {
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

        init {
            val uniqueMatchIds = mutableSetOf<String?>()
            var bBalls = 0
            var bowlBalls = 0
            var rConceded = 0
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
        }
    }

    class H2HStats internal constructor(
        p1shared: MutableList<PlayerMatchStatEntity>,
        p2shared: MutableList<PlayerMatchStatEntity>
    ) {
        var sharedInns: Int
        var r1: Int = 0
        var bb1: Int = 0
        var w1: Int = 0
        var rc1: Int = 0
        var c1: Int = 0
        var s1: Int = 0
        var ro1: Int = 0
        var r2: Int = 0
        var bb2: Int = 0
        var w2: Int = 0
        var rc2: Int = 0
        var c2: Int = 0
        var s2: Int = 0
        var ro2: Int = 0
        var bf1: Int = 0
        var bf2: Int = 0
        var sr1: Double
        var eco1: Double
        var avg1: Double
        var bsr1: Double
        var sr2: Double
        var eco2: Double
        var avg2: Double
        var bsr2: Double

        init {
            sharedInns = p1shared.size
            var i1_bi = 0
            var i1_no = 0
            var i2_bi = 0
            var i2_no = 0
            for (i in 0 until sharedInns) {
                val s1obj = p1shared[i]
                val s2obj = p2shared[i]
                r1 += s1obj.runsScored
                bf1 += s1obj.ballsFaced
                if (s1obj.runsScored > 0 || s1obj.ballsFaced > 0 || s1obj.isOut) {
                    i1_bi++
                    if (!s1obj.isOut) i1_no++
                }
                bb1 += s1obj.ballsBowled
                w1 += s1obj.wicketsTaken
                rc1 += s1obj.runsConceded
                c1 += s1obj.catches
                s1 += s1obj.stumpings
                ro1 += s1obj.runOuts

                r2 += s2obj.runsScored
                bf2 += s2obj.ballsFaced
                if (s2obj.runsScored > 0 || s2obj.ballsFaced > 0 || s2obj.isOut) {
                    i2_bi++
                    if (!s2obj.isOut) i2_no++
                }
                bb2 += s2obj.ballsBowled
                w2 += s2obj.wicketsTaken
                rc2 += s2obj.runsConceded
                c2 += s2obj.catches
                s2 += s2obj.stumpings
                ro2 += s2obj.runOuts
            }
            sr1 = if (bf1 > 0) (r1.toDouble() / bf1) * 100 else 0.0
            eco1 = if (bb1 > 0) rc1.toDouble() / bb1 * 6.0 else 0.0
            avg1 = if (i1_bi - i1_no > 0) r1.toDouble() / (i1_bi - i1_no) else r1.toDouble()
            bsr1 = if (w1 > 0) bb1.toDouble() / w1 else 0.0

            sr2 = if (bf2 > 0) (r2.toDouble() / bf2) * 100 else 0.0
            eco2 = if (bb2 > 0) rc2.toDouble() / bb2 * 6.0 else 0.0
            avg2 = if (i2_bi - i2_no > 0) r2.toDouble() / (i2_bi - i2_no) else r2.toDouble()
            bsr2 = if (w2 > 0) bb2.toDouble() / w2 else 0.0
        }
    }
}

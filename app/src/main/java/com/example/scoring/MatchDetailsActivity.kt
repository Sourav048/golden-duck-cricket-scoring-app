package com.example.scoring

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.scoring.AppDatabase.Companion.getInstance
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MatchDetailsActivity : BaseActivity(), ScoringProvider {
    private var viewPager: ViewPager2? = null
    private var tabLayout: TabLayout? = null
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = getInstance(this)
        viewPager = findViewById(R.id.viewPagerScoring)
        tabLayout = findViewById(R.id.tabLayoutScoring)

        // Hide scoring header parts for details view
        findViewById<View>(R.id.tvAnalysisLabel).visibility = View.GONE
        findViewById<View>(R.id.tvRateValue).visibility = View.GONE
        findViewById<View>(R.id.tvRateLabel).visibility = View.GONE

        val matchId = intent.getStringExtra("matchId")
        if (matchId != null) {
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
                teamANames = ArrayList(entity.teamANames ?: emptyList())
                teamBNames = ArrayList(entity.teamBNames ?: emptyList())
                photoMap.putAll(entity.photoMap?.filterKeys { it != null }?.mapKeys { it.key!! } ?: emptyMap())
                nameToIdMap.putAll(entity.nameToIdMap?.filterKeys { it != null }?.mapKeys { it.key!! } ?: emptyMap())

                for (s in stats) {
                    if (s == null) continue
                    val p = s.toPlayer()
                    // Historical view: No players are currently at the crease
                    p.entryTime = 0
                    playerStatCache[p.name] = p
                }

                // Update header to show result instead of live scoring UI
                findViewById<View>(R.id.layoutLiveHeader)?.visibility = View.GONE
                findViewById<TextView>(R.id.tvFinalResultBanner)?.apply {
                    visibility = View.VISIBLE
                    val baseResult = entity.result?.uppercase() ?: "MATCH COMPLETED"
                    val isPlaceholder = baseResult == "IN-PROGRESS" || baseResult == "MATCH COMPLETED" || baseResult == "MATCH FINISHED"
                    text = if (entity.isFinished && isPlaceholder) {
                        calculateMatchResult()?.uppercase() ?: baseResult
                    } else if (!entity.isFinished && !entity.isAbandoned) {
                        "IN-PROGRESS"
                    } else {
                        baseResult
                    }
                    gravity = android.view.Gravity.CENTER
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
                    val isPlaceholder = textUpper == "IN-PROGRESS" || textUpper == "MATCH COMPLETED" || textUpper == "MATCH FINISHED"
                    if (entity.isFinished && isPlaceholder) {
                        newText = matchResult ?: "MATCH COMPLETED"
                    }
                    
                    CommentaryEntry(entry.over, newText, entry.highlight, entry.type).apply {
                        this.baseText = newText
                    }
                } ?: emptyList()
            }

            if (!entity.commentaryJson.isNullOrEmpty()) {
                // Unified commentary is saved newest-at-top, so reverse for oldest-at-top display
                commentary.addAll(sanitize(entity.commentaryJson).asReversed())
            } else {
                // Fallback for older matches: Innings 1 then Innings 2 (both reversed to be chronological)
                commentary.addAll(sanitize(entity.commentaryJson1).asReversed())
                commentary.addAll(sanitize(entity.commentaryJson2).asReversed())
            }

            runOnUiThread {
                setupTabs()
                updateUI()
            }
        }
    }

    private fun setupTabs() {
        if (viewPager?.adapter != null) return

        val currentMatch = matchEntity
        val currentStats = statsEntities?.toMutableList()

        viewPager?.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> MatchInfoFragment()
                    1 -> MatchSummaryFragment().apply { setData(currentMatch, currentStats) }
                    2 -> ScorecardFragment()
                    3 -> CommentaryFragment()
                    4 -> OversFragment()
                    else -> SquadFragment()
                }
            }
            override fun getItemCount(): Int = 6
            override fun getItemId(position: Int): Long = position.toLong()
            override fun containsItem(itemId: Long): Boolean = itemId in 0..5
        }

        viewPager?.offscreenPageLimit = 5
        
        val tabs = tabLayout ?: return
        val pager = viewPager ?: return

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = when (position) {
                0 -> "Info"
                1 -> "Summary"
                2 -> "Scorecard"
                3 -> "Commentary"
                4 -> "Overs"
                else -> "Squad"
            }
        }.attach()
        
        // Make "Summary" (index 1) the default tab if starting fresh
        if (viewPager?.currentItem == 0) {
            viewPager?.setCurrentItem(1, false)
        }
    }

    override fun updateUI() {
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
    override fun getPlayerFromCache(name: String?): Player? {
        if (name == null) return null
        if (!playerStatCache.containsKey(name)) {
            val p = Player(name)
            p.id = nameToIdMap[name]
            playerStatCache[name] = p
        }
        return playerStatCache[name]
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
            if (!i1.isComplete) return "IN-PROGRESS"
            if (i2 == null) return "First Innings over. Target: ${m.target}"
            if (!i2.isComplete) return "IN-PROGRESS"
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
    override fun addPlayerToTeam(teamName: String?, playerName: String?, playerId: String?, photoUri: String?) {}
    override fun showEditCommentaryDialog(context: android.content.Context, entry: CommentaryEntry, pos: Int) {}
}

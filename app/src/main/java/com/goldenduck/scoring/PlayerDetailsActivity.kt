package com.goldenduck.scoring

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.signature.ObjectKey
import com.goldenduck.scoring.AppDatabase.Companion.getInstance
import com.goldenduck.scoring.RankingRegistry.applyPrestige
import com.goldenduck.scoring.SecurityUtils.AuthCallback
import com.goldenduck.scoring.SecurityUtils.authenticate
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerDetailsActivity : BaseActivity() {
    private var playerId: String? = null
    private var db: AppDatabase? = null
    private var currentPlayer: PlayerEntity? = null

    private var pendingPhotoUri: String? = null
    private var pendingName = ""
    private var pendingJersey = ""
    private var currentCameraUri: Uri? = null
    private var statsAdapter: StatsPagerAdapter? = null
    private var formDisciplineIsBatting = true
    private var recentMatchesStats: List<PlayerMatchStatEntity> = emptyList()
    private var matchesMap: Map<String, MatchEntity> = emptyMap()
    private var cachedPotmCount: Int = 0
    private var cachedWonMatchesCount: Int = 0
    private var cachedTotalMatchesCount: Int = 0
    private var cachedWinRatePct: Double = 0.0
    private var updateStatsHeightAction: (() -> Unit)? = null

    data class PlayerCardStats(
        val runs: Int = 0,
        val batAvg: Double = 0.0,
        val batSR: Double = 0.0,
        val highest: Int = 0,
        val isHighestNotOut: Boolean = false,
        val wickets: Int = 0,
        val bowlEco: Double = 0.0,
        val bowlAvg: Double = 0.0,
        val bbi: String = "-/-",
        val matches: Int = 0,
        val winPct: Double = 0.0,
        val potmCount: Int = 0,
        val totalDismissals: Int = 0
    )

    data class FormPillStyle(
        val strokeColor: Int,
        val scoreColor: Int,
        val cardBg: Int,
        val strokeDp: Float = 1.5f,
        val subColor: Int? = null
    )

    private var cachedCardStats = PlayerCardStats()
    private var cachedRankOverall: String = "Rank #-"
    private var recentFormAdapter: RecentFormPagerAdapter? = null

    private val pickMedia: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                pendingPhotoUri = uri.toString()
                showEditDialog()
            }
        }

    private val takePicture: ActivityResultLauncher<Uri> =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                pendingPhotoUri = currentCameraUri?.toString()
                showEditDialog()
            } else {
                currentCameraUri?.let { uri ->
                    try {
                        val f = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), uri.lastPathSegment ?: "")
                        if (f.exists() && f.length() == 0L) f.delete()
                    } catch (e: Exception) {
                        Log.e("PlayerDetails", "Failed to cleanup camera file: ${e.message}")
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_details)

        db = getInstance(this)
        playerId = intent.getStringExtra("playerId")

        findViewById<View>(R.id.btnPlayerDetailsMenu).setOnClickListener { v ->
            StatTooltipHelper.dismissCurrent()
            showPopupMenu(v)
        }
        val isShareCardEnabled = RemoteConfigManager.isFeatureEnabled(RemoteConfigManager.KEY_ENABLE_PLAYER_CARD_SHARE)
        val btnShare = findViewById<View>(R.id.btnSharePlayerCard)
        btnShare?.visibility = if (isShareCardEnabled) View.VISIBLE else View.GONE
        btnShare?.setOnClickListener {
            StatTooltipHelper.dismissCurrent()
            generateAndSharePlayerCard()
        }
        findViewById<View>(R.id.btnBackProfile).setOnClickListener {
            StatTooltipHelper.dismissCurrent()
            finish()
        }

        findViewById<View>(R.id.scrollPlayerDetails)?.setOnScrollChangeListener { _, _, _, _, _ ->
            StatTooltipHelper.dismissCurrent(animate = true)
        }

        setupRecentFormViewPager()
        setupStatsViewPager()

        RankingRegistry.refresh(this, object : RankingRegistry.OnRankingsLoaded {
            override fun onLoaded() {
                loadPlayerData()
            }
        })
    }

    override fun onDestroy() {
        StatTooltipHelper.dismissCurrent()
        super.onDestroy()
    }

    private fun setupStatsViewPager() {
        val viewPagerStats = findViewById<ViewPager2>(R.id.viewPagerStats) ?: return
        val tabLayoutStats = findViewById<TabLayout>(R.id.tabLayoutStats) ?: return

        val updateHeight: () -> Unit = {
            viewPagerStats.post {
                val pageView = statsAdapter?.getPageView(viewPagerStats.currentItem)
                if (pageView != null && viewPagerStats.width > 0) {
                    pageView.measure(
                        View.MeasureSpec.makeMeasureSpec(viewPagerStats.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    val h = pageView.measuredHeight
                    if (h > 0 && viewPagerStats.layoutParams.height != h) {
                        viewPagerStats.layoutParams.height = h
                        viewPagerStats.requestLayout()
                    }
                }
            }
        }
        updateStatsHeightAction = updateHeight

        val adapter = StatsPagerAdapter(
            onContentUpdated = { updateHeight() },
            onBindMatches = { pageView -> bindMatchesPage(pageView) }
        )
        statsAdapter = adapter
        viewPagerStats.adapter = adapter
        viewPagerStats.offscreenPageLimit = 3

        viewPagerStats.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                StatTooltipHelper.dismissCurrent()
                updateHeight()
                if (position == 0 && !formDisciplineIsBatting) {
                    formDisciplineIsBatting = true
                    updateRecentFormGuide()
                } else if (position == 1 && formDisciplineIsBatting) {
                    formDisciplineIsBatting = false
                    updateRecentFormGuide()
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state != ViewPager2.SCROLL_STATE_IDLE) {
                    StatTooltipHelper.dismissCurrent(animate = true)
                }
            }
        })

        TabLayoutMediator(tabLayoutStats, viewPagerStats) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "Batting"
                    tab.setIcon(R.drawable.ic_stat_bat)
                }
                1 -> {
                    tab.text = "Bowling"
                    tab.setIcon(R.drawable.ic_stat_ball)
                }
                2 -> {
                    tab.text = "Fielding"
                    tab.setIcon(R.drawable.ic_stat_fielding)
                }
                3 -> {
                    tab.text = "Matches"
                    tab.setIcon(R.drawable.ic_card_history)
                }
            }
        }.attach()
    }

    private fun loadPlayerData() {
        StatsRecalculator.cleanDuplicateMatchStats(this) {
            AppDatabase.ioExecutor.execute {
                val player = db?.playerDao()?.getPlayerById(playerId)
                currentPlayer = player
            
            val statsByPid = if (playerId != null) db?.statsDao()?.getStatsByPlayer(playerId)?.filterNotNull() else null
            val statsByGlobal = if (player?.globalId != null) db?.statsDao()?.getStatsByGlobalId(player.globalId!!)?.filterNotNull() else null
            val statsByName = if (player != null) db?.statsDao()?.getAllStats()?.filterNotNull()?.filter {
                it.playerName?.trim()?.equals(player.name.trim(), ignoreCase = true) == true
            } else null

            val combinedStatsMap = mutableMapOf<String, PlayerMatchStatEntity>()
            fun mergeStat(stat: PlayerMatchStatEntity) {
                val mId = stat.matchId ?: return
                val existing = combinedStatsMap[mId]
                if (existing == null) {
                    combinedStatsMap[mId] = stat
                } else {
                    val existingHasPid = !existing.playerId.isNullOrBlank()
                    val newHasPid = !stat.playerId.isNullOrBlank()
                    if (newHasPid && !existingHasPid) {
                        combinedStatsMap[mId] = stat
                    } else if (newHasPid == existingHasPid) {
                        val existingScore = existing.runsScored + existing.wicketsTaken * 20 + existing.catches * 10
                        val newScore = stat.runsScored + stat.wicketsTaken * 20 + stat.catches * 10
                        if (newScore > existingScore) {
                            combinedStatsMap[mId] = stat
                        }
                    }
                }
            }

            statsByPid?.forEach { mergeStat(it) }
            statsByGlobal?.forEach { mergeStat(it) }
            statsByName?.forEach { mergeStat(it) }

            val stats = combinedStatsMap.values.toList()

            val allMatches = db?.matchDao()?.getAllMatches()?.filterNotNull() ?: emptyList()
            val matchMap = allMatches.associateBy { it.id }
            val sortedStats = stats.sortedByDescending { matchMap[it.matchId]?.playedAt ?: it.entryTime }
            recentMatchesStats = sortedStats
            matchesMap = matchMap
            
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (player == null) {
                    finish()
                    return@runOnUiThread
                }

                // Show "GLOBAL PROFILE" badge if linked
                findViewById<TextView>(R.id.tvDetailSubTitle)?.apply {
                    if (player.globalId != null) {
                        text = "GLOBAL CAREER PROFILE"
                        visibility = View.VISIBLE
                    } else {
                        visibility = View.GONE
                    }
                }

                val iv = findViewById<ShapeableImageView>(R.id.ivDetailPhoto)
                val tvName = findViewById<TextView>(R.id.tvDetailNameDisplay)
                val tvJersey = findViewById<TextView>(R.id.tvDetailJerseyDisplay)

                // The header has a rich dark gradient in dark mode and deep teal in light mode,
                // so the text contrast on this card is always crisp white.
                val headerTextColor = Color.WHITE
                val chipBgColor = Color.argb(32, 255, 255, 255)
                val chipStrokeColor = Color.argb(45, 255, 255, 255)

                // Set Name and Jersey
                tvName?.text = player.name
                tvJersey?.text = "#" + player.jerseyNumber

                val photoTarget: Any = when {
                    !player.photoUrl.isNullOrEmpty() -> player.photoUrl
                    !player.photoUri.isNullOrEmpty() -> player.photoUri
                    else -> ""
                }

                if (iv != null) {
                    if (photoTarget != "" && !isFinishing && !isDestroyed) {
                        Glide.with(this)
                            .load(photoTarget)
                            .signature(ObjectKey("${player.id}_${player.lastSyncedAt}"))
                            .dontAnimate()
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery)
                            .into(iv)
                    } else {
                        iv.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }

                // Apply prestige with custom contrast fallback
                applyPrestige(player.id, tvName, iv, headerTextColor)

                // Handle ranking chip styling: frosted glass look with crisp white text
                val chips = listOf(R.id.tvRankOverall, R.id.tvRankBatting, R.id.tvRankBowling, R.id.tvPotmTotal)
                chips.forEach { id ->
                    val tv = findViewById<TextView>(id)
                    tv?.setTextColor(headerTextColor)
                    var parent = tv?.parent as? View
                    while (parent != null && parent !is MaterialCardView) {
                        parent = parent.parent as? View
                    }
                    (parent as MaterialCardView?)?.apply {
                        setCardBackgroundColor(chipBgColor)
                        setStrokeColor(chipStrokeColor)
                        strokeWidth = (1f * resources.displayMetrics.density).toInt()
                    }
                }

                val card = findViewById<MaterialCardView>(R.id.cardPhotoFrame)
                val cardBaseColor = if (ThemeManager.isDarkMode(this)) Color.parseColor("#141A26") else Color.parseColor("#004D40")
                card?.setCardBackgroundColor(cardBaseColor)

                // Apply prestige stroke to frame if the player has one, else a clean subtle border
                if (iv != null && card != null) {
                    if (iv.strokeWidth > 0) {
                        card.setStrokeColor(iv.strokeColor)
                        card.strokeWidth = (3.5f * resources.displayMetrics.density).toInt()
                        iv.strokeWidth = 0f
                    } else {
                        card.setStrokeColor(ColorStateList.valueOf(Color.argb(55, 255, 255, 255)))
                        card.strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
                    }
                }

                iv?.setOnClickListener { showFullScreenPhoto(player) }

                calculateAndDisplayStats(stats.toMutableList())
                calculateAndDisplayRankings()
                updateRecentFormGuide()
            }
        }
    }
}

    private fun setupRecentFormViewPager() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPagerRecentForm) ?: return

        val updateFormHeight: () -> Unit = {
            viewPager.post {
                val rv = viewPager.getChildAt(0) as? RecyclerView
                val pageView = rv?.layoutManager?.findViewByPosition(viewPager.currentItem)
                val row = pageView?.findViewById<View>(R.id.layoutFormPillsRow) ?: pageView
                if (row != null && viewPager.width > 0) {
                    row.measure(
                        View.MeasureSpec.makeMeasureSpec(viewPager.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    val h = Math.max(row.measuredHeight, (84 * resources.displayMetrics.density).toInt())
                    if (h > 0 && viewPager.layoutParams.height != h) {
                        viewPager.layoutParams.height = h
                        viewPager.requestLayout()
                    }
                }
            }
        }

        val adapter = RecentFormPagerAdapter { position, pageView ->
            bindFormPills(pageView, isBatting = (position == 0))
            updateFormHeight()
        }
        recentFormAdapter = adapter
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 1

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val isBatting = (position == 0)
                if (formDisciplineIsBatting != isBatting) {
                    formDisciplineIsBatting = isBatting
                    updateFormToggleHeader(isBatting)
                }
                updateFormHeight()
            }
        })

        findViewById<View>(R.id.btnFormToggleBatting)?.setOnClickListener {
            if (viewPager.currentItem != 0) {
                viewPager.setCurrentItem(0, true)
            }
        }
        findViewById<View>(R.id.btnFormToggleBowling)?.setOnClickListener {
            if (viewPager.currentItem != 1) {
                viewPager.setCurrentItem(1, true)
            }
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 80 && Math.abs(velocityX) > 100) {
                    if (diffX < 0 && viewPager.currentItem == 0) {
                        viewPager.setCurrentItem(1, true)
                        return true
                    } else if (diffX > 0 && viewPager.currentItem == 1) {
                        viewPager.setCurrentItem(0, true)
                        return true
                    }
                }
                return false
            }
        })
        findViewById<View>(R.id.layoutRecentFormHeaderRow)?.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            v.performClick()
            false
        }
    }

    private fun updateFormToggleHeader(isBatting: Boolean) {
        val btnBat = findViewById<TextView>(R.id.btnFormToggleBatting)
        val btnBowl = findViewById<TextView>(R.id.btnFormToggleBowling)
        val ivIcon = findViewById<ImageView>(R.id.ivFormDisciplineIcon)

        val isDark = ThemeManager.isDarkMode(this)
        val activeTextColor = if (isDark) Color.parseColor("#0F172A") else Color.WHITE
        val inactiveTextColor = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")

        if (isBatting) {
            btnBat?.setTextColor(activeTextColor)
            btnBat?.setBackgroundResource(R.drawable.bg_form_toggle_active)
            btnBowl?.setTextColor(inactiveTextColor)
            btnBowl?.setBackgroundColor(Color.TRANSPARENT)
            ivIcon?.setImageResource(R.drawable.ic_stat_bat)
            ivIcon?.imageTintList = ColorStateList.valueOf(if (isDark) Color.parseColor("#80DEEA") else Color.parseColor("#00695C"))
        } else {
            btnBowl?.setTextColor(activeTextColor)
            btnBowl?.setBackgroundResource(R.drawable.bg_form_toggle_active)
            btnBat?.setTextColor(inactiveTextColor)
            btnBat?.setBackgroundColor(Color.TRANSPARENT)
            ivIcon?.setImageResource(R.drawable.ic_stat_ball)
            ivIcon?.imageTintList = ColorStateList.valueOf(if (isDark) Color.parseColor("#CE93D8") else Color.parseColor("#7B1FA2"))
        }
    }

    private fun updateRecentFormGuide() {
        val formSection = findViewById<View>(R.id.layoutRecentFormSection) ?: return
        if (recentMatchesStats.isEmpty()) {
            formSection.visibility = View.GONE
            return
        }

        formSection.visibility = View.VISIBLE
        updateFormToggleHeader(formDisciplineIsBatting)

        val viewPager = findViewById<ViewPager2>(R.id.viewPagerRecentForm)
        val targetPage = if (formDisciplineIsBatting) 0 else 1
        if (viewPager != null && viewPager.currentItem != targetPage) {
            viewPager.setCurrentItem(targetPage, true)
        }

        recentFormAdapter?.notifyDataSetChanged()
    }

    private fun bindFormPills(containerView: View, isBatting: Boolean) {
        val pillIds = listOf(
            R.id.pillForm1,
            R.id.pillForm2,
            R.id.pillForm3,
            R.id.pillForm4,
            R.id.pillForm5
        )

        val last5 = recentMatchesStats.take(5)
        val isDark = ThemeManager.isDarkMode(this)

        for (i in 0 until 5) {
            val pillView = containerView.findViewById<View>(pillIds[i]) ?: continue
            val cardView = pillView as? MaterialCardView ?: pillView.findViewById(R.id.cardFormPillRoot)
            val tvMatch = pillView.findViewById<TextView>(R.id.tvFormMatch)
            val tvScore = pillView.findViewById<TextView>(R.id.tvFormScore)
            val tvSub = pillView.findViewById<TextView>(R.id.tvFormSub)

            tvMatch?.text = if (i == 0) "LATEST" else "M${i + 1}"
            tvMatch?.setTextColor(
                if (i == 0) {
                    if (isDark) Color.parseColor("#80DEEA") else Color.parseColor("#00695C")
                } else {
                    if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
                }
            )

            if (i < last5.size) {
                val stat = last5[i]
                val match = matchesMap[stat.matchId]

                val opponent = if (match != null) {
                    val pTeam = stat.teamName?.trim()
                    if (pTeam != null && pTeam.equals(match.teamAName?.trim(), ignoreCase = true)) {
                        match.teamBName ?: "Opponent"
                    } else {
                        match.teamAName ?: "Opponent"
                    }
                } else "Opponent"

                if (isBatting) {
                    if (stat.ballsFaced > 0 || stat.runsScored > 0 || stat.isOut) {
                        val scoreStr = if (!stat.isOut) "${stat.runsScored}*" else "${stat.runsScored}"
                        tvScore?.text = scoreStr
                        tvSub?.text = "${stat.ballsFaced}b"

                        val isExplosiveCameo = stat.ballsFaced >= 10 && (stat.runsScored.toDouble() / stat.ballsFaced.toDouble()) >= 2.0

                        val style = when {
                            stat.runsScored >= 100 -> if (isDark) {
                                FormPillStyle(Color.parseColor("#80FFD700"), Color.parseColor("#FFD700"), Color.parseColor("#26FFD700"), 1.5f, Color.parseColor("#FFE082"))
                            } else {
                                FormPillStyle(Color.parseColor("#FFA000"), Color.parseColor("#B78103"), Color.parseColor("#FFF8E1"), 1.5f, Color.parseColor("#F57F17"))
                            }
                            stat.runsScored >= 50 -> if (isDark) {
                                FormPillStyle(Color.parseColor("#8080DEEA"), Color.parseColor("#80DEEA"), Color.parseColor("#2280DEEA"), 1.5f, Color.parseColor("#B2EBF2"))
                            } else {
                                FormPillStyle(Color.parseColor("#00ACC1"), Color.parseColor("#006064"), Color.parseColor("#E0F7FA"), 1.5f, Color.parseColor("#00838F"))
                            }
                            stat.runsScored >= 30 || isExplosiveCameo -> if (isDark) {
                                FormPillStyle(Color.parseColor("#7081C784"), Color.parseColor("#81C784"), Color.parseColor("#1E81C784"), 1.5f, Color.parseColor("#A5D6A7"))
                            } else {
                                FormPillStyle(Color.parseColor("#4CAF50"), Color.parseColor("#1B5E20"), Color.parseColor("#E8F5E9"), 1.5f, Color.parseColor("#2E7D32"))
                            }
                            stat.isOut && stat.runsScored == 0 -> if (isDark) {
                                FormPillStyle(Color.parseColor("#80EF5350"), Color.parseColor("#EF5350"), Color.parseColor("#22EF5350"), 1.5f, Color.parseColor("#EF9A9A"))
                            } else {
                                FormPillStyle(Color.parseColor("#EF5350"), Color.parseColor("#C62828"), Color.parseColor("#FFEBEE"), 1.5f, Color.parseColor("#D32F2F"))
                            }
                            else -> if (isDark) {
                                FormPillStyle(Color.parseColor("#2E3A4E"), Color.parseColor("#F1F5F9"), Color.parseColor("#1B212B"), 1.2f, Color.parseColor("#94A3B8"))
                            } else {
                                FormPillStyle(Color.parseColor("#CBD5E1"), Color.parseColor("#0F172A"), Color.parseColor("#F8FAFC"), 1.2f, Color.parseColor("#64748B"))
                            }
                        }

                        cardView?.strokeWidth = (style.strokeDp * resources.displayMetrics.density).toInt()
                        cardView?.setStrokeColor(ColorStateList.valueOf(style.strokeColor))
                        cardView?.setCardBackgroundColor(style.cardBg)
                        tvScore?.setTextColor(style.scoreColor)
                        tvSub?.setTextColor(style.subColor ?: (if (isDark) Color.parseColor("#CBD5E1") else Color.parseColor("#64748B")))

                        pillView.setOnClickListener {
                            Toast.makeText(
                                this,
                                "${if (!stat.isOut) "${stat.runsScored}* not out" else "${stat.runsScored} runs"} (${stat.ballsFaced}b, ${stat.fours}x4, ${stat.sixes}x6) vs $opponent",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        tvScore?.text = "DNB"
                        tvSub?.text = "-"
                        tvScore?.setTextColor(if (isDark) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))
                        tvSub?.setTextColor(if (isDark) Color.parseColor("#475569") else Color.parseColor("#94A3B8"))
                        cardView?.strokeWidth = (1f * resources.displayMetrics.density).toInt()
                        cardView?.setStrokeColor(ColorStateList.valueOf(if (isDark) Color.parseColor("#253347") else Color.parseColor("#E2E8F0")))
                        cardView?.setCardBackgroundColor(if (isDark) Color.parseColor("#161D28") else Color.parseColor("#F1F5F9"))
                        pillView.setOnClickListener {
                            Toast.makeText(this, "Did not bat vs $opponent", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val didBowl = stat.ballsBowled > 0 || stat.wicketsTaken > 0 || stat.runsConceded > 0 || stat.widesConceded > 0 || stat.noBallsConceded > 0
                    if (didBowl) {
                        tvScore?.text = "${stat.wicketsTaken}/${stat.runsConceded}"
                        val ov = stat.ballsBowled / 6
                        val rem = stat.ballsBowled % 6
                        val ovStr = if (rem == 0) "${ov} ov" else "${ov}.${rem} ov"
                        tvSub?.text = ovStr

                        val eco = if (stat.ballsBowled > 0) stat.runsConceded.toDouble() / (stat.ballsBowled.toDouble() / 6.0) else 0.0
                        val isEconomicSpell = stat.ballsBowled >= 12 && eco <= 5.0

                        val style = when {
                            stat.wicketsTaken >= 5 -> if (isDark) {
                                FormPillStyle(Color.parseColor("#80FFD700"), Color.parseColor("#FFD700"), Color.parseColor("#26FFD700"), 1.5f, Color.parseColor("#FFE082"))
                            } else {
                                FormPillStyle(Color.parseColor("#FFA000"), Color.parseColor("#B78103"), Color.parseColor("#FFF8E1"), 1.5f, Color.parseColor("#F57F17"))
                            }
                            stat.wicketsTaken >= 3 -> if (isDark) {
                                FormPillStyle(Color.parseColor("#80BA68C8"), Color.parseColor("#CE93D8"), Color.parseColor("#22BA68C8"), 1.5f, Color.parseColor("#E1BEE7"))
                            } else {
                                FormPillStyle(Color.parseColor("#AB47BC"), Color.parseColor("#6A1B9A"), Color.parseColor("#F3E5F5"), 1.5f, Color.parseColor("#7B1FA2"))
                            }
                            stat.wicketsTaken >= 2 || isEconomicSpell -> if (isDark) {
                                FormPillStyle(Color.parseColor("#7081C784"), Color.parseColor("#81C784"), Color.parseColor("#1E81C784"), 1.5f, Color.parseColor("#A5D6A7"))
                            } else {
                                FormPillStyle(Color.parseColor("#4CAF50"), Color.parseColor("#1B5E20"), Color.parseColor("#E8F5E9"), 1.5f, Color.parseColor("#2E7D32"))
                            }
                            stat.runsConceded > 0 && eco > 11.0 -> if (isDark) {
                                FormPillStyle(Color.parseColor("#80EF5350"), Color.parseColor("#EF5350"), Color.parseColor("#22EF5350"), 1.5f, Color.parseColor("#EF9A9A"))
                            } else {
                                FormPillStyle(Color.parseColor("#EF5350"), Color.parseColor("#C62828"), Color.parseColor("#FFEBEE"), 1.5f, Color.parseColor("#D32F2F"))
                            }
                            else -> if (isDark) {
                                FormPillStyle(Color.parseColor("#2E3A4E"), Color.parseColor("#F1F5F9"), Color.parseColor("#1B212B"), 1.2f, Color.parseColor("#94A3B8"))
                            } else {
                                FormPillStyle(Color.parseColor("#CBD5E1"), Color.parseColor("#0F172A"), Color.parseColor("#F8FAFC"), 1.2f, Color.parseColor("#64748B"))
                            }
                        }

                        cardView?.strokeWidth = (style.strokeDp * resources.displayMetrics.density).toInt()
                        cardView?.setStrokeColor(ColorStateList.valueOf(style.strokeColor))
                        cardView?.setCardBackgroundColor(style.cardBg)
                        tvScore?.setTextColor(style.scoreColor)
                        tvSub?.setTextColor(style.subColor ?: (if (isDark) Color.parseColor("#CBD5E1") else Color.parseColor("#64748B")))

                        pillView.setOnClickListener {
                            Toast.makeText(
                                this,
                                "${stat.wicketsTaken} wkts for ${stat.runsConceded} runs ($ovStr, ${stat.dotBalls} dots) vs $opponent",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        tvScore?.text = "DNB"
                        tvSub?.text = "-"
                        tvScore?.setTextColor(if (isDark) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))
                        tvSub?.setTextColor(if (isDark) Color.parseColor("#475569") else Color.parseColor("#94A3B8"))
                        cardView?.strokeWidth = (1f * resources.displayMetrics.density).toInt()
                        cardView?.setStrokeColor(ColorStateList.valueOf(if (isDark) Color.parseColor("#253347") else Color.parseColor("#E2E8F0")))
                        cardView?.setCardBackgroundColor(if (isDark) Color.parseColor("#161D28") else Color.parseColor("#F1F5F9"))
                        pillView.setOnClickListener {
                            Toast.makeText(this, "Did not bowl vs $opponent", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                tvScore?.text = "-"
                tvSub?.text = "-"
                tvMatch?.setTextColor(if (isDark) Color.parseColor("#334155") else Color.parseColor("#CBD5E1"))
                tvScore?.setTextColor(if (isDark) Color.parseColor("#334155") else Color.parseColor("#CBD5E1"))
                tvSub?.setTextColor(if (isDark) Color.parseColor("#334155") else Color.parseColor("#CBD5E1"))
                cardView?.strokeWidth = (1f * resources.displayMetrics.density).toInt()
                cardView?.setStrokeColor(ColorStateList.valueOf(if (isDark) Color.parseColor("#1E2634") else Color.parseColor("#E2E8F0")))
                cardView?.setCardBackgroundColor(if (isDark) Color.parseColor("#141821") else Color.parseColor("#F8FAFC"))
                pillView.setOnClickListener(null)
            }
        }
    }

    inner class RecentFormPagerAdapter(
        private val onBindPage: (position: Int, pageView: View) -> Unit
    ) : RecyclerView.Adapter<RecentFormPagerAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

        override fun getItemCount(): Int = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_form_pills_row, parent, false)
            view.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            onBindPage(position, holder.view)
        }
    }

    private fun generateAndSharePlayerCard() {
        if (!RemoteConfigManager.isFeatureEnabled(RemoteConfigManager.KEY_ENABLE_PLAYER_CARD_SHARE)) {
            Toast.makeText(this, "Player card sharing is temporarily disabled", Toast.LENGTH_SHORT).show()
            return
        }
        val player = currentPlayer ?: run {
            Toast.makeText(this, "Player data not ready", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Generating cricket trading card...", Toast.LENGTH_SHORT).show()

        AppDatabase.ioExecutor.execute {
            try {
                val photoTarget: Any = when {
                    !player.photoUrl.isNullOrEmpty() -> player.photoUrl
                    !player.photoUri.isNullOrEmpty() -> player.photoUri
                    !player.photoBase64.isNullOrEmpty() -> PhotoUtils.base64ToPath(this, player.photoBase64, player.id) ?: ""
                    else -> ""
                }

                val photoBitmap: Bitmap? = if (photoTarget != "") {
                    try {
                        Glide.with(applicationContext)
                            .asBitmap()
                            .load(photoTarget)
                            .circleCrop()
                            .submit(320, 320)
                            .get()
                    } catch (e: Exception) {
                        Log.w("PlayerDetailsActivity", "Could not load photo bitmap for card: ${e.message}")
                        null
                    }
                } else null

                val gId = GullySyncManager.getCurrentGullyId(this) ?: "local"
                val leagueName = if (gId.isBlank() || gId.equals("local", ignoreCase = true)) {
                    "GULLY CRICKET"
                } else {
                    gId.uppercase(Locale.US)
                }

                var teamName = ""
                if (recentMatchesStats.isNotEmpty()) {
                    teamName = recentMatchesStats.firstOrNull { !it.teamName.isNullOrBlank() }?.teamName.orEmpty()
                }
                if (teamName.isBlank()) teamName = "Cricket Profile"

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    try {
                        val cardView = layoutInflater.inflate(R.layout.layout_shareable_player_card, null)

                        cardView.findViewById<TextView>(R.id.tvCardLeagueName)?.text = leagueName
                        cardView.findViewById<TextView>(R.id.tvCardPlayerName)?.text = player.name
                        cardView.findViewById<TextView>(R.id.tvCardJersey)?.text = "#" + player.jerseyNumber.ifBlank { "00" }
                        cardView.findViewById<TextView>(R.id.tvCardTeamName)?.text = "$teamName • Career Profile"
                        cardView.findViewById<TextView>(R.id.tvCardRankOverall)?.text = "⭐ $cachedRankOverall Overall"
                        cardView.findViewById<TextView>(R.id.tvCardPotmAwards)?.text = "🏆 $cachedPotmCount Awards"

                        val ivPhoto = cardView.findViewById<ImageView>(R.id.ivCardPhoto)
                        if (photoBitmap != null) {
                            ivPhoto?.setImageBitmap(photoBitmap)
                        } else {
                            ivPhoto?.setImageResource(R.drawable.ic_person_placeholder)
                        }

                        // Batting Signature
                        cardView.findViewById<TextView>(R.id.tvCardBatRuns)?.text = cachedCardStats.runs.toString()
                        cardView.findViewById<TextView>(R.id.tvCardBatAvg)?.text = String.format(Locale.US, "%.1f", cachedCardStats.batAvg)
                        cardView.findViewById<TextView>(R.id.tvCardBatSR)?.text = String.format(Locale.US, "%.1f", cachedCardStats.batSR)
                        cardView.findViewById<TextView>(R.id.tvCardBatHighest)?.text = if (cachedCardStats.isHighestNotOut) "${cachedCardStats.highest}*" else "${cachedCardStats.highest}"

                        // Bowling Weapon
                        cardView.findViewById<TextView>(R.id.tvCardBowlWkts)?.text = cachedCardStats.wickets.toString()
                        cardView.findViewById<TextView>(R.id.tvCardBowlEco)?.text = String.format(Locale.US, "%.1f", cachedCardStats.bowlEco)
                        cardView.findViewById<TextView>(R.id.tvCardBowlAvg)?.text = if (cachedCardStats.wickets > 0) String.format(Locale.US, "%.1f", cachedCardStats.bowlAvg) else "-"
                        cardView.findViewById<TextView>(R.id.tvCardBowlBBI)?.text = cachedCardStats.bbi

                        // Impact & Talisman
                        cardView.findViewById<TextView>(R.id.tvCardTotalMatches)?.text = cachedCardStats.matches.toString()
                        cardView.findViewById<TextView>(R.id.tvCardTeamWinPct)?.text = String.format(Locale.US, "%.1f%%", cachedCardStats.winPct)
                        cardView.findViewById<TextView>(R.id.tvCardPotmCount)?.text = cachedCardStats.potmCount.toString()
                        cardView.findViewById<TextView>(R.id.tvCardFielding)?.text = cachedCardStats.totalDismissals.toString()

                        // Measure exactly 1080 x 1920 (9:16 aspect ratio)
                        val cardWidth = 1080
                        val cardHeight = 1920
                        val widthSpec = View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY)
                        val heightSpec = View.MeasureSpec.makeMeasureSpec(cardHeight, View.MeasureSpec.EXACTLY)
                        cardView.measure(widthSpec, heightSpec)
                        cardView.layout(0, 0, cardWidth, cardHeight)

                        val outputBitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(outputBitmap)
                        canvas.drawColor(Color.parseColor("#080C14"))
                        cardView.draw(canvas)

                        AppDatabase.ioExecutor.execute {
                            try {
                                val cardsDir = File(cacheDir, "player_cards")
                                if (!cardsDir.exists()) cardsDir.mkdirs()
                                val cardFile = File(cardsDir, "player_${player.id}_card.png")
                                FileOutputStream(cardFile).use { fos ->
                                    outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                }

                                val uri = FileProvider.getUriForFile(this@PlayerDetailsActivity, "$packageName.fileprovider", cardFile)
                                runOnUiThread {
                                    if (isFinishing || isDestroyed) return@runOnUiThread
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_TEXT, "Check out ${player.name}'s official Cricket Player Card! 🏏⚡\nCreated with Scoring App")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(Intent.createChooser(shareIntent, "Share Player Card"))
                                }
                            } catch (e: Exception) {
                                Log.e("PlayerDetailsActivity", "Failed to save card bitmap: ${e.message}", e)
                                runOnUiThread {
                                    Toast.makeText(this@PlayerDetailsActivity, "Failed to export card: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerDetailsActivity", "Failed to render card: ${e.message}", e)
                        Toast.makeText(this@PlayerDetailsActivity, "Failed to render card: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerDetailsActivity", "Error generating card: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@PlayerDetailsActivity, "Error generating card: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFullScreenPhoto(player: PlayerEntity?) {
        if (player == null) return
        val photoTarget: Any = when {
            !player.photoUrl.isNullOrEmpty() -> player.photoUrl
            !player.photoUri.isNullOrEmpty() -> player.photoUri
            !player.photoBase64.isNullOrEmpty() -> player.photoBase64!!
            else -> ""
        }
        if (photoTarget == "") return

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_full_screen_photo)

        val ivFullScreen = dialog.findViewById<ImageView>(R.id.ivFullScreen)
        val btnRotate = dialog.findViewById<View>(R.id.btnRotatePhoto)
        val btnSave = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSavePhoto)
        btnSave?.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00695C")))
        btnSave?.setTextColor(android.graphics.Color.WHITE)
        val btnClose = dialog.findViewById<View>(R.id.btnCloseFull)

        if (!RemoteConfigManager.isFeatureEnabled(RemoteConfigManager.KEY_ENABLE_EDIT_OPTION)) {
            btnRotate?.visibility = View.GONE
            btnSave?.visibility = View.GONE
        }

        var currentRotationDegrees = 0f
        var loadedBitmap: android.graphics.Bitmap? = null

        Glide.with(this@PlayerDetailsActivity)
            .asBitmap()
            .load(photoTarget)
            .signature(ObjectKey("${player.id}_${player.lastSyncedAt}"))
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    loadedBitmap = resource
                    ivFullScreen.setImageBitmap(resource)
                }
                override fun onLoadCleared(placeholder: Drawable?) {
                    ivFullScreen.setImageDrawable(placeholder)
                }
            })

        btnRotate?.setOnClickListener {
            val base = loadedBitmap ?: return@setOnClickListener

            currentRotationDegrees = (currentRotationDegrees + 90f) % 360f

            val matrix = android.graphics.Matrix()
            matrix.postRotate(currentRotationDegrees)
            val rotated = android.graphics.Bitmap.createBitmap(
                base, 0, 0, base.width, base.height, matrix, true
            )
            ivFullScreen.setImageBitmap(rotated)

            btnSave?.isEnabled = true
            btnSave?.alpha = 1.0f
        }

        btnSave?.setOnClickListener {
            val base = loadedBitmap ?: return@setOnClickListener
            if (currentRotationDegrees == 0f) return@setOnClickListener

            btnSave.isEnabled = false
            btnSave.text = "SAVING..."

            java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                try {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(currentRotationDegrees)
                    val finalRotated = android.graphics.Bitmap.createBitmap(
                        base, 0, 0, base.width, base.height, matrix, true
                    )

                    val photoDir = File(filesDir, "player_photos")
                    if (!photoDir.exists()) photoDir.mkdirs()
                    val destFile = File(photoDir, "player_${player.id}.jpg")

                    java.io.FileOutputStream(destFile).use { out ->
                        finalRotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    }

                    val newPath = destFile.absolutePath
                    player.photoUri = newPath

                    val newBase64 = PhotoUtils.pathToBase64(this@PlayerDetailsActivity, newPath)
                    if (!newBase64.isNullOrEmpty()) {
                        player.photoBase64 = newBase64
                    }

                    db?.playerDao()?.updatePlayer(player)

                    val gId = GullySyncManager.getCurrentGullyId(this@PlayerDetailsActivity) ?: "local"
                    if (gId != "local") {
                        GullySyncManager.syncPlayerToCloud(gId, player)
                    }

                    runOnUiThread {
                        Toast.makeText(this@PlayerDetailsActivity, "Photo saved & synced across app!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadPlayerData()
                    }

                } catch (e: Exception) {
                    Log.e("PLAYER_DETAILS", "Error saving rotated photo: ${e.message}", e)
                    runOnUiThread {
                        btnSave.isEnabled = true
                        btnSave.text = "SAVE"
                        Toast.makeText(this@PlayerDetailsActivity, "Failed to save photo: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnClose?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPopupMenu(v: View?) {
        val popup = PopupMenu(this, v)
        val gId = GullySyncManager.getCurrentGullyId(this) ?: "local"
        val leagueName = if (gId.isBlank() || gId.equals("local", ignoreCase = true)) "Local League" else gId
        val removeTitle = "Remove Player from $leagueName"

        if (RemoteConfigManager.isFeatureEnabled(RemoteConfigManager.KEY_ENABLE_PLAYER_CARD_SHARE)) {
            popup.menu.add("Share Player Card")
        }
        if (RemoteConfigManager.isFeatureEnabled(RemoteConfigManager.KEY_ENABLE_EDIT_OPTION)) {
            popup.menu.add("Edit Profile")
        }
        popup.menu.add(removeTitle)
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Share Player Card" -> generateAndSharePlayerCard()
                "Edit Profile" -> checkSecurityForEdit()
                removeTitle -> checkSecurityForRemove(gId, leagueName)
            }
            true
        }
        popup.show()
    }

    private fun checkSecurityForEdit() {
        if (!RemoteConfigManager.isFeatureEnabled(RemoteConfigManager.KEY_ENABLE_EDIT_OPTION)) {
            Toast.makeText(this, "Editing is temporarily disabled", Toast.LENGTH_SHORT).show()
            return
        }
        showEditDialog()
    }

    private fun checkSecurityForRemove(gId: String, leagueName: String) {
        if (gId != "local" && gId.isNotBlank()) {
            GullyAdminManager.verifyAdminPinAndExecute(this, gId, "Remove Player") {
                confirmAndRemovePlayer(gId, leagueName)
            }
        } else {
            authenticate(this, "Remove Player", "Provide security to remove player", object : AuthCallback {
                override fun onSuccess() {
                    confirmAndRemovePlayer(gId, leagueName)
                }
                override fun onFailure(e: String?) {
                    Toast.makeText(this@PlayerDetailsActivity, "Failed: $e", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun confirmAndRemovePlayer(gId: String, leagueName: String) {
        val pName = currentPlayer?.name ?: "this player"
        showDynamicDialog {
            setTitle("Remove Player")
            setMessage("Are you sure you want to remove $pName from $leagueName?\n\nYou can bring this player back anytime using the 'Import Player' option.")
            setPositiveButton("Remove") { d, w -> removePlayerFromLeague(gId, leagueName) }
            setNegativeButton("Cancel", null)
        }
    }

    private fun removePlayerFromLeague(gId: String, leagueName: String) {
        val playerToRemove = currentPlayer ?: return
        val targetGullyId = playerToRemove.gullyId.ifBlank { gId }
        AppDatabase.ioExecutor.execute {
            db?.playerDao()?.deletePlayer(playerToRemove)
            if (targetGullyId != "local" && targetGullyId.isNotBlank()) {
                FirebaseFirestore.getInstance()
                    .collection("gullies")
                    .document(targetGullyId)
                    .collection("players")
                    .document(playerToRemove.id)
                    .delete()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Toast.makeText(this, "Player removed from $leagueName", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showEditDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_player, null)
        val nameIn = view.findViewById<EditText>(R.id.playerNameInput)
        val jerseyIn = view.findViewById<EditText>(R.id.playerJerseyInput)
        val preview = view.findViewById<ImageView>(R.id.ivDialogPreview)

        view.findViewById<View>(R.id.quickAddContainer)?.visibility = View.GONE
        view.findViewById<View>(R.id.quickAddDivider)?.visibility = View.GONE

        val player = currentPlayer ?: return

        nameIn.setText(if (pendingName.isEmpty()) player.name else pendingName)
        jerseyIn.setText(if (pendingJersey.isEmpty()) player.jerseyNumber else pendingJersey)

        val photoTarget: Any = pendingPhotoUri ?: when {
            !player.photoUrl.isNullOrEmpty() -> player.photoUrl
            !player.photoUri.isNullOrEmpty() -> player.photoUri
            !player.photoBase64.isNullOrEmpty() -> PhotoUtils.base64ToPath(this, player.photoBase64, player.id) ?: ""
            else -> ""
        }

        if (preview != null) {
            if (photoTarget != "") {
                Glide.with(this)
                    .load(photoTarget)
                    .signature(ObjectKey("${player.id}_${player.lastSyncedAt}"))
                    .placeholder(R.drawable.ic_person_placeholder)
                    .error(R.drawable.ic_person_placeholder)
                    .into(preview)
            } else {
                preview.setImageResource(R.drawable.ic_person_placeholder)
            }
        }

        val dialog = showDynamicDialog {
            setTitle("Edit Player")
            setView(view)
            setPositiveButton("Save", null)
            setNegativeButton("Cancel") { d, w ->
                pendingName = ""
                pendingJersey = ""
                pendingPhotoUri = null
            }
        }

        view.findViewById<View>(R.id.btnTakePhoto).setOnClickListener {
            pendingName = nameIn.text.toString().trim()
            pendingJersey = jerseyIn.text.toString().trim()
            launchCamera()
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnPickGallery).setOnClickListener {
            pendingName = nameIn.text.toString().trim()
            pendingJersey = jerseyIn.text.toString().trim()
            launchGallery()
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = nameIn.text.toString().trim()
            val j = jerseyIn.text.toString().trim()
            if (n.isNotEmpty() && j.isNotEmpty()) {
                val oldName = player.name // CAPTURE THE TRUTH HERE
                val nameChanged = (n != oldName)

                AppDatabase.ioExecutor.execute {
                    if (nameChanged) {
                        val existingSameName = db?.playerDao()?.getPlayerByNameByGully(n, player.gullyId)
                        if (existingSameName != null && existingSameName.id != player.id) {
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    Toast.makeText(this@PlayerDetailsActivity, "A player named '$n' already exists in this league!", Toast.LENGTH_LONG).show()
                                }
                            }
                            return@execute
                        }
                    }
                    
                    player.name = n
                    player.jerseyNumber = j
                    
                    // FIX: Ensure photo is saved to internal storage if it's a new URI
                    pendingPhotoUri?.let { uriStr ->
                        player.lastSyncedAt = System.currentTimeMillis()
                        if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) {
                            try {
                                val savedPath = PhotoUtils.savePhoto(this@PlayerDetailsActivity, Uri.parse(uriStr))
                                if (savedPath != null) player.photoUri = savedPath
                            } catch (e: Exception) {
                                Log.e("PlayerDetails", "Photo save failed: ${e.message}")
                                player.photoUri = uriStr
                            }
                        } else {
                            player.photoUri = uriStr
                        }
                    }

                    db?.playerDao()?.updatePlayer(player)

                    if (nameChanged) {
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                Toast.makeText(this@PlayerDetailsActivity, "Renaming player in all matches...", Toast.LENGTH_LONG).show()
                            }
                        }
                        PlayerRenameManager.renamePlayer(this@PlayerDetailsActivity, player, oldName, n) {
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    resetEditState()
                                    loadPlayerData()
                                    Toast.makeText(this@PlayerDetailsActivity, "Universal Rename Complete!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        GullySyncManager.syncPlayerToCloud(player.gullyId, player)
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                resetEditState()
                                loadPlayerData()
                            }
                        }
                    }

                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            dialog.dismiss()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Name and Jersey required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetEditState() {
        pendingName = ""
        pendingJersey = ""
        pendingPhotoUri = null
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERM_CAMERA)
            return
        }
        try {
            val photoFile = createImageFile()
            currentCameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            takePicture.launch(currentCameraUri!!)
        } catch (e: IOException) {
            Log.e("PlayerDetails", "Camera setup failed: ${e.message}")
        }
    }

    private fun launchGallery() {
        pickMedia.launch(PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
            .build())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_CAMERA && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        }
    }

    private fun calculateAndDisplayRankings() {
        AppDatabase.ioExecutor.execute {
            val gId = GullySyncManager.getCurrentGullyId(this) ?: "local"
            val overall = db?.statsDao()?.getOverallRankings(gId)
            val batting = db?.statsDao()?.getBattingRankings(gId)
            val bowling = db?.statsDao()?.getBowlingRankings(gId)

            var rO = "Rank #-"
            var rBat = "Rank #-"
            var rBowl = "Rank #-"

            playerId?.let { id ->
                overall?.indexOfFirst { it?.playerId == id }?.let { idx -> if (idx >= 0) rO = "Rank #${idx + 1}" }
                batting?.indexOfFirst { it?.playerId == id }?.let { idx -> if (idx >= 0) rBat = "Rank #${idx + 1}" }
                bowling?.indexOfFirst { it?.playerId == id }?.let { idx -> if (idx >= 0) rBowl = "Rank #${idx + 1}" }
            }

            // Calculate POTM awards for existing & upcoming matches
            val pName = currentPlayer?.name?.trim() ?: ""
            val pGlobalId = currentPlayer?.globalId

            val allMatches = db?.matchDao()?.getAllMatches()?.filterNotNull() ?: emptyList()
            val finishedMatches = allMatches.filter { it.isFinished && !it.isAbandoned }

            var potmCount = 0
            for (m in finishedMatches) {
                if (m.playerOfTheMatchName.isNullOrEmpty() || m.playerOfTheMatchName.equals("TBD", ignoreCase = true)) {
                    val matchStats = db?.statsDao()?.getStatsByMatch(m.id)?.filterNotNull() ?: emptyList()
                    if (matchStats.isNotEmpty()) {
                        var bestP: String? = null
                        var maxPts = -1.0
                        for (st in matchStats) {
                            val pts = PointsCalculator.calculatePlayerStatPoints(st)
                            if (pts > maxPts && pts > 0) {
                                maxPts = pts
                                bestP = st.playerName
                            }
                        }
                        if (!bestP.isNullOrEmpty()) {
                            m.playerOfTheMatchName = bestP
                            db?.matchDao()?.updateMatch(m)
                        }
                    }
                }

                val potmWinner = m.playerOfTheMatchName?.trim()
                if (!potmWinner.isNullOrEmpty() && !potmWinner.equals("TBD", ignoreCase = true)) {
                    val isMatchByName = pName.isNotEmpty() && (
                        PlayerNameNormalizer.areNamesMatching(potmWinner, pName) ||
                        PlayerNameNormalizer.isFuzzyMatch(potmWinner, pName)
                    )

                    var isMatchById = false
                    m.nameToIdMap?.forEach { (nameKey, mappedId) ->
                        if (!mappedId.isNullOrBlank()) {
                            val idMatches = mappedId == playerId || (pGlobalId != null && mappedId == pGlobalId)
                            if (idMatches && !nameKey.isNullOrBlank()) {
                                if (PlayerNameNormalizer.areNamesMatching(nameKey, potmWinner) || nameKey.trim().equals(potmWinner, ignoreCase = true)) {
                                    isMatchById = true
                                }
                            }
                        }
                    }

                    val matchStats = db?.statsDao()?.getStatsByMatch(m.id)?.filterNotNull() ?: emptyList()
                    val isMatchByStats = matchStats.any { st ->
                        val isThisPlayer = (playerId != null && st.playerId == playerId) ||
                                (pGlobalId != null && st.playerId == pGlobalId) ||
                                PlayerNameNormalizer.areNamesMatching(st.playerName, pName)
                        val isThisPotm = PlayerNameNormalizer.areNamesMatching(st.playerName, potmWinner) ||
                                PlayerNameNormalizer.isFuzzyMatch(st.playerName, potmWinner)
                        isThisPlayer && isThisPotm
                    }

                    if (isMatchByName || isMatchById || isMatchByStats) {
                        potmCount++
                    }
                }
            }

            cachedPotmCount = potmCount
            cachedRankOverall = rO
            cachedCardStats = cachedCardStats.copy(potmCount = potmCount)
            val rPotm = if (potmCount == 1) "1 Award" else "$potmCount Awards"

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                findViewById<TextView>(R.id.tvRankOverall)?.apply {
                    text = rO
                    if (rO == "Rank #1") setTextColor(Color.parseColor("#FFD54F"))
                }
                findViewById<TextView>(R.id.tvRankBatting)?.apply {
                    text = rBat
                    if (rBat == "Rank #1") setTextColor(Color.parseColor("#FF9800"))
                }
                findViewById<TextView>(R.id.tvRankBowling)?.apply {
                    text = rBowl
                    if (rBowl == "Rank #1") setTextColor(Color.parseColor("#E040FB"))
                }
                findViewById<TextView>(R.id.tvPotmTotal)?.apply {
                    text = rPotm
                    if (potmCount > 0) setTextColor(Color.parseColor("#FFD54F"))
                }

                if (rO == "Rank #1") {
                    findViewById<MaterialCardView>(R.id.cardRankOverall)?.apply {
                        setStrokeColor(ColorStateList.valueOf(Color.parseColor("#FFD54F")))
                        strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
                    }
                }
                if (rBat == "Rank #1") {
                    findViewById<MaterialCardView>(R.id.cardRankBatting)?.apply {
                        setStrokeColor(ColorStateList.valueOf(Color.parseColor("#FF9800")))
                        strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
                    }
                }
                if (rBowl == "Rank #1") {
                    findViewById<MaterialCardView>(R.id.cardRankBowling)?.apply {
                        setStrokeColor(ColorStateList.valueOf(Color.parseColor("#E040FB")))
                        strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
                    }
                }

                statsAdapter?.getPageView(3)?.let { bindMatchesPage(it) }
            }
        }
    }

    private fun isPlayerPotm(match: MatchEntity): Boolean {
        val player = currentPlayer ?: return false
        val potmWinner = match.playerOfTheMatchName?.trim()
        if (potmWinner.isNullOrEmpty() || potmWinner.equals("TBD", ignoreCase = true)) return false
        val pName = player.name
        val pGlobalId = player.globalId
        if (pName.isNotEmpty() && (PlayerNameNormalizer.areNamesMatching(potmWinner, pName) || PlayerNameNormalizer.isFuzzyMatch(potmWinner, pName))) {
            return true
        }
        match.nameToIdMap?.forEach { (nameKey, mappedId) ->
            if (!mappedId.isNullOrBlank()) {
                val idMatches = mappedId == playerId || (pGlobalId != null && mappedId == pGlobalId)
                if (idMatches && !nameKey.isNullOrBlank()) {
                    if (PlayerNameNormalizer.areNamesMatching(nameKey, potmWinner) || nameKey.trim().equals(potmWinner, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun bindMatchesPage(page: View) {
        val totalMatches = cachedTotalMatchesCount
        val wonMatches = cachedWonMatchesCount
        val winPct = cachedWinRatePct
        val potmCount = cachedPotmCount

        page.findViewById<TextView>(R.id.tvMatchTabTotalMatches)?.text = totalMatches.toString()
        page.findViewById<TextView>(R.id.tvMatchTabWonMatches)?.text = wonMatches.toString()
        page.findViewById<TextView>(R.id.tvMatchTabWinRate)?.text = String.format(Locale.US, "%.1f%%", winPct)
        page.findViewById<TextView>(R.id.tvMatchTabPotmCount)?.text = potmCount.toString()

        val tvEmpty = page.findViewById<TextView>(R.id.tvPlayerMatchesEmpty)
        val container = page.findViewById<LinearLayout>(R.id.layoutMatchesListContainer) ?: return

        container.removeAllViews()

        if (recentMatchesStats.isEmpty()) {
            tvEmpty?.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        tvEmpty?.visibility = View.GONE
        container.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val colorOnSurface = MaterialColors.getColor(page, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val colorMuted = Color.parseColor("#94A3B8")

        for (stat in recentMatchesStats) {
            val itemView = inflater.inflate(R.layout.item_player_match_history, container, false)
            val match = matchesMap[stat.matchId]

            // Date & Overs
            val tvDate = itemView.findViewById<TextView>(R.id.tvMatchHistoryDate)
            val dateStr = if (match != null && match.playedAt > 0) {
                dateFormat.format(Date(match.playedAt))
            } else if (stat.entryTime > 0) {
                dateFormat.format(Date(stat.entryTime))
            } else {
                "Match"
            }
            val oversStr = if (match != null && match.totalOvers > 0) " • ${match.totalOvers} Overs" else ""
            tvDate?.text = "$dateStr$oversStr"

            // POTM Badge
            val badgePotm = itemView.findViewById<TextView>(R.id.badgeMatchHistoryPotm)
            val isPotm = match != null && isPlayerPotm(match)
            badgePotm?.visibility = if (isPotm) View.VISIBLE else View.GONE

            // Teams / Opponent
            val tvTeams = itemView.findViewById<TextView>(R.id.tvMatchHistoryTeams)
            val opponent = if (match != null) {
                val pTeam = stat.teamName?.trim()
                if (pTeam != null && pTeam.equals(match.teamAName?.trim(), ignoreCase = true)) {
                    match.teamBName ?: "Opponent"
                } else {
                    match.teamAName ?: "Opponent"
                }
            } else "Opponent"
            val myTeam = stat.teamName?.trim()
            tvTeams?.text = if (!myTeam.isNullOrBlank()) "$myTeam vs $opponent" else "vs $opponent"

            // Result
            val tvResult = itemView.findViewById<TextView>(R.id.tvMatchHistoryResult)
            val rawResult = match?.result?.trim()
            if (!rawResult.isNullOrBlank()) {
                tvResult?.text = rawResult
                val pTeam = stat.teamName?.trim()
                val isTeamWin = !pTeam.isNullOrBlank() && (
                    rawResult.startsWith(pTeam, ignoreCase = true) ||
                    rawResult.contains("$pTeam won", ignoreCase = true)
                )
                val isTeamLoss = !pTeam.isNullOrBlank() && rawResult.contains("won", ignoreCase = true) && !isTeamWin

                when {
                    isTeamWin -> tvResult?.setTextColor(Color.parseColor("#81C784"))
                    isTeamLoss -> tvResult?.setTextColor(Color.parseColor("#EF5350"))
                    else -> tvResult?.setTextColor(Color.parseColor("#90CAF9"))
                }
            } else {
                tvResult?.text = "Match Completed"
                tvResult?.setTextColor(Color.parseColor("#94A3B8"))
            }

            // Batting Summary
            val tvBatting = itemView.findViewById<TextView>(R.id.tvMatchHistoryBatting)
            val layoutBat = itemView.findViewById<View>(R.id.layoutMatchBatSummary)
            if (stat.ballsFaced > 0 || stat.runsScored > 0 || stat.isOut) {
                val outMark = if (!stat.isOut) "*" else ""
                tvBatting?.text = "${stat.runsScored}$outMark (${stat.ballsFaced}b)"
                tvBatting?.setTextColor(colorOnSurface)
                layoutBat?.visibility = View.VISIBLE
            } else {
                tvBatting?.text = "DNB"
                tvBatting?.setTextColor(colorMuted)
            }

            // Bowling Summary
            val tvBowling = itemView.findViewById<TextView>(R.id.tvMatchHistoryBowling)
            val layoutBowl = itemView.findViewById<View>(R.id.layoutMatchBowlSummary)
            val didBowl = stat.ballsBowled > 0 || stat.wicketsTaken > 0 || stat.runsConceded > 0 || stat.widesConceded > 0 || stat.noBallsConceded > 0
            if (didBowl) {
                val ov = "${stat.ballsBowled / 6}.${stat.ballsBowled % 6}"
                tvBowling?.text = "${stat.wicketsTaken}/${stat.runsConceded} (${ov} ov)"
                tvBowling?.setTextColor(colorOnSurface)
                layoutBowl?.visibility = View.VISIBLE
            } else {
                tvBowling?.text = "DNB"
                tvBowling?.setTextColor(colorMuted)
            }

            // Fielding Summary
            val tvFielding = itemView.findViewById<TextView>(R.id.tvMatchHistoryFielding)
            val totalFielding = stat.catches + stat.stumpings + stat.runOuts
            if (totalFielding > 0) {
                val fList = mutableListOf<String>()
                if (stat.catches > 0) fList.add("${stat.catches}c")
                if (stat.stumpings > 0) fList.add("${stat.stumpings}st")
                if (stat.runOuts > 0) fList.add("${stat.runOuts}ro")
                tvFielding?.text = fList.joinToString(" ")
                tvFielding?.visibility = View.VISIBLE
            } else {
                tvFielding?.visibility = View.GONE
            }

            // Card click listener -> open MatchDetailsActivity
            val mId = stat.matchId
            if (!mId.isNullOrBlank()) {
                itemView.setOnClickListener {
                    StatTooltipHelper.dismissCurrent()
                    val intent = Intent(this, MatchDetailsActivity::class.java).apply {
                        putExtra("matchId", mId)
                    }
                    startActivity(intent)
                }
            }

            container.addView(itemView)
        }

        updateStatsHeightAction?.invoke()
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun calculateAndDisplayStats(stats: MutableList<PlayerMatchStatEntity>?) {
        val list = stats ?: mutableListOf()

        var runs = 0; var balls = 0; var fours = 0; var sixes = 0
        var bInns = 0; var no = 0; var highest = 0; var isHighestNotOut = false
        var f30 = 0; var f50 = 0; var f80 = 0; var f100 = 0; var dks = 0
        var totalSecsBatting = 0L

        var wInns = 0; var wkts = 0; var rCon = 0; var bBowl = 0
        var hats = 0; var maidensTotal = 0; var sixesGiven = 0
        var dotsTotal = 0; var extrasTotal = 0
        var widesTotal = 0; var noBallsTotal = 0
        var w2 = 0; var w3 = 0; var w4 = 0; var w5 = 0; var bbiW = 0; var bbiR = 0

        val uniqueMatchIds = mutableSetOf<String?>()
        var batDotsFromBalls = 0
        var batBallsFromBalls = 0
        var bowlFoursConceded = 0
        var wonMatchesCount = 0
        val evaluatedMatches = mutableSetOf<String>()

        for (s in list) {
            uniqueMatchIds.add(s.matchId)
            val mId = s.matchId
            if (mId != null && evaluatedMatches.add(mId)) {
                val match = matchesMap[mId]
                if (match != null) {
                    val pTeam = s.teamName?.trim()
                    val result = match.result
                    if (!pTeam.isNullOrBlank() && !result.isNullOrBlank()) {
                        if (result.startsWith(pTeam, ignoreCase = true) || result.contains("$pTeam won", ignoreCase = true)) {
                            wonMatchesCount++
                        }
                    }

                    val allMatchBalls = (match.ballsJson1?.filterNotNull().orEmpty() + match.ballsJson2?.filterNotNull().orEmpty())
                    val pName = currentPlayer?.name ?: s.playerName ?: ""
                    val pId = playerId ?: currentPlayer?.id
                    val pGlobalId = currentPlayer?.globalId

                    for (b in allMatchBalls) {
                        val isBatter = (pId != null && b.batsmanId == pId) ||
                                (pGlobalId != null && b.batsmanId == pGlobalId) ||
                                (pName.isNotBlank() && PlayerNameNormalizer.areNamesMatching(b.batsmanName, pName))

                        if (isBatter && b.type != BallType.WIDE) {
                            batBallsFromBalls++
                            if (b.runs == 0) {
                                batDotsFromBalls++
                            }
                        }

                        val isBowler = (pId != null && b.bowlerId == pId) ||
                                (pGlobalId != null && b.bowlerId == pGlobalId) ||
                                (pName.isNotBlank() && PlayerNameNormalizer.areNamesMatching(b.bowlerName, pName))

                        if (isBowler && b.batRuns == 4) {
                            bowlFoursConceded++
                        }
                    }
                }
            }
            if (s.runsScored > 0 || s.ballsFaced > 0 || s.isOut) {
                bInns++; runs += s.runsScored; balls += s.ballsFaced
                fours += s.fours; sixes += s.sixes
                totalSecsBatting += s.minutesPlayed
                if (!s.isOut) no++
                if (s.runsScored > highest) {
                    highest = s.runsScored
                    isHighestNotOut = !s.isOut
                } else if (s.runsScored == highest && !s.isOut) {
                    isHighestNotOut = true
                }
                if (s.runsScored >= 100) f100++ else if (s.runsScored >= 80) f80++ else if (s.runsScored >= 50) f50++ else if (s.runsScored >= 30) f30++
                if (s.isOut && s.runsScored == 0) dks++
            }
            val didBowl = s.ballsBowled > 0 || s.wicketsTaken > 0 || s.runsConceded > 0 || s.widesConceded > 0 || s.noBallsConceded > 0
            if (didBowl) {
                wInns++; wkts += s.wicketsTaken; rCon += s.runsConceded; bBowl += s.ballsBowled
                hats += s.hattricks; maidensTotal += s.maidens; sixesGiven += s.sixesConceded
                dotsTotal += s.dotBalls; extrasTotal += (s.widesConceded + s.noBallsConceded)
                widesTotal += s.widesConceded; noBallsTotal += s.noBallsConceded
                if (s.wicketsTaken >= 5) w5++ else if (s.wicketsTaken == 4) w4++ else if (s.wicketsTaken == 3) w3++ else if (s.wicketsTaken == 2) w2++
                if (s.wicketsTaken > bbiW || (s.wicketsTaken == bbiW && s.runsConceded < bbiR) || (bbiW == 0 && bbiR == 0 && s.wicketsTaken > 0)) {
                    bbiW = s.wicketsTaken; bbiR = s.runsConceded
                }
            }
        }

        val m = uniqueMatchIds.size
        val batAvg = if ((bInns - no) > 0) runs.toDouble() / (bInns - no) else runs.toDouble()
        val batSR = if (balls > 0) (runs.toDouble() / balls) * 100 else 0.0
        val boundaryRuns = (fours * 4) + (sixes * 6)
        val boundaryPct = if (runs > 0) (boundaryRuns.toDouble() / runs) * 100.0 else 0.0
        val totalBoundaries = fours + sixes
        val ballsPerBoundary = if (totalBoundaries > 0) balls.toDouble() / totalBoundaries else 0.0
        val totalMinsBatting = (totalSecsBatting / 60).toInt()
        val timeOnCreaseStr = "${totalMinsBatting}m"
        val runningRuns = runs - boundaryRuns
        val strikeRotPct = if (runs > 0) (runningRuns.toDouble() / runs) * 100.0 else 0.0
        val runsPerInns = if (bInns > 0) runs.toDouble() / bInns else 0.0
        val ballsPerInns = if (bInns > 0) balls.toDouble() / bInns else 0.0
        val avgCreaseTime = if (bInns > 0) "${totalMinsBatting / bInns}m" else "0m"
        val boundaryFreqPct = if (balls > 0) (totalBoundaries.toDouble() / balls) * 100.0 else 0.0

        val batDotPct = if (batBallsFromBalls > 0) {
            (batDotsFromBalls.toDouble() / batBallsFromBalls) * 100.0
        } else if (balls > 0) {
            val bndryBalls = fours + sixes
            val runningBalls = Math.max(0, balls - bndryBalls)
            val estimatedScoringBalls = Math.min(runningBalls, Math.max(0, (runningRuns / 1.2).toInt()))
            val estimatedDots = Math.max(0, runningBalls - estimatedScoringBalls)
            (estimatedDots.toDouble() / balls) * 100.0
        } else {
            0.0
        }
        val ratioStr = if (sixes > 0) {
            String.format(Locale.US, "%.1f : 1", fours.toDouble() / sixes)
        } else if (fours > 0) {
            "$fours : 0"
        } else {
            "0 : 0"
        }
        val totalMilestones = f50 + f80 + f100
        val convRatePct = if (totalMilestones > 0) (f100.toDouble() / totalMilestones) * 100.0 else 0.0

        val winPct = if (m > 0) (wonMatchesCount.toDouble() / m) * 100.0 else 0.0
        cachedTotalMatchesCount = m
        cachedWonMatchesCount = wonMatchesCount
        cachedWinRatePct = winPct

        val bowlAvg = if (wkts > 0) rCon.toDouble() / wkts else 0.0
        val bowlEco = if (bBowl > 0) (rCon.toDouble() / bBowl) * 6.0 else 0.0
        val oversStr = "${bBowl / 6}.${bBowl % 6}"
        val dotPct = if (bBowl > 0) (dotsTotal.toDouble() / bBowl) * 100.0 else 0.0
        val bowlSR = if (wkts > 0) bBowl.toDouble() / wkts else 0.0
        val dotsPerOver = if (bBowl > 0) (dotsTotal.toDouble() / (bBowl / 6.0)) else 0.0
        val wktsPerMatch = if (m > 0) wkts.toDouble() / m else 0.0
        val ballsPerSix = if (sixesGiven > 0) bBowl.toDouble() / sixesGiven else 0.0

        val totalBoundaryBallsConceded = bowlFoursConceded + sixesGiven
        val bndryConcededPct = if (bBowl > 0) (totalBoundaryBallsConceded.toDouble() / bBowl) * 100.0 else 0.0
        val wktsPerInns = if (wInns > 0) wkts.toDouble() / wInns else 0.0
        val bndryRunsConceded = (bowlFoursConceded * 4) + (sixesGiven * 6)

        // Batting Standard
        setBox(R.id.boxBatMatches, "MATCHES", m.toString())
        setBox(R.id.boxBatInnings, "INNINGS", bInns.toString())
        setBox(R.id.boxBatRuns, "RUNS", runs.toString())
        setBox(R.id.boxBatHighest, "HIGHEST", highest.toString())
        setBox(R.id.boxBatAvg, "AVERAGE", String.format(Locale.US, "%.1f", batAvg))
        setBox(R.id.boxBatSR, "STRIKE RATE", String.format(Locale.US, "%.1f", batSR))
        setBox(R.id.boxBatFours, "FOURS", fours.toString())
        setBox(R.id.boxBatSixes, "SIXES", sixes.toString())
        setBox(R.id.boxBatNotOut, "NOT OUT", no.toString())
        setBox(R.id.boxBatDucks, "DUCKS", dks.toString())
        setBox(R.id.boxBat30s, "30s", f30.toString())
        setBox(R.id.boxBat50s, "50s", f50.toString())
        setBox(R.id.boxBat80s, "80s", f80.toString())
        setBox(R.id.boxBat100s, "100s", f100.toString())

        // Batting Advanced
        setBox(R.id.boxBatBalls, "BALLS FACED", balls.toString())
        setBox(R.id.boxBatMinsPlayed, "MINS PLAYED", timeOnCreaseStr)
        setBox(R.id.boxBatAvgCreaseTime, "AVG CREASE", avgCreaseTime)
        setBox(R.id.boxBatRunsPerInnings, "RUNS / INNS", String.format(Locale.US, "%.1f", runsPerInns))
        setBox(R.id.boxBatBallsPerInnings, "BALLS / INNS", String.format(Locale.US, "%.1f", ballsPerInns))
        setBox(R.id.boxBatTotalBoundaries, "TOTAL BNDRY", totalBoundaries.toString())
        setBox(R.id.boxBatBoundaryRuns, "BOUNDARY RUNS", boundaryRuns.toString())
        setBox(R.id.boxBatBoundaryPercent, "BOUNDARY %", String.format(Locale.US, "%.1f%%", boundaryPct))
        setBox(R.id.boxBatBallsPerBoundary, "BALLS/BNDRY", String.format(Locale.US, "%.1f", ballsPerBoundary))
        setBox(R.id.boxBatBoundaryFreq, "BNDRY FREQ %", String.format(Locale.US, "%.1f%%", boundaryFreqPct))
        setBox(R.id.boxBatRunningRuns, "RUNNING RUNS", runningRuns.toString())
        setBox(R.id.boxBatStrikeRotation, "STRIKE ROT. %", String.format(Locale.US, "%.1f%%", strikeRotPct))
        setBox(R.id.boxBatDotPercent, "DOT BALL %", String.format(Locale.US, "%.1f%%", batDotPct))
        setBox(R.id.boxBatBoundaryRatio, "4s : 6s RATIO", ratioStr)
        setBox(R.id.boxBatConversionRate, "CONV. RATE %", String.format(Locale.US, "%.1f%%", convRatePct))
        setBox(R.id.boxBatTeamWinPct, "TEAM WIN %", String.format(Locale.US, "%.1f%%", winPct))

        // Bowling Standard
        setBox(R.id.boxBowlMatches, "MATCHES", m.toString())
        setBox(R.id.boxBowlInnings, "INNINGS", wInns.toString())
        setBox(R.id.boxBowlWickets, "WICKETS", wkts.toString())
        setBox(R.id.boxBowlOvers, "OVERS", oversStr)
        setBox(R.id.boxBowlEco, "ECONOMY", String.format(Locale.US, "%.1f", bowlEco))
        setBox(R.id.boxBowlAvg, "AVERAGE", String.format(Locale.US, "%.1f", bowlAvg))
        setBox(R.id.boxBowlBBI, "BEST FIGURE", "$bbiW/$bbiR")
        setBox(R.id.boxBowlMaidens, "MAIDENS", maidensTotal.toString())
        setBox(R.id.boxBowl2w, "2W HAUL", w2.toString())
        setBox(R.id.boxBowl3w, "3W HAUL", w3.toString())
        setBox(R.id.boxBowl4w, "4W HAUL", w4.toString())
        setBox(R.id.boxBowl5w, "5W HAUL", w5.toString())
        setBox(R.id.boxBowlHattricks, "HAT-TRICKS", hats.toString())
        setBox(R.id.boxBowlSixesConceded, "6S GIVEN", sixesGiven.toString())

        // Bowling Advanced
        setBox(R.id.boxBowlBalls, "BALLS BOWLED", bBowl.toString())
        setBox(R.id.boxBowlRunsConceded, "RUNS GIVEN", rCon.toString())
        setBox(R.id.boxBowlWktsPerMatch, "WKTS / MATCH", String.format(Locale.US, "%.1f", wktsPerMatch))
        setBox(R.id.boxBowlStrikeRate, "BOWLING SR", String.format(Locale.US, "%.1f", bowlSR))
        setBox(R.id.boxBowlDotBalls, "DOT BALLS", dotsTotal.toString())
        setBox(R.id.boxBowlDotPercent, "DOT BALL %", String.format(Locale.US, "%.1f%%", dotPct))
        setBox(R.id.boxBowlDotsPerOver, "DOTS / OVER", String.format(Locale.US, "%.1f", dotsPerOver))
        setBox(R.id.boxBowlExtras, "EXTRAS", extrasTotal.toString())
        setBox(R.id.boxBowlWides, "WIDES GIVEN", widesTotal.toString())
        setBox(R.id.boxBowlNoBalls, "NO-BALLS", noBallsTotal.toString())
        setBox(R.id.boxBowlBallsPerSix, "BALLS / 6 GIVEN", String.format(Locale.US, "%.1f", ballsPerSix))
        setBox(R.id.boxBowlBoundaryConcededPct, "BNDRY GIVEN %", String.format(Locale.US, "%.1f%%", bndryConcededPct))
        setBox(R.id.boxBowlWktsPerInns, "WKTS / INNS", String.format(Locale.US, "%.2f", wktsPerInns))
        setBox(R.id.boxBowlBoundaryRuns, "BNDRY RUNS", bndryRunsConceded.toString())
        setBox(R.id.boxBowlTeamWinPct, "TEAM WIN %", String.format(Locale.US, "%.1f%%", winPct))

        // Fielding Standard
        var fCatches = 0; var fStumps = 0; var fRO = 0
        for (s in list) { fCatches += s.catches; fStumps += s.stumpings; fRO += s.runOuts }
        val fTotalDism = fCatches + fStumps + fRO
        val dismPerMatch = if (m > 0) fTotalDism.toDouble() / m else 0.0
        val catchPerMatch = if (m > 0) fCatches.toDouble() / m else 0.0
        val roPerMatch = if (m > 0) fRO.toDouble() / m else 0.0
        val fieldingPts = (fCatches * 10) + (fRO * 15) + (fStumps * 15)
        val catchSharePct = if (fTotalDism > 0) (fCatches.toDouble() / fTotalDism) * 100.0 else 0.0
        val roSharePct = if (fTotalDism > 0) (fRO.toDouble() / fTotalDism) * 100.0 else 0.0
        val matchesPerCatch = if (fCatches > 0) m.toDouble() / fCatches else 0.0
        val matchesPerRO = if (fRO > 0) m.toDouble() / fRO else 0.0

        setBox(R.id.boxFieldMatches, "MATCHES", m.toString())
        setBox(R.id.boxFieldCatches, "CATCHES", fCatches.toString())
        setBox(R.id.boxFieldStumpings, "STUMPINGS", fStumps.toString())
        setBox(R.id.boxFieldRunOuts, "RUN OUTS", fRO.toString())

        // Fielding Advanced
        setBox(R.id.boxFieldTotalDismissals, "TOTAL DISM.", fTotalDism.toString())
        setBox(R.id.boxFieldDismissalsPerMatch, "DISM. / MATCH", String.format(Locale.US, "%.2f", dismPerMatch))
        setBox(R.id.boxFieldImpactPts, "FIELDING PTS", fieldingPts.toString())
        setBox(R.id.boxFieldCatchesPerMatch, "CATCH / MATCH", String.format(Locale.US, "%.2f", catchPerMatch))
        setBox(R.id.boxFieldCatchShare, "CATCH SHARE", String.format(Locale.US, "%.1f%%", catchSharePct))
        setBox(R.id.boxFieldMatchesPerCatch, "MATCHES / CT", String.format(Locale.US, "%.1f", matchesPerCatch))
        setBox(R.id.boxFieldRunOutsPerMatch, "RO / MATCH", String.format(Locale.US, "%.2f", roPerMatch))
        setBox(R.id.boxFieldRunOutShare, "RO SHARE", String.format(Locale.US, "%.1f%%", roSharePct))
        setBox(R.id.boxFieldMatchesPerRO, "MATCHES / RO", String.format(Locale.US, "%.1f", matchesPerRO))

        cachedCardStats = PlayerCardStats(
            runs = runs,
            batAvg = batAvg,
            batSR = batSR,
            highest = highest,
            isHighestNotOut = isHighestNotOut,
            wickets = wkts,
            bowlEco = bowlEco,
            bowlAvg = bowlAvg,
            bbi = "$bbiW/$bbiR",
            matches = m,
            winPct = winPct,
            potmCount = cachedPotmCount,
            totalDismissals = fTotalDism
        )

        statsAdapter?.getPageView(3)?.let { bindMatchesPage(it) }
    }

    private fun setBox(id: Int, label: String, value: String) {
        if (statsAdapter != null) {
            statsAdapter?.setStatValue(id, label, value)
        } else {
            val v = findViewById<View>(id) ?: return
            v.findViewById<TextView>(R.id.tvStatLabel)?.text = label
            v.findViewById<TextView>(R.id.tvStatValue)?.text = value
        }
    }

    class StatsPagerAdapter(
        private val onContentUpdated: (() -> Unit)? = null,
        private val onBindMatches: ((View) -> Unit)? = null
    ) : RecyclerView.Adapter<StatsPagerAdapter.StatsViewHolder>() {
        private val pageLayouts = intArrayOf(
            R.layout.page_player_stats_batting,
            R.layout.page_player_stats_bowling,
            R.layout.page_player_stats_fielding,
            R.layout.page_player_stats_matches
        )
        private val pageViews = Array<View?>(4) { null }
        private val pendingData = mutableMapOf<Int, Pair<String, String>>()

        fun getPageView(position: Int): View? = pageViews.getOrNull(position)

        fun setStatValue(boxId: Int, label: String, value: String) {
            pendingData[boxId] = Pair(label, value)
            for (page in pageViews) {
                val v = page?.findViewById<View>(boxId)
                if (v != null) {
                    v.findViewById<TextView>(R.id.tvStatLabel)?.text = label
                    v.findViewById<TextView>(R.id.tvStatValue)?.text = value
                    break
                }
            }
            onContentUpdated?.invoke()
        }

        override fun getItemCount(): Int = 4

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatsViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(pageLayouts[viewType], parent, false)
            StatTooltipHelper.attachTooltips(view)
            return StatsViewHolder(view)
        }

        override fun getItemViewType(position: Int): Int = position

        override fun onBindViewHolder(holder: StatsViewHolder, position: Int) {
            pageViews[position] = holder.itemView
            if (position == 3) {
                onBindMatches?.invoke(holder.itemView)
            } else {
                pendingData.forEach { (boxId, pair) ->
                    val v = holder.itemView.findViewById<View>(boxId)
                    if (v != null) {
                        v.findViewById<TextView>(R.id.tvStatLabel)?.text = pair.first
                        v.findViewById<TextView>(R.id.tvStatValue)?.text = pair.second
                    }
                }
            }
            onContentUpdated?.invoke()
        }

        class StatsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }

    companion object {
        private const val PERM_CAMERA = 2001
    }
}

package com.goldenduck.scoring

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.util.Locale

class LeaderboardActivity : BaseActivity() {

    private var type: String? = null
    private var recyclerView: RecyclerView? = null
    private var pbLoading: ProgressBar? = null
    private var layoutEmpty: LinearLayout? = null
    private var tvEmptyTitle: TextView? = null
    private var tvEmptySubtitle: TextView? = null

    // Search bar components
    private var layoutSearchContainer: LinearLayout? = null
    private var etSearch: EditText? = null
    private var btnClearSearch: ImageButton? = null
    private var btnSearch: ImageButton? = null

    private var adapter: LeaderboardUnifiedAdapter? = null
    private var allRankedList: List<RankedPlayerStat> = emptyList()
    private var filteredList: List<RankedPlayerStat> = emptyList()
    private var currentSearchQuery: String = ""

    private val viewModel: LeaderboardViewModel by viewModels()

    data class RankedPlayerStat(
        val rank: Int,
        val stat: PlayerTotalStat
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats_leaderboard)

        type = intent.getStringExtra("type")

        initViews()
        setupHeader()
        setupSearch()
        observeViewModel()

        viewModel.loadLeaderboard(type)
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.rvLeaderboard)
        recyclerView?.layoutManager = LinearLayoutManager(this)

        pbLoading = findViewById(R.id.pbLeaderboardLoading)
        layoutEmpty = findViewById(R.id.layoutEmptyLeaderboard)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptySubtitle = findViewById(R.id.tvEmptyLeaderboard)

        val btnBack = findViewById<ImageButton>(R.id.btnBackLeaderboard)
        btnBack?.setOnClickListener { finish() }

        layoutSearchContainer = findViewById(R.id.layoutSearchContainer)
        etSearch = findViewById(R.id.etSearchLeaderboard)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        btnSearch = findViewById(R.id.btnSearchLeaderboard)
    }

    private fun setupHeader() {
        val tvTitle = findViewById<TextView>(R.id.tvLeaderboardTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvLeaderboardSubtitle)
        val ivIcon = findViewById<ImageView>(R.id.ivLeaderboardCategoryIcon)
        val flCategoryIcon = findViewById<FrameLayout>(R.id.flCategoryIcon)

        val statTitle = type ?: "Leaderboard"
        tvTitle.text = statTitle
        tvSubtitle.text = getSubtitleForStat(statTitle)
        val iconRes = getIconForStat(statTitle)
        ivIcon.setImageResource(iconRes)

        val isDark = ThemeManager.isDarkMode(this)
        val style = getCategoryStyle(statTitle, isDark)

        val density = resources.displayMetrics.density
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * density
            setColor(style.bg)
            setStroke((1.5f * density).toInt(), style.stroke)
        }
        flCategoryIcon.background = shape
        ivIcon.imageTintList = ColorStateList.valueOf(style.iconTint)
    }

    private fun setupSearch() {
        btnSearch?.setOnClickListener {
            val isVisible = layoutSearchContainer?.visibility == View.VISIBLE
            if (isVisible) {
                closeSearch()
            } else {
                openSearch()
            }
        }

        btnClearSearch?.setOnClickListener {
            etSearch?.setText("")
        }

        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                filterResults(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun openSearch() {
        layoutSearchContainer?.visibility = View.VISIBLE
        etSearch?.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeSearch() {
        etSearch?.setText("")
        layoutSearchContainer?.visibility = View.GONE
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etSearch?.windowToken, 0)
        filterResults("")
    }

    private fun filterResults(query: String) {
        currentSearchQuery = query
        filteredList = if (query.isEmpty()) {
            allRankedList
        } else {
            allRankedList.filter {
                it.stat.playerName?.contains(query, ignoreCase = true) == true
            }
        }
        updateUiState()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is LeaderboardUiState.Loading -> {
                            pbLoading?.visibility = View.VISIBLE
                            layoutEmpty?.visibility = View.GONE
                            recyclerView?.visibility = View.GONE
                        }
                        is LeaderboardUiState.Success -> {
                            pbLoading?.visibility = View.GONE
                            allRankedList = state.stats.mapIndexed { index, stat ->
                                RankedPlayerStat(index + 1, stat)
                            }
                            filteredList = allRankedList
                            updateUiState()
                        }
                        is LeaderboardUiState.Error -> {
                            pbLoading?.visibility = View.GONE
                            Toast.makeText(this@LeaderboardActivity, state.message, Toast.LENGTH_SHORT).show()
                            layoutEmpty?.visibility = View.VISIBLE
                            recyclerView?.visibility = View.GONE
                            tvEmptyTitle?.text = "Error Loading Standings"
                            tvEmptySubtitle?.text = state.message
                        }
                    }
                }
            }
        }
    }

    private fun updateUiState() {
        if (filteredList.isEmpty()) {
            layoutEmpty?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
            if (currentSearchQuery.isNotEmpty()) {
                tvEmptyTitle?.text = "No Players Found"
                tvEmptySubtitle?.text = "No results matching \"$currentSearchQuery\""
            } else {
                tvEmptyTitle?.text = "No Standings Found"
                tvEmptySubtitle?.text = getString(R.string.empty_leaderboard_msg)
            }
        } else {
            layoutEmpty?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
            if (adapter == null) {
                adapter = LeaderboardUnifiedAdapter()
                recyclerView?.adapter = adapter
            } else {
                adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun openPlayerDetails(playerId: String?) {
        if (playerId.isNullOrEmpty()) return
        val intent = Intent(this, PlayerDetailsActivity::class.java)
        intent.putExtra("playerId", playerId)
        startActivity(intent)
    }

    // --- RECYCLER VIEW ADAPTER ---
    private inner class LeaderboardUnifiedAdapter :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val showPodium: Boolean
            get() = currentSearchQuery.isEmpty() && filteredList.size >= 3

        override fun getItemViewType(position: Int): Int {
            return if (showPodium) {
                when (position) {
                    0 -> VIEW_TYPE_PODIUM
                    1 -> VIEW_TYPE_HEADER
                    else -> VIEW_TYPE_ROW
                }
            } else {
                if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ROW
            }
        }

        override fun getItemCount(): Int {
            if (filteredList.isEmpty()) return 0
            return if (showPodium) {
                val remaining = filteredList.size - 3
                if (remaining > 0) 1 + 1 + remaining else 1
            } else {
                1 + filteredList.size // 1 Header + all rows
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_PODIUM -> {
                    val view = inflater.inflate(R.layout.item_leaderboard_podium, parent, false)
                    PodiumHolder(view)
                }
                VIEW_TYPE_HEADER -> {
                    val view = inflater.inflate(R.layout.item_leaderboard_header, parent, false)
                    HeaderHolder(view)
                }
                else -> {
                    val view = inflater.inflate(R.layout.item_stat_row, parent, false)
                    RowHolder(view)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is PodiumHolder -> holder.bind(filteredList.take(3))
                is HeaderHolder -> {
                    if (showPodium) {
                        val remainingCount = filteredList.size - 3
                        holder.title.text = "ALL STANDINGS (RANK 4+)"
                        holder.count.text = "$remainingCount Players"
                    } else {
                        if (currentSearchQuery.isNotEmpty()) {
                            holder.title.text = "SEARCH RESULTS"
                            holder.count.text = "${filteredList.size} Found"
                        } else {
                            holder.title.text = "ALL STANDINGS"
                            holder.count.text = "${filteredList.size} Players"
                        }
                    }
                }
                is RowHolder -> {
                    val dataIndex = if (showPodium) {
                        position - 2 + 3 // position 2 maps to index 3
                    } else {
                        position - 1 // position 1 maps to index 0
                    }
                    val item = filteredList.getOrNull(dataIndex) ?: return
                    holder.bind(item)
                }
            }
        }
    }

    // --- VIEW HOLDERS ---
    private inner class PodiumHolder(v: View) : RecyclerView.ViewHolder(v) {
        val layout1: View = v.findViewById(R.id.layoutPodium1)
        val photo1: ShapeableImageView = v.findViewById(R.id.ivPodiumPhoto1)
        val name1: TextView = v.findViewById(R.id.tvPodiumName1)
        val pos1: TextView = v.findViewById(R.id.tvPodiumPosition1)
        val value1: TextView = v.findViewById(R.id.tvPodiumValue1)
        val unit1: TextView = v.findViewById(R.id.tvPodiumUnit1)

        val layout2: View = v.findViewById(R.id.layoutPodium2)
        val photo2: ShapeableImageView = v.findViewById(R.id.ivPodiumPhoto2)
        val name2: TextView = v.findViewById(R.id.tvPodiumName2)
        val pos2: TextView = v.findViewById(R.id.tvPodiumPosition2)
        val value2: TextView = v.findViewById(R.id.tvPodiumValue2)
        val unit2: TextView = v.findViewById(R.id.tvPodiumUnit2)

        val layout3: View = v.findViewById(R.id.layoutPodium3)
        val photo3: ShapeableImageView = v.findViewById(R.id.ivPodiumPhoto3)
        val name3: TextView = v.findViewById(R.id.tvPodiumName3)
        val pos3: TextView = v.findViewById(R.id.tvPodiumPosition3)
        val value3: TextView = v.findViewById(R.id.tvPodiumValue3)
        val unit3: TextView = v.findViewById(R.id.tvPodiumUnit3)

        fun bind(top3: List<RankedPlayerStat>) {
            val first = top3.getOrNull(0)
            val second = top3.getOrNull(1)
            val third = top3.getOrNull(2)

            val unit = getUnitLabelForCategory(type).uppercase(Locale.US)

            // 1st Place (Center)
            if (first != null) {
                name1.text = first.stat.playerName ?: "Unknown"
                pos1.text = "1st"
                value1.text = formatStatValue(first.stat.total, type)
                unit1.text = unit
                loadAvatar(first.stat, photo1)
                layout1.setOnClickListener { openPlayerDetails(first.stat.playerId) }
                layout1.visibility = View.VISIBLE
            } else {
                layout1.visibility = View.INVISIBLE
            }

            // 2nd Place (Left)
            if (second != null) {
                name2.text = second.stat.playerName ?: "Unknown"
                name2.setTextColor(Color.WHITE)
                pos2.text = "2nd"
                value2.text = formatStatValue(second.stat.total, type)
                unit2.text = unit
                loadAvatar(second.stat, photo2)
                layout2.setOnClickListener { openPlayerDetails(second.stat.playerId) }
                layout2.visibility = View.VISIBLE
            } else {
                layout2.visibility = View.INVISIBLE
            }

            // 3rd Place (Right)
            if (third != null) {
                name3.text = third.stat.playerName ?: "Unknown"
                name3.setTextColor(Color.WHITE)
                pos3.text = "3rd"
                value3.text = formatStatValue(third.stat.total, type)
                unit3.text = unit
                loadAvatar(third.stat, photo3)
                layout3.setOnClickListener { openPlayerDetails(third.stat.playerId) }
                layout3.visibility = View.VISIBLE
            } else {
                layout3.visibility = View.INVISIBLE
            }
        }
    }

    private inner class HeaderHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvSectionTitle)
        val count: TextView = v.findViewById(R.id.tvSectionCount)
    }

    private inner class RowHolder(v: View) : RecyclerView.ViewHolder(v) {
        val card: MaterialCardView = v as MaterialCardView
        val flRankBadge: FrameLayout = v.findViewById(R.id.flRankBadge)
        val tvRank: TextView = v.findViewById(R.id.tvStatRank)
        val photo: ShapeableImageView = v.findViewById(R.id.ivStatPhoto)
        val name: TextView = v.findViewById(R.id.tvStatPlayerName)
        val subtitle: TextView = v.findViewById(R.id.tvStatSubtitle)
        val flValueBg: LinearLayout = v.findViewById(R.id.flStatValueBg)
        val tvValue: TextView = v.findViewById(R.id.tvStatValue)
        val tvUnit: TextView = v.findViewById(R.id.tvStatUnit)

        fun bind(item: RankedPlayerStat) {
            val rankNum = item.rank
            tvRank.text = rankNum.toString()
            name.text = item.stat.playerName ?: "Unknown"
            subtitle.text = "Rank #$rankNum"

            tvValue.text = formatStatValue(item.stat.total, type)
            tvUnit.text = getUnitLabelForCategory(type).uppercase(Locale.US)

            val density = itemView.context.resources.displayMetrics.density

            when (rankNum) {
                1 -> {
                    flRankBadge.setBackgroundResource(R.drawable.bg_rank_gold)
                    tvRank.setTextColor(Color.WHITE)
                    flValueBg.setBackgroundResource(R.drawable.bg_stat_value_pill_gold)
                    card.strokeColor = Color.parseColor("#D4AF37")
                    card.strokeWidth = (1.5f * density).toInt()
                    photo.strokeColor = ColorStateList.valueOf(Color.parseColor("#FFD700"))
                    photo.strokeWidth = 2f * density
                }
                2 -> {
                    flRankBadge.setBackgroundResource(R.drawable.bg_rank_silver)
                    tvRank.setTextColor(Color.WHITE)
                    flValueBg.setBackgroundResource(R.drawable.bg_stat_value_pill_silver)
                    card.strokeColor = Color.parseColor("#8A98A5")
                    card.strokeWidth = (1.5f * density).toInt()
                    photo.strokeColor = ColorStateList.valueOf(Color.parseColor("#CBD5E1"))
                    photo.strokeWidth = 2f * density
                }
                3 -> {
                    flRankBadge.setBackgroundResource(R.drawable.bg_rank_bronze)
                    tvRank.setTextColor(Color.WHITE)
                    flValueBg.setBackgroundResource(R.drawable.bg_stat_value_pill_bronze)
                    card.strokeColor = Color.parseColor("#CD7F32")
                    card.strokeWidth = (1.5f * density).toInt()
                    photo.strokeColor = ColorStateList.valueOf(Color.parseColor("#CD7F32"))
                    photo.strokeWidth = 2f * density
                }
                else -> {
                    flRankBadge.setBackgroundResource(R.drawable.bg_rank_pill)
                    tvRank.setTextColor(Color.WHITE)
                    flValueBg.setBackgroundResource(R.drawable.bg_stat_value_pill)
                    card.strokeColor = ThemeManager.getThemeColor(
                        itemView.context,
                        com.google.android.material.R.attr.colorOutline
                    )
                    card.strokeWidth = (1f * density).toInt()
                    photo.strokeColor = ColorStateList.valueOf(Color.parseColor("#2E3A4E"))
                    photo.strokeWidth = 1.5f * density
                }
            }

            loadAvatar(item.stat, photo)

            ThemeManager.colorize(itemView)

            val isDark = ThemeManager.isDarkMode(itemView.context)
            tvValue.setTextColor(if (isDark) Color.WHITE else Color.BLACK)

            itemView.setOnClickListener {
                openPlayerDetails(item.stat.playerId)
            }
        }
    }

    private fun loadAvatar(stat: PlayerTotalStat, imageView: ShapeableImageView) {
        val photoUri = stat.photoUri
        val cleanPath = photoUri?.removePrefix("file://") ?: ""
        val isFileValid = if (cleanPath.isNotEmpty() && (cleanPath.startsWith("/") || photoUri?.startsWith("file://") == true)) {
            File(cleanPath).exists()
        } else {
            !photoUri.isNullOrEmpty()
        }

        if (isFileValid && !photoUri.isNullOrEmpty()) {
            try {
                val sigKey = if (cleanPath.startsWith("/")) {
                    val f = File(cleanPath)
                    if (f.exists()) "${stat.playerId}_${f.lastModified()}_${f.length()}" else "${stat.playerId}_${photoUri.hashCode()}"
                } else {
                    "${stat.playerId}_${photoUri.hashCode()}"
                }

                Glide.with(this)
                    .load(photoUri)
                    .signature(ObjectKey(sigKey))
                    .placeholder(R.drawable.ic_person_placeholder)
                    .error(R.drawable.ic_person_placeholder)
                    .into(imageView)
            } catch (_: Exception) {
                Glide.with(this).clear(imageView)
                imageView.setImageResource(R.drawable.ic_person_placeholder)
            }
        } else {
            Glide.with(this).clear(imageView)
            imageView.setImageResource(R.drawable.ic_person_placeholder)
        }
    }

    // --- HELPER METADATA & FORMATTING LOGIC ---
    private fun getSubtitleForStat(title: String): String {
        return when (title) {
            "Most Runs" -> "Highest cumulative runs scored"
            "Highest Score" -> "Top individual match scores"
            "Best Strike Rate" -> "Highest runs per 100 balls"
            "Most Sixes" -> "Maximum sixes hit"
            "Most Fours" -> "Maximum boundaries hit"
            "Most 80s" -> "Innings with 80+ runs"
            "Most 50s" -> "Half-centuries scored"
            "Most 30s" -> "Innings with 30+ runs"
            "Most Ducks" -> "Dismissals for zero"

            "Most Wickets" -> "Leading wicket takers"
            "Best Bowling Figure" -> "Top spell performances"
            "Best Bowling Average" -> "Runs conceded per wicket"
            "Best Economy" -> "Lowest runs conceded per over"
            "Most Hattricks" -> "3 wickets in 3 balls"
            "Most 2 Wicket Hauls" -> "2+ wickets in an innings"
            "Most 3 Wicket Hauls" -> "3+ wickets in an innings"
            "Most 5 Wicket Hauls" -> "5+ wickets in an innings"
            "Most 6s Conceded" -> "Sixes conceded by bowler"

            "Most Catches" -> "Top catches taken"
            "Most Stumpings" -> "Wicketkeeper stumpings"
            "Most Run Outs" -> "Direct hits & run outs"

            "Overall Rankings" -> "Top overall player standings"
            "Batting Rankings" -> "Top ranked batsmen"
            "Bowling Rankings" -> "Top ranked bowlers"
            else -> "Leaderboard & player standings"
        }
    }

    private fun getIconForStat(title: String): Int {
        return when (title) {
            "Most Runs", "Most Fours" -> R.drawable.ic_stat_bat_mono
            "Highest Score", "Most 80s", "Most 50s", "Most 30s" -> R.drawable.ic_stat_star
            "Best Strike Rate" -> R.drawable.ic_stat_zap
            "Most Sixes", "Most Hattricks" -> R.drawable.ic_stat_fire
            "Most Ducks" -> R.drawable.ic_stat_duck

            "Most Wickets", "Most 2 Wicket Hauls", "Most 3 Wicket Hauls", "Most 5 Wicket Hauls" -> R.drawable.ic_stat_ball
            "Best Bowling Figure", "Overall Rankings", "Batting Rankings", "Bowling Rankings" -> R.drawable.ic_trophy
            "Best Bowling Average", "Most 6s Conceded" -> R.drawable.ic_stat_target
            "Best Economy", "Most Catches", "Most Stumpings", "Most Run Outs" -> R.drawable.ic_stat_fielding
            else -> R.drawable.ic_trophy
        }
    }

    private data class CategoryStyle(
        val bg: Int,
        val stroke: Int,
        val iconTint: Int
    )

    private fun getCategoryStyle(title: String, isDark: Boolean): CategoryStyle {
        return when (title) {
            "Most Sixes", "Most Hattricks", "Best Strike Rate" -> if (isDark) {
                CategoryStyle(
                    bg = Color.parseColor("#26EF4444"),
                    stroke = Color.parseColor("#EF4444"),
                    iconTint = Color.parseColor("#FF5252")
                )
            } else {
                CategoryStyle(
                    bg = Color.parseColor("#FEE2E2"),
                    stroke = Color.parseColor("#EF4444"),
                    iconTint = Color.parseColor("#DC2626")
                )
            }
            "Most Runs", "Most Fours", "Highest Score", "Most 80s", "Most 50s", "Most 30s" -> if (isDark) {
                CategoryStyle(
                    bg = Color.parseColor("#26F97316"),
                    stroke = Color.parseColor("#F97316"),
                    iconTint = Color.parseColor("#FFA726")
                )
            } else {
                CategoryStyle(
                    bg = Color.parseColor("#FFEDD5"),
                    stroke = Color.parseColor("#F97316"),
                    iconTint = Color.parseColor("#C2410C")
                )
            }
            "Most Ducks" -> if (isDark) {
                CategoryStyle(
                    bg = Color.parseColor("#2664748B"),
                    stroke = Color.parseColor("#64748B"),
                    iconTint = Color.parseColor("#94A3B8")
                )
            } else {
                CategoryStyle(
                    bg = Color.parseColor("#F1F5F9"),
                    stroke = Color.parseColor("#94A3B8"),
                    iconTint = Color.parseColor("#475569")
                )
            }
            "Most Wickets", "Best Bowling Figure", "Most 2 Wicket Hauls",
            "Most 3 Wicket Hauls", "Most 5 Wicket Hauls", "Most 6s Conceded" -> if (isDark) {
                CategoryStyle(
                    bg = Color.parseColor("#26F87171"),
                    stroke = Color.parseColor("#F87171"),
                    iconTint = Color.parseColor("#FF6B6B")
                )
            } else {
                CategoryStyle(
                    bg = Color.parseColor("#FEE2E2"),
                    stroke = Color.parseColor("#F87171"),
                    iconTint = Color.parseColor("#B91C1C")
                )
            }
            "Best Economy", "Best Bowling Average" -> if (isDark) {
                CategoryStyle(
                    bg = Color.parseColor("#263B82F6"),
                    stroke = Color.parseColor("#3B82F6"),
                    iconTint = Color.parseColor("#60A5FA")
                )
            } else {
                CategoryStyle(
                    bg = Color.parseColor("#DBEAFE"),
                    stroke = Color.parseColor("#3B82F6"),
                    iconTint = Color.parseColor("#1D4ED8")
                )
            }
            "Most Catches", "Most Stumpings", "Most Run Outs" -> if (isDark) {
                CategoryStyle(
                    bg = Color.parseColor("#2622C55E"),
                    stroke = Color.parseColor("#22C55E"),
                    iconTint = Color.parseColor("#4ADE80")
                )
            } else {
                CategoryStyle(
                    bg = Color.parseColor("#DCFCE7"),
                    stroke = Color.parseColor("#22C55E"),
                    iconTint = Color.parseColor("#15803D")
                )
            }
            "Overall Rankings", "Batting Rankings", "Bowling Rankings" -> if (isDark) {
                CategoryStyle(
                    bg = Color.parseColor("#26F59E0B"),
                    stroke = Color.parseColor("#F59E0B"),
                    iconTint = Color.parseColor("#FCD34D")
                )
            } else {
                CategoryStyle(
                    bg = Color.parseColor("#FEF3C7"),
                    stroke = Color.parseColor("#F59E0B"),
                    iconTint = Color.parseColor("#B45309")
                )
            }
            else -> if (isDark) {
                CategoryStyle(
                    bg = Color.parseColor("#2614B8A6"),
                    stroke = Color.parseColor("#14B8A6"),
                    iconTint = Color.parseColor("#2DD4BF")
                )
            } else {
                CategoryStyle(
                    bg = Color.parseColor("#CCFBF1"),
                    stroke = Color.parseColor("#14B8A6"),
                    iconTint = Color.parseColor("#0D9488")
                )
            }
        }
    }

    private fun getUnitLabelForCategory(title: String?): String {
        return when (title) {
            "Most Runs", "Highest Score" -> "runs"
            "Best Strike Rate" -> "S/R"
            "Most Sixes" -> "sixes"
            "Most Fours" -> "fours"
            "Most 80s" -> "80s"
            "Most 50s" -> "50s"
            "Most 30s" -> "30s"
            "Most Ducks" -> "ducks"
            "Most Wickets" -> "wkts"
            "Best Bowling Figure" -> "fig"
            "Best Bowling Average" -> "avg"
            "Best Economy" -> "econ"
            "Most Hattricks" -> "hat-tricks"
            "Most 2 Wicket Hauls", "Most 3 Wicket Hauls", "Most 5 Wicket Hauls" -> "hauls"
            "Most 6s Conceded" -> "sixes"
            "Most Catches" -> "catches"
            "Most Stumpings" -> "stumpings"
            "Most Run Outs" -> "run outs"
            "Overall Rankings", "Batting Rankings", "Bowling Rankings" -> "pts"
            else -> "pts"
        }
    }

    private fun formatStatValue(total: Int, title: String?): String {
        return when (title) {
            "Best Economy", "Best Bowling Average", "Best Strike Rate" -> {
                val value = total / 100.0
                String.format(Locale.US, "%.2f", value)
            }
            "Best Bowling Figure" -> {
                val w = total / 100000
                val r = 99999 - (total % 100000)
                "$w/$r"
            }
            else -> {
                NumberFormat.getNumberInstance(Locale.US).format(total)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_PODIUM = 0
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_ROW = 2
    }
}

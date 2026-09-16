package com.example.scoring

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scoring.AppDatabase.Companion.getInstance
import com.example.scoring.RankingRegistry.applyPrestigeByName
import com.example.scoring.SecurityUtils.AuthCallback
import com.example.scoring.SecurityUtils.authenticate
import com.facebook.shimmer.ShimmerFrameLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MatchListFragment : Fragment() {
    private var filterType = 0 // 0: Live, 1: Completed, 2: In-Progress, 3: Abandoned
    private var recyclerView: RecyclerView? = null
    private var matchAdapter: MatchHistoryAdapter? = null
    private lateinit var viewModel: MatchListViewModel

    // Fast Scroller Views
    private var viewTrack: View? = null
    private var cardHandle: View? = null
    private var viewTouchArea: View? = null
    private var cardBubble: View? = null
    private var tvBubbleText: TextView? = null

    private val fastScrollHandler = Handler(Looper.getMainLooper())
    private var fadeRunnable: Runnable? = null

    sealed class MatchListItem {
        data class Header(val dateTitle: String) : MatchListItem()
        data class Match(val match: MatchEntity, val matchNumber: Int) : MatchListItem()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterType = arguments?.getInt("type") ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val v = inflater.inflate(R.layout.fragment_match_list, container, false)
            recyclerView = v.findViewById(R.id.rvMatchList)
            recyclerView?.layoutManager = LinearLayoutManager(context)
            v
        } catch (e: Exception) {
            e.printStackTrace()
            inflater.inflate(R.layout.fragment_match_list, container, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupFastScroller(view)

        viewModel = ViewModelProvider(this)[MatchListViewModel::class.java]
        
        val gId = GullySyncManager.getCurrentGullyId(requireContext()) ?: "local"
        viewModel.setParams(gId, filterType)
        
        viewModel.matches.observe(viewLifecycleOwner) { matches ->
            updateUI(matches)
        }
    }

    private fun setupFastScroller(v: View) {
        viewTrack = v.findViewById(R.id.viewFastScrollTrack)
        cardHandle = v.findViewById(R.id.cardFastScrollHandle)
        viewTouchArea = v.findViewById(R.id.viewFastScrollTouchArea)
        cardBubble = v.findViewById(R.id.cardFastScrollBubble)
        tvBubbleText = v.findViewById(R.id.tvFastScrollBubbleText)
        val rv = recyclerView ?: return

        fadeRunnable = Runnable {
            cardBubble?.animate()?.alpha(0f)?.setDuration(250)?.withEndAction {
                cardBubble?.visibility = View.GONE
                cardBubble?.alpha = 1f
            }?.start()
        }

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateFastScrollerPosition()
            }
        })

        viewTouchArea?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    fadeRunnable?.let { fastScrollHandler.removeCallbacks(it) }
                    cardBubble?.animate()?.cancel()
                    cardBubble?.visibility = View.VISIBLE
                    cardBubble?.alpha = 1f

                    val rvHeight = rv.height.toFloat()
                    if (rvHeight > 0) {
                        val touchY = event.y.coerceIn(0f, rvHeight)
                        val progress = (touchY / rvHeight).coerceIn(0f, 1f)
                        val count = matchAdapter?.itemCount ?: 0
                        if (count > 0) {
                            val targetPos = (progress * (count - 1)).toInt().coerceIn(0, count - 1)
                            (rv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(targetPos, 0)

                            val dateTitle = matchAdapter?.getDateTitleForPosition(targetPos)
                            if (!dateTitle.isNullOrEmpty()) {
                                tvBubbleText?.text = dateTitle
                            }
                        }

                        val topMargin = 16f * resources.displayMetrics.density
                        val bottomMargin = 16f * resources.displayMetrics.density
                        val handleHeight = cardHandle?.height?.toFloat() ?: 0f
                        val availableHeight = (rvHeight - topMargin - bottomMargin - handleHeight).coerceAtLeast(1f)
                        val handleY = topMargin + (progress * availableHeight)
                        cardHandle?.translationY = handleY
                        cardBubble?.translationY = handleY
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.performClick()
                    fadeRunnable?.let { fastScrollHandler.postDelayed(it, 1000) }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateFastScrollerPosition() {
        val count = matchAdapter?.itemCount ?: 0
        val rv = recyclerView ?: return
        if (count < 4) {
            viewTrack?.visibility = View.GONE
            cardHandle?.visibility = View.GONE
            viewTouchArea?.visibility = View.GONE
            cardBubble?.visibility = View.GONE
            return
        }

        viewTrack?.visibility = View.VISIBLE
        cardHandle?.visibility = View.VISIBLE
        viewTouchArea?.visibility = View.VISIBLE

        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible < 0) return

        val rvHeight = rv.height.toFloat()
        if (rvHeight <= 0) return

        val progress = firstVisible.toFloat() / (count - 1).coerceAtLeast(1).toFloat()
        val topMargin = 16f * resources.displayMetrics.density
        val bottomMargin = 16f * resources.displayMetrics.density
        val handleHeight = cardHandle?.height?.toFloat() ?: 0f
        val availableHeight = (rvHeight - topMargin - bottomMargin - handleHeight).coerceAtLeast(1f)
        val handleY = topMargin + (progress * availableHeight)

        cardHandle?.translationY = handleY
        cardBubble?.translationY = handleY

        val dateTitle = matchAdapter?.getDateTitleForPosition(firstVisible)
        if (!dateTitle.isNullOrEmpty()) {
            tvBubbleText?.text = dateTitle
        }
    }

    private fun getMatchTime(m: MatchEntity): Long {
        return if (m.firstInningsStartTime > 0) m.firstInningsStartTime else m.playedAt
    }

    private fun updateUI(finalMatches: List<MatchEntity>?) {
        try {
            if (finalMatches == null) return
            val v = view ?: return
            val sortedMatches = finalMatches.sortedWith { m1, m2 ->
                val t1 = getMatchTime(m1)
                val t2 = getMatchTime(m2)
                t2.compareTo(t1)
            }

            val shimmer = v.findViewById<ShimmerFrameLayout>(R.id.shimmerMatchList)
            shimmer?.let {
                it.stopShimmer()
                it.visibility = View.GONE
            }
            recyclerView?.visibility = View.VISIBLE
            val empty = v.findViewById<View>(R.id.layoutEmptyMatches)
            empty?.visibility = if (sortedMatches.isEmpty()) View.VISIBLE else View.GONE
            
            if (matchAdapter == null) {
                matchAdapter = MatchHistoryAdapter(sortedMatches)
                recyclerView?.adapter = matchAdapter
            } else {
                matchAdapter?.updateData(sortedMatches)
            }
            updateFastScrollerPosition()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
    }

    fun refresh() {
        val gId = GullySyncManager.getCurrentGullyId(context) ?: "local"
        viewModel.setParams(gId, filterType)
    }

    private inner class MatchHistoryAdapter(rawMatches: List<MatchEntity?>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<MatchListItem>()

        init {
            buildItems(rawMatches)
        }

        fun updateData(newMatches: List<MatchEntity?>) {
            buildItems(newMatches)
            notifyDataSetChanged()
        }

        private fun buildItems(rawMatches: List<MatchEntity?>) {
            items.clear()
            val cleanMatches = rawMatches.filterNotNull()
            val totalCount = cleanMatches.size
            var lastHeaderKey: String? = null

            // Pre-calculate count of matches per date header title
            val headerCounts = mutableMapOf<String, Int>()
            cleanMatches.forEach { m ->
                val titleKey = formatDateHeader(getMatchTime(m))
                headerCounts[titleKey] = (headerCounts[titleKey] ?: 0) + 1
            }

            cleanMatches.forEachIndexed { idx, m ->
                val time = getMatchTime(m)
                val headerKey = formatDateHeader(time)
                if (headerKey != lastHeaderKey) {
                    val count = headerCounts[headerKey] ?: 1
                    val headerWithCount = "$headerKey($count)"
                    items.add(MatchListItem.Header(headerWithCount))
                    lastHeaderKey = headerKey
                }
                val matchNumber = totalCount - idx
                items.add(MatchListItem.Match(m, matchNumber))
            }
        }

        fun getDateTitleForPosition(position: Int): String {
            if (position !in items.indices) return "MATCHES"
            for (i in position downTo 0) {
                val item = items[i]
                if (item is MatchListItem.Header) {
                    return item.dateTitle
                }
            }
            return "MATCHES"
        }

        override fun getItemViewType(position: Int): Int {
            return if (items[position] is MatchListItem.Header) TYPE_HEADER else TYPE_MATCH
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_match_date_header, parent, false)
                HeaderHolder(v)
            } else {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_match_history, parent, false)
                MatchHolder(v)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is MatchListItem.Header -> {
                    (holder as HeaderHolder).tvTitle.text = item.dateTitle
                }
                is MatchListItem.Match -> {
                    bindMatchHolder(holder as MatchHolder, item.match, item.matchNumber)
                }
            }
        }

        private fun bindMatchHolder(holder: MatchHolder, m: MatchEntity, matchNum: Int) {
            val displayDate = getMatchTime(m)
            val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
            holder.tvDate.text = sdf.format(Date(displayDate))

            val team1 = m.firstInningsTeam ?: m.teamAName
            val team2 = m.secondInningsTeam ?: (if (team1 == m.teamAName) m.teamBName else m.teamAName)

            holder.tvTeamA.text = team1
            holder.tvTeamB.text = team2
            
            var i1OversStr = "0.0"
            try {
                val i1Legal = m.ballsJson1?.filterNotNull()?.count { it?.type == BallType.NORMAL || it?.type == BallType.BYE || it?.type == BallType.LEG_BYE } ?: 0
                i1OversStr = "${i1Legal / 6}.${i1Legal % 6}"
            } catch (e: Exception) {
                Log.e("MATCH_LIST", "Error calculating i1 overs: ${e.message}")
            }
            
            val maxOvers = m.revisedOvers ?: m.totalOvers
            holder.tvScoreA.text = "${m.firstInningsRuns}/${m.firstInningsWickets}($i1OversStr/$maxOvers)"
            
            if (m.secondInningsTeam != null) {
                var i2OversStr = "0.0"
                try {
                    val i2Legal = m.ballsJson2?.filterNotNull()?.count { it?.type == BallType.NORMAL || it?.type == BallType.BYE || it?.type == BallType.LEG_BYE } ?: 0
                    i2OversStr = "${i2Legal / 6}.${i2Legal % 6}"
                } catch (e: Exception) {
                    Log.e("MATCH_LIST", "Error calculating i2 overs: ${e.message}")
                }
                holder.tvScoreB.text = "${m.secondInningsRuns}/${m.secondInningsWickets}($i2OversStr/$maxOvers)"
            } else {
                holder.tvScoreB.text = "-"
            }

            // --- LIVE STATUS WITH HEARTBEAT CHECK ---
            val now = System.currentTimeMillis()
            val diff = now - m.lastScorerPulse
            val isPulseActive = diff in -60000..120000 // Allow 1 min future skew
            val isActuallyLive = m.isLive && isPulseActive && !m.isFinished && !m.isAbandoned

            if (isActuallyLive) {
                holder.layoutLive?.visibility = View.VISIBLE
                val tvLive = holder.layoutLive?.findViewById<TextView>(R.id.tvLiveLabel)
                val dot = holder.liveDot
                val context = holder.itemView.context
                
                tvLive?.text = "LIVE"
                tvLive?.setTextColor(ContextCompat.getColor(context, R.color.card_red))
                dot?.setBackgroundResource(R.drawable.circle_bg_red)
                dot?.visibility = View.VISIBLE

                dot?.clearAnimation()
                val anim = AlphaAnimation(1.0f, 0.2f).apply {
                    duration = 800
                    repeatMode = Animation.REVERSE
                    repeatCount = Animation.INFINITE
                }
                dot?.startAnimation(anim)
            } else if (!m.isFinished && !m.isAbandoned) {
                // Show "PAUSED" if it's not finished but not live
                holder.layoutLive?.visibility = View.VISIBLE
                val tvLive = holder.layoutLive?.findViewById<TextView>(R.id.tvLiveLabel)
                val dot = holder.liveDot
                val context = holder.itemView.context

                tvLive?.text = "PAUSED"
                tvLive?.setTextColor(Color.parseColor("#FBC02D"))
                dot?.setBackgroundResource(R.drawable.circle_bg_yellow)
                dot?.visibility = View.VISIBLE
                dot?.clearAnimation()
            } else {
                holder.liveDot?.clearAnimation()
                holder.layoutLive?.visibility = View.GONE
            }
            
            val finalResult = if (!m.isFinished && !m.isAbandoned) {
                val res = m.result ?: (if (isActuallyLive) "LIVE" else "PAUSED")
                if (res == "IN-PROGRESS" || res == "LIVE") {
                    if (isActuallyLive) "LIVE" else "IN-PROGRESS"
                } else res
            } else if (m.isAbandoned) {
                "MATCH ABANDONED"
            } else {
                val res = m.result ?: "MATCH COMPLETED"
                if (res == "IN-PROGRESS" || res == "MATCH FINISHED" || res == "MATCH COMPLETED") {
                    calculateFallbackResult(m)
                } else {
                    res
                }
            }
            holder.tvResult.text = finalResult
            
            val context = holder.itemView.context
            if (finalResult == "IN-PROGRESS" || finalResult == "PAUSED") {
                holder.tvResult.setTextColor(ThemeManager.getSeedColor(context))
            } else if (finalResult == "LIVE") {
                holder.tvResult.setTextColor(ContextCompat.getColor(context, R.color.card_red))
            } else {
                holder.tvResult.setTextColor(ThemeManager.getThemeColor(context, com.google.android.material.R.attr.colorOnSurface))
            }

            if (!m.isFinished && !m.isAbandoned && !isActuallyLive) {
                holder.btnResume?.visibility = View.VISIBLE
                holder.btnResume?.setOnClickListener { v ->
                    AppDatabase.ioExecutor.execute {
                        val db = getInstance(v.context)
                        val isDraft = db.draftDao().getDraftById(m.id) != null
                        if (!isDraft) {
                            val entity = db.matchDao().getMatchById(m.id)
                            if (entity != null) {
                                entity.isLive = true
                                entity.lastScorerPulse = System.currentTimeMillis()
                                entity.lastSyncedAt = System.currentTimeMillis()
                                db.matchDao().insertMatch(entity)
                                val gId = GullySyncManager.getCurrentGullyId(v.context)
                                if (gId != null) {
                                    GullySyncManager.syncMatchToCloud(gId, entity)
                                }
                            }
                        }
                        v.post {
                            if (isDraft) {
                                val intent = Intent(v.context, TossActivity::class.java)
                                intent.putExtra("draftId", m.id)
                                v.context.startActivity(intent)
                            } else {
                                val intent = Intent(v.context, MainActivity::class.java)
                                intent.putExtra("matchId", m.id)
                                intent.putExtra("resume", true)
                                intent.putExtra("isViewOnly", false)
                                v.context.startActivity(intent)
                            }
                        }
                    }
                }
            } else {
                holder.btnResume?.visibility = View.GONE
            }

            holder.btnMenu?.setOnClickListener { v ->
                val matchId = m.id
                val isFinished = m.isFinished
                val isAbandoned = m.isAbandoned
                
                v.post {
                    val popup = PopupMenu(v.context, v)
                    popup.menu.add("Start Match with Same Setup")
                    if (isFinished || isAbandoned) {
                        popup.menu.add("Recalculate Player Stats")
                    }
                    popup.setOnMenuItemClickListener { item ->
                        when (item.title.toString()) {
                            "Start Match with Same Setup" -> {
                                startSameSetup(m)
                                true
                            }
                            "Delete Match" -> {
                                holder.itemView.performLongClick()
                                true
                            }
                            "Recalculate Player Stats" -> {
                                val ctx = v.context
                                StatsRecalculator.recalculateAndSave(ctx, matchId) {
                                    activity?.runOnUiThread {
                                        Toast.makeText(ctx, "Stats Recalculated!", Toast.LENGTH_SHORT).show()
                                        refresh()
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
            }

            val isCompletedTab = filterType == 1
            if (isCompletedTab) {
                holder.tvMatchNumber.visibility = View.VISIBLE
                holder.tvMatchNumber.text = "Match #$matchNum"
            } else {
                holder.tvMatchNumber.visibility = View.GONE
            }

            if (isCompletedTab && m.isFinished && !m.isAbandoned && m.playerOfTheMatchName != null && m.playerOfTheMatchName != "TBD") {
                holder.tvPOTM.text = "POTM - ${m.playerOfTheMatchName}"
                holder.tvPOTM.visibility = View.VISIBLE
                applyPrestigeByName(m.playerOfTheMatchName, holder.tvPOTM)
            } else {
                holder.tvPOTM.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(activity, MatchDetailsActivity::class.java)
                intent.putExtra("matchId", m.id)
                startActivity(intent)
            }

            holder.itemView.setOnLongClickListener {
                val c = context ?: return@setOnLongClickListener true
                val act = activity ?: return@setOnLongClickListener true
                val gId = GullySyncManager.getCurrentGullyId(c)

                val executeDelete = {
                    AppDatabase.ioExecutor.execute {
                        val dbDel = getInstance(c)
                        val mMatch = dbDel.matchDao().getMatchById(m.id)
                        if (mMatch != null) dbDel.matchDao().deleteMatch(mMatch)
                        val mDraft = dbDel.draftDao().getDraftById(m.id)
                        if (mDraft != null) dbDel.draftDao().deleteDraft(mDraft)
                        
                        if (gId != null) {
                            GullySyncManager.deleteMatchFromCloud(gId, m.id)
                            GullySyncManager.deleteDraftFromCloud(gId, m.id)
                        }
                        activity?.runOnUiThread { refresh() }
                    }
                }

                if (gId != null) {
                    GullyAdminManager.verifyAdminPinAndExecute(act, gId, "Delete Match") {
                        executeDelete()
                    }
                } else {
                    val dialog = ThemeManager.createDynamicBuilder(c)
                        .setTitle("Delete Match")
                        .setMessage("Do you want to permanently delete this match record?")
                        .setPositiveButton("Delete") { d, w ->
                            authenticate(
                                act,
                                "Confirm Deletion",
                                "Provide security to delete match history",
                                object : AuthCallback {
                                    override fun onSuccess() {
                                        executeDelete()
                                    }
                                    override fun onFailure(error: String?) {
                                        context?.let {
                                            Toast.makeText(it, "Failed: $error", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                })
                        }
                        .setNegativeButton("Cancel", null)
                        .create()
                    dialog.show()
                    ThemeManager.colorizeDialog(dialog)
                }
                true
            }
        }

        override fun getItemCount(): Int = items.size

        private fun calculateFallbackResult(m: MatchEntity): String {
            val i1Team = m.firstInningsTeam ?: m.teamAName ?: "Team 1"
            val i1Runs = m.firstInningsRuns
            val i2Team = m.secondInningsTeam
            val i2Runs = m.secondInningsRuns
            
            if (i2Team == null) {
                return "$i1Team scored $i1Runs/${m.firstInningsWickets}".uppercase()
            }
            
            val target = m.revisedTarget ?: (i1Runs + 1)
            
            return when {
                i2Runs >= target -> {
                    val playerCnt = if (i2Team == m.teamAName) m.teamAPlayerCount else m.teamBPlayerCount
                    val maxW = if (m.ruleEveryPlayerBats) playerCnt else (playerCnt - 1).coerceAtLeast(1)
                    val wicketsLeft = (maxW - m.secondInningsWickets).coerceAtLeast(0)
                    "$i2Team WON BY $wicketsLeft WICKETS".uppercase()
                }
                i2Runs == (target - 1) -> "MATCH TIED"
                else -> {
                    val runsDiff = (target - 1) - i2Runs
                    "$i1Team WON BY $runsDiff RUNS".uppercase()
                }
            }
        }

        private fun startSameSetup(m: MatchEntity) {
            val ctx = context ?: return
            AppDatabase.ioExecutor.execute {
                val db = getInstance(ctx)
                
                val teamANames = ArrayList<String?>()
                val teamBNames = ArrayList<String?>()
                
                val teamAPhotos = ArrayList<String?>()
                val teamBPhotos = ArrayList<String?>()
                val teamAJerseys = ArrayList<String?>()
                val teamBJerseys = ArrayList<String?>()
                val teamAIds = ArrayList<String?>()
                val teamBIds = ArrayList<String?>()

                fun populateDetails(names: List<String?>, photos: ArrayList<String?>, jerseys: ArrayList<String?>, ids: ArrayList<String?>, targetNames: ArrayList<String?>) {
                    names.forEach { name ->
                        if (name == null) return@forEach
                        targetNames.add(name)
                        val pId = m.nameToIdMap?.get(name)
                        val photo = m.photoMap?.get(name)
                        val pe = pId?.let { db.playerDao().getPlayerById(it) }
                        
                        ids.add(pId)
                        val finalPhoto = pe?.photoUri ?: photo
                        photos.add(finalPhoto)
                        jerseys.add(pe?.jerseyNumber ?: "0")
                    }
                }

                m.teamANames?.let { populateDetails(it, teamAPhotos, teamAJerseys, teamAIds, teamANames) }
                m.teamBNames?.let { populateDetails(it, teamBPhotos, teamBJerseys, teamBIds, teamBNames) }

                activity?.runOnUiThread {
                    val intent = Intent(ctx, SetupActivity::class.java).apply {
                        putExtra("teamA", m.teamAName)
                        putExtra("teamB", m.teamBName)
                        putExtra("overs", m.totalOvers)
                        putExtra("playerCountA", m.teamAPlayerCount)
                        putExtra("playerCountB", m.teamBPlayerCount)
                        putExtra("ballType", m.ballType)
                        putExtra("venue", m.venue)
                        putExtra("ruleRunsOnWide", m.ruleRunsOnWide)
                        putExtra("ruleFreeHit", m.ruleFreeHit)
                        putExtra("ruleRunsOnBye", m.ruleRunsOnBye)
                        putExtra("ruleOverthrow", m.ruleOverthrow)
                        putExtra("ruleEveryPlayerBats", m.ruleEveryPlayerBats)
                        putExtra("isSharedOver", m.isSharedOver)

                        putStringArrayListExtra("teamANames", teamANames)
                        putStringArrayListExtra("teamBNames", teamBNames)
                        putStringArrayListExtra("teamAPhotos", teamAPhotos)
                        putStringArrayListExtra("teamBPhotos", teamBPhotos)
                        putStringArrayListExtra("teamAJerseys", teamAJerseys)
                        putStringArrayListExtra("teamBJerseys", teamBJerseys)
                        putStringArrayListExtra("teamAIds", teamAIds)
                        putStringArrayListExtra("teamBIds", teamBIds)
                    }
                    ctx.startActivity(intent)
                }
            }
        }
    }

    inner class HeaderHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvDateHeaderTitle)
    }

    inner class MatchHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvDate: TextView = v.findViewById(R.id.tvMatchDate)
        val tvMatchNumber: TextView = v.findViewById(R.id.tvMatchNumber)
        val layoutLive: View? = v.findViewById(R.id.layoutLiveIndicator)
        val liveDot: View? = v.findViewById(R.id.viewLiveDot)
        val tvTeamA: TextView = v.findViewById(R.id.tvTeamA)
        val tvTeamB: TextView = v.findViewById(R.id.tvTeamB)
        val tvScoreA: TextView = v.findViewById(R.id.tvScoreA)
        val tvScoreB: TextView = v.findViewById(R.id.tvScoreB)
        val tvResult: TextView = v.findViewById(R.id.tvMatchResult)
        val tvPOTM: TextView = v.findViewById(R.id.tvPOTM)
        val btnResume: View? = v.findViewById(R.id.btnResumeMatch)
        val btnMenu: View? = v.findViewById(R.id.btnMatchMenu)
    }

    private fun formatDateHeader(millis: Long): String {
        if (millis <= 0) return "OTHER MATCHES"

        val matchCal = Calendar.getInstance().apply { timeInMillis = millis }
        val todayCal = Calendar.getInstance()
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(matchCal, todayCal) -> "TODAY"
            isSameDay(matchCal, yesterdayCal) -> "YESTERDAY"
            else -> {
                val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.US)
                sdf.format(Date(millis)).uppercase(Locale.US)
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_MATCH = 1

        fun newInstance(type: Int): MatchListFragment {
            val fragment = MatchListFragment()
            val args = Bundle().apply {
                putInt("type", type)
            }
            fragment.arguments = args
            return fragment
        }
    }
}

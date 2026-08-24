package com.example.scoring

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.example.scoring.ThemeManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scoring.AppDatabase.Companion.getInstance
import com.example.scoring.RankingRegistry.applyPrestigeByName
import com.example.scoring.SecurityUtils.AuthCallback
import com.example.scoring.SecurityUtils.authenticate
import com.facebook.shimmer.ShimmerFrameLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.ViewModelProvider

class MatchListFragment : Fragment() {
    private var filterType = 0 // 0: Live, 1: Completed, 2: In-Progress, 3: Abandoned
    private var recyclerView: RecyclerView? = null
    private var matchAdapter: MatchHistoryAdapter? = null
    private lateinit var viewModel: MatchListViewModel

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
        
        viewModel = ViewModelProvider(this)[MatchListViewModel::class.java]
        
        val gId = GullySyncManager.getCurrentGullyId(requireContext()) ?: "local"
        viewModel.setParams(gId, filterType)
        
        viewModel.matches.observe(viewLifecycleOwner) { matches ->
            updateUI(matches)
        }
    }

    private fun updateUI(finalMatches: List<MatchEntity>?) {
        try {
            if (finalMatches == null) return
            val v = view ?: return
            val sortedMatches = finalMatches.sortedWith { m1, m2 ->
                val t1 = if (m1.playedAt > 0) m1.playedAt else m1.firstInningsStartTime
                val t2 = if (m2.playedAt > 0) m2.playedAt else m2.firstInningsStartTime
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
                matchAdapter = MatchHistoryAdapter(sortedMatches.toMutableList())
                recyclerView?.adapter = matchAdapter
            } else {
                matchAdapter?.updateData(sortedMatches)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        // No need to call refresh() anymore as we are observing LiveData
    }

    fun refresh() {
        // Kept for backward compatibility if any other fragment calls it
        val gId = GullySyncManager.getCurrentGullyId(context) ?: "local"
        viewModel.setParams(gId, filterType)
    }

    private inner class MatchHistoryAdapter(private var matches: MutableList<MatchEntity?>) :
        RecyclerView.Adapter<MatchHistoryAdapter.Holder>() {
        
        fun updateData(newMatches: List<MatchEntity?>) {
            this.matches.clear()
            this.matches.addAll(newMatches)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_match_history, parent, false)
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val m = matches[position] ?: return

            val displayDate = if (m.playedAt > 0) m.playedAt else m.firstInningsStartTime
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
                tvLive?.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.card_red))
                dot?.setBackgroundResource(R.drawable.circle_bg_red)
                dot?.visibility = View.VISIBLE

                dot?.clearAnimation()
                val anim = android.view.animation.AlphaAnimation(1.0f, 0.2f).apply {
                    duration = 800
                    repeatMode = android.view.animation.Animation.REVERSE
                    repeatCount = android.view.animation.Animation.INFINITE
                }
                dot?.startAnimation(anim)
            } else if (!m.isFinished && !m.isAbandoned) {
                // Show "PAUSED" if it's not finished but not live
                holder.layoutLive?.visibility = View.VISIBLE
                val tvLive = holder.layoutLive?.findViewById<TextView>(R.id.tvLiveLabel)
                val dot = holder.liveDot
                val context = holder.itemView.context

                tvLive?.text = "PAUSED"
                tvLive?.setTextColor(android.graphics.Color.parseColor("#FBC02D"))
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
                holder.tvResult.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.card_red))
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
                val matchNum = matches.size - position
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
                val dialog = ThemeManager.createDynamicBuilder(c)
                    .setTitle("Delete Match")
                    .setMessage("Do you want to permanently delete this match record?")
                    .setPositiveButton("Delete") { d, w ->
                        val act = activity ?: return@setPositiveButton
                        authenticate(
                            act,
                            "Confirm Deletion",
                            "Provide security to delete match history",
                            object : AuthCallback {
                                override fun onSuccess() {
                                    AppDatabase.ioExecutor.execute {
                                        val dbDel = getInstance(c)
                                        val mMatch = dbDel.matchDao().getMatchById(m.id)
                                        if (mMatch != null) dbDel.matchDao().deleteMatch(mMatch)
                                        val mDraft = dbDel.draftDao().getDraftById(m.id)
                                        if (mDraft != null) dbDel.draftDao().deleteDraft(mDraft)
                                        
                                        val gId = GullySyncManager.getCurrentGullyId(c)
                                        if (gId != null) {
                                            GullySyncManager.deleteMatchFromCloud(gId, m.id)
                                            GullySyncManager.deleteDraftFromCloud(gId, m.id)
                                        }
                                        activity?.runOnUiThread { refresh() }
                                    }
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
                true
            }
        }

        override fun getItemCount(): Int = matches.size

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

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
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

        private fun startSameSetup(m: MatchEntity) {
            val ctx = context ?: return
            AppDatabase.ioExecutor.execute {
                val db = getInstance(ctx)
                
                // Prioritize direct lists if available
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
                        // Jersey and latest photo require a DB lookup
                        val pe = pId?.let { db.playerDao().getPlayerById(it) }
                        
                        ids.add(pId)
                        val finalPhoto = pe?.photoUri ?: photo
                        photos.add(finalPhoto)
                        jerseys.add(pe?.jerseyNumber ?: "0")
                    }
                }

                if (!m.teamANames.isNullOrEmpty() || !m.teamBNames.isNullOrEmpty()) {
                    populateDetails(m.teamANames ?: emptyList(), teamAPhotos, teamAJerseys, teamAIds, teamANames)
                    populateDetails(m.teamBNames ?: emptyList(), teamBPhotos, teamBJerseys, teamBIds, teamBNames)
                } else {
                    // Fallback for older matches without direct lists
                    val stats = db.statsDao().getStatsByMatch(m.id)?.filterNotNull()
                    stats?.forEach { s ->
                        if (s == null) return@forEach
                        val pe = db.playerDao().getPlayerById(s.playerId)
                        if (s.teamName == m.teamAName) {
                            teamANames.add(s.playerName)
                            teamAPhotos.add(pe?.photoUri)
                            teamAJerseys.add(pe?.jerseyNumber ?: "0")
                            teamAIds.add(s.playerId)
                        } else {
                            teamBNames.add(s.playerName)
                            teamBPhotos.add(pe?.photoUri)
                            teamBJerseys.add(pe?.jerseyNumber ?: "0")
                            teamBIds.add(s.playerId)
                        }
                    }
                }

                activity?.runOnUiThread {
                    val intent = Intent(activity, SetupActivity::class.java)
                    intent.putExtra("cloneMatch", true)
                    intent.putExtra("venue", m.venue)
                    intent.putExtra("teamAName", m.teamAName)
                    intent.putExtra("teamBName", m.teamBName)
                    intent.putExtra("overs", m.totalOvers)
                    intent.putExtra("ballType", m.ballType ?: "Stumper") // Fallback
                    intent.putExtra("ruleRunsOnWide", m.ruleRunsOnWide)
                    intent.putExtra("ruleFreeHit", m.ruleFreeHit)
                    intent.putExtra("ruleRunsOnBye", m.ruleRunsOnBye)
                    intent.putExtra("ruleOverthrow", m.ruleOverthrow)
                    intent.putStringArrayListExtra("teamANames", teamANames)
                    intent.putStringArrayListExtra("teamBNames", teamBNames)
                    intent.putStringArrayListExtra("teamAPhotos", teamAPhotos)
                    intent.putStringArrayListExtra("teamBPhotos", teamBPhotos)
                    intent.putStringArrayListExtra("teamAJerseys", teamAJerseys)
                    intent.putStringArrayListExtra("teamBJerseys", teamBJerseys)
                    intent.putStringArrayListExtra("teamAIds", teamAIds)
                    intent.putStringArrayListExtra("teamBIds", teamBIds)
                    startActivity(intent)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(type: Int): MatchListFragment {
            val f = MatchListFragment()
            val b = Bundle()
            b.putInt("type", type)
            f.arguments = b
            return f
        }
    }
}

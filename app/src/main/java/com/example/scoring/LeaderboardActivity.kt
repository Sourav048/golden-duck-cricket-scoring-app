package com.example.scoring

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.scoring.AppDatabase.Companion.getInstance
import com.example.scoring.RankingRegistry.applyPrestige
import com.google.android.material.imageview.ShapeableImageView
import java.util.Locale

class LeaderboardActivity : BaseActivity() {
    private var type: String? = null
    private var recyclerView: RecyclerView? = null
    private var adapter: LeaderboardAdapter? = null
    private var db: AppDatabase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats_leaderboard)

        db = getInstance(this)
        type = intent.getStringExtra("type")

        val tvTitle = findViewById<TextView>(R.id.tvLeaderboardTitle)
        tvTitle.text = type

        recyclerView = findViewById(R.id.rvLeaderboard)
        recyclerView?.layoutManager = LinearLayoutManager(this)

        RankingRegistry.refresh(this, object : RankingRegistry.OnRankingsLoaded {
            override fun onLoaded() {
                loadData()
            }
        })
    }

    private fun loadData() {
        AppDatabase.ioExecutor.execute {
            val stats: List<PlayerTotalStat?>? = when (type) {
                "Most Runs" -> db?.statsDao()?.getMostRuns()
                "Best Strike Rate" -> db?.statsDao()?.getBestStrikeRate()
                "Most Sixes" -> db?.statsDao()?.getMostSixes()
                "Most Fours" -> db?.statsDao()?.getMostFours()
                "Most 80s" -> db?.statsDao()?.getMostEighties()
                "Highest Score" -> db?.statsDao()?.getHighestScores()
                "Most 50s" -> db?.statsDao()?.getMostFifties()
                "Most 30s" -> db?.statsDao()?.getMostThirties()
                "Most Ducks" -> db?.statsDao()?.getMostDucks()
                "Most Wickets" -> db?.statsDao()?.getMostWickets()
                "Best Bowling Figure" -> db?.statsDao()?.getBestBowlingInnings()
                "Best Bowling Average" -> db?.statsDao()?.getBestBowlingAverage()
                "Best Economy" -> db?.statsDao()?.getBestEconomy()
                "Most Hattricks" -> db?.statsDao()?.getMostHattricks()
                "Most 5 Wicket Hauls" -> db?.statsDao()?.getMostFiveWicketHauls()
                "Most 3 Wicket Hauls" -> db?.statsDao()?.getMostThreeWicketHauls()
                "Most 2 Wicket Hauls" -> db?.statsDao()?.getMostTwoWicketHauls()
                "Most 6s Conceded" -> db?.statsDao()?.getMostSixesConceded()
                "Most Catches" -> db?.statsDao()?.getMostCatches()
                "Most Stumpings" -> db?.statsDao()?.getMostStumpings()
                "Most Run Outs" -> db?.statsDao()?.getMostRunOuts()
                "Overall Rankings" -> db?.statsDao()?.getOverallRankings()
                "Batting Rankings" -> db?.statsDao()?.getBattingRankings()
                "Bowling Rankings" -> db?.statsDao()?.getBowlingRankings()
                else -> ArrayList()
            }

            val finalStats: MutableList<PlayerTotalStat> = stats?.filterNotNull()?.toMutableList() ?: ArrayList()
            runOnUiThread {
                adapter = LeaderboardAdapter(finalStats, type)
                recyclerView?.adapter = adapter
            }
        }
    }

    private inner class LeaderboardAdapter(
        private val stats: MutableList<PlayerTotalStat>,
        private val type: String?
    ) : RecyclerView.Adapter<LeaderboardAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_stat_row, parent, false)
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val s = stats[position]
            holder.rank.text = (position + 1).toString()
            holder.name.text = s.playerName

            applyPrestige(s.playerId, holder.name, holder.photo)

            if (!s.photoUri.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(s.photoUri)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(holder.photo)
            } else {
                holder.photo.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            when {
                type == "Best Economy" || type == "Best Bowling Average" || type == "Best Strike Rate" -> {
                    val value = s.total / 100.0
                    holder.value.text = String.format(Locale.US, "%.2f", value)
                }
                type == "Best Bowling Figure" -> {
                    var w = s.total / 1000
                    val rem = s.total % 1000
                    val r = if (rem == 0) 0 else 1000 - rem
                    if (rem == 0) w--
                    holder.value.text = "$w/$r"
                }
                else -> {
                    holder.value.text = s.total.toString()
                }
            }
            
            ThemeManager.colorize(holder.itemView)

            holder.itemView.setOnClickListener { v ->
                val intent = Intent(v.context, PlayerDetailsActivity::class.java)
                intent.putExtra("playerId", s.playerId)
                v.context.startActivity(intent)
            }
        }

        override fun getItemCount(): Int = stats.size

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val rank: TextView = v.findViewById(R.id.tvStatRank)
            val photo: ShapeableImageView = v.findViewById(R.id.ivStatPhoto)
            val name: TextView = v.findViewById(R.id.tvStatPlayerName)
            val value: TextView = v.findViewById(R.id.tvStatValue)
        }
    }
}

package com.example.scoring

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

class StatsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            val view = inflater.inflate(R.layout.fragment_stats_list, container, false)
            val content = view.findViewById<LinearLayout>(R.id.statsContainer)

            val category = arguments?.getString(ARG_CATEGORY, "") ?: ""

            when (category) {
                "Batting" -> {
                    addStatCard(content, "Most Runs")
                    addStatCard(content, "Highest Score")
                    addStatCard(content, "Best Strike Rate")
                    addStatCard(content, "Most Sixes")
                    addStatCard(content, "Most Fours")
                    addStatCard(content, "Most 80s")
                    addStatCard(content, "Most 50s")
                    addStatCard(content, "Most 30s")
                    addStatCard(content, "Most Ducks")
                }
                "Bowling" -> {
                    addStatCard(content, "Most Wickets")
                    addStatCard(content, "Best Bowling Figure")
                    addStatCard(content, "Best Bowling Average")
                    addStatCard(content, "Best Economy")
                    addStatCard(content, "Most Hattricks")
                    addStatCard(content, "Most 2 Wicket Hauls")
                    addStatCard(content, "Most 3 Wicket Hauls")
                    addStatCard(content, "Most 5 Wicket Hauls")
                    addStatCard(content, "Most 6s Conceded")
                }
                "Fielding" -> {
                    addStatCard(content, "Most Catches")
                    addStatCard(content, "Most Stumpings")
                    addStatCard(content, "Most Run Outs")
                }
                "Ranking" -> {
                    addStatCard(content, "Overall Rankings")
                    addStatCard(content, "Batting Rankings")
                    addStatCard(content, "Bowling Rankings")
                }
            }
            view
        } catch (e: Exception) {
            e.printStackTrace()
            inflater.inflate(R.layout.fragment_stats_list, container, false)
        }
    }

    private fun addStatCard(container: LinearLayout, title: String) {
        try {
            val inflater = LayoutInflater.from(container.context)
            val card = inflater.inflate(
                R.layout.item_stat_entry_card,
                container,
                false
            ) as MaterialCardView

            card.findViewById<TextView>(R.id.tvStatTitle)?.text = title
            card.findViewById<TextView>(R.id.tvStatSubtitle)?.text = getSubtitleForStat(title)

            val ivIcon = card.findViewById<ImageView>(R.id.ivStatIcon)
            ivIcon?.setImageResource(getIconForStat(title))

            card.setOnClickListener {
                val intent = Intent(activity, LeaderboardActivity::class.java)
                intent.putExtra("type", title)
                startActivity(intent)
            }
            ThemeManager.colorize(card)
            container.addView(card)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
            "Best Bowling Average" -> "Runs per wicket taken"
            "Best Economy" -> "Lowest runs per over"
            "Most Hattricks" -> "3 wickets in 3 consecutive balls"
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
            else -> "View leaderboard & rankings"
        }
    }

    private fun getIconForStat(title: String): Int {
        return when (title) {
            "Most Runs" -> R.drawable.ic_stat_bat
            "Highest Score" -> R.drawable.ic_stat_star
            "Best Strike Rate" -> R.drawable.ic_stat_zap
            "Most Sixes" -> R.drawable.ic_stat_fire
            "Most Fours" -> R.drawable.ic_stat_bat
            "Most 80s", "Most 50s", "Most 30s" -> R.drawable.ic_stat_star
            "Most Ducks" -> R.drawable.ic_stat_duck

            "Most Wickets" -> R.drawable.ic_stat_ball
            "Best Bowling Figure" -> R.drawable.ic_trophy
            "Best Bowling Average" -> R.drawable.ic_stat_target
            "Best Economy" -> R.drawable.ic_stat_fielding
            "Most Hattricks" -> R.drawable.ic_stat_fire
            "Most 2 Wicket Hauls", "Most 3 Wicket Hauls", "Most 5 Wicket Hauls" -> R.drawable.ic_stat_ball
            "Most 6s Conceded" -> R.drawable.ic_stat_target

            "Most Catches", "Most Stumpings", "Most Run Outs" -> R.drawable.ic_stat_fielding

            "Overall Rankings", "Batting Rankings", "Bowling Rankings" -> R.drawable.ic_trophy
            else -> R.drawable.ic_trophy
        }
    }

    companion object {
        private const val ARG_CATEGORY = "category"

        fun newInstance(category: String): StatsFragment {
            val fragment = StatsFragment()
            val args = Bundle()
            args.putString(ARG_CATEGORY, category)
            fragment.arguments = args
            return fragment
        }
    }
}
package com.example.scoring

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            card.findViewById<TextView>(R.id.tvStatTitle).text = title
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

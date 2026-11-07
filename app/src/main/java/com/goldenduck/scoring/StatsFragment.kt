package com.goldenduck.scoring

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

class StatsFragment : Fragment() {

    private data class StatStyle(
        val bg: Int,
        val stroke: Int,
        val iconTint: Int
    )

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
                    addShowcaseCard(content, "Most Runs", "Batting")
                    addSectionHeader(content, "POWER & BOUNDARIES")
                    addTileRow(content, "Highest Score", "Best Strike Rate")
                    addTileRow(content, "Most Sixes", "Most Fours")
                    addSectionHeader(content, "INNINGS MILESTONES")
                    addTileRow(content, "Most 80s", "Most 50s")
                    addTileRow(content, "Most 30s", "Most Ducks")
                }
                "Bowling" -> {
                    addShowcaseCard(content, "Most Wickets", "Bowling")
                    addSectionHeader(content, "MATCH SPELLS & CONTROL")
                    addTileRow(content, "Best Bowling Figure", "Best Bowling Average")
                    addTileRow(content, "Best Economy", "Most Hattricks")
                    addSectionHeader(content, "HAULS & CONCEDED")
                    addTileRow(content, "Most 2 Wicket Hauls", "Most 3 Wicket Hauls")
                    addTileRow(content, "Most 5 Wicket Hauls", "Most 6s Conceded")
                }
                "Fielding" -> {
                    addShowcaseCard(content, "Most Catches", "Fielding")
                    addSectionHeader(content, "WICKETKEEPING & RUN OUTS")
                    addTileRow(content, "Most Stumpings", "Most Run Outs")
                }
                "Ranking" -> {
                    addShowcaseCard(content, "Overall Rankings", "Ranking")
                    addSectionHeader(content, "DISCIPLINE STANDINGS")
                    addTileRow(content, "Batting Rankings", "Bowling Rankings")
                }
            }
            view
        } catch (e: Exception) {
            e.printStackTrace()
            inflater.inflate(R.layout.fragment_stats_list, container, false)
        }
    }

    private fun addShowcaseCard(container: LinearLayout, title: String, category: String) {
        try {
            val context = container.context
            val inflater = LayoutInflater.from(context)
            val card = inflater.inflate(
                R.layout.item_stat_showcase_card,
                container,
                false
            ) as MaterialCardView

            val isDark = ThemeManager.isDarkMode(context)
            val layoutBg = card.findViewById<View>(R.id.layoutShowcaseBg)
            layoutBg?.background = getShowcaseGradient(category, isDark)

            val tvTitle = card.findViewById<TextView>(R.id.tvShowcaseTitle)
            val tvSubtitle = card.findViewById<TextView>(R.id.tvShowcaseSubtitle)
            val tvTag = card.findViewById<TextView>(R.id.tvShowcaseTagText)
            val ivTagIcon = card.findViewById<ImageView>(R.id.ivShowcaseTagIcon)
            val ivWatermark = card.findViewById<ImageView>(R.id.ivShowcaseWatermark)

            tvTitle?.text = title
            tvSubtitle?.text = getSubtitleForStat(title)

            when (category) {
                "Batting" -> {
                    tvTag?.text = "PREMIER BATTING RECORD"
                    ivTagIcon?.setImageResource(R.drawable.ic_crown)
                    ivWatermark?.setImageResource(R.drawable.ic_stat_bat_mono)
                }
                "Bowling" -> {
                    tvTag?.text = "PREMIER BOWLING RECORD"
                    ivTagIcon?.setImageResource(R.drawable.ic_stat_fire)
                    ivWatermark?.setImageResource(R.drawable.ic_stat_ball)
                }
                "Fielding" -> {
                    tvTag?.text = "PREMIER FIELDING RECORD"
                    ivTagIcon?.setImageResource(R.drawable.ic_stat_fielding)
                    ivWatermark?.setImageResource(R.drawable.ic_stat_fielding)
                }
                "Ranking" -> {
                    tvTag?.text = "OFFICIAL CHAMPIONSHIP STANDINGS"
                    ivTagIcon?.setImageResource(R.drawable.ic_trophy)
                    ivWatermark?.setImageResource(R.drawable.ic_trophy)
                }
            }

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

    private fun addSectionHeader(container: LinearLayout, title: String) {
        try {
            val inflater = LayoutInflater.from(container.context)
            val view = inflater.inflate(R.layout.item_stat_section_header, container, false)
            view.findViewById<TextView>(R.id.tvSectionTitle)?.text = title
            container.addView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addTileRow(container: LinearLayout, leftTitle: String, rightTitle: String? = null) {
        try {
            val context = container.context
            val inflater = LayoutInflater.from(context)
            val row = inflater.inflate(R.layout.item_stat_grid_row, container, false)

            val leftContainer = row.findViewById<FrameLayout>(R.id.containerLeftTile)
            val rightContainer = row.findViewById<FrameLayout>(R.id.containerRightTile)

            val leftTile = createTile(container, leftTitle)
            leftContainer?.addView(leftTile)

            if (rightTitle != null) {
                val rightTile = createTile(container, rightTitle)
                rightContainer?.addView(rightTile)
            } else {
                rightContainer?.visibility = View.INVISIBLE
            }

            container.addView(row)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createTile(parent: ViewGroup, title: String): View {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val card = inflater.inflate(R.layout.item_stat_grid_tile, parent, false) as MaterialCardView

        val isDark = ThemeManager.isDarkMode(context)
        val density = resources.displayMetrics.density
        val style = getStatStyle(title, isDark)

        val tvTitle = card.findViewById<TextView>(R.id.tvTileTitle)
        val tvSubtitle = card.findViewById<TextView>(R.id.tvTileSubtitle)
        val ivIcon = card.findViewById<ImageView>(R.id.ivTileIcon)
        val flIconBg = card.findViewById<FrameLayout>(R.id.flTileIconBg)

        tvTitle?.text = title
        tvSubtitle?.text = getSubtitleForStat(title)

        val iconShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 11f * density
            setColor(style.bg)
            setStroke((1.5f * density).toInt(), style.stroke)
        }
        flIconBg?.background = iconShape

        ivIcon?.setImageResource(getIconForStat(title))
        ivIcon?.imageTintList = ColorStateList.valueOf(style.iconTint)

        card.setOnClickListener {
            val intent = Intent(activity, LeaderboardActivity::class.java)
            intent.putExtra("type", title)
            startActivity(intent)
        }

        ThemeManager.colorize(card)
        return card
    }

    private fun getShowcaseGradient(category: String, isDark: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        // Keep the vibrant, luminous, high-energy gradients in both light and dark modes!
        val colors = when (category) {
            "Batting" -> intArrayOf(Color.parseColor("#E65100"), Color.parseColor("#F57C00"), Color.parseColor("#FF9800"))
            "Bowling" -> intArrayOf(Color.parseColor("#991B1B"), Color.parseColor("#B91C1C"), Color.parseColor("#DC2626"))
            "Fielding" -> intArrayOf(Color.parseColor("#14532D"), Color.parseColor("#15803D"), Color.parseColor("#16A34A"))
            "Ranking" -> intArrayOf(Color.parseColor("#78350F"), Color.parseColor("#92400E"), Color.parseColor("#B45309"))
            else -> intArrayOf(Color.parseColor("#004D40"), Color.parseColor("#00695C"))
        }

        val strokeColor = if (isDark) {
            when (category) {
                "Batting" -> Color.parseColor("#4DFF9800")
                "Bowling" -> Color.parseColor("#4DEF4444")
                "Fielding" -> Color.parseColor("#4D22C55E")
                "Ranking" -> Color.parseColor("#4DF59E0B")
                else -> Color.TRANSPARENT
            }
        } else {
            Color.TRANSPARENT
        }

        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = 22f * density
            if (isDark) {
                setStroke((1.5f * density).toInt(), strokeColor)
            }
        }
    }

    private fun getStatStyle(title: String, isDark: Boolean): StatStyle {
        return when (title) {
            "Best Strike Rate", "Most Sixes" -> if (isDark) {
                StatStyle(
                    bg = Color.parseColor("#26EF4444"),
                    stroke = Color.parseColor("#EF4444"),
                    iconTint = Color.parseColor("#FF5252")
                )
            } else {
                StatStyle(
                    bg = Color.parseColor("#FEF2F2"),
                    stroke = Color.parseColor("#EF4444"),
                    iconTint = Color.parseColor("#DC2626")
                )
            }

            "Highest Score", "Most Fours" -> if (isDark) {
                StatStyle(
                    bg = Color.parseColor("#26F97316"),
                    stroke = Color.parseColor("#F97316"),
                    iconTint = Color.parseColor("#FFA726")
                )
            } else {
                StatStyle(
                    bg = Color.parseColor("#FFF7ED"),
                    stroke = Color.parseColor("#F97316"),
                    iconTint = Color.parseColor("#C2410C")
                )
            }

            "Most 80s", "Most 50s", "Most 30s" -> if (isDark) {
                StatStyle(
                    bg = Color.parseColor("#26F59E0B"),
                    stroke = Color.parseColor("#F59E0B"),
                    iconTint = Color.parseColor("#FCD34D")
                )
            } else {
                StatStyle(
                    bg = Color.parseColor("#FEF9C3"),
                    stroke = Color.parseColor("#EAB308"),
                    iconTint = Color.parseColor("#A16207")
                )
            }

            "Most Ducks" -> if (isDark) {
                StatStyle(
                    bg = Color.parseColor("#2664748B"),
                    stroke = Color.parseColor("#64748B"),
                    iconTint = Color.parseColor("#94A3B8")
                )
            } else {
                StatStyle(
                    bg = Color.parseColor("#F1F5F9"),
                    stroke = Color.parseColor("#94A3B8"),
                    iconTint = Color.parseColor("#475569")
                )
            }

            "Best Bowling Figure", "Most 5 Wicket Hauls" -> if (isDark) {
                StatStyle(
                    bg = Color.parseColor("#26A855F7"),
                    stroke = Color.parseColor("#A855F7"),
                    iconTint = Color.parseColor("#C084FC")
                )
            } else {
                StatStyle(
                    bg = Color.parseColor("#F5F3FF"),
                    stroke = Color.parseColor("#8B5CF6"),
                    iconTint = Color.parseColor("#6D28D9")
                )
            }

            "Best Bowling Average", "Best Economy" -> if (isDark) {
                StatStyle(
                    bg = Color.parseColor("#263B82F6"),
                    stroke = Color.parseColor("#3B82F6"),
                    iconTint = Color.parseColor("#60A5FA")
                )
            } else {
                StatStyle(
                    bg = Color.parseColor("#EFF6FF"),
                    stroke = Color.parseColor("#3B82F6"),
                    iconTint = Color.parseColor("#1D4ED8")
                )
            }

            "Most Hattricks", "Most 2 Wicket Hauls", "Most 3 Wicket Hauls", "Most 6s Conceded" -> if (isDark) {
                StatStyle(
                    bg = Color.parseColor("#26F87171"),
                    stroke = Color.parseColor("#F87171"),
                    iconTint = Color.parseColor("#FF6B6B")
                )
            } else {
                StatStyle(
                    bg = Color.parseColor("#FEE2E2"),
                    stroke = Color.parseColor("#F87171"),
                    iconTint = Color.parseColor("#B91C1C")
                )
            }

            "Most Catches", "Most Stumpings", "Most Run Outs" -> if (isDark) {
                StatStyle(
                    bg = Color.parseColor("#2622C55E"),
                    stroke = Color.parseColor("#22C55E"),
                    iconTint = Color.parseColor("#4ADE80")
                )
            } else {
                StatStyle(
                    bg = Color.parseColor("#DCFCE7"),
                    stroke = Color.parseColor("#22C55E"),
                    iconTint = Color.parseColor("#15803D")
                )
            }

            "Batting Rankings" -> if (isDark) {
                StatStyle(
                    bg = Color.parseColor("#26F97316"),
                    stroke = Color.parseColor("#F97316"),
                    iconTint = Color.parseColor("#FFA726")
                )
            } else {
                StatStyle(
                    bg = Color.parseColor("#FFF7ED"),
                    stroke = Color.parseColor("#F97316"),
                    iconTint = Color.parseColor("#C2410C")
                )
            }

            "Bowling Rankings" -> if (isDark) {
                StatStyle(
                    bg = Color.parseColor("#26EF4444"),
                    stroke = Color.parseColor("#EF4444"),
                    iconTint = Color.parseColor("#FF5252")
                )
            } else {
                StatStyle(
                    bg = Color.parseColor("#FEE2E2"),
                    stroke = Color.parseColor("#EF4444"),
                    iconTint = Color.parseColor("#B91C1C")
                )
            }

            else -> if (isDark) {
                StatStyle(
                    bg = Color.parseColor("#2614B8A6"),
                    stroke = Color.parseColor("#14B8A6"),
                    iconTint = Color.parseColor("#2DD4BF")
                )
            } else {
                StatStyle(
                    bg = Color.parseColor("#CCFBF1"),
                    stroke = Color.parseColor("#14B8A6"),
                    iconTint = Color.parseColor("#0D9488")
                )
            }
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
            else -> "View leaderboard & rankings"
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
            "Best Bowling Figure" -> R.drawable.ic_trophy
            "Best Bowling Average", "Most 6s Conceded" -> R.drawable.ic_stat_target
            "Best Economy", "Most Catches", "Most Stumpings", "Most Run Outs" -> R.drawable.ic_stat_fielding

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

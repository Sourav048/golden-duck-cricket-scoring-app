package com.example.scoring

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.scoring.AppDatabase.Companion.getInstance
import com.example.scoring.RankingRegistry.applyPrestige
import com.example.scoring.RankingRegistry.getColorForPlayer
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import java.util.Locale

class MatchSummaryFragment : Fragment() {
    private var match: MatchEntity? = null
    private var stats: MutableList<PlayerMatchStatEntity?>? = null

    fun setData(match: MatchEntity?, stats: MutableList<PlayerMatchStatEntity?>?) {
        this.match = match
        this.stats = stats
        updateUI()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_match_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUI()
    }

    private fun setHighlightText(textView: TextView?, names: List<String>, count: Int, safeStats: List<PlayerMatchStatEntity>) {
        if (textView == null) return
        if (names.isEmpty()) {
            textView.text = "None"
            return
        }

        val builder = SpannableStringBuilder()
        for (i in names.indices) {
            val name = names[i]
            val start = builder.length
            builder.append(name)
            
            // Check for prestige color
            val playerStat = safeStats.find { it.playerName?.trim().equals(name.trim(), ignoreCase = true) }
            val color = getColorForPlayer(textView.context, playerStat?.playerId, name)
            
            if (color != 0) {
                builder.setSpan(ForegroundColorSpan(color), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (i < names.size - 1) {
                builder.append(", ")
            }
        }
        builder.append(" ($count)")
        textView.text = builder
    }

    fun updateUI() {
        val v = view ?: return
        val m = match ?: return

        val layoutPotm = v.findViewById<View>(R.id.layoutPotmSection) ?: return
        val layoutHighlights = v.findViewById<View>(R.id.layoutHighlights) ?: return

        val potmName = m.playerOfTheMatchName
        val hasPotm = m.isFinished && !m.isAbandoned && !potmName.isNullOrEmpty() && potmName != "TBD"

        if (!hasPotm) {
            layoutPotm.visibility = View.GONE
        } else {
            layoutPotm.visibility = View.VISIBLE
        }

        val safeStats = stats?.filterNotNull() ?: emptyList()

        if (hasPotm) {
            val potmStat = safeStats.find { it.playerName?.trim().equals(potmName?.trim(), ignoreCase = true) }
            if (potmStat != null) {
                val tvName = v.findViewById<TextView>(R.id.tvPotmName)
                tvName.text = potmStat.playerName

                val ivPhoto = v.findViewById<ShapeableImageView>(R.id.ivPotmPhoto)
                applyPrestige(potmStat.playerId, tvName, ivPhoto)

                val tvPotmStats = v.findViewById<TextView>(R.id.tvPotmStats)
                val batStats = if (potmStat.ballsFaced > 0) String.format(Locale.US, "%d(%d)", potmStat.runsScored, potmStat.ballsFaced) else ""
                val bowlStats = if (potmStat.ballsBowled > 0) String.format(Locale.US, "%d/%d", potmStat.wicketsTaken, potmStat.runsConceded) else ""
                
                tvPotmStats.text = when {
                    batStats.isNotEmpty() && bowlStats.isNotEmpty() -> "$batStats & $bowlStats"
                    batStats.isNotEmpty() -> batStats
                    bowlStats.isNotEmpty() -> bowlStats
                    else -> "No Stats"
                }

                val card = v.findViewById<MaterialCardView>(R.id.cardPotmPhotoFrame)
                ivPhoto.strokeColor?.let {
                    card?.setStrokeColor(it)
                    card?.strokeWidth = 8
                    ivPhoto.strokeWidth = 0f
                }

                AppDatabase.ioExecutor.execute {
                    val db = getInstance(v.context)
                    val pe = potmStat.playerId?.let { db.playerDao().getPlayerById(it) }
                             ?: potmStat.playerName?.let { db.playerDao().getPlayerByName(it.trim()) }

                    val photoUri = pe?.photoUri
                    v.post {
                        if (view == null) return@post
                        if (!photoUri.isNullOrEmpty()) {
                            Glide.with(this).load(photoUri)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_menu_gallery)
                                .into(ivPhoto)
                        } else {
                            ivPhoto.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    }
                }
            }
        }

        // Calculate and Display Highlights for all matches with stats
        if (safeStats.isNotEmpty()) {
            layoutHighlights.visibility = View.VISIBLE
            
            // Most Sixes
            val maxSixes = safeStats.maxOfOrNull { it.sixes } ?: 0
            if (maxSixes > 0) {
                val winners = safeStats.filter { it.sixes == maxSixes }.mapNotNull { it.playerName }
                setHighlightText(v.findViewById(R.id.tvMostSixes), winners, maxSixes, safeStats)
            } else {
                v.findViewById<TextView?>(R.id.tvMostSixes)?.text = "None"
            }

            // Most Wickets
            val maxWickets = safeStats.maxOfOrNull { it.wicketsTaken } ?: 0
            if (maxWickets > 0) {
                val winners = safeStats.filter { it.wicketsTaken == maxWickets }.mapNotNull { it.playerName }
                setHighlightText(v.findViewById(R.id.tvMostWickets), winners, maxWickets, safeStats)
            } else {
                v.findViewById<TextView?>(R.id.tvMostWickets)?.text = "None"
            }

            // Most Fours
            val maxFours = safeStats.maxOfOrNull { it.fours } ?: 0
            if (maxFours > 0) {
                val winners = safeStats.filter { it.fours == maxFours }.mapNotNull { it.playerName }
                setHighlightText(v.findViewById(R.id.tvMostFours), winners, maxFours, safeStats)
            } else {
                v.findViewById<TextView?>(R.id.tvMostFours)?.text = "None"
            }

            // Most Dot Balls
            val maxDots = safeStats.maxOfOrNull { it.dotBalls } ?: 0
            if (maxDots > 0) {
                val winners = safeStats.filter { it.dotBalls == maxDots }.mapNotNull { it.playerName }
                setHighlightText(v.findViewById(R.id.tvMostDots), winners, maxDots, safeStats)
            } else {
                v.findViewById<TextView?>(R.id.tvMostDots)?.text = "None"
            }
        } else {
            layoutHighlights.visibility = View.GONE
        }
    }

    companion object {
        fun newInstance(match: MatchEntity?): MatchSummaryFragment {
            val f = MatchSummaryFragment()
            f.match = match
            return f
        }
    }
}

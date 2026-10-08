package com.example.scoring

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.scoring.RankingRegistry.applyPrestige
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class LeaderboardActivity : BaseActivity() {
    private var type: String? = null
    private var recyclerView: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private var adapter: LeaderboardAdapter? = null

    private val viewModel: LeaderboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats_leaderboard)

        type = intent.getStringExtra("type")

        val tvTitle = findViewById<TextView>(R.id.tvLeaderboardTitle)
        tvTitle.text = type ?: "Leaderboard"

        val btnBack = findViewById<ImageButton>(R.id.btnBackLeaderboard)
        btnBack?.setOnClickListener { finish() }

        tvEmpty = findViewById(R.id.tvEmptyLeaderboard)
        recyclerView = findViewById(R.id.rvLeaderboard)
        recyclerView?.layoutManager = LinearLayoutManager(this)

        observeViewModel()

        viewModel.loadLeaderboard(type)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is LeaderboardUiState.Loading -> {
                            // Loading state
                        }
                        is LeaderboardUiState.Success -> {
                            if (state.stats.isEmpty()) {
                                tvEmpty?.visibility = View.VISIBLE
                                recyclerView?.visibility = View.GONE
                            } else {
                                tvEmpty?.visibility = View.GONE
                                recyclerView?.visibility = View.VISIBLE
                                adapter = LeaderboardAdapter(state.stats.toMutableList(), state.type)
                                recyclerView?.adapter = adapter
                            }
                        }
                        is LeaderboardUiState.Error -> {
                            Toast.makeText(this@LeaderboardActivity, state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
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
            val s = stats.getOrNull(position) ?: return
            val rankNum = position + 1
            holder.rank.text = rankNum.toString()
            holder.name.text = s.playerName ?: "Unknown"

            // Apply Gold / Silver / Bronze badge and outline for top 3 ranks
            val flRankBadge = holder.itemView.findViewById<FrameLayout>(R.id.flRankBadge)
            val flStatValueBg = holder.itemView.findViewById<FrameLayout>(R.id.flStatValueBg)
            val card = holder.itemView as? MaterialCardView

            when (rankNum) {
                1 -> {
                    flRankBadge?.setBackgroundResource(R.drawable.bg_rank_gold)
                    holder.rank.setTextColor(Color.WHITE)
                    flStatValueBg?.setBackgroundResource(R.drawable.bg_stat_value_pill_gold)
                    card?.strokeColor = Color.parseColor("#D4AF37")
                    card?.strokeWidth = (2 * holder.itemView.context.resources.displayMetrics.density).toInt()
                }
                2 -> {
                    flRankBadge?.setBackgroundResource(R.drawable.bg_rank_silver)
                    holder.rank.setTextColor(Color.WHITE)
                    flStatValueBg?.setBackgroundResource(R.drawable.bg_stat_value_pill)
                    card?.strokeColor = Color.parseColor("#C0C0C0")
                    card?.strokeWidth = (1.5f * holder.itemView.context.resources.displayMetrics.density).toInt()
                }
                3 -> {
                    flRankBadge?.setBackgroundResource(R.drawable.bg_rank_bronze)
                    holder.rank.setTextColor(Color.WHITE)
                    flStatValueBg?.setBackgroundResource(R.drawable.bg_stat_value_pill)
                    card?.strokeColor = Color.parseColor("#CD7F32")
                    card?.strokeWidth = (1.5f * holder.itemView.context.resources.displayMetrics.density).toInt()
                }
                else -> {
                    flRankBadge?.setBackgroundResource(R.drawable.bg_rank_default)
                    holder.rank.setTextColor(Color.WHITE)
                    flStatValueBg?.setBackgroundResource(R.drawable.bg_stat_value_pill)
                    card?.strokeColor = ThemeManager.getThemeColor(holder.itemView.context, com.google.android.material.R.attr.colorOutline)
                    card?.strokeWidth = (1 * holder.itemView.context.resources.displayMetrics.density).toInt()
                }
            }

            applyPrestige(s.playerId, holder.name, holder.photo)

            val photoUri = s.photoUri
            val isFileValid = if (!photoUri.isNullOrEmpty() && (photoUri.startsWith("/") || photoUri.startsWith("file://"))) {
                File(photoUri.removePrefix("file://")).exists()
            } else {
                !photoUri.isNullOrEmpty()
            }

            if (isFileValid) {
                try {
                    Glide.with(holder.itemView.context)
                        .load(photoUri)
                        .placeholder(R.drawable.ic_person_placeholder)
                        .error(R.drawable.ic_person_placeholder)
                        .into(holder.photo)
                } catch (_: Exception) {
                    Glide.with(holder.itemView.context).clear(holder.photo)
                    holder.photo.setImageResource(R.drawable.ic_person_placeholder)
                }
            } else {
                Glide.with(holder.itemView.context).clear(holder.photo)
                holder.photo.setImageResource(R.drawable.ic_person_placeholder)
            }

            when {
                type == "Best Economy" || type == "Best Bowling Average" || type == "Best Strike Rate" -> {
                    val value = s.total / 100.0
                    holder.value.text = String.format(Locale.US, "%.2f", value)
                }
                type == null -> {
                    holder.value.text = s.total.toString()
                }
                type == "Best Bowling Figure" -> {
                    val w = s.total / 100000
                    val r = 99999 - (s.total % 100000)
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
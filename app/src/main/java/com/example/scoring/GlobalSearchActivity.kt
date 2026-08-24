package com.example.scoring

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView

class GlobalSearchActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var adapter: GlobalPlayerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_search)

        recyclerView = findViewById(R.id.globalPlayerRecyclerView)
        searchView = findViewById(R.id.globalSearchView)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        adapter = GlobalPlayerAdapter { player ->
            val resultIntent = Intent()
            resultIntent.putExtra("selected_player", player)
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    performSearch(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrBlank()) {
                    performSearch(newText)
                } else {
                    adapter.submitList(emptyList())
                }
                return true
            }
        })
    }

    private fun performSearch(query: String) {
        GullySyncManager.searchGlobalPlayers(query) { players ->
            runOnUiThread {
                adapter.submitList(players)
            }
        }
    }

    private inner class GlobalPlayerAdapter(private val onPlayerSelected: (PlayerEntity) -> Unit) :
        RecyclerView.Adapter<GlobalPlayerAdapter.Holder>() {

        private var playerList: List<PlayerEntity> = emptyList()

        fun submitList(list: List<PlayerEntity>) {
            playerList = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_global_player, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val p = playerList[position]
            
            // 1. Professional Name & Milestone Badge
            val isLegend = p.totalRuns >= 500 || p.totalWickets >= 50
            if (isLegend) {
                holder.name.text = "🏆 ${p.name} (#${p.jerseyNumber})"
                holder.name.setTextColor(android.graphics.Color.parseColor("#FFD700")) // Bright Gold
            } else {
                holder.name.text = "${p.name} (#${p.jerseyNumber})"
                val colorOnSurface = ThemeManager.getThemeColor(holder.itemView.context, com.google.android.material.R.attr.colorOnSurface)
                holder.name.setTextColor(colorOnSurface)
            }

            // 2. Origin Display (Reverted to show "Unknown" as requested)
            val origin = if (!p.originGully.isNullOrEmpty()) p.originGully else "Unknown"
            holder.origin.text = "Origin: $origin"
            holder.origin.visibility = View.VISIBLE

            // 3. Career Stats Display (Always show stats, even if 0)
            holder.stats.text = "Career: ${p.totalRuns} Runs • ${p.totalWickets} Wkts • ${p.totalMatches} Matches"

            // 4. Photo Rendering (Extremely Robust Base64 Decoder)
            if (!p.photoBase64.isNullOrEmpty()) {
                try {
                    // Clean the string: remove whitespace and any potential data:image prefix
                    var cleanBase64 = p.photoBase64!!.trim()
                    if (cleanBase64.startsWith("data:")) {
                        val commaIndex = cleanBase64.indexOf(",")
                        if (commaIndex != -1) {
                            cleanBase64 = cleanBase64.substring(commaIndex + 1)
                        }
                    }

                    // Try multiple decoding strategies
                    val bytes = try {
                        android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                    } catch (e: Exception) {
                        try {
                            android.util.Base64.decode(cleanBase64, android.util.Base64.NO_WRAP)
                        } catch (e2: Exception) {
                            android.util.Base64.decode(cleanBase64, android.util.Base64.URL_SAFE)
                        }
                    }

                    Glide.with(holder.itemView.context)
                        .asBitmap()
                        .load(bytes)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .circleCrop()
                        .into(holder.photo)

                } catch (e: Exception) {
                    Log.e("GlobalSearch", "Photo render failed: ${e.message}")
                    holder.photo.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else {
                holder.photo.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            holder.btnImport.setOnClickListener { onPlayerSelected(p) }
            holder.itemView.setOnClickListener { onPlayerSelected(p) }
        }

        override fun getItemCount(): Int = playerList.size

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val photo: ShapeableImageView = v.findViewById(R.id.playerPhoto)
            val name: TextView = v.findViewById(R.id.playerName)
            val origin: TextView = v.findViewById(R.id.playerOrigin)
            val stats: TextView = v.findViewById(R.id.playerStats)
            val btnImport: MaterialButton = v.findViewById(R.id.btnImport)
        }
    }
}

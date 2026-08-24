package com.example.scoring

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.google.android.material.imageview.ShapeableImageView
import java.util.concurrent.CopyOnWriteArrayList

object RankingRegistry {
    @Volatile
    var topOverallId: String? = null
    @Volatile
    var topBattingId: String? = null
    @Volatile
    var topBowlingId: String? = null

    @Volatile
    var topOverallName: String? = null
    @Volatile
    var topBattingName: String? = null
    @Volatile
    var topBowlingName: String? = null

    var top3OverallIds: MutableList<String?> = CopyOnWriteArrayList()
    var top3BattingIds: MutableList<String?> = CopyOnWriteArrayList()
    var top3BowlingIds: MutableList<String?> = CopyOnWriteArrayList()

    var cachedGold: Int = 0
    var cachedOrange: Int = 0
    var cachedPurple: Int = 0
    private var cachedDefaultText: Int = 0
    private var lastThemeConfig: Int = -1

    private var lastRefreshTime: Long = 0
    private val REFRESH_COOLDOWN = 5_000L // 5 seconds

    @JvmStatic
    fun refresh(context: Context?, callback: OnRankingsLoaded?) {
        if (context == null) return
        
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < REFRESH_COOLDOWN) {
            callback?.onLoaded()
            return
        }
        lastRefreshTime = now
        
        val appContext = context.applicationContext
        AppDatabase.ioExecutor.execute {
            try {
                // Clear current rankings first
                topOverallId = null
                topBattingId = null
                topBowlingId = null
                topOverallName = null
                topBattingName = null
                topBowlingName = null
                top3OverallIds.clear()
                top3BattingIds.clear()
                top3BowlingIds.clear()

                // Clear color cache to force re-fetch from theme
                cachedGold = 0
                lastThemeConfig = -1

                val db = AppDatabase.getInstance(appContext)
                val gId = GullySyncManager.getCurrentGullyId(appContext) ?: "local"
                
                // CRITICAL SAFETY: Wrap stats fetching in try-catch
                try {
                    val overall = db.statsDao().getOverallRankings(gId) ?: emptyList()
                    val batting = db.statsDao().getBattingRankings(gId) ?: emptyList()
                    val bowling = db.statsDao().getBowlingRankings(gId) ?: emptyList()

                    overall.firstOrNull()?.let {
                        topOverallId = it.playerId
                        topOverallName = it.playerName
                    }
                    
                    batting.firstOrNull()?.let {
                        topBattingId = it.playerId
                        topBattingName = it.playerName
                    }
                    
                    bowling.firstOrNull()?.let {
                        topBowlingId = it.playerId
                        topBowlingName = it.playerName
                    }

                    overall.take(3).forEach { it?.playerId?.let { id -> top3OverallIds.add(id) } }
                    batting.take(3).forEach { it?.playerId?.let { id -> top3BattingIds.add(id) } }
                    bowling.take(3).forEach { it?.playerId?.let { id -> top3BowlingIds.add(id) } }
                } catch (e: Exception) {
                    Log.e("RANKING", "Stats DAO fetch failed: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e("RANKING", "Refresh failed: ${e.message}")
            }
            callback?.let {
                Handler(Looper.getMainLooper()).post { it.onLoaded() }
            }
        }
    }

    @JvmStatic
    fun applyPrestige(playerId: String?, tvName: TextView?, ivPhoto: ShapeableImageView?, overrideDefaultColor: Int? = null) {
        applyPrestigeInternal(playerId, null, tvName, ivPhoto, overrideDefaultColor)
    }

    @JvmStatic
    fun applyPrestigeByName(playerName: String?, tvName: TextView?, overrideDefaultColor: Int? = null) {
        applyPrestigeInternal(null, playerName, tvName, null, overrideDefaultColor)
    }

    @JvmStatic
    fun getColorForPlayer(context: Context, playerId: String?, playerName: String?): Int {
        ensureColorsCached(context)
        
        return when {
            playerId != null -> {
                when (playerId) {
                    topOverallId -> cachedGold
                    topBattingId -> cachedOrange
                    topBowlingId -> cachedPurple
                    else -> 0
                }
            }
            playerName != null -> {
                val trimmed = playerName.trim()
                when {
                    trimmed.equals(topOverallName, ignoreCase = true) -> cachedGold
                    trimmed.equals(topBattingName, ignoreCase = true) -> cachedOrange
                    trimmed.equals(topBowlingName, ignoreCase = true) -> cachedPurple
                    else -> 0
                }
            }
            else -> 0
        }
    }

    private fun applyPrestigeInternal(playerId: String?, playerName: String?, tvName: TextView?, ivPhoto: ShapeableImageView?, overrideDefaultColor: Int? = null) {
        val name = tvName ?: return
        val context = name.context
        
        ensureColorsCached(context)

        val defaultColorToUse = overrideDefaultColor ?: cachedDefaultText

        val color = when {
            // Priority 1: Match by Unique ID
            playerId != null -> {
                when (playerId) {
                    topOverallId -> cachedGold
                    topBattingId -> cachedOrange
                    topBowlingId -> cachedPurple
                    else -> 0
                }
            }
            // Priority 2: Fallback to Name matching only if ID is unavailable
            playerName != null -> {
                when (playerName) {
                    topOverallName -> cachedGold
                    topBattingName -> cachedOrange
                    topBowlingName -> cachedPurple
                    else -> 0
                }
            }
            else -> 0
        }

        // ALWAYS clear shadows to prevent "smudge" recycling bugs
        name.setShadowLayer(0f, 0f, 0f, 0)

        if ((color != 0) && !name.text.isNullOrEmpty()) {
            name.setTextColor(color)
            
            ivPhoto?.let {
                it.strokeWidth = 18f
                it.strokeColor = ColorStateList.valueOf(color)
            }
        } else {
            // Reset to the specified default color if not prestige
            name.setTextColor(defaultColorToUse)
            ivPhoto?.let {
                it.strokeWidth = 0f
                it.strokeColor = null
            }
        }
    }

    @JvmStatic
    fun isPrestigeColor(color: Int): Boolean {
        if (color == 0) return false
        return color == cachedGold || color == cachedOrange || color == cachedPurple
    }

    private fun ensureColorsCached(context: Context) {
        val currentConfig = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (lastThemeConfig == currentConfig && cachedGold != 0) return

        val tv = TypedValue()
        cachedGold = if (context.theme.resolveAttribute(R.attr.prestigeGold, tv, true)) tv.data else -0x2b50c9
        cachedOrange = if (context.theme.resolveAttribute(R.attr.prestigeOrange, tv, true)) tv.data else -0x109400
        cachedPurple = if (context.theme.resolveAttribute(R.attr.prestigePurple, tv, true)) tv.data else -0xa5ff80
        
        cachedDefaultText = if (context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)) {
            tv.data
        } else {
            if (currentConfig == Configuration.UI_MODE_NIGHT_YES) -0x1 else -0x1000000
        }
        
        lastThemeConfig = currentConfig
    }

    fun interface OnRankingsLoaded {
        fun onLoaded()
    }
}

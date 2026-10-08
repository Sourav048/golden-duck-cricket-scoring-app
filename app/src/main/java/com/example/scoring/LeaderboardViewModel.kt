package com.example.scoring

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scoring.AppDatabase.Companion.getInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LeaderboardUiState {
    object Loading : LeaderboardUiState
    data class Success(
        val stats: List<PlayerTotalStat>,
        val type: String?
    ) : LeaderboardUiState
    data class Error(val message: String) : LeaderboardUiState
}

class LeaderboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    private val db = getInstance(application)

    fun loadLeaderboard(type: String?) {
        _uiState.value = LeaderboardUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                RankingRegistry.refresh(ctx, null)

                val gId = GullySyncManager.getCurrentGullyId(ctx) ?: "local"
                db.statsDao().fixOrphanMatchStats()

                val stats: List<PlayerTotalStat?>? = when (type) {
                    "Most Runs" -> db.statsDao().getMostRuns(gId)
                    "Best Strike Rate" -> db.statsDao().getBestStrikeRate(gId)
                    "Most Sixes" -> db.statsDao().getMostSixes(gId)
                    "Most Fours" -> db.statsDao().getMostFours(gId)
                    "Most 80s" -> db.statsDao().getMostEighties(gId)
                    "Highest Score" -> db.statsDao().getHighestScores(gId)
                    "Most 50s" -> db.statsDao().getMostFifties(gId)
                    "Most 30s" -> db.statsDao().getMostThirties(gId)
                    "Most Ducks" -> db.statsDao().getMostDucks(gId)
                    "Most Wickets" -> db.statsDao().getMostWickets(gId)
                    "Best Bowling Figure" -> db.statsDao().getBestBowlingInnings(gId)
                    "Best Bowling Average" -> db.statsDao().getBestBowlingAverage(gId)
                    "Best Economy" -> db.statsDao().getBestEconomy(gId)
                    "Most Hattricks" -> db.statsDao().getMostHattricks(gId)
                    "Most 5 Wicket Hauls" -> db.statsDao().getMostFiveWicketHauls(gId)
                    "Most 3 Wicket Hauls" -> db.statsDao().getMostThreeWicketHauls(gId)
                    "Most 2 Wicket Hauls" -> db.statsDao().getMostTwoWicketHauls(gId)
                    "Most 6s Conceded" -> db.statsDao().getMostSixesConceded(gId)
                    "Most Catches" -> db.statsDao().getMostCatches(gId)
                    "Most Stumpings" -> db.statsDao().getMostStumpings(gId)
                    "Most Run Outs" -> db.statsDao().getMostRunOuts(gId)
                    "Overall Rankings" -> db.statsDao().getOverallRankings(gId)
                    "Batting Rankings" -> db.statsDao().getBattingRankings(gId)
                    "Bowling Rankings" -> db.statsDao().getBowlingRankings(gId)
                    else -> emptyList()
                }

                val finalStats: List<PlayerTotalStat> = stats?.filterNotNull() ?: emptyList()
                _uiState.value = LeaderboardUiState.Success(finalStats, type)
            } catch (e: Exception) {
                Log.e("LeaderboardViewModel", "Error loading leaderboard: ${e.message}", e)
                _uiState.value = LeaderboardUiState.Error(e.message ?: "Failed to load leaderboard data")
            }
        }
    }
}

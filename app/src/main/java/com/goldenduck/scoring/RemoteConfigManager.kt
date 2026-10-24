package com.goldenduck.scoring

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

object RemoteConfigManager {

    private const val TAG = "RemoteConfigManager"

    const val KEY_ENABLE_EDIT_OPTION = "enable_edit_option"
    const val KEY_ENABLE_PLAYER_COMPARE = "enable_player_compare"
    const val KEY_ENABLE_STATS_RECALCULATE = "enable_stats_recalculate"

    fun init() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()

            // Set 0 seconds fetch interval so changes in Firebase Console take effect INSTANTLY
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 0
            }
            remoteConfig.setConfigSettingsAsync(configSettings)

            val defaults = mapOf(
                KEY_ENABLE_EDIT_OPTION to false,
                KEY_ENABLE_PLAYER_COMPARE to true,
                KEY_ENABLE_STATS_RECALCULATE to false
            )
            remoteConfig.setDefaultsAsync(defaults)

            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Remote Config updated successfully.")
                    } else {
                        Log.e(TAG, "Remote Config fetch failed", task.exception)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Remote Config", e)
        }
    }

    fun isFeatureEnabled(key: String): Boolean {
        return try {
            FirebaseRemoteConfig.getInstance().getBoolean(key)
        } catch (e: Exception) {
            true // Default to enabled if error or offline
        }
    }
}

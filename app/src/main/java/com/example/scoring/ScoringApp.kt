package com.example.scoring

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class ScoringApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Load and apply saved theme
        val prefs = getSharedPreferences("scoring_prefs", Context.MODE_PRIVATE)
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedMode)
    }
}

package com.example.scoring

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists the history of Gullies joined on this device.
 * Allows for instant "League Switching".
 */
object GullyHistoryManager {
    private const val PREF_NAME = "gully_history_prefs"
    private const val KEY_GULLIES = "joined_gullies"
    private val gson = Gson()

    data class GullyRecord(val id: String, val passcode: String, val joinedAt: Long)

    fun addGully(context: Context, id: String, passcode: String) {
        val list = getGullies(context).toMutableList()
        // Remove if exists to re-add at top (most recent)
        list.removeAll { it.id.equals(id, ignoreCase = true) }
        list.add(0, GullyRecord(id.uppercase(), passcode, System.currentTimeMillis()))
        
        saveList(context, list)
    }

    fun getGullies(context: Context): List<GullyRecord> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_GULLIES, null) ?: return emptyList()
        val type = object : TypeToken<List<GullyRecord>>() {}.type
        return gson.fromJson(json, type)
    }

    fun removeGully(context: Context, id: String) {
        val list = getGullies(context).toMutableList()
        list.removeAll { it.id.equals(id, ignoreCase = true) }
        saveList(context, list)
    }

    private fun saveList(context: Context, list: List<GullyRecord>) {
        val json = gson.toJson(list)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GULLIES, json)
            .apply()
    }
}

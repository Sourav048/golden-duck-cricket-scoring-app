package com.example.scoring

import android.util.Log
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Converters {
    private val gson = Gson()

    @TypeConverter
    @JvmStatic
    fun fromBallJson(value: String?): List<Ball?>? {
        if (value == null) return null
        return try {
            val listType = object : TypeToken<List<Ball?>?>() {}.type
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            Log.e("CONVERTER", "Ball JSON error: ${e.message}")
            emptyList()
        }
    }

    @TypeConverter
    @JvmStatic
    fun toBallJson(list: List<Ball?>?): String? {
        return gson.toJson(list)
    }

    @TypeConverter
    @JvmStatic
    fun fromFowJson(value: String?): List<FowEvent?>? {
        if (value == null) return null
        return try {
            val listType = object : TypeToken<List<FowEvent?>?>() {}.type
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            Log.e("CONVERTER", "FOW JSON error: ${e.message}")
            emptyList()
        }
    }

    @TypeConverter
    @JvmStatic
    fun toFowJson(list: List<FowEvent?>?): String? {
        return gson.toJson(list)
    }

    @TypeConverter
    @JvmStatic
    fun fromPshipJson(value: String?): List<PartnershipEvent?>? {
        if (value == null) return null
        return try {
            val listType = object : TypeToken<List<PartnershipEvent?>?>() {}.type
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            Log.e("CONVERTER", "Pship JSON error: ${e.message}")
            emptyList()
        }
    }

    @TypeConverter
    @JvmStatic
    fun toPshipJson(list: List<PartnershipEvent?>?): String? {
        return gson.toJson(list)
    }

    @TypeConverter
    @JvmStatic
    fun fromCommJson(value: String?): List<CommentaryEntry?>? {
        if (value == null) return null
        return try {
            val listType = object : TypeToken<List<CommentaryEntry?>?>() {}.type
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            Log.e("CONVERTER", "Comm JSON error: ${e.message}")
            emptyList()
        }
    }

    @TypeConverter
    @JvmStatic
    fun toCommJson(list: List<CommentaryEntry?>?): String? {
        return gson.toJson(list)
    }

    @TypeConverter
    @JvmStatic
    fun fromStringList(value: String?): List<String?>? {
        if (value == null) return null
        return try {
            val listType = object : TypeToken<List<String?>?>() {}.type
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            Log.e("CONVERTER", "StringList error: ${e.message}")
            emptyList()
        }
    }

    @TypeConverter
    @JvmStatic
    fun toStringList(list: List<String?>?): String? {
        return gson.toJson(list)
    }

    @TypeConverter
    @JvmStatic
    fun fromStringMap(value: String?): Map<String?, String?>? {
        if (value == null) return null
        return try {
            val mapType = object : TypeToken<Map<String?, String?>?>() {}.type
            gson.fromJson(value, mapType)
        } catch (e: Exception) {
            Log.e("CONVERTER", "StringMap error: ${e.message}")
            emptyMap()
        }
    }

    @TypeConverter
    @JvmStatic
    fun toStringMap(map: Map<String?, String?>?): String? {
        return gson.toJson(map)
    }
}

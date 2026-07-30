package com.example.scoring

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Converters {
    private val gson = Gson()

    @TypeConverter
    @JvmStatic
    fun fromBallJson(value: String?): List<Ball?>? {
        if (value == null) return null
        val listType = object : TypeToken<List<Ball?>?>() {}.type
        return gson.fromJson(value, listType)
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
        val listType = object : TypeToken<List<FowEvent?>?>() {}.type
        return gson.fromJson(value, listType)
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
        val listType = object : TypeToken<List<PartnershipEvent?>?>() {}.type
        return gson.fromJson(value, listType)
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
        val listType = object : TypeToken<List<CommentaryEntry?>?>() {}.type
        return gson.fromJson(value, listType)
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
        val listType = object : TypeToken<List<String?>?>() {}.type
        return gson.fromJson(value, listType)
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
        val mapType = object : TypeToken<Map<String?, String?>?>() {}.type
        return gson.fromJson(value, mapType)
    }

    @TypeConverter
    @JvmStatic
    fun toStringMap(map: Map<String?, String?>?): String? {
        return gson.toJson(map)
    }
}

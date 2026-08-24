package com.example.scoring

import android.util.Base64
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Phase 5: The Data Shrinker.
 * Uses GZIP compression to reduce match history size by ~95%.
 */
object MatchCompressor {

    private val gson = Gson()

    /**
     * Compresses heavy match components (Balls, Commentary, Partnerships) into a tiny Base64 string.
     */
    fun compressMatchData(data: Any?): String? {
        if (data == null) return null
        return try {
            val json = gson.toJson(data)
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { it.write(bytes) }
            
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decompresses the Base64 string back into original objects.
     */
    fun <T> decompressMatchData(compressed: String?, clazz: Class<T>): T? {
        if (compressed.isNullOrEmpty()) return null
        return try {
            val bytes = Base64.decode(compressed, Base64.DEFAULT)
            val bis = ByteArrayInputStream(bytes)
            val gis = GZIPInputStream(bis)
            
            val resultText = gis.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            gson.fromJson(resultText, clazz)
        } catch (e: Exception) {
            null
        }
    }
}

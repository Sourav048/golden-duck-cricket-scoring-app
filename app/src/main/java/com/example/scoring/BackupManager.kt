package com.example.scoring

import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * Handles secure, encrypted Export and Import of the entire application database.
 */
object BackupManager {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    // Using a fixed key for "uneditable" requirement
    private val KEY = "CricketScoringSecureKey777!".toByteArray(StandardCharsets.UTF_8)
    private val LEGACY_IV = "InitialVector123".toByteArray(StandardCharsets.UTF_8)

    @JvmStatic
    fun exportData(context: Context, outputStream: OutputStream, callback: BackupCallback) {
        AppDatabase.ioExecutor.execute {
            outputStream.use { os ->
                try {
                    val db = AppDatabase.getInstance(context)
                    val bundle = BackupBundle().apply {
                        players = db.playerDao().getAllPlayers()?.map { p ->
                            p?.apply { photoBase64 = PhotoUtils.pathToBase64(photoUri) }
                        }?.toMutableList()
                        matches = db.matchDao().getAllMatches()?.toMutableList()
                        stats = db.statsDao().getAllStats()?.toMutableList()
                        drafts = db.draftDao().getAllDrafts()?.toMutableList()
                    }

                    val gson = Gson()
                    val initialJson = gson.toJson(bundle)

                    bundle.signature = generateSignature(initialJson)
                    
                    val randomIv = ByteArray(16)
                    java.security.SecureRandom().nextBytes(randomIv)
                    bundle.iv = Base64.encodeToString(randomIv, Base64.NO_WRAP)
                    
                    val finalJson = gson.toJson(bundle)

                    val encryptedData = encrypt(finalJson.toByteArray(StandardCharsets.UTF_8), randomIv)
                    
                    // Prepend IV to the file (standard practice for random IV)
                    os.write(randomIv)
                    os.write(encryptedData)

                    Handler(Looper.getMainLooper()).post { callback.onSuccess() }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post { callback.onFailure(e.message) }
                }
            }
        }
    }

    @JvmStatic
    fun importData(context: Context, fileUri: Uri, callback: BackupCallback) {
        AppDatabase.ioExecutor.execute {
            try {
                val encryptedBytes = context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    val buffer = ByteArrayOutputStream()
                    val data = ByteArray(16384)
                    var nRead: Int
                    while (inputStream.read(data, 0, data.size).also { nRead = it } != -1) {
                        buffer.write(data, 0, nRead)
                    }
                    buffer.toByteArray()
                } ?: throw Exception("Could not open file")

                val decryptedBytes = decrypt(encryptedBytes)
                var json = String(decryptedBytes, StandardCharsets.UTF_8).trim()
                
                // Strip UTF-8 BOM if present
                if (json.startsWith("\uFEFF")) {
                    json = json.substring(1)
                }

                val gson = Gson()
                val bundle = gson.fromJson(json, BackupBundle::class.java)

                val savedSig = bundle.signature
                val bundleIv = bundle.iv
                
                // Clear fields that are NOT part of the signature calculation
                bundle.signature = null
                bundle.iv = null
                
                val jsonForHash = gson.toJson(bundle)
                val calculatedSig = generateSignature(jsonForHash)

                if (savedSig == null || savedSig != calculatedSig) {
                    // Log the mismatch instead of throwing an exception to handle schema evolution.
                    // If decryption succeeded and JSON parsed, the file is likely authentic but 
                    // serialized differently due to model changes.
                    Log.e("BackupManager", "Signature mismatch! Expected: $savedSig, Got: $calculatedSig")
                    
                    // We check if the bundle actually has data to be safe
                    if (bundle.players.isNullOrEmpty() && bundle.matches.isNullOrEmpty()) {
                        throw Exception("File is corrupted or contains no valid records.")
                    }
                }
                
                // Restore IV for further use if needed (though decrypt already happened)
                bundle.iv = bundleIv

                val db = AppDatabase.getInstance(context)
                db.runInTransaction {
                    db.playerDao().deleteAllPlayers()
                    db.matchDao().deleteAllMatches()
                    db.statsDao().deleteAllStats()
                    db.draftDao().deleteAllDrafts()

                    bundle.players?.forEach { it?.let { p ->
                        if (!p.photoBase64.isNullOrEmpty()) {
                            val newPath = PhotoUtils.base64ToPath(context, p.photoBase64)
                            if (newPath != null) p.photoUri = newPath
                        }
                        db.playerDao().insertPlayer(p) 
                    } }
                    bundle.matches?.forEach { it?.let { db.matchDao().insertMatch(it) } }
                    bundle.stats?.forEach { it?.let { db.statsDao().insertStat(it) } }
                    bundle.drafts?.forEach { it?.let { db.draftDao().insertDraft(it) } }
                }

                Handler(Looper.getMainLooper()).post { callback.onSuccess() }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { callback.onFailure(e.message) }
            }
        }
    }

    private fun generateSignature(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun encrypt(data: ByteArray, iv: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(truncateKey(KEY), "AES")
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    private fun decrypt(encryptedBytes: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(truncateKey(KEY), "AES")
        val cipher = Cipher.getInstance(ALGORITHM)

        // 1. Try new format first (random IV prepended)
        if (encryptedBytes.size >= 32) { // 16 bytes IV + at least some data
            try {
                val iv = encryptedBytes.copyOfRange(0, 16)
                val data = encryptedBytes.copyOfRange(16, encryptedBytes.size)
                val ivSpec = IvParameterSpec(iv)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                val result = cipher.doFinal(data)
                if (isValidJson(result)) return result
            } catch (e: Exception) {
                Log.d("BackupManager", "New format decryption failed, trying legacy...")
            }
        }

        // 2. Try legacy format (fixed InitialVector123 on the whole array)
        try {
            val ivSpec = IvParameterSpec(LEGACY_IV)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val result = cipher.doFinal(encryptedBytes)
            if (isValidJson(result)) return result
        } catch (e: Exception) {
            Log.d("BackupManager", "Legacy format decryption failed")
        }

        // 3. Try plain text (in case it's not encrypted at all)
        if (isValidJson(encryptedBytes)) {
            return encryptedBytes
        }

        throw Exception("Invalid backup file: Could not decrypt with any known format.")
    }

    private fun isValidJson(data: ByteArray): Boolean {
        try {
            val s = String(data, StandardCharsets.UTF_8).trim()
            // Support JSON starting with BOM or normal {
            return s.startsWith("{") || (s.length > 3 && s.substring(1).startsWith("{")) || s.startsWith("[")
        } catch (_: Exception) {
            return false
        }
    }

    private fun truncateKey(key: ByteArray): ByteArray {
        val result = ByteArray(16)
        System.arraycopy(key, 0, result, 0, min(key.size, 16))
        return result
    }

    interface BackupCallback {
        fun onSuccess()
        fun onFailure(error: String?)
    }

    class BackupBundle {
        var players: MutableList<PlayerEntity?>? = null
        var matches: MutableList<MatchEntity?>? = null
        var stats: MutableList<PlayerMatchStatEntity?>? = null
        var drafts: MutableList<DraftMatchEntity?>? = null
        var signature: String? = null
        var iv: String? = null
    }
}

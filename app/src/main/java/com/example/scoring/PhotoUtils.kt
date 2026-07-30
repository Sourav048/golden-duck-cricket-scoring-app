package com.example.scoring

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.annotation.Keep
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

@Keep
object PhotoUtils {
    private const val PHOTO_DIR = "player_photos"

    /**
     * Saves a photo from a Uri (Gallery) or File (Camera) to internal storage.
     * Returns the absolute path of the saved file.
     */
    fun savePhoto(context: Context, sourceUri: Uri): String? {
        try {
            val dir = File(context.filesDir, PHOTO_DIR)
            if (!dir.exists()) dir.mkdirs()

            val fileName = "player_${UUID.randomUUID()}.jpg"
            val destFile = File(dir, fileName)

            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            inputStream?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return destFile.absolutePath
        } catch (e: Exception) {
            Log.e("PHOTO_UTILS", "Failed to save photo: ${e.message}")
            return null
        }
    }

    /**
     * Converts a file path to a Base64 string for export.
     */
    fun pathToBase64(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        try {
            val file = File(path)
            if (!file.exists()) return null
            
            val bitmap = BitmapFactory.decodeFile(path) ?: return null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("PHOTO_UTILS", "Failed to convert to Base64: ${e.message}")
            return null
        }
    }

    /**
     * Saves a Base64 string back to a file during import.
     */
    fun base64ToPath(context: Context, base64: String?): String? {
        if (base64.isNullOrEmpty()) return null
        try {
            val dir = File(context.filesDir, PHOTO_DIR)
            if (!dir.exists()) dir.mkdirs()

            val fileName = "player_${UUID.randomUUID()}.jpg"
            val destFile = File(dir, fileName)

            val bytes = Base64.decode(base64, Base64.DEFAULT)
            FileOutputStream(destFile).use { it.write(bytes) }
            return destFile.absolutePath
        } catch (e: Exception) {
            Log.e("PHOTO_UTILS", "Failed to save Base64 to file: ${e.message}")
            return null
        }
    }
    
    /**
     * Checks if a path is actually a valid file in our internal storage.
     */
    fun isValidInternalPath(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val file = File(path)
        return file.exists() && file.isFile
    }
}

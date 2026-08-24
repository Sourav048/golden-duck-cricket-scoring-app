package com.example.scoring

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
    private const val MAX_SIZE = 400 // Max dimension in pixels for "Image Guard"
    private const val QUALITY = 75   // Professional compression balance

    /**
     * Saves a photo with "Image Guard" (Auto-Resize & Compression).
     * This keeps the app fast and stays within the 100% FREE cloud tier.
     */
    fun savePhoto(context: Context, sourceUri: Uri): String? {
        try {
            val dir = File(context.filesDir, PHOTO_DIR)
            if (!dir.exists()) dir.mkdirs()

            val fileName = "player_${UUID.randomUUID()}.jpg"
            val destFile = File(dir, fileName)

            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            inputStream?.close()

            // 1. Resize for "Image Guard"
            val processedBitmap = resizeBitmap(originalBitmap, MAX_SIZE)

            // 2. Compress and Save
            FileOutputStream(destFile).use { output ->
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)
            }
            
            originalBitmap.recycle()
            if (processedBitmap != originalBitmap) processedBitmap.recycle()

            return destFile.absolutePath
        } catch (e: Exception) {
            Log.e("PHOTO_UTILS", "Failed to save photo: ${e.message}")
            return null
        }
    }

    private fun resizeBitmap(bm: Bitmap, maxDim: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        
        if (width <= maxDim && height <= maxDim) return bm

        val scale = if (width > height) {
            maxDim.toFloat() / width
        } else {
            maxDim.toFloat() / height
        }

        val matrix = Matrix()
        matrix.postScale(scale, scale)
        
        return Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true)
    }

    /**
     * Converts a photo to a Base64 string for cloud sync.
     * Extremely resilient: handles raw paths, file URIs, and content URIs.
     */
    fun pathToBase64(context: Context, path: String?): String? {
        if (path.isNullOrEmpty()) return null
        
        var bitmap: Bitmap? = null
        try {
            // 1. Try decoding as a direct file path first (Fastest for internal storage)
            val file = File(path)
            if (file.exists() && file.isFile) {
                bitmap = BitmapFactory.decodeFile(path)
                Log.d("PHOTO_UTILS", "Decoded via direct path: $path")
            }

            // 2. Fallback to ContentResolver if direct path fails or it's a URI
            if (bitmap == null) {
                val uri = if (path.startsWith("content://") || path.startsWith("file://")) {
                    Uri.parse(path)
                } else {
                    Uri.fromFile(File(path))
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream)
                    Log.d("PHOTO_UTILS", "Decoded via ContentResolver: $path")
                }
            }

            if (bitmap == null) {
                Log.e("PHOTO_UTILS", "All decoding attempts failed for: $path")
                return null
            }

            // 3. Resize for Sync (Max 300px for better quality than before)
            val processedBitmap = resizeBitmap(bitmap!!, 300)
            
            val outputStream = ByteArrayOutputStream()
            // Using PNG for perfect transparency/quality, or high-quality JPEG
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            // Cleanup
            if (processedBitmap != bitmap) processedBitmap.recycle()
            bitmap!!.recycle()
            
            Log.d("PHOTO_UTILS", "Successfully converted to Base64 (Size: ${bytes.size} bytes)")
            return base64

        } catch (e: Exception) {
            Log.e("PHOTO_UTILS", "Critical failure in pathToBase64: ${e.message}")
            bitmap?.recycle()
            return null
        }
    }

    /**
     * Saves a Base64 string back to a file during import.
     * Uses Player ID to prevent duplicate files and "Ghost Photos".
     */
    fun base64ToPath(context: Context, base64: String?, playerId: String): String? {
        if (base64.isNullOrEmpty()) return null
        try {
            val dir = File(context.filesDir, PHOTO_DIR)
            if (!dir.exists()) dir.mkdirs()

            val fileName = "player_${playerId}.jpg"
            val destFile = File(dir, fileName)

            val bytes = Base64.decode(base64, Base64.DEFAULT)
            FileOutputStream(destFile).use { it.write(bytes) }
            return destFile.absolutePath
        } catch (e: Exception) {
            Log.e("PHOTO_UTILS", "Failed to save Base64 to file: ${e.message}")
            return null
        }
    }
    
    fun isValidInternalPath(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val file = File(path)
        return file.exists() && file.isFile
    }
}

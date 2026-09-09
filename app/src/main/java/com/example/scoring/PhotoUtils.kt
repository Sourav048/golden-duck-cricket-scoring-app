package com.example.scoring

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.annotation.Keep
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.Executors

@Keep
object PhotoUtils {
    private const val PHOTO_DIR = "player_photos"
    private const val MAX_SIZE = 400 // Max dimension in pixels for "Image Guard"
    private const val QUALITY = 75   // Professional compression balance

    /**
     * Scans and auto-fixes all existing saved player photos that are in landscape mode (width > height),
     * rotating them 90° clockwise into upright portrait mode automatically.
     */
    fun fixAllExistingPhotos(context: Context) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val dir = File(context.filesDir, PHOTO_DIR)
                if (!dir.exists() || !dir.isDirectory) return@execute

                val files = dir.listFiles() ?: return@execute
                var count = 0
                for (file in files) {
                    if (file.isFile && (file.name.endsWith(".jpg") || file.name.endsWith(".jpeg"))) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                        if (bitmap.width > bitmap.height) {
                            Log.d("PHOTO_UTILS", "Auto-fixing sideways photo: ${file.name}")
                            val matrix = Matrix()
                            matrix.postRotate(90f)
                            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                            FileOutputStream(file).use { out ->
                                rotated.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                            }
                            bitmap.recycle()
                            if (rotated != bitmap) rotated.recycle()
                            count++
                        } else {
                            bitmap.recycle()
                        }
                    }
                }
                Log.d("PHOTO_UTILS", "Finished auto-fixing existing player photos. Fixed $count photos.")
            } catch (e: Exception) {
                Log.e("PHOTO_UTILS", "Failed to fix existing photos: ${e.message}")
            }
        }
    }

    /**
     * Saves a photo with "Image Guard" (Auto-Resize, EXIF Orientation Correction & Compression).
     * Automatically fixes landscape rotation issue on photos taken in portrait mode.
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

            // 1. Correct EXIF Rotation (fixes sideways/landscape photos)
            var orientedBitmap = rotateBitmapIfRequired(context, originalBitmap, sourceUri)

            // 2. Fallback check: If width > height, force 90° rotation to portrait
            if (orientedBitmap.width > orientedBitmap.height) {
                val matrix = Matrix()
                matrix.postRotate(90f)
                val upright = Bitmap.createBitmap(orientedBitmap, 0, 0, orientedBitmap.width, orientedBitmap.height, matrix, true)
                if (upright != orientedBitmap) orientedBitmap.recycle()
                orientedBitmap = upright
            }

            // 3. Resize for "Image Guard"
            val processedBitmap = resizeBitmap(orientedBitmap, MAX_SIZE)

            // 4. Compress and Save
            FileOutputStream(destFile).use { output ->
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)
            }
            
            if (originalBitmap != orientedBitmap) originalBitmap.recycle()
            if (processedBitmap != orientedBitmap) orientedBitmap.recycle()

            return destFile.absolutePath
        } catch (e: Exception) {
            Log.e("PHOTO_UTILS", "Failed to save photo: ${e.message}")
            return null
        }
    }

    private fun rotateBitmapIfRequired(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()

            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (degrees != 0f) {
                val matrix = Matrix()
                matrix.postRotate(degrees)
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("PHOTO_UTILS", "Failed to check EXIF orientation: ${e.message}")
            bitmap
        }
    }

    private fun rotateBitmapFromFile(path: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (degrees != 0f) {
                val matrix = Matrix()
                matrix.postRotate(degrees)
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            bitmap
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
     * Extremely resilient: handles raw paths, file URIs, and content URIs, auto-correcting orientation.
     */
    fun pathToBase64(context: Context, path: String?): String? {
        if (path.isNullOrEmpty()) return null
        
        var bitmap: Bitmap? = null
        try {
            // 1. Try decoding as a direct file path first
            val file = File(path)
            if (file.exists() && file.isFile) {
                val raw = BitmapFactory.decodeFile(path)
                if (raw != null) {
                    bitmap = rotateBitmapFromFile(path, raw)
                }
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
                    val raw = BitmapFactory.decodeStream(stream)
                    if (raw != null) {
                        bitmap = rotateBitmapIfRequired(context, raw, uri)
                    }
                    Log.d("PHOTO_UTILS", "Decoded via ContentResolver: $path")
                }
            }

            if (bitmap == null) {
                Log.e("PHOTO_UTILS", "All decoding attempts failed for: $path")
                return null
            }

            // Fallback check: if width > height, force 90° rotation to portrait
            if (bitmap!!.width > bitmap!!.height) {
                val matrix = Matrix()
                matrix.postRotate(90f)
                val upright = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true)
                if (upright != bitmap) bitmap!!.recycle()
                bitmap = upright
            }

            // 3. Resize for Sync (Max 300px)
            val processedBitmap = resizeBitmap(bitmap!!, 300)
            
            val outputStream = ByteArrayOutputStream()
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
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null && bitmap.width > bitmap.height) {
                val matrix = Matrix()
                matrix.postRotate(90f)
                val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (upright != bitmap) bitmap.recycle()
                bitmap = upright
            }

            FileOutputStream(destFile).use { out ->
                if (bitmap != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                } else {
                    out.write(bytes)
                }
            }
            bitmap?.recycle()
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

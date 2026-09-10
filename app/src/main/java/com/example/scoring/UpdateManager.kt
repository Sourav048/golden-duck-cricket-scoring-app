package com.example.scoring

import android.R
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.util.Locale

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val CONFIG_COLLECTION = "app_config"
    private const val CONFIG_DOCUMENT = "update"

    /**
     * Checks Firebase Firestore for available app updates.
     * Compares local app versionCode with Firestore's latest_version_code.
     */
    fun checkForUpdates(activity: Activity) {
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection(CONFIG_COLLECTION).document(CONFIG_DOCUMENT)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val latestVersionCode = snapshot.getLong("latest_version_code") ?: 0L
                        val versionName = snapshot.getString("version_name") ?: ""
                        val downloadUrl = snapshot.getString("download_url") ?: ""
                        val releaseNotes = snapshot.getString("release_notes") ?: ""
                        val forceUpdate = snapshot.getBoolean("force_update") ?: false

                        val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                        val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

                        Log.d(TAG, "Current versionCode: $currentVersionCode, Latest versionCode: $latestVersionCode")

                        if (latestVersionCode > currentVersionCode && downloadUrl.isNotBlank()) {
                            showUpdateDialog(
                                activity = activity,
                                versionName = versionName,
                                releaseNotes = releaseNotes,
                                downloadUrl = downloadUrl,
                                forceUpdate = forceUpdate
                            )
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to check for updates from Firestore", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for app updates", e)
        }
    }

    private fun showUpdateDialog(
        activity: Activity,
        versionName: String,
        releaseNotes: String,
        downloadUrl: String,
        forceUpdate: Boolean
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val message = StringBuilder()
        if (releaseNotes.isNotBlank()) {
            message.append("What's New:\n").append(releaseNotes).append("\n\n")
        }
        message.append("A new update is available. Would you like to download and install it now?")

        val title = if (versionName.isNotBlank()) "Update Available ($versionName)" else "Update Available"

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message.toString())
            .setPositiveButton("Update Now") { dialog, _ ->
                dialog.dismiss()
                handleUpdateProcess(activity, downloadUrl)
            }
            .setCancelable(!forceUpdate)

        if (!forceUpdate) {
            builder.setNegativeButton("Later", null)
        }

        builder.show()
    }

    private fun handleUpdateProcess(activity: Activity, downloadUrl: String) {
        // Start the APK download with real-time progress dialog
        downloadAndInstallApk(activity, downloadUrl)

        // Check if Android 8.0+ unknown sources permission is needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Permission Required")
                .setMessage("Golden Duck is downloading the update in the background.\n\nTo install it, please enable 'Allow from this source' on the next screen.")
                .setPositiveButton("Open Settings") { dialog, _ ->
                    dialog.dismiss()
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}")
                        )
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch manage unknown app sources settings", e)
                    }
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    fun downloadAndInstallApk(activity: Activity, downloadUrl: String) {
        val context = activity.applicationContext
        try {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val destinationFile = File(cacheDir, "GoldenDuck_Update.apk")

            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Downloading Golden Duck Update")
                setDescription("Please wait while the update file is downloading...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadId = downloadManager.enqueue(request)

            // Create Progress Dialog UI inside the app
            val progressBar = ProgressBar(activity, null, R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = false
                max = 100
                progress = 0
            }

            val tvProgress = TextView(activity).apply {
                text = "Starting download..."
                setPadding(0, 16, 0, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }

            val layout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 32, 48, 16)
                addView(progressBar)
                addView(tvProgress)
            }

            val progressDialog = MaterialAlertDialogBuilder(activity)
                .setTitle("Downloading Update")
                .setView(layout)
                .setCancelable(false)
                .show()

            val handler = Handler(Looper.getMainLooper())
            var isDownloading = true

            // Poll DownloadManager every 300ms to update progress bar UI
            val progressRunnable = object : Runnable {
                override fun run() {
                    if (!isDownloading) return
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                        if (bytesDownloadedIdx != -1 && bytesTotalIdx != -1) {
                            val downloaded = cursor.getLong(bytesDownloadedIdx)
                            val total = cursor.getLong(bytesTotalIdx)
                            val status = if (statusIdx != -1) cursor.getInt(statusIdx) else 0

                            if (total > 0) {
                                val percent = ((downloaded * 100) / total).toInt()
                                val downloadedMb = String.format(Locale.US, "%.1f", downloaded / (1024f * 1024f))
                                val totalMb = String.format(Locale.US, "%.1f", total / (1024f * 1024f))

                                progressBar.progress = percent
                                tvProgress.text = "Downloaded $percent% ($downloadedMb MB / $totalMb MB)"
                            }

                            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                                isDownloading = false
                                try { progressDialog.dismiss() } catch (e: Exception) { Log.e(TAG, "Dialog dismiss error", e) }
                                cursor.close()
                                return
                            }
                        }
                        cursor.close()
                    }
                    if (isDownloading) {
                        handler.postDelayed(this, 300)
                    }
                }
            }

            handler.post(progressRunnable)

            val onCompleteReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id == downloadId) {
                        isDownloading = false
                        try { progressDialog.dismiss() } catch (e: Exception) { Log.e(TAG, "Dialog dismiss error", e) }
                        try {
                            context.applicationContext.unregisterReceiver(this)
                        } catch (e: Exception) {
                            Log.e(TAG, "Receiver unregister error", e)
                        }

                        if (destinationFile.exists()) {
                            installApk(context, destinationFile)
                        } else {
                            Toast.makeText(context, "Update download failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            ContextCompat.registerReceiver(
                context.applicationContext,
                onCompleteReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error initiating APK download", e)
            Toast.makeText(context, "Failed to start update download: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            Toast.makeText(context, "Failed to open update file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

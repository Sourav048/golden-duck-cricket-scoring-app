package com.goldenduck.scoring

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.text.HtmlCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.goldenduck.scoring.AppDatabase.Companion.getInstance
import com.goldenduck.scoring.BackupManager.BackupCallback
import com.goldenduck.scoring.BackupManager.exportData
import com.goldenduck.scoring.RankingRegistry.refresh
import kotlinx.coroutines.launch
import com.goldenduck.scoring.SecurityUtils.AuthCallback
import com.goldenduck.scoring.SecurityUtils.authenticate
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.abs

class HomeActivity : BaseActivity() {

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            // PROTECTED MANUAL IMPORT: Always backup before replacing data
            performAutoBackupAndImport(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<View>(R.id.cardNewMatch).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        findViewById<View>(R.id.btnHomeMenu).setOnClickListener { v -> showHomeMenu(v) }

        findViewById<View>(R.id.btnHomeAiAssistant)?.setOnClickListener {
            startActivity(Intent(this, AiChatActivity::class.java))
        }

        findViewById<View>(R.id.btnHomeLeagueChat)?.setOnClickListener { openLeagueChat() }

        findViewById<View>(R.id.cardHistory).setOnClickListener {
            startActivity(Intent(this, MatchHistoryActivity::class.java))
        }

        findViewById<View>(R.id.cardPlayers).setOnClickListener {
            startActivity(Intent(this, PlayerListActivity::class.java))
        }

        findViewById<View>(R.id.cardStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }

        findViewById<View>(R.id.cardGully).setOnClickListener {
            startActivity(Intent(this, GullyManagementActivity::class.java))
        }

        // Load rankings on startup
        StatsRecalculator.cleanDuplicateMatchStats(this)
        refresh(this, null)

        // Request OneSignal Notification Permissions & Subscribe all joined leagues
        try {
            val gullyPrefs = getSharedPreferences("gully_prefs", MODE_PRIVATE)
            val myUserId = gullyPrefs.getString("chat_sender_id", null)
            val allGullies = GullyHistoryManager.getGullies(this)
            for (g in allGullies) {
                LeagueNotificationManager.subscribeToLeague(g.id, myUserId)
            }
            CoroutineScope(Dispatchers.Main).launch {
                OneSignal.Notifications.requestPermission(true)
            }
        } catch (e: Exception) {
            Log.e("HomeNotif", "Error requesting notification permission / subscribing tags: ${e.message}")
        }

        // START GULLY SYNC ENGINE
        try {
            val currentGully = getSharedPreferences("gully_prefs", MODE_PRIVATE).getString("current_gully_id", null)
            if (currentGully != null) {
                GullySyncManager.startSync(this, currentGully)
            }
        } catch (e: Exception) {
            Log.e("GULLY_SYNC", "Failed to start sync: ${e.message}")
        }

        // Clean up duplicate match stat records from DB if any exist
        StatsRecalculator.cleanDuplicateMatchStats(this)

        handleIncomingFileIntent(intent)
        setupSwipeGesture()

        // Check for app updates via Firestore
        UpdateManager.checkForUpdates(this)
    }

    private lateinit var gestureDetector: GestureDetector

    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 120
            private val SWIPE_VELOCITY_THRESHOLD = 120

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                // Detect horizontal swipe from Right to Left (Swipe Left)
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) &&
                    kotlin.math.abs(diffX) > SWIPE_THRESHOLD &&
                    kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                ) {
                    if (diffX < 0) {
                        handleSwipeToLeagueChat()
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    @Suppress("DEPRECATION")
    private fun handleSwipeToLeagueChat() {
        val currentGully = GullySyncManager.getCurrentGullyId(this)
        if (currentGully.isNullOrEmpty() || currentGully == "local") {
            Toast.makeText(this, "Local Mode: Join or create a League for League Chat", Toast.LENGTH_SHORT).show()
            openLeagueChat()
        } else {
            val intent = Intent(this, LeagueChatActivity::class.java)
            intent.putExtra("LEAGUE_ID", currentGully)
            startActivity(intent)
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }

    private val chatBadgeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread {
                updateLeagueChatBadge()
            }
        }
    }

    private val notifPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("queue_") || key == "active_leagues")) {
            runOnUiThread {
                updateLeagueChatBadge()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateGullyStatusUI()
        updateLeagueChatBadge()
        syncNotificationPermissionWithOneSignal()
        try {
            ContextCompat.registerReceiver(
                this,
                chatBadgeReceiver,
                IntentFilter(ChatNotificationHelper.ACTION_UPDATE_CHAT_BADGE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}

        try {
            val prefs = getSharedPreferences("chat_notif_prefs", MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(notifPrefListener)
        } catch (_: Exception) {}
    }

    private fun syncNotificationPermissionWithOneSignal() {
        try {
            val isEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
            val gullyPrefs = getSharedPreferences("gully_prefs", MODE_PRIVATE)
            val myUserId = gullyPrefs.getString("chat_sender_id", null)

            if (isEnabled) {
                if (!myUserId.isNullOrEmpty()) {
                    try {
                        OneSignal.login(myUserId)
                        OneSignal.User.addTag("user_id", myUserId)
                        OneSignal.User.removeTag("user_$myUserId")
                    } catch (_: Exception) {}
                }

                val allGullies = GullyHistoryManager.getGullies(this)
                LeagueNotificationManager.syncAllSubscribedLeagues(this, allGullies.map { it.id }, myUserId)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        OneSignal.Notifications.requestPermission(true)
                    } catch (e: Exception) {
                        Log.e("HomeNotif", "Error syncing notification state: ${e.message}")
                    }
                }
            } else {
                // If notifications are disabled/not subscribed, prompt user every 5 hours
                val fiveHoursMs = 5 * 60 * 60 * 1000L
                val notifPrefs = getSharedPreferences("chat_notif_prefs", MODE_PRIVATE)
                val lastPromptTime = notifPrefs.getLong("last_notif_permission_prompt_time", 0L)
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastPromptTime >= fiveHoursMs) {
                    notifPrefs.edit().putLong("last_notif_permission_prompt_time", currentTime).apply()

                    MaterialAlertDialogBuilder(this)
                        .setTitle("Enable Notifications 🔔")
                        .setMessage("Stay connected with your league! Enable notifications to receive live score updates, match alerts, and chat messages.")
                        .setPositiveButton("Enable Now") { _, _ ->
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    OneSignal.Notifications.requestPermission(true)
                                } catch (_: Exception) {}
                            }
                        }
                        .setNegativeButton("Later", null)
                        .show()
                }
            }
        } catch (e: Exception) {
            Log.e("HomeNotif", "Error in syncNotificationPermissionWithOneSignal: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(chatBadgeReceiver)
        } catch (_: Exception) {}

        try {
            val prefs = getSharedPreferences("chat_notif_prefs", MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(notifPrefListener)
        } catch (_: Exception) {}
    }

    private fun updateLeagueChatBadge() {
        val tvBadge = findViewById<TextView>(R.id.tvLeagueChatBadge) ?: return
        val count = ChatNotificationHelper.getUnreadMessageCount(this)
        if (count > 0) {
            tvBadge.text = if (count > 99) "99+" else count.toString()
            tvBadge.setTextColor(Color.WHITE)
            tvBadge.visibility = View.VISIBLE
        } else {
            tvBadge.visibility = View.GONE
        }
    }

    private fun updateGullyStatusUI() {
        val tvStatus = findViewById<TextView>(R.id.tvHomeGullyStatus) ?: return
        val prefs = getSharedPreferences("gully_prefs", MODE_PRIVATE)
        val currentGully = prefs.getString("current_gully_id", null)
        
        if (currentGully != null) {
            tvStatus.text = "Connected: $currentGully"
            tvStatus.setTextColor(ThemeManager.getSeedColor(this))
        } else {
            tvStatus.text = "Local Mode (Offline)"
            val tv = TypedValue()
            val color = if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true)) tv.data else android.graphics.Color.GRAY
            tvStatus.setTextColor(color)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingFileIntent(intent)
    }

    private fun handleIncomingFileIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return

        var fileName = "records_${System.currentTimeMillis()}.cricket"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!fileName.endsWith(".cricket")) {
            fileName += ".cricket"
        }

        val targetSubDir = "Golden Duck - A Cricket Scoring App"
        
        if (checkIfFileExistsInDownloads(fileName, targetSubDir)) {
            Toast.makeText(this, "Records already saved in Downloads/$targetSubDir", Toast.LENGTH_LONG).show()
        } else {
            copyUriToDownloads(uri, fileName, targetSubDir)
        }
    }

    private fun checkIfFileExistsInDownloads(fileName: String, subDir: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$subDir/$fileName")
            return file.exists()
        }

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(fileName, "%$subDir%")

        try {
            contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                return cursor.count > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun copyUriToDownloads(sourceUri: Uri, fileName: String, subDir: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subDir")
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), subDir)
                if (!dir.exists()) dir.mkdirs()
                MediaStore.Files.getContentUri("external")
            }

            val destUri = contentResolver.insert(collection, values)
            if (destUri != null) {
                contentResolver.openInputStream(sourceUri)?.use { input ->
                    contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(this, "Records saved to Downloads/$subDir", Toast.LENGTH_LONG).show()
                // Proceed to Instant Import Flow with Auto-Safety Net
                showInstantImportPrompt(destUri, fileName)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to auto-save records: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInstantImportPrompt(fileUri: Uri, fileName: String) {
        showDynamicDialog {
            setTitle("New Records Detected")
            setMessage("We found new cricket records in \"$fileName\". Would you like to import them into the app now?")
            setNegativeButton("NO") { d, _ -> d.dismiss() }
            setPositiveButton("YES, IMPORT") { _, _ -> showSafetyBackupWarning(fileUri) }
        }
    }

    private fun showSafetyBackupWarning(fileUri: Uri) {
        showDynamicDialog {
            setTitle("⚠️ Security Confirmation")
            setMessage("Replacing current match records. For your safety, a professional backup of your existing data will be automatically saved to your Downloads folder before we proceed.")
            setNegativeButton("CANCEL", null)
            setPositiveButton("SAFE IMPORT") { _, _ -> performAutoBackupAndImport(fileUri) }
        }
    }

    private fun performAutoBackupAndImport(importUri: Uri) {
        // 1. SILENT AUTO-BACKUP with readable Date/Time
        val sdf = java.text.SimpleDateFormat("dd_MMM_yyyy_HH_mm_ss", java.util.Locale.getDefault())
        val dateStr = sdf.format(java.util.Date())
        val backupName = "AUTO_BACKUP_BEFORE_IMPORT_$dateStr.cricket"
        
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, backupName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Golden Duck - A Cricket Scoring App")
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Golden Duck - A Cricket Scoring App")
                if (!dir.exists()) dir.mkdirs()
                MediaStore.Files.getContentUri("external")
            }

            val backupUri = contentResolver.insert(collection, values)
            if (backupUri != null) {
                val os = contentResolver.openOutputStream(backupUri)
                if (os != null) {
                    exportData(this, os, object : BackupCallback {
                        override fun onSuccess() {
                            // 2. BACKUP SUCCESSFUL -> NOW DO THE ACTUAL IMPORT
                            executeFinalImport(importUri)
                        }
                        override fun onFailure(e: String?) {
                            Toast.makeText(this@HomeActivity, "Safety Backup failed. Import aborted to prevent data loss.", Toast.LENGTH_LONG).show()
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Auto-Backup error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeFinalImport(uri: Uri) {
        BackupManager.importData(this, uri, object : BackupCallback {
            override fun onSuccess() {
                Toast.makeText(this@HomeActivity, "Records Imported Successfully! (Old data backed up to Downloads)", Toast.LENGTH_LONG).show()
                refresh(this@HomeActivity, null)
            }
            override fun onFailure(error: String?) {
                Toast.makeText(this@HomeActivity, "Import Failed: $error", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showHomeMenu(v: View? = null) {
        val bottomSheet = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_home_menu, null, false)
        bottomSheet.setContentView(dialogView)

        bottomSheet.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.background = null
        }

        dialogView.findViewById<View>(R.id.btnCloseHomeMenu)?.setOnClickListener {
            bottomSheet.dismiss()
        }

        dialogView.findViewById<View>(R.id.itemExportRecords)?.setOnClickListener {
            bottomSheet.dismiss()
            checkSecurityAndLaunchExport()
        }

        dialogView.findViewById<View>(R.id.itemImportRecords)?.setOnClickListener {
            bottomSheet.dismiss()
            checkSecurityAndShowImportWarning()
        }

        dialogView.findViewById<View>(R.id.itemThemeMode)?.setOnClickListener {
            bottomSheet.dismiss()
            showThemeSettings()
        }

        dialogView.findViewById<View>(R.id.itemReadMe)?.setOnClickListener {
            bottomSheet.dismiss()
            showReadMeDialog()
        }

        dialogView.findViewById<View>(R.id.itemDeleteRecords)?.setOnClickListener {
            bottomSheet.dismiss()
            showDeleteWarning()
        }

        bottomSheet.show()
    }

    private fun openLeagueChat() {
        val currentGully = GullySyncManager.getCurrentGullyId(this)
        if (currentGully.isNullOrEmpty() || currentGully == "local") {
            AlertDialog.Builder(this)
                .setTitle("League Chat")
                .setMessage("You are currently in Local Mode. Please join or create a League (Gully) to chat with other players!")
                .setPositiveButton("Go to Leagues") { _, _ ->
                    startActivity(Intent(this, GullyManagementActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            val intent = Intent(this, LeagueChatActivity::class.java)
            intent.putExtra("LEAGUE_ID", currentGully)
            startActivity(intent)
        }
    }

    private fun showReadMeDialog() {
        val manualText = """
            <h3><b>🏆 Golden Duck - User Manual & Features Guide</b></h3>
            <p>Welcome to <b>Golden Duck Cricket Scorer & Community</b>! Here is a comprehensive overview of all features and capabilities available in the app.</p>

            <hr>
            <h3><b>🏏 Live Scoring & Match Engine</b></h3>
            <p>
            <ul>
                <li><b>Ball-by-Ball Live Scoring:</b> Track runs (0, 1, 2, 3, 4, 6, manual runs), extras (Wide, No Ball, Bye, Leg Bye), and custom run breakdowns (runs off bat vs extras).</li>
                <li><b>Wicket Types:</b> Bowled, Caught, LBW, Run Out (striker/non-striker end with fielder tagging), Stumped, Hit Wicket, Obstructing, Retired Hurt, and Retired Out.</li>
                <li><b>Flexible Match Modes:</b> Standard League matches, 1v1 Duels, and Gully Cricket.</li>
                <li><b>Gully Cricket Custom Rules:</b> Support for custom gully rules like Direct Hit Out, One Touch Out, Last Man Standing, and Boundary/Wall rules.</li>
                <li><b>Custom Ball Types:</b> Leather, Stumper, Red Tennis, Green Tennis, Wind Ball, Rubber, and Plastic ball modes.</li>
                <li><b>Duckworth-Lewis-Stern (DLS):</b> Automatic target calculation for rain-affected or reduced-overs matches.</li>
                <li><b>Undo Last Ball & Auto-Drafts:</b> Single-tap "Undo Last Ball" to correct entry errors; active matches auto-save as drafts so you can resume anytime from Match History.</li>
                <li><b>Live Visualizations:</b> Real-time over timeline, ball-by-ball commentary, run-rate progression charts, and partnership graphs.</li>
            </ul>
            </p>

            <hr>
            <h3><b>💬 Live League Chat & Messaging</b></h3>
            <p>
            <ul>
                <li><b>Real-Time In-App Chat:</b> Communicate live with all members inside your joined Gully leagues.</li>
                <li><b>Voice Notes & Audio Messages:</b> Record and send voice messages with an interactive play, pause, and seek audio bar.</li>
                <li><b>Rich Media Sharing:</b> Share photos from camera/gallery in high resolution (stored in 4K UHD on cloud) and play full-screen videos.</li>
                <li><b>Smart Player Mentions (@):</b> Type @ to trigger player suggestions with avatar previews and position tags.</li>
                <li><b>Quote Replies & Emojis/GIFs:</b> Long-press to quote reply; react with animated emojis or search and send GIFs.</li>
                <li><b>In-App Web Viewer:</b> Tap links to browse shared web pages directly inside the app without leaving chat.</li>
                <li><b>Notification Bar Direct Reply:</b> Respond to incoming league chat messages directly from Android system notifications.</li>
            </ul>
            </p>

            <hr>
            <h3><b>🤖 Duck AI - Intelligent Cricket Assistant</b></h3>
            <p>
            <ul>
                <li><b>AI Cricket Assistant:</b> Ask anything about cricket rules, match conditions, player performance, strategy, or DLS calculations.</li>
                <li><b>Smart Function Integration:</b> AI can fetch player stats, compare two players, analyze match situations, and project final scores directly from your app database.</li>
                <li><b>Background Service:</b> Duck AI runs in a seamless floating/background chat session while you navigate the app.</li>
            </ul>
            </p>

            <hr>
            <h3><b>📊 Player Analytics, Radar Charts & Head-to-Head</b></h3>
            <p>
            <ul>
                <li><b>Spider Radar Maps:</b> Multi-axis radar charts evaluating player capabilities across Strike Rate, Average, Economy, Lethality, and Fielding.</li>
                <li><b>Head-to-Head (H2H) Duels:</b> Detailed Batter vs. Bowler matchup analytics with rivalry badges (<i>Bunny</i>, <i>Owner</i>, <i>Fierce Rivalry</i>).</li>
                <li><b>Complete Career Statistics:</b> Detailed profiles tracking Matches, Innings, Runs, High Score, Average, SR, 50s, 100s, Wickets, Best Bowling, Economy, 3w/5w Hauls, Catches, and Stumpings.</li>
                <li><b>Player Rename & Merge:</b> Rename player entries across all historical matches or merge duplicate player records without losing career stats.</li>
                <li><b>4K UHD Player Photos:</b> Upload high-resolution player profile pictures saved on Supabase cloud storage.</li>
            </ul>
            </p>

            <hr>
            <h3><b>👑 Leaderboards & Prestige Badges</b></h3>
            <p>
            <ul>
                <li><b>Prestige Cap Colors:</b> Highlighting top league performers with Gold (#1 Overall Player), Orange Cap (Top Batter), and Purple Cap (Top Bowler).</li>
                <li><b>League Rankings:</b> Interactive leaderboards for Most Runs, Most Wickets, Best Strike Rate, Best Economy, Most 50s/30s, Most Boundaries, and Fielding.</li>
            </ul>
            </p>

            <hr>
            <h3><b>📁 Cloud Sync & League Management</b></h3>
            <p>
            <ul>
                <li><b>Cloud Sync:</b> Real-time synchronization of players, matches, scorecards, and chat across all league members.</li>
                <li><b>Gully League Management:</b> Create new leagues or join via Gully Code; manage admins, transfer ownership, and configure league settings.</li>
                <li><b>Offline First Architecture:</b> Continue scoring matches seamlessly offline; changes automatically sync once network connectivity returns.</li>
                <li><b>Universal Global Search:</b> Instant search across players, matches, venues, and chat messages.</li>
                <li><b>Backup & Import:</b> Export or import full app data backups under Settings, or import <code>.cricket</code> match files received via WhatsApp or file managers.</li>
            </ul>
            </p>

            <hr>
            <h3><b>📧 Feedback & Support</b></h3>
            <p>Have feedback, suggestions, or found an issue? We would love to hear from you!</p>
            <p>Send your feedback to: <a href="mailto:goldenduckscorer@gmail.com"><b>goldenduckscorer@gmail.com</b></a></p>
        """.trimIndent()

        val tv = TextView(this).apply {
            text = HtmlCompat.fromHtml(manualText, HtmlCompat.FROM_HTML_MODE_LEGACY)
            movementMethod = LinkMovementMethod.getInstance()
            textSize = 15f
            setPadding(40, 20, 40, 20)
            setTextColor(TypedValue().let {
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, it, true)
                it.data
            })
        }

        val scroll = ScrollView(this).apply {
            addView(tv)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("App Manual & Features")
            .setView(scroll)
            .setPositiveButton("GOT IT", null)
            .show()
    }

    private fun showThemeSettings() {
        val options = arrayOf("Light", "Dark", "System Default")
        val currentMode = getSharedPreferences("scoring_prefs", Context.MODE_PRIVATE)
            .getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        val checkedItem = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> 0
            AppCompatDelegate.MODE_NIGHT_YES -> 1
            else -> 2
        }

        showDynamicDialog {
            setTitle("Choose Theme")
            setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val newMode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }

                getSharedPreferences("scoring_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("theme_mode", newMode)
                    .apply()

                AppCompatDelegate.setDefaultNightMode(newMode)
                dialog.dismiss()
            }
            setNegativeButton("CANCEL", null)
        }
    }

    private fun checkSecurityAndLaunchExport() {
        authenticate(this, "Authorize Export", "Confirm identity to export records", object : AuthCallback {
            override fun onSuccess() { launchExport() }
            override fun onFailure(error: String?) {
                Toast.makeText(this@HomeActivity, "Authentication Failed: $error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun launchExport() {
        val sdf = java.text.SimpleDateFormat("dd_MMM_yyyy_HH_mm_ss", java.util.Locale.getDefault())
        val dateStr = sdf.format(java.util.Date())
        val defaultName = "cricket_records_$dateStr"
        
        val input = EditText(this).apply {
            setText(defaultName)
            setSelection(defaultName.length)
            setSingleLine(true)
        }
        val container = FrameLayout(this).apply {
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            val margin = (16 * resources.displayMetrics.density).toInt()
            params.setMargins(margin, margin / 2, margin, 0)
            layoutParams = params
            addView(input)
        }

        showDynamicDialog {
            setTitle("Set Record Name")
            setMessage("Enter a descriptive name for this record file:")
            setView(container)
            setPositiveButton("NEXT") { _, _ ->
                val fileName = input.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    showExportOptions(fileName)
                } else {
                    Toast.makeText(this@HomeActivity, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            setNegativeButton("CANCEL", null)
        }
    }

    private fun showExportOptions(fileName: String) {
        showDynamicDialog {
            setTitle("Handle Records")
            setMessage("How would you like to save or send \"$fileName\"?")
            setNeutralButton("CANCEL", null)
            setNegativeButton("SHARE") { _, _ -> performExportAndShare(fileName) }
            setPositiveButton("EXPORT") { _, _ -> saveExportToMediaStore("$fileName.cricket") }
        }
    }

    private fun saveExportToMediaStore(fileName: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Golden Duck - A Cricket Scoring App")
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val uri = contentResolver.insert(collection, values)
            if (uri != null) {
                val os = contentResolver.openOutputStream(uri)
                if (os != null) {
                    exportData(this, os, object : BackupCallback {
                        override fun onSuccess() {
                            // Remember this URI for the next import
                            getSharedPreferences("scoring_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("last_export_uri", uri.toString())
                                .apply()

                            Toast.makeText(this@HomeActivity, "Records saved to: Downloads/Golden Duck - A Cricket Scoring App/", Toast.LENGTH_LONG).show()
                        }
                        override fun onFailure(e: String?) {
                            Toast.makeText(this@HomeActivity, "Export Failed: $e", Toast.LENGTH_LONG).show()
                        }
                    })
                } else {
                    Toast.makeText(this, "Failed to open output stream", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Failed to create file entry", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performExportAndShare(fileName: String) {
        try {
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9-_]"), "_")
            val tempFile = File(cacheDir, "$safeName.cricket")
            tempFile.deleteOnExit()
            val os = FileOutputStream(tempFile)
            exportData(this, os, object : BackupCallback {
                override fun onSuccess() {
                    val uri: Uri = FileProvider.getUriForFile(
                        this@HomeActivity,
                        "$packageName.fileprovider",
                        tempFile
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream" // Standard binary type
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share \"$fileName\" Via"))
                }

                override fun onFailure(e: String?) {
                    Toast.makeText(this@HomeActivity, "Prepare for share failed: $e", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSecurityAndShowImportWarning() {
        authenticate(this, "Authorize Import", "Confirm identity to import records", object : AuthCallback {
            override fun onSuccess() { showImportWarning() }
            override fun onFailure(error: String?) {
                Toast.makeText(this@HomeActivity, "Authentication Failed: $error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showImportWarning() {
        showDynamicDialog {
            setTitle("⚠️ Import Records")
            setMessage("Importing records will replace all match history on this device. For your safety, a professional backup of your current records will be automatically created in your Downloads folder before we proceed.\n\nContinue to select file?")
            setPositiveButton("SELECT FILE") { d, w -> launchImport() }
            setNegativeButton("CANCEL", null)
        }
    }

    private fun launchImport() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        val chooser = Intent.createChooser(intent, "Select File Manager")
        importLauncher.launch(chooser)
    }

    private fun showDeleteWarning() {
        authenticate(this, "Authorize Deletion", "Confirm identity to wipe all records", object : AuthCallback {
            override fun onSuccess() {
                showDynamicDialog {
                    setTitle("🔥 FINAL WARNING")
                    setMessage("This will PERMANENTLY DELETE all your matches, players, and statistics. This cannot be undone.\n\nAre you absolutely sure?")
                    setPositiveButton("DELETE EVERYTHING") { d, w -> performFullWipe() }
                    setNegativeButton("CANCEL", null)
                    setIcon(android.R.drawable.ic_dialog_alert)
                }
            }
            override fun onFailure(error: String?) {
                Toast.makeText(this@HomeActivity, "Authentication Failed: $error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun performFullWipe() {
        AppDatabase.ioExecutor.execute {
            val db = getInstance(this)
            db.runInTransaction {
                db.playerDao().deleteAllPlayers()
                db.matchDao().deleteAllMatches()
                db.statsDao().deleteAllStats()
                // Use a default gullyId or logic to clear current active gully's drafts
                val currentGId = GullySyncManager.getCurrentGullyId(this) ?: "local"
                db.draftDao().deleteAllDraftsByGully(currentGId)
            }
            runOnUiThread {
                Toast.makeText(this, "All records deleted successfully", Toast.LENGTH_LONG).show()
                refresh(this, null)
            }
        }
    }
}

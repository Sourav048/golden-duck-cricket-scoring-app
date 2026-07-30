package com.example.scoring

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.method.LinkMovementMethod
import android.util.TypedValue
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
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.text.HtmlCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.scoring.AppDatabase.Companion.getInstance
import com.example.scoring.BackupManager.BackupCallback
import com.example.scoring.BackupManager.exportData
import com.example.scoring.RankingRegistry.refresh
import com.example.scoring.SecurityUtils.AuthCallback
import com.example.scoring.SecurityUtils.authenticate
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class HomeActivity : BaseActivity() {

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            BackupManager.importData(this, uri, object : BackupCallback {
                override fun onSuccess() {
                    Toast.makeText(this@HomeActivity, "Records Imported Successfully!", Toast.LENGTH_LONG).show()
                    refresh(this@HomeActivity, null)
                }
                override fun onFailure(error: String?) {
                    Toast.makeText(this@HomeActivity, "Import Failed: $error", Toast.LENGTH_LONG).show()
                }
            })
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

        findViewById<View>(R.id.cardHistory).setOnClickListener {
            startActivity(Intent(this, MatchHistoryActivity::class.java))
        }

        findViewById<View>(R.id.cardPlayers).setOnClickListener {
            startActivity(Intent(this, PlayerListActivity::class.java))
        }

        findViewById<View>(R.id.cardStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }

        // Load rankings on startup
        refresh(this, null)

        handleIncomingFileIntent(intent)
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
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to auto-save records: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHomeMenu(v: View?) {
        val popup = PopupMenu(this, v)
        popup.menu.add("Export Records")
        popup.menu.add("Import Records")
        popup.menu.add("Delete Records")
        popup.menu.add("Dark/Light Mode")
        popup.menu.add("ReadMe(Manual)")

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "ReadMe(Manual)" -> showReadMeDialog()
                "Dark/Light Mode" -> showThemeSettings()
                "Export Records" -> checkSecurityAndLaunchExport()
                "Import Records" -> checkSecurityAndShowImportWarning()
                "Delete Records" -> showDeleteWarning()
            }
            true
        }
        popup.show()
    }

    private fun showReadMeDialog() {
        val manualText = """
            <h3><b>🏆 Top Features</b></h3>
            <p><b>Prestige Colors:</b> The top-ranked players in your league are honored with special colors and photo borders:
            <ul>
                <li><font color='#FFD700'><b>Gold:</b></font> Overall #1 Player</li>
                <li><font color='#FF8C00'><b>Orange:</b></font> Best Batter</li>
                <li><font color='#800080'><b>Purple:</b></font> Best Bowler</li>
            </ul>
            </p>
            
            <p><b>Undo Last Ball:</b> Made a mistake? Use the <b>'Undo'</b> button to instantly revert the last ball and correct the score.</p>
            
            <p><b>Resume Drafts:</b> Matches are automatically saved. You can exit anytime and resume later from <b>Match History</b>.</p>

            <p><b>Player Comparison:</b> Go to any player profile and tap <b>"Compare Players"</b> to see head-to-head stats with smart winner highlighting.</p>
            
            <p><b>Detailed Insights:</b>
            <ul>
                <li><b>Crease Time:</b> Track exactly how many <b>minutes</b> every batsman spends at the crease.</li>
                <li><b>POTM & Highlights:</b> Finished matches feature a <b>Player of the Match</b> and key summary highlights.</li>
                <li><b>Role Stats:</b> Separate leaderboards for <b>Batting</b>, <b>Bowling</b>, and <b>Fielding</b> roles.</li>
            </ul>
            </p>

            <p><b>Advanced Scoring:</b> Mid-over bowler changes, rain-affected <b>DLS</b> target calculations, and support for 7+ ball types (Stumper, Leather, etc.).</p>

            <hr>
            <h3><b>📁 Sharing &amp; Importing Records</b></h3>
            <p><b>How to get records from a friend:</b></p>
            <ol>
                <li><b>Find the file:</b> Whereever you see a <b>.cricket</b> file (in WhatsApp or Email), just <b>tap it once</b>.</li>
                <li><b>Auto-Save:</b> The app will automatically save that file into your phone's <b>Downloads</b> folder inside: <i>"Golden Duck - A Cricket Scoring App"</i>.</li>
                <li><b>Import:</b> Open the app, tap the <b>Menu</b> (top right), and select <b>Import Records</b>.</li>
                <li><b>Pick File:</b> Choose the file from that folder, and all matches/players will appear in your app!</li>
            </ol>
            
            <p><b>How to share your own records:</b></p>
            <ul>
                <li>Go to the <b>Menu</b> -> <b>Export Records</b> -> <b>SHARE</b> to send your entire match history to a friend via WhatsApp.</li>
            </ul>

            <hr>
            <h3><b>🏏 How to Score</b></h3>
            <ol>
                <li><b>Setup:</b> Start a 'New Match' and enter team names, overs, and players.</li>
                <li><b>Toss:</b> Record the toss result and decision.</li>
                <li><b>Scoring:</b> Tap runs (0, 1, 2, 3, 4, 6) or extras (WD, NB, B, LB) for each ball.</li>
                <li><b>Wickets:</b> Use the 'WICKET' button to record various dismissal types.</li>
                <li><b>Match Flow:</b> The app handles over completions, innings breaks, and results automatically.</li>
            </ol>
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
        showDynamicDialog {
            setTitle("Export Records")
            setMessage("Choose how you would like to handle your records:")
            setNeutralButton("CANCEL", null)
            setNegativeButton("SHARE") { _, _ -> performExportAndShare() }
            setPositiveButton("EXPORT") { _, _ -> performExport() }
        }
    }

    private fun performExport() {
        val defaultName = "cricket_records_${System.currentTimeMillis()}"
        val input = EditText(this).apply {
            setText(defaultName)
            setSelection(defaultName.length)
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
            setTitle("Save Records As")
            setMessage("Enter a name for your export file:")
            setView(container)
            setPositiveButton("SAVE") { _, _ ->
                val fileName = input.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    saveExportToMediaStore("$fileName.cricket")
                } else {
                    Toast.makeText(this@HomeActivity, "Filename cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            setNegativeButton("CANCEL", null)
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

    private fun performExportAndShare() {
        try {
            val tempFile = File(cacheDir, "shared_cricket_records.cricket")
            tempFile.deleteOnExit() // Mark for cleanup
            val os = FileOutputStream(tempFile)
            exportData(this, os, object : BackupCallback {
                override fun onSuccess() {
                    val uri: Uri = FileProvider.getUriForFile(
                        this@HomeActivity,
                        "$packageName.fileprovider",
                        tempFile
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/x-cricket"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Records Via"))
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
            setTitle("⚠️ CRITICAL WARNING")
            setMessage("Importing records will PERMANENTLY DELETE all matches, players, and stats currently on this device.\n\nPlease export your current data first if you wish to keep it.\n\nProceed with Import?")
            setPositiveButton("YES, REPLACE ALL") { d, w -> launchImport() }
            setNegativeButton("CANCEL", null)
            setIcon(android.R.drawable.ic_dialog_alert)
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
                db.draftDao().deleteAllDrafts()
            }
            runOnUiThread {
                Toast.makeText(this, "All records deleted successfully", Toast.LENGTH_LONG).show()
                refresh(this, null)
            }
        }
    }
}

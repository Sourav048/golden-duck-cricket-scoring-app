package com.example.scoring

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.scoring.AppDatabase.Companion.getInstance
import com.example.scoring.RankingRegistry.applyPrestige
import com.example.scoring.SecurityUtils.AuthCallback
import com.example.scoring.SecurityUtils.authenticate
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerDetailsActivity : BaseActivity() {
    private var playerId: String? = null
    private var db: AppDatabase? = null
    private var currentPlayer: PlayerEntity? = null

    private var pendingPhotoUri: String? = null
    private var pendingName = ""
    private var pendingJersey = ""
    private var currentCameraUri: Uri? = null

    private val pickMedia: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                pendingPhotoUri = uri.toString()
                showEditDialog()
            }
        }

    private val takePicture: ActivityResultLauncher<Uri> =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                pendingPhotoUri = currentCameraUri?.toString()
                showEditDialog()
            } else {
                currentCameraUri?.let { uri ->
                    try {
                        val f = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), uri.lastPathSegment ?: "")
                        if (f.exists() && f.length() == 0L) f.delete()
                    } catch (e: Exception) {
                        Log.e("PlayerDetails", "Failed to cleanup camera file: ${e.message}")
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_details)

        db = getInstance(this)
        playerId = intent.getStringExtra("playerId")

        findViewById<View>(R.id.btnPlayerDetailsMenu).setOnClickListener { v -> showPopupMenu(v) }
        findViewById<View>(R.id.btnBackProfile).setOnClickListener { finish() }
        
        RankingRegistry.refresh(this, object : RankingRegistry.OnRankingsLoaded {
            override fun onLoaded() {
                loadPlayerData()
            }
        })
    }

    private fun loadPlayerData() {
        AppDatabase.ioExecutor.execute {
            val player = db?.playerDao()?.getPlayerById(playerId)
            currentPlayer = player
            
            val stats = if (player?.globalId != null) {
                db?.statsDao()?.getStatsByGlobalId(player.globalId!!)?.filterNotNull()
            } else {
                db?.statsDao()?.getStatsByPlayer(playerId)?.filterNotNull()
            }
            
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (player == null) {
                    finish()
                    return@runOnUiThread
                }

                // Show "GLOBAL PROFILE" badge if linked
                findViewById<TextView>(R.id.tvDetailSubTitle)?.apply {
                    if (player.globalId != null) {
                        text = "GLOBAL CAREER PROFILE"
                        visibility = View.VISIBLE
                    } else {
                        visibility = View.GONE
                    }
                }

                val iv = findViewById<ShapeableImageView>(R.id.ivDetailPhoto)
                val tvName = findViewById<TextView>(R.id.tvDetailNameDisplay)
                val tvJersey = findViewById<TextView>(R.id.tvDetailJerseyDisplay)

                // 1. Calculate dynamic contrast for the header
                val seed = ThemeManager.getSeedColor(this)
                val contrastColor = ThemeManager.getContrastColor(seed)
                val translucentContrast = Color.argb(40, Color.red(contrastColor), Color.green(contrastColor), Color.blue(contrastColor))

                // 2. Re-apply theme BEFORE prestige to ensure header background is solid
                ThemeManager.colorize(findViewById(R.id.layoutPlayerHeader))

                // 3. Set Name and Jersey with dynamic contrast (Override prestige default)
                tvName.text = player.name
                tvJersey.text = "#" + player.jerseyNumber
                tvJersey.setTextColor(contrastColor)

                if (!player.photoUri.isNullOrEmpty() && !isFinishing && !isDestroyed) {
                    Glide.with(this)
                        .load(player.photoUri)
                        .dontAnimate() // Better for shared element transitions
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(iv)
                } else {
                    iv.setImageResource(android.R.drawable.ic_menu_gallery)
                }

                // Apply prestige with custom contrast fallback
                applyPrestige(player.id, tvName, iv, contrastColor)

                // 4. Handle ranking chip visibility
                val chips = listOf(R.id.tvRankOverall, R.id.tvRankBatting, R.id.tvRankBowling)
                chips.forEach { id ->
                    val tv = findViewById<TextView>(id)
                    tv.setTextColor(contrastColor)
                    (tv.parent as? MaterialCardView)?.setCardBackgroundColor(translucentContrast)
                }

                val card = findViewById<MaterialCardView>(R.id.cardPhotoFrame)
                card.setCardBackgroundColor(Color.WHITE) // Ensure base is white
                
                // Only apply prestige stroke to frame if the player actually has one
                if (iv.strokeWidth > 0) {
                    card.setStrokeColor(iv.strokeColor)
                    card.strokeWidth = 8
                    iv.strokeWidth = 0f
                } else {
                    card.strokeWidth = 0
                }

                iv.setOnClickListener { showFullScreenPhoto(player.photoUri) }

                calculateAndDisplayStats(stats?.toMutableList())
                calculateAndDisplayRankings()
            }
        }
    }

    private fun showFullScreenPhoto(uri: String?) {
        if (uri.isNullOrEmpty()) return

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_full_screen_photo)

        val iv = dialog.findViewById<ImageView>(R.id.ivFullScreen)
        Glide.with(this).load(uri).into(iv)

        dialog.findViewById<View>(R.id.btnCloseFull).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPopupMenu(v: View?) {
        val popup = PopupMenu(this, v)
        popup.menu.add("Edit Profile")
        popup.menu.add("Delete Player")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Edit Profile" -> checkSecurityForEdit()
                "Delete Player" -> checkSecurityForDelete()
            }
            true
        }
        popup.show()
    }

    private fun checkSecurityForEdit() {
        authenticate(this, "Edit Profile", "Provide security to edit player", object : AuthCallback {
            override fun onSuccess() { showEditDialog() }
            override fun onFailure(e: String?) {
                Toast.makeText(this@PlayerDetailsActivity, "Failed: $e", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun checkSecurityForDelete() {
        authenticate(this, "Delete Player", "Provide security to delete player", object : AuthCallback {
            override fun onSuccess() {
                showDynamicDialog {
                    setTitle("Delete Player")
                    setMessage("Are you sure? This cannot be undone.")
                    setPositiveButton("Delete") { d, w -> deletePlayer() }
                    setNegativeButton("Cancel", null)
                }
            }
            override fun onFailure(e: String?) {
                Toast.makeText(this@PlayerDetailsActivity, "Failed: $e", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun deletePlayer() {
        val playerToDelete = currentPlayer ?: return
        AppDatabase.ioExecutor.execute {
            db?.playerDao()?.deletePlayer(playerToDelete)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Toast.makeText(this, "Player deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showEditDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_player, null)
        val nameIn = view.findViewById<EditText>(R.id.playerNameInput)
        val jerseyIn = view.findViewById<EditText>(R.id.playerJerseyInput)
        val preview = view.findViewById<ImageView>(R.id.ivDialogPreview)

        view.findViewById<View>(R.id.quickAddContainer)?.visibility = View.GONE
        view.findViewById<View>(R.id.quickAddDivider)?.visibility = View.GONE

        val player = currentPlayer ?: return

        nameIn.setText(if (pendingName.isEmpty()) player.name else pendingName)
        jerseyIn.setText(if (pendingJersey.isEmpty()) player.jerseyNumber else pendingJersey)

        val photoToShow = pendingPhotoUri ?: player.photoUri
        if (!photoToShow.isNullOrEmpty()) {
            try { 
                preview.setImageURI(Uri.parse(photoToShow)) 
            } catch (e: Exception) {
                Log.e("PlayerDetails", "Failed to load photo: ${e.message}")
            }
        }

        val dialog = showDynamicDialog {
            setTitle("Edit Player")
            setView(view)
            setPositiveButton("Save", null)
            setNegativeButton("Cancel") { d, w ->
                pendingName = ""
                pendingJersey = ""
                pendingPhotoUri = null
            }
        }

        view.findViewById<View>(R.id.btnTakePhoto).setOnClickListener {
            pendingName = nameIn.text.toString().trim()
            pendingJersey = jerseyIn.text.toString().trim()
            launchCamera()
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnPickGallery).setOnClickListener {
            pendingName = nameIn.text.toString().trim()
            pendingJersey = jerseyIn.text.toString().trim()
            launchGallery()
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = nameIn.text.toString().trim()
            val j = jerseyIn.text.toString().trim()
            if (n.isNotEmpty() && j.isNotEmpty()) {
                val oldName = player.name // CAPTURE THE TRUTH HERE
                val nameChanged = (n != oldName)
                
                player.name = n
                player.jerseyNumber = j
                
                // FIX: Ensure photo is saved to internal storage if it's a new URI
                pendingPhotoUri?.let { uriStr ->
                    if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) {
                        try {
                            val savedPath = PhotoUtils.savePhoto(this, Uri.parse(uriStr))
                            if (savedPath != null) player.photoUri = savedPath
                        } catch (e: Exception) {
                            Log.e("PlayerDetails", "Photo save failed: ${e.message}")
                            player.photoUri = uriStr
                        }
                    } else {
                        player.photoUri = uriStr
                    }
                }

                if (nameChanged) {
                    Toast.makeText(this, "Renaming player in all matches...", Toast.LENGTH_LONG).show()
                    PlayerRenameManager.renamePlayer(this, player, oldName, n) {
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                resetEditState()
                                loadPlayerData()
                                Toast.makeText(this@PlayerDetailsActivity, "Universal Rename Complete!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    AppDatabase.ioExecutor.execute {
                        db?.playerDao()?.updatePlayer(player)
                        GullySyncManager.syncPlayerToCloud(player.gullyId, player)
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                resetEditState()
                                loadPlayerData()
                            }
                        }
                    }
                }
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Name and Jersey required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetEditState() {
        pendingName = ""
        pendingJersey = ""
        pendingPhotoUri = null
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERM_CAMERA)
            return
        }
        try {
            val photoFile = createImageFile()
            currentCameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            takePicture.launch(currentCameraUri!!)
        } catch (e: IOException) {
            Log.e("PlayerDetails", "Camera setup failed: ${e.message}")
        }
    }

    private fun launchGallery() {
        pickMedia.launch(PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
            .build())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_CAMERA && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        }
    }

    private fun calculateAndDisplayRankings() {
        AppDatabase.ioExecutor.execute {
            val gId = GullySyncManager.getCurrentGullyId(this) ?: "local"
            val overall = db?.statsDao()?.getOverallRankings(gId)
            val batting = db?.statsDao()?.getBattingRankings(gId)
            val bowling = db?.statsDao()?.getBowlingRankings(gId)

            var rO = "Overall Ranking:- #-"
            var rBat = "Batting Ranking:- #-"
            var rBowl = "Bowling Ranking:- #-"

            playerId?.let { id ->
                overall?.indexOfFirst { it?.playerId == id }?.let { idx -> if (idx >= 0) rO = "Overall Ranking:- #${idx + 1}" }
                batting?.indexOfFirst { it?.playerId == id }?.let { idx -> if (idx >= 0) rBat = "Batting Ranking:- #${idx + 1}" }
                bowling?.indexOfFirst { it?.playerId == id }?.let { idx -> if (idx >= 0) rBowl = "Bowling Ranking:- #${idx + 1}" }
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                findViewById<TextView>(R.id.tvRankOverall).text = rO
                findViewById<TextView>(R.id.tvRankBatting).text = rBat
                findViewById<TextView>(R.id.tvRankBowling).text = rBowl
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun calculateAndDisplayStats(stats: MutableList<PlayerMatchStatEntity>?) {
        val list = stats ?: mutableListOf()

        var runs = 0; var balls = 0; var fours = 0; var sixes = 0
        var bInns = 0; var no = 0; var highest = 0
        var f30 = 0; var f50 = 0; var f80 = 0; var f100 = 0; var dks = 0
        var wInns = 0; var wkts = 0; var rCon = 0; var bBowl = 0
        var hats = 0; var maidensTotal = 0; var sixesGiven = 0
        var w2 = 0; var w3 = 0; var w5 = 0; var bbiW = 0; var bbiR = 0

        val uniqueMatchIds = mutableSetOf<String?>()
        for (s in list) {
            uniqueMatchIds.add(s.matchId)
            if (s.runsScored > 0 || s.ballsFaced > 0 || s.isOut) {
                bInns++; runs += s.runsScored; balls += s.ballsFaced
                fours += s.fours; sixes += s.sixes
                if (!s.isOut) no++
                if (s.runsScored > highest) highest = s.runsScored
                if (s.runsScored >= 100) f100++ else if (s.runsScored >= 80) f80++ else if (s.runsScored >= 50) f50++ else if (s.runsScored >= 30) f30++
                if (s.isOut && s.runsScored == 0) dks++
            }
            if (s.ballsBowled > 0) {
                wInns++; wkts += s.wicketsTaken; rCon += s.runsConceded; bBowl += s.ballsBowled
                hats += s.hattricks; maidensTotal += s.maidens; sixesGiven += s.sixesConceded
                if (s.wicketsTaken >= 5) w5++ else if (s.wicketsTaken >= 3) w3++ else if (s.wicketsTaken >= 2) w2++
                if (s.wicketsTaken > bbiW || (s.wicketsTaken == bbiW && s.runsConceded < bbiR) || bbiW == 0) {
                    bbiW = s.wicketsTaken; bbiR = s.runsConceded
                }
            }
        }

        val m = uniqueMatchIds.size
        val batAvg = if ((bInns - no) > 0) runs.toDouble() / (bInns - no) else runs.toDouble()
        val batSR = if (balls > 0) (runs.toDouble() / balls) * 100 else 0.0
        val bowlAvg = if (wkts > 0) rCon.toDouble() / wkts else 0.0
        val bowlEco = if (bBowl > 0) (rCon.toDouble() / bBowl) * 6.0 else 0.0

        setBox(R.id.boxBatMatches, "MATCHES", m.toString())
        setBox(R.id.boxBatInnings, "INNINGS", bInns.toString())
        setBox(R.id.boxBatRuns, "RUNS", runs.toString())
        setBox(R.id.boxBatHighest, "HIGHEST", highest.toString())
        setBox(R.id.boxBatAvg, "AVERAGE", String.format(Locale.US, "%.1f", batAvg))
        setBox(R.id.boxBatSR, "STRIKE RATE", String.format(Locale.US, "%.1f", batSR))
        setBox(R.id.boxBatFours, "FOURS", fours.toString())
        setBox(R.id.boxBatSixes, "SIXES", sixes.toString())
        setBox(R.id.boxBatNotOut, "NOT OUT", no.toString())
        setBox(R.id.boxBatDucks, "DUCKS", dks.toString())
        setBox(R.id.boxBat30s, "30s", f30.toString())
        setBox(R.id.boxBat50s, "50s", f50.toString())
        setBox(R.id.boxBat80s, "80s", f80.toString())
        setBox(R.id.boxBat100s, "100s", f100.toString())

        setBox(R.id.boxBowlMatches, "MATCHES", m.toString())
        setBox(R.id.boxBowlInnings, "INNINGS", wInns.toString())
        setBox(R.id.boxBowlWickets, "WICKETS", wkts.toString())
        setBox(R.id.boxBowlAvg, "AVERAGE", String.format(Locale.US, "%.1f", bowlAvg))
        setBox(R.id.boxBowlEco, "ECONOMY", String.format(Locale.US, "%.1f", bowlEco))
        setBox(R.id.boxBowlBBI, "BEST FIGURE", "$bbiW/$bbiR")
        setBox(R.id.boxBowlMaidens, "MAIDENS", maidensTotal.toString())
        setBox(R.id.boxBowlHattricks, "HAT-TRICKS", hats.toString())
        setBox(R.id.boxBowl2w, "2W HAUL", w2.toString())
        setBox(R.id.boxBowl3w, "3W HAUL", w3.toString())
        setBox(R.id.boxBowl5w, "5W HAUL", w5.toString())
        setBox(R.id.boxBowlSixesConceded, "6S GIVEN", sixesGiven.toString())

        var fCatches = 0; var fStumps = 0; var fRO = 0
        for (s in list) { fCatches += s.catches; fStumps += s.stumpings; fRO += s.runOuts }
        setBox(R.id.boxFieldMatches, "MATCHES", m.toString())
        setBox(R.id.boxFieldCatches, "CATCHES", fCatches.toString())
        setBox(R.id.boxFieldStumpings, "STUMPINGS", fStumps.toString())
        setBox(R.id.boxFieldRunOuts, "RUN OUTS", fRO.toString())
    }

    private fun setBox(id: Int, label: String, value: String) {
        val v = findViewById<View>(id) ?: return
        v.findViewById<TextView>(R.id.tvStatLabel).text = label
        v.findViewById<TextView>(R.id.tvStatValue).text = value
    }

    companion object {
        private const val PERM_CAMERA = 2001
    }
}

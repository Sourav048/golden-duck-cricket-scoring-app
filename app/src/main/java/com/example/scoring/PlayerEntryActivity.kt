package com.example.scoring

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Filter
import android.widget.ImageView
import android.widget.LinearLayout
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
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PlayerEntryActivity : BaseActivity() {
    private var teamAContainer: LinearLayout? = null
    private var teamBContainer: LinearLayout? = null
    private var btnStartMatch: Button? = null

    private var teamAName: String? = null
    private var teamBName: String? = null
    private var ballType: String? = null
    private var overs = 0
    private var ruleRunsOnWide = false
    private var ruleFreeHit = false
    private var ruleRunsOnBye = false
    private var ruleOverthrow = false
    private var ruleEveryPlayerBats = false

    private val teamAPlayers: MutableList<TempPlayer> = ArrayList()
    private val teamBPlayers: MutableList<TempPlayer> = ArrayList()

    private var currentTeam = 0
    private var pendingName: String = ""
    private var pendingJersey: String = ""
    private var pendingPhotoUri: String? = null
    private var pendingId: String? = null
    private var currentCameraUri: Uri? = null

    private val pickMedia: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val savedPath = PhotoUtils.savePhoto(this, uri)
                addPlayerCard(currentTeam, pendingName, pendingJersey, savedPath, pendingId)
                pendingName = ""
                pendingJersey = ""
                pendingId = null
            }
        }

    private val takePicture: ActivityResultLauncher<Uri> =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && currentCameraUri != null) {
                val savedPath = PhotoUtils.savePhoto(this, currentCameraUri!!)
                addPlayerCard(currentTeam, pendingName, pendingJersey, savedPath, pendingId)
                pendingName = ""
                pendingJersey = ""
                pendingId = null
            } else {
                cleanupEmptyCameraFile()
            }
        }

    private fun cleanupEmptyCameraFile() {
        currentCameraUri?.let { uri ->
            try {
                // If it's a file URI, delete it directly
                if (uri.scheme == "file") {
                    File(uri.path ?: "").let { if (it.exists() && it.length() == 0L) it.delete() }
                } else {
                    val f = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), uri.lastPathSegment ?: "")
                    if (f.exists() && f.length() == 0L) f.delete()
                }
            } catch (e: Exception) {
                Log.e("PlayerEntry", "Cleanup failed: ${e.message}")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentTeam", currentTeam)
        outState.putString("pendingName", pendingName)
        outState.putString("pendingJersey", pendingJersey)
        outState.putString("pendingId", pendingId)
        outState.putString("pendingPhotoUri", pendingPhotoUri)
        outState.putBoolean("ruleRunsOnWide", ruleRunsOnWide)
        outState.putBoolean("ruleFreeHit", ruleFreeHit)
        currentCameraUri?.let { outState.putString("currentCameraUri", it.toString()) }
        
        outState.putStringArrayList("teamANames", ArrayList(teamAPlayers.map { it.name }))
        outState.putStringArrayList("teamAPhotos", ArrayList(teamAPlayers.map { it.photoUri ?: "" }))
        outState.putStringArrayList("teamAJerseys", ArrayList(teamAPlayers.map { it.jersey }))
        outState.putStringArrayList("teamAIds", ArrayList(teamAPlayers.map { it.id }))

        outState.putStringArrayList("teamBNames", ArrayList(teamBPlayers.map { it.name }))
        outState.putStringArrayList("teamBPhotos", ArrayList(teamBPlayers.map { it.photoUri ?: "" }))
        outState.putStringArrayList("teamBJerseys", ArrayList(teamBPlayers.map { it.jersey }))
        outState.putStringArrayList("teamBIds", ArrayList(teamBPlayers.map { it.id }))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_entry)

        val intent = intent
        teamAName = intent.getStringExtra("teamA")
        teamBName = intent.getStringExtra("teamB")
        overs = intent.getIntExtra("overs", 20)
        ballType = intent.getStringExtra("ballType")
        ruleRunsOnWide = intent.getBooleanExtra("ruleRunsOnWide", true)
        ruleFreeHit = intent.getBooleanExtra("ruleFreeHit", true)
        ruleRunsOnBye = intent.getBooleanExtra("ruleRunsOnBye", true)
        ruleOverthrow = intent.getBooleanExtra("ruleOverthrow", true)
        ruleEveryPlayerBats = intent.getBooleanExtra("ruleEveryPlayerBats", true)

        findViewById<TextView>(R.id.teamATitle).text = teamAName
        findViewById<TextView>(R.id.teamBTitle).text = teamBName
        teamAContainer = findViewById(R.id.teamAContainer)
        teamBContainer = findViewById(R.id.teamBContainer)
        btnStartMatch = findViewById(R.id.btnStartMatch)

        findViewById<View>(R.id.btnAddPlayerA).setOnClickListener { showAddDialog(0) }
        findViewById<View>(R.id.btnAddPlayerB).setOnClickListener { showAddDialog(1) }
        btnStartMatch?.setOnClickListener { startMatch() }

        if (savedInstanceState != null) {
            currentTeam = savedInstanceState.getInt("currentTeam")
            pendingName = savedInstanceState.getString("pendingName", "")
            pendingJersey = savedInstanceState.getString("pendingJersey", "")
            pendingId = savedInstanceState.getString("pendingId", null)
            pendingPhotoUri = savedInstanceState.getString("pendingPhotoUri", null)
            ruleRunsOnWide = savedInstanceState.getBoolean("ruleRunsOnWide")
            ruleFreeHit = savedInstanceState.getBoolean("ruleFreeHit")
            savedInstanceState.getString("currentCameraUri")?.let { currentCameraUri = Uri.parse(it) }

            val aN = savedInstanceState.getStringArrayList("teamANames")
            val aP = savedInstanceState.getStringArrayList("teamAPhotos")
            val aJ = savedInstanceState.getStringArrayList("teamAJerseys")
            val aI = savedInstanceState.getStringArrayList("teamAIds")
            if (aN != null && aP != null && aJ != null && aI != null) {
                for (i in aN.indices) addPlayerCard(0, aN[i], aJ[i], aP[i], aI[i])
            }

            val bN = savedInstanceState.getStringArrayList("teamBNames")
            val bP = savedInstanceState.getStringArrayList("teamBPhotos")
            val bJ = savedInstanceState.getStringArrayList("teamBJerseys")
            val bI = savedInstanceState.getStringArrayList("teamBIds")
            if (bN != null && bP != null && bJ != null && bI != null) {
                for (i in bN.indices) addPlayerCard(1, bN[i], bJ[i], bP[i], bI[i])
            }
        }

        if (intent.getBooleanExtra("cloneMatch", false)) {
            val aNames = intent.getStringArrayListExtra("teamANames")
            val aPhotos = intent.getStringArrayListExtra("teamAPhotos")
            val aJerseys = intent.getStringArrayListExtra("teamAJerseys")
            val aIds = intent.getStringArrayListExtra("teamAIds")
            aNames?.let {
                for (i in it.indices) {
                    val j = if (aJerseys != null && aJerseys.size > i) aJerseys[i] else "0"
                    addPlayerCard(0, it[i], j, aPhotos?.get(i), aIds?.get(i))
                }
            }

            val bNames = intent.getStringArrayListExtra("teamBNames")
            val bPhotos = intent.getStringArrayListExtra("teamBPhotos")
            val bJerseys = intent.getStringArrayListExtra("teamBJerseys")
            val bIds = intent.getStringArrayListExtra("teamBIds")
            bNames?.let {
                for (i in it.indices) {
                    val j = if (bJerseys != null && bJerseys.size > i) bJerseys[i] else "0"
                    addPlayerCard(1, it[i], j, bPhotos?.get(i), bIds?.get(i))
                }
            }
        }

        refreshStartButton()
    }

    private fun showAddDialog(team: Int) {
        currentTeam = team
        pendingId = null
        val view = layoutInflater.inflate(R.layout.dialog_add_player, null)
        val nameInput = view.findViewById<EditText>(R.id.playerNameInput)
        val jerseyInput = view.findViewById<EditText>(R.id.playerJerseyInput)
        val quickAdd = view.findViewById<AutoCompleteTextView>(R.id.quickAddInput)
        val preview = view.findViewById<ImageView>(R.id.ivDialogPreview)

        AppDatabase.ioExecutor.execute {
            val ctx = applicationContext ?: return@execute
            val allExisting = getInstance(ctx).playerDao().getAllPlayers()?.filterNotNull() ?: emptyList()

            val displayList = allExisting.map { p ->
                val safeName = p.name ?: "Unknown"
                val safeJersey = p.jerseyNumber ?: "0"
                "#$safeJersey $safeName"
            }

            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val qAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, displayList) {
                    override fun getFilter(): Filter {
                        return object : Filter() {
                            override fun performFiltering(constraint: CharSequence?): FilterResults {
                                val results = FilterResults()
                                if (constraint.isNullOrEmpty()) {
                                    results.values = emptyList<String>()
                                    results.count = 0
                                } else {
                                    val search = constraint.toString().lowercase()
                                    val filtered = allExisting.filter { p ->
                                        p.name?.lowercase()?.contains(search) == true || p.jerseyNumber?.contains(search) == true
                                    }.map { p -> "#${p.jerseyNumber} ${p.name}" }
                                    results.values = filtered
                                    results.count = filtered.size
                                }
                                return results
                            }

                            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                                clear()
                                (results.values as? List<String>)?.let { addAll(it) }
                                notifyDataSetChanged()
                            }
                        }
                    }
                }

                quickAdd.setAdapter(qAdapter)
                quickAdd.setOnItemClickListener { parent, view1, position, id_ptr ->
                    val selected = parent.getItemAtPosition(position) as? String
                    for (p in allExisting) {
                        val matchStr = "#${p.jerseyNumber} ${p.name}"
                        if (matchStr == selected) {
                            nameInput.setText(p.name)
                            jerseyInput.setText(p.jerseyNumber)
                            pendingPhotoUri = p.photoUri
                            pendingId = p.id
                            if (!p.photoUri.isNullOrEmpty()) {
                                try {
                                    preview.setImageURI(Uri.parse(p.photoUri))
                                } catch (e: Exception) {
                                    Glide.with(this@PlayerEntryActivity).load(p.photoUri).into(preview)
                                }
                            } else {
                                preview.setImageDrawable(null)
                            }
                            quickAdd.setText("")
                            break
                        }
                    }
                }
            }
        }

        val dialog = showDynamicDialog {
            setTitle("Add Player — ${if (team == 0) teamAName else teamBName}")
            setView(view)
            setPositiveButton("Add (no photo)", null)
            setNegativeButton("Cancel", null)
        }

        view.findViewById<View>(R.id.btnTakePhoto).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val jersey = jerseyInput.text.toString().trim()
            if (name.isEmpty()) {
                nameInput.error = getString(R.string.error_enter_name)
                return@setOnClickListener
            }
            if (jersey.isEmpty()) {
                jerseyInput.error = "Enter jersey (1-999)"
                return@setOnClickListener
            }
            if (isDuplicatePlayer(pendingId, name, jersey)) {
                Toast.makeText(this, "Player already added!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingName = name
            pendingJersey = jersey
            dialog.dismiss()
            launchCamera()
        }

        view.findViewById<View>(R.id.btnPickGallery).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val jersey = jerseyInput.text.toString().trim()
            if (name.isEmpty()) {
                nameInput.error = getString(R.string.error_enter_name)
                return@setOnClickListener
            }
            if (jersey.isEmpty()) {
                jerseyInput.error = "Enter jersey (1-999)"
                return@setOnClickListener
            }
            if (isDuplicatePlayer(pendingId, name, jersey)) {
                Toast.makeText(this, "Player already added!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingName = name
            pendingJersey = jersey
            dialog.dismiss()
            launchGallery()
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val jersey = jerseyInput.text.toString().trim()
            if (name.isNotEmpty() && jersey.isNotEmpty()) {
                if (isDuplicatePlayer(pendingId, name, jersey)) {
                    Toast.makeText(this@PlayerEntryActivity, "Player already added!", Toast.LENGTH_SHORT).show()
                } else {
                    addPlayerCard(currentTeam, name, jersey, pendingPhotoUri, pendingId)
                    dialog.dismiss()
                }
            } else {
                if (name.isEmpty()) nameInput.error = "Required"
                if (jersey.isEmpty()) jerseyInput.error = "Required"
            }
        }
    }

    private fun isDuplicatePlayer(id: String?, name: String?, jersey: String?): Boolean {
        val n = name ?: ""
        val j = jersey ?: ""
        return (teamAPlayers + teamBPlayers).any { p ->
            (id != null && p.id == id) || (p.name?.equals(n, ignoreCase = true) == true && p.jersey == j)
        }
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
        } catch (e: Exception) {
            Toast.makeText(this, "${getString(R.string.error_no_camera)}: ${e.message}", Toast.LENGTH_LONG).show()
            addPlayerCard(currentTeam, pendingName, pendingJersey, null, pendingId)
            pendingName = ""
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_CAMERA && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        }
    }

    private fun launchGallery() {
        pickMedia.launch(PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
            .build())
    }


    private fun addPlayerCard(team: Int, name: String?, jersey: String?, photoUri: String?, id: String?) {
        val playerList = if (team == 0) teamAPlayers else teamBPlayers
        val container = if (team == 0) teamAContainer else teamBContainer
        if (container == null) return

        if (isDuplicatePlayer(id, name, jersey)) {
            Toast.makeText(this, "Player already added!", Toast.LENGTH_SHORT).show()
            return
        }

        // Always attempt to save/update the player in the database
        commitOrUpdatePlayer(id, name, jersey, photoUri, playerList, container)
    }

    private fun commitOrUpdatePlayer(id: String?, name: String?, jersey: String?, photo: String?, playerList: MutableList<TempPlayer>, container: LinearLayout) {
        val trimmedName = name?.trim() ?: ""
        AppDatabase.ioExecutor.execute {
            val db = getInstance(this)
            
            // 1. Try to find by ID
            // 2. If no ID (manual entry), try to find by Name (Fuzzy/Trimmed/Case-Insensitive via SQL)
            val pe = if (id != null) {
                db.playerDao().getPlayerById(id)
            } else {
                db.playerDao().getPlayerByName(trimmedName)
            }?.apply {
                this.name = trimmedName
                this.jerseyNumber = jersey ?: ""
                if (!photo.isNullOrEmpty()) this.photoUri = photo
            }

            val finalEntity = pe ?: PlayerEntity(trimmedName, jersey, photo)
            db.playerDao().insertPlayer(finalEntity)
            
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val player = TempPlayer(finalEntity.id, finalEntity.name, finalEntity.jerseyNumber, finalEntity.photoUri)
                playerList.add(player)
                renderPlayerCard(container, player, playerList)
                
                // Final cleanup of state after successful add
                pendingId = null
                pendingName = ""
                pendingJersey = ""
                pendingPhotoUri = null
            }
        }
    }

    private fun renderPlayerCard(container: LinearLayout, player: TempPlayer, playerList: MutableList<TempPlayer>) {
        val card = layoutInflater.inflate(R.layout.item_player_card, container, false)
        val safeName = player.name ?: "Unknown"
        val safeJersey = player.jersey ?: "0"
        card.findViewById<TextView>(R.id.playerName).text = "$safeName\n#$safeJersey"
        if (!player.photoUri.isNullOrEmpty()) {
            try {
                card.findViewById<ImageView>(R.id.playerPhoto).setImageURI(Uri.parse(player.photoUri))
            } catch (e: Exception) {
                Log.e("PlayerEntry", "Failed to load photo: ${e.message}")
            }
        }
        card.findViewById<View>(R.id.btnRemove).setOnClickListener {
            container.removeView(card)
            playerList.remove(player)
            refreshStartButton()
        }
        container.addView(card)
        refreshStartButton()
    }

    private fun refreshStartButton() {
        val ok = teamAPlayers.isNotEmpty() && teamBPlayers.isNotEmpty()
        btnStartMatch?.isEnabled = ok
        btnStartMatch?.alpha = if (ok) 1.0f else 0.5f
    }

    private fun startMatch() {
        if (teamAPlayers.size <= 1 && teamBPlayers.size <= 1) {
            Toast.makeText(this, "One team must have 2 Players", Toast.LENGTH_SHORT).show()
            return
        }

        AppDatabase.ioExecutor.execute {
            val draft = DraftMatchEntity()
            draft.teamAName = teamAName
            draft.teamBName = teamBName
            draft.overs = overs
            draft.ballType = ballType
            draft.ruleRunsOnWide = ruleRunsOnWide
            draft.ruleFreeHit = ruleFreeHit
            draft.ruleRunsOnBye = ruleRunsOnBye
            draft.ruleOverthrow = ruleOverthrow
            draft.ruleEveryPlayerBats = ruleEveryPlayerBats

            draft.teamANames = ArrayList(teamAPlayers.map { it.name ?: "Unknown" })
            draft.teamAPhotos = ArrayList(teamAPlayers.map { it.photoUri ?: "" })
            draft.teamAIds = ArrayList(teamAPlayers.map { it.id ?: UUID.randomUUID().toString() })

            draft.teamBNames = ArrayList(teamBPlayers.map { it.name ?: "Unknown" })
            draft.teamBPhotos = ArrayList(teamBPlayers.map { it.photoUri ?: "" })
            draft.teamBIds = ArrayList(teamBPlayers.map { it.id ?: UUID.randomUUID().toString() })

            getInstance(this).draftDao().insertDraft(draft)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val intent = Intent(this, TossActivity::class.java)
                intent.putExtra("draftId", draft.id)
                startActivity(intent)
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("PLAYER_${stamp}_", ".jpg", dir)
    }

    internal class TempPlayer(val id: String?, val name: String?, val jersey: String?, val photoUri: String?)

    companion object {
        private const val PERM_CAMERA = 2001
    }
}

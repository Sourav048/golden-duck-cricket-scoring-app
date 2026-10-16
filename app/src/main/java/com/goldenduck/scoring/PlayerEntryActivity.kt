package com.goldenduck.scoring

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.goldenduck.scoring.AppDatabase.Companion.getInstance
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
    private var venue: String? = null
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
    private var currentDraftId: String? = null

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
        outState.putString("currentDraftId", currentDraftId)
        outState.putString("venue", venue)
        outState.putString("teamAName", teamAName)
        outState.putString("teamBName", teamBName)
        outState.putInt("overs", overs)
        outState.putString("ballType", ballType)
        outState.putInt("currentTeam", currentTeam)
        outState.putString("pendingName", pendingName)
        outState.putString("pendingJersey", pendingJersey)
        outState.putString("pendingId", pendingId)
        outState.putString("pendingPhotoUri", pendingPhotoUri)
        outState.putBoolean("ruleRunsOnWide", ruleRunsOnWide)
        outState.putBoolean("ruleFreeHit", ruleFreeHit)
        outState.putBoolean("ruleRunsOnBye", ruleRunsOnBye)
        outState.putBoolean("ruleOverthrow", ruleOverthrow)
        outState.putBoolean("ruleEveryPlayerBats", ruleEveryPlayerBats)
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
        currentDraftId = savedInstanceState?.getString("currentDraftId") ?: intent.getStringExtra("draftId")
        venue = intent.getStringExtra("venue")
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

        findViewById<View>(R.id.btnBackPlayerEntry)?.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<View>(R.id.btnAddPlayerA).setOnClickListener { showAddDialog(0) }
        findViewById<View>(R.id.btnAddPlayerB).setOnClickListener { showAddDialog(1) }
        btnStartMatch?.setOnClickListener { startMatch() }

        if (savedInstanceState != null) {
            venue = savedInstanceState.getString("venue", venue)
            teamAName = savedInstanceState.getString("teamAName", teamAName)
            teamBName = savedInstanceState.getString("teamBName", teamBName)
            overs = savedInstanceState.getInt("overs", overs)
            ballType = savedInstanceState.getString("ballType", ballType)
            ruleRunsOnWide = savedInstanceState.getBoolean("ruleRunsOnWide", ruleRunsOnWide)
            ruleFreeHit = savedInstanceState.getBoolean("ruleFreeHit", ruleFreeHit)
            ruleRunsOnBye = savedInstanceState.getBoolean("ruleRunsOnBye", ruleRunsOnBye)
            ruleOverthrow = savedInstanceState.getBoolean("ruleOverthrow", ruleOverthrow)
            ruleEveryPlayerBats = savedInstanceState.getBoolean("ruleEveryPlayerBats", ruleEveryPlayerBats)

            currentTeam = savedInstanceState.getInt("currentTeam")
            pendingName = savedInstanceState.getString("pendingName", "")
            pendingJersey = savedInstanceState.getString("pendingJersey", "")
            pendingId = savedInstanceState.getString("pendingId", null)
            pendingPhotoUri = savedInstanceState.getString("pendingPhotoUri", null)
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

        if (savedInstanceState == null) {
            val aNames = intent.getStringArrayListExtra("teamANames")
            val aPhotos = intent.getStringArrayListExtra("teamAPhotos")
            val aJerseys = intent.getStringArrayListExtra("teamAJerseys")
            val aIds = intent.getStringArrayListExtra("teamAIds")
            aNames?.let {
                for (i in it.indices) {
                    val j = if (aJerseys != null && aJerseys.size > i) aJerseys[i] else "0"
                    addPlayerCard(0, it[i], j, aPhotos?.getOrNull(i), aIds?.getOrNull(i))
                }
            }

            val bNames = intent.getStringArrayListExtra("teamBNames")
            val bPhotos = intent.getStringArrayListExtra("teamBPhotos")
            val bJerseys = intent.getStringArrayListExtra("teamBJerseys")
            val bIds = intent.getStringArrayListExtra("teamBIds")
            bNames?.let {
                for (i in it.indices) {
                    val j = if (bJerseys != null && bJerseys.size > i) bJerseys[i] else "0"
                    addPlayerCard(1, it[i], j, bPhotos?.getOrNull(i), bIds?.getOrNull(i))
                }
            }
        }

        refreshStartButton()
    }

    private fun showAddDialog(team: Int) {
        currentTeam = team
        val view = layoutInflater.inflate(R.layout.dialog_select_squad_players, null)
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val btnAddNewPlayerTop = view.findViewById<Button>(R.id.btnAddNewPlayerTop)
        val rvAvailablePlayers = view.findViewById<RecyclerView>(R.id.rvAvailablePlayers)
        val pbLoading = view.findViewById<android.widget.ProgressBar>(R.id.pbLoadingAvailablePlayers)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyAvailablePlayers)
        val btnDismiss = view.findViewById<Button>(R.id.btnDismiss)
        val btnAddSelected = view.findViewById<Button>(R.id.btnAddSelectedPlayers)

        val teamName = if (team == 0) teamAName else teamBName
        tvTitle.text = "Add Player — $teamName"

        rvAvailablePlayers.layoutManager = LinearLayoutManager(this)

        val selectedPlayers = mutableSetOf<PlayerEntity>()

        val adapter = SelectSquadPlayerAdapter(emptyList(), selectedPlayers) { count ->
            btnAddSelected.text = if (count > 0) "Add ($count)" else "Add"
        }
        rvAvailablePlayers.adapter = adapter
        rvAvailablePlayers.visibility = View.VISIBLE
        pbLoading?.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var pendingRunnable: Runnable? = null

        val dialog = showDynamicDialog {
            setView(view)
        }

        dialog.setOnDismissListener {
            pendingRunnable?.let { handler.removeCallbacks(it) }
        }

        btnAddNewPlayerTop.setOnClickListener {
            dialog.dismiss()
            showAddNewPlayerSingleDialog(team)
        }

        btnDismiss.setOnClickListener {
            dialog.dismiss()
        }

        AppDatabase.ioExecutor.execute {
            val ctx = applicationContext ?: return@execute
            val gId = GullySyncManager.getCurrentGullyId(ctx) ?: "local"
            val db = getInstance(ctx)
            val allExisting = db.playerDao().getAllPlayersByGully(gId)?.filterNotNull() ?: emptyList()

            // Fetch overall points map for sorting & displaying points
            val overallRankings = db.statsDao().getOverallRankings(gId) ?: emptyList()
            val overallScoreMap = overallRankings.filterNotNull().associate { (it.playerId ?: "") to it.total }

            // Filter out players already added to Team A or Team B by ID or Name
            val addedIds = (teamAPlayers + teamBPlayers).mapNotNull { it.id }.toSet()
            val addedNames = (teamAPlayers + teamBPlayers).mapNotNull {
                it.name?.lowercase()?.trim()
            }.toSet()

            val available = allExisting.filter { p ->
                val pId = p.id
                val pName = p.name?.lowercase()?.trim() ?: ""
                pId !in addedIds && pName !in addedNames
            }.sortedWith { p1, p2 ->
                val score1 = overallScoreMap[p1.id] ?: 0
                val score2 = overallScoreMap[p2.id] ?: 0
                if (score1 != score2) {
                    score2.compareTo(score1) // Higher overall points first
                } else {
                    (p1.name ?: "").compareTo(p2.name ?: "", ignoreCase = true)
                }
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                adapter.setPointsMap(overallScoreMap)
            }

            if (available.isEmpty()) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    pbLoading?.visibility = View.GONE
                    rvAvailablePlayers.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                }
                return@execute
            }

            var index = 0
            val cascadeRunnable = object : Runnable {
                override fun run() {
                    if (isFinishing || isDestroyed) return
                    if (index < available.size) {
                        if (index == 0) {
                            pbLoading?.visibility = View.GONE
                            tvEmpty.visibility = View.GONE
                            rvAvailablePlayers.visibility = View.VISIBLE
                        }
                        adapter.addPlayer(available[index])
                        index++
                        if (index < available.size) {
                            handler.postDelayed(this, 30) // 30ms interval between each player insertion
                        }
                    }
                }
            }
            pendingRunnable = cascadeRunnable
            handler.post(cascadeRunnable)
        }

        btnAddSelected.setOnClickListener {
            if (selectedPlayers.isEmpty()) {
                Toast.makeText(this, "Please select at least one player", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val targetList = if (team == 0) teamAPlayers else teamBPlayers
            val targetContainer = if (team == 0) teamAContainer else teamBContainer

            if (targetContainer != null) {
                for (p in selectedPlayers) {
                    val resolvedPhoto = p.photoUri.takeIf { !it.isNullOrBlank() } ?: p.photoUrl
                    val temp = TempPlayer(p.id, p.name, p.jerseyNumber, resolvedPhoto)
                    targetList.add(temp)
                    renderPlayerCard(targetContainer, temp, targetList)
                }
                refreshStartButton()
            }

            dialog.dismiss()
        }
    }

    private fun showAddNewPlayerSingleDialog(team: Int) {
        currentTeam = team
        pendingId = null
        val view = layoutInflater.inflate(R.layout.dialog_add_player, null)
        val nameInput = view.findViewById<EditText>(R.id.playerNameInput)
        val jerseyInput = view.findViewById<EditText>(R.id.playerJerseyInput)

        view.findViewById<View>(R.id.quickAddContainer)?.visibility = View.GONE
        view.findViewById<View>(R.id.quickAddDivider)?.visibility = View.GONE

        val teamName = if (team == 0) teamAName else teamBName

        val dialog = showDynamicDialog {
            setTitle("Add New Player — $teamName")
            setView(view)
            setPositiveButton("Save", null)
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
            if (isDuplicatePlayer(null, name, jersey)) {
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
            if (isDuplicatePlayer(null, name, jersey)) {
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
                if (isDuplicatePlayer(null, name, jersey)) {
                    Toast.makeText(this@PlayerEntryActivity, "Player already added!", Toast.LENGTH_SHORT).show()
                } else {
                    addPlayerCard(currentTeam, name, jersey, pendingPhotoUri, null, dialog)
                }
            } else {
                if (name.isEmpty()) nameInput.error = "Required"
                if (jersey.isEmpty()) jerseyInput.error = "Required"
            }
        }
    }

    private inner class SelectSquadPlayerAdapter(
        availablePlayers: List<PlayerEntity>,
        private val selectedSet: MutableSet<PlayerEntity>,
        private val onSelectionChanged: (Int) -> Unit
    ) : RecyclerView.Adapter<SelectSquadPlayerAdapter.Holder>() {

        private val displayList = ArrayList(availablePlayers)
        private var pointsMap: Map<String, Int> = emptyMap()

        fun setPointsMap(map: Map<String, Int>) {
            this.pointsMap = map
            notifyDataSetChanged()
        }

        fun addPlayer(player: PlayerEntity) {
            displayList.add(player)
            notifyItemInserted(displayList.size - 1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_select_squad_player, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val p = displayList[position]
            val safeName = p.name?.takeIf { it.isNotBlank() } ?: "Unknown"
            val safeJersey = p.jerseyNumber?.takeIf { it.isNotBlank() } ?: "0"
            val pts = pointsMap[p.id] ?: 0

            holder.tvName.text = safeName
            holder.tvJersey.text = if (pts > 0) "#$safeJersey • $pts pts" else "#$safeJersey"

            val photoTarget = p.photoUri.takeIf { !it.isNullOrBlank() } ?: p.photoUrl ?: ""
            if (photoTarget.isNotEmpty()) {
                try {
                    Glide.with(holder.itemView.context)
                        .load(photoTarget)
                        .placeholder(R.drawable.ic_person_placeholder)
                        .error(R.drawable.ic_person_placeholder)
                        .into(holder.ivPhoto)
                } catch (_: Exception) {
                    holder.ivPhoto.setImageResource(R.drawable.ic_person_placeholder)
                }
            } else {
                holder.ivPhoto.setImageResource(R.drawable.ic_person_placeholder)
            }

            val isSelected = selectedSet.contains(p)
            holder.cbSelect.isChecked = isSelected

            holder.itemView.setOnClickListener {
                val currentPos = holder.adapterPosition
                if (currentPos == RecyclerView.NO_POSITION) return@setOnClickListener

                val item = displayList[currentPos]
                val newlySelected = !selectedSet.contains(item)

                if (newlySelected) {
                    selectedSet.add(item)
                    if (currentPos > 0) {
                        displayList.removeAt(currentPos)
                        displayList.add(0, item)
                        notifyItemMoved(currentPos, 0)
                        notifyItemChanged(0)
                    } else {
                        notifyItemChanged(0)
                    }
                } else {
                    selectedSet.remove(item)
                    val targetPos = selectedSet.size
                    if (currentPos != targetPos) {
                        displayList.removeAt(currentPos)
                        displayList.add(targetPos, item)
                        notifyItemMoved(currentPos, targetPos)
                        notifyItemChanged(targetPos)
                    } else {
                        notifyItemChanged(currentPos)
                    }
                }

                onSelectionChanged(selectedSet.size)
            }
        }

        override fun getItemCount(): Int = displayList.size

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val ivPhoto: ImageView = v.findViewById(R.id.ivSelectPlayerPhoto)
            val tvName: TextView = v.findViewById(R.id.tvSelectPlayerName)
            val tvJersey: TextView = v.findViewById(R.id.tvSelectPlayerJersey)
            val cbSelect: com.google.android.material.checkbox.MaterialCheckBox = v.findViewById(R.id.cbSelectPlayer)
        }
    }

    private fun isDuplicatePlayer(id: String?, name: String?, jersey: String? = null): Boolean {
        val n = name?.trim() ?: ""
        if (n.isEmpty()) return false
        return (teamAPlayers + teamBPlayers).any { p ->
            (id != null && p.id == id) || (p.name?.trim()?.equals(n, ignoreCase = true) == true)
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


    private fun addPlayerCard(team: Int, name: String?, jersey: String?, photoUri: String?, id: String?, dialogToDismiss: AlertDialog? = null) {
        val playerList = if (team == 0) teamAPlayers else teamBPlayers
        val container = if (team == 0) teamAContainer else teamBContainer
        if (container == null) return

        if (isDuplicatePlayer(id, name, jersey)) {
            Toast.makeText(this, "Player already added!", Toast.LENGTH_SHORT).show()
            return
        }

        // Always attempt to save/update the player in the database
        commitOrUpdatePlayer(id, name, jersey, photoUri, playerList, container, dialogToDismiss)
    }

    private fun commitOrUpdatePlayer(id: String?, name: String?, jersey: String?, photo: String?, playerList: MutableList<TempPlayer>, container: LinearLayout, dialogToDismiss: AlertDialog? = null) {
        val trimmedName = name?.trim() ?: ""
        val j = jersey ?: "0"
        val gId = GullySyncManager.getCurrentGullyId(this) ?: "local"
        AppDatabase.ioExecutor.execute {
            val db = getInstance(this)
            
            // Search for existing match by ID or by Name
            val existing = if (id != null) {
                db.playerDao().getPlayerById(id)
            } else {
                db.playerDao().getPlayerByNameByGully(trimmedName, gId)
            }

            if (existing == null) {
                // NEW PLAYER: Check network connection first
                if (!GullySyncManager.isNetworkAvailable(this@PlayerEntryActivity)) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this@PlayerEntryActivity, "Internet connection required to create a new player.", Toast.LENGTH_LONG).show()
                        }
                    }
                    return@execute
                }

                // Check if player name exists globally in another Gully
                GullySyncManager.checkGlobalPlayerExistsByName(this@PlayerEntryActivity, trimmedName, gId) { existsInOtherGully ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread

                        if (existsInOtherGully) {
                            AlertDialog.Builder(this@PlayerEntryActivity)
                                .setMessage("This player already exists in some other Gully. Change your name a little bit to create a new player.")
                                .setPositiveButton("Dismiss") { d, _ -> d.dismiss() }
                                .show()
                        } else {
                            // Unique player name: Create and Sync
                            AppDatabase.ioExecutor.execute {
                                val finalEntity = PlayerEntity(trimmedName, j, photo).apply { this.gullyId = gId }
                                db.playerDao().insertPlayer(finalEntity)
                                GullySyncManager.syncPlayerToCloud(gId, finalEntity)

                                val resolvedPhoto = finalEntity.photoUri.takeIf { !it.isNullOrBlank() } ?: finalEntity.photoUrl
                                runOnUiThread {
                                    if (isFinishing || isDestroyed) return@runOnUiThread
                                    val player = TempPlayer(finalEntity.id, finalEntity.name, finalEntity.jerseyNumber, resolvedPhoto)
                                    playerList.add(player)
                                    renderPlayerCard(container, player, playerList)

                                    // Final cleanup of state
                                    pendingId = null
                                    pendingName = ""
                                    pendingJersey = ""
                                    pendingPhotoUri = null
                                    dialogToDismiss?.dismiss()
                                }
                            }
                        }
                    }
                }
            } else {
                // EXACT PLAYER EXISTS: Adopt them.
                val finalEntity = existing
                val isNewCameraGalleryPhoto = (photo != null && photo == pendingPhotoUri && photo != existing.photoUri && (photo.contains("PLAYER_") || photo.contains("player_photos")))
                if (isNewCameraGalleryPhoto || (existing.photoUri.isNullOrEmpty() && !photo.isNullOrEmpty())) {
                    existing.photoUri = photo
                    db.playerDao().updatePlayer(existing)
                    GullySyncManager.syncPlayerToCloud(gId, existing)
                }
                if (id == null) {
                    runOnUiThread {
                        Toast.makeText(this@PlayerEntryActivity, "Added existing player '$trimmedName' from League database!", Toast.LENGTH_SHORT).show()
                    }
                }

                val resolvedPhoto = finalEntity.photoUri.takeIf { !it.isNullOrBlank() } ?: finalEntity.photoUrl
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val player = TempPlayer(finalEntity.id, finalEntity.name, finalEntity.jerseyNumber, resolvedPhoto)
                    playerList.add(player)
                    renderPlayerCard(container, player, playerList)

                    // Final cleanup of state
                    pendingId = null
                    pendingName = ""
                    pendingJersey = ""
                    pendingPhotoUri = null
                    dialogToDismiss?.dismiss()
                }
            }
        }
    }

    private fun renderPlayerCard(container: LinearLayout, player: TempPlayer, playerList: MutableList<TempPlayer>) {
        val card = layoutInflater.inflate(R.layout.item_player_card, container, false)
        val safeName = player.name ?: "Unknown"
        val safeJersey = player.jersey ?: "0"
        card.findViewById<TextView>(R.id.playerName).text = "$safeName\n#$safeJersey"
        
        val ivPhoto = card.findViewById<ImageView>(R.id.playerPhoto)
        val photoUri = player.photoUri
        if (!photoUri.isNullOrEmpty()) {
            val cleanPath = photoUri.removePrefix("file://")
            val sig = if (cleanPath.startsWith("/")) {
                val f = File(cleanPath)
                if (f.exists()) "${player.id}_${f.lastModified()}_${f.length()}" else "${player.id}_${photoUri.hashCode()}"
            } else {
                "${player.id}_${photoUri.hashCode()}"
            }
            Glide.with(this)
                .load(photoUri)
                .signature(com.bumptech.glide.signature.ObjectKey(sig))
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(ivPhoto)
        } else {
            ivPhoto.setImageResource(android.R.drawable.ic_menu_gallery)
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
        if (teamAPlayers.isEmpty() || teamBPlayers.isEmpty()) {
            Toast.makeText(this, "Each team must have at least 1 Player", Toast.LENGTH_SHORT).show()
            return
        }

        val dId = currentDraftId ?: intent.getStringExtra("draftId") ?: UUID.randomUUID().toString()
        currentDraftId = dId

        AppDatabase.ioExecutor.execute {
            val gId = GullySyncManager.getCurrentGullyId(this@PlayerEntryActivity) ?: "local"
            val db = getInstance(this)
            val draft = db.draftDao().getDraftById(dId) ?: DraftMatchEntity().apply { id = dId }
            draft.gullyId = gId
            draft.venue = venue
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

            db.draftDao().insertDraft(draft)

            // CLOUD SYNC: Draft Match (The Hand-off Feature)
            if (gId != "local") {
                GullySyncManager.syncDraftToCloud(gId, draft)
            }

            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val intent = Intent(this, TossActivity::class.java).apply {
                    putExtra("draftId", draft.id)
                    putExtra("venue", venue)
                    putExtra("teamA", teamAName)
                    putExtra("teamB", teamBName)
                    putExtra("teamAName", teamAName)
                    putExtra("teamBName", teamBName)
                    putExtra("overs", overs)
                    putExtra("ballType", ballType)
                    putExtra("ruleRunsOnWide", ruleRunsOnWide)
                    putExtra("ruleFreeHit", ruleFreeHit)
                    putExtra("ruleRunsOnBye", ruleRunsOnBye)
                    putExtra("ruleOverthrow", ruleOverthrow)
                    putExtra("ruleEveryPlayerBats", ruleEveryPlayerBats)

                    putStringArrayListExtra("teamANames", ArrayList(teamAPlayers.map { it.name ?: "Unknown" }))
                    putStringArrayListExtra("teamAPhotos", ArrayList(teamAPlayers.map { it.photoUri ?: "" }))
                    putStringArrayListExtra("teamAIds", ArrayList(teamAPlayers.map { it.id ?: "" }))

                    putStringArrayListExtra("teamBNames", ArrayList(teamBPlayers.map { it.name ?: "Unknown" }))
                    putStringArrayListExtra("teamBPhotos", ArrayList(teamBPlayers.map { it.photoUri ?: "" }))
                    putStringArrayListExtra("teamBIds", ArrayList(teamBPlayers.map { it.id ?: "" }))
                }
                startActivity(intent)
            }
        }
    }

    override fun finish() {
        val resultIntent = Intent().apply {
            currentDraftId?.let { putExtra("draftId", it) }
            putStringArrayListExtra("teamANames", ArrayList(teamAPlayers.map { it.name ?: "" }))
            putStringArrayListExtra("teamAPhotos", ArrayList(teamAPlayers.map { it.photoUri ?: "" }))
            putStringArrayListExtra("teamAJerseys", ArrayList(teamAPlayers.map { it.jersey ?: "0" }))
            putStringArrayListExtra("teamAIds", ArrayList(teamAPlayers.map { it.id ?: "" }))

            putStringArrayListExtra("teamBNames", ArrayList(teamBPlayers.map { it.name ?: "" }))
            putStringArrayListExtra("teamBPhotos", ArrayList(teamBPlayers.map { it.photoUri ?: "" }))
            putStringArrayListExtra("teamBJerseys", ArrayList(teamBPlayers.map { it.jersey ?: "0" }))
            putStringArrayListExtra("teamBIds", ArrayList(teamBPlayers.map { it.id ?: "" }))
        }
        setResult(RESULT_OK, resultIntent)
        super.finish()
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

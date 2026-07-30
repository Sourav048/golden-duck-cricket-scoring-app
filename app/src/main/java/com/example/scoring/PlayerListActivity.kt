package com.example.scoring

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.scoring.AppDatabase.Companion.getInstance
import com.example.scoring.RankingRegistry.applyPrestige
import com.example.scoring.RankingRegistry.refresh
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.firestore.ListenerRegistration
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerListActivity : BaseActivity() {
    private var recyclerView: RecyclerView? = null
    private var adapter: PlayerAdapter? = null
    private var db: AppDatabase? = null
    private var playerListener: ListenerRegistration? = null

    private var pendingName = ""
    private var pendingJersey = ""
    private var pendingPhotoUri: String? = null
    private var currentCameraUri: Uri? = null

    private val pickMedia: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val savedPath = PhotoUtils.savePhoto(this, uri)
                if (savedPath != null) handleImageResult(savedPath)
            }
        }

    private val takePicture: ActivityResultLauncher<Uri> =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && currentCameraUri != null) {
                val savedPath = PhotoUtils.savePhoto(this, currentCameraUri!!)
                if (savedPath != null) handleImageResult(savedPath)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_player_list)

            db = getInstance(this)
            recyclerView = findViewById(R.id.playerRecyclerView)
            recyclerView?.layoutManager = LinearLayoutManager(this)

            findViewById<View>(R.id.fabAddPlayer).setOnClickListener { showAddPlayerDialog() }

            val btnCompare = findViewById<View>(R.id.btnComparePlayers)
            btnCompare?.setOnClickListener {
                startActivity(Intent(this, PlayerCompareActivity::class.java))
            }

            refresh(this, object : RankingRegistry.OnRankingsLoaded {
                override fun onLoaded() {
                    if (!isFinishing) {
                        loadLocalPlayers()
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Player List initialization error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleImageResult(uriString: String) {
        if (pendingName.isNotEmpty()) {
            savePlayer(pendingName, pendingJersey, uriString)
        } else {
            pendingPhotoUri = uriString
            Toast.makeText(this, "Photo captured. Please re-open dialog to save.", Toast.LENGTH_SHORT).show()
        }
        pendingName = ""
        pendingJersey = ""
    }

    override fun onPause() {
        playerListener?.remove()
        playerListener = null
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        loadLocalPlayers()
    }

    private fun loadLocalPlayers() {
        AppDatabase.ioExecutor.execute {
            val playersListFromDb = db?.playerDao()?.getAllPlayers() ?: ArrayList()
            val playersList = ArrayList(playersListFromDb)

            playersList.sortWith { p1, p2 ->
                if (p1 == null) return@sortWith 1
                if (p2 == null) return@sortWith -1

                val p1Ov1 = p1.id == RankingRegistry.topOverallId
                val p2Ov1 = p2.id == RankingRegistry.topOverallId
                if (p1Ov1 != p2Ov1) return@sortWith if (p1Ov1) -1 else 1

                val p1Bat1 = p1.id == RankingRegistry.topBattingId
                val p2Bat1 = p2.id == RankingRegistry.topBattingId
                if (p1Bat1 != p2Bat1) return@sortWith if (p1Bat1) -1 else 1

                val p1Bowl1 = p1.id == RankingRegistry.topBowlingId
                val p2Bowl1 = p2.id == RankingRegistry.topBowlingId
                if (p1Bowl1 != p2Bowl1) return@sortWith if (p1Bowl1) -1 else 1

                p1.createdAt.compareTo(p2.createdAt)
            }

            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                updateUI(playersList)
            }
        }
    }

    private fun updateUI(players: MutableList<PlayerEntity?>) {
        val v = findViewById<View>(android.R.id.content)
        val shimmer = v.findViewById<ShimmerFrameLayout>(R.id.shimmerPlayerList)
        shimmer?.let {
            it.stopShimmer()
            it.visibility = View.GONE
        }
        recyclerView?.visibility = View.VISIBLE

        val empty = v.findViewById<View>(R.id.layoutEmptyPlayers)
        empty?.visibility = if (players.isEmpty()) View.VISIBLE else View.GONE

        adapter = PlayerAdapter(players)
        recyclerView?.adapter = adapter
    }

    private fun showAddPlayerDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_player, null)
        val nameInput = view.findViewById<EditText>(R.id.playerNameInput)
        val jerseyInput = view.findViewById<EditText>(R.id.playerJerseyInput)
        val quickAdd = view.findViewById<AutoCompleteTextView>(R.id.quickAddInput)
        val preview = view.findViewById<ImageView>(R.id.ivDialogPreview)

        view.findViewById<View>(R.id.quickAddContainer)?.visibility = View.GONE
        view.findViewById<View>(R.id.quickAddDivider)?.visibility = View.GONE

        quickAdd.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.length >= 1) {
                    AppDatabase.ioExecutor.execute {
                        val matches = db?.playerDao()?.getPlayersByJersey(s.toString()) ?: ArrayList()
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            val names = matches.filterNotNull().map { "${it.name} (${it.jerseyNumber})" }
                            val qAdapter = ArrayAdapter(this@PlayerListActivity, android.R.layout.simple_dropdown_item_1line, names)
                            quickAdd.setAdapter(qAdapter)
                            if (names.isNotEmpty()) quickAdd.showDropDown()
                        }
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        quickAdd.setOnItemClickListener { parent, view1, position, id ->
            val selected = parent.getItemAtPosition(position) as? String
            AppDatabase.ioExecutor.execute {
                val players = db?.playerDao()?.getAllPlayers() ?: return@execute
                for (p in players) {
                    if (p == null) continue
                    val matchName = "${p.name} (${p.jerseyNumber})"
                    if (matchName == selected) {
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            nameInput.setText(p.name)
                            jerseyInput.setText(p.jerseyNumber)
                            pendingPhotoUri = p.photoUri
                            if (!p.photoUri.isNullOrEmpty()) {
                                try { 
                                    preview.setImageURI(Uri.parse(p.photoUri)) 
                                } catch (e: Exception) {
                                    Log.e("PlayerList", "Failed to load photo: ${e.message}")
                                }
                            }
                            quickAdd.setText("")
                        }
                        break
                    }
                }
            }
        }

        val dialog = showDynamicDialog {
            setTitle("New Player")
            setView(view)
            setPositiveButton("Save", null)
            setNegativeButton("Cancel", null)
        }

        view.findViewById<View>(R.id.btnTakePhoto).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val jersey = jerseyInput.text.toString().trim()
            if (name.isEmpty()) { nameInput.error = "Enter name first"; return@setOnClickListener }
            if (jersey.isEmpty()) { jerseyInput.error = "Enter jersey (1-999)"; return@setOnClickListener }
            pendingName = name
            pendingJersey = jersey
            dialog.dismiss()
            launchCamera()
        }

        view.findViewById<View>(R.id.btnPickGallery).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val jersey = jerseyInput.text.toString().trim()
            if (name.isEmpty()) { nameInput.error = "Enter name first"; return@setOnClickListener }
            if (jersey.isEmpty()) { jerseyInput.error = "Enter jersey (1-999)"; return@setOnClickListener }
            pendingName = name
            pendingJersey = jersey
            dialog.dismiss()
            launchGallery()
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val jersey = jerseyInput.text.toString().trim()
            if (name.isNotEmpty() && jersey.isNotEmpty()) {
                savePlayer(name, jersey, pendingPhotoUri)
                dialog.dismiss()
            } else {
                if (name.isEmpty()) nameInput.error = "Required"
                if (jersey.isEmpty()) jerseyInput.error = "1-999"
            }
        }
        dialog.setOnDismissListener { pendingPhotoUri = null }
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
            Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchGallery() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("PLAYER_${stamp}_", ".jpg", dir)
    }

    private fun savePlayer(name: String, jersey: String, photoUri: String?) {
        commitPlayerToDatabase(name, jersey, photoUri)
    }

    private fun commitPlayerToDatabase(name: String, jersey: String, photoUri: String?) {
        val trimmedName = name.trim()
        AppDatabase.ioExecutor.execute {
            val db = getInstance(this)
            
            // Search for existing player by Name (fuzzy/trimmed/nocase) to prevent duplication
            val existing = db.playerDao().getPlayerByName(trimmedName)
            
            val finalEntity = if (existing != null) {
                existing.apply {
                    this.jerseyNumber = jersey
                    if (!photoUri.isNullOrEmpty()) this.photoUri = photoUri
                }
            } else {
                PlayerEntity(trimmedName, jersey, photoUri)
            }

            db.playerDao().insertPlayer(finalEntity)
            
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    loadLocalPlayers()
                    pendingPhotoUri = null
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_CAMERA && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        }
    }

    private inner class PlayerAdapter(private val players: MutableList<PlayerEntity?>) :
        RecyclerView.Adapter<PlayerAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_player_list, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val p = players[position] ?: return

            holder.name.text = "${p.name} (#${p.jerseyNumber})"
            applyPrestige(p.id, holder.name, holder.photo)

            val df = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            holder.joined.text = "Joined: ${df.format(Date(p.createdAt))}"

            if (!p.photoUri.isNullOrEmpty() && !isFinishing && !isDestroyed) {
                Glide.with(holder.itemView.context)
                    .load(p.photoUri)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(holder.photo)
            } else {
                holder.photo.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            holder.badgeLayout.removeAllViews()
            addBadgeIfRanked(holder.badgeLayout, p.id, RankingRegistry.top3OverallIds, R.drawable.badge_circle_gold)
            addBadgeIfRanked(holder.badgeLayout, p.id, RankingRegistry.top3BattingIds, R.drawable.badge_circle_orange)
            addBadgeIfRanked(holder.badgeLayout, p.id, RankingRegistry.top3BowlingIds, R.drawable.badge_circle_purple)

            holder.itemView.setOnClickListener {
                val intent = Intent(this@PlayerListActivity, PlayerDetailsActivity::class.java)
                intent.putExtra("playerId", p.id)
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this@PlayerListActivity, holder.photo, "player_image_transition")
                startActivity(intent, options.toBundle())
            }
        }

        override fun getItemCount(): Int = players.size

        private fun addBadgeIfRanked(container: LinearLayout, pid: String?, top3: List<String?>, bgRes: Int) {
            val rank = top3.indexOf(pid) + 1
            if (rank > 0) {
                val tv = TextView(container.context)
                val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22f, container.resources.displayMetrics).toInt()
                val lp = LinearLayout.LayoutParams(size, size).apply { setMargins(4, 0, 4, 0) }
                tv.layoutParams = lp
                tv.setBackgroundResource(bgRes)
                tv.setTextColor(-0x1)
                tv.textSize = 11f
                tv.setTypeface(null, Typeface.BOLD)
                tv.gravity = Gravity.CENTER
                tv.text = rank.toString()
                container.addView(tv)
            }
        }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val photo: ShapeableImageView = v.findViewById(R.id.playerPhoto)
            val name: TextView = v.findViewById(R.id.playerName)
            val joined: TextView = v.findViewById(R.id.playerJoined)
            val badgeLayout: LinearLayout = v.findViewById(R.id.layoutRankBadges)
        }
    }

    companion object {
        private const val PERM_CAMERA = 2001
    }
}

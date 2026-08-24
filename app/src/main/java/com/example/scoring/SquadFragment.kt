package com.example.scoring

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.scoring.RankingRegistry.applyPrestige
import com.google.android.material.imageview.ShapeableImageView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SquadFragment : Fragment() {
    private var rvA: RecyclerView? = null
    private var rvB: RecyclerView? = null

    // For mid-match player additions
    private var currentDialogPreview: ImageView? = null
    private val selectedPlayerInfo = arrayOf<String?>(null, null) // [id, photoUri]
    private var currentCameraUri: Uri? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val savedPath = PhotoUtils.savePhoto(requireContext(), uri)
            selectedPlayerInfo[1] = savedPath
            if (savedPath != null) currentDialogPreview?.setImageURI(Uri.parse(savedPath))
        }
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentCameraUri != null) {
            val savedPath = PhotoUtils.savePhoto(requireContext(), currentCameraUri!!)
            selectedPlayerInfo[1] = savedPath
            if (savedPath != null) currentDialogPreview?.setImageURI(Uri.parse(savedPath))
        } else {
            cleanupEmptyCameraFile()
        }
    }

    private fun cleanupEmptyCameraFile() {
        currentCameraUri?.let { uri ->
            try {
                if (uri.scheme == "file") {
                    File(uri.path ?: "").let { if (it.exists() && it.length() == 0L) it.delete() }
                } else {
                    val f = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), uri.lastPathSegment ?: "")
                    if (f.exists() && f.length() == 0L) f.delete()
                }
            } catch (e: Exception) {
                Log.e("SQUAD_FRAGMENT", "Cleanup failed: ${e.message}")
            }
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCameraInternal()
        } else {
            Toast.makeText(context, "Camera permission is required to take player photos", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        } else {
            launchCameraInternal()
        }
    }

    private fun launchCameraInternal() {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (storageDir == null) {
                Toast.makeText(context, "Storage not available", Toast.LENGTH_SHORT).show()
                return
            }
            val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            currentCameraUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            Log.e("SQUAD_FRAGMENT", "Failed to launch camera: ${e.message}")
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchGallery() {
        try {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: Exception) {
            Log.e("SQUAD_FRAGMENT", "Failed to launch gallery: ${e.message}")
            Toast.makeText(context, "Error launching gallery", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_squad, container, false)

        rvA = v.findViewById(R.id.rvSquadA)
        rvB = v.findViewById(R.id.rvSquadB)

        rvA?.layoutManager = LinearLayoutManager(context)
        rvB?.layoutManager = LinearLayoutManager(context)

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            val viewModel = ViewModelProvider(requireActivity())[ScoringViewModel::class.java]
            viewModel.match.observe(viewLifecycleOwner) { 
                updateUI() 
            }
        } catch (e: Exception) {
            Log.d("SQUAD_FRAGMENT", "ViewModel not provided (expected in MatchDetailsActivity)")
        }

        updateUI()
    }

    fun updateUI() {
        val act = activity as? ScoringProvider ?: return
        val v = view ?: return

        val match = act.match
        
        // Use names from match object or fallback to intent names
        val displayA = match?.teamA ?: "Team A"
        val displayB = match?.teamB ?: "Team B"

        v.findViewById<TextView>(R.id.tvSquadTitleA).text = displayA
        v.findViewById<TextView>(R.id.tvSquadTitleB).text = displayB

        // Ensure we are passing the latest lists from the activity
        val namesA = act.teamANames ?: ArrayList<String?>()
        val namesB = act.teamBNames ?: ArrayList<String?>()

        rvA?.adapter = SimpleSquadAdapter(
            namesA,
            act.photoMap,
            act.nameToIdMap,
            displayA,
            act,
            this
        )
        rvB?.adapter = SimpleSquadAdapter(
            namesB,
            act.photoMap,
            act.nameToIdMap,
            displayB,
            act,
            this
        )
        
        // Force layout refresh
        rvA?.adapter?.notifyDataSetChanged()
        rvB?.adapter?.notifyDataSetChanged()
    }

    internal class SimpleSquadAdapter(
        private val names: MutableList<String?>,
        private val photos: MutableMap<String?, String?>?,
        private val ids: MutableMap<String?, String?>?,
        private val teamName: String?,
        private val provider: ScoringProvider,
        private val fragment: SquadFragment
    ) : RecyclerView.Adapter<SimpleSquadAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            if (viewType == 1) {
                val v = LayoutInflater.from(parent.context)
                    .inflate(android.R.layout.simple_list_item_1, parent, false)
                val tv = v.findViewById<TextView>(android.R.id.text1)
                tv.text = "+ Add Player to $teamName"
                tv.setTextColor(-0xff96a4)
                tv.gravity = Gravity.CENTER
                return Holder(v, true)
            }
            return Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_player_card, parent, false), false
            )
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == names.size) 1 else 0
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            if (getItemViewType(position) == 1) {
                holder.itemView.setOnClickListener { v: View ->
                    showAddPlayerDialog(v.context)
                }
                return
            }

            val name = names[position] ?: "Unknown"
            holder.name?.text = name

            var idVal: String? = null
            ids?.let {
                for (entry in it.entries) {
                    if (entry.key?.trim().equals(name.trim(), ignoreCase = true)) {
                        idVal = entry.value
                        break
                    }
                }
            }

            applyPrestige(idVal, holder.name, holder.photo)

            val finalId = idVal
            val finalName = name
            AppDatabase.ioExecutor.execute {
                val context = holder.itemView.context
                val db = AppDatabase.getInstance(context)
                val gId = GullySyncManager.getCurrentGullyId(context) ?: "local"
                var actualPhotoUri: String? = null

                if (finalId != null) {
                    val pe = db.playerDao().getPlayerById(finalId)
                    if (pe != null) actualPhotoUri = pe.photoUri
                }

                if (actualPhotoUri == null) {
                    val pe = db.playerDao().getPlayerByNameByGully(finalName.trim(), gId)
                    if (pe != null) actualPhotoUri = pe.photoUri
                }

                val uriToLoad = actualPhotoUri
                holder.itemView.post {
                    val photoView = holder.photo
                    if (!uriToLoad.isNullOrEmpty() && photoView != null) {
                        try {
                            Glide.with(context)
                                .load(uriToLoad)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_menu_gallery)
                                .into(photoView)
                        } catch (e: Exception) {
                            photoView.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    } else {
                        photoView?.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            }

            holder.btnRemove?.visibility = View.GONE
        }

        private fun showAddPlayerDialog(context: Context) {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_player, null)
            val nameInput = view.findViewById<EditText>(R.id.playerNameInput)
            val jerseyInput = view.findViewById<EditText>(R.id.playerJerseyInput)
            val quickAdd = view.findViewById<AutoCompleteTextView>(R.id.quickAddInput)
            val preview = view.findViewById<ImageView>(R.id.ivDialogPreview)
            
            fragment.currentDialogPreview = preview
            fragment.selectedPlayerInfo[0] = null
            fragment.selectedPlayerInfo[1] = null

            val btnCamera = view.findViewById<View>(R.id.btnTakePhoto)
            val btnGallery = view.findViewById<View>(R.id.btnPickGallery)
            btnCamera.visibility = View.VISIBLE
            btnGallery.visibility = View.VISIBLE

            btnCamera.setOnClickListener {
                fragment.launchCamera()
            }

            btnGallery.setOnClickListener {
                fragment.launchGallery()
            }

            AppDatabase.ioExecutor.execute {
                val db = AppDatabase.getInstance(context)
                val gId = GullySyncManager.getCurrentGullyId(context) ?: "local"
                val allExisting = db.playerDao().getAllPlayersByGully(gId)?.filterNotNull() ?: emptyList()

                fragment.activity?.runOnUiThread {
                    if (!fragment.isAdded || fragment.isDetached) return@runOnUiThread
                    val displayList = allExisting.map { player ->
                        "#${player.jerseyNumber} ${player.name}"
                    }
                    val qAdapter = object : ArrayAdapter<String>(
                        context,
                        android.R.layout.simple_dropdown_item_1line, displayList
                    ) {
                        override fun getFilter(): Filter {
                            return object : Filter() {
                                override fun performFiltering(constraint: CharSequence?): FilterResults {
                                    val results = FilterResults()
                                    if (constraint.isNullOrEmpty()) {
                                        results.values = emptyList<String>()
                                        results.count = 0
                                    } else {
                                        val search = constraint.toString().lowercase(Locale.getDefault())
                                        val filtered = allExisting.filter { p ->
                                            val n = p.name.lowercase(Locale.getDefault())
                                            val j = p.jerseyNumber
                                            n.contains(search) || j.contains(search)
                                        }.map { p ->
                                            "#${p.jerseyNumber} ${p.name}"
                                        }
                                        results.values = filtered
                                        results.count = filtered.size
                                    }
                                    return results
                                }

                                override fun publishResults(
                                    constraint: CharSequence?,
                                    results: FilterResults?
                                ) {
                                    clear()
                                    (results?.values as? List<String>)?.let { addAll(it) }
                                    notifyDataSetChanged()
                                }
                            }
                        }
                    }
                    quickAdd.setAdapter(qAdapter)
                    quickAdd.onItemClickListener = AdapterView.OnItemClickListener { parent, view1, position, id_ptr ->
                        val selected = parent.getItemAtPosition(position) as? String
                        for (p in allExisting) {
                            if (p == null) continue
                            val matchStr = "#${p.jerseyNumber} ${p.name}"
                            if (matchStr == selected) {
                                nameInput.setText(p.name)
                                jerseyInput.setText(p.jerseyNumber)
                                fragment.selectedPlayerInfo[0] = p.id
                                fragment.selectedPlayerInfo[1] = p.photoUri
                                if (!p.photoUri.isNullOrEmpty()) {
                                    try {
                                        preview.setImageURI(Uri.parse(p.photoUri))
                                    } catch (e: Exception) {
                                        Log.e("SQUAD_FRAGMENT", "Failed to load photo: ${e.message}")
                                    }
                                }
                                quickAdd.setText("")
                                break
                            }
                        }
                    }
                }
            }

            val dialog = ThemeManager.createDynamicBuilder(context)
                .setTitle("Add Player to $teamName")
                .setView(view)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create()
            
            dialog.show()
            ThemeManager.colorizeDialog(dialog)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val jersey = jerseyInput.text.toString().trim()

                if (name.isEmpty() || jersey.isEmpty()) {
                    if (name.isEmpty()) nameInput.error = "Name required"
                    if (jersey.isEmpty()) jerseyInput.error = "Jersey required"
                    return@setOnClickListener
                }

                // CHECK FOR DUPLICATE IN GULLY
                AppDatabase.ioExecutor.execute {
                    val db = AppDatabase.getInstance(context)
                    val gId = GullySyncManager.getCurrentGullyId(context) ?: "local"
                    val existing = db.playerDao().getPlayerByNameByGully(name, gId)

                    fragment.activity?.runOnUiThread {
                        if (existing != null && existing.jerseyNumber != jersey) {
                            // WARN USER
                            ThemeManager.createDynamicBuilder(context)
                                .setTitle("Duplicate Name Found")
                                .setMessage("${existing.name} - ${existing.jerseyNumber} already exists in this Gully. Still want to add as a new player?")
                                .setPositiveButton("ADD ANYWAY") { _, _ ->
                                    provider.addPlayerToTeam(teamName, name, fragment.selectedPlayerInfo[0], fragment.selectedPlayerInfo[1], jersey)
                                    notifyItemInserted(names.size - 1)
                                    dialog.dismiss()
                                }
                                .setNegativeButton("CANCEL", null)
                                .show()
                        } else {
                            // Proceed normally
                            provider.addPlayerToTeam(teamName, name, fragment.selectedPlayerInfo[0], fragment.selectedPlayerInfo[1], jersey)
                            notifyItemInserted(names.size - 1)
                            dialog.dismiss()
                        }
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return names.size + (if (provider.isScorer) 1 else 0)
        }

        internal class Holder(v: View, isButton: Boolean) : RecyclerView.ViewHolder(v) {
            var photo: ShapeableImageView? = null
            var name: TextView? = null
            var btnRemove: View? = null

            init {
                if (!isButton) {
                    photo = v.findViewById(R.id.playerPhoto)
                    name = v.findViewById(R.id.playerName)
                    btnRemove = v.findViewById(R.id.btnRemove)
                }
            }
        }
    }
}

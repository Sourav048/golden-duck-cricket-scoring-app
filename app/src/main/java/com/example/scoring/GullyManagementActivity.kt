package com.example.scoring

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

class GullyManagementActivity : BaseActivity() {

    private lateinit var tvGullyName: TextView
    private lateinit var btnLeave: MaterialButton
    private lateinit var btnMigrate: MaterialButton
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var rvRecent: RecyclerView

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("GullyManagement", "Notification permission granted")
        } else {
            Toast.makeText(this, "Notifications are disabled. You won't get match updates.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gully_management)

        // Pre-warm Firestore to speed up joining
        com.google.firebase.firestore.FirebaseFirestore.getInstance()

        tvGullyName = findViewById(R.id.tvGullyName)
        btnLeave = findViewById(R.id.btnLeaveGully)
        btnMigrate = findViewById(R.id.btnMigrateLocal)
        viewPager = findViewById(R.id.viewPagerGully)
        tabLayout = findViewById(R.id.tabLayoutGully)
        rvRecent = findViewById(R.id.rvRecentGullies)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        setupViewPagerAndTabs()
        setupRecentList()
        updateCurrentGullyUI()
        checkNotificationPermission()

        btnLeave.setOnClickListener { leaveGully() }
        btnMigrate.setOnClickListener { performMigration() }
    }

    private fun setupViewPagerAndTabs() {
        viewPager.adapter = GullyPagerAdapter()
        
        com.google.android.material.tabs.TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "Join" else "Create"
        }.attach()
    }

    private fun setupRecentList() {
        val gullies = GullyHistoryManager.getGullies(this)
        findViewById<View>(R.id.tvRecentTitle).visibility = if (gullies.isEmpty()) View.GONE else View.VISIBLE
        
        rvRecent.layoutManager = LinearLayoutManager(this)
        rvRecent.adapter = RecentGullyAdapter(gullies)
    }

    private fun updateCurrentGullyUI() {
        val currentGully = GullySyncManager.getCurrentGullyId(this)

        if (currentGully != null) {
            tvGullyName.text = currentGully
            btnLeave.visibility = View.VISIBLE
            
            // Check for migration eligibility
            GullyMigrationManager.hasLocalData(this) { hasData ->
                runOnUiThread {
                    btnMigrate.visibility = if (hasData) View.VISIBLE else View.GONE
                }
            }
        } else {
            tvGullyName.text = "Local Mode (Offline)"
            btnLeave.visibility = View.GONE
            btnMigrate.visibility = View.GONE
        }
        rvRecent.adapter?.notifyDataSetChanged()
    }

    private fun performMigration() {
        val currentGId = GullySyncManager.getCurrentGullyId(this) ?: return
        
        ThemeManager.createDynamicBuilder(this)
            .setTitle("Sync Local Records")
            .setMessage("All your previous local matches and players will be moved to '$currentGId' and uploaded to the cloud. Continue?")
            .setPositiveButton("SYNC NOW") { _, _ ->
                btnMigrate.isEnabled = false
                btnMigrate.text = "Migrating..."
                
                GullyMigrationManager.migrateLocalToGully(this, currentGId, object : GullyMigrationManager.MigrationCallback {
                    override fun onSuccess(count: Int) {
                        runOnUiThread {
                            Toast.makeText(this@GullyManagementActivity, "Successfully migrated $count records!", Toast.LENGTH_LONG).show()
                            updateCurrentGullyUI()
                            btnMigrate.text = "Sync Local History to Cloud"
                            btnMigrate.isEnabled = true
                        }
                    }
                    override fun onFailure(error: String) {
                        runOnUiThread {
                            Toast.makeText(this@GullyManagementActivity, "Migration failed: $error", Toast.LENGTH_LONG).show()
                            btnMigrate.text = "Sync Local History to Cloud"
                            btnMigrate.isEnabled = true
                        }
                    }
                })
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }



    private fun saveGullyPreference(id: String) {
        getSharedPreferences("gully_prefs", MODE_PRIVATE)
            .edit()
            .putString("current_gully_id", id)
            .apply()
    }

    private fun leaveGully() {
        GullySyncManager.stopSync()
        getSharedPreferences("gully_prefs", MODE_PRIVATE)
            .edit()
            .remove("current_gully_id")
            .apply()
        updateCurrentGullyUI()
        Toast.makeText(this, "Switched to Local Mode", Toast.LENGTH_SHORT).show()
    }

    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private inner class RecentGullyAdapter(private val list: List<GullyHistoryManager.GullyRecord>) :
        RecyclerView.Adapter<RecentGullyAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_recent_gully, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = list[position]
            holder.name.text = item.id
            
            val activeGully = GullySyncManager.getCurrentGullyId(this@GullyManagementActivity)
            val isActive = item.id.equals(activeGully, ignoreCase = true)
            
            holder.status.text = if (isActive) "Currently active" else "Tap to switch"
            holder.status.setTextColor(if (isActive) ThemeManager.getSeedColor(this@GullyManagementActivity) else 0x8A000000.toInt())
            holder.check.visibility = if (isActive) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                if (isActive) return@setOnClickListener
                switchGully(item.id, item.passcode)
            }

            holder.itemView.setOnLongClickListener {
                showRemoveDialog(item.id)
                true
            }
        }

        override fun getItemCount(): Int = list.size

        private fun switchGully(id: String, pass: String) {
            Toast.makeText(this@GullyManagementActivity, "Switching to $id...", Toast.LENGTH_SHORT).show()
            GullySyncManager.joinGully(id, pass, object : GullySyncManager.SyncCallback {
                override fun onSuccess(message: String) {
                    saveGullyPreference(id)
                    GullySyncManager.startSync(this@GullyManagementActivity, id)
                    updateCurrentGullyUI()
                }
                override fun onFailure(error: String) {
                    Toast.makeText(this@GullyManagementActivity, "Auth failed: $error", Toast.LENGTH_LONG).show()
                }
            })
        }

        private fun showRemoveDialog(id: String) {
            showDynamicDialog {
                setTitle("Remove from Switchboard?")
                setMessage("Remove '$id' from your recent list? This won't delete the league data from the cloud.")
                setPositiveButton("REMOVE") { _, _ ->
                    LeagueNotificationManager.unsubscribeFromLeague(id)
                    GullyHistoryManager.removeGully(this@GullyManagementActivity, id)
                    setupRecentList()
                }
                setNegativeButton("CANCEL", null)
            }
        }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvRecentGullyName)
            val status: TextView = v.findViewById(R.id.tvRecentStatus)
            val check: ImageView = v.findViewById(R.id.ivActiveCheck)
        }
    }

    private inner class GullyPagerAdapter : RecyclerView.Adapter<GullyPagerAdapter.PageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = if (viewType == 0) {
                inflater.inflate(R.layout.item_gully_join, parent, false)
            } else {
                inflater.inflate(R.layout.item_gully_create, parent, false)
            }
            return PageHolder(view)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            if (position == 0) {
                val btnJoin = holder.itemView.findViewById<MaterialButton>(R.id.btnJoin)
                btnJoin.setOnClickListener { performJoin(holder.itemView) }
            } else {
                val btnCreate = holder.itemView.findViewById<MaterialButton>(R.id.btnCreate)
                btnCreate.setOnClickListener { performCreate(holder.itemView) }
            }
        }

        override fun getItemCount(): Int = 2

        override fun getItemViewType(position: Int): Int = position

        inner class PageHolder(v: View) : RecyclerView.ViewHolder(v)
    }

    private fun performJoin(view: View) {
        val etId = view.findViewById<EditText>(R.id.etJoinId)
        val etPass = view.findViewById<EditText>(R.id.etJoinPass)
        val btnJoin = view.findViewById<MaterialButton>(R.id.btnJoin)

        val id = etId.text.toString().trim().uppercase()
        val pass = etPass.text.toString().trim()

        if (id.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter ID and Passcode", Toast.LENGTH_SHORT).show()
            return
        }

        // Loading State: Disable inputs and show progress
        btnJoin.isEnabled = false
        btnJoin.text = "Joining..."
        etId.isEnabled = false
        etPass.isEnabled = false

        GullySyncManager.joinGully(id, pass, object : GullySyncManager.SyncCallback {
            override fun onSuccess(message: String) {
                GullyHistoryManager.addGully(this@GullyManagementActivity, id, pass)
                saveGullyPreference(id)
                GullySyncManager.startSync(this@GullyManagementActivity, id)
                Toast.makeText(this@GullyManagementActivity, message, Toast.LENGTH_SHORT).show()
                finish()
            }
            override fun onFailure(error: String) {
                runOnUiThread {
                    btnJoin.isEnabled = true
                    btnJoin.text = "Join Gully"
                    etId.isEnabled = true
                    etPass.isEnabled = true
                    Toast.makeText(this@GullyManagementActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun performCreate(view: View) {
        val etId = view.findViewById<EditText>(R.id.etCreateId)
        val etPass = view.findViewById<EditText>(R.id.etCreatePass)
        val btnCreate = view.findViewById<MaterialButton>(R.id.btnCreate)

        val id = etId.text.toString().trim().uppercase()
        val pass = etPass.text.toString().trim()

        if (id.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter a Name and Passcode", Toast.LENGTH_SHORT).show()
            return
        }

        // Loading State: Disable inputs and show progress
        btnCreate.isEnabled = false
        btnCreate.text = "Creating..."
        etId.isEnabled = false
        etPass.isEnabled = false

        GullySyncManager.createGully(id, pass, object : GullySyncManager.SyncCallback {
            override fun onSuccess(message: String) {
                GullyHistoryManager.addGully(this@GullyManagementActivity, id, pass)
                saveGullyPreference(id)
                GullySyncManager.startSync(this@GullyManagementActivity, id)
                Toast.makeText(this@GullyManagementActivity, message, Toast.LENGTH_SHORT).show()
                finish()
            }
            override fun onFailure(error: String) {
                runOnUiThread {
                    btnCreate.isEnabled = true
                    btnCreate.text = "Create League"
                    etId.isEnabled = true
                    etPass.isEnabled = true
                    Toast.makeText(this@GullyManagementActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        })
    }
}

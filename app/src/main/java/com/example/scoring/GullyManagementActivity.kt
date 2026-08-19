package com.example.scoring

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

class GullyManagementActivity : BaseActivity() {

    private lateinit var tvGullyName: TextView
    private lateinit var btnLeave: MaterialButton
    private lateinit var layoutJoin: View
    private lateinit var layoutCreate: View
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gully_management)

        tvGullyName = findViewById(R.id.tvGullyName)
        btnLeave = findViewById(R.id.btnLeaveGully)
        layoutJoin = findViewById(R.id.layoutJoin)
        layoutCreate = findViewById(R.id.layoutCreate)
        tabLayout = findViewById(R.id.tabLayoutGully)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        setupTabs()
        updateCurrentGullyUI()

        findViewById<MaterialButton>(R.id.btnJoin).setOnClickListener { performJoin() }
        findViewById<MaterialButton>(R.id.btnCreate).setOnClickListener { performCreate() }
        btnLeave.setOnClickListener { leaveGully() }
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    layoutJoin.visibility = View.VISIBLE
                    layoutCreate.visibility = View.GONE
                } else {
                    layoutJoin.visibility = View.GONE
                    layoutCreate.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateCurrentGullyUI() {
        val prefs = getSharedPreferences("gully_prefs", MODE_PRIVATE)
        val currentGully = prefs.getString("current_gully_id", null)

        if (currentGully != null) {
            tvGullyName.text = currentGully
            btnLeave.visibility = View.VISIBLE
        } else {
            tvGullyName.text = "Local Mode (Offline)"
            btnLeave.visibility = View.GONE
        }
    }

    private fun performJoin() {
        val id = findViewById<EditText>(R.id.etJoinId).text.toString().trim()
        val pass = findViewById<EditText>(R.id.etJoinPass).text.toString().trim()

        if (id.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter ID and Passcode", Toast.LENGTH_SHORT).show()
            return
        }

        // TODO: In Phase 3, this will check Firestore. For Phase 2, we just save locally.
        saveGullyPreference(id)
        Toast.makeText(this, "Joined Gully: $id", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun performCreate() {
        val id = findViewById<EditText>(R.id.etCreateId).text.toString().trim()
        val pass = findViewById<EditText>(R.id.etCreatePass).text.toString().trim()

        if (id.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter a Name and Passcode", Toast.LENGTH_SHORT).show()
            return
        }

        // TODO: In Phase 3, this will register in Firestore.
        saveGullyPreference(id)
        Toast.makeText(this, "Gully Created: $id", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun saveGullyPreference(id: String) {
        getSharedPreferences("gully_prefs", MODE_PRIVATE)
            .edit()
            .putString("current_gully_id", id)
            .apply()
    }

    private fun leaveGully() {
        getSharedPreferences("gully_prefs", MODE_PRIVATE)
            .edit()
            .remove("current_gully_id")
            .apply()
        updateCurrentGullyUI()
        Toast.makeText(this, "Switched to Local Mode", Toast.LENGTH_SHORT).show()
    }
}

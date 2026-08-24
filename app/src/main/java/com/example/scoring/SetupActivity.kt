package com.example.scoring

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch

class SetupActivity : BaseActivity() {
    private var venueInput: AutoCompleteTextView? = null
    private var teamAInput: EditText? = null
    private var teamBInput: EditText? = null
    private var oversInput: EditText? = null
    private var ballTypeInput: AutoCompleteTextView? = null
    private var switchRunsOnWide: MaterialSwitch? = null
    private var switchFreeHit: MaterialSwitch? = null
    private var switchRunsOnBye: MaterialSwitch? = null
    private var switchOverthrow: MaterialSwitch? = null
    private var switchEveryPlayerBats: MaterialSwitch? = null
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_setup)

            venueInput = findViewById(R.id.venueInput)
            teamAInput = findViewById(R.id.teamAInput)
            teamBInput = findViewById(R.id.teamBInput)
            oversInput = findViewById(R.id.oversInput)
            ballTypeInput = findViewById(R.id.ballTypeSpinner)
            switchRunsOnWide = findViewById(R.id.switchRunsOnWide)
            switchFreeHit = findViewById(R.id.switchFreeHit)
            switchRunsOnBye = findViewById(R.id.switchRunsOnBye)
            switchOverthrow = findViewById(R.id.switchOverthrow)
            switchEveryPlayerBats = findViewById(R.id.switchEveryPlayerBats)

            val ballTypes = arrayOf(
                getString(R.string.ball_type_stumper),
                getString(R.string.ball_type_red_tennis),
                getString(R.string.ball_type_wind_ball),
                getString(R.string.ball_type_green_tennis),
                getString(R.string.ball_type_leather),
                getString(R.string.ball_type_plastic),
                getString(R.string.ball_type_rubber),
                getString(R.string.select_ball_type)
            )

            val ballTypesList = ballTypes.take(ballTypes.size - 1)
            val adapter = ArrayAdapter<String>(this, R.layout.list_item_dropdown, ballTypesList)
            ballTypeInput?.setAdapter(adapter)
            // Removed auto-selection of first item to force user selection

            ballTypeInput?.setOnClickListener {
                ballTypeInput?.showDropDown()
            }

            // Setup Venue Suggestions
            AppDatabase.ioExecutor.execute {
                val venues = AppDatabase.getInstance(this).matchDao().getUniqueVenues() ?: emptyList()
                runOnUiThread {
                    val venueAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, venues)
                    venueInput?.setAdapter(venueAdapter)
                    
                    // Show dropdown when focused/clicked if there are items
                    venueInput?.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus && (venueInput?.text?.isEmpty() == true)) {
                            venueInput?.showDropDown()
                        }
                    }
                    venueInput?.setOnClickListener {
                        if (venueInput?.text?.isEmpty() == true) {
                            venueInput?.showDropDown()
                        }
                    }
                }
            }

            ballTypeInput?.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    hideKeyboard()
                }
                false
            }

            oversInput?.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                    hideKeyboard()
                    true
                } else false
            }

            if (intent.getBooleanExtra("cloneMatch", false)) {
                venueInput?.setText(intent.getStringExtra("venue"))
                teamAInput?.setText(intent.getStringExtra("teamAName"))
                teamBInput?.setText(intent.getStringExtra("teamBName"))
                oversInput?.setText(intent.getIntExtra("overs", 10).toString())

                switchRunsOnWide?.isChecked = intent.getBooleanExtra("ruleRunsOnWide", true)
                switchFreeHit?.isChecked = intent.getBooleanExtra("ruleFreeHit", true)
                switchRunsOnBye?.isChecked = intent.getBooleanExtra("ruleRunsOnBye", true)
                switchOverthrow?.isChecked = intent.getBooleanExtra("ruleOverthrow", true)

                intent.getStringExtra("ballType")?.let { bType ->
                    ballTypeInput?.setText(bType, false)
                }
            }

            findViewById<View>(R.id.btnProceedTop).setOnClickListener { onNext() }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Setup initialization error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun onNext() {
        hideKeyboard()
        val venue = venueInput?.text?.toString()?.trim() ?: ""
        var teamA = teamAInput?.text?.toString()?.trim() ?: ""
        var teamB = teamBInput?.text?.toString()?.trim() ?: ""

        if (teamA.isNotEmpty() && teamB.isEmpty()) {
            teamBInput?.error = "If Team A has a name, Team B must also have one"
            return
        }
        if (teamA.isEmpty() && teamB.isNotEmpty()) {
            teamAInput?.error = "If Team B has a name, Team A must also have one"
            return
        }

        if (teamA.isEmpty() && teamB.isEmpty()) {
            teamA = "Red Team"
            teamB = "Blue Team"
        }

        val oversStr = oversInput?.text?.toString()?.trim() ?: ""
        if (oversStr.isEmpty()) {
            oversInput?.error = "Number of Overs is required"
            return
        }
        val overs = oversStr.toIntOrNull()
        if (overs == null || overs <= 0) {
            oversInput?.error = "Enter a valid number greater than 0"
            return
        }

        var ballType = ballTypeInput?.text?.toString()?.trim() ?: ""
        if (ballType.isEmpty() || ballType == getString(R.string.select_ball_type)) {
            ballType = "Not Specified"
        }

        val intentNext = Intent(this, PlayerEntryActivity::class.java).apply {
            putExtra("venue", venue)
            putExtra("teamA", teamA)
            putExtra("teamB", teamB)
            putExtra("overs", overs)
            putExtra("ballType", ballType)
            putExtra("ruleRunsOnWide", switchRunsOnWide?.isChecked ?: true)
            putExtra("ruleFreeHit", switchFreeHit?.isChecked ?: true)
            putExtra("ruleRunsOnBye", switchRunsOnBye?.isChecked ?: true)
            putExtra("ruleOverthrow", switchOverthrow?.isChecked ?: true)
            putExtra("ruleEveryPlayerBats", switchEveryPlayerBats?.isChecked ?: true)

            if (intent.getBooleanExtra("cloneMatch", false)) {
                putExtra("cloneMatch", true)
                putStringArrayListExtra("teamANames", intent.getStringArrayListExtra("teamANames"))
                putStringArrayListExtra("teamBNames", intent.getStringArrayListExtra("teamBNames"))
                putStringArrayListExtra("teamAPhotos", intent.getStringArrayListExtra("teamAPhotos"))
                putStringArrayListExtra("teamBPhotos", intent.getStringArrayListExtra("teamBPhotos"))
                putStringArrayListExtra("teamAJerseys", intent.getStringArrayListExtra("teamAJerseys"))
                putStringArrayListExtra("teamBJerseys", intent.getStringArrayListExtra("teamBJerseys"))
                putStringArrayListExtra("teamAIds", intent.getStringArrayListExtra("teamAIds"))
                putStringArrayListExtra("teamBIds", intent.getStringArrayListExtra("teamBIds"))
            }
        }

        startActivity(intentNext)
        finish()
    }
}

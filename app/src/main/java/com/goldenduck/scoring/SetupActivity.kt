package com.goldenduck.scoring

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout

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

    private var savedTeamANames: ArrayList<String>? = null
    private var savedTeamAPhotos: ArrayList<String>? = null
    private var savedTeamAJerseys: ArrayList<String>? = null
    private var savedTeamAIds: ArrayList<String>? = null

    private var savedTeamBNames: ArrayList<String>? = null
    private var savedTeamBPhotos: ArrayList<String>? = null
    private var savedTeamBJerseys: ArrayList<String>? = null
    private var savedTeamBIds: ArrayList<String>? = null

    private var savedDraftId: String? = null

    private val playerEntryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            data.getStringExtra("draftId")?.let { savedDraftId = it }
            data.getStringArrayListExtra("teamANames")?.let { savedTeamANames = it }
            data.getStringArrayListExtra("teamAPhotos")?.let { savedTeamAPhotos = it }
            data.getStringArrayListExtra("teamAJerseys")?.let { savedTeamAJerseys = it }
            data.getStringArrayListExtra("teamAIds")?.let { savedTeamAIds = it }

            data.getStringArrayListExtra("teamBNames")?.let { savedTeamBNames = it }
            data.getStringArrayListExtra("teamBPhotos")?.let { savedTeamBPhotos = it }
            data.getStringArrayListExtra("teamBJerseys")?.let { savedTeamBJerseys = it }
            data.getStringArrayListExtra("teamBIds")?.let { savedTeamBIds = it }
        }
    }
    
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

            val ballTypeInputLayout = findViewById<TextInputLayout>(R.id.ballTypeInputLayout)
            ballTypeInputLayout?.setEndIconOnClickListener {
                showBallTypeSelectionDialog()
            }

            ballTypeInput?.setOnClickListener {
                showBallTypeSelectionDialog()
            }

            ballTypeInput?.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    showBallTypeSelectionDialog()
                }
            }

            ballTypeInput?.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                    showBallTypeSelectionDialog()
                }
                true
            }

            // Setup Venue Suggestions
            AppDatabase.ioExecutor.execute {
                val gId = GullySyncManager.getCurrentGullyId(this) ?: "local"
                val venues = AppDatabase.getInstance(this).matchDao().getUniqueVenues(gId) ?: emptyList()
                runOnUiThread {
                    val venueAdapter = ArrayAdapter(this, R.layout.list_item_dropdown, venues)
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

            oversInput?.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                    hideKeyboard()
                    true
                } else false
            }

            if (savedInstanceState != null) {
                savedDraftId = savedInstanceState.getString("savedDraftId")
                savedTeamANames = savedInstanceState.getStringArrayList("savedTeamANames")
                savedTeamAPhotos = savedInstanceState.getStringArrayList("savedTeamAPhotos")
                savedTeamAJerseys = savedInstanceState.getStringArrayList("savedTeamAJerseys")
                savedTeamAIds = savedInstanceState.getStringArrayList("savedTeamAIds")

                savedTeamBNames = savedInstanceState.getStringArrayList("savedTeamBNames")
                savedTeamBPhotos = savedInstanceState.getStringArrayList("savedTeamBPhotos")
                savedTeamBJerseys = savedInstanceState.getStringArrayList("savedTeamBJerseys")
                savedTeamBIds = savedInstanceState.getStringArrayList("savedTeamBIds")
            }

            val isClone = intent.getBooleanExtra("cloneMatch", false) || intent.hasExtra("teamANames")
            if (isClone) {
                venueInput?.setText(intent.getStringExtra("venue"))
                val nameA = intent.getStringExtra("teamA") ?: intent.getStringExtra("teamAName")
                val nameB = intent.getStringExtra("teamB") ?: intent.getStringExtra("teamBName")
                teamAInput?.setText(nameA)
                teamBInput?.setText(nameB)
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("savedDraftId", savedDraftId)
        outState.putStringArrayList("savedTeamANames", savedTeamANames)
        outState.putStringArrayList("savedTeamAPhotos", savedTeamAPhotos)
        outState.putStringArrayList("savedTeamAJerseys", savedTeamAJerseys)
        outState.putStringArrayList("savedTeamAIds", savedTeamAIds)

        outState.putStringArrayList("savedTeamBNames", savedTeamBNames)
        outState.putStringArrayList("savedTeamBPhotos", savedTeamBPhotos)
        outState.putStringArrayList("savedTeamBJerseys", savedTeamBJerseys)
        outState.putStringArrayList("savedTeamBIds", savedTeamBIds)
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
        if (overs == null || overs <= 0 || overs > 100) {
            oversInput?.error = "Enter a valid number between 1 and 100"
            return
        }

        var ballType = ballTypeInput?.text?.toString()?.trim() ?: ""
        if (ballType.isEmpty() || ballType == getString(R.string.select_ball_type)) {
            ballType = "Not Specified"
        }

        val intentNext = Intent(this, PlayerEntryActivity::class.java).apply {
            savedDraftId?.let { putExtra("draftId", it) }
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

            val aNames = savedTeamANames ?: intent.getStringArrayListExtra("teamANames")
            val aPhotos = savedTeamAPhotos ?: intent.getStringArrayListExtra("teamAPhotos")
            val aJerseys = savedTeamAJerseys ?: intent.getStringArrayListExtra("teamAJerseys")
            val aIds = savedTeamAIds ?: intent.getStringArrayListExtra("teamAIds")

            val bNames = savedTeamBNames ?: intent.getStringArrayListExtra("teamBNames")
            val bPhotos = savedTeamBPhotos ?: intent.getStringArrayListExtra("teamBPhotos")
            val bJerseys = savedTeamBJerseys ?: intent.getStringArrayListExtra("teamBJerseys")
            val bIds = savedTeamBIds ?: intent.getStringArrayListExtra("teamBIds")

            if (!aNames.isNullOrEmpty()) {
                putStringArrayListExtra("teamANames", aNames)
                putStringArrayListExtra("teamAPhotos", aPhotos)
                putStringArrayListExtra("teamAJerseys", aJerseys)
                putStringArrayListExtra("teamAIds", aIds)
            }

            if (!bNames.isNullOrEmpty()) {
                putStringArrayListExtra("teamBNames", bNames)
                putStringArrayListExtra("teamBPhotos", bPhotos)
                putStringArrayListExtra("teamBJerseys", bJerseys)
                putStringArrayListExtra("teamBIds", bIds)
            }

            if (intent.getBooleanExtra("cloneMatch", false)) {
                putExtra("cloneMatch", true)
            }
        }

        playerEntryLauncher.launch(intentNext)
    }

    private data class BallTypeOption(
        val name: String,
        val description: String,
        val badgeResId: Int
    )

    private fun showBallTypeSelectionDialog() {
        hideKeyboard()
        val options = listOf(
            BallTypeOption(
                getString(R.string.ball_type_stumper),
                "Heavy rubber stumper ball for outdoor play",
                R.drawable.circle_bg_orange
            ),
            BallTypeOption(
                getString(R.string.ball_type_red_tennis),
                "Standard red felt tennis ball for quick matches",
                R.drawable.circle_bg_red
            ),
            BallTypeOption(
                getString(R.string.ball_type_wind_ball),
                "Seamed synthetic wind ball for all weather",
                R.drawable.circle_bg_blue
            ),
            BallTypeOption(
                getString(R.string.ball_type_green_tennis),
                "Heavy green tennis ball with extra bounce",
                R.drawable.circle_bg_green
            ),
            BallTypeOption(
                getString(R.string.ball_type_leather),
                "Seasoned 156g leather ball for official games",
                R.drawable.circle_bg_purple
            ),
            BallTypeOption(
                getString(R.string.ball_type_plastic),
                "Hard plastic molded ball for practice",
                R.drawable.circle_bg_grey
            ),
            BallTypeOption(
                getString(R.string.ball_type_rubber),
                "Solid rubber bounce ball",
                R.drawable.circle_bg_yellow
            )
        )

        val dialogView = layoutInflater.inflate(R.layout.dialog_select_ball_type, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.ballTypeOptionsContainer)
        val currentSelected = ballTypeInput?.text?.toString()?.trim() ?: ""

        val primaryColor = ThemeManager.getThemeColor(this, com.google.android.material.R.attr.colorPrimary)
        val primaryContainerColor = ThemeManager.getThemeColor(this, com.google.android.material.R.attr.colorPrimaryContainer)
        val outlineColor = ThemeManager.getThemeColor(this, com.google.android.material.R.attr.colorOutline)

        var dialogRef: androidx.appcompat.app.AlertDialog? = null

        options.forEach { opt ->
            val itemView = layoutInflater.inflate(R.layout.item_ball_type_option, container, false)
            val card = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBallType)
            val tvName = itemView.findViewById<TextView>(R.id.tvBallTypeName)
            val tvDesc = itemView.findViewById<TextView>(R.id.tvBallTypeDesc)
            val ivBadgeBg = itemView.findViewById<ImageView>(R.id.ivBallBadgeBg)
            val ivBallIcon = itemView.findViewById<ImageView>(R.id.ivBallIcon)
            val ivSelected = itemView.findViewById<ImageView>(R.id.ivBallSelected)

            tvName.text = opt.name
            tvDesc.text = opt.description
            ivBadgeBg.setImageResource(opt.badgeResId)
            ivBallIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)

            val isSelected = opt.name.equals(currentSelected, ignoreCase = true)
            if (isSelected) {
                card.strokeColor = primaryColor
                card.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics).toInt()
                card.setCardBackgroundColor(primaryContainerColor)
                ivSelected.visibility = View.VISIBLE
            } else {
                card.strokeColor = outlineColor
                card.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.2f, resources.displayMetrics).toInt()
                ivSelected.visibility = View.GONE
            }

            card.setOnClickListener {
                ballTypeInput?.setText(opt.name, false)
                ballTypeInput?.error = null
                dialogRef?.dismiss()
            }

            container.addView(itemView)
        }

        dialogRef = showDynamicDialog {
            setView(dialogView)
            setNegativeButton("Cancel", null)
        }
    }
}

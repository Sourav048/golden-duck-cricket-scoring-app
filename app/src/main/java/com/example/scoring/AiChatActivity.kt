package com.example.scoring

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import android.widget.ArrayAdapter
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AiChatActivity : BaseActivity(), TextToSpeech.OnInitListener {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var layoutTyping: LinearLayout
    private lateinit var tvSubtitle: TextView
    private lateinit var adapter: AiChatAdapter

    private var textToSpeech: TextToSpeech? = null
    private var isTtsMuted: Boolean = false

    private var currentGullyId: String = "local"
    private var leagueDisplayName: String = "Local"
    private var appStatsSummaryContext: String = ""

    // Options: "Auto", "English", "Hindi", "Hinglish"
    private var selectedLanguage: String = "Auto"
    private val languageDisplayNames = arrayOf("✨ Auto (Match Prompt)", "🌐 English", "🇮🇳 हिन्दी (Hindi)", "🇮🇳 Hinglish")
    private val languageValues = arrayOf("Auto", "English", "Hindi", "Hinglish")
    private var actvLanguage: MaterialAutoCompleteTextView? = null

    // Active Player Selection ("I am")
    private var selectedPlayerName: String = "None"
    private var actvPlayer: MaterialAutoCompleteTextView? = null
    private var tilPlayer: TextInputLayout? = null

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                etInput.setText(spokenText)
                sendMessage(spokenText)
            }
        }
    }

    private val leaguePlayerMap = mutableMapOf<String, PlayerLeagueStats>()
    private var typingJob: Job? = null
    private var lastGroqRestError: String = ""
    private var lastGeminiRestError: String = ""

    private var selectedImageUri: Uri? = null
    private var selectedImageBitmap: Bitmap? = null

    private lateinit var layoutImagePreview: View
    private lateinit var ivAttachedPreview: ImageView
    private lateinit var ibRemoveImage: ImageButton
    private lateinit var ibAiPhoto: ImageButton

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            onImageSelected(uri, null)
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
            } else bitmap
            onImageSelected(null, softwareBitmap)
        }
    }

    private fun onImageSelected(uri: Uri?, bitmap: Bitmap?) {
        selectedImageUri = uri
        selectedImageBitmap = bitmap
        layoutImagePreview.visibility = View.VISIBLE
        if (bitmap != null) {
            ivAttachedPreview.setImageBitmap(bitmap)
        } else if (uri != null) {
            ivAttachedPreview.setImageURI(uri)
        }
    }

    private fun clearSelectedImage() {
        selectedImageUri = null
        selectedImageBitmap = null
        if (::layoutImagePreview.isInitialized) {
            layoutImagePreview.visibility = View.GONE
        }
    }

    private fun setupPhotoButton() {
        layoutImagePreview = findViewById(R.id.layoutImagePreview)
        ivAttachedPreview = findViewById(R.id.ivAttachedPreview)
        ibRemoveImage = findViewById(R.id.ibRemoveImage)
        ibAiPhoto = findViewById(R.id.ibAiPhoto)

        ibAiPhoto.setOnClickListener {
            val options = arrayOf("📷 Take Photo", "🖼️ Choose from Gallery")
            AlertDialog.Builder(this)
                .setTitle("Attach Photo for Duckie AI")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            try {
                                cameraLauncher.launch(null)
                            } catch (e: Exception) {
                                Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
                            }
                        }
                        1 -> {
                            try {
                                galleryLauncher.launch("image/*")
                            } catch (e: Exception) {
                                Toast.makeText(this, "Gallery picker error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .show()
        }

        ibRemoveImage.setOnClickListener {
            clearSelectedImage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        rvMessages = findViewById(R.id.rvAiChatMessages)
        etInput = findViewById(R.id.etAiInput)
        btnSend = findViewById(R.id.btnAiSend)
        layoutTyping = findViewById(R.id.layoutAiTyping)
        tvSubtitle = findViewById(R.id.tvAiSubtitle)

        findViewById<ImageButton>(R.id.btnAiBack).setOnClickListener { finish() }

        adapter = AiChatAdapter()
        rvMessages.layoutManager = LinearLayoutManager(this)
        rvMessages.itemAnimator = null
        rvMessages.adapter = adapter

        // Load active league ID & display name
        currentGullyId = GullySyncManager.getCurrentGullyId(this) ?: "local"
        leagueDisplayName = if (currentGullyId.isBlank() || currentGullyId.equals("local", ignoreCase = true)) {
            "Local"
        } else {
            currentGullyId
        }

        tvSubtitle.text = "League: $leagueDisplayName • Duckie AI"

        setupKeyboardInsets()
        setupLanguageDropdown()
        setupPhotoButton()
        setupSendButton()
        observeGenerationService()
        setupMicButton()
        setupSpeakerButton()
        setupClearChatButton()
        setupMessageLongClick()
        initTextToSpeech()

        // Load saved chat history or display welcome message
        loadChatHistory()

        // Load stats & match commentary context from Room DB in background
        loadLeagueStatsContext()
    }

    private fun scrollToBottom() {
        rvMessages.post {
            if (::adapter.isInitialized && adapter.itemCount > 0) {
                val lastPos = adapter.itemCount - 1
                rvMessages.scrollToPosition(lastPos)
                rvMessages.post {
                    (rvMessages.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(lastPos, -10000)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AiChatService.isActivityInForeground = true
    }

    override fun onResume() {
        super.onResume()
        AiChatService.isActivityInForeground = true
        // Force redraw, layout request, and scroll to the absolute bottom of the last response on resume
        rvMessages.post {
            rvMessages.invalidate()
            rvMessages.requestLayout()
            scrollToBottom()
        }
    }

    private fun setupMessageLongClick() {
        adapter.setOnMessageLongClickListener(object : AiChatAdapter.OnMessageLongClickListener {
            override fun onMessageLongClick(message: ChatMessage, position: Int) {
                val options = arrayOf(
                    "📋 Copy Text",
                    "🔊 Speak Aloud",
                    "🗑️ Delete Message",
                    "📤 Share Text"
                )

                AlertDialog.Builder(this@AiChatActivity)
                    .setTitle("Message Actions")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> { // Copy
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Duckie AI Message", message.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(this@AiChatActivity, "Text copied to clipboard 📋", Toast.LENGTH_SHORT).show()
                            }
                            1 -> { // Speak
                                speakText(message.text, force = true)
                            }
                            2 -> { // Delete
                                adapter.deleteMessageAt(position)
                                saveChatHistory()
                                Toast.makeText(this@AiChatActivity, "Message deleted 🗑️", Toast.LENGTH_SHORT).show()
                            }
                            3 -> { // Share
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, message.text)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Duckie AI Response")
                                startActivity(shareIntent)
                            }
                        }
                    }
                    .show()
            }
        })
    }

    private fun getPrefKey(): String = "ai_chat_history_$currentGullyId"

    private fun saveChatHistory() {
        try {
            val prefs = getSharedPreferences("duckie_ai_chat_prefs", MODE_PRIVATE)
            val jsonArray = JSONArray()
            val list = adapter.getMessagesList()
            for (msg in list) {
                jsonArray.put(JSONObject().apply {
                    put("id", msg.id)
                    put("text", msg.text)
                    put("isUser", msg.isUser)
                    put("timestamp", msg.timestamp)
                    if (msg.generationTimeSecs != null) {
                        put("generationTimeSecs", msg.generationTimeSecs)
                    }
                    if (!msg.imageUri.isNullOrBlank()) {
                        put("imageUri", msg.imageUri)
                    }
                })
            }
            prefs.edit().putString(getPrefKey(), jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("DuckieAI", "Error saving chat history: ${e.message}")
        }
    }

    private fun loadChatHistory() {
        try {
            val prefs = getSharedPreferences("duckie_ai_chat_prefs", MODE_PRIVATE)
            val jsonStr = prefs.getString(getPrefKey(), null)
            if (!jsonStr.isNullOrBlank()) {
                val jsonArray = JSONArray(jsonStr)
                if (jsonArray.length() > 0) {
                    adapter.clearAllMessages()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val genTime = if (obj.has("generationTimeSecs")) obj.optInt("generationTimeSecs") else null
                        val imgUriStr = if (obj.has("imageUri")) obj.optString("imageUri") else null
                        val msg = ChatMessage(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            text = obj.optString("text"),
                            isUser = obj.optBoolean("isUser"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            generationTimeSecs = genTime,
                            imageUri = if (imgUriStr.isNullOrBlank()) null else imgUriStr
                        )
                        adapter.addMessage(msg)
                    }
                    scrollToBottom()
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("DuckieAI", "Error loading chat history: ${e.message}")
        }

        // Welcome message if no saved history exists
        val welcomeMsg = "👋 Hello! I'm **Duckie**, your AI Assistant in Golden Duck!\n\nI am connected live to League: **$leagueDisplayName**. Tap 🎤 Mic to ask anything or select your response language above!"
        adapter.addMessage(ChatMessage(text = welcomeMsg, isUser = false))
        scrollToBottom()
    }

    private fun setupClearChatButton() {
        findViewById<ImageButton>(R.id.ibAiClearChat)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Chat History?")
                .setMessage("Are you sure you want to clear your conversation history with Duckie AI for league $leagueDisplayName?")
                .setPositiveButton("Clear 🗑️") { _, _ ->
                    val prefs = getSharedPreferences("duckie_ai_chat_prefs", MODE_PRIVATE)
                    prefs.edit().remove(getPrefKey()).apply()
                    adapter.clearAllMessages()
                    textToSpeech?.stop()

                    val welcomeMsg = "👋 Hello! I'm **Duckie**, your AI Assistant in Golden Duck!\n\nI am connected live to League: **$leagueDisplayName**. Tap 🎤 Mic to ask anything or select your response language above!"
                    adapter.addMessage(ChatMessage(text = welcomeMsg, isUser = false))
                    Toast.makeText(this, "Chat History Cleared 🗑️", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupKeyboardInsets() {
        val rootLayout = findViewById<View>(R.id.rootAiChatLayout) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val bottomPadding = if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom
            rootLayout.setPadding(0, 0, 0, bottomPadding)

            if (imeInsets.bottom > 0 && adapter.itemCount > 0) {
                scrollToBottom()
            }

            insets
        }

        etInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && adapter.itemCount > 0) {
                etInput.postDelayed({
                    scrollToBottom()
                }, 100)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            updateTtsLanguage()
        }
    }

    private fun initTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(this, this)
        } catch (e: Exception) {
            Log.e("DuckieAI", "TextToSpeech init error: ${e.message}")
        }
    }

    private fun updateTtsLanguage() {
        val tts = textToSpeech ?: return
        try {
            val targetLocale = if (selectedLanguage == "Hindi") Locale("hi", "IN") else Locale("en", "IN")
            val langResult = tts.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("DuckieAI", "Selected TTS locale missing or unsupported, falling back to US English")
                tts.language = Locale.US
            } else {
                val voices = tts.voices
                val matchingVoice = voices?.find { voice ->
                    voice.locale.language == targetLocale.language && voice.locale.country == targetLocale.country
                }
                if (matchingVoice != null) {
                    tts.voice = matchingVoice
                    Log.d("DuckieAI", "Selected TTS Voice: ${matchingVoice.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("DuckieAI", "Error switching TTS language: ${e.message}")
        }
    }

    private fun setupSpeakerButton() {
        val ibSpeaker = findViewById<ImageButton>(R.id.ibAiSpeaker)
        ibSpeaker?.setOnClickListener {
            isTtsMuted = !isTtsMuted
            if (isTtsMuted) {
                textToSpeech?.stop()
                ibSpeaker.setImageResource(android.R.drawable.ic_lock_silent_mode)
                Toast.makeText(this, "Voice Output Muted 🔇", Toast.LENGTH_SHORT).show()
            } else {
                ibSpeaker.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                Toast.makeText(this, "Voice Output Enabled 🔊", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMicButton() {
        findViewById<ImageButton>(R.id.ibAiMic)?.setOnClickListener {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    val langCode = if (selectedLanguage == "Hindi" || selectedLanguage == "Hinglish" || selectedLanguage == "Auto") "hi-IN" else "en-IN"
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Duckie in $selectedLanguage...")
                }
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun speakText(rawText: String, force: Boolean = false) {
        if ((isTtsMuted && !force) || textToSpeech == null) return

        var cleanText = TextFormatUtils.cleanHumanReadableText(rawText)

        // 1. Strip all Emojis & Symbols (Unicode Emoji Ranges)
        val emojiRegex = Regex("[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\u2300-\u23FF\u2B50\u200D]+")
        cleanText = cleanText.replace(emojiRegex, " ")

        // 2. Strip Markdown formatting symbols
        cleanText = cleanText.replace(Regex("[*#_•`~|\\-]"), " ")

        // 3. Clean up whitespace
        cleanText = cleanText.replace(Regex("\\s+"), " ").trim()

        if (cleanText.isNotBlank()) {
            if (cleanText.any { it in '\u0900'..'\u097F' }) {
                textToSpeech?.language = Locale("hi", "IN")
            } else {
                updateTtsLanguage()
            }
            val result = textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "DUCKIE_TTS")
            if (result == TextToSpeech.ERROR) {
                Log.e("DuckieAI", "TTS speak returned ERROR")
                Toast.makeText(this, "Text-to-Speech playback error ⚠️", Toast.LENGTH_SHORT).show()
            } else if (force) {
                Toast.makeText(this, "Reading message aloud... 🔊", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No readable text to speak", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSelectedLanguage(lang: String) {
        selectedLanguage = lang
        try {
            val prefs = getSharedPreferences("duckie_ai_chat_prefs", MODE_PRIVATE)
            prefs.edit().putString("selected_language", lang).apply()
        } catch (e: Exception) {
            Log.e("DuckieAI", "Error saving language preference: ${e.message}")
        }
    }

    private fun loadSelectedLanguage() {
        try {
            val prefs = getSharedPreferences("duckie_ai_chat_prefs", MODE_PRIVATE)
            val savedLang = prefs.getString("selected_language", "Auto") ?: "Auto"
            selectedLanguage = savedLang

            val displayIndex = languageValues.indexOf(savedLang).let { if (it >= 0) it else 0 }
            actvLanguage?.setText(languageDisplayNames[displayIndex], false)
        } catch (e: Exception) {
            Log.e("DuckieAI", "Error loading language preference: ${e.message}")
        }
    }

    private fun setupLanguageDropdown() {
        actvLanguage = findViewById(R.id.actvAiLanguage)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languageDisplayNames)
        actvLanguage?.setAdapter(adapter)

        loadSelectedLanguage()

        actvLanguage?.setOnItemClickListener { _, _, position, _ ->
            if (position in languageValues.indices) {
                val selectedLang = languageValues[position]
                saveSelectedLanguage(selectedLang)
                updateTtsLanguage()
                val toastText = when (selectedLang) {
                    "Auto" -> "Response Language set to ✨ Auto (Matches Prompt)"
                    "Hindi" -> "Response Language set to हिन्दी (Hindi) 🇮🇳"
                    "Hinglish" -> "Response Language set to Hinglish 🇮🇳"
                    else -> "Response Language set to English 🌐"
                }
                Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getPlayerPrefKey(): String = "selected_active_player_$currentGullyId"

    private fun saveSelectedPlayer(playerName: String) {
        selectedPlayerName = playerName
        try {
            val prefs = getSharedPreferences("duckie_ai_chat_prefs", MODE_PRIVATE)
            prefs.edit().putString(getPlayerPrefKey(), playerName).apply()
        } catch (e: Exception) {
            Log.e("DuckieAI", "Error saving player preference: ${e.message}")
        }
    }

    private fun loadSelectedPlayer() {
        try {
            val prefs = getSharedPreferences("duckie_ai_chat_prefs", MODE_PRIVATE)
            selectedPlayerName = prefs.getString(getPlayerPrefKey(), "None") ?: "None"
        } catch (e: Exception) {
            Log.e("DuckieAI", "Error loading player preference: ${e.message}")
        }
    }

    private fun setupPlayerDropdown(displayList: List<String>, actualPlayerNames: List<String>) {
        actvPlayer = findViewById(R.id.actvAiSecondary)
        tilPlayer = findViewById(R.id.tilAiSecondary)

        tilPlayer?.visibility = View.VISIBLE
        tilPlayer?.hint = "I am (Player)"

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayList)
        actvPlayer?.setAdapter(adapter)

        loadSelectedPlayer()

        val matchedIndex = actualPlayerNames.indexOfFirst { it.equals(selectedPlayerName, ignoreCase = true) }
        val initialDisplay = if (matchedIndex >= 0) displayList[matchedIndex + 1] else displayList[0]
        actvPlayer?.setText(initialDisplay, false)

        actvPlayer?.setOnItemClickListener { _, _, position, _ ->
            val pickedName = if (position > 0 && position - 1 < actualPlayerNames.size) {
                actualPlayerNames[position - 1]
            } else {
                "None"
            }

            saveSelectedPlayer(pickedName)

            if (pickedName != "None") {
                Toast.makeText(this, "Identified as: $pickedName 🏏", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Player identity reset 👤", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getActiveUserContext(): String {
        return if (selectedPlayerName.isNotBlank() && !selectedPlayerName.equals("None", ignoreCase = true)) {
            """
                USER IDENTITY MANDATE:
                The user chatting with you RIGHT NOW is identified as player '$selectedPlayerName' in this league.
                Whenever the user says 'me', 'my', 'I', 'my stats', 'how did I play', 'my runs', 'my wickets', 'my strike rate', 'my economy', 'my performance', or asks about themselves without naming a specific person, they are strictly referring to '$selectedPlayerName'!
                Look up '$selectedPlayerName' in the player stats and commentary logs below and answer directly as their personal coach/analyst!
            """.trimIndent()
        } else {
            """
                USER IDENTITY MANDATE:
                The user chatting with you has NOT selected a specific player identity yet ('Anonymous / Guest').
                If they ask 'who am I', 'how did I play', or refer to 'me' / 'my stats', answer them politely and let them know they can select their player name from the 'I am (Player)' dropdown menu at the top right!
            """.trimIndent()
        }
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(etInput.windowToken, 0)
            WindowInsetsControllerCompat(window, etInput).hide(WindowInsetsCompat.Type.ime())
            etInput.clearFocus()
        } catch (e: Exception) {
            Log.e("DuckieAI", "Error hiding keyboard: ${e.message}")
        }
    }

    private var isGenerating = false

    private fun setGeneratingState(generating: Boolean) {
        isGenerating = generating
        if (generating) {
            btnSend.setImageResource(android.R.drawable.ic_delete)
            btnSend.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.card_red))
            btnSend.contentDescription = "Stop Generating"
            layoutTyping.visibility = View.VISIBLE
        } else {
            btnSend.setImageResource(android.R.drawable.ic_menu_send)
            btnSend.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.send_btn_color))
            btnSend.contentDescription = "Send Message"
            layoutTyping.visibility = View.GONE
        }
    }

    private fun stopGeneration() {
        AiChatService.stopCurrentGeneration(this)
        textToSpeech?.stop()
        setGeneratingState(false)
        Toast.makeText(this, "Generation stopped 🛑", Toast.LENGTH_SHORT).show()
    }

    private fun setupSendButton() {
        btnSend.setOnClickListener {
            if (isGenerating) {
                stopGeneration()
            } else {
                val userText = etInput.text.toString().trim()
                if (userText.isNotEmpty() || selectedImageUri != null || selectedImageBitmap != null) {
                    sendMessage(userText)
                }
            }
        }
    }

    private fun observeGenerationService() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AiChatService.generationState.collect { state ->
                    when (state) {
                        is AiGenerationState.Generating -> {
                            setGeneratingState(true)
                            if (state.currentText.isNotBlank()) {
                                layoutTyping.visibility = View.GONE
                                val lastMsg = adapter.getMessagesList().lastOrNull()
                                if (lastMsg != null && !lastMsg.isUser) {
                                    adapter.updateLastMessageText(state.currentText)
                                } else {
                                    adapter.addMessage(ChatMessage(text = state.currentText, isUser = false))
                                }
                                scrollToBottom()
                            } else {
                                layoutTyping.visibility = View.VISIBLE
                            }
                        }
                        is AiGenerationState.Completed -> {
                            setGeneratingState(false)
                            layoutTyping.visibility = View.GONE
                            if (state.replyText.isNotBlank()) {
                                val lastMsg = adapter.getMessagesList().lastOrNull()
                                if (lastMsg != null && !lastMsg.isUser) {
                                    adapter.updateLastMessageText(state.replyText, generationTimeSecs = state.durationSecs)
                                } else {
                                    adapter.addMessage(ChatMessage(text = state.replyText, isUser = false, generationTimeSecs = state.durationSecs))
                                }
                                scrollToBottom()
                                speakText(state.replyText)
                                saveChatHistory()
                            }
                        }
                        is AiGenerationState.Error -> {
                            setGeneratingState(false)
                            layoutTyping.visibility = View.GONE
                            if (state.message.isNotBlank()) {
                                val lastMsg = adapter.getMessagesList().lastOrNull()
                                if (lastMsg != null && !lastMsg.isUser) {
                                    adapter.updateLastMessageText(state.message)
                                } else {
                                    adapter.addMessage(ChatMessage(text = state.message, isUser = false))
                                }
                                scrollToBottom()
                            }
                        }
                        is AiGenerationState.Cancelled -> {
                            setGeneratingState(false)
                            layoutTyping.visibility = View.GONE
                        }
                        is AiGenerationState.Idle -> {
                            setGeneratingState(false)
                        }
                    }
                }
            }
        }
    }

    private fun sendMessage(text: String) {
        val attachedImageUriStr = selectedImageUri?.toString()
        val attachedBitmap = selectedImageBitmap ?: if (selectedImageUri != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(contentResolver, selectedImageUri!!)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val bm = MediaStore.Images.Media.getBitmap(contentResolver, selectedImageUri)
                    bm?.copy(Bitmap.Config.ARGB_8888, false)
                }
            } catch (e: Exception) {
                Log.e("DuckieAI", "Error decoding image URI: ${e.message}")
                null
            }
        } else null

        // Clear preview bar
        clearSelectedImage()

        etInput.text.clear()
        hideKeyboard()

        // Stop any current speech
        textToSpeech?.stop()

        // Add user message with attached image URI
        adapter.addMessage(ChatMessage(text = text, isUser = true, imageUri = attachedImageUriStr))

        val lastPos = adapter.itemCount - 1
        rvMessages.postDelayed({
            (rvMessages.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(lastPos, 0)
        }, 120)

        // Set generating state UI
        setGeneratingState(true)

        if (appStatsSummaryContext.isBlank()) {
            lifecycleScope.launch(Dispatchers.IO) {
                buildLeagueStatsContextSync()
                AiChatService.startAiGeneration(
                    context = this@AiChatActivity,
                    prompt = text,
                    gullyId = currentGullyId,
                    leagueName = leagueDisplayName,
                    language = selectedLanguage,
                    playerName = selectedPlayerName,
                    imageUriStr = attachedImageUriStr,
                    bitmap = attachedBitmap
                )
            }
        } else {
            AiChatService.startAiGeneration(
                context = this,
                prompt = text,
                gullyId = currentGullyId,
                leagueName = leagueDisplayName,
                language = selectedLanguage,
                playerName = selectedPlayerName,
                imageUriStr = attachedImageUriStr,
                bitmap = attachedBitmap
            )
        }
    }

    private suspend fun generateAiResponseStream(
        prompt: String,
        attachedBitmap: Bitmap?,
        statsContext: String,
        onChunk: (String) -> Unit
    ): String {
        lastGroqRestError = ""
        lastGeminiRestError = ""

        val geminiKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            field.get(null) as? String ?: ""
        } catch (_: Exception) { "" }.trim()

        val groqKey = try {
            val field = BuildConfig::class.java.getField("GROQ_API_KEY")
            field.get(null) as? String ?: ""
        } catch (_: Exception) { "" }.trim()

        val langInstruction = getLanguageInstruction()
        val userContext = getActiveUserContext()
        val systemInstructionText = """
            You are 'Duckie', a witty, sharp, cricket-savvy AI Analyst for Golden Duck Scoring app.
            Active League Name: $leagueDisplayName
            
            $userContext
            
            $langInstruction
            
            You have full access to every match, ball, over, scorecard, fall of wicket, partnership, commentary log, match rules, ball type, and player statistic in this league using your tools.
            - CRITICAL MANDATE: Use your database tools (e.g. resolvePlayerByName, getPlayerStats, getLeaderboard) to fetch real live numbers dynamically! NEVER say "I don't have access to live database" or "paste your runs here". ALWAYS execute the appropriate tool to look up the exact numbers!
            - BE BRUTALLY HONEST & REALISTIC: Never give fake praise, deceptive compliments, or sugar-coated participation awards!
            - If a player has low runs, bad strike rate, high economy, or duck dismissals, destroy them with savage, profane, hilarious roasts using raw curse words!
            - If a top MVP player asks, acknowledge their real numbers with aggressive, profane respect.
            - When asked to compare players, give an uncensored, profane, objective verdict on who is genuinely better based strictly on database facts.
            - When asked about specific matches, dates, or ball-by-ball events (e.g. "What happened in 22 Sept match 4 ball 4.3 of first innings"), scan the commentary logs above and describe the exact event!
            - CRITICAL - BALL TYPE & MATCH RULES: Always check the [MATCH SETTINGS & INFO TAB] provided for each match in the context! Pay strict attention to the exact Ball Type (e.g., Stumper, Tennis, Leather) and match rules (e.g., Last Man Standing, Free Hit). NEVER claim or assume a match was played with a leather ball unless the Ball Type in the context explicitly says 'Leather'! If the ball type is 'Stumper', explicitly refer to it as a Stumper ball when discussing ball, match, or equipment details!
            - Always refer to the league by its name: '$leagueDisplayName'.
            - HUMAN-READABLE FORMATTING MANDATE: ALWAYS format your responses so they are clean, clear, complete, and easy to read on mobile screens! Format with clean Markdown, bold headers, bullet lists, and cricket emojis! 🏏🔥 Write math formulas in simple plain text.
            - MARKDOWN & TABLE FORMATTING RULES:
              1. ALWAYS put clean double line breaks (\n\n) before and after headers, paragraphs, bullet points, and tables.
              2. For tables, put each row on its OWN line with clear line breaks (\n). NEVER combine a heading and a table header on the same line!
              3. Keep tables concise with short column names so they fit nicely on mobile screens.
              4. Always generate and finish your entire answer/plan completely without stopping mid-sentence!
            - Be savagely funny, profane, uncensored, analytical, and format responses with clean markdown and cricket emojis! 🏏🔥
        """.trimIndent()

        val combinedPrompt = "$systemInstructionText\n\nUSER QUESTION: ${if (prompt.isBlank()) "Analyze this image in detail!" else prompt}"

        if (attachedBitmap == null && groqKey.isNotBlank() && groqKey != "null") {
            val groqStreamResult = callGroqApiStream(prompt, groqKey, statsContext, onChunk)
            if (!groqStreamResult.isNullOrBlank()) {
                return groqStreamResult
            }
        }

        if (geminiKey.isNotBlank() && geminiKey != "null") {
            val sdkModels = listOf(
                "gemini-2.5-flash",
                "gemini-flash-latest"
            )

            if (attachedBitmap != null) {
                for (modelName in sdkModels) {
                    try {
                        val generativeModel = GenerativeModel(modelName = modelName, apiKey = geminiKey)
                        val fullSb = StringBuilder()
                        val multimodalContent = content {
                            image(attachedBitmap)
                            text(combinedPrompt)
                        }
                        generativeModel.generateContentStream(multimodalContent).collect { chunk ->
                            val chunkText = chunk.text
                            if (!chunkText.isNullOrEmpty()) {
                                fullSb.append(chunkText)
                                onChunk(chunkText)
                            }
                        }
                        val finalResult = fullSb.toString()
                        if (finalResult.isNotBlank()) {
                            Log.d("DuckieAI", "Gemini SDK Stream success with model: $modelName")
                            return finalResult
                        }
                    } catch (e: Exception) {
                        Log.d("DuckieAI", "Model $modelName SDK Stream error: ${e.message}")
                    }
                }
            } else {
                val db = AppDatabase.getInstance(applicationContext)
                val functionRegistry = AiFunctionRegistry(db)

                for (modelName in sdkModels) {
                    try {
                        val generativeModel = GenerativeModel(
                            modelName = modelName,
                            apiKey = geminiKey,
                            tools = listOf(AiToolDeclarations.aiDatabaseTools)
                        )

                        val fullSb = StringBuilder()
                        var toolExecutedContext = ""

                        generativeModel.generateContentStream(combinedPrompt).collect { chunk ->
                            val functionCalls = try { chunk.functionCalls } catch (_: Exception) { emptyList() }
                            if (functionCalls.isNotEmpty()) {
                                val executedResults = StringBuilder()
                                for (fCall in functionCalls) {
                                    val result = functionRegistry.executeFunctionCall(fCall.name, fCall.args, currentGullyId)
                                    executedResults.append("\n[TOOL '${fCall.name}' RESULT]:\n${JSONObject(mapOf("result" to result))}\n")
                                }
                                toolExecutedContext += executedResults.toString()
                            }

                            val chunkText = chunk.text
                            if (!chunkText.isNullOrEmpty()) {
                                fullSb.append(chunkText)
                                onChunk(chunkText)
                            }
                        }

                        // If a function call was requested, perform a second pass with database result injected
                        if (toolExecutedContext.isNotBlank()) {
                            val followUpPrompt = "$combinedPrompt\n\nDATABASE QUERY RESULTS EXECUTED BY TOOL CALLS:\n$toolExecutedContext\nNow answer the user question accurately using the database results above!"
                            fullSb.clear()
                            generativeModel.generateContentStream(followUpPrompt).collect { secondChunk ->
                                val text = secondChunk.text
                                if (!text.isNullOrEmpty()) {
                                    fullSb.append(text)
                                    onChunk(text)
                                }
                            }
                        }

                        val finalResult = fullSb.toString()
                        if (finalResult.isNotBlank()) {
                            Log.d("DuckieAI", "Gemini SDK Stream success with model: $modelName")
                            return finalResult
                        }
                    } catch (e: Exception) {
                        Log.d("DuckieAI", "Model $modelName SDK Stream error: ${e.message}")
                    }
                }

                val restResponse = callGeminiRestApi(combinedPrompt, geminiKey)
                if (!restResponse.isNullOrBlank()) {
                    return restResponse
                }
            }
        }

        val keyErr = when {
            geminiKey.isBlank() && groqKey.isBlank() -> "API keys missing in local.properties"
            lastGroqRestError.isNotBlank() && lastGeminiRestError.isNotBlank() -> "$lastGroqRestError | $lastGeminiRestError"
            lastGroqRestError.isNotBlank() -> lastGroqRestError
            lastGeminiRestError.isNotBlank() -> lastGeminiRestError
            else -> "Request failed or rate limited"
        }
        return "⚠️ Unable to connect to AI services ($keyErr). Please verify network connection or API keys in local.properties."
    }

    private fun getLanguageInstruction(): String {
        return when (selectedLanguage) {
            "Hindi" -> "CRITICAL LANGUAGE MANDATE: FIRST PREFERENCE MUST GO TO THE LANGUAGE & SCRIPT OF THE USER PROMPT! If the prompt is written in Devnagari Hindi (हिन्दी), Hinglish, or English, respond in THAT EXACT SAME language and script! If prompt language is neutral or ambiguous, write strictly in natural Devnagari Hindi (हिन्दी) script!"
            "Hinglish" -> "CRITICAL LANGUAGE MANDATE: FIRST PREFERENCE MUST GO TO THE LANGUAGE & SCRIPT OF THE USER PROMPT! If the prompt is written in Hinglish, Devnagari Hindi, or English, respond in THAT EXACT SAME language and script! If prompt language is neutral or ambiguous, write strictly in Hinglish (Roman Hindi)."
            "English" -> "CRITICAL LANGUAGE MANDATE: FIRST PREFERENCE MUST GO TO THE LANGUAGE & SCRIPT OF THE USER PROMPT! If the prompt is written in Devnagari Hindi (हिन्दी) or Hinglish, respond in THAT EXACT SAME language/script so the user gets their answer in the language they typed! If prompt language is in English or neutral, write strictly in pure English."
            else -> """
                CRITICAL LANGUAGE MANDATE:
                1. FIRST PREFERENCE MUST GO TO THE LANGUAGE & SCRIPT OF THE USER PROMPT: You MUST detect the exact language and script used by the user in their prompt!
                   - If the prompt is written in Devnagari Hindi script (हिन्दी), respond strictly in Devnagari Hindi (हिन्दी) script!
                   - If the prompt is written in Hinglish (Hindi words typed using English/Roman characters, e.g. 'mera score kya hai', 'bhai kitne runs bane'), respond strictly in Hinglish!
                   - If the prompt is written in English, respond strictly in pure English!
                2. SECOND PREFERENCE (Fallback): If the user prompt is neutral or ambiguous (e.g. only numbers or image without text), default to pure English.
            """.trimIndent()
        }
    }

    private fun callGroqApiStream(prompt: String, apiKey: String, statsContext: String, onChunk: (String) -> Unit): String? {
        val models = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "qwen-2.5-coder-32b",
            "mixtral-8x7b-32768",
            "qwen/qwen3.8-27b",
            "openai/gpt-oss-20b"
        )
        val urlString = "https://api.groq.com/openai/v1/chat/completions"

        val langInstruction = getLanguageInstruction()
        val userContext = getActiveUserContext()
        val systemInstructionText = """
            You are 'Duckie', a witty, sharp, cricket-savvy AI Analyst for Golden Duck Scoring app.
            Active League Name: $leagueDisplayName
            
            $userContext
            
            $langInstruction
            
            You have full access to every match, ball, over, scorecard, fall of wicket, partnership, commentary log, match rules, ball type, and player statistic in this league using your tools.
            - CRITICAL MANDATE: Use your database tools (e.g. resolvePlayerByName, getPlayerStats, getLeaderboard) to fetch real live numbers dynamically! NEVER say "I don't have access to live database" or "paste your runs here". ALWAYS execute the appropriate tool to look up the exact numbers!
            - BE BRUTALLY HONEST & REALISTIC: Never give fake praise, deceptive compliments, or sugar-coated participation awards!
            - If a player has low runs, bad strike rate, high economy, or duck dismissals, destroy them with savage, profane, hilarious roasts using raw curse words!
            - If a top MVP player asks, acknowledge their real numbers with aggressive, profane respect.
            - When asked to compare players, give an uncensored, profane, objective verdict on who is genuinely better based strictly on database facts.
            - When asked about specific matches, dates, or ball-by-ball events (e.g. "What happened in 22 Sept match 4 ball 4.3 of first innings"), scan the commentary logs above and describe the exact event!
            - CRITICAL - BALL TYPE & MATCH RULES: Always check the [MATCH SETTINGS & INFO TAB] provided for each match in the context! Pay strict attention to the exact Ball Type (e.g., Stumper, Tennis, Leather) and match rules (e.g., Last Man Standing, Free Hit). NEVER claim or assume a match was played with a leather ball unless the Ball Type in the context explicitly says 'Leather'! If the ball type is 'Stumper', explicitly refer to it as a Stumper ball when discussing ball, match, or equipment details!
            - Always refer to the league by its name: '$leagueDisplayName'.
            - HUMAN-READABLE FORMATTING MANDATE: ALWAYS format your responses so they are clean, clear, complete, and easy to read on mobile screens! Format with clean Markdown, bold headers, bullet lists, and cricket emojis! 🏏🔥 Write math formulas in simple plain text.
            - MARKDOWN & TABLE FORMATTING RULES:
              1. ALWAYS put clean double line breaks (\n\n) before and after headers, paragraphs, bullet points, and tables.
              2. For tables, put each row on its OWN line with clear line breaks (\n). NEVER combine a heading and a table header on the same line!
              3. Keep tables concise with short column names so they fit nicely on mobile screens.
              4. Always generate and finish your entire answer/plan completely without stopping mid-sentence!
            - Be savagely funny, profane, uncensored, analytical, and format responses with clean markdown and cricket emojis! 🏏🔥
        """.trimIndent()

        val messagesArray = JSONArray()

        // 1. System Instruction
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemInstructionText)
        })

        // 2. Multi-turn Chat Conversation Memory (Last 8 messages)
        val recentHistory = adapter.getMessagesList().filter { it.text.isNotBlank() || !it.imageUri.isNullOrBlank() }.takeLast(8)
        for (msg in recentHistory) {
            if (msg.text.contains("Hello! I'm **Duckie**")) continue
            val msgContent = if (!msg.imageUri.isNullOrBlank()) {
                val textPart = if (msg.text.isBlank()) "[Attached Photo]" else msg.text
                "$textPart [User attached a photo 📷]".trim()
            } else {
                msg.text
            }
            messagesArray.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "assistant")
                put("content", msgContent)
            })
        }

        // 3. Current User Prompt
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        val db = AppDatabase.getInstance(applicationContext)
        val functionRegistry = AiFunctionRegistry(db)

        for (modelName in models) {
            try {
                var currentPass = 0
                val maxPasses = 2

                while (currentPass < maxPasses) {
                    currentPass++
                    val url = URL(urlString)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    conn.doOutput = true
                    conn.connectTimeout = 12000
                    conn.readTimeout = 12000

                    val payload = JSONObject().apply {
                        put("model", modelName)
                        put("stream", true)
                        put("messages", messagesArray)
                        put("tools", AiToolDeclarations.groqToolsJsonArray)
                        put("tool_choice", "auto")
                        put("temperature", 0.7)
                        put("max_tokens", 4096)
                    }

                    OutputStreamWriter(conn.outputStream).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }

                    val respCode = conn.responseCode
                    if (respCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val fullSb = StringBuilder()
                        val toolCallsAcc = mutableMapOf<Int, JSONObject>()
                        var line: String?

                        while (reader.readLine().also { line = it } != null) {
                            val currentLine = line?.trim() ?: continue
                            if (currentLine.startsWith("data: ")) {
                                val jsonStr = currentLine.substring(6).trim()
                                if (jsonStr == "[DONE]") break
                                try {
                                    val obj = JSONObject(jsonStr)
                                    val choices = obj.optJSONArray("choices")
                                    if (choices != null && choices.length() > 0) {
                                        val choiceObj = choices.getJSONObject(0)
                                        val delta = choiceObj.optJSONObject("delta")

                                        val contentChunk = if (delta != null && !delta.isNull("content")) delta.optString("content") else null
                                        if (!contentChunk.isNullOrBlank() && contentChunk != "null") {
                                            fullSb.append(contentChunk)
                                            onChunk(contentChunk)
                                        }

                                        val tCalls = delta?.optJSONArray("tool_calls")
                                        if (tCalls != null && tCalls.length() > 0) {
                                            for (i in 0 until tCalls.length()) {
                                                val tc = tCalls.getJSONObject(i)
                                                val idx = tc.optInt("index", 0)
                                                val existing = toolCallsAcc.getOrPut(idx) {
                                                    JSONObject().apply {
                                                        put("id", "")
                                                        put("name", "")
                                                        put("arguments", StringBuilder())
                                                    }
                                                }
                                                val tcId = tc.optString("id")
                                                if (!tcId.isNullOrBlank()) existing.put("id", tcId)

                                                val fn = tc.optJSONObject("function")
                                                if (fn != null) {
                                                    val fnName = fn.optString("name")
                                                    if (!fnName.isNullOrBlank()) existing.put("name", fnName)
                                                    val argsChunk = fn.optString("arguments")
                                                    if (!argsChunk.isNullOrEmpty()) {
                                                        (existing.get("arguments") as StringBuilder).append(argsChunk)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        if (toolCallsAcc.isNotEmpty()) {
                            val assistantToolCallsArr = JSONArray()
                            val toolResponses = mutableListOf<JSONObject>()

                            for ((_, tcObj) in toolCallsAcc) {
                                val callId = tcObj.optString("id").ifBlank { "call_${java.util.UUID.randomUUID().toString().take(8)}" }
                                val fnName = tcObj.optString("name")
                                val argsStr = (tcObj.get("arguments") as StringBuilder).toString()

                                if (fnName.isNotBlank()) {
                                    assistantToolCallsArr.put(JSONObject().apply {
                                        put("id", callId)
                                        put("type", "function")
                                        put("function", JSONObject().apply {
                                            put("name", fnName)
                                            put("arguments", argsStr)
                                        })
                                    })

                                    val argsMap = mutableMapOf<String, Any?>()
                                    try {
                                        val argsJson = JSONObject(argsStr)
                                        for (key in argsJson.keys()) {
                                            argsMap[key] = argsJson.get(key)
                                        }
                                    } catch (_: Exception) {}

                                    val execResult = functionRegistry.executeFunctionCall(fnName, argsMap, currentGullyId)
                                    val resultStr = JSONObject(mapOf("result" to execResult)).toString()

                                    toolResponses.add(JSONObject().apply {
                                        put("role", "tool")
                                        put("tool_call_id", callId)
                                        put("content", resultStr)
                                    })
                                }
                            }

                            if (assistantToolCallsArr.length() > 0) {
                                fullSb.clear()
                                messagesArray.put(JSONObject().apply {
                                    put("role", "assistant")
                                    put("tool_calls", assistantToolCallsArr)
                                })
                                for (tr in toolResponses) {
                                    messagesArray.put(tr)
                                }
                                continue // Loop to send tool result to Groq for second pass
                            }
                        }

                        val finalResult = fullSb.toString()
                        if (finalResult.isNotBlank()) {
                            Log.d("DuckieAI", "Groq AI Stream Success with model: $modelName")
                            return finalResult
                        }
                    } else {
                        val errorStream = conn.errorStream
                        val errText = if (errorStream != null) BufferedReader(InputStreamReader(errorStream)).use { it.readText() } else ""
                        val msg = try { JSONObject(errText).optJSONObject("error")?.optString("message") ?: errText } catch (_: Exception) { errText }
                        val isKeyError = respCode == 401 || respCode == 403 || respCode == 404 || msg.contains("does not exist") || msg.contains("access")
                        lastGroqRestError = if (isKeyError) {
                            "Groq API Key Invalid or Expired ($modelName HTTP $respCode)"
                        } else {
                            "Groq ($respCode): $msg"
                        }
                        Log.e("DuckieAI", "Groq API Error ($respCode) for $modelName: $errText")
                        break
                    }
                }
            } catch (e: Exception) {
                lastGroqRestError = "Groq Exception: ${e.message}"
                Log.e("DuckieAI", "Groq REST API error for $modelName: ${e.message}")
            }
        }
        return null
    }

    private fun callGeminiRestApi(combinedPrompt: String, apiKey: String): String? {
        val models = listOf(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"
        )

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", combinedPrompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 4096)
                put("temperature", 0.7)
            })
            put("safetySettings", JSONArray().apply {
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HARASSMENT")
                    put("threshold", "BLOCK_NONE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HATE_SPEECH")
                    put("threshold", "BLOCK_NONE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT")
                    put("threshold", "BLOCK_NONE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_DANGEROUS_CONTENT")
                    put("threshold", "BLOCK_NONE")
                })
            })
        }

        for (baseUrl in models) {
            try {
                val url = URL(baseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-goog-api-key", apiKey.trim())
                conn.doOutput = true
                conn.connectTimeout = 12000
                conn.readTimeout = 12000

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val respCode = conn.responseCode
                if (respCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val responseStr = reader.use { it.readText() }
                    val jsonResponse = JSONObject(responseStr)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text")
                            if (!text.isNullOrBlank()) {
                                Log.d("DuckieAI", "Gemini REST success with URL: $baseUrl")
                                return text
                            }
                        }
                    }
                } else {
                    val errorStream = conn.errorStream
                    val errText = if (errorStream != null) BufferedReader(InputStreamReader(errorStream)).use { it.readText() } else ""
                    val msg = try { JSONObject(errText).optJSONObject("error")?.optString("message") ?: errText } catch (_: Exception) { errText }
                    val isKeyError = respCode == 401 || respCode == 403 || msg.contains("API_KEY_INVALID")
                    lastGeminiRestError = if (isKeyError) {
                        "Google Gemini API Key Invalid"
                    } else {
                        "Google API ($respCode): $msg"
                    }
                    Log.e("DuckieAI", "Gemini REST API Error ($respCode) for $baseUrl: $errText")
                }
            } catch (e: Exception) {
                lastGeminiRestError = "Google API Exception: ${e.message}"
                Log.e("DuckieAI", "Gemini REST API error for $baseUrl: ${e.message}")
            }
        }
        return null
    }

    private fun loadLeagueStatsContext() {
        CoroutineScope(Dispatchers.IO).launch {
            buildLeagueStatsContextSync()
        }
    }

    private fun buildLeagueStatsContextSync() {
        try {
            val db = AppDatabase.getInstance(this@AiChatActivity)
            val allStats = db.statsDao().getAllStats() ?: emptyList()

            leaguePlayerMap.clear()

            for (stat in allStats) {
                if (stat == null) continue
                val pName = stat.playerName?.trim() ?: continue
                if (pName.uppercase(Locale.getDefault()) in listOf("FIELD", "PENALTY", "RETIRED")) continue

                val gId = stat.gullyId
                val matchesGully = if (currentGullyId.equals("local", ignoreCase = true)) {
                    gId.isBlank() || gId.equals("local", ignoreCase = true)
                } else {
                    gId.equals(currentGullyId, ignoreCase = true)
                }
                if (!matchesGully) continue

                val playerStats = leaguePlayerMap.getOrPut(pName.lowercase(Locale.getDefault())) {
                    PlayerLeagueStats(playerName = pName)
                }

                playerStats.totalRuns += stat.runsScored
                playerStats.totalBalls += stat.ballsFaced
                playerStats.fours += stat.fours
                playerStats.sixes += stat.sixes
                playerStats.wicketsTaken += stat.wicketsTaken
                playerStats.runsConceded += stat.runsConceded
                playerStats.ballsBowled += stat.ballsBowled
                playerStats.matchesPlayed += 1

                if (stat.runsScored > playerStats.highestScore) {
                    playerStats.highestScore = stat.runsScored
                }
                if (stat.runsScored >= 50) {
                    playerStats.fifties += 1
                } else if (stat.runsScored >= 30) {
                    playerStats.thirties += 1
                }

                if (stat.wicketsTaken > playerStats.bestWickets) {
                    playerStats.bestWickets = stat.wicketsTaken
                    playerStats.bestRunsConceded = stat.runsConceded
                }
            }

            val allPlayers = db.playerDao().getAllPlayersByGully(currentGullyId) ?: db.playerDao().getAllPlayers() ?: emptyList()
            for (p in allPlayers) {
                if (p == null) continue
                val pName = p.name.trim()
                if (pName.isNotEmpty() && !leaguePlayerMap.containsKey(pName.lowercase(Locale.getDefault()))) {
                    leaguePlayerMap[pName.lowercase(Locale.getDefault())] = PlayerLeagueStats(playerName = pName)
                }
            }

            val sb = StringBuilder()
            sb.append("ACTIVE LEAGUE NAME: ").append(leagueDisplayName).append("\n\n")

            sb.append("=== ALL PLAYER CAREER STATS ===\n")
            for ((_, p) in leaguePlayerMap) {
                val sr = if (p.totalBalls > 0) String.format(Locale.getDefault(), "%.1f", (p.totalRuns * 100.0) / p.totalBalls) else "0.0"
                val eco = if (p.ballsBowled > 0) String.format(Locale.getDefault(), "%.2f", (p.runsConceded * 6.0) / p.ballsBowled) else "0.00"
                sb.append("- P: ").append(p.playerName)
                    .append(" | R: ").append(p.totalRuns)
                    .append(" | Inn: ").append(p.matchesPlayed)
                    .append(" | High: ").append(p.highestScore)
                    .append(" | SR: ").append(sr)
                    .append(" | 6s: ").append(p.sixes)
                    .append(" | 4s: ").append(p.fours)
                    .append(" | 50s: ").append(p.fifties)
                    .append(" | 30s: ").append(p.thirties)
                    .append(" | Wkts: ").append(p.wicketsTaken)
                    .append(" | Best: ").append(p.bestWickets).append("/").append(if (p.bestRunsConceded < 999) p.bestRunsConceded else 0)
                    .append(" | Eco: ").append(eco)
                    .append("\n")
            }

            sb.append("\n=== ALL LEAGUE MATCHES & BALL-BY-BALL COMMENTARY LOGS (LIVE, COMPLETED & ABANDONED) ===\n")
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val rawMatches = db.matchDao().getAllMatchesByGully(currentGullyId) ?: db.matchDao().getAllMatches() ?: emptyList()
            val allMatches = rawMatches.filterNotNull().sortedBy { it.playedAt }
            val totalMatchesCount = allMatches.size

            allMatches.forEachIndexed { idx, match ->
                val dateStr = if (match.playedAt > 0) dateFormat.format(Date(match.playedAt)) else "Recorded Match"
                val matchNumber = idx + 1 // Matches UI "Match #1", "Match #2", ..., "Match #N"

                val statusTag = when {
                    match.isAbandoned -> " [ABANDONED MATCH 🌧️]"
                    !match.isFinished -> " [LIVE / IN-PROGRESS MATCH 🔴]"
                    idx == totalMatchesCount - 1 -> " [MOST RECENT COMPLETED MATCH 🏁]"
                    idx == 0 -> " [FIRST / EARLIEST MATCH RECORDED]"
                    else -> " [COMPLETED MATCH 🏁]"
                }

                val ballTypeStr = if (!match.ballType.isNullOrBlank() && !match.ballType.equals("Not Specified", ignoreCase = true)) {
                    match.ballType
                } else {
                    "Stumper"
                }

                val tossW = if (!match.tossWinner.isNullOrBlank()) match.tossWinner else "N/A"
                val tossD = if (!match.tossDecision.isNullOrBlank()) match.tossDecision else "N/A"
                val tossStr = if (tossW != "N/A") "$tossW won & opted to $tossD" else "N/A"

                val s1Str = if (match.firstInningsStartTime > 0) timeFormat.format(Date(match.firstInningsStartTime)) else "-"
                val e1Str = if (match.firstInningsEndTime > 0) timeFormat.format(Date(match.firstInningsEndTime)) else "-"
                val s2Str = if (match.secondInningsStartTime > 0) timeFormat.format(Date(match.secondInningsStartTime)) else "-"
                val e2Str = if (match.secondInningsEndTime > 0) timeFormat.format(Date(match.secondInningsEndTime)) else "-"

                val matchRulesStr = "Runs on Wides/NB: ${if (match.ruleRunsOnWide) "YES" else "NO"} | " +
                        "Free Hit: ${if (match.ruleFreeHit) "YES" else "NO"} | " +
                        "Byes/LegByes: ${if (match.ruleRunsOnBye) "YES" else "NO"} | " +
                        "Overthrows: ${if (match.ruleOverthrow) "YES" else "NO"} | " +
                        "Last Man Standing / Every Player Bats: ${if (match.ruleEveryPlayerBats) "YES" else "NO"}"

                sb.append("MATCH #").append(matchNumber).append(statusTag)
                    .append(" | Date: ").append(dateStr)
                    .append(" | Teams: ").append(match.teamAName).append(" vs ").append(match.teamBName)
                    .append(" | Format: ").append(match.totalOvers).append(" Overs")
                    .append(" | Squads: ").append(match.teamAPlayerCount).append("v").append(match.teamBPlayerCount)
                    .append(" | Venue: ").append(match.venue ?: "Local Ground")
                    .append(" | Result/Status: ").append(match.result ?: if (match.isAbandoned) "Abandoned / No Result" else "In Progress")
                    .append(" | POTM: ").append(match.playerOfTheMatchName ?: "N/A")
                    .append("\n  [MATCH SETTINGS & INFO TAB]")
                    .append(" -> Ball Type: ").append(ballTypeStr)
                    .append(" | Toss Result: ").append(tossStr)
                    .append(" | Conditions/Rules: ").append(matchRulesStr)
                    .append(" | Timings: 1st Inn (").append(s1Str).append(" to ").append(e1Str).append("), 2nd Inn (").append(s2Str).append(" to ").append(e2Str).append(")")
                    .append("\n")

                if (!match.isFinished && !match.isAbandoned) {
                    val striker = match.currentStrikerName ?: "N/A"
                    val nonStriker = match.currentNonStrikerName ?: "N/A"
                    val bowler = match.currentBowlerName ?: "N/A"
                    sb.append("  LIVE STATUS -> Striker: ").append(striker)
                        .append(" | Non-Striker: ").append(nonStriker)
                        .append(" | Bowler: ").append(bowler)
                        .append("\n")
                }

                sb.append("  1st Innings (").append(match.firstInningsTeam ?: match.teamAName).append("): ")
                    .append(match.firstInningsRuns).append("/").append(match.firstInningsWickets).append("\n")

                val comm1 = match.commentaryJson1 ?: emptyList()
                comm1.filterNotNull().forEach { c ->
                    sb.append("    [1st Innings Over ").append(c.over ?: "0.0").append("]: ").append(c.text ?: "").append("\n")
                }

                if (match.secondInningsTeam != null || match.secondInningsRuns > 0) {
                    sb.append("  2nd Innings (").append(match.secondInningsTeam ?: match.teamBName).append("): ")
                        .append(match.secondInningsRuns).append("/").append(match.secondInningsWickets).append("\n")

                    val comm2 = match.commentaryJson2 ?: emptyList()
                    comm2.filterNotNull().forEach { c ->
                        sb.append("    [2nd Innings Over ").append(c.over ?: "0.0").append("]: ").append(c.text ?: "").append("\n")
                    }
                }

                sb.append("\n")
            }

            appStatsSummaryContext = sb.toString()
            Log.d("DuckieAI", "Context Loaded: ${leaguePlayerMap.size} players, ${allMatches.size} matches for league $leagueDisplayName")

            val registeredPlayers = db.playerDao().getAllPlayersByGully(currentGullyId)?.filterNotNull()
                ?: if (currentGullyId.equals("local", ignoreCase = true)) {
                    db.playerDao().getAllPlayers()?.filterNotNull()?.filter { it.gullyId.isBlank() || it.gullyId.equals("local", ignoreCase = true) } ?: emptyList()
                } else {
                    emptyList()
                }

            val registeredNames = registeredPlayers.map { it.name.trim() }.filter { it.isNotBlank() }
            val activeLeagueNames = registeredNames.ifEmpty { leaguePlayerMap.values.map { it.playerName } }

            val playerList = activeLeagueNames.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            val displayList = mutableListOf("👤 Select Player")
            playerList.forEach { name ->
                displayList.add("🏏 $name")
            }

            runOnUiThread {
                setupPlayerDropdown(displayList, playerList)
            }
        } catch (e: Exception) {
            Log.e("DuckieAI", "Error loading stats context: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        saveChatHistory()
    }

    override fun onStop() {
        super.onStop()
        AiChatService.isActivityInForeground = false
        saveChatHistory()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveChatHistory()
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
    }
}

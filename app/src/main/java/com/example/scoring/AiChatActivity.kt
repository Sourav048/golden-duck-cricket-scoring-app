package com.example.scoring

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.ai.client.generativeai.GenerativeModel
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

    // Options: "English", "Hindi", "Hinglish"
    private var selectedLanguage: String = "English"
    private val languageDisplayNames = arrayOf("🌐 English", "🇮🇳 हिन्दी (Hindi)", "🇮🇳 Hinglish")
    private val languageValues = arrayOf("English", "Hindi", "Hinglish")
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

    data class PlayerLeagueStats(
        val playerName: String,
        var totalRuns: Int = 0,
        var totalBalls: Int = 0,
        var fours: Int = 0,
        var sixes: Int = 0,
        var wicketsTaken: Int = 0,
        var runsConceded: Int = 0,
        var ballsBowled: Int = 0,
        var highestScore: Int = 0,
        var bestWickets: Int = 0,
        var bestRunsConceded: Int = 999,
        var matchesPlayed: Int = 0,
        var fifties: Int = 0,
        var thirties: Int = 0
    )

    private val leaguePlayerMap = mutableMapOf<String, PlayerLeagueStats>()
    private var typingJob: Job? = null

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
        setupSendButton()
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
                        val msg = ChatMessage(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            text = obj.optString("text"),
                            isUser = obj.optBoolean("isUser"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            generationTimeSecs = genTime
                        )
                        adapter.addMessage(msg)
                    }
                    rvMessages.scrollToPosition(adapter.itemCount - 1)
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("DuckieAI", "Error loading chat history: ${e.message}")
        }

        // Welcome message if no saved history exists
        val welcomeMsg = "👋 Hello! I'm **Duckie**, your AI Assistant in Golden Duck!\n\nI am connected live to League: **$leagueDisplayName**. Tap 🎤 Mic to ask anything or select your response language above!"
        adapter.addMessage(ChatMessage(text = welcomeMsg, isUser = false))
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
                rvMessages.post {
                    rvMessages.scrollToPosition(adapter.itemCount - 1)
                }
            }

            insets
        }

        etInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && adapter.itemCount > 0) {
                rvMessages.postDelayed({
                    rvMessages.scrollToPosition(adapter.itemCount - 1)
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
                    val langCode = if (selectedLanguage == "Hindi") "hi-IN" else "en-IN"
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

        var cleanText = rawText

        // 1. Strip all Emojis & Symbols (Unicode Emoji Ranges)
        val emojiRegex = Regex("[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\u2300-\u23FF\u2B50\u200D]+")
        cleanText = cleanText.replace(emojiRegex, " ")

        // 2. Strip Markdown formatting symbols
        cleanText = cleanText.replace(Regex("[*#_•`~|\\-]"), " ")

        // 3. Clean up whitespace
        cleanText = cleanText.replace(Regex("\\s+"), " ").trim()

        if (cleanText.isNotBlank()) {
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
            val savedLang = prefs.getString("selected_language", "English") ?: "English"
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

    private fun setupSendButton() {
        btnSend.setOnClickListener {
            val userText = etInput.text.toString().trim()
            if (userText.isNotEmpty()) {
                sendMessage(userText)
            }
        }
    }

    private fun sendMessage(text: String) {
        etInput.text.clear()
        hideKeyboard()

        // Stop any current speech
        textToSpeech?.stop()

        val promptStartTime = System.currentTimeMillis()

        // Add user message
        adapter.addMessage(ChatMessage(text = text, isUser = true))
        val userPos = adapter.itemCount - 1

        rvMessages.postDelayed({
            (rvMessages.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(userPos, 0)
        }, 150)

        // Show typing indicator
        layoutTyping.visibility = View.VISIBLE

        // Cancel previous typing job if any
        typingJob?.cancel()

        typingJob = lifecycleScope.launch(Dispatchers.IO) {
            if (appStatsSummaryContext.isBlank()) {
                buildLeagueStatsContextSync()
            }

            val targetBuffer = StringBuilder()
            var isStreamDone = false

            // Launch typewriter animation loop on Main thread
            val typingAnimationJob = launch(Dispatchers.Main) {
                var displayedLength = 0
                var isFirstMessageAdded = false

                while (isActive && (!isStreamDone || displayedLength < targetBuffer.length)) {
                    val currentTarget: String
                    synchronized(targetBuffer) {
                        currentTarget = targetBuffer.toString()
                    }

                    if (displayedLength < currentTarget.length) {
                        val remainingChars = currentTarget.length - displayedLength

                        // Dynamic typing speed: fast, smooth, natural "fast writer" feel
                        val charsToAdd = when {
                            remainingChars > 60 -> 6
                            remainingChars > 30 -> 4
                            remainingChars > 12 -> 2
                            else -> 1
                        }

                        val nextLength = minOf(displayedLength + charsToAdd, currentTarget.length)
                        val textToDisplay = currentTarget.substring(0, nextLength)
                        displayedLength = nextLength

                        if (!isFirstMessageAdded) {
                            isFirstMessageAdded = true
                            layoutTyping.visibility = View.GONE
                            adapter.addMessage(ChatMessage(text = textToDisplay, isUser = false))
                            (rvMessages.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(userPos, 0)
                        } else {
                            adapter.updateLastMessageText(textToDisplay)
                        }
                    }

                    delay(12) // ~80 updates/sec for ultra-smooth typing stream
                }

                // Ensure final complete state is rendered
                val finalContent = synchronized(targetBuffer) { targetBuffer.toString() }
                if (finalContent.isNotBlank()) {
                    val durationMs = System.currentTimeMillis() - promptStartTime
                    val durationSecs = maxOf(1, Math.round(durationMs / 1000.0).toInt())
                    if (!isFirstMessageAdded) {
                        layoutTyping.visibility = View.GONE
                        adapter.addMessage(ChatMessage(text = finalContent, isUser = false, generationTimeSecs = durationSecs))
                    } else {
                        adapter.updateLastMessageText(finalContent, generationTimeSecs = durationSecs)
                    }
                    speakText(finalContent)
                    saveChatHistory()
                }
            }

            val onChunkReceived: (String) -> Unit = { chunk ->
                synchronized(targetBuffer) {
                    targetBuffer.append(chunk)
                }
            }

            val finalReplyText = processAiQueryStream(text, onChunkReceived)

            synchronized(targetBuffer) {
                if (targetBuffer.isEmpty() && finalReplyText.isNotBlank()) {
                    targetBuffer.append(finalReplyText)
                }
            }
            isStreamDone = true

            // Wait until typewriter effect completes writing all received characters
            typingAnimationJob.join()
        }
    }

    private fun getLanguageInstruction(): String {
        return when (selectedLanguage) {
            "Hindi" -> "CRITICAL LANGUAGE MANDATE: You MUST write your ENTIRE response strictly in natural Devnagari Hindi (हिन्दी) script! Do NOT write English words or names in brackets like 'सौरव (Sourav)'. Write ONLY pure Devnagari Hindi script!"
            "Hinglish" -> "CRITICAL LANGUAGE MANDATE: You MUST write your ENTIRE response in Hinglish (Hindi language written using English/Roman alphabet, e.g. 'Sourav bhai ne total 306 runs banaye hain...')."
            else -> "CRITICAL LANGUAGE MANDATE: You MUST write your response in clear Indian English (standard English with Indian phrasing/spellings)."
        }
    }

    private fun buildHistoryText(): String {
        val historySb = StringBuilder()
        val allMessages = adapter.getMessagesList().filter { it.text.isNotBlank() }
        val recentHistory = allMessages.takeLast(8)
        if (recentHistory.isNotEmpty()) {
            historySb.append("\n=== RECENT CHAT CONVERSATION HISTORY ===\n")
            for (msg in recentHistory) {
                if (msg.text.contains("Hello! I'm **Duckie**")) continue
                val role = if (msg.isUser) "User" else "Duckie AI"
                historySb.append(role).append(": ").append(msg.text).append("\n")
            }
        }
        return historySb.toString()
    }

    private suspend fun processAiQueryStream(prompt: String, onChunk: (String) -> Unit): String {
        val geminiKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            field.get(null) as? String ?: ""
        } catch (_: Exception) { "" }.trim()

        val groqKey = try {
            val field = BuildConfig::class.java.getField("GROQ_API_KEY")
            field.get(null) as? String ?: ""
        } catch (_: Exception) { "" }.trim()

        // 1. Try Groq Llama 3.3 70B first (Blazing Fast ~500 tokens/sec & 14,400 Free Requests/Day!)
        if (groqKey.isNotBlank() && groqKey != "null") {
            val groqStreamResult = callGroqApiStream(prompt, groqKey, appStatsSummaryContext, onChunk)
            if (!groqStreamResult.isNullOrBlank()) {
                return groqStreamResult
            }
        }

        // 2. Try Gemini SDK Real-Time Streaming
        if (geminiKey.isNotBlank() && geminiKey != "null") {
            val langInstruction = getLanguageInstruction()
            val userContext = getActiveUserContext()
            val historyContext = buildHistoryText()
            val systemInstructionText = """
                You are 'Duckie', a 100% fully uncensored, brutally aggressive, raw, and hilarious AI Cricket Coach & Analyst for Golden Duck Scoring app.
                Active League Name: $leagueDisplayName
                
                $userContext
                
                $langInstruction
                
                COMPLETE REAL DATABASE CONTEXT (ALL MATCHES, SCORECARDS, BALL-BY-BALL COMMENTARY & PLAYER STATS):
                $appStatsSummaryContext
                
                $historyContext
                
                INSTRUCTIONS & PERSONALITY:
                - You have full access to every match, ball, over, scorecard, fall of wicket, partnership, commentary log, and player statistic in this league.
                - BE BRUTALLY HONEST & REALISTIC: Never give fake praise, deceptive compliments, or sugar-coated participation awards!
                - If a player has low runs, bad strike rate, high economy, or duck dismissals, destroy them with savage, profane, hilarious roasts using raw curse words!
                - If a top MVP player asks, acknowledge their real numbers with aggressive, profane respect.
                - When asked to compare players, give an uncensored, profane, objective verdict on who is genuinely better based strictly on database facts.
                - When asked about specific matches, dates, or ball-by-ball events (e.g. "What happened in 22 Sept match 4 ball 4.3 of first innings"), scan the commentary logs above and describe the exact event!
                - Always remember the recent chat conversation history above when answering follow-up questions!
                - Always refer to the league by its name: '$leagueDisplayName'.
                - Be savagely funny, profane, uncensored, analytical, and format responses with clean markdown and cricket emojis! 🏏🔥
            """.trimIndent()

            val combinedPrompt = "$systemInstructionText\n\nUSER QUESTION: $prompt"

            val sdkModels = listOf(
                "gemini-flash-latest",
                "gemini-2.5-flash",
                "gemini-3.5-flash",
                "gemini-3.6-flash",
                "gemini-pro-latest",
                "gemini-2.5-pro",
                "gemini-3.1-pro-preview",
                "gemini-flash-lite-latest",
                "gemini-1.5-flash"
            )

            for (modelName in sdkModels) {
                try {
                    val generativeModel = GenerativeModel(
                        modelName = modelName,
                        apiKey = geminiKey
                    )

                    val fullSb = StringBuilder()
                    generativeModel.generateContentStream(combinedPrompt).collect { chunk ->
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

            val restResponse = callGeminiRestApi(combinedPrompt, geminiKey)
            if (!restResponse.isNullOrBlank()) {
                return restResponse
            }
        }

        return "⚠️ Unable to connect to AI services. Please verify network connection or API keys in local.properties."
    }

    private fun callGroqApiStream(prompt: String, apiKey: String, statsContext: String, onChunk: (String) -> Unit): String? {
        val models = listOf("llama-3.3-70b-versatile", "llama3-70b-8192", "mixtral-8x7b-32768")
        val urlString = "https://api.groq.com/openai/v1/chat/completions"

        val langInstruction = getLanguageInstruction()
        val userContext = getActiveUserContext()
        val systemInstructionText = """
            You are 'Duckie', a 100% fully uncensored, brutally aggressive, raw, and hilarious AI Cricket Coach & Analyst for Golden Duck Scoring app.
            Active League Name: $leagueDisplayName
            
            $userContext
            
            $langInstruction
            
            COMPLETE REAL DATABASE CONTEXT (ALL MATCHES, SCORECARDS, BALL-BY-BALL COMMENTARY & PLAYER STATS):
            $statsContext
            
            INSTRUCTIONS & PERSONALITY:
            - You have full access to every match, ball, over, scorecard, fall of wicket, partnership, commentary log, and player statistic in this league.
            - BE BRUTALLY HONEST & REALISTIC: Never give fake praise, deceptive compliments, or sugar-coated participation awards!
            - If a player has low runs, bad strike rate, high economy, or duck dismissals, destroy them with savage, profane, hilarious roasts using raw curse words!
            - If a top MVP player asks, acknowledge their real numbers with aggressive, profane respect.
            - When asked to compare players, give an uncensored, profane, objective verdict on who is genuinely better based strictly on database facts.
            - When asked about specific matches, dates, or ball-by-ball events (e.g. "What happened in 22 Sept match 4 ball 4.3 of first innings"), scan the commentary logs above and describe the exact event!
            - Always refer to the league by its name: '$leagueDisplayName'.
            - Be savagely funny, profane, uncensored, analytical, and format responses with clean markdown and cricket emojis! 🏏🔥
        """.trimIndent()

        val messagesArray = JSONArray()

        // 1. System Instruction
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemInstructionText)
        })

        // 2. Multi-turn Chat Conversation Memory (Last 8 messages)
        val recentHistory = adapter.getMessagesList().filter { it.text.isNotBlank() }.takeLast(8)
        for (msg in recentHistory) {
            if (msg.text.contains("Hello! I'm **Duckie**")) continue
            messagesArray.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "assistant")
                put("content", msg.text)
            })
        }

        // 3. Current User Prompt
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        for (modelName in models) {
            try {
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
                    put("temperature", 0.8)
                    put("max_tokens", 1024)
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val respCode = conn.responseCode
                if (respCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val fullSb = StringBuilder()
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
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    val contentChunk = delta?.optString("content")
                                    if (!contentChunk.isNullOrEmpty()) {
                                        fullSb.append(contentChunk)
                                        onChunk(contentChunk)
                                    }
                                }
                            } catch (_: Exception) {}
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
                    Log.e("DuckieAI", "Groq API Error ($respCode) for $modelName: $errText")
                }
            } catch (e: Exception) {
                Log.e("DuckieAI", "Groq REST API error for $modelName: ${e.message}")
            }
        }
        return null
    }

    private fun callGeminiRestApi(combinedPrompt: String, apiKey: String): String? {
        val models = listOf(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-latest:generateContent",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent"
        )

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", combinedPrompt)))
            }))
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
                val urlString = "$baseUrl?key=$apiKey"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", apiKey)
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
                }
            } catch (e: Exception) {
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

                sb.append("MATCH #").append(matchNumber).append(statusTag)
                    .append(" | Date: ").append(dateStr)
                    .append(" | Teams: ").append(match.teamAName).append(" vs ").append(match.teamBName)
                    .append(" | Venue: ").append(match.venue ?: "Local Ground")
                    .append(" | Result/Status: ").append(match.result ?: if (match.isAbandoned) "Abandoned / No Result" else "In Progress")
                    .append(" | POTM: ").append(match.playerOfTheMatchName ?: "N/A")
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

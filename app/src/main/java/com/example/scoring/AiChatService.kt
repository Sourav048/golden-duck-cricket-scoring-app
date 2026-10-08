package com.example.scoring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

class AiChatService : Service() {

    companion object {
        const val CHANNEL_BACKGROUND_ID = "duckie_ai_bg_channel"
        const val CHANNEL_REMINDER_ID = "duckie_ai_reminder_channel"

        const val NOTIFICATION_BG_ID = 9001
        const val NOTIFICATION_COMPLETED_ID = 9002

        const val ACTION_START_GENERATION = "com.example.scoring.action.START_GENERATION"
        const val ACTION_STOP_GENERATION = "com.example.scoring.action.STOP_GENERATION"

        const val EXTRA_PROMPT = "extra_prompt"
        const val EXTRA_GULLY_ID = "extra_gully_id"
        const val EXTRA_LEAGUE_NAME = "extra_league_name"
        const val EXTRA_LANGUAGE = "extra_language"
        const val EXTRA_PLAYER_NAME = "extra_player_name"
        const val EXTRA_IMAGE_URI = "extra_image_uri"

        var activeBitmap: Bitmap? = null
        var isActivityInForeground: Boolean = false

        private val _generationState = MutableStateFlow<AiGenerationState>(AiGenerationState.Idle)
        val generationState: StateFlow<AiGenerationState> = _generationState.asStateFlow()

        fun isGenerating(): Boolean {
            return _generationState.value is AiGenerationState.Generating
        }

        fun stopCurrentGeneration(context: Context) {
            val stopIntent = Intent(context, AiChatService::class.java).apply {
                action = ACTION_STOP_GENERATION
            }
            context.startService(stopIntent)
        }

        fun startAiGeneration(
            context: Context,
            prompt: String,
            gullyId: String,
            leagueName: String,
            language: String,
            playerName: String,
            imageUriStr: String?,
            bitmap: Bitmap?
        ) {
            activeBitmap = bitmap
            val intent = Intent(context, AiChatService::class.java).apply {
                action = ACTION_START_GENERATION
                putExtra(EXTRA_PROMPT, prompt)
                putExtra(EXTRA_GULLY_ID, gullyId)
                putExtra(EXTRA_LEAGUE_NAME, leagueName)
                putExtra(EXTRA_LANGUAGE, language)
                putExtra(EXTRA_PLAYER_NAME, playerName)
                putExtra(EXTRA_IMAGE_URI, imageUriStr)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    private var lastGroqRestError: String = ""
    private var lastGeminiRestError: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_GENERATION -> {
                val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: ""
                val gullyId = intent.getStringExtra(EXTRA_GULLY_ID) ?: "local"
                val leagueName = intent.getStringExtra(EXTRA_LEAGUE_NAME) ?: "Local"
                val language = intent.getStringExtra(EXTRA_LANGUAGE) ?: "Auto"
                val playerName = intent.getStringExtra(EXTRA_PLAYER_NAME) ?: "None"
                val imageUriStr = intent.getStringExtra(EXTRA_IMAGE_URI)

                val notification = buildSilentBackgroundNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_BG_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_BG_ID, notification)
                }

                startGeneration(
                    prompt = prompt,
                    gullyId = gullyId,
                    leagueName = leagueName,
                    language = language,
                    playerName = playerName,
                    imageUriStr = imageUriStr,
                    attachedBitmap = activeBitmap
                )
            }
            ACTION_STOP_GENERATION -> {
                stopGeneration()
            }
        }

        return START_NOT_STICKY
    }

    private fun startGeneration(
        prompt: String,
        gullyId: String,
        leagueName: String,
        language: String,
        playerName: String,
        imageUriStr: String?,
        attachedBitmap: Bitmap?
    ) {
        activeJob?.cancel()

        _generationState.value = AiGenerationState.Generating(prompt = prompt, currentText = "")

        val promptStartTime = System.currentTimeMillis()

        activeJob = serviceScope.launch {
            val targetBuffer = StringBuilder()

            val onChunkReceived: (String) -> Unit = { chunk ->
                synchronized(targetBuffer) {
                    targetBuffer.append(chunk)
                }
                val currentText = synchronized(targetBuffer) { targetBuffer.toString() }
                _generationState.value = AiGenerationState.Generating(
                    prompt = prompt,
                    currentText = TextFormatUtils.cleanHumanReadableText(currentText)
                )
            }

            try {
                val finalReplyText = processAiQueryStream(
                    prompt = prompt,
                    attachedBitmap = attachedBitmap,
                    gullyId = gullyId,
                    leagueDisplayName = leagueName,
                    selectedLanguage = language,
                    selectedPlayerName = playerName,
                    onChunk = onChunkReceived
                )

                synchronized(targetBuffer) {
                    if (targetBuffer.isEmpty() && finalReplyText.isNotBlank()) {
                        targetBuffer.append(finalReplyText)
                    }
                }

                val rawFinalContent = synchronized(targetBuffer) { targetBuffer.toString() }
                val finalContent = TextFormatUtils.cleanHumanReadableText(rawFinalContent)
                val durationSecs = maxOf(1, Math.round((System.currentTimeMillis() - promptStartTime) / 1000.0).toInt())

                if (finalContent.isNotBlank()) {
                    saveMessageToChatHistory(gullyId, finalContent, durationSecs)
                    _generationState.value = AiGenerationState.Completed(
                        prompt = prompt,
                        replyText = finalContent,
                        durationSecs = durationSecs
                    )
                    // If user left the AI chat screen, send a clean reminder notification
                    if (!isActivityInForeground) {
                        sendCompletionReminderNotification(
                            title = "Duckie AI 🏏",
                            message = "Response generated! Tap to open chat."
                        )
                    }
                } else {
                    _generationState.value = AiGenerationState.Error("No response received from AI.")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d("AiChatService", "Generation cancelled by user")
                    _generationState.value = AiGenerationState.Cancelled
                } else {
                    Log.e("AiChatService", "Error during background generation: ${e.message}")
                    _generationState.value = AiGenerationState.Error("Error: ${e.message}")
                }
            } finally {
                activeBitmap = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                val nm = getSystemService(NotificationManager::class.java)
                nm?.cancel(NOTIFICATION_BG_ID)
                stopSelf()
            }
        }
    }

    private fun stopGeneration() {
        activeJob?.cancel()
        activeJob = null
        activeBitmap = null
        _generationState.value = AiGenerationState.Cancelled
        stopForeground(STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIFICATION_BG_ID)
        stopSelf()
    }

    private fun saveMessageToChatHistory(gullyId: String, replyText: String, durationSecs: Int) {
        try {
            val prefs = getSharedPreferences("duckie_ai_chat_prefs", MODE_PRIVATE)
            val prefKey = "ai_chat_history_$gullyId"
            val jsonStr = prefs.getString(prefKey, null)
            val jsonArray = if (!jsonStr.isNullOrBlank()) JSONArray(jsonStr) else JSONArray()

            jsonArray.put(JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("text", replyText)
                put("isUser", false)
                put("timestamp", System.currentTimeMillis())
                put("generationTimeSecs", durationSecs)
            })

            prefs.edit().putString(prefKey, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("AiChatService", "Error saving message to chat history: ${e.message}")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return

            // 1. Min importance silent channel for foreground service execution
            val bgChannel = NotificationChannel(
                CHANNEL_BACKGROUND_ID,
                "Duckie AI Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent background channel for AI generation"
                setShowBadge(false)
            }
            nm.createNotificationChannel(bgChannel)

            // 2. High importance channel for completion reminders
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER_ID,
                "Duckie AI Response Ready",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when AI response finishes generating"
            }
            nm.createNotificationChannel(reminderChannel)
        }
    }

    private fun buildSilentBackgroundNotification(): android.app.Notification {
        val openIntent = Intent(this, AiChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_BACKGROUND_ID)
            .setContentTitle("Golden Duck")
            .setContentText("Duckie AI working in background...")
            .setSmallIcon(R.drawable.ic_ai_assistant)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    private fun sendCompletionReminderNotification(title: String, message: String) {
        val openIntent = Intent(this, AiChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_REMINDER_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_ai_assistant)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_COMPLETED_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun getLanguageInstruction(selectedLanguage: String): String {
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

    private fun getActiveUserContext(selectedPlayerName: String): String {
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

    private fun buildLightweightLeagueSummary(db: AppDatabase, gullyId: String, leagueDisplayName: String): String {
        return try {
            val sb = StringBuilder()
            sb.append("ACTIVE LEAGUE: ").append(leagueDisplayName).append("\n\n")

            val allPlayers = db.playerDao().getAllPlayersByGully(gullyId) ?: db.playerDao().getAllPlayers() ?: emptyList()
            val validPlayers = allPlayers.filterNotNull().filter { 
                it.name.isNotBlank() && it.name.uppercase(Locale.getDefault()) !in listOf("FIELD", "PENALTY", "RETIRED") 
            }

            if (validPlayers.isNotEmpty()) {
                sb.append("KNOWN PLAYERS IN LEAGUE (Name -> ID):\n")
                validPlayers.take(40).forEach { p ->
                    sb.append("- ").append(p.name).append(" (id: ").append(p.id).append(")\n")
                }
            }

            val rawMatches = db.matchDao().getAllMatchesByGully(gullyId) ?: db.matchDao().getAllMatches() ?: emptyList()
            val recentMatches = rawMatches.filterNotNull().sortedByDescending { it.playedAt }.take(5)
            if (recentMatches.isNotEmpty()) {
                sb.append("\nRECENT MATCHES SUMMARY:\n")
                recentMatches.forEach { m ->
                    val resultText = m.result ?: if (m.isAbandoned) "Abandoned" else "In Progress"
                    sb.append("- ").append(m.teamAName).append(" vs ").append(m.teamBName)
                        .append(" | Result: ").append(resultText)
                        .append(" | Scores: ").append(m.firstInningsRuns).append("/").append(m.firstInningsWickets)
                    if (m.secondInningsRuns > 0) {
                        sb.append(" vs ").append(m.secondInningsRuns).append("/").append(m.secondInningsWickets)
                    }
                    sb.append("\n")
                }
            }

            sb.toString()
        } catch (e: Exception) {
            "ACTIVE LEAGUE: $leagueDisplayName"
        }
    }

    private suspend fun processAiQueryStream(
        prompt: String,
        attachedBitmap: Bitmap?,
        gullyId: String,
        leagueDisplayName: String,
        selectedLanguage: String,
        selectedPlayerName: String,
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

        val db = AppDatabase.getInstance(applicationContext)
        val statsContext = buildLightweightLeagueSummary(db, gullyId, leagueDisplayName)

        val langInstruction = getLanguageInstruction(selectedLanguage)
        val userContext = getActiveUserContext(selectedPlayerName)
        val systemInstructionText = """
            You are 'Duckie', a witty, sharp, cricket-savvy AI Analyst for Golden Duck Scoring app.
            
            $userContext
            
            $langInstruction
            
            $statsContext
            
            INSTRUCTIONS & PERSONALITY:
            - You have access to every match, ball, over, scorecard, fall of wicket, partnership, commentary log, match rules, ball type, and player statistic in this league.
            - PREFER COMBINED TOOLS: Use 'getPlayerStatsByName' to look up player stats directly by name in 1 single step.
            - PRE-LOADED DIRECTORY: Check the 'KNOWN PLAYERS IN LEAGUE' list above to find player IDs directly without needing name resolution tool calls.
            - PARALLEL TOOL CALLS: If querying multiple players or items, emit ALL tool calls at once in a single response turn.
            - CRITICAL ANTI-DISCLAIMER MANDATE: YOU HAVE DIRECT ACCESS TO DATABASE STATS IN THE CONTEXT ABOVE AND VIA FUNCTION TOOLS! NEVER SAY "I don't have access to live database" OR "paste your runs here". ALWAYS look up exact numbers directly!
            - HUMAN-READABLE FORMATTING MANDATE: ALWAYS format your responses with clean Markdown, bold headers, bullet lists, and cricket emojis! 🏏🔥 Write math formulas in simple plain text.
            - SPACING & TYPOGRAPHY RULES:
              1. ALWAYS put proper spaces between words, numbers, and symbols! (e.g. write 'over 2.5' NOT 'over2.5', write 'just 22' NOT 'just22', write 'of 10' NOT 'of10', write 'only 1' NOT 'only1', write 'Strike-rate: 254.55' NOT 'Strike-rate:254.55').
              2. EVERY bullet point MUST be on its OWN SEPARATE LINE starting with a clean double line break (\n\n• ).
              3. NEVER group multiple analysis points into one continuous paragraph! Break paragraphs with double line breaks (\n\n).
              4. NARROW MOBILE SCREEN TABLE RULE: NEVER output wide tables with more than 3 or 4 columns! Wide tables wrap into squished single letters on mobile screens. Use clean 2-column tables (| Stat | Value |) or bullet lists (• **Runs:** 56) instead!
              5. Always generate and finish your entire answer completely without stopping mid-sentence!
        """.trimIndent()

        val combinedPrompt = "$systemInstructionText\n\nUSER QUESTION: ${if (prompt.isBlank()) "Analyze this image in detail!" else prompt}"

        val sdkModels = listOf(
            "gemini-2.5-flash",
            "gemini-flash-latest"
        )

        // 1. Multimodal / Image Queries -> Gemini Vision
        if (attachedBitmap != null && geminiKey.isNotBlank() && geminiKey != "null") {
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
                        return finalResult
                    }
                } catch (e: Exception) {
                    Log.d("AiChatService", "Model $modelName Vision Stream error: ${e.message}")
                }
            }
        }

        // 2. Text Queries -> Groq First (Ultra-fast 1-sec responses + Function Calling)
        if (attachedBitmap == null && groqKey.isNotBlank() && groqKey != "null") {
            val groqStreamResult = callGroqApiStream(prompt, groqKey, gullyId, selectedLanguage, selectedPlayerName, leagueDisplayName, statsContext, onChunk)
            if (!groqStreamResult.isNullOrBlank()) {
                return groqStreamResult
            }
        }

        // 3. Fallback to Gemini SDK with Function Calling
        if (geminiKey.isNotBlank() && geminiKey != "null") {
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
                                val result = functionRegistry.executeFunctionCall(fCall.name, fCall.args, gullyId)
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
                        return finalResult
                    }
                } catch (e: Exception) {
                    Log.d("AiChatService", "Model $modelName SDK Stream error: ${e.message}")
                }
            }

            val restResponse = callGeminiRestApi(combinedPrompt, geminiKey)
            if (!restResponse.isNullOrBlank()) {
                return restResponse
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

    private suspend fun callGroqApiStream(
        prompt: String,
        apiKey: String,
        gullyId: String,
        selectedLanguage: String,
        selectedPlayerName: String,
        leagueDisplayName: String,
        statsContext: String,
        onChunk: (String) -> Unit
    ): String? {
        val models = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "qwen-2.5-coder-32b",
            "mixtral-8x7b-32768",
            "qwen/qwen3.8-27b",
            "openai/gpt-oss-20b"
        )
        val urlString = "https://api.groq.com/openai/v1/chat/completions"

        val langInstruction = getLanguageInstruction(selectedLanguage)
        val userContext = getActiveUserContext(selectedPlayerName)
        val systemInstructionText = """
            You are 'Duckie', a witty, sharp, cricket-savvy AI Analyst for Golden Duck Scoring app.
            
            $userContext
            
            $langInstruction
            
            $statsContext
            
            OPTIMIZATION & TOOL INSTRUCTIONS:
            - PREFER COMBINED TOOLS: Use 'getPlayerStatsByName' to look up player stats directly by name in 1 single step.
            - PRE-LOADED DIRECTORY: Check the 'KNOWN PLAYERS IN LEAGUE' list above to find player IDs directly without needing name resolution tool calls.
            - PARALLEL TOOL CALLS: If querying multiple players or items, emit ALL tool calls at once in a single response turn.
            - CRITICAL ANTI-DISCLAIMER MANDATE: YOU HAVE DIRECT ACCESS TO DATABASE STATS IN THE CONTEXT ABOVE AND VIA FUNCTION TOOLS! NEVER SAY "I don't have access to live database" OR "paste your runs here". ALWAYS look up exact numbers directly!
            - HUMAN-READABLE FORMATTING MANDATE: ALWAYS format your responses with clean Markdown, bold headers, bullet lists, and cricket emojis! 🏏🔥 Write math formulas in simple plain text.
            - SPACING & TYPOGRAPHY RULES:
              1. ALWAYS put proper spaces between words, numbers, and symbols! (e.g. write 'over 2.5' NOT 'over2.5', write 'just 22' NOT 'just22', write 'of 10' NOT 'of10', write 'only 1' NOT 'only1', write 'Strike-rate: 254.55' NOT 'Strike-rate:254.55').
              2. EVERY bullet point MUST be on its OWN SEPARATE LINE starting with a clean double line break (\n\n• ).
              3. NEVER group multiple analysis points into one continuous paragraph! Break paragraphs with double line breaks (\n\n).
              4. NARROW MOBILE SCREEN TABLE RULE: NEVER output wide tables with more than 3 or 4 columns! Wide tables wrap into squished single letters on mobile screens. Use clean 2-column tables (| Stat | Value |) or bullet lists (• **Runs:** 56) instead!
              5. Always generate and finish your entire answer completely without stopping mid-sentence!
        """.trimIndent()

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstructionText)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val db = AppDatabase.getInstance(applicationContext)
        val functionRegistry = AiFunctionRegistry(db)

        for (modelName in models) {
            try {
                var currentPass = 0
                val maxPasses = 2

                while (currentPass < maxPasses && currentCoroutineContext().isActive) {
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

                        while (currentCoroutineContext().isActive) {
                            val currentLine = reader.readLine()?.trim() ?: break
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
                                val callId = tcObj.optString("id").ifBlank { "call_${UUID.randomUUID().toString().take(8)}" }
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

                                    val execResult = functionRegistry.executeFunctionCall(fnName, argsMap, gullyId)
                                    val resultStr = JSONObject(mapOf("result" to execResult)).toString()
                                    toolResponses.add(JSONObject().apply {
                                        put("role", "tool")
                                        put("tool_call_id", callId)
                                        put("name", fnName)
                                        put("content", resultStr)
                                    })
                                }
                            }

                            if (assistantToolCallsArr.length() > 0) {
                                fullSb.clear()
                                onChunk("\n\n")
                                messagesArray.put(JSONObject().apply {
                                    put("role", "assistant")
                                    put("tool_calls", assistantToolCallsArr)
                                })
                                for (tr in toolResponses) {
                                    messagesArray.put(tr)
                                }
                                continue
                            }
                        }

                        val finalResult = fullSb.toString()
                        if (finalResult.isNotBlank()) {
                            return finalResult
                        }
                    } else {
                        val errorStream = conn.errorStream
                        val errText = if (errorStream != null) BufferedReader(InputStreamReader(errorStream)).use { it.readText() } else ""
                        val msg = try { JSONObject(errText).optJSONObject("error")?.optString("message") ?: errText } catch (_: Exception) { errText }
                        lastGroqRestError = if (respCode == 401 || respCode == 403 || respCode == 404 || msg.contains("does not exist") || msg.contains("access")) {
                            "Groq API Key Invalid or Expired ($modelName HTTP $respCode)"
                        } else {
                            "Groq ($respCode): $msg"
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                lastGroqRestError = "Groq Exception: ${e.message}"
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
                }
            } catch (e: Exception) {
                lastGeminiRestError = "Google API Exception: ${e.message}"
            }
        }
        return null
    }
}

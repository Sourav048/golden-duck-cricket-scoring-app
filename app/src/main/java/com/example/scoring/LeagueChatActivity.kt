package com.example.scoring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.Executors

class LeagueChatActivity : BaseActivity() {

    companion object {
        private const val TAG = "LeagueChatActivity"
        private const val SUPABASE_URL = "https://alyfggrwppkctlbroqzr.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFseWZnZ3J3cHBrY3RsYnJvcXpyIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4ODgxMTE4MSwiZXhwIjoyMTA0Mzg3MTgxfQ.OsUzeI4QQZG0QOVAaGu_4iVStPJaOmmWUBNXcgk1juk"
        private const val SUPABASE_BUCKET = "chat_videos"

        @Volatile
        var activeLeagueId: String? = null
    }

    private lateinit var tvLeagueSubtitle: TextView
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var tvEmptyChat: TextView
    private lateinit var etChatMessage: EditText
    private lateinit var btnSendMessage: ImageButton
    private lateinit var btnAttachMedia: ImageButton
    private lateinit var btnChangeName: ImageButton

    private lateinit var cardReplyPreview: com.google.android.material.card.MaterialCardView
    private lateinit var tvReplySender: TextView
    private lateinit var tvReplyText: TextView
    private lateinit var ivReplyPreviewMedia: ImageView
    private lateinit var btnCloseReply: ImageButton

    private lateinit var cardMentionsPopup: MaterialCardView
    private lateinit var rvMentionsSuggestions: RecyclerView
    private lateinit var mentionAdapter: MentionSuggestionsAdapter
    private val activeChatUsersMap = mutableMapOf<String, String>()
    private val allLeaguePlayersList = mutableListOf<PlayerEntity>()

    private var activeReplyMessage: LeagueChatMessage? = null

    private lateinit var leagueId: String
    private lateinit var senderId: String
    private lateinit var senderName: String
    private var senderProfilePic: String? = null

    private var pendingProfilePicUri: Uri? = null
    private var currentProfileCameraUri: Uri? = null
    private val userProfilePicsMap = mutableMapOf<String, String>()

    private var activeProfileDialogAvatarView: ImageView? = null
    private var activeProfileDialogPlaceholderView: TextView? = null

    private lateinit var chatAdapter: LeagueChatAdapter
    private val messageList = mutableListOf<LeagueChatMessage>()
    private var chatListener: ListenerRegistration? = null

    private val db = FirebaseFirestore.getInstance()

    private var currentCameraUri: Uri? = null

    // Activity Result Launchers
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val caption = etChatMessage.text.toString().trim()
            etChatMessage.setText("")
            val mimeType = try { contentResolver.getType(uri) ?: "" } catch (e: Exception) { "" }
            val isVideo = mimeType.startsWith("video/") || 
                          uri.toString().endsWith(".mp4") || 
                          uri.toString().endsWith(".mkv") || 
                          uri.toString().endsWith(".3gp")
            uploadAndSendMedia(uri, isVideo = isVideo, captionText = caption)
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success && currentCameraUri != null) {
            val caption = etChatMessage.text.toString().trim()
            etChatMessage.setText("")
            uploadAndSendMedia(currentCameraUri!!, isVideo = false, captionText = caption)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraInternal()
        } else {
            Toast.makeText(this, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickProfilePicLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            pendingProfilePicUri = uri
            activeProfileDialogAvatarView?.let { iv ->
                Glide.with(this).load(uri).circleCrop().into(iv)
            }
            activeProfileDialogPlaceholderView?.visibility = View.GONE
            activeProfileDialogAvatarView?.visibility = View.VISIBLE
        }
    }

    private val takeProfilePicLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success && currentProfileCameraUri != null) {
            pendingProfilePicUri = currentProfileCameraUri
            activeProfileDialogAvatarView?.let { iv ->
                Glide.with(this).load(currentProfileCameraUri).circleCrop().into(iv)
            }
            activeProfileDialogPlaceholderView?.visibility = View.GONE
            activeProfileDialogAvatarView?.visibility = View.VISIBLE
        }
    }

    private val requestProfileCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            launchProfileCameraInternal()
        } else {
            Toast.makeText(this, "Camera permission is required to take photo.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_league_chat)

        leagueId = intent.getStringExtra("LEAGUE_ID")
            ?: intent.getStringExtra("gully_id")
            ?: intent.getStringExtra("leagueId")
            ?: GullySyncManager.getCurrentGullyId(this)
            ?: "local"

        if (leagueId.isEmpty() || leagueId == "local") {
            Toast.makeText(this, "League ID not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUserIdentity()

        tvLeagueSubtitle = findViewById(R.id.tvLeagueSubtitle)
        rvChatMessages = findViewById(R.id.rvChatMessages)
        tvEmptyChat = findViewById(R.id.tvEmptyChat)
        etChatMessage = findViewById(R.id.etChatMessage)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        btnAttachMedia = findViewById(R.id.btnAttachMedia)
        btnChangeName = findViewById(R.id.btnChangeName)

        cardReplyPreview = findViewById(R.id.cardReplyPreview)
        tvReplySender = findViewById(R.id.tvReplySender)
        tvReplyText = findViewById(R.id.tvReplyText)
        ivReplyPreviewMedia = findViewById(R.id.ivReplyPreviewMedia)
        btnCloseReply = findViewById(R.id.btnCloseReply)

        btnCloseReply.setOnClickListener { clearReplyMode() }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        tvLeagueSubtitle.text = "League: $leagueId • As: $senderName"

        setupRecyclerView()
        setupSwipeToReply()
        setupKeyboardRichContent()
        setupWindowInsetsAndKeyboard()

        btnSendMessage.setOnClickListener { sendMessage() }
        btnAttachMedia.setOnClickListener { showAttachmentOptions() }
        btnChangeName.setOnClickListener { showEditProfileDialog() }

        registerActiveChatUser()
        setupMentionsSystem()

        listenForMessages()
    }

    override fun onResume() {
        super.onResume()
        if (::leagueId.isInitialized && leagueId.isNotBlank()) {
            activeLeagueId = leagueId
            ChatNotificationHelper.clearNotificationForLeague(this, leagueId)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::leagueId.isInitialized && activeLeagueId == leagueId) {
            activeLeagueId = null
        }
    }

    private fun setupWindowInsetsAndKeyboard() {
        val rootLayout = findViewById<View>(R.id.rootLayoutChat)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val bottomPadding = if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom
            rootLayout.setPadding(0, 0, 0, bottomPadding)

            if (imeInsets.bottom > 0 && messageList.isNotEmpty()) {
                rvChatMessages.post {
                    rvChatMessages.scrollToPosition(messageList.size - 1)
                }
            }

            insets
        }

        etChatMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && messageList.isNotEmpty()) {
                rvChatMessages.postDelayed({
                    rvChatMessages.scrollToPosition(messageList.size - 1)
                }, 100)
            }
        }
    }

    private fun setupKeyboardRichContent() {
        ViewCompat.setOnReceiveContentListener(
            etChatMessage,
            arrayOf("image/*", "image/gif")
        ) { _, payload ->
            val split = payload.partition { item -> item.uri != null }
            val gifContent = split.first
            val remaining = split.second

            if (gifContent != null) {
                val clip = gifContent.clip
                for (i in 0 until clip.itemCount) {
                    val uri = clip.getItemAt(i).uri
                    if (uri != null) {
                        val caption = etChatMessage.text.toString().trim()
                        etChatMessage.setText("")
                        uploadAndSendMedia(uri, isVideo = false, captionText = caption)
                    }
                }
            }
            remaining
        }
    }

    private fun setupUserIdentity() {
        val prefs = getSharedPreferences("gully_prefs", Context.MODE_PRIVATE)
        
        var storedId = prefs.getString("chat_sender_id", null)
        if (storedId.isNullOrEmpty()) {
            storedId = try {
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()
            } catch (e: Exception) {
                UUID.randomUUID().toString()
            }
            prefs.edit().putString("chat_sender_id", storedId).apply()
        }
        senderId = storedId

        var storedName = prefs.getString("chat_sender_name", null)
        if (storedName.isNullOrEmpty()) {
            storedName = "Player-${senderId.takeLast(4)}"
            prefs.edit().putString("chat_sender_name", storedName).apply()
        }
        senderName = storedName

        senderProfilePic = prefs.getString("chat_sender_profile_pic", null)
    }

    private fun registerActiveChatUser() {
        if (::leagueId.isInitialized && leagueId.isNotBlank() && ::senderId.isInitialized && ::senderName.isInitialized) {
            LeagueNotificationManager.subscribeToLeague(leagueId, senderId)
            val userMap = hashMapOf(
                "userId" to senderId,
                "displayName" to senderName,
                "profilePic" to (senderProfilePic ?: ""),
                "lastActive" to System.currentTimeMillis()
            )
            db.collection("gullies").document(leagueId)
                .collection("chat_users").document(senderId)
                .set(userMap, SetOptions.merge())
        }
    }

    private fun setupMentionsSystem() {
        cardMentionsPopup = findViewById(R.id.cardMentionsPopup)
        rvMentionsSuggestions = findViewById(R.id.rvMentionsSuggestions)
        rvMentionsSuggestions.layoutManager = LinearLayoutManager(this)

        mentionAdapter = MentionSuggestionsAdapter { selectedPlayer ->
            onMentionPlayerSelected(selectedPlayer)
        }
        rvMentionsSuggestions.adapter = mentionAdapter

        loadLeaguePlayersAndChatUsers()

        etChatMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkAndFilterMentions()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun isSystemUser(idOrName: String?): Boolean {
        if (idOrName.isNullOrBlank()) return false
        val clean = idOrName.trim().lowercase()
        return clean == "system" || clean == "system user" || clean.startsWith("system ")
    }

    private fun loadLeaguePlayersAndChatUsers() {
        // 1. Listen to active chat users in Firestore
        db.collection("gullies").document(leagueId)
            .collection("chat_users")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    activeChatUsersMap.clear()
                    userProfilePicsMap.clear()
                    for (doc in snapshot.documents) {
                        if (isSystemUser(doc.id)) continue
                        val name = doc.getString("displayName") ?: doc.getString("name")
                        val pic = doc.getString("profilePic") ?: doc.getString("senderProfilePic")
                        if (!name.isNullOrBlank() && !isSystemUser(name)) {
                            activeChatUsersMap[doc.id] = name.trim()
                        }
                        if (!pic.isNullOrBlank() && !isSystemUser(name)) {
                            userProfilePicsMap[doc.id] = pic.trim()
                        }
                    }
                    chatAdapter.updateUserProfilePics(userProfilePicsMap)

                    val allNames = mutableSetOf<String>()
                    for (n in activeChatUsersMap.values) {
                        if (!isSystemUser(n)) allNames.add(n)
                    }
                    for (p in allLeaguePlayersList) {
                        if (p.name.isNotBlank() && !isSystemUser(p.name)) allNames.add(p.name.trim())
                    }
                    chatAdapter.updateKnownPlayerNames(allNames.toList())
                    checkAndFilterMentions()
                }
            }

        // 2. Load all players for this gully/league from Room DB
        Executors.newSingleThreadExecutor().execute {
            val roomDb = AppDatabase.getInstance(this@LeagueChatActivity)
            val players = roomDb?.playerDao()?.getAllPlayersByGully(leagueId)
                ?: roomDb?.playerDao()?.getAllPlayers()
                ?: emptyList()

            val validPlayers = players.filterNotNull()
            runOnUiThread {
                allLeaguePlayersList.clear()
                allLeaguePlayersList.addAll(validPlayers)

                val allNames = mutableSetOf<String>()
                for (n in activeChatUsersMap.values) {
                    if (!isSystemUser(n)) allNames.add(n)
                }
                for (p in allLeaguePlayersList) {
                    if (p.name.isNotBlank() && !isSystemUser(p.name)) allNames.add(p.name.trim())
                }
                chatAdapter.updateKnownPlayerNames(allNames.toList())
            }
        }
    }

    private fun checkAndFilterMentions() {
        val text = etChatMessage.text.toString()
        val cursorPosition = etChatMessage.selectionStart

        if (cursorPosition <= 0 || text.isEmpty()) {
            cardMentionsPopup.visibility = View.GONE
            return
        }

        val sub = text.substring(0, cursorPosition)
        val lastAt = sub.lastIndexOf('@')

        if (lastAt == -1) {
            cardMentionsPopup.visibility = View.GONE
            return
        }

        if (lastAt > 0 && !sub[lastAt - 1].isWhitespace()) {
            cardMentionsPopup.visibility = View.GONE
            return
        }

        val query = sub.substring(lastAt + 1)
        if (query.contains('\n')) {
            cardMentionsPopup.visibility = View.GONE
            return
        }

        updateMentionsList(query.trim())
    }

    private fun updateMentionsList(query: String) {
        val chatUserNames = mutableSetOf<String>()
        for ((id, name) in activeChatUsersMap) {
            if (!isSystemUser(id) && !isSystemUser(name)) {
                chatUserNames.add(name)
            }
        }

        val activeUserIds = activeChatUsersMap.keys
        for (msg in messageList) {
            if (msg.type == "SYSTEM" || isSystemUser(msg.senderId) || isSystemUser(msg.senderName)) continue

            val sId = msg.senderId.trim()
            if (sId.isNotBlank() && !activeUserIds.contains(sId)) {
                val resolved = resolvePlayerName(sId)
                if (resolved.isNotBlank() && !resolved.startsWith("League Member") && !isSystemUser(resolved)) {
                    chatUserNames.add(resolved)
                } else if (msg.senderName.isNotBlank() && !isSystemUser(msg.senderName)) {
                    chatUserNames.add(msg.senderName.trim())
                }
            } else if (sId.isBlank() && msg.senderName.isNotBlank() && !isSystemUser(msg.senderName)) {
                chatUserNames.add(msg.senderName.trim())
            }
        }

        val activeChatPlayers = mutableListOf<MentionItem.Player>()
        val otherLeaguePlayers = mutableListOf<MentionItem.Player>()
        val addedNames = mutableSetOf<String>()

        // 1. Group 1: Players in League Chat (ON TOP)
        for (name in chatUserNames) {
            if (isSystemUser(name)) continue
            if (name.contains(query, ignoreCase = true) && !addedNames.contains(name.lowercase())) {
                val matchingPlayerEntity = allLeaguePlayersList.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
                val jersey = matchingPlayerEntity?.jerseyNumber ?: "0"
                activeChatPlayers.add(MentionItem.Player(name = name, jersey = jersey, isActiveInChat = true))
                addedNames.add(name.lowercase())
            }
        }

        // 2. Group 2: Other League Players
        for (p in allLeaguePlayersList) {
            val name = p.name.trim()
            if (isSystemUser(name)) continue
            if (name.isNotBlank() && name.contains(query, ignoreCase = true) && !addedNames.contains(name.lowercase())) {
                otherLeaguePlayers.add(MentionItem.Player(name = name, jersey = p.jerseyNumber, isActiveInChat = false))
                addedNames.add(name.lowercase())
            }
        }

        if (activeChatPlayers.isEmpty() && otherLeaguePlayers.isEmpty()) {
            cardMentionsPopup.visibility = View.GONE
            return
        }

        val displayItems = mutableListOf<MentionItem>()

        if (activeChatPlayers.isNotEmpty()) {
            displayItems.add(MentionItem.Header("💬 PLAYERS IN LEAGUE CHAT"))
            displayItems.addAll(activeChatPlayers)
        }

        if (otherLeaguePlayers.isNotEmpty()) {
            displayItems.add(MentionItem.Header("🏏 OTHER LEAGUE PLAYERS"))
            displayItems.addAll(otherLeaguePlayers)
        }

        mentionAdapter.submitList(displayItems)
        cardMentionsPopup.visibility = View.VISIBLE
    }

    private fun onMentionPlayerSelected(player: MentionItem.Player) {
        val text = etChatMessage.text.toString()
        val cursorPosition = etChatMessage.selectionStart
        val sub = text.substring(0, cursorPosition)
        val lastAt = sub.lastIndexOf('@')

        if (lastAt != -1) {
            val prefix = text.substring(0, lastAt)
            val suffix = text.substring(cursorPosition)
            val mentionText = "@${player.name} "
            val newText = prefix + mentionText + suffix

            etChatMessage.setText(newText)
            val newCursorPos = (prefix + mentionText).length
            etChatMessage.setSelection(newCursorPos.coerceAtMost(newText.length))
        }

        cardMentionsPopup.visibility = View.GONE
    }

    private fun setupRecyclerView() {
        chatAdapter = LeagueChatAdapter(senderId) { msg ->
            showOptionsDialog(msg)
        }
        chatAdapter.onReplyClick = { replyId ->
            val index = messageList.indexOfFirst { it.id == replyId }
            if (index != -1) {
                rvChatMessages.smoothScrollToPosition(index)
            }
        }
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        rvChatMessages.layoutManager = layoutManager
        rvChatMessages.adapter = chatAdapter
        rvChatMessages.setItemViewCacheSize(20)
        rvChatMessages.setHasFixedSize(true)
    }

    private fun setupSwipeToReply() {
        val swipeHandler = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                if (pos in 0 until messageList.size) {
                    val msg = messageList[pos]
                    setReplyMode(msg)
                    chatAdapter.notifyItemChanged(pos)
                }
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(swipeHandler).attachToRecyclerView(rvChatMessages)
    }

    private fun buildReplyTextSnippet(msg: LeagueChatMessage): String {
        return LeagueChatAdapter.buildReplyTextSnippet(msg)
    }

    private fun setReplyMode(msg: LeagueChatMessage) {
        activeReplyMessage = msg
        cardReplyPreview.visibility = View.VISIBLE
        val isMine = msg.senderId == senderId
        val nameStr = if (isMine) "You" else msg.senderName.ifBlank { "League Member" }
        tvReplySender.text = "Replying to $nameStr"
        tvReplyText.text = buildReplyTextSnippet(msg)

        val mediaUrl = msg.mediaUrl ?: msg.thumbnailUrl
        if (!mediaUrl.isNullOrEmpty()) {
            ivReplyPreviewMedia.visibility = View.VISIBLE
            Glide.with(this)
                .load(mediaUrl)
                .centerCrop()
                .into(ivReplyPreviewMedia)
        } else {
            ivReplyPreviewMedia.visibility = View.GONE
            Glide.with(this).clear(ivReplyPreviewMedia)
        }

        etChatMessage.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(etChatMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun clearReplyMode() {
        activeReplyMessage = null
        cardReplyPreview.visibility = View.GONE
        ivReplyPreviewMedia.visibility = View.GONE
        Glide.with(this).clear(ivReplyPreviewMedia)
    }

    private fun listenForMessages() {
        chatListener?.remove()
        chatListener = db.collection("gullies")
            .document(leagueId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for chat messages: ${error.message}", error)
                    Toast.makeText(this, "Chat Sync Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val newMessages = mutableListOf<LeagueChatMessage>()
                    for (doc in snapshot.documents) {
                        val msg = doc.toObject(LeagueChatMessage::class.java)
                        if (msg != null) {
                            val fullMsg = msg.copy(id = doc.id)
                            newMessages.add(fullMsg)

                            // Mark unread messages as seen by current user
                            if (fullMsg.id.isNotBlank() && fullMsg.seenBy[senderId] == null) {
                                db.collection("gullies")
                                    .document(leagueId)
                                    .collection("messages")
                                    .document(fullMsg.id)
                                    .update("seenBy.$senderId", System.currentTimeMillis())
                            }
                        }
                    }

                    val isFirstLoad = messageList.isEmpty()
                    val prevSize = messageList.size
                    val prevLastId = messageList.lastOrNull()?.id

                    messageList.clear()
                    messageList.addAll(newMessages)
                    chatAdapter.submitList(messageList)

                    if (messageList.isEmpty()) {
                        tvEmptyChat.visibility = View.VISIBLE
                        rvChatMessages.visibility = View.GONE
                    } else {
                        tvEmptyChat.visibility = View.GONE
                        rvChatMessages.visibility = View.VISIBLE

                        if (isFirstLoad) {
                            rvChatMessages.post {
                                rvChatMessages.scrollToPosition(messageList.size - 1)
                            }
                        } else {
                            val layoutManager = rvChatMessages.layoutManager as? LinearLayoutManager
                            val lastVisiblePos = layoutManager?.findLastCompletelyVisibleItemPosition() ?: -1
                            val isNearBottom = lastVisiblePos >= prevSize - 3
                            val isNewMessageAdded = newMessages.size > prevSize && newMessages.lastOrNull()?.id != prevLastId
                            val lastMsgIsMine = newMessages.lastOrNull()?.senderId == senderId

                            if (isNewMessageAdded && (isNearBottom || lastMsgIsMine)) {
                                rvChatMessages.post {
                                    rvChatMessages.smoothScrollToPosition(messageList.size - 1)
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun extractMentionedUserId(text: String): String? {
        if (!text.contains("@")) return null

        for ((userId, name) in activeChatUsersMap) {
            if (name.isNotBlank() && text.contains("@$name", ignoreCase = true)) {
                if (userId != senderId) {
                    return userId
                }
            }
        }

        for (p in allLeaguePlayersList) {
            if (p.name.isNotBlank() && text.contains("@${p.name}", ignoreCase = true)) {
                if (p.id.isNotBlank() && p.id != senderId) {
                    return p.id
                }
            }
        }

        for (msg in messageList.reversed()) {
            if (msg.senderName.isNotBlank() && text.contains("@${msg.senderName}", ignoreCase = true)) {
                if (msg.senderId.isNotBlank() && msg.senderId != senderId) {
                    return msg.senderId
                }
            }
        }

        return null
    }

    private fun sendMessage() {
        val text = etChatMessage.text.toString().trim()
        if (text.isEmpty()) return

        etChatMessage.setText("")

        val videoUrl = LeagueChatAdapter.extractFirstVideoUrl(text)
        val imageUrl = LeagueChatAdapter.extractFirstImageUrl(text)

        val replyId = activeReplyMessage?.id
        val replySender = if (activeReplyMessage?.senderId == senderId) "You" else activeReplyMessage?.senderName
        val replyRecipientId = activeReplyMessage?.senderId
        val mentionedRecipientId = extractMentionedUserId(text)

        val (targetRecipientId, customNotificationSender) = when {
            !replyRecipientId.isNullOrEmpty() && replyRecipientId != senderId -> {
                Pair(replyRecipientId, "$senderName(Replied to you)")
            }
            !mentionedRecipientId.isNullOrEmpty() && mentionedRecipientId != senderId -> {
                Pair(mentionedRecipientId, "$senderName(Mentioned You🗣️🗣️)")
            }
            else -> Pair(null, senderName)
        }

        val replyText = activeReplyMessage?.let { buildReplyTextSnippet(it) }
        val replyMediaUrl = activeReplyMessage?.mediaUrl ?: activeReplyMessage?.thumbnailUrl
        val replyMediaType = activeReplyMessage?.type

        clearReplyMode()

        if (videoUrl != null) {
            sendMediaMessage(
                mediaUrl = videoUrl,
                mediaType = "VIDEO",
                captionText = text,
                replyId = replyId,
                replySender = replySender,
                replyText = replyText,
                replyMediaUrl = replyMediaUrl,
                replyMediaType = replyMediaType,
                targetRecipientId = targetRecipientId,
                customNotificationSender = customNotificationSender
            )
            return
        }

        if (imageUrl != null) {
            val type = if (LeagueChatAdapter.isGifUrl(imageUrl)) "GIF" else "IMAGE"
            sendMediaMessage(
                mediaUrl = imageUrl,
                mediaType = type,
                captionText = text,
                replyId = replyId,
                replySender = replySender,
                replyText = replyText,
                replyMediaUrl = replyMediaUrl,
                replyMediaType = replyMediaType,
                targetRecipientId = targetRecipientId,
                customNotificationSender = customNotificationSender
            )
            return
        }

        val newMsg = hashMapOf(
            "senderId" to senderId,
            "senderName" to senderName,
            "senderProfilePic" to (senderProfilePic ?: ""),
            "messageText" to text,
            "timestamp" to System.currentTimeMillis(),
            "type" to "TEXT",
            "replyToId" to replyId,
            "replyToSender" to replySender,
            "replyToText" to replyText,
            "replyToMediaUrl" to replyMediaUrl,
            "replyToMediaType" to replyMediaType
        )

        db.collection("gullies")
            .document(leagueId)
            .collection("messages")
            .add(newMsg)
            .addOnSuccessListener { docRef ->
                LeagueNotificationManager.sendLeagueChatNotification(
                    leagueId = leagueId,
                    senderName = senderName,
                    messageText = text,
                    senderId = senderId,
                    targetRecipientId = targetRecipientId,
                    recipientSenderLabel = customNotificationSender,
                    senderProfilePic = senderProfilePic,
                    msgId = docRef.id
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendSystemMessage(systemText: String) {
        if (!::leagueId.isInitialized || leagueId.isBlank()) return
        val systemMsg = hashMapOf(
            "senderId" to "SYSTEM",
            "senderName" to "System",
            "messageText" to systemText,
            "timestamp" to System.currentTimeMillis(),
            "type" to "SYSTEM"
        )
        db.collection("gullies")
            .document(leagueId)
            .collection("messages")
            .add(systemMsg)
    }

    private fun showAttachmentOptions() {
        val options = arrayOf("📷 Camera", "🖼️ Gallery (Photos, GIFs & Videos)")
        AlertDialog.Builder(this)
            .setTitle("Send Media")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCamera()
                    1 -> pickImageLauncher.launch("*/*")
                }
            }
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            launchCameraInternal()
        }
    }

    private fun launchCameraInternal() {
        try {
            val photoDir = File(cacheDir, "chat_photos")
            if (!photoDir.exists()) photoDir.mkdirs()
            val photoFile = File(photoDir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            currentCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching camera: ${e.message}", e)
            Toast.makeText(this, "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadAndSendMedia(uri: Uri, isVideo: Boolean, captionText: String) {
        val replyId = activeReplyMessage?.id
        val replySender = if (activeReplyMessage?.senderId == senderId) "You" else activeReplyMessage?.senderName
        val replyRecipientId = activeReplyMessage?.senderId
        val mentionedRecipientId = extractMentionedUserId(captionText)

        val (targetRecipientId, customNotificationSender) = when {
            !replyRecipientId.isNullOrEmpty() && replyRecipientId != senderId -> {
                Pair(replyRecipientId, "$senderName(Replied to you)")
            }
            !mentionedRecipientId.isNullOrEmpty() && mentionedRecipientId != senderId -> {
                Pair(mentionedRecipientId, "$senderName(Mentioned You🗣️🗣️)")
            }
            else -> Pair(null, senderName)
        }

        val replyText = activeReplyMessage?.let { buildReplyTextSnippet(it) }
        val replyMediaUrl = activeReplyMessage?.mediaUrl ?: activeReplyMessage?.thumbnailUrl
        val replyMediaType = activeReplyMessage?.type

        clearReplyMode()

        val uriStr = uri.toString()
        if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
            sendMediaMessage(
                mediaUrl = uriStr,
                mediaType = "IMAGE",
                captionText = captionText,
                replyId = replyId,
                replySender = replySender,
                replyText = replyText,
                replyMediaUrl = replyMediaUrl,
                replyMediaType = replyMediaType,
                targetRecipientId = targetRecipientId,
                customNotificationSender = customNotificationSender
            )
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle(if (isVideo) "Uploading Video..." else "Processing Photo...")
            .setMessage("Uploading media to cloud storage...")
            .setCancelable(false)
            .create()

        progressDialog.show()

        Executors.newSingleThreadExecutor().execute {
            try {
                val fileExtension = if (isVideo) "mp4" else "jpg"
                val fileName = "${UUID.randomUUID()}.$fileExtension"
                val mimeType = if (isVideo) "video/mp4" else "image/jpeg"

                // Stream directly to Supabase Storage API
                val url = URL("$SUPABASE_URL/storage/v1/object/$SUPABASE_BUCKET/$fileName")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                conn.setRequestProperty("apiKey", SUPABASE_KEY)
                conn.setRequestProperty("Content-Type", mimeType)
                conn.doOutput = true

                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@LeagueChatActivity, "Cannot read media file.", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                val outputStream = conn.outputStream
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == 201) {
                    val publicMediaUrl = "$SUPABASE_URL/storage/v1/object/public/$SUPABASE_BUCKET/$fileName"
                    runOnUiThread {
                        progressDialog.dismiss()
                        val mediaType = if (isVideo) "VIDEO" else "IMAGE"
                        sendMediaMessage(
                            mediaUrl = publicMediaUrl,
                            mediaType = mediaType,
                            captionText = captionText,
                            replyId = replyId,
                            replySender = replySender,
                            replyText = replyText,
                            replyMediaUrl = replyMediaUrl,
                            replyMediaType = replyMediaType,
                            targetRecipientId = targetRecipientId,
                            customNotificationSender = customNotificationSender
                        )
                    }
                } else {
                    val errorText = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                    Log.e(TAG, "Supabase upload failed ($responseCode): $errorText")

                    // Fallback to Base64 for small photo/video files
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.size <= 700_000) {
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val mediaType = if (isVideo) "VIDEO" else "IMAGE"
                        val dataUrl = "data:$mimeType;base64,$base64"
                        runOnUiThread {
                            progressDialog.dismiss()
                            sendMediaMessage(
                                mediaUrl = dataUrl,
                                mediaType = mediaType,
                                captionText = captionText,
                                replyId = replyId,
                                replySender = replySender,
                                replyText = replyText,
                                replyMediaUrl = replyMediaUrl,
                                replyMediaType = replyMediaType,
                                targetRecipientId = targetRecipientId,
                                customNotificationSender = customNotificationSender
                            )
                        }
                    } else {
                        runOnUiThread {
                            progressDialog.dismiss()
                            Toast.makeText(this@LeagueChatActivity, "Upload failed: Make sure bucket 'chat-videos' is Public in Supabase.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                conn.disconnect()

            } catch (e: Exception) {
                Log.e(TAG, "Media upload error: ${e.message}", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@LeagueChatActivity, "Upload error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendMediaMessage(
        mediaUrl: String,
        mediaType: String,
        captionText: String,
        replyId: String? = null,
        replySender: String? = null,
        replyText: String? = null,
        replyMediaUrl: String? = null,
        replyMediaType: String? = null,
        targetRecipientId: String? = null,
        customNotificationSender: String? = null
    ) {
        val newMsg = hashMapOf(
            "senderId" to senderId,
            "senderName" to senderName,
            "senderProfilePic" to (senderProfilePic ?: ""),
            "messageText" to captionText,
            "mediaUrl" to mediaUrl,
            "timestamp" to System.currentTimeMillis(),
            "type" to mediaType,
            "replyToId" to replyId,
            "replyToSender" to replySender,
            "replyToText" to replyText,
            "replyToMediaUrl" to replyMediaUrl,
            "replyToMediaType" to replyMediaType
        )

        db.collection("gullies")
            .document(leagueId)
            .collection("messages")
            .add(newMsg)
            .addOnSuccessListener { docRef ->
                val isGif = mediaType == "GIF" || LeagueChatAdapter.isGifUrl(mediaUrl)
                val notifText = when {
                    isGif -> "🎞️ Sent a GIF"
                    mediaType == "VIDEO" -> "🎥 Sent a Video"
                    else -> "📷 Sent a Photo"
                }

                val hasCaption = captionText.isNotBlank() && captionText.trim() != mediaUrl.trim()
                val finalBody = if (hasCaption) "$notifText: $captionText" else notifText

                LeagueNotificationManager.sendLeagueChatNotification(
                    leagueId = leagueId,
                    senderName = senderName,
                    messageText = finalBody,
                    senderId = senderId,
                    targetRecipientId = targetRecipientId,
                    recipientSenderLabel = customNotificationSender,
                    senderProfilePic = senderProfilePic,
                    msgId = docRef.id
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send media: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showOptionsDialog(msg: LeagueChatMessage) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_chat_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "😭")
        val layoutEmojiBar = dialogView.findViewById<LinearLayout>(R.id.layoutEmojiBar)
        layoutEmojiBar?.removeAllViews()

        for (emoji in emojis) {
            val tvEmoji = TextView(this).apply {
                text = emoji
                textSize = 24f
                setPadding(14, 8, 14, 8)
                setOnClickListener {
                    toggleReaction(msg, emoji)
                    dialog.dismiss()
                }
            }
            layoutEmojiBar?.addView(tvEmoji)
        }

        // Plus button to open full emoji picker / keyboard input
        val btnPlus = TextView(this).apply {
            text = "➕"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            background = ContextCompat.getDrawable(this@LeagueChatActivity, R.drawable.bg_circle_plus)
            setPadding(16, 12, 16, 12)
            setOnClickListener {
                dialog.dismiss()
                showFullEmojiPickerDialog(msg)
            }
        }
        val plusParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = 16 // Distinct gap, NO overlapping!
        }
        btnPlus.layoutParams = plusParams
        layoutEmojiBar?.addView(btnPlus)

        dialogView.findViewById<View>(R.id.optionReply)?.setOnClickListener {
            setReplyMode(msg)
            dialog.dismiss()
        }

        val extractedVideo = LeagueChatAdapter.extractFirstVideoUrl(msg.messageText)
        val extractedImage = LeagueChatAdapter.extractFirstImageUrl(msg.messageText)
        val effectiveMediaUrl = if (!msg.mediaUrl.isNullOrEmpty()) {
            msg.mediaUrl
        } else {
            extractedVideo ?: extractedImage
        }

        val hasMedia = !effectiveMediaUrl.isNullOrEmpty()
        val hasText = msg.messageText.isNotBlank() && (effectiveMediaUrl == null || msg.messageText.trim() != effectiveMediaUrl.trim())

        val optionCopy = dialogView.findViewById<View>(R.id.optionCopy)
        if (hasText) {
            optionCopy?.visibility = View.VISIBLE
            optionCopy?.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Chat Message", msg.messageText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        } else {
            optionCopy?.visibility = View.GONE
        }

        val optionDownloadMedia = dialogView.findViewById<View>(R.id.optionDownloadMedia)
        if (hasMedia && effectiveMediaUrl != null) {
            optionDownloadMedia?.visibility = View.VISIBLE
            optionDownloadMedia?.setOnClickListener {
                downloadMediaToAppFolder(effectiveMediaUrl, msg)
                dialog.dismiss()
            }
        } else {
            optionDownloadMedia?.visibility = View.GONE
        }

        dialogView.findViewById<View>(R.id.optionInfo)?.setOnClickListener {
            showMessageInfoDialog(msg)
            dialog.dismiss()
        }

        val optionDelete = dialogView.findViewById<View>(R.id.optionDelete)
        if (msg.senderId == senderId) {
            optionDelete?.visibility = View.VISIBLE
            optionDelete?.setOnClickListener {
                deleteMessage(msg)
                dialog.dismiss()
            }
        } else {
            optionDelete?.visibility = View.GONE
        }

        dialog.show()
    }

    private fun showFullEmojiPickerDialog(msg: LeagueChatMessage) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_emoji_picker, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val etEmojiInput = dialogView.findViewById<EditText>(R.id.etEmojiInput)
        etEmojiInput?.requestFocus()

        // Automatically popup soft keyboard
        dialog.setOnShowListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(etEmojiInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        // Listen for keyboard input (when user taps ANY emoji on their keyboard)
        etEmojiInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val typedEmoji = s?.toString()?.trim() ?: ""
                if (typedEmoji.isNotEmpty()) {
                    toggleReaction(msg, typedEmoji)
                    dialog.dismiss()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val categories = mapOf(
            "Popular" to listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "😭", "🔥", "👏", "🥰", "😍", "😊"),
            "Expressions" to listOf("😎", "🥳", "😴", "🤔", "🤫", "😜", "🤡", "🤯", "😱", "😡", "🤮", "💩"),
            "Gestures" to listOf("🙌", "🤝", "✌️", "🤞", "🤟", "🤘", "👌", "🤏", "🤙", "💪", "👊", "✊"),
            "Hearts" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "❣️", "💕", "💞"),
            "Cricket & Sports" to listOf("🏏", "🏆", "🥇", "🥈", "🥉", "⚽", "🏀", "🎯", "🏅", "🎉", "🎊", "💯")
        )

        val grid = dialogView.findViewById<LinearLayout>(R.id.layoutFullEmojiGrid)
        grid?.removeAllViews()

        for ((categoryName, emojiList) in categories) {
            val tvHeader = TextView(this).apply {
                text = categoryName
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(ThemeManager.getSeedColor(this@LeagueChatActivity))
                setPadding(0, 16, 0, 8)
            }
            grid?.addView(tvHeader)

            val chunked = emojiList.chunked(6)
            for (row in chunked) {
                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                for (emoji in row) {
                    val tvEmoji = TextView(this).apply {
                        text = emoji
                        textSize = 26f
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            setMargins(4, 6, 4, 6)
                        }
                        setOnClickListener {
                            toggleReaction(msg, emoji)
                            dialog.dismiss()
                        }
                    }
                    rowLayout.addView(tvEmoji)
                }
                grid?.addView(rowLayout)
            }
        }

        dialogView.findViewById<View>(R.id.btnCloseEmojiPicker)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun toggleReaction(msg: LeagueChatMessage, emoji: String) {
        if (msg.id.isBlank()) return
        val currentReaction = msg.reactions[senderId]
        val docRef = db.collection("gullies").document(leagueId).collection("messages").document(msg.id)

        if (currentReaction == emoji) {
            docRef.update("reactions.$senderId", com.google.firebase.firestore.FieldValue.delete())
        } else {
            docRef.update("reactions.$senderId", emoji)
        }
    }

    private fun resolvePlayerName(userId: String): String {
        if (userId.isBlank()) return "League Member"
        if (userId == senderId) return "$senderName (You)"

        val activeName = activeChatUsersMap[userId] ?: activeChatUsersMap.entries.firstOrNull { it.key.equals(userId, ignoreCase = true) }?.value
        if (!activeName.isNullOrBlank()) return activeName

        val playerEntityName = allLeaguePlayersList.find { it.id == userId || it.id.equals(userId, ignoreCase = true) }?.name
        if (!playerEntityName.isNullOrBlank()) return playerEntityName

        val msgName = messageList.find { it.senderId == userId }?.senderName
        if (!msgName.isNullOrBlank()) return msgName

        return "League Member (${userId.takeLast(4)})"
    }

    private fun showMessageInfoDialog(msg: LeagueChatMessage) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_message_info, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvSnippet = dialogView.findViewById<TextView>(R.id.tvInfoMessageSnippet)
        val tvHeading = dialogView.findViewById<TextView>(R.id.tvSeenByHeading)
        val layoutSeenList = dialogView.findViewById<LinearLayout>(R.id.layoutSeenList)
        val btnClose = dialogView.findViewById<View>(R.id.btnCloseInfo)

        tvSnippet?.text = if (msg.messageText.isNotBlank()) msg.messageText else "Media Message"
        tvHeading?.text = "Seen By (${msg.seenBy.size}):"

        layoutSeenList?.removeAllViews()

        if (msg.seenBy.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "No members have viewed this message yet."
                textSize = 13f
                setTextColor(Color.GRAY)
                setPadding(0, 12, 0, 12)
            }
            layoutSeenList?.addView(tvEmpty)
        } else {
            val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            for ((userId, timestamp) in msg.seenBy) {
                val isMe = userId == senderId
                val displayName = if (isMe) "$senderName (You)" else resolvePlayerName(userId)
                val timeStr = if (timestamp > 0) timeSdf.format(Date(timestamp)) else "Just now"

                val tvItem = TextView(this).apply {
                    text = "•  $displayName — $timeStr"
                    textSize = 14f
                    setPadding(0, 10, 0, 10)
                }
                layoutSeenList?.addView(tvItem)
            }
        }

        btnClose?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun deleteMessage(msg: LeagueChatMessage) {
        if (msg.id.isBlank()) return
        AlertDialog.Builder(this)
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message for everyone in the league?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("gullies").document(leagueId).collection("messages").document(msg.id).delete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadMediaToAppFolder(mediaUrl: String, msg: LeagueChatMessage) {
        Toast.makeText(this, "Downloading media...", Toast.LENGTH_SHORT).show()

        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try {
                val subDir = "Golden Duck - A Cricket Scoring App"
                val isVideo = msg.type == "VIDEO" || LeagueChatAdapter.isVideoUrl(mediaUrl)
                val ext = when {
                    mediaUrl.contains(".gif", ignoreCase = true) || mediaUrl.startsWith("data:image/gif") -> ".gif"
                    mediaUrl.contains(".png", ignoreCase = true) || mediaUrl.startsWith("data:image/png") -> ".png"
                    mediaUrl.contains(".webp", ignoreCase = true) || mediaUrl.startsWith("data:image/webp") -> ".webp"
                    mediaUrl.contains(".mp4", ignoreCase = true) || mediaUrl.startsWith("data:video/") || isVideo -> ".mp4"
                    else -> ".jpg"
                }

                val mimeType = when (ext) {
                    ".gif" -> "image/gif"
                    ".png" -> "image/png"
                    ".webp" -> "image/webp"
                    ".mp4" -> "video/mp4"
                    else -> "image/jpeg"
                }

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val suffix = if (msg.id.length >= 4) msg.id.takeLast(4) else msg.id
                val fileName = "chat_media_${timeStamp}_$suffix$ext"

                val bytes: ByteArray? = when {
                    mediaUrl.startsWith("data:") -> {
                        val pureBase64 = mediaUrl.substringAfter("base64,")
                        android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)
                    }
                    mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://") -> {
                        var downloadedBytes: ByteArray? = null
                        try {
                            val url = java.net.URL(mediaUrl)
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 15000
                            conn.readTimeout = 15000
                            conn.requestMethod = "GET"
                            conn.connect()
                            if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                                downloadedBytes = conn.inputStream.use { it.readBytes() }
                            }
                        } catch (_: Exception) {}

                        downloadedBytes ?: run {
                            val fileFromGlide = com.bumptech.glide.Glide.with(this@LeagueChatActivity)
                                .asFile()
                                .load(mediaUrl)
                                .submit()
                                .get()
                            fileFromGlide.readBytes()
                        }
                    }
                    else -> {
                        val uri = Uri.parse(mediaUrl)
                        contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                }

                if (bytes == null || bytes.isEmpty()) {
                    throw Exception("Could not fetch media data")
                }

                var savedLocation = ""

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/$subDir")
                    }
                    val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val destUri = contentResolver.insert(collection, values)
                    if (destUri != null) {
                        contentResolver.openOutputStream(destUri)?.use { out ->
                            out.write(bytes)
                        }
                        savedLocation = "Downloads/$subDir/$fileName"
                    }
                } else {
                    val publicDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), subDir)
                    if (!publicDir.exists()) publicDir.mkdirs()
                    val destFile = File(publicDir, fileName)
                    java.io.FileOutputStream(destFile).use { out ->
                        out.write(bytes)
                    }
                    savedLocation = destFile.absolutePath
                }

                if (savedLocation.isEmpty()) {
                    val appDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: File(filesDir, "downloads")
                    if (!appDir.exists()) appDir.mkdirs()
                    val destFile = File(appDir, fileName)
                    java.io.FileOutputStream(destFile).use { out ->
                        out.write(bytes)
                    }
                    savedLocation = destFile.absolutePath
                }

                runOnUiThread {
                    Toast.makeText(
                        this@LeagueChatActivity,
                        "Downloaded to: $savedLocation",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("LEAGUE_CHAT", "Failed to download media: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@LeagueChatActivity,
                        "Failed to download media: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun launchProfileCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestProfileCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            launchProfileCameraInternal()
        }
    }

    private fun launchProfileCameraInternal() {
        try {
            val photoDir = File(cacheDir, "profile_photos")
            if (!photoDir.exists()) photoDir.mkdirs()
            val photoFile = File(photoDir, "profile_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            currentProfileCameraUri = uri
            takeProfilePicLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching profile camera: ${e.message}", e)
            Toast.makeText(this, "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val etDisplayName = dialogView.findViewById<TextInputEditText>(R.id.etDialogDisplayName)
        val containerProfileAvatar = dialogView.findViewById<View>(R.id.containerProfileAvatar)
        val ivProfileAvatar = dialogView.findViewById<ImageView>(R.id.ivDialogProfileAvatar)
        val tvAvatarPlaceholder = dialogView.findViewById<TextView>(R.id.tvDialogAvatarPlaceholder)
        val pbProfileUpload = dialogView.findViewById<View>(R.id.pbProfileUpload)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelDialog)
        val btnSave = dialogView.findViewById<View>(R.id.btnSaveDialog)

        activeProfileDialogAvatarView = ivProfileAvatar
        activeProfileDialogPlaceholderView = tvAvatarPlaceholder
        pendingProfilePicUri = null

        etDisplayName.setText(senderName)
        etDisplayName.setSelection(senderName.length)

        fun updateDialogAvatarPreview() {
            val currentPic = senderProfilePic
            if (pendingProfilePicUri != null) {
                ivProfileAvatar.visibility = View.VISIBLE
                tvAvatarPlaceholder.visibility = View.GONE
                Glide.with(this).load(pendingProfilePicUri).circleCrop().into(ivProfileAvatar)
            } else if (!currentPic.isNullOrBlank()) {
                ivProfileAvatar.visibility = View.VISIBLE
                tvAvatarPlaceholder.visibility = View.GONE
                Glide.with(this).load(currentPic).circleCrop().into(ivProfileAvatar)
            } else {
                ivProfileAvatar.visibility = View.GONE
                tvAvatarPlaceholder.visibility = View.VISIBLE
                val initial = senderName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P"
                tvAvatarPlaceholder.text = initial
                tvAvatarPlaceholder.background = LeagueChatAdapter.createColoredCircleDrawable(
                    LeagueChatAdapter.getAvatarColor(senderId.ifBlank { senderName })
                )
            }
        }

        updateDialogAvatarPreview()

        containerProfileAvatar.setOnClickListener {
            val hasPhoto = pendingProfilePicUri != null || !senderProfilePic.isNullOrEmpty()
            val options = if (hasPhoto) {
                arrayOf("Choose from Gallery", "Take Photo", "Remove Photo")
            } else {
                arrayOf("Choose from Gallery", "Take Photo")
            }

            AlertDialog.Builder(this)
                .setTitle("Profile Photo")
                .setItems(options) { _, which ->
                    when (options[which]) {
                        "Choose from Gallery" -> pickProfilePicLauncher.launch("image/*")
                        "Take Photo" -> launchProfileCamera()
                        "Remove Photo" -> {
                            pendingProfilePicUri = null
                            senderProfilePic = null
                            updateDialogAvatarPreview()
                        }
                    }
                }
                .show()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newName = etDisplayName.text.toString().trim()
            if (newName.isBlank()) {
                Toast.makeText(this, "Display name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val oldName = senderName

            if (pendingProfilePicUri != null) {
                pbProfileUpload.visibility = View.VISIBLE
                btnSave.isEnabled = false
                btnCancel.isEnabled = false

                uploadProfilePicture(pendingProfilePicUri!!) { uploadedUrl ->
                    pbProfileUpload.visibility = View.GONE
                    btnSave.isEnabled = true
                    btnCancel.isEnabled = true

                    if (uploadedUrl != null) {
                        senderProfilePic = uploadedUrl
                    } else {
                        Toast.makeText(this, "Failed to upload photo, saving name only", Toast.LENGTH_SHORT).show()
                    }

                    saveProfileChangesAndDismiss(dialog, oldName, newName)
                }
            } else {
                saveProfileChangesAndDismiss(dialog, oldName, newName)
            }
        }

        dialog.setOnDismissListener {
            activeProfileDialogAvatarView = null
            activeProfileDialogPlaceholderView = null
        }

        dialog.show()
    }

    private fun uploadProfilePicture(uri: Uri, callback: (String?) -> Unit) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val fileName = "avatar_${senderId}_${System.currentTimeMillis()}.jpg"
                val url = URL("$SUPABASE_URL/storage/v1/object/$SUPABASE_BUCKET/$fileName")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                conn.setRequestProperty("apiKey", SUPABASE_KEY)
                conn.setRequestProperty("Content-Type", "image/jpeg")
                conn.doOutput = true

                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    runOnUiThread { callback(null) }
                    return@execute
                }

                val outputStream = conn.outputStream
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == 201) {
                    val publicMediaUrl = "$SUPABASE_URL/storage/v1/object/public/$SUPABASE_BUCKET/$fileName"
                    runOnUiThread { callback(publicMediaUrl) }
                } else {
                    runOnUiThread { callback(null) }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Profile pic upload error: ${e.message}", e)
                runOnUiThread { callback(null) }
            }
        }
    }

    private fun saveProfileChangesAndDismiss(dialog: AlertDialog, oldName: String, newName: String) {
        val picToSave = senderProfilePic ?: ""
        senderName = newName

        getSharedPreferences("gully_prefs", MODE_PRIVATE)
            .edit()
            .putString("chat_sender_name", newName)
            .putString("chat_sender_profile_pic", picToSave)
            .apply()

        tvLeagueSubtitle.text = "League: $leagueId • As: $senderName"
        registerActiveChatUser()

        activeChatUsersMap[senderId] = newName
        if (picToSave.isNotBlank()) {
            userProfilePicsMap[senderId] = picToSave
            chatAdapter.updateUserProfilePics(userProfilePicsMap)
        }

        val allNames = mutableSetOf<String>()
        allNames.addAll(activeChatUsersMap.values)
        for (p in allLeaguePlayersList) {
            if (p.name.isNotBlank()) allNames.add(p.name.trim())
        }
        chatAdapter.updateKnownPlayerNames(allNames.toList())
        checkAndFilterMentions()

        if (newName != oldName) {
            sendSystemMessage("$oldName changed name to $newName")
        }

        Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
        dialog.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::leagueId.isInitialized && activeLeagueId == leagueId) {
            activeLeagueId = null
        }
        chatListener?.remove()
    }
}

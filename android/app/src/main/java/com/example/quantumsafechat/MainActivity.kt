package com.example.quantumsafechat

import android.app.Activity
import android.os.Bundle
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.ArrayDeque

// ============================================================
// QUANTUMSAFE / CYBERPEACE PALETTE
// ============================================================

private val Background: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.background
private val White: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.surface
private val Navy: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.navy
private val DeepBlue: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.deepBlue
private val CyberPeaceBlue: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.cyberPeaceBlue
private val SoftBlueAccent: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.softBlueAccent
private val SoftBlue: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.softBlue
private val SecurityBackground: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.securityBackground
private val OutgoingBubble: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.outgoingBubble
private val TextDark: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.textPrimary
private val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.textSecondary
private val DividerColor: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.divider
private val Success: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.success
private val Danger: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.danger
private val Action: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.action
private val OnAction: Color
    @Composable @ReadOnlyComposable get() = LocalQuantumSafePalette.current.onAction

@Composable
fun QuantumSafeTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkQuantumSafePalette else LightQuantumSafePalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.deepBlue,
            onPrimary = Color(0xFF07161F),
            secondary = palette.cyberPeaceBlue,
            onSecondary = Color(0xFF07161F),
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.softBlue,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider
        )
    } else {
        lightColorScheme(
            primary = palette.deepBlue,
            onPrimary = Color(0xFFFFFFFF),
            secondary = palette.cyberPeaceBlue,
            onSecondary = Color(0xFFFFFFFF),
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.softBlue,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.softBlue
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.background.toArgb()
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalQuantumSafePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

// ============================================================
// DATA MODELS
// ============================================================

enum class MessageStatus {
    PENDING,
    SERVER_RECEIVED,
    DELIVERED,
    FAILED
}

data class Chat(
    val name: String,
    val message: String,
    val time: String,
    val initial: String,
    val about: String = "Secure messaging peer"
)

data class ChatMessage(
    val id: String,
    val text: String,
    val time: String,
    val fromMe: Boolean,
    val status: MessageStatus = MessageStatus.DELIVERED
)

private val contacts = listOf(
    Chat("Shriyaa", "Hey Angad! How are you?", "10:42 AM", "S", "QuantumSafe contact"),
    Chat("Rahul", "Let's discuss the project.", "Yesterday", "R", "Project collaborator"),
    Chat("Ananya", "See you tomorrow!", "Monday", "A", "Secure messaging peer"),
    Chat("Project Team", "The experiment is ready.", "Sunday", "P", "QuantumSafe project group")
)

// ============================================================
// MAIN ACTIVITY
// ============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuantumSafeApp()
        }
    }
}

// ============================================================
// APP STATE
// ============================================================

@Composable
fun QuantumSafeApp() {
    val context = LocalContext.current
    val identityStore = remember { QuantumSafeIdentityStore(context.applicationContext) }
    var account by remember { mutableStateOf(identityStore.load()) }
    var darkMode by remember { mutableStateOf(identityStore.isDarkMode()) }

    QuantumSafeTheme(darkTheme = darkMode) {
        if (account == null) {
            QuantumSafeOnboardingScreen(
                onAccountCreated = { created ->
                    account = created
                }
            )
            return@QuantumSafeTheme
        }

        QuantumSafeMainApp(
            currentAccount = account!!,
            darkMode = darkMode,
            onDarkModeChange = { enabled ->
                identityStore.setDarkMode(enabled)
                darkMode = enabled
            }
        )
    }
}

@Composable
private fun QuantumSafeMainApp(
    currentAccount: QuantumSafeAccount,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("quantumsafe_chat_state_" + currentAccount.accountId, android.content.Context.MODE_PRIVATE) }
    val database = remember { QuantumSafeDatabase(context.applicationContext) }

    val selectedChatState = remember { mutableStateOf<Chat?>(null) }
    var selectedChat by selectedChatState
    var profileChat by remember { mutableStateOf<Chat?>(null) }
    var showNewChat by remember { mutableStateOf(false) }

    var isConnected by remember { mutableStateOf(false) }
    var isSecure by remember { mutableStateOf(false) }
    var networkStatus by remember { mutableStateOf("Connecting...") }

    val conversations = remember {
        mutableStateMapOf<String, SnapshotStateList<ChatMessage>>().apply {
            put(
                "Shriyaa",
                mutableStateListOf(
                    ChatMessage("seed-1", "Hey Angad! How are you?", "10:41 AM", false),
                    ChatMessage("seed-2", "Hey Shriyaa! I'm good. Working on QuantumSafe.", "10:42 AM", true, MessageStatus.DELIVERED),
                    ChatMessage("seed-3", "That's great! How is the project going?", "10:43 AM", false),
                    ChatMessage("seed-4", "Really well. The secure messaging prototype is working.", "10:44 AM", true, MessageStatus.DELIVERED)
                )
            )
            put("Rahul", mutableStateListOf())
            put("Ananya", mutableStateListOf())
            put("Project Team", mutableStateListOf())
        }
    }

    val unreadCounts = remember {
        mutableStateMapOf<String, Int>().apply {
            put("Shriyaa", 0)
            put("Rahul", 2)
            put("Ananya", 1)
            put("Project Team", 0)
        }
    }

    val pinnedChats = remember { mutableStateListOf<String>() }
    val mutedChats = remember { mutableStateListOf<String>() }
    val archivedChats = remember { mutableStateListOf<String>() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    // Message IDs are session-specific. If a message reached the server but
    // the recipient changed/re-established a PQC session before decrypting it,
    // the old ciphertext cannot be decrypted with the new session key. We keep
    // the plaintext at the endpoint and transparently re-encrypt/re-send it on
    // the next secure session, while reusing the same UI bubble.
    val retryQueue = remember { mutableMapOf<String, ArrayDeque<String>>() }

    // Restore chat history after an app restart/reload. The previous version
    // kept conversations only in Compose memory, so Android Studio reloads
    // returned the app to the demo state.
    LaunchedEffect(Unit) {
        val loaded = database.loadState(
            conversations,
            unreadCounts,
            pinnedChats,
            mutedChats,
            archivedChats
        )

        // One-time migration: preserve the history from the previous
        // SharedPreferences version and move it into the real SQLite DB.
        if (!loaded) {
            loadPersistedState(
                prefs,
                conversations,
                unreadCounts,
                pinnedChats,
                mutedChats,
                archivedChats
            )
            database.saveState(
                conversations,
                unreadCounts,
                pinnedChats,
                mutedChats,
                archivedChats
            )
        }
    }

    fun persist() {
        database.saveState(
            conversations,
            unreadCounts,
            pinnedChats,
            mutedChats,
            archivedChats
        )
        // Keep the old store as a compatibility backup during development.
        savePersistedState(
            prefs,
            conversations,
            unreadCounts,
            pinnedChats,
            mutedChats,
            archivedChats
        )
    }

    // One transport connection per account. Do not recreate it when the
    // open chat changes — that used to drop every other PQC session.
    val network = remember(currentAccount.accountId) {
        QuantumSafeNetwork(
            username = currentAccount.displayName,
            accountId = currentAccount.accountId,
            deviceId = currentAccount.deviceId,
            phoneNumber = currentAccount.phoneNumber,
            displayName = currentAccount.displayName,
            recipient = "Shriyaa",
            knownRecipients = contacts.map { it.name },
            listener = object : QuantumSafeNetwork.Listener {
                override fun onConnectionChanged(connected: Boolean) {
                    handler.post {
                        isConnected = connected
                        networkStatus = if (connected) "Connected" else "Offline"
                        if (!connected) isSecure = false
                    }
                }

                override fun onSecurityChanged(secure: Boolean) {
                    handler.post {
                        if (selectedChatState.value == null) {
                            isSecure = secure
                        }
                    }
                }

                override fun onPeerSecurityChanged(peer: String, secure: Boolean) {
                    handler.post {
                        if (selectedChatState.value?.name == peer) {
                            isSecure = secure
                            networkStatus = if (secure) {
                                "Quantum-safe session"
                            } else if (isConnected) {
                                "Establishing secure session"
                            } else {
                                "Offline"
                            }
                        }
                    }
                }

                override fun onMessageReceived(message: String, messageId: String) {
                    // Incoming chat is delivered via onMessageReceivedFrom.
                }

                override fun onMessageReceivedFrom(sender: String, message: String, messageId: String) {
                    handler.post {
                        val list = conversations.getOrPut(sender) { mutableStateListOf() }
                        list.add(
                            ChatMessage(
                                id = messageId,
                                text = message,
                                time = currentTime(),
                                fromMe = false,
                                status = MessageStatus.DELIVERED
                            )
                        )

                        if (selectedChatState.value?.name != sender) {
                            unreadCounts[sender] = (unreadCounts[sender] ?: 0) + 1
                        }
                        persist()
                    }
                }

                override fun onMessageSent(message: String, messageId: String) {
                    // Outgoing chat is delivered via onMessageSentTo.
                }

                override fun onMessageSentTo(recipient: String, message: String, messageId: String) {
                    handler.post {
                        val list = conversations.getOrPut(recipient) { mutableStateListOf() }
                        val queue = retryQueue[recipient]

                        // If this send is a recovery of an older undelivered
                        // bubble, replace its message ID instead of creating a
                        // duplicate bubble in the chat.
                        val oldId = if (queue != null && queue.isNotEmpty()) queue.removeFirst() else null
                        val oldIndex = oldId?.let { id -> list.indexOfFirst { it.id == id } } ?: -1

                        if (oldIndex >= 0) {
                            list[oldIndex] = list[oldIndex].copy(
                                id = messageId,
                                time = currentTime(),
                                status = MessageStatus.PENDING
                            )
                        } else if (list.none { it.id == messageId }) {
                            list.add(
                                ChatMessage(
                                    id = messageId,
                                    text = message,
                                    time = currentTime(),
                                    fromMe = true,
                                    status = MessageStatus.PENDING
                                )
                            )
                        }
                        persist()
                    }
                }

                override fun onError(error: String) {
                    handler.post { networkStatus = error }
                }

                override fun onLog(message: String) {
                    handler.post {
                        when {
                            message.startsWith("Message ACK: ") -> {
                                val id = message.removePrefix("Message ACK: ").trim()
                                updateMessageStatus(conversations, id, MessageStatus.SERVER_RECEIVED)
                                persist()
                            }
                            message.startsWith("Delivery ACK: ") -> {
                                val id = message.removePrefix("Delivery ACK: ").trim()
                                updateMessageStatus(conversations, id, MessageStatus.DELIVERED)
                                persist()
                            }
                        }
                    }
                }
            }
        )
    }

    DisposableEffect(network) {
        network.connect()
        onDispose { network.disconnect() }
    }

    LaunchedEffect(selectedChat?.name, isConnected) {
        val peer = selectedChat?.name ?: return@LaunchedEffect
        if (isConnected) {
            network.prepareRecipient(peer)
        }
        isSecure = network.isSecure(peer)
        networkStatus = when {
            network.isSecure(peer) -> "Quantum-safe session"
            isConnected -> "Establishing secure session"
            else -> "Offline"
        }
    }

    // IMPORTANT reliability fix: when a new PQC session is established,
    // re-send messages that previously received only the server ACK.
    // Those old encrypted packets may have been created under the previous
    // session key and therefore cannot be decrypted after a new handshake.
    LaunchedEffect(network, isSecure, selectedChat?.name) {
        val peer = selectedChat?.name ?: return@LaunchedEffect
        if (!isSecure || !network.isSecure(peer)) return@LaunchedEffect

        val list = conversations[peer] ?: return@LaunchedEffect
        val candidates = list
            .filter {
                it.fromMe &&
                        it.status != MessageStatus.DELIVERED &&
                        !it.id.startsWith("seed-")
            }
            .toList()

        if (candidates.isEmpty()) return@LaunchedEffect

        val queue = retryQueue.getOrPut(peer) { ArrayDeque() }
        candidates.forEach { message ->
            if (!queue.contains(message.id)) {
                queue.addLast(message.id)
                network.sendMessageTo(peer, message.text)
            }
        }
    }

    if (selectedChat != null) {
        val chat = selectedChat!!
        val messages = conversations.getOrPut(chat.name) { mutableStateListOf() }

        unreadCounts[chat.name] = 0
        persist()

        BackHandler(enabled = true) {
            selectedChat = null
        }

        ChatScreen(
            chat = chat,
            messages = messages,
            isConnected = isConnected,
            isSecure = isSecure,
            networkStatus = networkStatus,
            onBack = { selectedChat = null },
            onProfile = { profileChat = chat },
            onSend = { text -> network.sendMessageTo(chat.name, text) },
            onClearChat = {
                messages.clear()
                persist()
                profileChat = null
            }
        )
    } else {
        HomeScreen(
            currentAccount = currentAccount,
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            conversations = conversations,
            unreadCounts = unreadCounts,
            pinnedChats = pinnedChats,
            mutedChats = mutedChats,
            archivedChats = archivedChats,
            onChatSelected = {
                unreadCounts[it.name] = 0
                selectedChat = it
                persist()
            },
            onProfile = { profileChat = it },
            onNewChat = { showNewChat = true }
        )
    }

    if (profileChat != null) {
        ContactProfileDialog(
            chat = profileChat!!,
            isPinned = pinnedChats.contains(profileChat!!.name),
            isMuted = mutedChats.contains(profileChat!!.name),
            onTogglePin = {
                val name = profileChat!!.name
                if (pinnedChats.contains(name)) pinnedChats.remove(name) else pinnedChats.add(name)
                persist()
            },
            onToggleMute = {
                val name = profileChat!!.name
                if (mutedChats.contains(name)) mutedChats.remove(name) else mutedChats.add(name)
                persist()
            },
            onClear = {
                conversations[profileChat!!.name]?.clear()
                persist()
                profileChat = null
            },
            onDismiss = { profileChat = null }
        )
    }

    if (showNewChat) {
        NewChatDialog(
            onSelect = {
                showNewChat = false
                selectedChat = it
                unreadCounts[it.name] = 0
                persist()
            },
            onDismiss = { showNewChat = false }
        )
    }
}

private fun savePersistedState(
    prefs: android.content.SharedPreferences,
    conversations: MutableMap<String, SnapshotStateList<ChatMessage>>,
    unreadCounts: MutableMap<String, Int>,
    pinnedChats: SnapshotStateList<String>,
    mutedChats: SnapshotStateList<String>,
    archivedChats: SnapshotStateList<String>
) {
    val root = JSONObject()
    val chatsJson = JSONObject()

    conversations.forEach { (name, messages) ->
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject().apply {
                    put("id", message.id)
                    put("text", message.text)
                    put("time", message.time)
                    put("fromMe", message.fromMe)
                    put("status", message.status.name)
                }
            )
        }
        chatsJson.put(name, array)
    }

    val unreadJson = JSONObject()
    unreadCounts.forEach { (name, count) -> unreadJson.put(name, count) }

    root.put("conversations", chatsJson)
    root.put("unread", unreadJson)
    root.put("pinned", JSONArray(pinnedChats))
    root.put("muted", JSONArray(mutedChats))
    root.put("archived", JSONArray(archivedChats))

    prefs.edit().putString("state", root.toString()).apply()
}

private fun loadPersistedState(
    prefs: android.content.SharedPreferences,
    conversations: MutableMap<String, SnapshotStateList<ChatMessage>>,
    unreadCounts: MutableMap<String, Int>,
    pinnedChats: SnapshotStateList<String>,
    mutedChats: SnapshotStateList<String>,
    archivedChats: SnapshotStateList<String>
) {
    val raw = prefs.getString("state", null) ?: return

    try {
        val root = JSONObject(raw)
        val chatsJson = root.optJSONObject("conversations") ?: return

        chatsJson.keys().forEach { name ->
            val array = chatsJson.optJSONArray(name) ?: return@forEach
            val list = mutableStateListOf<ChatMessage>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val status = runCatching {
                    MessageStatus.valueOf(item.optString("status", MessageStatus.DELIVERED.name))
                }.getOrDefault(MessageStatus.DELIVERED)

                list.add(
                    ChatMessage(
                        id = item.optString("id"),
                        text = item.optString("text"),
                        time = item.optString("time"),
                        fromMe = item.optBoolean("fromMe"),
                        status = status
                    )
                )
            }
            conversations[name] = list
        }

        val unreadJson = root.optJSONObject("unread")
        unreadJson?.keys()?.forEach { name ->
            unreadCounts[name] = unreadJson.optInt(name, 0)
        }

        pinnedChats.clear()
        mutedChats.clear()
        archivedChats.clear()

        root.optJSONArray("pinned")?.let { array ->
            for (i in 0 until array.length()) pinnedChats.add(array.optString(i))
        }
        root.optJSONArray("muted")?.let { array ->
            for (i in 0 until array.length()) mutedChats.add(array.optString(i))
        }
        root.optJSONArray("archived")?.let { array ->
            for (i in 0 until array.length()) archivedChats.add(array.optString(i))
        }
    } catch (_: Exception) {
        // If an old/corrupt saved state exists, retain the built-in demo state.
    }
}

private fun currentTime(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

private fun updateMessageStatus(
    conversations: MutableMap<String, SnapshotStateList<ChatMessage>>,
    messageId: String,
    status: MessageStatus
) {
    conversations.values.forEach { list ->
        val index = list.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            val current = list[index].status
            // ACK packets can arrive out of order. Never downgrade a message
            // that is already known to be delivered.
            val finalStatus = when {
                current == MessageStatus.DELIVERED -> MessageStatus.DELIVERED
                status == MessageStatus.DELIVERED -> MessageStatus.DELIVERED
                else -> status
            }
            list[index] = list[index].copy(status = finalStatus)
        }
    }
}

// ============================================================
// HOME
// ============================================================

@Composable
fun HomeScreen(
    currentAccount: QuantumSafeAccount,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    conversations: MutableMap<String, SnapshotStateList<ChatMessage>>,
    unreadCounts: MutableMap<String, Int>,
    pinnedChats: SnapshotStateList<String>,
    mutedChats: SnapshotStateList<String>,
    archivedChats: SnapshotStateList<String>,
    onChatSelected: (Chat) -> Unit,
    onProfile: (Chat) -> Unit,
    onNewChat: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchText by remember { mutableStateOf("") }

    val visibleChats = remember(searchText, archivedChats.toList()) {
        contacts.filter { chat ->
            !archivedChats.contains(chat.name) &&
                (chat.name.contains(searchText, true) || chat.message.contains(searchText, true))
        }
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(
                containerColor = White,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(painterResource(R.drawable.ic_chat), contentDescription = "Chats", modifier = Modifier.size(22.dp)) },
                    label = { Text("Chats", fontSize = 10.sp) },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(painterResource(R.drawable.ic_person), contentDescription = "Contacts", modifier = Modifier.size(22.dp)) },
                    label = { Text("Contacts", fontSize = 10.sp) },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", modifier = Modifier.size(22.dp)) },
                    label = { Text("Settings", fontSize = 10.sp) },
                    colors = navColors()
                )
            }
        },
        floatingActionButton = {
            if (selectedTab != 2) {
                FloatingActionButton(
                    onClick = onNewChat,
                    containerColor = Action,
                    contentColor = OnAction,
                    shape = CircleShape
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "New chat", tint = OnAction)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("QuantumSafe", color = Navy, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text("by CyberPeace", color = CyberPeaceBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Image(
                    painter = painterResource(R.drawable.cyberpeace_mark),
                    contentDescription = "CyberPeace",
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                )
            }

            Spacer(Modifier.height(16.dp))

            SearchField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = if (selectedTab == 0) "Search conversations" else "Search contacts"
            )

            Spacer(Modifier.height(15.dp))

            when (selectedTab) {
                0 -> {
                    Text(
                        "Chats",
                        color = TextDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    ChatList(
                        chats = visibleChats,
                        conversations = conversations,
                        unreadCounts = unreadCounts,
                        pinnedChats = pinnedChats,
                        mutedChats = mutedChats,
                        onChatSelected = onChatSelected,
                        onProfile = onProfile
                    )
                }
                1 -> ContactList(
                    searchText = searchText,
                    onContactSelected = onChatSelected,
                    onProfile = onProfile
                )
                else -> SettingsScreen(
                    currentAccount = currentAccount,
                    darkMode = darkMode,
                    onDarkModeChange = onDarkModeChange
                )
            }
        }
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        placeholder = { Text(placeholder, color = TextSecondary, fontSize = 13.sp) },
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = White,
            unfocusedContainerColor = White,
            focusedTextColor = TextDark,
            unfocusedTextColor = TextDark,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = CyberPeaceBlue
        )
    )
}

@Composable
fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = DeepBlue,
    selectedTextColor = DeepBlue,
    indicatorColor = SoftBlue,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary
)

// ============================================================
// CHAT LIST
// ============================================================

@Composable
fun ChatList(
    chats: List<Chat>,
    conversations: MutableMap<String, SnapshotStateList<ChatMessage>>,
    unreadCounts: MutableMap<String, Int>,
    pinnedChats: SnapshotStateList<String>,
    mutedChats: SnapshotStateList<String>,
    onChatSelected: (Chat) -> Unit,
    onProfile: (Chat) -> Unit
) {
    val sorted = chats.sortedWith(
        compareByDescending<Chat> { pinnedChats.contains(it.name) }
            .thenByDescending { conversations[it.name]?.lastIndex ?: -1 }
            .thenBy { it.name }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(sorted, key = { it.name }) { chat ->
            val latest = conversations[chat.name]?.lastOrNull()
            val preview = latest?.text ?: chat.message
            val time = latest?.time ?: chat.time
            val unread = unreadCounts[chat.name] ?: 0

            ChatRow(
                chat = chat,
                previewText = preview,
                previewTime = time,
                unread = unread,
                pinned = pinnedChats.contains(chat.name),
                muted = mutedChats.contains(chat.name),
                latestMessage = latest,
                onClick = { onChatSelected(chat) },
                onProfile = { onProfile(chat) }
            )
        }
    }
}

@Composable
fun ChatRow(
    chat: Chat,
    previewText: String,
    previewTime: String,
    unread: Int,
    pinned: Boolean,
    muted: Boolean,
    latestMessage: ChatMessage?,
    onClick: () -> Unit,
    onProfile: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(initial = chat.initial, size = 54.dp, onClick = onProfile)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.name, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (pinned) {
                    Spacer(Modifier.width(5.dp))
                    Icon(painterResource(R.drawable.ic_push_pin), contentDescription = "Pinned", tint = TextSecondary, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (latestMessage?.fromMe == true) {
                    MessageStatusMark(latestMessage.status)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    previewText,
                    color = if (unread > 0) TextDark else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                previewTime,
                color = if (unread > 0) CyberPeaceBlue else TextSecondary,
                fontSize = 9.sp,
                fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (muted) {
                    Icon(painterResource(R.drawable.ic_notifications_off), contentDescription = "Muted", tint = TextSecondary, modifier = Modifier.size(12.dp))
                }
                if (unread > 0) {
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(CyberPeaceBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (unread > 99) "99+" else unread.toString(), color = White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================
// CONTACTS
// ============================================================

@Composable
fun ContactList(
    searchText: String,
    onContactSelected: (Chat) -> Unit,
    onProfile: (Chat) -> Unit
) {
    val filtered = contacts.filter { it.name.contains(searchText, true) || it.about.contains(searchText, true) }

    Column(Modifier.fillMaxSize()) {
        Text("Secure Contacts", color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Every chat uses a quantum-safe session.", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(9.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(filtered, key = { it.name }) { chat ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onContactSelected(chat) }.padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(initial = chat.initial, size = 52.dp, onClick = { onProfile(chat) })
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.name, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(chat.about, color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = CyberPeaceBlue, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Quantum-safe session", color = CyberPeaceBlue, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// CHAT SCREEN
// ============================================================

@Composable
fun ChatScreen(
    chat: Chat,
    messages: SnapshotStateList<ChatMessage>,
    isConnected: Boolean,
    isSecure: Boolean,
    networkStatus: String,
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onSend: (String) -> Unit,
    onClearChat: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val sendEnabled = messageText.isNotBlank() && isSecure

    androidx.compose.runtime.LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(containerColor = Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ChatHeader(
                chat = chat,
                isSecure = isSecure,
                networkStatus = networkStatus,
                onBack = onBack,
                onProfile = onProfile,
                onMenu = { showMenu = !showMenu }
            )

            if (showMenu) {
                ChatMenu(
                    onProfile = { showMenu = false; onProfile() },
                    onClear = { showMenu = false; showClearConfirm = true },
                    onDismiss = { showMenu = false }
                )
            }

            SecurityStrip(isSecure = isSecure, isConnected = isConnected)

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 13.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                item { DateSeparator("Today") }
                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    MessageBubble(message)
                    if (index == 0 && messages.size > 6) {
                        Spacer(Modifier.height(2.dp))
                    }
                }
                item { Spacer(Modifier.height(7.dp)) }
            }

            Composer(
                messageText = messageText,
                onMessageChange = { messageText = it },
                sendEnabled = sendEnabled,
                onSend = {
                    val text = messageText.trim()
                    if (text.isNotEmpty() && sendEnabled) {
                        onSend(text)
                        messageText = ""
                    }
                }
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = White,
            titleContentColor = TextDark,
            textContentColor = TextDark,
            title = { Text("Clear chat") },
            text = { Text("Delete the local conversation history for ${chat.name}?") },
            confirmButton = {
                TextButton(onClick = { onClearChat(); showClearConfirm = false }) {
                    Text("Clear", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ChatHeader(
    chat: Chat,
    isSecure: Boolean,
    networkStatus: String,
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 7.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_arrow_back),
            contentDescription = "Back",
            tint = Navy,
            modifier = Modifier.clickable { onBack() }.padding(horizontal = 7.dp).size(22.dp)
        )
        Avatar(initial = chat.initial, size = 43.dp, onClick = onProfile)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { onProfile() }) {
            Text(chat.name, color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(7.dp).clip(CircleShape).background(if (isSecure) Success else TextSecondary)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isSecure) "secure session" else networkStatus,
                    color = if (isSecure) Success else TextSecondary,
                    fontSize = 9.sp
                )
            }
        }
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = "Encrypted",
            tint = if (isSecure) Success else TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            painterResource(R.drawable.ic_more_vert),
            contentDescription = "Menu",
            tint = Navy,
            modifier = Modifier.clickable { onMenu() }.size(22.dp)
        )
    }
}

@Composable
fun SecurityStrip(isSecure: Boolean, isConnected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().background(if (isSecure) SecurityBackground else SoftBlueAccent).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(if (isSecure) R.drawable.ic_lock else R.drawable.ic_security),
            contentDescription = null,
            tint = if (isSecure) Success else TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isSecure) "Quantum-safe encrypted session" else "Secure session not ready",
                color = if (isSecure) Navy else TextDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (isSecure) "ML-KEM-768 • HKDF-SHA256 • AES-256-GCM" else if (isConnected) "Establishing protected session…" else "Waiting for server connection…",
                color = TextSecondary,
                fontSize = 8.sp
            )
        }
        Text(if (isSecure) "LOCKED" else "WAIT", color = if (isSecure) Success else TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DateSeparator(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = SoftBlueAccent) {
            Text(text, color = TextSecondary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

@Composable
fun ChatMenu(onProfile: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onProfile) { Text("Contact", color = Navy, fontSize = 11.sp) }
        TextButton(onClick = onClear) { Text("Clear", color = Danger, fontSize = 11.sp) }
        TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary, fontSize = 11.sp) }
    }
}

@Composable
fun Composer(
    messageText: String,
    onMessageChange: (String) -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(White).imePadding().padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = messageText,
            onValueChange = onMessageChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message", color = TextSecondary, fontSize = 13.sp) },
            singleLine = false,
            maxLines = 4,
            shape = RoundedCornerShape(22.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Background,
                unfocusedContainerColor = Background,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = CyberPeaceBlue
            )
        )
        Spacer(Modifier.width(7.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (sendEnabled) Action else Action.copy(alpha = 0.38f))
                .clickable(enabled = sendEnabled) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_send),
                contentDescription = "Send",
                tint = OnAction,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ============================================================
// MESSAGE BUBBLES + REAL TICK STATES
// ============================================================

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.80f),
            shape = RoundedCornerShape(
                topStart = 17.dp,
                topEnd = 17.dp,
                bottomStart = if (message.fromMe) 17.dp else 4.dp,
                bottomEnd = if (message.fromMe) 4.dp else 17.dp
            ),
            color = if (message.fromMe) OutgoingBubble else White
        ) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                Text(message.text, color = TextDark, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text(message.time, color = TextSecondary, fontSize = 9.sp)
                    if (message.fromMe) {
                        Spacer(Modifier.width(5.dp))
                        MessageStatusMark(message.status)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusMark(status: MessageStatus) {
    when (status) {
        MessageStatus.PENDING -> Text("◷", color = TextSecondary, fontSize = 10.sp)
        MessageStatus.SERVER_RECEIVED -> Text("✓", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        MessageStatus.DELIVERED -> Text("✓✓", color = CyberPeaceBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        MessageStatus.FAILED -> Text("!", color = Danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ============================================================
// CONTACT PROFILE
// ============================================================

@Composable
fun ContactProfileDialog(
    chat: Chat,
    isPinned: Boolean,
    isMuted: Boolean,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        titleContentColor = TextDark,
        textContentColor = TextDark,
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Avatar(initial = chat.initial, size = 76.dp, onClick = {})
                Spacer(Modifier.height(10.dp))
                Text(chat.name, color = Navy, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(chat.about, color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(13.dp))
                SecurityCard()
                Spacer(Modifier.height(9.dp))
                ProfileAction(R.drawable.ic_push_pin, if (isPinned) "Unpin chat" else "Pin chat", onTogglePin)
                ProfileAction(R.drawable.ic_notifications_off, if (isMuted) "Unmute notifications" else "Mute notifications", onToggleMute)
                ProfileAction(R.drawable.ic_delete, "Clear chat", onClear)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = CyberPeaceBlue) } }
    )
}

@Composable
fun SecurityCard() {
    Surface(shape = RoundedCornerShape(15.dp), color = SecurityBackground) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = Navy, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Quantum-safe connection", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text("ML-KEM-768 key establishment", color = TextSecondary, fontSize = 9.sp)
            Text("AES-256-GCM message encryption", color = TextSecondary, fontSize = 9.sp)
            Text("HKDF-SHA256 session derivation", color = TextSecondary, fontSize = 9.sp)
        }
    }
}

@Composable
fun ProfileAction(icon: Int, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Navy, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(11.dp))
        Text(title, color = TextDark, fontSize = 12.sp)
    }
}

// ============================================================
// NEW CHAT
// ============================================================

@Composable
fun NewChatDialog(onSelect: (Chat) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = contacts.filter { it.name.contains(search, true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        titleContentColor = TextDark,
        textContentColor = TextDark,
        title = { Text("New chat", color = Navy, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                SearchField(value = search, onValueChange = { search = it }, placeholder = "Search contacts")
                Spacer(Modifier.height(9.dp))
                filtered.forEach { chat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(chat) }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(chat.initial, 42.dp, {})
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chat.name, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Start quantum-safe chat", color = TextSecondary, fontSize = 9.sp)
                        }
                        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = Navy, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

// ============================================================
// SETTINGS
// ============================================================

@Composable
fun SettingsScreen(
    currentAccount: QuantumSafeAccount,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    var notifications by remember { mutableStateOf(true) }
    var readReceipts by remember { mutableStateOf(true) }
    var securityDetails by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("QuantumSafe controls", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(11.dp))

        Surface(shape = RoundedCornerShape(18.dp), color = White) {
            Column(Modifier.fillMaxWidth()) {
                ToggleRow(R.drawable.ic_dark_mode, "Dark mode", "Use the CyberPeace night theme", darkMode, onDarkModeChange)
                ToggleRow(R.drawable.ic_notifications, "Notifications", "Chat notification preference", notifications) { notifications = it }
                ToggleRow(R.drawable.ic_done_all, "Read receipts", "Show message delivery state", readReceipts) { readReceipts = it }
                ToggleRow(R.drawable.ic_lock, "Show security details", "Display cryptographic session details", securityDetails) { securityDetails = it }
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(shape = RoundedCornerShape(18.dp), color = White) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(QuantumSafeIdentityStore.displayInitial(currentAccount.displayName), 52.dp, {})
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(currentAccount.displayName, color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(currentAccount.phoneNumber, color = TextSecondary, fontSize = 10.sp)
                    }
                    Image(
                        painter = painterResource(R.drawable.cyberpeace_mark),
                        contentDescription = "CyberPeace",
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                    )
                }
                Spacer(Modifier.height(13.dp))
                Divider(color = DividerColor)
                Spacer(Modifier.height(6.dp))
                AccountIdentitySummary(currentAccount)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Demo verification is for prototype accounts only. It is not production authentication.",
                    color = TextSecondary,
                    fontSize = 9.sp
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(R.drawable.ic_shield, "Privacy", "Messages stay encrypted on this device")
                SettingsRow(R.drawable.ic_security, "Security", "ML-KEM-768 + AES-256-GCM")
                SettingsRow(R.drawable.ic_cloud, "Data", "Local conversation state")
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(shape = RoundedCornerShape(18.dp), color = SecurityBackground) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Text("SECURITY STATUS", color = Navy, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Post-quantum secure architecture", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text("ML-KEM-768 → HKDF-SHA256 → AES-256-GCM", color = CyberPeaceBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text("Replay protection and authenticated metadata are enforced by the existing protocol.", color = TextSecondary, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun SettingsRow(icon: Int, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = Navy, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 9.sp)
        }
    }
}

@Composable
fun ToggleRow(icon: Int, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Navy, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 9.sp)
        }
        Box(
            modifier = Modifier.size(40.dp, 23.dp).clip(RoundedCornerShape(15.dp)).background(if (checked) CyberPeaceBlue else SoftBlue),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .then(if (checked) Modifier.padding(start = 18.dp) else Modifier.padding(start = 2.dp))
            )
        }
    }
}

// ============================================================
// SHARED UI
// ============================================================

@Composable
fun Avatar(initial: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(SoftBlueAccent).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = CyberPeaceBlue, fontSize = (size.value / 2.6f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmptySection(title: String, subtitle: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(65.dp).clip(CircleShape).background(SoftBlueAccent), contentAlignment = Alignment.Center) {
            Text("•", color = CyberPeaceBlue, fontSize = 28.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = TextSecondary, fontSize = 12.sp)
    }
}


// ============================================================
// LOCAL QUANTUMSAFE DATABASE
// ============================================================

class QuantumSafeDatabase(context: android.content.Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                chat_name TEXT NOT NULL,
                text TEXT NOT NULL,
                time TEXT NOT NULL,
                from_me INTEGER NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE chat_state (
                chat_name TEXT PRIMARY KEY,
                unread_count INTEGER NOT NULL DEFAULT 0,
                pinned INTEGER NOT NULL DEFAULT 0,
                muted INTEGER NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX idx_messages_chat_time ON messages(chat_name, created_at)"
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_messages_chat_time ON messages(chat_name, created_at)"
            )
        }
    }

    fun hasData(): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM messages LIMIT 1",
            null
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun loadState(
        conversations: MutableMap<String, SnapshotStateList<ChatMessage>>,
        unreadCounts: MutableMap<String, Int>,
        pinnedChats: SnapshotStateList<String>,
        mutedChats: SnapshotStateList<String>,
        archivedChats: SnapshotStateList<String>
    ): Boolean {
        if (!hasData()) return false

        readableDatabase.rawQuery(
            """
            SELECT id, chat_name, text, time, from_me, status
            FROM messages
            ORDER BY created_at ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val chatName = cursor.getString(1)
                val text = cursor.getString(2)
                val time = cursor.getString(3)
                val fromMe = cursor.getInt(4) == 1
                val status = runCatching {
                    MessageStatus.valueOf(cursor.getString(5))
                }.getOrDefault(MessageStatus.DELIVERED)

                val list = conversations.getOrPut(chatName) {
                    mutableStateListOf()
                }

                if (list.none { it.id == id }) {
                    list.add(
                        ChatMessage(
                            id = id,
                            text = text,
                            time = time,
                            fromMe = fromMe,
                            status = status
                        )
                    )
                }
            }
        }

        readableDatabase.rawQuery(
            """
            SELECT chat_name, unread_count, pinned, muted, archived
            FROM chat_state
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                unreadCounts[name] = cursor.getInt(1)

                if (cursor.getInt(2) == 1 && !pinnedChats.contains(name)) {
                    pinnedChats.add(name)
                }
                if (cursor.getInt(3) == 1 && !mutedChats.contains(name)) {
                    mutedChats.add(name)
                }
                if (cursor.getInt(4) == 1 && !archivedChats.contains(name)) {
                    archivedChats.add(name)
                }
            }
        }

        return true
    }

    fun saveState(
        conversations: MutableMap<String, SnapshotStateList<ChatMessage>>,
        unreadCounts: MutableMap<String, Int>,
        pinnedChats: SnapshotStateList<String>,
        mutedChats: SnapshotStateList<String>,
        archivedChats: SnapshotStateList<String>
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("messages", null, null)
            db.delete("chat_state", null, null)

            var sequence = System.currentTimeMillis()

            conversations.forEach { (chatName, messages) ->
                messages.forEach { message ->
                    val values = ContentValues().apply {
                        put("id", message.id)
                        put("chat_name", chatName)
                        put("text", message.text)
                        put("time", message.time)
                        put("from_me", if (message.fromMe) 1 else 0)
                        put("status", message.status.name)
                        put("created_at", sequence++)
                    }
                    db.insertOrThrow("messages", null, values)
                }
            }

            val names = mutableSetOf<String>()
            names.addAll(conversations.keys)
            names.addAll(unreadCounts.keys)
            names.addAll(pinnedChats)
            names.addAll(mutedChats)
            names.addAll(archivedChats)

            names.forEach { name ->
                val values = ContentValues().apply {
                    put("chat_name", name)
                    put("unread_count", unreadCounts[name] ?: 0)
                    put("pinned", if (pinnedChats.contains(name)) 1 else 0)
                    put("muted", if (mutedChats.contains(name)) 1 else 0)
                    put("archived", if (archivedChats.contains(name)) 1 else 0)
                }
                db.insertOrThrow("chat_state", null, values)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        private const val DATABASE_NAME = "quantumsafe_chat.db"
        private const val DATABASE_VERSION = 2
    }
}

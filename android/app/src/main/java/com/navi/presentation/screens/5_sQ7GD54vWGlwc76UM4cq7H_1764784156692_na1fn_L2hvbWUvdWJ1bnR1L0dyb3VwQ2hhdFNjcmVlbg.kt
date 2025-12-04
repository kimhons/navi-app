package com.example.socialapp.ui.groupchat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// --- Design Constants ---
val NaviBlue = Color(0xFF2563EB)

// --- Data Models ---

data class User(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val badge: String? = null,
    val isOnline: Boolean = false
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val sender: User,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val type: MessageType = MessageType.TEXT,
    val location: Location? = null,
    val photoUrl: String? = null
)

data class Location(
    val latitude: Double,
    val longitude: Double,
    val name: String
)

enum class MessageType {
    TEXT, PHOTO, LOCATION
}

data class ChatState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isTyping: Boolean = false,
    val isRefreshing: Boolean = false,
    val currentUserId: String = "user_123",
    val groupName: String = "The Manus Squad"
)

sealed class ChatEvent {
    data class NewMessage(val message: Message) : ChatEvent()
    data class TypingStatus(val isTyping: Boolean, val userId: String) : ChatEvent()
    data class MessageSent(val message: Message) : ChatEvent()
    data class Error(val message: String) : ChatEvent()
}

// --- API Service (Mocking WebSocket and REST) ---

interface ApiService {
    // Simulates initial message fetch
    suspend fun getInitialMessages(groupId: String, page: Int): List<Message>

    // Simulates sending a message via REST
    suspend fun sendMessage(message: Message): Message

    // Simulates a WebSocket connection for real-time updates
    fun observeRealTimeEvents(groupId: String): Flow<ChatEvent>
}

class MockApiService @Inject constructor() : ApiService {
    private val mockUsers = listOf(
        User("user_123", "Manus AI", "url_1", "Dev", true),
        User("user_456", "Alice", "url_2", "QA", true),
        User("user_789", "Bob", "url_3", "Design", false)
    )

    private val initialMessages = (1..10).map { i ->
        Message(
            id = "msg_$i",
            sender = mockUsers.random(),
            content = "This is message number $i. Hello from the mock API!",
            timestamp = System.currentTimeMillis() - (10 - i) * 60000,
            isRead = true
        )
    }.toMutableList()

    private val eventFlow = MutableSharedFlow<ChatEvent>()

    init {
        // Simulate a continuous stream of real-time events (e.g., from a WebSocket)
        GlobalScope.launch {
            while (true) {
                delay(5000) // Send a new event every 5 seconds
                val randomUser = mockUsers.filter { it.id != "user_123" }.random()
                val event = if (Math.random() < 0.5) {
                    // Simulate typing indicator
                    eventFlow.emit(ChatEvent.TypingStatus(true, randomUser.id))
                    delay(2000)
                    ChatEvent.TypingStatus(false, randomUser.id)
                } else {
                    // Simulate a new message
                    val newMessage = Message(
                        sender = randomUser,
                        content = "Real-time update: ${UUID.randomUUID().toString().take(4)}",
                        timestamp = System.currentTimeMillis()
                    )
                    initialMessages.add(newMessage)
                    ChatEvent.NewMessage(newMessage)
                }
                eventFlow.emit(event)
            }
        }
    }

    override suspend fun getInitialMessages(groupId: String, page: Int): List<Message> {
        delay(1000) // Simulate network delay
        return initialMessages.takeLast(10 * page).sortedBy { it.timestamp }
    }

    override suspend fun sendMessage(message: Message): Message {
        delay(500) // Simulate network delay
        val sentMessage = message.copy(isRead = false)
        initialMessages.add(sentMessage)
        eventFlow.emit(ChatEvent.MessageSent(sentMessage))
        return sentMessage
    }

    override fun observeRealTimeEvents(groupId: String): Flow<ChatEvent> = eventFlow.asSharedFlow()
}

// --- Repository ---

interface ChatRepository {
    fun getChatState(groupId: String): Flow<ChatState>
    suspend fun loadMoreMessages(groupId: String)
    suspend fun sendMessage(groupId: String, content: String, type: MessageType, location: Location? = null, photoUrl: String? = null)
    suspend fun refreshChat(groupId: String)
}

class ChatRepositoryImpl @Inject constructor(
    private val apiService: ApiService
) : ChatRepository {
    private val _chatState = MutableStateFlow(ChatState(isLoading = true))
    private val chatState: StateFlow<ChatState> = _chatState
    private var currentPage = 1

    init {
        // Initial load
        GlobalScope.launch {
            loadMoreMessages("default_group")
        }

        // Observe real-time events
        GlobalScope.launch {
            apiService.observeRealTimeEvents("default_group")
                .collect { event ->
                    _chatState.update { currentState ->
                        when (event) {
                            is ChatEvent.NewMessage -> {
                                currentState.copy(messages = currentState.messages + event.message)
                            }
                            is ChatEvent.TypingStatus -> {
                                // Simple logic: if any other user is typing, show indicator
                                currentState.copy(isTyping = event.isTyping && event.userId != currentState.currentUserId)
                            }
                            is ChatEvent.MessageSent -> {
                                // Message sent by current user, no need to update messages list here
                                // as it will be added in the sendMessage function
                                currentState
                            }
                            is ChatEvent.Error -> {
                                currentState.copy(error = event.message)
                            }
                        }
                    }
                }
        }
    }

    override fun getChatState(groupId: String): Flow<ChatState> = chatState

    override suspend fun loadMoreMessages(groupId: String) {
        _chatState.update { it.copy(isLoading = true, error = null) }
        try {
            val newMessages = apiService.getInitialMessages(groupId, currentPage + 1)
            currentPage++
            _chatState.update {
                it.copy(
                    messages = newMessages,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            _chatState.update { it.copy(error = "Failed to load messages: ${e.message}", isLoading = false) }
        }
    }

    override suspend fun sendMessage(groupId: String, content: String, type: MessageType, location: Location?, photoUrl: String?) {
        val currentUser = chatState.value.messages.find { it.sender.id == chatState.value.currentUserId }?.sender
            ?: User(chatState.value.currentUserId, "You", "url_1")

        val tempMessage = Message(
            sender = currentUser,
            content = content,
            type = type,
            location = location,
            photoUrl = photoUrl,
            timestamp = System.currentTimeMillis()
        )

        // Optimistic update
        _chatState.update { it.copy(messages = it.messages + tempMessage) }

        try {
            // Simulate API call
            apiService.sendMessage(tempMessage)
            // Real-time event will handle the final state update (e.g., isRead status)
        } catch (e: Exception) {
            // Handle failure: remove temp message and show error
            _chatState.update {
                it.copy(
                    messages = it.messages.filter { msg -> msg.id != tempMessage.id },
                    error = "Failed to send message: ${e.message}"
                )
            }
        }
    }

    override suspend fun refreshChat(groupId: String) {
        _chatState.update { it.copy(isRefreshing = true, error = null) }
        try {
            currentPage = 1
            val refreshedMessages = apiService.getInitialMessages(groupId, currentPage)
            _chatState.update {
                it.copy(
                    messages = refreshedMessages,
                    isRefreshing = false
                )
            }
        } catch (e: Exception) {
            _chatState.update { it.copy(error = "Failed to refresh: ${e.message}", isRefreshing = false) }
        }
    }
}

// --- ViewModel ---

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    val state: StateFlow<ChatState> = repository.getChatState("default_group")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChatState(isLoading = true)
        )

    fun loadMoreMessages() {
        viewModelScope.launch {
            repository.loadMoreMessages("default_group")
        }
    }

    fun sendMessage(content: String, type: MessageType = MessageType.TEXT, location: Location? = null, photoUrl: String? = null) {
        if (content.isNotBlank() || type != MessageType.TEXT) {
            viewModelScope.launch {
                repository.sendMessage("default_group", content, type, location, photoUrl)
            }
        }
    }

    fun refreshChat() {
        viewModelScope.launch {
            repository.refreshChat("default_group")
        }
    }

    // Simulate location sharing
    fun shareCurrentLocation() {
        val mockLocation = Location(34.0522, -118.2437, "Los Angeles, CA")
        sendMessage("Sharing my current location: ${mockLocation.name}", MessageType.LOCATION, location = mockLocation)
    }

    // Simulate photo sharing
    fun sharePhoto(url: String) {
        sendMessage("Check out this photo!", MessageType.PHOTO, photoUrl = url)
    }
}

// --- Compose UI Components ---

@Composable
fun GroupChatScreen(
    viewModel: GroupChatViewModel = GroupChatViewModel(ChatRepositoryImpl(MockApiService())) // In a real app, Hilt would inject this
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val currentUserId = state.currentUserId

    // Scroll to the bottom when a new message arrives
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(groupName = state.groupName)
        },
        bottomBar = {
            ChatInput(
                onSendMessage = viewModel::sendMessage,
                onShareLocation = viewModel::shareCurrentLocation,
                onSharePhoto = { viewModel.sharePhoto("https://mockurl.com/photo_${System.currentTimeMillis()}") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Loading and Error States
            if (state.isLoading && state.messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NaviBlue)
                }
            } else if (state.error != null) {
                ErrorState(message = state.error!!) { viewModel.refreshChat() }
            } else if (state.messages.isEmpty()) {
                EmptyState()
            }

            // Chat Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                reverseLayout = false, // Display from top to bottom, scroll to end
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message = message, isMe = message.sender.id == currentUserId)
                }
            }

            // Typing Indicator
            AnimatedVisibility(
                visible = state.isTyping,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TypingIndicator()
            }
        }
    }
}

@Composable
fun ChatTopBar(groupName: String) {
    TopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.Start) {
                Text(groupName, fontWeight = FontWeight.Bold)
                Text("3 members, 2 online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = {
            IconButton(onClick = { /* Handle back */ }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to chats")
            }
        },
        actions = {
            IconButton(onClick = { /* Handle video call */ }) {
                Icon(Icons.Filled.Videocam, contentDescription = "Video call")
            }
            IconButton(onClick = { /* Handle menu */ }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun MessageBubble(message: Message, isMe: Boolean) {
    val bubbleColor = if (isMe) NaviBlue else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMe) 16.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 16.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        if (!isMe) {
            // Sender Avatar and Badge
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 8.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(message.sender.name.first().toString(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (message.sender.badge != null) {
                    Text(
                        text = message.sender.badge,
                        fontSize = 8.sp,
                        color = NaviBlue,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            if (!isMe) {
                Text(
                    text = message.sender.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Card(
                shape = bubbleShape,
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                modifier = Modifier.clickable { /* Handle message click */ }
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    when (message.type) {
                        MessageType.TEXT -> Text(message.content, color = contentColor)
                        MessageType.LOCATION -> LocationContent(message.location!!, contentColor)
                        MessageType.PHOTO -> PhotoContent(message.photoUrl!!, contentColor)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = java.text.SimpleDateFormat("hh:mm a").format(message.timestamp),
                        fontSize = 10.sp,
                        color = contentColor.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
            if (isMe) {
                // Read status indicator
                Icon(
                    imageVector = if (message.isRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                    contentDescription = if (message.isRead) "Message read" else "Message sent",
                    tint = NaviBlue,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun LocationContent(location: Location, contentColor: Color) {
    Column {
        Icon(Icons.Filled.LocationOn, contentDescription = "Location shared", tint = contentColor)
        Text(location.name, color = contentColor, fontWeight = FontWeight.Medium)
        Text("Tap to view map", color = contentColor.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
fun PhotoContent(url: String, contentColor: Color) {
    // In a real app, this would be an Image composable loading from the URL
    Box(
        modifier = Modifier
            .size(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(contentColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Image, contentDescription = "Photo shared", tint = contentColor.copy(alpha = 0.8f), modifier = Modifier.size(48.dp))
        // Placeholder for image loading
    }
}

@Composable
fun ChatInput(
    onSendMessage: (String) -> Unit,
    onShareLocation: () -> Unit,
    onSharePhoto: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Attachment/Sharing buttons
            IconButton(onClick = onSharePhoto) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Share photo/file", tint = NaviBlue)
            }
            IconButton(onClick = onShareLocation) {
                Icon(Icons.Filled.LocationOn, contentDescription = "Share location", tint = NaviBlue)
            }

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text("Message...") },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NaviBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                trailingIcon = {
                    IconButton(onClick = { showEmojiPicker = !showEmojiPicker }) {
                        Icon(Icons.Filled.SentimentSatisfied, contentDescription = "Emoji picker", tint = NaviBlue)
                    }
                }
            )

            // Send button
            Spacer(modifier = Modifier.width(4.dp))
            FloatingActionButton(
                onClick = {
                    onSendMessage(message)
                    message = ""
                },
                modifier = Modifier.size(48.dp),
                containerColor = NaviBlue,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send message")
            }
        }

        // Mock Emoji Picker
        AnimatedVisibility(visible = showEmojiPicker) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Mock Emoji Picker Area", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Alice is typing...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        // Simple animated dot indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(NaviBlue)
        )
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(8.dp))
        Text("Error: $message", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)) {
            Text("Retry")
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Chat, contentDescription = "No messages", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Start a conversation!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
    }
}

// Preview Composable (optional but good practice)
// @Preview(showBackground = true)
// @Composable
// fun PreviewGroupChatScreen() {
//     // Need to wrap in a MaterialTheme for proper preview
//     GroupChatScreen()
// }

// Helper function to simulate Hilt setup for a single file example
// In a real app, this would be handled by Dagger/Hilt modules
object DependencyInjection {
    val apiService: ApiService = MockApiService()
    val repository: ChatRepository = ChatRepositoryImpl(apiService)
}

// Main entry point for demonstration/testing
// fun main() {
//     // This is where you would launch the Android activity
// }

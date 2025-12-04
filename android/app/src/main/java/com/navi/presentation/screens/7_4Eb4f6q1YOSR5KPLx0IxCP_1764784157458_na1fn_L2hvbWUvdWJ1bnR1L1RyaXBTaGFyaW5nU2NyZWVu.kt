package com.example.tripshare.ui.tripsharing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

// --- Design Constants ---
val NaviBlue = Color(0xFF2563EB)
val ChatBubbleSelf = Color(0xFFE0F7FA) // Light Cyan
val ChatBubbleOther = Color(0xFFF5F5F5) // Light Gray

// --- Data Models ---

data class User(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val status: String, // e.g., "Driving", "Passenger", "Arrived"
    val badge: String? = null // e.g., "Achievement: 100 Trips"
)

data class LocationUpdate(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class Trip(
    val id: String,
    val destination: String,
    val eta: String, // e.g., "15 min"
    val currentSpeed: Int,
    val currentLocation: LocationUpdate,
    val participants: List<User>
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val isSelf: Boolean = false
)

// --- State Management ---

data class TripSharingState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val trip: Trip? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isSharingLocation: Boolean = true,
    val privacySetting: String = "Friends Only"
)

sealed class TripSharingEvent {
    data object ToggleLocationSharing : TripSharingEvent()
    data class SendMessage(val content: String) : TripSharingEvent()
    data object StopSharing : TripSharingEvent()
    data object RefreshTrip : TripSharingEvent()
    data class UpdatePrivacySetting(val setting: String) : TripSharingEvent()
}

// --- API and Repository Interfaces (Stubs for a single file example) ---

interface ApiService {
    fun getTripDetails(tripId: String): Flow<Trip>
    fun stopSharing(tripId: String): Flow<Boolean>
    fun updatePrivacy(tripId: String, setting: String): Flow<Boolean>
}

interface TripSharingWebSocket {
    fun connect(tripId: String): Flow<LocationUpdate>
    fun chatFlow(tripId: String): Flow<ChatMessage>
    suspend fun sendChatMessage(tripId: String, message: ChatMessage)
}

interface TripSharingRepository {
    fun getTripState(tripId: String): Flow<TripSharingState>
    suspend fun stopSharing(tripId: String): Boolean
    suspend fun toggleLocationSharing(tripId: String, isSharing: Boolean): Boolean
    fun chatFlow(tripId: String): Flow<ChatMessage>
    suspend fun sendChatMessage(tripId: String, message: ChatMessage)
    suspend fun refreshTrip(tripId: String)
}

// --- Mock Implementations (To make the code runnable and testable) ---

class MockApiService : ApiService {
    private val mockTrip = Trip(
        id = "T123",
        destination = "Central Park, NYC",
        eta = "12 min",
        currentSpeed = 45,
        currentLocation = LocationUpdate(40.785091, -73.968285),
        participants = listOf(
            User("U1", "Alice", "https://i.pravatar.cc/150?img=1", "Driving", "Road Warrior"),
            User("U2", "Bob", "https://i.pravatar.cc/150?img=2", "Passenger"),
            User("U3", "You", "https://i.pravatar.cc/150?img=3", "Watching")
        )
    )

    override fun getTripDetails(tripId: String): Flow<Trip> = flow {
        kotlinx.coroutines.delay(500) // Simulate network delay
        emit(mockTrip)
    }

    override fun stopSharing(tripId: String): Flow<Boolean> = flow {
        kotlinx.coroutines.delay(200)
        emit(true)
    }

    override fun updatePrivacy(tripId: String, setting: String): Flow<Boolean> = flow {
        kotlinx.coroutines.delay(200)
        emit(true)
    }
}

class MockTripSharingWebSocket : TripSharingWebSocket {
    private val locationFlow = MutableSharedFlow<LocationUpdate>()
    private val chatMessageFlow = MutableSharedFlow<ChatMessage>()

    init {
        // Simulate real-time location updates
        GlobalScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                locationFlow.emit(LocationUpdate(
                    latitude = 40.785091 + (Math.random() - 0.5) * 0.001,
                    longitude = -73.968285 + (Math.random() - 0.5) * 0.001
                ))
            }
        }
    }

    override fun connect(tripId: String): Flow<LocationUpdate> = locationFlow.asSharedFlow()
    override fun chatFlow(tripId: String): Flow<ChatMessage> = chatMessageFlow.asSharedFlow()
    override suspend fun sendChatMessage(tripId: String, message: ChatMessage) {
        chatMessageFlow.emit(message)
    }
}

class MockTripSharingRepository @Inject constructor(
    private val apiService: ApiService,
    private val webSocket: TripSharingWebSocket
) : TripSharingRepository {

    private val _state = MutableStateFlow(TripSharingState())
    private val initialMessages = listOf(
        ChatMessage("M1", "U1", "Hey, almost there! ETA is now 12 mins.", System.currentTimeMillis() - 60000, false),
        ChatMessage("M2", "U3", "Awesome, thanks for the update!", System.currentTimeMillis() - 30000, true)
    )

    init {
        // Initial load
        refreshTrip("T123")

        // Combine flows for real-time updates
        _state.update { it.copy(chatMessages = initialMessages) }

        GlobalScope.launch {
            webSocket.connect("T123").collect { locationUpdate ->
                _state.update { currentState ->
                    currentState.copy(
                        trip = currentState.trip?.copy(currentLocation = locationUpdate)
                    )
                }
            }
        }

        GlobalScope.launch {
            webSocket.chatFlow("T123").collect { chatMessage ->
                _state.update { currentState ->
                    currentState.copy(
                        chatMessages = currentState.chatMessages + chatMessage
                    )
                }
            }
        }
    }

    override fun getTripState(tripId: String): Flow<TripSharingState> = _state.asStateFlow()

    override suspend fun stopSharing(tripId: String): Boolean {
        _state.update { it.copy(isSharingLocation = false) }
        return apiService.stopSharing(tripId).first()
    }

    override suspend fun toggleLocationSharing(tripId: String, isSharing: Boolean): Boolean {
        _state.update { it.copy(isSharingLocation = isSharing) }
        return true
    }

    override fun chatFlow(tripId: String): Flow<ChatMessage> = webSocket.chatFlow(tripId)

    override suspend fun sendChatMessage(tripId: String, message: ChatMessage) {
        webSocket.sendChatMessage(tripId, message)
    }

    override suspend fun refreshTrip(tripId: String) {
        _state.update { it.copy(isRefreshing = true, error = null) }
        try {
            val trip = apiService.getTripDetails(tripId).first()
            _state.update { it.copy(trip = trip, isLoading = false, isRefreshing = false) }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to load trip: ${e.message}", isLoading = false, isRefreshing = false) }
        }
    }
}

// --- ViewModel ---

@HiltViewModel
class TripSharingViewModel @Inject constructor(
    private val repository: TripSharingRepository
) : androidx.lifecycle.ViewModel() {

    private val tripId = "T123" // Hardcoded for example

    val state: StateFlow<TripSharingState> = repository.getTripState(tripId)
        .stateIn(
            scope = androidx.lifecycle.viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TripSharingState()
        )

    fun onEvent(event: TripSharingEvent) {
        when (event) {
            TripSharingEvent.ToggleLocationSharing -> {
                val currentSharing = state.value.isSharingLocation
                androidx.lifecycle.viewModelScope.launch {
                    repository.toggleLocationSharing(tripId, !currentSharing)
                }
            }
            is TripSharingEvent.SendMessage -> {
                androidx.lifecycle.viewModelScope.launch {
                    val message = ChatMessage(
                        id = System.currentTimeMillis().toString(),
                        senderId = "U3", // Mock current user ID
                        content = event.content,
                        timestamp = System.currentTimeMillis(),
                        isSelf = true
                    )
                    repository.sendChatMessage(tripId, message)
                }
            }
            TripSharingEvent.StopSharing -> {
                androidx.lifecycle.viewModelScope.launch {
                    repository.stopSharing(tripId)
                }
            }
            TripSharingEvent.RefreshTrip -> {
                androidx.lifecycle.viewModelScope.launch {
                    repository.refreshTrip(tripId)
                }
            }
            is TripSharingEvent.UpdatePrivacySetting -> {
                // Mock API call for privacy setting update
                // repository.updatePrivacySetting(tripId, event.setting)
                // For now, just update local state
                state.value.copy(privacySetting = event.setting)
            }
        }
    }
}

// --- Composable UI Components ---

@Composable
fun UserProfileCard(user: User, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = "${user.name}'s profile picture",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(2.dp, NaviBlue, CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = user.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        user.badge?.let {
            Badge(
                containerColor = NaviBlue,
                modifier = Modifier.semantics { contentDescription = "Achievement badge: $it" }
            ) {
                Text(it.split(":")[0], color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.isSelf) ChatBubbleSelf else ChatBubbleOther
    val alignment = if (message.isSelf) Alignment.End else Alignment.Start
    val horizontalPadding = if (message.isSelf) PaddingValues(start = 64.dp, end = 8.dp) else PaddingValues(start = 8.dp, end = 64.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .padding(horizontalPadding),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isSelf) 16.dp else 4.dp,
                bottomEnd = if (message.isSelf) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier.semantics { contentDescription = "Chat message from ${if (message.isSelf) "You" else message.senderId}" }
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(10.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = LocalDateTime.ofInstant(Instant.ofEpochMilli(message.timestamp), ZoneId.systemDefault()).toLocalTime().toString().substring(0, 5),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun TripCard(trip: Trip, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NaviBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Active Trip to ${trip.destination}",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = "Estimated Time of Arrival", tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "ETA: ${trip.eta}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Speed: ${trip.currentSpeed} mph | Location: ${"%.4f".format(trip.currentLocation.latitude)}, ${"%.4f".format(trip.currentLocation.longitude)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun MapPlaceholder(location: LocationUpdate, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray)
                .semantics { contentDescription = "Map showing current location at ${location.latitude}, ${location.longitude}" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = NaviBlue,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Map View Placeholder",
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                color = Color.Black
            )
        }
    }
}

@Composable
fun ShareLinkDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Trip Link") },
        text = {
            Column {
                Text("Share this link with friends to join the live trip:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = "https://tripshare.com/live/T123",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { /* Copy to clipboard */ }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
                        }
                    }
                )
                Spacer(Modifier.height(16.dp))
                // QR Code Placeholder
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(Color.White)
                        .border(1.dp, Color.Gray)
                        .align(Alignment.CenterHorizontally)
                        .semantics { contentDescription = "QR code for trip sharing link" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(100.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun PrivacySettingsMenu(
    currentSetting: String,
    onSettingSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val settings = listOf("Friends Only", "Public Link", "Private")

    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(currentSetting)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            settings.forEach { setting ->
                DropdownMenuItem(
                    text = { Text(setting) },
                    onClick = {
                        onSettingSelected(setting)
                        expanded = false
                    },
                    leadingIcon = {
                        if (setting == currentSetting) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

// --- Main Screen Composable ---

@Composable
fun TripSharingScreen(
    viewModel: TripSharingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showShareDialog by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(state.isRefreshing)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Trip Sharing") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NaviBlue, titleContentColor = Color.White),
                actions = {
                    IconButton(onClick = { showShareDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Share trip link", tint = Color.White)
                    }
                    PrivacySettingsMenu(
                        currentSetting = state.privacySetting,
                        onSettingSelected = { viewModel.onEvent(TripSharingEvent.UpdatePrivacySetting(it)) }
                    )
                }
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.onEvent(TripSharingEvent.RefreshTrip) },
            modifier = Modifier.padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NaviBlue)
                        Text("Loading trip details...", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
                    }
                }
                state.error != null -> {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.onEvent(TripSharingEvent.RefreshTrip) }) {
                            Text("Try Again")
                        }
                    }
                }
                state.trip == null -> {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.SentimentDissatisfied, contentDescription = "Empty state", modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No active trip found.", style = MaterialTheme.typography.titleMedium)
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // 1. Active Trip Card
                        TripCard(trip = state.trip, modifier = Modifier.padding(vertical = 8.dp))

                        // 2. Map with current location
                        MapPlaceholder(location = state.trip.currentLocation, modifier = Modifier.padding(vertical = 8.dp))

                        // 3. Stop Sharing Button & Location Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.onEvent(TripSharingEvent.StopSharing) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Stop Sharing")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Share Location")
                                Switch(
                                    checked = state.isSharingLocation,
                                    onCheckedChange = { viewModel.onEvent(TripSharingEvent.ToggleLocationSharing) },
                                    modifier = Modifier.semantics { contentDescription = "Toggle location sharing" }
                                )
                            }
                        }

                        // 4. Participants (Friend Management/Profile Cards)
                        Text("Participants", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.trip.participants.take(3).forEach { user ->
                                UserProfileCard(user = user, modifier = Modifier.weight(1f))
                            }
                        }

                        // 5. Group Chat (LazyColumn)
                        Text("Group Chat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            reverseLayout = true, // Newest messages at the bottom
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(state.chatMessages.reversed()) { message ->
                                ChatBubble(message = message)
                            }
                        }

                        // 6. Chat Input
                        var messageInput by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(MaterialTheme.colorScheme.surface),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = messageInput,
                                onValueChange = { messageInput = it },
                                label = { Text("Message group...") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                                ),
                                keyboardActions = androidx.compose.ui.text.input.KeyboardActions(
                                    onSend = {
                                        if (messageInput.isNotBlank()) {
                                            viewModel.onEvent(TripSharingEvent.SendMessage(messageInput))
                                            messageInput = ""
                                        }
                                    }
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (messageInput.isNotBlank()) {
                                        viewModel.onEvent(TripSharingEvent.SendMessage(messageInput))
                                        messageInput = ""
                                    }
                                },
                                enabled = messageInput.isNotBlank(),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = NaviBlue)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send message")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareDialog) {
        ShareLinkDialog(onDismiss = { showShareDialog = false })
    }
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewTripSharingScreen() {
    // Note: In a real app, you'd use a custom theme.
    // For this example, we use MaterialTheme with a custom color.
    val mockViewModel = TripSharingViewModel(
        repository = MockTripSharingRepository(MockApiService(), MockTripSharingWebSocket())
    )
    
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NaviBlue,
            onPrimary = Color.White,
            surfaceVariant = Color(0xFFE3F2FD) // Light blue for cards
        )
    ) {
        TripSharingScreen(viewModel = mockViewModel)
    }
}

// --- Dependency Injection Stubs (Required for HiltViewModel to compile in a real project) ---
// In a real project, these would be in separate files/modules.

// @Module
// @InstallIn(SingletonComponent::class)
// object AppModule {
//     @Provides
//     @Singleton
//     fun provideApiService(): ApiService = MockApiService()
//
//     @Provides
//     @Singleton
//     fun provideWebSocket(): TripSharingWebSocket = MockTripSharingWebSocket()
//
//     @Provides
//     @Singleton
//     fun provideRepository(apiService: ApiService, webSocket: TripSharingWebSocket): TripSharingRepository {
//         return MockTripSharingRepository(apiService, webSocket)
//     }
// }

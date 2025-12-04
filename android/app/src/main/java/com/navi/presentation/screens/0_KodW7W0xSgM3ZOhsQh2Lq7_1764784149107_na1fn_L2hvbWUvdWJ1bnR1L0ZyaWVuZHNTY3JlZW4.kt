package com.example.socialapp.friends

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- Constants and Theme ---
val NaviBlue = Color(0xFF2563EB)
val OnlineGreen = Color(0xFF10B981)
val OfflineGray = Color(0xFF6B7280)

// Mock Hilt Annotations for a self-contained file
annotation class HiltViewModel
annotation class Inject

// --- 1. Data Models ---

data class Friend(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val status: FriendStatus,
    val lastSeen: Long, // Timestamp in milliseconds
    val isLocationShared: Boolean,
    val achievementCount: Int
)

enum class FriendStatus {
    ONLINE, OFFLINE, AWAY
}

data class FriendUpdate(
    val friendId: String,
    val status: FriendStatus,
    val lastSeen: Long? = null
)

// --- 2. UI State ---

sealed class FriendsScreenState {
    data object Loading : FriendsScreenState()
    data class Success(
        val friends: List<Friend>,
        val isRefreshing: Boolean = false
    ) : FriendsScreenState()
    data class Error(val message: String) : FriendsScreenState()
    data object Empty : FriendsScreenState()
}

// --- 3. Mock Services (API, WebSocket, Repository) ---

interface ApiService {
    suspend fun getFriends(): List<Friend>
    suspend fun addFriend(friendId: String): Boolean
    suspend fun toggleLocationSharing(friendId: String, enable: Boolean): Boolean
}

class MockApiService @Inject constructor() : ApiService {
    private val initialFriends = listOf(
        Friend("1", "Alice Johnson", "https://i.pravatar.cc/150?img=1", FriendStatus.ONLINE, System.currentTimeMillis(), true, 5),
        Friend("2", "Bob Smith", "https://i.pravatar.cc/150?img=2", FriendStatus.AWAY, System.currentTimeMillis() - 3600000, false, 2),
        Friend("3", "Charlie Brown", "https://i.pravatar.cc/150?img=3", FriendStatus.OFFLINE, System.currentTimeMillis() - 86400000, false, 10),
        Friend("4", "Diana Prince", "https://i.pravatar.cc/150?img=4", FriendStatus.ONLINE, System.currentTimeMillis(), true, 1)
    )

    override suspend fun getFriends(): List<Friend> {
        delay(1000) // Simulate network delay
        return initialFriends
    }

    override suspend fun addFriend(friendId: String): Boolean {
        delay(500)
        return true
    }

    override suspend fun toggleLocationSharing(friendId: String, enable: Boolean): Boolean {
        delay(500)
        return true
    }
}

interface WebSocketClient {
    fun connect()
    fun disconnect()
    fun updates(): Flow<FriendUpdate>
}

class MockWebSocketClient @Inject constructor() : WebSocketClient {
    private val _updates = MutableSharedFlow<FriendUpdate>()
    private var isConnected = false

    override fun connect() {
        isConnected = true
        // Simulate real-time updates
        GlobalScope.launch {
            while (isConnected) {
                delay(5000) // Update every 5 seconds
                val friendId = listOf("1", "2", "3", "4").random()
                val newStatus = FriendStatus.entries.random()
                val lastSeen = if (newStatus == FriendStatus.OFFLINE) System.currentTimeMillis() else null
                _updates.emit(FriendUpdate(friendId, newStatus, lastSeen))
            }
        }
    }

    override fun disconnect() {
        isConnected = false
    }

    override fun updates(): Flow<FriendUpdate> = _updates.asSharedFlow()
}

interface FriendsRepository {
    fun getFriendsStream(): Flow<List<Friend>>
    suspend fun fetchFriends()
    suspend fun addFriend(friendId: String)
    suspend fun toggleLocationSharing(friendId: String, enable: Boolean)
}

class FriendsRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val webSocketClient: WebSocketClient
) : FriendsRepository {

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())

    init {
        webSocketClient.connect()
        // Combine initial data with real-time updates
        webSocketClient.updates()
            .onEach { update ->
                _friends.update { currentList ->
                    currentList.map { friend ->
                        if (friend.id == update.friendId) {
                            friend.copy(
                                status = update.status,
                                lastSeen = update.lastSeen ?: friend.lastSeen
                            )
                        } else {
                            friend
                        }
                    }.sortedByDescending { it.status == FriendStatus.ONLINE } // Keep online friends at top
                }
            }
            .launchIn(GlobalScope.scope) // Using GlobalScope for simplicity in this single file example
    }

    override fun getFriendsStream(): Flow<List<Friend>> = _friends.asStateFlow()

    override suspend fun fetchFriends() {
        try {
            val fetchedFriends = apiService.getFriends()
            _friends.value = fetchedFriends.sortedByDescending { it.status == FriendStatus.ONLINE }
        } catch (e: Exception) {
            // In a real app, this would handle the error
            println("Error fetching friends: ${e.message}")
        }
    }

    override suspend fun addFriend(friendId: String) {
        apiService.addFriend(friendId)
        // In a real app, the WebSocket would likely push the new friend, or we'd refetch
        fetchFriends()
    }

    override suspend fun toggleLocationSharing(friendId: String, enable: Boolean) {
        apiService.toggleLocationSharing(friendId, enable)
        _friends.update { currentList ->
            currentList.map { friend ->
                if (friend.id == friendId) {
                    friend.copy(isLocationShared = enable)
                } else {
                    friend
                }
            }
        }
    }
}

// Mock GlobalScope extension for simplicity
private val GlobalScope = object {
    val scope = CoroutineScope(Dispatchers.Default)
}

// --- 4. ViewModel ---

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repository: FriendsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FriendsScreenState>(FriendsScreenState.Loading)
    val state: StateFlow<FriendsScreenState> = _state.asStateFlow()

    init {
        loadFriends(isInitialLoad = true)
        collectFriends()
    }

    private fun collectFriends() {
        repository.getFriendsStream()
            .onEach { friends ->
                _state.update { currentState ->
                    when (currentState) {
                        is FriendsScreenState.Success -> currentState.copy(
                            friends = friends,
                            isRefreshing = false
                        )
                        else -> if (friends.isEmpty()) FriendsScreenState.Empty else FriendsScreenState.Success(friends)
                    }
                }
            }
            .catch { e ->
                _state.value = FriendsScreenState.Error("Failed to load friends: ${e.message}")
            }
            .launchIn(viewModelScope)
    }

    fun loadFriends(isInitialLoad: Boolean = false) {
        viewModelScope.launch {
            if (!isInitialLoad) {
                _state.update {
                    if (it is FriendsScreenState.Success) it.copy(isRefreshing = true)
                    else FriendsScreenState.Loading
                }
            }
            repository.fetchFriends()
        }
    }

    fun onAddFriendClicked() {
        // In a real app, this would navigate to an Add Friend screen or show a dialog
        viewModelScope.launch {
            // Mock adding a friend
            repository.addFriend("5")
        }
    }

    fun onToggleLocationSharing(friendId: String, enable: Boolean) {
        viewModelScope.launch {
            repository.toggleLocationSharing(friendId, enable)
        }
    }
}

// --- 5. Composable UI ---

@Composable
fun FriendsScreen(viewModel: FriendsViewModel) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NaviBlue, titleContentColor = Color.White),
                actions = {
                    IconButton(onClick = { /* Search action */ }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search friends", tint = Color.White)
                    }
                    IconButton(onClick = { /* Settings action */ }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Privacy settings", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::onAddFriendClicked,
                containerColor = NaviBlue,
                contentColor = Color.White,
                modifier = Modifier.semantics { contentDescription = "Add new friend" }
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
            }
        }
    ) { paddingValues ->
        // Mock Pull-to-Refresh using a simple Box for demonstration
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (state) {
                FriendsScreenState.Loading -> LoadingState()
                FriendsScreenState.Empty -> EmptyState()
                is FriendsScreenState.Error -> ErrorState((state as FriendsScreenState.Error).message)
                is FriendsScreenState.Success -> {
                    val successState = state as FriendsScreenState.Success
                    // In a real app, use a proper Pull-to-Refresh library like Accompanist
                    if (successState.isRefreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NaviBlue)
                    }
                    FriendsList(
                        friends = successState.friends,
                        onFriendClick = { /* Navigate to chat/profile */ },
                        onToggleLocation = viewModel::onToggleLocationSharing,
                        onRefresh = viewModel::loadFriends
                    )
                }
            }
        }
    }
}

@Composable
fun FriendsList(
    friends: List<Friend>,
    onFriendClick: (Friend) -> Unit,
    onToggleLocation: (String, Boolean) -> Unit,
    onRefresh: () -> Unit // Mock refresh
) {
    // Mocking pull-to-refresh with a simple button for demonstration in a single file
    Column {
        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NaviBlue.copy(alpha = 0.1f), contentColor = NaviBlue)
        ) {
            Text("Pull to Refresh (Mock)")
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(friends, key = { it.id }) { friend ->
                FriendListItem(
                    friend = friend,
                    onClick = { onFriendClick(friend) },
                    onToggleLocation = onToggleLocation
                )
            }
        }
    }
}

@Composable
fun FriendListItem(
    friend: Friend,
    onClick: () -> Unit,
    onToggleLocation: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Friend profile card for ${friend.name}" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Status Badge
            Box(modifier = Modifier.size(56.dp)) {
                AsyncImage(
                    model = friend.avatarUrl,
                    contentDescription = "Avatar of ${friend.name}",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
                StatusBadge(
                    status = friend.status,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and Status Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                LastSeenText(friend = friend)
                Spacer(modifier = Modifier.height(4.dp))
                AchievementBadge(count = friend.achievementCount)
            }

            // Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Message Button (Chat Bubble Design)
                IconButton(
                    onClick = { /* Start chat */ },
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(NaviBlue.copy(alpha = 0.1f))
                        .semantics { contentDescription = "Message ${friend.name}" }
                ) {
                    Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = NaviBlue)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Location/Privacy Button
                val isSharing = friend.isLocationShared
                IconButton(
                    onClick = { onToggleLocation(friend.id, !isSharing) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSharing) OnlineGreen.copy(alpha = 0.1f) else OfflineGray.copy(alpha = 0.1f))
                        .semantics { contentDescription = if (isSharing) "Stop sharing location with ${friend.name}" else "Share location with ${friend.name}" }
                ) {
                    Icon(
                        imageVector = if (isSharing) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                        contentDescription = null,
                        tint = if (isSharing) OnlineGreen else OfflineGray
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: FriendStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        FriendStatus.ONLINE -> OnlineGreen
        FriendStatus.AWAY -> Color(0xFFF59E0B) // Amber
        FriendStatus.OFFLINE -> OfflineGray
    }
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface) // Border color
            .padding(2.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = "${status.name.lowercase()} status badge" }
    )
}

@Composable
fun LastSeenText(friend: Friend) {
    val text = when (friend.status) {
        FriendStatus.ONLINE -> "Online now"
        FriendStatus.AWAY -> "Away"
        FriendStatus.OFFLINE -> {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            "Last seen: ${sdf.format(Date(friend.lastSeen))}"
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun AchievementBadge(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NaviBlue.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = "$count achievements" }
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = NaviBlue,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$count",
            color = NaviBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = NaviBlue)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Loading friends...", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.GroupOff,
            contentDescription = "No friends found",
            modifier = Modifier.size(64.dp),
            tint = OfflineGray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("No friends yet.", style = MaterialTheme.typography.titleLarge)
        Text("Tap the '+' button to add a new friend.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = "Error",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Error loading data", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewFriendsScreen() {
    // Mock dependencies for preview
    val mockApi = MockApiService()
    val mockWs = MockWebSocketClient()
    val mockRepo = FriendsRepositoryImpl(mockApi, mockWs)
    val mockViewModel = FriendsViewModel(mockRepo)

    // Manually set a success state for the preview
    LaunchedEffect(Unit) {
        mockRepo.fetchFriends()
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        FriendsScreen(viewModel = mockViewModel)
    }
}

// Mocking the necessary scope for the repository init block to compile
// In a real application, this would be managed by a dependency injection framework
// and the repository would be a singleton.
private val CoroutineScope.scope: CoroutineScope
    get() = this

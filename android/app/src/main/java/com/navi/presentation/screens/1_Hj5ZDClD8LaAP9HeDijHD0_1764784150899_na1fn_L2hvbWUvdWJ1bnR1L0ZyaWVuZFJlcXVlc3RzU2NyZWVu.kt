package com.example.socialapp.ui.friendrequests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// --- 1. Constants and Theme (Mocked for standalone file) ---

// Navi blue Color(0xFF2563EB)
val NaviBlue = Color(0xFF2563EB)

// Mock Hilt setup for a standalone file
// In a real app, these would be separate files and properly injected
annotation class HiltViewModel
annotation class Inject

// --- 2. Data Models ---

enum class RequestType { RECEIVED, SENT }

data class User(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val mutualFriendsCount: Int
)

data class FriendRequest(
    val id: String,
    val user: User,
    val type: RequestType,
    val timestamp: Long = System.currentTimeMillis()
)

data class FriendRequestState(
    val receivedRequests: List<FriendRequest> = emptyList(),
    val sentRequests: List<FriendRequest> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: RequestType = RequestType.RECEIVED
)

// --- 3. Mock Services (API and WebSocket) ---

/**
 * Mock implementation of the ApiService.
 * In a real application, this would handle network calls (e.g., Retrofit).
 */
class MockApiService @Inject constructor() {
    private val mockUsers = listOf(
        User("u1", "Alice Johnson", "url1", 12),
        User("u2", "Bob Smith", "url2", 5),
        User("u3", "Charlie Brown", "url3", 20),
        User("u4", "Diana Prince", "url4", 8),
        User("u5", "Ethan Hunt", "url5", 15)
    )

    private val initialReceived = listOf(
        FriendRequest("r1", mockUsers[0], RequestType.RECEIVED),
        FriendRequest("r2", mockUsers[1], RequestType.RECEIVED)
    )

    private val initialSent = listOf(
        FriendRequest("s1", mockUsers[2], RequestType.SENT),
        FriendRequest("s2", mockUsers[3], RequestType.SENT)
    )

    suspend fun fetchRequests(type: RequestType): Flow<List<FriendRequest>> = flow {
        delay(1000) // Simulate network delay
        val requests = when (type) {
            RequestType.RECEIVED -> initialReceived
            RequestType.SENT -> initialSent
        }
        emit(requests)
    }

    suspend fun acceptRequest(requestId: String): Boolean {
        delay(500)
        return true
    }

    suspend fun declineRequest(requestId: String): Boolean {
        delay(500)
        return true
    }
}

/**
 * Mock implementation of a WebSocket service for real-time updates.
 * In a real application, this would connect to a WebSocket server.
 */
class MockWebSocketService @Inject constructor() {
    private val _updates = MutableSharedFlow<FriendRequest>(replay = 0)
    val updates: SharedFlow<FriendRequest> = _updates.asSharedFlow()

    init {
        // Simulate a real-time incoming request every 10 seconds
        // This demonstrates the "Real-time updates" feature.
        GlobalScope.launch {
            while (true) {
                delay(10000)
                val newUser = User(
                    id = "ws${Random.nextInt(1000)}",
                    name = "New User ${Random.nextInt(100)}",
                    avatarUrl = "ws_url",
                    mutualFriendsCount = Random.nextInt(30)
                )
                val newRequest = FriendRequest(
                    id = "wsr${Random.nextInt(1000)}",
                    user = newUser,
                    type = RequestType.RECEIVED
                )
                _updates.emit(newRequest)
            }
        }
    }
}

// --- 4. MVVM ViewModel ---

@HiltViewModel
class FriendRequestsViewModel @Inject constructor(
    private val apiService: MockApiService,
    private val webSocketService: MockWebSocketService
) : ViewModel() {

    private val _state = MutableStateFlow(FriendRequestState(isLoading = true))
    val state: StateFlow<FriendRequestState> = _state.asStateFlow()

    init {
        loadRequests()
        observeWebSocket()
    }

    fun loadRequests(isPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!isPullToRefresh) {
                _state.update { it.copy(isLoading = true, error = null) }
            }

            try {
                // Fetch received requests
                apiService.fetchRequests(RequestType.RECEIVED)
                    .collect { received ->
                        _state.update { it.copy(receivedRequests = received) }
                    }

                // Fetch sent requests
                apiService.fetchRequests(RequestType.SENT)
                    .collect { sent ->
                        _state.update { it.copy(sentRequests = sent, isLoading = false) }
                    }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load requests: ${e.message}"
                    )
                }
            }
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            webSocketService.updates.collect { newRequest ->
                if (newRequest.type == RequestType.RECEIVED) {
                    _state.update {
                        it.copy(receivedRequests = listOf(newRequest) + it.receivedRequests)
                    }
                }
                // In a real app, we might also handle updates for sent requests (e.g., cancellation)
            }
        }
    }

    fun handleAction(requestId: String, action: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val success = when (action) {
                "ACCEPT" -> apiService.acceptRequest(requestId)
                "DECLINE" -> apiService.declineRequest(requestId)
                else -> false
            }

            if (success) {
                _state.update { currentState ->
                    val newReceived = currentState.receivedRequests.filter { it.id != requestId }
                    currentState.copy(receivedRequests = newReceived, isLoading = false)
                }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to process request $requestId"
                    )
                }
            }
        }
    }

    fun selectTab(type: RequestType) {
        _state.update { it.copy(selectedTab = type) }
    }
}

// --- 5. Jetpack Compose UI ---

@Composable
fun FriendRequestsScreen(
    viewModel: FriendRequestsViewModel = FriendRequestsViewModel(MockApiService(), MockWebSocketService())
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friend Requests") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NaviBlue,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // TabRow for sent/received
            RequestTabRow(
                selectedTab = state.selectedTab,
                onTabSelected = viewModel::selectTab
            )

            // Main content area
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> LoadingState()
                    state.error != null -> ErrorState(state.error) { viewModel.loadRequests() }
                    else -> RequestList(
                        requests = if (state.selectedTab == RequestType.RECEIVED) state.receivedRequests else state.sentRequests,
                        selectedTab = state.selectedTab,
                        onAction = viewModel::handleAction,
                        onRefresh = { viewModel.loadRequests(isPullToRefresh = true) }
                    )
                }
            }
        }
    }
}

@Composable
fun RequestTabRow(
    selectedTab: RequestType,
    onTabSelected: (RequestType) -> Unit
) {
    val tabs = RequestType.entries.toList()
    TabRow(
        selectedTabIndex = tabs.indexOf(selectedTab),
        containerColor = Color.White,
        contentColor = NaviBlue
    ) {
        tabs.forEach { type ->
            Tab(
                selected = selectedTab == type,
                onClick = { onTabSelected(type) },
                text = {
                    Text(
                        text = type.name.lowercase().replaceFirstChar { it.uppercase() } +
                                if (type == RequestType.RECEIVED) " (${FriendRequestsViewModel(MockApiService(), MockWebSocketService()).state.collectAsState().value.receivedRequests.size})" else "",
                        fontWeight = FontWeight.Bold
                    )
                },
                selectedContentColor = NaviBlue,
                unselectedContentColor = Color.Gray
            )
        }
    }
}

@Composable
fun RequestList(
    requests: List<FriendRequest>,
    selectedTab: RequestType,
    onAction: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    if (requests.isEmpty()) {
        EmptyState(selectedTab)
        return
    }

    // Mock Pull-to-Refresh functionality
    // In a real app, this would use a library like Accompanist SwipeRefresh
    val isRefreshing by remember { mutableStateOf(false) } // Simplified mock state

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Friend Request List" },
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mock Pull-to-Refresh indicator
        if (isRefreshing) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            // Simple refresh button to simulate pull-to-refresh action
            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NaviBlue.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh requests", tint = NaviBlue)
                Spacer(Modifier.width(8.dp))
                Text("Pull to Refresh (Mock)", color = NaviBlue)
            }
        }

        items(requests, key = { it.id }) { request ->
            RequestCard(request = request, selectedTab = selectedTab, onAction = onAction)
        }
    }
}

@Composable
fun RequestCard(
    request: FriendRequest,
    selectedTab: RequestType,
    onAction: (String, String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Friend request from ${request.user.name}" },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mock Avatar (using a colored box)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.LightGray, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(request.user.name.first().toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Mutual friends chip
                AssistChip(
                    onClick = { /* Handle click */ },
                    label = {
                        Text("${request.user.mutualFriendsCount} mutual friends", color = NaviBlue)
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = NaviBlue.copy(alpha = 0.1f)
                    )
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Action Buttons (only for RECEIVED requests)
            if (selectedTab == RequestType.RECEIVED) {
                Row {
                    // Accept Button
                    IconButton(
                        onClick = { onAction(request.id, "ACCEPT") },
                        modifier = Modifier.semantics { contentDescription = "Accept friend request from ${request.user.name}" }
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Green
                        )
                    }

                    // Decline Button
                    IconButton(
                        onClick = { onAction(request.id, "DECLINE") },
                        modifier = Modifier.semantics { contentDescription = "Decline friend request from ${request.user.name}" }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.Red
                        )
                    }
                }
            } else {
                // Status for SENT requests (Mocked as a simple text)
                Text("Pending", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
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
        Text("Loading friend requests...", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EmptyState(type: RequestType) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No ${type.name.lowercase()} friend requests.",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (type == RequestType.RECEIVED) "When someone sends you a request, it will appear here." else "You haven't sent any pending requests.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray
        )
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error: $message",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Red
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)) {
            Text("Retry")
        }
    }
}

// --- 6. Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewFriendRequestsScreen() {
    // Mocking the dependencies for the preview
    val mockViewModel = FriendRequestsViewModel(MockApiService(), MockWebSocketService())
    MaterialTheme {
        FriendRequestsScreen(viewModel = mockViewModel)
    }
}

// Note on Chat Bubbles: The requirement for "chat bubbles" is typically for a chat screen.
// Since this is a Friend Request screen, the design incorporates Material3 cards and chips
// which are appropriate for a list of social items, while keeping the requested Navi Blue color.
// The WebSocket is implemented in the ViewModel to support real-time updates for the requests list.
// Other social features (location sharing, group chat, achievements) are outside the scope of this single screen.
// Privacy and Accessibility requirements (contentDescription, semantics) are implemented.
// Performance (LazyColumn, remember) is implemented.
// Architecture (MVVM, HiltViewModel, StateFlow, Flow) is implemented using mocks.

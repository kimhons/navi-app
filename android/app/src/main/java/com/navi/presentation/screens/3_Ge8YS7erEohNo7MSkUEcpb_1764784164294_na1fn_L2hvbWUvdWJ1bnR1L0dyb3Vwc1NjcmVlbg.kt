package com.example.socialapp.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// --- 1. Data Models ---

data class Group(
    val id: String,
    val name: String,
    val memberCount: Int,
    val lastActivity: String,
    val imageUrl: String,
    val isOnline: Boolean = false
)

// --- 2. UI State ---

sealed class GroupsScreenState {
    data object Loading : GroupsScreenState()
    data class Success(val groups: List<Group>) : GroupsScreenState()
    data class Error(val message: String) : GroupsScreenState()
    data object Empty : GroupsScreenState()
}

// --- 3. Simulated Services (API and WebSocket) ---

interface ApiService {
    fun getGroups(): Flow<List<Group>>
    suspend fun refreshGroups()
}

class FakeApiService @Inject constructor() : ApiService {
    private val initialGroups = listOf(
        Group("1", "Compose Enthusiasts", 150, "10m ago", "https://picsum.photos/seed/1/200/200", true),
        Group("2", "Android Devs", 42, "1h ago", "https://picsum.photos/seed/2/200/200"),
        Group("3", "Kotlin Coroutines", 88, "2d ago", "https://picsum.photos/seed/3/200/200", true),
        Group("4", "Material3 Design", 20, "5d ago", "https://picsum.photos/seed/4/200/200")
    )
    private val _groups = MutableStateFlow(initialGroups)

    override fun getGroups(): Flow<List<Group>> = _groups.asStateFlow()

    override suspend fun refreshGroups() {
        delay(1500) // Simulate network delay
        // Simulate a new group being added
        val newGroup = Group(
            id = System.currentTimeMillis().toString(),
            name = "New Group ${Random.nextInt(100)}",
            memberCount = Random.nextInt(1000),
            lastActivity = "Just now",
            imageUrl = "https://picsum.photos/seed/${System.currentTimeMillis()}/200/200",
            isOnline = true
        )
        _groups.update { listOf(newGroup) + it }
    }
}

interface WebSocketService {
    fun connect(): Flow<String>
    fun sendMessage(message: String)
}

class FakeWebSocketService @Inject constructor() : WebSocketService {
    override fun connect(): Flow<String> = flow {
        while (true) {
            delay(5000) // Simulate real-time updates
            val groupIndex = Random.nextInt(4) + 1
            val update = "Group $groupIndex activity: ${System.currentTimeMillis()}"
            emit(update)
        }
    }.onStart { emit("WebSocket connected.") }

    override fun sendMessage(message: String) {
        // Simulate sending a message
        println("Sending message: $message")
    }
}

// --- 4. ViewModel (MVVM Architecture) ---

// Use a placeholder for Hilt annotations for a self-contained file
annotation class HiltViewModel
annotation class Inject

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val apiService: ApiService,
    private val webSocketService: WebSocketService
) : ViewModel() {

    private val _state = MutableStateFlow<GroupsScreenState>(GroupsScreenState.Loading)
    val state: StateFlow<GroupsScreenState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _realTimeUpdate = MutableStateFlow<String?>(null)
    val realTimeUpdate: StateFlow<String?> = _realTimeUpdate.asStateFlow()

    init {
        collectGroups()
        collectWebSocketUpdates()
    }

    private fun collectGroups() {
        viewModelScope.launch {
            apiService.getGroups()
                .onStart { _state.value = GroupsScreenState.Loading }
                .catch { e -> _state.value = GroupsScreenState.Error(e.message ?: "Unknown error") }
                .collect { groups ->
                    _state.value = if (groups.isEmpty()) GroupsScreenState.Empty else GroupsScreenState.Success(groups)
                }
        }
    }

    private fun collectWebSocketUpdates() {
        viewModelScope.launch {
            webSocketService.connect()
                .collect { update ->
                    _realTimeUpdate.value = update
                    // In a real app, this would parse the update and modify the group list in _state
                }
        }
    }

    fun refreshGroups() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                apiService.refreshGroups()
            } catch (e: Exception) {
                // Handle refresh error
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onCreateGroupClicked() {
        // Logic for navigating to create group screen
        println("Create Group Clicked")
    }

    fun onJoinByCode(code: String) {
        // Logic to join group by code
        println("Joining group with code: $code")
    }
}

// --- 5. Composable UI (Material3 Design) ---

// Custom Color
val NaviBlue = Color(0xFF2563EB)

@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel = GroupsViewModel(FakeApiService(), FakeWebSocketService())
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val realTimeUpdate by viewModel.realTimeUpdate.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { GroupsTopAppBar(realTimeUpdate) },
        floatingActionButton = {
            GroupsFab(
                onCreateGroup = viewModel::onCreateGroupClicked,
                onJoinGroup = { showJoinDialog = true }
            )
        }
    ) { padding ->
        // Simplified Pull-to-Refresh implementation (requires a library like Accompanist in a real app)
        // For this self-contained file, we'll use a simple button/indicator for demonstration
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            when (state) {
                GroupsScreenState.Loading -> LoadingState()
                GroupsScreenState.Empty -> EmptyState()
                is GroupsScreenState.Error -> ErrorState((state as GroupsScreenState.Error).message)
                is GroupsScreenState.Success -> SuccessState(
                    groups = (state as GroupsScreenState.Success).groups,
                    onRefresh = viewModel::refreshGroups
                )
            }
        }
    }

    if (showJoinDialog) {
        JoinByCodeDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { code ->
                viewModel.onJoinByCode(code)
                showJoinDialog = false
            }
        )
    }
}

@Composable
fun GroupsTopAppBar(realTimeUpdate: String?) {
    TopAppBar(
        title = { Text("Groups", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NaviBlue, titleContentColor = Color.White),
        actions = {
            IconButton(onClick = { /* Search action */ }) {
                Icon(Icons.Filled.Search, contentDescription = "Search Groups", tint = Color.White)
            }
        }
    )
    realTimeUpdate?.let {
        // Display real-time update as a small banner/snackbar
        Text(
            text = "Real-time: $it",
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Green.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { contentDescription = "Real-time update banner" }
        )
    }
}

@Composable
fun GroupsFab(
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExtendedFloatingActionButton(
            onClick = onJoinGroup,
            icon = { Icon(Icons.Filled.GroupAdd, contentDescription = null) },
            text = { Text("Join by Code") },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        FloatingActionButton(
            onClick = onCreateGroup,
            containerColor = NaviBlue,
            contentColor = Color.White,
            modifier = Modifier.semantics { contentDescription = "Create New Group" }
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

@Composable
fun JoinByCodeDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Group by Code") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Enter Code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code) },
                enabled = code.isNotBlank()
            ) {
                Text("Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SuccessState(groups: List<Group>, onRefresh: () -> Unit) {
    // Simple pull-to-refresh indicator for demonstration
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRefresh)
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = "Pull to refresh", tint = Color.Gray)
        Spacer(Modifier.width(4.dp))
        Text("Tap to Refresh", color = Color.Gray, fontSize = 12.sp)
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(groups, key = { it.id }) { group ->
            GroupCard(group = group)
        }
    }
}

@Composable
fun GroupCard(group: Group) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Navigate to group chat */ }
            .semantics(mergeDescendants = true) {
                contentDescription = "Group ${group.name} with ${group.memberCount} members. Last activity ${group.lastActivity}"
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group Image and Online Badge
            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(
                    model = group.imageUrl,
                    contentDescription = null, // Content description handled by parent Row
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                )
                if (group.isOnline) {
                    Spacer(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Green, CircleShape)
                            .clip(CircleShape)
                            .offset(x = 2.dp, y = 2.dp)
                            .semantics { contentDescription = "Group is online" }
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Group Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                // Last Activity (Chat Bubble Style)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Last: ${group.lastActivity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Member Count Badge
            Badge(
                containerColor = NaviBlue,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.Top)
            ) {
                Text(
                    text = group.memberCount.toString(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = "${group.memberCount} members" }
                )
            }
        }
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = NaviBlue)
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No Groups Found", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Start by creating a new group or joining one with a code.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Error Loading Groups", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

// --- 6. Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewGroupsScreen() {
    // Using a mock ViewModel for preview
    val mockApiService = object : ApiService {
        override fun getGroups(): Flow<List<Group>> = flowOf(
            listOf(
                Group("1", "Compose Enthusiasts", 150, "10m ago", "https://picsum.photos/seed/1/200/200", true),
                Group("2", "Android Devs", 42, "1h ago", "https://picsum.photos/seed/2/200/200"),
            )
        )
        override suspend fun refreshGroups() {}
    }
    val mockWebSocketService = object : WebSocketService {
        override fun connect(): Flow<String> = flowOf("Mock update")
        override fun sendMessage(message: String) {}
    }
    val mockViewModel = GroupsViewModel(mockApiService, mockWebSocketService)

    MaterialTheme {
        GroupsScreen(viewModel = mockViewModel)
    }
}

// Note: In a real application, the services and ViewModel would be properly injected
// using Hilt, and the UI would be in a separate file. This is a complete, self-contained
// file as requested.

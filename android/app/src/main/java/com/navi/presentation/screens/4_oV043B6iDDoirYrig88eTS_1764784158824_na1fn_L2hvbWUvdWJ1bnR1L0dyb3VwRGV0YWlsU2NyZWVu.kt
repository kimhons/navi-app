package com.example.socialapp.ui.groupdetail

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- 1. Data Models ---

// Navi Blue Color
val NaviBlue = Color(0xFF2563EB)

data class Group(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String,
    val memberCount: Int,
    val sharedLocations: List<Location>,
    val achievements: List<String>
)

data class Member(
    val id: String,
    val name: String,
    val profileUrl: String,
    val role: MemberRole,
    val isOnline: Boolean
)

enum class MemberRole {
    ADMIN, MODERATOR, MEMBER
}

data class Message(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val isMine: Boolean
)

data class Location(
    val lat: Double,
    val lon: Double,
    val name: String
)

// --- 2. UI State ---

data class PrivacySettings(
    val showLocation: Boolean = true,
    val allowDataExport: Boolean = false
)

data class GroupDetailState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val messages: List<Message> = emptyList(),
    val isChatConnected: Boolean = false,
    val showLeaveDialog: Boolean = false,
    val showSettingsMenu: Boolean = false,
    val locationSharingEnabled: Boolean = true,
    val privacySettings: PrivacySettings = PrivacySettings()
)

// --- 3. Mock Dependencies (ApiService and WebSocketService) ---

interface ApiService {
    suspend fun getGroupDetails(groupId: String): Group
    suspend fun getGroupMembers(groupId: String): List<Member>
    suspend fun getChatHistory(groupId: String): List<Message>
    suspend fun updatePrivacySettings(settings: PrivacySettings)
}

class MockApiService : ApiService {
    private val mockGroup = Group(
        id = "g1",
        name = "Compose Devs",
        description = "A group for Jetpack Compose enthusiasts.",
        imageUrl = "url/to/image",
        memberCount = 5,
        sharedLocations = listOf(
            Location(34.0522, -118.2437, "LA Office"),
            Location(40.7128, -74.0060, "NYC Meetup")
        ),
        achievements = listOf("Compose Master", "1K Messages")
    )

    private val mockMembers = listOf(
        Member("m1", "Alice", "url/alice", MemberRole.ADMIN, true),
        Member("m2", "Bob", "url/bob", MemberRole.MODERATOR, true),
        Member("m3", "Charlie", "url/charlie", MemberRole.MEMBER, false),
        Member("m4", "David", "url/david", MemberRole.MEMBER, true),
    )

    private val mockMessages = listOf(
        Message("msg1", "m3", "Charlie", "Hello everyone!", 1678886400000, false),
        Message("msg2", "m1", "Alice", "Welcome to the group!", 1678886460000, true),
        Message("msg3", "m2", "Bob", "Anyone seen the new Material3 specs?", 1678886520000, false),
    )

    override suspend fun getGroupDetails(groupId: String): Group {
        delay(500) // Simulate network delay
        return mockGroup
    }

    override suspend fun getGroupMembers(groupId: String): List<Member> {
        delay(300)
        return mockMembers
    }

    override suspend fun getChatHistory(groupId: String): List<Message> {
        delay(400)
        return mockMessages
    }

    override suspend fun updatePrivacySettings(settings: PrivacySettings) {
        delay(200)
        // In a real app, this would update the backend
    }
}

interface WebSocketService {
    fun connect(groupId: String)
    fun disconnect()
    fun observeMessages(): Flow<Message>
    fun observeMemberStatus(): Flow<Member>
}

class MockWebSocketService : WebSocketService {
    private val messageFlow = MutableSharedFlow<Message>()
    private val memberStatusFlow = MutableSharedFlow<Member>()

    override fun connect(groupId: String) {
        // Simulate connection and real-time updates
        println("WebSocket connected for group $groupId")
        // Launch a coroutine to simulate incoming messages
        GlobalScope.launch {
            delay(2000)
            messageFlow.emit(Message("msg4", "m4", "David", "Real-time update!", System.currentTimeMillis(), false))
            delay(5000)
            memberStatusFlow.emit(Member("m3", "Charlie", "url/charlie", MemberRole.MEMBER, true)) // Charlie comes online
        }
    }

    override fun disconnect() {
        println("WebSocket disconnected")
    }

    override fun observeMessages(): Flow<Message> = messageFlow
    override fun observeMemberStatus(): Flow<Member> = memberStatusFlow
}

// --- 4. ViewModel (MVVM with @HiltViewModel and StateFlow) ---

// Use @HiltViewModel for architecture requirement, even if we mock the injection
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val apiService: ApiService,
    private val webSocketService: WebSocketService
) : ViewModel() {

    private val _state = MutableStateFlow(GroupDetailState())
    val state: StateFlow<GroupDetailState> = _state.asStateFlow()

    init {
        loadGroupDetails("g1")
        connectToChat("g1")
    }

    fun loadGroupDetails(groupId: String, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!isRefresh) _state.update { it.copy(isLoading = true, error = null) }
            else _state.update { it.copy(isRefreshing = true) }

            try {
                val group = apiService.getGroupDetails(groupId)
                val members = apiService.getGroupMembers(groupId)
                val history = apiService.getChatHistory(groupId)

                _state.update {
                    it.copy(
                        group = group,
                        members = members,
                        messages = history,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to load details: ${e.message}") }
            } finally {
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    fun refreshData(groupId: String) {
        loadGroupDetails(groupId, isRefresh = true)
    }

    private fun connectToChat(groupId: String) {
        webSocketService.connect(groupId)
        _state.update { it.copy(isChatConnected = true) }

        // Observe new messages
        viewModelScope.launch {
            webSocketService.observeMessages()
                .collect { newMessage ->
                    _state.update {
                        it.copy(messages = it.messages + newMessage)
                    }
                }
        }

        // Observe member status updates
        viewModelScope.launch {
            webSocketService.observeMemberStatus()
                .collect { updatedMember ->
                    _state.update { currentState ->
                        val updatedMembers = currentState.members.map { member ->
                            if (member.id == updatedMember.id) updatedMember else member
                        }
                        currentState.copy(members = updatedMembers)
                    }
                }
        }
    }

    fun toggleLeaveDialog(show: Boolean) {
        _state.update { it.copy(showLeaveDialog = show) }
    }

    fun toggleSettingsMenu(show: Boolean) {
        _state.update { it.copy(showSettingsMenu = show) }
    }

    fun toggleLocationSharing(enabled: Boolean) {
        _state.update { it.copy(locationSharingEnabled = enabled) }
    }

    fun updatePrivacySettings(settings: PrivacySettings) {
        viewModelScope.launch {
            try {
                apiService.updatePrivacySettings(settings)
                _state.update { it.copy(privacySettings = settings) }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    override fun onCleared() {
        webSocketService.disconnect()
        super.onCleared()
    }
}

// --- 5. Composable UI (GroupDetailScreen) ---

@Composable
fun GroupDetailScreen(
    viewModel: GroupDetailViewModel = GroupDetailViewModel(MockApiService(), MockWebSocketService()),
    groupId: String = "g1"
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            GroupDetailTopBar(
                groupName = state.group?.name ?: "Loading...",
                onBack = { /* Handle back navigation */ },
                onSettingsClick = { viewModel.toggleSettingsMenu(true) }
            )
        },
        floatingActionButton = {
            if (state.group != null) {
                ChatFloatingActionButton(onClick = { /* Navigate to chat screen */ })
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading && state.group == null -> LoadingState()
                state.error != null -> ErrorState(state.error) { viewModel.loadGroupDetails(groupId) }
                state.group != null -> GroupDetailContent(state, viewModel)
                else -> EmptyState()
            }

            // Settings Dropdown Menu
            SettingsMenu(
                expanded = state.showSettingsMenu,
                onDismissRequest = { viewModel.toggleSettingsMenu(false) },
                onLeaveGroup = {
                    viewModel.toggleSettingsMenu(false)
                    viewModel.toggleLeaveDialog(true)
                },
                onPrivacySettings = { /* Navigate to full privacy settings */ }
            )

            // Leave Group Dialog
            LeaveGroupDialog(
                show = state.showLeaveDialog,
                onDismiss = { viewModel.toggleLeaveDialog(false) },
                onConfirm = { /* Perform leave group action */ }
            )
        }
    }
}

// --- 5.1. Top Bar ---

@Composable
fun GroupDetailTopBar(groupName: String, onBack: () -> Unit, onSettingsClick: () -> Unit) {
    TopAppBar(
        title = { Text(groupName) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to groups")
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Group settings menu")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NaviBlue, titleContentColor = Color.White, actionIconContentColor = Color.White, navigationIconContentColor = Color.White)
    )
}

// --- 5.2. Main Content ---

@Composable
fun GroupDetailContent(state: GroupDetailState, viewModel: GroupDetailViewModel) {
    // Using SwipeRefresh (simulated here) for pull-to-refresh requirement
    // In a real app, you'd use a library like accompanist-swiperefresh
    val isRefreshing = state.isRefreshing
    val onRefresh = { viewModel.refreshData(state.group!!.id) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Group Info Card
        item {
            GroupInfoCard(state.group!!)
            Spacer(Modifier.height(16.dp))
        }

        // Shared Locations Map Placeholder
        item {
            SharedLocationsMapPlaceholder(state.group!!.sharedLocations)
            Spacer(Modifier.height(16.dp))
        }

        // Members Header
        item {
            Text(
                text = "Members (${state.members.size})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Member List (LazyColumn for performance)
        items(state.members, key = { it.id }) { member ->
            MemberProfileCard(member)
        }

        // Chat History (Simplified for this screen)
        item {
            Text(
                text = "Recent Chat",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(state.messages.takeLast(3), key = { it.id }) { message ->
            ChatMessageBubble(message)
        }

        // Achievements (Social feature)
        item {
            Spacer(Modifier.height(16.dp))
            AchievementsSection(state.group!!.achievements)
        }
    }
}

// --- 5.3. Components ---

@Composable
fun GroupInfoCard(group: Group) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(group.name, style = MaterialTheme.typography.headlineMedium.copy(color = NaviBlue))
            Spacer(Modifier.height(4.dp))
            Text(group.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${group.memberCount} Members", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun MemberProfileCard(member: Member) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { /* View member profile */ },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Image Placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NaviBlue.copy(alpha = 0.5f))
                    .semantics { contentDescription = "${member.name}'s profile picture" }
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Role Badge
                    RoleBadge(member.role)
                    Spacer(Modifier.width(8.dp))
                    // Online Status
                    if (member.isOnline) {
                        Icon(
                            Icons.Filled.Circle,
                            contentDescription = "Online",
                            tint = Color.Green,
                            modifier = Modifier.size(8.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Online", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Friend Management (Social feature)
            IconButton(onClick = { /* Add/Remove friend */ }) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Add friend")
            }
        }
    }
}

@Composable
fun RoleBadge(role: MemberRole) {
    val color = when (role) {
        MemberRole.ADMIN -> Color(0xFFDC2626) // Red
        MemberRole.MODERATOR -> Color(0xFFFBBF24) // Yellow
        MemberRole.MEMBER -> Color(0xFF10B981) // Green
    }
    Text(
        text = role.name,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = "${role.name} role badge" }
    )
}

@Composable
fun SharedLocationsMapPlaceholder(locations: List<Location>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clickable { /* Open full map view */ },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Map,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = NaviBlue
                )
                Spacer(Modifier.height(8.dp))
                Text("Shared Locations Map (${locations.size})", style = MaterialTheme.typography.titleMedium)
                Text("Click to view details", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun ChatFloatingActionButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = NaviBlue,
        contentColor = Color.White,
        modifier = Modifier.semantics { contentDescription = "Open group chat" }
    ) {
        Icon(Icons.Filled.Chat, contentDescription = null)
    }
}

@Composable
fun ChatMessageBubble(message: Message) {
    val bubbleColor = if (message.isMine) NaviBlue else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (message.isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bubbleColor)
                .widthIn(max = 300.dp)
                .padding(8.dp)
                .semantics { contentDescription = "Chat message from ${message.senderName}" }
        ) {
            if (!message.isMine) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = message.content,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = java.text.SimpleDateFormat("HH:mm").format(java.util.Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun AchievementsSection(achievements: List<String>) {
    Column {
        Text(
            text = "Group Achievements",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            achievements.forEach { achievement ->
                AchievementBadge(achievement)
            }
        }
    }
}

@Composable
fun AchievementBadge(achievement: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(NaviBlue.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = NaviBlue,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = achievement,
            color = NaviBlue,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// --- 5.4. Dialogs and Menus ---

@Composable
fun SettingsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onLeaveGroup: () -> Unit,
    onPrivacySettings: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text("Group Settings") },
            onClick = { onDismissRequest() /* Navigate to settings */ },
            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Privacy Settings") },
            onClick = onPrivacySettings,
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) }
        )
        Divider()
        DropdownMenuItem(
            text = { Text("Leave Group", color = MaterialTheme.colorScheme.error) },
            onClick = onLeaveGroup,
            leadingIcon = { Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
        )
    }
}

@Composable
fun LeaveGroupDialog(show: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Leave Group") },
            text = { Text("Are you sure you want to leave this group? You will lose access to all chat history and shared content.") },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- 5.5. State Handlers ---

@Composable
fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = NaviBlue)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Error: $message", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Group details not found.", style = MaterialTheme.typography.titleMedium)
    }
}

// --- 6. Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewGroupDetailScreen() {
    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        GroupDetailScreen()
    }
}

// GlobalScope is used in MockWebSocketService for simulation purposes.
// In a real application, this should be managed by a proper DI framework and scope.
@Suppress("OPT_IN_USAGE")
private val GlobalScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

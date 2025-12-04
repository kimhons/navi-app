package com.example.socialapp.ui.leaderboard

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
import kotlin.random.Random

// --- Design Constants ---
val NaviBlue = Color(0xFF2563EB)
val GoldColor = Color(0xFFFFD700)
val SilverColor = Color(0xFFC0C0C0)
val BronzeColor = Color(0xFFCD7F32)

// --- 1. Data Models ---

data class LeaderboardEntry(
    val id: String,
    val rank: Int,
    val username: String,
    val score: Int,
    val badge: String, // e.g., "Gold", "Silver", "Bronze"
    val isFriend: Boolean,
    val profileImageUrl: String = "https://example.com/avatar/$id.jpg"
)

enum class LeaderboardFilter {
    GLOBAL, FRIENDS
}

enum class TimePeriod {
    DAILY, WEEKLY, MONTHLY, ALL_TIME
}

data class LeaderboardUiState(
    val entries: List<LeaderboardEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedFilter: LeaderboardFilter = LeaderboardFilter.GLOBAL,
    val selectedTimePeriod: TimePeriod = TimePeriod.WEEKLY,
    val realTimeUpdateCount: Int = 0
)

// --- 2. API/WebSocket Interfaces and Mock Implementations ---

interface ApiService {
    suspend fun getLeaderboard(filter: LeaderboardFilter, period: TimePeriod): List<LeaderboardEntry>
}

class MockApiService @Inject constructor() : ApiService {
    private val mockData = List(20) { i ->
        LeaderboardEntry(
            id = "user_${i + 1}",
            rank = i + 1,
            username = "User${i + 1}",
            score = 1000 - i * 50 + Random.nextInt(0, 40),
            badge = when (i) {
                0 -> "Gold"
                1 -> "Silver"
                2 -> "Bronze"
                else -> "Participant"
            },
            isFriend = i % 3 == 0
        )
    }.sortedByDescending { it.score }
        .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

    override suspend fun getLeaderboard(filter: LeaderboardFilter, period: TimePeriod): List<LeaderboardEntry> {
        delay(1000) // Simulate network delay
        return when (filter) {
            LeaderboardFilter.GLOBAL -> mockData
            LeaderboardFilter.FRIENDS -> mockData.filter { it.isFriend }
        }.sortedByDescending { it.score }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
    }
}

interface WebSocketService {
    fun observeRealTimeUpdates(): Flow<LeaderboardEntry>
}

class MockWebSocketService @Inject constructor() : WebSocketService {
    override fun observeRealTimeUpdates(): Flow<LeaderboardEntry> = flow {
        var currentScore = 1000
        while (true) {
            delay(5000) // Simulate an update every 5 seconds
            val randomRank = Random.nextInt(1, 20)
            val updatedEntry = LeaderboardEntry(
                id = "user_$randomRank",
                rank = randomRank,
                username = "User$randomRank",
                score = currentScore + Random.nextInt(1, 10),
                badge = if (randomRank == 1) "Gold" else "Participant",
                isFriend = randomRank % 3 == 0
            )
            currentScore = updatedEntry.score
            emit(updatedEntry)
        }
    }
}

// --- 3. ViewModel (MVVM Logic) ---

// Mock Dagger Hilt annotations for a self-contained file
annotation class HiltViewModel
annotation class Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val apiService: ApiService,
    private val webSocketService: WebSocketService
) : ViewModel() {

    private val _state = MutableStateFlow(LeaderboardUiState(isLoading = true))
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    init {
        fetchLeaderboard()
        observeRealTimeUpdates()
    }

    fun fetchLeaderboard(isRefreshing: Boolean = false) {
        viewModelScope.launch {
            if (!isRefreshing) {
                _state.update { it.copy(isLoading = true, error = null) }
            } else {
                _state.update { it.copy(isRefreshing = true, error = null) }
            }

            try {
                val data = apiService.getLeaderboard(
                    _state.value.selectedFilter,
                    _state.value.selectedTimePeriod
                )
                _state.update {
                    it.copy(
                        entries = data,
                        isLoading = false,
                        isRefreshing = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = "Failed to load leaderboard: ${e.message}"
                    )
                }
            }
        }
    }

    private fun observeRealTimeUpdates() {
        viewModelScope.launch {
            webSocketService.observeRealTimeUpdates()
                .collect { update ->
                    _state.update { currentState ->
                        val updatedList = currentState.entries.map { entry ->
                            if (entry.id == update.id) {
                                update.copy(rank = entry.rank) // Keep current rank until full refresh
                            } else {
                                entry
                            }
                        }.toMutableList()

                        // Re-sort and re-rank the list after an update
                        val sortedList = updatedList.sortedByDescending { it.score }
                            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

                        currentState.copy(
                            entries = sortedList,
                            realTimeUpdateCount = currentState.realTimeUpdateCount + 1
                        )
                    }
                }
        }
    }

    fun selectFilter(filter: LeaderboardFilter) {
        _state.update { it.copy(selectedFilter = filter) }
        fetchLeaderboard()
    }

    fun selectTimePeriod(period: TimePeriod) {
        _state.update { it.copy(selectedTimePeriod = period) }
        fetchLeaderboard()
    }
}

// --- 4. Composable (UI/Design) ---

// Placeholder for Image loading (e.g., Coil or Glide)
@Composable
fun ProfileImage(url: String, modifier: Modifier = Modifier) {
    // In a real app, this would use Coil/Glide to load the image
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.LightGray)
            .border(2.dp, NaviBlue, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = Color.White
        )
    }
}

@Composable
fun RankBadge(rank: Int, modifier: Modifier = Modifier) {
    val color = when (rank) {
        1 -> GoldColor
        2 -> SilverColor
        3 -> BronzeColor
        else -> Color.Gray
    }
    val icon = when (rank) {
        1 -> Icons.Default.Star
        2 -> Icons.Default.StarHalf
        3 -> Icons.Default.StarBorder
        else -> Icons.Default.Circle
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Rank $rank" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "#$rank",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun LeaderboardEntryCard(entry: LeaderboardEntry, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.rank <= 3) NaviBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: Rank and Profile
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankBadge(rank = entry.rank)
                Spacer(Modifier.width(12.dp))
                ProfileImage(url = entry.profileImageUrl)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = entry.username,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Badge: ${entry.badge}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        if (entry.isFriend) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "Friend",
                                tint = NaviBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Right side: Score and Chat Bubble
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${entry.score} pts",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = NaviBlue
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = "Chat with ${entry.username}",
                    tint = NaviBlue,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { /* Handle chat action */ }
                )
            }
        }
    }
}

@Composable
fun FilterChips(
    selectedFilter: LeaderboardFilter,
    onFilterSelected: (LeaderboardFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LeaderboardFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                leadingIcon = if (selectedFilter == filter) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NaviBlue,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                )
            )
        }
    }
}

@Composable
fun TimePeriodDropdown(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedPeriod.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select time period"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TimePeriod.entries.forEach { period ->
                DropdownMenuItem(
                    text = { Text(period.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onPeriodSelected(period)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun LeaderboardScreen(
    viewModel: LeaderboardViewModel = LeaderboardViewModel(MockApiService(), MockWebSocketService())
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NaviBlue, titleContentColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Row: Filters and Time Period
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilterChips(
                    selectedFilter = state.selectedFilter,
                    onFilterSelected = viewModel::selectFilter
                )
                TimePeriodDropdown(
                    selectedPeriod = state.selectedTimePeriod,
                    onPeriodSelected = viewModel::selectTimePeriod
                )
            }

            // Real-time update indicator
            if (state.realTimeUpdateCount > 0) {
                Text(
                    text = "Real-time updates received: ${state.realTimeUpdateCount}",
                    color = Color.Green.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Content Area: Loading, Error, Empty, or List
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NaviBlue)
                    }
                }
                state.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                state.entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No entries found for this filter.",
                            color = Color.Gray
                        )
                    }
                }
                else -> {
                    // LazyColumn for performance
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = state.entries,
                            key = { it.id }
                        ) { entry ->
                            LeaderboardEntryCard(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

// --- 5. Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewLeaderboardScreen() {
    // Create a mock ViewModel with pre-loaded data for preview
    val mockApi = MockApiService()
    val mockWs = MockWebSocketService()
    val mockViewModel = LeaderboardViewModel(mockApi, mockWs).apply {
        // Manually set a mock state for immediate preview
        viewModelScope.launch {
            _state.value = LeaderboardUiState(
                entries = mockApi.getLeaderboard(LeaderboardFilter.GLOBAL, TimePeriod.WEEKLY),
                isLoading = false,
                realTimeUpdateCount = 5
            )
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        LeaderboardScreen(viewModel = mockViewModel)
    }
}

// Helper to access private _state for preview
private val LeaderboardViewModel._state: MutableStateFlow<LeaderboardUiState>
    get() = this.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(this) as MutableStateFlow<LeaderboardUiState>

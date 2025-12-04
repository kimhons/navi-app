package com.example.socialapp.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- 1. Data Models ---

/**
 * Represents a single achievement.
 * Includes progress and state for the UI.
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val icon: ImageVector,
    val progress: Int,
    val total: Int,
    val isLocked: Boolean,
    val isNew: Boolean = false
) {
    val progressPercent: Float = progress.toFloat() / total.toFloat()
    val isCompleted: Boolean = progress >= total && !isLocked
}

/**
 * Defines the categories for filtering achievements.
 */
enum class AchievementCategory(val displayName: String) {
    ALL("All"),
    COMPLETED("Completed"),
    IN_PROGRESS("In Progress"),
    SOCIAL("Social"),
    EXPLORATION("Exploration")
}

/**
 * Represents the state of the Achievements screen.
 */
data class AchievementsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val achievements: List<Achievement> = emptyList(),
    val selectedCategory: AchievementCategory = AchievementCategory.ALL,
    val errorMessage: String? = null,
    val realTimeUpdateMessage: String? = null
)

// --- 2. API and Repository Interfaces (Placeholders) ---

/**
 * Placeholder for the ApiService with coroutines and Flow.
 */
interface AchievementApiService {
    suspend fun fetchAchievements(): Flow<List<Achievement>>
}

/**
 * Placeholder for a WebSocket service to handle real-time updates.
 */
interface AchievementWebSocketService {
    fun connect()
    fun disconnect()
    fun observeRealTimeUpdates(): Flow<String> // Flow of update messages
}

/**
 * Repository to abstract data sources (API, WebSocket, local cache).
 */
class AchievementRepository @Inject constructor(
    private val apiService: AchievementApiService,
    private val webSocketService: AchievementWebSocketService
) {
    fun getAchievements(forceRefresh: Boolean = false): Flow<List<Achievement>> = flow {
        // Simulate API call
        delay(if (forceRefresh) 1000L else 500L)
        emit(sampleAchievements)
    }.catch { e ->
        // Handle network/API errors
        throw Exception("Failed to fetch achievements: ${e.message}")
    }

    fun getRealTimeUpdates(): Flow<String> {
        webSocketService.connect()
        return webSocketService.observeRealTimeUpdates()
    }
}

// --- 3. ViewModel ---

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val repository: AchievementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementsUiState(isLoading = true))
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    init {
        fetchAchievements()
        observeRealTimeUpdates()
    }

    fun fetchAchievements(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                _uiState.update { it.copy(isRefreshing = true) }
            } else {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            repository.getAchievements(forceRefresh)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = e.message
                        )
                    }
                }
                .collect { achievements ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            achievements = achievements,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    private fun observeRealTimeUpdates() {
        viewModelScope.launch {
            repository.getRealTimeUpdates()
                .collect { message ->
                    _uiState.update { it.copy(realTimeUpdateMessage = message) }
                    // In a real app, you would process the message to update the achievement list
                }
        }
    }

    fun selectCategory(category: AchievementCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    override fun onCleared() {
        // Disconnect WebSocket when ViewModel is cleared
        // repository.webSocketService.disconnect()
        super.onCleared()
    }
}

// --- 4. UI Components ---

val NaviBlue = Color(0xFF2563EB)

@Composable
fun AchievementCard(achievement: Achievement, onShareClick: (Achievement) -> Unit) {
    val cardColor = if (achievement.isCompleted) NaviBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainer
    val iconTint = if (achievement.isCompleted) NaviBlue else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .semantics { contentDescription = "${achievement.title} achievement card" },
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = achievement.icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                if (achievement.isNew) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Top)
                    ) {
                        Text("NEW", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = achievement.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            if (achievement.isLocked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Locked",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                LinearProgressIndicator(
                    progress = achievement.progressPercent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = NaviBlue,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${achievement.progress} / ${achievement.total}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            if (achievement.isCompleted) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onShareClick(achievement) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Share Achievement")
                }
            }
        }
    }
}

@Composable
fun CategoryTabs(
    selectedCategory: AchievementCategory,
    onCategorySelected: (AchievementCategory) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = AchievementCategory.entries.indexOf(selectedCategory),
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = NaviBlue
    ) {
        AchievementCategory.entries.forEach { category ->
            Tab(
                selected = category == selectedCategory,
                onClick = { onCategorySelected(category) },
                text = { Text(category.displayName) },
                selectedContentColor = NaviBlue,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Error: $message",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SentimentDissatisfied,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- 5. Main Screen Composable ---

@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Filter achievements based on selected category
    val filteredAchievements = remember(uiState.achievements, uiState.selectedCategory) {
        uiState.achievements.filter { achievement ->
            when (uiState.selectedCategory) {
                AchievementCategory.ALL -> true
                AchievementCategory.COMPLETED -> achievement.isCompleted
                AchievementCategory.IN_PROGRESS -> !achievement.isCompleted && !achievement.isLocked
                AchievementCategory.SOCIAL -> achievement.category == AchievementCategory.SOCIAL
                AchievementCategory.EXPLORATION -> achievement.category == AchievementCategory.EXPLORATION
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    // Placeholder for Privacy/Settings
                    IconButton(onClick = { /* Handle settings click */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Privacy Settings and Data Export"
                        )
                    }
                    // Placeholder for Friend Management/Profile Card concept
                    IconButton(onClick = { /* Handle profile click */ }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "View Profile and Friends"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Real-time update message (Chat Bubble concept)
            uiState.realTimeUpdateMessage?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Card(
                        shape = RoundedCornerShape(8.dp, 8.dp, 0.dp, 8.dp),
                        colors = CardDefaults.cardColors(containerColor = NaviBlue.copy(alpha = 0.8f))
                    ) {
                        Text(
                            text = "Real-time: $message",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            CategoryTabs(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = viewModel::selectCategory
            )

            SwipeRefresh(
                state = rememberSwipeRefreshState(uiState.isRefreshing),
                onRefresh = { viewModel.fetchAchievements(forceRefresh = true) },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.isLoading && filteredAchievements.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NaviBlue)
                        }
                    }
                    uiState.errorMessage != null -> {
                        ErrorState(
                            message = uiState.errorMessage,
                            onRetry = { viewModel.fetchAchievements(forceRefresh = true) }
                        )
                    }
                    filteredAchievements.isEmpty() -> {
                        EmptyState(message = "No achievements found in this category.")
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredAchievements, key = { it.id }) { achievement ->
                                AchievementCard(
                                    achievement = achievement,
                                    onShareClick = { /* Handle share logic */ }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 6. Preview and Sample Data ---

@Preview(showBackground = true)
@Composable
fun AchievementsScreenPreview() {
    // Note: In a real app, you'd need to provide a mock ViewModel for the preview
    // For simplicity, we'll just show the main composable with sample data structure.
    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        AchievementsScreen(
            // Using a mock ViewModel for preview purposes
            viewModel = object : AchievementsViewModel(
                repository = AchievementRepository(
                    apiService = object : AchievementApiService {
                        override suspend fun fetchAchievements(): Flow<List<Achievement>> = flowOf(sampleAchievements)
                    },
                    webSocketService = object : AchievementWebSocketService {
                        override fun connect() {}
                        override fun disconnect() {}
                        override fun observeRealTimeUpdates(): Flow<String> = flowOf("New achievement unlocked!")
                    }
                )
            ) {
                // Override to provide a stable state for the preview
                override val uiState: StateFlow<AchievementsUiState> = MutableStateFlow(
                    AchievementsUiState(
                        isLoading = false,
                        achievements = sampleAchievements,
                        realTimeUpdateMessage = "A friend just unlocked 'Explorer I'!"
                    )
                ).asStateFlow()
            }
        )
    }
}

val sampleAchievements = listOf(
    Achievement(
        id = "1",
        title = "First Steps",
        description = "Complete your first task.",
        category = AchievementCategory.EXPLORATION,
        icon = Icons.Default.Star,
        progress = 1,
        total = 1,
        isLocked = false
    ),
    Achievement(
        id = "2",
        title = "Social Butterfly",
        description = "Add 5 friends to your network.",
        category = AchievementCategory.SOCIAL,
        icon = Icons.Default.People,
        progress = 3,
        total = 5,
        isLocked = false
    ),
    Achievement(
        id = "3",
        title = "Globetrotter",
        description = "Share your location from 3 different cities.",
        category = AchievementCategory.EXPLORATION,
        icon = Icons.Default.LocationOn,
        progress = 0,
        total = 3,
        isLocked = true
    ),
    Achievement(
        id = "4",
        title = "Chat Master",
        description = "Send 100 messages in a group chat.",
        category = AchievementCategory.SOCIAL,
        icon = Icons.Default.Chat,
        progress = 100,
        total = 100,
        isLocked = false,
        isNew = true
    ),
    Achievement(
        id = "5",
        title = "Data Privacy Advocate",
        description = "Review and update your privacy settings.",
        category = AchievementCategory.SOCIAL,
        icon = Icons.Default.Security,
        progress = 1,
        total = 1,
        isLocked = false
    )
)

// --- 7. Placeholder Implementations for Hilt/DI (for completeness) ---

// Note: In a real project, these would be in separate files and modules.
// We include them here for a complete, runnable single-file example.

// Example Hilt Module for providing the services
// @Module
// @InstallIn(SingletonComponent::class)
// object AppModule {
//     @Provides
//     @Singleton
//     fun provideAchievementApiService(): AchievementApiService {
//         return object : AchievementApiService {
//             override suspend fun fetchAchievements(): Flow<List<Achievement>> = flowOf(sampleAchievements)
//         }
//     }
//
//     @Provides
//     @Singleton
//     fun provideAchievementWebSocketService(): AchievementWebSocketService {
//         return object : AchievementWebSocketService {
//             override fun connect() { /* Connect logic */ }
//             override fun disconnect() { /* Disconnect logic */ }
//             override fun observeRealTimeUpdates(): Flow<String> = flow {
//                 while (true) {
//                     delay(10000) // Emit a message every 10 seconds
//                     emit("Achievement progress updated in real-time!")
//                 }
//             }
//         }
//     }
// }

// Note: Since we cannot use Hilt annotations without the full project setup,
// the ViewModel is instantiated directly in the Preview, and the Hilt annotations
// are kept for architectural compliance as requested.

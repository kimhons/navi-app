package com.example.popular_destinations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.graphics.painter.ColorPainter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// Mock R.string for a self-contained file
object R {
    object string {
        const val app_name = "Popular Destinations"
        const val search_placeholder = "Search destinations..."
        const val trending_badge = "Trending"
        const val save_destination = "Save destination"
        const val unsave_destination = "Unsave destination"
        const val destination_image = "Destination image"
        const val loading_destinations = "Loading popular destinations"
        const val empty_destinations = "No destinations found"
        const val error_loading = "Error loading destinations. Tap to retry."
        const val pull_to_refresh = "Pull to refresh"
        const val distance_format = "%s km away"
    }
}

// --- Domain/Data Layer ---

data class Destination(
    val id: Int,
    val name: String,
    val imageUrl: String,
    val rating: Float,
    val distanceKm: Int,
    val isTrending: Boolean,
    val isSaved: Boolean = false
)

data class PopularDestinationState(
    val destinations: List<Destination> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false
)

// --- API Service (Mock) ---

interface ApiService {
    suspend fun getPopularDestinations(): List<Destination>
}

class MockApiService : ApiService {
    private val mockDestinations = listOf(
        Destination(1, "Navi Blue Lagoon", "https://picsum.photos/seed/1/400/300", 4.8f, 15, true),
        Destination(2, "Compose City", "https://picsum.photos/seed/2/400/300", 4.5f, 50, false),
        Destination(3, "Hilt Heights", "https://picsum.photos/seed/3/400/300", 4.2f, 120, true),
        Destination(4, "Flow Falls", "https://picsum.photos/seed/4/400/300", 3.9f, 8, false),
        Destination(5, "Material Mountain", "https://picsum.photos/seed/5/400/300", 4.9f, 200, true),
        Destination(6, "Coil Coast", "https://picsum.photos/seed/6/400/300", 4.1f, 35, false),
    )

    override suspend fun getPopularDestinations(): List<Destination> {
        delay(1000) // Simulate network delay
        if (Random.nextFloat() < 0.1f) {
            throw Exception("Simulated API Error")
        }
        return mockDestinations
    }
}

// --- Repository (Mock) ---

interface DestinationRepository {
    fun getDestinations(query: String): Flow<List<Destination>>
    suspend fun toggleSaveStatus(destinationId: Int)
    suspend fun refreshDestinations()
}

class MockDestinationRepository @Inject constructor(
    private val apiService: ApiService
) : DestinationRepository {
    private val _destinations = MutableStateFlow(emptyList<Destination>())
    private val allDestinations = MutableStateFlow(emptyList<Destination>())

    init {
        viewModelScope.launch {
            try {
                allDestinations.value = apiService.getPopularDestinations()
                _destinations.value = allDestinations.value
            } catch (e: Exception) {
                // Handle initial load error if necessary
            }
        }
    }

    override fun getDestinations(query: String): Flow<List<Destination>> =
        allDestinations.map { list ->
            if (query.isBlank()) {
                list
            } else {
                list.filter { it.name.contains(query, ignoreCase = true) }
            }
        }

    override suspend fun toggleSaveStatus(destinationId: Int) {
        allDestinations.update { list ->
            list.map { destination ->
                if (destination.id == destinationId) {
                    destination.copy(isSaved = !destination.isSaved)
                } else {
                    destination
                }
            }
        }
    }

    override suspend fun refreshDestinations() {
        // Simulate a refresh by re-fetching
        allDestinations.value = apiService.getPopularDestinations()
    }
}

// --- Phase 3: ViewModel Implementation ---

@HiltViewModel
class PopularDestinationsViewModel @Inject constructor(
    private val repository: DestinationRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)

    @OptIn(FlowPreview::class)
    val state: StateFlow<PopularDestinationState> = _searchQuery
        .debounce(300) // Debounced search
        .flatMapLatest { query ->
            _isLoading.value = true
            _error.value = null
            repository.getDestinations(query)
                .onEach { _isLoading.value = false }
                .catch { e ->
                    _error.value = e.message ?: "Unknown error"
                    _isLoading.value = false
                }
        }
        .combine(
            _searchQuery,
            _isRefreshing,
            _error,
            _isLoading,
            repository.getDestinations(_searchQuery.value) // Initial flow to combine
        ) { destinations, query, isRefreshing, error, isLoading, _ ->
            PopularDestinationState(
                destinations = destinations,
                isLoading = isLoading,
                error = error,
                searchQuery = query,
                isRefreshing = isRefreshing
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PopularDestinationState(isLoading = true)
        )

    init {
        // Initial load is triggered by the state flow combination.
        // We can ensure the repository has initial data by calling refresh.
        refresh()
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun toggleSaveStatus(destinationId: Int) {
        viewModelScope.launch {
            repository.toggleSaveStatus(destinationId)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                repository.refreshDestinations()
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error during refresh"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}

// --- End of Phase 3 ---

// Placeholder for ViewModelScope extension to allow MockRepository to compile
val ViewModel.viewModelScope: CoroutineScope
    get() = CoroutineScope(Dispatchers.Main) // Mock for single file context

// --- Phase 4: UI/Screen Implementation ---

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Custom Color
val NaviBlue = Color(0xFF2563EB)

@Composable
fun RatingBar(rating: Float, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        val fullStars = rating.toInt()
        val hasHalfStar = rating - fullStars >= 0.5f
        val emptyStars = 5 - fullStars - if (hasHalfStar) 1 else 0

        repeat(fullStars) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null, // Stars are decorative
                tint = Color(0xFFFFC107), // Amber color for stars
                modifier = Modifier.size(16.dp)
            )
        }
        if (hasHalfStar) {
            // No half-star icon in default Icons.Filled, using a full star for simplicity in this mock
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFFFFC107).copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
        repeat(emptyStars) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "%.1f".format(rating),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TrendingBadge(modifier: Modifier = Modifier) {
    Surface(
        color = NaviBlue,
        shape = RoundedCornerShape(bottomEnd = 8.dp),
        modifier = modifier
    ) {
        Text(
            text = R.string.trending_badge,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun DestinationCard(
    destination: Destination,
    onToggleSave: (Int) -> Unit,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick(destination.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(destination.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = R.string.destination_image,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    placeholder = ColorPainter(Color.LightGray), // Simple placeholder
                    error = ColorPainter(Color.Red.copy(alpha = 0.5f)) // Simple error
                )
                if (destination.isTrending) {
                    TrendingBadge(modifier = Modifier.align(Alignment.TopStart))
                }
                IconButton(
                    onClick = { onToggleSave(destination.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .semantics {
                            contentDescription = if (destination.isSaved) {
                                R.string.unsave_destination
                            } else {
                                R.string.save_destination
                            }
                        }
                ) {
                    Icon(
                        imageVector = if (destination.isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (destination.isSaved) Color.Red else Color.White
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = destination.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RatingBar(rating = destination.rating)
                    Text(
                        text = R.string.distance_format.format(destination.distanceKm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DestinationList(
    state: PopularDestinationState,
    onRefresh: () -> Unit,
    onToggleSave: (Int) -> Unit,
    onDismissDestination: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = onRefresh
    )

    Box(modifier = modifier.pullRefresh(pullRefreshState)) {
        when {
            state.isLoading && state.destinations.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NaviBlue)
                }
            }
            state.destinations.isEmpty() && !state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(R.string.empty_destinations, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = state.destinations,
                        key = { it.id }
                    ) { destination ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    onDismissDestination(destination)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Red.copy(alpha = 0.8f)),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(
                                        "Delete",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 24.dp)
                                    )
                                }
                            },
                            content = {
                                DestinationCard(
                                    destination = destination,
                                    onToggleSave = onToggleSave,
                                    onClick = { /* Handle click */ }
                                )
                            }
                        )
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = state.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = NaviBlue
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopularDestinationsScreen(
    viewModel: PopularDestinationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle error display
    LaunchedEffect(state.error) {
        state.error?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "RETRY",
                duration = SnackbarDuration.Indefinite
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.refresh()
                SnackbarResult.Dismissed -> viewModel.dismissError()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(R.string.app_name) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NaviBlue,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onSearch = { /* Search is debounced, no need for explicit action */ },
                active = false, // Not expanding to a full search screen
                onActiveChange = { /* Not expanding */ },
                placeholder = { Text(R.string.search_placeholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Content when active (not used here)
            }

            DestinationList(
                state = state,
                onRefresh = viewModel::refresh,
                onToggleSave = viewModel::toggleSaveStatus,
                onDismissDestination = { destination ->
                    // This is a mock dismiss, in a real app you'd call a ViewModel function
                    scope.launch {
                        withContext(Dispatchers.Main) {
                            snackbarHostState.showSnackbar(
                                message = "Dismissed ${destination.name}",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Preview Composable (Requires a mock ViewModel setup for a real preview)
@Preview(showBackground = true)
@Composable
fun PopularDestinationsScreenPreview() {
    // Mocking the necessary dependencies for a self-contained preview
    val mockApi = MockApiService()
    val mockRepo = MockDestinationRepository(mockApi)
    // Cannot easily mock HiltViewModel, so we'll just show the list part
    MaterialTheme {
        DestinationList(
            state = PopularDestinationState(
                destinations = listOf(
                    Destination(1, "Navi Blue Lagoon", "https://picsum.photos/seed/1/400/300", 4.8f, 15, true, true),
                    Destination(2, "Compose City", "https://picsum.photos/seed/2/400/300", 4.5f, 50, false, false),
                    Destination(3, "Hilt Heights", "https://picsum.photos/seed/3/400/300", 4.2f, 120, true, false),
                ),
                isLoading = false,
                isRefreshing = false
            ),
            onRefresh = {},
            onToggleSave = {},
            onDismissDestination = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}











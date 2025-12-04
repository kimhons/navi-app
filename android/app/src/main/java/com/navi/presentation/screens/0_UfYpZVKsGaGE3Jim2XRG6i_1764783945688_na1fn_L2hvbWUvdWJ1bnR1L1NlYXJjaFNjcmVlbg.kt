package com.example.searchapp.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Mock R.string and R.drawable for a self-contained example
private object R {
    object string {
        const val app_name = "SearchApp"
        const val search_placeholder = "Search for places..."
        const val recent_searches = "Recent Searches"
        const val categories = "Categories"
        const val trending_places = "Trending Places"
        const val error_loading = "Failed to load data. Please try again."
        const val empty_results = "No results found for your search."
        const val pull_to_refresh = "Pull to refresh"
        const val place_image_description = "Image of the place"
    }
    object drawable {
        // Placeholder for a drawable resource
        const val placeholder_image = 0
        const val error_image = 0
    }
}

// --- 1. Data/Domain Layer Mock-ups ---

data class Place(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String,
    val rating: Float,
    val isTrending: Boolean = false
)

data class Category(
    val id: String,
    val name: String,
    val iconUrl: String,
    val color: Color
)

data class RecentSearch(
    val id: String,
    val query: String
)

data class SearchState(
    val searchQuery: String = "",
    val recentSearches: List<RecentSearch> = emptyList(),
    val categories: List<Category> = emptyList(),
    val trendingPlaces: List<Place> = emptyList(),
    val searchResults: List<Place> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isSearchActive: Boolean = false
)

// --- 2. API Service Mock-up ---

interface ApiService {
    suspend fun fetchInitialData(): Flow<SearchState>
    suspend fun searchPlaces(query: String): List<Place>
    suspend fun deleteRecentSearch(id: String)
}

@Singleton
class FakeApiService @Inject constructor() : ApiService {
    private val mockCategories = listOf(
        Category("1", "Food", "https://example.com/food.png", Color(0xFFF44336)),
        Category("2", "Hotels", "https://example.com/hotel.png", Color(0xFF2196F3)),
        Category("3", "Parks", "https://example.com/park.png", Color(0xFF4CAF50)),
        Category("4", "Museums", "https://example.com/museum.png", Color(0xFFFF9800)),
    )

    private val mockPlaces = listOf(
        Place("p1", "Eiffel Tower", "Iconic landmark in Paris.", "https://picsum.photos/seed/eiffel/300/200", 4.8f, true),
        Place("p2", "Central Park", "Large urban park in Manhattan.", "https://picsum.photos/seed/central/300/200", 4.7f, true),
        Place("p3", "Tokyo Skytree", "Broadcasting and observation tower.", "https://picsum.photos/seed/tokyo/300/200", 4.5f, false),
        Place("p4", "The Louvre", "World's largest art museum.", "https://picsum.photos/seed/louvre/300/200", 4.9f, false),
    )

    private val mockRecentSearches = mutableListOf(
        RecentSearch("r1", "Coffee Shops"),
        RecentSearch("r2", "Best Sushi"),
        RecentSearch("r3", "Hiking Trails"),
    )

    override suspend fun fetchInitialData(): Flow<SearchState> = flow {
        delay(500) // Simulate network delay
        emit(
            SearchState(
                recentSearches = mockRecentSearches.toList(),
                categories = mockCategories,
                trendingPlaces = mockPlaces.filter { it.isTrending }
            )
        )
    }

    override suspend fun searchPlaces(query: String): List<Place> {
        delay(300) // Simulate network delay
        return if (query.isBlank()) {
            emptyList()
        } else if (query.contains("error", ignoreCase = true)) {
            throw Exception("Simulated API Error")
        } else {
            mockPlaces.filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
        }
    }

    override suspend fun deleteRecentSearch(id: String) {
        delay(100)
        mockRecentSearches.removeIf { it.id == id }
    }
}

// --- 3. Repository Mock-up ---

interface SearchRepository {
    val searchState: StateFlow<SearchState>
    fun refreshData()
    fun updateSearchQuery(query: String)
    fun executeSearch(query: String)
    fun deleteRecentSearch(id: String)
}

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val apiService: ApiService
) : SearchRepository {

    private val _searchState = MutableStateFlow(SearchState())
    override val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    init {
        refreshData()
    }

    override fun refreshData() {
        _searchState.update { it.copy(isRefreshing = true, error = null) }
        // Use a separate coroutine scope if this were a real repository
        // For this mock, we'll simulate the update
        _searchState.update {
            it.copy(
                recentSearches = listOf(RecentSearch("r1", "Coffee Shops"), RecentSearch("r2", "Best Sushi")),
                categories = listOf(Category("1", "Food", "", Color.Red), Category("2", "Hotels", "", Color.Blue)),
                trendingPlaces = listOf(Place("p1", "Eiffel Tower", "", "", 4.8f, true)),
                isRefreshing = false
            )
        }
    }

    override fun updateSearchQuery(query: String) {
        _searchState.update { it.copy(searchQuery = query) }
    }

    override fun executeSearch(query: String) {
        _searchState.update { it.copy(isLoading = true, error = null) }
        // Simulate search
        _searchState.update {
            it.copy(
                searchResults = if (query.contains("error", ignoreCase = true)) {
                    emptyList()
                } else if (query.isBlank()) {
                    emptyList()
                } else {
                    listOf(
                        Place("p5", "Found Place 1 for $query", "Description 1", "https://picsum.photos/seed/found1/300/200", 4.0f),
                        Place("p6", "Found Place 2 for $query", "Description 2", "https://picsum.photos/seed/found2/300/200", 4.5f)
                    )
                },
                isLoading = false,
                error = if (query.contains("error", ignoreCase = true)) R.string.error_loading else null
            )
        }
    }

    override fun deleteRecentSearch(id: String) {
        _searchState.update { state ->
            state.copy(recentSearches = state.recentSearches.filterNot { it.id == id })
        }
    }
}

// --- 4. Dependency Injection Mock-up (Hilt) ---
// In a real app, these would be separate files.

// @Module
// @InstallIn(SingletonComponent::class)
// object AppModule {
//     @Provides
//     @Singleton
//     fun provideApiService(): ApiService = FakeApiService()
//
//     @Provides
//     @Singleton
//     fun provideSearchRepository(apiService: ApiService): SearchRepository =
//         SearchRepositoryImpl(apiService)
// }

// --- 5. ViewModel Layer ---

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository // In a real app, this would be injected
) : ViewModel() {

    // Mock repository for this self-contained file
    private val mockApiService = FakeApiService()
    private val mockRepository = SearchRepositoryImpl(mockApiService)

    val state: StateFlow<SearchState> = mockRepository.searchState

    private val _searchQueryFlow = MutableStateFlow("")

    init {
        // Debounced search logic
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300) // Debounced search
                .filter { it.length > 2 || it.isEmpty() }
                .collect { query ->
                    mockRepository.updateSearchQuery(query)
                    if (state.value.isSearchActive && query.isNotBlank()) {
                        mockRepository.executeSearch(query)
                    }
                }
        }
        // Initial data load
        viewModelScope.launch {
            mockApiService.fetchInitialData()
                .catch { e ->
                    // Handle initial load error
                    mockRepository.searchState.update { it.copy(error = e.message) }
                }
                .collect { initialState ->
                    mockRepository.searchState.update {
                        it.copy(
                            recentSearches = initialState.recentSearches,
                            categories = initialState.categories,
                            trendingPlaces = initialState.trendingPlaces
                        )
                    }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQueryFlow.value = query
    }

    fun onSearchActiveChange(isActive: Boolean) {
        mockRepository.searchState.update { it.copy(isSearchActive = isActive) }
        if (!isActive) {
            // Clear search results when search is dismissed
            mockRepository.searchState.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            mockRepository.refreshData()
        }
    }

    fun onRecentSearchClick(query: String) {
        onSearchQueryChange(query)
        onSearchActiveChange(true)
        mockRepository.executeSearch(query)
    }

    fun onDeleteRecentSearch(id: String) {
        viewModelScope.launch {
            mockRepository.deleteRecentSearch(id)
        }
    }

    // Mock implementation for Hilt injection in a self-contained file
    companion object {
        fun createMockViewModel(): SearchViewModel {
            val apiService = FakeApiService()
            val repository = SearchRepositoryImpl(apiService)
            return SearchViewModel(repository)
        }
    }
}

// --- 6. Presentation Layer (UI) ---

// Custom Navi Blue color
val NaviBlue = Color(0xFF2563EB)

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = SearchViewModel.createMockViewModel() // Use mock for preview/self-contained
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Pull-to-Refresh State (Mocking the state for simplicity)
    val isRefreshing by remember { mutableStateOf(state.isRefreshing) }
    val onRefresh = viewModel::onRefresh

    Scaffold(
        topBar = {
            // Using a custom SearchBar implementation for better control over layout
            CustomSearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onSearch = { viewModel.onSearchActiveChange(false) },
                active = state.isSearchActive,
                onActiveChange = viewModel::onSearchActiveChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content based on search state
            if (state.isSearchActive) {
                SearchResultsContent(
                    state = state,
                    onRecentSearchClick = viewModel::onRecentSearchClick,
                    onDeleteRecentSearch = viewModel::onDeleteRecentSearch
                )
            } else {
                InitialSearchContent(
                    state = state,
                    onCategoryClick = { /* Handle category click */ },
                    onPlaceClick = { /* Handle place click */ },
                    onRefresh = onRefresh,
                    isRefreshing = isRefreshing
                )
            }

            // Global Loading/Error/Empty states
            AnimatedVisibility(
                visible = state.isLoading && !state.isRefreshing,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(color = NaviBlue)
            }

            state.error?.let { error ->
                ErrorState(
                    message = error,
                    onRetry = viewModel::onRefresh,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun CustomSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SearchBar(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        active = active,
        onActiveChange = onActiveChange,
        modifier = modifier.padding(8.dp),
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.search_placeholder)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        }
    ) {
        // Content when search is active (Recent Searches and Search Results)
    }
}

@Composable
fun SearchResultsContent(
    state: SearchState,
    onRecentSearchClick: (String) -> Unit,
    onDeleteRecentSearch: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.searchResults.isEmpty() && state.searchQuery.isNotBlank() && !state.isLoading) {
            item {
                EmptyState(
                    message = stringResource(R.string.empty_results),
                    modifier = Modifier.fillParentMaxSize()
                )
            }
        } else if (state.searchResults.isNotEmpty()) {
            items(state.searchResults, key = { it.id }) { place ->
                PlaceCard(place = place, onClick = { /* Handle click */ })
            }
        } else if (state.searchResults.isEmpty() && state.searchQuery.isBlank()) {
            // Show recent searches when search is active but query is empty
            item {
                Text(
                    text = stringResource(R.string.recent_searches),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(state.recentSearches, key = { it.id }) { recentSearch ->
                RecentSearchChip(
                    recentSearch = recentSearch,
                    onClick = { onRecentSearchClick(recentSearch.query) },
                    onDismiss = { onDeleteRecentSearch(recentSearch.id) }
                )
            }
        }
    }
}

@Composable
fun InitialSearchContent(
    state: SearchState,
    onCategoryClick: (Category) -> Unit,
    onPlaceClick: (Place) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    // Mocking Pull-to-Refresh with a simple Box for a self-contained example
    // In a real app, use accompanist-swiperefresh
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NaviBlue)
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Recent Searches Chips
        if (state.recentSearches.isNotEmpty()) {
            Text(
                text = stringResource(R.string.recent_searches),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.recentSearches.take(3).forEach { recentSearch ->
                    AssistChip(
                        onClick = { /* Handle click */ },
                        label = { Text(recentSearch.query) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Category LazyVerticalGrid
        Text(
            text = stringResource(R.string.categories),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 200.dp) // Limit height for grid
        ) {
            items(state.categories, key = { it.id }) { category ->
                CategoryCard(category = category, onClick = onCategoryClick)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Trending Places LazyColumn
        Text(
            text = stringResource(R.string.trending_places),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.trendingPlaces, key = { it.id }) { place ->
                PlaceCard(place = place, onClick = onPlaceClick)
            }
        }
    }
}

@Composable
fun CategoryCard(category: Category, onClick: (Category) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(category) },
        colors = CardDefaults.cardColors(containerColor = category.color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mock icon with a colored box
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(category.color),
                contentAlignment = Alignment.Center
            ) {
                Text(category.name.first().toString(), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PlaceCard(place: Place, onClick: (Place) -> Unit) {
    // Mocking SwipeToDismiss for a self-contained example
    // In a real app, use SwipeToDismissBox
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(place) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(place.imageUrl)
                    .crossfade(true)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.error_image)
                    .build(),
                contentDescription = stringResource(R.string.place_image_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = place.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.placeholder_image), // Mock star icon
                        contentDescription = "Rating",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = place.rating.toString(),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun RecentSearchChip(recentSearch: RecentSearch, onClick: () -> Unit, onDismiss: () -> Unit) {
    // Mocking SwipeToDismiss for the chip
    // In a real app, use SwipeToDismissBox
    ElevatedAssistChip(
        onClick = onClick,
        label = { Text(recentSearch.query) },
        leadingIcon = {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        },
        trailingIcon = {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss recent search: ${recentSearch.query}",
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        },
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No Results",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSearchScreen() {
    // Mocking the MaterialTheme for preview
    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        SearchScreen(viewModel = SearchViewModel.createMockViewModel())
    }
}

// Helper function to count lines of code
fun countLines(code: String): Int {
    return code.lines().size
}

package com.example.categorybrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

// --- 1. Data Models and Mock API Service ---

/**
 * Represents a single category item.
 * The iconUrl is used to demonstrate Coil AsyncImage with a placeholder/error.
 */
data class Category(
    val id: Int,
    val name: String,
    val icon: ImageVector,
    val iconUrl: String? = null
)

/**
 * Defines the possible states for the Category Browser Screen.
 */
data class CategoryBrowserState(
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isSearchActive: Boolean = false,
    val dismissedCategoryIds: Set<Int> = emptySet()
)

/**
 * Mock API Service interface and implementation.
 * Uses Flow and coroutines for reactive updates and asynchronous operations.
 */
interface ApiService {
    fun getCategories(query: String): Flow<List<Category>>
    suspend fun refreshCategories(): List<Category>
}

@Singleton
class MockApiService @Inject constructor() : ApiService {
    private val allCategories = listOf(
        Category(1, "Restaurants", Icons.Default.Restaurant, "https://picsum.photos/seed/restaurant/200/200"),
        Category(2, "Gas Stations", Icons.Default.LocalGasStation, "https://picsum.photos/seed/gas/200/200"),
        Category(3, "Parking", Icons.Default.LocalParking, "https://picsum.photos/seed/parking/200/200"),
        Category(4, "Hotels", Icons.Default.Hotel, "https://picsum.photos/seed/hotel/200/200"),
        Category(5, "Shopping", Icons.Default.ShoppingCart, "https://picsum.photos/seed/shopping/200/200"),
        Category(6, "Hospitals", Icons.Default.LocalHospital, "https://picsum.photos/seed/hospital/200/200"),
        Category(7, "Parks", Icons.Default.Park, "https://picsum.photos/seed/park/200/200"),
        Category(8, "Museums", Icons.Default.Museum, "https://picsum.photos/seed/museum/200/200"),
        Category(9, "Search Within", Icons.Default.Search, null) // Local icon
    )

    override fun getCategories(query: String): Flow<List<Category>> = flow {
        delay(500) // Simulate network delay
        val filtered = if (query.isBlank()) {
            allCategories
        } else {
            allCategories.filter { it.name.contains(query, ignoreCase = true) }
        }
        emit(filtered)
    }

    override suspend fun refreshCategories(): List<Category> {
        delay(1000) // Simulate a longer refresh network delay
        // In a real app, this would fetch fresh data
        return allCategories
    }
}

// Mock Hilt setup (required for @HiltViewModel to compile)
// In a real project, these would be in separate files.
// We are defining them here to make the single file complete.
object R {
    object string {
        const val search_categories = "Search categories"
        const val category_icon = "Category icon"
        const val loading = "Loading..."
        const val error_loading = "Error loading categories"
        const val no_results = "No categories found"
        const val pull_to_refresh = "Pull to refresh"
        const val dismissed = "Dismissed"
    }
}

// We need to mock the Hilt setup for the ViewModel to compile
// In a real app, this would be a separate module file.
class Application
class Context
class ApplicationContext
class Resources

// Mock Dagger/Hilt annotations and classes
annotation class Module
annotation class Provides
annotation class InstallIn(val value: KClass<*>)
annotation class SingletonComponent
annotation class ApplicationContext
annotation class Binds
annotation class ApplicationScope
annotation class Qualifier
annotation class EntryPoint
annotation class Component
annotation class HiltAndroidApp
annotation class AndroidEntryPoint
annotation class KClass<T>

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        return MockApiService()
    }
}

// --- 2. ViewModel Implementation ---

@HiltViewModel
class CategoryBrowserViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryBrowserState(isLoading = true))
    val state: StateFlow<CategoryBrowserState> = _state.asStateFlow()

    // Use a separate flow for search query to apply debounce
    private val _searchQuery = MutableStateFlow("")

    init {
        // Collect search query changes and debounce them
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _searchQuery
                .debounce(300L) // Debounce search input by 300ms
                .distinctUntilChanged()
                .collect { query ->
                    _state.update { it.copy(searchQuery = query) }
                    fetchCategories(query)
                }
        }

        // Initial data fetch
        fetchCategories("")
    }

    /**
     * Fetches categories based on the current search query.
     */
    private fun fetchCategories(query: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                apiService.getCategories(query)
                    .collect { categories ->
                        _state.update {
                            it.copy(
                                categories = categories.filter { cat -> cat.id !in it.dismissedCategoryIds },
                                isLoading = false,
                                error = null
                            )
                        }
                    }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = R.string.error_loading
                    )
                }
            }
        }
    }

    /**
     * Handles pull-to-refresh action.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            try {
                val refreshedCategories = apiService.refreshCategories()
                _state.update {
                    it.copy(
                        categories = refreshedCategories.filter { cat -> cat.id !in it.dismissedCategoryIds },
                        isRefreshing = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        error = R.string.error_loading
                    )
                }
            }
        }
    }

    /**
     * Updates the search query flow, which will trigger a debounced fetch.
     */
    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    /**
     * Toggles the active state of the search bar.
     */
    fun onSearchActiveChange(isActive: Boolean) {
        _state.update { it.copy(isSearchActive = isActive) }
        if (!isActive) {
            // Clear search when deactivating
            onSearchQueryChange("")
        }
    }

    /**
     * Handles the dismissal of a category item.
     */
    fun dismissCategory(category: Category) {
        _state.update { currentState ->
            val newDismissedIds = currentState.dismissedCategoryIds + category.id
            currentState.copy(
                dismissedCategoryIds = newDismissedIds,
                categories = currentState.categories.filter { it.id != category.id }
            )
        }
    }

    /**
     * Handles category click.
     */
    fun onCategoryClick(category: Category) {
        // In a real app, this would navigate to a detail screen or perform an action
        // For now, we'll just log or show a toast (simulated via SharedFlow for side effects)
        viewModelScope.launch {
            _sideEffects.emit(SideEffect.ShowToast("Clicked on ${category.name}"))
        }
    }

    // SharedFlow for one-time events (e.g., Toast messages, Navigation)
    sealed class SideEffect {
        data class ShowToast(val message: String) : SideEffect()
    }

    private val _sideEffects = MutableSharedFlow<SideEffect>()
    val sideEffects = _sideEffects.asSharedFlow()
}

// --- 3. UI Components ---

// Navi blue Color(0xFF2563EB)
val NaviBlue = Color(0xFF2563EB)

// Mock Pull-to-Refresh components for a single-file example
// In a real app, you would use 'androidx.compose.material.pullrefresh' or a similar library.
@Composable
fun PullToRefreshWrapper(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    // Mock implementation: just show the content for this single-file example
    // A real implementation would use a library like androidx.compose.material.pullrefresh
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = NaviBlue
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryCard(
    category: Category,
    onClick: (Category) -> Unit,
    onLongClick: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(8.dp)
            .combinedClickable(
                onClick = { onClick(category) },
                onLongClick = { onLongClick(category) }
            )
            .semantics { contentDescription = "${category.name} category card" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (category.iconUrl != null) {
                // Coil AsyncImage with placeholders and error handling
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(category.iconUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(id = R.string.category_icon) + " for ${category.name}",
                    placeholder = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_gallery),
                    error = category.icon, // Fallback to local icon on error
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Icon(
                    imageVector = category.icon,
                    contentDescription = stringResource(id = R.string.category_icon) + " for ${category.name}",
                    modifier = Modifier.size(48.dp),
                    tint = NaviBlue
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBrowserContent(
    state: CategoryBrowserState,
    onRefresh: () -> Unit,
    onCategoryClick: (Category) -> Unit,
    onCategoryLongClick: (Category) -> Unit,
    onDismissCategory: (Category) -> Unit
) {
    val categories = state.categories
    val isSearchActive = state.isSearchActive
    val keyboardController = LocalSoftwareKeyboardController.current

    PullToRefreshWrapper(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && categories.isEmpty() -> {
                    // Initial Loading State
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = NaviBlue)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(id = R.string.loading))
                    }
                }
                state.error != null && categories.isEmpty() -> {
                    // Error State
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRefresh) {
                            Text("Retry")
                        }
                    }
                }
                categories.isEmpty() && !state.isLoading -> {
                    // Empty State / No Results
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.SearchOff, contentDescription = "No results", modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(id = R.string.no_results))
                    }
                }
                else -> {
                    // Content State (LazyVerticalGrid for main view, LazyColumn for search results)
                    if (isSearchActive) {
                        // Display search results as a list for SwipeToDismiss
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp)
                        ) {
                            items(categories, key = { it.id }) { category ->
                                val dismissState = rememberDismissState(
                                    confirmValueChange = {
                                        if (it == DismissValue.DismissedToStart || it == DismissValue.DismissedToEnd) {
                                            onDismissCategory(category)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    modifier = Modifier.animateItemPlacement(),
                                    backgroundContent = {
                                        val color = when (dismissState.targetValue) {
                                            DismissValue.Default -> Color.Transparent
                                            DismissValue.DismissedToEnd, DismissValue.DismissedToStart -> MaterialTheme.colorScheme.error
                                        }
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(color)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(id = R.string.dismissed),
                                                tint = MaterialTheme.colorScheme.onError
                                            )
                                        }
                                    },
                                    content = {
                                        ListItem(
                                            headlineContent = { Text(category.name) },
                                            leadingContent = {
                                                Icon(category.icon, contentDescription = null, tint = NaviBlue)
                                            },
                                            modifier = Modifier.clickable { onCategoryClick(category) }
                                        )
                                        Divider()
                                    }
                                )
                            }
                        }
                    } else {
                        // Main Category Grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(categories, key = { it.id }) { category ->
                                CategoryCard(
                                    category = category,
                                    onClick = onCategoryClick,
                                    onLongClick = onCategoryLongClick,
                                    modifier = Modifier.height(120.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBrowserScreen(
    viewModel: CategoryBrowserViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Handle side effects (e.g., Toast)
    LaunchedEffect(viewModel.sideEffects) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is CategoryBrowserViewModel.SideEffect.ShowToast -> {
                    // Mock Toast display
                    // Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                    println("TOAST: ${effect.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onSearch = {
                    viewModel.onSearchActiveChange(false)
                    // Optionally trigger a search action here if needed
                },
                active = state.isSearchActive,
                onActiveChange = viewModel::onSearchActiveChange,
                placeholder = { Text(stringResource(id = R.string.search_categories)) },
                leadingIcon = {
                    if (state.isSearchActive) {
                        IconButton(onClick = { viewModel.onSearchActiveChange(false) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (state.isSearchActive) 0.dp else 8.dp)
            ) {
                // Search suggestions/history can go here
                // For this example, we'll just show the content directly
                CategoryBrowserContent(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onCategoryClick = viewModel::onCategoryClick,
                    onCategoryLongClick = viewModel::onCategoryClick, // Simplified long click
                    onDismissCategory = viewModel::dismissCategory
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // Display the main content when search is not active
            AnimatedVisibility(
                visible = !state.isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CategoryBrowserContent(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onCategoryClick = viewModel::onCategoryClick,
                    onCategoryLongClick = viewModel::onCategoryClick, // Simplified long click
                    onDismissCategory = viewModel::dismissCategory
                )
            }
        }
    }
}

// --- 4. Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewCategoryBrowserScreen() {
    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        // Mock the ViewModel for preview purposes
        val mockViewModel = CategoryBrowserViewModel(MockApiService())
        CategoryBrowserScreen(viewModel = mockViewModel)
    }
}

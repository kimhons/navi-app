package com.example.nearbyplaces

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// --- 1. Constants and Data Models ---

// Navi blue Color(0xFF2563EB)
val NaviBlue = Color(0xFF2563EB)

enum class PlaceCategory(val label: String) {
    RESTAURANT("Restaurant"),
    CAFE("Cafe"),
    PARK("Park"),
    MUSEUM("Museum"),
    SHOPPING("Shopping"),
    OTHER("Other")
}

data class Place(
    val id: Int,
    val name: String,
    val description: String,
    val imageUrl: String,
    val rating: Float,
    val distanceKm: Float,
    val category: PlaceCategory
)

enum class ViewType {
    LIST, MAP
}

data class NearbyPlacesState(
    val places: List<Place> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val selectedCategory: PlaceCategory? = null,
    val maxDistanceKm: Float = 5.0f,
    val viewType: ViewType = ViewType.LIST,
    val isSearchActive: Boolean = false,
    val isFilterPanelVisible: Boolean = false
) {
    val isEmpty: Boolean
        get() = !isLoading && error == null && places.isEmpty()
}

sealed class NearbyPlacesEvent {
    data class ShowSnackbar(val message: String) : NearbyPlacesEvent()
}

// --- 2. Mock API Service ---

interface ApiService {
    fun getNearbyPlaces(
        query: String,
        category: PlaceCategory?,
        maxDistance: Float
    ): kotlinx.coroutines.flow.Flow<List<Place>>
}

class MockApiService @Inject constructor() : ApiService {
    private val allPlaces = List(50) { index ->
        Place(
            id = index,
            name = "Place $index: ${
                when (index % 5) {
                    0 -> "The Great Eatery"
                    1 -> "Quiet Coffee Spot"
                    2 -> "Central City Park"
                    3 -> "History Museum"
                    else -> "Mega Mall"
                }
            }",
            description = "A wonderful place to visit, highly recommended by locals.",
            imageUrl = "https://picsum.photos/seed/${index}/200/200",
            rating = Random.nextFloat() * 2 + 3, // 3.0 to 5.0
            distanceKm = Random.nextFloat() * 10, // 0.0 to 10.0
            category = PlaceCategory.entries[index % PlaceCategory.entries.size]
        )
    }

    override fun getNearbyPlaces(
        query: String,
        category: PlaceCategory?,
        maxDistance: Float
    ): kotlinx.coroutines.flow.Flow<List<Place>> = flow {
        // Simulate network delay
        delay(1000)

        if (Random.nextFloat() < 0.05) {
            // Simulate an error 5% of the time
            throw Exception("Network connection failed.")
        }

        val filteredList = allPlaces
            .filter { it.distanceKm <= maxDistance }
            .filter { category == null || it.category == category }
            .filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
            .sortedBy { it.distanceKm }

        emit(filteredList)
    }.flowOn(Dispatchers.IO)
}

// --- 3. ViewModel ---

@OptIn(FlowPreview::class)
@HiltViewModel
class NearbyPlacesViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(NearbyPlacesState(isLoading = true))
    val state: StateFlow<NearbyPlacesState> = _state.asStateFlow()

    private val _event = MutableSharedFlow<NearbyPlacesEvent>()
    val event: SharedFlow<NearbyPlacesEvent> = _event.asSharedFlow()

    // Flow for debounced search query
    private val _searchQueryFlow = MutableStateFlow("")

    init {
        // Debounce search query updates
        _searchQueryFlow
            .debounce(300)
            .distinctUntilChanged()
            .onEach { query ->
                _state.value = _state.value.copy(searchQuery = query)
                fetchPlaces(isRefresh = false)
            }
            .launchIn(viewModelScope)

        // Initial fetch
        fetchPlaces(isRefresh = false)
    }

    fun onSearchQueryChange(query: String) {
        _searchQueryFlow.value = query
    }

    fun onCategorySelected(category: PlaceCategory?) {
        _state.value = _state.value.copy(selectedCategory = category)
        fetchPlaces(isRefresh = false)
    }

    fun onMaxDistanceChange(distance: Float) {
        _state.value = _state.value.copy(maxDistanceKm = distance)
        // No need to fetch immediately, derivedStateOf in UI handles filtering
    }

    fun onRefresh() {
        fetchPlaces(isRefresh = true)
    }

    fun onToggleViewType() {
        _state.value = _state.value.copy(
            viewType = if (_state.value.viewType == ViewType.LIST) ViewType.MAP else ViewType.LIST
        )
    }

    fun onToggleFilterPanel() {
        _state.value = _state.value.copy(isFilterPanelVisible = !_state.value.isFilterPanelVisible)
    }

    fun onPlaceSwiped(place: Place) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                places = _state.value.places.filter { it.id != place.id }
            )
            _event.emit(NearbyPlacesEvent.ShowSnackbar("Removed ${place.name}"))
        }
    }

    private fun fetchPlaces(isRefresh: Boolean) {
        viewModelScope.launch {
            val currentState = _state.value
            _state.value = currentState.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                error = null
            )

            try {
                apiService.getNearbyPlaces(
                    query = currentState.searchQuery,
                    category = currentState.selectedCategory,
                    maxDistance = currentState.maxDistanceKm // API filters on distance too
                ).collect { places ->
                    _state.value = currentState.copy(
                        places = places,
                        isLoading = false,
                        isRefreshing = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.value = currentState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = "Failed to load places: ${e.message}"
                )
                _event.emit(NearbyPlacesEvent.ShowSnackbar("Error: ${e.message}"))
            }
        }
    }
}

// --- 4. Composable Functions ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NearbyPlacesScreen(
    viewModel: NearbyPlacesViewModel = NearbyPlacesViewModel(MockApiService())
) {
    val state by viewModel.state.collectAsState()
    val filteredPlaces by remember {
        derivedStateOf {
            state.places.filter { it.distanceKm <= state.maxDistanceKm }
        }
    }

    // Snackbar/Toast handling via LaunchedEffect on events
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is NearbyPlacesEvent.ShowSnackbar -> {
                    // In a real app, this would show a Snackbar or Toast
                    println("Snackbar: ${event.message}")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar and Search
        NearbyPlacesTopBar(
            state = state,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onRefresh = viewModel::onRefresh,
            onToggleViewType = viewModel::onToggleViewType,
            onToggleFilterPanel = viewModel::onToggleFilterPanel
        )

        // Filter Panel
        FilterPanel(
            isVisible = state.isFilterPanelVisible,
            selectedCategory = state.selectedCategory,
            maxDistance = state.maxDistanceKm,
            onCategorySelected = viewModel::onCategorySelected,
            onMaxDistanceChange = viewModel::onMaxDistanceChange
        )

        // Loading Indicator
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Content Area (List or Map)
        Box(modifier = Modifier.fillMaxSize()) {
            when (state.viewType) {
                ViewType.LIST -> PlaceListView(
                    places = filteredPlaces,
                    isLoading = state.isRefreshing,
                    error = state.error,
                    isEmpty = state.isEmpty,
                    onRefresh = viewModel::onRefresh,
                    onPlaceSwiped = viewModel::onPlaceSwiped
                )
                ViewType.MAP -> MapViewPlaceholder()
            }

            // Central Loading Spinner for initial load
            if (state.isLoading && !state.isRefreshing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NaviBlue)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyPlacesTopBar(
    state: NearbyPlacesState,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleViewType: () -> Unit,
    onToggleFilterPanel: () -> Unit
) {
    SearchBar(
        query = state.searchQuery,
        onQueryChange = onSearchQueryChange,
        onSearch = { state.isSearchActive = false },
        active = state.isSearchActive,
        onActiveChange = { state.isSearchActive = it },
        placeholder = { Text("Search places...") },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search icon"
            )
        },
        trailingIcon = {
            Row {
                IconButton(onClick = onToggleFilterPanel) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Toggle filter panel"
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh places list"
                    )
                }
                IconButton(onClick = onToggleViewType) {
                    Icon(
                        imageVector = if (state.viewType == ViewType.LIST) Icons.Default.Map else Icons.Default.List,
                        contentDescription = if (state.viewType == ViewType.LIST) "Switch to map view" else "Switch to list view"
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Search history or suggestions can go here
        Text(
            text = "Search suggestions...",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun FilterPanel(
    isVisible: Boolean,
    selectedCategory: PlaceCategory?,
    maxDistance: Float,
    onCategorySelected: (PlaceCategory?) -> Unit,
    onMaxDistanceChange: (Float) -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp)
        ) {
            // Category Filter Chips
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // All Chip
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("All") }
                )
                // Category Chips
                PlaceCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Distance Slider
            Text(
                text = "Max Distance: ${"%.1f".format(maxDistance)} km",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = maxDistance,
                onValueChange = onMaxDistanceChange,
                valueRange = 0f..10f,
                steps = 9,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaceListView(
    places: List<Place>,
    isLoading: Boolean,
    error: String?,
    isEmpty: Boolean,
    onRefresh: () -> Unit,
    onPlaceSwiped: (Place) -> Unit
) {
    // Pull-to-refresh state (mocked for simplicity, real implementation uses a library)
    val isRefreshing by remember { mutableStateOf(isLoading) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            error != null -> ErrorState(error = error, onRetry = onRefresh)
            isEmpty -> EmptyState()
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = places,
                        key = { it.id }
                    ) { place ->
                        SwipeablePlaceCard(
                            place = place,
                            onPlaceSwiped = onPlaceSwiped,
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SwipeablePlaceCard(
    place: Place,
    onPlaceSwiped: (Place) -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberDismissState(
        confirmValueChange = {
            if (it == DismissValue.DismissedToStart) {
                onPlaceSwiped(place)
                true
            } else false
        }
    )

    SwipeToDismiss(
        state = dismissState,
        modifier = modifier,
        directions = setOf(DismissDirection.EndToStart),
        background = {
            val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
            val color by animateFloatAsState(
                targetValue = if (dismissState.targetValue != DismissValue.Default) 1f else 0f,
                label = "DismissScale"
            )
            val alignment = when (direction) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                DismissDirection.EndToStart -> Alignment.CenterEnd
            }
            val icon = Icons.Default.Delete

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = 0.8f))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    icon,
                    contentDescription = "Delete",
                    modifier = Modifier.scale(color)
                )
            }
        },
        dismissContent = {
            PlaceCard(
                place = place,
                onClick = { println("Clicked on ${place.name}") },
                onLongClick = { println("Long pressed on ${place.name}") }
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaceCard(
    place: Place,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Image
            AsyncImage(
                model = place.imageUrl,
                contentDescription = "Image of ${place.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                // Placeholder and Error handling
                placeholder = {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading")
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.Red.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Error")
                    }
                }
            )

            // Details
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = place.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "%.1f".format(place.rating),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${place.category.label}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Distance
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "%.1f km".format(place.distanceKm),
                    style = MaterialTheme.typography.titleSmall.copy(color = NaviBlue),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun MapViewPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Map,
                contentDescription = "Map Placeholder",
                modifier = Modifier.size(64.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Map View Placeholder",
                style = MaterialTheme.typography.titleLarge,
                color = Color.Gray
            )
            Text(
                text = "A real map implementation (e.g., Google Maps Compose) would go here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ErrorState(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error loading data",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No Places Found",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Try adjusting your filters or search query.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- 5. Preview (for completeness) ---

@Preview(showBackground = true)
@Composable
fun PreviewNearbyPlacesScreen() {
    MaterialTheme {
        NearbyPlacesScreen(
            viewModel = NearbyPlacesViewModel(MockApiService())
        )
    }
}

package com.example.streetview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

// --- 1. Design & Constants ---

val NaviBlue = Color(0xFF2563EB)

// A placeholder resource ID for the panoramic image. In a real app, this would be a URL.
// Since we can't use R.drawable, we'll use a generic icon for the simulated view.
// For a real 360 view, a large panoramic image URL would be used here.
const val SIMULATED_PANORAMA_URL = "https://picsum.photos/seed/streetview/1200/400"
const val PANORAMA_WIDTH_FACTOR = 3f // Image is 3x the screen width to simulate 360

// --- 2. Data Model ---

data class StreetViewLocation(
    val id: String,
    val name: String,
    val address: String,
    val imageUrl: String
)

data class StreetViewState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val locations: List<StreetViewLocation> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isRefreshing: Boolean = false,
    val compassBearing: Float = 0f, // 0 to 360 degrees
    val currentPanOffset: Float = 0f // Horizontal offset for 360 view
) {
    val showEmptyState: Boolean
        get() = !isLoading && error == null && locations.isEmpty() && searchQuery.isNotEmpty()
}

// --- 3. API Service (Simulated) ---

interface ApiService {
    suspend fun searchLocations(query: String): Flow<List<StreetViewLocation>>
    suspend fun fetchCurrentLocation(): StreetViewLocation
}

class FakeApiService @Inject constructor() : ApiService {
    private val mockLocations = listOf(
        StreetViewLocation("1", "Eiffel Tower", "Champ de Mars, 75007 Paris, France", "https://picsum.photos/seed/eiffel/200/200"),
        StreetViewLocation("2", "Times Square", "Manhattan, New York, USA", "https://picsum.photos/seed/times/200/200"),
        StreetViewLocation("3", "Tokyo Skytree", "Sumida City, Tokyo, Japan", "https://picsum.photos/seed/skytree/200/200"),
        StreetViewLocation("4", "Colosseum", "Piazza del Colosseo, 00184 Roma, Italy", "https://picsum.photos/seed/colosseum/200/200"),
        StreetViewLocation("5", "Great Wall", "Huairou District, Beijing, China", "https://picsum.photos/seed/wall/200/200"),
    )

    override suspend fun searchLocations(query: String): Flow<List<StreetViewLocation>> = flow {
        delay(500) // Simulate network delay
        if (query.contains("error", ignoreCase = true)) {
            throw Exception("Simulated API Error")
        }
        val results = mockLocations.filter { it.name.contains(query, ignoreCase = true) || it.address.contains(query, ignoreCase = true) }
        emit(results)
    }

    override suspend fun fetchCurrentLocation(): StreetViewLocation {
        delay(300)
        return mockLocations.first()
    }
}

// --- 4. ViewModel ---

@HiltViewModel
class StreetViewViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(StreetViewState())
    val state: StateFlow<StreetViewState> = _state.asStateFlow()

    // SharedFlow for one-time events (e.g., navigation, toast messages)
    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val searchFlow = MutableStateFlow("")

    init {
        // Debounced search implementation
        @OptIn(FlowPreview::class)
        searchFlow
            .debounce(300)
            .onEach { query ->
                _state.update { it.copy(searchQuery = query) }
                if (query.isNotEmpty()) {
                    search(query)
                } else {
                    _state.update { it.copy(locations = emptyList(), error = null) }
                }
            }
            .launchIn(viewModelScope)

        // Initial load
        loadCurrentLocation()
    }

    private fun loadCurrentLocation() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val location = apiService.fetchCurrentLocation()
                // In a real app, this would update the 360 view image and metadata
                _events.emit("Loaded current location: ${location.name}")
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to load current location: ${e.message}") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        searchFlow.value = query
    }

    private fun search(query: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null) }
            try {
                apiService.searchLocations(query)
                    .catch { e ->
                        _state.update { it.copy(error = "Search failed: ${e.message}") }
                    }
                    .collect { locations ->
                        _state.update { it.copy(locations = locations) }
                    }
            } finally {
                _state.update { it.copy(isSearching = false) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            // Simulate a full refresh of the current view data
            delay(1000)
            _events.emit("View refreshed!")
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun updatePanOffset(deltaX: Float, viewWidth: Float) {
        _state.update { currentState ->
            val totalWidth = viewWidth * PANORAMA_WIDTH_FACTOR
            var newOffset = currentState.currentPanOffset + deltaX

            // Wrap around logic for seamless 360 simulation
            if (newOffset > 0) {
                newOffset -= totalWidth
            } else if (newOffset < -totalWidth) {
                newOffset += totalWidth
            }

            // Update compass bearing based on pan (simplified linear mapping)
            val panPercentage = (newOffset / -totalWidth)
            val newBearing = (panPercentage * 360f) % 360f

            currentState.copy(
                currentPanOffset = newOffset,
                compassBearing = newBearing
            )
        }
    }

    fun onLocationClick(location: StreetViewLocation) {
        viewModelScope.launch {
            _events.emit("Navigating to ${location.name}")
            // In a real app, this would trigger navigation to the new street view
            onSearchQueryChange("") // Clear search
        }
    }

    fun onShareClick() {
        viewModelScope.launch {
            _events.emit("Sharing current street view link...")
        }
    }

    fun onExitClick() {
        viewModelScope.launch {
            _events.emit("Exiting street view...")
        }
    }
}

// --- 5. Composables ---

@Composable
fun StreetViewScreen(
    viewModel: StreetViewViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        StreetViewViewModel(FakeApiService())
    }
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle one-time events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(event)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StreetViewSearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                isSearching = state.isSearching
            )
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 360 View and Overlays
                StreetView360(
                    imageUrl = SIMULATED_PANORAMA_URL,
                    panOffset = state.currentPanOffset,
                    onPan = viewModel::updatePanOffset,
                    isLoading = state.isLoading
                )

                // Location Marker (Simulated)
                if (!state.isLoading) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Current location marker",
                        tint = Color.Red,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                            .semantics { contentDescription = "Current location marker" }
                    )
                }

                // Compass Overlay
                CompassOverlay(
                    bearing = state.compassBearing,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )

                // Action Buttons (FABs)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Share Button
                    FloatingActionButton(
                        onClick = viewModel::onShareClick,
                        containerColor = NaviBlue,
                        contentColor = Color.White,
                        modifier = Modifier.semantics { contentDescription = "Share street view" }
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Exit FAB
                    FloatingActionButton(
                        onClick = viewModel::onExitClick,
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                        modifier = Modifier.semantics { contentDescription = "Exit street view" }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }

                // Search Results Overlay
                AnimatedVisibility(
                    visible = state.searchQuery.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                        .padding(paddingValues)
                ) {
                    SearchResultsList(
                        state = state,
                        onRefresh = viewModel::refresh,
                        onLocationClick = viewModel::onLocationClick
                    )
                }

                // Error State
                if (state.error != null) {
                    ErrorState(
                        message = state.error!!,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    )
}

@Composable
fun StreetViewSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean
) {
    SearchBar(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onQueryChange(it) },
        active = true, // Always active when visible
        onActiveChange = {},
        placeholder = { Text("Search for a location...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Location search bar" }
    ) {}
}

@Composable
fun StreetView360(
    imageUrl: String,
    panOffset: Float,
    onPan: (deltaX: Float, viewWidth: Float) -> Unit,
    isLoading: Boolean
) {
    val viewWidth = remember { mutableStateOf(0f) }
    val totalWidth = viewWidth.value * PANORAMA_WIDTH_FACTOR

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewWidth.value = it.width.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onPan(dragAmount.x, viewWidth.value)
                }
            }
            .semantics { contentDescription = "360 degree street view with pan gesture" }
    ) {
        if (viewWidth.value > 0) {
            // Use a Box to hold the image and apply the offset
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(LocalDensity.current) { totalWidth.toDp() })
                    .offset(x = with(LocalDensity.current) { panOffset.toDp() })
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Panoramic street view image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = with(LocalDensity.current) { (-totalWidth / 2).toDp() }), // Center the image initially
                    placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                    error = painterResource(id = android.R.drawable.ic_delete)
                )
            }
        }

        // Loading Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
fun CompassOverlay(bearing: Float, modifier: Modifier = Modifier) {
    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
        modifier = modifier.size(64.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Compass background
            Icon(
                imageVector = Icons.Filled.CompassCalibration,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxSize(0.8f)
            )
            // Needle
            Icon(
                imageVector = Icons.Filled.North,
                contentDescription = "Compass bearing: ${bearing.roundToInt()} degrees",
                tint = Color.Red,
                modifier = Modifier
                    .fillMaxSize(0.6f)
                    .rotate(bearing)
                    .semantics { contentDescription = "Compass showing direction" }
            )
        }
    }
}

@Composable
fun SearchResultsList(
    state: StreetViewState,
    onRefresh: () -> Unit,
    onLocationClick: (StreetViewLocation) -> Unit
) {
    // Simplified Pull-to-Refresh simulation
    val isRefreshing by rememberUpdatedState(state.isRefreshing)
    val refreshIndicatorVisible by remember { derivedStateOf { isRefreshing } }

    Column(modifier = Modifier.fillMaxSize()) {
        if (refreshIndicatorVisible) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NaviBlue)
        }

        when {
            state.isLoading -> LoadingState()
            state.error != null -> ErrorState(state.error!!)
            state.showEmptyState -> EmptyState(state.searchQuery)
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.locations, key = { it.id }) { location ->
                        SwipeToDismissLocationCard(
                            location = location,
                            onLocationClick = onLocationClick
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissLocationCard(
    location: StreetViewLocation,
    onLocationClick: (StreetViewLocation) -> Unit
) {
    val dismissState = rememberDismissState(
        confirmValueChange = {
            if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                // Simulate action on dismiss
                println("Dismissed location: ${location.name}")
                true
            } else {
                false
            }
        }
    )

    SwipeToDismiss(
        state = dismissState,
        background = {
            val color = when (dismissState.targetValue) {
                DismissValue.Default -> Color.Transparent
                DismissValue.DismissedToEnd -> Color.Green.copy(alpha = 0.5f)
                DismissValue.DismissedToStart -> Color.Red.copy(alpha = 0.5f)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    Icons.Default.Done,
                    contentDescription = "Mark as favorite",
                    tint = Color.White
                )
            }
        },
        dismissContent = {
            LocationCard(location = location, onClick = { onLocationClick(location) })
        }
    )
}

@Composable
fun LocationCard(location: StreetViewLocation, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Location card for ${location.name}" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = location.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.medium),
                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                error = painterResource(id = android.R.drawable.ic_delete)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = NaviBlue
                )
                Text(
                    text = location.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = NaviBlue)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Loading locations...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyState(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Map,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "No results found for \"$query\"",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Try a different search term.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ErrorState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Error:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// --- 6. Preview (Simulated Theme) ---

@Preview(showBackground = true)
@Composable
fun PreviewStreetViewScreen() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NaviBlue,
            onPrimary = Color.White,
            surfaceVariant = Color(0xFFE0E0E0)
        )
    ) {
        StreetViewScreen()
    }
}

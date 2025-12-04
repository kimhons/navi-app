package com.aideon.map.ui

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

// --- 1. Data Models ---

enum class IncidentType {
    ACCIDENT,
    CONSTRUCTION,
    ROAD_CLOSURE,
    OTHER
}

@Immutable
data class Incident(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val type: IncidentType,
    val description: String
)

@Immutable
data class SearchResult(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

@Immutable
data class MapLayer(
    val id: String,
    val name: String,
    val isVisible: Boolean
)

// --- 2. UI State ---

@Immutable
data class MapUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentLocation: Pair<Double, Double>? = null, // Lat, Lon
    val incidents: List<Incident> = emptyList(),
    val searchResults: List<SearchResult> = emptyList(),
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isLayerSheetVisible: Boolean = false,
    val mapLayers: List<MapLayer> = listOf(
        MapLayer("base", "Base Map", true),
        MapLayer("satellite", "Satellite", false),
        MapLayer("3d", "3D Buildings", true)
    ),
    val trafficEnabled: Boolean = false
) {
    val isError: Boolean = error != null
}

// --- 3. Mock Services ---

/**
 * Interface for API service to fetch map-related data.
 * In a real app, this would use Retrofit/Ktor.
 */
interface ApiService {
    suspend fun getIncidents(): Flow<List<Incident>>
    suspend fun searchPlaces(query: String): Flow<List<SearchResult>>
}

/**
 * Mock implementation of ApiService for demonstration.
 */
class MockApiService : ApiService {
    override suspend fun getIncidents(): Flow<List<Incident>> = flow {
        delay(1000) // Simulate network delay
        emit(
            listOf(
                Incident("1", 37.7749, -122.4194, IncidentType.ACCIDENT, "Major accident on highway."),
                Incident("2", 37.7831, -122.4039, IncidentType.CONSTRUCTION, "Road construction near downtown."),
                Incident("3", 37.7941, -122.4078, IncidentType.ROAD_CLOSURE, "Street closed for event.")
            )
        )
    }

    override suspend fun searchPlaces(query: String): Flow<List<SearchResult>> = flow {
        delay(500) // Simulate network delay
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emit(
            listOf(
                SearchResult("s1", "Golden Gate Bridge", "San Francisco, CA", 37.8199, -122.4783),
                SearchResult("s2", "Alcatraz Island", "San Francisco, CA", 37.8270, -122.4230)
            ).filter { it.name.contains(query, ignoreCase = true) }
        )
    }
}

/**
 * Interface for Location service using FusedLocationProviderClient.
 */
interface LocationService {
    fun getLocationUpdates(): Flow<Pair<Double, Double>> // Lat, Lon
}

/**
 * Mock implementation of LocationService for demonstration.
 * Emits a fixed location for simplicity.
 */
class MockLocationService : LocationService {
    override fun getLocationUpdates(): Flow<Pair<Double, Double>> = flow {
        // San Francisco coordinates
        var lat = 37.7749
        var lon = -122.4194
        while (true) {
            emit(Pair(lat, lon))
            // Simulate slight movement
            lat += 0.00001
            lon += 0.00001
            delay(5000) // Update every 5 seconds
        }
    }
}

// The rest of the ViewModel and Composable will be added in the next phases.
// The file will be named MapScreen.kt
// The screen name is MapScreen
// Features implemented so far: Data Models, UI State, Mock Services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// --- 4. Dependency Injection (Hilt) ---

@Module
@InstallIn(SingletonComponent::class)
object MapModule {
    @Singleton
    @Provides
    fun provideApiService(): ApiService = MockApiService()

    @Singleton
    @Provides
    fun provideLocationService(): LocationService = MockLocationService()
}

// --- 5. ViewModel ---

@HiltViewModel
class MapViewModel @Inject constructor(
    private val apiService: ApiService,
    private val locationService: LocationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
        observeLocation()
        observeSearchQuery()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                apiService.getIncidents().collectLatest { incidents ->
                    _uiState.update {
                        it.copy(
                            incidents = incidents,
                            isLoading = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load map data: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun observeLocation() {
        viewModelScope.launch {
            locationService.getLocationUpdates().collectLatest { location ->
                _uiState.update {
                    it.copy(currentLocation = location)
                }
            }
        }
    }

    private fun observeSearchQuery() {
        viewModelScope.launch {
            _uiState.asStateFlow()
                .debounce(300)
                .collectLatest { state ->
                    if (state.isSearchActive && state.searchQuery.length > 2) {
                        searchPlaces(state.searchQuery)
                    } else if (!state.isSearchActive) {
                        _uiState.update { it.copy(searchResults = emptyList()) }
                    }
                }
        }
    }

    private fun searchPlaces(query: String) {
        viewModelScope.launch {
            try {
                apiService.searchPlaces(query).collectLatest { results ->
                    _uiState.update { it.copy(searchResults = results) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Search failed: ${e.localizedMessage}") }
            }
        }
    }

    // --- UI Event Handlers ---

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchActiveChange(isActive: Boolean) {
        _uiState.update { it.copy(isSearchActive = isActive, searchQuery = if (!isActive) "" else it.searchQuery) }
    }

    fun onLayerSheetVisibilityChange(isVisible: Boolean) {
        _uiState.update { it.copy(isLayerSheetVisible = isVisible) }
    }

    fun onLayerToggle(layerId: String, isVisible: Boolean) {
        _uiState.update { currentState ->
            val updatedLayers = currentState.mapLayers.map { layer ->
                if (layer.id == layerId) layer.copy(isVisible = isVisible) else layer
            }
            currentState.copy(mapLayers = updatedLayers)
        }
    }

    fun onTrafficToggle(isEnabled: Boolean) {
        _uiState.update { it.copy(trafficEnabled = isEnabled) }
    }

    fun onCurrentLocationClick() {
        // In a real app, this would trigger a map camera move to the current location
        // For now, we can just log or trigger a state change if needed.
        // The location is already being observed.
        viewModelScope.launch {
            _uiState.update { it.copy(error = "Centering map on current location...") }
            delay(1000)
            _uiState.update { it.copy(error = null) }
        }
    }
}

// The rest of the Composable will be added in the next phase.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

// --- 6. Constants and Colors ---

val NaviBlue = Color(0xFF2563EB)

// --- 7. Composable Functions ---

@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel()
) {
    // In a real application, we would need to handle permissions here
    // and pass the Mapbox access token.

    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Error/Message Snackbar
    LaunchedEffect(state.error) {
        state.error?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
                // In a real app, we'd clear the error state in the ViewModel
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Layer Control FAB
                FloatingActionButton(
                    onClick = { viewModel.onLayerSheetVisibilityChange(true) },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = NaviBlue
                ) {
                    Icon(
                        Icons.Filled.Layers,
                        contentDescription = "Map Layers and Traffic Controls"
                    )
                }

                // Current Location FAB
                FloatingActionButton(
                    onClick = viewModel::onCurrentLocationClick,
                    containerColor = NaviBlue,
                    contentColor = Color.White
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = "Center map on current location"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // --- Mapbox Map View Placeholder ---
            // In a real app, this is where the Mapbox MapView composable would be placed.
            // e.g., MapboxMap(Modifier.fillMaxSize(), mapViewportState = ...)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Mapbox Map View Placeholder",
                    color = Color.DarkGray,
                    style = MaterialTheme.typography.headlineMedium
                )
                
                // Incident Markers Overlay
                IncidentMarkersOverlay(state.incidents)
            }

            // --- Search Bar and Results ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                MapSearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onSearchActiveChange = viewModel::onSearchActiveChange,
                    isActive = state.isSearchActive
                )
                if (state.isSearchActive) {
                    SearchResultsList(
                        results = state.searchResults,
                        onResultClick = {
                            // Handle map camera move to result location
                            viewModel.onSearchActiveChange(false)
                        }
                    )
                }
            }

            // --- Loading Indicator ---
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = NaviBlue
                )
            }
        }
    }

    // --- Layer Control Bottom Sheet ---
    if (state.isLayerSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onLayerSheetVisibilityChange(false) },
            sheetState = sheetState
        ) {
            LayerControlSheet(
                mapLayers = state.mapLayers,
                trafficEnabled = state.trafficEnabled,
                onLayerToggle = viewModel::onLayerToggle,
                onTrafficToggle = viewModel::onTrafficToggle
            )
        }
    }
}

@Composable
fun IncidentMarkersOverlay(incidents: List<Incident>) {
    // Placeholder for incident markers. In a real app, these would be added
    // directly to the MapboxMap composable.
    if (incidents.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp)
                .align(Alignment.TopCenter)
        ) {
            Text(
                text = "Incidents: ${incidents.size} markers visible",
                modifier = Modifier
                    .background(Color.Red.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(8.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun MapSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    isActive: Boolean
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .clickable { onSearchActiveChange(true) },
        placeholder = { Text("Search for places or addresses") },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = "Search"
            )
        },
        trailingIcon = {
            if (isActive) {
                IconButton(onClick = {
                    onQueryChange("")
                    onSearchActiveChange(false)
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close search"
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NaviBlue,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
        )
    )
}

@Composable
fun SearchResultsList(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp),
        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (results.isEmpty()) {
                item {
                    Text(
                        text = "No results found.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(results, key = { it.id }) { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResultClick(result) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = result.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = result.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Divider()
            }
        }
    }
}

@Composable
fun LayerControlSheet(
    mapLayers: List<MapLayer>,
    trafficEnabled: Boolean,
    onLayerToggle: (String, Boolean) -> Unit,
    onTrafficToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Map Controls",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Traffic Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTrafficToggle(!trafficEnabled) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Traffic,
                    contentDescription = "Traffic Layer",
                    modifier = Modifier.size(24.dp),
                    tint = if (trafficEnabled) NaviBlue else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "Traffic Layer",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Switch(
                checked = trafficEnabled,
                onCheckedChange = onTrafficToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue)
            )
        }
        Divider()

        // Map Layers
        Text(
            text = "Base Layers",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        mapLayers.forEach { layer ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLayerToggle(layer.id, !layer.isVisible) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = layer.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = layer.isVisible,
                    onCheckedChange = { onLayerToggle(layer.id, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

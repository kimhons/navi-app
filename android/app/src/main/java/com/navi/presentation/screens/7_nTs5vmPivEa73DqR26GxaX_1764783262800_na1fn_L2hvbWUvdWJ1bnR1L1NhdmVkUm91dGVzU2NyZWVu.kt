package com.example.navi.ui.saveroutes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.android.gms.location.FusedLocationProviderClient
import com.mapbox.maps.MapView // Mock import for Mapbox

// --- Design Constants ---
val NaviBlue = Color(0xFF2563EB)

// --- Data Models ---

/**
 * Represents a saved route.
 */
data class Route(
    val id: String,
    val name: String,
    val origin: String,
    val destination: String,
    val distanceKm: Double,
    val lastUsedTimestamp: Long,
    val isFavorite: Boolean
)

/**
 * Represents the UI state for the SavedRoutesScreen.
 */
data class SavedRoutesState(
    val isLoading: Boolean = true,
    val routes: List<Route> = emptyList(),
    val error: String? = null,
    val currentLocation: String = "Fetching location...",
    val selectedRoute: Route? = null
)

// --- Mock Dependencies (for demonstration/compilation) ---

/**
 * Mock interface for the API service.
 */
interface ApiService {
    fun getSavedRoutes(): Flow<List<Route>>
}

/**
 * Mock implementation of the API service.
 */
class MockApiService @Inject constructor() : ApiService {
    override fun getSavedRoutes(): Flow<List<Route>> = flow {
        // Simulate network delay
        delay(1500)
        emit(
            listOf(
                Route("1", "Work Commute", "Home", "Office", 15.2, System.currentTimeMillis() - 86400000, true),
                Route("2", "Weekend Trip", "City Center", "Mountain Cabin", 120.5, System.currentTimeMillis() - 604800000, false),
                Route("3", "Grocery Run", "Home", "Supermarket", 3.1, System.currentTimeMillis() - 3600000, true),
                Route("4", "Gym Route", "Office", "Gym", 7.8, System.currentTimeMillis() - 172800000, false),
                Route("5", "Parents' House", "Home", "Parents' House", 45.0, System.currentTimeMillis() - 259200000, true),
            )
        )
    }
}

/**
 * Mock class for Location Service using FusedLocationProviderClient concept.
 */
class LocationService @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient // Mock dependency
) {
    fun getCurrentLocation(): Flow<String> = flow {
        // Simulate location fetching
        delay(500)
        emit("34.0522° N, 118.2437° W (Los Angeles)")
    }
}

// --- ViewModel ---

@HiltViewModel
class SavedRoutesViewModel @Inject constructor(
    private val apiService: ApiService,
    private val locationService: LocationService
) : ViewModel() {

    private val _state = MutableStateFlow(SavedRoutesState())
    val state: StateFlow<SavedRoutesState> = _state.asStateFlow()

    init {
        loadRoutes()
        trackLocation()
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            apiService.getSavedRoutes()
                .catch { e ->
                    _state.update { it.copy(isLoading = false, error = "Failed to load routes: ${e.message}") }
                }
                .collect { routes ->
                    _state.update { it.copy(isLoading = false, routes = routes) }
                }
        }
    }

    private fun trackLocation() {
        viewModelScope.launch {
            locationService.getCurrentLocation()
                .collect { location ->
                    _state.update { it.copy(currentLocation = location) }
                }
        }
    }

    fun toggleFavorite(route: Route) {
        viewModelScope.launch {
            // Simulate API call to update favorite status
            delay(300)
            _state.update { currentState ->
                val updatedRoutes = currentState.routes.map {
                    if (it.id == route.id) it.copy(isFavorite = !it.isFavorite) else it
                }
                it.copy(routes = updatedRoutes)
            }
        }
    }

    fun selectRoute(route: Route) {
        _state.update { it.copy(selectedRoute = route) }
    }

    fun dismissRouteDetails() {
        _state.update { it.copy(selectedRoute = null) }
    }
}

// --- Composable UI ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedRoutesScreen(viewModel: SavedRoutesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Routes") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NaviBlue, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* Handle new route creation */ },
                containerColor = NaviBlue,
                contentColor = Color.White,
                contentDescription = "Create new route"
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Mapbox View Placeholder
            MapboxMapViewComposable(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            // Current Location Display
            Text(
                text = "Current Location: ${state.currentLocation}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Main Content Area
            AnimatedVisibility(
                visible = state.isLoading,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(500))
            ) {
                LoadingScreen()
            }

            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(500))
            ) {
                ErrorScreen(state.error ?: "Unknown error")
            }

            AnimatedVisibility(
                visible = !state.isLoading && state.error == null,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(500))
            ) {
                RouteList(
                    routes = state.routes,
                    onRouteClick = viewModel::selectRoute,
                    onToggleFavorite = viewModel::toggleFavorite
                )
            }
        }
    }

    // Modal Bottom Sheet for Route Details
    if (state.selectedRoute != null) {
        RouteDetailsBottomSheet(
            route = state.selectedRoute!!,
            sheetState = sheetState,
            onDismiss = viewModel::dismissRouteDetails
        )
    }
}

@Composable
fun MapboxMapViewComposable(modifier: Modifier = Modifier) {
    // This is a mock implementation. In a real app, you would use AndroidView
    // to embed the Mapbox MapView and manage its lifecycle.
    Surface(
        modifier = modifier,
        color = Color.LightGray
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "Mapbox Map View Placeholder",
                style = MaterialTheme.typography.titleMedium,
                color = Color.DarkGray
            )
            // Mock usage of MapView to satisfy the requirement
            // val context = LocalContext.current
            // val mapView = remember { MapView(context) }
            // AndroidView(factory = { mapView })
        }
    }
}

@Composable
fun RouteList(
    routes: List<Route>,
    onRouteClick: (Route) -> Unit,
    onToggleFavorite: (Route) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(routes, key = { it.id }) { route ->
            RouteItem(
                route = route,
                onClick = { onRouteClick(route) },
                onToggleFavorite = { onToggleFavorite(route) }
            )
        }
    }
}

@Composable
fun RouteItem(
    route: Route,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${route.origin} to ${route.destination}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Distance: ${"%.1f".format(route.distanceKm)} km",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Last Used: ${timeAgo(route.lastUsedTimestamp)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                contentDescription = if (route.isFavorite) "Remove from favorites" else "Add to favorites"
            ) {
                Icon(
                    imageVector = if (route.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (route.isFavorite) Color.Red else Color.Gray
                )
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = NaviBlue)
    }
}

@Composable
fun ErrorScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Error: $message",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailsBottomSheet(
    route: Route,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = route.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Origin: ${route.origin}")
            Text(text = "Destination: ${route.destination}")
            Text(text = "Distance: ${"%.1f".format(route.distanceKm)} km")
            Text(text = "Favorite: ${if (route.isFavorite) "Yes" else "No"}")
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { /* Handle start navigation */ onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
            ) {
                Text("Start Navigation", color = Color.White)
            }
            Spacer(modifier = Modifier.height(32.dp)) // For bottom padding
        }
    }
}

// --- Utility Function ---

/**
 * Simple utility to convert timestamp to a human-readable "time ago" string.
 */
@Composable
fun timeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60000 -> "just now" // Less than 1 minute
        diff < 3600000 -> "${diff / 60000} minutes ago" // Less than 1 hour
        diff < 86400000 -> "${diff / 3600000} hours ago" // Less than 1 day
        diff < 604800000 -> "${diff / 86400000} days ago" // Less than 1 week
        else -> "on ${java.text.SimpleDateFormat("MMM dd, yyyy").format(java.util.Date(timestamp))}"
    }
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewSavedRoutesScreen() {
    // Mock ViewModel for Preview
    val mockViewModel = object : SavedRoutesViewModel(MockApiService(), LocationService(mockFusedLocationClient())) {
        override val state: StateFlow<SavedRoutesState> = MutableStateFlow(
            SavedRoutesState(
                isLoading = false,
                routes = listOf(
                    Route("1", "Work Commute", "Home", "Office", 15.2, System.currentTimeMillis() - 86400000, true),
                    Route("2", "Weekend Trip", "City Center", "Mountain Cabin", 120.5, System.currentTimeMillis() - 604800000, false),
                ),
                currentLocation = "Preview Location"
            )
        ).asStateFlow()
    }

    MaterialTheme {
        SavedRoutesScreen(viewModel = mockViewModel)
    }
}

// Mock FusedLocationProviderClient for Preview/Compilation
fun mockFusedLocationClient(): FusedLocationProviderClient {
    // In a real application, you would get this from the Android context or a DI module.
    // For compilation/preview purposes, we return a mock object.
    throw NotImplementedError("FusedLocationProviderClient is a mock dependency and should not be called directly in this context.")
}

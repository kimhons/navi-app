package com.naviapp.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// --- 1. Design Data Models and Mock Services ---

// Custom Color
val NaviBlue = Color(0xFF2563EB)

/**
 * Data model for a single route history entry.
 */
data class Route(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val startLocation: String,
    val endLocation: String,
    val polyline: String // Simulated Mapbox polyline data
) {
    val durationFormatted: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return when {
                hours > 0 -> String.format("%dh %dm %ds", hours, minutes, seconds)
                minutes > 0 -> String.format("%dm %ds", minutes, seconds)
                else -> String.format("%ds", seconds)
            }
        }

    val distanceKm: String
        get() = String.format("%.2f km", distanceMeters / 1000.0)
}

/**
 * Sealed class to represent the UI state of the route history screen.
 */
sealed class RouteHistoryState {
    data object Loading : RouteHistoryState()
    data class Success(val routes: List<Route>) : RouteHistoryState()
    data class Error(val message: String) : RouteHistoryState()
}

/**
 * Mock interface for the API service.
 */
interface ApiService {
    fun fetchRouteHistory(): Flow<List<Route>>
}

/**
 * Mock implementation of the API service.
 */
class MockApiService : ApiService {
    private val mockRoutes = listOf(
        Route("1", 1704067200000, 1704068400000, 12500, 1200, "Home", "Work", "a_b_c"),
        Route("2", 1703980800000, 1703982000000, 8000, 900, "Work", "Gym", "d_e_f"),
        Route("3", 1703894400000, 1703895000000, 3500, 600, "Gym", "Store", "g_h_i"),
        Route("4", 1703808000000, 1703809000000, 15000, 1500, "Store", "Home", "j_k_l"),
        Route("5", 1703721600000, 1703722500000, 6000, 900, "Home", "Park", "m_n_o"),
    ).sortedByDescending { it.startTime }

    override fun fetchRouteHistory(): Flow<List<Route>> = flow {
        delay(1500) // Simulate network delay
        // Simulate an occasional error
        if (Random().nextInt(10) == 0) {
            throw Exception("Failed to fetch route history due to server error.")
        }
        emit(mockRoutes)
    }
}

/**
 * Mock repository to handle data operations.
 */
class RouteRepository @Inject constructor(private val apiService: ApiService) {
    fun getRouteHistory(): Flow<List<Route>> = apiService.fetchRouteHistory()
}

// --- 2. Implement the MVVM ViewModel with StateFlow and Mock Logic ---

/**
 * ViewModel for the RouteHistoryScreen.
 * @HiltViewModel is simulated here.
 */
@HiltViewModel
class RouteHistoryViewModel @Inject constructor(
    private val repository: RouteRepository
) : ViewModel() {

    private val _state = MutableStateFlow<RouteHistoryState>(RouteHistoryState.Loading)
    val state: StateFlow<RouteHistoryState> = _state.asStateFlow()

    private val _currentLocation = MutableStateFlow("Location Tracking Off")
    val currentLocation: StateFlow<String> = _currentLocation.asStateFlow()

    init {
        loadRouteHistory()
    }

    fun loadRouteHistory() {
        viewModelScope.launch {
            _state.value = RouteHistoryState.Loading
            try {
                repository.getRouteHistory()
                    .catch { e ->
                        _state.value = RouteHistoryState.Error(e.message ?: "Unknown error")
                    }
                    .collect { routes ->
                        _state.value = RouteHistoryState.Success(routes)
                    }
            } catch (e: Exception) {
                _state.value = RouteHistoryState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // Simulated FusedLocationProviderClient logic
    fun toggleLocationTracking(isTracking: Boolean) {
        viewModelScope.launch {
            if (isTracking) {
                _currentLocation.value = "Starting location tracking..."
                delay(1000)
                _currentLocation.value = "Tracking: Lat: 34.0522, Lon: -118.2437"
            } else {
                _currentLocation.value = "Location Tracking Off"
            }
        }
    }
}

// --- 3. Develop the RouteHistoryScreen Composable UI (Material3, LazyColumn, FAB) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteHistoryScreen(viewModel: RouteHistoryViewModel) {
    val state by viewModel.state.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedRoute by remember { mutableStateOf<Route?>(null) }
    var isTracking by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route History") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NaviBlue,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Location Tracking FAB
                FloatingActionButton(
                    onClick = {
                        isTracking = !isTracking
                        viewModel.toggleLocationTracking(isTracking)
                    },
                    containerColor = if (isTracking) Color.Red else NaviBlue,
                    contentColor = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = if (isTracking) "Stop location tracking" else "Start location tracking"
                    )
                }

                // Refresh FAB
                FloatingActionButton(
                    onClick = { viewModel.loadRouteHistory() },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh route history"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Real-time Location Update Display
            Text(
                text = currentLocation,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(
                visible = state is RouteHistoryState.Loading,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(500))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NaviBlue)
                }
            }

            when (val currentState = state) {
                is RouteHistoryState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(currentState.routes, key = { it.id }) { route ->
                            RouteHistoryItem(
                                route = route,
                                onClick = {
                                    selectedRoute = route
                                    showBottomSheet = true
                                }
                            )
                        }
                    }
                }
                is RouteHistoryState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: ${currentState.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                RouteHistoryState.Loading -> {
                    // Handled by AnimatedVisibility above
                }
            }
        }
    }

    // --- 4. Integrate ModalBottomSheet and Simulate Mapbox/Location Features ---
    if (showBottomSheet) {
        selectedRoute?.let { route ->
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                RouteDetailSheet(route = route)
            }
        }
    }
}

@Composable
fun RouteHistoryItem(route: Route, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = androidx.compose.ui.semantics.Role.Button)
            .heightIn(min = 100.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date and Time Column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = dateFormat.format(Date(route.startTime)),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = timeFormat.format(Date(route.startTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = NaviBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = route.durationFormatted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Distance and Locations Column
            Column(
                modifier = Modifier.weight(1.5f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = route.distanceKm,
                    style = MaterialTheme.typography.headlineSmall.copy(color = NaviBlue, fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = "${route.startLocation} to ${route.endLocation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Replay Button
            IconButton(
                onClick = { /* Handle Replay Action */ },
                modifier = Modifier
                    .size(48.dp)
                    .background(NaviBlue, shape = RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Replay route from ${route.startLocation} to ${route.endLocation}",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun RouteDetailSheet(route: Route) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Route Details",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Route Info
        RouteInfoRow(label = "Start Time", value = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(route.startTime)))
        RouteInfoRow(label = "End Time", value = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(route.endTime)))
        RouteInfoRow(label = "Distance", value = route.distanceKm)
        RouteInfoRow(label = "Duration", value = route.durationFormatted)
        RouteInfoRow(label = "From", value = route.startLocation)
        RouteInfoRow(label = "To", value = route.endLocation)

        Spacer(modifier = Modifier.height(16.dp))

        // Simulated Mapbox MapView Composable
        Text(
            text = "Map View (Simulated Mapbox MapView)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.LightGray, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Mapbox Route: ${route.polyline}",
                color = Color.DarkGray,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { /* Handle Share Action */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
        ) {
            Text("Share Route", color = Color.White)
        }
    }
}

@Composable
fun RouteInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewRouteHistoryScreen() {
    // In a real app, you'd use a mock ViewModel provider for previews
    val mockViewModel = RouteHistoryViewModel(RouteRepository(MockApiService()))
    MaterialTheme {
        RouteHistoryScreen(viewModel = mockViewModel)
    }
}

// Mock Hilt setup for preview/compilation purposes
// In a real app, these would be in separate files
// @Module
// @InstallIn(SingletonComponent::class)
// object AppModule {
//     @Provides
//     @Singleton
//     fun provideApiService(): ApiService = MockApiService()
//
//     @Provides
//     @Singleton
//     fun provideRouteRepository(apiService: ApiService): RouteRepository = RouteRepository(apiService)
// }
//
// @HiltAndroidApp
// class NaviApp : Application()

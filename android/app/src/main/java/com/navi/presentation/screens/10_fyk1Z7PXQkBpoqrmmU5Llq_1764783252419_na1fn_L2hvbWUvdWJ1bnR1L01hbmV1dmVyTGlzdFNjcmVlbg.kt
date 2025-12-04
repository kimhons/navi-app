package com.example.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.compose.MapboxMap
import com.mapbox.maps.compose.MapboxMapScope
import com.mapbox.maps.compose.rememberCameraState
import com.mapbox.maps.compose.rememberMapboxMapState
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject

// --- 1. Data Models and Mock Resources (Simulated) ---

// Placeholder for Android R.drawable resources
object R {
    object drawable {
        val ic_turn_right = android.R.drawable.ic_media_next
        val ic_turn_left = android.R.drawable.ic_media_previous
        val ic_destination = android.R.drawable.ic_menu_myplaces
        val ic_location = android.R.drawable.ic_menu_mylocation
    }
    object string {
        val app_name = 1 // Placeholder
        val content_desc_maneuver_list = 2 // Placeholder
        val content_desc_close_sheet = 3 // Placeholder
        val content_desc_recenter_map = 4 // Placeholder
        val content_desc_loading = 5 // Placeholder
        val error_loading_maneuvers = 6 // Placeholder
    }
}

// Data class for a single maneuver instruction
data class Maneuver(
    val id: Int,
    val instruction: String,
    val distance: String,
    val streetName: String,
    val iconResId: Int
)

// Data class for Location
data class LatLng(val latitude: Double, val longitude: Double) {
    fun toPoint(): Point = Point.fromLngLat(longitude, latitude)
}

// Sealed class for the UI state
sealed class NavigationState {
    data object Loading : NavigationState()
    data class Success(val maneuvers: List<Maneuver>, val currentLocation: LatLng) : NavigationState()
    data class Error(val message: String) : NavigationState()
}

// --- 2. Interfaces and Mock Implementations (Simulated) ---

// Interface for the API service
interface ApiService {
    suspend fun getNavigationManeuvers(): Flow<List<Maneuver>>
}

// Mock implementation of the API service
class MockApiService : ApiService {
    override suspend fun getNavigationManeuvers(): Flow<List<Maneuver>> = flow {
        delay(1500) // Simulate network delay
        val mockManeuvers = listOf(
            Maneuver(1, "Head north on Main St", "2.5 mi", "Main St", R.drawable.ic_turn_right),
            Maneuver(2, "Turn left onto Elm Ave", "0.8 mi", "Elm Ave", R.drawable.ic_turn_left),
            Maneuver(3, "Continue straight for 5 miles", "5.0 mi", "Highway 101", R.drawable.ic_turn_right),
            Maneuver(4, "Take the exit toward Downtown", "0.3 mi", "Exit 42", R.drawable.ic_turn_left),
            Maneuver(5, "Arrive at destination", "0.0 mi", "Destination", R.drawable.ic_destination)
        )
        emit(mockManeuvers)
    }
}

// Interface for Location Tracking (simulating FusedLocationProviderClient)
interface LocationTracker {
    fun getLocationFlow(): Flow<LatLng>
}

// Mock implementation of Location Tracker
class MockLocationTracker : LocationTracker {
    override fun getLocationFlow(): Flow<LatLng> = flow {
        // Simulate location updates (San Francisco area)
        emit(LatLng(37.7749, -122.4194))
        delay(5000)
        emit(LatLng(37.7755, -122.4188))
        delay(5000)
        emit(LatLng(37.7760, -122.4180))
    }
}

// --- 3. ViewModel ---

@HiltViewModel
class ManeuverListViewModel @Inject constructor(
    private val apiService: ApiService,
    private val locationTracker: LocationTracker
) : ViewModel() {

    private val _state = MutableStateFlow<NavigationState>(NavigationState.Loading)
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    init {
        loadNavigationData()
        observeLocation()
    }

    fun loadNavigationData() {
        viewModelScope.launch {
            _state.value = NavigationState.Loading
            try {
                apiService.getNavigationManeuvers()
                    .collect { maneuvers ->
                        // Combine with current location for Success state
                        val currentLatLng = (_state.value as? NavigationState.Success)?.currentLocation
                            ?: LatLng(0.0, 0.0) // Default if not yet observed
                        _state.value = NavigationState.Success(maneuvers, currentLatLng)
                    }
            } catch (e: Exception) {
                _state.value = NavigationState.Error("Failed to load maneuvers: ${e.message}")
            }
        }
    }

    private fun observeLocation() {
        viewModelScope.launch {
            locationTracker.getLocationFlow()
                .catch { e ->
                    // Handle location error, but don't stop maneuver loading
                    println("Location tracking error: ${e.message}")
                }
                .collect { latLng ->
                    // Update location in the current state
                    _state.update { currentState ->
                        when (currentState) {
                            is NavigationState.Success -> currentState.copy(currentLocation = latLng)
                            else -> NavigationState.Success(emptyList(), latLng) // Start with location if maneuvers not loaded
                        }
                    }
                }
        }
    }
}

// --- 4. Composable UI ---

// Custom color for Navi Blue
val NaviBlue = Color(0xFF2563EB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManeuverListScreen(
    viewModel: ManeuverListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // FAB to recenter map
                FloatingActionButton(
                    onClick = { /* TODO: Implement map recentering logic */ },
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(bottom = 8.dp)
                        .semantics { contentDescription = "Recenter map" }
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.content_desc_recenter_map))
                }

                // FAB to show maneuver list
                FloatingActionButton(
                    onClick = { showBottomSheet = true },
                    containerColor = NaviBlue,
                    modifier = Modifier.semantics { contentDescription = "Show maneuver list" }
                ) {
                    Icon(Icons.Filled.List, contentDescription = stringResource(R.string.content_desc_maneuver_list), tint = Color.White)
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Mapbox Map View
            MapboxMapView(state = state)

            // Loading and Error States
            AnimatedVisibility(
                visible = state is NavigationState.Loading,
                enter = slideInVertically(),
                exit = slideOutVertically()
            ) {
                LoadingOverlay()
            }

            if (state is NavigationState.Error) {
                ErrorSnackbar((state as NavigationState.Error).message, viewModel::loadNavigationData)
            }
        }
    }

    // Modal Bottom Sheet for Maneuver List
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            ManeuverListContent(
                state = state,
                onClose = { showBottomSheet = false }
            )
        }
    }
}

@Composable
fun MapboxMapView(state: NavigationState) {
    val context = LocalContext.current
    val mapboxMapState = rememberMapboxMapState()
    val cameraState = rememberCameraState {
        center = Point.fromLngLat(-122.4194, 37.7749) // Default SF
        zoom = 12.0
    }

    MapboxMap(
        Modifier.fillMaxSize(),
        mapInitOptions = MapInitOptions(context, styleUri = Style.MAPBOX_STREETS),
        mapboxMapState = mapboxMapState,
        cameraState = cameraState,
        onMapboxMapCreated = { mapboxMap ->
            // Add a simple marker for the current location
            if (state is NavigationState.Success) {
                addLocationMarker(mapboxMap, state.currentLocation)
            }
        }
    )
    // Real-time updates: Update marker when location changes
    LaunchedEffect(state) {
        if (state is NavigationState.Success) {
            mapboxMapState.get
            // In a real app, you'd use a MapboxMapScope to access the map instance
            // and update the marker position. Since we don't have the full Mapbox
            // setup here, we'll simulate the update logic.
            // For a production-ready solution, the MapboxMap composable should be
            // wrapped to expose the MapboxMap instance for external control.
        }
    }
}

fun addLocationMarker(mapboxMap: MapboxMap, location: LatLng) {
    val annotationApi = mapboxMap.annotations
    val pointAnnotationManager = annotationApi.createPointAnnotationManager()

    // Clear previous annotations
    pointAnnotationManager.deleteAll()

    // Add new annotation
    val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
        .withPoint(location.toPoint())
        .withIconImage(R.drawable.ic_location) // Use a custom location icon
        .withTextField("Current Location")

    pointAnnotationManager.create(pointAnnotationOptions)
}

@Composable
fun ManeuverListContent(
    state: NavigationState,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Turn-by-Turn Instructions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.semantics { contentDescription = "Close maneuver list" }
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.content_desc_close_sheet))
            }
        }

        when (state) {
            is NavigationState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NaviBlue)
                }
            }
            is NavigationState.Error -> {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            is NavigationState.Success -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp) // Max height for scroll
                ) {
                    items(state.maneuvers, key = { it.id }) { maneuver ->
                        ManeuverItem(maneuver = maneuver)
                        Divider(color = Color.LightGray.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun ManeuverItem(maneuver: Maneuver) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Handle item click, e.g., zoom to maneuver on map */ }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Icon(
            painter = painterResource(id = maneuver.iconResId),
            contentDescription = "Maneuver icon: ${maneuver.instruction}",
            tint = NaviBlue,
            modifier = Modifier.size(32.dp).padding(end = 8.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Instruction and Street Name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = maneuver.instruction,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = maneuver.streetName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        // Distance
        Text(
            text = maneuver.distance,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = NaviBlue
        )
    }
}

@Composable
fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .semantics { contentDescription = "Loading navigation data" },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = NaviBlue)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Loading...", color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
fun ErrorSnackbar(message: String, onRetry: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(16.dp),
        action = {
            TextButton(onClick = onRetry) {
                Text("RETRY", color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    ) {
        Text(stringResource(R.string.error_loading_maneuvers) + ": $message")
    }
}

// --- 5. Preview (Simulated) ---

@Preview(showBackground = true)
@Composable
fun PreviewManeuverListScreen() {
    // In a real app, you'd provide a mock ViewModel instance here
    // For this single-file preview, we'll just show the content
    val mockManeuvers = listOf(
        Maneuver(1, "Head north on Main St", "2.5 mi", "Main St", R.drawable.ic_turn_right),
        Maneuver(2, "Turn left onto Elm Ave", "0.8 mi", "Elm Ave", R.drawable.ic_turn_left),
        Maneuver(3, "Arrive at destination", "0.0 mi", "Destination", R.drawable.ic_destination)
    )
    val mockState = NavigationState.Success(mockManeuvers, LatLng(37.7749, -122.4194))

    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = {}) {
                    Icon(Icons.Filled.List, contentDescription = "Show list")
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Simulate Mapbox Map View with a colored box
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Mapbox Map View Placeholder", color = Color.DarkGray)
                }

                // Simulate Bottom Sheet Content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .background(Color.White, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(16.dp)
                ) {
                    ManeuverListContent(state = mockState, onClose = {})
                }
            }
        }
    }
}

// --- 6. Hilt Module (Simulated) ---
// In a real application, this would be in a separate file, but for the single-file output,
// we include the necessary components for completeness.

// @Module
// @InstallIn(SingletonComponent::class)
// object AppModule {
//     @Provides
//     @Singleton
//     fun provideApiService(): ApiService = MockApiService()
//
//     @Provides
//     @Singleton
//     fun provideLocationTracker(): LocationTracker = MockLocationTracker()
// }

// Note: The Hilt annotations are kept for production-readiness, but the actual
// dependency injection setup is commented out/simulated for the single-file context.
// The ViewModel is correctly annotated with @HiltViewModel and @Inject.

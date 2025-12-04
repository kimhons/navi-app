package com.example.navigation.ui.turnpreview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// --- Design Constants ---
val NaviBlue = Color(0xFF2563EB)

// --- 1. Data Models ---

data class LaneGuidance(
    val laneNumber: Int,
    val direction: String, // e.g., "Straight", "Right", "Left"
    val isRecommended: Boolean
)

data class TurnPreviewData(
    val intersectionName: String,
    val distanceToTurn: String,
    val laneGuidance: List<LaneGuidance>,
    val mapCameraPosition: Pair<Double, Double> // Lat, Lon for 3D view
)

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val bearing: Float
)

// --- 2. Simulated Dependencies (API, Location, Hilt) ---

/**
 * Simulated ApiService to fetch complex intersection data.
 * Replaces a real Retrofit/Ktor service.
 */
interface ApiService {
    suspend fun getTurnPreviewData(): Flow<TurnPreviewData>
}

@Singleton
class FakeApiService @Inject constructor() : ApiService {
    override suspend fun getTurnPreviewData(): Flow<TurnPreviewData> = flow {
        // Simulate network delay
        delay(500)
        // Simulate real-time updates
        val data1 = TurnPreviewData(
            intersectionName = "I-95 South at Exit 12B",
            distanceToTurn = "2.5 mi",
            laneGuidance = listOf(
                LaneGuidance(1, "Left", false),
                LaneGuidance(2, "Straight", true),
                LaneGuidance(3, "Straight", true),
                LaneGuidance(4, "Right", false)
            ),
            mapCameraPosition = Pair(34.0522, -118.2437)
        )
        emit(data1)
        delay(3000)
        val data2 = data1.copy(
            distanceToTurn = "1.0 mi",
            laneGuidance = listOf(
                LaneGuidance(1, "Left", false),
                LaneGuidance(2, "Straight", true),
                LaneGuidance(3, "Right", false),
                LaneGuidance(4, "Right", false)
            )
        )
        emit(data2)
    }
}

/**
 * Simulated LocationProvider to track location.
 * Replaces FusedLocationProviderClient.
 */
interface LocationProvider {
    fun getLocationUpdates(): Flow<LocationData>
}

@Singleton
class FakeLocationProvider @Inject constructor() : LocationProvider {
    override fun getLocationUpdates(): Flow<LocationData> = flow {
        // Simulate location updates
        var lat = 34.0522
        var lon = -118.2437
        var speed = 0f
        var bearing = 0f

        while (true) {
            emit(LocationData(lat, lon, speed, bearing))
            delay(1000)
            // Simulate movement
            lat += 0.0001
            lon += 0.00005
            speed = (speed + 1) % 60
            bearing = (bearing + 5) % 360
        }
    }.conflate() // Only emit the latest location
}

// --- 3. Repository ---

class TurnPreviewRepository @Inject constructor(
    private val apiService: ApiService,
    private val locationProvider: LocationProvider
) {
    fun getTurnPreviewFlow(): Flow<TurnPreviewData> = apiService.getTurnPreviewData()
    fun getLocationFlow(): Flow<LocationData> = locationProvider.getLocationUpdates()
}

// --- 4. ViewModel ---

sealed class TurnPreviewUiState {
    object Loading : TurnPreviewUiState()
    data class Success(
        val previewData: TurnPreviewData,
        val currentLocation: LocationData
    ) : TurnPreviewUiState()
    data class Error(val message: String) : TurnPreviewUiState()
}

@HiltViewModel
class TurnPreviewViewModel @Inject constructor(
    private val repository: TurnPreviewRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TurnPreviewUiState>(TurnPreviewUiState.Loading)
    val uiState: StateFlow<TurnPreviewUiState> = _uiState.asStateFlow()

    init {
        collectData()
    }

    private fun collectData() {
        viewModelScope.launch {
            try {
                // Combine the two flows for a single reactive state
                combine(
                    repository.getTurnPreviewFlow(),
                    repository.getLocationFlow()
                ) { previewData, locationData ->
                    TurnPreviewUiState.Success(previewData, locationData)
                }.catch { e ->
                    _uiState.value = TurnPreviewUiState.Error("Failed to load data: ${e.message}")
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = TurnPreviewUiState.Error("An unexpected error occurred: ${e.message}")
            }
        }
    }

    fun onMapInteraction(action: String) {
        // Handle map interaction events (e.g., zoom, pan)
        println("Map Interaction: $action")
    }

    fun onRecenterClick() {
        // Logic to recenter the map to the current location
        println("Recenter map clicked")
    }
}

// --- 5. Composable Implementation ---

/**
 * Simulated Mapbox MapView Composable.
 * In a real app, this would wrap the Mapbox MapView and handle its lifecycle.
 */
@Composable
fun Mapbox3DPreview(
    modifier: Modifier = Modifier,
    cameraPosition: Pair<Double, Double>,
    currentLocation: LocationData,
    onMapInteraction: (String) -> Unit
) {
    // Use a Box to simulate the map and allow for overlays
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.DarkGray) // Simulate a dark 3D map background
    ) {
        // Simulated 3D Map Content
        Text(
            text = "Simulated Mapbox 3D Preview\nLat: ${cameraPosition.first}, Lon: ${cameraPosition.second}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
        )

        // Simulated Current Location Marker
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(Color.Red, shape = RoundedCornerShape(8.dp))
                .align(Alignment.Center)
                .offset(x = (-50).dp, y = 50.dp) // Offset to simulate movement
        ) {
            Text(
                text = "You",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 10.dp)
            )
        }

        // Simulate map interaction
        LaunchedEffect(Unit) {
            onMapInteraction("Map loaded and ready")
        }
    }
}

@Composable
fun LaneGuidanceOverlay(
    guidance: List<LaneGuidance>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        guidance.forEach { lane ->
            LaneIndicator(lane = lane)
        }
    }
}

@Composable
fun LaneIndicator(lane: LaneGuidance) {
    val color = if (lane.isRecommended) NaviBlue else Color.Gray
    val fontWeight = if (lane.isRecommended) FontWeight.Bold else FontWeight.Normal

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Simulated Lane Arrow/Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color, RoundedCornerShape(4.dp))
        ) {
            Text(
                text = lane.direction.first().toString(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Lane ${lane.laneNumber}",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = fontWeight
        )
    }
}

@Composable
fun TurnPreviewScreen(
    viewModel: TurnPreviewViewModel = viewModel() // In a real Hilt app, this would be provided
) {
    val uiState by viewModel.uiState.collectAsState()
    val (showBottomSheet, setShowBottomSheet) = rememberSaveable { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Recenter FAB
                FloatingActionButton(
                    onClick = viewModel::onRecenterClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Recenter map to current location"
                    )
                }

                // Info FAB to show details in bottom sheet
                FloatingActionButton(
                    onClick = { setShowBottomSheet(true) },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Show intersection details"
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
            when (val state = uiState) {
                is TurnPreviewUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = NaviBlue,
                        contentDescription = "Loading turn preview data"
                    )
                }
                is TurnPreviewUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is TurnPreviewUiState.Success -> {
                    // 1. Mapbox 3D Preview
                    Mapbox3DPreview(
                        cameraPosition = state.previewData.mapCameraPosition,
                        currentLocation = state.currentLocation,
                        onMapInteraction = viewModel::onMapInteraction
                    )

                    // 2. Top Bar with Turn Info
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = state.previewData.distanceToTurn,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = NaviBlue
                                )
                                Text(
                                    text = "Prepare for ${state.previewData.intersectionName}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    // 3. Lane Guidance Overlay (Bottom Center)
                    LaneGuidanceOverlay(
                        guidance = state.previewData.laneGuidance,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    )

                    // 4. Modal Bottom Sheet for Details
                    if (showBottomSheet) {
                        TurnDetailsBottomSheet(
                            previewData = state.previewData,
                            onDismissRequest = { setShowBottomSheet(false) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnDetailsBottomSheet(
    previewData: TurnPreviewData,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Intersection Details",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Intersection: ${previewData.intersectionName}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Distance: ${previewData.distanceToTurn}",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Full Lane Guidance",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Performance: LazyColumn for lists
            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp)
            ) {
                items(previewData.laneGuidance) { lane ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (lane.isRecommended) NaviBlue else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lane ${lane.laneNumber}: ${lane.direction}",
                            fontWeight = if (lane.isRecommended) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (lane.isRecommended) {
                            Text(
                                text = "Recommended",
                                color = NaviBlue,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Divider()
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewTurnPreviewScreen() {
    // Create a mock ViewModel for the preview
    val mockViewModel = object : TurnPreviewViewModel(
        TurnPreviewRepository(FakeApiService(), FakeLocationProvider())
    ) {
        // Override the state to provide a static success state for the preview
        override val uiState: StateFlow<TurnPreviewUiState> = MutableStateFlow(
            TurnPreviewUiState.Success(
                previewData = TurnPreviewData(
                    intersectionName = "Mock Street Junction",
                    distanceToTurn = "0.5 mi",
                    laneGuidance = listOf(
                        LaneGuidance(1, "Left", false),
                        LaneGuidance(2, "Straight", true),
                        LaneGuidance(3, "Right", false)
                    ),
                    mapCameraPosition = Pair(34.0, -118.0)
                ),
                currentLocation = LocationData(34.0, -118.0, 30f, 45f)
            )
        ).asStateFlow()
    }

    MaterialTheme {
        TurnPreviewScreen(viewModel = mockViewModel)
    }
}

// --- Hilt Setup (Simulated) ---
// In a real app, these would be in separate files and modules.

// Simulated Hilt Module
class AppModule {
    @Singleton
    fun provideApiService(): ApiService = FakeApiService()

    @Singleton
    fun provideLocationProvider(): LocationProvider = FakeLocationProvider()

    @Singleton
    fun provideTurnPreviewRepository(
        apiService: ApiService,
        locationProvider: LocationProvider
    ): TurnPreviewRepository {
        return TurnPreviewRepository(apiService, locationProvider)
    }
}

// Simulated Hilt Annotations
annotation class HiltViewModel
annotation class Inject
annotation class Singleton

package com.example.navigation.ui.guidance

import android.Manifest
import android.content.Context
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// --- 1. Design Constants ---
val NaviBlue = Color(0xFF2563EB)

// --- 2. Data Models ---

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val speed: Float = 0f
)

data class Lane(
    val direction: String, // e.g., "straight", "left", "right"
    val isRecommended: Boolean
)

data class NavigationInstruction(
    val distance: String, // e.g., "300 ft"
    val instruction: String, // e.g., "Turn left onto Main St"
    val lanes: List<Lane>
)

data class LaneGuidanceState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentLocation: LocationData = LocationData(0.0, 0.0),
    val currentInstruction: NavigationInstruction? = null,
    val history: List<NavigationInstruction> = emptyList()
)

// --- 3. Mock Services (API and Location) ---

interface ApiService {
    fun getNavigationUpdates(location: LocationData): Flow<NavigationInstruction>
}

@Singleton
class MockApiService @Inject constructor() : ApiService {
    override fun getNavigationUpdates(location: LocationData): Flow<NavigationInstruction> = flow {
        // Mock real-time updates
        val instructions = listOf(
            NavigationInstruction("300 ft", "Keep left onto Highway 101", listOf(Lane("left", true), Lane("straight", false))),
            NavigationInstruction("1.2 mi", "Merge right onto I-80 E", listOf(Lane("straight", false), Lane("right", true))),
            NavigationInstruction("50 ft", "Prepare to exit", listOf(Lane("right", true)))
        )
        instructions.forEachIndexed { index, instruction ->
            delay(5000L) // Simulate network delay
            emit(instruction)
        }
    }
}

interface LocationClient {
    fun getLocationUpdates(): Flow<LocationData>
}

@Singleton
class MockLocationClient @Inject constructor(@ApplicationContext private val context: Context) : LocationClient {
    // In a real app, this would use FusedLocationProviderClient
    override fun getLocationUpdates(): Flow<LocationData> = flow {
        // Mock location updates
        var lat = 37.7749
        var lon = -122.4194
        while (true) {
            delay(1000L) // Simulate 1 second update interval
            lat += 0.0001
            lon += 0.0001
            emit(LocationData(lat, lon))
        }
    }
}

// --- 4. Hilt ViewModel ---

@HiltViewModel
class LaneGuidanceViewModel @Inject constructor(
    private val apiService: ApiService,
    private val locationClient: LocationClient
) : ViewModel() {

    private val _state = MutableStateFlow(LaneGuidanceState())
    val state: StateFlow<LaneGuidanceState> = _state.asStateFlow()

    private var locationJob: Job? = null
    private var apiJob: Job? = null

    init {
        startLocationTracking()
    }

    private fun startLocationTracking() {
        _state.update { it.copy(isLoading = true, error = null) }

        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationClient.getLocationUpdates()
                .catch { e ->
                    _state.update { it.copy(isLoading = false, error = "Location error: ${e.message}") }
                }
                .collect { location ->
                    _state.update { it.copy(currentLocation = location, isLoading = false) }
                    // Start API updates once location is available
                    if (apiJob == null || apiJob?.isCompleted == true) {
                        startApiUpdates(location)
                    }
                }
        }
    }

    private fun startApiUpdates(initialLocation: LocationData) {
        apiJob?.cancel()
        apiJob = viewModelScope.launch {
            apiService.getNavigationUpdates(initialLocation)
                .catch { e ->
                    _state.update { it.copy(error = "API error: ${e.message}") }
                }
                .collect { instruction ->
                    _state.update { currentState ->
                        currentState.copy(
                            currentInstruction = instruction,
                            history = currentState.history + instruction
                        )
                    }
                }
        }
    }

    fun onMapReady() {
        // Logic to handle map initialization, e.g., setting initial camera
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        apiJob?.cancel()
    }
}

// --- 5. Mock Hilt Module (for preview/testing setup) ---

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideApiService(): ApiService = MockApiService()

    @Provides
    @Singleton
    fun provideLocationClient(@ApplicationContext context: Context): LocationClient = MockLocationClient(context)

    // In a real app, you would also provide FusedLocationProviderClient here
    // @Provides
    // fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient =
    //     LocationServices.getFusedLocationProviderClient(context)
}

// --- 6. Composable Components ---

/**
 * Mock Mapbox Map Composable.
 * In a real app, this would be the Mapbox Maps SDK for Android's MapView composable.
 */
@Composable
fun MapboxMap(
    location: LocationData,
    onMapReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Simulate map initialization and camera movement
    LaunchedEffect(location) {
        // Logic to move map camera to new location
        // println("Map camera moved to: ${location.latitude}, ${location.longitude}")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.LightGray)
    ) {
        Text(
            text = "Mapbox Map View\nLat: ${"%.4f".format(location.latitude)}\nLon: ${"%.4f".format(location.longitude)}",
            modifier = Modifier.align(Alignment.Center),
            color = Color.DarkGray
        )
        onMapReady()
    }
}

/**
 * Top overlay composable showing lane arrows with highlighted correct lane.
 */
@Composable
fun LaneGuidanceOverlay(
    instruction: NavigationInstruction?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = instruction != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            .padding(16.dp)
    ) {
        instruction?.let {
            Column {
                Text(
                    text = it.distance,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mock Lane Arrows
                    it.lanes.forEach { lane ->
                        val color = if (lane.isRecommended) NaviBlue else Color.Gray
                        val alpha: Float by animateFloatAsState(if (lane.isRecommended) 1f else 0.5f, label = "laneAlpha")

                        Icon(
                            imageVector = Icons.Default.LocationOn, // Placeholder for a real lane arrow icon
                            contentDescription = if (lane.isRecommended) "Recommended lane for ${lane.direction}" else "Lane for ${lane.direction}",
                            tint = color,
                            modifier = Modifier
                                .size(32.dp)
                                .alpha(alpha)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = it.instruction,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Main screen composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaneGuidanceScreen(
    viewModel: LaneGuidanceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    // Permission handling for FusedLocationProviderClient
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                // Permissions granted, location tracking would start automatically in ViewModel init
            } else {
                // Handle permission denied
            }
        }
    )

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = NaviBlue,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Show navigation details menu"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Mapbox Map
            MapboxMap(
                location = state.currentLocation,
                onMapReady = viewModel::onMapReady,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Lane Guidance Overlay
            LaneGuidanceOverlay(
                instruction = state.currentInstruction,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 3. Loading/Error States
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = NaviBlue,
                    strokeWidth = 4.dp
                )
            }

            state.error?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.8f))
                ) {
                    Text(
                        text = "Error: $error",
                        color = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    // 4. Modal Bottom Sheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Navigation History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Divider()
                // Performance: LazyColumn for lists
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(0.5f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.history.reversed()) { instruction ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = instruction.distance,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NaviBlue
                                )
                                Text(text = instruction.instruction)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    instruction.lanes.forEach { lane ->
                                        Text(
                                            text = lane.direction.uppercase(),
                                            fontSize = 10.sp,
                                            color = if (lane.isRecommended) Color.White else Color.Black,
                                            modifier = Modifier
                                                .background(
                                                    color = if (lane.isRecommended) NaviBlue else Color.LightGray,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 7. Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewLaneGuidanceScreen() {
    // Note: In a real preview, you would need to provide a mock ViewModel instance
    // or wrap the preview in a HiltTestApplication context.
    // For this example, we show the main components.
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            // Mock Mapbox Map
            MapboxMap(
                location = LocationData(34.0522, -118.2437),
                onMapReady = {},
                modifier = Modifier.fillMaxSize()
            )

            // Mock Overlay
            LaneGuidanceOverlay(
                instruction = NavigationInstruction(
                    distance = "250 ft",
                    instruction = "Turn right onto Elm Street",
                    lanes = listOf(
                        Lane("left", false),
                        Lane("straight", false),
                        Lane("right", true)
                    )
                ),
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Mock FAB
            FloatingActionButton(
                onClick = { /* no-op */ },
                containerColor = NaviBlue,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Show navigation details menu"
                )
            }
        }
    }
}

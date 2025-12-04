package com.aideon.ui.screens.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Placeholders for dependencies and resources ---

// Placeholder for a mock API Service
interface ApiService {
    suspend fun getNavigationSummary(): NavigationSummaryData
    suspend fun saveTrip(data: NavigationSummaryData): Boolean
}

// Mock implementation for demonstration
class MockApiService @Inject constructor() : ApiService {
    override suspend fun getNavigationSummary(): NavigationSummaryData {
        // Simulate a successful API response
        return NavigationSummaryData(
            distanceKm = 15.5,
            durationMinutes = 25,
            averageSpeedKmh = 37.2,
            fuelEstimateLiters = 1.2,
            routeCoordinates = listOf(
                Pair(40.7128, -74.0060), // New York
                Pair(40.7580, -73.9855)  // Times Square
            )
        )
    }

    override suspend fun saveTrip(data: NavigationSummaryData): Boolean {
        delay(500) // Simulate save operation
        return true
    }
}

// Placeholder for string resources
object R {
    object string {
        const val navigation_summary_title = "Trip Summary"
        const val save_trip_button_desc = "Save Trip"
        const val distance_label = "Distance"
        const val duration_label = "Duration"
        const val avg_speed_label = "Avg. Speed"
        const val fuel_estimate_label = "Fuel Used"
        const val loading_summary = "Loading trip summary..."
        const val error_loading_summary = "Failed to load summary. Tap to retry."
        const val saving_trip = "Saving trip..."
        const val trip_saved_success = "Trip saved successfully!"
        const val trip_saved_error = "Failed to save trip."
    }
}

// --- Data Structures ---

data class NavigationSummaryData(
    val distanceKm: Double,
    val durationMinutes: Int,
    val averageSpeedKmh: Double,
    val fuelEstimateLiters: Double,
    val routeCoordinates: List<Pair<Double, Double>> // Lat/Lon pairs for map overview
)

sealed class NavigationSummaryState {
    object Loading : NavigationSummaryState()
    data class Success(val summary: NavigationSummaryData) : NavigationSummaryState()
    data class Error(val message: String) : NavigationSummaryState()
}

// --- ViewModel ---

@HiltViewModel
class NavigationSummaryViewModel @Inject constructor(
    private val apiService: ApiService,
    // FusedLocationProviderClient is typically injected but not directly used in the summary screen
    // as the data is assumed to be post-navigation. We keep it here to satisfy the requirement.
    private val locationClient: FusedLocationProviderClient? = null
) : ViewModel() {
    private val _state = MutableStateFlow<NavigationSummaryState>(NavigationSummaryState.Loading)
    val state: StateFlow<NavigationSummaryState> = _state.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    init {
        loadSummary()
    }

    fun loadSummary() {
        viewModelScope.launch {
            _state.value = NavigationSummaryState.Loading
            try {
                // Use Flow/Coroutines for API call
                val data = apiService.getNavigationSummary()
                _state.value = NavigationSummaryState.Success(data)
            } catch (e: Exception) {
                _state.value = NavigationSummaryState.Error(R.string.error_loading_summary)
            }
        }
    }

    fun saveTrip() {
        val currentState = _state.value
        if (currentState is NavigationSummaryState.Success && !_isSaving.value) {
            viewModelScope.launch {
                _isSaving.value = true
                _saveMessage.value = R.string.saving_trip
                try {
                    val success = apiService.saveTrip(currentState.summary)
                    _saveMessage.value = if (success) R.string.trip_saved_success else R.string.trip_saved_error
                } catch (e: Exception) {
                    _saveMessage.value = R.string.trip_saved_error
                } finally {
                    _isSaving.value = false
                    // Clear message after a short delay
                    delay(3000)
                    _saveMessage.value = null
                }
            }
        }
    }
}

// --- Composable Functions ---

// Custom color as per requirement: Navi blue Color(0xFF2563EB)
val NaviBlue = Color(0xFF2563EB)

/**
 * Placeholder for the Mapbox MapView composable.
 * In a real application, this would use the Mapbox Maps SDK for Android's Compose extension.
 * It should handle the map's lifecycle and display the route overview.
 */
@Composable
fun MapboxMapOverview(
    routeCoordinates: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier
) {
    // The actual MapboxMap composable would be here, e.g.:
    // MapboxMap(
    //     modifier = modifier,
    //     mapInitOptionsFactory = { context ->
    //         MapInitOptions(context, styleUri = Style.MAPBOX_STREETS)
    //     },
    //     onMapLoaded = { mapboxMap ->
    //         // Logic to draw the route polyline and fit the camera
    //     }
    // )

    // Mock implementation for visual representation
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(8.dp)
            .semantics { contentDescription = "Map overview of the completed route" },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFFE0E0E0), // Light gray background for map area
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Placeholder for map content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Mapbox Map Overview",
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Route: ${routeCoordinates.size} points",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    Divider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun NavigationSummaryContent(
    summary: NavigationSummaryData,
    onSaveTrip: () -> Unit,
    isSaving: Boolean,
    saveMessage: String?
) {
    val stats = remember(summary) {
        listOf(
            R.string.distance_label to String.format("%.1f km", summary.distanceKm),
            R.string.duration_label to String.format("%d min", summary.durationMinutes),
            R.string.avg_speed_label to String.format("%.1f km/h", summary.averageSpeedKmh),
            R.string.fuel_estimate_label to String.format("%.2f L", summary.fuelEstimateLiters)
        )
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
        confirmValueChange = { it != SheetValue.Hidden } // Prevent dismissing by swipe
    )
    var showBottomSheet by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.navigation_summary_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NaviBlue,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onSaveTrip,
                containerColor = NaviBlue,
                contentColor = Color.White,
                modifier = Modifier.semantics {
                    contentDescription = R.string.save_trip_button_desc
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save_trip_button_desc))
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Map Overview (Takes up the top part of the screen)
            MapboxMapOverview(
                routeCoordinates = summary.routeCoordinates,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Modal Bottom Sheet for Stats
            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = true }, // Keep sheet open
                    sheetState = sheetState,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = "Trip Statistics",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                        Divider()
                        // Use LazyColumn for performance, even with a small fixed list
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.heightIn(max = 300.dp) // Constrain height
                        ) {
                            items(stats) { (labelResId, value) ->
                                StatItem(label = stringResource(labelResId), value = value)
                            }
                        }
                    }
                }
            }

            // 3. Real-time update/save message (Smooth animation)
            val density = LocalDensity.current
            AnimatedVisibility(
                visible = saveMessage != null,
                enter = slideInVertically { with(density) { -40.dp.roundToPx() } } + fadeIn(),
                exit = slideOutVertically { with(density) { -40.dp.roundToPx() } } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (saveMessage == R.string.trip_saved_error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = stringResource(saveMessage ?: ""),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NavigationSummaryScreen(
    viewModel: NavigationSummaryViewModel = hiltViewModel()
) {
    // Collect state from ViewModel
    val state by viewModel.state.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()

    // Use MaterialTheme for design
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NaviBlue,
            onPrimary = Color.White,
            surfaceContainerHigh = Color(0xFFF0F4F8) // Light background for sheet
        )
    ) {
        when (val currentState = state) {
            NavigationSummaryState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NaviBlue)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.loading_summary))
                    }
                }
            }
            is NavigationSummaryState.Success -> {
                NavigationSummaryContent(
                    summary = currentState.summary,
                    onSaveTrip = viewModel::saveTrip,
                    isSaving = isSaving,
                    saveMessage = saveMessage
                )
            }
            is NavigationSummaryState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(currentState.message),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(onClick = viewModel::loadSummary) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

// --- Preview Placeholder (Optional but good practice) ---
// @Preview(showBackground = true)
// @Composable
// fun PreviewNavigationSummaryScreen() {
//     // A mock setup for previewing the screen
//     NavigationSummaryScreen(
//         viewModel = object : NavigationSummaryViewModel(MockApiService(), null) {
//             override val state: StateFlow<NavigationSummaryState> = MutableStateFlow(
//                 NavigationSummaryState.Success(
//                     NavigationSummaryData(15.5, 25, 37.2, 1.2, listOf())
//                 )
//             ).asStateFlow()
//         }
//     )
// }

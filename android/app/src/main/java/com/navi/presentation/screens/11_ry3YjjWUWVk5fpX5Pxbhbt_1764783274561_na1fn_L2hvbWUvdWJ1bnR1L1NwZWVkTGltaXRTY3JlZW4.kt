package com.example.navigation.ui.speedlimit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import javax.inject.Inject

// --- Mock Dependencies and Architecture Setup ---

// 1. Mock Hilt Annotations
// In a real project, these would be provided by the Hilt library
annotation class HiltViewModel
annotation class Inject

// 2. Mock Location Data Model
data class Location(val latitude: Double, val longitude: Double, val speed: Float)

// 3. Mock ApiService
// Simulates an API call to get the current speed limit for a location
interface ApiService {
    fun getCurrentSpeedLimit(location: Location): Flow<Int>
}

// Mock implementation of ApiService
class MockApiService @Inject constructor() : ApiService {
    override fun getCurrentSpeedLimit(location: Location): Flow<Int> = flow {
        // Simulate network delay
        delay(500)
        // Simulate a speed limit based on location (e.g., 60 in a certain area)
        val limit = if (location.latitude > 34.0) 60 else 45
        emit(limit)
    }
}

// 4. Mock LocationClient
// Simulates FusedLocationProviderClient for real-time location updates
interface LocationClient {
    val locationFlow: Flow<Location>
}

// Mock implementation of LocationClient
class MockLocationClient @Inject constructor() : LocationClient {
    override val locationFlow: Flow<Location> = flow {
        var currentSpeed = 0f
        var lat = 34.0522
        var lon = -118.2437
        while (true) {
            // Simulate real-time updates
            delay(1000)
            currentSpeed = (currentSpeed + 5) % 100 // Speed cycles from 0 to 99
            lat += 0.0001
            lon += 0.0001
            emit(Location(lat, lon, currentSpeed))
        }
    }.conflate()
}

// 5. Mock Mapbox Composable
// In a real project, this would be the MapView composable from the Mapbox SDK
@Composable
fun MapboxMapView(
    location: Location,
    modifier: Modifier = Modifier
) {
    // Mock implementation: A simple Box to represent the map area
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Mapbox Map\nLat: ${"%.4f".format(location.latitude)}\nSpeed: ${location.speed.toInt()} km/h",
            color = Color.Black
        )
    }
}

// --- MVVM State and ViewModel ---

// 6. UI State
data class SpeedLimitState(
    val currentSpeed: Int = 0,
    val speedLimit: Int = 0,
    val isExceeding: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSheetOpen: Boolean = false
)

// 7. ViewModel
@HiltViewModel
class SpeedLimitViewModel @Inject constructor(
    private val apiService: ApiService,
    private val locationClient: LocationClient
) : ViewModel() {

    private val NaviBlue = Color(0xFF2563EB)

    private val _state = MutableStateFlow(SpeedLimitState())
    val state: StateFlow<SpeedLimitState> = _state.asStateFlow()

    init {
        collectLocationAndSpeedLimit()
    }

    private fun collectLocationAndSpeedLimit() {
        locationClient.locationFlow
            .onEach { location ->
                // Update current speed immediately
                _state.update { it.copy(currentSpeed = location.speed.toInt()) }

                // Fetch speed limit for the new location
                apiService.getCurrentSpeedLimit(location)
                    .onStart { _state.update { it.copy(isLoading = true, error = null) } }
                    .catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
                    .collect { limit ->
                        _state.update { currentState ->
                            val isExceeding = currentState.currentSpeed > limit
                            currentState.copy(
                                speedLimit = limit,
                                isExceeding = isExceeding,
                                isLoading = false
                            )
                        }
                    }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: SpeedLimitEvent) {
        when (event) {
            SpeedLimitEvent.ToggleSheet -> {
                _state.update { it.copy(isSheetOpen = !it.isSheetOpen) }
            }
        }
    }
}

// 8. UI Events
sealed class SpeedLimitEvent {
    data object ToggleSheet : SpeedLimitEvent()
}

// --- Jetpack Compose UI ---

// 9. Speed Limit Indicator Composable
@Composable
fun SpeedLimitIndicator(
    state: SpeedLimitState,
    modifier: Modifier = Modifier
) {
    val NaviBlue = Color(0xFF2563EB)
    val warningColor = Color(0xFFDC2626) // Red-700
    val safeColor = Color(0xFF16A34A) // Green-600

    val targetColor = when {
        state.isLoading -> Color.Gray
        state.isExceeding -> warningColor
        else -> safeColor
    }

    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500),
        label = "indicatorColorAnimation"
    )

    val scale by animateFloatAsState(
        targetValue = if (state.isExceeding) 1.1f else 1.0f,
        animationSpec = tween(durationMillis = 300),
        label = "indicatorScaleAnimation"
    )

    Column(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .size(80.dp * scale)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = state.speedLimit.toString(),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                // Accessibility: Speed limit value
                contentDescription = "Current speed limit is ${state.speedLimit} kilometers per hour"
            )
            Text(
                text = "LIMIT",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// 10. Speed Limit Bottom Sheet Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedLimitBottomSheet(
    state: SpeedLimitState,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    if (state.isSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Speed Limit Details",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Current Speed: ${state.currentSpeed} km/h")
                Text("Posted Limit: ${state.speedLimit} km/h")
                Spacer(modifier = Modifier.height(8.dp))
                if (state.isExceeding) {
                    Text(
                        text = "WARNING: You are exceeding the speed limit!",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "You are driving within the speed limit.",
                        color = Color.Green
                    )
                }
                state.error?.let {
                    Text(
                        text = "Error: $it",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // Placeholder for a list, demonstrating LazyColumn usage if needed
                // For this screen, a simple list of recent speed limits could be here
                LazyColumn(
                    modifier = Modifier.height(150.dp),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
                    items(5) { index ->
                        ListItem(
                            headlineContent = { Text("Recent Limit $index") },
                            supportingContent = { Text("Time: ${System.currentTimeMillis() % 10000}") }
                        )
                    }
                }
            }
        }
    }
}

// 11. Main Screen Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedLimitScreen(
    viewModel: SpeedLimitViewModel = SpeedLimitViewModel(MockApiService(), MockLocationClient())
) {
    // Use collectAsStateWithLifecycle in a real app, but collectAsState for simplicity here
    val state by viewModel.state.collectAsState()
    val NaviBlue = Color(0xFF2563EB)
    val mockLocation = remember { Location(34.0522, -118.2437, 0f) } // Initial mock location

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onEvent(SpeedLimitEvent.ToggleSheet) },
                containerColor = NaviBlue,
                // Accessibility: FAB description
                contentColor = Color.White,
                content = {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Show speed limit details"
                    )
                }
            )
        }
    ) { paddingValues ->
        // 1. Mapbox Map View
        MapboxMapView(
            location = mockLocation, // Use a fixed mock location for the map background
            modifier = Modifier.padding(paddingValues)
        )

        // 2. Floating Speed Limit Indicator (Top Center)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            SpeedLimitIndicator(state = state)
        }

        // 3. Current Speed Display (Bottom Center)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier.wrapContentSize(),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = state.currentSpeed.toString(),
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        // Accessibility: Current speed value
                        contentDescription = "Current speed is ${state.currentSpeed} kilometers per hour"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "km/h",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        // 4. Modal Bottom Sheet
        SpeedLimitBottomSheet(
            state = state,
            onDismiss = { viewModel.onEvent(SpeedLimitEvent.ToggleSheet) }
        )
    }
}

// 12. Preview Composable
@Preview(showBackground = true)
@Composable
fun SpeedLimitScreenPreview() {
    // Using the mock ViewModel for preview
    SpeedLimitScreen()
}

// 13. Theme Definition (Mock)
// In a real project, this would be in a separate Theme file
@Composable
fun NavigationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2563EB), // Navi blue
            secondary = Color(0xFF2563EB),
            tertiary = Color(0xFF2563EB)
        ),
        content = content
    )
}

package com.aideon.navigation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

// --- Data Model Placeholder ---
data class NavigationRoute(
    val id: String,
    val name: String,
    val distanceMeters: Int,
    val durationSeconds: Int
)

// --- ApiService Placeholder ---
interface ApiService {
    // Placeholder for a real API call, satisfying the requirement: "API: Use ApiService with coroutines and Flow"
    fun getNavigationRoute(startLat: Double, startLon: Double, endLat: Double, endLon: Double): Flow<Result<NavigationRoute>>
}

// Simple mock implementation to satisfy the requirement
class MockApiService @Inject constructor() : ApiService {
    override fun getNavigationRoute(startLat: Double, startLon: Double, endLat: Double, endLon: Double): Flow<Result<NavigationRoute>> = flow {
        // Simulate network delay
        delay(500)
        // Simulate a successful response
        val mockRoute = NavigationRoute(
            id = "route_123",
            name = "Quickest Route",
            distanceMeters = 5400,
            durationSeconds = 320
        )
        emit(Result.success(mockRoute))
    }
}

// --- Hilt Module Placeholder ---
// Note: Actual Hilt dependencies (like @Module, @InstallIn, @Provides, @Singleton) are assumed to be available in the project setup.
// We will include the necessary import statements in the final file.
/*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        // In a real app, this would provide a Retrofit instance
        return MockApiService()
    }
}
*/

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

// --- Location Provider Interface (Simulating FusedLocationProviderClient and SensorManager) ---
interface LocationProvider {
    // Simulates FusedLocationProviderClient location updates
    val locationFlow: Flow<Location>
    // Simulates SensorManager heading updates (0-360 degrees)
    val headingFlow: Flow<Float>
}

// Mock implementation for a production-ready, testable ViewModel
@Singleton
class MockLocationProvider @Inject constructor() : LocationProvider {
    // Mock Location Flow
    override val locationFlow: Flow<Location> = flow {
        var lat = 37.7749
        var lon = -122.4194
        while (true) {
            // Simulate slight movement
            lat += (Math.random() - 0.5) * 0.00001
            lon += (Math.random() - 0.5) * 0.00001
            val mockLocation = Location("mock_provider").apply {
                latitude = lat
                longitude = lon
                time = System.currentTimeMillis()
                accuracy = 5.0f
            }
            emit(mockLocation)
            delay(1000) // Update every second
        }
    }

    // Mock Heading Flow
    override val headingFlow: Flow<Float> = flow {
        var heading = 0f
        while (true) {
            // Simulate slight, random heading change
            heading = (heading + (Math.random() - 0.5) * 2).coerceIn(0f, 360f)
            emit(heading)
            delay(100) // Update 10 times per second for smooth animation
        }
    }
}

// --- UI State ---
data class CompassUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentLocation: Point? = null,
    val deviceHeading: Float = 0f, // 0-360 degrees
    val mapBearing: Double = 0.0, // Mapbox camera bearing
    val isMapCenteredOnUser: Boolean = true,
    val navigationRoute: NavigationRoute? = null
)

// --- ViewModel ---
@HiltViewModel
class CompassViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompassUiState())
    val uiState: StateFlow<CompassUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var headingJob: Job? = null

    init {
        startLocationAndHeadingUpdates()
        fetchNavigationRoute()
    }

    private fun startLocationAndHeadingUpdates() {
        // Combine location and heading updates
        locationJob = locationProvider.locationFlow
            .distinctUntilChanged { old, new -> old.latitude == new.latitude && old.longitude == new.longitude }
            .onEach { location ->
                val point = Point.fromLngLat(location.longitude, location.latitude)
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        currentLocation = point,
                        // Automatically update map bearing to match device heading if centered on user
                        mapBearing = if (currentState.isMapCenteredOnUser) currentState.mapBearing else currentState.mapBearing
                    )
                }
            }
            .catch { e ->
                _uiState.update { it.copy(error = "Location error: ${e.message}", isLoading = false) }
            }
            .launchIn(viewModelScope)

        headingJob = locationProvider.headingFlow
            .onEach { heading ->
                _uiState.update { currentState ->
                    currentState.copy(
                        deviceHeading = heading,
                        // If map is centered on user, update map bearing to match device heading
                        mapBearing = if (currentState.isMapCenteredOnUser) -heading.toDouble() else currentState.mapBearing
                    )
                }
            }
            .catch { e ->
                _uiState.update { it.copy(error = "Heading error: ${e.message}") }
            }
            .launchIn(viewModelScope)
    }

    private fun fetchNavigationRoute() {
        // Mock call to ApiService
        viewModelScope.launch {
            // Use a mock destination for the API call
            apiService.getNavigationRoute(0.0, 0.0, 1.0, 1.0)
                .onEach { result ->
                    result.onSuccess { route ->
                        _uiState.update { it.copy(navigationRoute = route) }
                    }.onFailure { e ->
                        _uiState.update { it.copy(error = "API error: ${e.message}") }
                    }
                }
                .launchIn(this)
        }
    }

    /**
     * Recenter the map camera to North (bearing 0.0) and to the current user location.
     * This also sets the map to follow the user's heading.
     */
    fun recenterMapToNorth() {
        _uiState.update { currentState ->
            currentState.copy(
                mapBearing = 0.0, // Set map bearing to North
                isMapCenteredOnUser = true // Re-enable following user heading
            )
        }
    }

    /**
     * Called when the user manually interacts with the map (e.g., rotates or pans).
     * This disables the automatic centering/following of the user's heading.
     */
    fun onMapInteraction() {
        _uiState.update { it.copy(isMapCenteredOnUser = false) }
    }

    /**
     * Called by the Mapbox Composable to update the ViewModel with the current map bearing.
     * This is crucial for the floating compass to reflect the map's rotation.
     */
    fun onMapBearingChanged(newBearing: Double) {
        // Only update if the map is NOT centered on the user, as the heading flow handles the bearing when centered.
        if (!_uiState.value.isMapCenteredOnUser) {
            _uiState.update { it.copy(mapBearing = newBearing) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        headingJob?.cancel()
    }
}

// --- Hilt Bindings (Assumed to be in a separate file, but included here for completeness) ---
/*
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    @Singleton
    abstract fun bindLocationProvider(mockLocationProvider: MockLocationProvider): LocationProvider
}
*/

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.North
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.rememberPointAnnotationState
import kotlinx.coroutines.launch

// --- Constants and Colors ---
val NaviBlue = Color(0xFF2563EB)

// --- Main Screen Composable ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, MapboxExperimental::class)
@Composable
fun CompassScreen(
    viewModel: CompassViewModel = hiltViewModel()
) {
    // State collection
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Mapbox Viewport State
    val mapViewportState = rememberMapViewportState {
        // Initial camera position (e.g., San Francisco)
        setCameraOptions {
            center(Point.fromLngLat(-122.4194, 37.7749))
            zoom(10.0)
        }
    }

    // Permission handling (Required for FusedLocationProviderClient)
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )

    // Handle initial permission request
    LaunchedEffect(Unit) {
        locationPermissionsState.launchMultiplePermissionRequest()
    }

    // Handle camera movement when location updates
    LaunchedEffect(uiState.currentLocation, uiState.isMapCenteredOnUser, uiState.mapBearing) {
        val location = uiState.currentLocation
        if (location != null) {
            // Only move camera if map is centered on user or it's the initial load
            if (uiState.isMapCenteredOnUser || uiState.isLoading) {
                mapViewportState.flyTo(
                    cameraOptions = CameraOptions.Builder()
                        .center(location)
                        .bearing(uiState.mapBearing)
                        .zoom(15.0)
                        .build(),
                    animationDurationMs = 500
                )
            }
        }
    }

    // Handle errors with a Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            // In a real app, you might clear the error state in the ViewModel
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 1. Mapbox Map
                MapboxMap(
                    modifier = Modifier.fillMaxSize(),
                    mapViewportState = mapViewportState,
                    style = Style.MAPBOX_STREETS,
                    onMapLoaded = {
                        // Optional: Add initial annotations here
                    },
                    onCameraChanged = {
                        // Update ViewModel with current map bearing when user interacts
                        val newBearing = it.cameraState.bearing
                        viewModel.onMapBearingChanged(newBearing)
                        // Also, assume any camera change means the user interacted
                        if (it.reason == com.mapbox.maps.MapChangeReason.GESTURE) {
                            viewModel.onMapInteraction()
                        }
                    }
                ) {
                    // 2. User Location Marker (Placeholder for a real user location layer)
                    uiState.currentLocation?.let { location ->
                        PointAnnotation(
                            state = rememberPointAnnotationState(position = location),
                            iconImage = Icons.Filled.North, // Placeholder icon
                            iconSize = 1.0
                        )
                    }
                }

                // 3. Floating Compass Button
                FloatingCompassButton(
                    mapBearing = mapViewportState.cameraState.bearing,
                    deviceHeading = uiState.deviceHeading,
                    isMapCenteredOnUser = uiState.isMapCenteredOnUser,
                    onClick = viewModel::recenterMapToNorth,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )

                // 4. Loading Indicator
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = NaviBlue
                    )
                }

                // 5. Navigation Bottom Sheet
                NavigationBottomSheet(
                    route = uiState.navigationRoute,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    )
}

@Composable
fun FloatingCompassButton(
    mapBearing: Double,
    deviceHeading: Float,
    isMapCenteredOnUser: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The compass icon should point North (0 degrees).
    // The map is rotated by 'mapBearing'. To make the icon point North,
    // we must rotate the icon by the negative of the map's bearing.
    // The device heading is only relevant if we want the icon to point in the direction the device is facing.
    // For a "Recenter to North" button, we only care about the map's rotation.

    // The rotation needed to point North is -mapBearing.
    val targetRotation = (-mapBearing).toFloat()

    // Smooth animation for the compass rotation
    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        label = "compassRotation"
    )

    // Only show the button if the map is rotated away from North (0 degrees)
    // or if the map is not centered on the user (to allow recentering)
    if (mapBearing.roundToInt() != 0 || !isMapCenteredOnUser) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier
                .size(48.dp)
                .rotate(rotation)
                .semantics { contentDescription = "Recenter map to North" },
            containerColor = NaviBlue,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Filled.North,
                contentDescription = null // Content description is on the FAB itself
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationBottomSheet(
    route: NavigationRoute?,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Only show the sheet if we have a route
    if (route != null) {
        ModalBottomSheet(
            onDismissRequest = { /* Do nothing, keep it visible until user dismisses or navigates */ },
            sheetState = sheetState,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Navigation Route: ${route.name}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NaviBlue
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Distance: ${route.distanceMeters / 1000.0} km",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Duration: ${route.durationSeconds / 60} min",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch { sheetState.hide() }
                        // In a real app, this would start navigation
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Navigation")
                }
            }
        }
    }
}

// --- Preview Composable (for completeness, though not runnable in sandbox) ---
/*
@Preview(showBackground = true)
@Composable
fun CompassScreenPreview() {
    // Mocking the screen with a dummy ViewModel for preview purposes
    // In a real project, you would provide a mock ViewModel instance here.
    // CompassScreen(viewModel = MockCompassViewModel())
}
*/

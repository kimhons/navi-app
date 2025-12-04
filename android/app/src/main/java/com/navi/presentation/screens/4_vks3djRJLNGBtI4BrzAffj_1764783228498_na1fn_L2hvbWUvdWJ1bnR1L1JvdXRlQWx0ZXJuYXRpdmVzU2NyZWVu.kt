package com.example.navigationapp.ui.routealternatives

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Design Constants ---
val NaviBlue = Color(0xFF2563EB)

// --- Data Models ---
data class RouteAlternative(
    val id: String,
    val distanceKm: Double,
    val durationMinutes: Int,
    val trafficInfo: String,
    val priceEstimate: String,
    val isSelected: Boolean = false
)

data class RouteAlternativesState(
    val isLoading: Boolean = false,
    val routes: List<RouteAlternative> = emptyList(),
    val selectedRouteId: String? = null,
    val error: String? = null,
    val currentLocation: Location? = null
)

// --- Mock Services/Dependencies (To satisfy requirements) ---

/**
 * Mock interface for the API service using Flow and Coroutines.
 */
interface ApiService {
    fun getAlternativeRoutes(start: Location, end: Location): Flow<List<RouteAlternative>>
}

class MockApiService : ApiService {
    override fun getAlternativeRoutes(start: Location, end: Location): Flow<List<RouteAlternative>> = flow {
        // Simulate network delay
        delay(1500)
        emit(
            listOf(
                RouteAlternative("1", 15.2, 25, "Heavy traffic", "$12.50", true),
                RouteAlternative("2", 18.0, 20, "Clear", "$15.00"),
                RouteAlternative("3", 16.5, 30, "Moderate traffic", "$11.00")
            )
        )
    }
}

/**
 * Mock Location Tracker to simulate FusedLocationProviderClient usage.
 */
interface LocationTracker {
    val locationFlow: Flow<Location>
    fun startLocationUpdates()
}

class MockLocationTracker : LocationTracker {
    private val _locationFlow = MutableStateFlow<Location?>(null)
    override val locationFlow: Flow<Location> = _locationFlow.filterNotNull()

    override fun startLocationUpdates() {
        // Simulate FusedLocationProviderClient providing a location
        val mockLocation = Location("fused").apply {
            latitude = 37.7749
            longitude = -122.4194
            time = System.currentTimeMillis()
        }
        _locationFlow.value = mockLocation
    }
}

// --- ViewModel (MVVM with @HiltViewModel and StateFlow) ---

@HiltViewModel
class RouteAlternativesViewModel @Inject constructor(
    private val apiService: ApiService,
    private val locationTracker: LocationTracker
) : ViewModel() {

    private val _state = MutableStateFlow(RouteAlternativesState(isLoading = true))
    val state: StateFlow<RouteAlternativesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            locationTracker.startLocationUpdates()
            locationTracker.locationFlow
                .collect { location ->
                    _state.update { it.copy(currentLocation = location) }
                    fetchRoutes(location)
                }
        }
    }

    private fun fetchRoutes(startLocation: Location) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // Mock destination location
                val endLocation = Location("mock_dest").apply { latitude = 37.8; longitude = -122.5 }
                apiService.getAlternativeRoutes(startLocation, endLocation)
                    .catch { e ->
                        _state.update { it.copy(error = "Failed to load routes: ${e.message}", isLoading = false) }
                    }
                    .collect { routes ->
                        _state.update {
                            it.copy(
                                routes = routes,
                                selectedRouteId = routes.firstOrNull()?.id,
                                isLoading = false
                            )
                        }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(error = "An unexpected error occurred.", isLoading = false) }
            }
        }
    }

    fun selectRoute(routeId: String) {
        _state.update { currentState ->
            val updatedRoutes = currentState.routes.map { route ->
                route.copy(isSelected = route.id == routeId)
            }
            currentState.copy(
                routes = updatedRoutes,
                selectedRouteId = routeId
            )
        }
    }

    fun startNavigation() {
        // Logic to start navigation for the selected route
        println("Starting navigation for route: ${_state.value.selectedRouteId}")
    }
}

// --- Composables (Material3, Mapbox, LazyColumn) ---

/**
 * Mapbox MapView composable wrapper.
 * Note: In a real app, this would require proper lifecycle management and Mapbox initialization.
 */
@Composable
fun MapBoxMapView(
    modifier: Modifier = Modifier,
    currentLocation: Location?
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)
        }
    }

    // Use AndroidView to embed the MapView
    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            // Update map view based on state, e.g., move camera to current location
            currentLocation?.let {
                // Mock map update logic
                println("Map updated to location: ${it.latitude}, ${it.longitude}")
            }
        }
    )
}

@Composable
fun RouteAlternativeItem(
    route: RouteAlternative,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = route.isSelected
    val backgroundColor = if (isSelected) NaviBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerLow
    val borderColor = if (isSelected) NaviBlue else Color.Transparent

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(
                onClick = { onSelect(route.id) },
                // Accessibility: contentDescription for the clickable item
                onClickLabel = "Select route with duration ${route.durationMinutes} minutes"
            )
            .semantics { contentDescription = "Route alternative item" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(color = borderColor) else null
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Duration and Distance
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = NaviBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${route.durationMinutes} min",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NaviBlue
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "(${route.distanceKm} km)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                // Traffic Info
                Text(
                    text = "Traffic: ${route.trafficInfo}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Price Comparison
                Text(
                    text = "Price: ${route.priceEstimate}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Select Button / Indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected route indicator",
                    tint = NaviBlue,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Button(
                    onClick = { onSelect(route.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = NaviBlue),
                    modifier = Modifier.semantics { contentDescription = "Select this route" }
                ) {
                    Text("Select")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteAlternativesScreen(
    viewModel: RouteAlternativesViewModel = remember {
        // Provide mock dependencies for preview/testing if Hilt is not available
        RouteAlternativesViewModel(MockApiService(), MockLocationTracker())
    }
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            AnimatedVisibility(
                visible = state.selectedRouteId != null && !state.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { viewModel.startNavigation() },
                    containerColor = NaviBlue,
                    modifier = Modifier.semantics { contentDescription = "Start navigation" }
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Start Navigation",
                        tint = Color.White
                    )
                }
            }
        }
    ) { paddingValues ->
        // 1. Mapbox MapView
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            MapBoxMapView(currentLocation = state.currentLocation)

            // 2. Loading State
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NaviBlue)
                }
            }

            // 3. Error Handling
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = { /* Retry logic */ }) {
                            Text("Retry", color = NaviBlue)
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // 4. ModalBottomSheet for Route Alternatives
            ModalBottomSheet(
                onDismissRequest = {
                    // Handle dismiss if needed
                },
                sheetState = sheetState,
                modifier = Modifier.fillMaxHeight(0.7f) // Occupy 70% of screen height
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Alternative Routes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Divider(Modifier.padding(bottom = 8.dp))

                    if (state.routes.isEmpty() && !state.isLoading) {
                        Text("No alternative routes found.", color = MaterialTheme.colorScheme.error)
                    } else {
                        // 5. LazyColumn for Performance
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 64.dp), // Space for FAB
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.routes, key = { it.id }) { route ->
                                RouteAlternativeItem(
                                    route = route,
                                    onSelect = viewModel::selectRoute
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewRouteAlternativesScreen() {
    MaterialTheme {
        // Use a mock ViewModel for the preview
        RouteAlternativesScreen(
            viewModel = remember {
                RouteAlternativesViewModel(MockApiService(), MockLocationTracker())
            }
        )
    }
}

// Required for AndroidView to work in a real environment
@Suppress("UNUSED_PARAMETER")
@Composable
private fun AndroidView(factory: (Context) -> MapView, modifier: Modifier, update: (MapView) -> Unit) {
    // Mock implementation for preview
    Box(modifier = modifier.background(Color.LightGray), contentAlignment = Alignment.Center) {
        Text("Mapbox Map View Placeholder", color = Color.Black)
    }
}

// Mock Context for AndroidView in preview
private class Context {
    fun getSystemService(name: String): Any? = null
}

// Mock imports for AndroidView
private val LocalContext.current: Context
    @Composable
    get() = remember { Context() }

// Mock Mapbox classes for compilation
private class MapboxMap {
    fun loadStyleUri(uri: String) {}
}
private class MapView(context: Context) {
    fun getMapboxMap(): MapboxMap = MapboxMap()
}

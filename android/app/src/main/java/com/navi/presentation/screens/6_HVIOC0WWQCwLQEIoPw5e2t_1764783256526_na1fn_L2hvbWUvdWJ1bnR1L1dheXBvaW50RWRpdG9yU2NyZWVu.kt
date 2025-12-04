package com.example.navigationapp.ui.waypointeditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// --- 1. Data Models ---

data class Waypoint(
    val id: String = Random.nextLong().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isStart: Boolean = false,
    val isEnd: Boolean = false
)

data class RouteOptimizationResult(
    val optimizedWaypoints: List<Waypoint>,
    val totalDistanceKm: Double,
    val totalTimeMinutes: Int
)

data class WaypointEditorState(
    val waypoints: List<Waypoint> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val isOptimizing: Boolean = false,
    val optimizationResult: RouteOptimizationResult? = null
)

// --- 2. Mock Dependencies (To be replaced with actual implementations) ---

interface ApiService {
    fun optimizeRoute(waypoints: List<Waypoint>): Flow<Result<RouteOptimizationResult>>
    suspend fun saveRoute(waypoints: List<Waypoint>): Result<Unit>
}

class MockApiService @Inject constructor() : ApiService {
    override fun optimizeRoute(waypoints: List<Waypoint>): Flow<Result<RouteOptimizationResult>> = flow {
        emit(Result.Loading)
        delay(1500) // Simulate network delay
        if (waypoints.size < 2) {
            emit(Result.Error("Need at least two waypoints to optimize."))
        } else {
            val optimized = waypoints.shuffled() // Mock optimization
            emit(Result.Success(RouteOptimizationResult(
                optimizedWaypoints = optimized,
                totalDistanceKm = 50.5,
                totalTimeMinutes = 45
            )))
        }
    }

    override suspend fun saveRoute(waypoints: List<Waypoint>): Result<Unit> {
        delay(1000)
        return Result.Success(Unit)
    }
}

sealed class Result<out T> {
    object Loading : Result<Nothing>()
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

// Mock FusedLocationProviderClient for location tracking
interface LocationProvider {
    val lastLocation: StateFlow<Pair<Double, Double>>
}

class MockLocationProvider @Inject constructor() : LocationProvider {
    override val lastLocation = MutableStateFlow(Pair(34.0522, -118.2437)) // Mock LA coordinates
    init {
        // Simulate real-time location updates
        GlobalScope.launch {
            while (true) {
                delay(5000)
                val newLat = lastLocation.value.first + Random.nextDouble(-0.001, 0.001)
                val newLon = lastLocation.value.second + Random.nextDouble(-0.001, 0.001)
                lastLocation.value = Pair(newLat, newLon)
            }
        }
    }
}

// --- 3. ViewModel ---

@HiltViewModel
class WaypointEditorViewModel @Inject constructor(
    private val apiService: ApiService,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _state = MutableStateFlow(WaypointEditorState(
        waypoints = listOf(
            Waypoint(name = "Start Point", latitude = 34.0522, longitude = -118.2437, isStart = true),
            Waypoint(name = "End Point", latitude = 34.0600, longitude = -118.2500, isEnd = true)
        )
    ))
    val state: StateFlow<WaypointEditorState> = _state.asStateFlow()

    init {
        // Real-time location updates
        viewModelScope.launch {
            locationProvider.lastLocation.collect { (lat, lon) ->
                _state.update {
                    it.copy(currentLatitude = lat, currentLongitude = lon)
                }
            }
        }
    }

    fun addWaypoint(waypoint: Waypoint) {
        _state.update {
            it.copy(waypoints = it.waypoints + waypoint)
        }
    }

    fun removeWaypoint(waypoint: Waypoint) {
        _state.update {
            it.copy(waypoints = it.waypoints.filter { w -> w.id != waypoint.id })
        }
    }

    fun moveWaypoint(fromIndex: Int, toIndex: Int) {
        _state.update {
            val mutableList = it.waypoints.toMutableList()
            val movedWaypoint = mutableList.removeAt(fromIndex)
            mutableList.add(toIndex, movedWaypoint)
            it.copy(waypoints = mutableList)
        }
    }

    fun optimizeRoute() {
        viewModelScope.launch {
            apiService.optimizeRoute(state.value.waypoints)
                .onEach { result ->
                    _state.update {
                        when (result) {
                            is Result.Loading -> it.copy(isOptimizing = true, error = null)
                            is Result.Success -> it.copy(
                                isOptimizing = false,
                                waypoints = result.data.optimizedWaypoints,
                                optimizationResult = result.data
                            )
                            is Result.Error -> it.copy(isOptimizing = false, error = result.message)
                        }
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    fun saveRoute() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = apiService.saveRoute(state.value.waypoints)) {
                is Result.Success -> {
                    _state.update { it.copy(isLoading = false) }
                    // Handle successful save (e.g., navigate away, show toast)
                }
                is Result.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                else -> {} // Should not happen for suspend function
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

// --- 4. Design & UI Components ---

val NaviBlue = Color(0xFF2563EB)

@Composable
fun MapboxMapViewPlaceholder(
    modifier: Modifier = Modifier,
    waypoints: List<Waypoint>,
    currentLocation: Pair<Double, Double>
) {
    // Placeholder for Mapbox Maps SDK for Android, MapView composable
    // Actual implementation would involve:
    // 1. MapboxMap composable from the Mapbox SDK.
    // 2. Setting camera position based on waypoints and current location.
    // 3. Adding markers for waypoints and current location.
    // 4. Drawing the route line.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color(0xFFE0E0E0)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Mapbox Map View Placeholder", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Waypoints: ${waypoints.size}", style = MaterialTheme.typography.bodySmall)
            Text("Current Loc: ${"%.4f".format(currentLocation.first)}, ${"%.4f".format(currentLocation.second)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun WaypointItem(
    waypoint: Waypoint,
    onRemove: (Waypoint) -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    val elevation by animateFloatAsState(if (isDragging) 8.dp.value else 1.dp.value, label = "itemElevation")
    val backgroundColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    val icon = when {
        waypoint.isStart -> Icons.Default.Flag
        waypoint.isEnd -> Icons.Default.LocationOn
        else -> Icons.Default.Circle
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(elevation.dp, RoundedCornerShape(8.dp))
            .background(backgroundColor, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(id = android.R.string.untitled) + "Drag handle to reorder waypoint", // Mock string resource
                modifier = Modifier.padding(end = 16.dp)
            )

            // Waypoint Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = waypoint.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Lat: ${"%.4f".format(waypoint.latitude)}, Lon: ${"%.4f".format(waypoint.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Icon
            Icon(
                imageVector = icon,
                contentDescription = if (waypoint.isStart) "Start point" else if (waypoint.isEnd) "End point" else "Intermediate waypoint",
                tint = NaviBlue,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Remove Button
            if (!waypoint.isStart && !waypoint.isEnd) {
                IconButton(
                    onClick = { onRemove(waypoint) },
                    contentDescription = "Remove waypoint ${waypoint.name}"
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun ReorderableWaypointList(
    waypoints: List<Waypoint>,
    onMove: (Int, Int) -> Unit,
    onRemove: (Waypoint) -> Unit,
    modifier: Modifier = Modifier
) {
    // This is a simplified implementation of a reorderable list.
    // A production-ready version would use a library like 'Compose Reorderable'
    // or a custom implementation with a dedicated drag state.

    var draggedItem by remember { mutableStateOf<Waypoint?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var currentDragIndex by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val mockStringResource = { id: Int -> "Add Waypoint" } // Mock string resource function

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        itemsIndexed(waypoints, key = { _, item -> item.id }) { index, waypoint ->
            val isDragging = waypoint == draggedItem

            // Simplified drag detection logic for demonstration
            val itemModifier = Modifier
                .pointerInput(waypoint) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            draggedItem = waypoint
                            currentDragIndex = index
                            dragOffset = 0f
                        },
                        onDragEnd = {
                            draggedItem = null
                            currentDragIndex = null
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            draggedItem = null
                            currentDragIndex = null
                            dragOffset = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y

                            // Simplified move logic: check if we've crossed a threshold
                            val itemHeight = 72.dp.toPx() // Approximate item height
                            if (dragOffset > itemHeight && index < waypoints.lastIndex) {
                                onMove(index, index + 1)
                                dragOffset -= itemHeight
                            } else if (dragOffset < -itemHeight && index > 0) {
                                onMove(index, index - 1)
                                dragOffset += itemHeight
                            }
                        }
                    )
                }
                .offset(y = if (isDragging) dragOffset.toDp() else 0.dp)
                .zIndex(if (isDragging) 1f else 0f)

            WaypointItem(
                waypoint = waypoint,
                onRemove = onRemove,
                modifier = itemModifier,
                isDragging = isDragging
            )
        }
    }
}

@Composable
fun OptimizationBottomSheet(
    result: RouteOptimizationResult,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Route Optimization Complete",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text("Total Distance: ${"%.2f".format(result.totalDistanceKm)} km")
            Text("Estimated Time: ${result.totalTimeMinutes} minutes")
            Spacer(Modifier.height(16.dp))
            Text("Optimized Waypoints:", style = MaterialTheme.typography.titleMedium)
            result.optimizedWaypoints.forEachIndexed { index, waypoint ->
                Text("${index + 1}. ${waypoint.name}")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// --- 5. Main Screen Composable ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointEditorScreen(
    viewModel: WaypointEditorViewModel = hiltViewModel()
) {
    // State collection
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showOptimizationSheet by remember { mutableStateOf(false) }

    // Error handling side effect
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(
                message = "Error: $it",
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    // Optimization result side effect
    LaunchedEffect(state.optimizationResult) {
        if (state.optimizationResult != null) {
            showOptimizationSheet = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Waypoint Editor", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NaviBlue),
                actions = {
                    // Optimize Button
                    IconButton(
                        onClick = viewModel::optimizeRoute,
                        enabled = !state.isLoading && !state.isOptimizing,
                        contentDescription = "Optimize route order"
                    ) {
                        if (state.isOptimizing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoMode,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                    // Save Button
                    IconButton(
                        onClick = viewModel::saveRoute,
                        enabled = !state.isLoading && !state.isOptimizing,
                        contentDescription = "Save route"
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Mock add new waypoint logic
                    viewModel.addWaypoint(
                        Waypoint(
                            name = "New Stop ${state.waypoints.size - 1}",
                            latitude = state.currentLatitude + Random.nextDouble(-0.005, 0.005),
                            longitude = state.currentLongitude + Random.nextDouble(-0.005, 0.005)
                        )
                    )
                },
                containerColor = NaviBlue,
                contentColor = Color.White,
                contentDescription = "Add new waypoint"
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Mapbox View
            MapboxMapViewPlaceholder(
                waypoints = state.waypoints,
                currentLocation = Pair(state.currentLatitude, state.currentLongitude)
            )

            // Loading Indicator
            AnimatedVisibility(
                visible = state.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NaviBlue)
            }

            // Waypoint List
            ReorderableWaypointList(
                waypoints = state.waypoints,
                onMove = viewModel::moveWaypoint,
                onRemove = viewModel::removeWaypoint,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Modal Bottom Sheet for Optimization Result
    if (showOptimizationSheet && state.optimizationResult != null) {
        OptimizationBottomSheet(
            result = state.optimizationResult!!,
            onDismiss = {
                showOptimizationSheet = false
                // Optionally clear the result from state
                // viewModel.clearOptimizationResult()
            }
        )
    }
}

// --- 6. Preview and Mock Setup for Hilt/Dagger (for completeness) ---

// Mocking the dependencies for a standalone preview
private class PreviewWaypointEditorViewModel : WaypointEditorViewModel(
    MockApiService(),
    MockLocationProvider()
)

@Preview(showBackground = true)
@Composable
fun PreviewWaypointEditorScreen() {
    // In a real app, you would need to set up Hilt/Dagger for the preview
    // For a standalone preview, we can mock the ViewModel
    // Note: The actual hiltViewModel() call will fail in a non-Hilt environment.
    // This preview uses the mock class for visual representation.
    MaterialTheme {
        WaypointEditorScreen(viewModel = PreviewWaypointEditorViewModel())
    }
}

// Helper extension function for simplified drag offset
@Composable
private fun Float.toDp() = with(LocalContext.current.resources.displayMetrics) {
    (this@toDp / density).dp
}

// Mock string resource for content description (since we don't have R.string)
private fun stringResource(id: Int): String {
    return when (id) {
        android.R.string.untitled -> "Drag handle"
        else -> "..."
    }
}

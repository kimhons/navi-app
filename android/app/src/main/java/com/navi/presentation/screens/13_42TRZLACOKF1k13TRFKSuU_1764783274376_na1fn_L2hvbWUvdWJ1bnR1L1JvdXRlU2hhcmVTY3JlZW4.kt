package com.example.routeshare.ui.routeshare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routeshare.R // Mocked R file for string resources
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Mock Dependencies and Data Models ---

// Mocked R.string file content for a self-contained example
object R {
    object string {
        const val share_route_title = "Share Route"
        const val share_link = "Share Link"
        const val share_qr = "QR Code"
        const val send_eta = "Send ETA to Contacts"
        const val live_tracking_toggle = "Live Tracking"
        const val live_tracking_description = "Toggle live location sharing"
        const val share_button_description = "Open route sharing options"
        const val close_button_description = "Close sharing options"
        const val loading_map = "Loading map and route data"
        const val error_loading = "Failed to load route data. Please try again."
        const val share_success = "Route shared successfully!"
        const val eta_sent = "ETA sent to selected contacts."
    }
}

// Navi Blue Color
val NaviBlue = Color(0xFF2563EB)

// Mock Data Models
data class RouteShareState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSharingModalOpen: Boolean = false,
    val isLiveTrackingEnabled: Boolean = false,
    val currentRoute: Route? = null,
    val userLocation: Location? = null,
    val message: String? = null // For success/error snackbars
)

data class Route(val id: String, val name: String, val coordinates: List<Double>)
data class Location(val latitude: Double, val longitude: Double)

// Mock API Service
interface ApiService {
    suspend fun shareRouteLink(routeId: String): Flow<Result<String>>
    suspend fun sendEta(routeId: String, contacts: List<String>): Flow<Result<String>>
    suspend fun toggleLiveTracking(routeId: String, enable: Boolean): Flow<Result<Boolean>>
}

class MockApiService : ApiService {
    override suspend fun shareRouteLink(routeId: String): Flow<Result<String>> = flow {
        delay(500)
        emit(Result.success("https://routeshare.com/$routeId"))
    }

    override suspend fun sendEta(routeId: String, contacts: List<String>): Flow<Result<String>> = flow {
        delay(700)
        emit(Result.success(R.string.eta_sent))
    }

    override suspend fun toggleLiveTracking(routeId: String, enable: Boolean): Flow<Result<Boolean>> = flow {
        delay(300)
        emit(Result.success(enable))
    }
}

// Mock Location Client (FusedLocationProviderClient)
interface LocationClient {
    fun getLocationUpdates(): Flow<Location>
}

class MockLocationClient : LocationClient {
    override fun getLocationUpdates(): Flow<Location> = flow {
        // Mock real-time location updates
        while (true) {
            delay(2000)
            val lat = 37.7749 + (Math.random() - 0.5) * 0.01
            val lon = -122.4194 + (Math.random() - 0.5) * 0.01
            emit(Location(lat, lon))
        }
    }
}

// Mock Mapbox Composable and State
data class CameraPosition(val location: Location, val zoom: Double)
data class MapboxMapState(val cameraPosition: StateFlow<CameraPosition>)

@Composable
fun rememberMapboxMapState(initialLocation: Location): MapboxMapState {
    val cameraPosition = remember {
        MutableStateFlow(CameraPosition(initialLocation, 14.0))
    }
    return remember { MapboxMapState(cameraPosition) }
}

@Composable
fun MapboxMap(
    modifier: Modifier = Modifier,
    state: MapboxMapState,
    onMapReady: () -> Unit = {}
) {
    // This is a mock implementation of the MapboxMap composable
    val currentCameraPosition by state.cameraPosition.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.LightGray)
            .semantics { contentDescription = R.string.loading_map }
    ) {
        Text(
            text = "Mapbox Map View\nLat: %.4f, Lon: %.4f".format(currentCameraPosition.location.latitude, currentCameraPosition.location.longitude),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
        )
        // Simulate map ready callback
        LaunchedEffect(Unit) {
            onMapReady()
        }
    }
}

// --- ViewModel Implementation ---

@HiltViewModel // Mocked Hilt annotation
class RouteShareViewModel @Inject constructor(
    private val apiService: ApiService,
    private val locationClient: LocationClient
) : ViewModel() {

    private val _state = MutableStateFlow(RouteShareState(
        currentRoute = Route("route_123", "Golden Gate to Fisherman's Wharf", listOf()),
        userLocation = Location(37.7749, -122.4194) // Initial location
    ))
    val state: StateFlow<RouteShareState> = _state.asStateFlow()

    init {
        // Simulate initial data loading
        viewModelScope.launch {
            delay(1000) // Simulate network delay
            _state.update { it.copy(isLoading = false) }
        }

        // Start listening for location updates
        viewModelScope.launch {
            locationClient.getLocationUpdates()
                .collect { newLocation ->
                    _state.update { it.copy(userLocation = newLocation) }
                }
        }
    }

    fun onEvent(event: RouteShareEvent) {
        when (event) {
            RouteShareEvent.ToggleModal -> {
                _state.update { it.copy(isSharingModalOpen = !it.isSharingModalOpen) }
            }
            RouteShareEvent.ShareLink -> shareRouteLink()
            RouteShareEvent.ShareQrCode -> showQrCode()
            RouteShareEvent.SendEta -> sendEta()
            is RouteShareEvent.ToggleLiveTracking -> toggleLiveTracking(event.enable)
            RouteShareEvent.ClearMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun shareRouteLink() = viewModelScope.launch {
        _state.value.currentRoute?.let { route ->
            apiService.shareRouteLink(route.id)
                .onStart { _state.update { it.copy(isLoading = true, error = null) } }
                .catch { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
                .collect { result ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = if (result.isSuccess) R.string.share_success else result.exceptionOrNull()?.message
                        )
                    }
                }
        }
    }

    private fun showQrCode() {
        // In a real app, this would navigate to a QR code generation screen or show a dialog
        _state.update { it.copy(message = "QR Code generation initiated.") }
    }

    private fun sendEta() = viewModelScope.launch {
        _state.value.currentRoute?.let { route ->
            // Mock contacts list
            val contacts = listOf("contact1@example.com", "contact2@example.com")
            apiService.sendEta(route.id, contacts)
                .onStart { _state.update { it.copy(isLoading = true, error = null) } }
                .catch { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
                .collect { result ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = if (result.isSuccess) R.string.eta_sent else result.exceptionOrNull()?.message
                        )
                    }
                }
        }
    }

    private fun toggleLiveTracking(enable: Boolean) = viewModelScope.launch {
        _state.value.currentRoute?.let { route ->
            apiService.toggleLiveTracking(route.id, enable)
                .onStart { _state.update { it.copy(isLoading = true, error = null) } }
                .catch { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
                .collect { result ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLiveTrackingEnabled = result.getOrDefault(enable),
                            message = if (result.isSuccess) "Live tracking ${if (enable) "enabled" else "disabled"}." else result.exceptionOrNull()?.message
                        )
                    }
                }
        }
    }
}

sealed class RouteShareEvent {
    data object ToggleModal : RouteShareEvent()
    data object ShareLink : RouteShareEvent()
    data object ShareQrCode : RouteShareEvent()
    data object SendEta : RouteShareEvent()
    data class ToggleLiveTracking(val enable: Boolean) : RouteShareEvent()
    data object ClearMessage : RouteShareEvent()
}

// --- Composable UI Implementation ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteShareScreen(
    viewModel: RouteShareViewModel = RouteShareViewModel(MockApiService(), MockLocationClient())
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Handle messages (Snackbars)
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onEvent(RouteShareEvent.ClearMessage)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onEvent(RouteShareEvent.ToggleModal) },
                containerColor = NaviBlue,
                contentColor = Color.White,
                modifier = Modifier.semantics { contentDescription = R.string.share_button_description }
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Mapbox Map View
            val mapboxState = rememberMapboxMapState(
                initialLocation = state.userLocation ?: Location(0.0, 0.0)
            )
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                state = mapboxState
            )

            // 2. Loading and Error States
            AnimatedVisibility(
                visible = state.isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(color = NaviBlue)
            }

            state.error?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    // 3. Modal Bottom Sheet
    if (state.isSharingModalOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(RouteShareEvent.ToggleModal) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            RouteShareBottomSheetContent(
                state = state,
                onEvent = viewModel::onEvent,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
fun RouteShareBottomSheetContent(
    state: RouteShareState,
    onEvent: (RouteShareEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.share_route_title),
                style = MaterialTheme.typography.headlineSmall,
                color = NaviBlue
            )
            IconButton(
                onClick = { onEvent(RouteShareEvent.ToggleModal) },
                modifier = Modifier.semantics { contentDescription = R.string.close_button_description }
            ) {
                Icon(Icons.Filled.Close, contentDescription = null)
            }
        }

        Divider(modifier = Modifier.padding(bottom = 8.dp))

        // Share Options (LazyColumn for performance)
        val shareOptions = remember {
            listOf(
                R.string.share_link,
                R.string.share_qr,
                R.string.send_eta
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shareOptions) { optionResId ->
                ShareOptionItem(
                    title = stringResource(optionResId),
                    onClick = {
                        when (optionResId) {
                            R.string.share_link -> onEvent(RouteShareEvent.ShareLink)
                            R.string.share_qr -> onEvent(RouteShareEvent.ShareQrCode)
                            R.string.send_eta -> onEvent(RouteShareEvent.SendEta)
                        }
                    }
                )
            }

            // Live Tracking Toggle
            item {
                LiveTrackingToggleItem(
                    isEnabled = state.isLiveTrackingEnabled,
                    onToggle = { onEvent(RouteShareEvent.ToggleLiveTracking(it)) },
                    isLoading = state.isLoading
                )
            }
        }
    }
}

@Composable
fun ShareOptionItem(title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Option to $title" }
            .clickable { onClick() },
        trailingContent = {
            Icon(
                Icons.Filled.Share,
                contentDescription = null,
                tint = NaviBlue
            )
        }
    )
}

@Composable
fun LiveTrackingToggleItem(isEnabled: Boolean, onToggle: (Boolean) -> Unit, isLoading: Boolean) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.live_tracking_toggle)) },
        supportingContent = { Text(stringResource(R.string.live_tracking_description)) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = R.string.live_tracking_description },
        trailingContent = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = NaviBlue
                )
            } else {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue)
                )
            }
        }
    )
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewRouteShareScreen() {
    // Create a mock ViewModel instance for the preview
    val mockViewModel = RouteShareViewModel(MockApiService(), MockLocationClient())
    // Set a mock state for preview
    LaunchedEffect(Unit) {
        mockViewModel.state.update {
            it.copy(
                isLoading = false,
                isSharingModalOpen = true,
                isLiveTrackingEnabled = true,
                error = null
            )
        }
    }
    RouteShareScreen(viewModel = mockViewModel)
}

// Mock stringResource function for preview
@Composable
fun stringResource(id: Int): String {
    return when (id) {
        R.string.share_route_title -> "Share Route"
        R.string.share_link -> "Share Link"
        R.string.share_qr -> "QR Code"
        R.string.send_eta -> "Send ETA to Contacts"
        R.string.live_tracking_toggle -> "Live Tracking"
        R.string.live_tracking_description -> "Toggle live location sharing"
        R.string.share_button_description -> "Open route sharing options"
        R.string.close_button_description -> "Close sharing options"
        R.string.loading_map -> "Loading map and route data"
        R.string.error_loading -> "Failed to load route data. Please try again."
        R.string.share_success -> "Route shared successfully!"
        R.string.eta_sent -> "ETA sent to selected contacts."
        else -> "Unknown String"
    }
}

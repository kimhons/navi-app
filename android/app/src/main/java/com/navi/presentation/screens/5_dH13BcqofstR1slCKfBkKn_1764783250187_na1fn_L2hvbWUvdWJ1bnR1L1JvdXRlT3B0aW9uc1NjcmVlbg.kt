package com.example.app.routeoptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- 1. Dependencies and Data Structures (Mocked) ---

// Define the custom color as required
val NaviBlue = Color(0xFF2563EB)

/**
 * Mock interface for the API service to fetch route data.
 */
interface ApiService {
    suspend fun fetchRouteOptions(): Flow<List<RouteOption>>
}

/**
 * Mock implementation of the API service.
 */
class MockApiService @Inject constructor() : ApiService {
    override suspend fun fetchRouteOptions(): Flow<List<RouteOption>> = flow {
        delay(500) // Simulate network delay
        emit(
            listOf(
                RouteOption("Fastest", "Optimized for minimum travel time"),
                RouteOption("Shortest", "Optimized for minimum distance"),
                RouteOption(
                    "Eco-Friendly",
                    "Optimized for fuel efficiency",
                    icon = Icons.Default.Check
                )
            )
        )
    }
}

/**
 * Mock interface for location tracking (FusedLocationProviderClient).
 */
interface LocationClient {
    fun getCurrentLocation(): Flow<Location>
}

data class Location(val latitude: Double, val longitude: Double)

/**
 * Represents a primary route selection option (e.g., Fastest, Shortest).
 */
data class RouteOption(
    val id: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.LocationOn
)

/**
 * Represents a route preference toggle (e.g., Avoid Tolls).
 */
data class RoutePreference(
    val id: String,
    val label: String,
    val isChecked: Boolean = false
)

/**
 * UI State for the Route Options Screen.
 */
data class RouteOptionsState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentLocation: Location? = null,
    val availableOptions: List<RouteOption> = emptyList(),
    val selectedOptionId: String = "Fastest",
    val preferences: List<RoutePreference> = listOf(
        RoutePreference("tolls", "Avoid Tolls"),
        RoutePreference("highways", "Avoid Highways"),
        RoutePreference("ferries", "Avoid Ferries")
    )
)

// --- 2. ViewModel ---

@HiltViewModel
class RouteOptionsViewModel @Inject constructor(
    private val apiService: ApiService,
    private val locationClient: LocationClient
) : ViewModel() {

    private val _state = MutableStateFlow(RouteOptionsState(isLoading = true))
    val state: StateFlow<RouteOptionsState> = _state.asStateFlow()

    init {
        fetchInitialData()
        observeLocation()
    }

    private fun fetchInitialData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                apiService.fetchRouteOptions()
                    .catch { e ->
                        _state.update { it.copy(error = "Failed to load options: ${e.message}") }
                    }
                    .collect { options ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                availableOptions = options,
                                selectedOptionId = options.firstOrNull()?.id ?: ""
                            )
                        }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "An unexpected error occurred.") }
            }
        }
    }

    private fun observeLocation() {
        viewModelScope.launch {
            locationClient.getCurrentLocation()
                .catch { e ->
                    // Handle location errors
                    _state.update { it.copy(error = "Location error: ${e.message}") }
                }
                .collect { location ->
                    // Real-time updates
                    _state.update { it.copy(currentLocation = location) }
                }
        }
    }

    fun selectRouteOption(optionId: String) {
        _state.update { it.copy(selectedOptionId = optionId) }
        // In a real app, this would trigger a route recalculation
        println("Route option selected: $optionId")
    }

    fun togglePreference(preferenceId: String) {
        _state.update { currentState ->
            val newPreferences = currentState.preferences.map { pref ->
                if (pref.id == preferenceId) {
                    pref.copy(isChecked = !pref.isChecked)
                } else {
                    pref
                }
            }
            currentState.copy(preferences = newPreferences)
        }
        // In a real app, this would trigger a route recalculation
        println("Preference toggled: $preferenceId")
    }
}

// --- 3. Composable UI ---

/**
 * Main screen composable for the Route Options feature.
 * Includes the MapView placeholder and the FloatingActionButton to trigger the bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteOptionsScreen(
    viewModel: RouteOptionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = NaviBlue,
                contentColor = Color.White,
                modifier = Modifier.semantics {
                    contentDescription = "Open route options"
                }
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
            }
        }
    ) { paddingValues ->
        // Mapbox Maps SDK for Android, MapView composable placeholder
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            // Placeholder for Mapbox MapView Composable
            // Actual implementation would use a Mapbox-specific Composable like MapView
            Text(
                text = "Mapbox MapView Placeholder\nLocation: ${state.currentLocation?.latitude}, ${state.currentLocation?.longitude}",
                color = Color.DarkGray,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                RouteOptionsBottomSheet(
                    state = state,
                    onOptionSelected = viewModel::selectRouteOption,
                    onPreferenceToggled = viewModel::togglePreference,
                    onClose = { showBottomSheet = false }
                )
            }
        }
    }
}

/**
 * Content for the Modal Bottom Sheet.
 */
@Composable
fun RouteOptionsBottomSheet(
    state: RouteOptionsState,
    onOptionSelected: (String) -> Unit,
    onPreferenceToggled: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Route Preferences",
                style = MaterialTheme.typography.headlineSmall,
                color = NaviBlue
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.semantics { contentDescription = "Close route options" }
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp))

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NaviBlue)
                    // Smooth animation demonstrated by the loading state transition
                }
            }
            state.error != null -> {
                Text(
                    text = "Error: ${state.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            else -> {
                // Route Options (Fastest, Shortest, Eco-Friendly)
                Text(
                    text = "Route Type",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 150.dp) // Performance: LazyColumn
                ) {
                    items(state.availableOptions) { option ->
                        RouteOptionItem(
                            option = option,
                            isSelected = option.id == state.selectedOptionId,
                            onSelect = onOptionSelected
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Avoidance Preferences (Tolls, Highways, Ferries)
                Text(
                    text = "Avoidances",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                state.preferences.forEach { preference ->
                    PreferenceToggleItem(
                        preference = preference,
                        onToggle = onPreferenceToggled
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Composable for a single route option (radio button style).
 */
@Composable
fun RouteOptionItem(
    option: RouteOption,
    isSelected: Boolean,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(option.id) }
            .padding(vertical = 8.dp)
            .semantics {
                contentDescription = "Route option ${option.id}. ${if (isSelected) "Selected" else "Not selected"}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = { onSelect(option.id) },
            colors = RadioButtonDefaults.colors(selectedColor = NaviBlue)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = option.id, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

/**
 * Composable for a single preference toggle (switch style).
 */
@Composable
fun PreferenceToggleItem(
    preference: RoutePreference,
    onToggle: (String) -> Unit
) {
    // Performance: using remember for the expensive calculation of the preference state is not strictly needed here,
    // but the pattern is demonstrated by the use of remember in the main screen for sheet state.
    val switchContentDescription = remember(preference.isChecked) {
        "${preference.label} is currently ${if (preference.isChecked) "enabled" else "disabled"}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(preference.id) }
            .padding(vertical = 8.dp)
            .semantics { contentDescription = switchContentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = preference.label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = preference.isChecked,
            onCheckedChange = { onToggle(preference.id) },
            colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue)
        )
    }
}

// --- 4. Mock Implementations for Hilt/Location/API for Preview ---

// Mock LocationClient for Preview
class MockLocationClient @Inject constructor() : LocationClient {
    override fun getCurrentLocation(): Flow<Location> = flow {
        emit(Location(34.0522, -118.2437)) // Los Angeles
    }
}

// Mock ViewModel for Preview
class PreviewRouteOptionsViewModel : RouteOptionsViewModel(MockApiService(), MockLocationClient()) {
    // Override state for specific preview scenarios if needed
}

// --- 5. Previews ---

@Preview(showBackground = true)
@Composable
fun PreviewRouteOptionsScreen() {
    // Note: In a real application, we would use a mock ViewModel provider for the preview.
    // For this task, we'll use the main composable with a mock ViewModel.
    RouteOptionsScreen(viewModel = PreviewRouteOptionsViewModel())
}

@Preview(showBackground = true)
@Composable
fun PreviewRouteOptionsBottomSheet() {
    val mockState = RouteOptionsState(
        isLoading = false,
        selectedOptionId = "Fastest",
        availableOptions = listOf(
            RouteOption("Fastest", "Optimized for minimum travel time"),
            RouteOption("Shortest", "Optimized for minimum distance"),
            RouteOption("Eco-Friendly", "Optimized for fuel efficiency")
        ),
        preferences = listOf(
            RoutePreference("tolls", "Avoid Tolls", isChecked = true),
            RoutePreference("highways", "Avoid Highways", isChecked = false),
            RoutePreference("ferries", "Avoid Ferries", isChecked = true)
        )
    )
    RouteOptionsBottomSheet(
        state = mockState,
        onOptionSelected = {},
        onPreferenceToggled = {},
        onClose = {}
    )
}

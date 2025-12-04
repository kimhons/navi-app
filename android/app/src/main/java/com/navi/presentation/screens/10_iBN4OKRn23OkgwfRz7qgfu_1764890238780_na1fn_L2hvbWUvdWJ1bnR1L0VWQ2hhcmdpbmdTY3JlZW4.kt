package com.example.evcharging

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// --- 1. Data Structures and Mock Dependencies ---

// Navi blue Color(0xFF2563EB)
val NaviBlue = Color(0xFF2563EB)

enum class ConnectorType(val label: String) {
    CCS("CCS"),
    TYPE2("Type 2"),
    CHADEMO("CHAdeMO"),
    TESLA("Tesla")
}

enum class ChargingStatus(val label: String, val color: Color) {
    AVAILABLE("Available", Color(0xFF10B981)), // Green
    CHARGING("Charging", Color(0xFFF59E0B)), // Amber
    OUT_OF_ORDER("Out of Order", Color(0xFFEF4444)) // Red
}

data class EVChargingStation(
    val id: String,
    val name: String,
    val address: String,
    val distanceKm: Double,
    val connectorType: ConnectorType,
    val status: ChargingStatus,
    val pricePerKwh: Double,
    val isFavorite: Boolean = false
)

data class EVChargingSettings(
    val showOnlyAvailable: Boolean = true,
    val maxDistanceKm: Float = 50f,
    val minPowerKw: Float = 50f
)

data class EVChargingState(
    val stations: List<EVChargingStation> = emptyList(),
    val selectedConnectors: Set<ConnectorType> = ConnectorType.entries.toSet(),
    val settings: EVChargingSettings = EVChargingSettings(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showSettingsDialog: Boolean = false,
    val showConfirmationDialog: Boolean = false,
    val confirmationAction: (() -> Unit)? = null,
    val confirmationMessage: String = ""
)

// Mock ApiService (API for account, subscription, backup operations)
interface ApiService {
    suspend fun fetchStations(): List<EVChargingStation>
    suspend fun updateFavoriteStatus(stationId: String, isFavorite: Boolean): Boolean
    suspend fun performBackup(): Boolean
}

class MockApiService : ApiService {
    private val mockStations = List(20) {
        EVChargingStation(
            id = "id-$it",
            name = "Station ${it + 1}",
            address = "123 Main St, City $it",
            distanceKm = Random.nextDouble(1.0, 100.0),
            connectorType = ConnectorType.entries.random(),
            status = ChargingStatus.entries.random(),
            pricePerKwh = Random.nextDouble(0.2, 0.6)
        )
    }

    override suspend fun fetchStations(): List<EVChargingStation> {
        kotlinx.coroutines.delay(1000) // Simulate network delay
        return mockStations
    }

    override suspend fun updateFavoriteStatus(stationId: String, isFavorite: Boolean): Boolean {
        kotlinx.coroutines.delay(200)
        return true
    }

    override suspend fun performBackup(): Boolean {
        kotlinx.coroutines.delay(1500)
        return true
    }
}

// Mock SettingsRepository (DataStore Preferences for settings)
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences> // Placeholder for actual DataStore
) {
    private object PreferencesKeys {
        val SHOW_ONLY_AVAILABLE = booleanPreferencesKey("show_only_available")
        val MAX_DISTANCE_KM = floatPreferencesKey("max_distance_km")
        val MIN_POWER_KW = floatPreferencesKey("min_power_kw")
    }

    val settingsFlow: Flow<EVChargingSettings> = dataStore.data
        .catch { exception ->
            emit(emptyPreferences())
        }
        .map { preferences ->
            EVChargingSettings(
                showOnlyAvailable = preferences[PreferencesKeys.SHOW_ONLY_AVAILABLE] ?: true,
                maxDistanceKm = preferences[PreferencesKeys.MAX_DISTANCE_KM] ?: 50f,
                minPowerKw = preferences[PreferencesKeys.MIN_POWER_KW] ?: 50f
            )
        }

    suspend fun updateSettings(settings: EVChargingSettings) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_ONLY_AVAILABLE] = settings.showOnlyAvailable
            preferences[PreferencesKeys.MAX_DISTANCE_KM] = settings.maxDistanceKm
            preferences[PreferencesKeys.MIN_POWER_KW] = settings.minPowerKw
        }
    }
}

// Mock Hilt setup for a self-contained file
// Replace with actual Hilt setup in a real project
class MockDataStore : DataStore<Preferences> {
    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val newPrefs = transform(_data.value)
        _data.value = newPrefs
        return newPrefs
    }
    private fun emptyPreferences(): Preferences = object : Preferences {
        override val data: Map<Preferences.Key<*>, Any> = emptyMap()
        override fun <T> get(key: Preferences.Key<T>): T? = null
        override fun contains(key: Preferences.Key<*>): Boolean = false
    }
}

// --- 2. EVChargingViewModel ---

// Using MockDataStore and MockApiService for self-contained example
// In a real app, these would be injected via Hilt
@HiltViewModel
class EVChargingViewModel @Inject constructor(
    private val apiService: ApiService = MockApiService(),
    private val settingsRepository: SettingsRepository = SettingsRepository(MockDataStore())
) : ViewModel() {

    private val _state = MutableStateFlow(EVChargingState(isLoading = true))
    val state: StateFlow<EVChargingState> = _state.asStateFlow()

    // Combine flows for filtered stations (Performance: derivedStateOf logic in ViewModel)
    private val allStationsFlow = MutableStateFlow<List<EVChargingStation>>(emptyList())

    val filteredStations: StateFlow<List<EVChargingStation>> = combine(
        allStationsFlow,
        _state.map { it.selectedConnectors },
        _state.map { it.settings }
    ) { stations, selectedConnectors, settings ->
        stations
            .filter { station ->
                // Filter by connector type
                station.connectorType in selectedConnectors
            }
            .filter { station ->
                // Filter by availability (if setting is enabled)
                !settings.showOnlyAvailable || station.status == ChargingStatus.AVAILABLE
            }
            .filter { station ->
                // Filter by max distance
                station.distanceKm <= settings.maxDistanceKm
            }
            .sortedBy { it.distanceKm }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            // Load settings from DataStore
            settingsRepository.settingsFlow.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        fetchStations()
    }

    fun fetchStations() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val stations = apiService.fetchStations()
                allStationsFlow.value = stations
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load stations: ${e.message}") }
            }
        }
    }

    fun toggleConnectorFilter(connector: ConnectorType) {
        _state.update {
            val newSet = if (connector in it.selectedConnectors) {
                it.selectedConnectors - connector
            } else {
                it.selectedConnectors + connector
            }
            it.copy(selectedConnectors = newSet)
        }
    }

    fun toggleFavorite(station: EVChargingStation) {
        viewModelScope.launch {
            try {
                val newStatus = !station.isFavorite
                if (apiService.updateFavoriteStatus(station.id, newStatus)) {
                    // Update local list (simplified for mock)
                    allStationsFlow.update { list ->
                        list.map { s ->
                            if (s.id == station.id) s.copy(isFavorite = newStatus) else s
                        }
                    }
                } else {
                    _state.update { it.copy(error = "Failed to update favorite status.") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Error updating favorite: ${e.message}") }
            }
        }
    }

    fun showSettings() {
        _state.update { it.copy(showSettingsDialog = true) }
    }

    fun hideSettings() {
        _state.update { it.copy(showSettingsDialog = false) }
    }

    fun updateSettings(settings: EVChargingSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings)
            _state.update { it.copy(showSettingsDialog = false) }
        }
    }

    fun showConfirmation(message: String, action: () -> Unit) {
        _state.update {
            it.copy(
                showConfirmationDialog = true,
                confirmationMessage = message,
                confirmationAction = action
            )
        }
    }

    fun hideConfirmation() {
        _state.update {
            it.copy(
                showConfirmationDialog = false,
                confirmationMessage = "",
                confirmationAction = null
            )
        }
    }

    fun confirmAction() {
        _state.value.confirmationAction?.invoke()
        hideConfirmation()
    }

    fun performBackup() {
        showConfirmation(
            message = "Are you sure you want to perform a manual backup of your settings and account data?",
            action = {
                viewModelScope.launch {
                    _state.update { it.copy(isLoading = true) }
                    try {
                        if (apiService.performBackup()) {
                            _state.update { it.copy(error = "Backup successful!", isLoading = false) }
                        } else {
                            _state.update { it.copy(error = "Backup failed.", isLoading = false) }
                        }
                    } catch (e: Exception) {
                        _state.update { it.copy(error = "Backup error: ${e.message}", isLoading = false) }
                    }
                }
            }
        )
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

// --- 3. Composables (UI) ---

@Composable
fun EVChargingScreen(
    viewModel: EVChargingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val filteredStations by viewModel.filteredStations.collectAsState()

    // Performance: derivedStateOf for complex UI state logic
    val isFilterActive by remember(state.selectedConnectors, state.settings) {
        derivedStateOf {
            state.selectedConnectors.size < ConnectorType.entries.size ||
            !state.settings.showOnlyAvailable ||
            state.settings.maxDistanceKm < 100f // Assuming 100km is a high default
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EV Charging Stations", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NaviBlue,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { viewModel.showSettings() }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    IconButton(onClick = { viewModel.performBackup() }) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = "Backup Data"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            if (state.error != null) {
                SnackbarHost(hostState = remember { SnackbarHostState() }) {
                    Snackbar(
                        snackbarData = it,
                        action = {
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("DISMISS", color = Color.White)
                            }
                        },
                        containerColor = if (state.error?.contains("successful") == true) Color(0xFF10B981) else Color(0xFFEF4444)
                    ) {
                        Text(state.error ?: "")
                    }
                }
                // Auto-clear error after showing
                LaunchedEffect(state.error) {
                    if (state.error != null) {
                        kotlinx.coroutines.delay(5000)
                        viewModel.clearError()
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 4. Map Section (Placeholder)
            MapSection(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            // Filter Chips
            FilterChipsRow(
                selectedConnectors = state.selectedConnectors,
                onToggleConnector = viewModel::toggleConnectorFilter,
                isFilterActive = isFilterActive
            )

            // Loading/Error State
            when {
                state.isLoading -> LoadingState()
                state.error != null && !state.error!!.contains("successful") -> ErrorState(state.error!!) { viewModel.fetchStations() }
                filteredStations.isEmpty() && !state.isLoading -> EmptyState()
                else -> StationList(
                    stations = filteredStations,
                    onToggleFavorite = viewModel::toggleFavorite
                )
            }
        }
    }

    // Dialogs
    if (state.showSettingsDialog) {
        SettingsDialog(
            currentSettings = state.settings,
            onDismiss = viewModel::hideSettings,
            onSave = viewModel::updateSettings
        )
    }

    if (state.showConfirmationDialog) {
        ConfirmationDialog(
            message = state.confirmationMessage,
            onConfirm = viewModel::confirmAction,
            onDismiss = viewModel::hideConfirmation
        )
    }
}

@Composable
fun MapSection(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.LightGray.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Map View Placeholder",
            color = Color.DarkGray,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FilterChipsRow(
    selectedConnectors: Set<ConnectorType>,
    onToggleConnector: (ConnectorType) -> Unit,
    isFilterActive: Boolean
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Filters",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ConnectorType.entries) { connector ->
                FilterChip(
                    selected = connector in selectedConnectors,
                    onClick = { onToggleConnector(connector) },
                    label = { Text(connector.label) },
                    leadingIcon = if (connector in selectedConnectors) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NaviBlue.copy(alpha = 0.1f),
                        selectedLabelColor = NaviBlue,
                        selectedLeadingIconColor = NaviBlue
                    )
                )
            }
            // Indicator for other active filters (e.g., distance, availability)
            if (isFilterActive) {
                AssistChip(
                    onClick = { /* Settings will handle this */ },
                    label = { Text("Settings Active") },
                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = NaviBlue.copy(alpha = 0.2f),
                        labelColor = NaviBlue,
                        leadingIconContentColor = NaviBlue
                    )
                )
            }
        }
    }
}

@Composable
fun StationList(
    stations: List<EVChargingStation>,
    onToggleFavorite: (EVChargingStation) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(stations, key = { it.id }) { station ->
            StationItem(station = station, onToggleFavorite = onToggleFavorite)
        }
    }
}

@Composable
fun StationItem(
    station: EVChargingStation,
    onToggleFavorite: (EVChargingStation) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { /* Navigate to detail screen */ },
                // Accessibility: semantic properties
                onClickLabel = "View details for ${station.name}"
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NaviBlue
                )
                IconButton(
                    onClick = { onToggleFavorite(station) },
                    // Accessibility: contentDescription
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (station.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (station.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (station.isFavorite) Color.Red else Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = station.address,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Chip
                AssistChip(
                    onClick = { /* No action */ },
                    label = { Text(station.status.label) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = station.status.color.copy(alpha = 0.1f),
                        labelColor = station.status.color
                    ),
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Circle,
                            contentDescription = null,
                            tint = station.status.color,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                )

                // Price and Distance
                Text(
                    text = "€${"%.2f".format(station.pricePerKwh)}/kWh",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${"%.1f".format(station.distanceKm)} km",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.DarkGray
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { /* Navigate action */ },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Navigate")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { /* Add to route action */ },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NaviBlue)
                ) {
                    Icon(Icons.Filled.AddRoad, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add to Route")
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    currentSettings: EVChargingSettings,
    onDismiss: () -> Unit,
    onSave: (EVChargingSettings) -> Unit
) {
    var showOnlyAvailable by remember { mutableStateOf(currentSettings.showOnlyAvailable) }
    var maxDistance by remember { mutableStateOf(currentSettings.maxDistanceKm) }
    var minPower by remember { mutableStateOf(currentSettings.minPowerKw) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Settings", color = NaviBlue) },
        text = {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                // Switch: Show Only Available
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Show Only Available", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = showOnlyAvailable,
                        onCheckedChange = { showOnlyAvailable = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Slider: Max Distance
                Text(
                    text = "Max Distance: ${"%.0f".format(maxDistance)} km",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = maxDistance,
                    onValueChange = { maxDistance = it },
                    valueRange = 10f..100f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = NaviBlue, activeTrackColor = NaviBlue)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Slider: Min Power
                Text(
                    text = "Min Power: ${"%.0f".format(minPower)} kW",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = minPower,
                    onValueChange = { minPower = it },
                    valueRange = 10f..350f,
                    steps = 34,
                    colors = SliderDefaults.colors(thumbColor = NaviBlue, activeTrackColor = NaviBlue)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        EVChargingSettings(
                            showOnlyAvailable = showOnlyAvailable,
                            maxDistanceKm = maxDistance,
                            minPowerKw = minPower
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = NaviBlue)
            }
        }
    )
}

@Composable
fun ConfirmationDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Action") },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = NaviBlue)
            }
        }
    )
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = NaviBlue)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Loading charging stations...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Error",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Error: $message", style = MaterialTheme.typography.titleMedium, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.SearchOff,
                contentDescription = "No Results",
                tint = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("No stations found with current filters.", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Text("Try adjusting your settings.", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
        }
    }
}

// Preview Composable (for IDE preview, not part of the main screen logic)
// @Preview
// @Composable
// fun PreviewEVChargingScreen() {
//     EVChargingScreen()
// }

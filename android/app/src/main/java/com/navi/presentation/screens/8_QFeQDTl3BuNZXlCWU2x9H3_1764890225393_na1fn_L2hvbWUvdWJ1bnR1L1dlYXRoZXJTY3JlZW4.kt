package com.aideon.weather

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// --- 1. Data Structures and Enums ---

// Enum for Temperature Unit
enum class TemperatureUnit {
    CELSIUS, FAHRENHEIT
}

// Data Classes for Weather Data
data class CurrentConditions(
    val temperature: Double,
    val unit: TemperatureUnit,
    val description: String,
    val iconUrl: String, // Mocked
    val high: Double,
    val low: Double
)

data class HourlyForecast(
    val time: String,
    val temperature: Double,
    val iconUrl: String // Mocked
)

// Data Class for Settings (DataStore)
data class WeatherSettings(
    val isCelsius: Boolean = true,
    val mapZoomLevel: Float = 10f,
    val isSevereAlertsEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L
)

// Data Class for UI State
data class WeatherState(
    val isLoading: Boolean = true,
    val currentConditions: CurrentConditions? = null,
    val hourlyForecast: List<HourlyForecast> = emptyList(),
    val severeAlerts: List<String> = emptyList(),
    val settings: WeatherSettings = WeatherSettings(),
    val error: String? = null,
    val snackbarMessage: String? = null,
    val showConfirmationDialog: Boolean = false
)

// --- 2. Mock Dependencies (API, DataStore, WorkManager) ---

// Mock ApiService
interface ApiService {
    suspend fun fetchCurrentWeather(): CurrentConditions
    suspend fun fetchHourlyForecast(): List<HourlyForecast>
    suspend fun fetchSevereAlerts(): List<String>
    suspend fun syncBackup(): Boolean
}

@Singleton
class MockApiService @Inject constructor() : ApiService {
    override suspend fun fetchCurrentWeather(): CurrentConditions {
        delay(500) // Simulate network delay
        return CurrentConditions(
            temperature = 25.5,
            unit = TemperatureUnit.CELSIUS,
            description = "Partly Cloudy",
            iconUrl = "cloudy_icon",
            high = 28.0,
            low = 19.0
        )
    }

    override suspend fun fetchHourlyForecast(): List<HourlyForecast> {
        delay(300) // Simulate network delay
        return listOf(
            HourlyForecast("Now", 25.5, "sun"),
            HourlyForecast("1 PM", 26.0, "sun"),
            HourlyForecast("2 PM", 27.1, "cloud"),
            HourlyForecast("3 PM", 27.5, "cloud"),
            HourlyForecast("4 PM", 26.8, "rain"),
            HourlyForecast("5 PM", 25.0, "rain"),
            HourlyForecast("6 PM", 23.5, "cloud"),
        )
    }

    override suspend fun fetchSevereAlerts(): List<String> {
        delay(200) // Simulate network delay
        return listOf(
            "Flash Flood Warning until 4:00 PM.",
            "High Wind Advisory in effect."
        )
    }

    override suspend fun syncBackup(): Boolean {
        delay(1000) // Simulate backup sync
        return true
    }
}

// Mock SettingsRepository (DataStore Preferences)
interface SettingsRepository {
    val settingsFlow: Flow<WeatherSettings>
    suspend fun updateIsCelsius(isCelsius: Boolean)
    suspend fun updateMapZoomLevel(zoomLevel: Float)
    suspend fun updateIsSevereAlertsEnabled(isEnabled: Boolean)
    suspend fun updateLastBackupTimestamp(timestamp: Long)
}

@Singleton
class MockSettingsRepository @Inject constructor() : SettingsRepository {
    private val _settingsFlow = MutableStateFlow(WeatherSettings())
    override val settingsFlow: Flow<WeatherSettings> = _settingsFlow.asStateFlow()

    override suspend fun updateIsCelsius(isCelsius: Boolean) {
        _settingsFlow.update { it.copy(isCelsius = isCelsius) }
    }

    override suspend fun updateMapZoomLevel(zoomLevel: Float) {
        _settingsFlow.update { it.copy(mapZoomLevel = zoomLevel.coerceIn(5f, 20f)) }
    }

    override suspend fun updateIsSevereAlertsEnabled(isEnabled: Boolean) {
        _settingsFlow.update { it.copy(isSevereAlertsEnabled = isEnabled) }
    }

    override suspend fun updateLastBackupTimestamp(timestamp: Long) {
        _settingsFlow.update { it.copy(lastBackupTimestamp = timestamp) }
    }
}

// Mock WorkManager Wrapper
class WorkManagerWrapper @Inject constructor() {
    fun enqueueBackupSync() {
        // In a real app, this would enqueue a WorkRequest
        println("WorkManager: Backup sync enqueued.")
    }
}

// --- 3. ViewModel (MVVM with Hilt and StateFlow) ---

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val apiService: ApiService,
    private val settingsRepository: SettingsRepository,
    private val workManagerWrapper: WorkManagerWrapper
) : ViewModel() {

    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    init {
        // Combine settings flow with weather data
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        fetchWeatherData()
    }

    private fun fetchWeatherData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val current = apiService.fetchCurrentWeather()
                val hourly = apiService.fetchHourlyForecast()
                val alerts = apiService.fetchSevereAlerts()

                _state.update {
                    it.copy(
                        isLoading = false,
                        currentConditions = current,
                        hourlyForecast = hourly,
                        severeAlerts = alerts
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load weather data: ${e.message}",
                        snackbarMessage = "Error: Could not fetch data."
                    )
                }
            }
        }
    }

    // Business Logic
    fun toggleTemperatureUnit() {
        viewModelScope.launch {
            val newUnit = !_state.value.settings.isCelsius
            settingsRepository.updateIsCelsius(newUnit)
            _state.update { it.copy(snackbarMessage = "Unit changed to ${if (newUnit) "Celsius" else "Fahrenheit"}") }
        }
    }

    fun updateMapZoom(zoom: Float) {
        viewModelScope.launch {
            settingsRepository.updateMapZoomLevel(zoom)
        }
    }

    fun showBackupConfirmation() {
        _state.update { it.copy(showConfirmationDialog = true) }
    }

    fun dismissConfirmation() {
        _state.update { it.copy(showConfirmationDialog = false) }
    }

    fun performBackup() {
        dismissConfirmation()
        workManagerWrapper.enqueueBackupSync()
        viewModelScope.launch {
            _state.update { it.copy(snackbarMessage = "Backup sync started...") }
            // Simulate WorkManager completion and DataStore update
            delay(1500)
            val success = apiService.syncBackup()
            if (success) {
                val timestamp = System.currentTimeMillis()
                settingsRepository.updateLastBackupTimestamp(timestamp)
                _state.update { it.copy(snackbarMessage = "Backup successful at ${timestamp}") }
            } else {
                _state.update { it.copy(snackbarMessage = "Backup failed.") }
            }
        }
    }

    fun snackbarConsumed() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    // Helper for temperature conversion
    private fun convertTemp(temp: Double, isCelsius: Boolean): Double {
        return if (isCelsius) temp else (temp * 9 / 5) + 32
    }

    // Performance optimization: derivedStateOf for formatted temperature
    val formattedCurrentTemp: State<String> = derivedStateOf {
        val stateValue = _state.value
        val temp = stateValue.currentConditions?.temperature ?: 0.0
        val isCelsius = stateValue.settings.isCelsius
        val converted = convertTemp(temp, isCelsius)
        val unitSymbol = if (isCelsius) "°C" else "°F"
        String.format("%.1f%s", converted, unitSymbol)
    }
}

// --- 4. Compose UI (WeatherScreen) ---

// Navi Blue Color
val NaviBlue = Color(0xFF2563EB)

@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    val state by viewModel.state.collectAsState()
    val formattedTemp by viewModel.formattedCurrentTemp // Performance optimization

    val snackbarHostState = remember { SnackbarHostState() }

    // Handle Snackbar messages
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.snackbarConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Weather Overlay", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NaviBlue)
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NaviBlue)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF0F4F8)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Current Conditions Card
                item {
                    state.currentConditions?.let { conditions ->
                        CurrentConditionsCard(
                            conditions = conditions,
                            formattedTemp = formattedTemp,
                            isCelsius = state.settings.isCelsius,
                            onToggleUnit = viewModel::toggleTemperatureUnit
                        )
                    }
                }

                // 2. Hourly Forecast LazyRow
                item {
                    HourlyForecastRow(state.hourlyForecast, state.settings.isCelsius)
                }

                // 3. Severe Alerts
                item {
                    SevereAlertsSection(state.severeAlerts)
                }

                // 4. Settings Section (Toggle, Slider, Dropdown)
                item {
                    SettingsSection(
                        settings = state.settings,
                        onZoomChange = viewModel::updateMapZoom,
                        onBackupClick = viewModel::showBackupConfirmation
                    )
                }

                // 5. Map Integration Placeholder
                item {
                    MapIntegrationPlaceholder(state.settings.mapZoomLevel)
                }
            }
        }
    }

    // Confirmation Dialog
    if (state.showConfirmationDialog) {
        ConfirmationDialog(
            onConfirm = viewModel::performBackup,
            onDismiss = viewModel::dismissConfirmation
        )
    }
}

@Composable
fun CurrentConditionsCard(
    conditions: CurrentConditions,
    formattedTemp: String,
    isCelsius: Boolean,
    onToggleUnit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = conditions.description,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = NaviBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formattedTemp,
                    style = MaterialTheme.typography.displayLarge,
                    fontSize = 72.sp,
                    color = Color.Black,
                    modifier = Modifier.semantics { contentDescription = "Current temperature is $formattedTemp" }
                )
                Icon(
                    imageVector = Icons.Default.WbSunny, // Mock icon
                    contentDescription = "Weather Icon",
                    tint = Color.Yellow.copy(alpha = 0.8f),
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("High: ${conditions.high}°", style = MaterialTheme.typography.bodyLarge)
                Text("Low: ${conditions.low}°", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onToggleUnit,
                colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
            ) {
                Text("Toggle to ${if (isCelsius) "Fahrenheit" else "Celsius"}")
            }
        }
    }
}

@Composable
fun HourlyForecastRow(forecast: List<HourlyForecast>, isCelsius: Boolean) {
    Column {
        Text(
            "Hourly Forecast",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(forecast) { hour ->
                val temp = if (isCelsius) hour.temperature else (hour.temperature * 9 / 5) + 32
                val unit = if (isCelsius) "°C" else "°F"
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(hour.time, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(
                        imageVector = if (hour.iconUrl == "sun") Icons.Default.WbSunny else Icons.Default.Cloud,
                        contentDescription = "Weather icon for ${hour.time}",
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format("%.0f%s", temp, unit),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SevereAlertsSection(alerts: List<String>) {
    if (alerts.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Severe Weather Alert",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Severe Weather Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                alerts.forEach { alert ->
                    Text(alert, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    settings: WeatherSettings,
    onZoomChange: (Float) -> Unit,
    onBackupClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Advanced Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Switch: Severe Alerts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* Toggle logic would be here */ }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Severe Alerts", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.isSevereAlertsEnabled,
                    onCheckedChange = { /* onCheckedChange is handled by ViewModel in a real app */ },
                    colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue),
                    modifier = Modifier.semantics { contentDescription = "Toggle severe alerts" }
                )
            }
            Divider()

            // Slider: Map Zoom Level
            Text(
                "Map Zoom Level: ${settings.mapZoomLevel.toInt()}x",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = settings.mapZoomLevel,
                onValueChange = onZoomChange,
                valueRange = 5f..20f,
                steps = 14,
                colors = SliderDefaults.colors(thumbColor = NaviBlue, activeTrackColor = NaviBlue)
            )
            Divider()

            // Dropdown (Mocked for Account Type)
            var expanded by remember { mutableStateOf(false) }
            val options = listOf("Free", "Premium", "Enterprise")
            var selectedOption by remember { mutableStateOf(options[0]) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Account Type", style = MaterialTheme.typography.bodyLarge)
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(selectedOption)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown menu")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    selectedOption = selectionOption
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            Divider()

            // Backup Button
            Button(
                onClick = onBackupClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
            ) {
                Text("Sync Settings Backup")
            }
            Text(
                "Last Backup: ${if (settings.lastBackupTimestamp == 0L) "Never" else "Recent"}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun MapIntegrationPlaceholder(zoomLevel: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0E0E0))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "Map Integration",
                    modifier = Modifier.size(48.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Map Integration (Zoom: ${zoomLevel.toInt()}x)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )
                Text(
                    "Simulated Real-time Preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun ConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Backup") },
        text = { Text("Are you sure you want to sync your settings backup now?") },
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
                Text("Cancel")
            }
        }
    )
}

// --- 5. Preview (Optional but good practice) ---

// Assuming a standard Preview annotation is available
/*
@Preview(showBackground = true)
@Composable
fun PreviewWeatherScreen() {
    // Mocking the ViewModel for preview purposes
    val mockApi = MockApiService()
    val mockRepo = MockSettingsRepository()
    val mockWork = WorkManagerWrapper()
    val mockViewModel = WeatherViewModel(mockApi, mockRepo, mockWork)

    MaterialTheme {
        WeatherScreen(mockViewModel)
    }
}
*/

// --- 6. Hilt Module (Mocked for completeness) ---

// Assuming a standard Hilt module structure
/*
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideApiService(): ApiService = MockApiService()

    @Provides
    @Singleton
    fun provideSettingsRepository(): SettingsRepository = MockSettingsRepository()

    @Provides
    @Singleton
    fun provideWorkManagerWrapper(): WorkManagerWrapper = WorkManagerWrapper()
}
*/

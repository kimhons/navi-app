// DisplaySettingsScreen.kt

package com.aideon.settings.display

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

// --- 1. Data Structures (Enums and Data Class) ---

enum class Theme { LIGHT, DARK, SYSTEM }
enum class MapStyle { STANDARD, SATELLITE, TERRAIN }
enum class Units { METRIC, IMPERIAL }

data class DisplaySettings(
    val theme: Theme = Theme.SYSTEM,
    val mapStyle: MapStyle = MapStyle.STANDARD,
    val units: Units = Units.METRIC,
    val textSizeScale: Float = 1.0f,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showConfirmationDialog: Boolean = false
)

// --- 2. Mock Architectural Components (API and Storage) ---

// Mock API Service
class ApiService @Inject constructor() {
    suspend fun performBackupSync() {
        // Mock network call
        kotlinx.coroutines.delay(1000)
        // In a real app, this would handle account, subscription, or backup operations
        println("API: Backup sync initiated.")
    }
}

// Mock WorkManager Interface
interface BackupScheduler {
    fun scheduleBackupSync()
}

class MockBackupScheduler @Inject constructor() : BackupScheduler {
    override fun scheduleBackupSync() {
        // In a real app, this would use WorkManager to schedule a background task
        println("WorkManager: Backup sync scheduled.")
    }
}

// DataStore Preferences
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "display_settings")

class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val THEME = stringPreferencesKey("theme")
        val MAP_STYLE = stringPreferencesKey("map_style")
        val UNITS = stringPreferencesKey("units")
        val TEXT_SIZE_SCALE = floatPreferencesKey("text_size_scale")
    }

    val settingsFlow: Flow<DisplaySettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            DisplaySettings(
                theme = Theme.valueOf(preferences[PreferencesKeys.THEME] ?: Theme.SYSTEM.name),
                mapStyle = MapStyle.valueOf(preferences[PreferencesKeys.MAP_STYLE] ?: MapStyle.STANDARD.name),
                units = Units.valueOf(preferences[PreferencesKeys.UNITS] ?: Units.METRIC.name),
                textSizeScale = preferences[PreferencesKeys.TEXT_SIZE_SCALE] ?: 1.0f
            )
        }

    suspend fun updateTheme(theme: Theme) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = theme.name
        }
    }

    suspend fun updateMapStyle(style: MapStyle) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAP_STYLE] = style.name
        }
    }

    suspend fun updateUnits(units: Units) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.UNITS] = units.name
        }
    }

    suspend fun updateTextSizeScale(scale: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEXT_SIZE_SCALE] = scale
        }
    }
}

// --- 3. ViewModel (MVVM) ---

@HiltViewModel
class DisplaySettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val apiService: ApiService,
    private val backupScheduler: BackupScheduler
) : ViewModel() {

    private val _settingsState = MutableStateFlow(DisplaySettings())
    val settingsState: StateFlow<DisplaySettings> = _settingsState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .onStart { _settingsState.update { it.copy(isLoading = true) } }
                .catch { e ->
                    _settingsState.update { it.copy(error = "Failed to load settings: ${e.message}", isLoading = false) }
                    _snackbarMessage.emit("Error loading settings.")
                }
                .collect { settings ->
                    _settingsState.update { settings.copy(isLoading = false, error = null) }
                }
        }
    }

    fun updateTheme(theme: Theme) = viewModelScope.launch {
        _settingsState.update { it.copy(theme = theme) } // Real-time preview
        settingsDataStore.updateTheme(theme)
        _snackbarMessage.emit("Theme set to ${theme.name.lowercase().replaceFirstChar { it.uppercase() }}.")
    }

    fun updateMapStyle(style: MapStyle) = viewModelScope.launch {
        _settingsState.update { it.copy(mapStyle = style) } // Real-time preview
        settingsDataStore.updateMapStyle(style)
        _snackbarMessage.emit("Map style set to ${style.name.lowercase().replaceFirstChar { it.uppercase() }}.")
    }

    fun updateUnits(units: Units) = viewModelScope.launch {
        // Input validation example: only allow update if not loading
        if (_settingsState.value.isLoading) {
            _snackbarMessage.emit("Cannot change units while loading.")
            return@launch
        }
        _settingsState.update { it.copy(units = units) } // Real-time preview
        settingsDataStore.updateUnits(units)
        _snackbarMessage.emit("Units set to ${units.name.lowercase()}.")
    }

    fun updateTextSizeScale(scale: Float) = viewModelScope.launch {
        _settingsState.update { it.copy(textSizeScale = scale) } // Real-time preview
        // Defer persistence until user stops sliding or leaves screen for performance
        // For this example, we persist immediately for simplicity
        settingsDataStore.updateTextSizeScale(scale)
    }

    fun showBackupConfirmation() {
        _settingsState.update { it.copy(showConfirmationDialog = true) }
    }

    fun dismissConfirmation() {
        _settingsState.update { it.copy(showConfirmationDialog = false) }
    }

    fun confirmAndScheduleBackup() = viewModelScope.launch {
        dismissConfirmation()
        _settingsState.update { it.copy(isLoading = true) }
        try {
            apiService.performBackupSync()
            backupScheduler.scheduleBackupSync()
            _snackbarMessage.emit("Backup scheduled successfully!")
        } catch (e: Exception) {
            _settingsState.update { it.copy(error = "Backup failed: ${e.message}") }
            _snackbarMessage.emit("Backup failed. Check connection.")
        } finally {
            _settingsState.update { it.copy(isLoading = false) }
        }
    }
}

// --- 4. Composable UI (Material3, Custom Color, Features) ---

// Custom Navi Blue Color
val NaviBlue = Color(0xFF2563EB)

@Composable
fun DisplaySettingsScreen(
    viewModel: DisplaySettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.settingsState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Snackbar Feedback
    LaunchedEffect(viewModel.snackbarMessage) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Display Preferences", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NaviBlue)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { ThemeSelector(state.theme, viewModel::updateTheme) }
                item { MapStyleDropdown(state.mapStyle, viewModel::updateMapStyle) }
                item { UnitsRadioGroup(state.units, viewModel::updateUnits) }
                item { TextSizeSlider(state.textSizeScale, viewModel::updateTextSizeScale) }
                item { BackupSection(viewModel::showBackupConfirmation) }
            }
        }
    }

    if (state.showConfirmationDialog) {
        ConfirmationDialog(
            onConfirm = viewModel::confirmAndScheduleBackup,
            onDismiss = viewModel::dismissConfirmation
        )
    }
}

@Composable
fun ThemeSelector(
    selectedTheme: Theme,
    onThemeSelected: (Theme) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val themeName = selectedTheme.name.lowercase().replaceFirstChar { it.uppercase() }

    SettingItem(
        title = "App Theme",
        description = "Current: $themeName"
    ) {
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(themeName, color = NaviBlue)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Theme.entries.forEach { theme ->
                    DropdownMenuItem(
                        text = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        onClick = {
                            onThemeSelected(theme)
                            expanded = false
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Select ${theme.name} theme"
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MapStyleDropdown(
    selectedStyle: MapStyle,
    onStyleSelected: (MapStyle) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val styleName = selectedStyle.name.lowercase().replaceFirstChar { it.uppercase() }

    SettingItem(
        title = "Map Style",
        description = "Current: $styleName"
    ) {
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(styleName, color = NaviBlue)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                MapStyle.entries.forEach { style ->
                    DropdownMenuItem(
                        text = { Text(style.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        onClick = {
                            onStyleSelected(style)
                            expanded = false
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Select ${style.name} map style"
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UnitsRadioGroup(
    selectedUnits: Units,
    onUnitsSelected: (Units) -> Unit
) {
    SettingItem(
        title = "Measurement Units",
        description = "Choose between Metric and Imperial"
    ) {
        Row(Modifier.selectableGroup()) {
            Units.entries.forEach { units ->
                Row(
                    Modifier
                        .height(48.dp)
                        .selectable(
                            selected = (units == selectedUnits),
                            onClick = { onUnitsSelected(units) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (units == selectedUnits),
                        onClick = null, // null recommended for accessibility with row click
                        colors = RadioButtonDefaults.colors(selectedColor = NaviBlue)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(units.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    }
}

@Composable
fun TextSizeSlider(
    currentScale: Float,
    onScaleChanged: (Float) -> Unit
) {
    // Use derivedStateOf for efficient recomposition of the label
    val displayScale by remember(currentScale) {
        derivedStateOf { String.format("%.1fx", currentScale) }
    }

    SettingItem(
        title = "Text Size",
        description = "Adjust the application's text scale"
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Current Scale: $displayScale", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = currentScale,
                onValueChange = onScaleChanged,
                valueRange = 0.8f..1.5f,
                steps = 6, // 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5 (8 values)
                colors = SliderDefaults.colors(thumbColor = NaviBlue, activeTrackColor = NaviBlue),
                modifier = Modifier.semantics {
                    contentDescription = "Text size scale slider"
                }
            )
        }
    }
}

@Composable
fun BackupSection(onBackupClicked: () -> Unit) {
    SettingItem(
        title = "Data Backup",
        description = "Schedule a full data backup sync."
    ) {
        Button(onClick = onBackupClicked, colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)) {
            Text("Schedule Backup", color = Color.White)
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    description: String,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
    Divider()
}

@Composable
fun ConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Backup") },
        text = { Text("Are you sure you want to schedule a full data backup sync? This may consume data and battery.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
            ) {
                Text("Confirm", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = NaviBlue)
            }
        }
    )
}

// Extension function for StateFlow collection in Compose
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> {
    val lifecycleOwner = LocalContext.current as? androidx.lifecycle.LifecycleOwner
    return if (lifecycleOwner != null) {
        this.collectAsState(initial = this.value, context = lifecycleOwner.lifecycle.coroutineScope)
    } else {
        this.collectAsState(initial = this.value)
    }
}

// --- 5. Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewDisplaySettingsScreen() {
    // Mock Hilt dependencies for preview
    val mockDataStore = SettingsDataStore(LocalContext.current.dataStore)
    val mockApiService = ApiService()
    val mockBackupScheduler = MockBackupScheduler()
    val mockViewModel = DisplaySettingsViewModel(mockDataStore, mockApiService, mockBackupScheduler)

    // Wrap in a mock theme for preview
    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        DisplaySettingsScreen(viewModel = mockViewModel)
    }
}

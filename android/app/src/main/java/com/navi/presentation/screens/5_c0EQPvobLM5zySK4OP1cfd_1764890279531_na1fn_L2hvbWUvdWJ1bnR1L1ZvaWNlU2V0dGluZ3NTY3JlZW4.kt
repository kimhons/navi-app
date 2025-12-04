package com.example.app.settings.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay

// --- 1. Constants and Mock Dependencies ---

// Custom Navi Blue color as requested
val NaviBlue = Color(0xFF2563EB)

// Mock R.string for a complete file
object R {
    object string {
        const val voice_settings_title = "Voice Guidance Settings"
        const val voice_settings_subtitle = "Configure your navigation voice preferences"
        const val language_label = "Guidance Language"
        const val voice_label = "Voice Selection"
        const val volume_label = "Guidance Volume"
        const val announce_streets_label = "Announce Street Names"
        const val test_voice_button = "Test Voice"
        const val save_button = "Save Settings"
        const val saving_settings = "Saving settings..."
        const val settings_saved = "Settings saved successfully!"
        const val error_saving = "Error: Could not save settings."
        const val volume_content_desc = "Slider to adjust voice guidance volume"
        const val street_announcement_content_desc = "Switch to toggle street name announcements"
        const val confirm_dialog_title = "Confirm Voice Change"
        const val confirm_dialog_message = "Are you sure you want to change the voice? This will download a new voice pack."
        const val confirm_yes = "Yes"
        const val confirm_no = "No"
    }
}

// Mock ApiService for completeness
interface ApiService {
    suspend fun getAccountStatus(): String
    suspend fun subscribeToPremium()
    suspend fun triggerBackupSync()
}

class MockApiService @Inject constructor() : ApiService {
    override suspend fun getAccountStatus(): String {
        delay(500)
        return "Premium"
    }
    override suspend fun subscribeToPremium() { /* No-op */ }
    override suspend fun triggerBackupSync() { /* No-op */ }
}

// Mock DataStore for Preferences
// In a real app, this would be provided by Hilt
class MockDataStore<T> @Inject constructor() {
    private val data = MutableStateFlow(emptyPreferences())

    val dataFlow: Flow<Preferences> = data

    suspend fun updateData(transform: suspend (MutablePreferences) -> Unit) {
        data.update { currentPrefs ->
            val mutablePrefs = currentPrefs.toMutablePreferences()
            transform(mutablePrefs)
            mutablePrefs.toPreferences()
        }
    }
}

// --- 2. Data Model and State ---

data class VoiceSettings(
    val language: String = "English (US)",
    val voice: String = "Standard Female",
    val volume: Float = 0.7f,
    val announceStreets: Boolean = true
)

data class VoiceSettingsState(
    val settings: VoiceSettings = VoiceSettings(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val snackbarMessage: String? = null,
    val showConfirmDialog: Boolean = false,
    val pendingVoiceChange: String? = null,
    val accountStatus: String = "Loading..."
)

// --- 3. Repository (DataStore) ---

class VoiceSettingsRepository @Inject constructor(
    private val dataStore: MockDataStore<Preferences>
) {
    private object PreferencesKeys {
        val LANGUAGE = stringPreferencesKey("voice_language")
        val VOICE = stringPreferencesKey("voice_selection")
        val VOLUME = floatPreferencesKey("voice_volume")
        val ANNOUNCE_STREETS = booleanPreferencesKey("announce_streets")
    }

    val voiceSettingsFlow: Flow<VoiceSettings> = dataStore.dataFlow
        .map { preferences ->
            VoiceSettings(
                language = preferences[PreferencesKeys.LANGUAGE] ?: "English (US)",
                voice = preferences[PreferencesKeys.VOICE] ?: "Standard Female",
                volume = preferences[PreferencesKeys.VOLUME] ?: 0.7f,
                announceStreets = preferences[PreferencesKeys.ANNOUNCE_STREETS] ?: true
            )
        }

    suspend fun saveSettings(settings: VoiceSettings) {
        dataStore.updateData { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = settings.language
            preferences[PreferencesKeys.VOICE] = settings.voice
            preferences[PreferencesKeys.VOLUME] = settings.volume
            preferences[PreferencesKeys.ANNOUNCE_STREETS] = settings.announceStreets
        }
    }
}

// --- 4. ViewModel (MVVM with Hilt) ---

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val repository: VoiceSettingsRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceSettingsState())
    val state: StateFlow<VoiceSettingsState> = _state.asStateFlow()

    // Holds the settings that are currently being edited in the UI
    private val _currentSettings = MutableStateFlow(VoiceSettings())

    // Derived state for real-time preview (combines loading state and current settings)
    val previewSettings: StateFlow<VoiceSettings> = _currentSettings.asStateFlow()

    init {
        viewModelScope.launch {
            // Load initial settings from DataStore
            repository.voiceSettingsFlow
                .onEach { initialSettings ->
                    _currentSettings.value = initialSettings
                    _state.update { it.copy(settings = initialSettings, isLoading = false) }
                }
                .collect()
        }

        viewModelScope.launch {
            // Load account status
            val status = apiService.getAccountStatus()
            _state.update { it.copy(accountStatus = status) }
        }
    }

    fun onLanguageChange(newLanguage: String) {
        _currentSettings.update { it.copy(language = newLanguage) }
    }

    fun onVoiceChange(newVoice: String) {
        // Validation: Check if voice change requires confirmation (e.g., if it's a large download)
        if (newVoice != _state.value.settings.voice) {
            _state.update { it.copy(showConfirmDialog = true, pendingVoiceChange = newVoice) }
        } else {
            _currentSettings.update { it.copy(voice = newVoice) }
        }
    }

    fun confirmVoiceChange() {
        _state.value.pendingVoiceChange?.let { newVoice ->
            _currentSettings.update { it.copy(voice = newVoice) }
        }
        _state.update { it.copy(showConfirmDialog = false, pendingVoiceChange = null) }
    }

    fun dismissVoiceChange() {
        _state.update { it.copy(showConfirmDialog = false, pendingVoiceChange = null) }
    }

    fun onVolumeChange(newVolume: Float) {
        _currentSettings.update { it.copy(volume = newVolume) }
    }

    fun onAnnounceStreetsChange(newValue: Boolean) {
        _currentSettings.update { it.copy(announceStreets = newValue) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, snackbarMessage = R.string.saving_settings) }
            try {
                // Simulate WorkManager sync after save
                apiService.triggerBackupSync()
                repository.saveSettings(_currentSettings.value)
                _state.update {
                    it.copy(
                        isSaving = false,
                        settings = _currentSettings.value, // Update persisted state
                        snackbarMessage = R.string.settings_saved
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        snackbarMessage = R.string.error_saving
                    )
                }
            }
            delay(3000) // Show snackbar for 3 seconds
            _state.update { it.copy(snackbarMessage = null) }
        }
    }

    fun testVoice() {
        // Real-time preview feature: Use the current _currentSettings.value to play a sound
        // For this mock, we just show a temporary message
        viewModelScope.launch {
            _state.update { it.copy(snackbarMessage = "Testing voice: ${_currentSettings.value.voice} at volume: ${"%.1f".format(_currentSettings.value.volume)}") }
            delay(2000)
            _state.update { it.copy(snackbarMessage = null) }
        }
    }
}

// --- 5. UI Components and Screen ---

@Composable
fun VoiceSettingsScreen(
    viewModel: VoiceSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currentSettings by viewModel.previewSettings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar feedback
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NaviBlue,
                    titleContentColor = Color.White
                )
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
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.voice_settings_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Language Dropdown
                item {
                    SettingsDropdown(
                        label = stringResource(R.string.language_label),
                        options = listOf("English (US)", "English (UK)", "Spanish", "French"),
                        selectedOption = currentSettings.language,
                        onOptionSelected = viewModel::onLanguageChange
                    )
                }

                // Voice Selection Dropdown
                item {
                    SettingsDropdown(
                        label = stringResource(R.string.voice_label),
                        options = listOf("Standard Female", "Standard Male", "Premium Voice 1", "Premium Voice 2"),
                        selectedOption = currentSettings.voice,
                        onOptionSelected = viewModel::onVoiceChange
                    )
                }

                // Volume Slider
                item {
                    SettingsSlider(
                        label = stringResource(R.string.volume_label),
                        value = currentSettings.volume,
                        onValueChange = viewModel::onVolumeChange,
                        contentDescription = stringResource(R.string.volume_content_desc)
                    )
                }

                // Announce Streets Switch
                item {
                    SettingsSwitch(
                        label = stringResource(R.string.announce_streets_label),
                        checked = currentSettings.announceStreets,
                        onCheckedChange = viewModel::onAnnounceStreetsChange,
                        contentDescription = stringResource(R.string.street_announcement_content_desc)
                    )
                }

                // Test Voice Button (Real-time preview)
                item {
                    Button(
                        onClick = viewModel::testVoice,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.test_voice_button))
                    }
                }

                // Save Button
                item {
                    Button(
                        onClick = viewModel::saveSettings,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.save_button))
                        }
                    }
                }

                // Account Status (Mock API usage)
                item {
                    Text(
                        text = "Account Status: ${state.accountStatus}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    // Confirmation Dialog
    if (state.showConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissVoiceChange,
            title = { Text(stringResource(R.string.confirm_dialog_title)) },
            text = { Text(stringResource(R.string.confirm_dialog_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmVoiceChange) {
                    Text(stringResource(R.string.confirm_yes), color = NaviBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissVoiceChange) {
                    Text(stringResource(R.string.confirm_no), color = NaviBlue)
                }
            }
        )
    }
}

// Custom Composable for Dropdown
@Composable
fun SettingsDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentSize(Alignment.TopStart)
            ) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedOption, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onOptionSelected(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// Custom Composable for Slider
@Composable
fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    contentDescription: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text("${"%.0f".format(value * 100)}%", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.semantics { this.contentDescription = contentDescription },
                colors = SliderDefaults.colors(thumbColor = NaviBlue, activeTrackColor = NaviBlue)
            )
        }
    }
}

// Custom Composable for Switch
@Composable
fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics { this.contentDescription = contentDescription },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue)
            )
        }
    }
}

// --- 6. Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewVoiceSettingsScreen() {
    // Mock the dependencies for the preview
    val mockDataStore = MockDataStore<Preferences>()
    val mockRepository = VoiceSettingsRepository(mockDataStore)
    val mockApiService = MockApiService()

    // Create a mock ViewModel instance for the preview
    // Note: In a real app, hiltViewModel() would handle this, but for a standalone preview, we instantiate it.
    val mockViewModel = VoiceSettingsViewModel(mockRepository, mockApiService)

    MaterialTheme(
        colorScheme = lightColorScheme(primary = NaviBlue)
    ) {
        VoiceSettingsScreen(viewModel = mockViewModel)
    }
}

// Mock Hilt setup for compilation
// In a real app, these would be in separate files
// For a single file solution, we define them here to satisfy the architecture requirement
class AppContainer {
    val dataStore = MockDataStore<Preferences>()
    val apiService = MockApiService()
    val repository = VoiceSettingsRepository(dataStore)
    val viewModel = VoiceSettingsViewModel(repository, apiService)
}

// Mock Hilt entry point annotation
annotation class HiltAndroidApp

// Mock Hilt module annotation
annotation class Module

// Mock Hilt InstallIn annotation
annotation class InstallIn(val component: Any)

// Mock Hilt components
object SingletonComponent

// Mock Hilt Provides annotation
annotation class Provides

// Mock Hilt Inject annotation (already imported)

// Mock Hilt ViewModel annotation (already imported)

// Mock stringResource function
@Composable
fun stringResource(id: String): String = id

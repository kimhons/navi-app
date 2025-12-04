package com.manus.app.ui.settings.accessibility

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// ----------------------------------------------------------------------------
// 1. Data Model
// ----------------------------------------------------------------------------

/**
 * Data class representing all accessibility settings.
 */
data class AccessibilitySettings(
    val isTalkBackEnabled: Boolean = false,
    val isHighContrastEnabled: Boolean = false,
    val isReduceMotionEnabled: Boolean = false,
    val textSizeScale: Float = 1.0f, // Range: 1.0f to 2.0f
    val colorBlindMode: ColorBlindMode = ColorBlindMode.NONE
)

/**
 * Enum for different color blind modes.
 */
enum class ColorBlindMode(val label: String) {
    NONE("None"),
    PROTANOPIA("Protanopia (Red-Green)"),
    DEUTERANOPIA("Deuteranopia (Red-Green)"),
    TRITANOPIA("Tritanopia (Blue-Yellow)")
}

/**
 * Sealed class for UI state.
 */
sealed class UiState {
    data object Loading : UiState()
    data class Success(val settings: AccessibilitySettings) : UiState()
    data class Error(val message: String) : UiState()
}

// ----------------------------------------------------------------------------
// 2. Repository and DataStore (Mock Implementation)
// ----------------------------------------------------------------------------

/**
 * Interface for the Accessibility Settings Repository.
 */
interface AccessibilitySettingsRepository {
    val settingsFlow: Flow<AccessibilitySettings>
    suspend fun updateTalkBack(enabled: Boolean)
    suspend fun updateHighContrast(enabled: Boolean)
    suspend fun updateReduceMotion(enabled: Boolean)
    suspend fun updateTextSizeScale(scale: Float)
    suspend fun updateColorBlindMode(mode: ColorBlindMode)
}

/**
 * Mock implementation of the repository to simulate DataStore persistence.
 * In a real app, this would inject a DataStore instance and use it for persistence.
 */
@Singleton
class MockAccessibilitySettingsRepository @Inject constructor() : AccessibilitySettingsRepository {

    // Keys for DataStore Preferences (used for simulation)
    private object PreferencesKeys {
        val TALKBACK_ENABLED = booleanPreferencesKey("talkback_enabled")
        val HIGH_CONTRAST_ENABLED = booleanPreferencesKey("high_contrast_enabled")
        val REDUCE_MOTION_ENABLED = booleanPreferencesKey("reduce_motion_enabled")
        val TEXT_SIZE_SCALE = floatPreferencesKey("text_size_scale")
        val COLOR_BLIND_MODE = stringPreferencesKey("color_blind_mode")
    }

    // In-memory state to simulate DataStore's Flow behavior
    private val _settingsState = MutableStateFlow(AccessibilitySettings())
    override val settingsFlow: Flow<AccessibilitySettings> = _settingsState.asStateFlow()

    init {
        // Simulate initial load from DataStore
        viewModelScope.launch {
            delay(500) // Simulate network/disk delay
            _settingsState.value = AccessibilitySettings(
                isTalkBackEnabled = false,
                isHighContrastEnabled = false,
                isReduceMotionEnabled = false,
                textSizeScale = 1.0f,
                colorBlindMode = ColorBlindMode.NONE
            )
        }
    }

    // Helper function to simulate DataStore update
    private suspend fun updateSettings(transform: AccessibilitySettings.() -> AccessibilitySettings) {
        // Simulate DataStore write operation
        delay(100)
        _settingsState.update(transform)
    }

    override suspend fun updateTalkBack(enabled: Boolean) = updateSettings { copy(isTalkBackEnabled = enabled) }
    override suspend fun updateHighContrast(enabled: Boolean) = updateSettings { copy(isHighContrastEnabled = enabled) }
    override suspend fun updateReduceMotion(enabled: Boolean) = updateSettings { copy(isReduceMotionEnabled = enabled) }
    override suspend fun updateTextSizeScale(scale: Float) = updateSettings { copy(textSizeScale = scale.coerceIn(1.0f, 2.0f)) }
    override suspend fun updateColorBlindMode(mode: ColorBlindMode) = updateSettings { copy(colorBlindMode = mode) }
}

// Placeholder for ApiService (as required by the prompt)
interface ApiService {
    suspend fun backupSettings()
    // ... other account, subscription operations
}

// ----------------------------------------------------------------------------
// 3. ViewModel
// ----------------------------------------------------------------------------

@HiltViewModel
class AccessibilityViewModel @Inject constructor(
    private val repository: MockAccessibilitySettingsRepository, // Use the mock for a self-contained example
    private val apiService: ApiService? = null // Placeholder for API service
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _showConfirmationDialog = MutableStateFlow(false)
    val showConfirmationDialog: StateFlow<Boolean> = _showConfirmationDialog.asStateFlow()

    // State to hold the setting that triggered the confirmation dialog
    private var pendingUpdate: (() -> Unit)? = null

    init {
        collectSettings()
    }

    private fun collectSettings() {
        viewModelScope.launch {
            repository.settingsFlow
                .catch { e ->
                    _uiState.value = UiState.Error("Failed to load settings: ${e.message}")
                    _snackbarMessage.emit("Error: Failed to load settings.")
                }
                .collect { settings ->
                    _uiState.value = UiState.Success(settings)
                }
        }
    }

    /**
     * Handles updates that require a confirmation dialog (e.g., TalkBack).
     */
    fun onSettingChangeWithConfirmation(updateAction: () -> Unit) {
        pendingUpdate = updateAction
        _showConfirmationDialog.value = true
    }

    fun confirmUpdate() {
        pendingUpdate?.invoke()
        _showConfirmationDialog.value = false
        pendingUpdate = null
    }

    fun dismissConfirmation() {
        _showConfirmationDialog.value = false
        pendingUpdate = null
    }

    fun updateTalkBack(enabled: Boolean) = viewModelScope.launch {
        try {
            repository.updateTalkBack(enabled)
            _snackbarMessage.emit("TalkBack support ${if (enabled) "enabled" else "disabled"}.")
            apiService?.backupSettings() // Simulate backup sync
        } catch (e: Exception) {
            _snackbarMessage.emit("Failed to update TalkBack setting.")
        }
    }

    fun updateHighContrast(enabled: Boolean) = viewModelScope.launch {
        try {
            repository.updateHighContrast(enabled)
            _snackbarMessage.emit("High Contrast ${if (enabled) "enabled" else "disabled"}.")
        } catch (e: Exception) {
            _snackbarMessage.emit("Failed to update High Contrast setting.")
        }
    }

    fun updateReduceMotion(enabled: Boolean) = viewModelScope.launch {
        try {
            repository.updateReduceMotion(enabled)
            _snackbarMessage.emit("Reduce Motion ${if (enabled) "enabled" else "disabled"}.")
        } catch (e: Exception) {
            _snackbarMessage.emit("Failed to update Reduce Motion setting.")
        }
    }

    fun updateTextSizeScale(scale: Float) = viewModelScope.launch {
        repository.updateTextSizeScale(scale)
    }

    fun updateColorBlindMode(mode: ColorBlindMode) = viewModelScope.launch {
        repository.updateColorBlindMode(mode)
        _snackbarMessage.emit("Color Blind Mode set to ${mode.label}.")
    }
}

// ----------------------------------------------------------------------------
// 4. Compose UI
// ----------------------------------------------------------------------------

// Navi Blue color as requested
private val NaviBlue = Color(0xFF2563EB)

@Composable
fun AccessibilityScreen(
    viewModel: AccessibilityViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val showConfirmationDialog by viewModel.showConfirmationDialog.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Snackbar feedback
    LaunchedEffect(viewModel.snackbarMessage) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessibility Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NaviBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (val state = uiState) {
            UiState.Loading -> LoadingState(paddingValues)
            is UiState.Error -> ErrorState(paddingValues, state.message)
            is UiState.Success -> {
                AccessibilitySettingsContent(
                    paddingValues = paddingValues,
                    settings = state.settings,
                    viewModel = viewModel
                )
            }
        }
    }

    if (showConfirmationDialog) {
        ConfirmationDialog(
            onConfirm = viewModel::confirmUpdate,
            onDismiss = viewModel::dismissConfirmation
        )
    }
}

@Composable
private fun LoadingState(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = NaviBlue)
    }
}

@Composable
private fun ErrorState(paddingValues: PaddingValues, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text("Error", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AccessibilitySettingsContent(
    paddingValues: PaddingValues,
    settings: AccessibilitySettings,
    viewModel: AccessibilityViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. TalkBack Support (Requires Confirmation Dialog)
        item {
            SettingSwitchItem(
                title = "TalkBack Support",
                description = "Enables enhanced screen reader support for the app.",
                checked = settings.isTalkBackEnabled,
                onCheckedChange = { isChecked ->
                    viewModel.onSettingChangeWithConfirmation {
                        viewModel.updateTalkBack(isChecked)
                    }
                }
            )
        }

        // 2. High Contrast Switch
        item {
            SettingSwitchItem(
                title = "High Contrast Mode",
                description = "Increases color contrast for better readability.",
                checked = settings.isHighContrastEnabled,
                onCheckedChange = viewModel::updateHighContrast
            )
        }

        // 3. Reduce Motion
        item {
            SettingSwitchItem(
                title = "Reduce Motion",
                description = "Minimizes animations and transitions.",
                checked = settings.isReduceMotionEnabled,
                onCheckedChange = viewModel::updateReduceMotion
            )
        }

        // 4. Larger Text (Slider)
        item {
            TextSizeSliderItem(
                currentScale = settings.textSizeScale,
                onScaleChange = viewModel::updateTextSizeScale
            )
        }

        // 5. Color Blind Mode Selector (Dropdown/Segmented)
        item {
            ColorBlindModeSelector(
                selectedMode = settings.colorBlindMode,
                onModeSelected = viewModel::updateColorBlindMode
            )
        }

        // Placeholder for real-time preview (as required)
        item {
            RealTimePreviewCard(settings)
        }
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp)
            .semantics { contentDescription = "$title setting. Currently ${if (checked) "enabled" else "disabled"}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue)
        )
    }
    Divider()
}

@Composable
private fun TextSizeSliderItem(
    currentScale: Float,
    onScaleChange: (Float) -> Unit
) {
    val sliderValue by remember(currentScale) {
        derivedStateOf { ((currentScale - 1.0f) / 1.0f) * 100 } // Convert 1.0-2.0 to 0-100 for display
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Larger Text Scale", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${sliderValue.toInt()}%", style = MaterialTheme.typography.bodyMedium, color = NaviBlue)
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = currentScale,
            onValueChange = onScaleChange,
            valueRange = 1.0f..2.0f,
            steps = 9, // 1.0, 1.1, 1.2, ..., 2.0 (11 steps, 9 steps in between)
            colors = SliderDefaults.colors(thumbColor = NaviBlue, activeTrackColor = NaviBlue),
            modifier = Modifier.semantics { contentDescription = "Text size scale slider" }
        )
        Text(
            "Current text size scale: ${String.format("%.1f", currentScale)}x",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Divider()
}

@Composable
private fun ColorBlindModeSelector(
    selectedMode: ColorBlindMode,
    onModeSelected: (ColorBlindMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Color Blind Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        // Using SegmentedButton for a modern Material3 selector
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            ColorBlindMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selectedMode,
                    onClick = { onModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ColorBlindMode.entries.size),
                    modifier = Modifier.semantics { contentDescription = "Select ${mode.label} color blind mode" }
                ) {
                    Text(mode.label)
                }
            }
        }
    }
    Divider()
}

@Composable
private fun ConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Warning: System-Wide Impact") },
        text = {
            Text("Enabling this feature (e.g., TalkBack) may change how you interact with your device and other apps. Are you sure you want to proceed?")
        },
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

@Composable
private fun RealTimePreviewCard(settings: AccessibilitySettings) {
    val previewText = remember(settings) {
        derivedStateOf {
            "This is a real-time preview of the text. Scale: ${String.format("%.1f", settings.textSizeScale)}x. " +
            "High Contrast: ${if (settings.isHighContrastEnabled) "ON" else "OFF"}. " +
            "Color Mode: ${settings.colorBlindMode.label}."
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Real-Time Preview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NaviBlue
            )
            Spacer(Modifier.height(8.dp))
            // Apply the text size scale for real-time preview
            Text(
                text = previewText.value,
                fontSize = (16.sp * settings.textSizeScale).coerceAtMost(32.sp),
                fontWeight = if (settings.isHighContrastEnabled) FontWeight.ExtraBold else FontWeight.Normal,
                modifier = Modifier.semantics { contentDescription = "Real-time preview text" }
            )
            if (settings.isReduceMotionEnabled) {
                Text(
                    "Motion is reduced, animations are minimal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 5. Hilt/Dependency Injection Setup (Placeholders)
// ----------------------------------------------------------------------------

// Placeholder for Hilt modules to satisfy @HiltViewModel dependencies
// In a real app, these would be in separate files.

// @Module
// @InstallIn(SingletonComponent::class)
// object AppModule {
//     @Provides
//     @Singleton
//     fun provideAccessibilitySettingsRepository(
//         // dataStore: DataStore<Preferences>
//     ): AccessibilitySettingsRepository {
//         return MockAccessibilitySettingsRepository()
//     }
//
//     @Provides
//     @Singleton
//     fun provideApiService(): ApiService {
//         return object : ApiService {
//             override suspend fun backupSettings() {
//                 println("Simulating settings backup via API service...")
//             }
//         }
//     }
// }

// @HiltAndroidApp
// class MyApplication : Application()

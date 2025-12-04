package com.example.app.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// -----------------------------------------------------------------------------
// 1. Data Model and Constants
// -----------------------------------------------------------------------------

/**
 * Data class representing the complete state of notification settings.
 */
data class NotificationSettings(
    val navigationAlertsEnabled: Boolean = true,
    val trafficUpdatesEnabled: Boolean = true,
    val friendRequestsEnabled: Boolean = true,
    val groupMessagesEnabled: Boolean = false,
    val pushNotificationLevel: Float = 75f, // Slider value 0-100
    val backupFrequency: String = "Weekly", // Dropdown value
    val isLoading: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null
)

val NAVI_BLUE = Color(0xFF2563EB)
val BACKUP_FREQUENCIES = listOf("Daily", "Weekly", "Monthly", "Never")

// -----------------------------------------------------------------------------
// 2. Simulated Domain/Data Layer (Repository and API Service)
// -----------------------------------------------------------------------------

/**
 * Simulated DataStore Preferences keys.
 */
object SettingsKeys {
    val NAV_ALERTS = booleanPreferencesKey("nav_alerts")
    val TRAFFIC_UPDATES = booleanPreferencesKey("traffic_updates")
    val FRIEND_REQUESTS = booleanPreferencesKey("friend_requests")
    val GROUP_MESSAGES = booleanPreferencesKey("group_messages")
    val PUSH_LEVEL = floatPreferencesKey("push_level")
    val BACKUP_FREQ = stringPreferencesKey("backup_freq")
}

/**
 * Simulated DataStore for settings persistence.
 * In a real app, this would be injected and configured.
 */
class NotificationSettingsRepository @Inject constructor(
    // In a real app, DataStore<Preferences> would be injected here
    // private val dataStore: DataStore<Preferences>
) {
    // Simulate DataStore flow with an in-memory StateFlow for simplicity
    private val settingsDataStore = MutableStateFlow(
        mapOf(
            SettingsKeys.NAV_ALERTS to true,
            SettingsKeys.TRAFFIC_UPDATES to true,
            SettingsKeys.FRIEND_REQUESTS to true,
            SettingsKeys.GROUP_MESSAGES to false,
            SettingsKeys.PUSH_LEVEL to 75f,
            SettingsKeys.BACKUP_FREQ to "Weekly"
        )
    )

    /**
     * Exposes the current settings as a Flow.
     */
    fun getSettings(): Flow<NotificationSettings> = settingsDataStore
        .map { preferences ->
            NotificationSettings(
                navigationAlertsEnabled = preferences[SettingsKeys.NAV_ALERTS] as? Boolean ?: true,
                trafficUpdatesEnabled = preferences[SettingsKeys.TRAFFIC_UPDATES] as? Boolean ?: true,
                friendRequestsEnabled = preferences[SettingsKeys.FRIEND_REQUESTS] as? Boolean ?: true,
                groupMessagesEnabled = preferences[SettingsKeys.GROUP_MESSAGES] as? Boolean ?: false,
                pushNotificationLevel = preferences[SettingsKeys.PUSH_LEVEL] as? Float ?: 75f,
                backupFrequency = preferences[SettingsKeys.BACKUP_FREQ] as? String ?: "Weekly"
            )
        }

    /**
     * Updates a setting in the simulated DataStore.
     */
    suspend fun <T> updateSetting(key: Preferences.Key<T>, value: T) {
        // Simulate DataStore write operation
        settingsDataStore.update { currentMap ->
            currentMap.toMutableMap().apply {
                put(key, value)
            }
        }
        // Simulate a small delay for persistence
        kotlinx.coroutines.delay(100)
    }
}

/**
 * Simulated API Service for network operations.
 */
interface ApiService {
    suspend fun syncAccountSettings(): Result<Unit>
    suspend fun performBackup(): Result<Unit>
}

class ApiServiceImpl @Inject constructor() : ApiService {
    override suspend fun syncAccountSettings(): Result<Unit> {
        kotlinx.coroutines.delay(500) // Simulate network delay
        return if (Random.nextBoolean()) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Network sync failed."))
        }
    }

    override suspend fun performBackup(): Result<Unit> {
        kotlinx.coroutines.delay(1000) // Simulate WorkManager/Backup delay
        return if (Random.nextBoolean()) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Backup failed to complete."))
        }
    }
}

// -----------------------------------------------------------------------------
// 3. ViewModel (MVVM)
// -----------------------------------------------------------------------------

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: NotificationSettingsRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val _settings = MutableStateFlow(NotificationSettings())
    private val _uiState = MutableStateFlow(NotificationSettings())
    val uiState: StateFlow<NotificationSettings> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getSettings()
                .onEach { settings ->
                    // Update both settings and UI state from persistence
                    _settings.value = settings
                    _uiState.value = settings.copy(isLoading = false, error = null, snackbarMessage = null)
                }
                .catch { e ->
                    _uiState.update { it.copy(error = "Failed to load settings: ${e.message}") }
                }
                .collect()
        }
    }

    /**
     * Updates a setting in the UI state and persists it to the repository.
     * Includes confirmation dialog logic for critical settings.
     */
    fun <T> onSettingChange(key: Preferences.Key<T>, value: T, requiresConfirmation: Boolean = false) {
        if (requiresConfirmation) {
            // In a real app, this would trigger a separate confirmation dialog state
            // For simulation, we'll just show a snackbar and proceed
            showSnackbar("Confirmation required for this change. Proceeding for demo.")
        }

        // Real-time preview: Update UI state immediately
        _uiState.update { currentState ->
            when (key) {
                SettingsKeys.NAV_ALERTS -> currentState.copy(navigationAlertsEnabled = value as Boolean)
                SettingsKeys.TRAFFIC_UPDATES -> currentState.copy(trafficUpdatesEnabled = value as Boolean)
                SettingsKeys.FRIEND_REQUESTS -> currentState.copy(friendRequestsEnabled = value as Boolean)
                SettingsKeys.GROUP_MESSAGES -> currentState.copy(groupMessagesEnabled = value as Boolean)
                SettingsKeys.PUSH_LEVEL -> currentState.copy(pushNotificationLevel = value as Float)
                SettingsKeys.BACKUP_FREQ -> currentState.copy(backupFrequency = value as String)
                else -> currentState
            }
        }

        // Persistence: Launch coroutine to save to DataStore
        viewModelScope.launch {
            try {
                repository.updateSetting(key, value)
                showSnackbar("Setting saved successfully.")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save setting: ${e.message}") }
                showSnackbar("Error: Failed to save setting.")
                // Revert UI state to persisted state on failure
                _uiState.value = _settings.value.copy(snackbarMessage = _uiState.value.snackbarMessage)
            }
        }
    }

    /**
     * Simulates an API operation (e.g., syncing account settings).
     */
    fun onSyncSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = apiService.syncAccountSettings()
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess {
                showSnackbar("Account settings synced successfully.")
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Sync failed: ${e.message}") }
                showSnackbar("Sync failed: ${e.message}")
            }
        }
    }

    /**
     * Simulates a WorkManager-like backup operation.
     */
    fun onPerformBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = apiService.performBackup()
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess {
                showSnackbar("Backup initiated (WorkManager simulated).")
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Backup failed: ${e.message}") }
                showSnackbar("Backup failed: ${e.message}")
            }
        }
    }

    /**
     * Utility function to show a Snackbar message.
     */
    private fun showSnackbar(message: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(snackbarMessage = message) }
            // Clear message after a short delay
            kotlinx.coroutines.delay(3000)
            _uiState.update { if (it.snackbarMessage == message) it.copy(snackbarMessage = null) else it }
        }
    }

    /**
     * Clears the current error state.
     */
    fun onErrorConsumed() {
        _uiState.update { it.copy(error = null) }
    }
}

// -----------------------------------------------------------------------------
// 4. Composable UI (View)
// -----------------------------------------------------------------------------

@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationSettingsViewModel = NotificationSettingsViewModel(
        repository = NotificationSettingsRepository(),
        apiService = ApiServiceImpl()
    ),
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Side effect for Snackbar feedback
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            // Note: ViewModel handles clearing the message, no need to call onErrorConsumed here
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to previous screen",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NAVI_BLUE)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                SettingsContent(state = state, viewModel = viewModel)

                // Loading State Overlay
                if (state.isLoading) {
                    LoadingOverlay()
                }
            }
        }
    )
}

@Composable
private fun SettingsContent(
    state: NotificationSettings,
    viewModel: NotificationSettingsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Section 1: Alert Switches ---
        item {
            Text(
                text = "Alerts and Messages",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Divider()
        }

        // Navigation Alerts Switch
        item {
            SwitchSettingItem(
                title = "Navigation Alerts",
                description = "Receive alerts for turn-by-turn directions.",
                icon = Icons.Default.LocationOn,
                checked = state.navigationAlertsEnabled,
                onCheckedChange = {
                    viewModel.onSettingChange(SettingsKeys.NAV_ALERTS, it)
                }
            )
        }

        // Traffic Updates Switch
        item {
            SwitchSettingItem(
                title = "Traffic Updates",
                description = "Get real-time traffic and road condition updates.",
                icon = Icons.Default.Traffic,
                checked = state.trafficUpdatesEnabled,
                onCheckedChange = {
                    viewModel.onSettingChange(SettingsKeys.TRAFFIC_UPDATES, it)
                }
            )
        }

        // Friend Requests Switch (Requires Confirmation Simulation)
        item {
            SwitchSettingItem(
                title = "Friend Requests",
                description = "Notify me when someone sends a friend request.",
                icon = Icons.Default.PersonAdd,
                checked = state.friendRequestsEnabled,
                onCheckedChange = {
                    viewModel.onSettingChange(SettingsKeys.FRIEND_REQUESTS, it, requiresConfirmation = true)
                }
            )
        }

        // Group Messages Switch
        item {
            SwitchSettingItem(
                title = "Group Messages",
                description = "Receive notifications for new messages in groups.",
                icon = Icons.Default.Group,
                checked = state.groupMessagesEnabled,
                onCheckedChange = {
                    viewModel.onSettingChange(SettingsKeys.GROUP_MESSAGES, it)
                }
            )
        }

        // --- Section 2: Push Settings (Slider) ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Push Notification Intensity",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Divider()
        }

        item {
            SliderSettingItem(
                title = "Notification Volume",
                value = state.pushNotificationLevel,
                onValueChange = {
                    // Use derivedStateOf for efficient recomposition of the preview text
                    // The actual save to DataStore is debounced/throttled in a real app,
                    // but here we update the UI state in real-time.
                    viewModel.onSettingChange(SettingsKeys.PUSH_LEVEL, it)
                },
                valueRange = 0f..100f,
                steps = 0,
                previewText = {
                    // Performance: Use derivedStateOf to only re-calculate this string
                    // when the value actually changes, not on every recomposition.
                    val levelText by remember(state.pushNotificationLevel) {
                        derivedStateOf {
                            when (state.pushNotificationLevel.toInt()) {
                                in 0..25 -> "Low"
                                in 26..75 -> "Medium"
                                else -> "High"
                            }
                        }
                    }
                    Text(
                        text = "Current Level: ${state.pushNotificationLevel.toInt()}% ($levelText)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }

        // --- Section 3: Data & Backup (Dropdown and Actions) ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Data and Backup",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Divider()
        }

        // Backup Frequency Dropdown
        item {
            DropdownSettingItem(
                title = "Backup Frequency",
                description = "How often your data is backed up (WorkManager simulated).",
                icon = Icons.Default.CloudUpload,
                currentValue = state.backupFrequency,
                options = BACKUP_FREQUENCIES,
                onOptionSelected = {
                    viewModel.onSettingChange(SettingsKeys.BACKUP_FREQ, it)
                }
            )
        }

        // Action Buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = viewModel::onSyncSettings, enabled = !state.isLoading) {
                    Text("Sync Account")
                }
                Button(onClick = viewModel::onPerformBackup, enabled = !state.isLoading) {
                    Text("Run Backup Now")
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 5. Reusable Composable Components
// -----------------------------------------------------------------------------

@Composable
private fun SwitchSettingItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onCheckedChange(!checked) })
            .padding(vertical = 12.dp)
            .semantics { contentDescription = "$title setting. Currently ${if (checked) "enabled" else "disabled"}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null, // Icon is decorative
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = "Toggle $title" }
        )
    }
}

@Composable
private fun SliderSettingItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    previewText: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .semantics { contentDescription = "$title setting. Current value is ${value.toInt()}" }
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
        previewText()
    }
}

@Composable
private fun DropdownSettingItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    currentValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(currentValue)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select backup frequency")
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

@Composable
private fun LoadingOverlay() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.4f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

// -----------------------------------------------------------------------------
// 6. Preview and Theme Simulation
// -----------------------------------------------------------------------------

/**
 * Simulated theme for preview purposes.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = NAVI_BLUE,
        onPrimary = Color.White,
        surface = Color.White,
        onSurface = Color.Black
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

/**
 * Preview of the screen.
 */
@Preview(showBackground = true)
@Composable
fun PreviewNotificationSettingsScreen() {
    AppTheme {
        // Since we can't use Hilt in a preview, we pass a manually instantiated ViewModel
        NotificationSettingsScreen()
    }
}

// -----------------------------------------------------------------------------
// 7. Dummy Classes for Dependencies
// -----------------------------------------------------------------------------

// Dummy class for Hilt annotations
annotation class HiltViewModel

// Dummy function for StateFlow collection in a Compose context
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> = this.collectAsState()

// Dummy class for Result (already in Kotlin standard library, but included for clarity)
// typealias Result<T> = kotlin.Result<T>

// Dummy class for Preferences (already in DataStore library, but included for clarity)
// interface Preferences { ... }

// Dummy class for Preferences.Key (already in DataStore library, but included for clarity)
// interface Preferences {
//     interface Key<T>
// }

// Dummy function for booleanPreferencesKey (already in DataStore library, but included for clarity)
// fun booleanPreferencesKey(name: String): Preferences.Key<Boolean> = object : Preferences.Key<Boolean> {}

// ... and so on for all necessary imports and types.
// The provided code uses standard Kotlin/Compose/Coroutines imports, which are assumed to be available.
// The dummy HiltViewModel and collectAsStateWithLifecycle are included to make the file self-contained and compile-ready.

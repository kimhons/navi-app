package com.example.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// 1. Architecture & Design Constants
val NaviBlue = Color(0xFF2563EB)

// 2. Data Structures
sealed class SettingsItem(val key: String, val title: String, val icon: ImageVector) {
    data class ToggleSetting(
        val settingKey: String,
        val settingTitle: String,
        val initialValue: Boolean,
        val settingIcon: ImageVector
    ) : SettingsItem(settingKey, settingTitle, settingIcon)

    data class SliderSetting(
        val settingKey: String,
        val settingTitle: String,
        val initialValue: Float,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int,
        val settingIcon: ImageVector
    ) : SettingsItem(settingKey, settingTitle, settingIcon)

    data class DropdownSetting(
        val settingKey: String,
        val settingTitle: String,
        val options: List<String>,
        val initialValue: String,
        val settingIcon: ImageVector
    ) : SettingsItem(settingKey, settingTitle, settingIcon)

    data class ClickableSetting(
        val settingKey: String,
        val settingTitle: String,
        val subtitle: String,
        val onClickAction: () -> Unit,
        val settingIcon: ImageVector
    ) : SettingsItem(settingKey, settingTitle, settingIcon)
}

data class SettingsGroup(
    val title: String,
    val items: List<SettingsItem>
)

data class SettingsState(
    val isLoading: Boolean = true,
    val settings: Map<String, Any> = emptyMap(),
    val snackbarMessage: String? = null,
    val showConfirmationDialog: Boolean = false,
    val dialogTitle: String = "",
    val dialogMessage: String = "",
    val dialogAction: () -> Unit = {},
    val isApiLoading: Boolean = false,
    val error: String? = null
)

// 3. Mock API Service (for account, subscription, backup operations)
interface ApiService {
    suspend fun fetchAccountStatus(): Result<String>
    suspend fun updateSubscription(level: String): Result<Unit>
    suspend fun triggerBackupSync(): Result<Unit>
}

@Singleton
class MockApiService @Inject constructor() : ApiService {
    override suspend fun fetchAccountStatus(): Result<String> {
        kotlinx.coroutines.delay(500) // Simulate network delay
        return Result.success("Premium Active")
    }

    override suspend fun updateSubscription(level: String): Result<Unit> {
        kotlinx.coroutines.delay(800)
        return if (level == "Basic") Result.failure(Exception("Cannot downgrade at this time."))
        else Result.success(Unit)
    }

    override suspend fun triggerBackupSync(): Result<Unit> {
        kotlinx.coroutines.delay(1500)
        // Mock WorkManager/API call success
        return Result.success(Unit)
    }
}

// 4. Mock DataStore Repository (for settings persistence)
interface SettingsRepository {
    val settingsFlow: Flow<Map<String, Any>>
    suspend fun updateSetting(key: String, value: Any)
    suspend fun loadInitialSettings(): Map<String, Any>
}

@Singleton
class MockSettingsRepository @Inject constructor() : SettingsRepository {
    private val _settingsFlow = MutableStateFlow<Map<String, Any>>(emptyMap())
    override val settingsFlow: Flow<Map<String, Any>> = _settingsFlow.asStateFlow()

    private val initialSettings = mapOf(
        "notifications_enabled" to true,
        "display_theme" to "System Default",
        "voice_volume" to 0.75f,
        "privacy_analytics" to true,
        "data_sync_interval" to "Daily",
        "accessibility_text_size" to 1.0f
    )

    init {
        _settingsFlow.value = initialSettings
    }

    override suspend fun loadInitialSettings(): Map<String, Any> {
        kotlinx.coroutines.delay(300) // Simulate DataStore read delay
        return initialSettings
    }

    override suspend fun updateSetting(key: String, value: Any) {
        // Simulate DataStore write
        val newSettings = _settingsFlow.value.toMutableMap()
        newSettings[key] = value
        _settingsFlow.value = newSettings
    }
}

// 5. ViewModel
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Load initial settings
            val initialSettings = settingsRepository.loadInitialSettings()
            _state.update { it.copy(settings = initialSettings, isLoading = false) }

            // Real-time preview/persistence: Collect changes from repository
            settingsRepository.settingsFlow
                .onEach { newSettings ->
                    _state.update { it.copy(settings = newSettings) }
                }
                .launchIn(viewModelScope)
        }
    }

    // Function to handle all setting updates
    fun onSettingChanged(key: String, value: Any) {
        viewModelScope.launch {
            settingsRepository.updateSetting(key, value)
            _state.update { it.copy(snackbarMessage = "Setting '$key' updated.") }
        }
    }

    // Function to handle API/Clickable actions
    fun onActionClicked(actionKey: String) {
        when (actionKey) {
            "account_status" -> fetchAccountStatus()
            "backup_sync" -> showConfirmationDialog(
                title = "Confirm Backup Sync",
                message = "Are you sure you want to trigger a manual backup sync now?",
                action = { triggerBackupSync() }
            )
            "about_licenses" -> _state.update { it.copy(snackbarMessage = "Navigating to Licenses Screen...") }
        }
    }

    private fun fetchAccountStatus() {
        viewModelScope.launch {
            _state.update { it.copy(isApiLoading = true) }
            when (val result = apiService.fetchAccountStatus()) {
                is Result.Success -> _state.update {
                    it.copy(
                        isApiLoading = false,
                        snackbarMessage = "Account Status: ${result.value}"
                    )
                }
                is Result.Failure -> _state.update {
                    it.copy(
                        isApiLoading = false,
                        error = "Failed to fetch account status: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    private fun triggerBackupSync() {
        viewModelScope.launch {
            _state.update { it.copy(isApiLoading = true) }
            when (apiService.triggerBackupSync()) {
                is Result.Success -> _state.update {
                    it.copy(
                        isApiLoading = false,
                        snackbarMessage = "Backup Sync started successfully (WorkManager mocked)."
                    )
                }
                is Result.Failure -> _state.update {
                    it.copy(
                        isApiLoading = false,
                        error = "Backup Sync failed: ${it.error}"
                    )
                }
            }
        }
    }

    fun dismissSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    fun dismissDialog() {
        _state.update { it.copy(showConfirmationDialog = false) }
    }

    fun showConfirmationDialog(title: String, message: String, action: () -> Unit) {
        _state.update {
            it.copy(
                showConfirmationDialog = true,
                dialogTitle = title,
                dialogMessage = message,
                dialogAction = action
            )
        }
    }
}

// 6. Settings Data Definition
private fun createSettingsGroups(
    onAction: (String) -> Unit
): List<SettingsGroup> {
    return listOf(
        SettingsGroup(
            title = "Account",
            items = listOf(
                SettingsItem.ClickableSetting(
                    settingKey = "account_status",
                    settingTitle = "Account Status",
                    subtitle = "Check your subscription level",
                    onClickAction = { onAction("account_status") },
                    settingIcon = Icons.Default.Person
                ),
                SettingsItem.ClickableSetting(
                    settingKey = "account_logout",
                    settingTitle = "Sign Out",
                    subtitle = "Log out of your account",
                    onClickAction = { onAction("account_logout") },
                    settingIcon = Icons.Default.ExitToApp
                )
            )
        ),
        SettingsGroup(
            title = "Notifications",
            items = listOf(
                SettingsItem.ToggleSetting(
                    settingKey = "notifications_enabled",
                    settingTitle = "Enable Notifications",
                    initialValue = true,
                    settingIcon = Icons.Default.Notifications
                ),
                SettingsItem.ToggleSetting(
                    settingKey = "notifications_sound",
                    settingTitle = "Notification Sound",
                    initialValue = false,
                    settingIcon = Icons.Default.VolumeUp
                )
            )
        ),
        SettingsGroup(
            title = "Privacy",
            items = listOf(
                SettingsItem.ToggleSetting(
                    settingKey = "privacy_analytics",
                    settingTitle = "Share Analytics Data",
                    initialValue = true,
                    settingIcon = Icons.Default.Security
                ),
                SettingsItem.ClickableSetting(
                    settingKey = "privacy_data_export",
                    settingTitle = "Export My Data",
                    subtitle = "Request a copy of your data",
                    onClickAction = { onAction("privacy_data_export") },
                    settingIcon = Icons.Default.CloudDownload
                )
            )
        ),
        SettingsGroup(
            title = "Display",
            items = listOf(
                SettingsItem.DropdownSetting(
                    settingKey = "display_theme",
                    settingTitle = "App Theme",
                    options = listOf("System Default", "Light", "Dark"),
                    initialValue = "System Default",
                    settingIcon = Icons.Default.DarkMode
                ),
                SettingsItem.SliderSetting(
                    settingKey = "accessibility_text_size",
                    settingTitle = "Text Size",
                    initialValue = 1.0f,
                    range = 0.5f..1.5f,
                    steps = 4,
                    settingIcon = Icons.Default.FormatSize
                )
            )
        ),
        SettingsGroup(
            title = "Voice",
            items = listOf(
                SettingsItem.SliderSetting(
                    settingKey = "voice_volume",
                    settingTitle = "Voice Assistant Volume",
                    initialValue = 0.75f,
                    range = 0.0f..1.0f,
                    steps = 10,
                    settingIcon = Icons.Default.VolumeUp
                ),
                SettingsItem.DropdownSetting(
                    settingKey = "voice_language",
                    settingTitle = "Voice Language",
                    options = listOf("English (US)", "English (UK)", "Spanish"),
                    initialValue = "English (US)",
                    settingIcon = Icons.Default.Language
                )
            )
        ),
        SettingsGroup(
            title = "Data",
            items = listOf(
                SettingsItem.DropdownSetting(
                    settingKey = "data_sync_interval",
                    settingTitle = "Sync Interval",
                    options = listOf("Real-time", "Hourly", "Daily", "Manual"),
                    initialValue = "Daily",
                    settingIcon = Icons.Default.Sync
                ),
                SettingsItem.ClickableSetting(
                    settingKey = "backup_sync",
                    settingTitle = "Trigger Backup Sync",
                    subtitle = "Last sync: 5 minutes ago",
                    onClickAction = { onAction("backup_sync") },
                    settingIcon = Icons.Default.CloudUpload
                )
            )
        ),
        SettingsGroup(
            title = "Accessibility",
            items = listOf(
                SettingsItem.ToggleSetting(
                    settingKey = "accessibility_haptic",
                    settingTitle = "Haptic Feedback",
                    initialValue = true,
                    settingIcon = Icons.Default.Vibration
                ),
                SettingsItem.ToggleSetting(
                    settingKey = "accessibility_monochrome",
                    settingTitle = "Monochrome Mode",
                    initialValue = false,
                    settingIcon = Icons.Default.ColorLens
                )
            )
        ),
        SettingsGroup(
            title = "About",
            items = listOf(
                SettingsItem.ClickableSetting(
                    settingKey = "about_version",
                    settingTitle = "Version",
                    subtitle = "1.0.0 (Build 20251204)",
                    onClickAction = { onAction("about_version") },
                    settingIcon = Icons.Default.Info
                ),
                SettingsItem.ClickableSetting(
                    settingKey = "about_licenses",
                    settingTitle = "Open Source Licenses",
                    subtitle = "View third-party software licenses",
                    onClickAction = { onAction("about_licenses") },
                    settingIcon = Icons.Default.Description
                )
            )
        )
    )
}

// 7. UI Composables
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = SettingsViewModel(
        MockSettingsRepository(),
        MockApiService()
    ), // Mock injection for Preview/Standalone
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Error/Snackbar handling
    LaunchedEffect(state.snackbarMessage, state.error) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSnackbar()
        }
        state.error?.let { error ->
            snackbarHostState.showSnackbar("Error: $error", actionLabel = "Dismiss")
            // Note: In a real app, error would be cleared after user interaction or retry
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White) },
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
            val settingsGroups = remember {
                createSettingsGroups(viewModel::onActionClicked)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                settingsGroups.forEach { group ->
                    item(key = "header_${group.title}") {
                        SettingsGroupHeader(group.title)
                    }
                    items(group.items, key = { it.key }) { item ->
                        // Performance optimization: derivedStateOf for real-time preview
                        val currentValue by remember(item.key) {
                            derivedStateOf { state.settings[item.key] }
                        }
                        
                        when (item) {
                            is SettingsItem.ToggleSetting -> ToggleSettingItem(
                                item = item,
                                currentValue = currentValue as? Boolean ?: item.initialValue,
                                onValueChange = { viewModel.onSettingChanged(item.key, it) }
                            )
                            is SettingsItem.SliderSetting -> SliderSettingItem(
                                item = item,
                                currentValue = currentValue as? Float ?: item.initialValue,
                                onValueChange = { viewModel.onSettingChanged(item.key, it) }
                            )
                            is SettingsItem.DropdownSetting -> DropdownSettingItem(
                                item = item,
                                currentValue = currentValue as? String ?: item.initialValue,
                                onValueChange = { viewModel.onSettingChanged(item.key, it) }
                            )
                            is SettingsItem.ClickableSetting -> ClickableSettingItem(
                                item = item,
                                isLoading = state.isApiLoading
                            )
                        }
                        Divider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    // Confirmation Dialog
    if (state.showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(state.dialogTitle) },
            text = { Text(state.dialogMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        state.dialogAction()
                        viewModel.dismissDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = NaviBlue,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun ToggleSettingItem(
    item: SettingsItem.ToggleSetting,
    currentValue: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onValueChange(!currentValue) }
            .padding(16.dp)
            .semantics { contentDescription = "${item.title} toggle" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = NaviBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
        }
        Switch(
            checked = currentValue,
            onCheckedChange = onValueChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NaviBlue)
        )
    }
}

@Composable
fun SliderSettingItem(
    item: SettingsItem.SliderSetting,
    currentValue: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics { contentDescription = "${item.title} slider" }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = NaviBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = currentValue,
            onValueChange = onValueChange,
            valueRange = item.range,
            steps = item.steps,
            colors = SliderDefaults.colors(thumbColor = NaviBlue, activeTrackColor = NaviBlue)
        )
        Text(
            text = "Current value: ${"%.2f".format(currentValue)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
fun DropdownSettingItem(
    item: SettingsItem.DropdownSetting,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(16.dp)
            .semantics { contentDescription = "${item.title} dropdown" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = NaviBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
        }

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(currentValue, color = NaviBlue)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = NaviBlue)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                item.options.forEach { selection ->
                    DropdownMenuItem(
                        text = { Text(selection) },
                        onClick = {
                            onValueChange(selection)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ClickableSettingItem(
    item: SettingsItem.ClickableSetting,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClickAction, enabled = !isLoading)
            .padding(16.dp)
            .semantics { contentDescription = "${item.title} action" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = NaviBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = NaviBlue
            )
        } else {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 8. Preview
@Preview(showBackground = true)
@Composable
fun PreviewSettingsScreen() {
    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        SettingsScreen()
    }
}

// 9. Mock Hilt/Dependency Injection Setup (Required for @HiltViewModel)
// Note: In a real project, these would be in separate files and a Hilt module.
// For a single file solution, we define the necessary annotations and mock classes.
annotation class HiltViewModel
annotation class Inject
annotation class Singleton

// Mock Result class for API
sealed class Result<out T> {
    data class Success<out T>(val value: T) : Result<T>()
    data class Failure(val exception: Throwable? = null) : Result<Nothing>()
    fun exceptionOrNull(): Throwable? = if (this is Failure) exception else null
}

// Mock implementation of the Result class for simplicity in the ViewModel
val <T> Result<T>.value: T
    get() = (this as Result.Success).value

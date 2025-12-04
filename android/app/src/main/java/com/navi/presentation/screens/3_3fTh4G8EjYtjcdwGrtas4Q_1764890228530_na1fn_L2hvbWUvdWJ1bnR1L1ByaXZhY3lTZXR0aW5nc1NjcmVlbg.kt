package com.example.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// -------------------------------------------------------------------------------------------------
// 1. Data Structures and Models
// -------------------------------------------------------------------------------------------------

// Navi blue Color(0xFF2563EB)
val NaviBlue = Color(0xFF2563EB)

enum class ActivityVisibility(val label: String) {
    PUBLIC("Public"),
    FRIENDS("Friends Only"),
    PRIVATE("Private")
}

data class PrivacySettings(
    val isLocationSharingEnabled: Boolean = false,
    val activityVisibility: ActivityVisibility = ActivityVisibility.FRIENDS,
    val isDataCollectionEnabled: Boolean = true,
    val dataRetentionDays: Int = 180,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showDownloadDialog: Boolean = false
)

// -------------------------------------------------------------------------------------------------
// 2. Mock API Service and Repository (DataStore Abstraction)
// -------------------------------------------------------------------------------------------------

interface ApiService {
    suspend fun downloadUserData(): Result<String>
    suspend fun updateAccountSettings(settings: PrivacySettings): Result<Unit>
}

@Singleton
class MockApiService @Inject constructor() : ApiService {
    override suspend fun downloadUserData(): Result<String> {
        delay(1500) // Simulate network delay
        return Result.success("Download initiated successfully.")
    }

    override suspend fun updateAccountSettings(settings: PrivacySettings): Result<Unit> {
        delay(500) // Simulate network delay
        return Result.success(Unit)
    }
}

interface PrivacySettingsRepository {
    val settingsFlow: StateFlow<PrivacySettings>
    suspend fun updateLocationSharing(enabled: Boolean)
    suspend fun updateActivityVisibility(visibility: ActivityVisibility)
    suspend fun updateDataCollection(enabled: Boolean)
    suspend fun updateDataRetention(days: Int)
}

@Singleton
class MockDataStoreRepository @Inject constructor(
    private val apiService: ApiService
) : PrivacySettingsRepository {
    // Mocking DataStore with a simple in-memory StateFlow for persistence simulation
    private val _settingsFlow = MutableStateFlow(PrivacySettings())
    override val settingsFlow: StateFlow<PrivacySettings> = _settingsFlow.asStateFlow()

    private fun updateAndPersist(updateBlock: PrivacySettings.() -> PrivacySettings) {
        _settingsFlow.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val newSettings = _settingsFlow.value.updateBlock()
            // Simulate DataStore write
            delay(200)
            _settingsFlow.value = newSettings.copy(isLoading = false)
            // Simulate API call for persistence
            apiService.updateAccountSettings(newSettings)
        }
    }

    override suspend fun updateLocationSharing(enabled: Boolean) = updateAndPersist {
        copy(isLocationSharingEnabled = enabled)
    }

    override suspend fun updateActivityVisibility(visibility: ActivityVisibility) = updateAndPersist {
        copy(activityVisibility = visibility)
    }

    override suspend fun updateDataCollection(enabled: Boolean) = updateAndPersist {
        copy(isDataCollectionEnabled = enabled)
    }

    override suspend fun updateDataRetention(days: Int) = updateAndPersist {
        copy(dataRetentionDays = days)
    }
}

// -------------------------------------------------------------------------------------------------
// 3. ViewModel (MVVM with @HiltViewModel and StateFlow)
// -------------------------------------------------------------------------------------------------

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val repository: PrivacySettingsRepository,
    private val apiService: ApiService
) : ViewModel() {

    // StateFlow for real-time state updates
    val uiState: StateFlow<PrivacySettings> = repository.settingsFlow

    fun onLocationSharingChange(enabled: Boolean) = viewModelScope.launch {
        repository.updateLocationSharing(enabled)
    }

    fun onActivityVisibilityChange(visibility: ActivityVisibility) = viewModelScope.launch {
        repository.updateActivityVisibility(visibility)
    }

    fun onDataCollectionChange(enabled: Boolean) = viewModelScope.launch {
        repository.updateDataCollection(enabled)
    }

    fun onDataRetentionChange(days: Int) = viewModelScope.launch {
        repository.updateDataRetention(days)
    }

    fun onDownloadDataClick() {
        _snackbarMessage.value = null
        uiState.update { it.copy(showDownloadDialog = true) }
    }

    fun dismissDownloadDialog() {
        uiState.update { it.copy(showDownloadDialog = false) }
    }

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun onConfirmDownloadData() = viewModelScope.launch {
        dismissDownloadDialog()
        uiState.update { it.copy(isLoading = true) }
        val result = apiService.downloadUserData()
        uiState.update { it.copy(isLoading = false) }

        result.onSuccess { message ->
            _snackbarMessage.value = message
        }.onFailure { error ->
            _snackbarMessage.value = "Error: ${error.message}"
        }
    }

    fun snackbarMessageShown() {
        _snackbarMessage.value = null
    }
}

// -------------------------------------------------------------------------------------------------
// 4. Jetpack Compose UI (Material3, Navi Blue, LazyColumn, Switches, Dialogs)
// -------------------------------------------------------------------------------------------------

@Composable
fun PrivacySettingsScreen(
    viewModel: PrivacySettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Error/Success Snackbar Feedback
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.snackbarMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Settings") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content using LazyColumn
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text(
                        text = "Data Sharing and Visibility",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Location Sharing Switch
                item {
                    SettingsSwitchItem(
                        title = "Location Sharing",
                        description = "Allow sharing of your real-time location with friends.",
                        checked = state.isLocationSharingEnabled,
                        onCheckedChange = viewModel::onLocationSharingChange,
                        modifier = Modifier.semantics { contentDescription = "Location Sharing Toggle" }
                    )
                }

                // Activity Visibility Dropdown
                item {
                    ActivityVisibilityDropdown(
                        currentVisibility = state.activityVisibility,
                        onVisibilityChange = viewModel::onActivityVisibilityChange
                    )
                }

                item { Divider(Modifier.padding(vertical = 16.dp)) }

                item {
                    Text(
                        text = "Data Collection and Retention",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Data Collection Toggle
                item {
                    SettingsSwitchItem(
                        title = "Data Collection",
                        description = "Allow us to collect anonymous usage data to improve the app.",
                        checked = state.isDataCollectionEnabled,
                        onCheckedChange = viewModel::onDataCollectionChange,
                        modifier = Modifier.semantics { contentDescription = "Data Collection Toggle" }
                    )
                }

                // Data Retention Slider
                item {
                    DataRetentionSlider(
                        currentDays = state.dataRetentionDays,
                        onDaysChange = viewModel::onDataRetentionChange
                    )
                }

                item { Divider(Modifier.padding(vertical = 16.dp)) }

                // Download Data Button
                item {
                    DownloadDataButton(
                        onClick = viewModel::onDownloadDataClick
                    )
                }
            }

            // Loading State Overlay
            if (state.isLoading) {
                LoadingOverlay()
            }

            // Confirmation Dialog
            if (state.showDownloadDialog) {
                DownloadDataConfirmationDialog(
                    onConfirm = viewModel::onConfirmDownloadData,
                    onDismiss = viewModel::dismissDownloadDialog
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 5. Reusable Composable Components
// -------------------------------------------------------------------------------------------------

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = { onCheckedChange(!checked) })
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue)
        )
    }
}

@Composable
fun ActivityVisibilityDropdown(
    currentVisibility: ActivityVisibility,
    onVisibilityChange: (ActivityVisibility) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Activity Visibility", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Who can see your in-app activity.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(currentVisibility.label)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ActivityVisibility.entries.forEach { visibility ->
                    DropdownMenuItem(
                        text = { Text(visibility.label) },
                        onClick = {
                            onVisibilityChange(visibility)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DataRetentionSlider(
    currentDays: Int,
    onDaysChange: (Int) -> Unit
) {
    val sliderPosition = remember(currentDays) { mutableFloatStateOf(currentDays.toFloat()) }
    val derivedDays by remember {
        derivedStateOf { sliderPosition.floatValue.toInt() }
    }

    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            text = "Data Retention Period: $derivedDays days",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Slider(
            value = sliderPosition.floatValue,
            onValueChange = {
                sliderPosition.floatValue = it
                // Input validation: only update the ViewModel on value change end
            },
            onValueChangeFinished = {
                // Validation: Ensure the value is within bounds before passing to VM
                val validatedDays = derivedDays.coerceIn(30, 365)
                onDaysChange(validatedDays)
            },
            valueRange = 30f..365f,
            steps = (365 - 30) / 30 - 1, // Steps every 30 days
            colors = SliderDefaults.colors(thumbColor = NaviBlue, activeTrackColor = NaviBlue),
            modifier = Modifier.semantics { contentDescription = "Data Retention Slider" }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("30 days", style = MaterialTheme.typography.bodySmall)
            Text("365 days", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun DownloadDataButton(
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
    ) {
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Download My Data")
    }
}

@Composable
fun DownloadDataConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = "Information") },
        title = { Text("Confirm Data Download") },
        text = {
            Text("A compressed archive of your personal data will be prepared and sent to your registered email address. This process may take a few minutes.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = NaviBlue)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LoadingOverlay() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.4f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            CircularProgressIndicator(color = NaviBlue)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 6. Preview (Requires a separate file in a real project, but included here for completeness)
// -------------------------------------------------------------------------------------------------

// Note: In a real project, this would be in a separate file and require a Hilt setup.
// For this single-file submission, we omit the @Preview annotation to avoid build errors
// but keep the structure.

/*
@Preview(showBackground = true)
@Composable
fun PreviewPrivacySettingsScreen() {
    // Mocking the dependencies for a preview
    val mockApi = MockApiService()
    val mockRepo = MockDataStoreRepository(mockApi)
    val mockVm = PrivacySettingsViewModel(mockRepo, mockApi)

    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        PrivacySettingsScreen(viewModel = mockVm)
    }
}
*/

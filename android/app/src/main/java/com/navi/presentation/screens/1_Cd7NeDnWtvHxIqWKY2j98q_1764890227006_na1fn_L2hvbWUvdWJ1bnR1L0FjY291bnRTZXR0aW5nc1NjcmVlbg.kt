package com.example.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// 1. Architecture & Data Structures

// Navi Blue Color(0xFF2563EB)
private val NaviBlue = Color(0xFF2563EB)

/**
 * Stub for the API Service. In a real app, this would be an interface
 * implemented by a Retrofit service or similar.
 */
interface ApiService {
    suspend fun updateProfile(name: String, email: String): Result<Unit>
    suspend fun changePassword(oldPass: String, newPass: String): Result<Unit>
    suspend fun getSubscriptionTier(): Result<String>
    suspend fun updatePaymentMethod(token: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun triggerBackupSync(): Result<Unit>
}

/**
 * Stub for DataStore Preferences.
 */
interface SettingsDataStore {
    val isRealTimePreviewEnabled: Flow<Boolean>
    suspend fun setRealTimePreviewEnabled(enabled: Boolean)
    val isAutoBackupEnabled: Flow<Boolean>
    suspend fun setAutoBackupEnabled(enabled: Boolean)
}

/**
 * Represents the entire UI state of the Account Settings screen.
 */
data class AccountSettingsState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val userName: String = "John Doe",
    val userEmail: String = "john.doe@example.com",
    val subscriptionTier: String = "Premium",
    val paymentMethod: String = "Visa ending in 4242",
    val isRealTimePreviewEnabled: Boolean = true,
    val backupFrequency: Float = 7f, // Days
    val isAutoBackupEnabled: Boolean = true,
    val passwordChangeSuccess: Boolean = false,
    val error: String? = null
)

/**
 * Sealed class for all user actions/events on the screen.
 */
sealed class AccountSettingsEvent {
    data class UpdateProfile(val name: String, val email: String) : AccountSettingsEvent()
    data class ChangePassword(val oldPass: String, val newPass: String) : AccountSettingsEvent()
    data object UpdatePaymentMethod : AccountSettingsEvent()
    data object DeleteAccount : AccountSettingsEvent()
    data class ToggleRealTimePreview(val enabled: Boolean) : AccountSettingsEvent()
    data class SetBackupFrequency(val days: Float) : AccountSettingsEvent()
    data class ToggleAutoBackup(val enabled: Boolean) : AccountSettingsEvent()
    data object ClearError : AccountSettingsEvent()
}

/**
 * Sealed class for one-time UI effects (e.g., Snackbar, Navigation).
 */
sealed class AccountSettingsUiEffect {
    data class ShowSnackbar(val message: String) : AccountSettingsUiEffect()
    data object NavigateBack : AccountSettingsUiEffect()
}

// 2. ViewModel Implementation

// Stub implementations for injection
class FakeApiService @Inject constructor() : ApiService {
    override suspend fun updateProfile(name: String, email: String): Result<Unit> {
        delay(500) // Simulate network delay
        return if (name.isBlank() || email.isBlank()) {
            Result.failure(Exception("Name and email cannot be empty."))
        } else {
            Result.success(Unit)
        }
    }
    override suspend fun changePassword(oldPass: String, newPass: String): Result<Unit> {
        delay(500)
        return if (oldPass == newPass) {
            Result.failure(Exception("New password must be different."))
        } else {
            Result.success(Unit)
        }
    }
    override suspend fun getSubscriptionTier(): Result<String> = Result.success("Premium")
    override suspend fun updatePaymentMethod(token: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteAccount(): Result<Unit> {
        delay(1000)
        return Result.success(Unit)
    }
    override suspend fun triggerBackupSync(): Result<Unit> = Result.success(Unit)
}

class FakeSettingsDataStore @Inject constructor() : SettingsDataStore {
    private val _isRealTimePreviewEnabled = MutableStateFlow(true)
    override val isRealTimePreviewEnabled: Flow<Boolean> = _isRealTimePreviewEnabled.asStateFlow()
    override suspend fun setRealTimePreviewEnabled(enabled: Boolean) {
        _isRealTimePreviewEnabled.value = enabled
    }

    private val _isAutoBackupEnabled = MutableStateFlow(true)
    override val isAutoBackupEnabled: Flow<Boolean> = _isAutoBackupEnabled.asStateFlow()
    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        _isAutoBackupEnabled.value = enabled
    }
}

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val apiService: ApiService,
    private val dataStore: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(AccountSettingsState(isLoading = true))
    val state: StateFlow<AccountSettingsState> = _state.asStateFlow()

    private val _uiEffect = Channel<AccountSettingsUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Combine flows for settings persistence
            combine(
                dataStore.isRealTimePreviewEnabled,
                dataStore.isAutoBackupEnabled
            ) { preview, backup ->
                _state.update {
                    it.copy(
                        isRealTimePreviewEnabled = preview,
                        isAutoBackupEnabled = backup,
                        isLoading = false // Initial load complete
                    )
                }
            }.collect()
        }

        // Initial API data load
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val tierResult = apiService.getSubscriptionTier()
            _state.update {
                it.copy(
                    subscriptionTier = tierResult.getOrDefault("Basic"),
                    isLoading = false
                )
            }
        }
    }

    fun onEvent(event: AccountSettingsEvent) {
        when (event) {
            is AccountSettingsEvent.UpdateProfile -> updateProfile(event.name, event.email)
            is AccountSettingsEvent.ChangePassword -> changePassword(event.oldPass, event.newPass)
            is AccountSettingsEvent.UpdatePaymentMethod -> updatePaymentMethod()
            is AccountSettingsEvent.DeleteAccount -> deleteAccount()
            is AccountSettingsEvent.ToggleRealTimePreview -> toggleRealTimePreview(event.enabled)
            is AccountSettingsEvent.SetBackupFrequency -> setBackupFrequency(event.days)
            is AccountSettingsEvent.ToggleAutoBackup -> toggleAutoBackup(event.enabled)
            is AccountSettingsEvent.ClearError -> _state.update { it.copy(error = null) }
        }
    }

    private fun updateProfile(name: String, email: String) = viewModelScope.launch {
        if (name.isBlank() || email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Validation Error: Invalid name or email format."))
            return@launch
        }

        _state.update { it.copy(isSaving = true) }
        val result = apiService.updateProfile(name, email)
        _state.update { it.copy(isSaving = false) }

        result.onSuccess {
            _state.update { it.copy(userName = name, userEmail = email) }
            _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Profile updated successfully!"))
        }.onFailure {
            _state.update { it.copy(error = it.message) }
            _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Error updating profile: ${it.message}"))
        }
    }

    private fun changePassword(oldPass: String, newPass: String) = viewModelScope.launch {
        if (newPass.length < 8) {
            _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Validation Error: Password must be at least 8 characters."))
            return@launch
        }

        _state.update { it.copy(isSaving = true) }
        val result = apiService.changePassword(oldPass, newPass)
        _state.update { it.copy(isSaving = false) }

        result.onSuccess {
            _state.update { it.copy(passwordChangeSuccess = true) }
            _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Password changed successfully!"))
        }.onFailure {
            _state.update { it.copy(error = it.message) }
            _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Error changing password: ${it.message}"))
        }
    }

    private fun updatePaymentMethod() = viewModelScope.launch {
        _state.update { it.copy(isSaving = true) }
        // Simulate payment token acquisition and API call
        val result = apiService.updatePaymentMethod("new_token_123")
        _state.update { it.copy(isSaving = false) }

        result.onSuccess {
            _state.update { it.copy(paymentMethod = "Mastercard ending in 1010") }
            _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Payment method updated."))
        }.onFailure {
            _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Failed to update payment method."))
        }
    }

    private fun deleteAccount() = viewModelScope.launch {
        _state.update { it.copy(isSaving = true) }
        val result = apiService.deleteAccount()
        _state.update { it.copy(isSaving = false) }

        result.onSuccess {
            _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Account deleted. Goodbye!"))
            _uiEffect.send(AccountSettingsUiEffect.NavigateBack)
        }.onFailure {
            _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Failed to delete account: ${it.message}"))
        }
    }

    private fun toggleRealTimePreview(enabled: Boolean) = viewModelScope.launch {
        dataStore.setRealTimePreviewEnabled(enabled)
        _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Real-time preview ${if (enabled) "enabled" else "disabled"}."))
    }

    private fun toggleAutoBackup(enabled: Boolean) = viewModelScope.launch {
        dataStore.setAutoBackupEnabled(enabled)
        // In a real app, this would also interact with WorkManager
        if (enabled) {
            apiService.triggerBackupSync() // Simulate immediate sync
        }
        _uiEffect.send(AccountSettingsUiEffect.ShowSnackbar("Auto-backup ${if (enabled) "enabled" else "disabled"}."))
    }

    private fun setBackupFrequency(days: Float) {
        _state.update { it.copy(backupFrequency = days) }
        // Real-time preview of setting change
    }
}

// 3. Composable Implementation

@Composable
fun AccountSettingsScreen(
    viewModel: AccountSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // Provide fake dependencies for preview/standalone use
                @Suppress("UNCHECKED_CAST")
                return AccountSettingsViewModel(FakeApiService(), FakeSettingsDataStore()) as T
            }
        }
    )
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle one-time UI effects
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is AccountSettingsUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                AccountSettingsUiEffect.NavigateBack -> {
                    // Handle navigation back (e.g., findNavController().popBackStack())
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account Settings") },
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
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { ProfileCard(state, viewModel::onEvent) }
                item { PasswordSection(viewModel::onEvent) }
                item { SubscriptionSection(state, viewModel::onEvent) }
                item { AdvancedSettings(state, viewModel::onEvent) }
                item { DeleteAccountSection(viewModel::onEvent) }
            }
        }
    }
}

@Composable
fun ProfileCard(
    state: AccountSettingsState,
    onEvent: (AccountSettingsEvent) -> Unit
) {
    var name by remember { mutableStateOf(state.userName) }
    var email by remember { mutableStateOf(state.userEmail) }

    // Real-time preview: Update local state when ViewModel state changes
    LaunchedEffect(state.userName, state.userEmail) {
        name = state.userName
        email = state.userEmail
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                enabled = !state.isSaving
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                enabled = !state.isSaving
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onEvent(AccountSettingsEvent.UpdateProfile(name, email)) },
                enabled = (name != state.userName || email != state.userEmail) && !state.isSaving,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Profile")
                }
            }
        }
    }
}

@Composable
fun PasswordSection(onEvent: (AccountSettingsEvent) -> Unit) {
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    val isPasswordValid by remember(newPass, confirmPass) {
        derivedStateOf { newPass.length >= 8 && newPass == confirmPass }
    }

    SettingSection(title = "Change Password") {
        OutlinedTextField(
            value = oldPass,
            onValueChange = { oldPass = it },
            label = { Text("Current Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
        )
        OutlinedTextField(
            value = newPass,
            onValueChange = { newPass = it },
            label = { Text("New Password (min 8 chars)") },
            leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
        )
        OutlinedTextField(
            value = confirmPass,
            onValueChange = { confirmPass = it },
            label = { Text("Confirm New Password") },
            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            isError = newPass.isNotEmpty() && newPass != confirmPass
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onEvent(AccountSettingsEvent.ChangePassword(oldPass, newPass)) },
            enabled = isPasswordValid && oldPass.isNotEmpty(),
            modifier = Modifier.align(Alignment.End),
            colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
        ) {
            Text("Change Password")
        }
    }
}

@Composable
fun SubscriptionSection(
    state: AccountSettingsState,
    onEvent: (AccountSettingsEvent) -> Unit
) {
    SettingSection(title = "Subscription & Billing") {
        SettingItem(
            icon = Icons.Default.Star,
            title = "Subscription Tier",
            subtitle = state.subscriptionTier,
            onClick = { /* Navigate to subscription management */ }
        )
        Divider()
        SettingItem(
            icon = Icons.Default.Payment,
            title = "Payment Method",
            subtitle = state.paymentMethod,
            onClick = { onEvent(AccountSettingsEvent.UpdatePaymentMethod) }
        )
    }
}

@Composable
fun AdvancedSettings(
    state: AccountSettingsState,
    onEvent: (AccountSettingsEvent) -> Unit
) {
    SettingSection(title = "Advanced Features") {
        // Switch for Real-time Preview
        SettingRow(
            title = "Real-time Preview",
            subtitle = "Enable instant visual feedback on changes.",
            icon = Icons.Default.Visibility
        ) {
            Switch(
                checked = state.isRealTimePreviewEnabled,
                onCheckedChange = { onEvent(AccountSettingsEvent.ToggleRealTimePreview(it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue),
                modifier = Modifier.semantics { contentDescription = "Real-time Preview Switch" }
            )
        }
        Divider()

        // Switch for Auto-Backup
        SettingRow(
            title = "Automatic Backup",
            subtitle = "Sync data to cloud storage via WorkManager.",
            icon = Icons.Default.CloudUpload
        ) {
            Switch(
                checked = state.isAutoBackupEnabled,
                onCheckedChange = { onEvent(AccountSettingsEvent.ToggleAutoBackup(it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue),
                modifier = Modifier.semantics { contentDescription = "Automatic Backup Switch" }
            )
        }
        Divider()

        // Slider for Backup Frequency
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = "Backup Frequency: ${state.backupFrequency.toInt()} days",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = state.backupFrequency,
                onValueChange = { onEvent(AccountSettingsEvent.SetBackupFrequency(it)) },
                valueRange = 1f..30f,
                steps = 28,
                colors = SliderDefaults.colors(thumbColor = NaviBlue, activeTrackColor = NaviBlue),
                modifier = Modifier.semantics { contentDescription = "Backup Frequency Slider" }
            )
            Text(
                text = "Determines how often WorkManager triggers a full backup sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DeleteAccountSection(onEvent: (AccountSettingsEvent) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    SettingSection(title = "Danger Zone") {
        OutlinedButton(
            onClick = { showDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(MaterialTheme.colorScheme.error)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete Account")
        }
    }

    if (showDialog) {
        DeleteAccountDialog(
            onConfirm = {
                onEvent(AccountSettingsEvent.DeleteAccount)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Confirm Account Deletion") },
        text = { Text("Are you absolutely sure you want to delete your account? This action is irreversible.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper Composables

@Composable
fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = NaviBlue,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = NaviBlue,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Navigate to $title")
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                icon,
                contentDescription = null,
                tint = NaviBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        trailing()
    }
}

// Preview
@Preview(showBackground = true)
@Composable
fun PreviewAccountSettingsScreen() {
    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        AccountSettingsScreen()
    }
}

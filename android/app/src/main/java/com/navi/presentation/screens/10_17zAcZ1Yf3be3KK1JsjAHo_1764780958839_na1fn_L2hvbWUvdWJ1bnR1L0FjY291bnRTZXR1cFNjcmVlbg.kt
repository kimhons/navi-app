package com.example.app.ui.accountsetup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- 1. Navi Design System Definitions ---

// Primary color: #2563EB
val NaviPrimary = Color(0xFF2563EB)

// Placeholder for a simple Typography definition using Roboto (default system font)
object NaviTheme {
    val typography = Typography(
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default, // Assuming Roboto is the default system font
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        )
    )
}

// --- 2. Data Classes and State ---

data class AccountSetupState(
    val displayName: String = "",
    val displayNameError: String? = null,
    val selectedPreferences: Set<String> = emptySet(),
    val availablePreferences: List<String> = listOf("Tech", "Sports", "Music", "Travel", "Food", "Gaming"),
    val photoUri: String? = null, // Using String to simulate a URI or URL
    val isLoading: Boolean = false,
    val apiError: String? = null,
    val isSetupComplete: Boolean = false
) {
    val isFormValid: Boolean
        get() = displayName.isNotBlank() && displayNameError == null && selectedPreferences.isNotEmpty()
}

sealed class AccountSetupEvent {
    data class DisplayNameChanged(val name: String) : AccountSetupEvent()
    data class PreferenceToggled(val preference: String) : AccountSetupEvent()
    data class PhotoSelected(val uri: String) : AccountSetupEvent()
    object Submit : AccountSetupEvent()
    object ErrorDismissed : AccountSetupEvent()
}

// --- 3. Simulated API Service ---

interface ApiService {
    suspend fun updateProfile(displayName: String, preferences: Set<String>, photoUri: String?): Result<Unit>
}

class FakeApiService @Inject constructor() : ApiService {
    override suspend fun updateProfile(displayName: String, preferences: Set<String>, photoUri: String?): Result<Unit> {
        delay(1500) // Simulate network delay
        return if (displayName.contains("fail", ignoreCase = true)) {
            Result.failure(Exception("API Error: Display name cannot contain 'fail'."))
        } else {
            Result.success(Unit)
        }
    }
}

// --- 4. ViewModel ---

@HiltViewModel
class AccountSetupViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(AccountSetupState())
    val state: StateFlow<AccountSetupState> = _state.asStateFlow()

    fun onEvent(event: AccountSetupEvent) {
        when (event) {
            is AccountSetupEvent.DisplayNameChanged -> handleDisplayNameChange(event.name)
            is AccountSetupEvent.PreferenceToggled -> handlePreferenceToggle(event.preference)
            is AccountSetupEvent.PhotoSelected -> _state.update { it.copy(photoUri = event.uri) }
            AccountSetupEvent.Submit -> handleSubmit()
            AccountSetupEvent.ErrorDismissed -> _state.update { it.copy(apiError = null) }
        }
    }

    private fun handleDisplayNameChange(name: String) {
        val error = when {
            name.length < 3 -> "Display name must be at least 3 characters."
            name.length > 20 -> "Display name cannot exceed 20 characters."
            else -> null
        }
        _state.update { it.copy(displayName = name, displayNameError = error) }
    }

    private fun handlePreferenceToggle(preference: String) {
        _state.update { currentState ->
            val newSet = if (currentState.selectedPreferences.contains(preference)) {
                currentState.selectedPreferences - preference
            } else {
                currentState.selectedPreferences + preference
            }
            currentState.copy(selectedPreferences = newSet)
        }
    }

    private fun handleSubmit() {
        if (!_state.value.isFormValid) return

        _state.update { it.copy(isLoading = true, apiError = null) }

        viewModelScope.launch {
            val currentState = _state.value
            val result = apiService.updateProfile(
                displayName = currentState.displayName,
                preferences = currentState.selectedPreferences,
                photoUri = currentState.photoUri
            )

            _state.update { it.copy(isLoading = false) }

            result.onSuccess {
                _state.update { it.copy(isSetupComplete = true) }
                // In a real app, navigation would happen here
            }.onFailure { exception ->
                _state.update { it.copy(apiError = exception.message) }
            }
        }
    }
}

// --- 5. Composable UI ---

@Composable
fun AccountSetupScreen(
    navController: NavController,
    viewModel: AccountSetupViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Use MaterialTheme with NaviPrimary as the primary color
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(primary = NaviPrimary),
        typography = NaviTheme.typography
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Complete Your Profile") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            content = { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Photo Upload Placeholder
                    PhotoUploadSection(
                        photoUri = state.photoUri,
                        onPhotoSelected = { uri -> viewModel.onEvent(AccountSetupEvent.PhotoSelected(uri)) }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Display Name Field
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = { viewModel.onEvent(AccountSetupEvent.DisplayNameChanged(it)) },
                        label = { Text("Display Name") },
                        isError = state.displayNameError != null,
                        supportingText = {
                            if (state.displayNameError != null) {
                                Text(state.displayNameError, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("Choose a name for your profile.")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Display Name Input Field" },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Preferences Selection
                    PreferencesSelection(
                        availablePreferences = state.availablePreferences,
                        selectedPreferences = state.selectedPreferences,
                        onPreferenceToggled = { pref -> viewModel.onEvent(AccountSetupEvent.PreferenceToggled(pref)) }
                    )

                    Spacer(modifier = Modifier.weight(1f)) // Push button to the bottom

                    // Submit Button
                    Button(
                        onClick = { viewModel.onEvent(AccountSetupEvent.Submit) },
                        enabled = state.isFormValid && !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(vertical = 8.dp)
                            .semantics { contentDescription = "Complete Profile Button" }
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text("Complete Profile", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        )

        // Error Handling Snackbar
        if (state.apiError != null) {
            LaunchedEffect(state.apiError) {
                val snackbarResult = SnackbarHostState().showSnackbar(
                    message = state.apiError!!,
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Indefinite
                )
                if (snackbarResult == SnackbarResult.Dismissed || snackbarResult == SnackbarResult.ActionPerformed) {
                    viewModel.onEvent(AccountSetupEvent.ErrorDismissed)
                }
            }
        }

        // Success State (Simulated Navigation)
        if (state.isSetupComplete) {
            LaunchedEffect(Unit) {
                // Simulate navigation to the next screen
                navController.navigate("home_screen") {
                    popUpTo("account_setup_route") { inclusive = true }
                }
            }
        }
    }
}

@Composable
fun PhotoUploadSection(
    photoUri: String?,
    onPhotoSelected: (String) -> Unit
) {
    val photoDescription = if (photoUri == null) "Upload profile photo" else "Change profile photo"
    
    Card(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = photoDescription) {
                // Simulate photo selection
                onPhotoSelected("simulated_uri_123")
            }
            .semantics { contentDescription = photoDescription },
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (photoUri == null) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Profile Photo Placeholder",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // In a real app, use Coil/Glide to load the image from photoUri
                Text(
                    text = "Photo Loaded",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = if (photoUri == null) "Tap to upload photo" else "Photo uploaded",
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
    )
}

@Composable
fun PreferencesSelection(
    availablePreferences: List<String>,
    selectedPreferences: Set<String>,
    onPreferenceToggled: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Select Your Interests",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { contentDescription = "Interest Selection Chips" }
        ) {
            items(availablePreferences) { preference ->
                val isSelected = selectedPreferences.contains(preference)
                FilterChip(
                    selected = isSelected,
                    onClick = { onPreferenceToggled(preference) },
                    label = { Text(preference) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NaviPrimary.copy(alpha = 0.1f),
                        selectedLabelColor = NaviPrimary,
                        selectedLeadingIconColor = NaviPrimary
                    )
                )
            }
        }
        if (selectedPreferences.isEmpty()) {
            Text(
                text = "Please select at least one interest.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// --- 6. Preview ---

@Preview(showBackground = true)
@Composable
fun AccountSetupScreenPreview() {
    // Mock the dependencies for the preview
    val mockNavController = rememberNavController()
    val mockApiService = FakeApiService()
    val mockViewModel = AccountSetupViewModel(mockApiService)

    // Set a mock state for a better preview
    LaunchedEffect(Unit) {
        mockViewModel.onEvent(AccountSetupEvent.DisplayNameChanged("JohnDoe"))
        mockViewModel.onEvent(AccountSetupEvent.PreferenceToggled("Tech"))
    }

    AccountSetupScreen(navController = mockNavController, viewModel = mockViewModel)
}

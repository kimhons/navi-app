package com.example.app.permissions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.accompanist.permissions.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// --- 1. Design System Mocks (Navi Design) ---

val PrimaryColor = Color(0xFF2563EB)

val NaviColorScheme = lightColorScheme(
    primary = PrimaryColor,
    onPrimary = Color.White,
    surface = Color.White,
    onSurface = Color.Black
)

@Composable
fun NaviTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NaviColorScheme,
        typography = Typography(
            // Simulating Roboto font by using default system font with specific weights/sizes
            headlineLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            )
        ),
        content = content
    )
}

// --- 2. API Service Mock ---

/**
 * Mock API Service interface to simulate network interaction.
 */
interface ApiService {
    /**
     * Sends the current permission status to the backend.
     * Simulates a network call that can succeed or fail.
     */
    suspend fun sendPermissionsStatus(locationGranted: Boolean, notificationGranted: Boolean): Flow<Result<Boolean>>
}

@Singleton
class MockApiService @Inject constructor() : ApiService {
    override suspend fun sendPermissionsStatus(locationGranted: Boolean, notificationGranted: Boolean): Flow<Result<Boolean>> = flow {
        delay(1000) // Simulate network latency
        // Simple logic: always succeed if both are granted, otherwise 50% chance of failure
        if (locationGranted && notificationGranted) {
            emit(Result.success(true))
        } else if (System.currentTimeMillis() % 2 == 0L) {
            emit(Result.success(true))
        } else {
            emit(Result.failure(Exception("Failed to register permissions status on server.")))
        }
    }
}

// --- 3. State and Event Definitions ---

data class PermissionsScreenState(
    val isLoading: Boolean = false,
    val apiError: String? = null,
    val locationPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val isApiCallSuccessful: Boolean = false,
    val isLocationPermissionRequested: Boolean = false,
    val isNotificationPermissionRequested: Boolean = false
)

sealed class PermissionsScreenEvent {
    data object OnContinueClicked : PermissionsScreenEvent()
    data class OnLocationPermissionResult(val granted: Boolean) : PermissionsScreenEvent()
    data class OnNotificationPermissionResult(val granted: Boolean) : PermissionsScreenEvent()
    data object OnErrorDismissed : PermissionsScreenEvent()
    data object OnNavigationComplete : PermissionsScreenEvent()
}

// --- 4. ViewModel ---

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(PermissionsScreenState())
    val state: StateFlow<PermissionsScreenState> = _state

    fun handleEvent(event: PermissionsScreenEvent) {
        when (event) {
            is PermissionsScreenEvent.OnContinueClicked -> handleContinueClicked()
            is PermissionsScreenEvent.OnLocationPermissionResult -> updateLocationPermission(event.granted)
            is PermissionsScreenEvent.OnNotificationPermissionResult -> updateNotificationPermission(event.granted)
            is PermissionsScreenEvent.OnErrorDismissed -> dismissError()
            is PermissionsScreenEvent.OnNavigationComplete -> navigationComplete()
        }
    }

    private fun updateLocationPermission(granted: Boolean) {
        _state.value = _state.value.copy(
            locationPermissionGranted = granted,
            isLocationPermissionRequested = true
        )
    }

    private fun updateNotificationPermission(granted: Boolean) {
        _state.value = _state.value.copy(
            notificationPermissionGranted = granted,
            isNotificationPermissionRequested = true
        )
    }

    private fun dismissError() {
        _state.value = _state.value.copy(apiError = null)
    }

    private fun navigationComplete() {
        _state.value = _state.value.copy(isApiCallSuccessful = false)
    }

    private fun handleContinueClicked() {
        val currentState = _state.value
        if (currentState.isLoading) return

        _state.value = currentState.copy(isLoading = true, apiError = null)

        viewModelScope.launch {
            apiService.sendPermissionsStatus(
                locationGranted = currentState.locationPermissionGranted,
                notificationGranted = currentState.notificationPermissionGranted
            ).collect { result ->
                _state.value = _state.value.copy(isLoading = false)
                result.onSuccess { success ->
                    if (success) {
                        _state.value = _state.value.copy(isApiCallSuccessful = true)
                    } else {
                        // This case should ideally not happen with the current mock, but good practice
                        _state.value = _state.value.copy(apiError = "Server reported failure.")
                    }
                }.onFailure { exception ->
                    _state.value = _state.value.copy(apiError = exception.message ?: "An unknown API error occurred.")
                }
            }
        }
    }
}

// --- 5. Composable ---

import android.Manifest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog

@Composable
fun PermissionsScreen(
    navController: NavController,
    viewModel: PermissionsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle(
        initialValue = PermissionsScreenState(),
        lifecycleOwner = LocalLifecycleOwner.current
    )
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Accompanist Permissions State
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    )

    // Handle API call success for navigation
    LaunchedEffect(state.isApiCallSuccessful) {
        if (state.isApiCallSuccessful) {
            navController.navigate(NEXT_SCREEN_ROUTE) {
                popUpTo("permissions_screen") { inclusive = true } // Assuming "permissions_screen" is the current route
            }
            viewModel.handleEvent(PermissionsScreenEvent.OnNavigationComplete)
        }
    }

    // Handle API error for Snackbar
    LaunchedEffect(state.apiError) {
        state.apiError?.let { error ->
            snackbarHostState.showSnackbar(
                message = "Error: $error",
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Long
            )
            viewModel.handleEvent(PermissionsScreenEvent.OnErrorDismissed)
        }
    }

    NaviTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Welcome to Navi",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "We need a few permissions to get you started.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                }

                // Permission Cards
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Location Permission
                        PermissionItem(
                            icon = Icons.Default.LocationOn,
                            title = "Location Access",
                            description = "We use your location to provide personalized services.",
                            permissionState = permissionsState.permissions.find { it.permission == Manifest.permission.ACCESS_FINE_LOCATION }!!,
                            onPermissionResult = { granted ->
                                viewModel.handleEvent(PermissionsScreenEvent.OnLocationPermissionResult(granted))
                            }
                        )
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        // Notification Permission
                        PermissionItem(
                            icon = Icons.Default.Notifications,
                            title = "Notifications",
                            description = "Get real-time updates and important alerts.",
                            permissionState = permissionsState.permissions.find { it.permission == Manifest.permission.POST_NOTIFICATIONS }!!,
                            onPermissionResult = { granted ->
                                viewModel.handleEvent(PermissionsScreenEvent.OnNotificationPermissionResult(granted))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Continue Button
                Button(
                    onClick = {
                        // Request all permissions at once on continue
                        permissionsState.launchMultiplePermissionRequest()
                        // If all are granted, trigger API call
                        if (permissionsState.allPermissionsGranted) {
                            viewModel.handleEvent(PermissionsScreenEvent.OnContinueClicked)
                        }
                    },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { contentDescription = "Continue button" }
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Continue", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    permissionState: PermissionState,
    onPermissionResult: (Boolean) -> Unit
) {
    val isGranted = permissionState.status.isGranted
    val iconColor = if (isGranted) Color.Green else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val statusIcon = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // Decorative icon
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        if (isGranted) {
            Icon(
                imageVector = statusIcon,
                contentDescription = "$title granted",
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        } else {
            OutlinedButton(
                onClick = {
                    // This will trigger the system permission dialog or show rationale
                    permissionState.launchPermissionRequest()
                },
                modifier = Modifier.semantics { contentDescription = "Request $title permission" }
            ) {
                Text("Request")
            }
        }
    }

    // Handle Rationale Dialog
    if (permissionState.status.shouldShowRationale) {
        PermissionRationaleDialog(
            title = title,
            description = "The $title permission is important for the app to function correctly. Please grant access.",
            onDismiss = { /* User dismissed, do nothing or log */ },
            onConfirm = { permissionState.launchPermissionRequest() }
        )
    }

    // Use LaunchedEffect to track permission result after request
    LaunchedEffect(permissionState.status) {
        val newStatus = permissionState.status
        if (newStatus.isGranted) {
            onPermissionResult(true)
        } else if (newStatus.isPermanentlyDenied() && !permissionState.status.shouldShowRationale) {
            // Permanently denied, but we only update state if it was requested
            // The UI will show the "Request" button which can lead to settings
            onPermissionResult(false)
        }
    }
}

@Composable
fun PermissionRationaleDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Why we need $title",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Not Now")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onConfirm) {
                        Text("Continue")
                    }
                }
            }
        }
    }
}

// End of Phase 3 implementation

// Mock NavController for preview/testing purposes
// In a real app, this would be passed from the NavHost
const val NEXT_SCREEN_ROUTE = "home_screen"

// The actual screen composable will be defined below

class MockNavController : NavController(androidx.compose.ui.platform.LocalContext.current) {
    var navigatedTo: String? = null
    override fun navigate(route: String) {
        navigatedTo = route
    }
}

// Mock Hilt setup for a single file
// In a real app, this would be handled by the application class and modules
// We just need the annotations for the ViewModel
// @Module
// @InstallIn(SingletonComponent::class)
// object AppModule {
//     @Provides
//     @Singleton
//     fun provideApiService(): ApiService = MockApiService()
// }

// End of Phase 1 structure

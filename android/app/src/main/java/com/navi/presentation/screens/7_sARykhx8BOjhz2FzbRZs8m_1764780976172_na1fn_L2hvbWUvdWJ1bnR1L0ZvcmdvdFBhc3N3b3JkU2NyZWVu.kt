package com.navi.auth.forgotpassword

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

// --- 1. Navi Design System Mock ---

// Primary color: #2563EB
val NaviPrimary = Color(0xFF2563EB)

@Composable
fun NaviTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = NaviPrimary,
        onPrimary = Color.White,
        surface = Color.White,
        onSurface = Color.Black
        // In a real app, you'd define a full color scheme and typography (Roboto) here.
    )
    MaterialTheme(
        colorScheme = colorScheme,
        // Typography would be defined here, e.g., Typography(defaultFontFamily = Roboto)
        content = content
    )
}

// --- 2. Mock API Service and Models ---

interface ApiService {
    suspend fun requestPasswordReset(email: String): Result<Unit>
}

class MockApiService @Inject constructor() : ApiService {
    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        delay(1500) // Simulate network delay
        return when {
            email.isBlank() -> Result.failure(IllegalArgumentException("Email cannot be empty"))
            email == "error@navi.com" -> Result.failure(Exception("User not found or service unavailable."))
            else -> Result.success(Unit)
        }
    }
}

// --- 3. UI State and Events ---

data class ForgotPasswordState(
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val isEmailValid: Boolean = true
)

sealed class ForgotPasswordEvent {
    data class OnEmailChange(val email: String) : ForgotPasswordEvent()
    object OnSubmitClick : ForgotPasswordEvent()
    object OnBackClick : ForgotPasswordEvent()
    object OnErrorDismissed : ForgotPasswordEvent()
}

// --- 4. Email Validation Utility ---

fun isValidEmail(email: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

// --- 5. Mock String Resources (for production readiness) ---

// In a real app, these would be in res/values/strings.xml
@Composable
fun stringResource(id: Int): String {
    return when (id) {
        0 -> "Forgot Password"
        1 -> "Enter your email address to receive a password reset link."
        2 -> "Email"
        3 -> "Submit"
        4 -> "Invalid email format."
        5 -> "Password reset link sent! Check your inbox."
        6 -> "Error"
        7 -> "OK"
        else -> ""
    }
}

// --- ViewModel Implementation will follow in Phase 2 ---
// --- Composable UI Implementation will follow in Phase 3 ---
// --- Preview will follow in Phase 3 ---

// --- 6. ViewModel Implementation (MVVM, Hilt, StateFlow, Coroutines) ---

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordState())
    val state: StateFlow<ForgotPasswordState> = _state.asStateFlow()

    fun handleEvent(event: ForgotPasswordEvent) {
        when (event) {
            is ForgotPasswordEvent.OnEmailChange -> onEmailChange(event.email)
            is ForgotPasswordEvent.OnSubmitClick -> onSubmitClick()
            is ForgotPasswordEvent.OnBackClick -> onBackClick()
            is ForgotPasswordEvent.OnErrorDismissed -> onErrorDismissed()
        }
    }

    private fun onEmailChange(email: String) {
        _state.update {
            it.copy(
                email = email,
                isEmailValid = true, // Clear validation error on change
                errorMessage = null, // Clear general error on change
                isSuccess = false
            )
        }
    }

    private fun onSubmitClick() {
        val currentEmail = _state.value.email

        // 1. Form Validation
        if (!isValidEmail(currentEmail)) {
            _state.update {
                it.copy(isEmailValid = false)
            }
            return
        }

        // 2. API Call with Loading State
        _state.update {
            it.copy(isLoading = true, errorMessage = null, isSuccess = false)
        }

        viewModelScope.launch {
            try {
                val result = apiService.requestPasswordReset(currentEmail)
                if (result.isSuccess) {
                    _state.update {
                        it.copy(isLoading = false, isSuccess = true)
                    }
                } else {
                    // Error Handling
                    val error = result.exceptionOrNull()?.message ?: "An unknown error occurred."
                    _state.update {
                        it.copy(isLoading = false, errorMessage = error)
                    }
                }
            } catch (e: Exception) {
                // Network/Unexpected Error Handling
                _state.update {
                    it.copy(isLoading = false, errorMessage = "Network error: ${e.message}")
                }
            }
        }
    }

    private fun onBackClick() {
        // In a real app, this would trigger navigation back
        // For this mock, we'll just log or do nothing.
        println("Navigation back requested.")
    }

    private fun onErrorDismissed() {
        _state.update { it.copy(errorMessage = null) }
    }
}

// --- 7. Composable UI Implementation (Material 3, Jetpack Compose) ---

@Composable
fun ForgotPasswordScreen(
    navController: NavController,
    viewModel: ForgotPasswordViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    NaviTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(0)) }, // "Forgot Password"
                    navigationIcon = {
                        IconButton(onClick = {
                            // Handle back navigation using NavController
                            navController.popBackStack()
                            viewModel.handleEvent(ForgotPasswordEvent.OnBackClick)
                        }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 32.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(1), // "Enter your email address..."
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        OutlinedTextField(
                            value = state.email,
                            onValueChange = { viewModel.handleEvent(ForgotPasswordEvent.OnEmailChange(it)) },
                            label = { Text(stringResource(2)) }, // "Email"
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            isError = !state.isEmailValid,
                            supportingText = {
                                if (!state.isEmailValid) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = stringResource(4), // "Invalid email format."
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading,
                            // Content description for accessibility
                            label = { Text(stringResource(2), modifier = Modifier.semantics { contentDescription = "Email input field" }) }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { viewModel.handleEvent(ForgotPasswordEvent.OnSubmitClick) },
                            enabled = !state.isLoading && state.email.isNotBlank() && state.isEmailValid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Text(stringResource(3)) // "Submit"
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Error Handling Dialog ---
    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.handleEvent(ForgotPasswordEvent.OnErrorDismissed) },
            title = { Text(stringResource(6)) }, // "Error"
            text = { Text(state.errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.handleEvent(ForgotPasswordEvent.OnErrorDismissed) }) {
                    Text(stringResource(7)) // "OK"
                }
            }
        )
    }

    // --- Success State Dialog ---
    if (state.isSuccess) {
        AlertDialog(
            onDismissRequest = { /* Do nothing, success is a final state for this action */ },
            title = { Text("Success") },
            text = { Text(stringResource(5)) }, // "Password reset link sent! Check your inbox."
            confirmButton = {
                TextButton(onClick = {
                    // In a real app, you might navigate to a login screen here
                    navController.popBackStack()
                }) {
                    Text(stringResource(7)) // "OK"
                }
            }
        )
    }
}

// --- 8. Preview ---

@Preview(showBackground = true)
@Composable
fun ForgotPasswordScreenPreview() {
    // Mocking the ViewModel for the Preview
    class MockViewModel(private val initialState: ForgotPasswordState) : ViewModel() {
        private val _state = MutableStateFlow(initialState)
        val state: StateFlow<ForgotPasswordState> = _state.asStateFlow()
        fun handleEvent(event: ForgotPasswordEvent) {}
    }

    // Use a mock NavController
    val mockNavController = rememberNavController()

    // Preview 1: Default State
    NaviTheme {
        ForgotPasswordScreen(
            navController = mockNavController,
            viewModel = MockViewModel(ForgotPasswordState())
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ForgotPasswordScreenLoadingPreview() {
    class MockViewModel(private val initialState: ForgotPasswordState) : ViewModel() {
        private val _state = MutableStateFlow(initialState)
        val state: StateFlow<ForgotPasswordState> = _state.asStateFlow()
        fun handleEvent(event: ForgotPasswordEvent) {}
    }
    val mockNavController = rememberNavController()

    // Preview 2: Loading State
    NaviTheme {
        ForgotPasswordScreen(
            navController = mockNavController,
            viewModel = MockViewModel(ForgotPasswordState(email = "test@navi.com", isLoading = true))
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ForgotPasswordScreenErrorPreview() {
    class MockViewModel(private val initialState: ForgotPasswordState) : ViewModel() {
        private val _state = MutableStateFlow(initialState)
        val state: StateFlow<ForgotPasswordState> = _state.asStateFlow()
        fun handleEvent(event: ForgotPasswordEvent) {}
    }
    val mockNavController = rememberNavController()

    // Preview 3: Error State
    NaviTheme {
        ForgotPasswordScreen(
            navController = mockNavController,
            viewModel = MockViewModel(ForgotPasswordState(email = "error@navi.com", errorMessage = "User not found."))
        )
    }
}

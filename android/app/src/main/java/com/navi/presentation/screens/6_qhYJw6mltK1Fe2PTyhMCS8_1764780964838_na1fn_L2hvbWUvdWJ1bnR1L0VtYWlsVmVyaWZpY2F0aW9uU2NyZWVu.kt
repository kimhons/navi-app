package com.navi.auth.verification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// 1. Navi Design System Color
val NaviPrimary = Color(0xFF2563EB)

// 2. Mock API Service
interface ApiService {
    suspend fun verifyEmailCode(email: String, code: String): Result<Unit>
    suspend fun resendVerificationCode(email: String): Result<Unit>
}

class MockApiService : ApiService {
    override suspend fun verifyEmailCode(email: String, code: String): Result<Unit> {
        delay(1000) // Simulate network delay
        return if (code == "123456") {
            Result.success(Unit)
        } else if (code == "999999") {
            Result.failure(Exception("Invalid code. Please try again."))
        } else {
            Result.failure(Exception("Verification failed. Unknown error."))
        }
    }

    override suspend fun resendVerificationCode(email: String): Result<Unit> {
        delay(500) // Simulate network delay
        return Result.success(Unit)
    }
}

// 3. ViewModel State
data class EmailVerificationState(
    val code: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val countdownSeconds: Int = 60,
    val isResendEnabled: Boolean = false,
    val isCodeValid: Boolean = false,
    val verificationSuccess: Boolean = false
)

// 4. ViewModel Events
sealed class EmailVerificationEvent {
    data class OnCodeChange(val newCode: String) : EmailVerificationEvent()
    object OnVerifyClick : EmailVerificationEvent()
    object OnResendClick : EmailVerificationEvent()
    object OnErrorDismissed : EmailVerificationEvent()
}

// 5. ViewModel Implementation
@HiltViewModel
class EmailVerificationViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(EmailVerificationState())
    val state: StateFlow<EmailVerificationState> = _state.asStateFlow()

    private val email = "user@example.com" // Mock user email

    init {
        startCountdown()
    }

    fun onEvent(event: EmailVerificationEvent) {
        when (event) {
            is EmailVerificationEvent.OnCodeChange -> handleCodeChange(event.newCode)
            EmailVerificationEvent.OnVerifyClick -> verifyCode()
            EmailVerificationEvent.OnResendClick -> resendCode()
            EmailVerificationEvent.OnErrorDismissed -> _state.update { it.copy(error = null) }
        }
    }

    private fun handleCodeChange(newCode: String) {
        val filteredCode = newCode.filter { it.isDigit() }.take(6)
        _state.update {
            it.copy(
                code = filteredCode,
                isCodeValid = filteredCode.length == 6,
                error = null // Clear error on input change
            )
        }
    }

    private fun startCountdown() {
        viewModelScope.launch {
            _state.update { it.copy(isResendEnabled = false) }
            for (i in 60 downTo 0) {
                _state.update { it.copy(countdownSeconds = i) }
                if (i == 0) {
                    _state.update { it.copy(isResendEnabled = true) }
                    break
                }
                delay(1000)
            }
        }
    }

    private fun verifyCode() {
        if (!_state.value.isCodeValid) {
            _state.update { it.copy(error = "Please enter a valid 6-digit code.") }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = apiService.verifyEmailCode(email, _state.value.code)
            _state.update { it.copy(isLoading = false) }

            result.onSuccess {
                _state.update { it.copy(verificationSuccess = true) }
            }.onFailure { exception ->
                _state.update { it.copy(error = exception.message ?: "Verification failed.") }
            }
        }
    }

    private fun resendCode() {
        if (!_state.value.isResendEnabled) return

        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = apiService.resendVerificationCode(email)
            _state.update { it.copy(isLoading = false) }

            result.onSuccess {
                // Restart countdown on success
                startCountdown()
            }.onFailure { exception ->
                _state.update { it.copy(error = exception.message ?: "Failed to resend code.") }
            }
        }
    }
}

// 6. Composable UI
@Composable
fun EmailVerificationScreen(
    viewModel: EmailVerificationViewModel,
    navController: NavController
) {
    // Collect state from ViewModel
    val state by viewModel.state.collectAsState()

    // Mock navigation on success
    LaunchedEffect(state.verificationSuccess) {
        if (state.verificationSuccess) {
            // In a real app, navigate to the next screen (e.g., Home or Profile Setup)
            navController.navigate("home_screen") {
                popUpTo("verification_screen") { inclusive = true }
            }
        }
    }

    // Custom Theme for Navi Design System
    val naviColorScheme = lightColorScheme(
        primary = NaviPrimary,
        onPrimary = Color.White,
        error = MaterialTheme.colorScheme.error
    )

    MaterialTheme(
        colorScheme = naviColorScheme,
        typography = Typography(
            // Mocking Roboto font family as it's not standard in Compose
            // In a real app, we would define a custom FontFamily for Roboto
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Email Verification") })
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
                        .align(Alignment.Center),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Enter 6-Digit Code",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "A verification code has been sent to ${viewModel.email}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // OTP Input Field
                        OutlinedTextField(
                            value = state.code,
                            onValueChange = { viewModel.onEvent(EmailVerificationEvent.OnCodeChange(it)) },
                            label = { Text("Verification Code") },
                            isError = state.error != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            // Accessibility: Content Description
                            supportingText = {
                                Text(
                                    text = "Enter the 6-digit code sent to your email.",
                                    modifier = Modifier.semantics { contentDescription = "Verification code input field" }
                                )
                            }
                        )

                        // Error Handling
                        state.error?.let { errorMessage ->
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Verify Button
                        Button(
                            onClick = { viewModel.onEvent(EmailVerificationEvent.OnVerifyClick) },
                            enabled = state.isCodeValid && !state.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Verify Code")
                            }
                        }

                        // Resend Code Section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.isResendEnabled) "Code expired." else "Resend in ${state.countdownSeconds}s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { viewModel.onEvent(EmailVerificationEvent.OnResendClick) },
                                enabled = state.isResendEnabled && !state.isLoading
                            ) {
                                Text("Resend Code")
                            }
                        }
                    }
                }
            }
        }
    }
}

// 7. Preview Composable (for testing/display)
@Preview(showBackground = true)
@Composable
fun PreviewEmailVerificationScreen() {
    // Mock dependencies for preview
    val mockViewModel = EmailVerificationViewModel(MockApiService())
    val mockNavController = rememberNavController()

    // Inject a state for previewing different scenarios (e.g., error state)
    // For a real preview, you'd use a custom PreviewParameterProvider or a dedicated mock state
    // For simplicity, we'll use the default state here.

    EmailVerificationScreen(
        viewModel = mockViewModel,
        navController = mockNavController
    )
}

// Mock Hilt setup for compilation (required for @HiltViewModel)
// In a real project, these would be in separate files.
// We include them here to make the file self-contained.
annotation class HiltViewModel
annotation class Inject
class Result<T> private constructor(val value: Any?) {
    val isSuccess: Boolean get() = value !is Failure
    val isFailure: Boolean get() = value is Failure

    inline fun onSuccess(action: (value: T) -> Unit): Result<T> {
        if (isSuccess) action(value as T)
        return this
    }

    inline fun onFailure(action: (exception: Throwable) -> Unit): Result<T> {
        (value as? Failure)?.exception?.let(action)
        return this
    }

    companion object {
        fun <T> success(value: T): Result<T> = Result(value)
        fun <T> failure(exception: Throwable): Result<T> = Result(Failure(exception))
    }

    private class Failure(val exception: Throwable)
}

// Mock Compose dependencies for self-contained file
// In a real project, these would be imported from androidx.compose.ui.semantics
object SemanticsPropertyKey
class SemanticsPropertyReceiver
fun Modifier.semantics(properties: SemanticsPropertyReceiver.() -> Unit): Modifier = this
val SemanticsPropertyReceiver.contentDescription: SemanticsPropertyKey<String> get() = SemanticsPropertyKey()
fun SemanticsPropertyReceiver.contentDescription(value: String) {}

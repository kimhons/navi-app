package com.navi.auth.resetpassword

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

// --- 1. Design System & Theme Mock ---

// Mock R.string for a self-contained file
private object R {
    object string {
        const val reset_password_title = "Reset Password"
        const val new_password_label = "New Password"
        const val confirm_password_label = "Confirm Password"
        const val reset_button = "Reset"
        const val password_too_short = "Password must be at least 8 characters."
        const val passwords_do_not_match = "Passwords do not match."
        const val password_strength_weak = "Weak"
        const val password_strength_medium = "Medium"
        const val password_strength_strong = "Strong"
        const val password_strength_very_strong = "Very Strong"
        const val password_reset_success = "Password reset successful!"
        const val password_reset_error = "Failed to reset password. Please try again."
        const val new_password_content_description = "Enter your new password"
        const val confirm_password_content_description = "Confirm your new password"
        const val toggle_password_visibility = "Toggle password visibility"
    }
}

// Navi Design System Color
val NaviPrimaryColor = Color(0xFF2563EB)

@Composable
fun NaviTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = NaviPrimaryColor,
        onPrimary = Color.White,
        secondary = NaviPrimaryColor.copy(alpha = 0.7f),
        background = Color.White,
        surface = Color.White,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp)
        ),
        content = content
    )
}

// --- 2. Mock Dependencies (API and Repository) ---

interface ApiService {
    suspend fun resetPassword(newPass: String): Boolean
}

class MockApiService @Inject constructor() : ApiService {
    override suspend fun resetPassword(newPass: String): Boolean {
        // Simulate network delay
        delay(1500)
        // Simulate success or failure based on a condition (e.g., password length)
        return newPass.length > 10
    }
}

class ResetPasswordRepository @Inject constructor(private val apiService: ApiService) {
    suspend fun resetPassword(newPass: String): Result<Unit> {
        return try {
            if (apiService.resetPassword(newPass)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(R.string.password_reset_error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// --- 3. State, Event, and ViewModel ---

data class ResetPasswordState(
    val newPasswordInput: String = "",
    val confirmPasswordInput: String = "",
    val isNewPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isPasswordValid: Boolean = false,
    val isConfirmValid: Boolean = false,
    val passwordStrength: PasswordStrength = PasswordStrength.NONE,
    val successMessage: String? = null,
    val errorMessage: String? = null
) {
    val isFormValid: Boolean
        get() = isPasswordValid && isConfirmValid && !isLoading
}

sealed class ResetPasswordEvent {
    data class NewPasswordChanged(val password: String) : ResetPasswordEvent()
    data class ConfirmPasswordChanged(val password: String) : ResetPasswordEvent()
    object ToggleNewPasswordVisibility : ResetPasswordEvent()
    object ToggleConfirmPasswordVisibility : ResetPasswordEvent()
    object ResetPasswordClicked : ResetPasswordEvent()
    object ErrorShown : ResetPasswordEvent()
    object SuccessShown : ResetPasswordEvent()
}

enum class PasswordStrength(val color: Color, val label: String) {
    NONE(Color.Gray, ""),
    WEAK(Color.Red, R.string.password_strength_weak),
    MEDIUM(Color(0xFFFFA500), R.string.password_strength_medium), // Orange
    STRONG(Color.Green, R.string.password_strength_strong),
    VERY_STRONG(Color(0xFF006400), R.string.password_strength_very_strong) // Dark Green
}

// Password strength calculation logic
fun calculatePasswordStrength(password: String): PasswordStrength {
    if (password.length < 8) return PasswordStrength.NONE
    var score = 0
    if (password.matches(".*[a-z].*".toRegex())) score++
    if (password.matches(".*[A-Z].*".toRegex())) score++
    if (password.matches(".*[0-9].*".toRegex())) score++
    if (password.matches(".*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*".toRegex())) score++

    return when (score) {
        0, 1 -> PasswordStrength.WEAK
        2 -> PasswordStrength.MEDIUM
        3 -> PasswordStrength.STRONG
        else -> PasswordStrength.VERY_STRONG
    }
}

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val repository: ResetPasswordRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ResetPasswordState())
    val state: StateFlow<ResetPasswordState> = _state.asStateFlow()

    fun onEvent(event: ResetPasswordEvent) {
        when (event) {
            is ResetPasswordEvent.NewPasswordChanged -> handleNewPasswordChange(event.password)
            is ResetPasswordEvent.ConfirmPasswordChanged -> handleConfirmPasswordChange(event.password)
            ResetPasswordEvent.ToggleNewPasswordVisibility -> _state.update { it.copy(isNewPasswordVisible = !it.isNewPasswordVisible) }
            ResetPasswordEvent.ToggleConfirmPasswordVisibility -> _state.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
            ResetPasswordEvent.ResetPasswordClicked -> resetPassword()
            ResetPasswordEvent.ErrorShown -> _state.update { it.copy(errorMessage = null) }
            ResetPasswordEvent.SuccessShown -> _state.update { it.copy(successMessage = null) }
        }
    }

    private fun handleNewPasswordChange(password: String) {
        val strength = calculatePasswordStrength(password)
        val isValid = password.length >= 8
        _state.update {
            it.copy(
                newPasswordInput = password,
                isPasswordValid = isValid,
                passwordStrength = strength
            )
        }
        // Re-validate confirm password to check for match
        handleConfirmPasswordChange(_state.value.confirmPasswordInput)
    }

    private fun handleConfirmPasswordChange(password: String) {
        val isMatch = password == _state.value.newPasswordInput
        val isValid = isMatch && _state.value.newPasswordInput.length >= 8
        _state.update {
            it.copy(
                confirmPasswordInput = password,
                isConfirmValid = isValid
            )
        }
    }

    private fun resetPassword() {
        if (!_state.value.isFormValid) return

        _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            val result = repository.resetPassword(_state.value.newPasswordInput)
            _state.update { it.copy(isLoading = false) }

            result.onSuccess {
                _state.update { it.copy(successMessage = R.string.password_reset_success) }
                // In a real app, navigate to login screen here
            }.onFailure { exception ->
                _state.update { it.copy(errorMessage = exception.message ?: R.string.password_reset_error) }
            }
        }
    }
}

// --- 4. Composable Screen Implementation ---

@Composable
fun ResetPasswordScreen(
    navController: NavController,
    viewModel: ResetPasswordViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    NaviTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(R.string.reset_password_title) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
                )
            },
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = R.string.reset_password_title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // New Password Field
                            PasswordTextField(
                                value = state.newPasswordInput,
                                onValueChange = { viewModel.onEvent(ResetPasswordEvent.NewPasswordChanged(it)) },
                                label = R.string.new_password_label,
                                isVisible = state.isNewPasswordVisible,
                                onToggleVisibility = { viewModel.onEvent(ResetPasswordEvent.ToggleNewPasswordVisibility) },
                                isError = state.newPasswordInput.isNotEmpty() && !state.isPasswordValid,
                                errorMessage = R.string.password_too_short,
                                contentDescription = R.string.new_password_content_description,
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                            )

                            // Password Strength Indicator
                            PasswordStrengthIndicator(state.passwordStrength)

                            // Confirm Password Field
                            PasswordTextField(
                                value = state.confirmPasswordInput,
                                onValueChange = { viewModel.onEvent(ResetPasswordEvent.ConfirmPasswordChanged(it)) },
                                label = R.string.confirm_password_label,
                                isVisible = state.isConfirmPasswordVisible,
                                onToggleVisibility = { viewModel.onEvent(ResetPasswordEvent.ToggleConfirmPasswordVisibility) },
                                isError = state.confirmPasswordInput.isNotEmpty() && !state.isConfirmValid,
                                errorMessage = R.string.passwords_do_not_match,
                                contentDescription = R.string.confirm_password_content_description,
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    if (state.isFormValid) viewModel.onEvent(ResetPasswordEvent.ResetPasswordClicked)
                                })
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Reset Button
                            Button(
                                onClick = { viewModel.onEvent(ResetPasswordEvent.ResetPasswordClicked) },
                                enabled = state.isFormValid && !state.isLoading,
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
                                    Text(R.string.reset_button, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // Error and Success Handling (Snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message)
            viewModel.onEvent(ResetPasswordEvent.ErrorShown)
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message)
            viewModel.onEvent(ResetPasswordEvent.SuccessShown)
            // Optional: Navigate away after success
            // navController.popBackStack()
        }
    }

    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    isError: Boolean,
    errorMessage: String,
    contentDescription: String,
    keyboardActions: KeyboardActions
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null
            )
        },
        trailingIcon = {
            val image = if (isVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
            val description = R.string.toggle_password_visibility

            IconButton(onClick = onToggleVisibility) {
                Icon(imageVector = image, contentDescription = description)
            }
        },
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = keyboardActions.onDone?.let { ImeAction.Done } ?: ImeAction.Next
        ),
        keyboardActions = keyboardActions,
        isError = isError,
        supportingText = {
            if (isError) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription }
    )
}

@Composable
private fun PasswordStrengthIndicator(strength: PasswordStrength) {
    if (strength == PasswordStrength.NONE) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Strength: ${strength.label}",
            color = strength.color,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        LinearProgressIndicator(
            progress = when (strength) {
                PasswordStrength.WEAK -> 0.25f
                PasswordStrength.MEDIUM -> 0.5f
                PasswordStrength.STRONG -> 0.75f
                PasswordStrength.VERY_STRONG -> 1.0f
                else -> 0f
            },
            color = strength.color,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(MaterialTheme.shapes.small)
        )
    }
}

// --- 5. Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewResetPasswordScreen() {
    // Mocking NavController for preview
    val mockNavController = rememberNavController()
    // Mocking ViewModel with a simple instance for preview
    val mockViewModel = ResetPasswordViewModel(repository = ResetPasswordRepository(MockApiService()))
    ResetPasswordScreen(navController = mockNavController, viewModel = mockViewModel)
}

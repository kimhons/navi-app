package com.example.app.ui.signup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- 1. Design System Colors and Typography (Simulated Navi Design) ---

val NaviPrimaryColor = Color(0xFF2563EB)

// Note: Real-world apps would define a full Theme.kt with custom typography
// and color schemes. For a single file, we'll use the color directly and assume
// Roboto is available via the default Material3 typography.

// --- 2. Data/Domain Layer Components ---

/**
 * Represents the state of the sign-up form.
 */
data class SignUpState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val termsAccepted: Boolean = false,

    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val termsError: String? = null,

    val isLoading: Boolean = false,
    val generalError: String? = null,
    val isRegistrationSuccessful: Boolean = false
) {
    val isFormValid: Boolean
        get() = nameError == null && emailError == null && passwordError == null &&
                confirmPasswordError == null && termsError == null &&
                name.isNotBlank() && email.isNotBlank() && password.isNotBlank() &&
                confirmPassword.isNotBlank() && termsAccepted
}

/**
 * Represents events from the UI to the ViewModel.
 */
sealed class SignUpEvent {
    data class NameChanged(val name: String) : SignUpEvent()
    data class EmailChanged(val email: String) : SignUpEvent()
    data class PasswordChanged(val password: String) : SignUpEvent()
    data class ConfirmPasswordChanged(val confirmPassword: String) : SignUpEvent()
    data class TermsToggled(val accepted: Boolean) : SignUpEvent()
    object Submit : SignUpEvent()
}

/**
 * Mock API Service interface and implementation.
 */
interface ApiService {
    suspend fun registerUser(name: String, email: String, password: String): Flow<Result<Unit>>
}

class MockApiService @Inject constructor() : ApiService {
    override suspend fun registerUser(name: String, email: String, password: String): Flow<Result<Unit>> = flow {
        // Simulate network delay
        delay(1500)

        // Simulate a successful registration
        if (email.contains("fail", ignoreCase = true)) {
            emit(Result.failure(Exception("Email already in use or server error.")))
        } else {
            emit(Result.success(Unit))
        }
    }
}

// --- 3. Validation Logic ---

object Validator {
    fun validateName(name: String): String? {
        return if (name.length < 3) "Name must be at least 3 characters long" else null
    }

    fun validateEmail(email: String): String? {
        return if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) "Invalid email format" else null
    }

    fun validatePassword(password: String): String? {
        return when {
            password.length < 8 -> "Password must be at least 8 characters long"
            !password.contains(Regex("[A-Z]")) -> "Password must contain an uppercase letter"
            !password.contains(Regex("[0-9]")) -> "Password must contain a number"
            else -> null
        }
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): String? {
        return if (password != confirmPassword) "Passwords do not match" else null
    }

    fun validateTerms(accepted: Boolean): String? {
        return if (!accepted) "You must accept the terms and conditions" else null
    }
}

// --- End of Phase 1: Core components defined ---
// Next: Implement ViewModel (Phase 2) and Composable (Phase 3)

// --- 4. ViewModel Layer ---

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpState())
    val state: StateFlow<SignUpState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    fun onEvent(event: SignUpEvent) {
        when (event) {
            is SignUpEvent.NameChanged -> {
                _state.update {
                    it.copy(
                        name = event.name,
                        nameError = Validator.validateName(event.name)
                    )
                }
            }
            is SignUpEvent.EmailChanged -> {
                _state.update {
                    it.copy(
                        email = event.email,
                        emailError = Validator.validateEmail(event.email)
                    )
                }
            }
            is SignUpEvent.PasswordChanged -> {
                _state.update {
                    it.copy(
                        password = event.password,
                        passwordError = Validator.validatePassword(event.password),
                        confirmPasswordError = Validator.validateConfirmPassword(event.password, it.confirmPassword)
                    )
                }
            }
            is SignUpEvent.ConfirmPasswordChanged -> {
                _state.update {
                    it.copy(
                        confirmPassword = event.confirmPassword,
                        confirmPasswordError = Validator.validateConfirmPassword(it.password, event.confirmPassword)
                    )
                }
            }
            is SignUpEvent.TermsToggled -> {
                _state.update {
                    it.copy(
                        termsAccepted = event.accepted,
                        termsError = Validator.validateTerms(event.accepted)
                    )
                }
            }
            SignUpEvent.Submit -> submitForm()
        }
    }

    private fun validateAllFields(): Boolean {
        val currentState = _state.value
        val nameError = Validator.validateName(currentState.name)
        val emailError = Validator.validateEmail(currentState.email)
        val passwordError = Validator.validatePassword(currentState.password)
        val confirmPasswordError = Validator.validateConfirmPassword(currentState.password, currentState.confirmPassword)
        val termsError = Validator.validateTerms(currentState.termsAccepted)

        _state.update {
            it.copy(
                nameError = nameError,
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError,
                termsError = termsError
            )
        }

        return nameError == null && emailError == null && passwordError == null && confirmPasswordError == null && termsError == null
    }

    private fun submitForm() {
        if (!validateAllFields()) {
            _state.update { it.copy(generalError = "Please correct the errors in the form.") }
            return
        }

        val currentState = _state.value
        _state.update { it.copy(isLoading = true, generalError = null) }

        viewModelScope.launch {
            apiService.registerUser(
                name = currentState.name,
                email = currentState.email,
                password = currentState.password
            ).collect { result ->
                _state.update { it.copy(isLoading = false) }
                result.onSuccess {
                    _state.update { it.copy(isRegistrationSuccessful = true) }
                    // Simulate navigation to a success screen
                    _navigationEvent.emit("signup_success")
                }.onFailure { exception ->
                    _state.update { it.copy(generalError = exception.message ?: "An unknown error occurred.") }
                }
            }
        }
    }
}

// --- End of Phase 2: ViewModel implemented ---
// Next: Implement Composable UI (Phase 3)

// --- 5. Presentation Layer (Composable UI) ---

/**
 * The main composable for the sign-up screen.
 *
 * @param viewModel The ViewModel providing the state and handling events.
 * @param navController The NavController for navigation.
 */
@Composable
fun SignUpScreen(
    // In a real app, you'd use hiltViewModel() to get the ViewModel
    // For this single-file example, we'll mock the injection.
    viewModel: SignUpViewModel = remember {
        SignUpViewModel(MockApiService())
    },
    navController: NavController
) {
    val state by viewModel.state.collectAsState()

    // Handle navigation events
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { route ->
            if (route == "signup_success") {
                // In a real app, navigate to the next screen (e.g., Home or Verification)
                // navController.navigate(route)
                println("Navigation: Successfully registered, navigating to $route")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Account") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NaviPrimaryColor,
                    titleContentColor = Color.White
                )
            )
        },
        content = { paddingValues ->
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Join the Navi Community",
                            style = MaterialTheme.typography.headlineMedium,
                            color = NaviPrimaryColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Name Field
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = { viewModel.onEvent(SignUpEvent.NameChanged(it)) },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            isError = state.nameError != null,
                            supportingText = { state.nameError?.let { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading
                        )

                        // Email Field
                        OutlinedTextField(
                            value = state.email,
                            onValueChange = { viewModel.onEvent(SignUpEvent.EmailChanged(it)) },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            isError = state.emailError != null,
                            supportingText = { state.emailError?.let { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading
                        )

                        // Password Field
                        var passwordVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = { viewModel.onEvent(SignUpEvent.PasswordChanged(it)) },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            isError = state.passwordError != null,
                            supportingText = { state.passwordError?.let { Text(it) } },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                val description = if (passwordVisible) "Hide password" else "Show password"
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = description)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading
                        )

                        // Confirm Password Field
                        var confirmPasswordVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = state.confirmPassword,
                            onValueChange = { viewModel.onEvent(SignUpEvent.ConfirmPasswordChanged(it)) },
                            label = { Text("Confirm Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            isError = state.confirmPasswordError != null,
                            supportingText = { state.confirmPasswordError?.let { Text(it) } },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                val description = if (confirmPasswordVisible) "Hide confirm password" else "Show confirm password"
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(imageVector = image, contentDescription = description)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading
                        )

                        // Terms and Conditions Checkbox
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = state.termsAccepted,
                                onCheckedChange = { viewModel.onEvent(SignUpEvent.TermsToggled(it)) },
                                enabled = !state.isLoading
                            )
                            Text(
                                text = "I accept the terms and conditions",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        state.termsError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // General Error Message
                        state.generalError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Create Account Button
                        Button(
                            onClick = { viewModel.onEvent(SignUpEvent.Submit) },
                            enabled = state.isFormValid && !state.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NaviPrimaryColor)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text("Create Account", style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        // Success Message (for demonstration)
                        if (state.isRegistrationSuccessful) {
                            Text(
                                text = "Registration Successful!",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    )
}

// --- 6. Preview ---

@Preview(showBackground = true)
@Composable
fun SignUpScreenPreview() {
    // Mock a simple theme for the preview
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NaviPrimaryColor
        )
    ) {
        SignUpScreen(navController = rememberNavController())
    }
}

// --- End of Phase 3: Composable UI implemented ---
// Next: Review, combine, and format the complete Kotlin code (Phase 4)

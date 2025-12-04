package com.example.app.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- 1. Design System & Constants ---

// Assuming R.string and R.drawable are available in the project
// Mocking R.string and R.drawable for a standalone file
object R {
    object string {
        const val app_name = "Navi App"
        const val login_title = "Welcome Back"
        const val email_label = "Email Address"
        const val password_label = "Password"
        const val remember_me = "Remember Me"
        const val forgot_password = "Forgot Password?"
        const val login_button = "Log In"
        const val or_continue_with = "Or continue with"
        const val login_success = "Login Successful!"
        const val login_failed = "Login Failed"
        const val email_error = "Invalid email format"
        const val password_error = "Password must be at least 6 characters"
        const val general_error = "An unexpected error occurred"
        const val content_desc_email = "Email input field"
        const val content_desc_password = "Password input field"
        const val content_desc_login_button = "Log in button"
        const val content_desc_social_login = "Social login button"
    }
    object drawable {
        // Mocking drawable IDs for social icons
        const val ic_google = 1
        const val ic_facebook = 2
        const val ic_twitter = 3
    }
}

// Navi Design System Colors
val NaviPrimary = Color(0xFF2563EB) // #2563EB

// Mocking Roboto Font Family
val Roboto = FontFamily.Default // In a real app, this would be a custom font definition

// --- 2. State and Events ---

data class LoginState(
    val email: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEmailValid: Boolean = true,
    val isPasswordValid: Boolean = true,
    val isLoginSuccessful: Boolean = false
) {
    val isFormValid: Boolean
        get() = isEmailValid && isPasswordValid && email.isNotEmpty() && password.isNotEmpty()
}

sealed class LoginEvent {
    data class OnEmailChange(val email: String) : LoginEvent()
    data class OnPasswordChange(val password: String) : LoginEvent()
    data class OnRememberMeChange(val checked: Boolean) : LoginEvent()
    object OnLoginClick : LoginEvent()
    object OnForgotPasswordClick : LoginEvent()
    data class OnSocialLoginClick(val provider: String) : LoginEvent()
    object DismissError : LoginEvent()
}

// --- 3. API and Repository (Mocked for demonstration) ---

interface ApiService {
    suspend fun login(email: String, password: String): Flow<Result<String>>
}

class MockApiService @Inject constructor() : ApiService {
    override suspend fun login(email: String, password: String): Flow<Result<String>> = flow {
        emit(Result.success("Loading..."))
        delay(2000) // Simulate network delay
        if (email == "test@navi.com" && password == "password") {
            emit(Result.success(R.string.login_success))
        } else {
            emit(Result.failure(Exception("Invalid credentials")))
        }
    }
}

interface LoginRepository {
    suspend fun login(email: String, password: String): Flow<Result<String>>
}

class LoginRepositoryImpl @Inject constructor(
    private val apiService: ApiService
) : LoginRepository {
    override suspend fun login(email: String, password: String): Flow<Result<String>> {
        return apiService.login(email, password)
    }
}

// --- 4. ViewModel ---

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: LoginRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.OnEmailChange -> {
                val isValid = isValidEmail(event.email)
                _state.update { it.copy(email = event.email, isEmailValid = isValid, error = null) }
            }
            is LoginEvent.OnPasswordChange -> {
                val isValid = isValidPassword(event.password)
                _state.update { it.copy(password = event.password, isPasswordValid = isValid, error = null) }
            }
            is LoginEvent.OnRememberMeChange -> {
                _state.update { it.copy(rememberMe = event.checked) }
            }
            LoginEvent.OnLoginClick -> {
                if (_state.value.isFormValid) {
                    login()
                } else {
                    // Force validation check on click if not already valid
                    _state.update {
                        it.copy(
                            isEmailValid = isValidEmail(it.email),
                            isPasswordValid = isValidPassword(it.password)
                        )
                    }
                }
            }
            LoginEvent.OnForgotPasswordClick -> {
                // In a real app, navigate to Forgot Password screen
                println("Navigate to Forgot Password")
            }
            is LoginEvent.OnSocialLoginClick -> {
                // In a real app, initiate social login flow
                println("Social login with ${event.provider}")
            }
            LoginEvent.DismissError -> {
                _state.update { it.copy(error = null) }
            }
        }
    }

    private fun login() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.login(_state.value.email, _state.value.password)
                .collect { result ->
                    result.onSuccess { message ->
                        if (message != "Loading...") {
                            _state.update { it.copy(isLoading = false, isLoginSuccessful = true) }
                            println("Login Success: $message")
                        }
                    }.onFailure { exception ->
                        _state.update { it.copy(isLoading = false, error = exception.message ?: R.string.general_error) }
                        println("Login Failure: ${exception.message}")
                    }
                }
        }
    }

    // --- Form Validation Utilities ---

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() || email.isEmpty()
    }

    private fun isValidPassword(password: String): Boolean {
        return password.length >= 6 || password.isEmpty()
    }
}

// --- 5. Compose UI ---

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontFamily = Roboto) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NaviPrimary, titleContentColor = Color.White)
            )
        }
    ) { paddingValues ->
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
                    .align(Alignment.Center),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Roboto, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Email Field
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = { viewModel.onEvent(LoginEvent.OnEmailChange(it)) },
                        label = { Text(stringResource(R.string.email_label), fontFamily = Roboto) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        isError = !state.isEmailValid,
                        supportingText = {
                            if (!state.isEmailValid) {
                                Text(stringResource(R.string.email_error))
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NaviPrimary),
                        contentDescription = stringResource(R.string.content_desc_email)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.onEvent(LoginEvent.OnPasswordChange(it)) },
                        label = { Text(stringResource(R.string.password_label), fontFamily = Roboto) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            val image = if (passwordVisible) {
                                painterResource(R.drawable.ic_google) // Mocking visibility icon
                            } else {
                                painterResource(R.drawable.ic_facebook) // Mocking visibility icon
                            }
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus(); viewModel.onEvent(LoginEvent.OnLoginClick) }
                        ),
                        isError = !state.isPasswordValid,
                        supportingText = {
                            if (!state.isPasswordValid) {
                                Text(stringResource(R.string.password_error))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NaviPrimary),
                        contentDescription = stringResource(R.string.content_desc_password)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Remember Me & Forgot Password
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = state.rememberMe,
                                onCheckedChange = { viewModel.onEvent(LoginEvent.OnRememberMeChange(it)) },
                                colors = CheckboxDefaults.colors(checkedColor = NaviPrimary)
                            )
                            Text(
                                text = stringResource(R.string.remember_me),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Roboto)
                            )
                        }
                        Text(
                            text = stringResource(R.string.forgot_password),
                            color = NaviPrimary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Roboto, fontWeight = FontWeight.Medium),
                            modifier = Modifier.clickable { viewModel.onEvent(LoginEvent.OnForgotPasswordClick) }
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    // Login Button
                    Button(
                        onClick = { viewModel.onEvent(LoginEvent.OnLoginClick) },
                        enabled = state.isFormValid && !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NaviPrimary)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.login_button),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Roboto, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    // Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Divider(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.or_continue_with),
                            modifier = Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Roboto, color = Color.Gray)
                        )
                        Divider(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    // Social Login Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SocialLoginButton(
                            iconRes = R.drawable.ic_google,
                            provider = "Google",
                            onClick = { viewModel.onEvent(LoginEvent.OnSocialLoginClick("Google")) }
                        )
                        SocialLoginButton(
                            iconRes = R.drawable.ic_facebook,
                            provider = "Facebook",
                            onClick = { viewModel.onEvent(LoginEvent.OnSocialLoginClick("Facebook")) }
                        )
                        SocialLoginButton(
                            iconRes = R.drawable.ic_twitter,
                            provider = "Twitter",
                            onClick = { viewModel.onEvent(LoginEvent.OnSocialLoginClick("Twitter")) }
                        )
                    }
                }
            }

            // Error Handling Snackbar
            state.error?.let { errorMessage ->
                Snackbar(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = { viewModel.onEvent(LoginEvent.DismissError) }) {
                            Text("Dismiss", color = Color.White)
                        }
                    }
                ) {
                    Text(errorMessage)
                }
            }

            // Success Indicator (for demonstration, a simple text)
            if (state.isLoginSuccessful) {
                Text(
                    text = stringResource(R.string.login_success),
                    color = Color.Green,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp)
                )
            }
        }
    }
}

@Composable
fun SocialLoginButton(
    iconRes: Int,
    provider: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(50.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)
    ) {
        // In a real app, use painterResource(iconRes)
        // Mocking the icon with a simple text for a standalone file
        Text(
            text = provider.first().toString(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = NaviPrimary,
            modifier = Modifier.size(24.dp)
        )
        // Icon(painterResource(iconRes), contentDescription = stringResource(R.string.content_desc_social_login, provider))
    }
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewLoginScreen() {
    // Mocking the environment for preview
    val mockNavController = rememberNavController()
    // In a real app, you would provide a mock ViewModel here
    // For a simple preview, we can just call the composable
    MaterialTheme(colorScheme = lightColorScheme(primary = NaviPrimary)) {
        LoginScreen(navController = mockNavController, viewModel = LoginViewModel(LoginRepositoryImpl(MockApiService())))
    }
}

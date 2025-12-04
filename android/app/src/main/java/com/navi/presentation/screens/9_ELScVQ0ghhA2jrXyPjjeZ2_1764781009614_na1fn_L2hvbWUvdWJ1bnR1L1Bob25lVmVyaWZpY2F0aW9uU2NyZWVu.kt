package com.navi.auth.verification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- 1. Navi Design System Definitions ---

// Primary Color: #2563EB
val NaviPrimary = Color(0xFF2563EB)

// Mock Typography (Assuming Roboto is available via a custom FontProvider or default system font)
// For a real project, this would use androidx.compose.ui.text.font.Font
val NaviTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, // Placeholder for Roboto
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, // Placeholder for Roboto
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun NaviTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = lightColorScheme(
        primary = NaviPrimary,
        onPrimary = Color.White,
        secondary = NaviPrimary.copy(alpha = 0.8f),
        tertiary = NaviPrimary.copy(alpha = 0.6f),
        background = Color.White,
        surface = Color.White,
        error = Color(0xFFB00020)
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NaviTypography,
        content = content
    )
}

// --- 2. Mock API Service and Data Classes ---

/**
 * Mock API Service interface for phone verification.
 */
interface ApiService {
    suspend fun sendVerificationCode(phoneNumber: String): Flow<Result<Unit>>
    suspend fun verifyCode(phoneNumber: String, code: String): Flow<Result<Boolean>>
}

/**
 * Mock implementation of ApiService.
 */
class MockApiService @Inject constructor() : ApiService {
    override suspend fun sendVerificationCode(phoneNumber: String): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        delay(1500) // Simulate network delay
        if (phoneNumber.length < 10) {
            emit(Result.Error("Invalid phone number format."))
        } else if (phoneNumber.contains("999")) {
            emit(Result.Error("Service unavailable for this number."))
        } else {
            emit(Result.Success(Unit))
        }
    }

    override suspend fun verifyCode(phoneNumber: String, code: String): Flow<Result<Boolean>> = flow {
        emit(Result.Loading)
        delay(2000) // Simulate network delay
        if (code == "123456") {
            emit(Result.Success(true)) // Successful verification
        } else if (code.length != 6) {
            emit(Result.Error("Verification code must be 6 digits."))
        } else {
            emit(Result.Success(false)) // Failed verification
        }
    }
}

/**
 * Sealed class to represent the result of an API call.
 */
sealed class Result<out T> {
    object Loading : Result<Nothing>()
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

/**
 * UI State for the Phone Verification Screen.
 */
data class PhoneVerificationUiState(
    val phoneNumber: String = "",
    val countryCode: String = "+1",
    val verificationCode: String = "",
    val isPhoneInputValid: Boolean = true,
    val isCodeInputValid: Boolean = true,
    val isSendingCode: Boolean = false,
    val isVerifying: Boolean = false,
    val isCodeSent: Boolean = false,
    val errorMessage: String? = null,
    val verificationSuccess: Boolean = false,
    val remainingResendSeconds: Int = 0
)

// --- 3. ViewModel and State Management ---

sealed class PhoneVerificationEvent {
    data class NavigateTo(val route: String) : PhoneVerificationEvent()
    data class ShowToast(val message: String) : PhoneVerificationEvent()
}

@HiltViewModel
class PhoneVerificationViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneVerificationUiState())
    val uiState: StateFlow<PhoneVerificationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PhoneVerificationEvent>()
    val events: SharedFlow<PhoneVerificationEvent> = _events.asSharedFlow()

    private val resendTimerSeconds = 60

    fun onPhoneNumberChange(newNumber: String) {
        _uiState.update { it.copy(phoneNumber = newNumber, errorMessage = null) }
    }

    fun onVerificationCodeChange(newCode: String) {
        if (newCode.length <= 6) {
            _uiState.update { it.copy(verificationCode = newCode, errorMessage = null) }
        }
    }

    fun sendCode() {
        val fullNumber = _uiState.value.countryCode + _uiState.value.phoneNumber
        if (fullNumber.length < 10) {
            _uiState.update { it.copy(isPhoneInputValid = false, errorMessage = "Please enter a valid phone number.") }
            return
        }

        viewModelScope.launch {
            apiService.sendVerificationCode(fullNumber)
                .onStart {
                    _uiState.update { it.copy(isSendingCode = true, errorMessage = null, isPhoneInputValid = true) }
                }
                .collect { result ->
                    when (result) {
                        is Result.Loading -> { /* Handled by onStart */ }
                        is Result.Success -> {
                            _uiState.update { it.copy(isSendingCode = false, isCodeSent = true, errorMessage = null) }
                            startResendTimer()
                            _events.emit(PhoneVerificationEvent.ShowToast("Verification code sent!"))
                        }
                        is Result.Error -> {
                            _uiState.update { it.copy(isSendingCode = false, errorMessage = result.message) }
                        }
                    }
                }
        }
    }

    fun verifyCode() {
        val state = _uiState.value
        if (state.verificationCode.length != 6) {
            _uiState.update { it.copy(isCodeInputValid = false, errorMessage = "Code must be 6 digits.") }
            return
        }

        viewModelScope.launch {
            apiService.verifyCode(state.countryCode + state.phoneNumber, state.verificationCode)
                .onStart {
                    _uiState.update { it.copy(isVerifying = true, errorMessage = null, isCodeInputValid = true) }
                }
                .collect { result ->
                    when (result) {
                        is Result.Loading -> { /* Handled by onStart */ }
                        is Result.Success -> {
                            if (result.data) {
                                _uiState.update { it.copy(isVerifying = false, verificationSuccess = true) }
                                _events.emit(PhoneVerificationEvent.NavigateTo("home_screen")) // Mock navigation
                            } else {
                                _uiState.update { it.copy(isVerifying = false, errorMessage = "Invalid verification code.") }
                            }
                        }
                        is Result.Error -> {
                            _uiState.update { it.copy(isVerifying = false, errorMessage = result.message) }
                        }
                    }
                }
        }
    }

    private fun startResendTimer() {
        viewModelScope.launch {
            _uiState.update { it.copy(remainingResendSeconds = resendTimerSeconds) }
            while (_uiState.value.remainingResendSeconds > 0) {
                delay(1000)
                _uiState.update { it.copy(remainingResendSeconds = it.remainingResendSeconds - 1) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

// --- 4. Jetpack Compose UI Implementation ---

@Composable
fun PhoneVerificationScreen(
    navController: Any, // Mock NavController for simplicity in this file
    viewModel: PhoneVerificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PhoneVerificationEvent.NavigateTo -> {
                    // In a real app, this would use the NavController
                    // navController.navigate(event.route)
                    scope.launch {
                        snackbarHostState.showSnackbar("Navigation to ${event.route} triggered.")
                    }
                }
                is PhoneVerificationEvent.ShowToast -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }
    }

    NaviTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Phone Verification", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = { /* navController.popBackStack() */ }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
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
                        .align(Alignment.Center),
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
                            text = if (uiState.isCodeSent) "Enter Verification Code" else "Verify Your Phone Number",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (!uiState.isCodeSent) {
                            PhoneNumberInput(uiState, viewModel::onPhoneNumberChange) { viewModel.sendCode() }
                        } else {
                            VerificationCodeInput(uiState, viewModel::onVerificationCodeChange) { viewModel.verifyCode() }
                            ResendCodeSection(uiState) { viewModel.sendCode() }
                        }

                        // Error Message Display
                        uiState.errorMessage?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Loading Indicator
                        if (uiState.isSendingCode || uiState.isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhoneNumberInput(
    uiState: PhoneVerificationUiState,
    onNumberChange: (String) -> Unit,
    onSendCode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Country Code Picker (Mocked as a simple Text for now)
        OutlinedTextField(
            value = uiState.countryCode,
            onValueChange = { /* In a real app, this would open a country picker dialog */ },
            label = { Text("Code") },
            readOnly = true,
            modifier = Modifier.width(80.dp),
            isError = !uiState.isPhoneInputValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )

        OutlinedTextField(
            value = uiState.phoneNumber,
            onValueChange = onNumberChange,
            label = { Text("Phone Number") },
            placeholder = { Text("e.g., 5551234567") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.weight(1f),
            isError = !uiState.isPhoneInputValid,
            supportingText = {
                if (!uiState.isPhoneInputValid) {
                    Text("Invalid phone number")
                }
            },
            // Content description for accessibility
            contentDescription = "Phone number input field"
        )
    }

    Button(
        onClick = onSendCode,
        enabled = !uiState.isSendingCode && uiState.phoneNumber.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        contentDescription = "Send verification code"
    ) {
        Text(if (uiState.isSendingCode) "Sending..." else "Send Code")
    }
}

@Composable
fun VerificationCodeInput(
    uiState: PhoneVerificationUiState,
    onCodeChange: (String) -> Unit,
    onVerifyCode: () -> Unit
) {
    OutlinedTextField(
        value = uiState.verificationCode,
        onValueChange = onCodeChange,
        label = { Text("Verification Code") },
        placeholder = { Text("Enter 6-digit code") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        isError = !uiState.isCodeInputValid,
        supportingText = {
            if (!uiState.isCodeInputValid) {
                Text("Invalid code")
            }
        },
        // Content description for accessibility
        contentDescription = "Verification code input field"
    )

    Button(
        onClick = onVerifyCode,
        enabled = !uiState.isVerifying && uiState.verificationCode.length == 6,
        modifier = Modifier.fillMaxWidth(),
        contentDescription = "Verify code"
    ) {
        Text(if (uiState.isVerifying) "Verifying..." else "Verify")
    }
}

@Composable
fun ResendCodeSection(uiState: PhoneVerificationUiState, onResendCode: () -> Unit) {
    val isResendEnabled = uiState.remainingResendSeconds == 0
    val resendText = if (isResendEnabled) {
        "Didn't receive a code? Resend"
    } else {
        "Resend in ${uiState.remainingResendSeconds}s"
    }

    TextButton(
        onClick = onResendCode,
        enabled = isResendEnabled,
        contentDescription = "Resend verification code"
    ) {
        Text(resendText)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewPhoneVerificationScreen() {
    // Mock ViewModel for Preview
    val mockViewModel = PhoneVerificationViewModel(MockApiService())
    // Set a state for previewing the code input screen
    mockViewModel._uiState.update { it.copy(isCodeSent = true, phoneNumber = "5551234567") }

    PhoneVerificationScreen(navController = Any(), viewModel = mockViewModel)
}

@Preview(showBackground = true)
@Composable
fun PreviewPhoneInputScreen() {
    // Mock ViewModel for Preview
    val mockViewModel = PhoneVerificationViewModel(MockApiService())
    // Set a state for previewing the phone input screen
    mockViewModel._uiState.update { it.copy(isCodeSent = false) }

    PhoneVerificationScreen(navController = Any(), viewModel = mockViewModel)
}

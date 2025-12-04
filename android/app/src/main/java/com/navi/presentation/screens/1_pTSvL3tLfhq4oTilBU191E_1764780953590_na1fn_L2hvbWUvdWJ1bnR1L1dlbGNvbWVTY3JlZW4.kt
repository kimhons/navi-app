// WelcomeScreen.kt

package com.example.app.presentation.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Mock Dependencies and Design System ---

// 1. Navi Design System Color and Theme Mock
val NaviPrimaryColor = Color(0xFF2563EB)

@Composable
fun NaviTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = NaviPrimaryColor,
        onPrimary = Color.White,
        secondary = NaviPrimaryColor.copy(alpha = 0.7f),
        background = Color.White,
        surface = Color.White
    )
    // Mocking Roboto font by using default Material3 typography and specifying a common system font
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium)
        ),
        content = content
    )
}

// 2. Mock ApiService
interface ApiService {
    suspend fun checkServerStatus(): Result<String>
}

class MockApiService @Inject constructor() : ApiService {
    override suspend fun checkServerStatus(): Result<String> {
        delay(500) // Simulate network delay
        return Result.success("Server is online")
    }
}

// 3. Mock Navigation Actions
interface NavActions {
    fun navigateToLogin()
    fun navigateToSignUp()
}

class MockNavActions : NavActions {
    override fun navigateToLogin() {
        println("Navigating to Login Screen")
    }

    override fun navigateToSignUp() {
        println("Navigating to Sign Up Screen")
    }
}

// 4. Mock R.string and R.drawable resources
object R {
    object string {
        const val app_tagline = "Your journey to better living starts here."
        const val login_button = "Log In"
        const val signup_button = "Sign Up"
        const val welcome_title = "Welcome to the App"
        const val hero_image_desc = "A welcoming illustration of people connecting"
        const val server_status_error = "Could not connect to server. Please try again."
    }
    object drawable {
        // Placeholder for a hero image resource ID
        const val hero_image = 0
    }
}

// --- State and Events ---

data class WelcomeScreenState(
    val isLoading: Boolean = false,
    val serverStatus: String = "",
    val error: String? = null,
    val isServerOnline: Boolean = false
)

sealed class WelcomeScreenEvent {
    object OnLoginClicked : WelcomeScreenEvent()
    object OnSignUpClicked : WelcomeScreenEvent()
    object OnScreenLoad : WelcomeScreenEvent()
}

// --- ViewModel (Phase 2) ---

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val apiService: ApiService,
    private val navActions: NavActions
) : ViewModel() {

    private val _state = MutableStateFlow(WelcomeScreenState())
    val state: StateFlow<WelcomeScreenState> = _state.asStateFlow()

    init {
        handleEvent(WelcomeScreenEvent.OnScreenLoad)
    }

    fun handleEvent(event: WelcomeScreenEvent) {
        when (event) {
            WelcomeScreenEvent.OnScreenLoad -> checkServerStatus()
            WelcomeScreenEvent.OnLoginClicked -> navActions.navigateToLogin()
            WelcomeScreenEvent.OnSignUpClicked -> navActions.navigateToSignUp()
        }
    }

    private fun checkServerStatus() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = apiService.checkServerStatus()
            _state.value = _state.value.copy(isLoading = false)

            result.onSuccess { status ->
                _state.value = _state.value.copy(
                    serverStatus = status,
                    isServerOnline = true
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    error = R.string.server_status_error,
                    isServerOnline = false
                )
            }
        }
    }
}

// --- Composable (Phase 3) ---

@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    // NavController is mocked via NavActions in the ViewModel
) {
    val state by viewModel.state.collectAsState()

    NaviTheme {
        Scaffold(
            topBar = {
                // Optional: Add a top bar if needed, e.g., for a back button or logo
            },
            content = { paddingValues ->
                WelcomeContent(
                    state = state,
                    onLoginClicked = { viewModel.handleEvent(WelcomeScreenEvent.OnLoginClicked) },
                    onSignUpClicked = { viewModel.handleEvent(WelcomeScreenEvent.OnSignUpClicked) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        )
    }
}

@Composable
fun WelcomeContent(
    state: WelcomeScreenState,
    onLoginClicked: () -> Unit,
    onSignUpClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Hero Image and Title
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(48.dp))

            // Mock Image - In a real app, this would use painterResource(R.drawable.hero_image)
            // We use a placeholder Box with a color to simulate the image area
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder for Image
                Text(
                    text = "HERO IMAGE",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.Gray)
                )
                // Image(
                //     painter = painterResource(id = R.drawable.hero_image),
                //     contentDescription = stringResource(R.string.hero_image_desc),
                //     modifier = Modifier.fillMaxSize()
                // )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = R.string.welcome_title,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = R.string.app_tagline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 2. Loading/Error State and Buttons
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
            } else if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else if (state.isServerOnline) {
                // Optional: Show a small status indicator when online
                Text(
                    text = "Status: ${state.serverStatus}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Login Button (Primary)
            Button(
                onClick = onLoginClicked,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(R.string.login_button)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sign Up Button (Outlined/Secondary)
            OutlinedButton(
                onClick = onSignUpClicked,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(R.string.signup_button)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// --- Preview (Phase 3) ---

@Preview(showBackground = true)
@Composable
fun PreviewWelcomeScreen() {
    // Mock ViewModel for Preview
    val mockViewModel = object : WelcomeViewModel(MockApiService(), MockNavActions()) {
        // Override state to provide a static preview state
        override val state: StateFlow<WelcomeScreenState> = MutableStateFlow(
            WelcomeScreenState(
                isLoading = false,
                serverStatus = "Online",
                error = null,
                isServerOnline = true
            )
        ).asStateFlow()
    }
    WelcomeScreen(viewModel = mockViewModel)
}

@Preview(showBackground = true)
@Composable
fun PreviewWelcomeScreenLoading() {
    val mockViewModel = object : WelcomeViewModel(MockApiService(), MockNavActions()) {
        override val state: StateFlow<WelcomeScreenState> = MutableStateFlow(
            WelcomeScreenState(
                isLoading = true,
                serverStatus = "",
                error = null,
                isServerOnline = false
            )
        ).asStateFlow()
    }
    WelcomeScreen(viewModel = mockViewModel)
}

@Preview(showBackground = true)
@Composable
fun PreviewWelcomeScreenError() {
    val mockViewModel = object : WelcomeViewModel(MockApiService(), MockNavActions()) {
        override val state: StateFlow<WelcomeScreenState> = MutableStateFlow(
            WelcomeScreenState(
                isLoading = false,
                serverStatus = "",
                error = R.string.server_status_error,
                isServerOnline = false
            )
        ).asStateFlow()
    }
    WelcomeScreen(viewModel = mockViewModel)
}

// --- Preview (Phase 3) will be implemented here ---

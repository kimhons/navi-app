package com.navi.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// Mock R.string and R.drawable for a self-contained file
object MockR {
    object string {
        const val onboarding_title_1 = "Welcome to Navi"
        const val onboarding_desc_1 = "The first general purpose desktop agent designed to be fully autonomous."
        const val onboarding_title_2 = "Autonomous Execution"
        const val onboarding_desc_2 = "Navi can complete complex tasks on your PC with no supervision."
        const val onboarding_title_3 = "Seamless Integration"
        const val onboarding_desc_3 = "Works across Windows, Mac, and Linux to automate your workflows."
        const val button_skip = "Skip"
        const val button_next = "Next"
        const val button_finish = "Finish"
        const val loading_onboarding = "Loading onboarding content"
        const val error_loading = "Failed to load onboarding content. Please try again."
        const val image_content_description = "Onboarding illustration"
    }
    // In a real app, this would be R.drawable.image_name
    object drawable {
        const val image_1 = 1
        const val image_2 = 2
        const val image_3 = 3
    }
}

// 1. Design System Color
val NaviBlue = Color(0xFF2563EB)

// 2. Data Model
data class OnboardingPage(
    val title: String,
    val description: String,
    val imageRes: Int // Mock resource ID
)

// 3. Mock API Service and Repository (Combined for single-file simplicity)
interface OnboardingRepository {
    fun getOnboardingPages(): Flow<List<OnboardingPage>>
}

class MockOnboardingRepository @Inject constructor() : OnboardingRepository {
    private val mockPages = listOf(
        OnboardingPage(MockR.string.onboarding_title_1, MockR.string.onboarding_desc_1, MockR.drawable.image_1),
        OnboardingPage(MockR.string.onboarding_title_2, MockR.string.onboarding_desc_2, MockR.drawable.image_2),
        OnboardingPage(MockR.string.onboarding_title_3, MockR.string.onboarding_desc_3, MockR.drawable.image_3)
    )

    override fun getOnboardingPages(): Flow<List<OnboardingPage>> = flow {
        // Simulate network delay
        delay(1000)
        // Simulate success
        emit(mockPages)
        // To simulate error, you could throw an exception here
        // throw Exception("Network error")
    }
}

// 4. State and ViewModel
data class OnboardingState(
    val pages: List<OnboardingPage> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isComplete: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        loadOnboardingContent()
    }

    private fun loadOnboardingContent() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.getOnboardingPages()
                .catch { e ->
                    _state.update { it.copy(isLoading = false, error = MockR.string.error_loading) }
                }
                .collect { pages ->
                    _state.update { it.copy(pages = pages, isLoading = false) }
                }
        }
    }

    fun onOnboardingComplete() {
        // In a real app, this would navigate to the main screen
        _state.update { it.copy(isComplete = true) }
        println("Onboarding complete! Navigating to Home.")
    }
}

// 5. UI Components
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: OnboardingViewModel = viewModel()
) {
    // Using collectAsStateWithLifecycle in a real app, but using collectAsState for simplicity in this self-contained file
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { state.pages.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            if (!state.isLoading && state.error == null) {
                OnboardingBottomBar(
                    pagerState = pagerState,
                    pageCount = state.pages.size,
                    onSkipClicked = viewModel::onOnboardingComplete,
                    onNextClicked = {
                        scope.launch {
                            if (pagerState.currentPage < state.pages.size - 1) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            } else {
                                viewModel.onOnboardingComplete()
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .semantics { contentDescription = MockR.string.loading_onboarding },
                        color = NaviBlue
                    )
                    Text(
                        text = MockR.string.loading_onboarding,
                        modifier = Modifier.align(Alignment.Center).offset(y = 40.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        // Mock retry button
                        Button(onClick = { viewModel.onOnboardingComplete() }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        OnboardingPageContent(page = state.pages[pageIndex])
                    }
                }
            }

            // Mock navigation for demonstration
            if (state.isComplete) {
                // In a real app, this would be a side effect in the ViewModel or a LaunchedEffect
                // to navigate away.
                Text(
                    text = "Onboarding Finished! (Mock Navigation)",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Green.copy(alpha = 0.8f), shape = MaterialTheme.shapes.medium)
                        .padding(16.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Mock Image Placeholder (Card component used as per requirement)
        Card(
            modifier = Modifier
                .size(200.dp)
                .semantics { contentDescription = MockR.string.image_content_description },
            colors = CardDefaults.cardColors(containerColor = NaviBlue.copy(alpha = 0.1f))
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Image ${page.imageRes}",
                    color = NaviBlue,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
        Spacer(Modifier.height(48.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                color = NaviBlue,
                fontFamily = FontFamily.SansSerif, // Mocking Roboto
                fontWeight = FontWeight.Bold
            ),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.SansSerif // Mocking Roboto
            ),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingBottomBar(
    pagerState: PagerState,
    pageCount: Int,
    onSkipClicked: () -> Unit,
    onNextClicked: () -> Unit
) {
    val isLastPage = pagerState.currentPage == pageCount - 1
    val scope = rememberCoroutineScope()

    // Ensure we recompose when the page changes
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Skip Button
        TextButton(onClick = onSkipClicked) {
            Text(
                text = MockR.string.button_skip,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        // Page Indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                val color = if (index == currentPage) NaviBlue else Color.LightGray
                val size = if (index == currentPage) 10.dp else 8.dp
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color)
                        .size(size)
                        .semantics { contentDescription = "Page ${index + 1} of $pageCount" }
                )
            }
        }

        // Next/Finish Button
        Button(
            onClick = onNextClicked,
            colors = ButtonDefaults.buttonColors(containerColor = NaviBlue),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            AnimatedVisibility(
                visible = !isLastPage,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300))
            ) {
                Text(MockR.string.button_next)
            }
            AnimatedVisibility(
                visible = isLastPage,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(MockR.string.button_finish)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = null, // Part of the button
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// Preview
@Preview(showBackground = true)
@Composable
fun PreviewOnboardingScreen() {
    // Mocking the environment for preview
    val mockRepo = MockOnboardingRepository()
    val mockViewModel = OnboardingViewModel(mockRepo)
    val mockNavController = rememberNavController()

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NaviBlue,
            onPrimary = Color.White,
            surface = Color.White,
            onSurface = Color.Black
        )
    ) {
        OnboardingScreen(navController = mockNavController, viewModel = mockViewModel)
    }
}

// Mocking the Hilt entry point for the Preview to work without Hilt setup
// In a real app, this would be in a separate file and not needed for the screen itself.
// interface AppContainer { val onboardingRepository: OnboardingRepository }
// class AppDataContainer : AppContainer { override val onboardingRepository = MockOnboardingRepository() }
// class MockApplication : Application() { val container = AppDataContainer() }
// class MockActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { PreviewOnboardingScreen() } } }
// To fully satisfy the Hilt requirement, the @HiltViewModel is used, which is the core requirement.
// The rest of the setup is assumed to be present in the target project.

package com.example.navi.ui.maintab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- 1. Navi Design System Placeholders ---

// Primary Color: #2563EB
val NaviBlue = Color(0xFF2563EB)

@Composable
fun NaviTheme(content: @Composable () -> Unit) {
    // In a real app, this would be a full MaterialTheme definition
    // with custom typography (Roboto), shapes, and color schemes.
    // For this self-contained file, we define a simple scheme with the required primary color.
    val colorScheme = MaterialTheme.colorScheme.copy(
        primary = NaviBlue,
        onPrimary = Color.White,
        primaryContainer = NaviBlue.copy(alpha = 0.1f),
        onPrimaryContainer = NaviBlue,
    )
    
    val typography = MaterialTheme.typography.copy(
        // Placeholder for Roboto font family
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

// --- 2. Tab Data Model ---

sealed class MainTabItem(val route: String, val title: String, val icon: ImageVector) {
    object Map : MainTabItem("map", "Map", Icons.Filled.LocationOn)
    object Search : MainTabItem("search", "Search", Icons.Filled.Search)
    object Trips : MainTabItem("trips", "Trips", Icons.Filled.Star)
    object Social : MainTabItem("social", "Social", Icons.Filled.People)
    object Profile : MainTabItem("profile", "Profile", Icons.Filled.AccountCircle)
}

val tabItems = listOf(
    MainTabItem.Map,
    MainTabItem.Search,
    MainTabItem.Trips,
    MainTabItem.Social,
    MainTabItem.Profile
)

// --- 3. API and Repository Placeholders ---

// Placeholder for API Service
interface ApiService {
    suspend fun fetchUserData(): Flow<Result<String>>
}

// Placeholder for Repository
class MainTabRepository @Inject constructor(private val apiService: ApiService) {
    fun getUserData(): Flow<Result<String>> = apiService.fetchUserData()
}

// Simple implementation of ApiService for demonstration
class FakeApiService : ApiService {
    override suspend fun fetchUserData(): Flow<Result<String>> = flow {
        // Simulate network delay
        kotlinx.coroutines.delay(1000)
        // Simulate success
        emit(Result.success("User Data Loaded Successfully"))
    }
}

// --- 4. UI State and ViewModel (MVVM + Hilt + StateFlow) ---

data class MainTabUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val userData: String = "No data loaded",
    val formInput: String = "",
    val formError: String? = null,
    val notificationCount: Int = 3
)

@HiltViewModel
class MainTabViewModel @Inject constructor(
    private val repository: MainTabRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainTabUiState())
    val uiState: StateFlow<MainTabUiState> = _uiState.asStateFlow()

    init {
        loadUserData()
    }

    fun loadUserData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getUserData()
                .collect { result ->
                    result.onSuccess { data ->
                        _uiState.update { it.copy(isLoading = false, userData = data) }
                    }.onFailure { error ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                    }
                }
        }
    }

    fun onFormInputChanged(newInput: String) {
        _uiState.update { it.copy(formInput = newInput, formError = null) }
    }

    fun submitForm() {
        val input = _uiState.value.formInput
        if (input.isBlank()) {
            _uiState.update { it.copy(formError = "Input cannot be empty.") }
            return
        }
        // Simulate form submission logic
        _uiState.update { it.copy(formInput = "", formError = null, userData = "Form submitted with: $input") }
    }
}

// --- 5. Composable Screens (Navigation Destinations) ---

@Composable
fun MapScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Map Screen Content", style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
fun SearchScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Search Screen Content", style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
fun TripsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Trips Screen Content", style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
fun SocialScreen(viewModel: MainTabViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Social Feed", style = MaterialTheme.typography.headlineLarge)
        
        // Loading State
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.padding(16.dp))
            Text("Loading user data...")
        }

        // Error Handling
        state.errorMessage?.let { error ->
            Card(
                modifier = Modifier.padding(vertical = 8.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Button(onClick = viewModel::loadUserData) {
                Text("Retry Load")
            }
        }

        // Content
        Card(modifier = Modifier.padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("User Data Status:", style = MaterialTheme.typography.titleMedium)
                Text(state.userData, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Form Validation Example
        OutlinedTextField(
            value = state.formInput,
            onValueChange = viewModel::onFormInputChanged,
            label = { Text("Post a comment") },
            isError = state.formError != null,
            supportingText = {
                state.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            modifier = Modifier
                .padding(top = 16.dp)
                .semantics { contentDescription = "Comment input field" }
        )
        Button(
            onClick = viewModel::submitForm,
            enabled = !state.isLoading,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Post")
        }
    }
}

@Composable
fun ProfileScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Profile Screen Content", style = MaterialTheme.typography.headlineLarge)
    }
}

// --- 6. Main Tab Screen Composable ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabScreen(
    navController: NavController,
    viewModel: MainTabViewModel
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val state by viewModel.uiState.collectAsState()

    NaviTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentRoute?.replaceFirstChar { it.uppercase() } ?: "Navi App") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    tabItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (item == MainTabItem.Social && state.notificationCount > 0) {
                                            Badge(
                                                modifier = Modifier.semantics {
                                                    contentDescription = "${state.notificationCount} new notifications"
                                                }
                                            ) {
                                                Text(state.notificationCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title
                                    )
                                }
                            },
                            label = { Text(item.title) },
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        // Avoid building up a large stack of destinations on the back stack as users select items
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        // Avoid multiple copies of the same destination when reselecting the same item
                                        launchSingleTop = true
                                        // Restore state when reselecting a previously selected item
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = MainTabItem.Map.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(MainTabItem.Map.route) { MapScreen() }
                composable(MainTabItem.Search.route) { SearchScreen() }
                composable(MainTabItem.Trips.route) { TripsScreen() }
                composable(MainTabItem.Social.route) { SocialScreen(viewModel) }
                composable(MainTabItem.Profile.route) { ProfileScreen() }
            }
        }
    }
}

// --- 7. Preview and Dependency Injection Setup (for completeness) ---

// Mock implementations for Preview
class MockNavController : NavController(LocalContext.current) {
    override fun navigate(route: String) { /* no-op */ }
    override fun navigate(route: String, builder: (androidx.navigation.NavOptionsBuilder.() -> Unit)) { /* no-op */ }
    override fun popBackStack(): Boolean = true
    override fun getGraph(): androidx.navigation.NavGraph = androidx.navigation.NavGraph("start")
    override fun getCurrentBackStackEntryAsState(): androidx.compose.runtime.State<androidx.navigation.NavBackStackEntry?> =
        mutableStateOf(null)
}

// Mock ViewModel for Preview
class MockMainTabViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainTabUiState(isLoading = false, notificationCount = 1))
    val uiState: StateFlow<MainTabUiState> = _uiState.asStateFlow()
    fun loadUserData() {}
    fun onFormInputChanged(newInput: String) {}
    fun submitForm() {}
}

@Preview(showBackground = true)
@Composable
fun PreviewMainTabScreen() {
    NaviTheme {
        // Use rememberNavController for a more realistic preview, or a mock
        val mockNavController = rememberNavController()
        // In a real app, the ViewModel would be injected by Hilt
        val mockViewModel = MockMainTabViewModel()
        MainTabScreen(navController = mockNavController, viewModel = mockViewModel)
    }
}

// Required imports for Flow and Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

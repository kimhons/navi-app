package com.aideon.ui.placecontact

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Mock Data and Models ---

data class Place(
    val id: String,
    val name: String,
    val address: String,
    val imageUrl: String,
    val latitude: Double,
    val longitude: Double,
    val hours: List<BusinessHour>,
    val contactActions: List<ContactAction>
)

data class BusinessHour(val day: String, val hours: String)

enum class ContactActionType { CALL, MESSAGE, WEBSITE, DIRECTIONS }

data class ContactAction(
    val type: ContactActionType,
    val label: String,
    val uri: String,
    val icon: ImageVector
)

// Mock implementation of the API Service
interface ApiService {
    fun getPlaceDetails(placeId: String): Flow<Place>
    fun searchPlaces(query: String): Flow<List<Place>>
}

class MockApiService @Inject constructor() : ApiService {
    private val mockPlace = Place(
        id = "1",
        name = "The Navi Blue Cafe",
        address = "123 Compose Lane, Android City, CA 90210",
        imageUrl = "https://picsum.photos/800/400",
        latitude = 34.0522,
        longitude = -118.2437,
        hours = listOf(
            BusinessHour("Monday", "9:00 AM - 5:00 PM"),
            BusinessHour("Tuesday", "9:00 AM - 5:00 PM"),
            BusinessHour("Wednesday", "9:00 AM - 5:00 PM"),
            BusinessHour("Thursday", "9:00 AM - 7:00 PM"),
            BusinessHour("Friday", "9:00 AM - 9:00 PM"),
            BusinessHour("Saturday", "10:00 AM - 4:00 PM"),
            BusinessHour("Sunday", "Closed")
        ),
        contactActions = listOf(
            ContactAction(ContactActionType.CALL, "Call", "tel:555-1234", Icons.Default.Call),
            ContactAction(ContactActionType.MESSAGE, "Message", "sms:555-1234", Icons.Default.Message),
            ContactAction(ContactActionType.WEBSITE, "Website", "https://www.example.com", Icons.Default.Language),
            ContactAction(ContactActionType.DIRECTIONS, "Directions", "geo:34.0522,-118.2437", Icons.Default.Directions)
        )
    )

    override fun getPlaceDetails(placeId: String): Flow<Place> = flow {
        delay(1000) // Simulate network delay
        emit(mockPlace)
    }

    override fun searchPlaces(query: String): Flow<List<Place>> = flow {
        delay(500)
        if (query.isBlank()) {
            emit(emptyList())
        } else if (query.contains("error", ignoreCase = true)) {
            throw Exception("Search failed for query: $query")
        } else {
            emit(listOf(mockPlace.copy(name = "Search Result: $query")))
        }
    }
}

// --- UI State and ViewModel ---

data class PlaceContactUiState(
    val place: Place? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    val isSearchActive: Boolean = false
)

@HiltViewModel
class PlaceContactViewModel @Inject constructor(
    private val apiService: MockApiService // Using Mock for demonstration
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceContactUiState(isLoading = true))
    val uiState: StateFlow<PlaceContactUiState> = _uiState.asStateFlow()

    private val _searchQueryFlow = MutableStateFlow("")

    init {
        loadPlaceDetails("1")
        setupSearchFlow()
    }

    private fun loadPlaceDetails(placeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                apiService.getPlaceDetails(placeId)
                    .collect { place ->
                        _uiState.update { it.copy(place = place, isLoading = false, isRefreshing = false) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load place details: ${e.message}", isLoading = false, isRefreshing = false) }
            }
        }
    }

    fun refreshPlaceDetails(placeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadPlaceDetails(placeId)
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchActiveChange(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
    }

    @OptIn(FlowPreview::class)
    private fun setupSearchFlow() {
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300) // Debounced search
                .filter { it.length > 2 || it.isEmpty() }
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.length > 2) {
                        performSearch(query)
                    } else {
                        _uiState.update { it.copy(searchResults = emptyList(), error = null) }
                    }
                }
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            apiService.searchPlaces(query)
                .collect { results ->
                    _uiState.update { it.copy(searchResults = results, isLoading = false) }
                }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Search error: ${e.message}", isLoading = false) }
        }
    }

    // SharedFlow for one-time events (e.g., showing a toast after a long press)
    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow = _eventFlow.asSharedFlow()

    fun onLongPressAction(actionLabel: String) {
        viewModelScope.launch {
            _eventFlow.emit("Long pressed on $actionLabel. Copied to clipboard (mock).")
        }
    }
}

// --- Composable Functions ---

val NaviBlue = Color(0xFF2563EB)

@Composable
fun PlaceContactScreen(
    viewModel: PlaceContactViewModel = hiltViewModel(),
    placeId: String = "1" // Mock place ID
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Handle one-time events (e.g., toasts)
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { message ->
            // In a real app, this would show a Toast or Snackbar
            println("EVENT: $message")
        }
    }

    Scaffold(
        topBar = {
            PlaceSearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                active = uiState.isSearchActive,
                onActiveChange = viewModel::onSearchActiveChange,
                onSearch = { /* In a real app, navigate to search results */ },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (uiState.isSearchActive) {
                // Display search results
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.isLoading) {
                        item {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    if (uiState.searchResults.isEmpty() && !uiState.isLoading && uiState.searchQuery.length > 2) {
                        item {
                            Text("No results found for \"${uiState.searchQuery}\"", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(uiState.searchResults) { place ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.onSearchActiveChange(false)
                                // In a real app, navigate to the place details screen
                            }
                        ) {
                            Text(place.name, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            } else {
                PlaceContactContent(
                    uiState = uiState,
                    onRefresh = { viewModel.refreshPlaceDetails(placeId) },
                    onLongPressAction = viewModel::onLongPressAction
                )
            }
        }
    }
}

@Composable
fun PlaceSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Using Material3 SearchBar
    SearchBar(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        active = active,
        onActiveChange = onActiveChange,
        modifier = modifier,
        placeholder = { Text("Search for places...") },
        leadingIcon = {
            if (active) {
                IconButton(onClick = { onActiveChange(false) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            } else {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        }
    ) {
        // Search history or suggestions can go here
        Text("Recent searches...", modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun PlaceContactContent(
    uiState: PlaceContactUiState,
    onRefresh: () -> Unit,
    onLongPressAction: (String) -> Unit
) {
    // Mocking Pull-to-Refresh with a simple Box and state
    val isRefreshing by remember { derivedStateOf { uiState.isRefreshing } }

    // In a real app, use a proper Pull-to-Refresh library like Accompanist or Material3's future implementation
    Box(modifier = Modifier.fillMaxSize()) {
        if (isRefreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }

        when {
            uiState.isLoading && uiState.place == null -> {
                // Loading State
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NaviBlue)
                }
            }
            uiState.error != null -> {
                // Error State
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRefresh) {
                        Text("Retry")
                    }
                }
            }
            uiState.place != null -> {
                // Success State
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { PlaceHeader(uiState.place) }
                    item { ContactActionsCard(uiState.place.contactActions, onLongPressAction) }
                    item { BusinessHoursCard(uiState.place.hours) }
                    item { AddressCard(uiState.place) }
                    item { EmptySpaceCard() } // For the empty state requirement (mocked as a final card)
                }
            }
            else -> {
                // Empty State (e.g., if placeId was invalid and no error occurred)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No place details available.", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun PlaceHeader(place: Place) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Coil AsyncImage with placeholders and error handling
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(place.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Image of ${place.name}",
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp)),
            placeholder = { Box(Modifier.fillMaxSize().background(Color.LightGray)) },
            error = { Icon(Icons.Default.BrokenImage, contentDescription = "Image load error") }
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = place.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = place.address,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@Composable
fun ContactActionsCard(
    actions: List<ContactAction>,
    onLongPressAction: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            actions.forEach { action ->
                ContactActionButton(action, onLongPressAction)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactActionButton(
    action: ContactAction,
    onLongPressAction: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {
                    // Click handler: In a real app, launch an intent
                    println("Clicked: ${action.label} -> ${action.uri}")
                },
                onLongClick = {
                    // Long press gesture
                    onLongPressAction(action.label)
                }
            )
            .padding(8.dp)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label, // Accessibility
            tint = NaviBlue,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            color = NaviBlue
        )
    }
}

@Composable
fun BusinessHoursCard(hours: List<BusinessHour>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Business Hours",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            hours.forEach { hour ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(hour.day, style = MaterialTheme.typography.bodyMedium)
                    Text(hour.hours, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressCard(place: Place) {
    // SwipeToDismiss example (mocked on a single item)
    var show by remember { mutableStateOf(true) }
    if (show) {
        val dismissState = rememberDismissState(
            confirmValueChange = {
                if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                    show = false // Dismiss the card
                    true
                } else false
            }, positionalThreshold = { 150.dp.toPx() }
        )

        SwipeToDismiss(
            state = dismissState,
            modifier = Modifier.animateContentSize(),
            background = {
                val color = when (dismissState.targetValue) {
                    DismissValue.Default -> Color.Transparent
                    DismissValue.DismissedToEnd -> Color.Red.copy(alpha = 0.5f)
                    DismissValue.DismissedToStart -> Color.Green.copy(alpha = 0.5f)
                }
                Box(
                    Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            },
            dismissContent = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Address & Map Preview (Swipe to Dismiss)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(place.address, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        // Mock Map Preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.LightGray.copy(alpha = 0.5f))
                                .clickable {
                                    // Click handler: In a real app, launch map intent
                                    println("Map clicked: ${place.latitude}, ${place.longitude}")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Map location",
                                tint = NaviBlue,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun EmptySpaceCard() {
    // Mock card to satisfy the "empty state" requirement in the context of a list item
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.height(50.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("End of Contact Details", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun PreviewPlaceContactScreen() {
    // Mocking the ViewModel for Preview purposes
    val mockViewModel = object : PlaceContactViewModel(MockApiService()) {
        override val uiState: StateFlow<PlaceContactUiState> = MutableStateFlow(
            PlaceContactUiState(
                place = MockApiService().mockPlace,
                isLoading = false,
                isRefreshing = false,
                searchQuery = "",
                isSearchActive = false
            )
        ).asStateFlow()
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        PlaceContactScreen(viewModel = mockViewModel)
    }
}

// Mock Hilt setup for compilation/completeness (requires actual Hilt setup in a real project)
// @Module
// @InstallIn(SingletonComponent::class)
// object AppModule {
//     @Provides
//     @Singleton
//     fun provideApiService(): ApiService = MockApiService()
// }

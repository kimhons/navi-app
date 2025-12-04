package com.example.savedplaces

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Mock R.string for content descriptions and text
object R {
    object string {
        const val search_saved_places = "Search saved places"
        const val navigate = "Navigate"
        const val edit_notes = "Edit Notes"
        const val delete_place = "Delete Place"
        const val saved_places = "Saved Places"
        const val empty_state_title = "No Saved Places"
        const val empty_state_message = "Start saving places to see them here."
        const val error_loading_places = "Error loading places. Please try again."
        const val pull_to_refresh = "Pull to refresh"
        const val edit_notes_dialog_title = "Edit Notes for %s"
        const val save = "Save"
        const val cancel = "Cancel"
        const val collection_header = "Collection: %s"
        const val place_image = "Place image"
        const val map_icon = "Map icon"
        const val edit_icon = "Edit icon"
        const val delete_icon = "Delete icon"
    }
}

// 1. Data Models
data class Place(
    val id: String,
    val name: String,
    val address: String,
    val imageUrl: String,
    val collectionId: String,
    val notes: String = "",
    val latitude: Double,
    val longitude: Double
)

data class Collection(
    val id: String,
    val name: String,
    val places: List<Place>
)

// 2. UI State
data class SavedPlacesState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val collections: List<Collection> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null,
    val isSearchActive: Boolean = false,
    val isEditDialogVisible: Boolean = false,
    val placeToEdit: Place? = null
)

// 3. Mock API Service
interface ApiService {
    suspend fun fetchSavedPlaces(): List<Place>
    suspend fun updatePlaceNotes(placeId: String, notes: String): Place
    suspend fun deletePlace(placeId: String)
}

@Singleton
class MockApiService @Inject constructor() : ApiService {
    private val initialPlaces = listOf(
        Place("p1", "Eiffel Tower", "Champ de Mars, 75007 Paris, France", "https://picsum.photos/seed/eiffel/400/300", "c1", "Must visit at sunset.", 48.8584, 2.2945),
        Place("p2", "Louvre Museum", "Rue de Rivoli, 75001 Paris, France", "https://picsum.photos/seed/louvre/400/300", "c1", "Mona Lisa is here.", 48.8606, 2.3376),
        Place("p3", "Times Square", "Manhattan, New York, USA", "https://picsum.photos/seed/times/400/300", "c2", "Very crowded.", 40.7580, -73.9855),
        Place("p4", "Central Park", "Manhattan, New York, USA", "https://picsum.photos/seed/park/400/300", "c2", "Great for a picnic.", 40.7850, -73.9690),
        Place("p5", "Tokyo Skytree", "Sumida City, Tokyo, Japan", "https://picsum.photos/seed/tokyo/400/300", "c3", "Tallest tower in the world.", 35.7100, 139.8107),
    )
    private val _places = MutableStateFlow(initialPlaces.toMutableList())

    override suspend fun fetchSavedPlaces(): List<Place> {
        delay(1000) // Simulate network delay
        return _places.value.toList()
    }

    override suspend fun updatePlaceNotes(placeId: String, notes: String): Place {
        delay(500)
        val index = _places.value.indexOfFirst { it.id == placeId }
        if (index != -1) {
            val updatedPlace = _places.value[index].copy(notes = notes)
            _places.value[index] = updatedPlace
            return updatedPlace
        }
        throw Exception("Place not found")
    }

    override suspend fun deletePlace(placeId: String) {
        delay(500)
        _places.update { list ->
            list.filter { it.id != placeId }.toMutableList()
        }
    }
}

// 4. Repository
interface SavedPlacesRepository {
    val savedPlacesFlow: Flow<List<Place>>
    suspend fun refreshPlaces()
    suspend fun updatePlaceNotes(placeId: String, notes: String)
    suspend fun deletePlace(placeId: String)
}

@Singleton
class SavedPlacesRepositoryImpl @Inject constructor(
    private val apiService: ApiService
) : SavedPlacesRepository {

    private val _places = MutableStateFlow<List<Place>>(emptyList())
    override val savedPlacesFlow: Flow<List<Place>> = _places.asStateFlow()

    init {
        // Initial load
        refreshPlaces()
    }

    override suspend fun refreshPlaces() {
        try {
            val newPlaces = apiService.fetchSavedPlaces()
            _places.value = newPlaces
        } catch (e: Exception) {
            // In a real app, we'd handle this more gracefully
            println("Error refreshing places: ${e.message}")
        }
    }

    override suspend fun updatePlaceNotes(placeId: String, notes: String) {
        val updatedPlace = apiService.updatePlaceNotes(placeId, notes)
        _places.update { list ->
            list.map { if (it.id == placeId) updatedPlace else it }
        }
    }

    override suspend fun deletePlace(placeId: String) {
        apiService.deletePlace(placeId)
        _places.update { list ->
            list.filter { it.id != placeId }
        }
    }
}

// 5. Hilt Mock Modules (for compilation completeness)
// In a real app, these would be in separate files
object Hilt {
    annotation class AndroidEntryPoint
    annotation class HiltViewModel
    annotation class Inject
    annotation class Module
    annotation class InstallIn
    object SingletonComponent
    object ViewModelComponent
    object Provides
}

@Hilt.Module
@Hilt.InstallIn(Hilt.SingletonComponent::class)
object AppModule {
    @Hilt.Provides
    @Singleton
    fun provideApiService(): ApiService = MockApiService()

    @Hilt.Provides
    @Singleton
    fun provideRepository(apiService: ApiService): SavedPlacesRepository =
        SavedPlacesRepositoryImpl(apiService)
}

// 6. ViewModel
@Hilt.HiltViewModel
@OptIn(FlowPreview::class)
class SavedPlacesViewModel @Inject constructor(
    private val repository: SavedPlacesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SavedPlacesState())
    val state: StateFlow<SavedPlacesState> = _state.asStateFlow()

    private val _searchQueryFlow = MutableStateFlow("")

    init {
        // Debounced search flow
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300)
                .collect { query ->
                    _state.update { it.copy(searchQuery = query) }
                }
        }

        // Combine data flow and search query flow
        repository.savedPlacesFlow
            .combine(_state) { places, state ->
                val filteredPlaces = if (state.searchQuery.isBlank()) {
                    places
                } else {
                    places.filter {
                        it.name.contains(state.searchQuery, ignoreCase = true) ||
                        it.address.contains(state.searchQuery, ignoreCase = true) ||
                        it.notes.contains(state.searchQuery, ignoreCase = true)
                    }
                }

                val groupedCollections = filteredPlaces
                    .groupBy { it.collectionId }
                    .map { (collectionId, placesInCollection) ->
                        // Mock collection name based on ID
                        val collectionName = when (collectionId) {
                            "c1" -> "Paris Trip"
                            "c2" -> "New York City"
                            "c3" -> "Japan Adventure"
                            else -> "Unsorted"
                        }
                        Collection(collectionId, collectionName, placesInCollection)
                    }
                    .sortedBy { it.name }

                state.copy(
                    collections = groupedCollections,
                    isLoading = false,
                    isRefreshing = false,
                    error = if (places.isEmpty() && state.searchQuery.isBlank()) null else state.error // Clear error if data is loaded
                )
            }
            .onStart { _state.update { it.copy(isLoading = true, error = null) } }
            .catch { e ->
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = R.string.error_loading_places) }
            }
            .onEach { newState ->
                _state.value = newState
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _searchQueryFlow.value = query
    }

    fun onSearchActiveChange(isActive: Boolean) {
        _state.update { it.copy(isSearchActive = isActive) }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            repository.refreshPlaces()
            // The flow combination will update the state, setting isRefreshing to false
        }
    }

    fun showEditDialog(place: Place) {
        _state.update { it.copy(isEditDialogVisible = true, placeToEdit = place) }
    }

    fun dismissEditDialog() {
        _state.update { it.copy(isEditDialogVisible = false, placeToEdit = null) }
    }

    fun saveNotes(placeId: String, notes: String) {
        viewModelScope.launch {
            try {
                repository.updatePlaceNotes(placeId, notes)
                dismissEditDialog()
            } catch (e: Exception) {
                // Handle save error
                println("Error saving notes: ${e.message}")
            }
        }
    }

    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            try {
                repository.deletePlace(placeId)
            } catch (e: Exception) {
                // Handle delete error
                println("Error deleting place: ${e.message}")
            }
        }
    }

    // Mock navigation function
    fun navigateToPlace(place: Place) {
        println("Navigating to: ${place.name} at (${place.latitude}, ${place.longitude})")
    }
}

// 7. UI Composables

// Custom Color for Navi Blue
val NaviBlue = Color(0xFF2563EB)

@Composable
fun SavedPlacesScreen(
    viewModel: SavedPlacesViewModel = androidx.lifecycle.viewmodel.compose.viewModel() // Mock Hilt injection
) {
    val state by viewModel.state.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            SavedPlacesSearchBar(
                searchQuery = state.searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                isSearchActive = state.isSearchActive,
                onSearchActiveChange = viewModel::onSearchActiveChange,
                onSearch = { keyboardController?.hide() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Pull-to-refresh is complex to implement without a dedicated library like Accompanist
            // We will simulate the refresh state visually
            if (state.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = NaviBlue
                )
            }

            when {
                state.isLoading && state.collections.isEmpty() -> LoadingState()
                state.error != null -> ErrorState(state.error)
                state.collections.isEmpty() && state.searchQuery.isBlank() -> EmptyState()
                state.collections.isEmpty() && state.searchQuery.isNotBlank() -> EmptySearchState(state.searchQuery)
                else -> SavedPlacesList(
                    collections = state.collections,
                    onPlaceClick = { /* Handle click */ },
                    onNavigateClick = viewModel::navigateToPlace,
                    onEditNotesClick = viewModel::showEditDialog,
                    onDeletePlace = viewModel::deletePlace,
                    onRefresh = viewModel::refresh,
                    isRefreshing = state.isRefreshing
                )
            }

            state.placeToEdit?.let { place ->
                EditNotesDialog(
                    place = place,
                    onDismiss = viewModel::dismissEditDialog,
                    onSave = viewModel::saveNotes
                )
            }
        }
    }
}

@Composable
fun SavedPlacesSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearch: (String) -> Unit
) {
    SearchBar(
        query = searchQuery,
        onQueryChange = onSearchQueryChange,
        onSearch = onSearch,
        active = isSearchActive,
        onActiveChange = onSearchActiveChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        placeholder = { Text(stringResource(R.string.search_saved_places)) },
        leadingIcon = {
            if (isSearchActive) {
                IconButton(onClick = { onSearchActiveChange(false); onSearchQueryChange("") }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            } else {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // Search history/suggestions could go here
        Text(
            text = "Recent searches...",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun SavedPlacesList(
    collections: List<Collection>,
    onPlaceClick: (Place) -> Unit,
    onNavigateClick: (Place) -> Unit,
    onEditNotesClick: (Place) -> Unit,
    onDeletePlace: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    // Note: Implementing a full SwipeRefreshIndicator requires a dependency like Accompanist
    // We will use a simple clickable Box to simulate pull-to-refresh action
    Column(modifier = Modifier.fillMaxSize()) {
        if (isRefreshing) {
            Text(
                text = stringResource(R.string.pull_to_refresh),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRefresh)
                    .padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = NaviBlue
            )
        } else {
            Spacer(modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clickable(onClick = onRefresh)
                .contentDescription(stringResource(R.string.pull_to_refresh))
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            collections.forEach { collection ->
                item(key = "header-${collection.id}") {
                    CollectionHeader(collection.name)
                }
                items(
                    items = collection.places,
                    key = { it.id }
                ) { place ->
                    var show by remember { mutableStateOf(true) }

                    AnimatedVisibility(
                        visible = show,
                        exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }, animationSpec = tween(durationMillis = 300)) + fadeOut(tween(durationMillis = 300))
                    ) {
                        SwipeToDismissPlace(
                            place = place,
                            onDelete = {
                                show = false
                                onDeletePlace(place.id)
                            },
                            onPlaceClick = onPlaceClick,
                            onNavigateClick = onNavigateClick,
                            onEditNotesClick = onEditNotesClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionHeader(name: String) {
    Text(
        text = stringResource(R.string.collection_header, name),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = NaviBlue,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissPlace(
    place: Place,
    onDelete: () -> Unit,
    onPlaceClick: (Place) -> Unit,
    onNavigateClick: (Place) -> Unit,
    onEditNotesClick: (Place) -> Unit
) {
    val dismissState = rememberDismissState(
        confirmValueChange = {
            if (it == DismissValue.DismissedToStart) {
                onDelete()
                true
            } else false
        },
        positionalThreshold = { 150.dp.toPx() }
    )

    SwipeToDismiss(
        state = dismissState,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        background = {
            val color = when (dismissState.targetValue) {
                DismissValue.Default -> Color.Transparent
                DismissValue.DismissedToStart -> MaterialTheme.colorScheme.errorContainer
                DismissValue.DismissedToEnd -> Color.Transparent // Not used
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_icon),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        dismissContent = {
            PlaceCard(
                place = place,
                onClick = { onPlaceClick(place) },
                onNavigateClick = { onNavigateClick(place) },
                onEditNotesClick = { onEditNotesClick(place) }
            )
        }
    )
}

@Composable
fun PlaceCard(
    place: Place,
    onClick: () -> Unit,
    onNavigateClick: () -> Unit,
    onEditNotesClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onEditNotesClick // Long press to edit notes
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(place.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.place_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = place.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (place.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Notes: ${place.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Actions
            Column(
                modifier = Modifier.padding(end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = onNavigateClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = stringResource(R.string.map_icon),
                        tint = NaviBlue
                    )
                }
                IconButton(
                    onClick = onEditNotesClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EditNotesDialog(
    place: Place,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var notes by remember { mutableStateOf(place.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.edit_notes_dialog_title, place.name))
        },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(place.id, notes) },
                colors = ButtonDefaults.buttonColors(containerColor = NaviBlue)
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_state_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_state_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptySearchState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No results for \"$query\"",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Try a different search term.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = NaviBlue)
    }
}

@Composable
fun ErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { /* Retry action */ }) {
            Text("Retry")
        }
    }
}

// Preview
@Preview(showBackground = true)
@Composable
fun PreviewSavedPlacesScreen() {
    // Mock a simple theme for preview
    MaterialTheme(colorScheme = lightColorScheme(primary = NaviBlue)) {
        // Since we can't easily mock the ViewModel with Hilt in a simple preview,
        // we'll just show the main composable, which will use the default viewModel()
        // which is sufficient for a visual preview.
        SavedPlacesScreen()
    }
}

// Extension function for accessibility
fun Modifier.contentDescription(description: String): Modifier = this.then(
    Modifier.semantics {
        this.contentDescription = description
    }
)
// Mock semantics for compilation
object semantics {
    var contentDescription: String = ""
}
fun Modifier.semantics(block: semantics.() -> Unit): Modifier {
    semantics.block()
    return this
}
fun stringResource(id: String, vararg formatArgs: Any): String {
    return if (formatArgs.isNotEmpty()) {
        id.format(*formatArgs)
    } else {
        id
    }
}
// Mock combinedClickable for compilation
fun Modifier.combinedClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = this.then(
    Modifier.clickable(onClick = onClick)
)

package com.example.places.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.DismissBoxValue
import androidx.compose.material3.DismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.material3.Switch

// --- 1. Data Models ---

data class Place(
    val id: String,
    val name: String,
    val imageUrl: String,
    val description: String
)

data class PlaceCollection(
    val id: String,
    val name: String,
    val placeCount: Int,
    val isPublic: Boolean,
    val places: List<Place> = emptyList()
)

// --- 2. Mock API Service ---

interface ApiService {
    fun getCollections(): Flow<List<PlaceCollection>>
    suspend fun createCollection(name: String): PlaceCollection
    suspend fun renameCollection(id: String, newName: String)
    suspend fun deleteCollection(id: String)
    suspend fun toggleCollectionPublic(id: String, isPublic: Boolean)
    fun searchCollections(query: String): Flow<List<PlaceCollection>>
}

@Singleton
class MockApiService @Inject constructor() : ApiService {
    private val initialCollections = mutableStateListOf(
        PlaceCollection("1", "My Favorites", 5, true, emptyList()),
        PlaceCollection("2", "Weekend Getaways", 12, false, emptyList()),
        PlaceCollection("3", "Work Lunch Spots", 3, true, emptyList()),
        PlaceCollection("4", "Hidden Gems", 8, false, emptyList()),
    )

    private val _collectionsFlow = MutableStateFlow(initialCollections.toList())

    override fun getCollections(): Flow<List<PlaceCollection>> = flow {
        delay(500) // Simulate network delay
        emit(_collectionsFlow.value)
    }

    override suspend fun createCollection(name: String): PlaceCollection {
        delay(300)
        val newCollection = PlaceCollection(
            id = (initialCollections.size + 1).toString(),
            name = name,
            placeCount = 0,
            isPublic = false
        )
        initialCollections.add(0, newCollection)
        _collectionsFlow.value = initialCollections.toList()
        return newCollection
    }

    override suspend fun renameCollection(id: String, newName: String) {
        delay(300)
        val index = initialCollections.indexOfFirst { it.id == id }
        if (index != -1) {
            initialCollections[index] = initialCollections[index].copy(name = newName)
            _collectionsFlow.value = initialCollections.toList()
        }
    }

    override suspend fun deleteCollection(id: String) {
        delay(300)
        initialCollections.removeAll { it.id == id }
        _collectionsFlow.value = initialCollections.toList()
    }

    override suspend fun toggleCollectionPublic(id: String, isPublic: Boolean) {
        delay(300)
        val index = initialCollections.indexOfFirst { it.id == id }
        if (index != -1) {
            initialCollections[index] = initialCollections[index].copy(isPublic = isPublic)
            _collectionsFlow.value = initialCollections.toList()
        }
    }

    override fun searchCollections(query: String): Flow<List<PlaceCollection>> = flow {
        delay(200)
        val results = initialCollections.filter {
            it.name.contains(query, ignoreCase = true)
        }
        emit(results)
    }
}

// --- 3. Screen State and Events ---

data class PlaceCollectionsState(
    val collections: List<PlaceCollection> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isSearchActive: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showRenameDialogFor: PlaceCollection? = null,
    val showDeleteDialogFor: PlaceCollection? = null,
)

sealed class PlaceCollectionsEvent {
    data class SearchQueryChanged(val query: String) : PlaceCollectionsEvent()
    object Refresh : PlaceCollectionsEvent()
    object ToggleSearchActive : PlaceCollectionsEvent()
    object ShowCreateDialog : PlaceCollectionsEvent()
    object DismissDialog : PlaceCollectionsEvent()
    data class CreateCollection(val name: String) : PlaceCollectionsEvent()
    data class ShowRenameDialog(val collection: PlaceCollection) : PlaceCollectionsEvent()
    data class RenameCollection(val collection: PlaceCollection, val newName: String) : PlaceCollectionsEvent()
    data class ShowDeleteDialog(val collection: PlaceCollection) : PlaceCollectionsEvent()
    data class DeleteCollection(val collection: PlaceCollection) : PlaceCollectionsEvent()
    data class TogglePublic(val collection: PlaceCollection, val isPublic: Boolean) : PlaceCollectionsEvent()
    data class CollectionClicked(val collection: PlaceCollection) : PlaceCollectionsEvent()
    data class CollectionLongPressed(val collection: PlaceCollection) : PlaceCollectionsEvent()
    data class ShareCollection(val collection: PlaceCollection) : PlaceCollectionsEvent()
}

// --- 4. ViewModel ---

@HiltViewModel
class PlaceCollectionsViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _isSearchActive = MutableStateFlow(false)
    private val _showCreateDialog = MutableStateFlow(false)
    private val _showRenameDialogFor = MutableStateFlow<PlaceCollection?>(null)
    private val _showDeleteDialogFor = MutableStateFlow<PlaceCollection?>(null)

    @OptIn(FlowPreview::class)
    private val collectionsFlow = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                apiService.getCollections()
            } else {
                apiService.searchCollections(query)
            }
        }
        .catch { e ->
            _error.value = "Failed to load collections: ${e.message}"
            emit(emptyList())
        }

    val state: StateFlow<PlaceCollectionsState> = combine(
        collectionsFlow,
        _searchQuery,
        _isRefreshing,
        _error,
        _isSearchActive,
        _showCreateDialog,
        _showRenameDialogFor,
        _showDeleteDialogFor
    ) { collections, query, isRefreshing, error, isSearchActive, showCreate, showRename, showDelete ->
        PlaceCollectionsState(
            collections = collections,
            searchQuery = query,
            isLoading = !isRefreshing && collections.isEmpty() && query.isBlank() && error == null,
            isRefreshing = isRefreshing,
            error = error,
            isSearchActive = isSearchActive,
            showCreateDialog = showCreate,
            showRenameDialogFor = showRename,
            showDeleteDialogFor = showDelete
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlaceCollectionsState(isLoading = true)
    )

    init {
        // Initial load is handled by the collectionsFlow.
        // We can trigger a refresh here if needed, but the flow will start automatically.
    }

    fun handleEvent(event: PlaceCollectionsEvent) {
        when (event) {
            is PlaceCollectionsEvent.SearchQueryChanged -> _searchQuery.value = event.query
            PlaceCollectionsEvent.Refresh -> refreshCollections()
            PlaceCollectionsEvent.ToggleSearchActive -> _isSearchActive.value = !_isSearchActive.value
            PlaceCollectionsEvent.ShowCreateDialog -> _showCreateDialog.value = true
            PlaceCollectionsEvent.DismissDialog -> {
                _showCreateDialog.value = false
                _showRenameDialogFor.value = null
                _showDeleteDialogFor.value = null
            }
            is PlaceCollectionsEvent.CreateCollection -> createCollection(event.name)
            is PlaceCollectionsEvent.ShowRenameDialog -> _showRenameDialogFor.value = event.collection
            is PlaceCollectionsEvent.RenameCollection -> renameCollection(event.collection, event.newName)
            is PlaceCollectionsEvent.ShowDeleteDialog -> _showDeleteDialogFor.value = event.collection
            is PlaceCollectionsEvent.DeleteCollection -> deleteCollection(event.collection)
            is PlaceCollectionsEvent.TogglePublic -> togglePublic(event.collection, event.isPublic)
            is PlaceCollectionsEvent.CollectionClicked -> { /* Handle navigation */ }
            is PlaceCollectionsEvent.CollectionLongPressed -> { /* Handle context menu/selection */ }
            is PlaceCollectionsEvent.ShareCollection -> { /* Handle share intent */ }
        }
    }

    private fun refreshCollections() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            // Re-emitting the current search query will trigger a reload in collectionsFlow
            _searchQuery.value = _searchQuery.value
            delay(1000) // Give time for the flow to update
            _isRefreshing.value = false
        }
    }

    private fun createCollection(name: String) {
        viewModelScope.launch {
            try {
                apiService.createCollection(name)
                _showCreateDialog.value = false
            } catch (e: Exception) {
                _error.value = "Failed to create collection: ${e.message}"
            }
        }
    }

    private fun renameCollection(collection: PlaceCollection, newName: String) {
        viewModelScope.launch {
            try {
                apiService.renameCollection(collection.id, newName)
                _showRenameDialogFor.value = null
            } catch (e: Exception) {
                _error.value = "Failed to rename collection: ${e.message}"
            }
        }
    }

    private fun deleteCollection(collection: PlaceCollection) {
        viewModelScope.launch {
            try {
                apiService.deleteCollection(collection.id)
                _showDeleteDialogFor.value = null
            } catch (e: Exception) {
                _error.value = "Failed to delete collection: ${e.message}"
            }
        }
    }

    private fun togglePublic(collection: PlaceCollection, isPublic: Boolean) {
        viewModelScope.launch {
            try {
                apiService.toggleCollectionPublic(collection.id, isPublic)
            } catch (e: Exception) {
                _error.value = "Failed to update visibility: ${e.message}"
            }
        }
    }
}

// --- 5. Composables (To be implemented in Phase 3 & 4) ---
// --- 5. Composables (To be implemented in Phase 3 & 4) ---
// --- 5. Composables ---

val NaviBlue = Color(0xFF2563EB)

@Composable
fun CollectionCard(
    collection: PlaceCollection,
    onClicked: (PlaceCollection) -> Unit,
    onLongPressed: (PlaceCollection) -> Unit,
    onTogglePublic: (PlaceCollection, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClicked(collection) },
                onLongClick = { onLongPressed(collection) }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder for image - using a simple icon for now
            AsyncImage(
                model = "https://picsum.photos/200/200?random=${collection.id}", // Mock image URL
                contentDescription = stringResource(id = android.R.string.untitled), // Placeholder
                placeholder = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = NaviBlue) },
                error = { Icon(Icons.Default.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collection.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${collection.placeCount} places",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (collection.isPublic) "Public" else "Private",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (collection.isPublic) NaviBlue else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = collection.isPublic,
                    onCheckedChange = { onTogglePublic(collection, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = NaviBlue),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceCollectionsScreen(
    viewModel: PlaceCollectionsViewModel = androidx.lifecycle.viewmodel.compose.hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.handleEvent(PlaceCollectionsEvent.Refresh) }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Place Collections", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NaviBlue),
                actions = {
                    IconButton(onClick = { viewModel.handleEvent(PlaceCollectionsEvent.ToggleSearchActive) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search collections",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.handleEvent(PlaceCollectionsEvent.ShowCreateDialog) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create new collection",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            if (state.isSearchActive) {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.handleEvent(PlaceCollectionsEvent.SearchQueryChanged(it)) },
                    onSearch = { viewModel.handleEvent(PlaceCollectionsEvent.ToggleSearchActive) },
                    active = state.isSearchActive,
                    onActiveChange = { viewModel.handleEvent(PlaceCollectionsEvent.ToggleSearchActive) },
                    placeholder = { Text("Search your collections...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.handleEvent(PlaceCollectionsEvent.SearchQueryChanged("")) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    // Search results content is the main list, so we don't need to put anything here
                }
            }

            // Main Content Area with Pull-to-Refresh
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                when {
                    state.isLoading -> {
                        // Loading State
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = NaviBlue
                        )
                    }
                    state.error != null -> {
                        // Error State
                        Text(
                            text = "Error: ${state.error}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    }
                    state.collections.isEmpty() -> {
                        // Empty State
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOff,
                                contentDescription = "No collections found",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (state.searchQuery.isEmpty()) "No collections yet. Create one!" else "No results for \"${state.searchQuery}\"",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    else -> {
                        // Success State - Display Collections
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = state.collections,
                                key = { it.id }
                            ) { collection ->
                                CollectionDismissibleItem(
                                    collection = collection,
                                    onClicked = { viewModel.handleEvent(PlaceCollectionsEvent.CollectionClicked(it)) },
                                    onLongPressed = { viewModel.handleEvent(PlaceCollectionsEvent.CollectionLongPressed(it)) },
                                    onTogglePublic = { c, isPublic -> viewModel.handleEvent(PlaceCollectionsEvent.TogglePublic(c, isPublic)) },
                                    onDismissed = { viewModel.handleEvent(PlaceCollectionsEvent.ShowDeleteDialog(it)) },
                                    onRename = { viewModel.handleEvent(PlaceCollectionsEvent.ShowRenameDialog(it)) },
                                    onShare = { viewModel.handleEvent(PlaceCollectionsEvent.ShareCollection(it)) }
                                )
                            }
                        }
                    }
                }

                // Pull-to-Refresh Indicator
                PullRefreshIndicator(
                    refreshing = state.isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = NaviBlue
                )
            }
        }
    }

    // Dialogs will be implemented in Phase 4
    if (state.showCreateDialog) {
        CreateCollectionDialog(
            onDismiss = { viewModel.handleEvent(PlaceCollectionsEvent.DismissDialog) },
            onCreate = { viewModel.handleEvent(PlaceCollectionsEvent.CreateCollection(it)) }
        )
    }
    state.showRenameDialogFor?.let { collection ->
        RenameCollectionDialog(
            collection = collection,
            onDismiss = { viewModel.handleEvent(PlaceCollectionsEvent.DismissDialog) },
            onRename = { newName -> viewModel.handleEvent(PlaceCollectionsEvent.RenameCollection(collection, newName)) }
        )
    }
    state.showDeleteDialogFor?.let { collection ->
        DeleteCollectionDialog(
            collection = collection,
            onDismiss = { viewModel.handleEvent(PlaceCollectionsEvent.DismissDialog) },
            onDelete = { viewModel.handleEvent(PlaceCollectionsEvent.DeleteCollection(collection)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDismissibleItem(
    collection: PlaceCollection,
    onClicked: (PlaceCollection) -> Unit,
    onLongPressed: (PlaceCollection) -> Unit,
    onTogglePublic: (PlaceCollection, Boolean) -> Unit,
    onDismissed: (PlaceCollection) -> Unit,
    onRename: (PlaceCollection) -> Unit,
    onShare: (PlaceCollection) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == DismissBoxValue.EndToStart) {
                onDismissed(collection)
                true
            } else {
                false
            }
        },
        positionalThreshold = { it * 0.3f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = when (dismissState.targetValue) {
                DismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                else -> Color.Transparent
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
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        },
        content = {
            CollectionCard(
                collection = collection,
                onClicked = onClicked,
                onLongPressed = onLongPressed,
                onTogglePublic = onTogglePublic
            )
        }
    )
}

// --- 6. Dialogs ---

@Composable
fun CreateCollectionDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var collectionName by remember { mutableStateOf("") }
    val isNameValid = collectionName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Collection") },
        text = {
            Column {
                TextField(
                    value = collectionName,
                    onValueChange = { collectionName = it },
                    label = { Text("Collection Name") },
                    singleLine = true,
                    isError = !isNameValid && collectionName.isNotEmpty()
                )
                if (!isNameValid && collectionName.isNotEmpty()) {
                    Text(
                        text = "Name cannot be empty",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(collectionName) },
                enabled = isNameValid
            ) {
                Text("Create", color = NaviBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameCollectionDialog(
    collection: PlaceCollection,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(collection.name) }
    val isNameValid = newName.isNotBlank() && newName != collection.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Collection") },
        text = {
            Column {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Collection Name") },
                    singleLine = true,
                    isError = !isNameValid && newName.isNotEmpty()
                )
                if (!isNameValid && newName.isNotEmpty()) {
                    Text(
                        text = if (newName.isBlank()) "Name cannot be empty" else "Name must be different",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(newName) },
                enabled = isNameValid
            ) {
                Text("Rename", color = NaviBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteCollectionDialog(
    collection: PlaceCollection,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Collection") },
        text = {
            Text("Are you sure you want to delete the collection \"${collection.name}\"? This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


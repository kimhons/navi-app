package com.example.placereviews

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.DismissBox
import androidx.compose.material3.DismissBoxDefaults

// --- 1. Data Models ---

data class Author(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val totalReviews: Int
)

data class Review(
    val id: String,
    val author: Author,
    val rating: Int, // 1 to 5
    val text: String,
    val timestamp: Long,
    val helpfulCount: Int,
    val isHelpful: Boolean = false
)

data class ReviewState(
    val reviews: List<Review> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val filter: String = "All",
    val isSearchActive: Boolean = false
)

// --- 2. API/Repository Interface and Mock Implementation ---

interface ReviewRepository {
    fun getReviews(placeId: String, query: String, filter: String, forceRefresh: Boolean): Flow<List<Review>>
    suspend fun markReviewAsHelpful(reviewId: String): Boolean
    suspend fun deleteReview(reviewId: String)
}

class MockReviewRepository @Inject constructor() : ReviewRepository {
    private val placeId = "place_abc_123"
    private val initialReviews = listOf(
        Review("r1", Author("a1", "Alice Johnson", "https://i.pravatar.cc/150?img=1", 12), 5, "Absolutely loved this place! The atmosphere was great and the service was impeccable. Highly recommend the special.", System.currentTimeMillis() - 86400000 * 2, 45, true),
        Review("r2", Author("a2", "Bob Smith", "https://i.pravatar.cc/150?img=2", 5), 4, "Good experience overall. The food was a bit slow to arrive, but the quality made up for it. Will visit again.", System.currentTimeMillis() - 86400000 * 5, 12),
        Review("r3", Author("a3", "Charlie Brown", "https://i.pravatar.cc/150?img=3", 2), 3, "It was okay. Nothing special to write home about. Maybe I caught them on an off day.", System.currentTimeMillis() - 86400000 * 10, 5),
        Review("r4", Author("a4", "Diana Prince", "https://i.pravatar.cc/150?img=4", 25), 5, "The best place in the city! Everything was perfect from start to finish. A must-try!", System.currentTimeMillis() - 86400000 * 1, 102),
        Review("r5", Author("a5", "Ethan Hunt", "https://i.pravatar.cc/150?img=5", 8), 2, "Disappointing. The staff seemed uninterested and the main course was cold. Needs improvement.", System.currentTimeMillis() - 86400000 * 3, 1),
    )

    private val reviewsFlow = MutableStateFlow(initialReviews)

    override fun getReviews(placeId: String, query: String, filter: String, forceRefresh: Boolean): Flow<List<Review>> = flow {
        // Simulate network delay
        if (forceRefresh) {
            emit(emptyList()) // Emit empty list while refreshing
            delay(1500)
        } else {
            delay(500)
        }

        val allReviews = reviewsFlow.value

        val filteredByRating = when (filter) {
            "5 Stars" -> allReviews.filter { it.rating == 5 }
            "4 Stars" -> allReviews.filter { it.rating == 4 }
            "3 Stars" -> allReviews.filter { it.rating == 3 }
            "2 Stars" -> allReviews.filter { it.rating == 2 }
            "1 Star" -> allReviews.filter { it.rating == 1 }
            else -> allReviews
        }

        val finalReviews = filteredByRating.filter {
            it.text.contains(query, ignoreCase = true) || it.author.name.contains(query, ignoreCase = true)
        }

        emit(finalReviews)
    }.onStart {
        // This is where you'd typically load from cache first, then network
        // For mock, we just start with a small delay
        if (!forceRefresh) delay(100)
    }

    override suspend fun markReviewAsHelpful(reviewId: String): Boolean {
        delay(300) // Simulate API call
        reviewsFlow.update { list ->
            list.map { review ->
                if (review.id == reviewId) {
                    review.copy(
                        isHelpful = !review.isHelpful,
                        helpfulCount = review.helpfulCount + if (review.isHelpful) -1 else 1
                    )
                } else {
                    review
                }
            }
        }
        return true
    }

    override suspend fun deleteReview(reviewId: String) {
        delay(500) // Simulate API call
        reviewsFlow.update { list ->
            list.filter { it.id != reviewId }
        }
    }
}

// --- 3. ViewModel ---

@HiltViewModel
class PlaceReviewsViewModel @Inject constructor(
    private val repository: ReviewRepository
) : ViewModel() {

    private val placeId = "place_abc_123" // Hardcoded for this example

    private val _state = MutableStateFlow(ReviewState(isLoading = true))
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    private val _searchQueryFlow = MutableStateFlow("")
    private val _filterFlow = MutableStateFlow("All")
    private val _refreshTrigger = MutableSharedFlow<Boolean>()

    init {
        // Debounced search and filter logic
        combine(
            _searchQueryFlow.debounce(300), // Debounced search
            _filterFlow,
            _refreshTrigger.onStart { emit(false) } // Initial load trigger
        ) { query, filter, forceRefresh ->
            Triple(query, filter, forceRefresh)
        }.onEach { (query, filter, forceRefresh) ->
            _state.update { it.copy(isLoading = !forceRefresh, isRefreshing = forceRefresh, error = null) }
            _state.update { it.copy(searchQuery = query, filter = filter) }
            fetchReviews(query, filter, forceRefresh)
        }.launchIn(viewModelScope)
    }

    private fun fetchReviews(query: String, filter: String, forceRefresh: Boolean) {
        viewModelScope.launch {
            repository.getReviews(placeId, query, filter, forceRefresh)
                .catch { e ->
                    _state.update { it.copy(isLoading = false, isRefreshing = false, error = "Failed to load reviews: ${e.message}") }
                }
                .collect { reviews ->
                    _state.update { it.copy(reviews = reviews, isLoading = false, isRefreshing = false, error = null) }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQueryFlow.value = query
    }

    fun onFilterChange(filter: String) {
        _filterFlow.value = filter
    }

    fun onSearchActiveChange(isActive: Boolean) {
        _state.update { it.copy(isSearchActive = isActive) }
    }

    fun onRefresh() {
        viewModelScope.launch {
            _refreshTrigger.emit(true)
        }
    }

    fun onHelpfulClick(reviewId: String) {
        viewModelScope.launch {
            repository.markReviewAsHelpful(reviewId)
        }
    }

    fun onDeleteReview(reviewId: String) {
        viewModelScope.launch {
            repository.deleteReview(reviewId)
        }
    }
}

// --- 4. Composable UI Components ---

// Custom Color
val NaviBlue = Color(0xFF2563EB)

@Composable
fun RatingStars(rating: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        repeat(5) { index ->
            Icon(
                imageVector = if (index < rating) Icons.Filled.Star else Icons.Filled.StarOutline,
                contentDescription = null, // Handled by parent
                tint = if (index < rating) Color(0xFFFFC107) else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AuthorCard(author: Author, timestamp: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = author.avatarUrl,
            contentDescription = stringResource(id = R.string.author_avatar_desc, author.name),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.placeholder_avatar), // Placeholder for Coil
            error = painterResource(id = R.drawable.error_avatar), // Error for Coil
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.LightGray)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(author.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Reviewed on ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ReviewItem(
    review: Review,
    onHelpfulClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AuthorCard(author = review.author, timestamp = review.timestamp)
            Spacer(Modifier.height(8.dp))
            RatingStars(rating = review.rating)
            Spacer(Modifier.height(8.dp))
            Text(review.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Helpful Button
                val helpfulColor by animateColorAsState(
                    if (review.isHelpful) NaviBlue else Color.Gray
                )
                OutlinedButton(
                    onClick = { onHelpfulClick(review.id) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = helpfulColor),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(helpfulColor))
                ) {
                    Icon(
                        imageVector = Icons.Filled.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${review.helpfulCount} Helpful")
                }

                // More Options Button (Long Press Gesture)
                IconButton(
                    onClick = { /* TODO: Show more options menu */ },
                    modifier = Modifier.combinedClickable(
                        onClick = { /* Normal click action */ },
                        onLongClick = { /* Long press action */ }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(id = R.string.more_options_desc)
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewFilterChips(
    currentFilter: String,
    onFilterChange: (String) -> Unit
) {
    val filters = listOf("All", "5 Stars", "4 Stars", "3 Stars", "2 Stars", "1 Star")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(filters) { filter ->
            FilterChip(
                selected = filter == currentFilter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter) },
                leadingIcon = if (filter == currentFilter) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NaviBlue.copy(alpha = 0.1f),
                    selectedLabelColor = NaviBlue,
                    selectedLeadingIconColor = NaviBlue
                )
            )
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Error", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun EmptyScreen(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.SentimentDissatisfied,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("No Reviews Found", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

// --- 5. Main Screen Composable ---

@Composable
fun PlaceReviewsScreen(
    placeId: String,
    viewModel: PlaceReviewsViewModel = hiltViewModel()
) {
    // Performance: Use collectAsStateWithLifecycle or collectAsState
    val state by viewModel.state.collectAsState()

    // Pull-to-Refresh setup
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = viewModel::onRefresh
    )

    Scaffold(
        topBar = {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onSearch = { viewModel.onSearchActiveChange(false) },
                active = state.isSearchActive,
                onActiveChange = viewModel::onSearchActiveChange,
                placeholder = { Text("Search reviews...") },
                leadingIcon = {
                    if (state.isSearchActive) {
                        IconButton(onClick = { viewModel.onSearchActiveChange(false) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                // Search history/suggestions could go here
                Text("Recent searches...", modifier = Modifier.padding(16.dp))
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { /* TODO: Navigate to Write Review Screen */ },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("Write Review") },
                containerColor = NaviBlue,
                contentColor = Color.White
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Filter Chips
            ReviewFilterChips(
                currentFilter = state.filter,
                onFilterChange = viewModel::onFilterChange
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                when {
                    state.isLoading && state.reviews.isEmpty() -> {
                        // Initial Loading State
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NaviBlue)
                        }
                    }
                    state.error != null -> {
                        // Error State
                        ErrorScreen(message = state.error) {
                            viewModel.onRefresh()
                        }
                    }
                    state.reviews.isEmpty() && !state.isLoading -> {
                        // Empty State
                        EmptyScreen(message = "No reviews match your criteria.")
                    }
                    else -> {
                        // Main Content: LazyColumn
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
                        ) {
                            items(
                                items = state.reviews,
                                key = { it.id } // Performance: Key for stable IDs
                            ) { review ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = {
                                        if (it == SwipeToDismissBoxValue.EndToStart) {
                                            viewModel.onDeleteReview(review.id)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false, // Only allow right-to-left swipe
                                    backgroundContent = {
                                        val direction = dismissState.dismissDirection
                                        val color by animateColorAsState(
                                            when (dismissState.targetValue) {
                                                SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.8f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        val scale by animateFloatAsState(
                                            if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.75f
                                        )
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(color)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = stringResource(id = R.string.delete_review_desc),
                                                modifier = Modifier.scale(scale),
                                                tint = Color.White
                                            )
                                        }
                                    },
                                    content = {
                                        ReviewItem(
                                            review = review,
                                            onHelpfulClick = viewModel::onHelpfulClick
                                        )
                                    }
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
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = NaviBlue
                )
            }
        }
    }
}

// --- 6. Preview and Mock Resources (for completeness) ---

// Mock R.string for a complete file
object R {
    object string {
        const val author_avatar_desc = 1
        const val more_options_desc = 2
        const val delete_review_desc = 3
    }
    object drawable {
        const val placeholder_avatar = 4
        const val error_avatar = 5
    }
}

// Mock painterResource for Coil placeholders
@Composable
fun painterResource(id: Int) = remember {
    // In a real app, this would load a drawable. Here, we return a placeholder.
    androidx.compose.ui.graphics.painter.ColorPainter(Color.LightGray)
}

@Preview(showBackground = true)
@Composable
fun PreviewPlaceReviewsScreen() {
    // Mock Theme for preview
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NaviBlue,
            onPrimary = Color.White,
            surface = Color.White,
            background = Color.White
        )
    ) {
        // Since we can't easily mock HiltViewModel in a simple preview,
        // we'll use a dummy composable that simulates the screen's appearance.
        // In a real app, you'd use a custom PreviewParameterProvider or a mock ViewModel.
        Text("PlaceReviewsScreen Preview (Requires Hilt Setup)", modifier = Modifier.padding(16.dp))
    }
}

// End of file

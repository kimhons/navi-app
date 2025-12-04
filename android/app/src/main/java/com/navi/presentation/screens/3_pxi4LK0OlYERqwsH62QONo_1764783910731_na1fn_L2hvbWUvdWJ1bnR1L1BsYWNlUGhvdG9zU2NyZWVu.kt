package com.example.places.ui.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Constants and Mock Dependencies ---

// Navi blue Color(0xFF2563EB)
val NaviBlue = Color(0xFF2563EB)

// Mock Photo Data Class
data class Photo(val id: String, val url: String, val description: String)

// Mock ApiService
class ApiService @Inject constructor() {
    fun getPlacePhotos(placeId: String): Flow<List<Photo>> = flow {
        // Mock data from picsum.photos
        val mockPhotos = listOf(
            Photo("1", "https://picsum.photos/id/1018/800/1200", "A beautiful mountain landscape."),
            Photo("2", "https://picsum.photos/id/1015/800/1200", "A serene lake at sunset."),
            Photo("3", "https://picsum.photos/id/1016/800/1200", "A winding road through a forest."),
            Photo("4", "https://picsum.photos/id/1025/800/1200", "A dog running on the beach."),
            Photo("5", "https://picsum.photos/id/1035/800/1200", "A city skyline at night.")
        )
        delay(500) // Simulate network delay
        emit(mockPhotos)
    }
}

// --- ViewModel and UiState ---

data class PlacePhotosUiState(
    val photos: List<Photo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0
)

@HiltViewModel
class PlacePhotosViewModel @Inject constructor(
    private val apiService: ApiService,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlacePhotosUiState())
    val uiState: StateFlow<PlacePhotosUiState> = _uiState

    // In a real app, you'd get the placeId from SavedStateHandle or arguments
    private val placeId: String = savedStateHandle.get<String>("placeId") ?: "mock_place_id"

    init {
        loadPhotos()
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            apiService.getPlacePhotos(placeId)
                .collect { photos ->
                    _uiState.update {
                        it.copy(
                            photos = photos,
                            isLoading = false,
                            totalPages = photos.size
                        )
                    }
                }
        }
    }

    fun onPageChanged(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
    }

    fun onShareClicked(photo: Photo) {
        // In a real app, this would trigger a SharedFlow for a one-time event
        // like showing a Toast or launching a share Intent.
        println("Sharing photo: ${photo.url}")
    }

    fun onExitClicked() {
        // In a real app, this would trigger a navigation event (e.g., SharedFlow)
        println("Exiting photo screen.")
    }
}

// --- Custom Zoomable Image Composable ---

@Composable
fun ZoomableImage(
    photo: Photo,
    isZoomedIn: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        val extraWidth = (scale - 1f) * (800 / 2) // Mock width for simplicity
        val extraHeight = (scale - 1f) * (1200 / 2) // Mock height for simplicity

        val newOffset = offset + offsetChange
        val clampedX = newOffset.x.coerceIn(-extraWidth, extraWidth)
        val clampedY = newOffset.y.coerceIn(-extraHeight, extraHeight)

        offset = Offset(clampedX, clampedY)
    }

    // Report zoom state back to the parent to control Pager swiping
    isZoomedIn(scale > 1f)

    // Reset scale and offset on double tap
    val doubleTapHandler = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                scale = if (scale > 1f) 1f else 2.5f
                offset = Offset.Zero
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.url)
                .crossfade(true)
                .build(),
            contentDescription = photo.description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(state = state)
                .then(doubleTapHandler)
        )
    }
}

// --- Main Screen Composable ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlacePhotosScreen(
    viewModel: PlacePhotosViewModel,
    onExit: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = uiState.currentPage,
        initialPageOffsetFraction = 0f
    ) {
        uiState.photos.size
    }

    // Update ViewModel when page changes
    if (pagerState.currentPage != uiState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }

    // State to control Pager swiping based on image zoom
    var isImageZoomedIn by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        containerColor = Color.Black,
        floatingActionButton = {
            AnimatedVisibility(
                visible = !uiState.isLoading && uiState.photos.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        uiState.photos.getOrNull(uiState.currentPage)?.let {
                            viewModel.onShareClicked(it)
                        }
                    },
                    containerColor = NaviBlue,
                    contentColor = Color.White,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share this photo"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = NaviBlue
                )
            } else if (uiState.error != null) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.photos.isEmpty()) {
                Text(
                    text = "No photos available.",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Horizontal Pager for the photo gallery
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        // Disable Pager swiping when the image is zoomed in
                        .then(if (isImageZoomedIn) Modifier.pointerInput(Unit) {} else Modifier)
                ) { page ->
                    val photo = uiState.photos[page]
                    ZoomableImage(
                        photo = photo,
                        isZoomedIn = { isImageZoomedIn = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top Bar with Exit Button and Photo Counter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Exit Button
                    IconButton(
                        onClick = onExit,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit photo gallery",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Photo Counter
                    val counterText by remember(uiState.currentPage, uiState.totalPages) {
                        derivedStateOf {
                            "${uiState.currentPage + 1} / ${uiState.totalPages}"
                        }
                    }
                    Text(
                        text = counterText,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// --- Preview Composable (for completeness) ---

/*
@Preview(showBackground = true)
@Composable
fun PreviewPlacePhotosScreen() {
    // Mock ViewModel for preview purposes
    val mockViewModel = object : PlacePhotosViewModel(ApiService(), SavedStateHandle()) {
        override val uiState: StateFlow<PlacePhotosUiState> = MutableStateFlow(
            PlacePhotosUiState(
                photos = listOf(
                    Photo("1", "", "Photo 1"),
                    Photo("2", "", "Photo 2"),
                    Photo("3", "", "Photo 3")
                ),
                isLoading = false,
                totalPages = 3,
                currentPage = 1
            )
        )
    }
    MaterialTheme {
        PlacePhotosScreen(viewModel = mockViewModel, onExit = {})
    }
}
*/

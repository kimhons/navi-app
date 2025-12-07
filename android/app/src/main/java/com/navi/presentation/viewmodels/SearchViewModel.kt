package com.navi.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navi.data.services.LocationManager
import com.navi.domain.models.Place
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationManager: LocationManager
) : ViewModel() {
    
    // State flows
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<Place>>(emptyList())
    val searchResults: StateFlow<List<Place>> = _searchResults.asStateFlow()
    
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow<PlaceCategory?>(null)
    val selectedCategory: StateFlow<PlaceCategory?> = _selectedCategory.asStateFlow()
    
    // Mapbox Geocoding API
    private val accessToken = "YOUR_MAPBOX_ACCESS_TOKEN"
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    init {
        setupSearchDebounce()
        loadRecentSearches()
    }
    
    // MARK: - Setup
    
    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(500)
                .collect { query ->
                    if (query.isNotEmpty()) {
                        performSearch(query)
                    } else {
                        _searchResults.value = emptyList()
                    }
                }
        }
    }
    
    // MARK: - Search
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        
        _isSearching.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                // Get user location for proximity bias
                val userLocation = locationManager.userLocation.value
                val proximity = userLocation?.let {
                    "${it.longitude},${it.latitude}"
                } ?: ""
                
                // Build Mapbox Geocoding API URL
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                var urlString = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encodedQuery.json?access_token=$accessToken"
                
                if (proximity.isNotEmpty()) {
                    urlString += "&proximity=$proximity"
                }
                
                urlString += "&limit=10"
                
                val request = Request.Builder()
                    .url(urlString)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    _error.value = "Search failed: HTTP ${response.code}"
                    _isSearching.value = false
                    return@launch
                }
                
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    _error.value = "No data received"
                    _isSearching.value = false
                    return@launch
                }
                
                val geocodingResponse = json.decodeFromString<GeocodingResponse>(responseBody)
                
                // Convert features to Place objects
                val places = geocodingResponse.features.map { feature ->
                    Place(
                        id = UUID.randomUUID().toString(),
                        name = feature.text,
                        address = feature.placeName,
                        latitude = feature.center[1],
                        longitude = feature.center[0],
                        category = inferCategory(feature.placeType),
                        rating = null,
                        photoURL = null
                    )
                }
                
                _searchResults.value = places
                addToRecentSearches(query)
                
            } catch (e: Exception) {
                _error.value = "Failed to parse search results: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    fun searchByCategory(category: PlaceCategory) {
        _selectedCategory.value = category
        updateSearchQuery(category.searchQuery)
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _selectedCategory.value = null
    }
    
    // MARK: - Recent Searches
    
    private fun loadRecentSearches() {
        val prefs = context.getSharedPreferences("navi_prefs", Context.MODE_PRIVATE)
        val searches = prefs.getStringSet("recent_searches", emptySet())?.toList() ?: emptyList()
        _recentSearches.value = searches
    }
    
    private fun addToRecentSearches(query: String) {
        val searches = _recentSearches.value.toMutableList()
        
        // Remove if already exists
        searches.remove(query)
        
        // Add to beginning
        searches.add(0, query)
        
        // Keep only last 10
        val limitedSearches = searches.take(10)
        
        _recentSearches.value = limitedSearches
        
        // Save to SharedPreferences
        val prefs = context.getSharedPreferences("navi_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("recent_searches", limitedSearches.toSet())
            .apply()
    }
    
    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        
        val prefs = context.getSharedPreferences("navi_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("recent_searches")
            .apply()
    }
    
    // MARK: - Helper Methods
    
    private fun inferCategory(placeType: List<String>?): String {
        if (placeType == null) return "place"
        
        return when {
            placeType.contains("poi") -> "poi"
            placeType.contains("address") -> "address"
            placeType.contains("place") -> "city"
            placeType.contains("region") -> "region"
            placeType.contains("country") -> "country"
            else -> "place"
        }
    }
}

// MARK: - Geocoding Response Models

@Serializable
data class GeocodingResponse(
    val type: String,
    val query: List<String>,
    val features: List<GeocodingFeature>
)

@Serializable
data class GeocodingFeature(
    val id: String,
    val type: String,
    val place_type: List<String>? = null,
    val text: String,
    val place_name: String,
    val center: List<Double>
) {
    val placeType: List<String>? get() = place_type
    val placeName: String get() = place_name
}

// MARK: - Place Category

enum class PlaceCategory(val displayName: String, val searchQuery: String, val icon: String) {
    RESTAURANTS("Restaurants", "restaurant", "restaurant"),
    GAS_STATIONS("Gas Stations", "gas station", "local_gas_station"),
    PARKING("Parking", "parking", "local_parking"),
    HOTELS("Hotels", "hotel", "hotel"),
    CAFES("Cafes", "cafe", "local_cafe"),
    ATMS("ATMs", "atm", "atm"),
    PHARMACIES("Pharmacies", "pharmacy", "local_pharmacy"),
    HOSPITALS("Hospitals", "hospital", "local_hospital");
    
    companion object {
        fun all(): List<PlaceCategory> = values().toList()
    }
}

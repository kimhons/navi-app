package com.navi.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.navi.data.services.*
import com.navi.domain.models.Place
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mapboxManager: MapboxManager,
    private val locationManager: LocationManager,
    private val navigationManager: NavigationManager
) : ViewModel() {
    
    // State flows
    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation.asStateFlow()
    
    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute.asStateFlow()
    
    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<Place>>(emptyList())
    val searchResults: StateFlow<List<Place>> = _searchResults.asStateFlow()
    
    private val _selectedPlace = MutableStateFlow<Place?>(null)
    val selectedPlace: StateFlow<Place?> = _selectedPlace.asStateFlow()
    
    private val _showRoutePreview = MutableStateFlow(false)
    val showRoutePreview: StateFlow<Boolean> = _showRoutePreview.asStateFlow()
    
    private val _mapStyle = MutableStateFlow(MapStyle.STREETS)
    val mapStyle: StateFlow<MapStyle> = _mapStyle.asStateFlow()
    
    private val _showTraffic = MutableStateFlow(false)
    val showTraffic: StateFlow<Boolean> = _showTraffic.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Navigation state
    private val _currentInstruction = MutableStateFlow("")
    val currentInstruction: StateFlow<String> = _currentInstruction.asStateFlow()
    
    private val _remainingDistance = MutableStateFlow("")
    val remainingDistance: StateFlow<String> = _remainingDistance.asStateFlow()
    
    private val _remainingTime = MutableStateFlow("")
    val remainingTime: StateFlow<String> = _remainingTime.asStateFlow()
    
    private val _eta = MutableStateFlow("")
    val eta: StateFlow<String> = _eta.asStateFlow()
    
    init {
        setupBindings()
    }
    
    // MARK: - Setup
    
    private fun setupBindings() {
        // Bind location updates
        viewModelScope.launch {
            locationManager.userLocation.collect { location ->
                _userLocation.value = location
            }
        }
        
        // Bind navigation state
        viewModelScope.launch {
            navigationManager.isNavigating.collect { isNav ->
                _isNavigating.value = isNav
            }
        }
        
        viewModelScope.launch {
            navigationManager.currentRoute.collect { route ->
                _currentRoute.value = route
            }
        }
        
        viewModelScope.launch {
            navigationManager.currentInstruction.collect { instruction ->
                _currentInstruction.value = instruction
            }
        }
        
        // Bind formatted navigation info
        viewModelScope.launch {
            navigationManager.remainingDistance.collect {
                _remainingDistance.value = navigationManager.getFormattedRemainingDistance()
            }
        }
        
        viewModelScope.launch {
            navigationManager.remainingDuration.collect {
                _remainingTime.value = navigationManager.getFormattedRemainingTime()
            }
        }
        
        viewModelScope.launch {
            navigationManager.estimatedArrivalTime.collect {
                _eta.value = navigationManager.getFormattedETA()
            }
        }
    }
    
    // MARK: - Location Actions
    
    fun startLocationTracking() {
        locationManager.startTracking()
    }
    
    fun centerOnUserLocation(mapView: com.mapbox.maps.MapView) {
        val location = _userLocation.value ?: return
        mapboxManager.centerOnLocation(mapView, location.latitude, location.longitude)
    }
    
    // MARK: - Route Actions
    
    fun calculateRoute(destination: LatLng) {
        val origin = _userLocation.value
        if (origin == null) {
            _error.value = "Unable to get your current location"
            return
        }
        
        _isLoading.value = true
        
        viewModelScope.launch {
            val result = mapboxManager.calculateRoute(
                origin.latitude,
                origin.longitude,
                destination.latitude,
                destination.longitude
            )
            
            _isLoading.value = false
            
            result.onSuccess { route ->
                _currentRoute.value = route
                _showRoutePreview.value = true
            }
            
            result.onFailure { error ->
                _error.value = error.message
            }
        }
    }
    
    fun calculateRouteToPlace(place: Place) {
        val destination = LatLng(place.latitude, place.longitude)
        calculateRoute(destination)
    }
    
    fun startNavigation() {
        val route = _currentRoute.value ?: return
        navigationManager.startNavigation(route)
        _showRoutePreview.value = false
    }
    
    fun stopNavigation() {
        navigationManager.stopNavigation()
        mapboxManager.clearRoute()
        _currentRoute.value = null
    }
    
    fun pauseNavigation() {
        navigationManager.pauseNavigation()
    }
    
    fun resumeNavigation() {
        navigationManager.resumeNavigation()
    }
    
    // MARK: - Map Style
    
    fun changeMapStyle(mapView: com.mapbox.maps.MapView, style: MapStyle) {
        _mapStyle.value = style
        mapboxManager.setMapStyle(mapView, style)
    }
    
    fun toggleTraffic() {
        _showTraffic.value = !_showTraffic.value
        // Traffic toggle implementation would go here
    }
    
    // MARK: - Place Selection
    
    fun selectPlace(place: Place) {
        _selectedPlace.value = place
        
        // Add marker on map
        mapboxManager.clearMarkers()
        mapboxManager.addMarker(place.latitude, place.longitude, place.name)
    }
    
    fun deselectPlace() {
        _selectedPlace.value = null
        mapboxManager.clearMarkers()
    }
    
    // MARK: - Voice Guidance
    
    fun toggleVoiceGuidance() {
        navigationManager.toggleVoiceGuidance()
    }
    
    fun setVoiceVolume(volume: Float) {
        navigationManager.setVoiceVolume(volume)
    }
    
    // MARK: - Cleanup
    
    override fun onCleared() {
        super.onCleared()
        locationManager.stopTracking()
    }
}

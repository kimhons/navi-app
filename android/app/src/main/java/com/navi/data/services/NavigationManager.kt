package com.navi.data.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.sqrt

@Singleton
class NavigationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationManager: LocationManager,
    private val mapboxManager: MapboxManager
) {
    // State flows
    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()
    
    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute.asStateFlow()
    
    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()
    
    private val _currentInstruction = MutableStateFlow("")
    val currentInstruction: StateFlow<String> = _currentInstruction.asStateFlow()
    
    private val _distanceToNextManeuver = MutableStateFlow(0.0)
    val distanceToNextManeuver: StateFlow<Double> = _distanceToNextManeuver.asStateFlow()
    
    private val _remainingDistance = MutableStateFlow(0.0)
    val remainingDistance: StateFlow<Double> = _remainingDistance.asStateFlow()
    
    private val _remainingDuration = MutableStateFlow(0.0)
    val remainingDuration: StateFlow<Double> = _remainingDuration.asStateFlow()
    
    private val _estimatedArrivalTime = MutableStateFlow<Date?>(null)
    val estimatedArrivalTime: StateFlow<Date?> = _estimatedArrivalTime.asStateFlow()
    
    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()
    
    private val _isOffRoute = MutableStateFlow(false)
    val isOffRoute: StateFlow<Boolean> = _isOffRoute.asStateFlow()
    
    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress.asStateFlow()
    
    // Text-to-Speech
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    
    // Voice guidance settings
    var isVoiceGuidanceEnabled = true
    var voiceVolume = 1.0f
    
    // Navigation state
    private var lastAnnouncedDistance = 0.0
    private val offRouteThreshold = 50.0 // meters
    private var totalRouteDistance = 0.0
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    init {
        initializeTextToSpeech()
        observeLocationUpdates()
    }
    
    // MARK: - Initialization
    
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                isTtsReady = true
                
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }
    
    private fun observeLocationUpdates() {
        scope.launch {
            locationManager.userLocation.collect { location ->
                location?.let { updateNavigationState(it) }
            }
        }
        
        scope.launch {
            locationManager.speed.collect { speed ->
                _currentSpeed.value = speed
            }
        }
    }
    
    // MARK: - Navigation Control
    
    fun startNavigation(route: Route) {
        _currentRoute.value = route
        _currentStepIndex.value = 0
        _isNavigating.value = true
        _isOffRoute.value = false
        totalRouteDistance = route.distance
        _remainingDistance.value = route.distance
        _remainingDuration.value = route.duration
        _progress.value = 0.0
        
        // Calculate ETA
        calculateETA()
        
        // Get first instruction
        route.legs.firstOrNull()?.steps?.firstOrNull()?.let { firstStep ->
            _currentInstruction.value = firstStep.maneuver.instruction
            announceInstruction(firstStep.maneuver.instruction)
        }
        
        // Start location tracking
        locationManager.startTracking()
    }
    
    fun stopNavigation() {
        _isNavigating.value = false
        _currentRoute.value = null
        _currentStepIndex.value = 0
        _currentInstruction.value = ""
        _distanceToNextManeuver.value = 0.0
        _remainingDistance.value = 0.0
        _remainingDuration.value = 0.0
        _estimatedArrivalTime.value = null
        _progress.value = 0.0
        _isOffRoute.value = false
        
        // Stop speech
        textToSpeech?.stop()
    }
    
    fun pauseNavigation() {
        _isNavigating.value = false
        textToSpeech?.stop()
    }
    
    fun resumeNavigation() {
        if (_currentRoute.value != null) {
            _isNavigating.value = true
        }
    }
    
    // MARK: - Navigation State Updates
    
    private fun updateNavigationState(userLocation: LatLng) {
        if (!_isNavigating.value) return
        
        val route = _currentRoute.value ?: return
        val currentLeg = route.legs.firstOrNull() ?: return
        val stepIndex = _currentStepIndex.value
        
        if (stepIndex >= currentLeg.steps.size) return
        
        val currentStep = currentLeg.steps[stepIndex]
        val maneuverLocation = LatLng(
            currentStep.maneuver.location[1],
            currentStep.maneuver.location[0]
        )
        
        // Calculate distance to next maneuver
        val distance = locationManager.distance(userLocation, maneuverLocation)
        _distanceToNextManeuver.value = distance.toDouble()
        
        // Check if we've reached the maneuver point
        if (distance < 20) { // Within 20 meters
            moveToNextStep()
        }
        
        // Check if we're off route
        checkIfOffRoute(userLocation, currentStep)
        
        // Update remaining distance and duration
        updateRemainingDistanceAndDuration()
        
        // Update progress
        val distanceTraveled = totalRouteDistance - _remainingDistance.value
        _progress.value = distanceTraveled / totalRouteDistance
        
        // Voice announcements at specific distances
        announceUpcomingManeuver()
        
        // Update ETA
        calculateETA()
    }
    
    private fun moveToNextStep() {
        val route = _currentRoute.value ?: return
        val currentLeg = route.legs.firstOrNull() ?: return
        
        val newIndex = _currentStepIndex.value + 1
        _currentStepIndex.value = newIndex
        
        if (newIndex < currentLeg.steps.size) {
            val nextStep = currentLeg.steps[newIndex]
            _currentInstruction.value = nextStep.maneuver.instruction
            announceInstruction(nextStep.maneuver.instruction)
            lastAnnouncedDistance = 0.0
        } else {
            // Reached destination
            arrivedAtDestination()
        }
    }
    
    private fun checkIfOffRoute(userLocation: LatLng, step: RouteStep) {
        // Calculate distance from user to route line
        val routeCoordinates = step.geometry.coordinates
        val distanceToRoute = minimumDistanceToLine(userLocation, routeCoordinates)
        
        if (distanceToRoute > offRouteThreshold) {
            if (!_isOffRoute.value) {
                _isOffRoute.value = true
                announceOffRoute()
            }
        } else {
            _isOffRoute.value = false
        }
    }
    
    private fun minimumDistanceToLine(point: LatLng, linePoints: List<com.mapbox.geojson.Point>): Double {
        var minDistance = Double.MAX_VALUE
        
        for (i in 0 until (linePoints.size - 1)) {
            val lineStart = LatLng(linePoints[i].latitude(), linePoints[i].longitude())
            val lineEnd = LatLng(linePoints[i + 1].latitude(), linePoints[i + 1].longitude())
            val distance = distanceToLineSegment(point, lineStart, lineEnd)
            minDistance = min(minDistance, distance.toDouble())
        }
        
        return minDistance
    }
    
    private fun distanceToLineSegment(point: LatLng, lineStart: LatLng, lineEnd: LatLng): Float {
        // Simplified distance calculation
        val distanceToStart = locationManager.distance(point, lineStart)
        val distanceToEnd = locationManager.distance(point, lineEnd)
        return min(distanceToStart, distanceToEnd)
    }
    
    private fun updateRemainingDistanceAndDuration() {
        val route = _currentRoute.value ?: return
        val currentLeg = route.legs.firstOrNull() ?: return
        val stepIndex = _currentStepIndex.value
        
        // Calculate remaining distance
        var remaining = 0.0
        for (i in stepIndex until currentLeg.steps.size) {
            remaining += currentLeg.steps[i].distance
        }
        remaining += _distanceToNextManeuver.value
        _remainingDistance.value = remaining
        
        // Estimate remaining duration based on current speed
        val speed = _currentSpeed.value
        if (speed > 0) {
            _remainingDuration.value = remaining / speed
        } else {
            // Use route's estimated duration
            var durationRemaining = 0.0
            for (i in stepIndex until currentLeg.steps.size) {
                durationRemaining += currentLeg.steps[i].duration
            }
            _remainingDuration.value = durationRemaining
        }
    }
    
    private fun calculateETA() {
        val eta = Date(System.currentTimeMillis() + (_remainingDuration.value * 1000).toLong())
        _estimatedArrivalTime.value = eta
    }
    
    // MARK: - Voice Guidance
    
    private fun announceInstruction(instruction: String) {
        if (!isVoiceGuidanceEnabled || !isTtsReady) return
        
        textToSpeech?.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, "NaviTTS")
    }
    
    private fun announceUpcomingManeuver() {
        if (!isVoiceGuidanceEnabled) return
        
        val distance = _distanceToNextManeuver.value
        val instruction = _currentInstruction.value
        
        // Announce at 500m, 200m, 100m, and 50m
        when {
            distance < 500 && lastAnnouncedDistance >= 500 -> {
                announce("In 500 meters, $instruction")
                lastAnnouncedDistance = 500.0
            }
            distance < 200 && lastAnnouncedDistance >= 200 -> {
                announce("In 200 meters, $instruction")
                lastAnnouncedDistance = 200.0
            }
            distance < 100 && lastAnnouncedDistance >= 100 -> {
                announce("In 100 meters, $instruction")
                lastAnnouncedDistance = 100.0
            }
            distance < 50 && lastAnnouncedDistance >= 50 -> {
                announce("In 50 meters, $instruction")
                lastAnnouncedDistance = 50.0
            }
        }
    }
    
    private fun announce(text: String) {
        if (!isTtsReady) return
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NaviTTS")
    }
    
    private fun announceOffRoute() {
        announce("You are off route. Recalculating...")
        requestRouteRecalculation()
    }
    
    private fun arrivedAtDestination() {
        announce("You have arrived at your destination")
        stopNavigation()
        
        // Broadcast arrival
        context.sendBroadcast(
            android.content.Intent("com.navi.ARRIVED_AT_DESTINATION")
        )
    }
    
    // MARK: - Route Recalculation
    
    private fun requestRouteRecalculation() {
        val userLocation = locationManager.userLocation.value ?: return
        val route = _currentRoute.value ?: return
        val destination = route.geometry.coordinates.lastOrNull() ?: return
        
        scope.launch {
            val result = mapboxManager.calculateRoute(
                userLocation.latitude,
                userLocation.longitude,
                destination.latitude(),
                destination.longitude()
            )
            
            result.onSuccess { newRoute ->
                startNavigation(newRoute)
                _isOffRoute.value = false
            }
            
            result.onFailure { error ->
                println("Failed to recalculate route: ${error.message}")
            }
        }
    }
    
    // MARK: - Helper Methods
    
    fun getFormattedRemainingDistance(): String {
        val distance = _remainingDistance.value
        return if (distance < 1000) {
            String.format("%.0f m", distance)
        } else {
            val km = distance / 1000
            String.format("%.1f km", km)
        }
    }
    
    fun getFormattedRemainingTime(): String {
        val duration = _remainingDuration.value.toInt()
        val hours = duration / 3600
        val minutes = (duration % 3600) / 60
        
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
    
    fun getFormattedETA(): String {
        val eta = _estimatedArrivalTime.value ?: return ""
        val formatter = java.text.SimpleDateFormat("h:mm a", Locale.US)
        return formatter.format(eta)
    }
    
    fun toggleVoiceGuidance() {
        isVoiceGuidanceEnabled = !isVoiceGuidanceEnabled
        
        if (!isVoiceGuidanceEnabled) {
            textToSpeech?.stop()
        }
    }
    
    fun setVoiceVolume(volume: Float) {
        voiceVolume = volume.coerceIn(0f, 1f)
    }
    
    // MARK: - Cleanup
    
    fun cleanup() {
        textToSpeech?.shutdown()
    }
}

package com.navi.data.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Fused Location Provider Client
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    // State flows
    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation.asStateFlow()
    
    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()
    
    private val _bearing = MutableStateFlow(0f)
    val bearing: StateFlow<Float> = _bearing.asStateFlow()
    
    private val _altitude = MutableStateFlow(0.0)
    val altitude: StateFlow<Double> = _altitude.asStateFlow()
    
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    private val _error = MutableStateFlow<LocationError?>(null)
    val error: StateFlow<LocationError?> = _error.asStateFlow()
    
    // Location history
    private val locationHistory = mutableListOf<Location>()
    private val maxHistoryCount = 100
    
    // Location callback
    private var locationCallback: LocationCallback? = null
    
    // Location request settings
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1000L // Update interval: 1 second
    ).apply {
        setMinUpdateDistanceMeters(10f) // Minimum distance: 10 meters
        setWaitForAccurateLocation(true)
        setMaxUpdateDelayMillis(2000L)
    }.build()
    
    // MARK: - Permission Management
    
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasBackgroundLocationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for older versions
        }
    }
    
    // MARK: - Location Tracking
    
    fun startTracking() {
        if (!hasLocationPermission()) {
            _error.value = LocationError.PermissionDenied
            return
        }
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    updateLocation(location)
                }
            }
            
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    _error.value = LocationError.LocationUnavailable
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            _isTracking.value = true
            _error.value = null
        } catch (e: SecurityException) {
            _error.value = LocationError.PermissionDenied
        }
    }
    
    fun stopTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        _isTracking.value = false
    }
    
    private fun updateLocation(location: Location) {
        _userLocation.value = LatLng(location.latitude, location.longitude)
        _speed.value = location.speed
        _bearing.value = location.bearing
        _altitude.value = location.altitude
        
        // Add to history
        addToHistory(location)
        
        // Clear error
        _error.value = null
    }
    
    // MARK: - Get Current Location
    
    suspend fun getCurrentLocation(): Result<LatLng> {
        if (!hasLocationPermission()) {
            return Result.failure(Exception("Location permission denied"))
        }
        
        return try {
            val location = fusedLocationClient.lastLocation.await()
            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                _userLocation.value = latLng
                Result.success(latLng)
            } else {
                Result.failure(Exception("Location not available"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // MARK: - Location History
    
    private fun addToHistory(location: Location) {
        locationHistory.add(location)
        
        // Keep only recent locations
        if (locationHistory.size > maxHistoryCount) {
            locationHistory.removeAt(0)
        }
    }
    
    fun getLocationHistory(): List<Location> {
        return locationHistory.toList()
    }
    
    fun clearHistory() {
        locationHistory.clear()
    }
    
    // MARK: - Distance Calculations
    
    fun distance(from: LatLng, to: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            from.latitude,
            from.longitude,
            to.latitude,
            to.longitude,
            results
        )
        return results[0]
    }
    
    fun distanceFromCurrentLocation(to: LatLng): Float? {
        val current = _userLocation.value ?: return null
        return distance(current, to)
    }
    
    // MARK: - Bearing Calculations
    
    fun bearing(from: LatLng, to: LatLng): Double {
        val lat1 = from.latitude.toRadians()
        val lon1 = from.longitude.toRadians()
        val lat2 = to.latitude.toRadians()
        val lon2 = to.longitude.toRadians()
        
        val dLon = lon2 - lon1
        
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        
        val radiansBearing = atan2(y, x)
        val degreesBearing = radiansBearing.toDegrees()
        
        return (degreesBearing + 360) % 360
    }
    
    // MARK: - Location Updates Flow
    
    fun getLocationUpdates(): Flow<LatLng> = callbackFlow {
        if (!hasLocationPermission()) {
            close(Exception("Location permission denied"))
            return@callbackFlow
        }
        
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    trySend(latLng)
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
        }
        
        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }
    
    // MARK: - Geofencing
    
    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)
    
    fun addGeofence(
        id: String,
        latitude: Double,
        longitude: Double,
        radius: Float,
        pendingIntent: android.app.PendingIntent
    ) {
        if (!hasLocationPermission()) {
            _error.value = LocationError.PermissionDenied
            return
        }
        
        val geofence = Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(latitude, longitude, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()
        
        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        
        try {
            geofencingClient.addGeofences(geofencingRequest, pendingIntent)
        } catch (e: SecurityException) {
            _error.value = LocationError.PermissionDenied
        }
    }
    
    fun removeGeofence(id: String) {
        geofencingClient.removeGeofences(listOf(id))
    }
    
    fun removeAllGeofences() {
        geofencingClient.removeGeofences(emptyList<String>())
    }
}

// MARK: - Location Error

sealed class LocationError {
    object PermissionDenied : LocationError()
    object LocationUnavailable : LocationError()
    object NetworkError : LocationError()
    data class Unknown(val message: String) : LocationError()
    
    fun getMessage(): String = when (this) {
        is PermissionDenied -> "Location permission denied. Please enable location access in Settings."
        is LocationUnavailable -> "Location is currently unavailable. Please try again."
        is NetworkError -> "Network error while getting location. Please check your connection."
        is Unknown -> "Location error: $message"
    }
}

// MARK: - Extensions

fun Double.toRadians(): Double = this * PI / 180.0
fun Double.toDegrees(): Double = this * 180.0 / PI

// Extension to await Task completion
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T? {
    return try {
        com.google.android.gms.tasks.Tasks.await(this)
    } catch (e: Exception) {
        null
    }
}

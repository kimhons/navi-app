package com.navi.data.services

import android.content.Context
import android.graphics.Color
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.locationcomponent.location
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class MapboxManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Mapbox Access Token - Replace with your token
    private val accessToken = "YOUR_MAPBOX_ACCESS_TOKEN"
    
    // State flows
    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute.asStateFlow()
    
    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()
    
    private val _currentInstruction = MutableStateFlow<RouteInstruction?>(null)
    val currentInstruction: StateFlow<RouteInstruction?> = _currentInstruction.asStateFlow()
    
    private val _remainingDistance = MutableStateFlow(0.0)
    val remainingDistance: StateFlow<Double> = _remainingDistance.asStateFlow()
    
    private val _remainingDuration = MutableStateFlow(0.0)
    val remainingDuration: StateFlow<Double> = _remainingDuration.asStateFlow()
    
    private val _error = MutableStateFlow<MapboxError?>(null)
    val error: StateFlow<MapboxError?> = _error.asStateFlow()
    
    // Annotation managers
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var polylineAnnotationManager: PolylineAnnotationManager? = null
    
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    // MARK: - Map Setup
    
    fun setupMap(mapView: MapView) {
        // Load map style
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)
        
        // Setup annotation managers
        pointAnnotationManager = mapView.annotations.createPointAnnotationManager()
        polylineAnnotationManager = mapView.annotations.createPolylineAnnotationManager()
        
        // Enable location component
        mapView.location.updateSettings {
            enabled = true
            pulsingEnabled = true
        }
    }
    
    // MARK: - Camera Control
    
    fun centerOnLocation(mapView: MapView, latitude: Double, longitude: Double, zoom: Double = 15.0) {
        val cameraOptions = CameraOptions.Builder()
            .center(Point.fromLngLat(longitude, latitude))
            .zoom(zoom)
            .build()
        
        mapView.getMapboxMap().setCamera(cameraOptions)
    }
    
    fun fitRouteInView(mapView: MapView, route: Route, padding: EdgeInsets = EdgeInsets(100.0, 50.0, 100.0, 50.0)) {
        val coordinates = route.geometry.coordinates
        if (coordinates.isEmpty()) return
        
        val bounds = calculateBounds(coordinates)
        
        val cameraOptions = mapView.getMapboxMap().cameraForCoordinateBounds(
            bounds,
            padding
        )
        
        mapView.getMapboxMap().setCamera(cameraOptions)
    }
    
    private fun calculateBounds(coordinates: List<Point>): com.mapbox.geojson.BoundingBox {
        var minLat = coordinates[0].latitude()
        var maxLat = coordinates[0].latitude()
        var minLon = coordinates[0].longitude()
        var maxLon = coordinates[0].longitude()
        
        for (coord in coordinates) {
            minLat = min(minLat, coord.latitude())
            maxLat = max(maxLat, coord.latitude())
            minLon = min(minLon, coord.longitude())
            maxLon = max(maxLon, coord.longitude())
        }
        
        return com.mapbox.geojson.BoundingBox.fromLngLats(minLon, minLat, maxLon, maxLat)
    }
    
    // MARK: - Markers
    
    fun addMarker(latitude: Double, longitude: Double, title: String? = null) {
        val pointAnnotation = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(longitude, latitude))
            .withIconImage("marker-icon")
        
        title?.let {
            pointAnnotation.withTextField(it)
        }
        
        pointAnnotationManager?.create(pointAnnotation)
    }
    
    fun clearMarkers() {
        pointAnnotationManager?.deleteAll()
    }
    
    // MARK: - Route Display
    
    fun displayRoute(mapView: MapView, route: Route) {
        // Clear existing routes
        polylineAnnotationManager?.deleteAll()
        clearMarkers()
        
        // Create polyline from route geometry
        val lineString = LineString.fromLngLats(route.geometry.coordinates)
        
        val polylineAnnotation = PolylineAnnotationOptions()
            .withLineString(lineString)
            .withLineColor(Color.parseColor("#2563EB")) // Navi blue
            .withLineWidth(6.0)
        
        polylineAnnotationManager?.create(polylineAnnotation)
        
        // Add start and end markers
        route.geometry.coordinates.firstOrNull()?.let { start ->
            addMarker(start.latitude(), start.longitude(), "Start")
        }
        route.geometry.coordinates.lastOrNull()?.let { end ->
            addMarker(end.latitude(), end.longitude(), "Destination")
        }
        
        // Fit route in view
        fitRouteInView(mapView, route)
        
        _currentRoute.value = route
    }
    
    fun clearRoute() {
        polylineAnnotationManager?.deleteAll()
        clearMarkers()
        _currentRoute.value = null
    }
    
    // MARK: - Route Calculation
    
    suspend fun calculateRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double
    ): Result<Route> {
        return try {
            val originString = "$originLon,$originLat"
            val destString = "$destLon,$destLat"
            
            val url = "https://api.mapbox.com/directions/v5/mapbox/driving/$originString;$destString?geometries=geojson&steps=true&access_token=$accessToken"
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(MapboxError.NetworkError("HTTP ${response.code}"))
            }
            
            val responseBody = response.body?.string() ?: return Result.failure(MapboxError.NoData)
            
            val directionsResponse = json.decodeFromString<DirectionsResponse>(responseBody)
            
            val route = directionsResponse.routes.firstOrNull()
                ?: return Result.failure(MapboxError.NoRouteFound)
            
            _currentRoute.value = route
            Result.success(route)
        } catch (e: Exception) {
            Result.failure(MapboxError.DecodingError(e.message ?: "Unknown error"))
        }
    }
    
    suspend fun calculateRouteWithWaypoints(waypoints: List<Pair<Double, Double>>): Result<Route> {
        if (waypoints.size < 2) {
            return Result.failure(MapboxError.InvalidWaypoints)
        }
        
        return try {
            val coordinatesString = waypoints.joinToString(";") { "${it.second},${it.first}" }
            
            val url = "https://api.mapbox.com/directions/v5/mapbox/driving/$coordinatesString?geometries=geojson&steps=true&access_token=$accessToken"
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(MapboxError.NetworkError("HTTP ${response.code}"))
            }
            
            val responseBody = response.body?.string() ?: return Result.failure(MapboxError.NoData)
            
            val directionsResponse = json.decodeFromString<DirectionsResponse>(responseBody)
            
            val route = directionsResponse.routes.firstOrNull()
                ?: return Result.failure(MapboxError.NoRouteFound)
            
            _currentRoute.value = route
            Result.success(route)
        } catch (e: Exception) {
            Result.failure(MapboxError.DecodingError(e.message ?: "Unknown error"))
        }
    }
    
    // MARK: - Map Style
    
    fun setMapStyle(mapView: MapView, style: MapStyle) {
        val styleUri = when (style) {
            MapStyle.STREETS -> Style.MAPBOX_STREETS
            MapStyle.SATELLITE -> Style.SATELLITE
            MapStyle.SATELLITE_STREETS -> Style.SATELLITE_STREETS
            MapStyle.DARK -> Style.DARK
            MapStyle.LIGHT -> Style.LIGHT
            MapStyle.OUTDOORS -> Style.OUTDOORS
        }
        
        mapView.getMapboxMap().loadStyleUri(styleUri)
    }
}

// MARK: - Supporting Types

enum class MapStyle {
    STREETS,
    SATELLITE,
    SATELLITE_STREETS,
    DARK,
    LIGHT,
    OUTDOORS
}

sealed class MapboxError : Exception() {
    object InvalidURL : MapboxError()
    data class NetworkError(val msg: String) : MapboxError()
    object NoData : MapboxError()
    object NoRouteFound : MapboxError()
    data class DecodingError(val msg: String) : MapboxError()
    object InvalidWaypoints : MapboxError()
    
    fun getMessage(): String = when (this) {
        is InvalidURL -> "Invalid URL for route calculation"
        is NetworkError -> "Network error: $msg"
        is NoData -> "No data received from Mapbox"
        is NoRouteFound -> "No route found between the locations"
        is DecodingError -> "Failed to decode response: $msg"
        is InvalidWaypoints -> "Invalid waypoints - need at least 2 points"
    }
}

// MARK: - Directions API Models

@Serializable
data class DirectionsResponse(
    val routes: List<Route>,
    val waypoints: List<Waypoint>? = null,
    val code: String
)

@Serializable
data class Route(
    val distance: Double, // meters
    val duration: Double, // seconds
    val geometry: Geometry,
    val legs: List<RouteLeg>,
    val weight: Double? = null,
    val weightName: String? = null
)

@Serializable
data class Geometry(
    val coordinates: List<@Serializable(with = PointSerializer::class) Point>,
    val type: String
)

@Serializable
data class RouteLeg(
    val distance: Double,
    val duration: Double,
    val steps: List<RouteStep>,
    val summary: String? = null
)

@Serializable
data class RouteStep(
    val distance: Double,
    val duration: Double,
    val geometry: Geometry,
    val name: String? = null,
    val mode: String,
    val maneuver: Maneuver
)

@Serializable
data class Maneuver(
    val location: List<Double>,
    val bearingBefore: Double? = null,
    val bearingAfter: Double? = null,
    val instruction: String,
    val type: String,
    val modifier: String? = null
)

@Serializable
data class Waypoint(
    val name: String,
    val location: List<Double>
)

// Route Instruction for navigation
data class RouteInstruction(
    val text: String,
    val distance: Double,
    val type: String,
    val modifier: String?,
    val latitude: Double,
    val longitude: Double
)

// Point Serializer for Mapbox Point
object PointSerializer : kotlinx.serialization.KSerializer<Point> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("Point", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
    
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Point) {
        encoder.encodeString("${value.longitude()},${value.latitude()}")
    }
    
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Point {
        val coords = decoder.decodeString().split(",")
        return Point.fromLngLat(coords[0].toDouble(), coords[1].toDouble())
    }
}

package com.navi.domain.models

import java.util.Date

data class Route(
    val id: String,
    val origin: Coordinate,
    val destination: Coordinate,
    val waypoints: List<Coordinate> = emptyList(),
    val distance: Double, // meters
    val duration: Double, // seconds
    val trafficDuration: Double? = null, // seconds with traffic
    val polyline: String,
    val steps: List<RouteStep> = emptyList(),
    val alternatives: List<RouteAlternative>? = null,
    val createdAt: Date
)

data class RouteStep(
    val id: String,
    val instruction: String,
    val distance: Double,
    val duration: Double,
    val coordinate: Coordinate,
    val maneuver: Maneuver,
    val streetName: String? = null
)

data class Maneuver(
    val type: ManeuverType,
    val modifier: String? = null,
    val bearingBefore: Double? = null,
    val bearingAfter: Double? = null
)

enum class ManeuverType {
    TURN,
    NEW_NAME,
    DEPART,
    ARRIVE,
    MERGE,
    ON_RAMP,
    OFF_RAMP,
    FORK,
    END_OF_ROAD,
    CONTINUE,
    ROUNDABOUT,
    ROTARY,
    ROUNDABOUT_TURN,
    EXIT_ROUNDABOUT,
    EXIT_ROTARY
}

data class RouteAlternative(
    val id: String,
    val distance: Double,
    val duration: Double,
    val trafficDuration: Double? = null,
    val description: String,
    val polyline: String
)

data class SavedRoute(
    val id: String,
    val name: String,
    val route: Route,
    val userId: String,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    val savedAt: Date
)

data class ActiveNavigation(
    val route: Route,
    val currentStepIndex: Int,
    val remainingDistance: Double,
    val remainingDuration: Double,
    val currentSpeed: Double? = null,
    val speedLimit: Double? = null,
    val nextManeuver: RouteStep? = null,
    val isOffRoute: Boolean = false
)

data class TrafficIncident(
    val id: String,
    val type: IncidentType,
    val severity: Severity,
    val description: String,
    val coordinate: Coordinate,
    val startTime: Date,
    val endTime: Date? = null,
    val affectedRoads: List<String> = emptyList()
)

enum class IncidentType {
    ACCIDENT,
    ROAD_CLOSURE,
    CONSTRUCTION,
    CONGESTION,
    WEATHER_HAZARD,
    POLICE_ACTIVITY,
    SPEED_CAMERA
}

enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class RouteOptions(
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false
)

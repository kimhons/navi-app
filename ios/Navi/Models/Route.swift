import Foundation
import CoreLocation

struct Route: Codable, Identifiable {
    let id: String
    let origin: Coordinate
    let destination: Coordinate
    let waypoints: [Coordinate]
    let distance: Double // meters
    let duration: Double // seconds
    let trafficDuration: Double? // seconds with traffic
    let polyline: String
    let steps: [RouteStep]
    let alternatives: [RouteAlternative]?
    let createdAt: Date
}

struct RouteStep: Codable, Identifiable {
    let id: String
    let instruction: String
    let distance: Double
    let duration: Double
    let coordinate: Coordinate
    let maneuver: Maneuver
    let streetName: String?
}

struct Maneuver: Codable {
    let type: ManeuverType
    let modifier: String?
    let bearingBefore: Double?
    let bearingAfter: Double?
    
    enum ManeuverType: String, Codable {
        case turn
        case newName = "new name"
        case depart
        case arrive
        case merge
        case onRamp = "on ramp"
        case offRamp = "off ramp"
        case fork
        case endOfRoad = "end of road"
        case continue_
        case roundabout
        case rotary
        case roundaboutTurn = "roundabout turn"
        case exitRoundabout = "exit roundabout"
        case exitRotary = "exit rotary"
    }
}

struct RouteAlternative: Codable, Identifiable {
    let id: String
    let distance: Double
    let duration: Double
    let trafficDuration: Double?
    let description: String
    let polyline: String
}

struct SavedRoute: Codable, Identifiable {
    let id: String
    let name: String
    let route: Route
    let userId: String
    let notes: String?
    let isFavorite: Bool
    let savedAt: Date
}

struct ActiveNavigation: Codable {
    let route: Route
    let currentStepIndex: Int
    let remainingDistance: Double
    let remainingDuration: Double
    let currentSpeed: Double?
    let speedLimit: Double?
    let nextManeuver: RouteStep?
    let isOffRoute: Bool
}

struct TrafficIncident: Codable, Identifiable {
    let id: String
    let type: IncidentType
    let severity: Severity
    let description: String
    let coordinate: Coordinate
    let startTime: Date
    let endTime: Date?
    let affectedRoads: [String]
    
    enum IncidentType: String, Codable {
        case accident
        case roadClosure
        case construction
        case congestion
        case weatherHazard
        case policeActivity
        case speedCamera
    }
    
    enum Severity: String, Codable {
        case low
        case medium
        case high
        case critical
    }
}

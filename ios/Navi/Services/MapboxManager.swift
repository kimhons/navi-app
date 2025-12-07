import Foundation
import MapboxMaps
import CoreLocation
import Combine

/// Mapbox Manager - Handles all Mapbox SDK interactions for map rendering and navigation
class MapboxManager: ObservableObject {
    static let shared = MapboxManager()
    
    // MARK: - Published Properties
    @Published var mapView: MapView?
    @Published var currentRoute: Route?
    @Published var isNavigating = false
    @Published var currentInstruction: RouteInstruction?
    @Published var remainingDistance: Double = 0
    @Published var remainingDuration: Double = 0
    @Published var error: MapboxError?
    
    // MARK: - Private Properties
    private var pointAnnotationManager: PointAnnotationManager?
    private var polylineAnnotationManager: PolylineAnnotationManager?
    private var cancellables = Set<AnyCancellable>()
    
    // Mapbox Access Token - Replace with your token
    private let accessToken = "YOUR_MAPBOX_ACCESS_TOKEN"
    
    // MARK: - Initialization
    private init() {
        // Configure Mapbox
        MapboxOptions.accessToken = accessToken
    }
    
    // MARK: - Map Setup
    
    /// Initialize map view with default configuration
    func createMapView(frame: CGRect) -> MapView {
        let resourceOptions = ResourceOptions(accessToken: accessToken)
        let mapInitOptions = MapInitOptions(
            resourceOptions: resourceOptions,
            styleURI: .streets
        )
        
        let mapView = MapView(frame: frame, mapInitOptions: mapInitOptions)
        self.mapView = mapView
        
        // Setup annotation managers
        setupAnnotationManagers()
        
        return mapView
    }
    
    /// Setup annotation managers for markers and routes
    private func setupAnnotationManagers() {
        guard let mapView = mapView else { return }
        
        pointAnnotationManager = mapView.annotations.makePointAnnotationManager()
        polylineAnnotationManager = mapView.annotations.makePolylineAnnotationManager()
    }
    
    // MARK: - Camera Control
    
    /// Center map on user location
    func centerOnUserLocation(_ location: CLLocationCoordinate2D, zoom: Double = 15.0) {
        guard let mapView = mapView else { return }
        
        let cameraOptions = CameraOptions(
            center: location,
            zoom: zoom,
            bearing: 0,
            pitch: 0
        )
        
        mapView.camera.ease(to: cameraOptions, duration: 1.0)
    }
    
    /// Fit map to show entire route
    func fitRouteInView(route: Route, padding: UIEdgeInsets = UIEdgeInsets(top: 100, left: 50, bottom: 100, right: 50)) {
        guard let mapView = mapView else { return }
        
        let coordinates = route.geometry.coordinates
        guard !coordinates.isEmpty else { return }
        
        let coordinateBounds = CoordinateBounds(coordinates: coordinates)
        
        let cameraOptions = mapView.mapboxMap.camera(
            for: coordinateBounds,
            padding: padding,
            bearing: nil,
            pitch: nil
        )
        
        mapView.camera.ease(to: cameraOptions, duration: 1.0)
    }
    
    // MARK: - Markers
    
    /// Add a marker at a location
    func addMarker(at coordinate: CLLocationCoordinate2D, title: String? = nil, color: UIColor = .red) {
        guard let pointAnnotationManager = pointAnnotationManager else { return }
        
        var annotation = PointAnnotation(coordinate: coordinate)
        annotation.textField = title
        annotation.textColor = StyleColor(.black)
        annotation.iconImage = "marker-icon"
        
        pointAnnotationManager.annotations.append(annotation)
    }
    
    /// Clear all markers
    func clearMarkers() {
        pointAnnotationManager?.annotations.removeAll()
    }
    
    // MARK: - Route Display
    
    /// Display route on map
    func displayRoute(_ route: Route) {
        guard let polylineAnnotationManager = polylineAnnotationManager else { return }
        
        // Clear existing routes
        polylineAnnotationManager.annotations.removeAll()
        
        // Create polyline from route geometry
        var polyline = PolylineAnnotation(lineCoordinates: route.geometry.coordinates)
        polyline.lineColor = StyleColor(UIColor(red: 0.15, green: 0.39, blue: 0.92, alpha: 1.0)) // Navi blue
        polyline.lineWidth = 6.0
        polyline.lineJoin = .round
        polyline.lineCap = .round
        
        polylineAnnotationManager.annotations.append(polyline)
        
        // Add start and end markers
        if let start = route.geometry.coordinates.first {
            addMarker(at: start, title: "Start", color: .green)
        }
        if let end = route.geometry.coordinates.last {
            addMarker(at: end, title: "Destination", color: .red)
        }
        
        // Fit route in view
        fitRouteInView(route: route)
        
        self.currentRoute = route
    }
    
    /// Clear route from map
    func clearRoute() {
        polylineAnnotationManager?.annotations.removeAll()
        clearMarkers()
        currentRoute = nil
    }
    
    // MARK: - Route Calculation
    
    /// Calculate route between two points
    func calculateRoute(from origin: CLLocationCoordinate2D, to destination: CLLocationCoordinate2D, completion: @escaping (Result<Route, MapboxError>) -> Void) {
        
        let originString = "\(origin.longitude),\(origin.latitude)"
        let destinationString = "\(destination.longitude),\(destination.latitude)"
        
        let urlString = "https://api.mapbox.com/directions/v5/mapbox/driving/\(originString);\(destinationString)?geometries=geojson&steps=true&access_token=\(accessToken)"
        
        guard let url = URL(string: urlString) else {
            completion(.failure(.invalidURL))
            return
        }
        
        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            if let error = error {
                DispatchQueue.main.async {
                    completion(.failure(.networkError(error.localizedDescription)))
                }
                return
            }
            
            guard let data = data else {
                DispatchQueue.main.async {
                    completion(.failure(.noData))
                }
                return
            }
            
            do {
                let decoder = JSONDecoder()
                let directionsResponse = try decoder.decode(DirectionsResponse.self, from: data)
                
                guard let route = directionsResponse.routes.first else {
                    DispatchQueue.main.async {
                        completion(.failure(.noRouteFound))
                    }
                    return
                }
                
                DispatchQueue.main.async {
                    self?.currentRoute = route
                    completion(.success(route))
                }
            } catch {
                DispatchQueue.main.async {
                    completion(.failure(.decodingError(error.localizedDescription)))
                }
            }
        }.resume()
    }
    
    /// Calculate route with waypoints
    func calculateRouteWithWaypoints(waypoints: [CLLocationCoordinate2D], completion: @escaping (Result<Route, MapboxError>) -> Void) {
        
        guard waypoints.count >= 2 else {
            completion(.failure(.invalidWaypoints))
            return
        }
        
        let coordinatesString = waypoints.map { "\($0.longitude),\($0.latitude)" }.joined(separator: ";")
        
        let urlString = "https://api.mapbox.com/directions/v5/mapbox/driving/\(coordinatesString)?geometries=geojson&steps=true&access_token=\(accessToken)"
        
        guard let url = URL(string: urlString) else {
            completion(.failure(.invalidURL))
            return
        }
        
        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            if let error = error {
                DispatchQueue.main.async {
                    completion(.failure(.networkError(error.localizedDescription)))
                }
                return
            }
            
            guard let data = data else {
                DispatchQueue.main.async {
                    completion(.failure(.noData))
                }
                return
            }
            
            do {
                let decoder = JSONDecoder()
                let directionsResponse = try decoder.decode(DirectionsResponse.self, from: data)
                
                guard let route = directionsResponse.routes.first else {
                    DispatchQueue.main.async {
                        completion(.failure(.noRouteFound))
                    }
                    return
                }
                
                DispatchQueue.main.async {
                    self?.currentRoute = route
                    completion(.success(route))
                }
            } catch {
                DispatchQueue.main.async {
                    completion(.failure(.decodingError(error.localizedDescription)))
                }
            }
        }.resume()
    }
    
    // MARK: - Map Style
    
    /// Change map style
    func setMapStyle(_ style: MapStyle) {
        guard let mapView = mapView else { return }
        
        let styleURI: StyleURI
        switch style {
        case .streets:
            styleURI = .streets
        case .satellite:
            styleURI = .satellite
        case .satelliteStreets:
            styleURI = .satelliteStreets
        case .dark:
            styleURI = .dark
        case .light:
            styleURI = .light
        case .outdoors:
            styleURI = .outdoors
        }
        
        mapView.mapboxMap.loadStyleURI(styleURI)
    }
    
    // MARK: - Traffic
    
    /// Toggle traffic layer
    func toggleTraffic(enabled: Bool) {
        guard let mapView = mapView else { return }
        
        if enabled {
            // Add traffic layer
            try? mapView.mapboxMap.style.addLayer(TrafficLayer())
        } else {
            // Remove traffic layer
            try? mapView.mapboxMap.style.removeLayer(withId: "traffic")
        }
    }
}

// MARK: - Supporting Types

/// Map style options
enum MapStyle {
    case streets
    case satellite
    case satelliteStreets
    case dark
    case light
    case outdoors
}

/// Mapbox error types
enum MapboxError: Error, LocalizedError {
    case invalidURL
    case networkError(String)
    case noData
    case noRouteFound
    case decodingError(String)
    case invalidWaypoints
    
    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid URL for route calculation"
        case .networkError(let message):
            return "Network error: \(message)"
        case .noData:
            return "No data received from Mapbox"
        case .noRouteFound:
            return "No route found between the locations"
        case .decodingError(let message):
            return "Failed to decode response: \(message)"
        case .invalidWaypoints:
            return "Invalid waypoints - need at least 2 points"
        }
    }
}

// MARK: - Directions API Models

struct DirectionsResponse: Codable {
    let routes: [Route]
    let waypoints: [Waypoint]?
    let code: String
}

struct Route: Codable {
    let distance: Double // meters
    let duration: Double // seconds
    let geometry: Geometry
    let legs: [RouteLeg]
    let weight: Double?
    let weightName: String?
}

struct Geometry: Codable {
    let coordinates: [CLLocationCoordinate2D]
    let type: String
    
    enum CodingKeys: String, CodingKey {
        case coordinates
        case type
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        type = try container.decode(String.self, forKey: .type)
        
        let coordArrays = try container.decode([[Double]].self, forKey: .coordinates)
        coordinates = coordArrays.map { CLLocationCoordinate2D(latitude: $0[1], longitude: $0[0]) }
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(type, forKey: .type)
        let coordArrays = coordinates.map { [$0.longitude, $0.latitude] }
        try container.encode(coordArrays, forKey: .coordinates)
    }
}

struct RouteLeg: Codable {
    let distance: Double
    let duration: Double
    let steps: [RouteStep]
    let summary: String?
}

struct RouteStep: Codable {
    let distance: Double
    let duration: Double
    let geometry: Geometry
    let name: String?
    let mode: String
    let maneuver: Maneuver
}

struct Maneuver: Codable {
    let location: [Double]
    let bearingBefore: Double?
    let bearingAfter: Double?
    let instruction: String
    let type: String
    let modifier: String?
    
    enum CodingKeys: String, CodingKey {
        case location
        case bearingBefore = "bearing_before"
        case bearingAfter = "bearing_after"
        case instruction
        case type
        case modifier
    }
}

struct Waypoint: Codable {
    let name: String
    let location: [Double]
}

// MARK: - Route Instruction (for navigation)
struct RouteInstruction {
    let text: String
    let distance: Double
    let type: String
    let modifier: String?
    let location: CLLocationCoordinate2D
}

// MARK: - Traffic Layer (placeholder)
struct TrafficLayer: Layer {
    var id: String = "traffic"
    var type: LayerType = .line
    var filter: Expression?
    var source: String?
    var sourceLayer: String?
    var minZoom: Double?
    var maxZoom: Double?
}

// MARK: - CoordinateBounds Extension
extension CoordinateBounds {
    init(coordinates: [CLLocationCoordinate2D]) {
        guard !coordinates.isEmpty else {
            self.init(southwest: CLLocationCoordinate2D(latitude: 0, longitude: 0),
                     northeast: CLLocationCoordinate2D(latitude: 0, longitude: 0))
            return
        }
        
        var minLat = coordinates[0].latitude
        var maxLat = coordinates[0].latitude
        var minLon = coordinates[0].longitude
        var maxLon = coordinates[0].longitude
        
        for coord in coordinates {
            minLat = min(minLat, coord.latitude)
            maxLat = max(maxLat, coord.latitude)
            minLon = min(minLon, coord.longitude)
            maxLon = max(maxLon, coord.longitude)
        }
        
        self.init(
            southwest: CLLocationCoordinate2D(latitude: minLat, longitude: minLon),
            northeast: CLLocationCoordinate2D(latitude: maxLat, longitude: maxLon)
        )
    }
}

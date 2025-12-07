import Foundation
import CoreLocation
import Combine

/// Location Manager - Handles user location tracking and permissions
class LocationManager: NSObject, ObservableObject {
    static let shared = LocationManager()
    
    // MARK: - Published Properties
    @Published var userLocation: CLLocationCoordinate2D?
    @Published var heading: CLHeading?
    @Published var speed: CLLocationSpeed = 0
    @Published var altitude: CLLocationDistance = 0
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published var isTracking = false
    @Published var error: LocationError?
    
    // MARK: - Private Properties
    private let locationManager = CLLocationManager()
    private var cancellables = Set<AnyCancellable>()
    
    // Location history for route tracking
    private var locationHistory: [CLLocation] = []
    private let maxHistoryCount = 100
    
    // MARK: - Initialization
    private override init() {
        super.init()
        setupLocationManager()
    }
    
    // MARK: - Setup
    
    private func setupLocationManager() {
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.distanceFilter = 10 // Update every 10 meters
        locationManager.activityType = .automotiveNavigation
        locationManager.pausesLocationUpdatesAutomatically = false
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.showsBackgroundLocationIndicator = true
        
        // Check initial authorization status
        authorizationStatus = locationManager.authorizationStatus
    }
    
    // MARK: - Permission Management
    
    /// Request location permissions
    func requestPermissions() {
        switch authorizationStatus {
        case .notDetermined:
            locationManager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse:
            locationManager.requestAlwaysAuthorization()
        case .denied, .restricted:
            error = .permissionDenied
        case .authorizedAlways:
            // Already have full permissions
            break
        @unknown default:
            break
        }
    }
    
    /// Check if location services are available
    var isLocationServicesEnabled: Bool {
        return CLLocationManager.locationServicesEnabled()
    }
    
    /// Check if we have location permission
    var hasLocationPermission: Bool {
        return authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways
    }
    
    // MARK: - Location Tracking
    
    /// Start tracking user location
    func startTracking() {
        guard isLocationServicesEnabled else {
            error = .servicesDisabled
            return
        }
        
        guard hasLocationPermission else {
            requestPermissions()
            return
        }
        
        locationManager.startUpdatingLocation()
        locationManager.startUpdatingHeading()
        isTracking = true
    }
    
    /// Stop tracking user location
    func stopTracking() {
        locationManager.stopUpdatingLocation()
        locationManager.stopUpdatingHeading()
        isTracking = false
    }
    
    /// Get current location once
    func getCurrentLocation(completion: @escaping (CLLocationCoordinate2D?) -> Void) {
        guard isLocationServicesEnabled else {
            error = .servicesDisabled
            completion(nil)
            return
        }
        
        guard hasLocationPermission else {
            requestPermissions()
            completion(nil)
            return
        }
        
        if let location = userLocation {
            completion(location)
        } else {
            // Request one-time location
            locationManager.requestLocation()
            
            // Wait for location update
            var cancellable: AnyCancellable?
            cancellable = $userLocation
                .compactMap { $0 }
                .first()
                .sink { location in
                    completion(location)
                    cancellable?.cancel()
                }
        }
    }
    
    // MARK: - Location History
    
    /// Add location to history
    private func addToHistory(_ location: CLLocation) {
        locationHistory.append(location)
        
        // Keep only recent locations
        if locationHistory.count > maxHistoryCount {
            locationHistory.removeFirst()
        }
    }
    
    /// Get location history
    func getLocationHistory() -> [CLLocation] {
        return locationHistory
    }
    
    /// Clear location history
    func clearHistory() {
        locationHistory.removeAll()
    }
    
    // MARK: - Distance Calculations
    
    /// Calculate distance between two coordinates
    func distance(from: CLLocationCoordinate2D, to: CLLocationCoordinate2D) -> CLLocationDistance {
        let fromLocation = CLLocation(latitude: from.latitude, longitude: from.longitude)
        let toLocation = CLLocation(latitude: to.latitude, longitude: to.longitude)
        return fromLocation.distance(from: toLocation)
    }
    
    /// Calculate distance from current location to a coordinate
    func distanceFromCurrentLocation(to coordinate: CLLocationCoordinate2D) -> CLLocationDistance? {
        guard let userLocation = userLocation else { return nil }
        return distance(from: userLocation, to: coordinate)
    }
    
    // MARK: - Bearing Calculations
    
    /// Calculate bearing between two coordinates
    func bearing(from: CLLocationCoordinate2D, to: CLLocationCoordinate2D) -> Double {
        let lat1 = from.latitude.toRadians()
        let lon1 = from.longitude.toRadians()
        let lat2 = to.latitude.toRadians()
        let lon2 = to.longitude.toRadians()
        
        let dLon = lon2 - lon1
        
        let y = sin(dLon) * cos(lat2)
        let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        
        let radiansBearing = atan2(y, x)
        let degreesBearing = radiansBearing.toDegrees()
        
        return (degreesBearing + 360).truncatingRemainder(dividingBy: 360)
    }
    
    // MARK: - Geofencing
    
    /// Start monitoring a region
    func startMonitoring(region: CLCircularRegion) {
        guard CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self) else {
            error = .geofencingNotAvailable
            return
        }
        
        locationManager.startMonitoring(for: region)
    }
    
    /// Stop monitoring a region
    func stopMonitoring(region: CLCircularRegion) {
        locationManager.stopMonitoring(for: region)
    }
    
    /// Stop monitoring all regions
    func stopMonitoringAllRegions() {
        for region in locationManager.monitoredRegions {
            locationManager.stopMonitoring(for: region)
        }
    }
}

// MARK: - CLLocationManagerDelegate

extension LocationManager: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        
        // Update published properties
        userLocation = location.coordinate
        speed = location.speed
        altitude = location.altitude
        
        // Add to history
        addToHistory(location)
        
        // Clear error
        error = nil
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        heading = newHeading
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        if let clError = error as? CLError {
            switch clError.code {
            case .denied:
                self.error = .permissionDenied
            case .locationUnknown:
                self.error = .locationUnknown
            case .network:
                self.error = .networkError
            default:
                self.error = .unknown(error.localizedDescription)
            }
        } else {
            self.error = .unknown(error.localizedDescription)
        }
    }
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        
        // Start tracking if we just got permission
        if hasLocationPermission && !isTracking {
            startTracking()
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        NotificationCenter.default.post(
            name: .didEnterRegion,
            object: nil,
            userInfo: ["region": region]
        )
    }
    
    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        NotificationCenter.default.post(
            name: .didExitRegion,
            object: nil,
            userInfo: ["region": region]
        )
    }
}

// MARK: - Location Error

enum LocationError: Error, LocalizedError {
    case servicesDisabled
    case permissionDenied
    case locationUnknown
    case networkError
    case geofencingNotAvailable
    case unknown(String)
    
    var errorDescription: String? {
        switch self {
        case .servicesDisabled:
            return "Location services are disabled. Please enable them in Settings."
        case .permissionDenied:
            return "Location permission denied. Please enable location access in Settings."
        case .locationUnknown:
            return "Unable to determine your location. Please try again."
        case .networkError:
            return "Network error while getting location. Please check your connection."
        case .geofencingNotAvailable:
            return "Geofencing is not available on this device."
        case .unknown(let message):
            return "Location error: \(message)"
        }
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let didEnterRegion = Notification.Name("didEnterRegion")
    static let didExitRegion = Notification.Name("didExitRegion")
}

// MARK: - Double Extensions

extension Double {
    func toRadians() -> Double {
        return self * .pi / 180.0
    }
    
    func toDegrees() -> Double {
        return self * 180.0 / .pi
    }
}

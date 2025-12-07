import Foundation
import CoreLocation
import Combine
import MapboxMaps

/// Map ViewModel - Manages map screen state and user interactions
class MapViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var userLocation: CLLocationCoordinate2D?
    @Published var currentRoute: Route?
    @Published var isNavigating = false
    @Published var searchResults: [Place] = []
    @Published var selectedPlace: Place?
    @Published var showRoutePreview = false
    @Published var mapStyle: MapStyle = .streets
    @Published var showTraffic = false
    @Published var error: String?
    @Published var isLoading = false
    
    // Navigation state
    @Published var currentInstruction: String = ""
    @Published var remainingDistance: String = ""
    @Published var remainingTime: String = ""
    @Published var eta: String = ""
    
    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()
    private let mapboxManager = MapboxManager.shared
    private let locationManager = LocationManager.shared
    private let navigationManager = NavigationManager.shared
    
    // MARK: - Initialization
    init() {
        setupBindings()
    }
    
    // MARK: - Setup
    
    private func setupBindings() {
        // Bind location updates
        locationManager.$userLocation
            .assign(to: &$userLocation)
        
        // Bind navigation state
        navigationManager.$isNavigating
            .assign(to: &$isNavigating)
        
        navigationManager.$currentRoute
            .assign(to: &$currentRoute)
        
        navigationManager.$currentInstruction
            .assign(to: &$currentInstruction)
        
        // Bind formatted navigation info
        navigationManager.$remainingDistance
            .map { _ in navigationManager.getFormattedRemainingDistance() }
            .assign(to: &$remainingDistance)
        
        navigationManager.$remainingDuration
            .map { _ in navigationManager.getFormattedRemainingTime() }
            .assign(to: &$remainingTime)
        
        navigationManager.$estimatedArrivalTime
            .map { _ in navigationManager.getFormattedETA() }
            .assign(to: &$eta)
    }
    
    // MARK: - Location Actions
    
    func requestLocationPermission() {
        locationManager.requestPermissions()
    }
    
    func startLocationTracking() {
        locationManager.startTracking()
    }
    
    func centerOnUserLocation() {
        guard let location = userLocation else { return }
        mapboxManager.centerOnUserLocation(location)
    }
    
    // MARK: - Route Actions
    
    func calculateRoute(to destination: CLLocationCoordinate2D) {
        guard let origin = userLocation else {
            error = "Unable to get your current location"
            return
        }
        
        isLoading = true
        
        mapboxManager.calculateRoute(from: origin, to: destination) { [weak self] result in
            self?.isLoading = false
            
            switch result {
            case .success(let route):
                self?.currentRoute = route
                self?.showRoutePreview = true
                self?.mapboxManager.displayRoute(route)
            case .failure(let error):
                self?.error = error.localizedDescription
            }
        }
    }
    
    func calculateRouteToPlace(_ place: Place) {
        let destination = CLLocationCoordinate2D(
            latitude: place.latitude,
            longitude: place.longitude
        )
        calculateRoute(to: destination)
    }
    
    func startNavigation() {
        guard let route = currentRoute else { return }
        
        navigationManager.startNavigation(route: route)
        showRoutePreview = false
    }
    
    func stopNavigation() {
        navigationManager.stopNavigation()
        mapboxManager.clearRoute()
        currentRoute = nil
    }
    
    func pauseNavigation() {
        navigationManager.pauseNavigation()
    }
    
    func resumeNavigation() {
        navigationManager.resumeNavigation()
    }
    
    // MARK: - Map Style
    
    func changeMapStyle(_ style: MapStyle) {
        mapStyle = style
        mapboxManager.setMapStyle(style)
    }
    
    func toggleTraffic() {
        showTraffic.toggle()
        mapboxManager.toggleTraffic(enabled: showTraffic)
    }
    
    // MARK: - Place Selection
    
    func selectPlace(_ place: Place) {
        selectedPlace = place
        
        // Add marker on map
        let coordinate = CLLocationCoordinate2D(
            latitude: place.latitude,
            longitude: place.longitude
        )
        mapboxManager.clearMarkers()
        mapboxManager.addMarker(at: coordinate, title: place.name)
        
        // Center on place
        mapboxManager.centerOnUserLocation(coordinate, zoom: 14)
    }
    
    func deselectPlace() {
        selectedPlace = nil
        mapboxManager.clearMarkers()
    }
    
    // MARK: - Voice Guidance
    
    func toggleVoiceGuidance() {
        navigationManager.toggleVoiceGuidance()
    }
    
    func setVoiceVolume(_ volume: Float) {
        navigationManager.setVoiceVolume(volume)
    }
}

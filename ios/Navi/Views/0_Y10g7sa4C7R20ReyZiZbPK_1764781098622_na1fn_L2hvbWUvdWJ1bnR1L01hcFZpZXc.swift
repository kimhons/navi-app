//
//  MapView.swift
//  NavigationApp
//
//  Created by Manus on 2025-12-03.
//

import SwiftUI
import MapboxMaps
import CoreLocation

// MARK: - 1. Stubs for External Services

/// A stub for the backend API service.
/// In a real application, this would handle network requests.
class APIService: ObservableObject {
    static let shared = APIService()
    
    // Placeholder for a data model, e.g., a traffic incident
    struct Incident: Identifiable {
        let id = UUID()
        let coordinate: CLLocationCoordinate2D
        let title: String
        let severity: String
    }
    
    @Published var incidents: [Incident] = []
    
    private init() {
        // Simulate fetching data
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            self.incidents = [
                Incident(coordinate: CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194), title: "Accident", severity: "High"),
                Incident(coordinate: CLLocationCoordinate2D(latitude: 37.7858, longitude: -122.4064), title: "Road Work", severity: "Medium")
            ]
        }
    }
    
    func fetchIncidents() {
        // Real-time update simulation
        print("Fetching real-time incidents...")
    }
}

/// Manages the user's current location and location permissions.
class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    @Published var location: CLLocation?
    @Published var authorizationStatus: CLAuthorizationStatus?
    
    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }
    
    func requestLocation() {
        manager.requestLocation()
    }
    
    // MARK: - CLLocationManagerDelegate
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let newLocation = locations.last else { return }
        DispatchQueue.main.async {
            self.location = newLocation
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location Manager failed with error: \(error.localizedDescription)")
    }
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        DispatchQueue.main.async {
            self.authorizationStatus = manager.authorizationStatus
        }
    }
}

// MARK: - 2. ViewModel

class MapViewModel: ObservableObject {
    // MARK: - Published Properties
    
    @Published var isLoading: Bool = true
    @Published var errorMessage: String?
    @Published var isShowingDetails: Bool = false
    @Published var selectedIncident: APIService.Incident?
    @Published var isTrafficOverlayActive: Bool = false
    @Published var mapCameraPosition: MapCameraPosition = .userLocation(followsHeading: true, fallback: .camera(MapboxMaps.CameraOptions(center: CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194), zoom: 12)))
    
    // Dependencies
    @StateObject var locationManager = LocationManager()
    @StateObject var apiService = APIService.shared
    
    // MARK: - Initialization
    
    init() {
        // Simulate initial loading
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            self.isLoading = false
        }
        
        // Observe location changes to update camera
        locationManager.$location
            .compactMap { $0 }
            .sink { [weak self] newLocation in
                guard let self = self else { return }
                // Only update camera if it's the initial load or user is following
                if self.isLoading || self.mapCameraPosition.isUserLocationFollowing {
                    self.mapCameraPosition = .camera(MapboxMaps.CameraOptions(center: newLocation.coordinate, zoom: 15))
                }
            }
            .store(in: &cancellables)
    }
    
    // Simple property to check if the user location is available
    var userLocationCoordinate: CLLocationCoordinate2D? {
        locationManager.location?.coordinate
    }
    
    // MARK: - Actions
    
    func toggleTrafficOverlay() {
        isTrafficOverlayActive.toggle()
        // In a real app, this would update the Mapbox style layers
    }
    
    func recenterMap() {
        guard let location = locationManager.location else {
            errorMessage = "Cannot recenter: Location not available."
            return
        }
        // Set camera to follow user location
        mapCameraPosition = .userLocation(followsHeading: true, fallback: .camera(MapboxMaps.CameraOptions(center: location.coordinate, zoom: 15)))
    }
    
    func selectIncident(_ incident: APIService.Incident) {
        selectedIncident = incident
        isShowingDetails = true
    }
    
    // For reactive programming with Combine
    private var cancellables = Set<AnyCancellable>()
}

// MARK: - 3. View

struct MapView: View {
    @StateObject var viewModel = MapViewModel()
    
    // Navi Blue color
    let naviBlue = Color(hex: "#2563EB")
    
    var body: some View {
        ZStack {
            // 3.1. Mapbox Map
            Map(initialViewport: .camera(MapboxMaps.CameraOptions(center: CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194), zoom: 12)))
                .mapStyle(.standard)
                .mapCamera(position: $viewModel.mapCameraPosition)
                .onMapTap { context in
                    // Handle map tap for potential feature selection
                    print("Map tapped at: \(context.point)")
                }
                .overlay(alignment: .top) {
                    // 3.2. Search Bar
                    SearchBarView(text: .constant(""))
                        .padding(.horizontal)
                        .padding(.top, 50)
                }
                .overlay(alignment: .bottomTrailing) {
                    // 3.3. Floating Action Buttons (FABs)
                    VStack(spacing: 15) {
                        LayerControlsButton(viewModel: viewModel, naviBlue: naviBlue)
                        RecenterButton(viewModel: viewModel, naviBlue: naviBlue)
                    }
                    .padding(.trailing)
                    .padding(.bottom, 100) // Space for the bottom sheet
                }
            
            // 3.4. Loading and Error States
            if viewModel.isLoading {
                LoadingView()
            }
            
            if let error = viewModel.errorMessage {
                ErrorBanner(message: error)
            }
            
            // 3.5. Bottom Sheet for Details
            if viewModel.isShowingDetails, let incident = viewModel.selectedIncident {
                IncidentDetailSheet(incident: incident, isShowing: $viewModel.isShowingDetails, naviBlue: naviBlue)
            }
        }
        .edgesIgnoringSafeArea(.all)
        .onAppear {
            // Request location on appear
            viewModel.locationManager.requestLocation()
        }
        .alert(item: $viewModel.errorMessage) { error in
            Alert(title: Text("Error"), message: Text(error), dismissButton: .default(Text("OK")))
        }
    }
}

// MARK: - 4. Subviews and Helpers

/// Custom Search Bar View
struct SearchBarView: View {
    @Binding var text: String
    
    var body: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.gray)
            TextField("Search for places or addresses", text: $text)
                .accessibilityLabel("Search input field")
        }
        .padding(10)
        .background(Color(.systemBackground))
        .cornerRadius(10)
        .shadow(radius: 5)
    }
}

/// Floating Action Button for Layer Controls
struct LayerControlsButton: View {
    @ObservedObject var viewModel: MapViewModel
    let naviBlue: Color
    
    var body: some View {
        Button {
            viewModel.toggleTrafficOverlay()
        } label: {
            Image(systemName: viewModel.isTrafficOverlayActive ? "trafficlight.fill" : "trafficlight")
                .font(.title2)
                .padding(15)
                .background(naviBlue)
                .foregroundColor(.white)
                .clipShape(Circle())
                .shadow(radius: 5)
                .accessibilityLabel(viewModel.isTrafficOverlayActive ? "Turn off traffic overlay" : "Turn on traffic overlay")
        }
    }
}

/// Floating Action Button for Recenter
struct RecenterButton: View {
    @ObservedObject var viewModel: MapViewModel
    let naviBlue: Color
    
    var body: some View {
        Button {
            viewModel.recenterMap()
        } label: {
            Image(systemName: "location.fill")
                .font(.title2)
                .padding(15)
                .background(naviBlue)
                .foregroundColor(.white)
                .clipShape(Circle())
                .shadow(radius: 5)
                .accessibilityLabel("Recenter map to current location")
        }
    }
}

/// Bottom Sheet for Incident Details
struct IncidentDetailSheet: View {
    let incident: APIService.Incident
    @Binding var isShowing: Bool
    let naviBlue: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(incident.title)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.primary)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Button {
                    isShowing = false
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.gray)
                        .font(.title)
                        .accessibilityLabel("Close details")
                }
            }
            
            Text("Severity: \(incident.severity)")
                .font(.subheadline)
                .foregroundColor(incident.severity == "High" ? .red : .orange)
                .dynamicTypeSize(.medium)
            
            Text("A detailed description of the incident would go here. This bottom sheet provides a clean, modern way to display contextual information.")
                .font(.body)
                .foregroundColor(.secondary)
                .dynamicTypeSize(.medium)
            
            Button("View Route Impact") {
                // Action to show route impact
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(naviBlue)
            .foregroundColor(.white)
            .cornerRadius(8)
            .padding(.top, 10)
            .accessibilityLabel("View route impact of \(incident.title)")
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(20)
        .shadow(radius: 10)
        .padding(.horizontal)
        .padding(.bottom, 20)
        .transition(.move(edge: .bottom))
        .animation(.spring(), value: isShowing)
    }
}

/// Simple Loading View
struct LoadingView: View {
    var body: some View {
        VStack {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .white))
            Text("Loading Map Data...")
                .foregroundColor(.white)
                .dynamicTypeSize(.medium)
        }
        .padding(20)
        .background(Color.black.opacity(0.7))
        .cornerRadius(10)
        .transition(.opacity)
        .animation(.easeInOut, value: true)
    }
}

/// Simple Error Banner
struct ErrorBanner: View {
    let message: String
    
    var body: some View {
        Text(message)
            .foregroundColor(.white)
            .padding()
            .frame(maxWidth: .infinity)
            .background(Color.red)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .padding(.horizontal)
            .padding(.top, 10)
            .accessibilityLabel("Error: \(message)")
            .transition(.move(edge: .top))
            .animation(.easeInOut, value: message)
    }
}

// MARK: - 5. Extensions for Helpers

/// Extension to allow easy color initialization from hex string
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }
        
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

/// Extension to allow optional string binding for alerts
extension String: Identifiable {
    public var id: String { self }
}

/// Extension to check if MapCameraPosition is following user location
extension MapCameraPosition {
    var isUserLocationFollowing: Bool {
        if case .userLocation(let followsHeading, _) = self {
            return followsHeading
        }
        return false
    }
}

// MARK: - 6. Combine Imports (Required for ViewModel)

import Combine

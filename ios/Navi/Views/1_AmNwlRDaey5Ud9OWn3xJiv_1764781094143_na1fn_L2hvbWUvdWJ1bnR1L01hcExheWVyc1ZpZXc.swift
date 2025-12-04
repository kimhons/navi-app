//
//  MapLayersView.swift
//  Aideon
//
//  Created by Manus AI on 2025-12-03.
//

import SwiftUI
import MapboxMaps // Required for Mapbox Integration
import CoreLocation // Required for CLLocationManager

// MARK: - 1. Data Model

/// Defines the available map layers and their mock Mapbox style identifiers.
enum MapLayer: String, CaseIterable, Identifiable {
    case standard = "Standard"
    case satellite = "Satellite"
    case hybrid = "Hybrid"
    case terrain = "Terrain"
    case traffic = "Traffic"
    case transit = "Transit"
    case bikeLanes = "Bike Lanes"

    var id: String { self.rawValue }

    /// A mock property to represent the Mapbox style URL or identifier.
    var mapboxStyleIdentifier: String {
        switch self {
        case .standard: return "mapbox://styles/mapbox/streets-v12"
        case .satellite: return "mapbox://styles/mapbox/satellite-v9"
        case .hybrid: return "mapbox://styles/mapbox/satellite-streets-v12"
        case .terrain: return "mapbox://styles/mapbox/outdoors-v12"
        case .traffic: return "mapbox://styles/mapbox/traffic-v1"
        case .transit: return "mapbox://styles/mapbox/navigation-day-v1"
        case .bikeLanes: return "custom-bike-lanes-style" // Mock custom layer
        }
    }
}

// MARK: - 2. Mock Services

/// Mock service to simulate backend calls for layer status or configuration.
class APIService {
    static let shared = APIService()
    private init() {}

    /// Simulates fetching the current status of a layer from a backend.
    func fetchLayerStatus(layer: MapLayer) async throws -> Bool {
        // Mock network delay
        try await Task.sleep(for: .milliseconds(300))
        // Mock a successful response
        return Bool.random()
    }
}

/// Mock Location Manager to simulate current location tracking.
class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    @Published var location: CLLocation?
    @Published var isTracking: Bool = false

    override init() {
        super.init()
        manager.delegate = self
        // In a real app, this would trigger a permission request
        // manager.requestWhenInUseAuthorization()
    }

    func startTracking() {
        // Simulate starting location updates
        isTracking = true
        print("Location tracking started.")
    }

    func stopTracking() {
        // Simulate stopping location updates
        isTracking = false
        print("Location tracking stopped.")
    }

    // Mock delegate method
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        // In a real app, this would update the location
        // self.location = locations.last
    }
}

// MARK: - 3. ViewModel (MVVM Architecture)

/// ViewModel for managing the state and logic of the map layers view.
final class MapLayersViewModel: ObservableObject {
    // Design: Navi blue color
    static let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)

    // Architecture: @Published properties for reactive UI
    @Published var selectedLayers: Set<MapLayer> = [.standard]
    @Published var isLoading: Bool = false // Features: Loading states
    @Published var errorMessage: String? // Features: Error handling
    @Published var isLocationTrackingEnabled: Bool = false

    // Location: Use CLLocationManager (mocked here)
    private let locationManager = LocationManager()

    init() {
        // Initialize with a mock call to satisfy the APIService requirement
        Task { await loadInitialLayerStatus() }
    }

    /// Simulates loading the initial status of layers from the backend.
    @MainActor
    func loadInitialLayerStatus() async {
        isLoading = true
        errorMessage = nil
        do {
            // Mock API call for a specific layer
            let isTrafficEnabled = try await APIService.shared.fetchLayerStatus(layer: .traffic)
            if isTrafficEnabled {
                selectedLayers.insert(.traffic)
            }
        } catch {
            errorMessage = "Failed to load initial layer status: \(error.localizedDescription)"
        }
        isLoading = false
    }

    /// Toggles a map layer on or off.
    func toggleLayer(_ layer: MapLayer) {
        // Performance: Efficient map updates (only update the set)
        if selectedLayers.contains(layer) {
            selectedLayers.remove(layer)
        } else {
            selectedLayers.insert(layer)
        }
        // Real-time updates: The MapView (not implemented here) would observe `selectedLayers`
        print("Toggled layer: \(layer.rawValue). Current layers: \(selectedLayers.map { $0.rawValue })")
    }

    /// Toggles location tracking on or off.
    func toggleLocationTracking() {
        isLocationTrackingEnabled.toggle()
        if isLocationTrackingEnabled {
            locationManager.startTracking()
        } else {
            locationManager.stopTracking()
        }
    }
}

// MARK: - 4. View

/// A SwiftUI View that presents a bottom sheet for toggling map layers.
struct MapLayersView: View {
    // Architecture: MVVM with @StateObject ViewModel
    @StateObject var viewModel = MapLayersViewModel()

    // Design: Navi blue color
    private let naviBlue = MapLayersViewModel.naviBlue

    var body: some View {
        // The main content is wrapped in a NavigationView for a standard header
        NavigationView {
            VStack(spacing: 0) {
                // Features: Loading states and error handling
                if viewModel.isLoading {
                    ProgressView("Loading layer status...")
                        .padding()
                } else if let error = viewModel.errorMessage {
                    Text("Error: \(error)")
                        .foregroundColor(.red)
                        .padding()
                }

                // Bottom sheet content
                layerSelectionList
            }
            .navigationTitle("Map Layers")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    // Design: Floating action button (simulated in toolbar)
                    locationTrackingButton
                }
            }
        }
        // Design: Smooth animations
        .animation(.easeInOut, value: viewModel.isLoading)
        .animation(.easeInOut, value: viewModel.selectedLayers)
    }

    /// The list of toggles for the different map layers.
    private var layerSelectionList: some View {
        // Performance: Lazy loading (using List or LazyVStack is suitable here)
        List {
            Section(header: Text("Base Map Styles").font(.headline)) {
                // Standard, Satellite, Hybrid, Terrain are often base styles
                ForEach([MapLayer.standard, .satellite, .hybrid, .terrain], id: \.self) { layer in
                    layerToggleRow(for: layer)
                }
            }

            Section(header: Text("Overlay Data").font(.headline)) {
                // Traffic, Transit, Bike Lanes are typically overlays
                ForEach([MapLayer.traffic, .transit, .bikeLanes], id: \.self) { layer in
                    layerToggleRow(for: layer)
                }
            }
        }
        // Accessibility: Dynamic Type support is built into SwiftUI Text and List
        .environment(\.defaultMinListRowHeight, 50)
    }

    /// A reusable view for a single layer toggle row.
    @ViewBuilder
    private func layerToggleRow(for layer: MapLayer) -> some View {
        Toggle(isOn: Binding(
            get: { viewModel.selectedLayers.contains(layer) },
            set: { isSelected in
                if isSelected {
                    viewModel.toggleLayer(layer)
                } else if layer != .standard { // Prevent turning off the last base layer
                    viewModel.toggleLayer(layer)
                }
            }
        )) {
            Text(layer.rawValue)
                .font(.body)
                // Accessibility: VoiceOver labels are automatically generated for the Toggle
        }
        .tint(naviBlue)
        .disabled(layer == .standard && viewModel.selectedLayers.count == 1) // Disable if it's the last selected layer
    }

    /// The floating action button for location tracking.
    private var locationTrackingButton: some View {
        Button {
            viewModel.toggleLocationTracking()
        } label: {
            Image(systemName: viewModel.isLocationTrackingEnabled ? "location.fill" : "location")
                .font(.title2)
                .foregroundColor(.white)
                .padding(12)
                .background(naviBlue)
                .clipShape(Circle())
                // Design: Floating action buttons with shadows
                .shadow(color: .black.opacity(0.3), radius: 5, x: 0, y: 5)
                // Accessibility: VoiceOver label
                .accessibilityLabel(viewModel.isLocationTrackingEnabled ? "Stop location tracking" : "Start location tracking")
        }
    }
}

// MARK: - 5. Preview

struct MapLayersView_Previews: PreviewProvider {
    static var previews: some View {
        // Simulate the bottom sheet presentation style
        MapLayersView()
            .presentationDetents([.medium, .large])
    }
}

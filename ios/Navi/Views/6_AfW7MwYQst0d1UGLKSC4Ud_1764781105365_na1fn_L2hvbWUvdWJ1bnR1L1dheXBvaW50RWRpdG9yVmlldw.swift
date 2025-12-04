import SwiftUI
import CoreLocation
import Combine
import MapboxMaps // Required for Mapbox Integration

// MARK: - 1. Mock Dependencies

/// Mock structure for a Waypoint, conforming to necessary protocols.
struct Waypoint: Identifiable, Codable, Equatable {
    let id = UUID()
    var name: String
    var latitude: Double
    var longitude: Double
    
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

/// Mock for the backend API service.
class APIService {
    static let shared = APIService()
    
    enum APIError: Error, LocalizedError {
        case optimizationFailed
        case saveFailed
        
        var errorDescription: String? {
            switch self {
            case .optimizationFailed: return "Failed to optimize route. Please try again."
            case .saveFailed: return "Could not save the route to the server."
            }
        }
    }
    
    /// Simulates an asynchronous call to optimize the route.
    func optimizeRoute(waypoints: [Waypoint]) async throws -> [Waypoint] {
        try await Task.sleep(for: .seconds(1.5))
        if waypoints.count < 2 {
            throw APIError.optimizationFailed
        }
        // Simple mock: reverse the order to simulate a change
        return waypoints.reversed()
    }
    
    /// Simulates an asynchronous call to save the route.
    func saveRoute(waypoints: [Waypoint]) async throws {
        try await Task.sleep(for: .seconds(1))
        if waypoints.isEmpty {
            throw APIError.saveFailed
        }
        // Success
    }
}

// MARK: - 2. ViewModel (MVVM Architecture)

/// The ViewModel for WaypointEditorView, managing state and business logic.
final class WaypointEditorViewModel: ObservableObject {
    
    // MARK: - Published Properties
    @Published var waypoints: [Waypoint] = []
    @Published var isLoading: Bool = false
    @Published var error: APIService.APIError? = nil
    @Published var userLocation: CLLocationCoordinate2D? = nil
    @Published var isShowingWaypointList: Bool = true
    
    // Mapbox-specific state
    @Published var mapCameraPosition: MapboxMaps.CameraOptions? = nil
    
    // MARK: - Dependencies (Mocked)
    private let apiService = APIService.shared
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Initialization
    init() {
        // Mock initial waypoints
        waypoints = [
            Waypoint(name: "Start Point", latitude: 34.0522, longitude: -118.2437),
            Waypoint(name: "Mid Stop", latitude: 34.0622, longitude: -118.2537),
            Waypoint(name: "End Destination", latitude: 34.0722, longitude: -118.2637)
        ]
        
        // Start location tracking (mocked)
        startLocationUpdates()
        
        // Set initial map camera to center of waypoints
        if let center = waypoints.first?.coordinate {
            mapCameraPosition = MapboxMaps.CameraOptions(center: center, zoom: 10)
        }
    }
    
    // MARK: - Location Tracking (CLLocationManager Mock)
    private func startLocationUpdates() {
        // In a real app, this would involve setting up CLLocationManagerDelegate
        // and requesting authorization. Here, we mock a location update.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.userLocation = CLLocationCoordinate2D(latitude: 34.0522 + 0.01, longitude: -118.2437 + 0.01)
        }
    }
    
    // MARK: - Actions
    
    /// Adds a new waypoint at a mock location.
    func addWaypoint() {
        let newWaypoint = Waypoint(
            name: "New Stop \(waypoints.count + 1)",
            latitude: 34.05 + Double(waypoints.count) * 0.005,
            longitude: -118.24 - Double(waypoints.count) * 0.005
        )
        withAnimation(.spring()) {
            waypoints.append(newWaypoint)
        }
        // Move map to the new waypoint
        mapCameraPosition = MapboxMaps.CameraOptions(center: newWaypoint.coordinate, zoom: 12)
    }
    
    /// Moves waypoints in the list for reordering.
    func moveWaypoint(from source: IndexSet, to destination: Int) {
        withAnimation(.easeInOut) {
            waypoints.move(fromOffsets: source, toOffset: destination)
        }
    }
    
    /// Removes a waypoint at a specific index.
    func removeWaypoint(at offsets: IndexSet) {
        withAnimation(.easeInOut) {
            waypoints.remove(atOffsets: offsets)
        }
    }
    
    /// Calls the mock API to optimize the route order.
    func optimizeRoute() async {
        guard !isLoading else { return }
        isLoading = true
        error = nil
        
        do {
            let optimized = try await apiService.optimizeRoute(waypoints: waypoints)
            withAnimation(.spring()) {
                self.waypoints = optimized
            }
        } catch let apiError as APIService.APIError {
            self.error = apiError
        } catch {
            self.error = APIService.APIError.optimizationFailed
        }
        isLoading = false
    }
    
    /// Calls the mock API to save the current route.
    func saveRoute() async {
        guard !isLoading else { return }
        isLoading = true
        error = nil
        
        do {
            try await apiService.saveRoute(waypoints: waypoints)
            // Optionally, show a success message or navigate away
        } catch let apiError as APIService.APIError {
            self.error = apiError
        } catch {
            self.error = APIService.APIError.saveFailed
        }
        isLoading = false
    }
    
    /// Centers the map on the user's current location.
    func centerOnUserLocation() {
        if let location = userLocation {
            mapCameraPosition = MapboxMaps.CameraOptions(center: location, zoom: 14)
        }
    }
}

// MARK: - 3. SwiftUI View

/// A SwiftUI wrapper for MapboxMap.
struct MapboxMapView: UIViewRepresentable {
    @Binding var cameraOptions: MapboxMaps.CameraOptions?
    let waypoints: [Waypoint]
    
    func makeUIView(context: Context) -> MapView {
        let resourceOptions = ResourceOptions(accessToken: "YOUR_MAPBOX_ACCESS_TOKEN") // Placeholder
        let mapInitOptions = MapInitOptions(resourceOptions: resourceOptions, styleURI: .navigationDay)
        let mapView = MapView(frame: .zero, mapInitOptions: mapInitOptions)
        mapView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        
        // Add a point annotation manager
        let pointAnnotationManager = mapView.annotations.makePointAnnotationManager()
        context.coordinator.pointAnnotationManager = pointAnnotationManager
        
        return mapView
    }
    
    func updateUIView(_ uiView: MapView, context: Context) {
        // Update camera position
        if let options = cameraOptions {
            uiView.camera.ease(to: options, duration: 1.0)
            // Reset cameraOptions after use to prevent re-easing on every update
            DispatchQueue.main.async {
                cameraOptions = nil
            }
        }
        
        // Update annotations
        context.coordinator.updateAnnotations(on: uiView, with: waypoints)
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject {
        var parent: MapboxMapView
        var pointAnnotationManager: PointAnnotationManager?
        
        init(_ parent: MapboxMapView) {
            self.parent = parent
        }
        
        func updateAnnotations(on mapView: MapView, with waypoints: [Waypoint]) {
            guard let manager = pointAnnotationManager else { return }
            
            var annotations: [PointAnnotation] = []
            for (index, waypoint) in waypoints.enumerated() {
                var annotation = PointAnnotation(coordinate: waypoint.coordinate)
                annotation.image = .init(image: UIImage(systemName: "\(index + 1).circle.fill")!, name: "waypoint-\(index)")
                annotation.iconColor = .systemBlue
                annotation.iconSize = 1.5
                annotation.textField = waypoint.name
                annotation.textColor = .black
                annotations.append(annotation)
            }
            
            manager.annotations = annotations
        }
    }
}

/// The main view for editing waypoints.
struct WaypointEditorView: View {
    
    // Navi Blue color
    private static let naviBlue = Color(hex: "#2563EB")
    
    @StateObject var viewModel = WaypointEditorViewModel()
    
    var body: some View {
        ZStack(alignment: .bottom) {
            // 1. Map View
            MapboxMapView(cameraOptions: $viewModel.mapCameraPosition, waypoints: viewModel.waypoints)
                .edgesIgnoringSafeArea(.all)
            
            // 2. Floating Action Buttons (FABs)
            VStack(spacing: 16) {
                Spacer()
                
                // Center on User Location FAB
                Button(action: viewModel.centerOnUserLocation) {
                    Image(systemName: "location.fill")
                        .font(.title2)
                        .padding(16)
                        .background(Color.white)
                        .clipShape(Circle())
                        .shadow(radius: 5)
                        .accessibilityLabel("Center map on current location")
                }
                
                // Add Waypoint FAB
                Button(action: viewModel.addWaypoint) {
                    Image(systemName: "plus.circle.fill")
                        .font(.largeTitle)
                        .padding(16)
                        .background(Self.naviBlue)
                        .foregroundColor(.white)
                        .clipShape(Circle())
                        .shadow(radius: 8)
                        .accessibilityLabel("Add new waypoint")
                }
            }
            .padding(.trailing, 20)
            .frame(maxWidth: .infinity, alignment: .trailing)
            
            // 3. Bottom Sheet for Waypoint List
            if viewModel.isShowingWaypointList {
                WaypointListBottomSheet(viewModel: viewModel)
                    .transition(.move(edge: .bottom))
            }
            
            // 4. Loading/Error Overlay
            if viewModel.isLoading {
                ProgressView("Optimizing Route...")
                    .padding()
                    .background(Color.black.opacity(0.7))
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
            
            if let error = viewModel.error {
                ErrorBanner(error: error, onDismiss: { viewModel.error = nil })
                    .transition(.move(edge: .top))
                    .frame(maxHeight: .infinity, alignment: .top)
            }
        }
        .animation(.default, value: viewModel.isLoading)
        .animation(.default, value: viewModel.error)
        .animation(.default, value: viewModel.isShowingWaypointList)
    }
}

// MARK: - Components

/// Custom View for the Waypoint List Bottom Sheet.
struct WaypointListBottomSheet: View {
    @ObservedObject var viewModel: WaypointEditorViewModel
    
    var body: some View {
        VStack(spacing: 0) {
            // Drag Handle
            Capsule()
                .frame(width: 40, height: 5)
                .foregroundColor(Color(.systemGray4))
                .padding(.vertical, 8)
                .onTapGesture {
                    withAnimation {
                        viewModel.isShowingWaypointList.toggle()
                    }
                }
            
            HStack {
                Text("Route Waypoints (\(viewModel.waypoints.count))")
                    .font(.headline)
                    .foregroundColor(WaypointEditorView.naviBlue)
                
                Spacer()
                
                // Optimize Order Button
                Button("Optimize Order") {
                    Task { await viewModel.optimizeRoute() }
                }
                .buttonStyle(.borderedProminent)
                .tint(WaypointEditorView.naviBlue)
                .disabled(viewModel.isLoading || viewModel.waypoints.count < 2)
                .accessibilityLabel("Optimize route order")
            }
            .padding(.horizontal)
            
            // Waypoint List (Lazy Loading/Performance)
            List {
                ForEach(viewModel.waypoints) { waypoint in
                    WaypointRow(waypoint: waypoint)
                        .accessibilityElement(children: .combine)
                }
                .onMove(perform: viewModel.moveWaypoint) // Drag handles for reordering
                .onDelete(perform: viewModel.removeWaypoint) // Swipe to remove
            }
            .listStyle(.plain)
            .frame(height: min(300, CGFloat(viewModel.waypoints.count) * 50 + 100)) // Dynamic height
            
            // Save Route Button
            Button(action: { Task { await viewModel.saveRoute() } }) {
                Text(viewModel.isLoading ? "Saving..." : "Save Route")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(WaypointEditorView.naviBlue)
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
            .padding([.horizontal, .bottom])
            .disabled(viewModel.isLoading)
        }
        .background(Color(.systemBackground))
        .cornerRadius(20)
        .shadow(radius: 10)
        .padding(.horizontal)
        .padding(.bottom, 10)
    }
}

/// Custom View for a single Waypoint Row.
struct WaypointRow: View {
    let waypoint: Waypoint
    
    var body: some View {
        HStack {
            Image(systemName: "line.3.horizontal") // Drag Handle Visual
                .foregroundColor(Color(.systemGray))
                .accessibilityHidden(true)
            
            VStack(alignment: .leading) {
                Text(waypoint.name)
                    .font(.body)
                    .foregroundColor(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .accessibilityAddTraits(.isHeader)
                
                Text("\(waypoint.latitude, specifier: "%.4f"), \(waypoint.longitude, specifier: "%.4f")")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .accessibilityLabel("Coordinates: \(waypoint.latitude, specifier: "%.4f") latitude, \(waypoint.longitude, specifier: "%.4f") longitude")
            }
            
            Spacer()
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle()) // Ensures the whole row is tappable/draggable
    }
}

/// Custom View for displaying errors.
struct ErrorBanner: View {
    let error: APIService.APIError
    let onDismiss: () -> Void
    
    var body: some View {
        HStack {
            Image(systemName: "exclamationmark.triangle.fill")
            Text(error.localizedDescription)
                .font(.subheadline)
            Spacer()
            Button(action: onDismiss) {
                Image(systemName: "xmark.circle.fill")
            }
        }
        .padding()
        .background(Color.red)
        .foregroundColor(.white)
        .cornerRadius(8)
        .padding()
    }
}

// MARK: - Extensions

/// Extension to create Color from Hex string.
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
            (a, r, g, b) = (255, 0, 0, 0)
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

// MARK: - Preview

struct WaypointEditorView_Previews: PreviewProvider {
    static var previews: some View {
        WaypointEditorView()
            .environment(\.sizeCategory, .extraExtraLarge) // Dynamic Type Support Preview
    }
}

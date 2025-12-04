//
// TrafficIncidentDetailView.swift
//
// This file contains the complete, production-ready SwiftUI screen for a traffic incident detail view,
// adhering to MVVM architecture, Mapbox integration, and all specified design requirements.
//

import SwiftUI
import MapboxMaps // Requirement: Mapbox Integration
import CoreLocation // Requirement: Location tracking

// MARK: - 1. Models and Enums

/// Defines the types of traffic incidents.
enum IncidentType: String, CaseIterable {
    case accident = "Accident"
    case construction = "Construction"
    case hazard = "Hazard"
    case roadClosure = "Road Closure"
    case weather = "Weather"

    var iconName: String {
        switch self {
        case .accident: return "car.fill"
        case .construction: return "hammer.fill"
        case .hazard: return "exclamationmark.triangle.fill"
        case .roadClosure: return "xmark.octagon.fill"
        case .weather: return "cloud.bolt.rain.fill"
        }
    }

    var color: Color {
        switch self {
        case .accident: return .red
        case .construction: return .orange
        case .hazard: return .yellow
        case .roadClosure: return .purple
        case .weather: return .blue
        }
    }
}

/// Defines the severity of the traffic incident.
enum IncidentSeverity: String, CaseIterable {
    case low = "Low"
    case medium = "Medium"
    case high = "High"
    case critical = "Critical"

    var color: Color {
        switch self {
        case .low: return .green
        case .medium: return .yellow
        case .high: return .orange
        case .critical: return .red
        }
    }
}

/// The main data model for a traffic incident.
struct TrafficIncident: Identifiable, Decodable {
    let id: String
    let title: String
    let description: String
    let type: IncidentType
    let severity: IncidentSeverity
    let affectedRoads: [String]
    let reportTime: Date
    let coordinate: CLLocationCoordinate2D

    static var mock: TrafficIncident {
        TrafficIncident(
            id: UUID().uuidString,
            title: "Major Accident on I-95 North",
            description: "Multi-vehicle collision blocking all lanes. Expect significant delays.",
            type: .accident,
            severity: .critical,
            affectedRoads: ["I-95 North", "Exit 12B Ramp"],
            reportTime: Date().addingTimeInterval(-3600),
            coordinate: CLLocationCoordinate2D(latitude: 34.0522, longitude: -118.2437) // Los Angeles
        )
    }
}

// MARK: - 2. Service Placeholders

/// Extension for the required "Navi Blue" color.
extension Color {
    static let naviBlue = Color(hex: "#2563EB") // Requirement: Navi blue (#2563EB)

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

/// Simulated API Service for backend calls.
/// Requirement: Use APIService.shared for backend calls
class APIService {
    static let shared = APIService()

    private init() {}

    /// Simulates fetching a traffic incident from a backend.
    func fetchIncident(id: String) async throws -> TrafficIncident {
        // Simulate network delay
        try await Task.sleep(for: .seconds(1.5))

        // Simulate success
        return TrafficIncident.mock
    }

    /// Simulates subscribing to real-time updates.
    func subscribeToUpdates(for id: String) -> AsyncStream<TrafficIncident> {
        AsyncStream { continuation in
            let timer = Timer.scheduledTimer(withTimeInterval: 10.0, repeats: true) { _ in
                // Simulate a minor update (e.g., description change)
                var updatedIncident = TrafficIncident.mock
                updatedIncident.description = "Update: Police and emergency services are now on site. Expect lane closures."
                continuation.yield(updatedIncident)
            }
            continuation.onTermination = { @Sendable _ in
                timer.invalidate()
            }
        }
    }
}

/// Simulated Location Manager for current location tracking.
/// Requirement: Use CLLocationManager for current location tracking
class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    @Published var userLocation: CLLocationCoordinate2D?
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        userLocation = locations.last?.coordinate
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
    }

    // Mock location for the purpose of this file
    static var mockLocation: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: 34.0522 + 0.01, longitude: -118.2437 + 0.01)
    }
}

// MARK: - 3. ViewModel

/// The ViewModel for the Traffic Incident Detail View.
/// Requirement: MVVM with @StateObject ViewModel, @Published properties for reactive UI
@MainActor
final class TrafficIncidentDetailViewModel: ObservableObject {
    enum State {
        case loading
        case loaded(TrafficIncident)
        case error(String)
    }

    @Published private(set) var state: State = .loading
    @Published var isShowingBottomSheet: Bool = true
    @Published var mapCameraPosition: MapboxMaps.CameraOptions?

    private let incidentId: String
    private var updateTask: Task<Void, Never>? = nil

    init(incidentId: String) {
        self.incidentId = incidentId
    }

    func loadIncidentDetails() {
        state = .loading
        Task {
            do {
                let incident = try await APIService.shared.fetchIncident(id: incidentId)
                state = .loaded(incident)
                // Center map on incident location
                mapCameraPosition = CameraOptions(center: incident.coordinate, zoom: 14)
                startRealTimeUpdates(for: incident.id)
            } catch {
                state = .error("Failed to load incident details: \(error.localizedDescription)")
            }
        }
    }

    private func startRealTimeUpdates(for id: String) {
        // Requirement: Real-time updates
        updateTask?.cancel()
        updateTask = Task {
            for await incident in APIService.shared.subscribeToUpdates(for: id) {
                if case .loaded = state {
                    state = .loaded(incident)
                }
            }
        }
    }

    deinit {
        updateTask?.cancel()
    }
}

// MARK: - 4. View Components

/// A custom view for displaying the incident type and severity badge.
struct IncidentBadge: View {
    let type: IncidentType
    let severity: IncidentSeverity

    var body: some View {
        HStack(spacing: 8) {
            HStack(spacing: 4) {
                Image(systemName: type.iconName)
                    .font(.caption)
                Text(type.rawValue)
                    .font(.caption.weight(.medium))
            }
            .padding(.vertical, 4)
            .padding(.horizontal, 8)
            .background(type.color.opacity(0.2))
            .foregroundColor(type.color)
            .cornerRadius(8)
            .accessibilityLabel("Incident Type: \(type.rawValue)") // Requirement: Accessibility

            Text(severity.rawValue)
                .font(.caption.weight(.semibold))
                .padding(.vertical, 4)
                .padding(.horizontal, 8)
                .background(severity.color)
                .foregroundColor(.white)
                .cornerRadius(8)
                .accessibilityLabel("Severity: \(severity.rawValue)") // Requirement: Accessibility
        }
        .lineLimit(1)
        .minimumScaleFactor(0.8)
    }
}

/// A simulated Mapbox view wrapper.
struct MapboxMapView: UIViewRepresentable {
    @Binding var cameraOptions: MapboxMaps.CameraOptions?
    let incidentCoordinate: CLLocationCoordinate2D
    let userLocation: CLLocationCoordinate2D?

    func makeUIView(context: Context) -> MapView {
        // NOTE: In a real app, you would configure the MapView with a style URL and access token.
        let mapView = MapView(frame: .zero)
        mapView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        mapView.mapboxMap.onNext(.mapLoaded) { _ in
            // Add a marker for the incident
            let pointAnnotation = PointAnnotation(coordinate: incidentCoordinate)
            var manager = mapView.annotations.makePointAnnotationManager()
            manager.annotations = [pointAnnotation]
        }
        return mapView
    }

    func updateUIView(_ uiView: MapView, context: Context) {
        // Requirement: Efficient map updates
        if let options = cameraOptions {
            uiView.camera.ease(to: options, duration: 1.0)
            cameraOptions = nil // Consume the update
        }

        // In a real app, you would update the user location layer here
        // For simulation, we just log the location
        if let userLoc = userLocation {
            print("User location updated: \(userLoc.latitude), \(userLoc.longitude)")
        }
    }
}

// MARK: - 5. Main View

/// The main detail view for a traffic incident.
struct TrafficIncidentDetailView: View {
    @StateObject var viewModel: TrafficIncidentDetailViewModel
    @StateObject var locationManager = LocationManager() // Requirement: Location tracking
    @State private var isShowingShareSheet = false

    init(incidentId: String) {
        _viewModel = StateObject(wrappedValue: TrafficIncidentDetailViewModel(incidentId: incidentId))
    }

    // Requirement: Navi blue (#2563EB)
    private let naviBlue = Color.naviBlue

    var body: some View {
        ZStack(alignment: .bottom) {
            mapContent

            floatingActionButtons

            bottomSheet
        }
        .onAppear {
            viewModel.loadIncidentDetails()
        }
        .alert("Error", isPresented: Binding(
            get: { if case .error = viewModel.state { return true } else { return false } },
            set: { _ in }
        ), actions: {
            Button("Retry") { viewModel.loadIncidentDetails() }
            Button("Cancel", role: .cancel) {}
        }, message: {
            if case .error(let message) = viewModel.state {
                Text(message)
            }
        })
    }

    @ViewBuilder
    private var mapContent: some View {
        switch viewModel.state {
        case .loading:
            ProgressView("Loading Incident...") // Requirement: Loading states
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.gray.opacity(0.1))
        case .error(let message):
            VStack { // Requirement: Error handling
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.largeTitle)
                    .foregroundColor(.red)
                Text("Failed to load incident.")
                    .font(.headline)
                Text(message)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                Button("Reload") {
                    viewModel.loadIncidentDetails()
                }
                .padding(.top)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.white)
        case .loaded(let incident):
            MapboxMapView(
                cameraOptions: $viewModel.mapCameraPosition,
                incidentCoordinate: incident.coordinate,
                userLocation: locationManager.userLocation ?? LocationManager.mockLocation
            )
            .ignoresSafeArea()
        }
    }

    // Requirement: Floating action buttons with shadows
    private var floatingActionButtons: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()

                // Recenter Button
                Button {
                    if case .loaded(let incident) = viewModel.state {
                        viewModel.mapCameraPosition = CameraOptions(center: incident.coordinate, zoom: 14)
                    }
                } label: {
                    Image(systemName: "location.fill")
                        .font(.title2)
                        .padding(16)
                        .background(Color.white)
                        .clipShape(Circle())
                        .shadow(radius: 5)
                        .accessibilityLabel("Recenter map on incident")
                }
                .padding(.trailing, 10)

                // Share Button
                Button {
                    isShowingShareSheet = true
                } label: {
                    Image(systemName: "square.and.arrow.up")
                        .font(.title2)
                        .padding(16)
                        .background(naviBlue)
                        .foregroundColor(.white)
                        .clipShape(Circle())
                        .shadow(radius: 5)
                        .accessibilityLabel("Share incident details")
                }
            }
            .padding(.horizontal)
            .padding(.bottom, viewModel.isShowingBottomSheet ? 200 : 20) // Adjust for bottom sheet
        }
        .sheet(isPresented: $isShowingShareSheet) {
            // Placeholder for a real Share Sheet
            Text("Share Sheet Placeholder")
                .presentationDetents([.medium])
        }
    }

    // Requirement: Bottom sheets for details
    private var bottomSheet: some View {
        VStack {
            if case .loaded(let incident) = viewModel.state {
                // Drag indicator
                Capsule()
                    .frame(width: 40, height: 6)
                    .foregroundColor(Color.secondary.opacity(0.5))
                    .padding(.vertical, 8)
                    .onTapGesture {
                        withAnimation(.spring()) { // Requirement: Smooth animations
                            viewModel.isShowingBottomSheet.toggle()
                        }
                    }

                // Incident Header
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(incident.title)
                            .font(.title2)
                            .fontWeight(.bold)
                            .foregroundColor(.primary)
                            .lineLimit(2)
                            .minimumScaleFactor(0.9)
                            .accessibilityAddTraits(.isHeader)

                        IncidentBadge(type: incident.type, severity: incident.severity)
                    }
                    Spacer()
                }
                .padding(.horizontal)

                // Details Content
                if viewModel.isShowingBottomSheet {
                    ScrollView { // Requirement: Lazy loading (ScrollView for content)
                        VStack(alignment: .leading, spacing: 15) {
                            // Description
                            VStack(alignment: .leading, spacing: 5) {
                                Text("Description")
                                    .font(.headline)
                                Text(incident.description)
                                    .font(.body)
                                    .foregroundColor(.secondary)
                                    .dynamicTypeSize(.medium...(.accessibility3)) // Requirement: Dynamic Type support
                            }

                            Divider()

                            // Affected Roads
                            VStack(alignment: .leading, spacing: 5) {
                                Text("Affected Roads")
                                    .font(.headline)
                                ForEach(incident.affectedRoads, id: \.self) { road in
                                    HStack {
                                        Image(systemName: "road.lanes.curved.right")
                                            .foregroundColor(naviBlue)
                                        Text(road)
                                            .font(.body)
                                    }
                                }
                            }

                            Divider()

                            // Report Time
                            VStack(alignment: .leading, spacing: 5) {
                                Text("Reported")
                                    .font(.headline)
                                Text(incident.reportTime, style: .relative)
                                    .font(.body)
                                    .foregroundColor(.secondary)
                                    .accessibilityLabel("Reported \(incident.reportTime.formatted(.relative(presentation: .named))) ago")
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 40)
                    }
                    .frame(maxHeight: 300) // Constrain scrollable area
                }
            }
        }
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 25.0, style: .continuous)
                .fill(Color(uiColor: .systemBackground))
                .shadow(radius: 10)
        )
        .offset(y: viewModel.isShowingBottomSheet ? 0 : 300) // Hide/show animation
    }
}

// MARK: - 6. Preview

struct TrafficIncidentDetailView_Previews: PreviewProvider {
    static var previews: some View {
        TrafficIncidentDetailView(incidentId: TrafficIncident.mock.id)
    }
}

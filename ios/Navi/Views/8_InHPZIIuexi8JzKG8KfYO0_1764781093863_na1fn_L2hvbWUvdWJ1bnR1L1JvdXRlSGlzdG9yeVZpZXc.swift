import SwiftUI
import Combine
import CoreLocation // Required for CLLocationManager mock

// MARK: - 1. Mock Dependencies and Data Structures

/// Mock structure for a single navigation route.
struct Route: Identifiable, Equatable {
    let id = UUID()
    let date: Date
    let distance: Measurement<UnitLength>
    let duration: TimeInterval
    let startLocation: String
    let endLocation: String
    // Mock for map data - in a real app, this would be a list of coordinates
    let polylineData: String
    
    static var mock: Route {
        Route(
            date: Date().addingTimeInterval(-Double.random(in: 86400...86400 * 30)),
            distance: Measurement(value: Double.random(in: 1.0...100.0), unit: .kilometers),
            duration: TimeInterval.random(in: 600...7200),
            startLocation: "123 Main St",
            endLocation: "456 Oak Ave",
            polylineData: "MockPolylineData"
        )
    }
}

/// Mock for the APIService.shared
class APIService {
    static let shared = APIService()
    
    enum APIError: Error {
        case networkError
        case serverError
    }
    
    /// Simulates a backend call to fetch route history.
    func fetchRouteHistory() async throws -> [Route] {
        // Simulate network delay
        try await Task.sleep(for: .seconds(Double.random(in: 0.5...1.5)))
        
        // Simulate an error 10% of the time
        if Int.random(in: 1...10) == 1 {
            throw APIError.networkError
        }
        
        // Generate mock data
        let mockRoutes = (0..<20).map { _ in Route.mock }
        // Sort chronologically (most recent first)
        return mockRoutes.sorted { $0.date > $1.date }
    }
}

/// Mock for CLLocationManager
class LocationManager: NSObject, ObservableObject {
    @Published var currentLocation: CLLocation?
    
    override init() {
        super.init()
        // Simulate a current location
        self.currentLocation = CLLocation(latitude: 37.7749, longitude: -122.4194) // San Francisco
    }
    
    // In a real app, this would start location updates
    func startUpdatingLocation() {
        print("Mock LocationManager: Starting location updates.")
    }
}

/// Mock for MapboxMaps SDK integration
/// In a real application, this would import MapboxMaps and use MapView.
struct MapboxMapView: View {
    let route: Route?
    
    var body: some View {
        // Placeholder for the actual Mapbox map view
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.gray.opacity(0.3))
            .overlay(
                VStack {
                    if let route = route {
                        Text("Mapbox Map Placeholder")
                            .font(.caption)
                        Text("Route: \(route.startLocation) to \(route.endLocation)")
                            .font(.caption2)
                    } else {
                        Text("Mapbox Map Placeholder")
                            .font(.caption)
                    }
                }
                .foregroundColor(.white)
            )
            .accessibilityLabel("Map showing route history")
    }
}

// MARK: - 2. ViewModel

class RouteHistoryViewModel: ObservableObject {
    @Published var routes: [Route] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var selectedRoute: Route?
    
    private let apiService: APIService
    private let locationManager: LocationManager
    private var cancellables = Set<AnyCancellable>()
    
    // Navi blue color
    static let naviBlue = Color(hex: "2563EB")
    
    init(apiService: APIService = .shared, locationManager: LocationManager = LocationManager()) {
        self.apiService = apiService
        self.locationManager = locationManager
        
        // Real-time updates mock: Observe location changes (though mock)
        locationManager.$currentLocation
            .sink { location in
                if let loc = location {
                    print("Real-time update: Current location is \(loc.coordinate.latitude), \(loc.coordinate.longitude)")
                }
            }
            .store(in: &cancellables)
        
        fetchRoutes()
    }
    
    func fetchRoutes() {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        
        Task { @MainActor in
            do {
                let fetchedRoutes = try await apiService.fetchRouteHistory()
                // Simulate "real-time" update/smooth animation by delaying the state change slightly
                try await Task.sleep(for: .milliseconds(300))
                self.routes = fetchedRoutes
            } catch {
                self.errorMessage = "Failed to load routes: \((error as? APIService.APIError)?.localizedDescription ?? error.localizedDescription)"
            }
            self.isLoading = false
        }
    }
    
    func replayRoute(_ route: Route) {
        print("Replaying route: \(route.id)")
        // In a real app, this would trigger a navigation or map animation
    }
    
    func selectRoute(_ route: Route) {
        withAnimation(.easeInOut) {
            self.selectedRoute = route
        }
    }
}

// MARK: - 3. View

struct RouteHistoryView: View {
    @StateObject var viewModel = RouteHistoryViewModel()
    
    // Navi blue color
    private let naviBlue = RouteHistoryViewModel.naviBlue
    
    var body: some View {
        NavigationView {
            ZStack {
                // 1. Main Content: Lazy-loaded list of routes
                listContent
                
                // 2. Loading/Error Overlay
                if viewModel.isLoading {
                    ProgressView("Loading Route History...")
                        .padding()
                        .background(.ultraThinMaterial)
                        .cornerRadius(10)
                } else if let error = viewModel.errorMessage {
                    errorView(error)
                }
                
                // 3. Floating Action Button (FAB)
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        refreshButton
                    }
                }
                .padding(.trailing, 20)
                .padding(.bottom, 20)
            }
            .navigationTitle("Route History")
            .sheet(item: $viewModel.selectedRoute) { route in
                RouteDetailSheet(route: route, naviBlue: naviBlue)
            }
            .onAppear {
                // Ensure data is loaded on appear if not already present
                if viewModel.routes.isEmpty && !viewModel.isLoading {
                    viewModel.fetchRoutes()
                }
            }
        }
    }
    
    // MARK: - Subviews
    
    private var listContent: some View {
        List {
            // Mapbox View at the top (efficient map updates placeholder)
            MapboxMapView(route: viewModel.routes.first)
                .frame(height: 200)
                .listRowInsets(EdgeInsets())
                .padding(.bottom, 10)
            
            // Lazy-loaded list of routes
            ForEach(viewModel.routes) { route in
                RouteRow(route: route, naviBlue: naviBlue, viewModel: viewModel)
            }
        }
        .listStyle(.plain)
        .accessibilityLabel("Chronological list of past routes")
    }
    
    private var refreshButton: some View {
        Button {
            viewModel.fetchRoutes()
        } label: {
            Image(systemName: "arrow.clockwise")
                .font(.title2)
                .padding(15)
                .background(naviBlue)
                .foregroundColor(.white)
                .clipShape(Circle())
                .shadow(color: .black.opacity(0.3), radius: 5, x: 0, y: 5) // Floating action button with shadow
                .accessibilityLabel("Refresh route history")
        }
    }
    
    private func errorView(_ message: String) -> some View {
        VStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.red)
                .font(.largeTitle)
            Text("Error")
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .multilineTextAlignment(.center)
            Button("Try Again") {
                viewModel.fetchRoutes()
            }
            .padding(.top, 10)
            .buttonStyle(.borderedProminent)
        }
        .padding()
        .background(.regularMaterial)
        .cornerRadius(10)
        .shadow(radius: 5)
        .accessibilityLiveRegion(.assertive)
    }
}

// MARK: - Components

struct RouteRow: View {
    let route: Route
    let naviBlue: Color
    @ObservedObject var viewModel: RouteHistoryViewModel
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(route.date, style: .date)
                    .font(.headline)
                    .foregroundColor(naviBlue)
                Spacer()
                Text(route.date, style: .time)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            HStack {
                VStack(alignment: .leading) {
                    // Distance
                    HStack {
                        Image(systemName: "ruler.fill")
                        Text("Distance: \(route.distance.formatted(.measurement(width: .abbreviated, usage: .road)))")
                    }
                    .font(.subheadline)
                    .accessibilityValue("\(route.distance.value.formatted(.number.precision(.fractionLength(1)))) kilometers")
                    
                    // Duration
                    HStack {
                        Image(systemName: "clock.fill")
                        Text("Duration: \(formattedDuration(route.duration))")
                    }
                    .font(.subheadline)
                    .accessibilityValue("\(Int(route.duration / 60)) minutes")
                }
                
                Spacer()
                
                // Replay Option Button
                Button {
                    viewModel.replayRoute(route)
                } label: {
                    Image(systemName: "play.circle.fill")
                        .font(.largeTitle)
                        .foregroundColor(naviBlue)
                        .accessibilityLabel("Replay route from \(route.startLocation) to \(route.endLocation)")
                }
            }
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle()) // Makes the whole row tappable
        .onTapGesture {
            viewModel.selectRoute(route)
        }
        .accessibilityElement(children: .combine)
        .accessibilityHint("Tap to view details.")
    }
    
    private func formattedDuration(_ duration: TimeInterval) -> String {
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = [.hour, .minute, .second]
        formatter.unitsStyle = .abbreviated
        return formatter.string(from: duration) ?? ""
    }
}

struct RouteDetailSheet: View {
    let route: Route
    let naviBlue: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text("Route Details")
                .font(.largeTitle)
                .fontWeight(.bold)
                .foregroundColor(naviBlue)
                .padding(.bottom, 10)
            
            // Map Preview
            MapboxMapView(route: route)
                .frame(height: 150)
                .cornerRadius(10)
            
            // Details
            detailRow(icon: "calendar", label: "Date", value: route.date.formatted(date: .long, time: .shortened))
            detailRow(icon: "ruler.fill", label: "Distance", value: route.distance.formatted(.measurement(width: .wide, usage: .road)))
            detailRow(icon: "clock.fill", label: "Duration", value: formattedDuration(route.duration))
            detailRow(icon: "mappin.circle.fill", label: "Start", value: route.startLocation)
            detailRow(icon: "flag.checkered.circle.fill", label: "End", value: route.endLocation)
            
            Spacer()
        }
        .padding()
        .presentationDetents([.medium, .large]) // Bottom sheet
        .accessibilityLabel("Details for route on \(route.date.formatted(date: .long, time: .omitted))")
    }
    
    private func detailRow(icon: String, label: String, value: String) -> some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(naviBlue)
                .frame(width: 25)
                .accessibilityHidden(true)
            Text(label)
                .font(.headline)
                .accessibilityLabel(label)
            Spacer()
            Text(value)
                .font(.body)
                .foregroundColor(.secondary)
                .accessibilityValue(value)
        }
        .padding(.vertical, 2)
    }
    
    private func formattedDuration(_ duration: TimeInterval) -> String {
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = [.hour, .minute, .second]
        formatter.unitsStyle = .full
        return formatter.string(from: duration) ?? ""
    }
}

// MARK: - Extensions

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
            (a, r, g, b) = (1, 0, 0, 0)
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

#Preview {
    RouteHistoryView()
}

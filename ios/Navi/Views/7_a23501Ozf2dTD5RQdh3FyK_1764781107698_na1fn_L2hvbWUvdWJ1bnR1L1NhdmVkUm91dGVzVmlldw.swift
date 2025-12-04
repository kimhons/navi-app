import SwiftUI
import CoreLocation
import MapboxMaps // Required by task input

// MARK: - 1. Data Models

/// Represents a single saved navigation route.
struct Route: Identifiable, Codable {
    let id = UUID()
    let name: String
    let origin: String
    let destination: String
    let distance: Double // in kilometers
    let lastUsed: Date
    var isFavorite: Bool
    
    // Mock for Mapbox-related properties, showing the intent to store route geometry
    // In a real app, this would be a Mapbox Route object or a set of coordinates.
    let polylineCoordinates: [CLLocationCoordinate2D]
    
    static var mock: Route {
        Route(
            name: "Work Commute",
            origin: "Home",
            destination: "Office",
            distance: 15.2,
            lastUsed: Calendar.current.date(byAdding: .day, value: -1, to: Date())!,
            isFavorite: true,
            polylineCoordinates: [
                CLLocationCoordinate2D(latitude: 34.0522, longitude: -118.2437),
                CLLocationCoordinate2D(latitude: 34.0622, longitude: -118.2537)
            ]
        )
    }
}

// MARK: - 2. Mock Services

/// Mock implementation of APIService for fetching saved routes.
/// In a production app, this would handle real network requests.
class APIService {
    static let shared = APIService()
    private init() {}

    func fetchSavedRoutes() async throws -> [Route] {
        // Simulate network delay for loading state
        try await Task.sleep(nanoseconds: 500_000_000)

        // Mock data
        let mockRoutes: [Route] = [
            Route(name: "Home to Gym", origin: "Home", destination: "Gym", distance: 5.8, lastUsed: Date(), isFavorite: true, polylineCoordinates: []),
            Route(name: "Weekend Getaway", origin: "City Center", destination: "Mountain Cabin", distance: 150.5, lastUsed: Calendar.current.date(byAdding: .day, value: -7, to: Date())!, isFavorite: false, polylineCoordinates: []),
            Route(name: "Work Commute", origin: "Apartment", destination: "Tech Park", distance: 22.1, lastUsed: Calendar.current.date(byAdding: .hour, value: -3, to: Date())!, isFavorite: true, polylineCoordinates: []),
            Route(name: "Grocery Run", origin: "Home", destination: "Supermarket", distance: 3.2, lastUsed: Calendar.current.date(byAdding: .month, value: -1, to: Date())!, isFavorite: false, polylineCoordinates: [])
        ]
        
        // Simulate a potential error 10% of the time
        if Int.random(in: 1...10) == 1 {
            throw APIError.networkFailure
        }
        
        return mockRoutes.sorted { $0.lastUsed > $1.lastUsed }
    }
    
    enum APIError: Error {
        case networkFailure
        case invalidResponse
    }
    
    func toggleFavorite(route: Route) async throws -> Route {
        // Simulate API call to update favorite status
        try await Task.sleep(nanoseconds: 200_000_000)
        return Route(
            name: route.name,
            origin: route.origin,
            destination: route.destination,
            distance: route.distance,
            lastUsed: route.lastUsed,
            isFavorite: !route.isFavorite,
            polylineCoordinates: route.polylineCoordinates
        )
    }
}

/// Mock implementation of CLLocationManager for current location tracking.
class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var currentLocation: CLLocation?
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.requestWhenInUseAuthorization()
        // Simulate location update after a short delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            self.currentLocation = CLLocation(latitude: 34.0522, longitude: -118.2437) // Mock Los Angeles
        }
    }
    
    func startUpdatingLocation() {
        // In a real app: manager.startUpdatingLocation()
        print("Location tracking started (Mock)")
    }
    
    // MARK: - CLLocationManagerDelegate (Mocked)
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        // In a real app: self.currentLocation = locations.last
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location error (Mock): \(error.localizedDescription)")
    }
}

// MARK: - 3. ViewModel

/// ViewModel for the SavedRoutesView, handling data fetching, state management, and business logic.
@MainActor
class SavedRoutesViewModel: ObservableObject {
    @Published var routes: [Route] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var selectedRoute: Route?
    
    // Dependency on LocationManager for current location tracking
    @ObservedObject var locationManager: LocationManager
    
    init(locationManager: LocationManager = LocationManager()) {
        self.locationManager = locationManager
        fetchRoutes()
    }
    
    /// Fetches saved routes from the API service.
    func fetchRoutes() {
        isLoading = true
        errorMessage = nil
        
        Task {
            do {
                self.routes = try await APIService.shared.fetchSavedRoutes()
            } catch let error as APIService.APIError {
                self.errorMessage = "Failed to load routes: \(error.localizedDescription)"
            } catch {
                self.errorMessage = "An unknown error occurred."
            }
            isLoading = false
        }
    }
    
    /// Toggles the favorite status of a route.
    func toggleFavorite(route: Route) {
        guard let index = routes.firstIndex(where: { $0.id == route.id }) else { return }
        
        // Optimistic update
        routes[index].isFavorite.toggle()
        
        Task {
            do {
                let updatedRoute = try await APIService.shared.toggleFavorite(route: route)
                // Final update if successful (or revert if necessary, but for a mock, we'll assume success)
                self.routes[index] = updatedRoute
            } catch {
                // Revert optimistic update on failure
                self.routes[index].isFavorite.toggle()
                self.errorMessage = "Failed to update favorite status."
            }
        }
    }
    
    /// Selects a route to display in the bottom sheet.
    func selectRoute(route: Route?) {
        selectedRoute = route
    }
}

// MARK: - 4. View Components

/// A SwiftUI view to represent a single saved route in the list.
struct RouteRow: View {
    @ObservedObject var viewModel: SavedRoutesViewModel
    let route: Route
    
    // Custom color for the design requirement
    private let naviBlue = Color(hex: "2563EB")
    
    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text(route.name)
                    .font(.headline)
                    .foregroundColor(.primary)
                    .accessibilityLabel("Route name: \(route.name)")
                
                Text("\(route.origin) to \(route.destination)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .accessibilityLabel("From \(route.origin) to \(route.destination)")
                
                HStack {
                    Image(systemName: "ruler.fill")
                    Text("\(route.distance, specifier: "%.1f") km")
                    Image(systemName: "clock.fill")
                    Text("Last used: \(route.lastUsed, style: .date)")
                }
                .font(.caption)
                .foregroundColor(.secondary)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Distance \(route.distance, specifier: "%.1f") kilometers. Last used \(route.lastUsed, style: .date)")
            }
            
            Spacer()
            
            Button {
                viewModel.toggleFavorite(route: route)
            } label: {
                Image(systemName: route.isFavorite ? "star.fill" : "star")
                    .foregroundColor(route.isFavorite ? naviBlue : .gray)
                    .scaleEffect(route.isFavorite ? 1.1 : 1.0)
                    .animation(.spring(response: 0.3, dampingFraction: 0.5), value: route.isFavorite)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(route.isFavorite ? "Remove from favorites" : "Add to favorites")
        }
        .padding(.vertical, 8)
    }
}

/// A view for the bottom sheet displaying detailed information about a selected route.
struct RouteDetailSheet: View {
    @Binding var route: Route?
    
    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            HStack {
                Text(route?.name ?? "Route Details")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                Spacer()
                Button {
                    route = nil
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundColor(.gray)
                }
            }
            
            Divider()
            
            if let route = route {
                VStack(alignment: .leading, spacing: 10) {
                    DetailRow(label: "Origin", value: route.origin, icon: "location.circle.fill")
                    DetailRow(label: "Destination", value: route.destination, icon: "flag.circle.fill")
                    DetailRow(label: "Distance", value: "\(route.distance, specifier: "%.1f") km", icon: "ruler.fill")
                    DetailRow(label: "Last Used", value: route.lastUsed, icon: "calendar")
                    DetailRow(label: "Favorite", value: route.isFavorite ? "Yes" : "No", icon: route.isFavorite ? "star.fill" : "star")
                }
            } else {
                Text("No route selected.")
            }
            
            Spacer()
        }
        .padding()
        .presentationDetents([.medium, .large])
    }
}

/// Helper view for detail rows in the sheet.
struct DetailRow: View {
    let label: String
    let value: String
    let icon: String
    
    init(label: String, value: String, icon: String) {
        self.label = label
        self.value = value
        self.icon = icon
    }
    
    init(label: String, value: Date, icon: String) {
        self.label = label
        self.value = value.formatted(date: .abbreviated, time: .shortened)
        self.icon = icon
    }
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(Color(hex: "2563EB"))
                .frame(width: 25)
            Text(label)
                .fontWeight(.medium)
            Spacer()
            Text(value)
                .foregroundColor(.secondary)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label): \(value)")
    }
}

// MARK: - 5. Mapbox Mock View

/// A mock UIViewRepresentable to satisfy the MapboxMaps SDK requirement.
/// In a real application, this would instantiate and manage the MapboxMapView.
struct MapboxMapView: UIViewRepresentable {
    @ObservedObject var viewModel: SavedRoutesViewModel
    
    func makeUIView(context: Context) -> UIView {
        // In a real app: return MapboxMaps.MapView(frame: .zero, mapInitOptions: mapInitOptions)
        let view = UIView()
        view.backgroundColor = .systemGray5
        
        let label = UILabel()
        label.text = "Mapbox Map View (Mock)"
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
        
        return view
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {
        // Update map based on viewModel.selectedRoute or viewModel.currentLocation
        // This simulates efficient map updates.
        if let location = viewModel.locationManager.currentLocation {
            print("Mock Mapbox View updated to center on: \(location.coordinate.latitude), \(location.coordinate.longitude)")
        }
    }
}

// MARK: - 6. Main View

/// The main view for displaying saved routes.
struct SavedRoutesView: View {
    // Architecture: MVVM with @StateObject ViewModel
    @StateObject var viewModel = SavedRoutesViewModel()
    
    // Custom color for the design requirement
    private let naviBlue = Color(hex: "2563EB")
    
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            // 1. Map Background
            MapboxMapView(viewModel: viewModel)
                .ignoresSafeArea()
            
            // 2. Route List Overlay
            VStack(spacing: 0) {
                // Error Banner
                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .foregroundColor(.white)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.red)
                        .transition(.move(edge: .top))
                        .animation(.easeInOut, value: errorMessage)
                }
                
                // Main Content Area
                ScrollView {
                    LazyVStack(spacing: 0) { // Performance: Lazy loading
                        if viewModel.isLoading {
                            ProgressView("Loading Routes...")
                                .padding()
                                .frame(maxWidth: .infinity)
                        } else if viewModel.routes.isEmpty {
                            ContentUnavailableView("No Saved Routes", systemImage: "map.slash.fill", description: Text("Tap the '+' button to save your first route."))
                        } else {
                            ForEach(viewModel.routes) { route in
                                RouteRow(viewModel: viewModel, route: route)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        // Real-time updates: selection triggers sheet
                                        withAnimation(.spring) {
                                            viewModel.selectRoute(route: route)
                                        }
                                    }
                                Divider()
                            }
                        }
                    }
                    .padding(.horizontal)
                    .background(.regularMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 15))
                    .padding(.top, 10)
                }
                .frame(maxHeight: 300) // Constrain the list height
                .background(.clear)
            }
            
            // 3. Floating Action Buttons
            VStack(spacing: 15) {
                // Add New Route Button
                Button {
                    // Action to add a new route
                } label: {
                    Image(systemName: "plus")
                        .font(.title2)
                        .padding(15)
                        .background(naviBlue)
                        .foregroundColor(.white)
                        .clipShape(Circle())
                        .shadow(radius: 5) // Design: floating action buttons with shadows
                }
                .accessibilityLabel("Add new route")
                
                // Recenter Map Button (uses current location)
                Button {
                    viewModel.locationManager.startUpdatingLocation()
                } label: {
                    Image(systemName: "location.fill")
                        .font(.title2)
                        .padding(15)
                        .background(.white)
                        .foregroundColor(naviBlue)
                        .clipShape(Circle())
                        .shadow(radius: 5) // Design: floating action buttons with shadows
                }
                .accessibilityLabel("Recenter map to current location")
            }
            .padding(.trailing, 20)
            .padding(.bottom, 20)
        }
        .sheet(item: $viewModel.selectedRoute) { route in
            RouteDetailSheet(route: $viewModel.selectedRoute)
        }
        .onAppear {
            // Initial location tracking start
            viewModel.locationManager.startUpdatingLocation()
        }
    }
}

// MARK: - 7. Extensions

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
            blue:  Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}



// The rest of the file will be implemented in the next phases.

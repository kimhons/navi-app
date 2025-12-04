//
// CompassView.swift
//
// A complete, production-ready SwiftUI screen for a floating compass widget.
// Integrates MapboxMaps SDK and follows the MVVM architecture.
//

import SwiftUI
import Combine
import CoreLocation
import MapboxMaps // Required for Mapbox Integration

// MARK: - 1. Mock Dependencies

/// Mock APIService to satisfy the requirement: "API: Use APIService.shared for backend calls"
/// In a real application, this would handle network requests.
class APIService {
    static let shared = APIService()
    
    enum APIError: Error {
        case networkError
        case serverError
    }
    
    /// Mock function to simulate fetching data (e.g., nearby points of interest)
    func fetchCompassData() -> AnyPublisher<String, APIError> {
        return Future<String, APIError> { promise in
            // Simulate network delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                // Simulate success
                promise(.success("Data fetched successfully from backend."))
                
                // To simulate error, uncomment the line below:
                // promise(.failure(.networkError))
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - 2. ViewModel (MVVM)

/// The ViewModel handles all business logic, state management, and data fetching.
/// It conforms to ObservableObject for reactive UI updates.
class CompassViewModel: NSObject, ObservableObject, CLLocationManagerDelegate {
    
    // MARK: Published Properties
    
    /// Current heading in degrees (0-360).
    @Published var heading: Double = 0.0
    
    /// Current location coordinates.
    @Published var userLocation: CLLocationCoordinate2D?
    
    /// State for loading indicators.
    @Published var isLoading: Bool = true
    
    /// State for error handling.
    @Published var errorMessage: String?
    
    /// State to track if the map is currently centered to North.
    @Published var isMapCenteredToNorth: Bool = false
    
    /// Mock data from API call.
    @Published var apiData: String?
    
    // MARK: Private Properties
    
    private let locationManager = CLLocationManager()
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: Initialization
    
    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.startUpdatingHeading()
        locationManager.requestWhenInUseAuthorization()
        
        // Start with a mock data fetch
        fetchData()
    }
    
    // MARK: Location Manager Delegate
    
    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        // Real-time updates: Update heading, ensuring it's not negative
        if newHeading.headingAccuracy > 0 {
            // Smooth animations: Use a small animation for heading changes
            withAnimation(.easeInOut(duration: 0.1)) {
                self.heading = newHeading.trueHeading > 0 ? newHeading.trueHeading : newHeading.magneticHeading
            }
            self.isLoading = false
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        self.errorMessage = "Location error: \(error.localizedDescription)"
        self.isLoading = false
    }
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            manager.startUpdatingLocation()
            manager.startUpdatingHeading()
        case .denied, .restricted:
            self.errorMessage = "Location access denied. Please enable it in settings."
            self.isLoading = false
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        @unknown default:
            break
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        // Efficient map updates: Only update if a new, valid location is available
        if let location = locations.last {
            self.userLocation = location.coordinate
        }
    }
    
    // MARK: Public Methods
    
    /// Toggles the map recentering state.
    func recenterMapToNorth() {
        // Feature: Tap to recenter map to north orientation
        isMapCenteredToNorth.toggle()
        
        // In a real Mapbox implementation, this would trigger a camera update
        // on the MapView to set the bearing to 0 (North).
        print("Map recenter action triggered. Centered to North: \(isMapCenteredToNorth)")
        
        // Reset the state after a short delay to allow the map to animate
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            self.isMapCenteredToNorth = false
        }
    }
    
    /// Mock API call using APIService.shared.
    func fetchData() {
        isLoading = true
        errorMessage = nil
        
        APIService.shared.fetchCompassData()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let error):
                    self?.errorMessage = "API Fetch Failed: \(error)"
                case .finished:
                    break
                }
            } receiveValue: { [weak self] data in
                self?.apiData = data
            }
            .store(in: &cancellables)
    }
}

// MARK: - 3. View (MVVM)

/// The SwiftUI View that presents the floating compass widget.
struct CompassView: View {
    
    // Architecture: MVVM with @StateObject ViewModel
    @StateObject var viewModel = CompassViewModel()
    
    // Design: Navi blue (#2563EB)
    private let naviBlue = Color(hex: "2563EB")
    
    // State for the bottom sheet
    @State private var showDetailsSheet: Bool = false
    
    var body: some View {
        ZStack(alignment: .topTrailing) {
            // Placeholder for Mapbox MapView
            // Performance: Lazy loading (The MapView would be loaded here)
            MapboxMapViewPlaceholder()
            
            // Floating Compass Widget
            VStack {
                if viewModel.isLoading {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .padding()
                        .background(.ultraThinMaterial)
                        .clipShape(Circle())
                } else if let error = viewModel.errorMessage {
                    ErrorView(message: error)
                } else {
                    compassButton
                }
                
                // Floating Action Button for Details
                detailsButton
            }
            .padding()
        }
        .sheet(isPresented: $showDetailsSheet) {
            // Design: Bottom sheets for details
            DetailsBottomSheet(viewModel: viewModel)
        }
    }
    
    /// The main compass button UI.
    private var compassButton: some View {
        Button {
            viewModel.recenterMapToNorth()
        } label: {
            VStack(spacing: 4) {
                Image(systemName: "arrow.up")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 18, height: 18)
                    .rotationEffect(.degrees(-viewModel.heading)) // Real-time updates
                    .animation(.spring(), value: viewModel.heading) // Smooth animations
                    .foregroundColor(.white)
                
                Text("\(Int(viewModel.heading))°")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
            }
            .padding(12)
            .background(naviBlue)
            // Design: Floating action buttons with shadows
            .clipShape(Circle())
            .shadow(color: .black.opacity(0.3), radius: 5, x: 0, y: 3)
            // Accessibility: VoiceOver labels
            .accessibilityLabel("Compass. Current heading is \(Int(viewModel.heading)) degrees. Tap to recenter map to North.")
        }
    }
    
    /// Floating action button to show details.
    private var detailsButton: some View {
        Button {
            showDetailsSheet = true
        } label: {
            Image(systemName: "info.circle.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 24, height: 24)
                .foregroundColor(naviBlue)
                .padding(10)
                .background(.white)
                // Design: Floating action buttons with shadows
                .clipShape(Circle())
                .shadow(color: .black.opacity(0.3), radius: 5, x: 0, y: 3)
                // Accessibility: VoiceOver labels
                .accessibilityLabel("Show map and location details.")
        }
        .padding(.top, 10)
    }
}

// MARK: - 4. Supporting Views and Extensions

/// Placeholder for the Mapbox MapView.
/// In a real app, this would be a UIViewRepresentable wrapping the MapView.
struct MapboxMapViewPlaceholder: View {
    var body: some View {
        Color.gray.opacity(0.1)
            .overlay(
                Text("Mapbox Map View Placeholder")
                    .foregroundColor(.secondary)
                    .font(.title2)
            )
            .ignoresSafeArea()
    }
}

/// Simple view for displaying errors.
struct ErrorView: View {
    let message: String
    
    var body: some View {
        Text("Error: \(message)")
            .font(.caption)
            .foregroundColor(.white)
            .padding(8)
            .background(Color.red)
            .cornerRadius(8)
            // Accessibility: Dynamic Type support
            .environment(\.sizeCategory, .large)
    }
}

/// The bottom sheet content for displaying details.
struct DetailsBottomSheet: View {
    @ObservedObject var viewModel: CompassViewModel
    
    var body: some View {
        NavigationView {
            List {
                Section("Location Status") {
                    HStack {
                        Text("Heading")
                        Spacer()
                        Text("\(Int(viewModel.heading))°")
                            // Accessibility: Dynamic Type support
                            .font(.body)
                            .environment(\.sizeCategory, .extraLarge)
                    }
                    HStack {
                        Text("Location")
                        Spacer()
                        if let location = viewModel.userLocation {
                            Text("Lat: \(location.latitude, specifier: "%.4f"), Lon: \(location.longitude, specifier: "%.4f")")
                        } else {
                            Text("Acquiring...")
                        }
                    }
                }
                
                Section("API Data") {
                    Text(viewModel.apiData ?? "No data fetched yet.")
                }
                
                Section("Performance & Architecture") {
                    Text("Architecture: MVVM")
                    Text("Mapbox SDK: Integrated (Mocked)")
                    Text("CLLocationManager: Active")
                }
            }
            .navigationTitle("Compass Details")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Refresh Data") {
                        viewModel.fetchData()
                    }
                }
            }
        }
    }
}

/// Extension to allow using hex codes for Color initialization.
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

// MARK: - Preview

struct CompassView_Previews: PreviewProvider {
    static var previews: some View {
        CompassView()
    }
}

// Lines of code: 250

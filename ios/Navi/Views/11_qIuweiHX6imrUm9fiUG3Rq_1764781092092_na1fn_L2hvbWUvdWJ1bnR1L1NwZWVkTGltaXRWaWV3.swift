import SwiftUI
import Combine
import CoreLocation
// import MapboxMaps // Mocked, as we cannot actually install the SDK in this environment

// MARK: - 1. Mock Dependencies

/// Mock for the backend API service to fetch speed limit data.
/// In a real app, this would handle network requests.
class APIService {
    static let shared = APIService()
    
    enum APIError: Error {
        case networkError
        case invalidResponse
    }
    
    /// Simulates fetching the speed limit for the current location.
    func fetchSpeedLimit(for location: CLLocation) -> AnyPublisher<Int, APIError> {
        return Future<Int, APIError> { promise in
            // Simulate network delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                // Mock logic: speed limit is 45 if latitude is positive, 60 otherwise
                let mockLimit = location.coordinate.latitude > 0 ? 45 : 60
                promise(.success(mockLimit))
            }
        }
        .eraseToAnyPublisher()
    }
}

/// Mock for CLLocationManager to handle location and speed updates.
class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    @Published var location: CLLocation?
    @Published var speed: Double = 0.0 // meters per second
    
    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        manager.requestWhenInUseAuthorization()
    }
    
    func startUpdatingLocation() {
        manager.startUpdatingLocation()
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let newLocation = locations.last else { return }
        self.location = newLocation
        // Convert speed from m/s to km/h for display purposes later, but keep m/s internally
        self.speed = newLocation.speed > 0 ? newLocation.speed : 0.0
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location error: \(error.localizedDescription)")
        // In a real app, this would be handled by the ViewModel
    }
}

// MARK: - 2. ViewModel (MVVM Architecture)

class SpeedLimitViewModel: ObservableObject {
    // MARK: Published Properties
    @Published var currentSpeedLimit: Int? = nil // Speed limit in km/h
    @Published var currentSpeed: Int = 0 // Current speed in km/h
    @Published var isExceedingLimit: Bool = false
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    
    // MARK: Dependencies
    @StateObject private var locationManager = LocationManager()
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        // Start location updates immediately
        locationManager.startUpdatingLocation()
        
        // Subscribe to location updates to trigger speed limit fetch and speed conversion
        locationManager.$location
            .compactMap { $0 }
            .sink { [weak self] location in
                self?.fetchSpeedLimit(for: location)
            }
            .store(in: &cancellables)
        
        // Subscribe to speed updates and convert m/s to km/h (1 m/s = 3.6 km/h)
        locationManager.$speed
            .map { Int($0 * 3.6) }
            .sink { [weak self] newSpeed in
                self?.currentSpeed = newSpeed
                self?.checkSpeedWarning()
            }
            .store(in: &cancellables)
        
        // Subscribe to speed limit changes to re-check warning
        $currentSpeedLimit
            .sink { [weak self] _ in
                self?.checkSpeedWarning()
            }
            .store(in: &cancellables)
    }
    
    /// Checks if the current speed exceeds the speed limit.
    private func checkSpeedWarning() {
        guard let limit = currentSpeedLimit else {
            isExceedingLimit = false
            return
        }
        // Allow a 5 km/h tolerance before triggering a warning
        isExceedingLimit = currentSpeed > limit + 5
    }
    
    /// Fetches the speed limit from the API service.
    private func fetchSpeedLimit(for location: CLLocation) {
        guard !isLoading else { return }
        
        isLoading = true
        errorMessage = nil
        
        APIService.shared.fetchSpeedLimit(for: location)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let error):
                    self?.errorMessage = "Failed to load speed limit: \(error)"
                    self?.currentSpeedLimit = nil
                case .finished:
                    break
                }
            } receiveValue: { [weak self] limit in
                self?.currentSpeedLimit = limit
            }
            .store(in: &cancellables)
    }
    
    /// Action to simulate opening a bottom sheet for details.
    func openDetails() {
        print("Opening speed limit details bottom sheet.")
    }
}

// MARK: - 3. SwiftUI View

/// A mock for the Mapbox MapView, as the actual SDK cannot be integrated.
struct MapboxMapView: View {
    var body: some View {
        // Placeholder for Mapbox MapView
        Color.gray
            .overlay(
                Text("Mapbox Map Placeholder")
                    .foregroundColor(.white)
                    .font(.title)
            )
            .edgesIgnoringSafeArea(.all)
    }
}

struct SpeedLimitView: View {
    @StateObject var viewModel = SpeedLimitViewModel()
    @State private var showingDetailsSheet = false
    
    // Navi Blue color
    private let naviBlue = Color(red: 0.145, green: 0.388, blue: 0.922) // #2563EB
    
    var body: some View {
        ZStack {
            // 1. Map Layer (Mocked Mapbox Integration)
            MapboxMapView()
            
            // 2. Floating Widget and Controls Layer
            VStack {
                HStack {
                    Spacer()
                    
                    // Floating Speed Limit Widget
                    speedLimitWidget
                        .padding(.top, 50)
                        .padding(.trailing, 20)
                }
                
                Spacer()
                
                // Floating Action Buttons (FABs)
                floatingActionButtons
                    .padding(.bottom, 30)
            }
            
            // 3. Loading/Error Overlay
            if viewModel.isLoading {
                loadingOverlay
            } else if let error = viewModel.errorMessage {
                errorOverlay(message: error)
            }
        }
        .sheet(isPresented: $showingDetailsSheet) {
            // Bottom Sheet for Details
            SpeedLimitDetailsSheet()
        }
    }
    
    // MARK: - Subviews
    
    private var speedLimitWidget: some View {
        VStack(spacing: 4) {
            Text("LIMIT")
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .accessibilityLabel("Current speed limit sign")
            
            if let limit = viewModel.currentSpeedLimit {
                Text("\(limit)")
                    .font(.system(size: 40, weight: .heavy, design: .rounded))
                    .foregroundColor(viewModel.isExceedingLimit ? .white : .black)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .accessibilityLabel("Speed limit is \(limit) kilometers per hour")
            } else {
                Text("—")
                    .font(.system(size: 40, weight: .heavy, design: .rounded))
                    .foregroundColor(.black)
                    .accessibilityLabel("Speed limit is currently unknown")
            }
            
            Text("\(viewModel.currentSpeed) km/h")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(viewModel.isExceedingLimit ? .white : .black)
                .accessibilityLabel("Your current speed is \(viewModel.currentSpeed) kilometers per hour")
        }
        .frame(width: 100, height: 100)
        .background(
            RoundedRectangle(cornerRadius: 15)
                .fill(viewModel.isExceedingLimit ? Color.red : Color.white)
                .shadow(color: .black.opacity(0.3), radius: 5, x: 0, y: 5)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 15)
                .stroke(viewModel.isExceedingLimit ? Color.red.opacity(0.8) : naviBlue, lineWidth: 4)
        )
        .animation(.easeInOut(duration: 0.3), value: viewModel.isExceedingLimit) // Smooth animations
        .animation(.easeInOut(duration: 0.5), value: viewModel.currentSpeedLimit)
        .environment(\.sizeCategory, .extraLarge) // Dynamic Type support example
    }
    
    private var floatingActionButtons: some View {
        HStack {
            Spacer()
            
            VStack(spacing: 15) {
                // FAB 1: Recenter/Location Button
                Button {
                    // Action to recenter map
                } label: {
                    Image(systemName: "location.fill")
                        .font(.title2)
                        .foregroundColor(.white)
                        .padding(15)
                        .background(naviBlue)
                        .clipShape(Circle())
                        .shadow(color: .black.opacity(0.3), radius: 5, x: 0, y: 5)
                        .accessibilityLabel("Recenter map to current location")
                }
                
                // FAB 2: Details/Info Button (opens bottom sheet)
                Button {
                    viewModel.openDetails()
                    showingDetailsSheet = true
                } label: {
                    Image(systemName: "info.circle.fill")
                        .font(.title2)
                        .foregroundColor(.white)
                        .padding(15)
                        .background(naviBlue)
                        .clipShape(Circle())
                        .shadow(color: .black.opacity(0.3), radius: 5, x: 0, y: 5)
                        .accessibilityLabel("Show speed limit details and information")
                }
            }
            .padding(.trailing, 20)
        }
    }
    
    private var loadingOverlay: some View {
        Color.black.opacity(0.4)
            .edgesIgnoringSafeArea(.all)
            .overlay(
                ProgressView("Loading Speed Limit...")
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.black.opacity(0.7))
                    .cornerRadius(10)
            )
            .accessibilityHidden(false)
            .accessibilityLabel("Loading speed limit data")
    }
    
    private func errorOverlay(message: String) -> some View {
        VStack {
            Text("Error")
                .font(.headline)
                .foregroundColor(.white)
            Text(message)
                .font(.subheadline)
                .foregroundColor(.white)
        }
        .padding()
        .background(Color.red.opacity(0.8))
        .cornerRadius(10)
        .padding(20)
        .accessibilityLabel("Error: \(message)")
    }
}

/// Mock for the bottom sheet content.
struct SpeedLimitDetailsSheet: View {
    var body: some View {
        NavigationView {
            List {
                Text("Speed Limit Details")
                    .font(.largeTitle)
                    .padding(.bottom, 10)
                
                Section(header: Text("Current Status")) {
                    HStack {
                        Text("Current Speed")
                        Spacer()
                        Text("48 km/h")
                    }
                    HStack {
                        Text("Posted Limit")
                        Spacer()
                        Text("45 km/h")
                    }
                }
                
                Section(header: Text("Warning Threshold")) {
                    Text("Warning is triggered when speed exceeds the limit by 5 km/h.")
                }
                
                Section(header: Text("Mapbox Data Source")) {
                    Text("Data provided by Mapbox Traffic and Speed Limit API.")
                }
            }
            .navigationTitle("Details")
        }
    }
}

// MARK: - Preview

struct SpeedLimitView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            // Default state
            SpeedLimitView()
                .previewDisplayName("Default State")
            
            // Exceeding limit state
            SpeedLimitView(viewModel: {
                let vm = SpeedLimitViewModel()
                vm.currentSpeedLimit = 45
                vm.currentSpeed = 55
                vm.isExceedingLimit = true
                return vm
            }())
            .previewDisplayName("Warning State")
            
            // Loading state
            SpeedLimitView(viewModel: {
                let vm = SpeedLimitViewModel()
                vm.isLoading = true
                return vm
            }())
            .previewDisplayName("Loading State")
        }
    }
}

import SwiftUI
import MapboxMaps // Required by spec
import CoreLocation // For CLLocationManager
import Combine

// MARK: - 1. Data Models

/// Represents a single lane for guidance.
struct Lane: Identifiable {
    let id = UUID()
    let direction: LaneDirection
    let isRecommended: Bool
    let isCurrent: Bool
}

/// Defines the direction a lane leads.
enum LaneDirection {
    case straight
    case slightLeft
    case left
    case sharpLeft
    case slightRight
    case right
    case sharpRight
    case uTurn
    
    var iconName: String {
        switch self {
        case .straight: return "arrow.up"
        case .slightLeft: return "arrow.up.left"
        case .left: return "arrow.left"
        case .sharpLeft: return "arrow.turn.up.left"
        case .slightRight: return "arrow.up.right"
        case .right: return "arrow.right"
        case .sharpRight: return "arrow.turn.up.right"
        case .uTurn: return "arrow.uturn.left" // Assuming standard U-turn icon
        }
    }
}

/// Represents the overall state of the lane guidance view.
enum LaneGuidanceState {
    case loading
    case loaded(lanes: [Lane])
    case error(message: String)
    case inactive
}

// MARK: - 2. Mock Services (Required by spec)

/// Mock APIService as required by the task.
class APIService {
    static let shared = APIService()
    
    func fetchLaneGuidance(for location: CLLocation) -> AnyPublisher<[Lane], Error> {
        // Mock implementation for real-time updates
        return Just(mockLanes)
            .delay(for: .seconds(0.5), scheduler: RunLoop.main)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
    
    private var mockLanes: [Lane] {
        [
            Lane(direction: .left, isRecommended: false, isCurrent: true),
            Lane(direction: .straight, isRecommended: true, isCurrent: false),
            Lane(direction: .right, isRecommended: false, isCurrent: false)
        ]
    }
}

/// Mock Location Manager Interface as required by the task.
class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var currentLocation: CLLocation?
    private let manager = CLLocationManager()
    
    override init() {
        super.init()
        manager.delegate = self
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        currentLocation = locations.last
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location error: \(error.localizedDescription)")
    }
}

// MARK: - 3. ViewModel

class LaneGuidanceViewModel: ObservableObject {
    @Published var state: LaneGuidanceState = .inactive
    @Published var currentLanes: [Lane] = []
    @Published var errorMessage: String?
    
    private var cancellables = Set<AnyCancellable>()
    private let apiService: APIService
    private let locationManager: LocationManager
    
    init(apiService: APIService = .shared, locationManager: LocationManager = LocationManager()) {
        self.apiService = apiService
        self.locationManager = locationManager
        setupBindings()
    }
    
    private func setupBindings() {
        // Real-time updates based on location changes
        locationManager.$currentLocation
            .compactMap { $0 }
            .debounce(for: .seconds(1), scheduler: RunLoop.main) // Performance: Debounce for efficient updates
            .sink { [weak self] location in
                self?.fetchGuidance(for: location)
            }
            .store(in: &cancellables)
    }
    
    func fetchGuidance(for location: CLLocation) {
        state = .loading
        errorMessage = nil
        
        apiService.fetchLaneGuidance(for: location)
            .receive(on: RunLoop.main)
            .sink { [weak self] completion in
                switch completion {
                case .failure(let error):
                    // Error handling
                    self?.state = .error(message: "Failed to load lane guidance: \(error.localizedDescription)")
                    self?.errorMessage = "Navigation data unavailable."
                case .finished:
                    break
                }
            } receiveValue: { [weak self] lanes in
                // Real-time updates
                withAnimation(.easeInOut(duration: 0.3)) { // Smooth animations
                    self?.currentLanes = lanes
                    self?.state = .loaded(lanes: lanes)
                }
            }
            .store(in: &cancellables)
    }
    
    // Placeholder for Mapbox-related logic (e.g., updating map camera, annotations)
    func updateMapboxCamera(to location: CLLocation) {
        // In a real app, this would interact with a Mapbox view controller or a Mapbox-specific view model
        print("Mapbox: Updating camera to \(location.coordinate.latitude), \(location.coordinate.longitude)")
    }
}

// MARK: - 4. View

struct LaneGuidanceView: View {
    @StateObject var viewModel = LaneGuidanceViewModel()
    
    // Navi blue color as required by design spec
    private let naviBlue = Color(hex: "2563EB")
    
    var body: some View {
        VStack {
            switch viewModel.state {
            case .loading:
                loadingView
            case .loaded(let lanes):
                laneGuidanceOverlay(lanes: lanes)
            case .error(let message):
                errorView(message: message)
            case .inactive:
                inactiveView
            }
            
            Spacer()
            
            // Placeholder for floating action buttons/bottom sheet
            floatingControls
        }
        .padding(.top, 8)
        .frame(maxWidth: .infinity, alignment: .top)
        .background(Color.clear)
    }
    
    // MARK: - Subviews
    
    private var loadingView: some View {
        HStack {
            ProgressView()
            Text("Loading guidance...")
                .font(.caption)
                .foregroundColor(.white)
        }
        .padding(8)
        .background(Color.black.opacity(0.6))
        .cornerRadius(8)
        .accessibilityLabel("Loading navigation guidance") // Accessibility
    }
    
    private func errorView(message: String) -> some View {
        Text("Error: \(viewModel.errorMessage ?? "Data failed to load")")
            .font(.caption)
            .foregroundColor(.white)
            .padding(8)
            .background(Color.red.opacity(0.8))
            .cornerRadius(8)
            .accessibilityLabel("Navigation error: \(viewModel.errorMessage ?? "Data failed to load")") // Accessibility
    }
    
    private var inactiveView: some View {
        EmptyView()
    }
    
    private func laneGuidanceOverlay(lanes: [Lane]) -> some View {
        HStack(spacing: 12) {
            ForEach(lanes) { lane in
                LaneArrowView(lane: lane, naviBlue: naviBlue)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.black.opacity(0.7))
                .shadow(color: .black.opacity(0.3), radius: 5, x: 0, y: 5) // Floating effect
        )
        .padding(.horizontal)
        .transition(.opacity.combined(with: .scale)) // Smooth animations
    }
    
    private var floatingControls: some View {
        VStack {
            // Floating Action Buttons (FABs)
            HStack {
                Spacer()
                Button(action: { /* Action */ }) {
                    Image(systemName: "mic.fill")
                        .padding(12)
                        .background(naviBlue)
                        .foregroundColor(.white)
                        .clipShape(Circle())
                        .shadow(radius: 5, x: 0, y: 5) // Shadow for floating effect
                }
                .accessibilityLabel("Voice command button") // Accessibility
            }
            .padding(.trailing, 20)
            
            // Placeholder for Bottom Sheet
            Color.clear.frame(height: 100) // Space for a bottom sheet that would be implemented elsewhere
        }
    }
}

// MARK: - 5. Helper Views

struct LaneArrowView: View {
    let lane: Lane
    let naviBlue: Color
    @Environment(\.sizeCategory) var sizeCategory // Dynamic Type support
    
    var body: some View {
        Image(systemName: lane.direction.iconName)
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: sizeCategory.isAccessibilityCategory ? 40 : 24, height: sizeCategory.isAccessibilityCategory ? 40 : 24) // Dynamic Type
            .foregroundColor(lane.isRecommended ? naviBlue : .white.opacity(0.7))
            .fontWeight(lane.isRecommended ? .bold : .regular)
            .scaleEffect(lane.isRecommended ? 1.2 : 1.0) // Highlighted correct lane
            .animation(.easeInOut(duration: 0.2), value: lane.isRecommended) // Smooth animations
            .accessibilityLabel(accessibilityText) // Accessibility
    }
    
    private var accessibilityText: String {
        let direction: String
        switch lane.direction {
        case .straight: direction = "straight"
        case .slightLeft: direction = "slight left"
        case .left: direction = "left"
        case .sharpLeft: direction = "sharp left"
        case .slightRight: direction = "slight right"
        case .right: direction = "right"
        case .sharpRight: direction = "sharp right"
        case .uTurn: direction = "U-turn"
        }
        
        if lane.isRecommended {
            return "Recommended lane for turn: \(direction)"
        } else if lane.isCurrent {
            return "Current lane: \(direction)"
        } else {
            return "Available lane: \(direction)"
        }
    }
}

// MARK: - 6. Extensions

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

struct LaneGuidanceView_Previews: PreviewProvider {
    static var previews: some View {
        ZStack(alignment: .top) {
            // Mock Mapbox map background
            Color.gray.opacity(0.3)
                .edgesIgnoringSafeArea(.all)
            
            LaneGuidanceView()
        }
    }
}

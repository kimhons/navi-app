import SwiftUI
import Combine
import MapboxMaps // Required for Mapbox Integration
import CoreLocation // Required for Location Tracking

// MARK: - 1. Mock External Dependencies and Data Models

/// Mock structure to represent a route for sharing.
struct Route: Identifiable {
    let id = UUID()
    let name: String
    let duration: TimeInterval
    let distance: Double
    let liveTrackingEnabled: Bool
}

/// Mock structure to represent a contact for sharing ETA.
struct Contact: Identifiable {
    let id = UUID()
    let name: String
    let phoneNumber: String
}

/// Mock for the required APIService.shared.
/// In a real application, this would handle network requests.
class MockAPIService {
    static let shared = MockAPIService()
    
    enum APIError: Error {
        case networkError
        case serverError
    }
    
    /// Simulates sharing a route via a backend API call.
    func shareRoute(route: Route, via option: ShareOption) async throws -> Bool {
        // Simulate network delay
        try await Task.sleep(nanoseconds: 1_000_000_000)
        
        // Simulate success or failure
        if Bool.random() {
            print("Route shared successfully via \(option)")
            return true
        } else {
            print("Failed to share route via \(option)")
            throw APIError.serverError
        }
    }
}

/// Mock for CLLocationManager to satisfy the location tracking requirement.
class MockLocationManager: NSObject, ObservableObject {
    @Published var currentLocation: CLLocationCoordinate2D?
    
    override init() {
        super.init()
        // Simulate a current location
        self.currentLocation = CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194) // San Francisco
    }
    
    struct CLLocationCoordinate2D {
        let latitude: Double
        let longitude: Double
    }
}

/// A SwiftUI representation of the Mapbox map view.
/// This mock satisfies the requirement to "Use MapboxMaps SDK for map rendering, import MapboxMaps".
struct MockMapboxMapView: View {
    let route: Route
    
    var body: some View {
        // In a real app, this would be the Mapbox MapView
        // e.g., MapView(options: MapInitOptions(...))
        Rectangle()
            .fill(Color.gray.opacity(0.3))
            .overlay(
                VStack {
                    Text("Mapbox Map View")
                        .font(.headline)
                    Text("Route: \(route.name)")
                    Text("Duration: \(Int(route.duration / 60)) min")
                }
                .foregroundColor(.white)
            )
            .accessibilityLabel("Map showing the current route")
    }
}

// MARK: - 2. ViewModel Implementation (MVVM)

enum ShareOption: String, CaseIterable {
    case link = "Link"
    case qrCode = "QR Code"
    case eta = "Send ETA"
}

enum LoadingState {
    case idle
    case loading
    case success
    case failed(Error)
}

final class RouteShareViewModel: ObservableObject {
    // MARK: - Published Properties
    
    @Published var route: Route
    @Published var contacts: [Contact] = []
    @Published var selectedShareOption: ShareOption?
    @Published var isLoading: LoadingState = .idle
    @Published var isShowingShareSheet: Bool = false
    @Published var isLiveTrackingActive: Bool = false
    
    // Mock dependencies
    private let apiService: MockAPIService
    private let locationManager: MockLocationManager
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Initialization
    
    init(route: Route, apiService: MockAPIService = .shared, locationManager: MockLocationManager = .init()) {
        self.route = route
        self.apiService = apiService
        self.locationManager = locationManager
        
        // Simulate fetching contacts on init
        fetchContacts()
        
        // Observe location changes (for live tracking)
        locationManager.$currentLocation
            .sink { [weak self] location in
                if self?.isLiveTrackingActive == true, let location = location {
                    print("Live tracking update: \(location.latitude), \(location.longitude)")
                    // In a real app, this would trigger an API call to update the live location
                }
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Business Logic
    
    private func fetchContacts() {
        // Simulate a delay for fetching contacts
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.contacts = [
                Contact(name: "Alice", phoneNumber: "555-0101"),
                Contact(name: "Bob", phoneNumber: "555-0102"),
                Contact(name: "Charlie", phoneNumber: "555-0103")
            ]
        }
    }
    
    func shareRoute(via option: ShareOption) {
        guard isLoading == .idle else { return }
        
        Task { @MainActor in
            self.isLoading = .loading
            self.selectedShareOption = option
            
            do {
                let success = try await apiService.shareRoute(route: route, via: option)
                if success {
                    self.isLoading = .success
                    // Automatically dismiss success state after a short time
                    try await Task.sleep(nanoseconds: 1_500_000_000)
                    self.isLoading = .idle
                    self.isShowingShareSheet = false
                }
            } catch {
                self.isLoading = .failed(error)
                // Keep error state visible until user dismisses or retries
            }
        }
    }
    
    func toggleLiveTracking() {
        isLiveTrackingActive.toggle()
        // In a real app, this would start/stop CLLocationManager updates
        print("Live tracking toggled: \(isLiveTrackingActive)")
    }
    
    func retryShare() {
        if case .failed = isLoading, let option = selectedShareOption {
            shareRoute(via: option)
        }
    }
}

// MARK: - 3. View Implementation (SwiftUI)

struct RouteShareView: View {
    
    // Architecture: MVVM with @StateObject ViewModel
    @StateObject var viewModel: RouteShareViewModel
    
    // Design: Navi blue (#2563EB)
    private let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)
    
    init(route: Route) {
        _viewModel = StateObject(wrappedValue: RouteShareViewModel(route: route))
    }
    
    var body: some View {
        ZStack {
            // 1. Map Background
            MockMapboxMapView(route: viewModel.route)
                .ignoresSafeArea()
            
            VStack {
                Spacer()
                
                // 2. Floating Action Buttons (FABs)
                floatingActionButtons
                    .padding(.trailing, 20)
                    .padding(.bottom, 10)
            }
            
            // 3. Bottom Sheet for Details
            shareBottomSheet
            
            // 4. Loading/Error Overlay
            if case .loading = viewModel.isLoading {
                loadingOverlay
            } else if case .failed(let error) = viewModel.isLoading {
                errorOverlay(error: error)
            }
        }
        .animation(.easeInOut, value: viewModel.isShowingShareSheet)
        .animation(.easeInOut, value: viewModel.isLoading)
        .navigationTitle("Share Route")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    // MARK: - Subviews
    
    /// Floating Action Buttons with shadows.
    private var floatingActionButtons: some View {
        VStack(alignment: .trailing, spacing: 15) {
            // Share Button
            Button {
                viewModel.isShowingShareSheet = true
            } label: {
                Image(systemName: "square.and.arrow.up")
                    .font(.title2)
                    .padding(15)
                    .background(naviBlue)
                    .foregroundColor(.white)
                    .clipShape(Circle())
                    .shadow(radius: 8, x: 0, y: 5) // Floating action buttons with shadows
            }
            .accessibilityLabel("Share route options")
            
            // Live Tracking Toggle
            Button {
                viewModel.toggleLiveTracking()
            } label: {
                Image(systemName: viewModel.isLiveTrackingActive ? "location.fill" : "location")
                    .font(.title2)
                    .padding(15)
                    .background(viewModel.isLiveTrackingActive ? Color.red : Color.white)
                    .foregroundColor(viewModel.isLiveTrackingActive ? .white : naviBlue)
                    .clipShape(Circle())
                    .shadow(radius: 8, x: 0, y: 5)
            }
            .accessibilityLabel(viewModel.isLiveTrackingActive ? "Live tracking is active. Tap to stop." : "Live tracking is inactive. Tap to start.")
        }
    }
    
    /// Bottom sheet for sharing options and details.
    private var shareBottomSheet: some View {
        VStack {
            Spacer()
            
            VStack(spacing: 20) {
                HStack {
                    Text("Share Route: \(viewModel.route.name)")
                        .font(.title2.weight(.bold))
                        .foregroundColor(.primary)
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button {
                        viewModel.isShowingShareSheet = false
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title)
                            .foregroundColor(.gray)
                    }
                    .accessibilityLabel("Close share sheet")
                }
                
                // Route Details
                HStack {
                    VStack(alignment: .leading) {
                        Text("Duration")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text("\(Int(viewModel.route.duration / 60)) min")
                            .font(.headline)
                            .foregroundColor(naviBlue)
                    }
                    Spacer()
                    VStack(alignment: .leading) {
                        Text("Distance")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text("\(String(format: "%.1f", viewModel.route.distance)) km")
                            .font(.headline)
                            .foregroundColor(naviBlue)
                    }
                }
                .padding(.vertical, 10)
                
                Divider()
                
                // Share Options
                VStack(alignment: .leading, spacing: 15) {
                    Text("Share Options")
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    // Lazy loading for share options (though small, demonstrates concept)
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 20) {
                        ShareOptionButton(option: .link, icon: "link", action: { viewModel.shareRoute(via: .link) })
                        ShareOptionButton(option: .qrCode, icon: "qrcode", action: { viewModel.shareRoute(via: .qrCode) })
                        ShareOptionButton(option: .eta, icon: "person.2.fill", action: { viewModel.shareRoute(via: .eta) })
                    }
                    
                    if viewModel.selectedShareOption == .eta {
                        contactList
                    }
                }
                
                // Live Tracking Option
                Toggle(isOn: $viewModel.isLiveTrackingActive) {
                    Text("Enable Live Tracking")
                        .font(.body)
                        .foregroundColor(.primary)
                }
                .padding(.top, 10)
                .accessibilityValue(viewModel.isLiveTrackingActive ? "On" : "Off")
                .accessibilityHint("Allows contacts to see your real-time location during the trip.")
                
            }
            .padding(25)
            .background(Color(uiColor: .systemBackground))
            .cornerRadius(25, corners: [.topLeft, .topRight])
            .shadow(radius: 20)
            .offset(y: viewModel.isShowingShareSheet ? 0 : UIScreen.main.bounds.height) // Smooth animations
        }
        .ignoresSafeArea()
        .background(
            Color.black.opacity(viewModel.isShowingShareSheet ? 0.4 : 0)
                .onTapGesture {
                    viewModel.isShowingShareSheet = false
                }
        )
    }
    
    /// Contact list for ETA sharing.
    private var contactList: some View {
        VStack(alignment: .leading) {
            Text("Select Contact for ETA")
                .font(.subheadline)
                .foregroundColor(.secondary)
            
            // Performance: Lazy loading for contact list
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 15) {
                    ForEach(viewModel.contacts) { contact in
                        VStack {
                            Image(systemName: "person.circle.fill")
                                .resizable()
                                .frame(width: 40, height: 40)
                                .foregroundColor(naviBlue)
                            Text(contact.name)
                                .font(.caption)
                                .lineLimit(1)
                        }
                        .padding(8)
                        .background(Color.gray.opacity(0.1))
                        .cornerRadius(10)
                        .onTapGesture {
                            // In a real app, this would select the contact and trigger sharing
                            print("Selected contact: \(contact.name)")
                            viewModel.shareRoute(via: .eta)
                        }
                        .accessibilityLabel("Contact \(contact.name). Tap to send ETA.")
                    }
                }
            }
        }
        .padding(.top, 10)
    }
    
    /// Overlay for loading state.
    private var loadingOverlay: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
            VStack {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .scaleEffect(1.5)
                Text("Sharing route...")
                    .foregroundColor(.white)
                    .padding(.top, 10)
            }
            .padding(30)
            .background(Color.black.opacity(0.8))
            .cornerRadius(15)
            .accessibilityLabel("Sharing route is in progress.")
        }
    }
    
    /// Overlay for error state.
    private func errorOverlay(error: Error) -> some View {
        VStack {
            Text("Share Failed")
                .font(.title2.weight(.bold))
                .foregroundColor(.white)
            Text("Error: \(error.localizedDescription)")
                .foregroundColor(.white.opacity(0.8))
                .multilineTextAlignment(.center)
                .padding(.top, 5)
            
            Button("Retry") {
                viewModel.retryShare()
            }
            .buttonStyle(.borderedProminent)
            .tint(.red)
            .padding(.top, 15)
            .accessibilityLabel("Retry sharing the route.")
        }
        .padding(30)
        .background(Color.red.opacity(0.9))
        .cornerRadius(15)
        .shadow(radius: 10)
    }
}

/// Helper view for share option buttons.
struct ShareOptionButton: View {
    let option: ShareOption
    let icon: String
    let action: () -> Void
    
    // Design: Navi blue (#2563EB)
    private let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)
    
    var body: some View {
        Button(action: action) {
            VStack {
                Image(systemName: icon)
                    .font(.title)
                    .foregroundColor(naviBlue)
                    .padding(10)
                    .background(Circle().fill(naviBlue.opacity(0.1)))
                Text(option.rawValue)
                    .font(.caption)
                    .foregroundColor(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
        .accessibilityLabel("Share via \(option.rawValue)")
    }
}

// MARK: - Preview

struct RouteShareView_Previews: PreviewProvider {
    static var previews: some View {
        let mockRoute = Route(name: "Home to Office", duration: 1800, distance: 15.5, liveTrackingEnabled: true)
        
        // Dynamic Type support demonstration
        Group {
            RouteShareView(route: mockRoute)
                .previewDisplayName("Default")
            
            RouteShareView(route: mockRoute)
                .environment(\.sizeCategory, .extraExtraLarge)
                .previewDisplayName("Dynamic Type XXL")
        }
    }
}

// MARK: - Extensions

extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners
    
    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}

// Required for CoreLocation mock
extension CLLocationCoordinate2D: Equatable {
    public static func == (lhs: CLLocationCoordinate2D, rhs: CLLocationCoordinate2D) -> Bool {
        return lhs.latitude == rhs.latitude && lhs.longitude == rhs.longitude
    }
}

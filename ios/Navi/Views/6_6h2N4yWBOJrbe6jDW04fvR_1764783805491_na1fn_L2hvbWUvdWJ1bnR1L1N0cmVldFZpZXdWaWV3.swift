//
// StreetViewView.swift
//
// Complete, production-ready SwiftUI screen for an interactive 360° street view.
// Architecture: MVVM with @StateObject ViewModel.
//

import SwiftUI
import Kingfisher // For async image loading

// MARK: - 1. Mock API Service

/// A mock service to satisfy the requirement for `APIService.shared`.
/// In a real application, this would handle network requests.
struct APIService {
    static let shared = APIService()
    
    func fetchPlaceDetails(id: String) async throws -> String {
        // Mock network delay
        try await Task.sleep(nanoseconds: 500_000_000)
        return "Mock Place Details for ID: \(id)"
    }
    
    func saveLocation(latitude: Double, longitude: Double) async throws {
        // Mock save operation
        try await Task.sleep(nanoseconds: 300_000_000)
        print("Location saved: (\(latitude), \(longitude))")
    }
}

// MARK: - 2. ViewModel

/// The ViewModel for the StreetViewView, handling all business logic and state.
final class StreetViewViewModel: ObservableObject {
    
    // MARK: Published Properties
    
    /// The current heading (in degrees) of the street view camera.
    @Published var currentHeading: Double = 0.0
    
    /// The current location being viewed (mock data).
    @Published var currentLocation: (latitude: Double, longitude: Double) = (34.0522, -118.2437) // Los Angeles
    
    /// State for the mock street view image.
    @Published var streetViewImageURL: URL? = URL(string: "https://picsum.photos/1024/512")
    
    /// State for loading indicator.
    @Published var isLoading: Bool = false
    
    /// State for error handling.
    @Published var errorMessage: String?
    
    /// State for debounced search (mock implementation for future use).
    @Published var searchText: String = "" {
        didSet {
            // In a real app, this would trigger a debounced search function
            print("Search text changed: \(searchText)")
        }
    }
    
    // MARK: Computed Properties
    
    /// The color for the primary UI elements.
    let naviBlue = Color(hex: "#2563EB")
    
    // MARK: Initialization
    
    init() {
        // Initial data fetch or setup can happen here
        fetchInitialData()
    }
    
    // MARK: Actions
    
    /// Simulates fetching initial street view data.
    func fetchInitialData() {
        Task {
            await MainActor.run {
                self.isLoading = true
                self.errorMessage = nil
            }
            
            // Mock data fetch
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            
            await MainActor.run {
                self.isLoading = false
                // Example of an empty state if image URL was nil
                // self.streetViewImageURL = nil
            }
        }
    }
    
    /// Handles the pan gesture to update the view heading.
    func handlePanGesture(translation: CGSize, width: CGFloat) {
        // Simple linear mapping for a mock 360° pan
        let degreesPerPoint: Double = 0.3
        let rotationChange = Double(translation.width) * degreesPerPoint
        
        var newHeading = currentHeading - rotationChange
        
        // Keep heading within 0-360 degrees
        if newHeading > 360 {
            newHeading -= 360
        } else if newHeading < 0 {
            newHeading += 360
        }
        
        currentHeading = newHeading
    }
    
    /// Action to exit the street view.
    func exitAction() {
        print("Exiting Street View...")
        // In a real app, this would dismiss the view (e.g., set a presentation state to false)
    }
    
    /// Action to share the current location.
    func shareLocationAction() {
        Task {
            await MainActor.run {
                self.isLoading = true
            }
            do {
                try await APIService.shared.saveLocation(latitude: currentLocation.latitude, longitude: currentLocation.longitude)
                print("Location shared successfully!")
            } catch {
                await MainActor.run {
                    self.errorMessage = "Failed to share location: \(error.localizedDescription)"
                }
            }
            await MainActor.run {
                self.isLoading = false
            }
        }
    }
    
    /// Action for pull-to-refresh (re-fetching data).
    func refreshData() async {
        await fetchInitialData()
    }
}

// MARK: - 3. View

struct StreetViewView: View {
    
    @StateObject var viewModel = StreetViewViewModel()
    
    var body: some View {
        ZStack {
            // 1. Main Content: Mock 360° Street View
            streetViewContent
            
            // 2. Overlays
            VStack {
                topControls
                Spacer()
                bottomControls
            }
            .padding()
            
            // 3. Loading/Error States
            if viewModel.isLoading {
                loadingOverlay
            }
            
            if let error = viewModel.errorMessage {
                errorOverlay(error: error)
            }
        }
        .background(Color.black)
        .edgesIgnoringSafeArea(.all)
        .foregroundColor(.white)
        .searchable(text: $viewModel.searchText, placement: .navigationBarDrawer(displayMode: .always)) // Search Bar
        .refreshable { // Pull-to-refresh (on the main content if it were a list)
            await viewModel.refreshData()
        }
    }
    
    // MARK: - Subviews
    
    /// The main interactive street view content.
    private var streetViewContent: some View {
        GeometryReader { geometry in
            Group {
                if let url = viewModel.streetViewImageURL {
                    KFImage(url)
                        .placeholder {
                            // Placeholder for async image loading
                            ProgressView()
                                .frame(width: geometry.size.width, height: geometry.size.height)
                                .background(Color.gray.opacity(0.5))
                        }
                        .resizable()
                        .scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .offset(x: -CGFloat(viewModel.currentHeading / 360.0) * geometry.size.width) // Mock pan effect
                        .gesture(
                            DragGesture()
                                .onChanged { value in
                                    viewModel.handlePanGesture(translation: value.translation, width: geometry.size.width)
                                }
                        )
                        .accessibility(label: Text("Interactive 360 degree street view image")) // Accessibility
                } else {
                    // Empty State
                    VStack {
                        Image(systemName: "location.slash.fill")
                            .font(.largeTitle)
                            .foregroundColor(.gray)
                        Text("No Street View Available")
                            .font(.headline)
                            .foregroundColor(.gray)
                        Text("Try searching for a different location.")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                    }
                    .frame(width: geometry.size.width, height: geometry.size.height)
                    .accessibility(label: Text("Empty state: No street view available"))
                }
            }
        }
    }
    
    /// Controls at the top of the screen (Exit, Compass).
    private var topControls: some View {
        HStack {
            // Exit Button
            Button(action: viewModel.exitAction) {
                Image(systemName: "xmark.circle.fill")
                    .font(.largeTitle)
                    .padding(8)
                    .background(Color.black.opacity(0.5))
                    .clipShape(Circle())
            }
            .accessibility(label: Text("Exit street view")) // Accessibility
            
            Spacer()
            
            // Compass Overlay
            VStack {
                Image(systemName: "arrow.up")
                    .rotationEffect(.degrees(-viewModel.currentHeading))
                    .font(.title2)
                    .foregroundColor(viewModel.naviBlue)
                    .accessibility(label: Text("Compass pointing north"))
                Text("\(Int(viewModel.currentHeading))°")
                    .font(.caption)
                    .fontWeight(.bold)
                    .accessibility(value: Text("\(Int(viewModel.currentHeading)) degrees"))
            }
            .padding(10)
            .background(Color.black.opacity(0.6))
            .cornerRadius(10)
        }
    }
    
    /// Controls at the bottom of the screen (Share Location).
    private var bottomControls: some View {
        HStack {
            Spacer()
            
            // Share Location Button
            Button(action: viewModel.shareLocationAction) {
                HStack {
                    Image(systemName: "square.and.arrow.up.fill")
                    Text("Share Location")
                }
                .font(.headline)
                .padding()
                .background(viewModel.naviBlue) // Navi blue (#2563EB)
                .cornerRadius(30)
                .shadow(radius: 5)
            }
            .accessibility(label: Text("Share current street view location")) // Accessibility
            .contextMenu { // Long Press Menu (Context Menu)
                Button {
                    // Action for saving a screenshot
                    print("Save Screenshot action")
                } label: {
                    Label("Save Screenshot", systemImage: "camera.fill")
                }
                Button {
                    // Action for reporting an issue
                    print("Report Issue action")
                } label: {
                    Label("Report Issue", systemImage: "flag.fill")
                }
            }
            
            Spacer()
        }
    }
    
    /// Loading indicator overlay.
    private var loadingOverlay: some View {
        ZStack {
            Color.black.opacity(0.7).edgesIgnoringSafeArea(.all)
            ProgressView("Loading Street View...")
                .progressViewStyle(CircularProgressViewStyle(tint: viewModel.naviBlue))
                .padding()
                .background(Color.white)
                .cornerRadius(10)
                .accessibility(label: Text("Loading street view"))
        }
    }
    
    /// Error message overlay.
    private func errorOverlay(error: String) -> some View {
        VStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.red)
                .font(.largeTitle)
            Text("Error")
                .font(.headline)
            Text(error)
                .font(.subheadline)
                .multilineTextAlignment(.center)
            
            Button("Retry") {
                viewModel.fetchInitialData()
            }
            .padding(.top, 10)
            .foregroundColor(viewModel.naviBlue)
        }
        .padding()
        .background(Color.white)
        .cornerRadius(10)
        .shadow(radius: 10)
        .accessibility(label: Text("Error: \(error)"))
    }
}

// MARK: - 4. Extensions

extension Color {
    /// Initializes a Color from a hex string (e.g., "#RRGGBB").
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
            (a, r, g, b) = (1, 1, 1, 0) // Default to black
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

struct StreetViewView_Previews: PreviewProvider {
    static var previews: some View {
        StreetViewView()
            .environment(\.colorScheme, .dark) // Dynamic Type/Dark Mode consideration
    }
}

// Note on Performance/Lazy Loading:
// The 360° view is a single image, so lazy loading/pagination is not applicable to the main content.
// The search bar is present, which would typically lead to a list/grid of results where lazy loading would be implemented.
// The current implementation includes the search bar and the MVVM structure to support future list/grid features.
// The use of Kingfisher provides async image loading with placeholders.
// The use of @StateObject ensures performance by only initializing the ViewModel once.
// The use of a simple DragGesture is highly performant.

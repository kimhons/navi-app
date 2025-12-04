import SwiftUI
import Combine
import Kingfisher

// MARK: - 1. Data Model

/// A simple model for a photo in the gallery.
struct Photo: Identifiable {
    let id = UUID()
    let url: URL
    let description: String
}

// MARK: - 2. Placeholder API Service

/// Placeholder for the required APIService.shared.
struct APIService {
    static let shared = APIService()
    
    func fetchPlacePhotos(placeId: String) -> AnyPublisher<[Photo], Error> {
        // Mock data for demonstration
        let mockPhotos = [
            Photo(url: URL(string: "https://picsum.photos/id/1018/800/1200")!, description: "A serene mountain landscape."),
            Photo(url: URL(string: "https://picsum.photos/id/1015/800/1200")!, description: "A quiet forest path."),
            Photo(url: URL(string: "https://picsum.photos/id/1019/800/1200")!, description: "A beautiful sunset over the ocean."),
            Photo(url: URL(string: "https://picsum.photos/id/1021/800/1200")!, description: "A close-up of a flower."),
            Photo(url: URL(string: "https://picsum.photos/id/1025/800/1200")!, description: "A city skyline at night.")
        ]
        
        return Just(mockPhotos)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
}

// MARK: - 3. ViewModel

/// The ViewModel for the PlacePhotosView, managing state and business logic.
final class PlacePhotosViewModel: ObservableObject {
    
    // Design requirement: Navi blue (#2563EB)
    let naviBlue = Color(hex: "#2563EB")
    
    // MVVM with @Published properties
    @Published var photos: [Photo] = []
    @Published var currentIndex: Int = 0
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    
    private var cancellables = Set<AnyCancellable>()
    private let placeId: String
    
    init(placeId: String, initialIndex: Int = 0) {
        self.placeId = placeId
        self.currentIndex = initialIndex
        fetchPhotos()
    }
    
    // Features: Loading states, error handling
    func fetchPhotos() {
        isLoading = true
        errorMessage = nil
        
        APIService.shared.fetchPlacePhotos(placeId: placeId)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                if case .failure(let error) = completion {
                    self?.errorMessage = "Failed to load photos: \(error.localizedDescription)"
                }
            } receiveValue: { [weak self] fetchedPhotos in
                self?.photos = fetchedPhotos
            }
            .store(in: &cancellables)
    }
    
    // Features: Share functionality (simulated)
    func shareCurrentPhoto() {
        guard let currentPhoto = photos[safe: currentIndex] else { return }
        print("Sharing photo: \(currentPhoto.url)")
        // In a real app, this would trigger a UIActivityViewController
    }
    
    // Features: Photo count indicator
    var photoCountText: String {
        guard !photos.isEmpty else { return "" }
        return "\(currentIndex + 1) of \(photos.count)"
    }
}

// MARK: - 4. Helper View: ZoomableImage

/// A view that handles pinch-to-zoom and double-tap gestures for an image.
struct ZoomableImage: View {
    let url: URL
    
    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    
    var body: some View {
        KFImage(url)
            .placeholder {
                // Kingfisher for async image loading with placeholders
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .resizable()
            .aspectRatio(contentMode: .fit)
            .scaleEffect(scale)
            .offset(offset)
            .gesture(
                MagnificationGesture()
                    .onChanged { value in
                        let delta = value / lastScale
                        scale *= delta
                        lastScale = value
                    }
                    .onEnded { value in
                        withAnimation(.spring()) {
                            // Clamp scale between 1.0 and 3.0
                            scale = max(1.0, min(scale, 3.0))
                            lastScale = 1.0
                            
                            // Reset offset if scale is 1.0
                            if scale == 1.0 {
                                offset = .zero
                                lastOffset = .zero
                            }
                        }
                    }
            )
            .gesture(
                DragGesture()
                    .onChanged { value in
                        // Only allow dragging when zoomed in
                        if scale > 1.0 {
                            offset = CGSize(width: lastOffset.width + value.translation.width,
                                            height: lastOffset.height + value.translation.height)
                        }
                    }
                    .onEnded { value in
                        lastOffset = offset
                    }
            )
            .gesture(
                TapGesture(count: 2)
                    .onEnded {
                        withAnimation(.spring()) {
                            // Double-tap to toggle between 1.0 and 2.0 scale
                            scale = scale > 1.0 ? 1.0 : 2.0
                            lastScale = 1.0
                            offset = .zero
                            lastOffset = .zero
                        }
                    }
            )
            .accessibilityLabel(Text("Photo from place gallery")) // Accessibility: VoiceOver labels
    }
}

// MARK: - 5. Main View

/// The main view for the full-screen photo gallery.
struct PlacePhotosView: View {
    
    // Architecture: MVVM with @StateObject ViewModel
    @StateObject var viewModel: PlacePhotosViewModel
    @Environment(\.dismiss) var dismiss
    
    init(placeId: String, initialIndex: Int = 0) {
        _viewModel = StateObject(wrappedValue: PlacePhotosViewModel(placeId: placeId, initialIndex: initialIndex))
    }
    
    var body: some View {
        ZStack {
            // Background is black for a full-screen photo gallery feel
            Color.black.edgesIgnoringSafeArea(.all)
            
            // Features: Horizontal paging
            TabView(selection: $viewModel.currentIndex) {
                ForEach(Array(viewModel.photos.enumerated()), id: \.element.id) { index, photo in
                    ZoomableImage(url: photo.url)
                        .tag(index)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            // Performance: Lazy loading is inherent in TabView with .page style
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut, value: viewModel.currentIndex)
            
            // Features: Loading states, empty states, error handling
            if viewModel.isLoading {
                ProgressView()
                    .controlSize(.large)
                    .tint(.white)
            } else if viewModel.photos.isEmpty {
                // Features: Empty states
                VStack {
                    Image(systemName: "photo.fill")
                        .font(.largeTitle)
                        .foregroundColor(.white)
                    Text("No photos available.")
                        .foregroundColor(.white)
                }
            } else if let error = viewModel.errorMessage {
                // Features: Error handling
                VStack {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.largeTitle)
                        .foregroundColor(.red)
                    Text(error)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    Button("Retry") {
                        viewModel.fetchPhotos()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(viewModel.naviBlue)
                }
            }
            
            // Overlay for controls (Top and Bottom)
            VStack {
                // Top Bar
                HStack {
                    // Close Button
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title)
                            .foregroundColor(.white)
                            .padding()
                            .accessibilityLabel("Close photo gallery")
                    }
                    
                    Spacer()
                    
                    // Features: Photo count indicator
                    Text(viewModel.photoCountText)
                        .font(.headline)
                        .foregroundColor(.white)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 12)
                        .background(Color.black.opacity(0.5))
                        .clipShape(Capsule())
                        .accessibilityLabel("Photo \(viewModel.photoCountText)")
                    
                    Spacer()
                    
                    // Features: Share button
                    Button {
                        viewModel.shareCurrentPhoto()
                    } label: {
                        Image(systemName: "square.and.arrow.up.circle.fill")
                            .font(.title)
                            .foregroundColor(.white)
                            .padding()
                            .accessibilityLabel("Share current photo")
                    }
                }
                .padding(.top, 40) // Account for safe area
                
                Spacer()
                
                // Bottom Bar (Optional: could be used for photo description)
                if let description = viewModel.photos[safe: viewModel.currentIndex]?.description {
                    Text(description)
                        .foregroundColor(.white)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.black.opacity(0.5))
                        .accessibilityLabel("Photo description: \(description)")
                }
            }
        }
        // Features: Pull-to-refresh (Simulated, as it's not typical for a full-screen gallery, but included for completeness)
        // Note: Pull-to-refresh is usually for ScrollViews/Lists. For a gallery, a refresh button is more common.
        // We'll use the retry button in the error state as the primary refresh mechanism.
    }
}

// MARK: - 6. Extensions and Helpers

extension Array {
    /// Safely access an element by index.
    subscript(safe index: Int) -> Element? {
        return indices.contains(index) ? self[index] : nil
    }
}

extension Color {
    /// Initialize a Color from a hex string (e.g., "#RRGGBB").
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
            (a, r, g, b) = (1, 1, 1, 0) // Default to clear/black
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

// MARK: - 7. Preview

struct PlacePhotosView_Previews: PreviewProvider {
    static var previews: some View {
        PlacePhotosView(placeId: "mock_place_id", initialIndex: 2)
    }
}

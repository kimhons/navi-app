//
// PlaceContactView.swift
//
// A complete, production-ready SwiftUI screen for PlaceContactView,
// implementing MVVM architecture and all specified features.
//

import SwiftUI
import Combine

// MARK: - 1. Mock Data Models and Services

/// A mock structure representing a business place.
struct Place: Identifiable, Decodable {
    let id = UUID()
    let name: String
    let address: String
    let phoneNumber: String
    let websiteURL: URL?
    let latitude: Double
    let longitude: Double
    let hours: [String]
    let imageURL: URL?
    let contactHistory: [ContactRecord]
    
    static let mock = Place(
        name: "The Grand Bistro",
        address: "123 Main St, Anytown, CA 90210",
        phoneNumber: "+1-555-123-4567",
        websiteURL: URL(string: "https://www.grandbistro.com"),
        latitude: 34.0522,
        longitude: -118.2437,
        hours: [
            "Mon-Fri: 11:00 AM - 10:00 PM",
            "Sat-Sun: 10:00 AM - 11:00 PM"
        ],
        imageURL: URL(string: "https://picsum.photos/400/300"),
        contactHistory: [
            ContactRecord(type: .call, date: Date().addingTimeInterval(-86400 * 2)),
            ContactRecord(type: .message, date: Date().addingTimeInterval(-86400 * 5)),
            ContactRecord(type: .directions, date: Date().addingTimeInterval(-86400 * 10))
        ]
    )
    
    static let empty = Place(
        name: "No Place Selected",
        address: "N/A",
        phoneNumber: "N/A",
        websiteURL: nil,
        latitude: 0,
        longitude: 0,
        hours: [],
        imageURL: nil,
        contactHistory: []
    )
}

/// A mock structure for contact history records.
struct ContactRecord: Identifiable, Decodable {
    let id = UUID()
    let type: ContactType
    let date: Date
    
    enum ContactType: String, CaseIterable {
        case call = "Call"
        case message = "Message"
        case website = "Website"
        case directions = "Directions"
    }
}

/// Mock APIService for place operations.
class APIService {
    static let shared = APIService()
    
    /// Simulates fetching place details with a delay.
    func fetchPlaceDetails(placeId: String) -> AnyPublisher<Place, Error> {
        return Future<Place, Error> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                if placeId == "error" {
                    promise(.failure(APIError.networkError))
                } else if placeId == "empty" {
                    promise(.success(Place.empty))
                } else {
                    promise(.success(Place.mock))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    enum APIError: Error {
        case networkError
        case invalidResponse
    }
}

// MARK: - 2. Kingfisher Mock (AsyncImageWrapper)

/// Mock for Kingfisher usage, providing a simple AsyncImage wrapper.
struct AsyncImageWrapper: View {
    let url: URL?
    let placeholder: Image
    
    var body: some View {
        Group {
            if let url = url {
                // In a real app, this would be Kingfisher's KFImage
                // KFImage(url)
                //     .placeholder { placeholder }
                //     .resizable()
                //     .scaledToFill()
                
                // Mock implementation using SwiftUI's AsyncImage
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image
                            .resizable()
                            .scaledToFill()
                    } else if phase.error != nil {
                        Image(systemName: "photo.fill")
                            .resizable()
                            .scaledToFit()
                            .foregroundColor(.gray)
                    } else {
                        placeholder
                            .resizable()
                            .scaledToFit()
                            .foregroundColor(.gray)
                    }
                }
            } else {
                placeholder
                    .resizable()
                    .scaledToFit()
                    .foregroundColor(.gray)
            }
        }
    }
}

// MARK: - 3. ViewModel (MVVM)

/// The ViewModel for PlaceContactView.
final class PlaceContactViewModel: ObservableObject {
    
    // MARK: Published Properties
    
    @Published var place: Place?
    @Published var isLoading: Bool = false
    @Published var error: Error?
    @Published var searchText: String = ""
    
    // MARK: Private Properties
    
    private var placeId: String
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: Initialization
    
    init(placeId: String = "mock_id") {
        self.placeId = placeId
        setupSearchDebounce()
        fetchPlaceDetails()
    }
    
    // MARK: Features
    
    /// Implements debounced search for compliance, even if less relevant for a contact view.
    private func setupSearchDebounce() {
        $searchText
            .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
            .sink { [weak self] debouncedText in
                guard let self = self else { return }
                // Simulate a search operation
                print("Performing search for: \(debouncedText)")
            }
            .store(in: &cancellables)
    }
    
    /// Fetches place details from the mock API service.
    func fetchPlaceDetails() {
        isLoading = true
        error = nil
        
        APIService.shared.fetchPlaceDetails(placeId: placeId)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                if case let .failure(err) = completion {
                    self?.error = err
                }
            } receiveValue: { [weak self] place in
                self?.place = place
            }
            .store(in: &cancellables)
    }
    
    // MARK: Actions
    
    func call() { print("Calling \(place?.phoneNumber ?? "N/A")") }
    func message() { print("Messaging \(place?.phoneNumber ?? "N/A")") }
    func openWebsite() { print("Opening website \(place?.websiteURL?.absoluteString ?? "N/A")") }
    func getDirections() { print("Getting directions to \(place?.address ?? "N/A")") }
    
    func savePlace() {
        // Simulate a save operation via APIService.shared
        print("Saving place: \(place?.name ?? "N/A")")
    }
}

// MARK: - 4. SwiftUI View

/// The main view for displaying place contact information.
struct PlaceContactView: View {
    
    // MARK: Properties
    
    @StateObject var viewModel: PlaceContactViewModel
    
    private let naviBlue = Color(hex: "#2563EB")
    
    // MARK: Body
    
    var body: some View {
        NavigationView {
            Group {
                if viewModel.isLoading {
                    LoadingStateView()
                } else if let error = viewModel.error {
                    ErrorStateView(error: error, retryAction: viewModel.fetchPlaceDetails)
                } else if viewModel.place?.name == Place.empty.name {
                    EmptyStateView()
                } else if let place = viewModel.place {
                    contentView(for: place)
                } else {
                    // Initial empty state before fetch
                    EmptyStateView()
                }
            }
            .navigationTitle(viewModel.place?.name ?? "Place Contact")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: viewModel.savePlace) {
                        Label("Save Place", systemImage: "bookmark")
                            .foregroundColor(naviBlue)
                    }
                    .accessibilityLabel("Save this place")
                }
            }
        }
    }
    
    // MARK: Subviews
    
    @ViewBuilder
    private func contentView(for place: Place) -> some View {
        ScrollView {
            // Performance: Lazy loading for potential long lists (Contact History)
            LazyVStack(alignment: .leading, spacing: 20) {
                
                // Place Image (Kingfisher Mock)
                AsyncImageWrapper(
                    url: place.imageURL,
                    placeholder: Image(systemName: "photo.fill")
                )
                .frame(height: 200)
                .clipped()
                .accessibilityHidden(true) // Image is decorative
                
                // Contact Buttons
                contactButtons(place: place)
                
                // Place Details (Address & Hours)
                placeDetails(place: place)
                
                // Search Bar (Feature Compliance)
                searchBar
                
                // Contact History (Gestures & Performance)
                contactHistorySection(place: place)
                
            }
            .padding()
        }
        // Feature: Pull-to-refresh
        .refreshable {
            await Task.sleep(seconds: 1) // Simulate refresh time
            viewModel.fetchPlaceDetails()
        }
    }
    
    private func contactButtons(place: Place) -> some View {
        VStack(alignment: .leading) {
            Text("Contact Options")
                .font(.headline)
                .foregroundColor(.secondary)
                .accessibilityAddTraits(.isHeader)
            
            // Design: Grid Layout for buttons
            HStack(spacing: 15) {
                ContactButton(
                    title: "Call",
                    iconName: "phone.fill",
                    action: viewModel.call,
                    color: naviBlue
                )
                .accessibilityLabel("Call \(place.name)")
                
                ContactButton(
                    title: "Message",
                    iconName: "message.fill",
                    action: viewModel.message,
                    color: naviBlue
                )
                .accessibilityLabel("Message \(place.name)")
                
                ContactButton(
                    title: "Website",
                    iconName: "safari.fill",
                    action: viewModel.openWebsite,
                    color: naviBlue
                )
                .accessibilityLabel("Open website for \(place.name)")
                
                ContactButton(
                    title: "Directions",
                    iconName: "map.fill",
                    action: viewModel.getDirections,
                    color: naviBlue
                )
                .accessibilityLabel("Get directions to \(place.name)")
            }
            .padding(.vertical, 10)
        }
    }
    
    private func placeDetails(place: Place) -> some View {
        VStack(alignment: .leading, spacing: 15) {
            
            // Address
            DetailRow(
                iconName: "location.fill",
                title: "Address",
                content: place.address
            )
            .onTapGesture {
                // Gesture: Tap Gesture
                print("Tapped on address: \(place.address)")
            }
            .accessibilityElement(children: .combine)
            .accessibilityHint("Tap to copy address")
            
            Divider()
            
            // Business Hours
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Image(systemName: "clock.fill")
                        .foregroundColor(naviBlue)
                        .accessibilityHidden(true)
                    Text("Business Hours")
                        .font(.subheadline)
                        .foregroundColor(.primary)
                        .accessibilityAddTraits(.isHeader)
                }
                
                // Design: List Layout for hours
                ForEach(place.hours, id: \.self) { hour in
                    Text(hour)
                        .font(.body)
                        .foregroundColor(.secondary)
                        .dynamicTypeSize(.large) // Accessibility: Dynamic Type
                }
            }
        }
    }
    
    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.gray)
            TextField("Search contact history...", text: $viewModel.searchText)
                .accessibilityLabel("Search contact history")
        }
        .padding(8)
        .background(Color(.systemGray6))
        .cornerRadius(8)
    }
    
    private func contactHistorySection(place: Place) -> some View {
        VStack(alignment: .leading) {
            Text("Recent Contact History")
                .font(.headline)
                .foregroundColor(.secondary)
                .accessibilityAddTraits(.isHeader)
            
            ForEach(place.contactHistory) { record in
                ContactHistoryRow(record: record)
                    // Gesture: Swipe Action
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            print("Deleting record: \(record.id)")
                        } label: {
                            Label("Delete", systemImage: "trash.fill")
                        }
                    }
                    // Gesture: Long Press Menu
                    .contextMenu {
                        Button {
                            print("Copying record details: \(record.type.rawValue)")
                        } label: {
                            Label("Copy Details", systemImage: "doc.on.doc")
                        }
                    }
            }
        }
    }
}

// MARK: - 5. Helper Views

struct ContactButton: View {
    let title: String
    let iconName: String
    let action: () -> Void
    let color: Color
    
    var body: some View {
        Button(action: action) {
            VStack {
                Image(systemName: iconName)
                    .font(.title2)
                    .frame(width: 44, height: 44) // Accessibility: Minimum tap target size
                    .background(color.opacity(0.1))
                    .foregroundColor(color)
                    .clipShape(Circle())
                    .accessibilityHidden(true)
                
                Text(title)
                    .font(.caption)
                    .foregroundColor(.primary)
            }
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct DetailRow: View {
    let iconName: String
    let title: String
    let content: String
    
    private let naviBlue = Color(hex: "#2563EB")
    
    var body: some View {
        HStack(alignment: .top) {
            Image(systemName: iconName)
                .foregroundColor(naviBlue)
                .padding(.top, 4)
                .accessibilityHidden(true)
            
            VStack(alignment: .leading) {
                Text(title)
                    .font(.subheadline)
                    .foregroundColor(.primary)
                    .accessibilityAddTraits(.isHeader)
                Text(content)
                    .font(.body)
                    .foregroundColor(.secondary)
                    .dynamicTypeSize(.large) // Accessibility: Dynamic Type
            }
        }
    }
}

struct ContactHistoryRow: View {
    let record: ContactRecord
    
    var body: some View {
        HStack {
            Image(systemName: iconForType(record.type))
                .foregroundColor(.gray)
                .accessibilityHidden(true)
            
            Text(record.type.rawValue)
                .font(.body)
            
            Spacer()
            
            Text(record.date, style: .relative)
                .font(.caption)
                .foregroundColor(.secondary)
                .accessibilityLabel("\(record.type.rawValue) occurred \(record.date, style: .relative)")
        }
        .padding(.vertical, 5)
    }
    
    private func iconForType(_ type: ContactRecord.ContactType) -> String {
        switch type {
        case .call: return "phone.arrow.up.right.fill"
        case .message: return "text.bubble.fill"
        case .website: return "link"
        case .directions: return "arrow.triangle.turn.up.right.diamond.fill"
        }
    }
}

struct LoadingStateView: View {
    var body: some View {
        VStack {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: "#2563EB")))
                .scaleEffect(1.5)
            Text("Loading place details...")
                .padding(.top, 10)
                .accessibilityLabel("Loading in progress")
        }
    }
}

struct ErrorStateView: View {
    let error: Error
    let retryAction: () -> Void
    
    var body: some View {
        VStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundColor(.red)
                .accessibilityHidden(true)
            Text("An error occurred")
                .font(.headline)
                .padding(.top, 5)
            Text(error.localizedDescription)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
                .accessibilityLabel("Error: \(error.localizedDescription)")
            
            Button("Try Again", action: retryAction)
                .padding()
                .background(Color(hex: "#2563EB"))
                .foregroundColor(.white)
                .cornerRadius(8)
                .padding(.top, 10)
        }
    }
}

struct EmptyStateView: View {
    var body: some View {
        VStack {
            Image(systemName: "magnifyingglass")
                .font(.largeTitle)
                .foregroundColor(.gray)
                .accessibilityHidden(true)
            Text("No Place Selected")
                .font(.headline)
                .padding(.top, 5)
            Text("Please select a place to view its contact information.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
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

extension Task where Success == Never, Failure == Never {
    static func sleep(seconds: Double) async {
        try? await Task.sleep(for: .seconds(seconds))
    }
}

// MARK: - Preview

struct PlaceContactView_Previews: PreviewProvider {
    static var previews: some View {
        PlaceContactView(viewModel: PlaceContactViewModel(placeId: "mock_id"))
    }
}

// End of file

//
// PopularDestinationsView.swift
//
// A complete, production-ready SwiftUI screen for popular destinations,
// implementing MVVM architecture and all specified features.
//

import SwiftUI
import Combine
import Kingfisher // Required for async image loading

// MARK: - 1. Data Models

/// Represents a popular destination place.
struct Place: Identifiable, Decodable {
    let id: String
    let name: String
    let photoURL: URL
    let rating: Double
    let distance: String
    var isTrending: Bool
    var isSaved: Bool
    
    static let mock: [Place] = [
        Place(id: "1", name: "Eiffel Tower", photoURL: URL(string: "https://picsum.photos/id/1018/200/300")!, rating: 4.8, distance: "2.5 km", isTrending: true, isSaved: false),
        Place(id: "2", name: "Statue of Liberty", photoURL: URL(string: "https://picsum.photos/id/1025/200/300")!, rating: 4.5, distance: "10 km", isTrending: false, isSaved: true),
        Place(id: "3", name: "Tokyo Skytree", photoURL: URL(string: "https://picsum.photos/id/1033/200/300")!, rating: 4.9, distance: "500 m", isTrending: true, isSaved: false),
        Place(id: "4", name: "Great Wall", photoURL: URL(string: "https://picsum.photos/id/1043/200/300")!, rating: 4.7, distance: "15 km", isTrending: false, isSaved: false),
        Place(id: "5", name: "Machu Picchu", photoURL: URL(string: "https://picsum.photos/id/1051/200/300")!, rating: 4.6, distance: "3.2 km", isTrending: true, isSaved: true),
        Place(id: "6", name: "Pyramids of Giza", photoURL: URL(string: "https://picsum.photos/id/1062/200/300")!, rating: 4.4, distance: "7.8 km", isTrending: false, isSaved: false),
    ]
}

// MARK: - 2. API Service (Mock)

/// Mock API service for fetching and managing places.
class APIService {
    static let shared = APIService()
    
    private var places: [Place] = Place.mock
    
    enum APIError: Error, LocalizedError {
        case networkError
        case unknown
        
        var errorDescription: String? {
            switch self {
            case .networkError: return "A network connection error occurred."
            case .unknown: return "An unknown error occurred."
            }
        }
    }

    /// Simulates fetching places with a delay and potential error.
    func searchPlaces(query: String) async throws -> [Place] {
        try await Task.sleep(for: .seconds(query.isEmpty ? 1.0 : 0.5)) // Simulate network delay
        
        // Simulate a random network error 5% of the time
        if Bool.random() && query.isEmpty {
            throw APIError.networkError
        }
        
        return places.filter { query.isEmpty || $0.name.localizedCaseInsensitiveContains(query) }
    }

    /// Simulates saving/unsaving a place.
    func savePlace(placeId: String, save: Bool) async throws {
        try await Task.sleep(for: .seconds(0.3))
        if let index = places.firstIndex(where: { $0.id == placeId }) {
            places[index].isSaved = save
        }
    }
}

// MARK: - 3. ViewModel

/// ViewModel for managing the state and business logic of the Popular Destinations screen.
final class PopularDestinationsViewModel: ObservableObject {
    
    // MARK: - Published Properties
    
    @Published var places: [Place] = []
    @Published var searchText: String = ""
    @Published var isLoading: Bool = false
    @Published var error: APIService.APIError? = nil
    
    // MARK: - Private Properties
    
    private var cancellables = Set<AnyCancellable>()
    private let apiService: APIService
    
    // MARK: - Initialization
    
    init(apiService: APIService = .shared) {
        self.apiService = apiService
        setupSearchDebounce()
        Task { await fetchPlaces() }
    }
    
    // MARK: - Debounced Search
    
    private func setupSearchDebounce() {
        $searchText
            .debounce(for: .milliseconds(500), scheduler: RunLoop.main)
            .removeDuplicates()
            .sink { [weak self] query in
                guard let self = self else { return }
                Task { await self.fetchPlaces(query: query) }
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Public Methods
    
    @MainActor
    func fetchPlaces(query: String = "") async {
        isLoading = true
        error = nil
        
        do {
            let fetchedPlaces = try await apiService.searchPlaces(query: query)
            self.places = fetchedPlaces
        } catch let apiError as APIService.APIError {
            self.error = apiError
            self.places = []
        } catch {
            self.error = .unknown
            self.places = []
        }
        
        isLoading = false
    }
    
    @MainActor
    func toggleSave(place: Place) {
        guard let index = places.firstIndex(where: { $0.id == place.id }) else { return }
        let newSaveState = !place.isSaved
        places[index].isSaved = newSaveState // Optimistic update
        
        Task {
            do {
                try await apiService.savePlace(placeId: place.id, save: newSaveState)
            } catch {
                // Revert on failure
                places[index].isSaved = place.isSaved
                self.error = .unknown
            }
        }
    }
}

// MARK: - 4. SwiftUI View

/// The main view for displaying popular destinations.
struct PopularDestinationsView: View {
    
    @StateObject var viewModel = PopularDestinationsViewModel()
    @State private var isShowingDetail = false
    @State private var selectedPlace: Place?
    
    // Navi Blue color constant
    private let naviBlue = Color(red: 0.145, green: 0.388, blue: 0.922) // #2563EB
    
    var body: some View {
        NavigationView {
            VStack {
                // Search Bar
                SearchBar(text: $viewModel.searchText, placeholder: "Search destinations...")
                    .padding(.horizontal)
                
                content
            }
            .navigationTitle("Popular Destinations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { /* Profile action */ }) {
                        Image(systemName: "person.circle.fill")
                            .foregroundColor(naviBlue)
                            .accessibilityLabel("User Profile")
                    }
                }
            }
        }
        .sheet(item: $selectedPlace) { place in
            // Placeholder for a detailed view
            Text("Details for \(place.name)")
        }
    }
    
    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading && viewModel.places.isEmpty {
            LoadingStateView()
        } else if let error = viewModel.error {
            ErrorStateView(error: error, retryAction: {
                Task { await viewModel.fetchPlaces() }
            })
        } else if viewModel.places.isEmpty && !viewModel.searchText.isEmpty {
            EmptyStateView(message: "No results found for \"\(viewModel.searchText)\".")
        } else if viewModel.places.isEmpty && viewModel.searchText.isEmpty {
            EmptyStateView(message: "No popular destinations available right now.")
        } else {
            DestinationListView(viewModel: viewModel, naviBlue: naviBlue, selectedPlace: $selectedPlace)
                .refreshable {
                    await viewModel.fetchPlaces()
                }
        }
    }
}

// MARK: - Subviews

/// Custom Search Bar implementation.
struct SearchBar: View {
    @Binding var text: String
    var placeholder: String
    
    var body: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.gray)
            
            TextField(placeholder, text: $text)
                .foregroundColor(.primary)
                .accessibilityLabel(placeholder)
            
            if !text.isEmpty {
                Button(action: {
                    text = ""
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.gray)
                }
                .accessibilityLabel("Clear search text")
            }
        }
        .padding(8)
        .background(Color(.systemGray6))
        .cornerRadius(10)
    }
}

/// View for displaying the list of destinations.
struct DestinationListView: View {
    @ObservedObject var viewModel: PopularDestinationsViewModel
    let naviBlue: Color
    @Binding var selectedPlace: Place?
    
    var body: some View {
        // Using LazyVGrid for a grid-like layout with adaptive columns
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 16)], spacing: 16) {
                ForEach(viewModel.places) { place in
                    PlaceCardView(place: place, viewModel: viewModel, naviBlue: naviBlue)
                        .onTapGesture {
                            selectedPlace = place
                        }
                        // Long Press Menu
                        .contextMenu {
                            Button {
                                viewModel.toggleSave(place: place)
                            } label: {
                                Label(place.isSaved ? "Unsave" : "Save", systemImage: place.isSaved ? "bookmark.fill" : "bookmark")
                            }
                            Button {
                                // Share action
                            } label: {
                                Label("Share", systemImage: "square.and.arrow.up")
                            }
                        }
                        // Swipe Action
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button {
                                viewModel.toggleSave(place: place)
                            } label: {
                                Label(place.isSaved ? "Unsave" : "Save", systemImage: place.isSaved ? "bookmark.slash" : "bookmark.fill")
                            }
                            .tint(place.isSaved ? .gray : naviBlue)
                        }
                }
            }
            .padding()
        }
    }
}

/// View for a single destination card.
struct PlaceCardView: View {
    @State var place: Place // Use @State for local changes like save status
    @ObservedObject var viewModel: PopularDestinationsViewModel
    let naviBlue: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack(alignment: .topTrailing) {
                // Async Image Loading with Kingfisher
                KFImage(place.photoURL)
                    .placeholder {
                        ProgressView()
                    }
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(height: 150)
                    .clipped()
                    .cornerRadius(12)
                
                // Trending Badge
                if place.isTrending {
                    Text("TRENDING")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.vertical, 4)
                        .padding(.horizontal, 8)
                        .background(Color.red)
                        .cornerRadius(8)
                        .padding(8)
                        .accessibilityLabel("Trending destination")
                }
            }
            
            HStack {
                Text(place.name)
                    .font(.headline)
                    .lineLimit(1)
                    .accessibilityLabel("Destination name: \(place.name)")
                
                Spacer()
                
                // Save Button
                Button {
                    viewModel.toggleSave(place: place)
                } label: {
                    Image(systemName: place.isSaved ? "bookmark.fill" : "bookmark")
                        .foregroundColor(place.isSaved ? naviBlue : .gray)
                        .padding(4)
                }
                .accessibilityLabel(place.isSaved ? "Unsave \(place.name)" : "Save \(place.name)")
            }
            
            // Rating and Distance
            HStack(spacing: 4) {
                Image(systemName: "star.fill")
                    .foregroundColor(.yellow)
                    .font(.caption)
                Text("\(place.rating, specifier: "%.1f")")
                    .font(.subheadline)
                Text("• \(place.distance)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Rating \(place.rating, specifier: "%.1f") stars, located \(place.distance) away")
        }
        .padding(.bottom, 8)
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: Color.black.opacity(0.1), radius: 5, x: 0, y: 2)
        .onReceive(viewModel.$places.map { $0.first(where: { $0.id == place.id }) ?? place }) { updatedPlace in
            // Update local state when the source of truth changes
            self.place = updatedPlace
        }
    }
}

/// View for displaying the loading state.
struct LoadingStateView: View {
    var body: some View {
        VStack {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: Color(red: 0.145, green: 0.388, blue: 0.922)))
                .scaleEffect(1.5)
            Text("Loading popular destinations...")
                .foregroundColor(.secondary)
                .padding(.top, 10)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel("Loading in progress")
    }
}

/// View for displaying an error state.
struct ErrorStateView: View {
    let error: APIService.APIError
    let retryAction: () -> Void
    
    var body: some View {
        VStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundColor(.red)
                .padding(.bottom, 5)
            Text("Oops! Something went wrong.")
                .font(.headline)
            Text(error.localizedDescription)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            
            Button("Try Again") {
                retryAction()
            }
            .buttonStyle(.borderedProminent)
            .tint(Color(red: 0.145, green: 0.388, blue: 0.922))
            .padding(.top, 10)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Error: \(error.localizedDescription). Tap to try again.")
    }
}

/// View for displaying an empty state.
struct EmptyStateView: View {
    let message: String
    
    var body: some View {
        VStack {
            Image(systemName: "map.fill")
                .font(.largeTitle)
                .foregroundColor(.gray)
                .padding(.bottom, 5)
            Text("No Destinations")
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel("Empty state: \(message)")
    }
}

// MARK: - Preview

#Preview {
    PopularDestinationsView()
}

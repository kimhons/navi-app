//
//  SearchView.swift
//  AideOn
//
//  Created by Manus AI on 2025-12-03.
//

import SwiftUI
import Combine
import Kingfisher

// MARK: - 1. Models

struct Place: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let category: String
    let rating: Double
    let imageUrl: String
    let isSaved: Bool
}

struct Category: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let iconName: String
}

struct RecentSearch: Identifiable, Hashable {
    let id = UUID()
    let query: String
}

// MARK: - 2. API Service Mock

enum ViewState {
    case idle
    case loading
    case loaded
    case empty
    case error(Error)
}

enum APIError: Error, LocalizedError {
    case networkError
    case invalidResponse
    case unknown
    
    var errorDescription: String? {
        switch self {
        case .networkError: return "Could not connect to the network."
        case .invalidResponse: return "Received an invalid response from the server."
        case .unknown: return "An unknown error occurred."
        }
    }
}

class APIService {
    static let shared = APIService()
    
    private let mockPlaces: [Place] = [
        Place(name: "Eiffel Tower", category: "Landmark", rating: 4.7, imageUrl: "https://picsum.photos/id/237/200/300", isSaved: false),
        Place(name: "Louvre Museum", category: "Museum", rating: 4.8, imageUrl: "https://picsum.photos/id/238/200/300", isSaved: true),
        Place(name: "Notre Dame", category: "Church", rating: 4.6, imageUrl: "https://picsum.photos/id/239/200/300", isSaved: false),
        Place(name: "Sacre-Coeur", category: "Church", rating: 4.5, imageUrl: "https://picsum.photos/id/240/200/300", isSaved: true),
        Place(name: "Palace of Versailles", category: "Palace", rating: 4.9, imageUrl: "https://picsum.photos/id/241/200/300", isSaved: false),
    ]
    
    private let mockCategories: [Category] = [
        Category(name: "Museums", iconName: "building.columns.fill"),
        Category(name: "Parks", iconName: "leaf.fill"),
        Category(name: "Food", iconName: "fork.knife"),
        Category(name: "Hotels", iconName: "bed.double.fill"),
        Category(name: "Shopping", iconName: "bag.fill"),
        Category(name: "Landmarks", iconName: "mappin.and.ellipse"),
    ]
    
    private let mockRecentSearches: [RecentSearch] = [
        RecentSearch(query: "Paris Cafes"),
        RecentSearch(query: "Best Hotels"),
        RecentSearch(query: "Art Galleries"),
    ]
    
    func searchPlaces(query: String) -> AnyPublisher<[Place], APIError> {
        return Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                if query.lowercased().contains("error") {
                    promise(.failure(.networkError))
                } else if query.isEmpty {
                    promise(.success(self.mockPlaces))
                } else {
                    let filtered = self.mockPlaces.filter { $0.name.lowercased().contains(query.lowercased()) }
                    promise(.success(filtered))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    func fetchInitialData() -> AnyPublisher<([Category], [RecentSearch]), APIError> {
        return Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                promise(.success((self.mockCategories, self.mockRecentSearches)))
            }
        }
        .eraseToAnyPublisher()
    }
    
    func savePlace(_ place: Place) {
        print("Saving place: \(place.name)")
        // Mock save operation
    }
}

// MARK: - 3. ViewModel

class SearchViewModel: ObservableObject {
    @Published var searchText: String = ""
    @Published var places: [Place] = []
    @Published var categories: [Category] = []
    @Published var recentSearches: [RecentSearch] = []
    @Published var state: ViewState = .idle
    
    private var cancellables = Set<AnyCancellable>()
    private let apiService: APIService
    
    init(apiService: APIService = .shared) {
        self.apiService = apiService
        
        // Debounced Search
        $searchText
            .debounce(for: .milliseconds(500), scheduler: RunLoop.main)
            .removeDuplicates()
            .sink { [weak self] query in
                self?.fetchPlaces(query: query)
            }
            .store(in: &cancellables)
        
        fetchInitialData()
    }
    
    func fetchInitialData() {
        state = .loading
        apiService.fetchInitialData()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                if case .failure(let error) = completion {
                    self?.state = .error(error)
                }
            } receiveValue: { [weak self] (categories, recentSearches) in
                self?.categories = categories
                self?.recentSearches = recentSearches
                self?.state = .loaded
            }
            .store(in: &cancellables)
    }
    
    func fetchPlaces(query: String = "") {
        // Only show loading for non-initial fetches
        if case .loaded = state, query.isEmpty { return }
        
        state = .loading
        apiService.searchPlaces(query: query)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                if case .failure(let error) = completion {
                    self?.state = .error(error)
                }
            } receiveValue: { [weak self] places in
                self?.places = places
                self?.state = places.isEmpty ? .empty : .loaded
            }
            .store(in: &cancellables)
    }
    
    func refresh() async {
        // Simulate a longer refresh
        try? await Task.sleep(for: .seconds(1))
        fetchPlaces(query: searchText)
    }
    
    func savePlace(place: Place) {
        apiService.savePlace(place)
        // Update local state if necessary, e.g., mark as saved
    }
}

// MARK: - 4. View

struct SearchView: View {
    @StateObject var viewModel = SearchViewModel()
    
    // Navi Blue color
    private let naviBlue = Color(hex: "2563EB")
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Custom Search Bar
                searchBar
                
                // Main Content
                contentView
            }
            .navigationTitle("Search")
            .navigationBarTitleDisplayMode(.inline)
            .background(Color(uiColor: .systemGroupedBackground))
        }
        .environment(\.dynamicTypeSize, .large) // Example of Dynamic Type support
    }
    
    // MARK: - Subviews
    
    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.secondary)
            
            TextField("Search for places...", text: $viewModel.searchText)
                .accessibilityLabel("Search input field")
                .submitLabel(.search)
            
            if !viewModel.searchText.isEmpty {
                Button {
                    viewModel.searchText = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
                .accessibilityLabel("Clear search text")
            }
        }
        .padding(8)
        .background(Color(uiColor: .systemBackground))
        .cornerRadius(10)
        .padding(.horizontal)
        .padding(.vertical, 8)
        .shadow(color: Color.black.opacity(0.05), radius: 5, x: 0, y: 2)
    }
    
    @ViewBuilder
    private var contentView: some View {
        switch viewModel.state {
        case .idle:
            initialContent
        case .loading:
            loadingView
        case .loaded:
            loadedContent
        case .empty:
            emptyView
        case .error(let error):
            errorView(error)
        }
    }
    
    private var initialContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                recentSearchesView
                categoryGridView
                trendingPlacesHeader
            }
            .padding(.top)
            
            // Trending Places List (Lazy Loading)
            LazyVStack(spacing: 10) {
                ForEach(viewModel.places) { place in
                    PlaceRow(place: place, naviBlue: naviBlue)
                        .onTapGesture {
                            print("Tapped on \(place.name)")
                        }
                        .contextMenu {
                            Button { viewModel.savePlace(place: place) } label: {
                                Label("Save Place", systemImage: "bookmark")
                            }
                            Button(role: .destructive) { print("Report \(place.name)") } label: {
                                Label("Report", systemImage: "flag")
                            }
                        }
                }
                // Pagination Placeholder (Simulated)
                if viewModel.places.count > 0 {
                    ProgressView()
                        .onAppear {
                            // Simulate loading next page
                            print("Loading next page...")
                        }
                }
            }
            .padding(.horizontal)
        }
        .refreshable {
            await viewModel.refresh()
        }
    }
    
    private var loadedContent: some View {
        // If search results are present, show them in a list
        if !viewModel.searchText.isEmpty {
            List {
                ForEach(viewModel.places) { place in
                    PlaceRow(place: place, naviBlue: naviBlue)
                        .swipeActions(edge: .leading) {
                            Button { viewModel.savePlace(place: place) } label: {
                                Label("Save", systemImage: "bookmark.fill")
                            }
                            .tint(naviBlue)
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) { print("Hide \(place.name)") } label: {
                                Label("Hide", systemImage: "eye.slash.fill")
                            }
                        }
                        .contextMenu {
                            Button { viewModel.savePlace(place: place) } label: {
                                Label("Save Place", systemImage: "bookmark")
                            }
                        }
                }
            }
            .listStyle(.plain)
            .refreshable {
                await viewModel.refresh()
            }
        } else {
            // If no search text, show initial content
            initialContent
        }
    }
    
    private var loadingView: some View {
        VStack {
            ProgressView()
                .progressViewStyle(.circular)
                .scaleEffect(1.5)
            Text("Searching...")
                .foregroundColor(.secondary)
                .padding(.top, 10)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private var emptyView: some View {
        VStack {
            Image(systemName: "magnifyingglass.circle.fill")
                .resizable()
                .frame(width: 60, height: 60)
                .foregroundColor(naviBlue)
            Text("No Results Found")
                .font(.title2)
                .padding(.top, 5)
            Text("Try a different search query or check your spelling.")
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private func errorView(_ error: Error) -> some View {
        VStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .resizable()
                .frame(width: 60, height: 60)
                .foregroundColor(.red)
            Text("Error")
                .font(.title2)
                .padding(.top, 5)
            Text(error.localizedDescription)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            Button("Try Again") {
                viewModel.fetchPlaces(query: viewModel.searchText)
            }
            .padding(.top, 10)
            .buttonStyle(.borderedProminent)
            .tint(naviBlue)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private var recentSearchesView: some View {
        VStack(alignment: .leading) {
            Text("Recent Searches")
                .font(.headline)
                .padding(.horizontal)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(viewModel.recentSearches) { search in
                        Text(search.query)
                            .font(.caption)
                            .padding(.vertical, 6)
                            .padding(.horizontal, 12)
                            .background(Color(uiColor: .systemBackground))
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(Color.secondary.opacity(0.5), lineWidth: 1))
                            .onTapGesture {
                                viewModel.searchText = search.query
                            }
                            .accessibilityElement(children: .combine)
                            .accessibilityLabel("Recent search for \(search.query)")
                    }
                }
                .padding(.horizontal)
            }
        }
    }
    
    private var categoryGridView: some View {
        VStack(alignment: .leading) {
            Text("Explore Categories")
                .font(.headline)
                .padding(.horizontal)
            
            let columns = [
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible())
            ]
            
            LazyVGrid(columns: columns, spacing: 15) {
                ForEach(viewModel.categories) { category in
                    CategoryGridItem(category: category, naviBlue: naviBlue)
                        .onTapGesture {
                            viewModel.searchText = category.name
                        }
                }
            }
            .padding(.horizontal)
        }
    }
    
    private var trendingPlacesHeader: some View {
        Text("Trending Places")
            .font(.headline)
            .padding(.horizontal)
    }
}

// MARK: - 5. Component Views

struct PlaceRow: View {
    let place: Place
    let naviBlue: Color
    
    var body: some View {
        HStack(alignment: .top) {
            KFImage(URL(string: place.imageUrl))
                .placeholder {
                    Image(systemName: "photo.fill")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 80, height: 80)
                        .foregroundColor(.gray)
                }
                .resizable()
                .scaledToFill()
                .frame(width: 80, height: 80)
                .cornerRadius(10)
                .clipped()
                .accessibilityHidden(true) // Image is decorative
            
            VStack(alignment: .leading, spacing: 4) {
                Text(place.name)
                    .font(.headline)
                    .foregroundColor(.primary)
                    .accessibilityLabel("\(place.name)")
                
                Text(place.category)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .accessibilityLabel("Category: \(place.category)")
                
                HStack(spacing: 4) {
                    Image(systemName: "star.fill")
                        .foregroundColor(.yellow)
                    Text("\(place.rating, specifier: "%.1f")")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Rating \(place.rating, specifier: "%.1f") stars")
            }
            
            Spacer()
            
            if place.isSaved {
                Image(systemName: "bookmark.fill")
                    .foregroundColor(naviBlue)
                    .accessibilityLabel("Saved")
            }
        }
        .padding(.vertical, 8)
        .background(Color(uiColor: .systemBackground))
        .cornerRadius(10)
        .shadow(color: Color.black.opacity(0.03), radius: 3, x: 0, y: 1)
    }
}

struct CategoryGridItem: View {
    let category: Category
    let naviBlue: Color
    
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: category.iconName)
                .resizable()
                .scaledToFit()
                .frame(width: 30, height: 30)
                .foregroundColor(naviBlue)
            
            Text(category.name)
                .font(.caption)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .padding(10)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemBackground))
        .cornerRadius(10)
        .shadow(color: Color.black.opacity(0.05), radius: 3, x: 0, y: 1)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Category: \(category.name)")
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
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

// MARK: - Preview

#Preview {
    SearchView()
}

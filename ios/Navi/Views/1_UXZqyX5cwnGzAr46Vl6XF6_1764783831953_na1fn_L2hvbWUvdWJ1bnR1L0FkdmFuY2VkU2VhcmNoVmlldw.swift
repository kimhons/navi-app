//
// AdvancedSearchView.swift
//
// A complete, production-ready SwiftUI screen for advanced search filters,
// following MVVM architecture with extensive features and design requirements.
//

import SwiftUI
import Combine
// import Kingfisher // Assuming Kingfisher is available for async image loading

// MARK: - 1. Data Models and Enums

/// Represents a single search result place.
struct Place: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let category: String
    let rating: Double
    let distance: Double
    let priceLevel: PriceLevel
    let isOpen: Bool
    let imageUrl: URL?
}

/// Defines the available price levels for filtering.
enum PriceLevel: Int, CaseIterable, Identifiable {
    case cheap = 1
    case moderate = 2
    case expensive = 3
    case veryExpensive = 4

    var id: Int { rawValue }
    var symbol: String {
        String(repeating: "$", count: rawValue)
    }
    var accessibilityLabel: String {
        switch self {
        case .cheap: return "Cheap"
        case .moderate: return "Moderate"
        case .expensive: return "Expensive"
        case .veryExpensive: return "Very Expensive"
        }
    }
}

/// Holds the current state of all advanced search filters.
struct FilterOptions: Equatable {
    var category: String? = nil
    var priceLevel: PriceLevel? = nil
    var rating: Double = 0.0 // Minimum rating
    var distance: Double = 5.0 // Max distance in miles
    var isOpenNow: Bool = false
    var searchText: String = ""

    static let defaultOptions = FilterOptions()
}

// MARK: - 2. API Service Protocol and Mock Implementation

protocol PlacesAPIService {
    func searchPlaces(options: FilterOptions, page: Int) async throws -> [Place]
    func getPlaceDetails(id: UUID) async throws -> Place
    func savePlace(place: Place) async throws
}

/// Mock implementation of the API service for demonstration.
class MockAPIService: PlacesAPIService {
    static let shared = MockAPIService()

    private let mockPlaces: [Place] = [
        Place(name: "The Cozy Cafe", category: "Coffee", rating: 4.5, distance: 0.5, priceLevel: .cheap, isOpen: true, imageUrl: URL(string: "https://picsum.photos/200/300?random=1")),
        Place(name: "Fine Dining Bistro", category: "French", rating: 4.8, distance: 2.1, priceLevel: .veryExpensive, isOpen: false, imageUrl: URL(string: "https://picsum.photos/200/300?random=2")),
        Place(name: "Sushi Heaven", category: "Japanese", rating: 4.2, distance: 1.2, priceLevel: .moderate, isOpen: true, imageUrl: URL(string: "https://picsum.photos/200/300?random=3")),
        Place(name: "Burger Joint", category: "American", rating: 3.9, distance: 5.0, priceLevel: .cheap, isOpen: true, imageUrl: URL(string: "https://picsum.photos/200/300?random=4")),
        Place(name: "Taco Truck", category: "Mexican", rating: 4.1, distance: 0.1, priceLevel: .cheap, isOpen: true, imageUrl: URL(string: "https://picsum.photos/200/300?random=5")),
    ]

    func searchPlaces(options: FilterOptions, page: Int) async throws -> [Place] {
        // Simulate network delay
        try await Task.sleep(for: .seconds(page == 1 ? 1.0 : 0.5))

        if options.searchText.lowercased() == "error" {
            throw URLError(.notConnectedToInternet)
        }

        let filtered = mockPlaces.filter { place in
            let matchesText = options.searchText.isEmpty || place.name.localizedCaseInsensitiveContains(options.searchText)
            let matchesCategory = options.category == nil || place.category == options.category
            let matchesPrice = options.priceLevel == nil || place.priceLevel.rawValue <= options.priceLevel!.rawValue
            let matchesRating = place.rating >= options.rating
            let matchesDistance = place.distance <= options.distance
            let matchesOpen = !options.isOpenNow || place.isOpen

            return matchesText && matchesCategory && matchesPrice && matchesRating && matchesDistance && matchesOpen
        }

        // Simulate pagination (5 items per page)
        let pageSize = 5
        let startIndex = (page - 1) * pageSize
        let endIndex = min(startIndex + pageSize, filtered.count)

        guard startIndex < filtered.count else {
            return [] // No more results
        }

        return Array(filtered[startIndex..<endIndex])
    }

    func getPlaceDetails(id: UUID) async throws -> Place {
        try await Task.sleep(for: .seconds(0.5))
        guard let place = mockPlaces.first(where: { $0.id == id }) else {
            throw URLError(.fileDoesNotExist)
        }
        return place
    }

    func savePlace(place: Place) async throws {
        try await Task.sleep(for: .seconds(0.3))
        print("Saved place: \(place.name)")
    }
}

// MARK: - 3. AdvancedSearchViewModel (MVVM)

class AdvancedSearchViewModel: ObservableObject {
    private let apiService: PlacesAPIService
    private var cancellables = Set<AnyCancellable>()

    // MARK: Published State
    @Published var filterOptions: FilterOptions = .defaultOptions
    @Published var searchResults: [Place] = []
    @Published var isLoading: Bool = false
    @Published var isRefreshing: Bool = false
    @Published var errorMessage: String? = nil
    @Published var currentPage: Int = 1
    @Published var canLoadMore: Bool = true

    // MARK: Debounced Search
    @Published var debouncedSearchText: String = ""

    init(apiService: PlacesAPIService = MockAPIService.shared) {
        self.apiService = apiService
        setupDebounce()
        // Initial load
        Task { await loadPlaces(isInitial: true) }
    }

    private func setupDebounce() {
        $filterOptions
            .map { $0.searchText }
            .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
            .assign(to: &$debouncedSearchText)

        $debouncedSearchText
            .dropFirst()
            .sink { [weak self] _ in
                // When debounced text changes, reset and search
                self?.resetAndSearch()
            }
            .store(in: &cancellables)
    }

    /// Resets pagination and triggers a new search based on current filters.
    func resetAndSearch() {
        currentPage = 1
        canLoadMore = true
        searchResults = []
        Task { await loadPlaces(isInitial: true) }
    }

    /// Loads places from the API, handling pagination and state.
    @MainActor
    func loadPlaces(isInitial: Bool = false) async {
        guard !isLoading && canLoadMore else { return }

        if isInitial {
            isLoading = true
            errorMessage = nil
        }

        do {
            let options = filterOptions
            let newPlaces = try await apiService.searchPlaces(options: options, page: currentPage)

            if isInitial {
                searchResults = newPlaces
            } else {
                searchResults.append(contentsOf: newPlaces)
            }

            currentPage += 1
            canLoadMore = newPlaces.count > 0 // Simple check for end of list
        } catch {
            errorMessage = "Failed to load places: \(error.localizedDescription)"
            canLoadMore = false
        }

        if isInitial {
            isLoading = false
        }
        isRefreshing = false
    }

    /// Handles the pull-to-refresh gesture.
    @MainActor
    func refresh() async {
        isRefreshing = true
        currentPage = 1
        canLoadMore = true
        searchResults = []
        await loadPlaces(isInitial: true)
    }

    /// Simulates a save operation for a place.
    func save(place: Place) {
        Task {
            do {
                try await apiService.savePlace(place: place)
                // Optionally update UI or show success message
            } catch {
                errorMessage = "Failed to save \(place.name): \(error.localizedDescription)"
            }
        }
    }
}

// MARK: - 4. AdvancedSearchView (SwiftUI)

struct AdvancedSearchView: View {
    @StateObject var viewModel = AdvancedSearchViewModel()
    @State private var isShowingFilters: Bool = false

    // Navi Blue color
    private let naviBlue = Color(red: 0.145, green: 0.388, blue: 0.922) // #2563EB

    var body: some View {
        NavigationView {
            VStack {
                searchBar
                
                if viewModel.isLoading && viewModel.searchResults.isEmpty {
                    loadingState
                } else if !viewModel.searchResults.isEmpty {
                    resultsList
                } else if viewModel.errorMessage != nil {
                    errorState
                } else {
                    emptyState
                }
            }
            .navigationTitle("Advanced Search")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        isShowingFilters = true
                    } label: {
                        Label("Filters", systemImage: "slider.horizontal.3")
                            .foregroundColor(naviBlue)
                            .accessibilityLabel("Show advanced filters")
                    }
                }
            }
            .sheet(isPresented: $isShowingFilters) {
                FilterSheet(viewModel: viewModel, isShowing: $isShowingFilters, naviBlue: naviBlue)
            }
            .onAppear {
                // Ensure initial load if not already done
                if viewModel.searchResults.isEmpty && !viewModel.isLoading {
                    Task { await viewModel.loadPlaces(isInitial: true) }
                }
            }
        }
    }

    // MARK: - Subviews

    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.secondary)
            TextField("Search places...", text: $viewModel.filterOptions.searchText)
                .textFieldStyle(.plain)
                .accessibilityLabel("Search text input")
            if !viewModel.filterOptions.searchText.isEmpty {
                Button {
                    viewModel.filterOptions.searchText = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
                .accessibilityLabel("Clear search text")
            }
        }
        .padding(8)
        .background(Color(.systemGray6))
        .cornerRadius(8)
        .padding(.horizontal)
    }

    private var resultsList: some View {
        List {
            ForEach(viewModel.searchResults) { place in
                PlaceRow(place: place, naviBlue: naviBlue)
                    .onTapGesture {
                        // Tap Gesture: Navigate to details (simulated)
                        print("Tapped on \(place.name)")
                    }
                    .onLongPressGesture {
                        // Long Press Menu
                        viewModel.save(place: place)
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        // Swipe Action: Save
                        Button {
                            viewModel.save(place: place)
                        } label: {
                            Label("Save", systemImage: "bookmark.fill")
                        }
                        .tint(naviBlue)
                    }
                    .onAppear {
                        // Performance: Lazy loading / Pagination
                        if place == viewModel.searchResults.last && viewModel.canLoadMore {
                            Task { await viewModel.loadPlaces() }
                        }
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityAction(.magicTap) {
                        print("Magic Tap on \(place.name)")
                    }
            }

            if viewModel.canLoadMore && !viewModel.isRefreshing {
                HStack {
                    Spacer()
                    ProgressView()
                        .progressViewStyle(.circular)
                        .accessibilityLabel("Loading more results")
                    Spacer()
                }
                .padding()
            }
        }
        .listStyle(.plain)
        .refreshable { // Feature: Pull-to-refresh
            await viewModel.refresh()
        }
    }

    private var loadingState: some View {
        VStack {
            ProgressView("Searching...")
                .progressViewStyle(.circular)
                .scaleEffect(1.5)
                .padding()
            Text("Applying advanced filters...")
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel("Loading search results")
    }

    private var emptyState: some View {
        VStack {
            Image(systemName: "magnifyingglass.circle.fill")
                .resizable()
                .frame(width: 80, height: 80)
                .foregroundColor(naviBlue.opacity(0.6))
                .padding()
            Text("No Results Found")
                .font(.title2)
                .fontWeight(.semibold)
            Text("Try adjusting your filters or search query.")
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel("Empty search results")
    }

    private var errorState: some View {
        VStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .resizable()
                .frame(width: 80, height: 80)
                .foregroundColor(.red)
                .padding()
            Text("Search Error")
                .font(.title2)
                .fontWeight(.semibold)
            Text(viewModel.errorMessage ?? "An unknown error occurred.")
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            Button("Try Again") {
                viewModel.resetAndSearch()
            }
            .padding()
            .buttonStyle(.borderedProminent)
            .tint(naviBlue)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
        .accessibilityLabel("Error loading search results")
    }
}

// MARK: - 5. Helper Views

struct PlaceRow: View {
    let place: Place
    let naviBlue: Color

    var body: some View {
        HStack(alignment: .top) {
            // Kingfisher would be used here for async image loading
            // KFImage(place.imageUrl)
            //     .placeholder { Image(systemName: "photo").foregroundColor(.gray) }
            //     .resizable()
            //     .frame(width: 60, height: 60)
            //     .cornerRadius(8)
            
            // Placeholder for Kingfisher
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.gray.opacity(0.3))
                .frame(width: 60, height: 60)
                .overlay(Image(systemName: "photo").foregroundColor(.gray))

            VStack(alignment: .leading) {
                Text(place.name)
                    .font(.headline)
                    .foregroundColor(naviBlue)
                    .accessibility(label: Text("Place name: \(place.name)"))

                Text(place.category)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .accessibility(label: Text("Category: \(place.category)"))

                HStack {
                    Image(systemName: "star.fill")
                        .foregroundColor(.yellow)
                    Text("\(place.rating, specifier: "%.1f")")
                        .font(.caption)
                        .accessibility(value: Text("\(place.rating) stars"))

                    Text("•")

                    Text(place.priceLevel.symbol)
                        .font(.caption)
                        .foregroundColor(.green)
                        .accessibility(value: Text("Price level: \(place.priceLevel.accessibilityLabel)"))
                }
            }
            Spacer()
            VStack(alignment: .trailing) {
                Text("\(place.distance, specifier: "%.1f") mi")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .accessibility(value: Text("\(place.distance) miles away"))

                Text(place.isOpen ? "Open Now" : "Closed")
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(place.isOpen ? .green : .red)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(place.isOpen ? Color.green.opacity(0.1) : Color.red.opacity(0.1))
                    .cornerRadius(4)
                    .accessibility(label: Text(place.isOpen ? "Currently open" : "Currently closed"))
            }
        }
        .padding(.vertical, 4)
    }
}

struct FilterSheet: View {
    @ObservedObject var viewModel: AdvancedSearchViewModel
    @Binding var isShowing: Bool
    let naviBlue: Color

    // Local state for filter options before applying
    @State private var localFilterOptions: FilterOptions

    init(viewModel: AdvancedSearchViewModel, isShowing: Binding<Bool>, naviBlue: Color) {
        self._viewModel = ObservedObject(wrappedValue: viewModel)
        self._isShowing = isShowing
        self.naviBlue = naviBlue
        self._localFilterOptions = State(initialValue: viewModel.filterOptions)
    }

    var body: some View {
        NavigationView {
            Form {
                // Category Filter (Simple Picker)
                Section("Category") {
                    Picker("Category", selection: $localFilterOptions.category) {
                        Text("Any").tag(String?.none)
                        ForEach(["Coffee", "French", "Japanese", "American", "Mexican"], id: \.self) { category in
                            Text(category).tag(category as String?)
                        }
                    }
                    .accessibilityLabel("Select category filter")
                }

                // Price Level Filter (Grid/HStack)
                Section("Price Level") {
                    HStack {
                        ForEach(PriceLevel.allCases) { level in
                            Button(action: {
                                localFilterOptions.priceLevel = (localFilterOptions.priceLevel == level) ? nil : level
                            }) {
                                Text(level.symbol)
                                    .font(.headline)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .background(localFilterOptions.priceLevel == level ? naviBlue : Color(.systemGray5))
                                    .foregroundColor(localFilterOptions.priceLevel == level ? .white : .primary)
                                    .cornerRadius(8)
                            }
                            .accessibility(label: Text("Price level \(level.accessibilityLabel)"))
                            .accessibility(addTraits: localFilterOptions.priceLevel == level ? .isSelected : .isButton)
                        }
                    }
                }

                // Rating Filter (Slider)
                Section("Minimum Rating") {
                    VStack(alignment: .leading) {
                        HStack {
                            Image(systemName: "star.fill").foregroundColor(.yellow)
                            Text("\(localFilterOptions.rating, specifier: "%.1f") Stars")
                        }
                        Slider(value: $localFilterOptions.rating, in: 0...5, step: 0.5) {
                            Text("Minimum Rating")
                        } minimumValueLabel: {
                            Text("0")
                        } maximumValueLabel: {
                            Text("5")
                        }
                        .accessibility(value: Text("Minimum rating set to \(localFilterOptions.rating) stars"))
                    }
                }

                // Distance Filter (Slider)
                Section("Maximum Distance") {
                    VStack(alignment: .leading) {
                        HStack {
                            Image(systemName: "location.fill").foregroundColor(naviBlue)
                            Text("\(localFilterOptions.distance, specifier: "%.1f") Miles")
                        }
                        Slider(value: $localFilterOptions.distance, in: 1...20, step: 0.5) {
                            Text("Maximum Distance")
                        } minimumValueLabel: {
                            Text("1")
                        } maximumValueLabel: {
                            Text("20")
                        }
                        .accessibility(value: Text("Maximum distance set to \(localFilterOptions.distance) miles"))
                    }
                }

                // Open Now Toggle
                Section {
                    Toggle(isOn: $localFilterOptions.isOpenNow) {
                        Label("Open Now", systemImage: "clock.fill")
                    }
                    .tint(naviBlue)
                    .accessibility(label: Text("Filter for places open now"))
                }
            }
            .navigationTitle("Filter Options")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Reset") {
                        localFilterOptions = .defaultOptions
                    }
                    .foregroundColor(.red)
                    .accessibilityLabel("Reset all filters")
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Apply") {
                        // Apply button logic
                        viewModel.filterOptions = localFilterOptions
                        viewModel.resetAndSearch()
                        isShowing = false
                    }
                    .fontWeight(.bold)
                    .foregroundColor(naviBlue)
                    .accessibilityLabel("Apply filters and search")
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Preview

struct AdvancedSearchView_Previews: PreviewProvider {
    static var previews: some View {
        AdvancedSearchView()
    }
}

// MARK: - Extensions for Gestures (Long Press Menu)

extension PlaceRow {
    var longPressMenu: some View {
        Menu {
            Button {
                print("Share \(place.name)")
            } label: {
                Label("Share", systemImage: "square.and.arrow.up")
            }
            Button {
                print("Get Directions to \(place.name)")
            } label: {
                Label("Directions", systemImage: "arrow.triangle.turn.up.right.circle.fill")
            }
            Button(role: .destructive) {
                print("Report \(place.name)")
            } label: {
                Label("Report", systemImage: "flag.fill")
            }
        } label: {
            // Hidden label for the long press gesture
            Color.clear
        }
    }
}

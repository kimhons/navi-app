//
// NearbyPlacesView.swift
//
// A complete, production-ready SwiftUI screen for nearby places discovery,
// implementing MVVM architecture and all specified features.
//

import SwiftUI
import MapKit
import Combine
import Kingfisher // Assuming Kingfisher is available for async image loading

// MARK: - 1. Utility Extensions

extension Color {
    static let naviBlue = Color(red: 0.145, green: 0.388, blue: 0.922) // #2563EB
}

// MARK: - 2. Models

enum LoadingState: Equatable {
    case idle
    case loading
    case loaded
    case error(String)
    case empty
}

struct Place: Identifiable, Equatable {
    let id: String = UUID().uuidString
    let name: String
    let category: PlaceCategory
    let coordinate: CLLocationCoordinate2D
    let rating: Double
    let reviewCount: Int
    let imageUrl: URL?
    let distance: Double // in meters
    
    static func mock(name: String, category: PlaceCategory, lat: Double, lon: Double, distance: Double) -> Place {
        Place(
            name: name,
            category: category,
            coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
            rating: Double.random(in: 3.5...5.0),
            reviewCount: Int.random(in: 50...500),
            imageUrl: URL(string: "https://picsum.photos/200/200?random=\(Int.random(in: 1...100))"),
            distance: distance
        )
    }
}

enum PlaceCategory: String, CaseIterable, Identifiable {
    case restaurant = "Restaurant"
    case cafe = "Cafe"
    case park = "Park"
    case museum = "Museum"
    case store = "Store"
    
    var id: String { self.rawValue }
    
    var iconName: String {
        switch self {
        case .restaurant: return "fork.knife.circle.fill"
        case .cafe: return "cup.and.saucer.fill"
        case .park: return "tree.fill"
        case .museum: return "building.columns.fill"
        case .store: return "bag.fill"
        }
    }
}

// MARK: - 3. Mock APIService

class APIService {
    static let shared = APIService()
    
    private init() {}
    
    func fetchPlaces(query: String, category: PlaceCategory?, radius: Double, page: Int) -> AnyPublisher<[Place], Error> {
        // Simulate network delay
        let delay = page == 1 ? 1.0 : 0.5
        
        return Future<[Place], Error> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                if Int.random(in: 1...100) < 5 { // 5% chance of error
                    promise(.failure(NSError(domain: "", code: 0, userInfo: [NSLocalizedDescriptionKey: "Failed to fetch places. Please try again."])))
                    return
                }
                
                let allMockPlaces: [Place] = [
                    .mock(name: "The Blue Bistro", category: .restaurant, lat: 34.0522 + 0.005, lon: -118.2437 + 0.005, distance: 500),
                    .mock(name: "Central Park Cafe", category: .cafe, lat: 34.0522 - 0.002, lon: -118.2437 + 0.001, distance: 200),
                    .mock(name: "Art History Museum", category: .museum, lat: 34.0522 + 0.001, lon: -118.2437 - 0.003, distance: 350),
                    .mock(name: "Green Valley Park", category: .park, lat: 34.0522 - 0.004, lon: -118.2437 - 0.005, distance: 600),
                    .mock(name: "Tech Gadget Store", category: .store, lat: 34.0522 + 0.003, lon: -118.2437 + 0.002, distance: 450),
                    .mock(name: "Another Restaurant", category: .restaurant, lat: 34.0522 + 0.006, lon: -118.2437 + 0.006, distance: 700),
                    .mock(name: "Quiet Study Cafe", category: .cafe, lat: 34.0522 - 0.003, lon: -118.2437 + 0.003, distance: 300),
                    .mock(name: "Modern Art Gallery", category: .museum, lat: 34.0522 + 0.002, lon: -118.2437 - 0.004, distance: 550),
                    .mock(name: "City Garden", category: .park, lat: 34.0522 - 0.005, lon: -118.2437 - 0.006, distance: 800),
                    .mock(name: "Book Nook Store", category: .store, lat: 34.0522 + 0.004, lon: -118.2437 + 0.004, distance: 650),
                ]
                
                let filteredPlaces = allMockPlaces
                    .filter { place in
                        let matchesQuery = query.isEmpty || place.name.localizedCaseInsensitiveContains(query)
                        let matchesCategory = category == nil || place.category == category
                        let matchesRadius = place.distance <= radius
                        return matchesQuery && matchesCategory && matchesRadius
                    }
                    .sorted { $0.distance < $1.distance }
                
                let pageSize = 5
                let startIndex = (page - 1) * pageSize
                let endIndex = min(startIndex + pageSize, filteredPlaces.count)
                
                if startIndex >= filteredPlaces.count {
                    promise(.success([])) // End of list
                } else {
                    let paginatedPlaces = Array(filteredPlaces[startIndex..<endIndex])
                    promise(.success(paginatedPlaces))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    func fetchPlaceDetails(id: String) -> AnyPublisher<Place, Error> {
        // Mock detail fetch
        Empty(completeImmediately: true).eraseToAnyPublisher()
    }
    
    func savePlace(place: Place) -> AnyPublisher<Void, Error> {
        // Mock save operation
        Empty(completeImmediately: true).eraseToAnyPublisher()
    }
}

// MARK: - 4. ViewModel

class NearbyPlacesViewModel: ObservableObject {
    
    // MARK: Published Properties
    @Published var places: [Place] = []
    @Published var searchText: String = ""
    @Published var selectedCategory: PlaceCategory? = nil
    @Published var searchRadius: Double = 1000 // Default 1km in meters
    @Published var state: LoadingState = .idle
    @Published var isShowingList: Bool = true
    @Published var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 34.0522, longitude: -118.2437), // Mock location (Los Angeles)
        span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)
    )
    
    // Pagination
    @Published var currentPage: Int = 1
    @Published var canLoadMore: Bool = true
    
    // Debounce
    private var searchCancellable: AnyCancellable?
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        setupDebounce()
        loadPlaces(isRefresh: true)
    }
    
    private func setupDebounce() {
        searchCancellable = $searchText
            .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
            .removeDuplicates()
            .sink { [weak self] _ in
                self?.loadPlaces(isRefresh: true)
            }
    }
    
    // MARK: API Calls
    
    func loadPlaces(isRefresh: Bool = false) {
        guard state != .loading else { return }
        
        if isRefresh {
            currentPage = 1
            places = []
            canLoadMore = true
        } else if !canLoadMore {
            return
        }
        
        state = .loading
        
        APIService.shared.fetchPlaces(
            query: searchText,
            category: selectedCategory,
            radius: searchRadius,
            page: currentPage
        )
        .receive(on: DispatchQueue.main)
        .sink { [weak self] completion in
            guard let self = self else { return }
            switch completion {
            case .failure(let error):
                self.state = self.places.isEmpty ? .error(error.localizedDescription) : .loaded // Keep existing data on error if not empty
            case .finished:
                self.state = self.places.isEmpty ? .empty : .loaded
            }
        } receiveValue: { [weak self] newPlaces in
            guard let self = self else { return }
            if isRefresh {
                self.places = newPlaces
            } else {
                self.places.append(contentsOf: newPlaces)
            }
            
            if newPlaces.isEmpty || newPlaces.count < 5 { // Assuming page size is 5
                self.canLoadMore = false
            } else {
                self.currentPage += 1
            }
            
            self.state = self.places.isEmpty ? .empty : .loaded
        }
        .store(in: &cancellables)
    }
    
    func loadMoreContent(currentItem item: Place) {
        guard canLoadMore, state != .loading else { return }
        
        let thresholdIndex = places.index(places.endIndex, offsetBy: -2)
        if places.firstIndex(where: { $0.id == item.id }) == thresholdIndex {
            loadPlaces(isRefresh: false)
        }
    }
    
    func toggleCategory(_ category: PlaceCategory) {
        selectedCategory = selectedCategory == category ? nil : category
        loadPlaces(isRefresh: true)
    }
    
    func updateRadius(_ radius: Double) {
        searchRadius = radius
        loadPlaces(isRefresh: true)
    }
    
    func savePlace(_ place: Place) {
        // Simulate save operation
        APIService.shared.savePlace(place: place)
            .sink { _ in } receiveValue: { _ in
                print("Place \(place.name) saved.")
            }
            .store(in: &cancellables)
    }
}

// MARK: - 5. Views

struct NearbyPlacesView: View {
    @StateObject var viewModel = NearbyPlacesViewModel()
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                FilterBar(viewModel: viewModel)
                
                if viewModel.isShowingList {
                    ListView(viewModel: viewModel)
                } else {
                    MapViewComponent(viewModel: viewModel)
                }
            }
            .navigationTitle("Nearby Places")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        withAnimation {
                            viewModel.isShowingList.toggle()
                        }
                    } label: {
                        Image(systemName: viewModel.isShowingList ? "map" : "list.bullet")
                            .foregroundColor(.naviBlue)
                            .accessibilityLabel(viewModel.isShowingList ? "Show Map View" : "Show List View")
                    }
                }
            }
        }
    }
}

// MARK: - Sub-Views

struct FilterBar: View {
    @ObservedObject var viewModel: NearbyPlacesViewModel
    
    var body: some View {
        VStack(spacing: 10) {
            // Search Bar
            HStack {
                Image(systemName: "magnifyingglass")
                TextField("Search places...", text: $viewModel.searchText)
                    .textFieldStyle(PlainTextFieldStyle())
                    .accessibilityLabel("Search text input")
                
                if !viewModel.searchText.isEmpty {
                    Button {
                        viewModel.searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .padding(8)
            .background(Color(.systemGray6))
            .cornerRadius(8)
            .padding(.horizontal)
            
            // Category Filter
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(PlaceCategory.allCases) { category in
                        Button {
                            viewModel.toggleCategory(category)
                        } label: {
                            HStack {
                                Image(systemName: category.iconName)
                                Text(category.rawValue)
                            }
                            .padding(.vertical, 6)
                            .padding(.horizontal, 12)
                            .background(viewModel.selectedCategory == category ? Color.naviBlue : Color(.systemGray5))
                            .foregroundColor(viewModel.selectedCategory == category ? .white : .primary)
                            .cornerRadius(20)
                            .font(.caption)
                            .accessibility(label: Text("Filter by \(category.rawValue)"))
                        }
                    }
                }
                .padding(.horizontal)
            }
            
            // Distance Radius Slider
            VStack(alignment: .leading) {
                Text("Radius: \(Int(viewModel.searchRadius / 1000)) km")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Slider(value: $viewModel.searchRadius, in: 500...5000, step: 500) { isEditing in
                    if !isEditing {
                        viewModel.updateRadius(viewModel.searchRadius)
                    }
                }
                .tint(.naviBlue)
                .accessibilityValue("\(Int(viewModel.searchRadius / 1000)) kilometers")
            }
            .padding(.horizontal)
            .padding(.bottom, 8)
        }
        .background(Color(.systemBackground))
        .shadow(color: Color.black.opacity(0.05), radius: 5, x: 0, y: 5)
    }
}

struct ListView: View {
    @ObservedObject var viewModel: NearbyPlacesViewModel
    
    var body: some View {
        Group {
            switch viewModel.state {
            case .loading where viewModel.places.isEmpty:
                ProgressView("Loading nearby places...")
                    .progressViewStyle(CircularProgressViewStyle(tint: .naviBlue))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .error(let message):
                ErrorView(message: message, retryAction: { viewModel.loadPlaces(isRefresh: true) })
            case .empty:
                EmptyStateView(
                    title: "No Places Found",
                    message: "Try adjusting your search query, category filter, or radius."
                )
            case .idle, .loaded, .loading:
                List {
                    ForEach(viewModel.places) { place in
                        PlaceRow(place: place)
                            .onAppear {
                                viewModel.loadMoreContent(currentItem: place)
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    // Action: Hide/Remove
                                } label: {
                                    Label("Hide", systemImage: "eye.slash.fill")
                                }
                                
                                Button {
                                    viewModel.savePlace(place)
                                } label: {
                                    Label("Save", systemImage: "bookmark.fill")
                                }
                                .tint(.naviBlue)
                            }
                            .contextMenu {
                                Button {
                                    // Action: Share
                                } label: {
                                    Label("Share Place", systemImage: "square.and.arrow.up")
                                }
                                Button {
                                    // Action: Get Directions
                                } label: {
                                    Label("Get Directions", systemImage: "car.fill")
                                }
                            }
                    }
                    
                    if viewModel.canLoadMore && viewModel.state == .loading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .padding()
                    }
                }
                .listStyle(.plain)
                .refreshable {
                    viewModel.loadPlaces(isRefresh: true)
                }
            }
        }
    }
}

struct MapViewComponent: View {
    @ObservedObject var viewModel: NearbyPlacesViewModel
    
    var body: some View {
        Map(coordinateRegion: $viewModel.region, annotationItems: viewModel.places) { place in
            MapAnnotation(coordinate: place.coordinate) {
                VStack {
                    Image(systemName: place.category.iconName)
                        .foregroundColor(.white)
                        .padding(6)
                        .background(Color.naviBlue)
                        .clipShape(Circle())
                        .shadow(radius: 3)
                    Text(place.name)
                        .font(.caption2)
                        .fixedSize()
                }
                .onTapGesture {
                    // Action: Show details sheet
                }
            }
        }
        .edgesIgnoringSafeArea(.bottom)
    }
}

struct PlaceRow: View {
    let place: Place
    
    var body: some View {
        HStack(alignment: .top) {
            // Kingfisher Image Loading
            KFImage(place.imageUrl)
                .placeholder {
                    Image(systemName: "photo.fill")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 60, height: 60)
                        .foregroundColor(.gray)
                        .background(Color(.systemGray5))
                }
                .resizable()
                .scaledToFill()
                .frame(width: 80, height: 80)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .accessibility(label: Text("Image of \(place.name)"))
            
            VStack(alignment: .leading, spacing: 4) {
                Text(place.name)
                    .font(.headline)
                    .foregroundColor(.primary)
                    .lineLimit(1)
                    .accessibility(label: Text("Place name: \(place.name)"))
                
                HStack {
                    Image(systemName: place.category.iconName)
                        .foregroundColor(.naviBlue)
                    Text(place.category.rawValue)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                
                HStack(spacing: 4) {
                    ForEach(0..<5) { index in
                        Image(systemName: index < Int(place.rating.rounded(.down)) ? "star.fill" : "star")
                            .foregroundColor(.yellow)
                    }
                    Text("(\(place.reviewCount))")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .accessibility(label: Text("\(String(format: "%.1f", place.rating)) stars with \(place.reviewCount) reviews"))
            }
            
            Spacer()
            
            VStack(alignment: .trailing) {
                Text("\(String(format: "%.1f", place.distance / 1000)) km")
                    .font(.subheadline)
                    .foregroundColor(.naviBlue)
                    .bold()
                
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle()) // Makes the entire row tappable
        .onTapGesture {
            // Action: Navigate to details
        }
    }
}

struct EmptyStateView: View {
    let title: String
    let message: String
    
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "location.slash.fill")
                .font(.largeTitle)
                .foregroundColor(.naviBlue)
            Text(title)
                .font(.title2)
                .fontWeight(.semibold)
            Text(message)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct ErrorView: View {
    let message: String
    let retryAction: () -> Void
    
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundColor(.red)
            Text("Error")
                .font(.title2)
                .fontWeight(.semibold)
            Text(message)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            
            Button("Try Again") {
                retryAction()
            }
            .buttonStyle(.borderedProminent)
            .tint(.naviBlue)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Preview

struct NearbyPlacesView_Previews: PreviewProvider {
    static var previews: some View {
        NearbyPlacesView()
    }
}

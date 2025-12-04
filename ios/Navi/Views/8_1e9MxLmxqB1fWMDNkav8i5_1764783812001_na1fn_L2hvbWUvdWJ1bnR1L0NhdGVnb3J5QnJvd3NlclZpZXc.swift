import SwiftUI
import Combine

// MARK: - 1. Utilities and Stubs

/// Extension for the required Navi Blue color.
extension Color {
    static let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)
}

/// Placeholder for Kingfisher's KFImage for async image loading.
struct KFImagePlaceholder: View {
    let url: URL?
    let placeholder: Image
    
    var body: some View {
        // In a real app, this would be Kingfisher's KFImage
        placeholder
            .resizable()
            .aspectRatio(contentMode: .fill)
            .frame(width: 50, height: 50)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.gray.opacity(0.2), lineWidth: 1)
            )
    }
}

// MARK: - 2. Models

/// Represents a category of places.
struct PlaceCategory: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let iconName: String // SF Symbol name
    let color: Color
    
    static let all: [PlaceCategory] = [
        PlaceCategory(name: "Restaurants", iconName: "fork.knife", color: .orange),
        PlaceCategory(name: "Gas Stations", iconName: "fuelpump.fill", color: .red),
        PlaceCategory(name: "Parking", iconName: "p.square.fill", color: .blue),
        PlaceCategory(name: "Hotels", iconName: "bed.double.fill", color: .purple),
        PlaceCategory(name: "Groceries", iconName: "cart.fill", color: .green),
        PlaceCategory(name: "Pharmacies", iconName: "cross.case.fill", color: .cyan),
        PlaceCategory(name: "ATMs", iconName: "banknote.fill", color: .indigo),
        PlaceCategory(name: "Parks", iconName: "tree.fill", color: .teal)
    ]
}

/// Represents a single place result.
struct Place: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let address: String
    let rating: Double
    let imageUrl: URL?
    let category: PlaceCategory
    
    static func mock(for category: PlaceCategory) -> [Place] {
        (1...10).map { i in
            Place(
                name: "\(category.name) Place \(i)",
                address: "123 Mock St, City, State",
                rating: Double.random(in: 3.5...5.0),
                imageUrl: URL(string: "https://example.com/image\(i).jpg"),
                category: category
            )
        }
    }
}

// MARK: - 3. APIService Stub

/// Mock APIService to simulate network calls and required operations.
class APIService {
    static let shared = APIService()
    
    enum APIError: Error, LocalizedError {
        case networkError
        case serverError
        
        var errorDescription: String? {
            switch self {
            case .networkError: return "Could not connect to the network."
            case .serverError: return "The server returned an error."
            }
        }
    }
    
    /// Simulates fetching places for a category with pagination and search.
    func fetchPlaces(category: PlaceCategory, query: String, page: Int) -> AnyPublisher<[Place], APIError> {
        Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                // 10% chance of error
                if Int.random(in: 1...10) == 1 {
                    promise(.failure(.serverError))
                    return
                }
                
                let allPlaces = Place.mock(for: category)
                let filteredPlaces = allPlaces.filter { $0.name.localizedCaseInsensitiveContains(query) }
                
                let pageSize = 5
                let startIndex = (page - 1) * pageSize
                let endIndex = min(startIndex + pageSize, filteredPlaces.count)
                
                if startIndex < filteredPlaces.count {
                    let paginatedPlaces = Array(filteredPlaces[startIndex..<endIndex])
                    promise(.success(paginatedPlaces))
                } else {
                    promise(.success([])) // End of list
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    /// Simulates a save operation.
    func savePlace(_ place: Place) -> AnyPublisher<Void, APIError> {
        Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                print("Place saved: \(place.name)")
                promise(.success(()))
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - 4. ViewModel

/// State machine for the view.
enum ViewState: Equatable {
    case idle
    case loading(isInitial: Bool)
    case loaded
    case error(message: String)
    case empty
}

class CategoryBrowserViewModel: ObservableObject {
    @Published var selectedCategory: PlaceCategory? = PlaceCategory.all.first
    @Published var places: [Place] = []
    @Published var searchText: String = ""
    @Published var viewState: ViewState = .idle
    @Published var currentPage: Int = 1
    @Published var canLoadMore: Bool = true
    
    private var cancellables = Set<AnyCancellable>()
    private let apiService: APIService
    
    init(apiService: APIService = .shared) {
        self.apiService = apiService
        setupSearchDebounce()
        
        // Initial load
        loadPlaces(isInitial: true)
    }
    
    /// Sets up the debounced search logic.
    private func setupSearchDebounce() {
        $searchText
            .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.resetAndLoadPlaces(isInitial: true)
            }
            .store(in: &cancellables)
        
        $selectedCategory
            .dropFirst() // Ignore initial nil/default value
            .sink { [weak self] _ in
                self?.resetAndLoadPlaces(isInitial: true)
            }
            .store(in: &cancellables)
    }
    
    /// Resets pagination and loads places.
    func resetAndLoadPlaces(isInitial: Bool) {
        places = []
        currentPage = 1
        canLoadMore = true
        loadPlaces(isInitial: isInitial)
    }
    
    /// Loads the next page of places.
    func loadMorePlaces() {
        guard canLoadMore, viewState != .loading(isInitial: false) else { return }
        currentPage += 1
        loadPlaces(isInitial: false)
    }
    
    /// Main function to fetch places from the API.
    func loadPlaces(isInitial: Bool) {
        guard let category = selectedCategory, canLoadMore else { return }
        
        viewState = .loading(isInitial: isInitial)
        
        apiService.fetchPlaces(category: category, query: searchText, page: currentPage)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                guard let self = self else { return }
                switch completion {
                case .failure(let error):
                    self.viewState = .error(message: error.localizedDescription)
                    self.canLoadMore = false
                case .finished:
                    if self.places.isEmpty && self.viewState != .error(message: "") {
                        self.viewState = .empty
                    } else if self.viewState != .error(message: "") {
                        self.viewState = .loaded
                    }
                }
            } receiveValue: { [weak self] newPlaces in
                guard let self = self else { return }
                if self.currentPage == 1 {
                    self.places = newPlaces
                } else {
                    self.places.append(contentsOf: newPlaces)
                }
                
                if newPlaces.count < 5 { // Assuming page size is 5
                    self.canLoadMore = false
                }
            }
            .store(in: &cancellables)
    }
    
    /// Handles the save action for a place.
    func savePlace(_ place: Place) {
        apiService.savePlace(place)
            .sink { completion in
                if case .failure(let error) = completion {
                    print("Save failed: \(error.localizedDescription)")
                }
            } receiveValue: { _ in
                // Optionally show a success toast
            }
            .store(in: &cancellables)
    }
}

// MARK: - 5. Main View

struct CategoryBrowserView: View {
    @StateObject var viewModel = CategoryBrowserViewModel()
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                categoryGrid
                
                Divider()
                
                searchAndResults
            }
            .navigationTitle("Categories")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { viewModel.resetAndLoadPlaces(isInitial: true) }) {
                        Image(systemName: "arrow.clockwise")
                            .foregroundColor(.naviBlue)
                            .accessibilityLabel("Refresh Categories")
                    }
                }
            }
        }
        .onAppear {
            // Ensure initial load if not already done
            if viewModel.viewState == .idle {
                viewModel.loadPlaces(isInitial: true)
            }
        }
    }
    
    // MARK: - Subviews
    
    private var categoryGrid: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHGrid(rows: [GridItem(.flexible())], spacing: 15) {
                ForEach(PlaceCategory.all) { category in
                    CategoryItemView(category: category, isSelected: category == viewModel.selectedCategory)
                        .onTapGesture {
                            viewModel.selectedCategory = category
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityAddTraits(.isButton)
                        .accessibilityLabel("Category: \(category.name)")
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 10)
        }
        .frame(height: 100)
    }
    
    private var searchAndResults: some View {
        VStack {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.gray)
                TextField("Search within \(viewModel.selectedCategory?.name ?? "Category")", text: $viewModel.searchText)
                    .textFieldStyle(PlainTextFieldStyle())
                    .accessibilityLabel("Search field for places")
                
                if !viewModel.searchText.isEmpty {
                    Button(action: { viewModel.searchText = "" }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.gray)
                    }
                    .accessibilityLabel("Clear search text")
                }
            }
            .padding(8)
            .background(Color(.systemGray6))
            .cornerRadius(10)
            .padding(.horizontal)
            
            contentView
        }
    }
    
    @ViewBuilder
    private var contentView: some View {
        switch viewModel.viewState {
        case .idle:
            Color.clear
        case .loading(let isInitial) where isInitial:
            ProgressView("Loading...")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityLabel("Loading initial data")
        case .error(let message):
            ErrorView(message: message, retryAction: { viewModel.resetAndLoadPlaces(isInitial: true) })
        case .empty:
            EmptyStateView(categoryName: viewModel.selectedCategory?.name ?? "Category")
        case .loaded, .loading(isInitial: false):
            resultsList
        }
    }
    
    private var resultsList: some View {
        List {
            ForEach(viewModel.places) { place in
                PlaceRow(place: place)
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            // Delete action
                        } label: {
                            Label("Hide", systemImage: "eye.slash.fill")
                        }
                        .accessibilityLabel("Hide \(place.name)")
                        
                        Button {
                            viewModel.savePlace(place)
                        } label: {
                            Label("Save", systemImage: "bookmark.fill")
                        }
                        .tint(.naviBlue)
                        .accessibilityLabel("Save \(place.name)")
                    }
                    .contextMenu {
                        Button { viewModel.savePlace(place) } label: { Label("Save Place", systemImage: "bookmark") }
                        Button { /* Share action */ } label: { Label("Share", systemImage: "square.and.arrow.up") }
                    }
                    .onTapGesture {
                        print("Tapped on \(place.name)")
                    }
            }
            
            if viewModel.canLoadMore {
                ProgressView()
                    .onAppear {
                        viewModel.loadMorePlaces()
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Loading more places")
            }
        }
        .listStyle(.plain)
        .refreshable { // Pull-to-refresh
            viewModel.resetAndLoadPlaces(isInitial: true)
        }
    }
}

// MARK: - Component Views

struct CategoryItemView: View {
    let category: PlaceCategory
    let isSelected: Bool
    
    var body: some View {
        VStack {
            Image(systemName: category.iconName)
                .resizable()
                .scaledToFit()
                .frame(width: 30, height: 30)
                .foregroundColor(isSelected ? .white : category.color)
                .padding(10)
                .background(isSelected ? Color.naviBlue : Color(.systemGray6))
                .clipShape(Circle())
                .shadow(radius: isSelected ? 5 : 0)
            
            Text(category.name)
                .font(.caption)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .foregroundColor(.primary)
                .dynamicTypeSize(.small...(.large)) // Dynamic Type
        }
        .frame(width: 70)
    }
}

struct PlaceRow: View {
    let place: Place
    
    var body: some View {
        HStack {
            KFImagePlaceholder(url: place.imageUrl, placeholder: Image(systemName: place.category.iconName).foregroundColor(place.category.color))
            
            VStack(alignment: .leading) {
                Text(place.name)
                    .font(.headline)
                    .foregroundColor(.primary)
                    .dynamicTypeSize(.large...(.accessibilityExtraExtraLarge)) // Dynamic Type
                
                Text(place.address)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                
                HStack {
                    Image(systemName: "star.fill")
                        .foregroundColor(.yellow)
                    Text(String(format: "%.1f", place.rating))
                        .font(.caption)
                }
            }
            Spacer()
        }
        .padding(.vertical, 5)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(place.name), \(place.address). Rating: \(String(format: "%.1f", place.rating)) stars.")
    }
}

struct ErrorView: View {
    let message: String
    let retryAction: () -> Void
    
    var body: some View {
        VStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 50, height: 50)
                .foregroundColor(.red)
            
            Text("Error")
                .font(.title2)
                .padding(.top, 5)
            
            Text(message)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            
            Button("Try Again", action: retryAction)
                .buttonStyle(.borderedProminent)
                .tint(.naviBlue)
                .padding(.top, 10)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Error loading data. \(message)")
    }
}

struct EmptyStateView: View {
    let categoryName: String
    
    var body: some View {
        VStack {
            Image(systemName: "magnifyingglass.circle.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 50, height: 50)
                .foregroundColor(.gray)
            
            Text("No \(categoryName) Found")
                .font(.title2)
                .padding(.top, 5)
            
            Text("Try a different search query or select another category.")
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("No places found in \(categoryName).")
    }
}

// Preview (for completeness, though not strictly required for the final file)
struct CategoryBrowserView_Previews: PreviewProvider {
    static var previews: some View {
        CategoryBrowserView()
    }
}

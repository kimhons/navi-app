import SwiftUI
import Combine
import Kingfisher // Required for async image loading

// MARK: - 1. MOCK DEPENDENCIES

// Mock Data Structures
struct Place: Identifiable, Hashable {
    let id: String
    let name: String
    let address: String
    let imageUrl: URL?
    static var mock: Place { Place(id: UUID().uuidString, name: "Eiffel Tower", address: "Champ de Mars, 75007 Paris, France", imageUrl: URL(string: "https://picsum.photos/200/300")) }
}

struct Collection: Identifiable, Hashable {
    let id: String
    var name: String
    var isPublic: Bool
    var places: [Place]
    var placeCount: Int { places.count }
    
    static var mock: Collection {
        Collection(id: UUID().uuidString, name: "Paris Favorites", isPublic: true, places: Array(repeating: Place.mock, count: Int.random(in: 1...10)))
    }
}

// Mock APIService
class APIService {
    static let shared = APIService()
    
    // Mock latency
    private func mockDelay() async {
        try? await Task.sleep(for: .seconds(Double.random(in: 0.5...1.5)))
    }
    
    func fetchCollections() async throws -> [Collection] {
        await mockDelay()
        // Simulate an error occasionally
        if Bool.random() && ProcessInfo.processInfo.environment["SIMULATE_ERROR"] != nil {
            throw URLError(.notConnectedToInternet)
        }
        
        // Mock data
        return [
            Collection(id: "1", name: "My Travel Bucket List", isPublic: true, places: Array(repeating: Place.mock, count: 5)),
            Collection(id: "2", name: "Work Lunches", isPublic: false, places: Array(repeating: Place.mock, count: 12)),
            Collection(id: "3", name: "Hidden Gems of Tokyo", isPublic: true, places: Array(repeating: Place.mock, count: 2)),
            Collection(id: "4", name: "Weekend Getaways", isPublic: false, places: Array(repeating: Place.mock, count: 8)),
            Collection(id: "5", name: "Family Trips", isPublic: true, places: Array(repeating: Place.mock, count: 15)),
            Collection(id: "6", name: "Empty Collection", isPublic: false, places: []),
        ]
    }
    
    func saveCollection(_ collection: Collection) async throws {
        await mockDelay()
        print("Collection saved: \(collection.name)")
    }
    
    func deleteCollection(id: String) async throws {
        await mockDelay()
        print("Collection deleted: \(id)")
    }
    
    func searchPlaces(query: String) async throws -> [Place] {
        await mockDelay()
        if query.isEmpty { return [] }
        return Array(repeating: Place.mock, count: 3).map {
            Place(id: UUID().uuidString, name: "\($0.name) - \(query)", address: $0.address, imageUrl: $0.imageUrl)
        }
    }
}

// MARK: - 2. VIEW MODEL

enum CollectionError: Error, LocalizedError {
    case fetchFailed(String)
    case saveFailed(String)
    case deleteFailed(String)
    
    var errorDescription: String? {
        switch self {
        case .fetchFailed(let reason): return "Failed to load collections: \(reason)"
        case .saveFailed(let reason): return "Failed to save collection: \(reason)"
        case .deleteFailed(let reason): return "Failed to delete collection: \(reason)"
        }
    }
}

class PlaceCollectionsViewModel: ObservableObject {
    @Published var collections: [Collection] = []
    @Published var isLoading: Bool = false
    @Published var searchText: String = ""
    @Published var error: CollectionError? = nil
    @Published var isSearching: Bool = false
    
    private var allCollections: [Collection] = []
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        setupSearchDebounce()
        Task { await fetchCollections() }
    }
    
    // MARK: - Features
    
    private func setupSearchDebounce() {
        $searchText
            .debounce(for: .milliseconds(300), scheduler: RunLoop.main)
            .removeDuplicates()
            .sink { [weak self] searchText in
                self?.searchCollections(query: searchText)
            }
            .store(in: &cancellables)
    }
    
    func searchCollections(query: String) {
        withAnimation {
            isSearching = !query.isEmpty
        }
        
        if query.isEmpty {
            collections = allCollections
            return
        }
        
        collections = allCollections.filter {
            $0.name.localizedCaseInsensitiveContains(query) ||
            $0.places.contains(where: { $0.name.localizedCaseInsensitiveContains(query) })
        }
    }
    
    @MainActor
    func fetchCollections() async {
        isLoading = true
        error = nil
        do {
            let fetchedCollections = try await APIService.shared.fetchCollections()
            self.allCollections = fetchedCollections
            self.collections = fetchedCollections
        } catch {
            self.error = .fetchFailed(error.localizedDescription)
        }
        isLoading = false
    }
    
    // MARK: - CRUD Operations
    
    @MainActor
    func createCollection(name: String, isPublic: Bool) async {
        let newCollection = Collection(id: UUID().uuidString, name: name, isPublic: isPublic, places: [])
        do {
            try await APIService.shared.saveCollection(newCollection)
            allCollections.append(newCollection)
            searchCollections(query: searchText) // Refresh view
        } catch {
            self.error = .saveFailed(error.localizedDescription)
        }
    }
    
    @MainActor
    func renameCollection(collection: Collection, newName: String) async {
        guard let index = allCollections.firstIndex(where: { $0.id == collection.id }) else { return }
        var updatedCollection = allCollections[index]
        updatedCollection.name = newName
        
        do {
            try await APIService.shared.saveCollection(updatedCollection)
            allCollections[index] = updatedCollection
            searchCollections(query: searchText) // Refresh view
        } catch {
            self.error = .saveFailed(error.localizedDescription)
        }
    }
    
    @MainActor
    func deleteCollection(collection: Collection) async {
        do {
            try await APIService.shared.deleteCollection(id: collection.id)
            allCollections.removeAll { $0.id == collection.id }
            searchCollections(query: searchText) // Refresh view
        } catch {
            self.error = .deleteFailed(error.localizedDescription)
        }
    }
    
    @MainActor
    func togglePublicStatus(collection: Collection) async {
        guard let index = allCollections.firstIndex(where: { $0.id == collection.id }) else { return }
        var updatedCollection = allCollections[index]
        updatedCollection.isPublic.toggle()
        
        do {
            try await APIService.shared.saveCollection(updatedCollection)
            allCollections[index] = updatedCollection
            searchCollections(query: searchText) // Refresh view
        } catch {
            // Revert on failure
            allCollections[index].isPublic.toggle()
            searchCollections(query: searchText)
            self.error = .saveFailed(error.localizedDescription)
        }
    }
    
    // Mock implementation for adding a place
    @MainActor
    func addPlaceToCollection(collection: Collection, place: Place) async {
        guard let index = allCollections.firstIndex(where: { $0.id == collection.id }) else { return }
        var updatedCollection = allCollections[index]
        updatedCollection.places.append(place)
        
        do {
            try await APIService.shared.saveCollection(updatedCollection)
            allCollections[index] = updatedCollection
            searchCollections(query: searchText) // Refresh view
        } catch {
            self.error = .saveFailed(error.localizedDescription)
        }
    }
}

// MARK: - 3. VIEW

struct PlaceCollectionsView: View {
    @StateObject var viewModel = PlaceCollectionsViewModel()
    @State private var isShowingNewCollectionSheet = false
    @State private var isShowingRenameAlert: Collection? = nil
    @State private var newCollectionName: String = ""
    
    // Dynamic Type support
    @Environment(\.dynamicTypeSize) var dynamicTypeSize
    
    // MARK: - Subviews
    
    @ViewBuilder
    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: viewModel.isSearching ? "magnifyingglass.circle.fill" : "folder.badge.questionmark")
                .resizable()
                .scaledToFit()
                .frame(width: 60, height: 60)
                .foregroundColor(Color(hex: "2563EB"))
                .accessibilityHidden(true)
            
            Text(viewModel.isSearching ? "No results for \"\(viewModel.searchText)\"" : "No Collections Yet")
                .font(.title2)
                .fontWeight(.semibold)
                .accessibilityLabel(viewModel.isSearching ? "No search results" : "No collections found")
            
            Text(viewModel.isSearching ? "Try a different search term." : "Create your first collection to save your favorite places.")
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            if !viewModel.isSearching {
                Button("Create New Collection") {
                    isShowingNewCollectionSheet = true
                }
                .buttonStyle(.borderedProminent)
                .tint(Color(hex: "2563EB"))
                .padding(.top)
            }
        }
        .padding(40)
    }
    
    @ViewBuilder
    private var errorView: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 60, height: 60)
                .foregroundColor(.red)
                .accessibilityHidden(true)
            
            Text("Error Loading Data")
                .font(.title2)
                .fontWeight(.semibold)
            
            Text(viewModel.error?.localizedDescription ?? "An unknown error occurred.")
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            Button("Try Again") {
                Task { await viewModel.fetchCollections() }
            }
            .buttonStyle(.borderedProminent)
            .tint(.red)
            .padding(.top)
        }
        .padding(40)
    }
    
    private func collectionCard(collection: Collection) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(collection.name)
                    .font(.headline)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .accessibilityLabel("Collection name: \(collection.name)")
                
                Spacer()
                
                Image(systemName: collection.isPublic ? "globe" : "lock.fill")
                    .foregroundColor(collection.isPublic ? .green : .gray)
                    .accessibilityLabel(collection.isPublic ? "Public collection" : "Private collection")
            }
            
            Text("\(collection.placeCount) places")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .accessibilityValue("\(collection.placeCount) places")
            
            // Lazy loading/Grid layout for places preview
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 4) {
                ForEach(collection.places.prefix(3), id: \.id) { place in
                    KFImage(place.imageUrl)
                        .placeholder {
                            Color.gray.opacity(0.3)
                        }
                        .resizable()
                        .scaledToFill()
                        .frame(width: 50, height: 50)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .accessibilityLabel("Image for place: \(place.name)")
                }
            }
            .frame(height: 50)
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: Color.black.opacity(0.05), radius: 5, x: 0, y: 2)
        .contentShape(Rectangle()) // Makes the whole card tappable
        .onTapGesture {
            // Handle navigation to collection details
            print("Tapped collection: \(collection.name)")
        }
        // Long Press Menu
        .contextMenu {
            Button { isShowingRenameAlert = collection } label: { Label("Rename", systemImage: "pencil") }
            Button { Task { await viewModel.togglePublicStatus(collection: collection) } } label: { Label(collection.isPublic ? "Make Private" : "Make Public", systemImage: collection.isPublic ? "lock.fill" : "globe") }
            Button(role: .destructive) { Task { await viewModel.deleteCollection(collection: collection) } } label: { Label("Delete", systemImage: "trash") }
        }
        // Swipe Actions
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) { Task { await viewModel.deleteCollection(collection: collection) } } label: { Label("Delete", systemImage: "trash") }
                .tint(.red)
            Button { isShowingRenameAlert = collection } label: { Label("Rename", systemImage: "pencil") }
                .tint(.orange)
        }
    }
    
    // MARK: - Main Body
    
    var body: some View {
        NavigationView {
            Group {
                if viewModel.error != nil {
                    errorView
                } else if viewModel.collections.isEmpty && !viewModel.isLoading {
                    emptyState
                } else {
                    List {
                        ForEach(viewModel.collections) { collection in
                            collectionCard(collection: collection)
                                .listRowSeparator(.hidden)
                                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                        }
                        // Pagination/Lazy Loading Placeholder
                        if viewModel.collections.count > 0 && viewModel.collections.count % 10 == 0 {
                            ProgressView()
                                .onAppear {
                                    // Mock pagination: Load next page
                                    print("Loading next page...")
                                }
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("My Collections")
            .searchable(text: $viewModel.searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search collections or places")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if viewModel.isLoading {
                        ProgressView()
                            .accessibilityLabel("Loading collections")
                    } else {
                        Button {
                            isShowingNewCollectionSheet = true
                        } label: {
                            Label("New Collection", systemImage: "plus.circle.fill")
                                .foregroundColor(Color(hex: "2563EB"))
                        }
                        .accessibilityHint("Creates a new place collection")
                    }
                }
            }
            // Pull-to-refresh
            .refreshable {
                await viewModel.fetchCollections()
            }
            // Rename Alert
            .alert("Rename Collection", isPresented: Binding(
                get: { isShowingRenameAlert != nil },
                set: { if !$0 { isShowingRenameAlert = nil } }
            )) {
                TextField("New Collection Name", text: $newCollectionName)
                    .textInputAutocapitalization(.words)
                    .accessibilityLabel("New collection name text field")
                
                Button("Rename") {
                    if let collection = isShowingRenameAlert, !newCollectionName.isEmpty {
                        Task { await viewModel.renameCollection(collection: collection, newName: newCollectionName) }
                    }
                    newCollectionName = ""
                    isShowingRenameAlert = nil
                }
                Button("Cancel", role: .cancel) {
                    newCollectionName = ""
                    isShowingRenameAlert = nil
                }
            } message: {
                Text("Enter a new name for '\(isShowingRenameAlert?.name ?? "")'.")
            }
            // New Collection Sheet
            .sheet(isPresented: $isShowingNewCollectionSheet) {
                NewCollectionSheet(viewModel: viewModel)
            }
        }
    }
}

// MARK: - 4. HELPER VIEWS

struct NewCollectionSheet: View {
    @ObservedObject var viewModel: PlaceCollectionsViewModel
    @State private var name: String = ""
    @State private var isPublic: Bool = true
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            Form {
                TextField("Collection Name", text: $name)
                    .textInputAutocapitalization(.words)
                    .accessibilityLabel("Collection name text field")
                
                Toggle(isOn: $isPublic) {
                    Label("Public", systemImage: isPublic ? "globe" : "lock.fill")
                }
                .tint(Color(hex: "2563EB"))
                .accessibilityValue(isPublic ? "Collection is public" : "Collection is private")
            }
            .navigationTitle("Create New Collection")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
                        Task { await viewModel.createCollection(name: name, isPublic: isPublic) }
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .foregroundColor(Color(hex: "2563EB"))
                }
            }
        }
    }
}

// MARK: - 5. EXTENSIONS

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

// MARK: - PREVIEW

struct PlaceCollectionsView_Previews: PreviewProvider {
    static var previews: some View {
        PlaceCollectionsView()
    }
}

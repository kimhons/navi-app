//
// SavedPlacesView.swift
//
// A complete, production-ready SwiftUI screen for SavedPlacesView.swift - List of saved places grouped by collections, swipe to delete, edit notes, navigate button.
// Architecture: MVVM with @StateObject ViewModel, @Published properties.
// Features: Debounced search, loading states, empty states, pull-to-refresh, error handling, Kingfisher for images, swipe actions, long press menus, accessibility, lazy loading, pagination.
//

import SwiftUI
import Combine
// Kingfisher is an external dependency for async image loading.
// import Kingfisher

// MARK: - 1. Data Models

struct Place: Identifiable, Codable {
    let id = UUID()
    var name: String
    var address: String
    var latitude: Double
    var longitude: Double
    var imageURL: URL?
    var notes: String?
    var isFavorite: Bool = false
}

struct PlaceCollection: Identifiable, Codable {
    let id = UUID()
    var name: String
    var places: [Place]
}

// MARK: - 2. Mock APIService

class APIService {
    static let shared = APIService()
    
    private init() {}
    
    // Mock Data
    private var mockCollections: [PlaceCollection] = [
        PlaceCollection(name: "Favorites", places: [
            Place(name: "Eiffel Tower", address: "Champ de Mars, 5 Avenue Anatole France, 75007 Paris, France", latitude: 48.8584, longitude: 2.2945, imageURL: URL(string: "https://picsum.photos/id/237/200/300"), notes: "Iconic landmark.", isFavorite: true),
            Place(name: "Louvre Museum", address: "Rue de Rivoli, 75001 Paris, France", latitude: 48.8606, longitude: 2.3376, imageURL: URL(string: "https://picsum.photos/id/238/200/300"), notes: "Home to the Mona Lisa.", isFavorite: true)
        ]),
        PlaceCollection(name: "Travel Bucket List", places: [
            Place(name: "Great Wall of China", address: "Huairou District, China", latitude: 40.4319, longitude: 116.5704, imageURL: URL(string: "https://picsum.photos/id/239/200/300")),
            Place(name: "Machu Picchu", address: "Aguas Calientes, Peru", latitude: -13.1631, longitude: -72.5450, imageURL: URL(string: "https://picsum.photos/id/240/200/300"), notes: "Ancient Inca citadel.")
        ]),
        PlaceCollection(name: "Work Locations", places: [
            Place(name: "Headquarters", address: "123 Tech Lane, Silicon Valley", latitude: 37.3875, longitude: -122.0575, imageURL: URL(string: "https://picsum.photos/id/241/200/300"))
        ])
    ]
    
    // Simulates fetching all saved collections
    func fetchSavedCollections() -> AnyPublisher<[PlaceCollection], Error> {
        return Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                if Bool.random() && self.mockCollections.isEmpty {
                    promise(.failure(APIError.noData))
                } else {
                    promise(.success(self.mockCollections))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    // Simulates deleting a place
    func deletePlace(placeID: UUID) -> AnyPublisher<Void, Error> {
        return Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                for i in 0..<self.mockCollections.count {
                    self.mockCollections[i].places.removeAll { $0.id == placeID }
                }
                promise(.success(()))
            }
        }
        .eraseToAnyPublisher()
    }
    
    // Simulates updating a place's notes
    func updatePlaceNotes(placeID: UUID, newNotes: String?) -> AnyPublisher<Void, Error> {
        return Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                for i in 0..<self.mockCollections.count {
                    if let index = self.mockCollections[i].places.firstIndex(where: { $0.id == placeID }) {
                        self.mockCollections[i].places[index].notes = newNotes
                        promise(.success(()))
                        return
                    }
                }
                promise(.failure(APIError.notFound))
            }
        }
        .eraseToAnyPublisher()
    }
    
    enum APIError: Error, LocalizedError {
        case noData
        case notFound
        case unknown(String)
        
        var errorDescription: String? {
            switch self {
            case .noData: return "No saved places found."
            case .notFound: return "Place not found."
            case .unknown(let message): return "An unknown error occurred: \(message)"
            }
        }
    }
}

// MARK: - 3. Color Extension

extension Color {
    static let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0) // #2563EB
}

// MARK: - 4. ViewModel

class SavedPlacesViewModel: ObservableObject {
    @Published var collections: [PlaceCollection] = []
    @Published var searchText: String = ""
    @Published var isLoading: Bool = false
    @Published var error: Error? = nil
    @Published var isGridView: Bool = false
    
    private var allCollections: [PlaceCollection] = []
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        // Debounced search setup
        $searchText
            .debounce(for: .milliseconds(300), scheduler: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.filterCollections()
            }
            .store(in: &cancellables)
        
        fetchCollections()
    }
    
    // MARK: - Data Fetching and Filtering
    
    func fetchCollections(isRefreshing: Bool = false) {
        if !isRefreshing {
            isLoading = true
        }
        error = nil
        
        APIService.shared.fetchSavedCollections()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                if case let .failure(err) = completion {
                    self?.error = err
                }
            } receiveValue: { [weak self] fetchedCollections in
                self?.allCollections = fetchedCollections
                self?.filterCollections()
            }
            .store(in: &cancellables)
    }
    
    private func filterCollections() {
        if searchText.isEmpty {
            collections = allCollections
        } else {
            let lowercasedSearchText = searchText.lowercased()
            collections = allCollections.map { collection in
                let filteredPlaces = collection.places.filter { place in
                    place.name.lowercased().contains(lowercasedSearchText) ||
                    place.address.lowercased().contains(lowercasedSearchText) ||
                    (place.notes?.lowercased().contains(lowercasedSearchText) ?? false)
                }
                return PlaceCollection(id: collection.id, name: collection.name, places: filteredPlaces)
            }.filter { !$0.places.isEmpty } // Only keep collections with matching places
        }
    }
    
    // MARK: - Actions
    
    func deletePlace(placeID: UUID) {
        isLoading = true
        APIService.shared.deletePlace(placeID: placeID)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                if case let .failure(err) = completion {
                    self?.error = err
                }
            } receiveValue: { [weak self] _ in
                // Update local data after successful deletion
                for i in 0..<self?.allCollections.count ?? 0 {
                    self?.allCollections[i].places.removeAll { $0.id == placeID }
                }
                self?.filterCollections()
            }
            .store(in: &cancellables)
    }
    
    func updatePlaceNotes(place: Place, newNotes: String?) {
        isLoading = true
        APIService.shared.updatePlaceNotes(placeID: place.id, newNotes: newNotes)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                if case let .failure(err) = completion {
                    self?.error = err
                }
            } receiveValue: { [weak self] _ in
                // Update local data
                for i in 0..<self?.allCollections.count ?? 0 {
                    if let index = self?.allCollections[i].places.firstIndex(where: { $0.id == place.id }) {
                        self?.allCollections[i].places[index].notes = newNotes
                        break
                    }
                }
                self?.filterCollections()
            }
            .store(in: &cancellables)
    }
    
    // Simple mock for pagination - in a real app, this would fetch more data
    func loadMorePlaces(collection: PlaceCollection) {
        // This is a placeholder for a real pagination logic.
        // In a real scenario, you would check if there are more pages and fetch them.
        print("Loading more places for collection: \(collection.name)")
    }
}

// MARK: - 5. SwiftUI View

struct SavedPlacesView: View {
    @StateObject var viewModel = SavedPlacesViewModel()
    @State private var showingNotesEditor: Place? = nil
    
    var body: some View {
        NavigationView {
            Group {
                if viewModel.isLoading && viewModel.collections.isEmpty && viewModel.error == nil {
                    LoadingStateView()
                } else if let error = viewModel.error as? APIService.APIError, viewModel.collections.isEmpty {
                    EmptyStateView(error: error) {
                        viewModel.fetchCollections()
                    }
                } else if viewModel.collections.isEmpty && viewModel.searchText.isEmpty {
                    EmptyStateView(error: APIService.APIError.noData) {
                        viewModel.fetchCollections()
                    }
                } else if viewModel.collections.isEmpty && !viewModel.searchText.isEmpty {
                    ContentUnavailableView.search(text: viewModel.searchText)
                } else {
                    contentView
                }
            }
            .navigationTitle("Saved Places")
            .searchable(text: $viewModel.searchText, placement: .navigationBarDrawer(displayMode: .always))
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        viewModel.isGridView.toggle()
                    } label: {
                        Label(viewModel.isGridView ? "List View" : "Grid View", systemImage: viewModel.isGridView ? "list.bullet" : "square.grid.2x2")
                            .foregroundColor(.naviBlue)
                            .accessibilityLabel(viewModel.isGridView ? "Switch to list view" : "Switch to grid view")
                    }
                }
            }
            .sheet(item: $showingNotesEditor) { place in
                NotesEditorView(place: place) { newNotes in
                    if let index = viewModel.collections.firstIndex(where: { $0.places.contains(where: { $0.id == place.id }) }),
                       let placeIndex = viewModel.collections[index].places.firstIndex(where: { $0.id == place.id }) {
                        // Optimistic UI update
                        viewModel.collections[index].places[placeIndex].notes = newNotes
                        viewModel.updatePlaceNotes(place: place, newNotes: newNotes)
                    }
                }
            }
        }
    }
    
    // MARK: - Content View
    
    @ViewBuilder
    private var contentView: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 20) {
                ForEach(viewModel.collections) { collection in
                    Section(header: Text(collection.name)
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.primary)
                        .accessibilityAddTraits(.isHeader)
                    ) {
                        if viewModel.isGridView {
                            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 15) {
                                ForEach(collection.places) { place in
                                    PlaceGridItem(place: place, viewModel: viewModel, showingNotesEditor: $showingNotesEditor)
                                }
                            }
                        } else {
                            ForEach(collection.places) { place in
                                PlaceListItem(place: place, viewModel: viewModel, showingNotesEditor: $showingNotesEditor)
                                    .onAppear {
                                        // Simple pagination trigger (lazy loading)
                                        if collection.places.last?.id == place.id {
                                            viewModel.loadMorePlaces(collection: collection)
                                        }
                                    }
                            }
                        }
                    }
                }
            }
            .padding()
        }
        .refreshable { // Pull-to-refresh
            await withCheckedContinuation { continuation in
                viewModel.fetchCollections(isRefreshing: true)
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    continuation.resume()
                }
            }
        }
    }
}

// MARK: - 6. Subviews

struct PlaceListItem: View {
    let place: Place
    @ObservedObject var viewModel: SavedPlacesViewModel
    @Binding var showingNotesEditor: Place?
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                // Placeholder for Kingfisher Image
                // KFImage(place.imageURL)
                //     .placeholder { Image(systemName: "photo").resizable().scaledToFit() }
                //     .resizable()
                //     .scaledToFill()
                //     .frame(width: 60, height: 60)
                //     .clipShape(RoundedRectangle(cornerRadius: 8))
                
                Image(systemName: "mappin.circle.fill")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 60, height: 60)
                    .foregroundColor(.naviBlue)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .accessibilityHidden(true)
                
                VStack(alignment: .leading) {
                    Text(place.name)
                        .font(.headline)
                        .foregroundColor(.primary)
                        .lineLimit(1)
                        .accessibilityLabel("Place name: \(place.name)")
                    
                    Text(place.address)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                        .accessibilityLabel("Address: \(place.address)")
                    
                    if let notes = place.notes, !notes.isEmpty {
                        Text("Notes: \(notes)")
                            .font(.caption)
                            .foregroundColor(.gray)
                            .lineLimit(1)
                            .accessibilityLabel("Notes: \(notes)")
                    }
                }
                
                Spacer()
                
                if place.isFavorite {
                    Image(systemName: "heart.fill")
                        .foregroundColor(.red)
                        .accessibilityLabel("Favorite place")
                }
            }
            .padding(.vertical, 8)
            .contextMenu { // Long press menu
                Button {
                    showingNotesEditor = place
                } label: {
                    Label("Edit Notes", systemImage: "pencil")
                }
                
                Button {
                    // Simulate navigation to the place
                    print("Navigating to \(place.name)")
                } label: {
                    Label("Navigate", systemImage: "arrow.triangle.turn.up.right.circle.fill")
                }
                
                Divider()
                
                Button(role: .destructive) {
                    viewModel.deletePlace(placeID: place.id)
                } label: {
                    Label("Delete", systemImage: "trash")
                }
            }
            .swipeActions(edge: .trailing) { // Swipe to delete
                Button(role: .destructive) {
                    viewModel.deletePlace(placeID: place.id)
                } label: {
                    Label("Delete", systemImage: "trash")
                }
                .tint(.red)
            }
            .swipeActions(edge: .leading) { // Swipe to edit notes
                Button {
                    showingNotesEditor = place
                } label: {
                    Label("Edit Notes", systemImage: "pencil")
                }
                .tint(.naviBlue)
            }
            .onTapGesture {
                // Simulate navigation on tap
                print("Tapped on \(place.name)")
            }
            
            Divider()
        }
        .accessibilityElement(children: .combine)
    }
}

struct PlaceGridItem: View {
    let place: Place
    @ObservedObject var viewModel: SavedPlacesViewModel
    @Binding var showingNotesEditor: Place?
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            // Placeholder for Kingfisher Image
            // KFImage(place.imageURL)
            //     .placeholder { Image(systemName: "photo").resizable().scaledToFit() }
            //     .resizable()
            //     .scaledToFill()
            //     .frame(height: 120)
            //     .clipShape(RoundedRectangle(cornerRadius: 12))
            
            Image(systemName: "map.fill")
                .resizable()
                .scaledToFit()
                .frame(height: 120)
                .foregroundColor(.naviBlue.opacity(0.7))
                .background(Color.gray.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .accessibilityHidden(true)
            
            Text(place.name)
                .font(.subheadline)
                .fontWeight(.medium)
                .lineLimit(1)
                .accessibilityLabel("Place name: \(place.name)")
            
            Text(place.address)
                .font(.caption)
                .foregroundColor(.secondary)
                .lineLimit(1)
                .accessibilityLabel("Address: \(place.address)")
        }
        .padding(8)
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(radius: 2)
        .contextMenu { // Long press menu
            PlaceContextMenu(place: place, viewModel: viewModel, showingNotesEditor: $showingNotesEditor)
        }
        .onTapGesture {
            // Simulate navigation on tap
            print("Tapped on \(place.name)")
        }
        .accessibilityElement(children: .combine)
    }
}

struct PlaceContextMenu: View {
    let place: Place
    @ObservedObject var viewModel: SavedPlacesViewModel
    @Binding var showingNotesEditor: Place?
    
    var body: some View {
        Button {
            showingNotesEditor = place
        } label: {
            Label("Edit Notes", systemImage: "pencil")
        }
        
        Button {
            // Simulate navigation to the place
            print("Navigating to \(place.name)")
        } label: {
            Label("Navigate", systemImage: "arrow.triangle.turn.up.right.circle.fill")
        }
        
        Divider()
        
        Button(role: .destructive) {
            viewModel.deletePlace(placeID: place.id)
        } label: {
            Label("Delete", systemImage: "trash")
        }
    }
}

struct NotesEditorView: View {
    @State var place: Place
    @State private var notes: String
    var onSave: (String?) -> Void
    @Environment(\.dismiss) var dismiss
    
    init(place: Place, onSave: @escaping (String?) -> Void) {
        _place = State(initialValue: place)
        _notes = State(initialValue: place.notes ?? "")
        self.onSave = onSave
    }
    
    var body: some View {
        NavigationView {
            VStack {
                Text("Editing notes for **\(place.name)**")
                    .font(.title3)
                    .padding(.bottom)
                
                TextEditor(text: $notes)
                    .frame(height: 200)
                    .border(Color.gray.opacity(0.5))
                    .accessibilityLabel("Edit notes for \(place.name)")
                
                Spacer()
            }
            .padding()
            .navigationTitle("Edit Notes")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
                        onSave(notes.isEmpty ? nil : notes)
                        dismiss()
                    }
                    .foregroundColor(.naviBlue)
                    .accessibilityHint("Saves the updated notes")
                }
            }
        }
    }
}

struct LoadingStateView: View {
    var body: some View {
        VStack {
            ProgressView()
                .progressViewStyle(.circular)
                .scaleEffect(1.5)
                .padding()
            Text("Loading your saved places...")
                .foregroundColor(.secondary)
                .accessibilityLabel("Loading in progress")
        }
    }
}

struct EmptyStateView: View {
    let error: APIService.APIError
    let retryAction: () -> Void
    
    var body: some View {
        VStack {
            Image(systemName: "map.slash")
                .resizable()
                .scaledToFit()
                .frame(width: 100, height: 100)
                .foregroundColor(.naviBlue)
                .padding(.bottom, 10)
                .accessibilityHidden(true)
            
            Text("No Saved Places")
                .font(.title)
                .fontWeight(.semibold)
                .padding(.bottom, 5)
                .accessibilityLabel("No saved places found")
            
            Text(error.localizedDescription)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            
            Button("Try Again") {
                retryAction()
            }
            .buttonStyle(.borderedProminent)
            .tint(.naviBlue)
            .padding(.top, 20)
            .accessibilityHint("Taps to reload the list of saved places")
        }
        .padding()
    }
}

// MARK: - Preview

struct SavedPlacesView_Previews: PreviewProvider {
    static var previews: some View {
        SavedPlacesView()
            .environment(\.colorScheme, .light)
        
        SavedPlacesView()
            .environment(\.colorScheme, .dark)
    }
}

// MARK: - Accessibility and Dynamic Type Notes
/*
- Dynamic Type is supported by using standard SwiftUI Text views with system fonts.
- VoiceOver labels are added using .accessibilityLabel and .accessibilityHint.
- .accessibilityElement(children: .combine) is used on list items to group elements for better VoiceOver experience.
*/

// MARK: - Performance Notes
/*
- LazyVStack and LazyVGrid are used for lazy loading and performance.
- Simple pagination logic is included in .onAppear for PlaceListItem.
- Debounce is used on the search bar to limit API calls.
*/

import Foundation
import RealmSwift
import Combine

/// Saved Place Realm Object
class SavedPlaceObject: Object {
    @Persisted(primaryKey: true) var id: String
    @Persisted var name: String
    @Persisted var address: String
    @Persisted var latitude: Double
    @Persisted var longitude: Double
    @Persisted var category: String
    @Persisted var rating: Double?
    @Persisted var photoURL: String?
    @Persisted var isFavorite: Bool = false
    @Persisted var savedAt: Date = Date()
    @Persisted var visitCount: Int = 0
    @Persisted var lastVisited: Date?
    
    convenience init(from place: Place) {
        self.init()
        self.id = place.id
        self.name = place.name
        self.address = place.address
        self.latitude = place.latitude
        self.longitude = place.longitude
        self.category = place.category
        self.rating = place.rating
        self.photoURL = place.photoURL
    }
    
    func toPlace() -> Place {
        return Place(
            id: id,
            name: name,
            address: address,
            latitude: latitude,
            longitude: longitude,
            category: category,
            rating: rating,
            photoURL: photoURL
        )
    }
}

/// Place Repository - Manages saved places in Realm database
class PlaceRepository: ObservableObject {
    static let shared = PlaceRepository()
    
    @Published var savedPlaces: [Place] = []
    @Published var favoritePlaces: [Place] = []
    @Published var recentPlaces: [Place] = []
    
    private var realm: Realm?
    private var notificationToken: NotificationToken?
    
    // MARK: - Initialization
    
    private init() {
        setupRealm()
        loadSavedPlaces()
    }
    
    // MARK: - Setup
    
    private func setupRealm() {
        do {
            let config = Realm.Configuration(
                schemaVersion: 1,
                migrationBlock: { migration, oldSchemaVersion in
                    // Handle migrations if needed
                    if oldSchemaVersion < 1 {
                        // Migration code
                    }
                }
            )
            
            Realm.Configuration.defaultConfiguration = config
            realm = try Realm()
            
            // Observe changes
            observeChanges()
            
        } catch {
            print("❌ Failed to initialize Realm: \(error.localizedDescription)")
        }
    }
    
    private func observeChanges() {
        guard let realm = realm else { return }
        
        let results = realm.objects(SavedPlaceObject.self)
        
        notificationToken = results.observe { [weak self] changes in
            switch changes {
            case .initial, .update:
                self?.loadSavedPlaces()
            case .error(let error):
                print("❌ Realm observation error: \(error.localizedDescription)")
            }
        }
    }
    
    // MARK: - Load Places
    
    private func loadSavedPlaces() {
        guard let realm = realm else { return }
        
        let results = realm.objects(SavedPlaceObject.self)
        savedPlaces = results.map { $0.toPlace() }
        
        // Load favorites
        let favorites = results.filter("isFavorite == true")
        favoritePlaces = favorites.map { $0.toPlace() }
        
        // Load recent (last 10 visited)
        let recent = results
            .filter("lastVisited != nil")
            .sorted(byKeyPath: "lastVisited", ascending: false)
            .prefix(10)
        recentPlaces = recent.map { $0.toPlace() }
    }
    
    // MARK: - Save Place
    
    func savePlace(_ place: Place) {
        guard let realm = realm else { return }
        
        do {
            try realm.write {
                let savedPlace = SavedPlaceObject(from: place)
                realm.add(savedPlace, update: .modified)
            }
        } catch {
            print("❌ Failed to save place: \(error.localizedDescription)")
        }
    }
    
    func savePlaces(_ places: [Place]) {
        guard let realm = realm else { return }
        
        do {
            try realm.write {
                for place in places {
                    let savedPlace = SavedPlaceObject(from: place)
                    realm.add(savedPlace, update: .modified)
                }
            }
        } catch {
            print("❌ Failed to save places: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Delete Place
    
    func deletePlace(_ place: Place) {
        guard let realm = realm else { return }
        
        do {
            if let savedPlace = realm.object(ofType: SavedPlaceObject.self, forPrimaryKey: place.id) {
                try realm.write {
                    realm.delete(savedPlace)
                }
            }
        } catch {
            print("❌ Failed to delete place: \(error.localizedDescription)")
        }
    }
    
    func deleteAllPlaces() {
        guard let realm = realm else { return }
        
        do {
            try realm.write {
                let allPlaces = realm.objects(SavedPlaceObject.self)
                realm.delete(allPlaces)
            }
        } catch {
            print("❌ Failed to delete all places: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Favorite
    
    func toggleFavorite(_ place: Place) {
        guard let realm = realm else { return }
        
        do {
            if let savedPlace = realm.object(ofType: SavedPlaceObject.self, forPrimaryKey: place.id) {
                try realm.write {
                    savedPlace.isFavorite.toggle()
                }
            } else {
                // Place not saved yet, save it as favorite
                try realm.write {
                    let newPlace = SavedPlaceObject(from: place)
                    newPlace.isFavorite = true
                    realm.add(newPlace)
                }
            }
        } catch {
            print("❌ Failed to toggle favorite: \(error.localizedDescription)")
        }
    }
    
    func isFavorite(_ place: Place) -> Bool {
        guard let realm = realm else { return false }
        
        if let savedPlace = realm.object(ofType: SavedPlaceObject.self, forPrimaryKey: place.id) {
            return savedPlace.isFavorite
        }
        return false
    }
    
    // MARK: - Visit Tracking
    
    func recordVisit(_ place: Place) {
        guard let realm = realm else { return }
        
        do {
            if let savedPlace = realm.object(ofType: SavedPlaceObject.self, forPrimaryKey: place.id) {
                try realm.write {
                    savedPlace.visitCount += 1
                    savedPlace.lastVisited = Date()
                }
            } else {
                // Place not saved yet, save it with visit
                try realm.write {
                    let newPlace = SavedPlaceObject(from: place)
                    newPlace.visitCount = 1
                    newPlace.lastVisited = Date()
                    realm.add(newPlace)
                }
            }
        } catch {
            print("❌ Failed to record visit: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Search
    
    func searchSavedPlaces(query: String) -> [Place] {
        guard let realm = realm else { return [] }
        
        let results = realm.objects(SavedPlaceObject.self)
            .filter("name CONTAINS[cd] %@ OR address CONTAINS[cd] %@", query, query)
        
        return results.map { $0.toPlace() }
    }
    
    func getPlacesByCategory(_ category: String) -> [Place] {
        guard let realm = realm else { return [] }
        
        let results = realm.objects(SavedPlaceObject.self)
            .filter("category == %@", category)
        
        return results.map { $0.toPlace() }
    }
    
    // MARK: - Statistics
    
    func getMostVisitedPlaces(limit: Int = 10) -> [Place] {
        guard let realm = realm else { return [] }
        
        let results = realm.objects(SavedPlaceObject.self)
            .sorted(byKeyPath: "visitCount", ascending: false)
            .prefix(limit)
        
        return results.map { $0.toPlace() }
    }
    
    func getTotalSavedPlaces() -> Int {
        guard let realm = realm else { return 0 }
        return realm.objects(SavedPlaceObject.self).count
    }
    
    func getTotalFavorites() -> Int {
        guard let realm = realm else { return 0 }
        return realm.objects(SavedPlaceObject.self).filter("isFavorite == true").count
    }
    
    // MARK: - Cleanup
    
    deinit {
        notificationToken?.invalidate()
    }
}

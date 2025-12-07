import Foundation
import CoreLocation
import Combine

/// Search ViewModel - Manages place search and results
class SearchViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var searchQuery = ""
    @Published var searchResults: [Place] = []
    @Published var recentSearches: [String] = []
    @Published var isSearching = false
    @Published var error: String?
    
    // Categories
    @Published var selectedCategory: PlaceCategory?
    
    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()
    private let locationManager = LocationManager.shared
    
    // Mapbox Geocoding API
    private let accessToken = "YOUR_MAPBOX_ACCESS_TOKEN"
    
    // MARK: - Initialization
    init() {
        setupSearchDebounce()
        loadRecentSearches()
    }
    
    // MARK: - Setup
    
    private func setupSearchDebounce() {
        // Debounce search to avoid excessive API calls
        $searchQuery
            .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
            .removeDuplicates()
            .sink { [weak self] query in
                if !query.isEmpty {
                    self?.performSearch(query: query)
                } else {
                    self?.searchResults = []
                }
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Search
    
    func performSearch(query: String) {
        guard !query.isEmpty else {
            searchResults = []
            return
        }
        
        isSearching = true
        error = nil
        
        // Get user location for proximity bias
        let userLocation = locationManager.userLocation
        let proximity = userLocation != nil ?
            "\(userLocation!.longitude),\(userLocation!.latitude)" : ""
        
        // Build Mapbox Geocoding API URL
        let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        var urlString = "https://api.mapbox.com/geocoding/v5/mapbox.places/\(encodedQuery).json?access_token=\(accessToken)"
        
        if !proximity.isEmpty {
            urlString += "&proximity=\(proximity)"
        }
        
        urlString += "&limit=10"
        
        guard let url = URL(string: urlString) else {
            isSearching = false
            error = "Invalid search URL"
            return
        }
        
        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            DispatchQueue.main.async {
                self?.isSearching = false
                
                if let error = error {
                    self?.error = error.localizedDescription
                    return
                }
                
                guard let data = data else {
                    self?.error = "No data received"
                    return
                }
                
                do {
                    let decoder = JSONDecoder()
                    let geocodingResponse = try decoder.decode(GeocodingResponse.self, from: data)
                    
                    // Convert features to Place objects
                    self?.searchResults = geocodingResponse.features.map { feature in
                        Place(
                            id: UUID().uuidString,
                            name: feature.text,
                            address: feature.placeName,
                            latitude: feature.center[1],
                            longitude: feature.center[0],
                            category: self?.inferCategory(from: feature.placeType) ?? "place",
                            rating: nil,
                            photoURL: nil
                        )
                    }
                    
                    // Save to recent searches
                    self?.addToRecentSearches(query)
                    
                } catch {
                    self?.error = "Failed to parse search results"
                }
            }
        }.resume()
    }
    
    func searchByCategory(_ category: PlaceCategory) {
        selectedCategory = category
        performSearch(query: category.searchQuery)
    }
    
    func clearSearch() {
        searchQuery = ""
        searchResults = []
        selectedCategory = nil
    }
    
    // MARK: - Recent Searches
    
    private func loadRecentSearches() {
        recentSearches = UserDefaults.standard.stringArray(forKey: "recentSearches") ?? []
    }
    
    private func addToRecentSearches(_ query: String) {
        var searches = recentSearches
        
        // Remove if already exists
        searches.removeAll { $0 == query }
        
        // Add to beginning
        searches.insert(query, at: 0)
        
        // Keep only last 10
        if searches.count > 10 {
            searches = Array(searches.prefix(10))
        }
        
        recentSearches = searches
        UserDefaults.standard.set(searches, forKey: "recentSearches")
    }
    
    func clearRecentSearches() {
        recentSearches = []
        UserDefaults.standard.removeObject(forKey: "recentSearches")
    }
    
    // MARK: - Helper Methods
    
    private func inferCategory(from placeType: [String]?) -> String {
        guard let types = placeType else { return "place" }
        
        if types.contains("poi") { return "poi" }
        if types.contains("address") { return "address" }
        if types.contains("place") { return "city" }
        if types.contains("region") { return "region" }
        if types.contains("country") { return "country" }
        
        return "place"
    }
}

// MARK: - Geocoding Response Models

struct GeocodingResponse: Codable {
    let type: String
    let query: [String]
    let features: [GeocodingFeature]
}

struct GeocodingFeature: Codable {
    let id: String
    let type: String
    let placeType: [String]?
    let text: String
    let placeName: String
    let center: [Double]
    
    enum CodingKeys: String, CodingKey {
        case id, type, text, center
        case placeType = "place_type"
        case placeName = "place_name"
    }
}

// MARK: - Place Category

enum PlaceCategory: String, CaseIterable {
    case restaurants = "Restaurants"
    case gasStations = "Gas Stations"
    case parking = "Parking"
    case hotels = "Hotels"
    case cafes = "Cafes"
    case atms = "ATMs"
    case pharmacies = "Pharmacies"
    case hospitals = "Hospitals"
    
    var searchQuery: String {
        switch self {
        case .restaurants: return "restaurant"
        case .gasStations: return "gas station"
        case .parking: return "parking"
        case .hotels: return "hotel"
        case .cafes: return "cafe"
        case .atms: return "atm"
        case .pharmacies: return "pharmacy"
        case .hospitals: return "hospital"
        }
    }
    
    var icon: String {
        switch self {
        case .restaurants: return "fork.knife"
        case .gasStations: return "fuelpump"
        case .parking: return "parkingsign"
        case .hotels: return "bed.double"
        case .cafes: return "cup.and.saucer"
        case .atms: return "dollarsign.circle"
        case .pharmacies: return "cross.case"
        case .hospitals: return "cross.fill"
        }
    }
}

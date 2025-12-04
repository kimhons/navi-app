import Foundation
import CoreLocation

struct Place: Codable, Identifiable {
    let id: String
    let name: String
    let address: String
    let coordinate: Coordinate
    let category: String
    let rating: Double?
    let reviewCount: Int?
    let phoneNumber: String?
    let website: String?
    let hours: [String]?
    let photos: [String]?
    let priceLevel: Int?
    let isOpen: Bool?
    
    var location: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }
}

struct Coordinate: Codable {
    let latitude: Double
    let longitude: Double
}

struct PlaceDetail: Codable {
    let place: Place
    let description: String?
    let reviews: [Review]
    let amenities: [String]
    let accessibility: [String]
    let popularTimes: [PopularTime]?
}

struct Review: Codable, Identifiable {
    let id: String
    let authorName: String
    let authorPhoto: String?
    let rating: Double
    let text: String
    let time: Date
    let helpful: Int
}

struct PopularTime: Codable {
    let day: String
    let hours: [Int]
}

struct SavedPlace: Codable, Identifiable {
    let id: String
    let place: Place
    let userId: String
    let collectionId: String?
    let notes: String?
    let savedAt: Date
}

struct PlaceCollection: Codable, Identifiable {
    let id: String
    let name: String
    let description: String?
    let userId: String
    let places: [Place]
    let isPublic: Bool
    let createdAt: Date
}

import Foundation
import Alamofire
import Combine

class APIService {
    static let shared = APIService()
    
    private let baseURL = "https://navi-backend.onrender.com/api"
    private var authToken: String?
    
    private init() {}
    
    // MARK: - Authentication
    
    func register(email: String, password: String, name: String) -> AnyPublisher<User, Error> {
        let parameters: [String: Any] = [
            "email": email,
            "password": password,
            "name": name
        ]
        
        return request(endpoint: "/auth/register", method: .post, parameters: parameters)
    }
    
    func login(email: String, password: String) -> AnyPublisher<AuthResponse, Error> {
        let parameters: [String: Any] = [
            "email": email,
            "password": password
        ]
        
        return request(endpoint: "/auth/login", method: .post, parameters: parameters)
            .handleEvents(receiveOutput: { [weak self] response in
                self?.authToken = response.token
            })
            .eraseToAnyPublisher()
    }
    
    func logout() -> AnyPublisher<Void, Error> {
        return request(endpoint: "/auth/logout", method: .post)
    }
    
    // MARK: - User
    
    func getProfile() -> AnyPublisher<User, Error> {
        return request(endpoint: "/user/profile", method: .get)
    }
    
    func updateProfile(name: String?, phone: String?, preferences: UserPreferences?) -> AnyPublisher<User, Error> {
        var parameters: [String: Any] = [:]
        if let name = name { parameters["name"] = name }
        if let phone = phone { parameters["phone"] = phone }
        if let preferences = preferences {
            parameters["preferences"] = try? JSONEncoder().encode(preferences)
        }
        
        return request(endpoint: "/user/profile", method: .put, parameters: parameters)
    }
    
    // MARK: - Places
    
    func searchPlaces(query: String, location: Coordinate?, radius: Double?) -> AnyPublisher<[Place], Error> {
        var parameters: [String: Any] = ["query": query]
        if let location = location {
            parameters["latitude"] = location.latitude
            parameters["longitude"] = location.longitude
        }
        if let radius = radius {
            parameters["radius"] = radius
        }
        
        return request(endpoint: "/places/search", method: .get, parameters: parameters)
    }
    
    func getPlaceDetail(placeId: String) -> AnyPublisher<PlaceDetail, Error> {
        return request(endpoint: "/places/\(placeId)", method: .get)
    }
    
    func getNearbyPlaces(location: Coordinate, category: String?, radius: Double) -> AnyPublisher<[Place], Error> {
        var parameters: [String: Any] = [
            "latitude": location.latitude,
            "longitude": location.longitude,
            "radius": radius
        ]
        if let category = category {
            parameters["category"] = category
        }
        
        return request(endpoint: "/places/nearby", method: .get, parameters: parameters)
    }
    
    // MARK: - Routes
    
    func calculateRoute(origin: Coordinate, destination: Coordinate, waypoints: [Coordinate]?, options: RouteOptions?) -> AnyPublisher<Route, Error> {
        var parameters: [String: Any] = [
            "origin": "\(origin.latitude),\(origin.longitude)",
            "destination": "\(destination.latitude),\(destination.longitude)"
        ]
        
        if let waypoints = waypoints, !waypoints.isEmpty {
            parameters["waypoints"] = waypoints.map { "\($0.latitude),\($0.longitude)" }.joined(separator: "|")
        }
        
        if let options = options {
            if options.avoidTolls { parameters["avoid_tolls"] = true }
            if options.avoidHighways { parameters["avoid_highways"] = true }
            if options.avoidFerries { parameters["avoid_ferries"] = true }
        }
        
        return request(endpoint: "/routes/calculate", method: .post, parameters: parameters)
    }
    
    func getTrafficIncidents(bounds: CoordinateBounds) -> AnyPublisher<[TrafficIncident], Error> {
        let parameters: [String: Any] = [
            "north": bounds.north,
            "south": bounds.south,
            "east": bounds.east,
            "west": bounds.west
        ]
        
        return request(endpoint: "/traffic/incidents", method: .get, parameters: parameters)
    }
    
    // MARK: - Saved Places
    
    func getSavedPlaces() -> AnyPublisher<[SavedPlace], Error> {
        return request(endpoint: "/user/saved-places", method: .get)
    }
    
    func savePlace(placeId: String, collectionId: String?, notes: String?) -> AnyPublisher<SavedPlace, Error> {
        var parameters: [String: Any] = ["placeId": placeId]
        if let collectionId = collectionId { parameters["collectionId"] = collectionId }
        if let notes = notes { parameters["notes"] = notes }
        
        return request(endpoint: "/user/saved-places", method: .post, parameters: parameters)
    }
    
    func deleteSavedPlace(id: String) -> AnyPublisher<Void, Error> {
        return request(endpoint: "/user/saved-places/\(id)", method: .delete)
    }
    
    // MARK: - Generic Request
    
    private func request<T: Decodable>(
        endpoint: String,
        method: HTTPMethod,
        parameters: [String: Any]? = nil
    ) -> AnyPublisher<T, Error> {
        let url = baseURL + endpoint
        
        var headers: HTTPHeaders = [
            "Content-Type": "application/json"
        ]
        
        if let token = authToken {
            headers["Authorization"] = "Bearer \(token)"
        }
        
        return Future<T, Error> { promise in
            AF.request(
                url,
                method: method,
                parameters: parameters,
                encoding: JSONEncoding.default,
                headers: headers
            )
            .validate()
            .responseDecodable(of: T.self) { response in
                switch response.result {
                case .success(let value):
                    promise(.success(value))
                case .failure(let error):
                    promise(.failure(error))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - Supporting Types

struct AuthResponse: Codable {
    let token: String
    let user: User
}

struct RouteOptions {
    var avoidTolls: Bool = false
    var avoidHighways: Bool = false
    var avoidFerries: Bool = false
}

struct CoordinateBounds {
    let north: Double
    let south: Double
    let east: Double
    let west: Double
}

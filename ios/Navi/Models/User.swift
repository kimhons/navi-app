import Foundation

struct User: Codable, Identifiable {
    let id: String
    var name: String
    var email: String
    var phone: String?
    var profileImageUrl: String?
    var isPremium: Bool
    var subscriptionTier: SubscriptionTier
    var createdAt: Date
    var preferences: UserPreferences
    
    enum SubscriptionTier: String, Codable {
        case free = "free"
        case premium = "premium"
        case pro = "pro"
    }
}

struct UserPreferences: Codable {
    var language: String = "en"
    var voiceGuidance: Bool = true
    var voiceLanguage: String = "en-US"
    var units: UnitSystem = .metric
    var theme: AppTheme = .system
    var mapStyle: MapStyle = .standard
    var avoidTolls: Bool = false
    var avoidHighways: Bool = false
    var avoidFerries: Bool = false
    var showTraffic: Bool = true
    var showSpeedLimits: Bool = true
    var speedWarningEnabled: Bool = true
    var offlineMapsEnabled: Bool = true
    
    enum UnitSystem: String, Codable {
        case metric
        case imperial
    }
    
    enum AppTheme: String, Codable {
        case light
        case dark
        case system
    }
    
    enum MapStyle: String, Codable {
        case standard
        case satellite
        case hybrid
        case terrain
    }
}

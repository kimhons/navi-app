import Foundation
import Intents
import IntentsUI

/// Siri Shortcuts Manager - Handles Siri integration and app shortcuts
class SiriShortcutsManager {
    static let shared = SiriShortcutsManager()
    
    private init() {}
    
    // MARK: - Donate Shortcuts
    
    /// Donate "Navigate Home" shortcut
    func donateNavigateHomeShortcut() {
        let intent = NavigateIntent()
        intent.destination = "Home"
        intent.suggestedInvocationPhrase = "Navigate home"
        
        let interaction = INInteraction(intent: intent, response: nil)
        interaction.donate { error in
            if let error = error {
                print("❌ Failed to donate Navigate Home shortcut: \(error.localizedDescription)")
            } else {
                print("✅ Navigate Home shortcut donated successfully")
            }
        }
    }
    
    /// Donate "Navigate to Work" shortcut
    func donateNavigateWorkShortcut() {
        let intent = NavigateIntent()
        intent.destination = "Work"
        intent.suggestedInvocationPhrase = "Navigate to work"
        
        let interaction = INInteraction(intent: intent, response: nil)
        interaction.donate { error in
            if let error = error {
                print("❌ Failed to donate Navigate Work shortcut: \(error.localizedDescription)")
            } else {
                print("✅ Navigate Work shortcut donated successfully")
            }
        }
    }
    
    /// Donate "Find Gas Stations" shortcut
    func donateFindGasStationsShortcut() {
        let intent = SearchNearbyIntent()
        intent.category = "Gas Stations"
        intent.suggestedInvocationPhrase = "Find gas stations"
        
        let interaction = INInteraction(intent: intent, response: nil)
        interaction.donate { error in
            if let error = error {
                print("❌ Failed to donate Find Gas Stations shortcut: \(error.localizedDescription)")
            } else {
                print("✅ Find Gas Stations shortcut donated successfully")
            }
        }
    }
    
    /// Donate "Share My Location" shortcut
    func donateShareLocationShortcut() {
        let intent = ShareLocationIntent()
        intent.suggestedInvocationPhrase = "Share my location with Navi"
        
        let interaction = INInteraction(intent: intent, response: nil)
        interaction.donate { error in
            if let error = error {
                print("❌ Failed to donate Share Location shortcut: \(error.localizedDescription)")
            } else {
                print("✅ Share Location shortcut donated successfully")
            }
        }
    }
    
    /// Donate custom navigation shortcut
    func donateNavigationShortcut(destination: String) {
        let intent = NavigateIntent()
        intent.destination = destination
        intent.suggestedInvocationPhrase = "Navigate to \(destination)"
        
        let interaction = INInteraction(intent: intent, response: nil)
        interaction.donate { error in
            if let error = error {
                print("❌ Failed to donate \(destination) shortcut: \(error.localizedDescription)")
            } else {
                print("✅ \(destination) shortcut donated successfully")
            }
        }
    }
    
    // MARK: - App Shortcuts (iOS 16+)
    
    @available(iOS 16.0, *)
    func updateAppShortcuts() {
        // Define app shortcuts that appear in Spotlight and Siri Suggestions
        let shortcuts = [
            AppShortcut(
                intent: NavigateIntent(),
                phrases: [
                    "Navigate with \(.applicationName)",
                    "Start navigation in \(.applicationName)",
                    "Get directions with \(.applicationName)"
                ],
                shortTitle: "Navigate",
                systemImageName: "location.fill"
            ),
            AppShortcut(
                intent: SearchNearbyIntent(),
                phrases: [
                    "Find nearby places with \(.applicationName)",
                    "Search nearby in \(.applicationName)",
                    "What's nearby in \(.applicationName)"
                ],
                shortTitle: "Find Nearby",
                systemImageName: "magnifyingglass"
            ),
            AppShortcut(
                intent: ShareLocationIntent(),
                phrases: [
                    "Share my location with \(.applicationName)",
                    "Send my location in \(.applicationName)"
                ],
                shortTitle: "Share Location",
                systemImageName: "location.circle.fill"
            )
        ]
        
        AppShortcuts.updateAppShortcutParameters()
    }
}

// MARK: - Custom Intents

/// Navigate Intent - For Siri "Navigate to..." commands
class NavigateIntent: NSObject, INIntent {
    @NSManaged var destination: String?
}

/// Search Nearby Intent - For Siri "Find nearby..." commands
class SearchNearbyIntent: NSObject, INIntent {
    @NSManaged var category: String?
}

/// Share Location Intent - For Siri "Share my location" commands
class ShareLocationIntent: NSObject, INIntent {
    // No parameters needed
}

// MARK: - Intent Handling

/// Intent Handler - Processes Siri intents
class IntentHandler: INExtension {
    override func handler(for intent: INIntent) -> Any {
        if intent is NavigateIntent {
            return NavigateIntentHandler()
        } else if intent is SearchNearbyIntent {
            return SearchNearbyIntentHandler()
        } else if intent is ShareLocationIntent {
            return ShareLocationIntentHandler()
        }
        return self
    }
}

// MARK: - Navigate Intent Handler
class NavigateIntentHandler: NSObject, INIntentHandler {
    func handle(intent: NavigateIntent, completion: @escaping (INIntentResponse) -> Void) {
        guard let destination = intent.destination else {
            completion(INIntentResponse())
            return
        }
        
        // Post notification to app to start navigation
        NotificationCenter.default.post(
            name: .siriNavigateCommand,
            object: nil,
            userInfo: ["destination": destination]
        )
        
        let response = INIntentResponse()
        response.userActivity = NSUserActivity(activityType: "com.navi.navigate")
        response.userActivity?.userInfo = ["destination": destination]
        
        completion(response)
    }
}

// MARK: - Search Nearby Intent Handler
class SearchNearbyIntentHandler: NSObject, INIntentHandler {
    func handle(intent: SearchNearbyIntent, completion: @escaping (INIntentResponse) -> Void) {
        let category = intent.category ?? "places"
        
        // Post notification to app to search nearby
        NotificationCenter.default.post(
            name: .siriSearchNearbyCommand,
            object: nil,
            userInfo: ["category": category]
        )
        
        let response = INIntentResponse()
        response.userActivity = NSUserActivity(activityType: "com.navi.searchNearby")
        response.userActivity?.userInfo = ["category": category]
        
        completion(response)
    }
}

// MARK: - Share Location Intent Handler
class ShareLocationIntentHandler: NSObject, INIntentHandler {
    func handle(intent: ShareLocationIntent, completion: @escaping (INIntentResponse) -> Void) {
        // Post notification to app to share location
        NotificationCenter.default.post(
            name: .siriShareLocationCommand,
            object: nil
        )
        
        let response = INIntentResponse()
        response.userActivity = NSUserActivity(activityType: "com.navi.shareLocation")
        
        completion(response)
    }
}

// MARK: - Notification Names for Siri Commands
extension Notification.Name {
    static let siriNavigateCommand = Notification.Name("siriNavigateCommand")
    static let siriSearchNearbyCommand = Notification.Name("siriSearchNearbyCommand")
    static let siriShareLocationCommand = Notification.Name("siriShareLocationCommand")
}

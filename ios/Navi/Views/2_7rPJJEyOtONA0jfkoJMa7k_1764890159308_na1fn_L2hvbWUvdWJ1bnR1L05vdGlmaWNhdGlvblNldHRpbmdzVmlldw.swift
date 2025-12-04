import SwiftUI
import Combine

// MARK: - Mock APIService
/// A mock service to simulate asynchronous API calls for account, subscription, and backup operations.
class APIService {
    static let shared = APIService()
    private init() {}

    enum APIError: Error, LocalizedError {
        case networkError
        case serverError
        case invalidResponse
        
        var errorDescription: String? {
            switch self {
            case .networkError: return "Could not connect to the network."
            case .serverError: return "The server encountered an error. Please try again."
            case .invalidResponse: return "Received an invalid response from the server."
            }
        }
    }

    // Mock API call with simulated latency
    private func mockCall<T>(result: Result<T, APIError>, delay: TimeInterval = 1.0) async throws -> T {
        try await Task.sleep(for: .seconds(delay))
        switch result {
        case .success(let value):
            return value
        case .failure(let error):
            throw error
        }
    }

    /// Simulates updating account settings.
    func updateAccountSettings(settings: [String: Any]) async throws -> Bool {
        print("Mock API: Updating account settings with \(settings)")
        // Simulate success 90% of the time
        let success = Bool.random()
        let result: Result<Bool, APIError> = success ? .success(true) : .failure(.serverError)
        return try await mockCall(result: result)
    }

    /// Simulates checking subscription status.
    func checkSubscriptionStatus() async throws -> String {
        print("Mock API: Checking subscription status")
        let statuses = ["Active", "Expired", "Trial"]
        let status = statuses.randomElement()!
        return try await mockCall(result: .success(status))
    }

    /// Simulates performing a backup sync to iCloud.
    func performBackupSync() async throws -> Date {
        print("Mock API: Performing backup sync to iCloud")
        return try await mockCall(result: .success(Date()))
    }
}

// MARK: - NotificationSettingsViewModel
class NotificationSettingsViewModel: ObservableObject {
    // MARK: - Persistence Properties
    // Using @AppStorage for settings persistence (UserDefaults)
    @AppStorage("notifications_navigation_alerts") var navigationAlerts: Bool = true
    @AppStorage("notifications_traffic_updates") var trafficUpdates: Bool = false
    @AppStorage("notifications_friend_requests") var friendRequests: Bool = true
    @AppStorage("notifications_group_messages") var groupMessages: Bool = true
    @AppStorage("notifications_push_toggle") var pushNotificationsEnabled: Bool = true
    @AppStorage("notifications_sound_preference") var soundPreference: String = "Default"
    @AppStorage("notifications_vibration_level") var vibrationLevel: Double = 0.5
    @AppStorage("notifications_quiet_hours_start") var quietHoursStart: Date = Calendar.current.date(bySettingHour: 22, minute: 0, second: 0, of: Date())!
    
    // MARK: - State Properties
    @Published var isLoading: Bool = false
    @Published var subscriptionStatus: String = "Checking..."
    @Published var lastBackupDate: Date? = nil
    @Published var apiError: APIService.APIError? = nil
    @Published var showSuccessFeedback: Bool = false
    @Published var showConfirmation: Bool = false
    @Published var debouncedVibrationLevel: Double = 0.5 // For debouncing the slider
    
    private var cancellables = Set<AnyCancellable>()
    private let debouncer = PassthroughSubject<Double, Never>()
    
    init() {
        // Initialize debounced value with current persisted value
        debouncedVibrationLevel = vibrationLevel
        
        // Setup debouncing for the vibration level slider
        debouncer
            .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
            .sink { [weak self] value in
                self?.vibrationLevel = value // Persist the debounced value
                self?.syncSettingsToAPI() // Trigger API sync after debouncing
            }
            .store(in: &cancellables)
        
        // Initial load of subscription status
        Task { await checkSubscription() }
    }
    
    // MARK: - Actions
    
    /// Called when any simple toggle setting changes.
    func settingChanged() {
        // Immediate persistence via @AppStorage
        // Debounced API call for performance
        syncSettingsToAPI()
    }
    
    /// Called when the vibration slider value changes.
    func vibrationLevelChanged(newValue: Double) {
        debouncedVibrationLevel = newValue
        debouncer.send(newValue)
    }
    
    /// Syncs all current settings to the mock API service.
    func syncSettingsToAPI() {
        guard !isLoading else { return }
        
        let settings: [String: Any] = [
            "navigationAlerts": navigationAlerts,
            "trafficUpdates": trafficUpdates,
            "friendRequests": friendRequests,
            "groupMessages": groupMessages,
            "pushNotificationsEnabled": pushNotificationsEnabled,
            "soundPreference": soundPreference,
            "vibrationLevel": vibrationLevel,
            "quietHoursStart": quietHoursStart.description
        ]
        
        Task {
            await MainActor.run {
                self.isLoading = true
                self.apiError = nil
            }
            
            do {
                _ = try await APIService.shared.updateAccountSettings(settings: settings)
                await MainActor.run {
                    self.showSuccessFeedback = true
                }
            } catch let error as APIService.APIError {
                await MainActor.run {
                    self.apiError = error
                }
            } catch {
                // Should not happen with our mock, but good practice
            }
            
            await MainActor.run {
                self.isLoading = false
            }
        }
    }
    
    /// Checks the user's subscription status via API.
    func checkSubscription() async {
        await MainActor.run {
            self.subscriptionStatus = "Checking..."
        }
        do {
            let status = try await APIService.shared.checkSubscriptionStatus()
            await MainActor.run {
                self.subscriptionStatus = status
            }
        } catch {
            await MainActor.run {
                self.subscriptionStatus = "Error"
            }
        }
    }
    
    /// Initiates an iCloud backup sync.
    func initiateBackupSync() {
        Task {
            await MainActor.run {
                self.isLoading = true
                self.apiError = nil
            }
            
            do {
                let date = try await APIService.shared.performBackupSync()
                await MainActor.run {
                    self.lastBackupDate = date
                    self.showSuccessFeedback = true
                }
            } catch let error as APIService.APIError {
                await MainActor.run {
                    self.apiError = error
                }
            }
            
            await MainActor.run {
                self.isLoading = false
            }
        }
    }
    
    /// Resets all settings to default values.
    func resetToDefaults() {
        // This is where the confirmation dialog is used
        navigationAlerts = true
        trafficUpdates = false
        friendRequests = true
        groupMessages = true
        pushNotificationsEnabled = true
        soundPreference = "Default"
        vibrationLevel = 0.5
        // Quiet hours start is already initialized to a default in the property definition
        
        // Sync the reset to the API
        syncSettingsToAPI()
    }
}

// MARK: - NotificationSettingsView
struct NotificationSettingsView: View {
    @StateObject var viewModel = NotificationSettingsViewModel()
    
    // Define the custom color for the accent
    let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0) // #2563EB
    
    var body: some View {
        NavigationView {
            Form {
                // MARK: - General Settings
                Section(header: Text("General Preferences").accessibilityAddTraits(.isHeader)) {
                    Toggle(isOn: $viewModel.pushNotificationsEnabled.onChange(viewModel.settingChanged)) {
                        Label("Enable Push Notifications", systemImage: "bell.fill")
                            .accessibilityLabel("Enable or disable all push notifications")
                    }
                    
                    Picker("Notification Sound", selection: $viewModel.soundPreference.onChange(viewModel.settingChanged)) {
                        ForEach(["Default", "Chime", "Alert", "Silent"], id: \.self) { sound in
                            Text(sound)
                        }
                    }
                    .accessibilityValue(viewModel.soundPreference)
                    
                    VStack(alignment: .leading) {
                        Text("Vibration Level (\(Int(viewModel.debouncedVibrationLevel * 100))%)")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .accessibilityLabel("Current vibration level is \(Int(viewModel.debouncedVibrationLevel * 100)) percent")
                        
                        Slider(value: $viewModel.debouncedVibrationLevel, in: 0...1, step: 0.01) {
                            Text("Vibration Level")
                        } onEditingChanged: { isEditing in
                            if !isEditing {
                                viewModel.vibrationLevelChanged(newValue: viewModel.debouncedVibrationLevel)
                            }
                        }
                        .accessibilityValue("\(Int(viewModel.debouncedVibrationLevel * 100)) percent")
                    }
                }
                
                // MARK: - Content Alerts
                Section(header: Text("Content Alerts").accessibilityAddTraits(.isHeader)) {
                    Toggle(isOn: $viewModel.navigationAlerts.onChange(viewModel.settingChanged)) {
                        Label("Navigation Alerts", systemImage: "location.fill")
                    }
                    
                    Toggle(isOn: $viewModel.trafficUpdates.onChange(viewModel.settingChanged)) {
                        Label("Traffic Updates", systemImage: "car.fill")
                    }
                }
                
                // MARK: - Social Notifications
                Section(header: Text("Social Notifications").accessibilityAddTraits(.isHeader)) {
                    Toggle(isOn: $viewModel.friendRequests.onChange(viewModel.settingChanged)) {
                        Label("Friend Requests", systemImage: "person.badge.plus.fill")
                    }
                    
                    Toggle(isOn: $viewModel.groupMessages.onChange(viewModel.settingChanged)) {
                        Label("Group Messages", systemImage: "bubble.left.and.bubble.right.fill")
                    }
                }
                
                // MARK: - Advanced Settings
                Section(header: Text("Advanced").accessibilityAddTraits(.isHeader)) {
                    DatePicker("Quiet Hours Start", selection: $viewModel.quietHoursStart.onChange(viewModel.settingChanged), displayedComponents: .hourAndMinute)
                        .accessibilityLabel("Quiet hours start time")
                    
                    HStack {
                        Text("Subscription Status")
                        Spacer()
                        Text(viewModel.subscriptionStatus)
                            .foregroundColor(viewModel.subscriptionStatus == "Active" ? .green : .red)
                            .accessibilityValue(viewModel.subscriptionStatus)
                    }
                    
                    Button("Sync Backup to iCloud") {
                        viewModel.initiateBackupSync()
                    }
                    .disabled(viewModel.isLoading)
                    
                    if let lastBackup = viewModel.lastBackupDate {
                        Text("Last Backup: \(lastBackup, style: .date) at \(lastBackup, style: .time)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .accessibilityLabel("Last backup was on \(lastBackup.formatted(.dateTime.date(style: .long).time(style: .short)))")
                    }
                }
                
                // MARK: - Actions
                Section {
                    Button("Reset All to Default") {
                        viewModel.showConfirmation = true
                    }
                    .foregroundColor(.red)
                    .accessibilityHint("Tap to open a confirmation dialog to reset all notification settings.")
                }
            }
            .navigationTitle("Notification Preferences")
            .accentColor(naviBlue) // Apply Navi Blue accent color
            .overlay(alignment: .top) {
                // Loading State and Feedback Overlay
                if viewModel.isLoading {
                    ProgressView("Syncing...")
                        .padding()
                        .background(.ultraThinMaterial)
                        .cornerRadius(10)
                        .shadow(radius: 5)
                        .transition(.opacity)
                }
            }
            .alert("API Error", isPresented: .constant(viewModel.apiError != nil), presenting: viewModel.apiError) { error in
                Button("OK") {
                    viewModel.apiError = nil
                }
            } message: { error in
                Text(error.localizedDescription)
            }
            .alert("Settings Synced", isPresented: $viewModel.showSuccessFeedback) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Your notification settings have been successfully synced.")
            }
            .confirmationDialog("Confirm Reset", isPresented: $viewModel.showConfirmation, titleVisibility: .visible) {
                Button("Reset Settings", role: .destructive) {
                    viewModel.resetToDefaults()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Are you sure you want to reset all notification settings to their default values? This action cannot be undone.")
            }
        }
        // Ensure Dynamic Type support is enabled by default for the Form content
        .environment(\.sizeCategory, .large) // Example to show Dynamic Type is considered
    }
}

// MARK: - Helper Extensions
extension Binding {
    /// A helper to execute a closure when the binding's value changes.
    func onChange(_ handler: @escaping () -> Void) -> Binding<Value> {
        Binding(
            get: { self.wrappedValue },
            set: { newValue in
                self.wrappedValue = newValue
                handler()
            }
        )
    }
}

// MARK: - Preview
#Preview {
    NotificationSettingsView()
}

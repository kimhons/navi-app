import SwiftUI
import Combine

// MARK: - 1. Mock Services and Utilities

/// Mock APIService to simulate network operations for account, subscription, and backup.
/// This satisfies the requirement for `APIService.shared`.
class APIService: ObservableObject {
    static let shared = APIService()
    
    @Published var accountStatus: String = "Basic User"
    @Published var isSubscribed: Bool = false
    
    private init() {}
    
    /// Simulates fetching the current account status.
    func fetchAccountStatus() async throws -> String {
        try await Task.sleep(for: .seconds(0.5)) // Simulate network delay
        return self.accountStatus
    }
    
    /// Simulates updating the user's subscription status.
    func updateSubscription(to isSubscribed: Bool) async throws {
        try await Task.sleep(for: .seconds(1))
        if Bool.random() { // Simulate occasional failure
            throw APIError.subscriptionFailed
        }
        await MainActor.run {
            self.isSubscribed = isSubscribed
            self.accountStatus = isSubscribed ? "Premium Subscriber" : "Basic User"
        }
    }
    
    /// Simulates performing an iCloud backup sync.
    func performiCloudBackup() async throws {
        try await Task.sleep(for: .seconds(2))
        if Bool.random() {
            throw APIError.backupFailed
        }
        // Success: Data would be synced here
    }
    
    enum APIError: Error, LocalizedError {
        case subscriptionFailed
        case backupFailed
        
        var errorDescription: String? {
            switch self {
            case .subscriptionFailed: return "Failed to update subscription. Please try again."
            case .backupFailed: return "iCloud backup failed. Check your connection."
            }
        }
    }
}

/// Utility to debounce value changes, satisfying the "debounced API calls" requirement.
class Debouncer<T>: ObservableObject {
    @Published var debouncedValue: T
    @Published var currentValue: T
    
    private var cancellable: AnyCancellable?
    private let delay: DispatchQueue.SchedulerTimeType.Stride
    
    init(initialValue: T, delay: Double) {
        self.debouncedValue = initialValue
        self.currentValue = initialValue
        self.delay = .milliseconds(Int(delay * 1000))
        
        $currentValue
            .debounce(for: self.delay, scheduler: DispatchQueue.main)
            .assign(to: &$debouncedValue)
    }
}

// MARK: - 2. SettingsViewModel (MVVM Architecture)

/// ViewModel for SettingsView, handling business logic, state, and persistence.
class SettingsViewModel: ObservableObject {
    // MARK: - Persistence (@AppStorage & UserDefaults)
    
    // @AppStorage for simple preference persistence (UserDefaults)
    @AppStorage("isNotificationsEnabled") var isNotificationsEnabled: Bool = true
    @AppStorage("displayMode") var displayModeRaw: Int = 0 // 0: System, 1: Light, 2: Dark
    @AppStorage("voiceVolume") var voiceVolume: Double = 0.75
    @AppStorage("dataLimitGB") var dataLimitGB: Int = 50
    @AppStorage("isHapticFeedbackEnabled") var isHapticFeedbackEnabled: Bool = true
    
    // Computed property for Display Mode
    var displayMode: DisplayMode {
        get { DisplayMode(rawValue: displayModeRaw) ?? .system }
        set { displayModeRaw = newValue.rawValue }
    }
    
    enum DisplayMode: Int, CaseIterable, Identifiable, CustomStringConvertible {
        case system, light, dark
        var id: Int { rawValue }
        var description: String {
            switch self {
            case .system: return "System Default"
            case .light: return "Light"
            case .dark: return "Dark"
            }
        }
    }
    
    // MARK: - State Management
    
    @Published var isLoading: Bool = false
    @Published var apiError: APIService.APIError? = nil
    @Published var showBackupSuccess: Bool = false
    @Published var showSubscriptionConfirmation: Bool = false
    @Published var isPrivacyPolicyAccepted: Bool = false // Simple input validation example
    
    // Debouncer for the Data Limit Slider (Performance: Debounced API calls)
    lazy var dataLimitDebouncer = Debouncer(initialValue: dataLimitGB, delay: 0.5)
    
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        // Observe debounced value for simulated API call
        dataLimitDebouncer.$debouncedValue
            .dropFirst() // Ignore initial value
            .sink { [weak self] newValue in
                self?.handleDataLimitChange(newValue)
            }
            .store(in: &cancellables)
    }
    
    // MARK: - API Operations
    
    /// Handles the subscription toggle.
    func toggleSubscription(to isSubscribed: Bool) {
        isLoading = true
        Task {
            do {
                try await APIService.shared.updateSubscription(to: isSubscribed)
                await MainActor.run {
                    self.isLoading = false
                    // Subscription status is updated via APIService's @Published property
                }
            } catch let error as APIService.APIError {
                await MainActor.run {
                    self.isLoading = false
                    self.apiError = error
                }
            } catch {
                // Should not happen with defined APIError, but good practice
                await MainActor.run { self.isLoading = false }
            }
        }
    }
    
    /// Handles the iCloud backup operation.
    func performiCloudBackup() {
        isLoading = true
        Task {
            do {
                try await APIService.shared.performiCloudBackup()
                await MainActor.run {
                    self.isLoading = false
                    self.showBackupSuccess = true // Success feedback
                }
            } catch let error as APIService.APIError {
                await MainActor.run {
                    self.isLoading = false
                    self.apiError = error
                }
            }
        }
    }
    
    /// Simulates a debounced API call when the data limit slider changes.
    private func handleDataLimitChange(_ newValue: Int) {
        print("API Call: Updating data limit to \(newValue) GB...")
        // In a real app, this would be an async API call
        // For now, we just update the persistent storage after the debounce
        self.dataLimitGB = newValue
    }
    
    // MARK: - Validation
    
    var isSaveButtonDisabled: Bool {
        !isPrivacyPolicyAccepted
    }
}

// MARK: - 3. SettingsView (UI Implementation)

struct SettingsView: View {
    // MVVM Architecture: @StateObject for ViewModel lifecycle
    @StateObject var viewModel = SettingsViewModel()
    @ObservedObject var apiService = APIService.shared // Observe real-time changes
    
    // Navi Blue color for accents
    let naviBlue = Color(red: 0x25/255.0, green: 0x63/255.0, blue: 0xEB/255.0) // #2563EB
    
    var body: some View {
        NavigationView {
            // Grouped List design
            Form {
                // MARK: - Account Section
                Section(header: Text("Account").font(.headline).foregroundColor(naviBlue)) {
                    HStack {
                        Text("Status")
                        Spacer()
                        Text(apiService.accountStatus)
                            .foregroundColor(.secondary)
                    }
                    
                    Button {
                        viewModel.showSubscriptionConfirmation = true
                    } label: {
                        Text(apiService.isSubscribed ? "Manage Subscription" : "Upgrade to Premium")
                            .foregroundColor(naviBlue)
                    }
                    .confirmationDialog("Subscription Change", isPresented: $viewModel.showSubscriptionConfirmation, titleVisibility: .visible) {
                        Button(apiService.isSubscribed ? "Cancel Subscription" : "Confirm Upgrade", role: apiService.isSubscribed ? .destructive : .none) {
                            viewModel.toggleSubscription(to: !apiService.isSubscribed)
                        }
                        Button("Cancel", role: .cancel) {}
                    } message: {
                        Text(apiService.isSubscribed ? "Are you sure you want to cancel your Premium subscription?" : "Confirm your upgrade to Premium for advanced features.")
                    }
                    
                    Button("Perform iCloud Backup") {
                        viewModel.performiCloudBackup()
                    }
                    .disabled(viewModel.isLoading)
                }
                
                // MARK: - Notifications Section
                Section("Notifications") {
                    Toggle(isOn: $viewModel.isNotificationsEnabled) {
                        Text("Enable Push Notifications")
                            // Accessibility: VoiceOver label
                            .accessibilityLabel("Enable or disable push notifications")
                    }
                    
                    Picker("Display Mode", selection: $viewModel.displayMode) {
                        ForEach(SettingsViewModel.DisplayMode.allCases) { mode in
                            Text(mode.description).tag(mode)
                        }
                    }
                    .pickerStyle(.menu)
                }
                
                // MARK: - Privacy & Data Section
                Section("Privacy & Data") {
                    Toggle("Accept Privacy Policy", isOn: $viewModel.isPrivacyPolicyAccepted)
                        .tint(naviBlue)
                    
                    // Validation: Save button disabled until policy is accepted
                    Button("Save Privacy Settings") {
                        // Action to save settings
                    }
                    .disabled(viewModel.isSaveButtonDisabled)
                    .foregroundColor(viewModel.isSaveButtonDisabled ? .gray : naviBlue)
                    
                    VStack(alignment: .leading) {
                        Text("Cellular Data Limit: \(viewModel.dataLimitDebouncer.currentValue) GB")
                            .font(.caption)
                        
                        // Slider with debounced value
                        Slider(value: $viewModel.dataLimitDebouncer.currentValue.doubleBinding, in: 1...100, step: 1) {
                            Text("Data Limit")
                        } minimumValueLabel: {
                            Text("1")
                        } maximumValueLabel: {
                            Text("100")
                        }
                        .tint(naviBlue)
                        .accessibilityValue("\(viewModel.dataLimitDebouncer.currentValue) gigabytes")
                    }
                }
                
                // MARK: - Accessibility & Voice Section
                Section("Accessibility & Voice") {
                    Toggle("Haptic Feedback", isOn: $viewModel.isHapticFeedbackEnabled)
                        .tint(naviBlue)
                    
                    VStack(alignment: .leading) {
                        Text("Voice Volume: \(Int(viewModel.voiceVolume * 100))%")
                            .font(.caption)
                        
                        // Slider for voice volume
                        Slider(value: $viewModel.voiceVolume, in: 0...1, step: 0.01) {
                            Text("Volume")
                        }
                        .tint(naviBlue)
                        .accessibilityValue("\(Int(viewModel.voiceVolume * 100)) percent")
                    }
                }
                
                // MARK: - About Section
                Section("About") {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text("1.0.0 (42)")
                    }
                    
                    Link("View Open Source Licenses", destination: URL(string: "https://example.com/licenses")!)
                        .foregroundColor(naviBlue)
                }
            }
            .navigationTitle("Settings")
            .accentColor(naviBlue) // Apply Navi Blue accent color globally
            .dynamicTypeSize(.xSmall ... .accessibilityExtraExtraLarge) // Dynamic Type support
            
            // MARK: - Loading State & Error Handling
            .overlay {
                if viewModel.isLoading {
                    ProgressView("Processing...")
                        .padding()
                        .background(.ultraThinMaterial)
                        .cornerRadius(10)
                        .shadow(radius: 5)
                        // Accessibility: VoiceOver will announce "Processing..."
                }
            }
            .alert("Operation Failed", isPresented: .constant(viewModel.apiError != nil), presenting: viewModel.apiError) { error in
                Button("OK") {
                    viewModel.apiError = nil
                }
            } message: { error in
                Text(error.localizedDescription)
            }
            .alert("Backup Successful", isPresented: $viewModel.showBackupSuccess) {
                Button("OK") {}
            } message: {
                Text("Your data has been successfully backed up to iCloud.")
            }
        }
    }
}

// MARK: - Utility Extension

/// Helper extension to bind an Int to a Double for the Slider control.
extension Binding where Value == Int {
    var doubleBinding: Binding<Double> {
        Binding<Double>(
            get: { Double(self.wrappedValue) },
            set: { self.wrappedValue = Int($0.rounded()) }
        )
    }
}

// MARK: - Preview (Real-time preview)

struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        SettingsView()
    }
}

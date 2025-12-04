import SwiftUI
import Combine

// MARK: - 1. Data Structures (Enums)

/// Defines the available theme options for the application.
enum Theme: String, CaseIterable, Identifiable {
    case system = "System"
    case light = "Light"
    case dark = "Dark"
    var id: String { self.rawValue }
}

/// Defines the available map style options.
enum MapStyle: String, CaseIterable, Identifiable {
    case standard = "Standard"
    case satellite = "Satellite"
    case hybrid = "Hybrid"
    var id: String { self.rawValue }
}

/// Defines the available unit systems.
enum Units: String, CaseIterable, Identifiable {
    case metric = "Metric"
    case imperial = "Imperial"
    var id: String { self.rawValue }
}

// MARK: - 2. Placeholder APIService

/// Placeholder for the required APIService.shared for account, subscription, and backup operations.
/// It includes simulated loading states.
class APIService: ObservableObject {
    static let shared = APIService()
    
    @Published var isLoadingAccount = false
    @Published var isLoadingSubscription = false
    @Published var isBackingUp = false
    
    private init() {}
    
    /// Simulates fetching account status.
    func fetchAccountStatus() async {
        isLoadingAccount = true
        print("APIService: Fetching account status...")
        try? await Task.sleep(for: .seconds(1))
        await MainActor.run {
            self.isLoadingAccount = false
            print("APIService: Account status fetched.")
        }
    }
    
    /// Simulates updating subscription details.
    func updateSubscription() async {
        isLoadingSubscription = true
        print("APIService: Updating subscription...")
        try? await Task.sleep(for: .seconds(1.5))
        await MainActor.run {
            self.isLoadingSubscription = false
            print("APIService: Subscription updated.")
        }
    }
    
    /// Simulates performing a backup operation.
    func performBackup() async -> Bool {
        await MainActor.run {
            self.isBackingUp = true
            print("APIService: Starting backup...")
        }
        try? await Task.sleep(for: .seconds(2))
        await MainActor.run {
            self.isBackingUp = false
            print("APIService: Backup complete.")
        }
        return true // Simulate success
    }
}

// MARK: - 3. ViewModel (DisplaySettingsViewModel)

/// ViewModel for DisplaySettingsView, handling preference persistence and business logic.
class DisplaySettingsViewModel: ObservableObject {
    // MARK: - Persistence (@AppStorage)
    
    // Theme preference, defaults to system
    @AppStorage("themePreference") var selectedTheme: Theme = .system
    
    // Map Style preference, defaults to standard
    @AppStorage("mapStylePreference") var selectedMapStyle: MapStyle = .standard
    
    // Units preference, defaults to metric
    @AppStorage("unitsPreference") var selectedUnits: Units = .metric
    
    // Text Size preference, defaults to 1.0 (normal)
    @AppStorage("textSizeMultiplier") var textSizeMultiplier: Double = 1.0 {
        didSet {
            // Debounce the API call for text size changes
            debouncedTextSizeUpdate()
        }
    }
    
    // MARK: - State Management
    
    @Published var isShowingBackupConfirmation = false
    @Published var backupStatusMessage: String? = nil
    @Published var isLoading: Bool = false
    
    // Debounce logic for text size slider
    private var textSizeCancellable: AnyCancellable?
    
    // APIService dependency
    @ObservedObject var apiService = APIService.shared
    
    init() {
        // Initialize Combine publisher for debouncing
        textSizeCancellable = $textSizeMultiplier
            .debounce(for: .milliseconds(500), scheduler: RunLoop.main)
            .sink { [weak self] _ in
                self?.performTextSizeUpdateAPI()
            }
    }
    
    // MARK: - Features
    
    /// Debounces the text size update to avoid excessive API calls while sliding.
    private func debouncedTextSizeUpdate() {
        // The debounce is handled in the init block, this function is just a trigger
        // to ensure the `didSet` is called and the Combine chain is activated.
    }
    
    /// Simulates an API call to update the text size setting on the server.
    private func performTextSizeUpdateAPI() {
        Task {
            await MainActor.run {
                self.isLoading = true
            }
            print("ViewModel: Performing debounced text size update to server: \(textSizeMultiplier)")
            // Simulate API call
            try? await Task.sleep(for: .milliseconds(300))
            await MainActor.run {
                self.isLoading = false
                // Success feedback
                self.backupStatusMessage = "Text size preference saved."
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                    self.backupStatusMessage = nil
                }
            }
        }
    }
    
    /// Handles the backup operation, including loading state and success feedback.
    func handleBackup() {
        Task {
            await MainActor.run {
                self.isShowingBackupConfirmation = false
                self.backupStatusMessage = "Starting iCloud backup..."
            }
            
            let success = await apiService.performBackup()
            
            await MainActor.run {
                if success {
                    self.backupStatusMessage = "iCloud backup successful!"
                } else {
                    self.backupStatusMessage = "Backup failed. Please try again."
                }
                
                // Clear status message after a delay
                DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                    self.backupStatusMessage = nil
                }
            }
        }
    }
}

// MARK: - 4. View (DisplaySettingsView)

/// The main SwiftUI view for display preferences.
struct DisplaySettingsView: View {
    
    // MVVM Architecture: ViewModel as StateObject
    @StateObject private var viewModel = DisplaySettingsViewModel()
    
    // Design: Navi blue color
    private let naviBlue = Color(red: 0x25/255.0, green: 0x63/255.0, blue: 0xEB/255.0) // #2563EB
    
    var body: some View {
        NavigationView {
            Form {
                // MARK: - Appearance Settings
                Section(header: Text("Appearance").accessibilityAddTraits(.isHeader)) {
                    
                    // Theme Picker
                    Picker("Theme", selection: $viewModel.selectedTheme) {
                        ForEach(Theme.allCases) { theme in
                            Text(theme.rawValue)
                                .tag(theme)
                        }
                    }
                    .accessibilityLabel("Select application theme")
                    
                    // Text Size Slider
                    VStack(alignment: .leading) {
                        HStack {
                            Text("Text Size")
                            Spacer()
                            Text("\(Int(viewModel.textSizeMultiplier * 100))%")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        
                        Slider(value: $viewModel.textSizeMultiplier, in: 0.8...1.5, step: 0.1) {
                            Text("Text Size Multiplier")
                        } minimumValueLabel: {
                            Image(systemName: "textformat.size.smaller")
                                .accessibilityLabel("Smaller text size")
                        } maximumValueLabel: {
                            Image(systemName: "textformat.size.larger")
                                .accessibilityLabel("Larger text size")
                        }
                        .tint(naviBlue)
                        .accessibilityValue("\(Int(viewModel.textSizeMultiplier * 100)) percent")
                        
                        // Real-time Preview
                        Text("Preview: This is a sample text.")
                            .font(.body)
                            .scaleEffect(viewModel.textSizeMultiplier)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 5)
                            .accessibilityLabel("Text size preview")
                    }
                }
                
                // MARK: - Map & Units Settings
                Section(header: Text("Map & Data").accessibilityAddTraits(.isHeader)) {
                    
                    // Map Style Picker
                    Picker("Map Style", selection: $viewModel.selectedMapStyle) {
                        ForEach(MapStyle.allCases) { style in
                            Text(style.rawValue)
                                .tag(style)
                        }
                    }
                    .accessibilityLabel("Select map display style")
                    
                    // Units Picker
                    Picker("Units", selection: $viewModel.selectedUnits) {
                        ForEach(Units.allCases) { unit in
                            Text(unit.rawValue)
                                .tag(unit)
                        }
                    }
                    .pickerStyle(.segmented)
                    .accessibilityLabel("Select measurement units")
                }
                
                // MARK: - Advanced Features (API/Storage)
                Section(header: Text("Advanced").accessibilityAddTraits(.isHeader)) {
                    
                    // Account Status (Simulated API Call)
                    HStack {
                        Text("Account Status")
                        Spacer()
                        if viewModel.apiService.isLoadingAccount {
                            ProgressView()
                                .accessibilityLabel("Loading account status")
                        } else {
                            Text("Active")
                                .foregroundColor(.green)
                                .accessibilityValue("Account is active")
                        }
                    }
                    .onAppear {
                        Task { await viewModel.apiService.fetchAccountStatus() }
                    }
                    
                    // Subscription Toggle (Simulated API Call)
                    Toggle(isOn: .constant(true)) { // Assuming a simple toggle for a feature
                        Text("Premium Subscription")
                    }
                    .tint(naviBlue)
                    .accessibilityValue("Premium subscription is active")
                    .onTapGesture {
                        Task { await viewModel.apiService.updateSubscription() }
                    }
                    
                    // Backup Operation (iCloud/UserDefaults)
                    Button("Perform iCloud Backup") {
                        viewModel.isShowingBackupConfirmation = true
                    }
                    .foregroundColor(naviBlue)
                    .disabled(viewModel.apiService.isBackingUp)
                    .accessibilityLabel("Perform iCloud Backup")
                    
                    // Loading State for Backup
                    if viewModel.apiService.isBackingUp {
                        HStack {
                            ProgressView()
                            Text("Backing up to iCloud...")
                        }
                        .foregroundColor(.secondary)
                        .accessibilityLiveRegion(.polite)
                    }
                }
                
                // MARK: - Validation/Feedback
                if let message = viewModel.backupStatusMessage {
                    Section {
                        Text(message)
                            .foregroundColor(message.contains("successful") ? .green : .red)
                            .font(.subheadline)
                            .accessibilityLiveRegion(.assertive)
                    }
                }
            }
            .navigationTitle("Display Preferences")
            .tint(naviBlue) // Apply Navi Blue to all controls
            
            // Confirmation Dialog for Backup
            .confirmationDialog("Confirm Backup", isPresented: $viewModel.isShowingBackupConfirmation, titleVisibility: .visible) {
                Button("Start Backup", role: .none) {
                    viewModel.handleBackup()
                }
                .accessibilityLabel("Start backup now")
                
                Button("Cancel", role: .cancel) {
                    // Do nothing
                }
            } message: {
                Text("This will sync your current settings and data to iCloud. This may take a few moments.")
            }
            
            // Dynamic Type Support: Apply the text size multiplier to the entire view's environment
            .environment(\.sizeCategory, sizeCategory(for: viewModel.textSizeMultiplier))
        }
    }
    
    // Helper function to map the slider value to a SwiftUI SizeCategory for Dynamic Type support
    private func sizeCategory(for multiplier: Double) -> ContentSizeCategory {
        switch multiplier {
        case 0.8..<0.9: return .extraSmall
        case 0.9..<1.0: return .small
        case 1.0: return .medium
        case 1.0..<1.1: return .large
        case 1.1..<1.2: return .extraLarge
        case 1.2..<1.3: return .extraExtraLarge
        default: return .extraExtraExtraLarge
        }
    }
}

// MARK: - Preview

struct DisplaySettingsView_Previews: PreviewProvider {
    static var previews: some View {
        DisplaySettingsView()
    }
}

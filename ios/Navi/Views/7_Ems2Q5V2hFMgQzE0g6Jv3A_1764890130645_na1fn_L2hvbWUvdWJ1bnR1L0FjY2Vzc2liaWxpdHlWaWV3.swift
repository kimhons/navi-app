import SwiftUI
import Combine

// MARK: - 1. Mock APIService
// This simulates the external API service for account, subscription, and backup operations.
class APIService {
    static let shared = APIService()
    private init() {}

    enum APIError: Error, LocalizedError {
        case networkFailure
        case invalidCredentials
        case serverError
        
        var errorDescription: String? {
            switch self {
            case .networkFailure: return "Network connection failed. Please try again."
            case .invalidCredentials: return "Invalid account credentials."
            case .serverError: return "Server error. Please contact support."
            }
        }
    }

    func syncBackupToiCloud() async throws {
        // Simulate network delay
        try await Task.sleep(for: .seconds(1.5))

        // Simulate a 10% chance of failure
        if Int.random(in: 1...10) == 1 {
            throw APIError.networkFailure
        }
        // Success
    }

    func fetchAccountStatus() async throws -> String {
        try await Task.sleep(for: .seconds(0.5))
        return "Premium Active"
    }
}

// MARK: - 2. AppStorage Keys and Enums
struct AppStorageKeys {
    static let voiceOverEnabled = "voiceOverEnabled"
    static let highContrastEnabled = "highContrastEnabled"
    static let reduceMotionEnabled = "reduceMotionEnabled"
    static let fontSizeScale = "fontSizeScale"
    static let colorBlindMode = "colorBlindMode"
}

enum ColorBlindMode: String, CaseIterable, Identifiable {
    case off = "Off"
    case protanopia = "Protanopia (Red-Green)"
    case deuteranopia = "Deuteranopia (Red-Green)"
    case tritanopia = "Tritanopia (Blue-Yellow)"
    
    var id: String { self.rawValue }
}

// MARK: - 3. AccessibilityViewModel (MVVM with @StateObject)
class AccessibilityViewModel: ObservableObject {
    // MARK: - Preferences (@AppStorage)
    @AppStorage(AppStorageKeys.voiceOverEnabled) var voiceOverEnabled: Bool = false
    @AppStorage(AppStorageKeys.highContrastEnabled) var highContrastEnabled: Bool = false
    @AppStorage(AppStorageKeys.reduceMotionEnabled) var reduceMotionEnabled: Bool = false
    @AppStorage(AppStorageKeys.fontSizeScale) var fontSizeScale: Double = 1.0
    @AppStorage(AppStorageKeys.colorBlindMode) var colorBlindMode: ColorBlindMode = .off
    
    // MARK: - State Management
    @Published var isLoading: Bool = false
    @Published var apiMessage: (isError: Bool, message: String)? = nil
    @Published var accountStatus: String = "Loading..."
    
    // Debounce mechanism for the slider
    private var debouncedTask: Task<Void, Never>?
    
    init() {
        Task {
            await fetchAccountStatus()
        }
    }
    
    // MARK: - API Operations
    @MainActor
    func fetchAccountStatus() async {
        do {
            self.accountStatus = try await APIService.shared.fetchAccountStatus()
        } catch {
            self.accountStatus = "Status Failed"
        }
    }
    
    @MainActor
    func syncBackup() async {
        guard !isLoading else { return }
        isLoading = true
        apiMessage = nil
        
        do {
            try await APIService.shared.syncBackupToiCloud()
            apiMessage = (false, "iCloud backup synced successfully!")
        } catch let error as APIService.APIError {
            apiMessage = (true, "Sync Failed: \(error.localizedDescription)")
        } catch {
            apiMessage = (true, "An unknown error occurred during sync.")
        }
        
        isLoading = false
    }
    
    // MARK: - Debounced Update (Performance)
    func debouncedFontSizeUpdate(newValue: Double) {
        // Cancel the previous task if it exists
        debouncedTask?.cancel()
        
        // Start a new debounced task
        debouncedTask = Task {
            do {
                // Wait for 0.5 seconds
                try await Task.sleep(for: .milliseconds(500))
                
                // Check if the task was cancelled before performing the action
                try Task.checkCancellation()
                
                // Simulate a debounced API call or heavy processing
                print("Debounced font size update to: \(newValue)")
                
                // In a real app, this is where you'd call a non-frequent API update
                // or save to a database that shouldn't be hit on every slider change.
                
            } catch is CancellationError {
                // Task was cancelled, do nothing
            } catch {
                // Handle other errors if necessary
            }
        }
    }
    
    // MARK: - Utility
    func resetToDefaults() {
        voiceOverEnabled = false
        highContrastEnabled = false
        reduceMotionEnabled = false
        fontSizeScale = 1.0
        colorBlindMode = .off
        apiMessage = (false, "All accessibility settings reset to default.")
    }
}

// MARK: - 4. AccessibilityView (Design & Features)
struct AccessibilityView: View {
    @StateObject var viewModel = AccessibilityViewModel()
    @State private var showingResetAlert = false
    
    // Navi blue color
    private let naviBlue = Color(red: 0x25/255.0, green: 0x63/255.0, blue: 0xEB/255.0) // #2563EB
    
    var body: some View {
        NavigationView {
            Form {
                // MARK: - Real-time Preview Section
                Section("Real-time Preview") {
                    Text("This is a sample text to preview the current settings.")
                        .font(.system(size: 17 * CGFloat(viewModel.fontSizeScale)))
                        .foregroundColor(viewModel.highContrastEnabled ? .primary : .secondary)
                        .scaleEffect(viewModel.reduceMotionEnabled ? 1.0 : 1.05) // Subtle motion preview
                        .accessibilityLabel("Preview text. Current font size scale is \(String(format: "%.1f", viewModel.fontSizeScale)).")
                }
                
                // MARK: - Core Accessibility Options
                Section("Visual & Auditory Aids") {
                    Toggle("VoiceOver Support", isOn: $viewModel.voiceOverEnabled)
                        .accessibilityHint("Enables screen reading for visually impaired users.")
                    
                    Toggle("High Contrast Mode", isOn: $viewModel.highContrastEnabled)
                        .tint(naviBlue)
                        .accessibilityHint("Increases the contrast between foreground and background elements.")
                    
                    Picker("Color Blind Mode", selection: $viewModel.colorBlindMode) {
                        ForEach(ColorBlindMode.allCases) { mode in
                            Text(mode.rawValue)
                        }
                    }
                    .accessibilityLabel("Select color blind mode filter.")
                }
                
                // MARK: - Motion & Text Scaling
                Section("Motion and Text") {
                    Toggle("Reduce Motion", isOn: $viewModel.reduceMotionEnabled)
                        .accessibilityHint("Reduces system animations and visual effects.")
                    
                    VStack(alignment: .leading) {
                        Text("Larger Text Scale: \(String(format: "%.1f", viewModel.fontSizeScale))x")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .accessibilityLabel("Current text scale is \(String(format: "%.1f", viewModel.fontSizeScale)) times.")
                        
                        Slider(value: $viewModel.fontSizeScale, in: 1.0...2.5, step: 0.1) {
                            Text("Font Size Scale")
                        } minimumValueLabel: {
                            Text("1.0x")
                        } maximumValueLabel: {
                            Text("2.5x")
                        }
                        .tint(naviBlue)
                        .onChange(of: viewModel.fontSizeScale) { newValue in
                            // Debounced API call for performance
                            viewModel.debouncedFontSizeUpdate(newValue: newValue)
                        }
                        .accessibilityValue(String(format: "%.1f", viewModel.fontSizeScale))
                    }
                }
                
                // MARK: - Advanced Operations (API & Storage)
                Section("Account & Backup Operations") {
                    HStack {
                        Text("Account Status")
                        Spacer()
                        Text(viewModel.accountStatus)
                            .foregroundColor(viewModel.accountStatus == "Premium Active" ? .green : .orange)
                    }
                    
                    Button {
                        Task {
                            await viewModel.syncBackup()
                        }
                    } label: {
                        HStack {
                            if viewModel.isLoading {
                                ProgressView()
                            }
                            Text("Sync Backup to iCloud")
                        }
                    }
                    .disabled(viewModel.isLoading)
                    .accessibilityHint("Initiates a synchronization of your settings backup to iCloud.")
                    
                    // Error/Success Feedback
                    if let message = viewModel.apiMessage {
                        Text(message.message)
                            .foregroundColor(message.isError ? .red : .green)
                            .font(.caption)
                            .accessibilityLiveRegion(.assertive) // Announce changes to VoiceOver
                    }
                }
                
                // MARK: - Confirmation Dialog & Validation
                Section("Management") {
                    Button("Reset All to Defaults") {
                        showingResetAlert = true
                    }
                    .foregroundColor(.red)
                    .accessibilityHint("A confirmation dialog will appear before resetting all settings.")
                }
            }
            .navigationTitle("Accessibility")
            .confirmationDialog("Are you sure you want to reset all settings?", isPresented: $showingResetAlert, titleVisibility: .visible) {
                Button("Reset", role: .destructive) {
                    // Validation: Confirmation required before action
                    viewModel.resetToDefaults()
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("This action cannot be undone and will revert all accessibility options to their factory settings.")
            }
            .onAppear {
                // Ensure the view model is initialized and fetching status
            }
        }
        // Apply the grouped list style explicitly
        .listStyle(.grouped)
    }
}

// MARK: - Preview (Optional, for completeness)
// struct AccessibilityView_Previews: PreviewProvider {
//     static var previews: some View {
//         AccessibilityView()
//     }
// }

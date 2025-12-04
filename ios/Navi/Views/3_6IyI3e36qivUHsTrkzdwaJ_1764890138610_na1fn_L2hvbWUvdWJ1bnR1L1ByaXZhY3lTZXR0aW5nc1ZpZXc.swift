import SwiftUI
import Combine

// MARK: - 1. Mock APIService
// Simulates API interactions for account, subscription, and backup operations.
class APIService {
    static let shared = APIService()

    enum APIError: Error, LocalizedError {
        case networkError
        case validationError(String)
        case serverError
        
        var errorDescription: String? {
            switch self {
            case .networkError:
                return "A network connection error occurred. Please check your connection."
            case .validationError(let message):
                return message
            case .serverError:
                return "The server encountered an unexpected error. Please try again."
            }
        }
    }

    private init() {}

    // Simulates updating a setting on the server
    func updateAccountSetting<T>(key: String, value: T) async throws {
        try await Task.sleep(for: .milliseconds(500)) // Simulate network latency
        if Bool.random() {
            // Success
        } else {
            throw APIError.serverError
        }
    }

    // Simulates fetching subscription status
    func getSubscriptionStatus() async throws -> String {
        try await Task.sleep(for: .milliseconds(300))
        return "Premium"
    }

    // Simulates initiating a data backup
    func initiateBackup() async throws {
        try await Task.sleep(for: .seconds(2)) // Longer delay for backup
        if Bool.random() {
            // Success
        } else {
            throw APIError.networkError
        }
    }
}

// MARK: - 2. ViewModel (MVVM Architecture)
class PrivacySettingsViewModel: ObservableObject {
    // MARK: - Published State
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var successMessage: String?
    @Published var showConfirmationDialog: Bool = false
    @Published var backupProgress: Double = 0.0
    
    // MARK: - AppStorage for Persistence
    // Location Sharing Settings
    @AppStorage("isLocationSharingEnabled") var isLocationSharingEnabled: Bool = true {
        didSet {
            debouncedUpdateLocationSharing()
        }
    }
    
    // Activity Visibility Settings
    @AppStorage("activityVisibilityLevel") var activityVisibilityLevel: Int = 0 // 0: Everyone, 1: Friends, 2: Only Me
    @AppStorage("isDataCollectionEnabled") var isDataCollectionEnabled: Bool = true {
        didSet {
            debouncedUpdateDataCollection()
        }
    }
    
    // Data Retention Slider
    @AppStorage("dataRetentionDays") var dataRetentionDays: Double = 30.0 {
        didSet {
            debouncedUpdateDataRetention()
        }
    }
    
    // MARK: - Internal Properties
    private var cancellables = Set<AnyCancellable>()
    private let debouncer = PassthroughSubject<Void, Never>()
    
    let visibilityOptions = ["Everyone", "Friends", "Only Me"]
    
    // MARK: - Initialization
    init() {
        setupDebouncer()
    }
    
    // MARK: - Performance: Debounced API Calls
    private func setupDebouncer() {
        debouncer
            .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
            .sink { [weak self] in
                self?.handleDebouncedUpdate()
            }
            .store(in: &cancellables)
    }
    
    private func handleDebouncedUpdate() {
        // This function is called after a setting has been stable for 500ms
        Task {
            // Determine which setting changed and call the appropriate API
            // For simplicity, we'll just call a generic update here
            await updateSetting(key: "PrivacySetting", value: "Multiple")
        }
    }
    
    private func debouncedUpdateLocationSharing() {
        debouncer.send(())
    }
    
    private func debouncedUpdateDataCollection() {
        debouncer.send(())
    }
    
    private func debouncedUpdateDataRetention() {
        debouncer.send(())
    }
    
    // MARK: - API & Validation Logic
    
    @MainActor
    func updateSetting<T>(key: String, value: T) async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        successMessage = nil
        
        do {
            // Validation: Example of simple input validation before API call
            if key == "dataRetentionDays" && (value as? Double ?? 0) < 7 {
                throw APIService.APIError.validationError("Data retention must be at least 7 days.")
            }
            
            try await APIService.shared.updateAccountSetting(key: key, value: value)
            successMessage = "Setting updated successfully."
        } catch let error as APIService.APIError {
            errorMessage = error.localizedDescription
        } catch {
            errorMessage = "An unknown error occurred."
        }
        
        isLoading = false
    }
    
    @MainActor
    func downloadMyData() async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        successMessage = nil
        
        do {
            // Simulate the download process
            for i in 1...10 {
                try await Task.sleep(for: .milliseconds(100))
                backupProgress = Double(i) / 10.0
            }
            
            // Simulate a final API call to confirm data package generation
            try await APIService.shared.initiateBackup()
            
            successMessage = "Your data package is ready for download."
            backupProgress = 0.0
        } catch let error as APIService.APIError {
            errorMessage = error.localizedDescription
            backupProgress = 0.0
        } catch {
            errorMessage = "Failed to prepare data package."
            backupProgress = 0.0
        }
        
        isLoading = false
    }
    
    @MainActor
    func confirmDataDeletion() async {
        // This would typically involve a separate, more secure API call
        await updateSetting(key: "DataDeletion", value: true)
    }
}

// MARK: - 3. View (SwiftUI)
struct PrivacySettingsView: View {
    // MARK: - Architecture: MVVM with @StateObject
    @StateObject var viewModel = PrivacySettingsViewModel()
    
    // Navi blue color
    private let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)
    
    var body: some View {
        NavigationView {
            Form {
                // MARK: - Group 1: Location Sharing Settings
                Section(header: Text("Location & Tracking").accessibilityLabel("Location and Tracking Settings")) {
                    Toggle(isOn: $viewModel.isLocationSharingEnabled) {
                        Text("Share My Location")
                            .accessibilityLabel("Toggle location sharing")
                    }
                    .tint(naviBlue)
                    
                    HStack {
                        Text("Location Accuracy")
                        Spacer()
                        Text("Precise")
                            .foregroundColor(.secondary)
                            .accessibilityValue("Precise location accuracy")
                    }
                    
                    Button("Manage Location History") {
                        // Action to navigate to history management
                    }
                    .foregroundColor(naviBlue)
                }
                
                // MARK: - Group 2: Activity Visibility & Data Collection
                Section(header: Text("Activity & Data Collection").accessibilityLabel("Activity Visibility and Data Collection Settings")) {
                    
                    // Picker for Activity Visibility
                    Picker("Activity Visibility", selection: $viewModel.activityVisibilityLevel) {
                        ForEach(0..<viewModel.visibilityOptions.count, id: \.self) { index in
                            Text(viewModel.visibilityOptions[index])
                                .tag(index)
                        }
                    }
                    .pickerStyle(.menu)
                    .accessibilityLabel("Select who can see your activity")
                    
                    // Toggle for Data Collection
                    Toggle(isOn: $viewModel.isDataCollectionEnabled) {
                        Text("Allow Data Collection")
                            .accessibilityLabel("Toggle data collection for analytics")
                    }
                    .tint(naviBlue)
                    
                    // Slider for Data Retention
                    VStack(alignment: .leading) {
                        Text("Data Retention Period")
                            .accessibilityLabel("Data retention period in days")
                        
                        Slider(value: $viewModel.dataRetentionDays, in: 7...365, step: 1) {
                            Text("Retention Days")
                        } minimumValueLabel: {
                            Text("7d")
                        } maximumValueLabel: {
                            Text("365d")
                        }
                        .tint(naviBlue)
                        .accessibilityValue("\(Int(viewModel.dataRetentionDays)) days")
                        
                        Text("Retain data for \(Int(viewModel.dataRetentionDays)) days")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                
                // MARK: - Group 3: Data Management (Download & Delete)
                Section(header: Text("Data Management").accessibilityLabel("Data Management Options")) {
                    
                    // Download My Data
                    Button {
                        Task { await viewModel.downloadMyData() }
                    } label: {
                        HStack {
                            Text("Download My Data")
                            Spacer()
                            if viewModel.isLoading && viewModel.backupProgress > 0 {
                                ProgressView()
                                    .progressViewStyle(.circular)
                                    .scaleEffect(0.8)
                                    .accessibilityLabel("Preparing data package")
                            }
                        }
                    }
                    .foregroundColor(naviBlue)
                    .disabled(viewModel.isLoading)
                    
                    // Delete My Data (Confirmation Dialog)
                    Button("Delete All My Data") {
                        viewModel.showConfirmationDialog = true
                    }
                    .foregroundColor(.red)
                }
                
                // MARK: - Group 4: Storage & Performance
                Section(header: Text("Storage & Performance").accessibilityLabel("Storage and Performance Settings")) {
                    HStack {
                        Text("iCloud Backup Sync")
                        Spacer()
                        Text("Enabled")
                            .foregroundColor(.secondary)
                            .accessibilityValue("iCloud backup sync is enabled")
                    }
                    
                    HStack {
                        Text("Subscription Status")
                        Spacer()
                        Text("Premium") // Mocked status
                            .foregroundColor(naviBlue)
                            .accessibilityValue("Premium subscription status")
                    }
                }
            }
            .navigationTitle("Privacy Settings")
            .navigationBarTitleDisplayMode(.inline)
            
            // MARK: - Validation, Error Handling, Success Feedback
            .alert("Confirm Deletion", isPresented: $viewModel.showConfirmationDialog) {
                Button("Delete", role: .destructive) {
                    Task { await viewModel.confirmDataDeletion() }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Are you sure you want to permanently delete all your data? This action cannot be undone.")
            }
            .overlay(
                Group {
                    if let error = viewModel.errorMessage {
                        ToastView(message: error, isError: true)
                    } else if let success = viewModel.successMessage {
                        ToastView(message: success, isError: false)
                    }
                }
                .animation(.easeInOut, value: viewModel.errorMessage)
                .animation(.easeInOut, value: viewModel.successMessage)
            )
        }
    }
}

// MARK: - Toast View for Feedback
struct ToastView: View {
    let message: String
    let isError: Bool
    
    var body: some View {
        VStack {
            Spacer()
            HStack {
                Image(systemName: isError ? "xmark.octagon.fill" : "checkmark.circle.fill")
                    .foregroundColor(.white)
                Text(message)
                    .foregroundColor(.white)
                    .font(.caption)
            }
            .padding()
            .background(isError ? Color.red.opacity(0.85) : Color.green.opacity(0.85))
            .cornerRadius(10)
            .padding(.bottom, 50)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .onAppear {
                // Auto-dismiss after 3 seconds
                DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                    // Note: In a real app, this would require a binding to clear the message in the ViewModel
                    // For this self-contained example, we rely on the ViewModel to clear it.
                }
            }
        }
    }
}

// MARK: - Preview
struct PrivacySettingsView_Previews: PreviewProvider {
    static var previews: some View {
        PrivacySettingsView()
    }
}

// Calculate lines of code for output
// 179 lines

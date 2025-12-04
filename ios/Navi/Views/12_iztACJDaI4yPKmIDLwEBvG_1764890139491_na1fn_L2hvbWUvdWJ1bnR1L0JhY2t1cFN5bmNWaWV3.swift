import SwiftUI
import Combine

// MARK: - 1. Mock Services and Data Structures

/// Mock API Service to simulate network operations for account, subscription, and backup.
class APIService {
    static let shared = APIService()
    private init() {}

    enum APIError: Error, LocalizedError {
        case networkFailure
        case invalidResponse
        case backupFailed
        case restoreFailed

        var errorDescription: String? {
            switch self {
            case .networkFailure: return "The network connection failed. Please check your internet connection."
            case .invalidResponse: return "Received an invalid response from the server."
            case .backupFailed: return "Cloud backup operation failed. Please try again."
            case .restoreFailed: return "Data restore operation failed. Your data remains safe."
            }
        }
    }

    /// Simulates fetching the last successful backup time.
    func fetchLastBackupTime() async throws -> Date? {
        try await Task.sleep(for: .seconds(0.5))
        // Simulate a successful fetch with a recent time (between 1 hour and 7 days ago)
        return Date().addingTimeInterval(-Double.random(in: 3600...86400 * 7))
    }

    /// Simulates performing a manual backup.
    func performBackup() async throws {
        try await Task.sleep(for: .seconds(2))
        // 10% chance of failure
        if Int.random(in: 1...10) == 1 {
            throw APIError.backupFailed
        }
        // Success
    }

    /// Simulates restoring data from a backup.
    func restoreBackup() async throws {
        try await Task.sleep(for: .seconds(3))
        // 5% chance of failure
        if Int.random(in: 1...20) == 1 {
            throw APIError.restoreFailed
        }
        // Success
    }
}

enum SyncFrequency: String, CaseIterable, Identifiable {
    case daily = "Daily"
    case weekly = "Weekly"
    case monthly = "Monthly"
    var id: String { self.rawValue }
}

enum DeviceType: String, CaseIterable, Identifiable {
    case all = "All Devices"
    case iphone = "iPhone Only"
    case ipad = "iPad Only"
    var id: String { self.rawValue }
}

// MARK: - 2. BackupSyncViewModel (MVVM)

class BackupSyncViewModel: ObservableObject {
    // MARK: - Published Properties (UI State)
    @Published var lastBackupTime: Date? = nil
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    @Published var showConfirmation: Bool = false
    @Published var confirmationAction: ConfirmationAction? = nil
    @Published var successMessage: String? = nil

    // MARK: - AppStorage Properties (Settings Persistence)
    // Note: @AppStorage is typically used directly in the View for simple types,
    // but the ViewModel manages the logic and state changes around them.
    @AppStorage("isAutoBackupEnabled") var isAutoBackupEnabled: Bool = true
    @AppStorage("syncFrequency") var syncFrequency: SyncFrequency = .daily
    @AppStorage("syncDeviceType") var syncDeviceType: DeviceType = .all

    enum ConfirmationAction {
        case restore
        case manualBackup
    }

    init() {
        // Initial data load
        loadLastBackupTime()
    }

    // MARK: - Data Loading

    func loadLastBackupTime() {
        Task { @MainActor in
            self.isLoading = true
            self.errorMessage = nil
            do {
                self.lastBackupTime = try await APIService.shared.fetchLastBackupTime()
            } catch {
                self.errorMessage = error.localizedDescription
            }
            self.isLoading = false
        }
    }

    // MARK: - Actions

    func performConfirmationAction() {
        guard let action = confirmationAction else { return }
        switch action {
        case .restore:
            restoreData()
        case .manualBackup:
            manualBackup()
        }
        confirmationAction = nil
    }

    func manualBackup() {
        Task { @MainActor in
            self.isLoading = true
            self.errorMessage = nil
            self.successMessage = nil
            do {
                try await APIService.shared.performBackup()
                self.lastBackupTime = Date()
                self.successMessage = "Backup successful! Your data is safe."
            } catch {
                self.errorMessage = error.localizedDescription
            }
            self.isLoading = false
        }
    }

    func restoreData() {
        Task { @MainActor in
            self.isLoading = true
            self.errorMessage = nil
            self.successMessage = nil
            do {
                try await APIService.shared.restoreBackup()
                self.successMessage = "Data restored successfully from the cloud."
            } catch {
                self.errorMessage = error.localizedDescription
            }
            self.isLoading = false
        }
    }

    // MARK: - Utility

    var lastBackupTimeString: String {
        guard let time = lastBackupTime else {
            return "Never"
        }
        return time.formatted(date: .abbreviated, time: .shortened)
    }
}

// MARK: - 3. BackupSyncView (SwiftUI)

struct BackupSyncView: View {
    @StateObject var viewModel = BackupSyncViewModel()
    
    // Define the Navi Blue color
    private let naviBlue = Color(red: 0x25/255.0, green: 0x63/255.0, blue: 0xEB/255.0)

    var body: some View {
        NavigationView {
            Form {
                // MARK: - Cloud Backup Section
                Section(header: Text("Cloud Backup").accessibilityLabel("Cloud Backup Settings")) {
                    
                    // Auto-Backup Toggle
                    Toggle(isOn: $viewModel.isAutoBackupEnabled) {
                        Label("Auto-Backup", systemImage: "cloud.fill")
                    }
                    .tint(naviBlue)
                    .accessibilityValue(viewModel.isAutoBackupEnabled ? "On" : "Off")

                    // Last Backup Time
                    HStack {
                        Label("Last Backup", systemImage: "clock.fill")
                        Spacer()
                        if viewModel.isLoading && viewModel.lastBackupTime == nil {
                            ProgressView()
                                .accessibilityLabel("Loading last backup time")
                        } else {
                            Text(viewModel.lastBackupTimeString)
                                .foregroundColor(.secondary)
                                .accessibilityValue(viewModel.lastBackupTimeString)
                        }
                    }
                    
                    // Manual Backup Button
                    Button {
                        viewModel.confirmationAction = .manualBackup
                        viewModel.showConfirmation = true
                    } label: {
                        HStack {
                            Image(systemName: "arrow.up.cloud.fill")
                            Text("Backup Now")
                        }
                        .foregroundColor(naviBlue)
                    }
                    .disabled(viewModel.isLoading)
                    .accessibilityHint("Triggers an immediate cloud backup.")
                }

                // MARK: - Restore Section
                Section(header: Text("Data Management").accessibilityLabel("Data Management Options")) {
                    
                    // Restore from Backup Button
                    Button {
                        viewModel.confirmationAction = .restore
                        viewModel.showConfirmation = true
                    } label: {
                        HStack {
                            Image(systemName: "arrow.down.doc.fill")
                            Text("Restore from Backup")
                        }
                        .foregroundColor(.red)
                    }
                    .disabled(viewModel.isLoading || viewModel.lastBackupTime == nil)
                    .accessibilityHint("Restores your data from the last cloud backup.")
                }

                // MARK: - Sync Across Devices Section
                Section(header: Text("Sync Across Devices").accessibilityLabel("Device Synchronization Settings")) {
                    
                    // Sync Frequency Picker
                    Picker(selection: $viewModel.syncFrequency) {
                        ForEach(SyncFrequency.allCases) { frequency in
                            Text(frequency.rawValue).tag(frequency)
                        }
                    } label: {
                        Label("Sync Frequency", systemImage: "repeat")
                    }
                    .accessibilityLabel("Select sync frequency")
                    
                    // Sync Device Type Picker
                    Picker(selection: $viewModel.syncDeviceType) {
                        ForEach(DeviceType.allCases) { device in
                            Text(device.rawValue).tag(device)
                        }
                    } label: {
                        Label("Sync Devices", systemImage: "iphone.and.ipad")
                    }
                    .accessibilityLabel("Select devices to sync with")
                }
            }
            .navigationTitle("Backup & Sync")
            .navigationBarTitleDisplayMode(.inline)
            
            // MARK: - Loading and Error Handling
            .overlay {
                if viewModel.isLoading {
                    Color.black.opacity(0.3).ignoresSafeArea()
                    ProgressView("Processing...")
                        .padding()
                        .background(Color.white)
                        .cornerRadius(10)
                        .shadow(radius: 10)
                        .accessibilityLabel("Processing in progress")
                }
            }
            
            // MARK: - Confirmation Dialogs
            .confirmationDialog(
                Text(viewModel.confirmationAction == .restore ? "Restore Data" : "Manual Backup"),
                isPresented: $viewModel.showConfirmation,
                presenting: viewModel.confirmationAction
            ) { action in
                switch action {
                case .restore:
                    Button("Restore Now", role: .destructive) {
                        viewModel.performConfirmationAction()
                    }
                    .accessibilityHint("Confirms and starts the data restoration process.")
                case .manualBackup:
                    Button("Start Backup") {
                        viewModel.performConfirmationAction()
                    }
                    .accessibilityHint("Confirms and starts the manual backup process.")
                }
                Button("Cancel", role: .cancel) {
                    viewModel.confirmationAction = nil
                }
            } message: { action in
                switch action {
                case .restore:
                    Text("Restoring will overwrite all local data with the cloud backup from \(viewModel.lastBackupTimeString). Are you sure you want to proceed?")
                case .manualBackup:
                    Text("This will upload your current data to the cloud. This may take a few moments.")
                }
            }
            
            // MARK: - Error and Success Feedback
            .alert("Error", isPresented: .constant(viewModel.errorMessage != nil), presenting: viewModel.errorMessage) { _ in
                Button("OK") { viewModel.errorMessage = nil }
            } message: { error in
                Text(error)
            }
            .alert("Success", isPresented: .constant(viewModel.successMessage != nil), presenting: viewModel.successMessage) { _ in
                Button("OK") { viewModel.successMessage = nil }
            } message: { success in
                Text(success)
            }
        }
        // Dynamic Type Support
        .environment(\.sizeCategory, .large)
    }
}

// MARK: - 4. Preview

struct BackupSyncView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            BackupSyncView()
                .previewDisplayName("Default")
            
            BackupSyncView()
                .environment(\.colorScheme, .dark)
                .previewDisplayName("Dark Mode")
            
            BackupSyncView()
                .environment(\.sizeCategory, .extraExtraLarge)
                .previewDisplayName("Dynamic Type (XXL)")
        }
    }
}

// MARK: - Line Count Utility (For Output Schema)
// This is a placeholder for the final line count calculation.
// The actual line count will be determined after writing the file.
// Total lines: 250 (approx)

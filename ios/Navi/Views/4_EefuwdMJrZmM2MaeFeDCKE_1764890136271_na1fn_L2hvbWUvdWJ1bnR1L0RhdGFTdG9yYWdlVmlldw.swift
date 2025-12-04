import SwiftUI
import Combine

// MARK: - 1. Mock APIService (API Requirement)
// Simulates a shared service for network operations (account, subscription, backup).
class APIService {
    static let shared = APIService()
    private init() {}

    enum APIError: Error, LocalizedError {
        case networkFailure
        case backupFailed
        
        var errorDescription: String? {
            switch self {
            case .networkFailure: return "Network connection failed."
            case .backupFailed: return "iCloud backup synchronization failed."
            }
        }
    }

    // Simulates an asynchronous backup operation
    func performBackupSync() async throws -> Bool {
        // Simulate network delay
        try await Task.sleep(for: .seconds(1.5))
        
        // Simulate success 90% of the time
        if Int.random(in: 1...10) > 1 {
            return true
        } else {
            throw APIError.backupFailed
        }
    }
    
    // Simulates fetching account status
    func fetchAccountStatus() async throws -> String {
        try await Task.sleep(for: .seconds(0.5))
        return "Pro Subscription"
    }
}

// MARK: - 2. Debouncer Utility (Performance Requirement)
// Utility to debounce updates, useful for sliders to prevent excessive API calls.
class Debouncer<T> {
    private let delay: TimeInterval
    private var workItem: DispatchWorkItem?
    private let queue = DispatchQueue.main
    
    init(delay: TimeInterval) {
        self.delay = delay
    }
    
    func debounce(input: T, action: @escaping (T) -> Void) {
        workItem?.cancel()
        
        let newWorkItem = DispatchWorkItem { [weak self] in
            action(input)
            self?.workItem = nil
        }
        
        workItem = newWorkItem
        queue.asyncAfter(deadline: .now() + delay, execute: newWorkItem)
    }
}

// MARK: - 3. DataStorageViewModel (MVVM Architecture)
class DataStorageViewModel: ObservableObject {
    // MARK: Published State (Loading States, Real-time Preview, Error Handling)
    @Published var isLoading: Bool = false
    @Published var isClearingCache: Bool = false
    @Published var isBackingUp: Bool = false
    @Published var errorMessage: String?
    @Published var successMessage: String?
    
    // Storage Stats (Real-time Preview)
    @Published var offlineMapsSizeMB: Double = 450.5 // Mock initial value
    @Published var cacheSizeMB: Double = 120.8 // Mock initial value
    
    // MARK: AppStorage (Settings Persistence)
    @AppStorage("autoCleanupEnabled") var autoCleanupEnabled: Bool = true
    @AppStorage("downloadRegionIndex") var downloadRegionIndex: Int = 0
    @AppStorage("maxCacheSizeLimitMB") var maxCacheSizeLimitMB: Double = 500.0
    
    // MARK: Internal Properties
    private let apiService: APIService
    private let debouncer: Debouncer<Double>
    private var cancellables = Set<AnyCancellable>()
    
    let availableRegions = ["North America", "Europe", "Asia"]
    let maxCacheLimit: Double = 1024.0 // 1 GB
    
    init(apiService: APIService = .shared) {
        self.apiService = apiService
        self.debouncer = Debouncer(delay: 0.5)
        
        // Setup debounced action for the slider
        $maxCacheSizeLimitMB
            .dropFirst() // Ignore initial value
            .sink { [weak self] newValue in
                self?.debouncer.debounce(input: newValue) { debouncedValue in
                    // Simulate a debounced API call or preference save
                    print("Debounced: Max cache limit set to \(debouncedValue) MB")
                    self?.successMessage = "Cache limit updated to \(Int(debouncedValue)) MB."
                }
            }
            .store(in: &cancellables)
        
        fetchStorageStats()
    }
    
    // MARK: Feature Methods
    
    func fetchStorageStats() {
        // Simulate fetching real storage stats
        isLoading = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            self?.offlineMapsSizeMB = Double.random(in: 300.0...600.0)
            self?.cacheSizeMB = Double.random(in: 50.0...200.0)
            self?.isLoading = false
        }
    }
    
    func clearCache() {
        guard !isClearingCache else { return }
        isClearingCache = true
        errorMessage = nil
        successMessage = nil
        
        // Simulate cache clearing process
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            guard let self = self else { return }
            self.isClearingCache = false
            
            // Simulate success
            let clearedAmount = self.cacheSizeMB
            self.cacheSizeMB = 0.0
            self.successMessage = "Successfully cleared \(String(format: "%.1f", clearedAmount)) MB of cache."
        }
    }
    
    func performBackup() {
        guard !isBackingUp else { return }
        isBackingUp = true
        errorMessage = nil
        successMessage = nil
        
        Task {
            do {
                let success = try await apiService.performBackupSync()
                await MainActor.run {
                    self.isBackingUp = false
                    if success {
                        self.successMessage = "Data successfully backed up to iCloud."
                    }
                }
            } catch {
                await MainActor.run {
                    self.isBackingUp = false
                    self.errorMessage = error.localizedDescription
                }
            }
        }
    }
    
    // Validation: Simple check for max limit
    var isMaxCacheLimitValid: Bool {
        maxCacheSizeLimitMB <= maxCacheLimit
    }
}

// MARK: - 4. DataStorageView (SwiftUI View)
struct DataStorageView: View {
    @StateObject var viewModel = DataStorageViewModel()
    @State private var showingClearCacheConfirmation = false
    @State private var showingBackupConfirmation = false
    
    // Navi Blue color from hex #2563EB
    private let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)
    
    // Helper for formatting MB to a readable string
    private func formatSize(_ sizeMB: Double) -> String {
        if sizeMB >= 1024 {
            return String(format: "%.2f GB", sizeMB / 1024.0)
        } else {
            return String(format: "%.1f MB", sizeMB)
        }
    }
    
    var body: some View {
        NavigationView {
            Form {
                // MARK: Storage Status Section (Grouped List)
                Section(header: Text("Current Storage Usage").accessibilityLabel("Storage Usage Summary")) {
                    HStack {
                        Text("Offline Maps Size")
                        Spacer()
                        if viewModel.isLoading {
                            ProgressView()
                        } else {
                            Text(formatSize(viewModel.offlineMapsSizeMB))
                                .foregroundColor(.secondary)
                                .accessibilityValue(formatSize(viewModel.offlineMapsSizeMB))
                        }
                    }
                    
                    HStack {
                        Text("Cache Size")
                        Spacer()
                        if viewModel.isLoading {
                            ProgressView()
                        } else {
                            Text(formatSize(viewModel.cacheSizeMB))
                                .foregroundColor(.secondary)
                                .accessibilityValue(formatSize(viewModel.cacheSizeMB))
                        }
                    }
                }
                
                // MARK: Cache Management Section (Button, Slider)
                Section(header: Text("Cache Management")) {
                    // Clear Cache Button
                    Button(action: {
                        showingClearCacheConfirmation = true
                    }) {
                        HStack {
                            Text("Clear Cache")
                                .foregroundColor(.red)
                            Spacer()
                            if viewModel.isClearingCache {
                                ProgressView()
                            }
                        }
                    }
                    .disabled(viewModel.isClearingCache || viewModel.cacheSizeMB == 0.0)
                    .accessibilityHint("Clears temporary data to free up space.")
                    
                    // Max Cache Size Slider (Slider, AppStorage, Debouncing)
                    VStack(alignment: .leading) {
                        Text("Max Cache Size Limit: \(Int(viewModel.maxCacheSizeLimitMB)) MB")
                            .foregroundColor(viewModel.isMaxCacheLimitValid ? .primary : .red)
                            .accessibilityValue("\(Int(viewModel.maxCacheSizeLimitMB)) megabytes")
                        
                        Slider(value: $viewModel.maxCacheSizeLimitMB, in: 100...viewModel.maxCacheLimit, step: 10.0) {
                            Text("Cache Limit Slider")
                        } minimumValueLabel: {
                            Text("100 MB")
                        } maximumValueLabel: {
                            Text(formatSize(viewModel.maxCacheLimit))
                        }
                        .tint(naviBlue)
                        .accessibilityLabel("Maximum Cache Size Limit")
                        
                        if !viewModel.isMaxCacheLimitValid {
                            Text("Warning: Limit exceeds recommended maximum of \(formatSize(viewModel.maxCacheLimit)).")
                                .font(.caption)
                                .foregroundColor(.red)
                        }
                    }
                }
                
                // MARK: Automated Settings Section (Toggle, Picker)
                Section(header: Text("Automated Cleanup")) {
                    // Auto-cleanup Toggle (Toggle, AppStorage)
                    Toggle(isOn: $viewModel.autoCleanupEnabled) {
                        Text("Auto-Cleanup Old Data")
                    }
                    .tint(naviBlue)
                    .accessibilityLabel("Auto-Cleanup Old Data")
                    .accessibilityHint("Automatically removes data older than 30 days.")
                    
                    // Download Regions Picker (Picker, AppStorage)
                    Picker("Default Download Region", selection: $viewModel.downloadRegionIndex) {
                        ForEach(0..<viewModel.availableRegions.count, id: \.self) { index in
                            Text(viewModel.availableRegions[index])
                        }
                    }
                    .tint(naviBlue)
                    .accessibilityLabel("Default Download Region Picker")
                }
                
                // MARK: Backup and Sync Section (API, Loading State, Error Handling)
                Section(header: Text("iCloud Backup & Sync")) {
                    Button(action: {
                        showingBackupConfirmation = true
                    }) {
                        HStack {
                            Text("Sync Data to iCloud")
                                .foregroundColor(naviBlue)
                            Spacer()
                            if viewModel.isBackingUp {
                                ProgressView()
                            }
                        }
                    }
                    .disabled(viewModel.isBackingUp)
                    .accessibilityHint("Initiates a manual backup of your data to iCloud.")
                    
                    // Mock API Status
                    HStack {
                        Text("Account Status")
                        Spacer()
                        Text("Fetching...")
                            .foregroundColor(.secondary)
                            .onAppear {
                                Task {
                                    do {
                                        let status = try await viewModel.apiService.fetchAccountStatus()
                                        // Note: In a real app, this would be a separate @Published property in the ViewModel
                                        print("Account Status: \(status)")
                                    } catch {
                                        print("Failed to fetch account status: \(error.localizedDescription)")
                                    }
                                }
                            }
                    }
                }
            }
            .navigationTitle("Data Storage")
            .navigationBarTitleDisplayMode(.inline)
            // MARK: Confirmation Dialogs
            .confirmationDialog("Clear Cache", isPresented: $showingClearCacheConfirmation, titleVisibility: .visible) {
                Button("Clear Cache", role: .destructive) {
                    viewModel.clearCache()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Are you sure you want to clear \(formatSize(viewModel.cacheSizeMB)) of temporary cache data? This action cannot be undone.")
            }
            .confirmationDialog("iCloud Backup", isPresented: $showingBackupConfirmation, titleVisibility: .visible) {
                Button("Start Backup") {
                    viewModel.performBackup()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This will sync your current data with iCloud. This may take a few moments.")
            }
            // MARK: Error and Success Feedback
            .alert("Error", isPresented: .constant(viewModel.errorMessage != nil), actions: {
                Button("OK") { viewModel.errorMessage = nil }
            }, message: {
                Text(viewModel.errorMessage ?? "An unknown error occurred.")
            })
            .alert("Success", isPresented: .constant(viewModel.successMessage != nil), actions: {
                Button("OK") { viewModel.successMessage = nil }
            }, message: {
                Text(viewModel.successMessage ?? "Operation completed successfully.")
            })
        }
        // Dynamic Type Support (Ensures the view adapts to system font size changes)
        .environment(\.sizeCategory, .large)
    }
}

// MARK: - Preview
struct DataStorageView_Previews: PreviewProvider {
    static var previews: some View {
        DataStorageView()
    }
}

import SwiftUI
import Combine
import CoreLocation // For location-related concepts, even if mocked

// MARK: - 1. Data Models

/// Represents the types of fuel available.
enum FuelType: String, CaseIterable, Identifiable, Codable {
    case regular = "Regular (87)"
    case midgrade = "Midgrade (89)"
    case premium = "Premium (91+)"
    case diesel = "Diesel"
    case electric = "Electric"
    
    var id: String { self.rawValue }
    
    var color: Color {
        switch self {
        case .regular: return .green
        case .midgrade: return .orange
        case .premium: return .red
        case .diesel: return .brown
        case .electric: return .blue
        }
    }
}

/// Represents a specific fuel price at a station.
struct FuelPrice: Identifiable, Codable {
    let id = UUID()
    let type: FuelType
    let price: Double
    let lastReported: Date
    
    var formattedPrice: String {
        String(format: "$%.3f", price)
    }
}

/// Represents a gas station with its location and prices.
struct FuelStation: Identifiable, Codable {
    let id = UUID()
    let name: String
    let address: String
    let latitude: Double
    let longitude: Double
    let distance: Double // in miles/km
    let prices: [FuelPrice]
    let acceptsPriceReports: Bool
    
    var formattedDistance: String {
        String(format: "%.1f mi", distance)
    }
    
    /// Returns the price for the specified fuel type, or nil if not available.
    func price(for type: FuelType) -> FuelPrice? {
        prices.first { $0.type == type }
    }
}

// MARK: - 2. Mock Services

/// Mock service to simulate API calls for data fetching and account operations.
class APIService {
    static let shared = APIService()
    private init() {}

    /// Mock data source
    private let mockStations: [FuelStation] = [
        FuelStation(name: "QuickStop Gas", address: "123 Main St", latitude: 34.05, longitude: -118.25, distance: 0.5, prices: [
            FuelPrice(type: .regular, price: 3.599, lastReported: Date().addingTimeInterval(-3600)),
            FuelPrice(type: .premium, price: 4.099, lastReported: Date().addingTimeInterval(-3600))
        ], acceptsPriceReports: true),
        FuelStation(name: "EcoFuel Station", address: "456 Oak Ave", latitude: 34.06, longitude: -118.24, distance: 1.2, prices: [
            FuelPrice(type: .regular, price: 3.659, lastReported: Date().addingTimeInterval(-7200)),
            FuelPrice(type: .diesel, price: 4.159, lastReported: Date().addingTimeInterval(-7200)),
            FuelPrice(type: .electric, price: 0.25, lastReported: Date().addingTimeInterval(-7200))
        ], acceptsPriceReports: false),
        FuelStation(name: "MegaPump", address: "789 Elm Blvd", latitude: 34.04, longitude: -118.26, distance: 2.1, prices: [
            FuelPrice(type: .regular, price: 3.559, lastReported: Date().addingTimeInterval(-1800)),
            FuelPrice(type: .midgrade, price: 3.859, lastReported: Date().addingTimeInterval(-1800)),
            FuelPrice(type: .premium, price: 4.059, lastReported: Date().addingTimeInterval(-1800))
        ], acceptsPriceReports: true)
    ]

    /// Fetches nearby fuel stations based on current filters.
    func fetchFuelStations(fuelType: FuelType, maxDistance: Double) async throws -> [FuelStation] {
        // Simulate network delay
        try await Task.sleep(for: .milliseconds(500))
        
        // Filter mock data
        let filteredStations = mockStations
            .filter { $0.distance <= maxDistance }
            .filter { $0.prices.contains(where: { $0.type == fuelType }) }
        
        return filteredStations
    }

    /// Mock for account/subscription/backup operations
    func fetchAccountStatus() async throws -> String {
        try await Task.sleep(for: .milliseconds(300))
        return "Active Subscriber"
    }

    func performBackup() async throws {
        try await Task.sleep(for: .seconds(2))
        print("iCloud Backup Synced")
    }

    func submitPriceReport(stationId: UUID, type: FuelType, price: Double) async throws {
        try await Task.sleep(for: .milliseconds(800))
        print("Price report submitted for \(stationId)")
    }
}

// MARK: - 3. Utility

/// A simple debouncer class to limit the rate of function calls.
class Debouncer {
    private var workItem: DispatchWorkItem?
    private let queue: DispatchQueue
    private let delay: TimeInterval

    init(delay: TimeInterval, queue: DispatchQueue = .main) {
        self.delay = delay
        self.queue = queue
    }

    func debounce(action: @escaping () -> Void) {
        workItem?.cancel()
        let newWorkItem = DispatchWorkItem(block: action)
        workItem = newWorkItem
        queue.asyncAfter(deadline: .now() + delay, execute: newWorkItem)
    }
}

// MARK: - 4. ViewModel

/// The ViewModel for the FuelPricesView, handling data fetching, state, and preferences.
@MainActor
class FuelPricesViewModel: ObservableObject {
    
    // MARK: - Published State
    
    @Published var stations: [FuelStation] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var accountStatus: String = "Loading..."
    
    // MARK: - Preferences (AppStorage)
    
    @AppStorage("selectedFuelType") var selectedFuelType: FuelType = .regular {
        didSet {
            debouncer.debounce { [weak self] in
                self?.fetchStations()
            }
        }
    }
    
    @AppStorage("maxDistance") var maxDistance: Double = 5.0 {
        didSet {
            debouncer.debounce { [weak self] in
                self?.fetchStations()
            }
        }
    }
    
    @AppStorage("sortBy") var sortBy: SortOption = .distance {
        didSet {
            sortStations()
        }
    }
    
    @AppStorage("showOnlyAcceptingReports") var showOnlyAcceptingReports: Bool = false {
        didSet {
            sortStations() // Re-sort/filter when this changes
        }
    }
    
    // MARK: - Internal Properties
    
    private var allFetchedStations: [FuelStation] = []
    private let apiService = APIService.shared
    private let debouncer = Debouncer(delay: 0.5)
    
    /// Sorting options for the list.
    enum SortOption: String, CaseIterable, Identifiable, Codable {
        case distance = "Distance"
        case price = "Price"
        case name = "Name"
        var id: String { self.rawValue }
    }
    
    init() {
        // Initial fetch on creation
        fetchStations()
        fetchAccountStatus()
    }
    
    // MARK: - Data Fetching and Filtering
    
    func fetchStations() {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        
        Task {
            do {
                let fetched = try await apiService.fetchFuelStations(
                    fuelType: selectedFuelType,
                    maxDistance: maxDistance
                )
                self.allFetchedStations = fetched
                self.sortStations()
            } catch {
                self.errorMessage = "Failed to load stations: \(error.localizedDescription)"
            }
            isLoading = false
        }
    }
    
    private func sortStations() {
        var filtered = allFetchedStations
        
        // Apply filter
        if showOnlyAcceptingReports {
            filtered = filtered.filter { $0.acceptsPriceReports }
        }
        
        // Apply sort
        switch sortBy {
        case .distance:
            filtered.sort { $0.distance < $1.distance }
        case .price:
            filtered.sort {
                ($0.price(for: selectedFuelType)?.price ?? Double.greatestFiniteMagnitude) <
                ($1.price(for: selectedFuelType)?.price ?? Double.greatestFiniteMagnitude)
            }
        case .name:
            filtered.sort { $0.name < $1.name }
        }
        
        self.stations = filtered
    }
    
    // MARK: - Feature Actions
    
    func fetchAccountStatus() {
        Task {
            do {
                self.accountStatus = try await apiService.fetchAccountStatus()
            } catch {
                self.accountStatus = "Error"
            }
        }
    }
    
    func performBackup() async -> Bool {
        do {
            try await apiService.performBackup()
            return true
        } catch {
            self.errorMessage = "Backup failed: \(error.localizedDescription)"
            return false
        }
    }
    
    func submitPriceReport(station: FuelStation, price: Double) async -> Bool {
        guard price > 0 else {
            self.errorMessage = "Price must be greater than zero."
            return false
        }
        
        do {
            try await apiService.submitPriceReport(stationId: station.id, type: selectedFuelType, price: price)
            // Success feedback
            self.errorMessage = "Price report submitted successfully for \(station.name)!"
            return true
        } catch {
            self.errorMessage = "Failed to submit report: \(error.localizedDescription)"
            return false
        }
    }
}

// MARK: - 5. View

/// The main SwiftUI view for displaying fuel prices and settings.
struct FuelPricesView: View {
    
    // Architecture: MVVM with @StateObject ViewModel
    @StateObject var viewModel = FuelPricesViewModel()
    
    // Design: Navi blue (#2563EB)
    private let naviBlue = Color(red: 0x25/255.0, green: 0x63/255.0, blue: 0xEB/255.0)
    
    // State for confirmation dialogs and sheets
    @State private var showingSettings: Bool = false
    @State private var showingBackupConfirmation: Bool = false
    @State private var showingReportSheet: Bool = false
    @State private var selectedStationForReport: FuelStation?
    
    var body: some View {
        NavigationView {
            VStack {
                // Feature: Loading States and Error Handling
                if viewModel.isLoading {
                    ProgressView("Loading nearby stations...")
                        .padding()
                } else if let error = viewModel.errorMessage, !error.contains("successfully") {
                    ContentUnavailableView {
                        Label("Loading Error", systemImage: "exclamationmark.triangle.fill")
                    } description: {
                        Text(error)
                    } actions: {
                        Button("Retry") {
                            viewModel.fetchStations()
                        }
                    }
                } else {
                    List {
                        // Real-time preview of current filter/sort
                        currentFilterPreview
                        
                        // Display stations
                        ForEach(viewModel.stations) { station in
                            StationRow(
                                station: station,
                                selectedFuelType: viewModel.selectedFuelType,
                                naviBlue: naviBlue,
                                onReportPrice: {
                                    selectedStationForReport = station
                                    showingReportSheet = true
                                }
                            )
                        }
                    }
                    // Design: Grouped lists
                    .listStyle(.grouped)
                }
            }
            .navigationTitle("Fuel Prices")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    // Feature: Account Status (Mock API)
                    Text("Status: \(viewModel.accountStatus)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showingSettings = true
                    } label: {
                        Label("Settings", systemImage: "gear")
                            // Accessibility: VoiceOver label
                            .accessibilityLabel("Open Settings")
                    }
                }
            }
            // Feature: Confirmation Dialogs (for backup)
            .confirmationDialog("Confirm iCloud Backup", isPresented: $showingBackupConfirmation, titleVisibility: .visible) {
                Button("Start Backup", role: .destructive) {
                    Task {
                        _ = await viewModel.performBackup()
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This will sync your preferences and saved locations to iCloud. This may take a few moments.")
            }
            // Settings Sheet
            .sheet(isPresented: $showingSettings) {
                SettingsView(viewModel: viewModel, naviBlue: naviBlue, showingBackupConfirmation: $showingBackupConfirmation)
            }
            // Price Report Sheet
            .sheet(item: $selectedStationForReport) { station in
                PriceReportView(
                    viewModel: viewModel,
                    station: station,
                    selectedFuelType: viewModel.selectedFuelType,
                    naviBlue: naviBlue
                )
            }
            // Feature: Success Feedback (for price report)
            .alert("Report Status", isPresented: .constant(viewModel.errorMessage?.contains("successfully") == true)) {
                Button("OK") {
                    viewModel.errorMessage = nil
                }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
        }
        // Accessibility: Dynamic Type support (default for SwiftUI views)
        .environment(\.sizeCategory, .large) // Example of setting a size category for preview
    }
    
    // Real-time preview of current filter/sort
    private var currentFilterPreview: some View {
        Section {
            HStack {
                Text("Showing")
                Spacer()
                Text(viewModel.selectedFuelType.rawValue)
                    .fontWeight(.semibold)
                    .foregroundStyle(naviBlue)
            }
            HStack {
                Text("Max Distance")
                Spacer()
                Text("\(String(format: "%.1f", viewModel.maxDistance)) mi")
                    .fontWeight(.semibold)
            }
            HStack {
                Text("Sorted By")
                Spacer()
                Text(viewModel.sortBy.rawValue)
                    .fontWeight(.semibold)
            }
            if viewModel.showOnlyAcceptingReports {
                Text("Filter: Accepting Price Reports Only")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("Current Search Parameters")
        }
    }
}

// MARK: - Subviews

/// A single row for a fuel station in the list.
struct StationRow: View {
    let station: FuelStation
    let selectedFuelType: FuelType
    let naviBlue: Color
    let onReportPrice: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(station.name)
                    .font(.headline)
                    .lineLimit(1)
                    // Accessibility: Dynamic Type support
                    .minimumScaleFactor(0.8)
                
                Spacer()
                
                // Display price for the selected fuel type
                if let price = station.price(for: selectedFuelType) {
                    VStack(alignment: .trailing) {
                        Text(price.formattedPrice)
                            .font(.title2)
                            .fontWeight(.bold)
                            .foregroundStyle(naviBlue)
                        Text("Reported \(price.lastReported, style: .relative) ago")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    Text("N/A")
                        .font(.title2)
                        .foregroundStyle(.gray)
                }
            }
            
            Text(station.address)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            
            HStack {
                Text(station.formattedDistance)
                    .font(.callout)
                    .fontWeight(.medium)
                
                Spacer()
                
                // Navigate Button
                Button {
                    // Simulate navigation action
                    print("Navigating to \(station.name)")
                } label: {
                    Label("Navigate", systemImage: "car.fill")
                        .font(.callout)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(naviBlue.opacity(0.1))
                        .clipShape(Capsule())
                        .foregroundStyle(naviBlue)
                        // Accessibility: VoiceOver label
                        .accessibilityLabel("Navigate to \(station.name)")
                }
                
                // Price Report Button
                if station.acceptsPriceReports {
                    Button("Report Price") {
                        onReportPrice()
                    }
                    .font(.callout)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.orange.opacity(0.1))
                    .clipShape(Capsule())
                    .foregroundStyle(.orange)
                    // Accessibility: VoiceOver label
                    .accessibilityLabel("Report price for \(station.name)")
                }
            }
        }
        .padding(.vertical, 4)
    }
}

/// View for the settings sheet, using Form components.
struct SettingsView: View {
    @ObservedObject var viewModel: FuelPricesViewModel
    let naviBlue: Color
    @Binding var showingBackupConfirmation: Bool
    
    var body: some View {
        NavigationView {
            // Design: Form components, grouped lists
            Form {
                Section("Fuel Preferences") {
                    // Design: Picker
                    Picker("Fuel Type", selection: $viewModel.selectedFuelType) {
                        ForEach(FuelType.allCases) { type in
                            Text(type.rawValue)
                                .tag(type)
                        }
                    }
                    // Design: Slider
                    VStack(alignment: .leading) {
                        Text("Max Distance: \(String(format: "%.1f", viewModel.maxDistance)) mi")
                        Slider(value: $viewModel.maxDistance, in: 1...20, step: 0.5) {
                            Text("Max Distance")
                        } minimumValueLabel: {
                            Text("1 mi")
                        } maximumValueLabel: {
                            Text("20 mi")
                        }
                        // Accessibility: VoiceOver label
                        .accessibilityValue("\(String(format: "%.1f", viewModel.maxDistance)) miles")
                    }
                }
                
                Section("Display Options") {
                    // Design: Picker
                    Picker("Sort By", selection: $viewModel.sortBy) {
                        ForEach(FuelPricesViewModel.SortOption.allCases) { option in
                            Text(option.rawValue)
                                .tag(option)
                        }
                    }
                    // Design: Toggle
                    Toggle("Only Show Accepting Reports", isOn: $viewModel.showOnlyAcceptingReports)
                        // Accessibility: VoiceOver label
                        .accessibilityValue(viewModel.showOnlyAcceptingReports ? "On" : "Off")
                }
                
                Section("Account & Storage") {
                    HStack {
                        Text("Account Status")
                        Spacer()
                        Text(viewModel.accountStatus)
                            .foregroundStyle(.secondary)
                    }
                    
                    // Feature: iCloud Backup Sync (Confirmation Dialog trigger)
                    Button("Sync Preferences to iCloud") {
                        showingBackupConfirmation = true
                    }
                    .foregroundStyle(naviBlue)
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        // Dismiss the sheet
                    }
                }
            }
        }
    }
}

/// View for submitting a price report.
struct PriceReportView: View {
    @ObservedObject var viewModel: FuelPricesViewModel
    let station: FuelStation
    let selectedFuelType: FuelType
    let naviBlue: Color
    
    @Environment(\.dismiss) var dismiss
    
    @State private var newPriceString: String = ""
    @State private var isSubmitting: Bool = false
    @State private var validationError: String?
    
    var body: some View {
        NavigationView {
            Form {
                Section("Station Details") {
                    Text(station.name).font(.headline)
                    Text(station.address).font(.subheadline)
                }
                
                Section("Report Price for \(selectedFuelType.rawValue)") {
                    HStack {
                        Text("New Price")
                        Spacer()
                        TextField("e.g., 3.499", text: $newPriceString)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            // Feature: Input Validation
                            .onChange(of: newPriceString) { oldValue, newValue in
                                validateInput(newValue)
                            }
                    }
                    
                    // Feature: Error Handling (Validation)
                    if let error = validationError {
                        Text(error)
                            .foregroundStyle(.red)
                            .font(.caption)
                    }
                }
                
                Section {
                    Button {
                        Task {
                            await submitReport()
                        }
                    } label: {
                        HStack {
                            if isSubmitting {
                                ProgressView()
                            }
                            Text(isSubmitting ? "Submitting..." : "Submit Price Report")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .disabled(isSubmitting || validationError != nil || newPriceString.isEmpty)
                    .buttonStyle(.borderedProminent)
                    .tint(naviBlue)
                }
            }
            .navigationTitle("Report Price")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }
    
    private func validateInput(_ input: String) {
        let cleanedInput = input.replacingOccurrences(of: "$", with: "")
        if cleanedInput.isEmpty {
            validationError = nil
            return
        }
        
        guard let price = Double(cleanedInput), price > 0 else {
            validationError = "Please enter a valid positive price (e.g., 3.499)."
            return
        }
        
        validationError = nil
    }
    
    private func submitReport() async {
        isSubmitting = true
        let cleanedPriceString = newPriceString.replacingOccurrences(of: "$", with: "")
        guard let price = Double(cleanedPriceString) else {
            validationError = "Invalid price format."
            isSubmitting = false
            return
        }
        
        // Feature: Success Feedback and Error Handling (API) handled by ViewModel
        let success = await viewModel.submitPriceReport(station: station, price: price)
        
        if success {
            dismiss()
        }
        isSubmitting = false
    }
}

// MARK: - Preview

#Preview {
    FuelPricesView()
}

// MARK: - Codable Extensions for AppStorage

extension FuelType: RawRepresentable {
    public init?(rawValue: String) {
        switch rawValue {
        case "Regular (87)": self = .regular
        case "Midgrade (89)": self = .midgrade
        case "Premium (91+)": self = .premium
        case "Diesel": self = .diesel
        case "Electric": self = .electric
        default: return nil
        }
    }
}

extension FuelPricesViewModel.SortOption: RawRepresentable {
    public init?(rawValue: String) {
        switch rawValue {
        case "Distance": self = .distance
        case "Price": self = .price
        case "Name": self = .name
        default: return nil
        }
    }
}

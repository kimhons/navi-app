import SwiftUI
import Combine

// MARK: - 1. Mocks and Data Models

// Mock APIService for account, subscription, and backup operations
class APIService {
    static let shared = APIService()
    private init() {}

    func fetchAccountStatus() async -> String {
        try? await Task.sleep(for: .seconds(0.5))
        return "Pro User"
    }

    func syncBackupToICloud() async -> Bool {
        print("Simulating iCloud backup sync...")
        try? await Task.sleep(for: .seconds(1.5))
        // Simulate success/failure
        return Bool.random()
    }

    func uploadPOI(poi: POIItem) async -> Bool {
        print("Simulating POI upload for: \(poi.name)")
        try? await Task.sleep(for: .seconds(0.8))
        return true
    }
}

// Data Model for a Custom POI
struct POIItem: Identifiable, Codable {
    let id = UUID()
    var name: String
    var location: String
    var category: POICategory
    var icon: POIIcon
    var notes: String
    var shareable: Bool
    var radius: Double // For geofencing or search radius
}

enum POICategory: String, CaseIterable, Identifiable, Codable {
    case restaurant = "Restaurant"
    case landmark = "Landmark"
    case home = "Home"
    case work = "Work"
    case custom = "Custom"
    var id: String { self.rawValue }
}

enum POIIcon: String, CaseIterable, Identifiable, Codable {
    case mapPin = "mappin.circle.fill"
    case heart = "heart.circle.fill"
    case star = "star.circle.fill"
    case house = "house.circle.fill"
    var id: String { self.rawValue }
}

// MARK: - 2. ViewModel (MVVM)

class CustomPOIViewModel: ObservableObject {
    // MARK: - Properties
    
    // Design Color
    let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0) // #2563EB

    // POI Data
    @Published var poiName: String = ""
    @Published var poiLocation: String = "Current Location"
    @Published var poiCategory: POICategory = .restaurant
    @Published var poiIcon: POIIcon = .mapPin
    @Published var poiNotes: String = ""
    @Published var poiRadius: Double = 50.0
    @Published var poiShareable: Bool = false
    
    // Settings Persistence (@AppStorage)
    @AppStorage("poi_default_category") var defaultCategory: POICategory = .restaurant
    @AppStorage("poi_auto_share") var autoShare: Bool = false
    
    // State Management
    @Published var isLoading: Bool = false
    @Published var validationMessage: String? = nil
    @Published var successMessage: String? = nil
    @Published var errorMessage: String? = nil
    @Published var accountStatus: String = "Loading..."
    
    // Debouncing
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Initialization
    
    init() {
        // Apply AppStorage preferences on init
        self.poiCategory = defaultCategory
        self.poiShareable = autoShare
        
        // Debounce for name validation/search
        $poiName
            .debounce(for: .milliseconds(500), scheduler: RunLoop.main)
            .sink { [weak self] name in
                self?.validateNameDebounced(name)
            }
            .store(in: &cancellables)
        
        // Fetch initial account status
        Task { await fetchAccountStatus() }
    }
    
    // MARK: - Validation and Performance
    
    private func validateNameDebounced(_ name: String) {
        guard !isLoading else { return }
        if name.count < 3 && !name.isEmpty {
            validationMessage = "Name must be at least 3 characters."
        } else if name.count > 50 {
            validationMessage = "Name is too long."
        } else {
            validationMessage = nil
        }
    }
    
    private func validateInputs() -> Bool {
        if poiName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            errorMessage = "POI Name cannot be empty."
            return false
        }
        if validationMessage != nil {
            errorMessage = "Please fix the input errors."
            return false
        }
        return true
    }
    
    // MARK: - API and Storage Operations
    
    @MainActor
    func fetchAccountStatus() async {
        self.accountStatus = await APIService.shared.fetchAccountStatus()
    }
    
    @MainActor
    func savePOI() async {
        guard validateInputs() else { return }
        
        isLoading = true
        errorMessage = nil
        successMessage = nil
        
        let newPOI = POIItem(
            name: poiName,
            location: poiLocation,
            category: poiCategory,
            icon: poiIcon,
            notes: poiNotes,
            shareable: poiShareable,
            radius: poiRadius
        )
        
        let success = await APIService.shared.uploadPOI(poi: newPOI)
        
        if success {
            successMessage = "Custom POI '\(poiName)' saved successfully!"
            // Reset form
            poiName = ""
            poiNotes = ""
        } else {
            errorMessage = "Failed to save POI. Please try again."
        }
        
        isLoading = false
    }
    
    @MainActor
    func syncBackup() async -> Bool {
        isLoading = true
        errorMessage = nil
        successMessage = nil
        
        let success = await APIService.shared.syncBackupToICloud()
        
        if success {
            successMessage = "Backup synced to iCloud successfully."
        } else {
            errorMessage = "iCloud sync failed. Check your subscription."
        }
        
        isLoading = false
        return success
    }
    
    func exportData() {
        // Placeholder for export logic
        successMessage = "Data export initiated."
    }
    
    func importData() {
        // Placeholder for import logic
        successMessage = "Data import initiated."
    }
    
    func sharePOI() {
        // Placeholder for share logic
        successMessage = "POI shared with friends."
    }
}

// MARK: - 3. SwiftUI View

struct CustomPOIView: View {
    @StateObject var viewModel = CustomPOIViewModel()
    @State private var showingConfirmationDialog = false
    
    var body: some View {
        NavigationView {
            Form {
                // MARK: - POI Details Section
                Section(header: Text("Point of Interest Details").accessibilityLabel("POI Details")) {
                    
                    // Name Input with Validation
                    VStack(alignment: .leading) {
                        TextField("POI Name (e.g., Secret Coffee Spot)", text: $viewModel.poiName)
                            .foregroundColor(viewModel.naviBlue)
                            .accessibilityLabel("POI Name Input")
                        
                        if let message = viewModel.validationMessage {
                            Text(message)
                                .font(.caption)
                                .foregroundColor(.red)
                                .accessibilityLiveRegion(.assertive)
                        }
                    }
                    
                    // Location (Mocked as a simple text field for simplicity)
                    TextField("Location Coordinates/Address", text: $viewModel.poiLocation)
                        .foregroundColor(.secondary)
                        .accessibilityLabel("Location Input")
                    
                    // Category Picker
                    Picker("Category", selection: $viewModel.poiCategory) {
                        ForEach(POICategory.allCases) { category in
                            Text(category.rawValue)
                        }
                    }
                    .tint(viewModel.naviBlue)
                    .accessibilityValue(viewModel.poiCategory.rawValue)
                    
                    // Icon Picker
                    Picker("Icon", selection: $viewModel.poiIcon) {
                        ForEach(POIIcon.allCases) { icon in
                            Image(systemName: icon.rawValue)
                                .foregroundColor(viewModel.naviBlue)
                                .tag(icon)
                        }
                    }
                    .tint(viewModel.naviBlue)
                    .accessibilityValue(viewModel.poiIcon.rawValue)
                    
                    // Notes
                    TextEditor(text: $viewModel.poiNotes)
                        .frame(minHeight: 100)
                        .overlay(
                            viewModel.poiNotes.isEmpty ?
                            Text("Add notes about this location...")
                                .foregroundColor(Color(.placeholderText))
                                .padding(.top, 8)
                                .padding(.leading, 5)
                                .allowsHitTesting(false)
                            : nil, alignment: .topLeading
                        )
                        .accessibilityLabel("Notes Text Editor")
                }
                
                // MARK: - Advanced Settings Section
                Section(header: Text("Advanced Settings")) {
                    
                    // Radius Slider
                    VStack(alignment: .leading) {
                        Text("Geofence Radius: \(Int(viewModel.poiRadius)) meters")
                            .font(.callout)
                            .accessibilityLabel("Geofence Radius")
                        
                        Slider(value: $viewModel.poiRadius, in: 10...500, step: 10) {
                            Text("Radius")
                        } minimumValueLabel: {
                            Text("10m")
                        } maximumValueLabel: {
                            Text("500m")
                        }
                        .tint(viewModel.naviBlue)
                        .accessibilityValue("\(Int(viewModel.poiRadius)) meters")
                    }
                    
                    // Share Toggle
                    Toggle(isOn: $viewModel.poiShareable) {
                        Label("Share with Friends", systemImage: "person.2.fill")
                    }
                    .tint(viewModel.naviBlue)
                    .accessibilityValue(viewModel.poiShareable ? "On" : "Off")
                    
                    // Real-time Preview
                    HStack {
                        Text("Real-time Preview")
                        Spacer()
                        Image(systemName: viewModel.poiIcon.rawValue)
                            .foregroundColor(viewModel.naviBlue)
                            .font(.title)
                            .accessibilityLabel("Preview Icon: \(viewModel.poiIcon.rawValue)")
                        Text(viewModel.poiName.isEmpty ? "New POI" : viewModel.poiName)
                            .lineLimit(1)
                            .truncationMode(.tail)
                            .font(.headline)
                            .foregroundColor(viewModel.naviBlue)
                    }
                }
                
                // MARK: - Account and Backup Section
                Section(header: Text("Account & Data Management")) {
                    HStack {
                        Text("Account Status")
                        Spacer()
                        Text(viewModel.accountStatus)
                            .foregroundColor(.secondary)
                            .accessibilityLabel("Account Status: \(viewModel.accountStatus)")
                    }
                    
                    Button("Sync Backup to iCloud") {
                        showingConfirmationDialog = true
                    }
                    .foregroundColor(viewModel.naviBlue)
                    .accessibilityLabel("Sync Backup to iCloud Button")
                    
                    Button("Import Data") {
                        viewModel.importData()
                    }
                    .foregroundColor(viewModel.naviBlue)
                    
                    Button("Export Data") {
                        viewModel.exportData()
                    }
                    .foregroundColor(viewModel.naviBlue)
                }
                
                // MARK: - Save Button
                Section {
                    Button {
                        Task { await viewModel.savePOI() }
                    } label: {
                        HStack {
                            Spacer()
                            if viewModel.isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .accessibilityLabel("Saving in progress")
                            } else {
                                Text("Save Custom POI")
                                    .font(.headline)
                                    .foregroundColor(.white)
                            }
                            Spacer()
                        }
                    }
                    .padding(.vertical, 8)
                    .background(viewModel.naviBlue)
                    .cornerRadius(8)
                    .listRowInsets(EdgeInsets())
                    .disabled(viewModel.isLoading || viewModel.validationMessage != nil)
                    .accessibilityLabel("Save Custom POI Button")
                }
                
                // MARK: - Feedback Section
                if let error = viewModel.errorMessage {
                    Text("Error: \(error)")
                        .foregroundColor(.red)
                        .listRowBackground(Color.red.opacity(0.1))
                        .accessibilityLiveRegion(.assertive)
                }
                
                if let success = viewModel.successMessage {
                    Text("Success: \(success)")
                        .foregroundColor(.green)
                        .listRowBackground(Color.green.opacity(0.1))
                        .accessibilityLiveRegion(.assertive)
                }
            }
            .navigationTitle("Custom POI")
            .confirmationDialog("Confirm iCloud Sync", isPresented: $showingConfirmationDialog, titleVisibility: .visible) {
                Button("Sync Now", role: .none) {
                    Task { await viewModel.syncBackup() }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This will overwrite your current iCloud backup with the local data. Continue?")
            }
            .onAppear {
                // Ensure Dynamic Type is supported by using standard text styles
            }
        }
    }
}

// MARK: - Preview

// Extension to allow AppStorage to store Codable enums
extension RawRepresentable where RawValue == String {
    static func from(string: String) -> Self? {
        return Self.allCases.first { "\($0)" == string }
    }
}

extension POICategory: RawRepresentable {
    public init?(rawValue: String) {
        guard let category = POICategory.allCases.first(where: { $0.rawValue == rawValue }) else {
            return nil
        }
        self = category
    }
}

struct CustomPOIView_Previews: PreviewProvider {
    static var previews: some View {
        CustomPOIView()
    }
}

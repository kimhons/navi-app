import SwiftUI
import MapKit
import Combine

// MARK: - 1. Utilities and Extensions

extension Color {
    /// Navi blue color: #2563EB
    static let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)
}

/// Utility class for debouncing API calls or updates
class Debouncer {
    private let delay: TimeInterval
    private var workItem: DispatchWorkItem?

    init(delay: TimeInterval) {
        self.delay = delay
    }

    func debounce(action: @escaping () -> Void) {
        workItem?.cancel()
        let newWorkItem = DispatchWorkItem {
            action()
        }
        workItem = newWorkItem
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: newWorkItem)
    }
}

// MARK: - 2. Models

enum ConnectorType: String, CaseIterable, Identifiable, Codable {
    case ccs = "CCS"
    case chademo = "CHAdeMO"
    case type2 = "Type 2"
    case tesla = "Tesla"
    case all = "All"
    var id: String { self.rawValue }
}

struct ChargingStation: Identifiable, Codable {
    let id = UUID()
    let name: String
    let coordinate: CLLocationCoordinate2D
    let connectorType: ConnectorType
    let pricePerKWh: Double
    let availableStalls: Int
    
    var isAvailable: Bool { availableStalls > 0 }
    
    var markerColor: Color {
        if availableStalls > 2 {
            return .green
        } else if availableStalls > 0 {
            return .yellow
        } else {
            return .red
        }
    }
}

struct FilterSettings: Codable {
    var selectedConnectorType: ConnectorType = .all
    var maxPricePerKWh: Double = 1.00
    var minAvailableStalls: Int = 1
    var showOnlyAvailable: Bool = true
}

// MARK: - 3. Mock Services

/// Mock APIService for account, subscription, and backup operations
class APIService {
    static let shared = APIService()
    
    private init() {}
    
    func fetchStations() -> AnyPublisher<[ChargingStation], Error> {
        let mockStations: [ChargingStation] = [
            .init(name: "City Hall Charger", coordinate: .init(latitude: 37.7749, longitude: -122.4194), connectorType: .ccs, pricePerKWh: 0.45, availableStalls: 3),
            .init(name: "Park Garage Station", coordinate: .init(latitude: 37.785, longitude: -122.405), connectorType: .tesla, pricePerKWh: 0.55, availableStalls: 0),
            .init(name: "Highway Rest Stop", coordinate: .init(latitude: 37.75, longitude: -122.45), connectorType: .chademo, pricePerKWh: 0.35, availableStalls: 1),
            .init(name: "Downtown Fast Charge", coordinate: .init(latitude: 37.79, longitude: -122.40), connectorType: .type2, pricePerKWh: 0.60, availableStalls: 5),
            .init(name: "Suburban Hub", coordinate: .init(latitude: 37.76, longitude: -122.43), connectorType: .ccs, pricePerKWh: 0.40, availableStalls: 2)
        ]
        
        return Just(mockStations)
            .delay(for: .seconds(1), scheduler: DispatchQueue.main)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
    
    func saveBackup() -> AnyPublisher<Bool, Error> {
        // Simulate iCloud/API backup operation
        return Just(true)
            .delay(for: .seconds(0.5), scheduler: DispatchQueue.main)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
}

// MARK: - 4. ViewModel

class EVChargingViewModel: ObservableObject {
    
    // MARK: - AppStorage for Preferences
    
    @AppStorage("filterSettings") var storedFilterSettingsData: Data = Data()
    
    @Published var filterSettings: FilterSettings {
        didSet {
            // Settings persistence
            if let encoded = try? JSONEncoder().encode(filterSettings) {
                storedFilterSettingsData = encoded
            }
            // Performance: Debounced update
            debouncer.debounce { [weak self] in
                self?.applyFilters()
            }
        }
    }
    
    // MARK: - Published Properties
    
    @Published var stations: [ChargingStation] = []
    @Published var filteredStations: [ChargingStation] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var successMessage: String?
    
    @Published var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194),
        span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
    )
    
    private var cancellables = Set<AnyCancellable>()
    private let debouncer = Debouncer(delay: 0.3)
    
    init() {
        // Load settings from AppStorage/UserDefaults
        if let decoded = try? JSONDecoder().decode(FilterSettings.self, from: storedFilterSettingsData) {
            self.filterSettings = decoded
        } else {
            self.filterSettings = FilterSettings()
        }
        
        fetchStations()
    }
    
    // MARK: - Data Fetching and Filtering
    
    func fetchStations() {
        isLoading = true
        errorMessage = nil
        
        APIService.shared.fetchStations()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                if case .failure(let error) = completion {
                    self?.errorMessage = "Failed to fetch stations: \(error.localizedDescription)"
                }
            } receiveValue: { [weak self] fetchedStations in
                self?.stations = fetchedStations
                self?.applyFilters()
            }
            .store(in: &cancellables)
    }
    
    func applyFilters() {
        // Real-time preview of filtering
        filteredStations = stations.filter { station in
            let connectorMatch: Bool
            if filterSettings.selectedConnectorType == .all {
                connectorMatch = true
            } else {
                connectorMatch = station.connectorType == filterSettings.selectedConnectorType
            }
            
            let priceMatch = station.pricePerKWh <= filterSettings.maxPricePerKWh
            let availabilityMatch = station.availableStalls >= filterSettings.minAvailableStalls
            let showAvailableMatch = !filterSettings.showOnlyAvailable || station.isAvailable
            
            return connectorMatch && priceMatch && availabilityMatch && showAvailableMatch
        }
    }
    
    // MARK: - Actions
    
    func saveSettingsBackup() {
        isLoading = true
        errorMessage = nil
        successMessage = nil
        
        APIService.shared.saveBackup()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                if case .failure(let error) = completion {
                    self?.errorMessage = "Backup failed: \(error.localizedDescription)"
                }
            } receiveValue: { [weak self] success in
                if success {
                    self?.successMessage = "Settings successfully backed up to iCloud."
                }
            }
            .store(in: &cancellables)
    }
    
    func validateAndAddToRoute(station: ChargingStation) -> Bool {
        // Simple input validation example
        if station.availableStalls == 0 {
            errorMessage = "Cannot add \(station.name) to route: No available stalls."
            return false
        }
        // Success feedback
        successMessage = "Added \(station.name) to your route."
        return true
    }
}

// MARK: - 5. View

struct EVChargingView: View {
    
    @StateObject private var viewModel = EVChargingViewModel()
    @State private var isShowingFilterSheet = false
    @State private var stationToNavigate: ChargingStation?
    @State private var isShowingConfirmation = false
    
    var body: some View {
        NavigationView {
            ZStack {
                // Map View
                Map(coordinateRegion: $viewModel.region, annotationItems: viewModel.filteredStations) { station in
                    MapAnnotation(coordinate: station.coordinate) {
                        VStack {
                            Image(systemName: "bolt.circle.fill")
                                .foregroundColor(station.markerColor)
                                .font(.title)
                                .accessibilityLabel("Charging Station: \(station.name)")
                            Text(station.name)
                                .font(.caption2)
                                .lineLimit(1)
                        }
                        .onTapGesture {
                            stationToNavigate = station
                            isShowingConfirmation = true
                        }
                    }
                }
                .ignoresSafeArea(edges: .bottom)
                
                // Loading State Overlay
                if viewModel.isLoading {
                    ProgressView("Loading Stations...")
                        .padding()
                        .background(Color.white.opacity(0.8))
                        .cornerRadius(10)
                }
                
                // Error/Success Feedback
                VStack {
                    Spacer()
                    if let message = viewModel.errorMessage {
                        FeedbackBanner(message: message, isError: true)
                            .accessibilityLiveRegion(.assertive)
                    }
                    if let message = viewModel.successMessage {
                        FeedbackBanner(message: message, isError: false)
                            .accessibilityLiveRegion(.polite)
                    }
                }
            }
            .navigationTitle("EV Charging Map")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Backup") {
                        viewModel.saveSettingsBackup()
                    }
                    .foregroundColor(.naviBlue)
                    .accessibilityLabel("Backup Settings")
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        isShowingFilterSheet = true
                    } label: {
                        Image(systemName: "line.3.horizontal.decrease.circle.fill")
                            .foregroundColor(.naviBlue)
                            .accessibilityLabel("Filter Charging Stations")
                    }
                }
            }
            // Confirmation Dialog for Navigation/Route
            .confirmationDialog("Add to Route", isPresented: $isShowingConfirmation, presenting: stationToNavigate) { station in
                Button("Navigate to \(station.name)") {
                    // Mock navigation action
                    viewModel.successMessage = "Starting navigation to \(station.name)."
                }
                Button("Add to Route") {
                    _ = viewModel.validateAndAddToRoute(station: station)
                }
            } message: { station in
                Text("What would you like to do with \(station.name)?")
            }
            // Filter Sheet
            .sheet(isPresented: $isShowingFilterSheet) {
                FilterSettingsView(filterSettings: $viewModel.filterSettings)
            }
        }
        .accentColor(.naviBlue) // Apply Navi Blue accent color
        .onAppear {
            // Ensure Dynamic Type is supported by using standard SwiftUI components
        }
    }
}

// MARK: - 6. Subviews

struct FilterSettingsView: View {
    @Binding var filterSettings: FilterSettings
    
    var body: some View {
        NavigationView {
            Form {
                // Grouped List 1: Availability
                Section(header: Text("Availability").accessibilityLabel("Availability Filters")) {
                    Toggle(isOn: $filterSettings.showOnlyAvailable) {
                        Text("Show Only Available Stations")
                            .accessibilityValue(filterSettings.showOnlyAvailable ? "On" : "Off")
                    }
                    
                    VStack(alignment: .leading) {
                        Text("Minimum Available Stalls: \(filterSettings.minAvailableStalls)")
                            .accessibilityLabel("Minimum Available Stalls")
                            .accessibilityValue("\(filterSettings.minAvailableStalls)")
                        Slider(value: $filterSettings.minAvailableStalls.doubleValue, in: 0...5, step: 1)
                            .accessibilityAdjustableAction { direction in
                                switch direction {
                                case .increment: filterSettings.minAvailableStalls += 1
                                case .decrement: filterSettings.minAvailableStalls -= 1
                                @unknown default: break
                                }
                            }
                    }
                }
                
                // Grouped List 2: Connector Type
                Section(header: Text("Connector Type").accessibilityLabel("Connector Type Filter")) {
                    Picker("Connector Type", selection: $filterSettings.selectedConnectorType) {
                        ForEach(ConnectorType.allCases) { type in
                            Text(type.rawValue).tag(type as ConnectorType?)
                        }
                    }
                    .pickerStyle(.menu)
                    .accessibilityLabel("Select Connector Type")
                }
                
                // Grouped List 3: Pricing
                Section(header: Text("Pricing").accessibilityLabel("Pricing Filter")) {
                    VStack(alignment: .leading) {
                        Text("Max Price per kWh: \(filterSettings.maxPricePerKWh, specifier: "%.2f")")
                            .accessibilityLabel("Maximum Price per Kilowatt Hour")
                            .accessibilityValue("\(filterSettings.maxPricePerKWh, specifier: "%.2f")")
                        Slider(value: $filterSettings.maxPricePerKWh, in: 0.10...1.50, step: 0.05)
                            .accessibilityAdjustableAction { direction in
                                switch direction {
                                case .increment: filterSettings.maxPricePerKWh += 0.05
                                case .decrement: filterSettings.maxPricePerKWh -= 0.05
                                @unknown default: break
                                }
                            }
                    }
                }
            }
            .navigationTitle("Filter Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        // Dismiss the sheet
                    }
                    .foregroundColor(.naviBlue)
                }
            }
        }
    }
}

/// Helper extension to bind Int to a Double for Slider
extension Int {
    var doubleValue: Double {
        get { Double(self) }
        set { self = Int(newValue.rounded()) }
    }
}

/// Simple banner for error/success messages
struct FeedbackBanner: View {
    let message: String
    let isError: Bool
    
    var body: some View {
        Text(message)
            .font(.callout)
            .padding()
            .frame(maxWidth: .infinity)
            .background(isError ? Color.red.opacity(0.8) : Color.green.opacity(0.8))
            .foregroundColor(.white)
            .cornerRadius(8)
            .padding(.horizontal)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .animation(.easeInOut, value: message)
    }
}

// Helper for CLLocationCoordinate2D to conform to Equatable and Hashable for MapKit
extension CLLocationCoordinate2D: Identifiable {
    public var id: String {
        "\(latitude)-\(longitude)"
    }
}

extension CLLocationCoordinate2D: Equatable {
    public static func == (lhs: CLLocationCoordinate2D, rhs: CLLocationCoordinate2D) -> Bool {
        lhs.latitude == rhs.latitude && lhs.longitude == rhs.longitude
    }
}

extension CLLocationCoordinate2D: Hashable {
    public func hash(into hasher: inout Hasher) {
        hasher.combine(latitude)
        hasher.combine(longitude)
    }
}

// Preview
struct EVChargingView_Previews: PreviewProvider {
    static var previews: some View {
        EVChargingView()
    }
}

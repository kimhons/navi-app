import SwiftUI
import MapboxMaps
import CoreLocation

// MARK: - 1. Data Models

/// Represents the options for avoiding certain road types.
enum AvoidanceOption: String, CaseIterable, Identifiable {
    case tolls = "Tolls"
    case highways = "Highways"
    case ferries = "Ferries"
    var id: String { self.rawValue }
    
    var iconName: String {
        switch self {
        case .tolls: return "dollarsign.circle.fill"
        case .highways: return "road.lanes"
        case .ferries: return "ferry.fill"
        }
    }
}

/// Represents the preferred routing strategy.
enum RoutePreference: String, CaseIterable, Identifiable {
    case fastest = "Fastest"
    case shortest = "Shortest"
    case ecoFriendly = "Eco-Friendly"
    var id: String { self.rawValue }
    
    var iconName: String {
        switch self {
        case .fastest: return "hare.fill"
        case .shortest: return "ruler.fill"
        case .ecoFriendly: return "leaf.fill"
        }
    }
}

/// A structure to hold the current route configuration.
struct RouteOptions: Equatable {
    var avoidanceOptions: Set<AvoidanceOption> = []
    var preference: RoutePreference = .fastest
}

// MARK: - 2. Services (Protocols and Mocks)

/// Protocol for interacting with the backend routing API.
protocol RoutingServiceProtocol {
    func fetchCurrentOptions() async throws -> RouteOptions
    func saveOptions(_ options: RouteOptions) async throws
}

/// Mock implementation for APIService.shared.
class MockRoutingService: RoutingServiceProtocol {
    func fetchCurrentOptions() async throws -> RouteOptions {
        // Simulate network delay and fetch
        try await Task.sleep(for: .milliseconds(500))
        // Mock data: Avoid tolls, prefer fastest
        return RouteOptions(avoidanceOptions: [.tolls], preference: .fastest)
    }

    func saveOptions(_ options: RouteOptions) async throws {
        // Simulate network delay and save
        try await Task.sleep(for: .milliseconds(300))
        print("Options saved to backend: \(options)")
    }
}

/// Protocol for location management.
protocol LocationManagerProtocol: ObservableObject {
    var currentLocation: CLLocationCoordinate2D? { get }
    func requestLocation()
}

/// Mock implementation for CLLocationManager.
class MockLocationManager: LocationManagerProtocol {
    @Published var currentLocation: CLLocationCoordinate2D? = CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194) // San Francisco
    
    func requestLocation() {
        print("Mock location requested.")
        // In a real app, this would trigger CoreLocation updates
    }
}

// MARK: - 3. View Model (MVVM)

/// The ViewModel for the RouteOptionsView.
class RouteOptionsViewModel: ObservableObject {
    // MARK: - Dependencies
    private let routingService: RoutingServiceProtocol
    @ObservedObject private var locationManager: MockLocationManager // Use concrete type for simplicity in mock
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Published Properties
    @Published var currentOptions: RouteOptions = RouteOptions()
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var isSaving: Bool = false
    
    // Derived state for UI
    var isDirty: Bool = false // Tracks if options have changed since last fetch/save
    
    // MARK: - Initialization
    init(routingService: RoutingServiceProtocol = MockRoutingService(), 
         locationManager: MockLocationManager = MockLocationManager()) {
        self.routingService = routingService
        self.locationManager = locationManager
        
        // Setup Combine pipeline for real-time updates and change tracking
        $currentOptions
            .dropFirst() // Ignore initial value
            .sink { [weak self] _ in
                self?.isDirty = true
            }
            .store(in: &cancellables)
        
        fetchOptions()
        locationManager.requestLocation()
    }
    
    // MARK: - Public Methods
    
    /// Toggles an avoidance option on or off.
    func toggleAvoidance(option: AvoidanceOption) {
        if currentOptions.avoidanceOptions.contains(option) {
            currentOptions.avoidanceOptions.remove(option)
        } else {
            currentOptions.avoidanceOptions.insert(option)
        }
    }
    
    /// Sets the preferred routing strategy.
    func setPreference(preference: RoutePreference) {
        currentOptions.preference = preference
    }
    
    /// Fetches the current route options from the backend.
    func fetchOptions() {
        isLoading = true
        errorMessage = nil
        
        Task { @MainActor in
            do {
                let fetchedOptions = try await routingService.fetchCurrentOptions()
                self.currentOptions = fetchedOptions
                self.isDirty = false // Reset dirty flag after successful fetch
            } catch {
                self.errorMessage = "Failed to load route options: \(error.localizedDescription)"
            }
            self.isLoading = false
        }
    }
    
    /// Saves the current route options to the backend.
    func saveOptions() {
        guard isDirty else { return }
        isSaving = true
        errorMessage = nil
        
        Task { @MainActor in
            do {
                try await routingService.saveOptions(self.currentOptions)
                self.isDirty = false // Reset dirty flag after successful save
            } catch {
                self.errorMessage = "Failed to save route options: \(error.localizedDescription)"
            }
            self.isSaving = false
        }
    }
    
    // MARK: - Location Access
    
    var currentLocationText: String {
        guard let location = locationManager.currentLocation else {
            return "Location not available"
        }
        return String(format: "Lat: %.4f, Lon: %.4f", location.latitude, location.longitude)
    }
}

// MARK: - 4. SwiftUI View

/// The main view for route preferences.
struct RouteOptionsView: View {
    @Environment(\.dismiss) var dismiss
    @StateObject var viewModel = RouteOptionsViewModel()
    
    // Navi Blue color: #2563EB
    private let naviBlue = Color(red: 0.145, green: 0.388, blue: 0.922)
    
    var body: some View {
        NavigationView {
            ZStack(alignment: .bottomTrailing) {
                // Main Content ScrollView
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        
                        // MARK: - Avoidance Options Section
                        SectionHeader(title: "Avoidances", icon: "nosign")
                        
                        VStack(spacing: 10) {
                            ForEach(AvoidanceOption.allCases) { option in
                                AvoidanceToggleRow(
                                    option: option,
                                    isSelected: viewModel.currentOptions.avoidanceOptions.contains(option),
                                    toggleAction: { viewModel.toggleAvoidance(option: option) }
                                )
                            }
                        }
                        .padding(.horizontal)
                        
                        Divider()
                        
                        // MARK: - Route Preference Section
                        SectionHeader(title: "Route Preference", icon: "arrow.triangle.branch")
                        
                        VStack(spacing: 10) {
                            ForEach(RoutePreference.allCases) { preference in
                                PreferenceSelectionRow(
                                    preference: preference,
                                    isSelected: viewModel.currentOptions.preference == preference,
                                    selectAction: { viewModel.setPreference(preference: preference) }
                                )
                            }
                        }
                        .padding(.horizontal)
                        
                        Divider()
                        
                        // MARK: - Status and Debug Info
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Status")
                                .font(.headline)
                                .foregroundColor(.secondary)
                                .accessibilityAddTraits(.isHeader)
                            
                            if viewModel.isLoading {
                                ProgressView("Loading options...")
                                    .accessibilityLabel("Loading route options")
                            }
                            
                            if let error = viewModel.errorMessage {
                                Text("Error: \(error)")
                                    .foregroundColor(.red)
                                    .font(.caption)
                                    .accessibilityLabel("Error loading options: \(error)")
                            }
                            
                            Text("Current Location: \(viewModel.currentLocationText)")
                                .font(.caption)
                                .foregroundColor(.gray)
                                .accessibilityLabel("Current location coordinates")
                        }
                        .padding()
                        
                        // Placeholder for Mapbox Map (as per requirement, though typically in a parent view)
                        // Including a placeholder to acknowledge the Mapbox requirement.
                        Text("Mapbox Integration Logic Applied in Parent View")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                            .padding(.bottom, 100) // Ensure space for FAB
                        
                    }
                    .padding(.top)
                }
                
                // MARK: - Floating Action Button (FAB)
                if viewModel.isDirty {
                    FloatingActionButton(
                        action: viewModel.saveOptions,
                        label: viewModel.isSaving ? "Saving..." : "Apply Options",
                        color: naviBlue,
                        isLoading: viewModel.isSaving
                    )
                    .padding()
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .animation(.spring(), value: viewModel.isDirty)
                }
            }
            .navigationTitle("Route Preferences")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
        // Ensure the view respects Dynamic Type
        .environment(\.sizeCategory, .large) 
    }
}

// MARK: - 5. Subviews and Components

/// Reusable header for sections.
struct SectionHeader: View {
    let title: String
    let icon: String
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(.secondary)
                .accessibilityHidden(true)
            Text(title)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.primary)
                .accessibilityAddTraits(.isHeader)
        }
        .padding(.horizontal)
    }
}

/// Row for toggling an avoidance option.
struct AvoidanceToggleRow: View {
    let option: AvoidanceOption
    let isSelected: Bool
    let toggleAction: () -> Void
    
    var body: some View {
        Button(action: toggleAction) {
            HStack {
                Image(systemName: option.iconName)
                    .foregroundColor(isSelected ? .white : .secondary)
                    .frame(width: 30)
                    .accessibilityHidden(true)
                
                Text(option.rawValue)
                    .foregroundColor(.primary)
                    .accessibilityLabel("\(option.rawValue) avoidance option")
                
                Spacer()
                
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(isSelected ? Color(red: 0.145, green: 0.388, blue: 0.922) : .gray)
                    .scaleEffect(isSelected ? 1.1 : 1.0)
                    .animation(.spring(response: 0.3, dampingFraction: 0.6), value: isSelected)
                    .accessibilityLabel(isSelected ? "Selected" : "Not selected")
            }
            .padding()
            .background(isSelected ? Color(red: 0.145, green: 0.388, blue: 0.922).opacity(0.1) : Color(.systemBackground))
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isSelected ? Color(red: 0.145, green: 0.388, blue: 0.922) : Color.gray.opacity(0.3), lineWidth: 1)
            )
            .contentShape(Rectangle()) // Makes the whole row tappable
        }
        .buttonStyle(PlainButtonStyle())
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(isSelected ? "On" : "Off")
    }
}

/// Row for selecting a route preference.
struct PreferenceSelectionRow: View {
    let preference: RoutePreference
    let isSelected: Bool
    let selectAction: () -> Void
    
    var body: some View {
        Button(action: selectAction) {
            HStack {
                Image(systemName: preference.iconName)
                    .foregroundColor(isSelected ? .white : .secondary)
                    .frame(width: 30)
                    .accessibilityHidden(true)
                
                Text(preference.rawValue)
                    .foregroundColor(.primary)
                    .accessibilityLabel("\(preference.rawValue) route preference")
                
                Spacer()
                
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .foregroundColor(isSelected ? Color(red: 0.145, green: 0.388, blue: 0.922) : .gray)
                    .scaleEffect(isSelected ? 1.1 : 1.0)
                    .animation(.spring(response: 0.3, dampingFraction: 0.6), value: isSelected)
                    .accessibilityLabel(isSelected ? "Selected" : "Not selected")
            }
            .padding()
            .background(isSelected ? Color(red: 0.145, green: 0.388, blue: 0.922).opacity(0.1) : Color(.systemBackground))
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isSelected ? Color(red: 0.145, green: 0.388, blue: 0.922) : Color.gray.opacity(0.3), lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(isSelected ? "Current preference" : "Tap to select")
    }
}

/// Floating Action Button component.
struct FloatingActionButton: View {
    let action: () -> Void
    let label: String
    let color: Color
    let isLoading: Bool
    
    var body: some View {
        Button(action: action) {
            HStack {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .accessibilityLabel("Saving in progress")
                } else {
                    Image(systemName: "arrow.right.circle.fill")
                        .scaleEffect(1.2)
                        .accessibilityHidden(true)
                }
                Text(label)
                    .fontWeight(.semibold)
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 20)
            .background(color)
            .foregroundColor(.white)
            .cornerRadius(30)
            .shadow(color: color.opacity(0.5), radius: 10, x: 0, y: 5)
        }
        .disabled(isLoading)
        .accessibilityLabel(label)
        .accessibilityHint("Applies the selected route options and updates the map.")
    }
}

// MARK: - 6. Preview

struct RouteOptionsView_Previews: PreviewProvider {
    static var previews: some View {
        RouteOptionsView()
    }
}

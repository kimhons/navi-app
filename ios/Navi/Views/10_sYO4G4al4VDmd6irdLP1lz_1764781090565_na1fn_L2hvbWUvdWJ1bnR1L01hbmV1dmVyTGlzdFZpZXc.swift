//
//  ManeuverListView.swift
//  NavigationApp
//
//  Created by Manus AI on 2025-12-03.
//

import SwiftUI
import Combine
import CoreLocation // Required for CLLocationManager
// import MapboxMaps // Stubbed for compilation, as actual SDK is not available in sandbox

// MARK: - 1. Data Structures and Constants

/// Represents a single turn-by-turn instruction.
struct Maneuver: Identifiable, Equatable {
    let id = UUID()
    let instruction: String
    let distance: Measurement<UnitLength>
    let streetName: String
    let iconName: String // SF Symbol name for the maneuver type
}

/// Represents the possible states of the view.
enum ViewState: Equatable {
    case loading
    case loaded
    case error(message: String)
}

/// Custom color definition for "Navi blue" (#2563EB)
extension Color {
    static let naviBlue = Color(red: 0.145, green: 0.388, blue: 0.922) // #2563EB
}

// MARK: - 2. Mock Services

/// Mock APIService singleton for backend calls.
class APIService {
    static let shared = APIService()
    
    func fetchManeuvers() -> AnyPublisher<[Maneuver], Error> {
        // Simulate network delay
        return Just(Maneuver.mockData)
            .delay(for: .seconds(1.5), scheduler: DispatchQueue.main)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
}

/// Mock Location Manager to simulate CLLocationManager behavior.
class MockLocationManager: NSObject, ObservableObject {
    @Published var currentLocation: CLLocation?
    
    override init() {
        super.init()
        // Simulate location update after a short delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.currentLocation = CLLocation(latitude: 37.7749, longitude: -122.4194)
        }
    }
    
    func startUpdatingLocation() {
        // In a real app, this would call CLLocationManager().startUpdatingLocation()
        print("MockLocationManager: Started updating location.")
    }
}

extension Maneuver {
    static let mockData: [Maneuver] = [
        Maneuver(instruction: "Head north on Market St", distance: Measurement(value: 0.5, unit: .miles), streetName: "Market Street", iconName: "arrow.up"),
        Maneuver(instruction: "Turn left onto 10th St", distance: Measurement(value: 300, unit: .meters), streetName: "10th Street", iconName: "arrow.turn.up.left"),
        Maneuver(instruction: "Keep right at the fork", distance: Measurement(value: 1.2, unit: .kilometers), streetName: "Highway 101 N", iconName: "arrow.triangle.branch"),
        Maneuver(instruction: "Arrive at destination", distance: Measurement(value: 0, unit: .meters), streetName: "Final Destination", iconName: "flag.checkered")
    ]
}

// MARK: - 3. ViewModel (MVVM)

class ManeuverListViewModel: ObservableObject {
    @Published var maneuvers: [Maneuver] = []
    @Published var viewState: ViewState = .loading
    @Published var isDetailSheetPresented: Bool = false
    
    private var cancellables = Set<AnyCancellable>()
    private let apiService: APIService
    private let locationManager: MockLocationManager
    
    // Location tracking via published property from MockLocationManager
    @Published var currentLocation: CLLocation?
    
    init(apiService: APIService = .shared, locationManager: MockLocationManager = MockLocationManager()) {
        self.apiService = apiService
        self.locationManager = locationManager
        
        // Subscribe to location updates
        locationManager.$currentLocation
            .assign(to: &$currentLocation)
        
        // Start initial data fetch and location updates
        fetchManeuvers()
        locationManager.startUpdatingLocation()
    }
    
    /// Fetches the list of maneuvers from the mock API service.
    func fetchManeuvers() {
        viewState = .loading
        
        apiService.fetchManeuvers()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                switch completion {
                case .failure(let error):
                    self?.viewState = .error(message: "Failed to load maneuvers: \(error.localizedDescription)")
                case .finished:
                    self?.viewState = .loaded
                }
            } receiveValue: { [weak self] maneuvers in
                self?.maneuvers = maneuvers
            }
            .store(in: &cancellables)
    }
    
    /// Simulates a real-time update to the maneuver list.
    func simulateRealTimeUpdate() {
        guard viewState == .loaded else { return }
        
        // Example: Remove the first maneuver and add a new one
        var updatedManeuvers = maneuvers
        if !updatedManeuvers.isEmpty {
            updatedManeuvers.removeFirst()
        }
        updatedManeuvers.append(Maneuver(instruction: "New real-time instruction", distance: Measurement(value: 50, unit: .meters), streetName: "Updated Route Segment", iconName: "figure.walk"))
        
        withAnimation(.easeInOut(duration: 0.5)) {
            self.maneuvers = updatedManeuvers
        }
    }
}

// MARK: - 4. Views

/// A single row representing a maneuver instruction.
struct ManeuverRow: View {
    let maneuver: Maneuver
    
    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            Image(systemName: maneuver.iconName)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 24, height: 24)
                .foregroundColor(.naviBlue)
                .accessibilityLabel("Maneuver icon: \(maneuver.instruction)")
            
            VStack(alignment: .leading, spacing: 4) {
                Text(maneuver.instruction)
                    .font(.headline)
                    .foregroundColor(.primary)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .accessibilityLabel(maneuver.instruction)
                
                Text(maneuver.streetName)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .accessibilityLabel("Street name: \(maneuver.streetName)")
            }
            
            Spacer()
            
            Text(maneuver.distance.formatted(.measurement(width: .abbreviated, usage: .asProvided)))
                .font(.callout.monospacedDigit())
                .fontWeight(.medium)
                .foregroundColor(.naviBlue)
                .accessibilityLabel("Distance: \(maneuver.distance.formatted(.measurement(width: .wide, usage: .asProvided)))")
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle()) // Ensure the whole row is tappable
    }
}

/// The main view for the scrollable list of turn-by-turn instructions.
struct ManeuverListView: View {
    @StateObject var viewModel = ManeuverListViewModel()
    
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            mainContent
            
            floatingButtons
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Turn-by-Turn Instructions")
        .sheet(isPresented: $viewModel.isDetailSheetPresented) {
            // Bottom sheet for details
            VStack(alignment: .leading, spacing: 16) {
                Text("Route Details")
                    .font(.largeTitle.weight(.bold))
                    .foregroundColor(.naviBlue)
                
                Text("Current Location: \(viewModel.currentLocation?.coordinate.latitude.description ?? "Unknown")")
                    .font(.body)
                
                Text("This bottom sheet can be used to display more detailed information about the route, such as estimated time of arrival, traffic conditions, or alternative routes.")
                    .font(.callout)
                    .foregroundColor(.secondary)
                
                Button("Dismiss") {
                    viewModel.isDetailSheetPresented = false
                }
                .buttonStyle(.borderedProminent)
                .tint(.naviBlue)
                .padding(.top, 10)
            }
            .padding()
            .presentationDetents([.medium, .large])
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Route Details Bottom Sheet")
        }
    }
    
    @ViewBuilder
    private var mainContent: some View {
        switch viewModel.viewState {
        case .loading:
            ProgressView("Loading Route...")
                .progressViewStyle(.circular)
                .scaleEffect(1.5)
                .foregroundColor(.naviBlue)
                .accessibilityLabel("Loading route instructions")
            
        case .error(let message):
            VStack(spacing: 16) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(.red)
                    .font(.largeTitle)
                Text("Error")
                    .font(.title)
                Text(message)
                    .font(.body)
                    .multilineTextAlignment(.center)
                Button("Retry") {
                    viewModel.fetchManeuvers()
                }
                .buttonStyle(.borderedProminent)
                .tint(.naviBlue)
            }
            .padding()
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Error loading route: \(message)")
            
        case .loaded:
            // Lazy loading for performance
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(viewModel.maneuvers) { maneuver in
                        ManeuverRow(maneuver: maneuver)
                            .padding(.horizontal)
                            .background(Color.white)
                            .overlay(
                                Divider(), alignment: .bottom
                            )
                            .accessibilityElement(children: .combine)
                    }
                }
            }
            .accessibilityLabel("Turn-by-turn instructions list")
        }
    }
    
    @ViewBuilder
    private var floatingButtons: some View {
        VStack(spacing: 16) {
            // Real-time update simulation button
            Button {
                viewModel.simulateRealTimeUpdate()
            } label: {
                Image(systemName: "arrow.clockwise.circle.fill")
                    .font(.title2)
                    .padding(12)
                    .background(Color.white)
                    .clipShape(Circle())
                    .shadow(color: .black.opacity(0.2), radius: 5, x: 0, y: 2)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Simulate real-time route update")
            
            // Floating action button for details (bottom sheet)
            Button {
                viewModel.isDetailSheetPresented = true
            } label: {
                Image(systemName: "info.circle.fill")
                    .font(.title)
                    .padding(15)
                    .background(Color.naviBlue)
                    .foregroundColor(.white)
                    .clipShape(Circle())
                    .shadow(color: .naviBlue.opacity(0.5), radius: 10, x: 0, y: 5) // Shadow requirement
            }
            .buttonStyle(.plain)
            .padding(.trailing, 20)
            .padding(.bottom, 20)
            .accessibilityLabel("Show route details")
        }
    }
}

// MARK: - Preview

struct ManeuverListView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            ManeuverListView()
        }
        .environment(\.dynamicTypeSize, .large) // Dynamic Type support preview
    }
}

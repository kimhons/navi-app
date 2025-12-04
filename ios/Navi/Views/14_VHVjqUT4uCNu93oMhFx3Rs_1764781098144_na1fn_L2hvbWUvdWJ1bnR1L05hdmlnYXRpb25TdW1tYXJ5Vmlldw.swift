//
// NavigationSummaryView.swift
//
// A complete, production-ready SwiftUI screen for post-navigation summary.
// Implements MVVM architecture, Mapbox integration (mocked), and required design features.
//

import SwiftUI
import MapboxMaps // Required for Mapbox Integration
import Combine
import CoreLocation // Required for CLLocationManager usage (mocked in ViewModel)

// MARK: - 1. Data Models

/// Represents the final summary data of a navigation session.
struct NavigationSummary: Identifiable, Codable {
    let id = UUID()
    let totalDistance: Measurement<UnitLength>
    let totalTime: TimeInterval // in seconds
    let averageSpeed: Measurement<UnitSpeed>
    let fuelEstimate: Double // in liters or gallons
    let routeCoordinates: [CLLocationCoordinate2D] // Mock for map display
    
    static var mock: NavigationSummary {
        NavigationSummary(
            totalDistance: Measurement(value: 15.4, unit: .kilometers),
            totalTime: 1845, // 30 minutes 45 seconds
            averageSpeed: Measurement(value: 30.0, unit: .kilometersPerHour),
            fuelEstimate: 1.25,
            routeCoordinates: [
                CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194),
                CLLocationCoordinate2D(latitude: 37.7849, longitude: -122.4094),
                CLLocationCoordinate2D(latitude: 37.7949, longitude: -122.3994)
            ]
        )
    }
}

// MARK: - 2. Mock API Service

/// Protocol for backend communication, satisfying the APIService.shared requirement.
protocol APIServiceProtocol {
    func fetchNavigationSummary() -> AnyPublisher<NavigationSummary, Error>
    func saveNavigationSummary(_ summary: NavigationSummary) -> AnyPublisher<Bool, Error>
}

enum APIError: Error {
    case networkError
    case saveFailed
    case noData
    
    var localizedDescription: String {
        switch self {
        case .networkError: return "Could not connect to the server."
        case .saveFailed: return "Failed to save the navigation summary."
        case .noData: return "No summary data available."
        }
    }
}

/// Mock implementation of the API service.
class MockAPIService: APIServiceProtocol {
    static let shared = MockAPIService() // Singleton to satisfy APIService.shared
    
    func fetchNavigationSummary() -> AnyPublisher<NavigationSummary, Error> {
        // Simulate network delay and success
        Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                // 90% success rate, 10% error rate
                if Bool.random() {
                    promise(.success(NavigationSummary.mock))
                } else {
                    promise(.failure(APIError.networkError))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    func saveNavigationSummary(_ summary: NavigationSummary) -> AnyPublisher<Bool, Error> {
        // Simulate network delay and success
        Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                // 95% success rate
                if Bool.random() {
                    promise(.success(true))
                } else {
                    promise(.failure(APIError.saveFailed))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - 3. ViewModel

/// ViewModel for NavigationSummaryView, handling data fetching and state management.
class NavigationSummaryViewModel: ObservableObject {
    
    // MVVM with @Published properties for reactive UI
    @Published var summary: NavigationSummary?
    @Published var isLoading: Bool = false
    @Published var error: APIError?
    @Published var isSaving: Bool = false
    @Published var showBottomSheet: Bool = true
    
    private var apiService: APIServiceProtocol
    private var cancellables = Set<AnyCancellable>()
    
    // Mock for CLLocationManager usage (e.g., tracking the route)
    private let locationManager = CLLocationManager()
    
    init(apiService: APIServiceProtocol = MockAPIService.shared) {
        self.apiService = apiService
        // Simulate location manager setup
        locationManager.requestWhenInUseAuthorization()
        loadSummary()
    }
    
    /// Fetches the navigation summary data.
    func loadSummary() {
        isLoading = true
        error = nil
        
        apiService.fetchNavigationSummary()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                if case .failure(let err) = completion {
                    self?.error = err as? APIError ?? .networkError
                }
            } receiveValue: { [weak self] summary in
                withAnimation(.spring()) {
                    self?.summary = summary
                }
            }
            .store(in: &cancellables)
    }
    
    /// Saves the navigation summary data.
    func saveSummary() {
        guard let summary = summary else {
            error = .noData
            return
        }
        
        isSaving = true
        error = nil
        
        apiService.saveNavigationSummary(summary)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isSaving = false
                if case .failure(let err) = completion {
                    self?.error = err as? APIError ?? .saveFailed
                } else {
                    // Successfully saved, perhaps show a confirmation
                    print("Summary saved successfully!")
                }
            } receiveValue: { _ in
                // Value is true on success, nothing to do here
            }
            .store(in: &cancellables)
    }
    
    // Helper to format time interval into H:MM:SS
    func formattedTime(from interval: TimeInterval) -> String {
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = [.hour, .minute, .second]
        formatter.unitsStyle = .abbreviated
        return formatter.string(from: interval) ?? "N/A"
    }
}

// MARK: - 4. View Components (Design)

/// Custom color for the main design theme.
extension Color {
    static let naviBlue = Color(red: 0x25/255.0, green: 0x63/255.0, blue: 0xEB/255.0)
}

/// A custom view to simulate the Mapbox map rendering area.
struct MapboxMapViewPlaceholder: View {
    let coordinates: [CLLocationCoordinate2D]
    
    var body: some View {
        // In a real app, this would be a UIViewRepresentable wrapping MapboxMapView
        // and using the coordinates to draw the route.
        Rectangle()
            .fill(Color.gray.opacity(0.3))
            .overlay(
                VStack {
                    Text("Mapbox Map View")
                        .font(.title2)
                        .foregroundColor(.white)
                    Text("Route: \(coordinates.count) points")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                }
            )
            .accessibilityLabel("Map showing the completed navigation route.")
    }
}

/// A card to display a single summary metric.
struct SummaryDetailCard: View {
    let title: String
    let value: String
    let iconName: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Image(systemName: iconName)
                    .foregroundColor(.naviBlue)
                    .accessibilityHidden(true)
                Text(title)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .accessibilityLabel(title)
            }
            Text(value)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.primary)
                .minimumScaleFactor(0.7)
                .lineLimit(1)
                .accessibilityValue(value)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: Color.black.opacity(0.05), radius: 5, x: 0, y: 2)
        .accessibilityElement(children: .combine)
    }
}

/// A floating action button with a shadow.
struct FloatingActionButton: View {
    let iconName: String
    let action: () -> Void
    let accessibilityLabel: String
    
    var body: some View {
        Button(action: action) {
            Image(systemName: iconName)
                .font(.title2)
                .foregroundColor(.white)
                .padding(18)
                .background(Color.naviBlue)
                .clipShape(Circle())
                .shadow(color: Color.naviBlue.opacity(0.5), radius: 10, x: 0, y: 5)
        }
        .accessibilityLabel(accessibilityLabel)
    }
}

// MARK: - 5. Main View

struct NavigationSummaryView: View {
    
    // MVVM with @StateObject ViewModel
    @StateObject var viewModel = NavigationSummaryViewModel()
    
    var body: some View {
        ZStack {
            // 1. Mapbox Map Area
            if let summary = viewModel.summary {
                MapboxMapViewPlaceholder(coordinates: summary.routeCoordinates)
                    .edgesIgnoringSafeArea(.all)
            } else {
                Color.gray.opacity(0.1)
                    .edgesIgnoringSafeArea(.all)
            }
            
            // 2. Loading and Error States
            if viewModel.isLoading {
                ProgressView("Loading Summary...")
                    .padding()
                    .background(Color(.systemBackground))
                    .cornerRadius(10)
                    .shadow(radius: 5)
                    .transition(.opacity)
            } else if let error = viewModel.error {
                VStack(spacing: 10) {
                    Text("Error")
                        .font(.headline)
                    Text(error.localizedDescription)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Button("Retry") {
                        viewModel.loadSummary()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.naviBlue)
                }
                .padding(20)
                .background(Color(.systemBackground))
                .cornerRadius(10)
                .shadow(radius: 5)
                .transition(.opacity)
            }
            
            // 3. Floating Action Buttons
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    FloatingActionButton(
                        iconName: viewModel.isSaving ? "arrow.up.circle.fill" : "square.and.arrow.down",
                        action: {
                            if !viewModel.isSaving {
                                viewModel.saveSummary()
                            }
                        },
                        accessibilityLabel: viewModel.isSaving ? "Saving summary" : "Save navigation summary"
                    )
                    .padding(.trailing, 20)
                    .padding(.bottom, 10)
                    .opacity(viewModel.summary != nil && !viewModel.isLoading ? 1 : 0)
                    .animation(.easeInOut, value: viewModel.summary)
                }
            }
            
            // 4. Bottom Sheet for Details
            if viewModel.summary != nil {
                summaryBottomSheet
                    .transition(.move(edge: .bottom))
            }
        }
        .animation(.default, value: viewModel.isLoading)
        .animation(.default, value: viewModel.error)
        .onAppear {
            // Ensure summary loads if not already
            if viewModel.summary == nil && !viewModel.isLoading {
                viewModel.loadSummary()
            }
        }
    }
    
    /// The content of the bottom sheet, using LazyVStack for performance.
    @ViewBuilder
    private var summaryBottomSheet: some View {
        VStack(spacing: 0) {
            // Drag Indicator
            Capsule()
                .fill(Color.secondary)
                .frame(width: 40, height: 5)
                .padding(.vertical, 8)
                .onTapGesture {
                    withAnimation(.spring()) {
                        viewModel.showBottomSheet.toggle()
                    }
                }
            
            if viewModel.showBottomSheet, let summary = viewModel.summary {
                ScrollView(.vertical, showsIndicators: false) {
                    // Lazy loading of summary cards
                    LazyVStack(spacing: 15) {
                        Text("Navigation Complete")
                            .font(.largeTitle)
                            .fontWeight(.heavy)
                            .foregroundColor(.primary)
                            .padding(.top, 10)
                            .accessibilityAddTraits(.isHeader)
                        
                        // Summary Grid
                        VStack(spacing: 15) {
                            SummaryDetailCard(
                                title: "Total Distance",
                                value: summary.totalDistance.formatted(.measurement(width: .abbreviated, usage: .road)),
                                iconName: "point.fill.and.text.image.copy"
                            )
                            
                            SummaryDetailCard(
                                title: "Total Time",
                                value: viewModel.formattedTime(from: summary.totalTime),
                                iconName: "clock.fill"
                            )
                            
                            SummaryDetailCard(
                                title: "Average Speed",
                                value: summary.averageSpeed.formatted(.measurement(width: .abbreviated, usage: .road)),
                                iconName: "speedometer"
                            )
                            
                            SummaryDetailCard(
                                title: "Fuel Estimate",
                                value: String(format: "%.2f L", summary.fuelEstimate),
                                iconName: "fuelpump.fill"
                            )
                        }
                        .padding(.horizontal)
                        
                        // Additional Details Section
                        VStack(alignment: .leading) {
                            Text("Route Details")
                                .font(.title3)
                                .fontWeight(.semibold)
                                .padding(.top, 10)
                            
                            Text("The route covered a total of \(summary.totalDistance.formatted(.measurement(width: .wide, usage: .road))) over a period of \(viewModel.formattedTime(from: summary.totalTime)). The estimated fuel consumption for this trip was \(String(format: "%.2f liters", summary.fuelEstimate)).")
                                .font(.body)
                                .foregroundColor(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                                .padding(.bottom, 20)
                                .accessibilityLabel("Detailed route information.")
                        }
                        .padding(.horizontal)
                        
                    }
                    .padding(.bottom, 40) // Space for the floating button
                }
            }
        }
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 25, style: .continuous)
                .fill(Color(.systemBackground))
                .shadow(radius: 20)
        )
        .padding(.top, 50) // Ensure it doesn't cover the whole screen
        .frame(maxHeight: .infinity, alignment: .bottom)
    }
}

// MARK: - 6. Preview

struct NavigationSummaryView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationSummaryView()
    }
}

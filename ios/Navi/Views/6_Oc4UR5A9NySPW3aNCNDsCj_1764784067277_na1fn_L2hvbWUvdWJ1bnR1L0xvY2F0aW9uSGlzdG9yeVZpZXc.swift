import Foundation
import SwiftUI
import Combine
import MapKit

// MARK: - 1. Color Definition

extension Color {
    static let naviBlue = Color(red: 37/255, green: 99/255, blue: 235/255) // #2563EB
}

// MARK: - 2. Data Models

struct LocationRecord: Identifiable, Equatable {
    let id = UUID()
    let latitude: Double
    let longitude: Double
    let timestamp: Date
    let address: String
    let sharedWith: [Friend]
    
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

struct Friend: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let isOnline: Bool
    let profileImageName: String // Mock for simplicity
}

struct Achievement: Identifiable {
    let id = UUID()
    let title: String
    let description: String
    let iconName: String
}

// MARK: - 3. Mock APIService

class APIService {
    static let shared = APIService()
    
    private init() {}
    
    // Mock Data
    private let mockFriends: [Friend] = [
        Friend(name: "Alice", isOnline: true, profileImageName: "person.fill"),
        Friend(name: "Bob", isOnline: false, profileImageName: "person.fill"),
        Friend(name: "Charlie", isOnline: true, profileImageName: "person.fill")
    ]
    
    private let mockLocations: [LocationRecord] = [
        LocationRecord(latitude: 34.0522, longitude: -118.2437, timestamp: Calendar.current.date(byAdding: .day, value: -1, to: Date())!, address: "Los Angeles, CA", sharedWith: []),
        LocationRecord(latitude: 34.0522, longitude: -118.2437, timestamp: Calendar.current.date(byAdding: .hour, value: -12, to: Date())!, address: "Los Angeles, CA", sharedWith: []),
        LocationRecord(latitude: 34.0522, longitude: -118.2437, timestamp: Calendar.current.date(byAdding: .hour, value: -6, to: Date())!, address: "Los Angeles, CA", sharedWith: []),
        LocationRecord(latitude: 37.7749, longitude: -122.4194, timestamp: Date(), address: "San Francisco, CA", sharedWith: [Friend(name: "Alice", isOnline: true, profileImageName: "person.fill")])
    ]
    
    func fetchFriends() -> AnyPublisher<[Friend], Error> {
        Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                promise(.success(self.mockFriends))
            }
        }
        .eraseToAnyPublisher()
    }
    
    func fetchLocationHistory() -> AnyPublisher<[LocationRecord], Error> {
        Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                // Simulate an error occasionally for error handling test
                // promise(.failure(URLError(.notConnectedToInternet)))
                promise(.success(self.mockLocations.sorted(by: { $0.timestamp > $1.timestamp })))
            }
        }
        .eraseToAnyPublisher()
    }
    
    func exportData() -> AnyPublisher<Bool, Error> {
        Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                promise(.success(true))
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - 4. ViewModel

class LocationHistoryViewModel: ObservableObject {
    @Published var locations: [LocationRecord] = []
    @Published var friends: [Friend] = []
    @Published var isLoading: Bool = false
    @Published var error: Error?
    @Published var filterDate: Date = Calendar.current.startOfDay(for: Date())
    @Published var isExporting: Bool = false
    @Published var exportSuccess: Bool = false
    @Published var privacyLevel: PrivacyLevel = .friends
    
    private var cancellables = Set<AnyCancellable>()
    
    enum PrivacyLevel: String, CaseIterable {
        case everyone = "Everyone"
        case friends = "Friends Only"
        case nobody = "Nobody"
    }
    
    init() {
        fetchData()
    }
    
    var filteredLocations: [LocationRecord] {
        locations.filter { Calendar.current.isDate($0.timestamp, inSameDayAs: filterDate) }
    }
    
    var region: MKCoordinateRegion {
        guard let latestLocation = filteredLocations.first else {
            // Default to a central location if no data
            return MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 34.0522, longitude: -118.2437),
                span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5)
            )
        }
        return MKCoordinateRegion(
            center: latestLocation.coordinate,
            span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
        )
    }
    
    func fetchData() {
        isLoading = true
        error = nil
        
        APIService.shared.fetchLocationHistory()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                if case let .failure(err) = completion {
                    self?.error = err
                }
            } receiveValue: { [weak self] records in
                self?.locations = records
            }
            .store(in: &cancellables)
        
        APIService.shared.fetchFriends()
            .receive(on: DispatchQueue.main)
            .sink { _ in } receiveValue: { [weak self] friends in
                self?.friends = friends
            }
            .store(in: &cancellables)
    }
    
    func exportHistory() {
        isExporting = true
        exportSuccess = false
        
        APIService.shared.exportData()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isExporting = false
                if case let .failure(err) = completion {
                    self?.error = err
                }
            } receiveValue: { [weak self] success in
                self?.exportSuccess = success
            }
            .store(in: &cancellables)
    }
    
    func deleteHistory() {
        // In a real app, this would call an API to delete data
        locations = []
        // Simulate API call delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
            self.error = nil
        }
    }
    
    func updatePrivacy(level: PrivacyLevel) {
        privacyLevel = level
        // In a real app, this would call an API to update privacy settings
    }
}

// MARK: - 5. View

struct LocationHistoryView: View {
    @StateObject var viewModel = LocationHistoryViewModel()
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                mapView
                
                Divider()
                
                contentView
            }
            .navigationTitle("Location History")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    dateFilter
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    privacyButton
                }
            }
            .onAppear {
                // Real-time updates simulation (e.g., WebSocket connection)
                // In a real app, a timer or WebSocket publisher would be set up here
            }
        }
        .alert("Export Successful", isPresented: $viewModel.exportSuccess) {
            Button("OK", role: .cancel) {}
        }
        .accessibilityElement(children: .contain)
    }
    
    // MARK: - Subviews
    
    private var mapView: some View {
        Map(coordinateRegion: $viewModel.region, annotationItems: viewModel.filteredLocations) { location in
            MapMarker(coordinate: location.coordinate, tint: .naviBlue)
        }
        .frame(height: 200)
        .accessibilityLabel("Map showing location history for \(Calendar.current.localizedString(for: viewModel.filterDate))")
    }
    
    private var dateFilter: some View {
        DatePicker("Filter Date", selection: $viewModel.filterDate, displayedComponents: .date)
            .labelsHidden()
            .datePickerStyle(.compact)
            .tint(.naviBlue)
            .accessibilityLabel("Select date to filter location history")
    }
    
    private var privacyButton: some View {
        Menu {
            Button(action: { viewModel.exportHistory() }) {
                Label(viewModel.isExporting ? "Exporting..." : "Export Data", systemImage: "square.and.arrow.up")
            }
            .disabled(viewModel.isExporting)
            
            Button(role: .destructive, action: { viewModel.deleteHistory() }) {
                Label("Delete History", systemImage: "trash")
            }
            
            Divider()
            
            Text("Location Sharing")
            ForEach(LocationHistoryViewModel.PrivacyLevel.allCases, id: \.self) { level in
                Button(action: { viewModel.updatePrivacy(level: level) }) {
                    Label(level.rawValue, systemImage: viewModel.privacyLevel == level ? "checkmark.circle.fill" : "circle")
                }
            }
            
        } label: {
            Image(systemName: "ellipsis.circle")
                .foregroundColor(.naviBlue)
                .accessibilityLabel("Privacy and Data Controls")
        }
    }
    
    @ViewBuilder
    private var contentView: some View {
        if viewModel.isLoading {
            loadingState
        } else if let error = viewModel.error {
            errorState(error)
        } else if viewModel.filteredLocations.isEmpty {
            emptyState
        } else {
            timelineList
        }
    }
    
    private var loadingState: some View {
        VStack {
            ProgressView()
                .progressViewStyle(.circular)
                .tint(.naviBlue)
            Text("Loading location history...")
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel("Loading location history")
    }
    
    private func errorState(_ error: Error) -> some View {
        VStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundColor(.red)
            Text("Error loading data")
                .font(.headline)
            Text(error.localizedDescription)
                .font(.subheadline)
                .foregroundColor(.secondary)
            Button("Retry") {
                viewModel.fetchData()
            }
            .padding(.top, 8)
            .buttonStyle(.borderedProminent)
            .tint(.naviBlue)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Error: \(error.localizedDescription). Tap retry to reload.")
    }
    
    private var emptyState: some View {
        VStack {
            Image(systemName: "map.circle.fill")
                .font(.largeTitle)
                .foregroundColor(.naviBlue)
            Text("No Locations Found")
                .font(.headline)
            Text("Try selecting a different date or check your privacy settings.")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("No location history found for the selected date.")
    }
    
    private var timelineList: some View {
        List {
            ForEach(viewModel.filteredLocations) { record in
                LocationTimelineRow(record: record, friends: viewModel.friends)
                    .listRowSeparator(.hidden)
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("Location at \(record.address) on \(record.timestamp.formatted(date: .omitted, time: .shortened))")
            }
        }
        .listStyle(.plain)
        .accessibilityIdentifier("LocationHistoryList")
    }
}

// MARK: - 6. Component Views

struct LocationTimelineRow: View {
    let record: LocationRecord
    let friends: [Friend]
    
    var body: some View {
        HStack(alignment: .top, spacing: 15) {
            timelineIndicator
            locationDetails
        }
        .padding(.vertical, 5)
    }
    
    private var timelineIndicator: some View {
        VStack {
            Circle()
                .fill(Color.naviBlue)
                .frame(width: 10, height: 10)
            Rectangle()
                .fill(Color.naviBlue.opacity(0.5))
                .frame(width: 2, height: 50)
        }
    }
    
    private var locationDetails: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(record.timestamp, style: .time)
                .font(.caption)
                .foregroundColor(.secondary)
                .accessibilityAddTraits(.isHeader)
            
            Text(record.address)
                .font(.headline)
                .foregroundColor(.primary)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
                .accessibilityHint("Location address")
            
            if !record.sharedWith.isEmpty {
                HStack {
                    Text("Shared with:")
                        .font(.caption)
                    
                    ForEach(record.sharedWith) { friend in
                        ProfileCard(friend: friend)
                    }
                }
                .padding(.top, 2)
            }
        }
    }
}

struct ProfileCard: View {
    let friend: Friend
    
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: friend.profileImageName)
                .resizable()
                .scaledToFit()
                .frame(width: 15, height: 15)
                .clipShape(Circle())
                .foregroundColor(.white)
                .padding(4)
                .background(Color.naviBlue)
                .clipShape(Circle())
            
            Text(friend.name)
                .font(.caption)
                .lineLimit(1)
            
            if friend.isOnline {
                Circle()
                    .fill(Color.green)
                    .frame(width: 6, height: 6)
                    .accessibilityLabel("Online indicator")
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.naviBlue.opacity(0.1))
        .cornerRadius(12)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Profile card for \(friend.name). Status: \(friend.isOnline ? "Online" : "Offline")")
    }
}

// MARK: - 7. Preview

struct LocationHistoryView_Previews: PreviewProvider {
    static var previews: some View {
        LocationHistoryView()
            .environment(\.colorScheme, .light)
        
        LocationHistoryView()
            .environment(\.colorScheme, .dark)
            .previewDisplayName("Dark Mode")
    }
}

// MARK: - Accessibility Helper

extension Calendar {
    func localizedString(for date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.timeStyle = .none
        return formatter.string(from: date)
    }
}

// MARK: - Performance Optimization Note
// The List uses ForEach with Identifiable data, which is efficient.
// Image caching is simulated by using system images, but in a real app, a dedicated image caching solution (e.g., Kingfisher) would be used for remote images.
// Combine is used for reactive, efficient data flow.

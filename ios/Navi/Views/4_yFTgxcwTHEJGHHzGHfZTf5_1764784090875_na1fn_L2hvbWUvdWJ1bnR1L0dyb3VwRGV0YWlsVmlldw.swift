import SwiftUI
import Combine
import MapKit

// MARK: - 1. Data Models

/// Represents the state of the Group Detail View.
enum GroupDetailState {
    case loading
    case loaded
    case empty
    case error(String)
}

/// Defines the roles a member can have in a group.
enum GroupRole: String, CaseIterable {
    case owner = "Owner"
    case admin = "Admin"
    case member = "Member"
    case guest = "Guest"
    
    var color: Color {
        switch self {
        case .owner: return .red
        case .admin: return .orange
        case .member: return .blue
        case .guest: return .gray
        }
    }
}

/// Represents a shared location by a group member.
struct SharedLocation: Identifiable {
    let id = UUID()
    let memberName: String
    let latitude: Double
    let longitude: Double
    let timestamp: Date
}

/// Represents a member of the group.
struct Member: Identifiable {
    let id: String
    let name: String
    let avatarURL: String
    let role: GroupRole
    let isOnline: Bool
}

/// Represents the main group data structure.
struct Group: Identifiable {
    let id: String
    let name: String
    let description: String
    let memberCount: Int
    let members: [Member]
    let sharedLocations: [SharedLocation]
    let isChatEnabled: Bool
    let privacySettings: String
}

// MARK: - 2. Mock API Service (Placeholder for APIService.shared)

/// Mock implementation of the APIService for fetching group data.
class MockAPIService {
    static let shared = MockAPIService()
    
    private init() {}
    
    /// Simulates fetching group details with a delay.
    func fetchGroupDetails(groupId: String) -> AnyPublisher<Group, Error> {
        Future<Group, Error> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                let mockMembers: [Member] = [
                    Member(id: "m1", name: "Alice Johnson", avatarURL: "url1", role: .owner, isOnline: true),
                    Member(id: "m2", name: "Bob Smith", avatarURL: "url2", role: .admin, isOnline: true),
                    Member(id: "m3", name: "Charlie Brown", avatarURL: "url3", role: .member, isOnline: false),
                    Member(id: "m4", name: "Diana Prince", avatarURL: "url4", role: .member, isOnline: true),
                    Member(id: "m5", name: "Eve Adams", avatarURL: "url5", role: .guest, isOnline: false)
                ]
                
                let mockLocations: [SharedLocation] = [
                    SharedLocation(memberName: "Alice Johnson", latitude: 34.0522, longitude: -118.2437, timestamp: Date()), // LA
                    SharedLocation(memberName: "Bob Smith", latitude: 34.0522 + 0.01, longitude: -118.2437 + 0.01, timestamp: Date())
                ]
                
                let mockGroup = Group(
                    id: groupId,
                    name: "The SwiftUI Crew",
                    description: "A group for discussing all things SwiftUI and iOS development.",
                    memberCount: mockMembers.count,
                    members: mockMembers,
                    sharedLocations: mockLocations,
                    isChatEnabled: true,
                    privacySettings: "Private"
                )
                
                // Simulate an error or empty state occasionally for robustness
                if groupId == "error" {
                    promise(.failure(NSError(domain: "", code: 0, userInfo: [NSLocalizedDescriptionKey: "Failed to fetch group data."])))
                } else if groupId == "empty" {
                    let emptyGroup = Group(id: "empty", name: "Empty Group", description: "No members yet.", memberCount: 0, members: [], sharedLocations: [], isChatEnabled: false, privacySettings: "Public")
                    promise(.success(emptyGroup))
                } else {
                    promise(.success(mockGroup))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - 3. ViewModel

/// ViewModel for the GroupDetailView, handling data fetching and state management.
class GroupDetailViewModel: ObservableObject {
    @Published var state: GroupDetailState = .loading
    @Published var group: Group?
    @Published var isLocationSharingEnabled: Bool = false // New state for location sharing
    
    private var cancellables = Set<AnyCancellable>()
    private let groupId: String
    
    init(groupId: String) {
        self.groupId = groupId
        // Initialize location sharing state from a mock user default or API
        self.isLocationSharingEnabled = true 
    }
    
    /// Fetches the group details using the mock API service.
    func fetchGroupDetails() {
        state = .loading
        
        MockAPIService.shared.fetchGroupDetails(groupId: groupId)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                guard let self = self else { return }
                switch completion {
                case .failure(let error):
                    self.state = .error(error.localizedDescription)
                case .finished:
                    break
                }
            } receiveValue: { [weak self] group in
                guard let self = self else { return }
                self.group = group
                if group.memberCount == 0 {
                    self.state = .empty
                } else {
                    self.state = .loaded
                }
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Intent Handlers (Placeholders)
    
    func openChat() {
        print("Opening chat for \(group?.name ?? "group")")
        // Implementation for WebSocket connection and chat view navigation
    }
    
    func openSettings() {
        print("Opening settings for \(group?.name ?? "group")")
        // Implementation for navigation to settings view
    }
    
    func leaveGroup() {
        print("Attempting to leave group \(group?.name ?? "group")")
        // Implementation for confirmation and API call to leave group
    }
    
    func toggleLocationSharing() {
        isLocationSharingEnabled.toggle()
        print("Location sharing toggled to: \(isLocationSharingEnabled)")
        // In a real app, this would call APIService.shared.updatePrivacySettings(...)
    }
}

// MARK: - 4. View Components

extension Color {
    static let naviBlue = Color(hex: "#2563EB")
    
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

struct GroupInfoSection: View {
    let group: Group
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(group.name)
                .font(.largeTitle.bold())
                .foregroundColor(.primary)
                .accessibilityLabel("Group name: \(group.name)")
            
            Text(group.description)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .accessibilityLabel("Group description: \(group.description)")
            
            HStack {
                Image(systemName: "person.3.fill")
                Text("\(group.memberCount) Members")
                    .font(.caption)
            }
            .foregroundColor(.gray)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.systemBackground))
    }
}

struct MemberRow: View {
    let member: Member
    
    var body: some View {
        HStack {
            // Profile Card/Avatar (Placeholder for Image Caching)
            // In a production app, this would use an async image loader with caching (e.g., Kingfisher, SDWebImage)
            ZStack(alignment: .bottomTrailing) {
                Circle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: 40, height: 40)
                    .overlay(Text(String(member.name.prefix(1))).foregroundColor(.white))
                    .accessibilityLabel("Profile picture for \(member.name)")
                
                if member.isOnline {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 10, height: 10)
                        .overlay(Circle().stroke(Color(.systemBackground), lineWidth: 1.5))
                        .accessibilityLabel("Online indicator")
                }
            }
            
            VStack(alignment: .leading) {
                Text(member.name)
                    .font(.headline)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8) // Dynamic Type support
                
                Text(member.role.rawValue)
                    .font(.caption)
                    .foregroundColor(member.role.color)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(member.role.color.opacity(0.1))
                    .cornerRadius(4)
                    .accessibilityLabel("Role: \(member.role.rawValue)")
            }
            
            Spacer()
            
            if member.role == .owner || member.role == .admin {
                Image(systemName: "shield.lefthalf.filled")
                    .foregroundColor(.naviBlue)
                    .accessibilityLabel("Group management badge")
            }
        }
        .padding(.vertical, 5)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Tap to view profile")
    }
}

struct MemberListSection: View {
    let members: [Member]
    
    var body: some View {
        VStack(alignment: .leading) {
            Text("Group Members (\(members.count))")
                .font(.title2.bold())
                .padding(.horizontal)
                .padding(.top)
            
            // Efficient list updates using LazyVStack
            LazyVStack {
                ForEach(members.sorted(by: { $0.role.hashValue < $1.role.hashValue })) { member in
                    MemberRow(member: member)
                        .padding(.horizontal)
                    Divider()
                }
            }
        }
        .background(Color(.systemBackground))
    }
}

struct MapSection: View {
    let locations: [SharedLocation]
    
    var body: some View {
        VStack(alignment: .leading) {
            Text("Shared Locations")
                .font(.title2.bold())
                .padding(.horizontal)
                .padding(.top)
            
            if locations.isEmpty {
                Text("No locations currently shared.")
                    .foregroundColor(.secondary)
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Color(.secondarySystemBackground))
                    .cornerRadius(8)
                    .padding(.horizontal)
            } else {
                let region = MKCoordinateRegion(
                    center: CLLocationCoordinate2D(latitude: locations.first?.latitude ?? 34.0522, longitude: locations.first?.longitude ?? -118.2437),
                    span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
                )
                
                Map(coordinateRegion: .constant(region), annotationItems: locations) { location in
                    MapAnnotation(coordinate: CLLocationCoordinate2D(latitude: location.latitude, longitude: location.longitude)) {
                        VStack {
                            Image(systemName: "mappin.circle.fill")
                                .foregroundColor(.red)
                            Text(location.memberName)
                                .font(.caption2)
                        }
                    }
                }
                .frame(height: 200)
                .cornerRadius(10)
                .padding(.horizontal)
                .accessibilityLabel("Map showing \(locations.count) shared locations")
            }
        }
        .padding(.bottom)
    }
}

struct ActionButtonsSection: View {
    @ObservedObject var viewModel: GroupDetailViewModel
    
    var body: some View {
        VStack(spacing: 15) {
            Button {
                viewModel.openChat()
            } label: {
                Label("Group Chat", systemImage: "message.fill")
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.naviBlue)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                    .overlay(
                        // Simple visual element to hint at chat bubbles/activity
                        Image(systemName: "bubble.left.and.bubble.right.fill")
                            .font(.caption2)
                            .foregroundColor(.white)
                            .padding(4)
                            .background(Color.red)
                            .clipShape(Circle())
                            .offset(x: 10, y: -10)
                            .opacity(0.8)
                        , alignment: .topTrailing
                    )
            }
            .accessibilityHint("Opens the real-time group chat")
            
            HStack {
                Button {
                    viewModel.openSettings()
                } label: {
                    Label("Settings", systemImage: "gearshape.fill")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color(.systemGray5))
                        .foregroundColor(.primary)
                        .cornerRadius(10)
                }
                
                Button {
                    viewModel.leaveGroup()
                } label: {
                    Label("Leave Group", systemImage: "figure.walk.motion")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.red.opacity(0.1))
                        .foregroundColor(.red)
                        .cornerRadius(10)
                }
            }
            
            Toggle("Share My Location", isOn: $viewModel.isLocationSharingEnabled)
                .padding()
                .background(Color(.systemGray5))
                .cornerRadius(10)
                .accessibilityValue(viewModel.isLocationSharingEnabled ? "On" : "Off")
        }
        .padding()
    }
}

// MARK: - 5. Main View

struct GroupDetailView: View {
    @StateObject var viewModel: GroupDetailViewModel
    
    init(groupId: String) {
        _viewModel = StateObject(wrappedValue: GroupDetailViewModel(groupId: groupId))
    }
    
    var body: some View {
        NavigationView {
            Group {
                switch viewModel.state {
                case .loading:
                    ProgressView("Loading Group Details...")
                        .progressViewStyle(CircularProgressViewStyle(tint: .naviBlue))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .onAppear {
                            viewModel.fetchGroupDetails()
                        }
                        .accessibilityLabel("Loading state")
                    
                case .error(let message):
                    VStack {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.largeTitle)
                            .foregroundColor(.red)
                        Text("Error Loading Group")
                            .font(.headline)
                        Text(message)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Button("Retry") {
                            viewModel.fetchGroupDetails()
                        }
                        .padding(.top)
                        .buttonStyle(.borderedProminent)
                        .tint(.naviBlue)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityLabel("Error state: \(message)")
                    
                case .empty:
                    VStack {
                        Image(systemName: "person.fill.badge.plus")
                            .font(.largeTitle)
                            .foregroundColor(.naviBlue)
                        Text("Group is Empty")
                            .font(.headline)
                        Text("Be the first to invite members!")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityLabel("Empty state")
                    
                case .loaded:
                    if let group = viewModel.group {
                        ScrollView {
                            VStack(spacing: 20) {
                                GroupInfoSection(group: group)
                                
                                ActionButtonsSection(viewModel: viewModel)
                                
                                MemberListSection(members: group.members)
                                
                                MapSection(locations: group.sharedLocations)
                            }
                            .padding(.bottom, 20)
                        }
                        .navigationTitle(group.name)
                        .navigationBarTitleDisplayMode(.inline)
                        .background(Color(.systemGroupedBackground).ignoresSafeArea())
                    } else {
                        // Should not happen if state is .loaded, but for safety
                        Text("Group data missing.")
                    }
                }
            }
        }
        .onAppear {
            // Ensure data is fetched on first appearance
            if viewModel.group == nil && viewModel.state != .loading {
                viewModel.fetchGroupDetails()
            }
        }
    }
}

// MARK: - Preview

struct GroupDetailView_Previews: PreviewProvider {
    static var previews: some View {
        GroupDetailView(groupId: "mock_group_id")
        
        // Example of error state preview
        GroupDetailView(groupId: "error")
            .previewDisplayName("Error State")
        
        // Example of empty state preview
        GroupDetailView(groupId: "empty")
            .previewDisplayName("Empty State")
    }
}

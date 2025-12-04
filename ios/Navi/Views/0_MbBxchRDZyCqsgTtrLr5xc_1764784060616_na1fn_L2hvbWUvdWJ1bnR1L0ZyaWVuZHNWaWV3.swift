import SwiftUI
import Combine

// MARK: - 1. Constants and Extensions

extension Color {
    // Navi blue (#2563EB)
    static let naviBlue = Color(red: 0.145, green: 0.388, blue: 0.922)
    static let onlineGreen = Color.green
    static let offlineGray = Color.gray
    static let locationRed = Color.red
}

extension Date {
    func timeAgoDisplay() -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: self, relativeTo: Date())
    }
}

// MARK: - 2. Model

struct Friend: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let avatarURL: String // Placeholder for image caching/loading
    var isOnline: Bool
    var lastSeen: Date
    var isSharingLocation: Bool
    var statusBadge: String? // e.g., "Pro", "Admin"
}

enum LoadingState {
    case idle
    case loading
    case loaded
    case empty
    case error(String)
}

// MARK: - 3. API Service Stub (Simulating APIService.shared)

class APIService {
    static let shared = APIService()
    
    // A Combine publisher to simulate real-time updates
    let friendsPublisher = CurrentValueSubject<[Friend], Never>([])
    
    private var friendsData: [Friend] = [
        Friend(name: "Alex Johnson", avatarURL: "avatar_alex", isOnline: true, lastSeen: Date().addingTimeInterval(-60), isSharingLocation: true, statusBadge: "Pro"),
        Friend(name: "Sarah Connor", avatarURL: "avatar_sarah", isOnline: false, lastSeen: Date().addingTimeInterval(-3600), isSharingLocation: false, statusBadge: nil),
        Friend(name: "Mike Ross", avatarURL: "avatar_mike", isOnline: true, lastSeen: Date().addingTimeInterval(-10), isSharingLocation: true, statusBadge: "Admin"),
        Friend(name: "Jessica Pearson", avatarURL: "avatar_jessica", isOnline: false, lastSeen: Date().addingTimeInterval(-86400), isSharingLocation: false, statusBadge: nil)
    ]
    
    private var timer: AnyCancellable?
    
    private init() {
        // Simulate initial fetch
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            self.friendsPublisher.send(self.friendsData)
            self.startRealTimeUpdates()
        }
    }
    
    private func startRealTimeUpdates() {
        // Simulate real-time status changes every 5 seconds
        timer = Timer.publish(every: 5, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                guard let self = self else { return }
                
                // Toggle online status for a random friend
                if let randomIndex = self.friendsData.indices.randomElement() {
                    self.friendsData[randomIndex].isOnline.toggle()
                    self.friendsData[randomIndex].lastSeen = Date()
                }
                
                // Simulate a new friend request occasionally
                if Int.random(in: 1...10) == 1 {
                    print("Simulating new friend request...")
                }
                
                self.friendsPublisher.send(self.friendsData)
            }
    }
    
    func fetchFriends() {
        // In a real app, this would be an async network call
        // For now, it just ensures the publisher is active
        print("Fetching friends...")
    }
    
    func sendFriendRequest(to name: String) {
        print("Sending friend request to \(name)...")
    }
    
    func toggleLocationSharing(for friend: Friend) {
        if let index = friendsData.firstIndex(where: { $0.id == friend.id }) {
            friendsData[index].isSharingLocation.toggle()
            friendsPublisher.send(friendsData)
            print("Toggled location sharing for \(friend.name) to \(friendsData[index].isSharingLocation)")
        }
    }
}

// MARK: - 4. ViewModel (MVVM with @StateObject and Combine)

class FriendsViewModel: ObservableObject {
    @Published var friends: [Friend] = []
    @Published var loadingState: LoadingState = .idle
    @Published var hasNewFriendRequests: Bool = false
    
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        setupSubscriptions()
        fetchFriends()
    }
    
    private func setupSubscriptions() {
        loadingState = .loading
        
        APIService.shared.friendsPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] newFriends in
                guard let self = self else { return }
                
                // Efficient list updates (SwiftUI handles the diffing, but we ensure the data is sorted)
                self.friends = newFriends.sorted { $0.isOnline && !$1.isOnline }
                
                if self.friends.isEmpty {
                    self.loadingState = .empty
                } else {
                    self.loadingState = .loaded
                }
                
                // Simulate checking for new friend requests
                self.hasNewFriendRequests = Int.random(in: 1...10) > 8
                
            }
            .store(in: &cancellables)
    }
    
    func fetchFriends() {
        loadingState = .loading
        APIService.shared.fetchFriends()
        // Error simulation
        if Int.random(in: 1...100) == 1 {
            loadingState = .error("Failed to load friends list. Please try again.")
        }
    }
    
    func addFriend() {
        // Placeholder for navigation or modal presentation
        print("Navigate to Add Friend screen.")
    }
    
    func message(friend: Friend) {
        print("Opening chat with \(friend.name). (WebSocket integration placeholder)")
    }
    
    func locate(friend: Friend) {
        print("Locating \(friend.name). (Location sharing control check)")
    }
    
    func toggleLocationSharing(for friend: Friend) {
        APIService.shared.toggleLocationSharing(for: friend)
    }
}

// MARK: - 5. View (FriendsView.swift)

struct FriendsView: View {
    @StateObject var viewModel = FriendsViewModel()
    
    var body: some View {
        NavigationView {
            ZStack {
                content
                floatingActionButton
            }
            .navigationTitle("Friends")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Groups") {
                        print("Navigate to Group Management")
                    }
                    .foregroundColor(.naviBlue)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        print("Navigate to Privacy Settings")
                    } label: {
                        Image(systemName: "gearshape.fill")
                            .foregroundColor(.naviBlue)
                    }
                }
            }
        }
    }
    
    @ViewBuilder
    private var content: some View {
        switch viewModel.loadingState {
        case .idle, .loading:
            ProgressView("Loading Friends...")
                .scaleEffect(1.5)
                .foregroundColor(.naviBlue)
        case .empty:
            emptyStateView
        case .error(let message):
            errorStateView(message: message)
        case .loaded:
            friendListView
        }
    }
    
    private var friendListView: some View {
        List {
            if viewModel.hasNewFriendRequests {
                friendRequestCard
            }
            
            ForEach(viewModel.friends) { friend in
                FriendRow(friend: friend, viewModel: viewModel)
                    // Performance: efficient list updates handled by ForEach and Identifiable
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
            }
        }
        .listStyle(.plain)
        .refreshable {
            viewModel.fetchFriends()
        }
    }
    
    private var friendRequestCard: some View {
        HStack {
            Image(systemName: "person.badge.plus.fill")
                .foregroundColor(.naviBlue)
                .font(.title2)
            VStack(alignment: .leading) {
                Text("New Friend Requests")
                    .font(.headline)
                Text("You have pending requests to review.")
                    .font(.subheadline)
                    .foregroundColor(.gray)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundColor(.gray)
        }
        .padding()
        .background(Color.naviBlue.opacity(0.1))
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.naviBlue, lineWidth: 1)
        )
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("New Friend Requests. Tap to review.")
    }
    
    private var emptyStateView: some View {
        VStack {
            Image(systemName: "person.3.fill")
                .font(.largeTitle)
                .foregroundColor(.naviBlue)
                .padding()
            Text("No Friends Yet")
                .font(.title2)
                .fontWeight(.bold)
            Text("Start connecting with people! Tap the '+' button to add your first friend.")
                .multilineTextAlignment(.center)
                .foregroundColor(.gray)
                .padding(.horizontal)
        }
    }
    
    private func errorStateView(message: String) -> some View {
        VStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundColor(.red)
                .padding()
            Text("Error")
                .font(.title2)
                .fontWeight(.bold)
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundColor(.gray)
                .padding(.horizontal)
            Button("Retry") {
                viewModel.fetchFriends()
            }
            .padding()
            .background(Color.naviBlue)
            .foregroundColor(.white)
            .cornerRadius(8)
            .padding(.top)
        }
    }
    
    private var floatingActionButton: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()
                Button {
                    viewModel.addFriend()
                } label: {
                    Image(systemName: "plus")
                        .font(.title.weight(.semibold))
                        .foregroundColor(.white)
                        .padding(20)
                        .background(Color.naviBlue)
                        .clipShape(Circle())
                        .shadow(color: .gray, radius: 5, x: 0, y: 5)
                }
                .padding(.trailing, 20)
                .padding(.bottom, 20)
                .accessibilityLabel("Add New Friend")
            }
        }
    }
}

// MARK: - 6. Subviews

struct FriendRow: View {
    let friend: Friend
    @ObservedObject var viewModel: FriendsViewModel
    
    var body: some View {
        HStack(alignment: .top, spacing: 15) {
            avatarView
            
            VStack(alignment: .leading, spacing: 4) {
                nameAndBadge
                statusAndLastSeen
                actionButtons
            }
            .padding(.vertical, 5)
            
            Spacer()
            
            if friend.isSharingLocation {
                locationIndicator
            }
        }
        .padding(.vertical, 8)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("\(friend.name). Status: \(friend.isOnline ? "Online" : "Offline"). Last seen \(friend.lastSeen.timeAgoDisplay()).")
    }
    
    private var avatarView: some View {
        ZStack(alignment: .bottomTrailing) {
            // Placeholder for Image Caching (e.g., Kingfisher or AsyncImage in a real app)
            Circle()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 60, height: 60)
                .overlay(
                    Text(String(friend.name.prefix(1)))
                        .font(.title)
                        .foregroundColor(.white)
                )
            
            // Online Status Indicator
            Circle()
                .fill(friend.isOnline ? Color.onlineGreen : Color.offlineGray)
                .frame(width: 15, height: 15)
                .overlay(
                    Circle()
                        .stroke(Color.white, lineWidth: 2)
                )
                .offset(x: 3, y: 3)
                .accessibilityHidden(true)
        }
    }
    
    private var nameAndBadge: some View {
        HStack {
            Text(friend.name)
                .font(.headline)
                .foregroundColor(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            
            if let badge = friend.statusBadge {
                Text(badge)
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.naviBlue)
                    .cornerRadius(5)
                    .accessibilityLabel("\(badge) Badge")
            }
        }
    }
    
    private var statusAndLastSeen: some View {
        HStack(spacing: 5) {
            Text(friend.isOnline ? "Online" : "Offline")
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundColor(friend.isOnline ? .onlineGreen : .offlineGray)
            
            if !friend.isOnline {
                Text("• Last seen \(friend.lastSeen.timeAgoDisplay())")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }
    
    private var actionButtons: some View {
        HStack(spacing: 10) {
            // Message Button (Chat Bubble Design)
            Button {
                viewModel.message(friend: friend)
            } label: {
                HStack {
                    Image(systemName: "message.fill")
                    Text("Message")
                }
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundColor(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(Color.naviBlue)
                .clipShape(Capsule())
            }
            .accessibilityLabel("Message \(friend.name)")
            
            // Locate/Location Sharing Button
            Button {
                if friend.isSharingLocation {
                    viewModel.locate(friend: friend)
                } else {
                    // Placeholder for a privacy-controlled action
                    print("Prompting \(friend.name) to share location.")
                }
            } label: {
                HStack {
                    Image(systemName: friend.isSharingLocation ? "location.fill" : "location.slash.fill")
                    Text(friend.isSharingLocation ? "Locate" : "Request Loc.")
                }
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundColor(friend.isSharingLocation ? .white : .naviBlue)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(friend.isSharingLocation ? Color.locationRed : Color.naviBlue.opacity(0.1))
                .clipShape(Capsule())
            }
            .accessibilityLabel(friend.isSharingLocation ? "Locate \(friend.name)" : "Request location from \(friend.name)")
        }
        .padding(.top, 5)
    }
    
    private var locationIndicator: some View {
        VStack {
            Image(systemName: "map.fill")
                .foregroundColor(.locationRed)
                .font(.title3)
            Text("Sharing")
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .accessibilityLabel("Location Sharing Active")
    }
}

// MARK: - 7. Preview

struct FriendsView_Previews: PreviewProvider {
    static var previews: some View {
        FriendsView()
            .environment(\.dynamicTypeSize, .large) // Dynamic Type Test
    }
}

// MARK: - 8. Privacy Control Example (Conceptual)

// This struct would typically be in a separate file, but included here for completeness
struct PrivacySettingsView: View {
    @State private var isLocationSharingEnabled = true
    
    var body: some View {
        Form {
            Section(header: Text("Location Sharing Controls")) {
                Toggle("Share My Location", isOn: $isLocationSharingEnabled)
                    .accessibilityValue(isLocationSharingEnabled ? "On" : "Off")
                
                Text("Control who can see your location and when.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Section(header: Text("Data Export")) {
                Button("Export My Data") {
                    print("Initiating data export process...")
                }
                .foregroundColor(.naviBlue)
            }
        }
        .navigationTitle("Privacy Settings")
    }
}

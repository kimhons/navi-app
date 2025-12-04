import SwiftUI
import Combine

// MARK: - 1. Design Constants

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

// MARK: - 2. Data Models (M)

struct Friend: Identifiable, Equatable {
    let id: String
    let name: String
    let avatarURL: URL?
    let isOnline: Bool
    let tripsCount: Int
    let placesVisitedCount: Int
    let sharedTrips: [Trip]
    let lastKnownLocation: String?
}

struct Trip: Identifiable, Equatable {
    let id: String
    let name: String
    let date: Date
    let imageURL: URL?
}

struct ChatMessage: Identifiable, Equatable {
    let id = UUID()
    let text: String
    let isFromFriend: Bool
    let timestamp: Date
}

enum LoadingState: Equatable {
    case idle
    case loading
    case loaded
    case empty
    case error(String)
}

// MARK: - 3. API Service Stub

class APIService {
    static let shared = APIService()
    
    private let mockFriend = Friend(
        id: "123",
        name: "Alex Johnson",
        avatarURL: URL(string: "https://example.com/alex.jpg"), // Placeholder
        isOnline: true,
        tripsCount: 14,
        placesVisitedCount: 32,
        sharedTrips: [
            Trip(id: "t1", name: "European Backpacking", date: Calendar.current.date(byAdding: .month, value: -3, to: Date())!, imageURL: nil),
            Trip(id: "t2", name: "Ski Trip to Aspen", date: Calendar.current.date(byAdding: .year, value: -1, to: Date())!, imageURL: nil)
        ],
        lastKnownLocation: "San Francisco, CA"
    )
    
    // Simulate a data fetch for a friend profile
    func fetchFriendProfile(friendId: String) -> AnyPublisher<Friend, Error> {
        // Simulate network delay
        return Just(mockFriend)
            .delay(for: .seconds(1.5), scheduler: RunLoop.main)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
    
    // Simulate a WebSocket connection for real-time updates (online status)
    func realTimeStatusPublisher(friendId: String) -> AnyPublisher<Bool, Never> {
        // Simulates status changing after a delay
        return Just(true)
            .delay(for: .seconds(2), scheduler: RunLoop.main)
            .prepend(mockFriend.isOnline) // Start with initial status
            .eraseToAnyPublisher()
    }
    
    // Simulate unfriend action
    func unfriend(friendId: String) -> AnyPublisher<Void, Error> {
        // Simulate network delay and success
        return Just(())
            .delay(for: .seconds(1), scheduler: RunLoop.main)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
    
    // Simulate fetching chat messages (for WebSocket/chat feature)
    func fetchChatHistory(friendId: String) -> AnyPublisher<[ChatMessage], Error> {
        let mockMessages = [
            ChatMessage(text: "Hey, are you free this weekend?", isFromFriend: true, timestamp: Date().addingTimeInterval(-3600)),
            ChatMessage(text: "Yeah, what's up?", isFromFriend: false, timestamp: Date().addingTimeInterval(-3500)),
            ChatMessage(text: "Thinking of a road trip!", isFromFriend: true, timestamp: Date().addingTimeInterval(-3400))
        ]
        return Just(mockMessages)
            .delay(for: .seconds(0.5), scheduler: RunLoop.main)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
}

// MARK: - 4. View Model (VM)

class FriendProfileViewModel: ObservableObject {
    @Published var friend: Friend?
    @Published var loadingState: LoadingState = .idle
    @Published var isLocationShared: Bool = false
    @Published var isOnline: Bool = false
    @Published var chatMessages: [ChatMessage] = []
    
    private var cancellables = Set<AnyCancellable>()
    private let friendId: String
    
    init(friendId: String) {
        self.friendId = friendId
        
        // Initial setup for location sharing (simulated from a setting)
        self.isLocationShared = UserDefaults.standard.bool(forKey: "isLocationShared_\(friendId)")
        
        // Real-time online status subscription
        APIService.shared.realTimeStatusPublisher(friendId: friendId)
            .receive(on: DispatchQueue.main)
            .assign(to: &self.$isOnline)
        
        // Chat history subscription (simulating initial load)
        APIService.shared.fetchChatHistory(friendId: friendId)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                if case .failure(let error) = completion {
                    print("Failed to load chat history: \(error.localizedDescription)")
                }
            } receiveValue: { [weak self] messages in
                self?.chatMessages = messages
            }
            .store(in: &cancellables)
    }
    
    func loadProfile() {
        guard loadingState != .loading else { return }
        loadingState = .loading
        
        APIService.shared.fetchFriendProfile(friendId: friendId)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                guard let self = self else { return }
                switch completion {
                case .failure(let error):
                    self.loadingState = .error(error.localizedDescription)
                case .finished:
                    if self.friend == nil {
                        self.loadingState = .empty
                    } else {
                        self.loadingState = .loaded
                    }
                }
            } receiveValue: { [weak self] friend in
                self?.friend = friend
            }
            .store(in: &cancellables)
    }
    
    func toggleLocationSharing() {
        isLocationShared.toggle()
        // In a real app, this would call an API to update the setting
        UserDefaults.standard.set(isLocationShared, forKey: "isLocationShared_\(friendId)")
    }
    
    func performUnfriend() {
        loadingState = .loading // Optional: show loading state during action
        
        APIService.shared.unfriend(friendId: friendId)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                guard let self = self else { return }
                switch completion {
                case .failure(let error):
                    // Handle error, maybe show an alert
                    self.loadingState = .error("Failed to unfriend: \(error.localizedDescription)")
                case .finished:
                    // Successfully unfriended. Transition to an empty state or navigate away.
                    self.loadingState = .empty
                    // In a real app, you'd likely dismiss the view or navigate to a different screen
                }
            } receiveValue: { _ in
                // Success is handled in the completion block
            }
            .store(in: &cancellables)
    }
}

// MARK: - 5. View (V)

struct FriendProfileView: View {
    @StateObject var viewModel: FriendProfileViewModel
    
    init(friendId: String) {
        _viewModel = StateObject(wrappedValue: FriendProfileViewModel(friendId: friendId))
    }
    
    var body: some View {
        Group {
            switch viewModel.loadingState {
            case .idle, .loading:
                ProgressView("Loading Profile...")
            case .loaded:
                loadedContentView
            case .empty:
                Text("Friend profile not found.")
                    .foregroundColor(.gray)
            case .error(let message):
                VStack {
                    Text("Error loading profile.")
                        .foregroundColor(.red)
                    Text(message)
                        .font(.caption)
                }
            }
        }
        .onAppear {
            if viewModel.loadingState == .idle {
                viewModel.loadProfile()
            }
        }
        .navigationTitle(viewModel.friend?.name ?? "Profile")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    @ViewBuilder
    private var loadedContentView: some View {
        if let friend = viewModel.friend {
            ScrollView {
                VStack(spacing: 20) {
                    // MARK: - Profile Header
                    VStack(spacing: 10) {
                        ZStack(alignment: .bottomTrailing) {
                            // Avatar (In a production app, this would use an image caching library like Kingfisher)
                            Image(systemName: "person.circle.fill")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 100, height: 100)
                                .foregroundColor(.gray)
                                .clipShape(Circle())
                                .overlay(Circle().stroke(Color.naviBlue, lineWidth: 4))
                                .accessibilityLabel("Profile picture of \(friend.name)")
                            
                            // Online Indicator
                            if viewModel.isOnline {
                                Circle()
                                    .fill(Color.green)
                                    .frame(width: 20, height: 20)
                                    .overlay(Circle().stroke(Color.white, lineWidth: 3))
                                    .offset(x: 5, y: 5)
                                    .accessibilityLabel("Online status indicator")
                            }
                        }
                        
                        Text(friend.name)
                            .font(.title)
                            .fontWeight(.bold)
                            .accessibilityAddTraits(.isHeader)
                        
                        // Stats Card
                        HStack {
                            StatView(value: friend.tripsCount, label: "Trips")
                            Divider()
                            StatView(value: friend.placesVisitedCount, label: "Places Visited")
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal)
                        .accessibilityElement(children: .contain)
                        .accessibilityLabel("Travel statistics")
                    }
                    .padding(.top, 20)
                    
                    // MARK: - Actions Card
                    ProfileCard(title: "Actions") {
                        VStack(spacing: 15) {
                            // Location Sharing Toggle
                            Toggle(isOn: $viewModel.isLocationShared) {
                                Label("Share My Location", systemImage: "location.fill")
                            }
                            .tint(.naviBlue)
                            .accessibilityValue(viewModel.isLocationShared ? "On" : "Off")
                            
                            // Unfriend Button
                            Button(role: .destructive) {
                                viewModel.performUnfriend()
                            } label: {
                                Label("Unfriend \(friend.name)", systemImage: "person.crop.circle.badge.minus")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.red)
                            .accessibilityHint("Removes \(friend.name) from your friends list.")
                        }
                    }
                    
                    // MARK: - Shared Trips Card
                    SharedTripsCard(trips: friend.sharedTrips)
                    
                    // MARK: - Chat Section
                    ChatSection(messages: viewModel.chatMessages)
                    
                }
                .padding()
            }
        } else {
            // Should not happen if loadingState is .loaded, but for safety
            Text("Error: Friend data missing.")
        }
    }
}

// MARK: - 6. Helper Views

struct StatView: View {
    let value: Int
    let label: String
    
    var body: some View {
        VStack {
            Text("\(value)")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.naviBlue)
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(value) \(label)")
    }
}

struct SharedTripsCard: View {
    let trips: [Trip]
    
    var body: some View {
        ProfileCard(title: "Shared Trips (\(trips.count))") {
            if trips.isEmpty {
                Text("No shared trips yet.")
                    .foregroundColor(.secondary)
            } else {
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(trips) { trip in
                        HStack {
                            Image(systemName: "map.fill")
                                .foregroundColor(.naviBlue)
                            VStack(alignment: .leading) {
                                Text(trip.name)
                                    .font(.subheadline)
                                Text(trip.date, style: .date)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 5)
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel("Trip: \(trip.name), on \(trip.date, style: .date)")
                    }
                }
            }
        }
    }
}

struct ChatSection: View {
    let messages: [ChatMessage]
    
    var body: some View {
        ProfileCard(title: "Recent Chat") {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(messages) { message in
                    ChatBubble(message: message)
                }
                
                // Link to full chat
                HStack {
                    Spacer()
                    Button("View Full Chat") {
                        // Action to navigate to full chat view
                    }
                    .font(.caption)
                    .foregroundColor(.naviBlue)
                    Spacer()
                }
            }
        }
    }
}

struct ChatBubble: View {
    let message: ChatMessage
    
    var body: some View {
        HStack {
            if message.isFromFriend {
                Spacer()
            }
            
            VStack(alignment: message.isFromFriend ? .trailing : .leading, spacing: 4) {
                Text(message.text)
                    .padding(10)
                    .background(message.isFromFriend ? Color.naviBlue : Color(.systemGray5))
                    .foregroundColor(message.isFromFriend ? .white : .primary)
                    .cornerRadius(15, corners: message.isFromFriend ? [.topLeft, .bottomLeft, .bottomRight] : [.topRight, .bottomLeft, .bottomRight])
                    .accessibilityLabel(message.isFromFriend ? "Friend said: \(message.text)" : "You said: \(message.text)")
                
                Text(message.timestamp, style: .time)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            
            if !message.isFromFriend {
                Spacer()
            }
        }
    }
}

// Custom corner radius extension for chat bubbles
extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners
    
    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}

struct ProfileCard<Content: View>: View {
    let title: String
    let content: Content
    
    init(title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }
    
    var body: some View {
        VStack(alignment: .leading) {
            Text(title)
                .font(.headline)
                .foregroundColor(.primary)
            
            Divider()
            
            content
        }
        .padding()
        .background(Color.white)
        .cornerRadius(12)
        .shadow(color: Color.black.opacity(0.05), radius: 5, x: 0, y: 2)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(title)
    }
}

struct BadgeView: View {
    let text: String
    let color: Color
    
    var body: some View {
        Text(text)
            .font(.caption2)
            .fontWeight(.bold)
            .foregroundColor(.white)
            .padding(.vertical, 4)
            .padding(.horizontal, 8)
            .background(color)
            .cornerRadius(10)
            .accessibilityLabel("Badge: \(text)")
    }
}

// MARK: - Preview

struct FriendProfileView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            FriendProfileView(friendId: "123")
        }
    }
}

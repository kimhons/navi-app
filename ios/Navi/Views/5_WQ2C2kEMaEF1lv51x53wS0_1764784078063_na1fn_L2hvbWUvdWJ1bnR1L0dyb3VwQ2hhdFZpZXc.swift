import SwiftUI
import Combine

// MARK: - Utility Extensions

extension Color {
    /// Navi Blue: #2563EB
    static let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)
}

// MARK: - Data Models

/// Represents a user in the chat system.
struct User: Identifiable, Equatable {
    let id: String
    let name: String
    let avatarURL: String
    let isOnline: Bool
    let badge: String? // e.g., "Admin", "Verified"
    
    static var current: User {
        User(id: "u1", name: "Current User", avatarURL: "user_avatar_1", isOnline: true, badge: "Pro")
    }
    
    static var friend: User {
        User(id: "u2", name: "Alex Johnson", avatarURL: "user_avatar_2", isOnline: true, badge: nil)
    }
    
    static var groupMember: User {
        User(id: "u3", name: "Sarah Connor", avatarURL: "user_avatar_3", isOnline: false, badge: "Mod")
    }
}

/// Represents a single message in the chat.
struct Message: Identifiable, Equatable {
    enum Content {
        case text(String)
        case photo(String) // URL or asset name
        case location(latitude: Double, longitude: Double)
    }
    
    enum Reaction: String, CaseIterable {
        case heart = "❤️"
        case thumbsUp = "👍"
        case laugh = "😂"
        case wow = "😮"
    }
    
    let id: String = UUID().uuidString
    let sender: User
    let content: Content
    let timestamp: Date
    var reactions: [User: Reaction] = [:]
    var isRead: Bool = false
    
    var isCurrentUser: Bool {
        sender.id == User.current.id
    }
}

/// Represents the chat group.
struct Chat: Identifiable {
    let id: String
    let name: String
    let members: [User]
    let isGroup: Bool
    var lastActive: Date
}

// MARK: - Mock APIService (Simulated)

/// A simulated API service for fetching social data.
class APIService {
    static let shared = APIService()
    
    func fetchFriends() -> [User] {
        // Mock implementation
        [User.friend, User.groupMember]
    }
    
    func fetchGroups() -> [Chat] {
        // Mock implementation
        [Chat(id: "c1", name: "SwiftUI Devs", members: [User.current, User.friend, User.groupMember], isGroup: true, lastActive: Date())]
    }
    
    func fetchAchievements() -> [String] {
        // Mock implementation
        ["Swift Master", "Community Leader"]
    }
}

// MARK: - Mock Data

extension Message {
    static func mockMessages(for user: User) -> [Message] {
        [
            Message(sender: User.friend, content: .text("Hey, did you see the new SwiftUI update?"), timestamp: Date().addingTimeInterval(-120)),
            Message(sender: user, content: .text("Not yet! Is it good? I'm working on the GroupChatView right now."), timestamp: Date().addingTimeInterval(-90), reactions: [User.friend: .heart]),
            Message(sender: User.friend, content: .photo("mock_photo_1"), timestamp: Date().addingTimeInterval(-60)),
            Message(sender: user, content: .text("Nice photo! Where was that taken?"), timestamp: Date().addingTimeInterval(-50)),
            Message(sender: User.friend, content: .location(latitude: 34.0522, longitude: -118.2437), timestamp: Date().addingTimeInterval(-30)),
            Message(sender: User.friend, content: .text("I'm also seeing a typing indicator issue. Can you check?"), timestamp: Date().addingTimeInterval(-10)),
            Message(sender: user, content: .text("On it!"), timestamp: Date().addingTimeInterval(-5)),
        ]
    }
}

// MARK: - ViewModel

/// The ViewModel for the GroupChatView, handling all business logic and state.
/// Architecture: MVVM with @StateObject ViewModel, Combine for real-time updates.
final class GroupChatViewModel: ObservableObject {
    
    // MARK: Published Properties (State)
    
    @Published var messages: [Message] = []
    @Published var isLoading: Bool = false
    @Published var error: Error?
    @Published var isTyping: Bool = false
    @Published var inputMessage: String = ""
    @Published var isLocationSharingEnabled: Bool = false
    
    // MARK: Private Properties
    
    private let chat: Chat
    private var cancellables = Set<AnyCancellable>()
    
    // Mock WebSocket/Real-time update publisher
    private let realTimePublisher = Timer.publish(every: 5, on: .main, in: .common).autoconnect()
    
    // MARK: Initialization
    
    init(chat: Chat) {
        self.chat = chat
        loadMessages()
        setupRealTimeUpdates()
    }
    
    // MARK: Public Methods
    
    /// Loads initial messages and simulates a network delay.
    func loadMessages() {
        isLoading = true
        // Simulate API call
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            guard let self = self else { return }
            self.messages = Message.mockMessages(for: User.current)
            self.isLoading = false
            // Simulate error state for demonstration
            // self.error = NSError(domain: "ChatError", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to connect to chat server."])
        }
    }
    
    /// Sends a new message (text, photo, or location).
    func sendMessage() {
        guard !inputMessage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        
        let newMessage = Message(
            sender: User.current,
            content: .text(inputMessage),
            timestamp: Date()
        )
        
        // Simulate sending via WebSocket
        messages.append(newMessage)
        inputMessage = ""
        
        // Simulate a delayed response from another user
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            self?.simulateIncomingMessage()
        }
    }
    
    /// Toggles an emoji reaction on a specific message.
    func toggleReaction(for message: Message, reaction: Message.Reaction) {
        guard let index = messages.firstIndex(where: { $0.id == message.id }) else { return }
        
        var messageToUpdate = messages[index]
        let currentUser = User.current
        
        if messageToUpdate.reactions[currentUser] == reaction {
            // Remove reaction
            messageToUpdate.reactions.removeValue(forKey: currentUser)
        } else {
            // Add or change reaction
            messageToUpdate.reactions[currentUser] = reaction
        }
        
        messages[index] = messageToUpdate
        // In a real app, this would be sent to the server
    }
    
    /// Simulates sharing the current location.
    func shareLocation() {
        guard isLocationSharingEnabled else { return }
        
        let locationMessage = Message(
            sender: User.current,
            content: .location(latitude: 34.0522, longitude: -118.2437), // Mock location
            timestamp: Date()
        )
        messages.append(locationMessage)
    }
    
    // MARK: Private Real-Time Simulation
    
    private func setupRealTimeUpdates() {
        realTimePublisher
            .sink { [weak self] _ in
                self?.simulateTypingIndicator()
            }
            .store(in: &cancellables)
    }
    
    private func simulateTypingIndicator() {
        // Simulate a random chance of a user typing
        if Bool.random() {
            isTyping = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
                self?.isTyping = false
            }
        }
    }
    
    private func simulateIncomingMessage() {
        let incomingMessage = Message(
            sender: User.friend,
            content: .text("Got it! I'll check the typing indicator issue now. Thanks!"),
            timestamp: Date()
        )
        messages.append(incomingMessage)
    }
}

// MARK: - Sub-Views

/// A view to display a single message in a chat bubble.
struct MessageBubbleView: View {
    @ObservedObject var viewModel: GroupChatViewModel
    let message: Message
    
    private var isCurrentUser: Bool {
        message.isCurrentUser
    }
    
    private var bubbleColor: Color {
        isCurrentUser ? .naviBlue : Color(.systemGray5)
    }
    
    private var textColor: Color {
        isCurrentUser ? .white : .primary
    }
    
    var body: some View {
        HStack(alignment: .bottom, spacing: 10) {
            if isCurrentUser {
                Spacer()
            }
            
            // Avatar for non-current user
            if !isCurrentUser {
                UserAvatarView(user: message.sender)
            }
            
            VStack(alignment: isCurrentUser ? .trailing : .leading, spacing: 4) {
                // Sender Name (for group chat)
                if !isCurrentUser {
                    Text(message.sender.name)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                // Message Content
                contentView
                    .padding(10)
                    .background(bubbleColor)
                    .foregroundColor(textColor)
                    .cornerRadius(15, corners: isCurrentUser ? [.topLeft, .bottomLeft, .bottomRight] : [.topRight, .bottomLeft, .bottomRight])
                
                // Reactions
                if !message.reactions.isEmpty {
                    reactionsView
                }
            }
            
            if !isCurrentUser {
                Spacer()
            }
            
            // Avatar for current user (optional, can be omitted for cleaner look)
            if isCurrentUser {
                // UserAvatarView(user: message.sender)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
    }
    
    @ViewBuilder
    private var contentView: some View {
        switch message.content {
        case .text(let text):
            Text(text)
                .font(.body)
                .accessibilityLabel(text)
        case .photo(let url):
            // In a real app, this would be an AsyncImage
            Image(systemName: "photo.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 150, height: 150)
                .background(Color.gray.opacity(0.3))
                .cornerRadius(10)
                .accessibilityLabel("Shared photo: \(url)")
        case .location(let lat, let lon):
            VStack(alignment: .leading) {
                Label("Location Shared", systemImage: "location.fill")
                    .font(.headline)
                Text("Lat: \(lat, specifier: "%.2f"), Lon: \(lon, specifier: "%.2f")")
                    .font(.caption)
            }
            .accessibilityLabel("Shared location at latitude \(lat) and longitude \(lon)")
        }
    }
    
    private var reactionsView: some View {
        HStack(spacing: 4) {
            ForEach(Array(message.reactions.keys), id: \.id) { user in
                if let reaction = message.reactions[user] {
                    Text(reaction.rawValue)
                        .font(.caption)
                        .padding(4)
                        .background(Color.white.opacity(0.8))
                        .clipShape(Circle())
                        .shadow(radius: 1)
                        .onTapGesture {
                            // Simulate toggling the reaction
                            viewModel.toggleReaction(for: message, reaction: reaction)
                        }
                        .accessibilityLabel("\(user.name) reacted with \(reaction.rawValue)")
                }
            }
        }
        .offset(x: isCurrentUser ? 0 : 10, y: -10) // Position reactions slightly outside the bubble
    }
}

/// A view to display a user's avatar and online status.
struct UserAvatarView: View {
    let user: User
    
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            // Placeholder for actual image loading
            Circle()
                .fill(Color.gray.opacity(0.5))
                .frame(width: 30, height: 30)
                .overlay(
                    Text(String(user.name.prefix(1)))
                        .foregroundColor(.white)
                )
            
            if user.isOnline {
                Circle()
                    .fill(Color.green)
                    .frame(width: 8, height: 8)
                    .overlay(Circle().stroke(Color.white, lineWidth: 1))
                    .offset(x: 2, y: 2)
            }
        }
        .accessibilityLabel("\(user.name) avatar. Status: \(user.isOnline ? "Online" : "Offline")")
    }
}

/// The input field for sending messages, photos, and location.
struct ChatInputView: View {
    @ObservedObject var viewModel: GroupChatViewModel
    
    var body: some View {
        VStack {
            if viewModel.isTyping {
                HStack {
                    Text("\(User.friend.name) is typing...")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                }
                .padding(.horizontal)
            }
            
            HStack {
                // Location Sharing Button
                Button {
                    viewModel.shareLocation()
                } label: {
                    Image(systemName: "location.circle.fill")
                        .font(.title2)
                        .foregroundColor(viewModel.isLocationSharingEnabled ? .naviBlue : .gray)
                }
                .disabled(!viewModel.isLocationSharingEnabled)
                .accessibilityLabel("Share location. Currently \(viewModel.isLocationSharingEnabled ? "enabled" : "disabled")")
                
                // Photo Sharing Button
                Button {
                    // Action to open photo picker
                } label: {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.title2)
                        .foregroundColor(.naviBlue)
                }
                .accessibilityLabel("Share photo")
                
                // Text Input Field
                TextField("Message...", text: $viewModel.inputMessage, axis: .vertical)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(Color(.systemGray6))
                    .cornerRadius(20)
                    .lineLimit(5)
                    .accessibilityValue(viewModel.inputMessage)
                    .accessibilityHint("Enter your message here")
                
                // Send Button
                Button {
                    viewModel.sendMessage()
                } label: {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.largeTitle)
                        .foregroundColor(.naviBlue)
                }
                .disabled(viewModel.inputMessage.isEmpty)
                .accessibilityLabel("Send message")
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
            .background(Color(.systemBackground))
        }
    }
}

// MARK: - Main View

/// The main Group Chat Screen.
struct GroupChatView: View {
    @StateObject var viewModel: GroupChatViewModel
    
    init(chat: Chat) {
        _viewModel = StateObject(wrappedValue: GroupChatViewModel(chat: chat))
    }
    
    var body: some View {
        VStack(spacing: 0) {
            if viewModel.isLoading {
                loadingState
            } else if let error = viewModel.error {
                errorState(error)
            } else if viewModel.messages.isEmpty {
                emptyState
            } else {
                chatContent
            }
            
            ChatInputView(viewModel: viewModel)
        }
        .navigationTitle(viewModel.chat.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    // Action for group info/settings
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .foregroundColor(.naviBlue)
                }
                .accessibilityLabel("Group settings and info")
            }
        }
        .onAppear {
            viewModel.loadMessages()
        }
    }
    
    // MARK: - State Views
    
    private var chatContent: some View {
        ScrollViewReader { proxy in
            List {
                ForEach(viewModel.messages) { message in
                    MessageBubbleView(viewModel: viewModel, message: message)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets())
                        .id(message.id)
                }
            }
            .listStyle(.plain)
            .onChange(of: viewModel.messages.count) { _ in
                // Scroll to bottom on new message
                if let lastMessage = viewModel.messages.last {
                    withAnimation {
                        proxy.scrollTo(lastMessage.id, anchor: .bottom)
                    }
                }
            }
            .onAppear {
                // Initial scroll to bottom
                if let lastMessage = viewModel.messages.last {
                    proxy.scrollTo(lastMessage.id, anchor: .bottom)
                }
            }
        }
    }
    
    private var loadingState: some View {
        VStack {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .naviBlue))
            Text("Loading messages...")
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel("Loading chat messages")
    }
    
    private func errorState(_ error: Error) -> some View {
        VStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundColor(.red)
            Text("Error: \(error.localizedDescription)")
                .multilineTextAlignment(.center)
                .padding(.top, 5)
            Button("Retry") {
                viewModel.loadMessages()
            }
            .padding(.top, 10)
            .buttonStyle(.borderedProminent)
            .tint(.naviBlue)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
        .accessibilityLabel("Chat loading failed with error: \(error.localizedDescription)")
    }
    
    private var emptyState: some View {
        VStack {
            Image(systemName: "bubble.left.and.bubble.right.fill")
                .font(.largeTitle)
                .foregroundColor(.naviBlue)
            Text("Start a conversation!")
                .font(.headline)
                .padding(.top, 5)
            Text("Be the first to send a message to the group.")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel("Empty chat. Start a conversation.")
    }
}

// MARK: - Helper Extension for Corner Radius

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

// MARK: - Preview

struct GroupChatView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            GroupChatView(chat: Chat(id: "c1", name: "SwiftUI Devs", members: [User.current, User.friend], isGroup: true, lastActive: Date()))
        }
    }
}

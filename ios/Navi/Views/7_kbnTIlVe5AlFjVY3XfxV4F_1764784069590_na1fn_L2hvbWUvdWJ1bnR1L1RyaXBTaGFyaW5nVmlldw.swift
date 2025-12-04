import SwiftUI
import Combine

// MARK: - 1. Models (M)

/// Represents a user participating in the trip sharing.
struct User: Identifiable {
    let id = UUID()
    let name: String
    let profileImageURL: String // Simulated
    let isOnline: Bool
    let badge: String? // e.g., "Driver", "Co-Pilot"
}

/// Represents a real-time update for the trip.
struct TripUpdate {
    let eta: String
    let location: String // e.g., "123 Main St, Anytown"
}

/// Represents a message in the live chat.
struct ChatMessage: Identifiable {
    let id = UUID()
    let sender: User
    let content: String
    let timestamp: Date
    let isFromCurrentUser: Bool
}

enum SharingStatus {
    case stopped
    case sharing
    case paused
}

// MARK: - 2. ViewModel (VM)

/// The ViewModel for the TripSharingView, managing state and business logic.
final class TripSharingViewModel: ObservableObject {
    
    // MARK: Published Properties
    
    @Published var sharingStatus: SharingStatus = .stopped
    @Published var currentUpdate: TripUpdate = TripUpdate(eta: "N/A", location: "Starting point...")
    @Published var sharedUsers: [User] = []
    @Published var chatMessages: [ChatMessage] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var shareLink: String = "https://app.example.com/trip/xyz123"
    
    private var cancellables = Set<AnyCancellable>()
    private let apiService = APIService.shared // Simulated API Service
    
    // Simulated Current User
    private let currentUser = User(name: "Manus AI", profileImageURL: "manus_avatar", isOnline: true, badge: "Sharer")
    
    init() {
        loadInitialData()
        // Simulate WebSocket connection for chat
        simulateWebSocketChat()
    }
    
    // MARK: Data Loading and Simulation
    
    func loadInitialData() {
        isLoading = true
        errorMessage = nil
        
        // Simulate API call to fetch friends/groups
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            guard let self = self else { return }
            self.isLoading = false
            
            // Simulated data from APIService.shared
            self.sharedUsers = [
                User(name: "Alice", profileImageURL: "alice_avatar", isOnline: true, badge: "Friend"),
                User(name: "Bob", profileImageURL: "bob_avatar", isOnline: false, badge: "Group Member"),
                User(name: "Charlie", profileImageURL: "charlie_avatar", isOnline: true, badge: nil)
            ]
            
            // Simulate initial chat messages
            self.chatMessages = [
                ChatMessage(sender: self.sharedUsers[0], content: "Excited for the trip!", timestamp: Date().addingTimeInterval(-120), isFromCurrentUser: false),
                ChatMessage(sender: self.currentUser, content: "Just started sharing my location!", timestamp: Date().addingTimeInterval(-60), isFromCurrentUser: true)
            ]
        }
    }
    
    // MARK: Trip Sharing Controls
    
    func startSharing() {
        guard sharingStatus != .sharing else { return }
        
        isLoading = true
        errorMessage = nil
        
        // Simulate network request to start sharing
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            guard let self = self else { return }
            self.sharingStatus = .sharing
            self.isLoading = false
            self.currentUpdate = TripUpdate(eta: "35 min", location: "Approaching Highway 101")
            self.simulateRealTimeUpdates()
        }
    }
    
    func stopSharing() {
        guard sharingStatus == .sharing || sharingStatus == .paused else { return }
        
        // Cancel real-time updates
        cancellables.removeAll()
        
        // Simulate network request to stop sharing
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            self?.sharingStatus = .stopped
            self?.currentUpdate = TripUpdate(eta: "N/A", location: "Sharing stopped.")
        }
    }
    
    func togglePauseSharing() {
        if sharingStatus == .sharing {
            sharingStatus = .paused
            cancellables.removeAll() // Pause updates
        } else if sharingStatus == .paused {
            sharingStatus = .sharing
            simulateRealTimeUpdates() // Resume updates
        }
    }
    
    // MARK: Real-Time Simulation (Combine)
    
    private func simulateRealTimeUpdates() {
        Timer.publish(every: 10, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                self?.updateLocationAndETA()
            }
            .store(in: &cancellables)
    }
    
    private func updateLocationAndETA() {
        let newETA = ["25 min", "18 min", "10 min", "5 min"].randomElement()!
        let newLocation = ["Entering downtown area", "Passing Central Park", "Arrived at destination"].randomElement()!
        
        if newLocation == "Arrived at destination" {
            currentUpdate = TripUpdate(eta: "Arrived", location: newLocation)
            stopSharing()
        } else {
            currentUpdate = TripUpdate(eta: newETA, location: newLocation)
        }
    }
    
    // MARK: Chat Functionality
    
    func sendMessage(_ content: String) {
        guard !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        
        let newMessage = ChatMessage(sender: currentUser, content: content, timestamp: Date(), isFromCurrentUser: true)
        chatMessages.append(newMessage)
        
        // Simulate sending via WebSocket
        print("Sending message: \(content)")
    }
    
    private func simulateWebSocketChat() {
        // Simulate incoming messages
        Timer.publish(every: 20, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                guard let self = self, self.sharingStatus == .sharing, !self.sharedUsers.isEmpty else { return }
                
                let randomUser = self.sharedUsers.randomElement()!
                let randomMessage = ["See you soon!", "Traffic looks clear.", "Where are you now?", "Safe travels!"].randomElement()!
                
                let incomingMessage = ChatMessage(sender: randomUser, content: randomMessage, timestamp: Date(), isFromCurrentUser: false)
                self.chatMessages.append(incomingMessage)
            }
            .store(in: &cancellables)
    }
    
    // MARK: Error Simulation
    
    func simulateError() {
        errorMessage = "Location service temporarily unavailable. Please check your privacy settings."
        sharingStatus = .paused
    }
}

// MARK: - 3. View (V)

struct TripSharingView: View {
    
    @StateObject var viewModel = TripSharingViewModel()
    @State private var messageInput: String = ""
    
    // Navi Blue color
    private let naviBlue = Color(hex: "#2563EB")
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                
                // 1. Status and Controls Section
                statusAndControls
                
                // 2. Shared Users Section
                sharedUsersList
                
                // 3. Chat Section
                chatSection
            }
            .background(Color(uiColor: .systemGroupedBackground).edgesIgnoringSafeArea(.all))
            .navigationTitle("Live Trip Sharing")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Privacy Settings") {
                        // Action for privacy settings
                    }
                }
            }
            .onAppear {
                // Ensure initial data load if not already done
                if viewModel.sharedUsers.isEmpty && !viewModel.isLoading {
                    viewModel.loadInitialData()
                }
            }
        }
    }
    
    // MARK: Subviews
    
    private var statusAndControls: some View {
        VStack(spacing: 10) {
            
            // Error State
            if let error = viewModel.errorMessage {
                Text("Error: \(error)")
                    .foregroundColor(.white)
                    .padding(8)
                    .frame(maxWidth: .infinity)
                    .background(Color.red)
                    .accessibility(label: Text("Error: \(error)"))
            }
            
            // Loading State
            if viewModel.isLoading {
                ProgressView("Loading trip data...")
                    .padding()
            } else if viewModel.sharedUsers.isEmpty && viewModel.sharingStatus == .stopped {
                // Empty State
                Text("No one is sharing with you yet. Start sharing your trip!")
                    .foregroundColor(.secondary)
                    .padding()
                    .multilineTextAlignment(.center)
            }
            
            // Current Status Card
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Image(systemName: viewModel.sharingStatus == .sharing ? "location.fill" : "location.slash.fill")
                        .foregroundColor(viewModel.sharingStatus == .sharing ? .green : .red)
                        .accessibility(label: Text(viewModel.sharingStatus == .sharing ? "Sharing Live Location" : "Sharing Stopped"))
                    
                    Text(viewModel.sharingStatus == .sharing ? "Sharing Live" : viewModel.sharingStatus == .paused ? "Sharing Paused" : "Sharing Stopped")
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    Spacer()
                    
                    // Stop/Start Button
                    Button(action: {
                        if viewModel.sharingStatus == .sharing || viewModel.sharingStatus == .paused {
                            viewModel.stopSharing()
                        } else {
                            viewModel.startSharing()
                        }
                    }) {
                        Text(viewModel.sharingStatus == .sharing || viewModel.sharingStatus == .paused ? "Stop Sharing" : "Start Sharing")
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .padding(.vertical, 8)
                            .padding(.horizontal, 12)
                            .background(viewModel.sharingStatus == .sharing || viewModel.sharingStatus == .paused ? Color.red : naviBlue)
                            .cornerRadius(8)
                    }
                    .accessibility(label: Text(viewModel.sharingStatus == .sharing || viewModel.sharingStatus == .paused ? "Stop Sharing Trip" : "Start Sharing Trip"))
                }
                
                Divider()
                
                // ETA and Location
                HStack {
                    VStack(alignment: .leading) {
                        Text("ETA:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text(viewModel.currentUpdate.eta)
                            .font(.title2)
                            .fontWeight(.bold)
                            .foregroundColor(naviBlue)
                            .accessibility(value: Text("Estimated time of arrival: \(viewModel.currentUpdate.eta)"))
                    }
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text("Current Location:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text(viewModel.currentUpdate.location)
                            .font(.subheadline)
                            .lineLimit(1)
                            .truncationMode(.tail)
                            .accessibility(value: Text("Current location update: \(viewModel.currentUpdate.location)"))
                    }
                }
                
                // Share Link/QR Code
                HStack {
                    Text("Share Link:")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text(viewModel.shareLink)
                        .font(.caption)
                        .foregroundColor(naviBlue)
                        .lineLimit(1)
                    
                    Spacer()
                    
                    Button(action: {
                        // Action to show QR code or copy link
                    }) {
                        Image(systemName: "qrcode.viewfinder")
                            .foregroundColor(naviBlue)
                            .padding(4)
                            .background(Color.white)
                            .cornerRadius(4)
                    }
                    .accessibility(label: Text("Show QR Code to Share Trip"))
                }
            }
            .padding()
            .background(Color.white)
            .cornerRadius(12)
            .shadow(color: Color.black.opacity(0.05), radius: 5, x: 0, y: 2)
            .padding([.horizontal, .top])
        }
    }
    
    private var sharedUsersList: some View {
        VStack(alignment: .leading) {
            Text("Sharing With (\(viewModel.sharedUsers.count))")
                .font(.subheadline)
                .fontWeight(.medium)
                .padding(.horizontal)
                .padding(.top, 10)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 15) {
                    ForEach(viewModel.sharedUsers) { user in
                        UserCardView(user: user, naviBlue: naviBlue)
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 10)
            }
        }
        .background(Color.white)
        .padding(.top, 10)
    }
    
    private var chatSection: some View {
        VStack(spacing: 0) {
            Text("Live Chat")
                .font(.subheadline)
                .fontWeight(.medium)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
                .background(Color(uiColor: .systemGray6))
            
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(viewModel.chatMessages) { message in
                            ChatBubbleView(message: message, naviBlue: naviBlue)
                                .id(message.id)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top, 5)
                }
                .onChange(of: viewModel.chatMessages.count) { _ in
                    // Scroll to the bottom when a new message arrives
                    if let lastMessage = viewModel.chatMessages.last {
                        withAnimation {
                            proxy.scrollTo(lastMessage.id, anchor: .bottom)
                        }
                    }
                }
            }
            
            // Message Input
            HStack {
                TextField("Send a message...", text: $messageInput)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .frame(minHeight: 30)
                    .accessibility(label: Text("Message input field"))
                
                Button(action: {
                    viewModel.sendMessage(messageInput)
                    messageInput = ""
                }) {
                    Image(systemName: "arrow.up.circle.fill")
                        .resizable()
                        .frame(width: 30, height: 30)
                        .foregroundColor(naviBlue)
                }
                .disabled(messageInput.isEmpty)
                .accessibility(label: Text("Send message"))
            }
            .padding()
            .background(Color.white)
            .overlay(
                Rectangle()
                    .frame(height: 1)
                    .foregroundColor(Color(uiColor: .systemGray5)),
                alignment: .top
            )
        }
    }
}

// MARK: - Component Views

struct UserCardView: View {
    let user: User
    let naviBlue: Color
    
    var body: some View {
        VStack(spacing: 5) {
            ZStack(alignment: .bottomTrailing) {
                // Simulated Profile Image
                Circle()
                    .fill(Color(uiColor: .systemGray4))
                    .frame(width: 60, height: 60)
                    .overlay(
                        Text(String(user.name.prefix(1)))
                            .font(.title)
                            .foregroundColor(.white)
                    )
                
                // Online Indicator
                Circle()
                    .fill(user.isOnline ? .green : .gray)
                    .frame(width: 12, height: 12)
                    .overlay(Circle().stroke(Color.white, lineWidth: 2))
                    .offset(x: 3, y: 3)
                    .accessibility(label: Text(user.isOnline ? "Online" : "Offline"))
            }
            
            Text(user.name)
                .font(.caption)
                .lineLimit(1)
            
            if let badge = user.badge {
                Text(badge)
                    .font(.system(size: 10))
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(naviBlue)
                    .cornerRadius(4)
                    .accessibility(label: Text("Badge: \(badge)"))
            }
        }
        .frame(width: 80)
        .padding(.vertical, 5)
    }
}

struct ChatBubbleView: View {
    let message: ChatMessage
    let naviBlue: Color
    
    var body: some View {
        HStack {
            if message.isFromCurrentUser {
                Spacer()
            }
            
            VStack(alignment: message.isFromCurrentUser ? .trailing : .leading, spacing: 4) {
                Text(message.sender.name)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                
                Text(message.content)
                    .padding(10)
                    .background(message.isFromCurrentUser ? naviBlue : Color(uiColor: .systemGray4))
                    .foregroundColor(message.isFromCurrentUser ? .white : .primary)
                    .cornerRadius(15, corners: message.isFromCurrentUser ? [.topLeft, .bottomLeft, .bottomRight] : [.topRight, .bottomLeft, .bottomRight])
                    .accessibility(label: Text("Chat message from \(message.sender.name): \(message.content)"))
                
                Text(message.timestamp, style: .time)
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: 250, alignment: message.isFromCurrentUser ? .trailing : .leading)
            
            if !message.isFromCurrentUser {
                Spacer()
            }
        }
    }
}

// MARK: - Utility Extensions

extension Color {
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

// MARK: - Simulated API Service

/// A simulated singleton for API calls as required by the prompt.
class APIService {
    static let shared = APIService()
    private init() {}
    
    // Placeholder methods for required API features
    func fetchFriends() { /* ... */ }
    func fetchGroups() { /* ... */ }
    func fetchAchievements() { /* ... */ }
}

// MARK: - Preview

struct TripSharingView_Previews: PreviewProvider {
    static var previews: some View {
        TripSharingView()
    }
}

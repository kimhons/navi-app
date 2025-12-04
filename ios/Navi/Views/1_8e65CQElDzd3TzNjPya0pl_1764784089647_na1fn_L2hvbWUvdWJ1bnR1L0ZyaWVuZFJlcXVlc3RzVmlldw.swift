import SwiftUI
import Combine

// MARK: - 1. Data Models

/// Represents a user in the social network.
struct User: Identifiable, Equatable {
    let id: Int
    let name: String
    let username: String
    let avatarURL: String // Mock for image caching
    let isOnline: Bool
    let mutualFriendsCount: Int
    
    static var mock: User {
        User(id: 100, name: "Alice Johnson", username: "alice_j", avatarURL: "https://example.com/alice.png", isOnline: true, mutualFriendsCount: 12)
    }
}

/// Represents a friend request.
struct FriendRequest: Identifiable, Equatable {
    enum Status {
        case pending
        case sent
    }
    
    let id: Int
    let user: User
    let status: Status
    let timestamp: Date
}

// MARK: - 2. Mock API Service

/// A mock service to simulate API calls and real-time updates using Combine.
class APIService: ObservableObject {
    static let shared = APIService()
    
    // Publishers for real-time updates (simulated)
    @Published var pendingRequests: [FriendRequest] = []
    @Published var sentRequests: [FriendRequest] = []
    
    private var cancellables = Set<AnyCancellable>()
    
    private init() {
        // Initial mock data
        self.pendingRequests = [
            FriendRequest(id: 1, user: User(id: 101, name: "Bob Smith", username: "bob_s", avatarURL: "https://example.com/bob.png", isOnline: true, mutualFriendsCount: 5), status: .pending, timestamp: Date().addingTimeInterval(-3600)),
            FriendRequest(id: 2, user: User(id: 102, name: "Charlie Brown", username: "charlie_b", avatarURL: "https://example.com/charlie.png", isOnline: false, mutualFriendsCount: 1), status: .pending, timestamp: Date().addingTimeInterval(-86400))
        ]
        
        self.sentRequests = [
            FriendRequest(id: 3, user: User(id: 103, name: "Diana Prince", username: "diana_p", avatarURL: "https://example.com/diana.png", isOnline: true, mutualFriendsCount: 2), status: .sent, timestamp: Date().addingTimeInterval(-1800))
        ]
        
        // Simulate a real-time update every 10 seconds
        Timer.publish(every: 10, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                self?.simulateRealTimeUpdate()
            }
            .store(in: &cancellables)
    }
    
    private func simulateRealTimeUpdate() {
        // Example: Add a new pending request occasionally
        if Int.random(in: 1...5) == 1 {
            let newId = (pendingRequests.map { $0.id }.max() ?? 10) + 1
            let newUser = User(id: newId + 100, name: "New User \(newId)", username: "new_user_\(newId)", avatarURL: "https://example.com/new.png", isOnline: true, mutualFriendsCount: Int.random(in: 0...20))
            let newRequest = FriendRequest(id: newId, user: newUser, status: .pending, timestamp: Date())
            
            // Ensure we don't add duplicates in this simple mock
            if !pendingRequests.contains(where: { $0.id == newRequest.id }) {
                pendingRequests.append(newRequest)
                print("Simulated: New pending request added.")
            }
        }
    }
    
    // MARK: - API Methods
    
    enum APIError: Error, LocalizedError {
        case networkError
        case serverError
        case unknown
        
        var errorDescription: String? {
            switch self {
            case .networkError: return "Could not connect to the network."
            case .serverError: return "The server returned an error."
            case .unknown: return "An unknown error occurred."
            }
        }
    }
    
    /// Simulates fetching pending friend requests.
    func fetchPendingRequests() -> AnyPublisher<[FriendRequest], APIError> {
        // Simulate network delay and potential error
        return Future<[FriendRequest], APIError> { [weak self] promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                guard let self = self else { return }
                // 10% chance of error
                if Int.random(in: 1...10) == 1 {
                    promise(.failure(.networkError))
                } else {
                    promise(.success(self.pendingRequests.filter { $0.status == .pending }))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    /// Simulates fetching sent friend requests.
    func fetchSentRequests() -> AnyPublisher<[FriendRequest], APIError> {
        // Simulate network delay
        return Future<[FriendRequest], APIError> { [weak self] promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                guard let self = self else { return }
                promise(.success(self.sentRequests.filter { $0.status == .sent }))
            }
        }
        .eraseToAnyPublisher()
    }
    
    /// Simulates accepting a friend request.
    func acceptRequest(id: Int) -> AnyPublisher<Void, APIError> {
        return Future<Void, APIError> { [weak self] promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                guard let self = self else { return }
                if let index = self.pendingRequests.firstIndex(where: { $0.id == id }) {
                    self.pendingRequests.remove(at: index)
                    promise(.success(()))
                } else {
                    promise(.failure(.unknown)) // Request not found
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    /// Simulates declining a friend request.
    func declineRequest(id: Int) -> AnyPublisher<Void, APIError> {
        return Future<Void, APIError> { [weak self] promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                guard let self = self else { return }
                if let index = self.pendingRequests.firstIndex(where: { $0.id == id }) {
                    self.pendingRequests.remove(at: index)
                    promise(.success(()))
                } else {
                    promise(.failure(.unknown)) // Request not found
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - 3. ViewModel

class FriendRequestsViewModel: ObservableObject {
    
    enum ViewState {
        case loading
        case loaded
        case error(String)
        case empty
    }
    
    enum Tab: String, CaseIterable {
        case pending = "Pending"
        case sent = "Sent"
    }
    
    @Published var pendingRequests: [FriendRequest] = []
    @Published var sentRequests: [FriendRequest] = []
    @Published var viewState: ViewState = .loading
    @Published var selectedTab: Tab = .pending
    @Published var errorMessage: String?
    
    private var apiService: APIService
    private var cancellables = Set<AnyCancellable>()
    
    init(apiService: APIService = .shared) {
        self.apiService = apiService
        setupRealTimeUpdates()
        fetchRequests()
    }
    
    // Listen to the APIService's published properties for real-time updates
    private func setupRealTimeUpdates() {
        apiService.$pendingRequests
            .sink { [weak self] newRequests in
                guard let self = self else { return }
                // Only update if we are not currently in an error state from a fetch
                if case .error = self.viewState { return }
                
                let newPending = newRequests.filter { $0.status == .pending }
                self.pendingRequests = newPending
                self.updateViewState()
            }
            .store(in: &cancellables)
        
        apiService.$sentRequests
            .sink { [weak self] newRequests in
                guard let self = self else { return }
                self.sentRequests = newRequests.filter { $0.status == .sent }
            }
            .store(in: &cancellables)
    }
    
    func fetchRequests() {
        viewState = .loading
        errorMessage = nil
        
        // Fetch pending requests
        apiService.fetchPendingRequests()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                guard let self = self else { return }
                if case .failure(let error) = completion {
                    self.viewState = .error(error.localizedDescription)
                    self.errorMessage = error.localizedDescription
                }
            } receiveValue: { [weak self] requests in
                guard let self = self else { return }
                self.pendingRequests = requests
                self.updateViewState()
            }
            .store(in: &cancellables)
        
        // Fetch sent requests (no need to change viewState for this one)
        apiService.fetchSentRequests()
            .receive(on: DispatchQueue.main)
            .sink { _ in } receiveValue: { [weak self] requests in
                self?.sentRequests = requests
            }
            .store(in: &cancellables)
    }
    
    private func updateViewState() {
        // Only update viewState based on pending requests, as the sent tab doesn't have actions
        if selectedTab == .pending {
            if pendingRequests.isEmpty {
                viewState = .empty
            } else {
                viewState = .loaded
            }
        } else {
            // Keep viewState as loaded or empty for sent tab based on its own list
            viewState = sentRequests.isEmpty ? .empty : .loaded
        }
    }
    
    func acceptRequest(request: FriendRequest) {
        guard request.status == .pending else { return }
        
        // Optimistic update
        pendingRequests.removeAll { $0.id == request.id }
        updateViewState()
        
        apiService.acceptRequest(id: request.id)
            .receive(on: DispatchQueue.main)
            .sink { completion in
                if case .failure(let error) = completion {
                    // Revert optimistic update and show error if needed
                    print("Error accepting request: \(error.localizedDescription)")
                    // In a real app, you might re-add the request or show a persistent error
                }
            } receiveValue: { _ in
                // Success, the real-time update from APIService will handle the final state
            }
            .store(in: &cancellables)
    }
    
    func declineRequest(request: FriendRequest) {
        guard request.status == .pending else { return }
        
        // Optimistic update
        pendingRequests.removeAll { $0.id == request.id }
        updateViewState()
        
        apiService.declineRequest(id: request.id)
            .receive(on: DispatchQueue.main)
            .sink { completion in
                if case .failure(let error) = completion {
                    print("Error declining request: \(error.localizedDescription)")
                }
            } receiveValue: { _ in
                // Success
            }
            .store(in: &cancellables)
    }
}

// MARK: - 4. SwiftUI Views

// Helper for custom color
extension Color {
    /// Navi blue color: #2563EB
    static let naviBlue = Color(red: 0x25/255.0, green: 0x63/255.0, blue: 0xEB/255.0)
}

// MARK: - Subviews

/// A view representing a single friend request row.
struct RequestRowView: View {
    @ObservedObject var viewModel: FriendRequestsViewModel
    let request: FriendRequest
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            // 1. Avatar and Online Indicator
            ZStack(alignment: .bottomTrailing) {
                // Mock for image caching (using a simple circle for now)
                Circle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: 50, height: 50)
                    .overlay(
                        Text(String(request.user.name.prefix(1)))
                            .font(.title2)
                            .foregroundColor(.white)
                    )
                
                if request.user.isOnline {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 12, height: 12)
                        .overlay(Circle().stroke(Color.white, lineWidth: 2))
                        .offset(x: 3, y: 3)
                }
            }
            
            // 2. User Info and Mutual Friends
            VStack(alignment: .leading, spacing: 4) {
                Text(request.user.name)
                    .font(.headline)
                    .lineLimit(1)
                
                HStack(spacing: 4) {
                    Image(systemName: "person.2.fill")
                        .foregroundColor(.secondary)
                        .font(.caption)
                    Text("\(request.user.mutualFriendsCount) mutual friends")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                
                // Badge for sent requests
                if request.status == .sent {
                    Text("Request Sent")
                        .font(.caption2)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.naviBlue.opacity(0.8))
                        .cornerRadius(8)
                }
            }
            
            Spacer()
            
            // 3. Action Buttons (only for pending requests)
            if request.status == .pending {
                HStack(spacing: 8) {
                    Button {
                        viewModel.declineRequest(request: request)
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.red)
                            .font(.title2)
                            .accessibilityLabel("Decline friend request from \(request.user.name)")
                    }
                    
                    Button {
                        viewModel.acceptRequest(request: request)
                    } label: {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.title2)
                            .accessibilityLabel("Accept friend request from \(request.user.name)")
                    }
                }
            }
        }
        .padding(.vertical, 8)
    }
}

/// A view for displaying the empty state.
struct EmptyStateView: View {
    let title: String
    let message: String
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.crop.circle.badge.checkmark")
                .font(.largeTitle)
                .foregroundColor(.naviBlue)
            
            Text(title)
                .font(.title2)
                .fontWeight(.bold)
            
            Text(message)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .padding()
    }
}

/// A view for displaying the error state.
struct ErrorStateView: View {
    let message: String
    let retryAction: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundColor(.red)
            
            Text("Error Loading Requests")
                .font(.title2)
                .fontWeight(.bold)
            
            Text(message)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            
            Button("Try Again") {
                retryAction()
            }
            .buttonStyle(.borderedProminent)
            .tint(.naviBlue)
        }
        .padding()
    }
}

// MARK: - Main View

/// The main view for managing friend requests.
struct FriendRequestsView: View {
    @StateObject var viewModel = FriendRequestsViewModel()
    
    var body: some View {
        NavigationView {
            VStack {
                // Segmented Picker for Tabs
                Picker("Request Type", selection: $viewModel.selectedTab) {
                    ForEach(FriendRequestsViewModel.Tab.allCases, id: \.self) { tab in
                        Text(tab.rawValue)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                
                contentView
            }
            .navigationTitle("Friend Requests")
            .refreshable {
                viewModel.fetchRequests()
            }
        }
    }
    
    @ViewBuilder
    private var contentView: some View {
        switch viewModel.viewState {
        case .loading:
            ProgressView("Loading Requests...")
                .progressViewStyle(.circular)
                .scaleEffect(1.5)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            
        case .error(let message):
            ErrorStateView(message: message) {
                viewModel.fetchRequests()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            
        case .empty:
            let title: String
            let message: String
            
            if viewModel.selectedTab == .pending {
                title = "No Pending Requests"
                message = "You're all caught up! Check back later for new friend requests."
            } else {
                title = "No Sent Requests"
                message = "You haven't sent any friend requests recently."
            }
            
            EmptyStateView(title: title, message: message)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            
        case .loaded:
            List {
                if viewModel.selectedTab == .pending {
                    ForEach(viewModel.pendingRequests) { request in
                        RequestRowView(viewModel: viewModel, request: request)
                    }
                } else {
                    ForEach(viewModel.sentRequests) { request in
                        RequestRowView(viewModel: viewModel, request: request)
                    }
                }
            }
            .listStyle(.plain)
        }
    }
}

// MARK: - Preview

struct FriendRequestsView_Previews: PreviewProvider {
    static var previews: some View {
        FriendRequestsView()
    }
}

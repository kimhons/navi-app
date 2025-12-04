import SwiftUI
import Combine

// MARK: - 1. Model

/// Represents a social group in the application.
struct Group: Identifiable, Decodable {
    let id: String
    let name: String
    let memberCount: Int
    let lastActivity: Date
    let avatarURL: String // Mock for image caching
    let isOnline: Bool // Mock for online indicator
}

// MARK: - 2. Mock API Service

/// Mock implementation of the APIService for groups.
/// Uses Combine to simulate asynchronous network requests and real-time updates.
class APIService: ObservableObject {
    static let shared = APIService()
    
    // Mock data
    private var mockGroups: [Group] = [
        Group(id: "1", name: "SwiftUI Developers", memberCount: 125, lastActivity: Calendar.current.date(byAdding: .hour, value: -2, to: Date())!, avatarURL: "avatar1", isOnline: true),
        Group(id: "2", name: "Mobile Design Enthusiasts", memberCount: 89, lastActivity: Calendar.current.date(byAdding: .day, value: -1, to: Date())!, avatarURL: "avatar2", isOnline: false),
        Group(id: "3", name: "Navi Blue Fan Club", memberCount: 42, lastActivity: Calendar.current.date(byAdding: .minute, value: -15, to: Date())!, avatarURL: "avatar3", isOnline: true),
        Group(id: "4", name: "Performance Optimization", memberCount: 201, lastActivity: Calendar.current.date(byAdding: .day, value: -5, to: Date())!, avatarURL: "avatar4", isOnline: false),
        Group(id: "5", name: "Accessibility Advocates", memberCount: 15, lastActivity: Calendar.current.date(byAdding: .hour, value: -8, to: Date())!, avatarURL: "avatar5", isOnline: true)
    ]
    
    enum APIError: Error {
        case networkError
        case invalidCode
        case alreadyExists
    }
    
    /// Simulates fetching a list of groups with a delay.
    func fetchGroups() -> AnyPublisher<[Group], APIError> {
        return Future<[Group], APIError> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                // Simulate a network error 10% of the time
                if Int.random(in: 1...10) == 1 {
                    promise(.failure(.networkError))
                } else {
                    promise(.success(self.mockGroups))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    /// Simulates joining a group by code.
    func joinGroup(code: String) -> AnyPublisher<Group, APIError> {
        return Future<Group, APIError> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                if code == "INVALID" {
                    promise(.failure(.invalidCode))
                } else {
                    let newGroup = Group(id: UUID().uuidString, name: "Joined Group \(code)", memberCount: 1, lastActivity: Date(), avatarURL: "avatar6", isOnline: true)
                    self.mockGroups.append(newGroup)
                    promise(.success(newGroup))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    /// Simulates creating a new group.
    func createGroup(name: String) -> AnyPublisher<Group, APIError> {
        return Future<Group, APIError> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                let newGroup = Group(id: UUID().uuidString, name: name, memberCount: 1, lastActivity: Date(), avatarURL: "avatar7", isOnline: true)
                self.mockGroups.append(newGroup)
                promise(.success(newGroup))
            }
        }
        .eraseToAnyPublisher()
    }
    
    // Mock for real-time updates (WebSocket)
    let realTimeGroupUpdate = PassthroughSubject<Group, Never>()
    
    func startRealTimeUpdates() {
        // Simulate a new member joining a random group every 5 seconds
        Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            guard let self = self, !self.mockGroups.isEmpty else { return }
            if let randomGroup = self.mockGroups.randomElement(),
               let index = self.mockGroups.firstIndex(where: { $0.id == randomGroup.id }) {
                
                let updatedGroup = Group(
                    id: randomGroup.id,
                    name: randomGroup.name,
                    memberCount: randomGroup.memberCount + 1, // Increment member count
                    lastActivity: Date(),
                    avatarURL: randomGroup.avatarURL,
                    isOnline: randomGroup.isOnline
                )
                self.mockGroups[index] = updatedGroup
                self.realTimeGroupUpdate.send(updatedGroup)
            }
        }.fire()
    }
}

// MARK: - 3. ViewModel

/// Manages the state and business logic for the GroupsView.
class GroupsViewModel: ObservableObject {
    @Published var groups: [Group] = []
    @Published var isLoading: Bool = false
    @Published var error: APIService.APIError? = nil
    @Published var isShowingJoinGroupSheet: Bool = false
    @Published var isShowingCreateGroupSheet: Bool = false
    @Published var joinCode: String = ""
    @Published var newGroupName: String = ""
    
    private var cancellables = Set<AnyCancellable>()
    private let apiService: APIService
    
    init(apiService: APIService = .shared) {
        self.apiService = apiService
        // Start real-time updates mock
        apiService.startRealTimeUpdates()
        
        // Subscribe to real-time updates
        apiService.realTimeGroupUpdate
            .receive(on: DispatchQueue.main)
            .sink { [weak self] updatedGroup in
                guard let self = self else { return }
                if let index = self.groups.firstIndex(where: { $0.id == updatedGroup.id }) {
                    // Update existing group
                    self.groups[index] = updatedGroup
                }
            }
            .store(in: &cancellables)
    }
    
    /// Fetches the list of groups from the API.
    func fetchGroups() {
        isLoading = true
        error = nil
        
        apiService.fetchGroups()
            .receive(on: DispatchQueue.main)
            .sink(receiveCompletion: { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let err):
                    self?.error = err
                case .finished:
                    break
                }
            }, receiveValue: { [weak self] fetchedGroups in
                self?.groups = fetchedGroups.sorted(by: { $0.lastActivity > $1.lastActivity })
            })
            .store(in: &cancellables)
    }
    
    /// Attempts to join a group using a code.
    func joinGroup() {
        guard !joinCode.isEmpty else { return }
        isLoading = true
        
        apiService.joinGroup(code: joinCode)
            .receive(on: DispatchQueue.main)
            .sink(receiveCompletion: { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let err):
                    self?.error = err
                case .finished:
                    self?.isShowingJoinGroupSheet = false
                    self?.joinCode = ""
                }
            }, receiveValue: { [weak self] newGroup in
                self?.groups.append(newGroup)
                self?.groups.sort(by: { $0.lastActivity > $1.lastActivity })
            })
            .store(in: &cancellables)
    }
    
    /// Attempts to create a new group.
    func createGroup() {
        guard !newGroupName.isEmpty else { return }
        isLoading = true
        
        apiService.createGroup(name: newGroupName)
            .receive(on: DispatchQueue.main)
            .sink(receiveCompletion: { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let err):
                    self?.error = err
                case .finished:
                    self?.isShowingCreateGroupSheet = false
                    self?.newGroupName = ""
                }
            }, receiveValue: { [weak self] newGroup in
                self?.groups.append(newGroup)
                self?.groups.sort(by: { $0.lastActivity > $1.lastActivity })
            })
            .store(in: &cancellables)
    }
}

// MARK: - 4. Views and Components

/// Custom Color for Navi Blue (#2563EB)
extension Color {
    static let naviBlue = Color(red: 0x25 / 255, green: 0x63 / 255, blue: 0xEB / 255)
}

/// Mock for an image that would be cached in a real app.
struct CachedAsyncImage: View {
    let url: String
    let size: CGFloat
    
    var body: some View {
        ZStack {
            Circle()
                .fill(Color.gray.opacity(0.3))
                .frame(width: size, height: size)
            Text(String(url.prefix(1)).uppercased())
                .font(.system(size: size * 0.5, weight: .bold))
                .foregroundColor(.white)
        }
        .accessibilityLabel("Group avatar for \(url)")
    }
}

/// A card view representing a single group.
struct GroupCardView: View {
    let group: Group
    
    var body: some View {
        HStack(alignment: .top) {
            // Group Avatar with Online Indicator
            ZStack(alignment: .bottomTrailing) {
                CachedAsyncImage(url: group.avatarURL, size: 50)
                
                if group.isOnline {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 12, height: 12)
                        .overlay(Circle().stroke(Color.white, lineWidth: 2))
                        .offset(x: 3, y: 3)
                        .accessibilityLabel("Group is online")
                }
            }
            
            VStack(alignment: .leading, spacing: 4) {
                // Group Name (Profile Card element)
                Text(group.name)
                    .font(.headline)
                    .foregroundColor(.primary)
                    .accessibilityAddTraits(.isHeader)
                
                // Member Count and Last Activity (Badge/Chat Bubble element)
                HStack {
                    Image(systemName: "person.3.fill")
                        .foregroundColor(.naviBlue)
                        .accessibilityHidden(true)
                    Text("\(group.memberCount) Members")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .accessibilityValue("\(group.memberCount) members")
                    
                    Spacer()
                    
                    Text(timeAgo(from: group.lastActivity))
                        .font(.caption)
                        .foregroundColor(.gray)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.naviBlue.opacity(0.1))
                        .clipShape(Capsule())
                        .accessibilityLabel("Last activity \(timeAgo(from: group.lastActivity)) ago")
                }
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .foregroundColor(.naviBlue)
                .accessibilityHidden(true)
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle()) // Makes the whole row tappable
    }
    
    /// Helper function to format time ago.
    private func timeAgo(from date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

/// Floating Action Buttons (FABs) for Create and Join Group.
struct GroupFABs: View {
    @Binding var isShowingJoinGroupSheet: Bool
    @Binding var isShowingCreateGroupSheet: Bool
    
    var body: some View {
        VStack(spacing: 15) {
            // Join Group by Code FAB
            Button {
                isShowingJoinGroupSheet = true
            } label: {
                Label("Join Group", systemImage: "qrcode.viewfinder")
                    .labelStyle(.iconOnly)
                    .padding(15)
                    .background(Color.naviBlue.opacity(0.8))
                    .foregroundColor(.white)
                    .clipShape(Circle())
                    .shadow(radius: 5)
            }
            .accessibilityLabel("Join group by code")
            
            // Create Group FAB
            Button {
                isShowingCreateGroupSheet = true
            } label: {
                Label("Create Group", systemImage: "plus")
                    .labelStyle(.iconOnly)
                    .padding(20)
                    .background(Color.naviBlue)
                    .foregroundColor(.white)
                    .clipShape(Circle())
                    .shadow(radius: 8)
            }
            .accessibilityLabel("Create a new group")
        }
    }
}

/// View for the empty state when no groups are available.
struct EmptyGroupsView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.3.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 80, height: 80)
                .foregroundColor(.naviBlue.opacity(0.6))
            
            Text("No Groups Yet")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.primary)
            
            Text("Start by creating a new group or joining one with a code.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .padding()
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Empty state. No groups yet. Start by creating a new group or joining one with a code.")
    }
}

/// View for displaying errors.
struct ErrorView: View {
    let error: APIService.APIError
    let retryAction: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 50, height: 50)
                .foregroundColor(.red)
            
            Text("Error Loading Groups")
                .font(.headline)
            
            Text(errorMessage(for: error))
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            Button("Try Again") {
                retryAction()
            }
            .buttonStyle(.borderedProminent)
            .tint(.naviBlue)
        }
        .padding()
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Error: \(errorMessage(for: error)). Tap try again to reload.")
    }
    
    private func errorMessage(for error: APIService.APIError) -> String {
        switch error {
        case .networkError:
            return "A network connection error occurred. Please check your internet connection."
        case .invalidCode:
            return "The group code is invalid. Please check the code and try again."
        case .alreadyExists:
            return "A group with this name already exists."
        }
    }
}

// MARK: - 5. Main View

/// The main screen for displaying and managing social groups.
struct GroupsView: View {
    // MVVM Architecture: Use @StateObject for the ViewModel
    @StateObject var viewModel = GroupsViewModel()
    
    var body: some View {
        NavigationView {
            ZStack(alignment: .bottomTrailing) {
                content
                    .navigationTitle("Groups")
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button("Friends") {
                                // Action for APIService.shared for friends
                            }
                            .foregroundColor(.naviBlue)
                        }
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button {
                                // Action for APIService.shared for achievements
                            } label: {
                                Image(systemName: "trophy.fill")
                                    .foregroundColor(.naviBlue)
                            }
                            .accessibilityLabel("View achievements")
                        }
                    }
                    .onAppear {
                        // Fetch groups only if not already loaded or loading
                        if viewModel.groups.isEmpty && !viewModel.isLoading {
                            viewModel.fetchGroups()
                        }
                    }
                
                // Floating Action Buttons
                GroupFABs(
                    isShowingJoinGroupSheet: $viewModel.isShowingJoinGroupSheet,
                    isShowingCreateGroupSheet: $viewModel.isShowingCreateGroupSheet
                )
                .padding(.trailing, 20)
                .padding(.bottom, 20)
            }
        }
        // Ensure Dynamic Type is supported
        .environment(\.sizeCategory, .large)
        // Sheets for Join and Create Group
        .sheet(isPresented: $viewModel.isShowingJoinGroupSheet) {
            JoinGroupSheet(viewModel: viewModel)
        }
        .sheet(isPresented: $viewModel.isShowingCreateGroupSheet) {
            CreateGroupSheet(viewModel: viewModel)
        }
    }
    
    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading {
            ProgressView("Loading Groups...")
                .progressViewStyle(.circular)
                .scaleEffect(1.5)
                .foregroundColor(.naviBlue)
                .accessibilityLabel("Loading groups")
        } else if let error = viewModel.error {
            ErrorView(error: error) {
                viewModel.fetchGroups()
            }
        } else if viewModel.groups.isEmpty {
            EmptyGroupsView()
        } else {
            List {
                ForEach(viewModel.groups) { group in
                    // Use NavigationLink for group management/chat functionality
                    NavigationLink(destination: Text("Chat View for \(group.name)")) {
                        GroupCardView(group: group)
                    }
                    // Performance: efficient list updates handled by SwiftUI's List/ForEach with Identifiable
                }
                .listRowSeparator(.hidden)
            }
            .listStyle(.plain)
        }
    }
}

/// Sheet for joining a group by code.
struct JoinGroupSheet: View {
    @ObservedObject var viewModel: GroupsViewModel
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Join Group by Code")
                    .font(.title)
                    .fontWeight(.bold)
                
                TextField("Enter Group Code", text: $viewModel.joinCode)
                    .textFieldStyle(.roundedBorder)
                    .padding()
                    .accessibilityLabel("Group code input field")
                
                if viewModel.isLoading {
                    ProgressView()
                }
                
                Button("Join Group") {
                    viewModel.joinGroup()
                }
                .buttonStyle(.borderedProminent)
                .tint(.naviBlue)
                .disabled(viewModel.joinCode.isEmpty || viewModel.isLoading)
                .accessibilityHint("Taps to join the group with the entered code.")
                
                Spacer()
            }
            .padding()
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }
}

/// Sheet for creating a new group.
struct CreateGroupSheet: View {
    @ObservedObject var viewModel: GroupsViewModel
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Create New Group")
                    .font(.title)
                    .fontWeight(.bold)
                
                TextField("Enter Group Name", text: $viewModel.newGroupName)
                    .textFieldStyle(.roundedBorder)
                    .padding()
                    .accessibilityLabel("New group name input field")
                
                if viewModel.isLoading {
                    ProgressView()
                }
                
                Button("Create Group") {
                    viewModel.createGroup()
                }
                .buttonStyle(.borderedProminent)
                .tint(.naviBlue)
                .disabled(viewModel.newGroupName.isEmpty || viewModel.isLoading)
                .accessibilityHint("Taps to create a new group with the entered name.")
                
                Spacer()
            }
            .padding()
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - Preview

struct GroupsView_Previews: PreviewProvider {
    static var previews: some View {
        GroupsView()
    }
}

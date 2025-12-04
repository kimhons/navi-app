import SwiftUI
import Combine

// MARK: - 1. Data Models

/// Represents a user on the platform.
struct User: Identifiable, Hashable {
    let id: String
    let name: String
    let avatarURL: String // Placeholder for image caching
    let isOnline: Bool
    let hasNewChat: Bool
}

/// Represents a single entry in the leaderboard.
struct LeaderboardItem: Identifiable, Hashable {
    let id: String
    let user: User
    let rank: Int
    let distanceTraveled: Double // in km
    let placesVisited: Int
    let achievementsCount: Int
    
    var score: Double {
        // A simple way to calculate a score based on the current filter
        switch APIService.shared.selectedFilter {
        case .distance: return distanceTraveled
        case .places: return Double(placesVisited)
        case .achievements: return Double(achievementsCount)
        }
    }
}

/// Defines the criteria for ranking.
enum FilterType: String, CaseIterable {
    case distance = "Distance"
    case places = "Places Visited"
    case achievements = "Achievements"
}

/// Defines the scope of the leaderboard.
enum ScopeType: String, CaseIterable {
    case global = "Global"
    case friends = "Friends"
}

/// Defines the time period for the ranking.
enum TimePeriod: String, CaseIterable {
    case allTime = "All Time"
    case monthly = "Monthly"
    case weekly = "Weekly"
}

// MARK: - 2. Mock API Service

/// A mock service to simulate API calls and real-time updates.
class APIService {
    static let shared = APIService()
    
    // Combine publisher to simulate real-time updates
    let leaderboardUpdatePublisher = PassthroughSubject<[LeaderboardItem], Never>()
    
    // State to be accessed by the ViewModel
    @Published var selectedFilter: FilterType = .distance
    
    private init() {
        // Simulate a real-time update every 5 seconds
        Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            // In a real app, this would be triggered by a WebSocket message
            let updatedItems = self.generateMockItems().shuffled()
            self.leaderboardUpdatePublisher.send(updatedItems)
        }.fire()
    }
    
    func fetchLeaderboard(scope: ScopeType, period: TimePeriod, filter: FilterType) async throws -> [LeaderboardItem] {
        // Simulate network delay
        try await Task.sleep(for: .seconds(1))
        
        // In a real app, this would call the actual API
        let items = generateMockItems()
        
        // Apply sorting based on the requested filter
        let sortedItems = items.sorted { item1, item2 in
            switch filter {
            case .distance: return item1.distanceTraveled > item2.distanceTraveled
            case .places: return item1.placesVisited > item2.placesVisited
            case .achievements: return item1.achievementsCount > item2.achievementsCount
            }
        }
        
        // Re-rank after sorting
        return sortedItems.enumerated().map { index, item in
            LeaderboardItem(
                id: item.id,
                user: item.user,
                rank: index + 1,
                distanceTraveled: item.distanceTraveled,
                placesVisited: item.placesVisited,
                achievementsCount: item.achievementsCount
            )
        }
    }
    
    private func generateMockItems() -> [LeaderboardItem] {
        let mockUsers: [User] = [
            User(id: "u1", name: "Alex Johnson", avatarURL: "a1", isOnline: true, hasNewChat: false),
            User(id: "u2", name: "Maria Garcia", avatarURL: "a2", isOnline: false, hasNewChat: true),
            User(id: "u3", name: "Chen Li", avatarURL: "a3", isOnline: true, hasNewChat: false),
            User(id: "u4", name: "David Smith", avatarURL: "a4", isOnline: false, hasNewChat: false),
            User(id: "u5", name: "Emily Brown", avatarURL: "a5", isOnline: true, hasNewChat: true)
        ]
        
        return mockUsers.enumerated().map { index, user in
            LeaderboardItem(
                id: user.id,
                user: user,
                rank: index + 1,
                distanceTraveled: Double.random(in: 100...5000).rounded(),
                placesVisited: Int.random(in: 10...500),
                achievementsCount: Int.random(in: 1...50)
            )
        }
    }
}

// MARK: - 3. ViewModel (MVVM Architecture)

class LeaderboardViewModel: ObservableObject {
    @Published var items: [LeaderboardItem] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    
    @Published var selectedFilter: FilterType = .distance { didSet { APIService.shared.selectedFilter = selectedFilter; fetchLeaderboard() } }
    @Published var selectedScope: ScopeType = .global { didSet { fetchLeaderboard() } }
    @Published var selectedTimePeriod: TimePeriod = .allTime { didSet { fetchLeaderboard() } }
    
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        setupRealTimeUpdates()
        fetchLeaderboard()
    }
    
    /// Fetches the leaderboard data based on current filters.
    func fetchLeaderboard() {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        
        Task {
            do {
                let fetchedItems = try await APIService.shared.fetchLeaderboard(
                    scope: selectedScope,
                    period: selectedTimePeriod,
                    filter: selectedFilter
                )
                
                // Ensure UI updates happen on the main thread
                await MainActor.run {
                    self.items = fetchedItems
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = "Failed to load leaderboard: \(error.localizedDescription)"
                    self.isLoading = false
                }
            }
        }
    }
    
    /// Sets up a subscription to the real-time update publisher.
    func setupRealTimeUpdates() {
        APIService.shared.leaderboardUpdatePublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] updatedItems in
                // Only update if we are not currently loading a fresh set
                guard let self = self, !self.isLoading else { return }
                
                // Simple merge/update logic for real-time changes
                // In a real app, this would be more sophisticated (e.g., diffing)
                print("Real-time update received.")
                self.items = updatedItems.sorted { $0.rank < $1.rank }
            }
            .store(in: &cancellables)
    }
    
    // MARK: Social/Privacy Placeholders
    
    func sendFriendRequest(to user: User) {
        print("Sending friend request to \(user.name)")
        // API call to send request
    }
    
    func toggleLocationSharing() {
        print("Toggling location sharing...")
        // Logic for updating privacy settings
    }
}

// MARK: - 4. View Components (To be implemented in Phase 2)

// LeaderboardView will be implemented in the next phase.
// LeaderboardRowView will be implemented in the next phase.

// Color definition for Navi Blue
extension Color {
    static let naviBlue = Color(red: 37/255, green: 99/255, blue: 235/255) // #2563EB
}

// Placeholder for Image Caching (Performance)
struct CachedAsyncImage<Content: View>: View {
    let url: String
    @ViewBuilder let content: (Image) -> Content
    
    var body: some View {
        // In a production app, this would use a library like Kingfisher or a custom cache
        // For now, we use a placeholder image.
        Image(systemName: "person.circle.fill")
            .resizable()
            .scaledToFit()
            .frame(width: 40, height: 40)
            .foregroundColor(.gray)
            .overlay(
                Text(String(url.prefix(1)).uppercased())
                    .font(.headline)
                    .foregroundColor(.white)
            )
    }
}

// Placeholder for Chat Bubble (Design)
struct ChatBubbleIndicator: View {
    let hasNewChat: Bool
    
    var body: some View {
        if hasNewChat {
            Circle()
                .fill(Color.red)
                .frame(width: 10, height: 10)
                .overlay(
                    Text("1")
                        .font(.system(size: 7, weight: .bold))
                        .foregroundColor(.white)
                )
                .offset(x: 5, y: -5)
        }
    }
}

// Placeholder for Badge (Design)
struct RankBadge: View {
    let rank: Int
    
    var body: some View {
        Text("\(rank)")
            .font(.system(.title2, design: .rounded, weight: .heavy))
            .foregroundColor(.white)
            .frame(width: 40, height: 40)
            .background(
                Circle()
                    .fill(rank <= 3 ? Color.naviBlue : Color.gray.opacity(0.7))
            )
            .accessibilityLabel("Rank \(rank)")
    }
}

// Placeholder for Online Indicator (Design)
struct OnlineIndicator: View {
    let isOnline: Bool
    
    var body: some View {
        Circle()
            .fill(isOnline ? Color.green : Color.gray)
            .frame(width: 10, height: 10)
            .overlay(Circle().stroke(Color.white, lineWidth: 1))
            .offset(x: 15, y: 15)
    }
}

// Placeholder for Privacy Settings View
struct PrivacySettingsView: View {
    @State private var isLocationSharingEnabled = true
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Location Sharing Controls")) {
                    Toggle("Share My Location", isOn: $isLocationSharingEnabled)
                        .accessibilityValue(isLocationSharingEnabled ? "On" : "Off")
                }
                
                Section(header: Text("Data Export")) {
                    Button("Export My Data") {
                        print("Initiating data export...")
                    }
                }
            }
            .navigationTitle("Privacy Settings")
        }
    }
}

// MARK: - 5. Leaderboard Row View

struct LeaderboardRowView: View {
    let item: LeaderboardItem
    @ObservedObject var viewModel: LeaderboardViewModel
    
    var body: some View {
        HStack(spacing: 15) {
            
            // 1. Rank Badge
            RankBadge(rank: item.rank)
            
            // 2. Profile Card (Avatar, Online Indicator, Chat Bubble)
            ZStack(alignment: .bottomTrailing) {
                CachedAsyncImage(url: item.user.avatarURL) { image in
                    image
                }
                .clipShape(Circle())
                
                OnlineIndicator(isOnline: item.user.isOnline)
                
                if item.user.hasNewChat {
                    ChatBubbleIndicator(hasNewChat: item.user.hasNewChat)
                        .offset(x: 10, y: -10)
                }
            }
            .frame(width: 50, height: 50)
            
            // 3. User Info
            VStack(alignment: .leading) {
                Text(item.user.name)
                    .font(.headline)
                    .foregroundColor(.primary)
                    .accessibilityLabel("User: \(item.user.name)")
                
                Text(viewModel.selectedFilter.rawValue)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .accessibilityLabel("Ranking by \(viewModel.selectedFilter.rawValue)")
            }
            
            Spacer()
            
            // 4. Score/Value
            VStack(alignment: .trailing) {
                Text(formattedScore(item.score))
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.naviBlue)
                    .accessibilityValue(formattedScore(item.score))
                
                Text(unitText)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            // 5. Social Action Button (Friend Request)
            Button {
                viewModel.sendFriendRequest(to: item.user)
            } label: {
                Image(systemName: "person.badge.plus")
                    .foregroundColor(.naviBlue)
                    .padding(8)
                    .background(Circle().fill(Color.naviBlue.opacity(0.1)))
            }
            .accessibilityLabel("Send friend request to \(item.user.name)")
        }
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Double tap to view \(item.user.name)'s full profile.")
    }
    
    private var unitText: String {
        switch viewModel.selectedFilter {
        case .distance: return "km"
        case .places: return "places"
        case .achievements: return "badges"
        }
    }
    
    private func formattedScore(_ score: Double) -> String {
        switch viewModel.selectedFilter {
        case .distance: return String(format: "%.1f", score)
        case .places, .achievements: return String(format: "%.0f", score)
        }
    }
}

// MARK: - 6. Main Leaderboard View

struct LeaderboardView: View {
    // MVVM Architecture: Use @StateObject for the ViewModel
    @StateObject var viewModel = LeaderboardViewModel()
    
    // State for the Privacy Settings Sheet
    @State private var showingPrivacySettings = false
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                
                // 1. Filter and Scope Selectors
                filterSelectors
                
                // 2. Main Content (List, Loading, Error, Empty States)
                mainContent
            }
            .navigationTitle("Leaderboard")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    // Placeholder for Group Management
                    Button {
                        print("Showing group management...")
                    } label: {
                        Image(systemName: "person.3.fill")
                            .foregroundColor(.naviBlue)
                    }
                    .accessibilityLabel("Group Management")
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    // Placeholder for Privacy Settings
                    Button {
                        showingPrivacySettings = true
                    } label: {
                        Image(systemName: "gearshape.fill")
                            .foregroundColor(.naviBlue)
                    }
                    .accessibilityLabel("Privacy Settings and Controls")
                }
            }
            .sheet(isPresented: $showingPrivacySettings) {
                PrivacySettingsView()
            }
        }
        // Dynamic Type support is automatic for standard SwiftUI Text
        .onAppear {
            // Ensure data is loaded on view appearance
            viewModel.fetchLeaderboard()
        }
    }
    
    // MARK: - Subviews
    
    private var filterSelectors: some View {
        VStack(spacing: 10) {
            // Scope (Friends/Global) and Time Period Selector
            HStack {
                Picker("Scope", selection: $viewModel.selectedScope) {
                    ForEach(ScopeType.allCases, id: \.self) { scope in
                        Text(scope.rawValue).tag(scope)
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityLabel("Leaderboard Scope Selector")
                
                Picker("Time Period", selection: $viewModel.selectedTimePeriod) {
                    ForEach(TimePeriod.allCases, id: \.self) { period in
                        Text(period.rawValue).tag(period)
                    }
                }
                .pickerStyle(.menu)
                .accessibilityLabel("Time Period Selector")
            }
            .padding(.horizontal)
            
            // Filter Type Selector (Distance, Places, Achievements)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(FilterType.allCases, id: \.self) { filter in
                        Button(filter.rawValue) {
                            viewModel.selectedFilter = filter
                        }
                        .padding(.vertical, 8)
                        .padding(.horizontal, 15)
                        .background(
                            Capsule()
                                .fill(viewModel.selectedFilter == filter ? Color.naviBlue : Color.gray.opacity(0.2))
                        )
                        .foregroundColor(viewModel.selectedFilter == filter ? .white : .primary)
                        .font(.subheadline.weight(.medium))
                        .accessibilityAddTraits(viewModel.selectedFilter == filter ? .isSelected : .isButton)
                        .accessibilityHint("Filter leaderboard by \(filter.rawValue)")
                    }
                }
                .padding(.horizontal)
            }
        }
        .padding(.bottom, 10)
        .background(Color(uiColor: .systemBackground))
        .shadow(color: .black.opacity(0.05), radius: 5, x: 0, y: 5)
    }
    
    @ViewBuilder
    private var mainContent: some View {
        if viewModel.isLoading {
            // Loading State
            ProgressView("Loading Rankings...")
                .progressViewStyle(.circular)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityLabel("Loading Leaderboard")
        } else if let error = viewModel.errorMessage {
            // Error State
            VStack {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.largeTitle)
                    .foregroundColor(.red)
                Text("Error")
                    .font(.headline)
                Text(error)
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                Button("Retry") {
                    viewModel.fetchLeaderboard()
                }
                .padding()
                .background(Color.naviBlue)
                .foregroundColor(.white)
                .clipShape(Capsule())
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Error loading leaderboard. \(error)")
        } else if viewModel.items.isEmpty {
            // Empty State
            VStack {
                Image(systemName: "person.3.fill")
                    .font(.largeTitle)
                    .foregroundColor(.gray)
                Text("No Rankings Found")
                    .font(.headline)
                Text("Try adjusting your filters or check back later.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityLabel("Leaderboard is empty.")
        } else {
            // Success State: Leaderboard List
            List {
                // Performance: List uses efficient diffing and updates for identifiable data
                ForEach(viewModel.items) { item in
                    LeaderboardRowView(item: item, viewModel: viewModel)
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                }
            }
            .listStyle(.plain)
            .refreshable {
                // Pull-to-refresh for manual data fetch
                viewModel.fetchLeaderboard()
            }
        }
    }
}

// MARK: - Preview

struct LeaderboardView_Previews: PreviewProvider {
    static var previews: some View {
        LeaderboardView()
    }
}

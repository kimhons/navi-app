import SwiftUI
import Combine

// MARK: - 1. Model

/// A structure representing a single search history entry.
struct SearchHistoryItem: Identifiable, Equatable {
    let id = UUID()
    let query: String
    let timestamp: Date
    let isPlaceDetail: Bool // To simulate different types of history
    
    static func mock(query: String, daysAgo: Int) -> SearchHistoryItem {
        let date = Calendar.current.date(byAdding: .day, value: -daysAgo, to: Date()) ?? Date()
        return SearchHistoryItem(query: query, timestamp: date, isPlaceDetail: daysAgo % 3 == 0)
    }
}

// MARK: - 2. Mock Services

/// Mock APIService to simulate network operations.
class APIService {
    static let shared = APIService()
    
    func fetchHistory() -> AnyPublisher<[SearchHistoryItem], Error> {
        // Simulate network delay
        return Just([
            SearchHistoryItem.mock(query: "Eiffel Tower, Paris", daysAgo: 1),
            SearchHistoryItem.mock(query: "Tokyo Skytree", daysAgo: 2),
            SearchHistoryItem.mock(query: "Statue of Liberty", daysAgo: 5),
            SearchHistoryItem.mock(query: "Great Wall of China", daysAgo: 8),
            SearchHistoryItem.mock(query: "Machu Picchu", daysAgo: 10),
            SearchHistoryItem.mock(query: "Colosseum, Rome", daysAgo: 15),
            SearchHistoryItem.mock(query: "Sydney Opera House", daysAgo: 20),
            SearchHistoryItem.mock(query: "Burj Khalifa, Dubai", daysAgo: 25),
        ])
        .delay(for: .seconds(0.5), scheduler: RunLoop.main)
        .setFailureType(to: Error.self)
        .eraseToAnyPublisher()
    }
    
    func search(query: String) {
        print("API: Performing search for: \(query)")
        // In a real app, this would trigger a navigation or data fetch
    }
    
    func saveHistory(item: SearchHistoryItem) {
        print("API: Saving history item: \(item.query)")
    }
    
    // Mock error for demonstration
    func simulateError() -> AnyPublisher<[SearchHistoryItem], Error> {
        return Fail(error: NSError(domain: "", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to load history due to server error."]))
            .delay(for: .seconds(0.5), scheduler: RunLoop.main)
            .eraseToAnyPublisher()
    }
}

/// Mock Kingfisher usage with a simple AsyncImage wrapper.
struct KFAsyncImage<Content: View, Placeholder: View>: View {
    let url: URL?
    let content: (Image) -> Content
    let placeholder: () -> Placeholder
    
    init(url: URL?, @ViewBuilder content: @escaping (Image) -> Content, @ViewBuilder placeholder: @escaping () -> Placeholder) {
        self.url = url
        self.content = content
        self.placeholder = placeholder
    }
    
    var body: some View {
        // Simulating Kingfisher's behavior with a standard AsyncImage
        AsyncImage(url: url) { phase in
            if let image = phase.image {
                content(image)
            } else if phase.error != nil {
                placeholder() // Use placeholder on error
            } else {
                placeholder() // Use placeholder while loading
            }
        }
    }
}

// MARK: - 3. ViewModel

class SearchHistoryViewModel: ObservableObject {
    // Architecture: MVVM with @Published properties
    @Published var historyItems: [SearchHistoryItem] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var searchText: String = ""
    @Published var canLoadMore: Bool = true
    
    private var cancellables = Set<AnyCancellable>()
    private let pageSize = 10
    private var currentPage = 0
    
    // Design: Navi blue color
    let naviBlue = Color(hex: "#2563EB")
    
    init() {
        // Feature: Debounced search (for a search bar that might be added later)
        $searchText
            .debounce(for: .milliseconds(500), scheduler: RunLoop.main)
            .removeDuplicates()
            .sink { [weak self] query in
                guard !query.isEmpty else { return }
                self?.performDebouncedSearch(query: query)
            }
            .store(in: &cancellables)
        
        loadHistory(isInitial: true)
    }
    
    // MARK: - Data Loading
    
    /// Loads the initial or next page of search history.
    func loadHistory(isInitial: Bool = false) {
        guard !isLoading else { return }
        
        if isInitial {
            historyItems = []
            currentPage = 0
            canLoadMore = true
        }
        
        isLoading = true
        errorMessage = nil
        
        // Simulate loading a page of data
        APIService.shared.fetchHistory()
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let error):
                    // Feature: Error handling
                    self?.errorMessage = error.localizedDescription
                case .finished:
                    break
                }
            } receiveValue: { [weak self] newItems in
                // Simulate pagination logic
                let startIndex = self?.currentPage ?? 0 * self?.pageSize ?? 0
                let endIndex = min(startIndex + (self?.pageSize ?? 10), newItems.count)
                
                if startIndex < newItems.count {
                    let pageItems = Array(newItems[startIndex..<endIndex])
                    self?.historyItems.append(contentsOf: pageItems)
                    self?.currentPage += 1
                    self?.canLoadMore = endIndex < newItems.count
                } else {
                    self?.canLoadMore = false
                }
            }
            .store(in: &cancellables)
    }
    
    /// Feature: Pull-to-refresh
    func pullToRefresh() async {
        // Simulate a refresh operation
        await withCheckedContinuation { continuation in
            APIService.shared.fetchHistory()
                .sink { [weak self] completion in
                    self?.isLoading = false
                    if case .failure(let error) = completion {
                        self?.errorMessage = error.localizedDescription
                    }
                    continuation.resume()
                } receiveValue: { [weak self] items in
                    self?.historyItems = items
                    self?.currentPage = 1
                    self?.canLoadMore = items.count > self?.pageSize ?? 10
                }
                .store(in: &cancellables)
        }
    }
    
    // MARK: - Actions
    
    /// Feature: Tap to search again
    func searchAgain(item: SearchHistoryItem) {
        APIService.shared.search(query: item.query)
    }
    
    /// Feature: Clear individual button
    func clearItem(item: SearchHistoryItem) {
        withAnimation {
            historyItems.removeAll { $0.id == item.id }
        }
    }
    
    /// Feature: Clear all button
    func clearAll() {
        withAnimation {
            historyItems.removeAll()
            canLoadMore = false
        }
    }
    
    private func performDebouncedSearch(query: String) {
        // This would typically search the history list or trigger a new places search
        print("Debounced search triggered for: \(query)")
    }
    
    // MARK: - Performance
    
    /// Checks if the item is the last one to trigger "Load More"
    func shouldLoadMore(item: SearchHistoryItem) -> Bool {
        guard let lastItem = historyItems.last else { return false }
        return item.id == lastItem.id && canLoadMore && !isLoading
    }
}

// MARK: - 4. View

struct SearchHistoryView: View {
    // Architecture: @StateObject ViewModel
    @StateObject var viewModel = SearchHistoryViewModel()
    
    var body: some View {
        NavigationView {
            VStack {
                // Design: Search bar
                SearchBar(text: $viewModel.searchText, placeholder: "Search history or places...")
                
                content
            }
            .navigationTitle("Recent Searches")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    // Feature: Clear all button
                    Button("Clear All", role: .destructive) {
                        viewModel.clearAll()
                    }
                    .disabled(viewModel.historyItems.isEmpty)
                    .accessibilityLabel("Clear all search history")
                }
            }
            // Feature: Pull-to-refresh
            .refreshable {
                await viewModel.pullToRefresh()
            }
        }
        // Accessibility: Dynamic Type support
        .environment(\.sizeCategory, .large)
    }
    
    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading && viewModel.historyItems.isEmpty {
            // Feature: Loading states
            ProgressView("Loading History...")
                .padding()
        } else if let error = viewModel.errorMessage {
            // Feature: Error handling
            ErrorView(message: error, retryAction: viewModel.loadHistory)
        } else if viewModel.historyItems.isEmpty {
            // Feature: Empty states
            EmptyStateView(naviBlue: viewModel.naviBlue)
        } else {
            historyList
        }
    }
    
    private var historyList: some View {
        // Performance: Lazy loading (using List which handles lazy loading)
        List {
            ForEach(viewModel.historyItems) { item in
                HistoryRow(item: item, viewModel: viewModel)
                    .onAppear {
                        // Performance: Pagination for long lists
                        if viewModel.shouldLoadMore(item: item) {
                            viewModel.loadHistory()
                        }
                    }
            }
            
            if viewModel.canLoadMore && !viewModel.historyItems.isEmpty {
                HStack {
                    Spacer()
                    ProgressView()
                    Text("Loading more...")
                    Spacer()
                }
                .listRowSeparator(.hidden)
            }
        }
        .listStyle(.plain)
    }
}

// MARK: - 5. Subviews

struct HistoryRow: View {
    let item: SearchHistoryItem
    @ObservedObject var viewModel: SearchHistoryViewModel
    
    var body: some View {
        HStack {
            // Design: SF Symbols icons
            Image(systemName: item.isPlaceDetail ? "mappin.circle.fill" : "magnifyingglass")
                .foregroundColor(viewModel.naviBlue)
                .imageScale(.large)
                // Accessibility: VoiceOver labels
                .accessibilityLabel(item.isPlaceDetail ? "Place detail view" : "Search query")
            
            VStack(alignment: .leading) {
                Text(item.query)
                    .font(.headline)
                    .lineLimit(1)
                
                Text(item.timestamp, style: .time) + Text(" - ") + Text(item.timestamp, style: .date)
                    .font(.caption)
                    .foregroundColor(.gray)
            }
            
            Spacer()
            
            // Feature: Clear individual button
            Button {
                viewModel.clearItem(item: item)
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .foregroundColor(.secondary)
            }
            .accessibilityLabel("Clear this search item")
        }
        // Feature: Tap to search again
        .contentShape(Rectangle()) // Make the entire row tappable
        .onTapGesture {
            viewModel.searchAgain(item: item)
        }
        // Feature: Swipe actions
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button(role: .destructive) {
                viewModel.clearItem(item: item)
            } label: {
                Label("Delete", systemImage: "trash.fill")
            }
            
            Button {
                viewModel.searchAgain(item: item)
            } label: {
                Label("Search", systemImage: "magnifyingglass")
            }
            .tint(.green)
        }
        // Feature: Long press menus
        .contextMenu {
            Button {
                viewModel.searchAgain(item: item)
            } label: {
                Label("Search Again", systemImage: "magnifyingglass")
            }
            
            Button {
                UIPasteboard.general.string = item.query
            } label: {
                Label("Copy Query", systemImage: "doc.on.doc")
            }
            
            Divider()
            
            Button(role: .destructive) {
                viewModel.clearItem(item: item)
            } label: {
                Label("Delete from History", systemImage: "trash")
            }
        }
    }
}

struct SearchBar: View {
    @Binding var text: String
    var placeholder: String
    
    var body: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.secondary)
            
            TextField(placeholder, text: $text)
                .accessibilityLabel(placeholder)
            
            if !text.isEmpty {
                Button {
                    text = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
                .accessibilityLabel("Clear search text")
            }
        }
        .padding(8)
        .background(Color(.systemGray6))
        .cornerRadius(10)
        .padding(.horizontal)
    }
}

struct EmptyStateView: View {
    let naviBlue: Color
    
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "clock.arrow.circlepath")
                .resizable()
                .scaledToFit()
                .frame(width: 80, height: 80)
                .foregroundColor(naviBlue)
            
            Text("No Recent Searches")
                .font(.title2)
                .fontWeight(.semibold)
            
            Text("Start searching for places to see your history here.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct ErrorView: View {
    let message: String
    let retryAction: () -> Void
    
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 60, height: 60)
                .foregroundColor(.red)
            
            Text("Error")
                .font(.title2)
                .fontWeight(.semibold)
            
            Text(message)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            
            Button("Try Again") {
                retryAction()
            }
            .buttonStyle(.borderedProminent)
            .padding(.top)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Extensions

extension Color {
    // Helper to create Color from hex string
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

// MARK: - Preview

struct SearchHistoryView_Previews: PreviewProvider {
    static var previews: some View {
        SearchHistoryView()
    }
}

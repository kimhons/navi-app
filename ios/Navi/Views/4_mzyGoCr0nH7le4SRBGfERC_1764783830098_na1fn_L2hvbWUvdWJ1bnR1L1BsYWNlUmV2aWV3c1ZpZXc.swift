import SwiftUI
import Combine

// MARK: - 1. Data Models

struct Author: Identifiable, Hashable {
    let id: String = UUID().uuidString
    let name: String
    let avatarURL: URL?
}

struct Review: Identifiable, Hashable {
    let id: String = UUID().uuidString
    let author: Author
    let rating: Int // 1 to 5
    let text: String
    var helpfulVotes: Int
    let date: Date
    var isHelpful: Bool = false
}

// MARK: - 2. Mock APIService

class APIService {
    static let shared = APIService()
    
    private init() {}
    
    // Mock data generation
    private func generateMockReviews(count: Int, forPlaceId placeId: String) -> [Review] {
        let names = ["Alice Johnson", "Bob Smith", "Charlie Brown", "Diana Prince", "Ethan Hunt", "Fiona Green", "George King", "Hannah Lee", "Ivy Chen", "Jack Ryan"]
        let texts = [
            "Absolutely fantastic experience! Highly recommend this place to everyone. The atmosphere was great and the food was delicious.",
            "It was okay, nothing special. The service was a bit slow, and the prices were high for the quality.",
            "The best I've ever had! Will definitely be coming back soon. Five stars all the way!",
            "A little disappointed with the quality, but the staff was friendly and tried their best to accommodate us.",
            "Five stars all the way! Flawless execution and great atmosphere. A must-visit in the area.",
            "Decent place for a quick bite. Nothing to write home about, but reliable.",
            "The view is incredible, but the seating was uncomfortable. Mixed feelings overall.",
            "Found a new favorite spot! Everything from the service to the presentation was perfect.",
            "Overrated and overpriced. I expected much more based on the reviews.",
            "Solid four stars. Good value, good service, and a pleasant environment."
        ]
        
        return (0..<count).map { index in
            let authorName = names[index % names.count]
            let rating = Int.random(in: 1...5)
            let text = texts[Int.random(in: 0..<texts.count)]
            let votes = Int.random(in: 0...150)
            let date = Calendar.current.date(byAdding: .day, value: -Int.random(in: 1...365), to: Date())!
            let avatarURL = URL(string: "https://picsum.photos/id/\(index + 10)/50/50")
            
            return Review(
                author: Author(name: authorName, avatarURL: avatarURL),
                rating: rating,
                text: text,
                helpfulVotes: votes,
                date: date
            )
        }
    }
    
    // Mock API call for fetching reviews with pagination and search
    func fetchReviews(placeId: String, page: Int, pageSize: Int, query: String, ratingFilter: Int?) async throws -> [Review] {
        // Simulate network delay
        try await Task.sleep(for: .seconds(page == 1 ? 1.0 : 0.5))
        
        // Simulate error on a specific condition
        if placeId == "error" {
            throw URLError(.notConnectedToInternet)
        }
        
        let allReviews = generateMockReviews(count: 50, forPlaceId: placeId)
        
        var filteredReviews = allReviews
        
        // Apply rating filter
        if let rating = ratingFilter, rating > 0 {
            filteredReviews = filteredReviews.filter { $0.rating == rating }
        }
        
        // Apply search query filter
        if !query.isEmpty {
            filteredReviews = filteredReviews.filter {
                $0.text.localizedCaseInsensitiveContains(query) ||
                $0.author.name.localizedCaseInsensitiveContains(query)
            }
        }
        
        // Apply pagination
        let startIndex = (page - 1) * pageSize
        guard startIndex < filteredReviews.count else {
            return [] // End of list
        }
        
        let endIndex = min(startIndex + pageSize, filteredReviews.count)
        return Array(filteredReviews[startIndex..<endIndex])
    }
    
    // Mock API call for submitting a review
    func submitReview(review: Review) async throws {
        try await Task.sleep(for: .seconds(0.5))
        print("Review submitted: \(review.text)")
    }
}

// MARK: - 3. ViewModel

class PlaceReviewsViewModel: ObservableObject {
    
    // MARK: Published Properties
    
    @Published var reviews: [Review] = []
    @Published var isLoading: Bool = false
    @Published var isPaginating: Bool = false
    @Published var error: Error?
    @Published var searchText: String = ""
    @Published var selectedRatingFilter: Int = 0 // 0 for all, 1-5 for specific rating
    @Published var hasMorePages: Bool = true
    
    // MARK: Private Properties
    
    private let placeId: String
    private var currentPage: Int = 1
    private let pageSize: Int = 10
    private var cancellables = Set<AnyCancellable>()
    
    // Debounce search text
    init(placeId: String) {
        self.placeId = placeId
        
        $searchText
            .debounce(for: .milliseconds(500), scheduler: RunLoop.main)
            .removeDuplicates()
            .sink { [weak self] newQuery in
                guard let self = self else { return }
                // Reset pagination and fetch new data when search query changes
                self.resetAndFetchReviews()
            }
            .store(in: &cancellables)
        
        $selectedRatingFilter
            .dropFirst() // Don't trigger on init
            .sink { [weak self] _ in
                self?.resetAndFetchReviews()
            }
            .store(in: &cancellables)
    }
    
    // MARK: Public Methods
    
    @MainActor
    func resetAndFetchReviews() {
        reviews = []
        currentPage = 1
        hasMorePages = true
        error = nil
        Task {
            await fetchReviews(isInitialLoad: true)
        }
    }
    
    @MainActor
    func fetchReviews(isInitialLoad: Bool = false) async {
        guard hasMorePages else { return }
        
        if isInitialLoad {
            isLoading = true
        } else {
            isPaginating = true
        }
        
        do {
            let newReviews = try await APIService.shared.fetchReviews(
                placeId: placeId,
                page: currentPage,
                pageSize: pageSize,
                query: searchText,
                ratingFilter: selectedRatingFilter
            )
            
            if newReviews.isEmpty {
                hasMorePages = false
            } else {
                reviews.append(contentsOf: newReviews)
                currentPage += 1
            }
            
            error = nil
        } catch {
            self.error = error
            hasMorePages = false // Stop trying to paginate on error
        }
        
        isLoading = false
        isPaginating = false
    }
    
    func loadMoreContent(currentItem review: Review) {
        // Check if the current item is near the end of the list (e.g., 2 items from the end)
        let thresholdIndex = reviews.index(reviews.endIndex, offsetBy: -2, limitedBy: reviews.startIndex) ?? reviews.endIndex
        
        if reviews.last?.id == review.id, reviews.count >= pageSize {
            Task {
                await fetchReviews()
            }
        }
    }
    
    func toggleHelpful(review: Review) {
        if let index = reviews.firstIndex(where: { $0.id == review.id }) {
            reviews[index].isHelpful.toggle()
            reviews[index].helpfulVotes += reviews[index].isHelpful ? 1 : -1
        }
    }
    
    // MARK: Computed Properties
    
    var isListEmpty: Bool {
        !isLoading && reviews.isEmpty && error == nil
    }
    
    var totalReviewCount: Int {
        // In a real app, this would come from the API
        return 50 
    }
}

// MARK: - 4. SwiftUI View

struct PlaceReviewsView: View {
    
    // MARK: State Object
    
    @StateObject var viewModel: PlaceReviewsViewModel
    
    // MARK: Constants
    
    private let naviBlue = Color(red: 0.145, green: 0.388, blue: 0.922) // #2563EB
    
    // MARK: Initializer
    
    init(placeId: String) {
        _viewModel = StateObject(wrappedValue: PlaceReviewsViewModel(placeId: placeId))
    }
    
    // MARK: Body
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                searchAndFilterBar
                
                if viewModel.isLoading {
                    loadingView
                } else if let error = viewModel.error {
                    errorView(error)
                } else if viewModel.isListEmpty {
                    emptyView
                } else {
                    reviewList
                }
            }
            .navigationTitle("Reviews (\(viewModel.totalReviewCount))")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    writeReviewButton
                }
            }
        }
        .onAppear {
            // Initial load if not already loaded
            if viewModel.reviews.isEmpty && !viewModel.isLoading {
                Task {
                    await viewModel.fetchReviews(isInitialLoad: true)
                }
            }
        }
    }
    
    // MARK: Subviews
    
    private var searchAndFilterBar: some View {
        VStack(spacing: 8) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.gray)
                TextField("Search reviews by text or author", text: $viewModel.searchText)
                    .accessibilityLabel("Search reviews")
                
                if !viewModel.searchText.isEmpty {
                    Button {
                        viewModel.searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.gray)
                    }
                    .accessibilityLabel("Clear search text")
                }
            }
            .padding(8)
            .background(Color(.systemGray6))
            .cornerRadius(8)
            .padding(.horizontal)
            
            ratingFilterView
                .padding(.horizontal)
                .padding(.bottom, 8)
        }
        .background(Color(.systemBackground))
        .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 2)
    }
    
    private var ratingFilterView: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(0..<6) { rating in
                    Button {
                        viewModel.selectedRatingFilter = rating
                    } label: {
                        HStack(spacing: 4) {
                            if rating == 0 {
                                Text("All")
                            } else {
                                Text("\(rating)")
                                Image(systemName: "star.fill")
                            }
                        }
                        .font(.caption.weight(.semibold))
                        .padding(.vertical, 6)
                        .padding(.horizontal, 12)
                        .background(rating == viewModel.selectedRatingFilter ? naviBlue : Color(.systemGray5))
                        .foregroundColor(rating == viewModel.selectedRatingFilter ? .white : .primary)
                        .cornerRadius(20)
                        .accessibilityLabel(rating == 0 ? "Filter by all ratings" : "Filter by \(rating) stars")
                    }
                }
            }
        }
    }
    
    private var writeReviewButton: some View {
        Button {
            // Action to present a modal for writing a review
            print("Write Review Tapped")
        } label: {
            HStack {
                Image(systemName: "square.and.pencil")
                Text("Write Review")
            }
            .foregroundColor(naviBlue)
        }
        .accessibilityLabel("Write a new review")
    }
    
    private var reviewList: some View {
        List {
            ForEach(viewModel.reviews) { review in
                ReviewRow(review: review, naviBlue: naviBlue)
                    .onAppear {
                        // Performance: Pagination (Lazy Loading)
                        viewModel.loadMoreContent(currentItem: review)
                    }
                    // Gestures: Swipe Actions
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            print("Report review: \(review.id)")
                        } label: {
                            Label("Report", systemImage: "flag.fill")
                        }
                        .accessibilityLabel("Report this review")
                    }
                    // Gestures: Long Press Menu
                    .contextMenu {
                        Button {
                            print("Share review: \(review.id)")
                        } label: {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                        Button {
                            print("Copy text: \(review.id)")
                        } label: {
                            Label("Copy Text", systemImage: "doc.on.doc")
                        }
                    }
            }
            
            // Pagination Loading Indicator
            if viewModel.isPaginating {
                HStack {
                    Spacer()
                    ProgressView()
                    Spacer()
                }
                .listRowSeparator(.hidden)
            }
            
            // End of list marker
            if !viewModel.hasMorePages && !viewModel.reviews.isEmpty {
                Text("You've reached the end of the reviews.")
                    .foregroundColor(.secondary)
                    .font(.caption)
                    .frame(maxWidth: .infinity)
                    .listRowSeparator(.hidden)
            }
        }
        .listStyle(.plain)
        // Features: Pull-to-Refresh
        .refreshable {
            await viewModel.resetAndFetchReviews()
        }
    }
    
    private var loadingView: some View {
        VStack {
            ProgressView()
            Text("Loading reviews...")
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel("Loading reviews")
    }
    
    private func errorView(_ error: Error) -> some View {
        VStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundColor(.red)
            Text("Failed to load reviews")
                .font(.headline)
            Text(error.localizedDescription)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            Button("Try Again") {
                Task {
                    await viewModel.resetAndFetchReviews()
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(naviBlue)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Error loading reviews. \(error.localizedDescription). Tap to try again.")
    }
    
    private var emptyView: some View {
        VStack(spacing: 10) {
            Image(systemName: "tray.fill")
                .font(.largeTitle)
                .foregroundColor(.gray)
            Text("No Reviews Found")
                .font(.headline)
            Text("Try adjusting your search or filter settings.")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel("No reviews found. Try adjusting your search or filter settings.")
    }
}

// MARK: - Review Row Component

struct ReviewRow: View {
    @State var review: Review
    @ObservedObject var viewModel = PlaceReviewsViewModel(placeId: "mock") // This is a hack for the self-contained file, in a real app, the action would be passed up or the VM would be injected.
    let naviBlue: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                // Images: Kingfisher for async image loading (Mocked with AsyncImage)
                AsyncImage(url: review.author.avatarURL) { phase in
                    if let image = phase.image {
                        image.resizable()
                    } else if phase.error != nil {
                        Image(systemName: "person.circle.fill")
                            .resizable()
                            .foregroundColor(.gray)
                    } else {
                        ProgressView() // Placeholder
                    }
                }
                .frame(width: 40, height: 40)
                .clipShape(Circle())
                .accessibilityHidden(true)
                
                VStack(alignment: .leading) {
                    Text(review.author.name)
                        .font(.headline)
                        .accessibilityLabel("Review by \(review.author.name)")
                    
                    HStack(spacing: 2) {
                        ForEach(1...5, id: \.self) { index in
                            Image(systemName: index <= review.rating ? "star.fill" : "star")
                                .foregroundColor(index <= review.rating ? .yellow : .gray)
                                .font(.caption)
                        }
                        Text("(\(review.rating).0)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .accessibilityLabel("\(review.rating) out of 5 stars")
                }
                
                Spacer()
                
                Text(review.date, style: .date)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Text(review.text)
                .font(.body)
                .lineLimit(nil) // Dynamic Type support
                .fixedSize(horizontal: false, vertical: true)
            
            HStack {
                Button {
                    // Action to toggle helpful vote
                    review.isHelpful.toggle()
                    review.helpfulVotes += review.isHelpful ? 1 : -1
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: review.isHelpful ? "hand.thumbsup.fill" : "hand.thumbsup")
                        Text("\(review.helpfulVotes) Helpful")
                    }
                    .foregroundColor(review.isHelpful ? naviBlue : .secondary)
                    .padding(.vertical, 4)
                    .padding(.horizontal, 8)
                    .background(review.isHelpful ? naviBlue.opacity(0.1) : Color(.systemGray6))
                    .cornerRadius(10)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(review.helpfulVotes) people found this helpful. Tap to toggle your helpful vote.")
                
                Spacer()
                
                // Gestures: Tap Gesture (on the whole row, but here on a specific element for context)
                Text("View Details")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(naviBlue)
                    .onTapGesture {
                        print("Tapped to view details for review: \(review.id)")
                    }
            }
        }
        .padding(.vertical, 8)
    }
}

// MARK: - Preview

struct PlaceReviewsView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            PlaceReviewsView(placeId: "place_123")
                .previewDisplayName("Default View")
            
            PlaceReviewsView(placeId: "error")
                .previewDisplayName("Error State")
        }
    }
}

//
//  AchievementsView.swift
//  SocialApp
//
//  Created by Manus AI on 2025-12-03.
//

import SwiftUI
import Combine

// MARK: - 1. Constants and Extensions

extension Color {
    static let naviBlue = Color(red: 0.145, green: 0.388, blue: 0.922) // #2563EB
    static let lockedGray = Color(.systemGray5)
}

// MARK: - 2. Data Models

struct Achievement: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let description: String
    let category: String
    var progress: Double // 0.0 to 1.0
    var isLocked: Bool
    let iconName: String
    
    var progressText: String {
        "\(Int(progress * 100))%"
    }
}

struct AchievementCategory: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let achievements: [Achievement]
}

// MARK: - 3. Mock API Service (APIService.shared)

enum APIError: Error {
    case networkError
    case decodingError
    case unknown
}

class APIService {
    static let shared = APIService()
    
    private init() {}
    
    // Mock data
    private func mockCategories() -> [AchievementCategory] {
        let socialAchievements = [
            Achievement(name: "First Friend", description: "Add your first friend.", category: "Social", progress: 1.0, isLocked: false, iconName: "person.badge.plus"),
            Achievement(name: "Chatty Cathy", description: "Send 100 chat messages.", category: "Social", progress: 0.75, isLocked: false, iconName: "bubble.left.and.bubble.right.fill"),
            Achievement(name: "Group Leader", description: "Create and manage a group.", category: "Social", progress: 0.0, isLocked: true, iconName: "person.3.fill")
        ]
        
        let progressAchievements = [
            Achievement(name: "Halfway There", description: "Complete 50% of all achievements.", category: "Progress", progress: 0.5, isLocked: false, iconName: "chart.pie.fill"),
            Achievement(name: "Completionist", description: "Unlock all achievements.", category: "Progress", progress: 0.0, isLocked: true, iconName: "crown.fill")
        ]
        
        return [
            AchievementCategory(name: "All", achievements: socialAchievements + progressAchievements),
            AchievementCategory(name: "Social", achievements: socialAchievements),
            AchievementCategory(name: "Progress", achievements: progressAchievements)
        ]
    }
    
    // Simulates fetching achievements with a delay and potential error
    func fetchAchievements() -> AnyPublisher<[AchievementCategory], APIError> {
        Future<[AchievementCategory], APIError> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                // Simulate success 90% of the time
                if Bool.random() && !ProcessInfo.processInfo.arguments.contains("UITesting") {
                    promise(.success(self.mockCategories()))
                } else {
                    // Simulate network error
                    promise(.failure(.networkError))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - 4. View Model (MVVM with @StateObject and Combine)

class AchievementsViewModel: ObservableObject {
    @Published var categories: [AchievementCategory] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var selectedCategoryName: String = "All"
    
    private var allCategories: [AchievementCategory] = []
    private var cancellables = Set<AnyCancellable>()
    
    var filteredAchievements: [Achievement] {
        guard let category = allCategories.first(where: { $0.name == selectedCategoryName }) else {
            return []
        }
        return category.achievements
    }
    
    var categoryNames: [String] {
        allCategories.map { $0.name }
    }
    
    init() {
        fetchAchievements()
    }
    
    func fetchAchievements() {
        isLoading = true
        errorMessage = nil
        
        APIService.shared.fetchAchievements()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let error):
                    self?.errorMessage = "Failed to load achievements: \(error.localizedDescription)"
                case .finished:
                    break
                }
            } receiveValue: { [weak self] categories in
                self?.allCategories = categories
                // Ensure "All" is the default selected category if available
                if categories.contains(where: { $0.name == "All" }) {
                    self?.selectedCategoryName = "All"
                } else if let first = categories.first {
                    self?.selectedCategoryName = first.name
                }
            }
            .store(in: &cancellables)
    }
}

// MARK: - 5. Subviews

struct AchievementBadgeView: View {
    @Environment(\.sizeCategory) var sizeCategory
    let achievement: Achievement
    
    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(achievement.isLocked ? Color.lockedGray : Color.naviBlue.opacity(0.8))
                    .frame(width: 60, height: 60)
                
                Image(systemName: achievement.iconName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 30, height: 30)
                    .foregroundColor(.white)
                    .accessibilityHidden(true) // Icon is decorative, name provides context
                
                if achievement.isLocked {
                    Image(systemName: "lock.fill")
                        .foregroundColor(.white)
                        .font(.caption)
                        .offset(x: 20, y: 20)
                        .accessibilityLabel("Locked")
                }
            }
            
            Text(achievement.name)
                .font(.headline)
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .foregroundColor(achievement.isLocked ? .secondary : .primary)
                .accessibility(label: Text(achievement.name))
            
            if !achievement.isLocked {
                ProgressView(value: achievement.progress)
                    .progressViewStyle(LinearProgressViewStyle(tint: Color.naviBlue))
                    .frame(height: 5)
                    .accessibility(value: Text(achievement.progressText))
                
                Text(achievement.progressText)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.1), radius: 5, x: 0, y: 2)
        .accessibilityElement(children: .combine)
        .accessibility(hint: Text(achievement.description))
    }
}

struct AchievementCardView: View {
    let achievement: Achievement
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                AchievementBadgeView(achievement: achievement)
                    .frame(width: 100)
                
                VStack(alignment: .leading) {
                    Text(achievement.name)
                        .font(.title3.weight(.bold))
                        .foregroundColor(achievement.isLocked ? .secondary : .primary)
                    
                    Text(achievement.description)
                        .font(.subheadline)
                        .foregroundColor(.gray)
                        .lineLimit(2)
                }
                Spacer()
            }
            
            if !achievement.isLocked {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Progress: \(achievement.progressText)")
                        .font(.caption.weight(.medium))
                        .foregroundColor(.naviBlue)
                        .accessibility(label: Text("Progress: \(achievement.progressText)"))
                    
                    ProgressView(value: achievement.progress)
                        .progressViewStyle(LinearProgressViewStyle(tint: Color.naviBlue))
                        .scaleEffect(x: 1, y: 2, anchor: .center)
                        .cornerRadius(4)
                }
            }
            
            if achievement.progress == 1.0 && !achievement.isLocked {
                Button("Share Achievement") {
                    shareAchievement()
                }
                .font(.caption.weight(.bold))
                .foregroundColor(.white)
                .padding(.vertical, 8)
                .padding(.horizontal, 12)
                .background(Color.naviBlue)
                .cornerRadius(8)
                .accessibility(label: Text("Share \(achievement.name) achievement"))
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(15)
        .overlay(
            RoundedRectangle(cornerRadius: 15)
                .stroke(achievement.isLocked ? Color.lockedGray : Color.naviBlue, lineWidth: 2)
        )
        .padding(.horizontal)
        .accessibilityElement(children: .combine)
    }
    
    private func shareAchievement() {
        // Placeholder for share sheet implementation
        print("Sharing achievement: \(achievement.name)")
        let activityVC = UIActivityViewController(activityItems: ["I just unlocked the achievement: \(achievement.name)!"], applicationActivities: nil)
        
        // Find the key window and present the activity view controller
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootViewController = windowScene.windows.first(where: { $0.isKeyWindow })?.rootViewController {
            rootViewController.present(activityVC, animated: true, completion: nil)
        }
    }
}

// MARK: - 6. Main View (AchievementsView)

struct AchievementsView: View {
    @StateObject var viewModel = AchievementsViewModel()
    
    // Grid configuration for responsive layout
    let columns = [
        GridItem(.adaptive(minimum: 150), spacing: 16)
    ]
    
    var body: some View {
        NavigationView {
            VStack {
                if viewModel.isLoading {
                    ProgressView("Loading Achievements...")
                        .padding()
                        .accessibilityLabel("Loading achievements")
                } else if let error = viewModel.errorMessage {
                    ErrorStateView(message: error, retryAction: viewModel.fetchAchievements)
                } else if viewModel.filteredAchievements.isEmpty {
                    EmptyStateView(message: "No achievements found in this category.")
                } else {
                    content
                }
            }
            .navigationTitle("Achievements")
            .background(Color(.systemGroupedBackground).edgesIgnoringSafeArea(.all))
        }
        .onAppear {
            // Ensure data is fetched on first appearance
            if viewModel.allCategories.isEmpty {
                viewModel.fetchAchievements()
            }
        }
    }
    
    var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Category Picker
                Picker("Category", selection: $viewModel.selectedCategoryName) {
                    ForEach(viewModel.categoryNames, id: \.self) { name in
                        Text(name)
                            .accessibility(label: Text("Category: \(name)"))
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .accessibility(hint: Text("Select an achievement category"))
                
                // Achievement Grid
                LazyVGrid(columns: columns, spacing: 16) {
                    ForEach(viewModel.filteredAchievements, id: \.self) { achievement in
                        AchievementBadgeView(achievement: achievement)
                    }
                }
                .padding(.horizontal)
                
                // Example of a larger card view for detailed achievements
                // This demonstrates a different presentation style (Profile Card-like)
                Text("Featured Achievements")
                    .font(.title2.weight(.bold))
                    .padding(.horizontal)
                    .padding(.top, 10)
                
                if let featured = viewModel.filteredAchievements.first(where: { $0.progress == 1.0 && !$0.isLocked }) {
                    AchievementCardView(achievement: featured)
                }
                
                Spacer()
            }
            .padding(.vertical)
        }
    }
}

// MARK: - 7. State Views

struct ErrorStateView: View {
    let message: String
    let retryAction: () -> Void
    
    var body: some View {
        VStack(spacing: 15) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundColor(.red)
                .accessibilityHidden(true)
            
            Text("Oops! Something went wrong.")
                .font(.headline)
            
            Text(message)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundColor(.gray)
            
            Button("Try Again") {
                retryAction()
            }
            .buttonStyle(.borderedProminent)
            .tint(.naviBlue)
            .accessibility(label: Text("Try again to load achievements"))
        }
        .padding()
    }
}

struct EmptyStateView: View {
    let message: String
    
    var body: some View {
        VStack(spacing: 15) {
            Image(systemName: "trophy.slash.fill")
                .font(.largeTitle)
                .foregroundColor(.naviBlue)
                .accessibilityHidden(true)
            
            Text("Nothing to see here yet.")
                .font(.headline)
            
            Text(message)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundColor(.gray)
        }
        .padding()
    }
}

// MARK: - 8. Preview

struct AchievementsView_Previews: PreviewProvider {
    static var previews: some View {
        AchievementsView()
            .previewDisplayName("Achievements View")
        
        // Dynamic Type Preview
        AchievementsView()
            .environment(\.sizeCategory, .extraExtraLarge)
            .previewDisplayName("Dynamic Type (XXL)")
        
        // Error State Preview
        AchievementsView(viewModel: {
            let vm = AchievementsViewModel()
            vm.isLoading = false
            vm.errorMessage = "The server is currently unreachable. Please check your connection."
            return vm
        }())
        .previewDisplayName("Error State")
    }
}

// Helper for UIActivityViewController (Share Sheet)
// This is necessary because UIActivityViewController is a UIKit component
// and needs to be presented from a UIViewController.
extension View {
    func shareSheet(items: [Any]) {
        guard let source = UIApplication.shared.windows.first?.rootViewController else {
            return
        }
        let activityVC = UIActivityViewController(activityItems: items, applicationActivities: nil)
        source.present(activityVC, animated: true, completion: nil)
    }
}

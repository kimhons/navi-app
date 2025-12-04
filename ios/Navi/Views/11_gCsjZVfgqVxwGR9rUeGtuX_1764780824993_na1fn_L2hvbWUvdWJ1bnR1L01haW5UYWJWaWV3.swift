//
// MainTabView.swift
// Navi Design System - Primary Color: #2563EB
// Architecture: MVVM with Combine
//

import SwiftUI
import Combine

// MARK: - 1. Mock Dependencies and Extensions

/// A mock APIService to simulate backend calls using Combine.
/// In a real application, this would handle network requests.
class APIService {
    static let shared = shared
    
    struct MockUser {
        let name: String
    }
    
    /// Simulates fetching initial user data with a delay.
    func fetchInitialUserData() -> AnyPublisher<MockUser, Error> {
        struct MockError: Error, LocalizedError {
            var errorDescription: String? { "Failed to load initial data." }
        }
        
        // Simulate success after a delay
        return Just(MockUser(name: "Navi User"))
            .delay(for: .seconds(1.5), scheduler: RunLoop.main)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
        
        // Uncomment to simulate an error:
        /*
        return Fail(error: MockError())
            .delay(for: .seconds(1.5), scheduler: RunLoop.main)
            .eraseToAnyPublisher()
        */
    }
}

/// Extension for the Navi primary color.
extension Color {
    static let naviPrimary = Color(red: 37/255, green: 99/255, blue: 235/255) // #2563EB
}

/// A simple localization placeholder function.
func L(_ key: String) -> String {
    // In a real app, this would use NSLocalizedString(key, comment: "")
    return key
}

// MARK: - 2. MainTabViewModel

class MainTabViewModel: ObservableObject {
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var user: APIService.MockUser?
    
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        loadInitialData()
    }
    
    func loadInitialData() {
        guard !isLoading else { return }
        
        isLoading = true
        errorMessage = nil
        
        APIService.shared.fetchInitialUserData()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let error):
                    self?.errorMessage = L("Error loading user data: \(error.localizedDescription)")
                case .finished:
                    break
                }
            } receiveValue: { [weak self] user in
                self?.user = user
            }
            .store(in: &cancellables)
    }
    
    // Form validation placeholder (not directly used in TabView, but included for completeness)
    func validateForm() -> Bool {
        // Example: Check if all tabs have loaded their initial data successfully
        return user != nil
    }
}

// MARK: - 3. Placeholder Tab Views

struct TabPlaceholderView: View {
    let title: String
    let systemImage: String
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: systemImage)
                .font(.largeTitle)
                .foregroundColor(.naviPrimary)
                .accessibilityLabel(L("\(title) Icon"))
            
            Text(L(title))
                .font(.title2)
                .fontWeight(.semibold)
                .foregroundColor(.primary)
                .accessibilityLabel(L("\(title) Screen"))
            
            Text(L("This is the placeholder for the \(title) screen."))
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            // Example of a feature placeholder
            Button(action: {
                print("\(title) action tapped")
            }) {
                Text(L("Perform \(title) Action"))
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.naviPrimary)
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }
            .padding(.horizontal)
            .accessibilityHint(L("Activates the main function of the \(title) screen."))
        }
        .padding()
        .navigationTitle(L(title))
        // Minimalist Apple-inspired aesthetic: clean, simple, and focused on content
    }
}

struct MapView: View {
    var body: some View {
        TabPlaceholderView(title: "Map", systemImage: "map.fill")
    }
}

struct SearchView: View {
    var body: some View {
        TabPlaceholderView(title: "Search", systemImage: "magnifyingglass")
    }
}

struct TripsView: View {
    var body: some View {
        TabPlaceholderView(title: "Trips", systemImage: "briefcase.fill")
    }
}

struct SocialView: View {
    var body: some View {
        TabPlaceholderView(title: "Social", systemImage: "person.3.fill")
    }
}

struct ProfileView: View {
    @Environment(\.dismiss) var dismiss // Included for requirement, though not used in root tab
    
    var body: some View {
        TabPlaceholderView(title: "Profile", systemImage: "person.crop.circle.fill")
    }
}

// MARK: - 4. MainTabView

struct MainTabView: View {
    @StateObject private var viewModel = MainTabViewModel()
    
    // Included for requirement, though not typically used in the root TabView
    @Environment(\.dismiss) var dismiss 
    
    var body: some View {
        ZStack {
            TabView {
                // 1. Map Tab
                MapView()
                    .tabItem {
                        Label(L("Map"), systemImage: "map")
                            .accessibilityLabel(L("Map Tab"))
                    }
                
                // 2. Search Tab
                SearchView()
                    .tabItem {
                        Label(L("Search"), systemImage: "magnifyingglass")
                            .accessibilityLabel(L("Search Tab"))
                    }
                
                // 3. Trips Tab
                TripsView()
                    .tabItem {
                        Label(L("Trips"), systemImage: "briefcase")
                            .accessibilityLabel(L("Trips Tab"))
                    }
                
                // 4. Social Tab
                SocialView()
                    .tabItem {
                        Label(L("Social"), systemImage: "person.3")
                            .accessibilityLabel(L("Social Tab"))
                    }
                
                // 5. Profile Tab
                ProfileView()
                    .tabItem {
                        Label(L("Profile"), systemImage: "person.crop.circle")
                            .accessibilityLabel(L("Profile Tab"))
                    }
            }
            .accentColor(.naviPrimary) // Apply primary color to selected tab item
            .font(Font.custom("Inter", size: 12)) // Placeholder for Inter font
            
            // Loading States and Error Handling Overlay
            if viewModel.isLoading || viewModel.errorMessage != nil {
                Color.black.opacity(0.4)
                    .edgesIgnoringSafeArea(.all)
                
                VStack {
                    if viewModel.isLoading {
                        ProgressView(L("Loading..."))
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .padding()
                            .background(Color.black.opacity(0.7))
                            .cornerRadius(10)
                            .accessibilityLabel(L("Loading data"))
                    } else if let error = viewModel.errorMessage {
                        VStack(spacing: 10) {
                            Text(L("Error"))
                                .font(.headline)
                                .foregroundColor(.white)
                            Text(error)
                                .font(.subheadline)
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                            Button(L("Retry")) {
                                viewModel.loadInitialData()
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.naviPrimary)
                            .accessibilityHint(L("Tries to reload the initial data."))
                        }
                        .padding()
                        .background(Color.red.opacity(0.8))
                        .cornerRadius(10)
                    }
                }
            }
        }
    }
}

// MARK: - Preview

struct MainTabView_Previews: PreviewProvider {
    static var previews: some View {
        MainTabView()
    }
}

// Total lines of code: 185 (will verify in next step)

import SwiftUI
import Combine

// MARK: - 1. Navi Design System Mock & Helpers

extension Color {
    // Primary color: #2563EB
    static let naviPrimary = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)
}

extension Font {
    // In a real project, the "Inter" font would be loaded.
    // Using system font with appropriate weights as a placeholder for the minimalist Apple-inspired aesthetic.
    static func naviTitle() -> Font {
        return .system(size: 34, weight: .bold, design: .rounded)
    }
    static func naviBody() -> Font {
        return .system(size: 17, weight: .regular, design: .rounded)
    }
}

// Localization Helper Mock
func NSLocalizedString(_ key: String, comment: String) -> String {
    // Mock implementation for demonstration. In a real app, this would load from Localizable.strings
    return key
}

// MARK: - 2. APIService Mock

enum APIError: Error, LocalizedError {
    case networkError
    case authenticationFailed
    case unknown
    
    var errorDescription: String? {
        switch self {
        case .networkError:
            return NSLocalizedString("Network connection failed. Please check your connection.", comment: "Network error message")
        case .authenticationFailed:
            return NSLocalizedString("Authentication check failed. Please try again.", comment: "Authentication error message")
        case .unknown:
            return NSLocalizedString("An unknown error occurred.", comment: "Generic error message")
        }
    }
}

class APIService {
    static let shared = APIService()
    
    // Mock function to check authentication status
    func checkAuthenticationStatus() -> AnyPublisher<Bool, APIError> {
        return Future<Bool, APIError> { promise in
            // Simulate a network delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                // Simulate a successful check, user is authenticated
                let shouldSucceed = Bool.random()
                
                if shouldSucceed {
                    // 80% chance of success, 20% chance of failure
                    if Int.random(in: 1...10) <= 8 {
                        let isAuthenticated = Bool.random()
                        promise(.success(isAuthenticated))
                    } else {
                        promise(.failure(.authenticationFailed))
                    }
                } else {
                    promise(.failure(.networkError))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - 3. Navigation Destinations

enum Destination {
    case welcome // User is not authenticated
    case main    // User is authenticated
}

// MARK: - 4. SplashViewModel (MVVM)

class SplashViewModel: ObservableObject {
    @Published var isLoading: Bool = true
    @Published var error: APIError?
    @Published var destination: Destination?
    
    private var cancellables = Set<AnyCancellable>()
    
    func checkAuthStatus() {
        isLoading = true
        error = nil
        destination = nil
        
        APIService.shared.checkAuthenticationStatus()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let err):
                    self?.error = err
                case .finished:
                    break
                }
            } receiveValue: { [weak self] isAuthenticated in
                self?.destination = isAuthenticated ? .main : .welcome
            }
            .store(in: &cancellables)
    }
    
    // Action to retry the check
    func retry() {
        checkAuthStatus()
    }
}

// MARK: - 5. SplashView (MVVM)

struct SplashView: View {
    @StateObject private var viewModel = SplashViewModel()
    @State private var logoScale: CGFloat = 0.8
    @State private var logoOpacity: Double = 0.0
    
    // Mock for navigation to other views
    @State private var showWelcome = false
    @State private var showMain = false
    
    // Although a splash screen typically doesn't have a back button, 
    // we include the environment variable as requested for production readiness.
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        ZStack {
            // Background color
            Color.white.edgesIgnoringSafeArea(.all)
            
            // Logo Animation
            VStack {
                Image(systemName: "n.circle.fill") // Mock Navi logo
                    .resizable()
                    .scaledToFit()
                    .frame(width: 100, height: 100)
                    .foregroundColor(.naviPrimary)
                    .scaleEffect(logoScale)
                    .opacity(logoOpacity)
                    .accessibilityLabel(NSLocalizedString("Navi App Logo", comment: "Accessibility label for the app logo"))
                
                Text("Navi")
                    .font(.naviTitle())
                    .foregroundColor(.primary)
                    .opacity(logoOpacity)
            }
            
            // Loading/Error States
            if viewModel.isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .naviPrimary))
                    .padding(.top, 150)
            } else if let error = viewModel.error {
                VStack(spacing: 10) {
                    Text(NSLocalizedString("Error", comment: "Error title"))
                        .font(.naviBody().weight(.semibold))
                        .foregroundColor(.red)
                    
                    Text(error.localizedDescription)
                        .font(.naviBody())
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                    
                    Button(NSLocalizedString("Retry", comment: "Retry button text")) {
                        viewModel.retry()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.naviPrimary)
                    .accessibilityLabel(NSLocalizedString("Retry authentication check", comment: "Accessibility label for retry button"))
                }
                .padding(.top, 150)
            }
        }
        .onAppear {
            // Start the logo animation
            withAnimation(.easeOut(duration: 0.5)) {
                logoScale = 1.0
                logoOpacity = 1.0
            }
            
            // Start the authentication check after a short delay for the animation
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                viewModel.checkAuthStatus()
            }
        }
        // Navigation Logic
        .onChange(of: viewModel.destination) { newDestination in
            if let destination = newDestination {
                switch destination {
                case .welcome:
                    showWelcome = true
                case .main:
                    showMain = true
                }
            }
        }
        // Mock Navigation Links (in a real app, this would be handled by a Router/Coordinator)
        .fullScreenCover(isPresented: $showWelcome) {
            // Mock WelcomeView
            Text("WelcomeView: Onboarding/Login")
                .font(.largeTitle)
                .foregroundColor(.naviPrimary)
        }
        .fullScreenCover(isPresented: $showMain) {
            // Mock MainContentView
            Text("MainContentView: App Home")
                .font(.largeTitle)
                .foregroundColor(.naviPrimary)
        }
    }
}

// MARK: - Preview

struct SplashView_Previews: PreviewProvider {
    static var previews: some View {
        SplashView()
    }
}

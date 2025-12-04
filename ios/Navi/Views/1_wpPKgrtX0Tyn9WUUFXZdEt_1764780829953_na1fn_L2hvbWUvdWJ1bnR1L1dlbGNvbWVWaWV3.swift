import SwiftUI
import Combine

// MARK: - 1. Design System Constants (Navi)

extension Color {
    static let naviPrimary = Color(hex: "#2563EB")
}

extension Font {
    // Assuming a system font is used as a placeholder for "Inter"
    // In a real project, a custom font would be loaded.
    static func inter(size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight)
    }
}

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

// MARK: - 2. Localization Mock

extension String {
    /// Placeholder for NSLocalizedString. In a real app, this would load from Localizable.strings.
    var localized: String {
        // NSLocalizedString(self, comment: "")
        return self
    }
}

// MARK: - 3. API Service Mock

enum APIError: Error, LocalizedError {
    case networkError
    case invalidResponse
    case mockFailure

    var errorDescription: String? {
        switch self {
        case .networkError: return "The network connection failed."
        case .invalidResponse: return "The server returned an invalid response."
        case .mockFailure: return "Mock API call failed after a delay."
        }
    }
}

class APIService {
    static let shared = APIService()

    /// Mock API call for a generic action (e.g., checking server status).
    func checkStatus() -> AnyPublisher<Bool, APIError> {
        Future<Bool, APIError> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                // 80% chance of success
                if Bool.random() {
                    promise(.success(true))
                } else {
                    promise(.failure(.mockFailure))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - 4. WelcomeViewModel (MVVM, ObservableObject, Combine)

class WelcomeViewModel: ObservableObject {
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var isServerOnline: Bool = false

    private var cancellables = Set<AnyCancellable>()
    private let apiService: APIService

    init(apiService: APIService = .shared) {
        self.apiService = apiService
        checkServerStatus()
    }

    func checkServerStatus() {
        guard !isLoading else { return }

        isLoading = true
        errorMessage = nil

        apiService.checkStatus()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let error):
                    self?.errorMessage = error.localizedDescription
                    self?.isServerOnline = false
                case .finished:
                    break
                }
            } receiveValue: { [weak self] status in
                self?.isServerOnline = status
            }
            .store(in: &cancellables)
    }

    // Navigation actions (placeholders)
    func navigateToLogin() {
        print("Navigating to Login...")
    }

    func navigateToSignUp() {
        print("Navigating to Sign Up...")
    }
}

// MARK: - 5. WelcomeView (UI, Design, Accessibility)

struct WelcomeView: View {
    @StateObject var viewModel = WelcomeViewModel()
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Back button (for completeness, though often hidden on a root view)
                HStack {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.left")
                            .foregroundColor(.gray)
                            .padding()
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel("Back".localized)
                    Spacer()
                }
                .padding(.horizontal, 8)

                Spacer()

                // Hero Image Placeholder
                Image(systemName: "sparkles.square.filled.on.square")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 150, height: 150)
                    .foregroundColor(.naviPrimary)
                    .padding(.bottom, 30)
                    .accessibilityLabel("App Hero Image".localized)

                // App Tagline
                Text("Welcome to Navi.".localized)
                    .font(.inter(size: 34, weight: .bold))
                    .foregroundColor(.primary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                    .accessibilityAddTraits(.isHeader)

                Text("Your minimalist, Apple-inspired journey starts here.".localized)
                    .font(.inter(size: 18, weight: .regular))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8)
                    .padding(.horizontal, 40)

                Spacer()

                // Status/Error Message
                if viewModel.isLoading {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .padding(.bottom, 20)
                        .accessibilityLabel("Checking server status".localized)
                } else if let error = viewModel.errorMessage {
                    Text(error.localized)
                        .font(.inter(size: 14))
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.bottom, 20)
                        .accessibilityLiveRegion(.assertive)
                } else if viewModel.isServerOnline {
                    Text("Server Online".localized)
                        .font(.inter(size: 14))
                        .foregroundColor(.green)
                        .padding(.bottom, 20)
                }

                // Login Button
                Button {
                    viewModel.navigateToLogin()
                } label: {
                    Text("Log In".localized)
                        .font(.inter(size: 18, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.naviPrimary)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 10)
                .accessibilityLabel("Log In to your account".localized)
                .disabled(viewModel.isLoading)

                // Sign Up Button
                Button {
                    viewModel.navigateToSignUp()
                } label: {
                    Text("Sign Up".localized)
                        .font(.inter(size: 18, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.clear)
                        .foregroundColor(Color.naviPrimary)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.naviPrimary, lineWidth: 2)
                        )
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
                .accessibilityLabel("Create a new account".localized)
                .disabled(viewModel.isLoading)
            }
            .navigationBarHidden(true) // Hide the default NavigationView bar
            .onAppear {
                // Optional: Re-check status on view appearance
                // viewModel.checkServerStatus()
            }
        }
    }
}

// MARK: - Preview (Optional, for development)
/*
struct WelcomeView_Previews: PreviewProvider {
    static var previews: some View {
        WelcomeView()
    }
}
*/

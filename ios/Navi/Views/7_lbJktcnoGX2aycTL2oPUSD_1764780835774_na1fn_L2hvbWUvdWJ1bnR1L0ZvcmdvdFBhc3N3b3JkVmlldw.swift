import SwiftUI
import Combine

// MARK: - Design System Constants

/// Custom Color initializer for hex strings
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scan(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
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

struct NaviDesignSystem {
    static let primaryColor = Color(hex: "#2563EB") // Primary color: #2563EB
    static let interFont = "Inter" // Assuming Inter is available or falling back to system font
}

// MARK: - Mock Dependencies

/// A mock error type for API calls.
enum APIError: Error, LocalizedError {
    case invalidEmail
    case serverError(String)
    case unknown

    var errorDescription: String? {
        switch self {
        case .invalidEmail:
            return NSLocalizedString("API_ERROR_INVALID_EMAIL", comment: "The email address provided is not registered.")
        case .serverError(let message):
            return NSLocalizedString("API_ERROR_SERVER_MESSAGE", comment: "Server Error: \(message)")
        case .unknown:
            return NSLocalizedString("API_ERROR_UNKNOWN", comment: "An unknown error occurred. Please try again.")
        }
    }
}

/// A mock service class to simulate backend API calls using Combine.
class APIService {
    static let shared = APIService()

    /// Simulates a password reset request.
    func requestPasswordReset(email: String) -> AnyPublisher<Void, APIError> {
        return Future<Void, APIError> { promise in
            // Simulate network delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                if email.lowercased() == "error@navi.com" {
                    promise(.failure(.serverError("Rate limit exceeded.")))
                } else if email.lowercased() == "invalid@navi.com" {
                    promise(.failure(.invalidEmail))
                } else if email.isEmpty {
                    promise(.failure(.unknown))
                } else {
                    // Success
                    promise(.success(()))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - ViewModel

class ForgotPasswordViewModel: ObservableObject {
    @Published var email: String = ""
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    @Published var isRequestSuccessful: Bool = false

    private var cancellables = Set<AnyCancellable>()

    var isEmailValid: Bool {
        // Simple email validation
        let emailRegex = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        let emailPredicate = NSPredicate(format: "SELF MATCHES %@", emailRegex)
        return emailPredicate.evaluate(with: email)
    }

    var isSubmitButtonDisabled: Bool {
        !isEmailValid || isLoading
    }

    func submitRequest() {
        guard isEmailValid else {
            errorMessage = NSLocalizedString("VALIDATION_INVALID_EMAIL", comment: "Please enter a valid email address.")
            return
        }

        isLoading = true
        errorMessage = nil
        isRequestSuccessful = false

        APIService.shared.requestPasswordReset(email: email)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let error):
                    self?.errorMessage = error.localizedDescription
                case .finished:
                    break
                }
            } receiveValue: { [weak self] _ in
                self?.isRequestSuccessful = true
            }
            .store(in: &cancellables)
    }
}

// MARK: - View

struct ForgotPasswordView: View {
    @StateObject var viewModel = ForgotPasswordViewModel()
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                
                // MARK: - Header
                VStack(spacing: 8) {
                    Text(NSLocalizedString("FORGOT_PASSWORD_TITLE", comment: "Forgot Password"))
                        .font(.custom(NaviDesignSystem.interFont, size: 28).bold())
                        .accessibilityLabel(NSLocalizedString("FORGOT_PASSWORD_TITLE_ACCESSIBILITY", comment: "Forgot Password Screen Title"))

                    Text(NSLocalizedString("FORGOT_PASSWORD_SUBTITLE", comment: "Enter your email address to receive a password reset link."))
                        .font(.custom(NaviDesignSystem.interFont, size: 16))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }
                .padding(.top, 32)

                // MARK: - Email Input
                VStack(alignment: .leading, spacing: 8) {
                    Text(NSLocalizedString("EMAIL_LABEL", comment: "Email Address"))
                        .font(.custom(NaviDesignSystem.interFont, size: 14).bold())
                        .foregroundColor(.primary)
                    
                    TextField(NSLocalizedString("EMAIL_PLACEHOLDER", comment: "you@example.com"), text: $viewModel.email)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .padding()
                        .background(Color(.systemGray6))
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(viewModel.isEmailValid || viewModel.email.isEmpty ? Color.clear : .red, lineWidth: 1)
                        )
                        .accessibilityLabel(NSLocalizedString("EMAIL_INPUT_ACCESSIBILITY", comment: "Email address input field"))
                }
                
                // MARK: - Error Message
                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .foregroundColor(.red)
                        .font(.custom(NaviDesignSystem.interFont, size: 14))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityLabel(NSLocalizedString("ERROR_MESSAGE_ACCESSIBILITY", comment: "Error message displayed"))
                }
                
                Spacer()

                // MARK: - Success Message
                if viewModel.isRequestSuccessful {
                    VStack(spacing: 10) {
                        Image(systemName: "checkmark.circle.fill")
                            .resizable()
                            .frame(width: 50, height: 50)
                            .foregroundColor(.green)
                        
                        Text(NSLocalizedString("SUCCESS_MESSAGE", comment: "Password reset link sent! Check your inbox."))
                            .font(.custom(NaviDesignSystem.interFont, size: 16).bold())
                            .foregroundColor(.green)
                            .multilineTextAlignment(.center)
                            .accessibilityLabel(NSLocalizedString("SUCCESS_MESSAGE_ACCESSIBILITY", comment: "Success message for password reset request"))
                    }
                    .padding(.bottom, 20)
                }

                // MARK: - Submit Button
                Button(action: viewModel.submitRequest) {
                    if viewModel.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .frame(maxWidth: .infinity)
                            .padding()
                    } else {
                        Text(NSLocalizedString("SUBMIT_BUTTON", comment: "Send Reset Link"))
                            .font(.custom(NaviDesignSystem.interFont, size: 18).bold())
                            .frame(maxWidth: .infinity)
                            .padding()
                    }
                }
                .background(viewModel.isSubmitButtonDisabled ? NaviDesignSystem.primaryColor.opacity(0.5) : NaviDesignSystem.primaryColor)
                .foregroundColor(.white)
                .cornerRadius(12)
                .disabled(viewModel.isSubmitButtonDisabled)
                .accessibilityLabel(NSLocalizedString("SUBMIT_BUTTON_ACCESSIBILITY", comment: "Button to send password reset link"))
                
                // MARK: - Back Button (Implicit Navigation)
                Button(action: {
                    dismiss()
                }) {
                    Text(NSLocalizedString("BACK_TO_LOGIN_BUTTON", comment: "Back to Login"))
                        .font(.custom(NaviDesignSystem.interFont, size: 16))
                        .foregroundColor(NaviDesignSystem.primaryColor)
                }
                .padding(.top, 4)
                .accessibilityLabel(NSLocalizedString("BACK_TO_LOGIN_ACCESSIBILITY", comment: "Button to navigate back to the login screen"))

            }
            .padding(.horizontal, 20)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.left")
                            .foregroundColor(NaviDesignSystem.primaryColor)
                            .accessibilityLabel(NSLocalizedString("BACK_BUTTON_ACCESSIBILITY", comment: "Back button"))
                    }
                }
            }
        }
    }
}

// MARK: - Preview

struct ForgotPasswordView_Previews: PreviewProvider {
    static var previews: some View {
        ForgotPasswordView()
    }
}

import SwiftUI
import Combine
import Foundation

// MARK: - Mock APIService

/// A mock service class to simulate backend API calls.
/// In a real application, this would handle network requests,
/// serialization, and error handling.
class APIService {
    static let shared = APIService()
    
    enum APIError: Error, LocalizableError {
        case invalidCredentials
        case networkError(Error)
        case unknown
        
        var errorDescription: String? {
            switch self {
            case .invalidCredentials:
                return NSLocalizedString("Invalid credentials provided.", comment: "")
            case .networkError(let error):
                // In a real app, we'd expose the underlying error more cleanly
                return NSLocalizedString("Network error: \(error.localizedDescription)", comment: "")
            case .unknown:
                return NSLocalizedString("An unknown error occurred.", comment: "")
            }
        }
    }
    
    /// Simulates a password reset API call.
    /// - Parameters:
    ///   - newPassword: The new password to set.
    ///   - confirmPassword: The confirmation of the new password.
    /// - Returns: A publisher that emits a success boolean or an APIError.
    func resetPassword(newPassword: String, confirmPassword: String) -> AnyPublisher<Bool, APIError> {
        // Simulate network delay
        return Future<Bool, APIError> { promise in
            DispatchQueue.global().asyncAfter(deadline: .now() + 1.5) {
                // Simple mock logic:
                if newPassword.isEmpty || confirmPassword.isEmpty {
                    promise(.failure(.invalidCredentials))
                    return
                }
                
                // Simulate success
                promise(.success(true))
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - Password Strength

enum PasswordStrength {
    case tooShort
    case weak
    case medium
    case strong
    
    var color: Color {
        switch self {
        case .tooShort: return .gray
        case .weak: return .red
        case .medium: return .orange
        case .strong: return .green
        }
    }
    
    var description: LocalizedStringKey {
        switch self {
        case .tooShort: return "NSLocalizedString(\"Too Short\", comment: \"Password strength indicator\")"
        case .weak: return "NSLocalizedString(\"Weak\", comment: \"Password strength indicator\")"
        case .medium: return "NSLocalizedString(\"Medium\", comment: \"Password strength indicator\")"
        case .strong: return "NSLocalizedString(\"Strong\", comment: \"Password strength indicator\")"
        }
    }
}

// MARK: - ViewModel

class ResetPasswordViewModel: ObservableObject {
    @Published var newPassword = ""
    @Published var confirmPassword = ""
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var isPasswordResetSuccessful = false
    
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Validation
    
    var passwordStrength: PasswordStrength {
        guard newPassword.count >= 8 else { return .tooShort }
        
        let hasUppercase = newPassword.rangeOfCharacter(from: .uppercaseLetters) != nil
        let hasNumber = newPassword.rangeOfCharacter(from: .decimalDigits) != nil
        let hasSpecialCharacter = newPassword.rangeOfCharacter(from: .punctuationCharacters) != nil || newPassword.rangeOfCharacter(from: .symbols) != nil
        
        let score = [hasUppercase, hasNumber, hasSpecialCharacter].filter { $0 }.count
        
        switch score {
        case 3: return .strong
        case 2: return .medium
        default: return .weak
        }
    }
    
    var passwordsMatch: Bool {
        newPassword == confirmPassword
    }
    
    var isFormValid: Bool {
        passwordStrength != .tooShort && passwordsMatch && !newPassword.isEmpty && !confirmPassword.isEmpty
    }
    
    // MARK: - Actions
    
    func resetPassword() {
        guard isFormValid else {
            errorMessage = NSLocalizedString("Please ensure both passwords match and meet the strength requirements.", comment: "Form validation error")
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        APIService.shared.resetPassword(newPassword: newPassword, confirmPassword: confirmPassword)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let error):
                    self?.errorMessage = error.errorDescription
                case .finished:
                    break
                }
            } receiveValue: { [weak self] success in
                if success {
                    self?.isPasswordResetSuccessful = true
                } else {
                    self?.errorMessage = NSLocalizedString("Password reset failed. Please try again.", comment: "Generic reset failure")
                }
            }
            .store(in: &cancellables)
    }
}

// MARK: - Design System Components

/// Primary color from the Navi design system: #2563EB
private let primaryColor = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)

/// Custom Button Style for the primary action.
struct PrimaryButtonStyle: ButtonStyle {
    var isEnabled: Bool
    
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 17, weight: .semibold)) // Mimics Inter font weight
            .frame(maxWidth: .infinity)
            .padding()
            .background(isEnabled ? primaryColor : primaryColor.opacity(0.5))
            .foregroundColor(.white)
            .cornerRadius(12)
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

// MARK: - View

struct ResetPasswordView: View {
    @StateObject private var viewModel = ResetPasswordViewModel()
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                
                // MARK: - Header
                VStack(spacing: 8) {
                    Text(NSLocalizedString("Reset Password", comment: "Screen title"))
                        .font(.largeTitle.weight(.bold))
                        .accessibilityLabel(NSLocalizedString("Reset Password Screen", comment: ""))
                    
                    Text(NSLocalizedString("Enter your new password below.", comment: "Screen subtitle"))
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                
                // MARK: - Form Fields
                VStack(spacing: 16) {
                    SecureField(NSLocalizedString("New Password", comment: "New password field placeholder"), text: $viewModel.newPassword)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .textContentType(.newPassword)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .accessibilityLabel(NSLocalizedString("New Password Input", comment: ""))
                    
                    // Password Strength Indicator
                    HStack {
                        Text(viewModel.passwordStrength.description)
                            .font(.caption.weight(.semibold))
                            .foregroundColor(viewModel.passwordStrength.color)
                            .accessibilityValue(viewModel.passwordStrength.description)
                        
                        Spacer()
                    }
                    
                    SecureField(NSLocalizedString("Confirm Password", comment: "Confirm password field placeholder"), text: $viewModel.confirmPassword)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .textContentType(.newPassword)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .accessibilityLabel(NSLocalizedString("Confirm New Password Input", comment: ""))
                    
                    // Password Match Indicator
                    if !viewModel.confirmPassword.isEmpty && !viewModel.passwordsMatch {
                        HStack {
                            Text(NSLocalizedString("Passwords do not match", comment: "Password mismatch error"))
                                .font(.caption)
                                .foregroundColor(.red)
                            Spacer()
                        }
                    }
                }
                .padding(.horizontal)
                
                // MARK: - Error Message
                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                        .accessibilityLabel(NSLocalizedString("Error: \(errorMessage)", comment: ""))
                }
                
                Spacer()
                
                // MARK: - Reset Button
                Button {
                    viewModel.resetPassword()
                } label: {
                    if viewModel.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .accessibilityLabel(NSLocalizedString("Loading, please wait", comment: ""))
                    } else {
                        Text(NSLocalizedString("Reset Password", comment: "Reset button title"))
                    }
                }
                .buttonStyle(PrimaryButtonStyle(isEnabled: viewModel.isFormValid && !viewModel.isLoading))
                .disabled(!viewModel.isFormValid || viewModel.isLoading)
                .padding(.horizontal)
                
                // MARK: - Footer
                Text(NSLocalizedString("By resetting your password, you agree to our terms.", comment: "Legal disclaimer"))
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.bottom)
            }
            .padding(.top, 32)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.left")
                            .foregroundColor(primaryColor)
                            .accessibilityLabel(NSLocalizedString("Back", comment: "Back button accessibility label"))
                    }
                }
            }
            .alert(NSLocalizedString("Success", comment: "Success alert title"), isPresented: $viewModel.isPasswordResetSuccessful) {
                Button(NSLocalizedString("OK", comment: "OK button title")) {
                    dismiss()
                }
            } message: {
                Text(NSLocalizedString("Your password has been successfully reset.", comment: "Success alert message"))
            }
        }
    }
}

// MARK: - Preview

struct ResetPasswordView_Previews: PreviewProvider {
    static var previews: some View {
        ResetPasswordView()
    }
}

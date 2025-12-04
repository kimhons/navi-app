import SwiftUI
import Combine

// MARK: - Localization Keys (Mocked for NSLocalizedString)
// In a real project, these would be defined in Localizable.strings
extension String {
    static let signUpTitle = NSLocalizedString("SIGNUP_TITLE", value: "Create Account", comment: "Title for the sign-up screen")
    static let namePlaceholder = NSLocalizedString("NAME_PLACEHOLDER", value: "Full Name", comment: "Placeholder for name text field")
    static let emailPlaceholder = NSLocalizedString("EMAIL_PLACEHOLDER", value: "Email Address", comment: "Placeholder for email text field")
    static let passwordPlaceholder = NSLocalizedString("PASSWORD_PLACEHOLDER", value: "Password", comment: "Placeholder for password text field")
    static let confirmPasswordPlaceholder = NSLocalizedString("CONFIRM_PASSWORD_PLACEHOLDER", value: "Confirm Password", comment: "Placeholder for confirm password text field")
    static let termsText = NSLocalizedString("TERMS_TEXT", value: "I agree to the Terms and Conditions", comment: "Text for the terms and conditions checkbox")
    static let createAccountButton = NSLocalizedString("CREATE_ACCOUNT_BUTTON", value: "Create Account", comment: "Text for the create account button")
    static let nameError = NSLocalizedString("NAME_ERROR", value: "Name must be at least 2 characters.", comment: "Error message for name validation")
    static let emailError = NSLocalizedString("EMAIL_ERROR", value: "Please enter a valid email address.", comment: "Error message for email validation")
    static let passwordError = NSLocalizedString("PASSWORD_ERROR", value: "Password must be at least 8 characters.", comment: "Error message for password validation")
    static let passwordMatchError = NSLocalizedString("PASSWORD_MATCH_ERROR", value: "Passwords do not match.", comment: "Error message for password confirmation")
    static let termsError = NSLocalizedString("TERMS_ERROR", value: "You must accept the terms.", comment: "Error message for terms acceptance")
    static let genericError = NSLocalizedString("GENERIC_ERROR", value: "An unexpected error occurred. Please try again.", comment: "Generic error message")
    static let successMessage = NSLocalizedString("SUCCESS_MESSAGE", value: "Registration Successful!", comment: "Success message after sign-up")
}

// MARK: - Design System Constants
struct NaviDesign {
    static let primaryColor = Color(red: 37/255, green: 99/255, blue: 235/255) // #2563EB
    static let interFont = "Inter" // Placeholder, assuming system font for simplicity
}

// MARK: - Mock APIService
// Mock implementation to satisfy the requirement: "Use APIService.shared for backend calls with Combine publishers"
class APIService {
    static let shared = APIService()
    
    struct SignUpResponse: Decodable {
        let success: Bool
        let message: String
    }
    
    enum APIError: Error, LocalizedError {
        case networkError(String)
        
        var errorDescription: String? {
            switch self {
            case .networkError(let message):
                return message
            }
        }
    }
    
    func signUp(name: String, email: String, password: String) -> AnyPublisher<SignUpResponse, Error> {
        // Simulate a network delay
        return Future<SignUpResponse, Error> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                // Simple mock logic: fail if email is "test@fail.com"
                if email.lowercased() == "test@fail.com" {
                    promise(.failure(APIError.networkError("Email already in use.")))
                } else {
                    promise(.success(SignUpResponse(success: true, message: .successMessage)))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - SignUpViewModel
class SignUpViewModel: ObservableObject {
    // MARK: Form Fields
    @Published var name = ""
    @Published var email = ""
    @Published var password = ""
    @Published var confirmPassword = ""
    @Published var termsAccepted = false
    
    // MARK: State Management
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var isSignUpSuccessful = false
    
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: Validation Properties
    var isNameValid: Bool { name.count >= 2 }
    var isEmailValid: Bool { email.contains("@") && email.contains(".") }
    var isPasswordValid: Bool { password.count >= 8 }
    var passwordsMatch: Bool { password == confirmPassword }
    
    var isFormValid: Bool {
        isNameValid && isEmailValid && isPasswordValid && passwordsMatch && termsAccepted
    }
    
    // MARK: Actions
    func signUp() {
        guard isFormValid, !isLoading else {
            // Display specific validation errors if form is not valid
            if !termsAccepted {
                errorMessage = .termsError
            }
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        APIService.shared.signUp(name: name, email: email, password: password)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let error):
                    self?.errorMessage = error.localizedDescription
                case .finished:
                    break
                }
            } receiveValue: { [weak self] response in
                if response.success {
                    self?.isSignUpSuccessful = true
                } else {
                    self?.errorMessage = response.message
                }
            }
            .store(in: &cancellables)
    }
}

// MARK: - SignUpView
struct SignUpView: View {
    @StateObject private var viewModel = SignUpViewModel()
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    Text(.signUpTitle)
                        .font(.system(size: 34, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)
                        .padding(.bottom, 20)
                        .accessibilityLabel(Text(.signUpTitle))
                    
                    // MARK: Name Field
                    VStack(alignment: .leading, spacing: 5) {
                        TextField(.namePlaceholder, text: $viewModel.name)
                            .textFieldStyle(NaviTextFieldStyle())
                            .keyboardType(.default)
                            .textContentType(.name)
                            .autocorrectionDisabled(true)
                            .accessibilityLabel(Text(.namePlaceholder))
                        
                        if !viewModel.name.isEmpty && !viewModel.isNameValid {
                            Text(.nameError)
                                .font(.caption)
                                .foregroundColor(.red)
                        }
                    }
                    
                    // MARK: Email Field
                    VStack(alignment: .leading, spacing: 5) {
                        TextField(.emailPlaceholder, text: $viewModel.email)
                            .textFieldStyle(NaviTextFieldStyle())
                            .keyboardType(.emailAddress)
                            .textContentType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                            .accessibilityLabel(Text(.emailPlaceholder))
                        
                        if !viewModel.email.isEmpty && !viewModel.isEmailValid {
                            Text(.emailError)
                                .font(.caption)
                                .foregroundColor(.red)
                        }
                    }
                    
                    // MARK: Password Field
                    VStack(alignment: .leading, spacing: 5) {
                        SecureField(.passwordPlaceholder, text: $viewModel.password)
                            .textFieldStyle(NaviTextFieldStyle())
                            .textContentType(.newPassword)
                            .accessibilityLabel(Text(.passwordPlaceholder))
                        
                        if !viewModel.password.isEmpty && !viewModel.isPasswordValid {
                            Text(.passwordError)
                                .font(.caption)
                                .foregroundColor(.red)
                        }
                    }
                    
                    // MARK: Confirm Password Field
                    VStack(alignment: .leading, spacing: 5) {
                        SecureField(.confirmPasswordPlaceholder, text: $viewModel.confirmPassword)
                            .textFieldStyle(NaviTextFieldStyle())
                            .textContentType(.newPassword)
                            .accessibilityLabel(Text(.confirmPasswordPlaceholder))
                        
                        if !viewModel.confirmPassword.isEmpty && !viewModel.passwordsMatch {
                            Text(.passwordMatchError)
                                .font(.caption)
                                .foregroundColor(.red)
                        }
                    }
                    
                    // MARK: Terms Checkbox
                    HStack {
                        Toggle(isOn: $viewModel.termsAccepted) {
                            Text(.termsText)
                                .font(.callout)
                        }
                        .toggleStyle(CheckboxToggleStyle())
                        .accessibilityLabel(Text(.termsText))
                        
                        Spacer()
                    }
                    .padding(.top, 10)
                    
                    // MARK: Error Message
                    if let errorMessage = viewModel.errorMessage {
                        Text(errorMessage)
                            .foregroundColor(.red)
                            .multilineTextAlignment(.center)
                            .padding(.vertical, 10)
                            .accessibilityLiveRegion(.assertive)
                            .accessibilityHint("Error message displayed")
                    }
                    
                    // MARK: Create Account Button
                    Button {
                        viewModel.signUp()
                    } label: {
                        if viewModel.isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                        } else {
                            Text(.createAccountButton)
                                .font(.system(.headline, design: .rounded))
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                        }
                    }
                    .buttonStyle(NaviButtonStyle(isDisabled: !viewModel.isFormValid || viewModel.isLoading))
                    .disabled(!viewModel.isFormValid || viewModel.isLoading)
                    .padding(.top, 20)
                    .accessibilityLabel(Text(.createAccountButton))
                    .accessibilityHint("Activates registration process")
                    
                    Spacer()
                }
                .padding()
            }
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.left")
                            .foregroundColor(.primary)
                            .accessibilityLabel("Back")
                    }
                }
            }
            .alert(.successMessage, isPresented: $viewModel.isSignUpSuccessful) {
                Button("OK") {
                    dismiss()
                }
            }
        }
        // Use a system font that resembles Inter and apply the minimalist aesthetic
        .font(.custom(NaviDesign.interFont, size: 16, relativeTo: .body))
    }
}

// MARK: - Custom Styles (Navi Design System)

// Custom TextField Style (Apple-inspired aesthetic)
struct NaviTextFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .padding(12)
            .background(Color(.secondarySystemBackground))
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color(.systemGray4), lineWidth: 1)
            )
    }
}

// Custom Button Style
struct NaviButtonStyle: ButtonStyle {
    var isDisabled: Bool
    
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundColor(.white)
            .background(isDisabled ? NaviDesign.primaryColor.opacity(0.5) : NaviDesign.primaryColor)
            .cornerRadius(10)
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

// Custom Checkbox Toggle Style
struct CheckboxToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack {
            Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                .foregroundColor(configuration.isOn ? NaviDesign.primaryColor : .gray)
                .font(.title2)
                .onTapGesture { configuration.isOn.toggle() }
                .accessibilityHidden(true) // Hide the image from accessibility since the whole toggle is labeled
            
            configuration.label
        }
        .contentShape(Rectangle())
        .onTapGesture { configuration.isOn.toggle() }
    }
}

// MARK: - Preview
struct SignUpView_Previews: PreviewProvider {
    static var previews: some View {
        SignUpView()
    }
}

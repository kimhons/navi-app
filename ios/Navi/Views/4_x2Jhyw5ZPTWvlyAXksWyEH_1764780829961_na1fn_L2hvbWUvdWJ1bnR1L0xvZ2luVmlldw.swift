import SwiftUI
import Combine

// MARK: - 1. Design System & Utilities

extension Color {
    /// Navi Design System Primary Color: #2563EB (Indigo-600)
    static let naviPrimary = Color(red: 0.145, green: 0.388, blue: 0.922)
}

extension Font {
    /// Simulates the Inter font by using a system font with appropriate weight.
    static func naviBody(_ weight: Font.Weight = .regular) -> Font {
        .system(.body, design: .default).weight(weight)
    }
    
    static func naviHeadline(_ weight: Font.Weight = .semibold) -> Font {
        .system(.headline, design: .default).weight(weight)
    }
}

// MARK: - 2. Mock API Service and Models

/// Mock response model for the login API call.
struct LoginResponse: Decodable {
    let token: String
    let userId: String
}

/// Mock APIService to simulate backend calls using Combine.
class APIService {
    static let shared = APIService()
    
    /// Simulates a network call for user login.
    func login(email: String, password: String) -> AnyPublisher<LoginResponse, Error> {
        Future<LoginResponse, Error> { promise in
            // Simulate network delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                if email == "test@navi.com" && password == "password" {
                    // Simulate successful login
                    let response = LoginResponse(token: "mock_token_123", userId: "user_456")
                    promise(.success(response))
                } else if email == "error@navi.com" {
                    // Simulate a specific API error
                    promise(.failure(NSError(domain: "APIServiceError", code: 401, userInfo: [NSLocalizedDescriptionKey: "Invalid credentials. Please check your email and password."])))
                } else {
                    // Simulate a generic network error
                    promise(.failure(URLError(.notConnectedToInternet)))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - 3. LoginViewModel (MVVM)

class LoginViewModel: ObservableObject {
    
    // MARK: - Input Properties
    @Published var email = ""
    @Published var password = ""
    @Published var rememberMe = false
    
    // MARK: - State Properties
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var isAuthenticated = false // For demonstration/navigation
    
    // MARK: - Validation Properties
    var isEmailValid: Bool {
        // Simple email validation
        email.contains("@") && email.contains(".")
    }
    
    var isPasswordValid: Bool {
        // Simple password validation
        password.count >= 6
    }
    
    var isFormValid: Bool {
        isEmailValid && isPasswordValid && !isLoading
    }
    
    // MARK: - Combine & Cancellables
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Initialization
    init() {
        // Clear error message when user starts typing
        $email
            .combineLatest($password)
            .sink { [weak self] _, _ in
                self?.errorMessage = nil
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Actions
    
    /// Handles the login process using APIService and Combine.
    func login() {
        guard isFormValid else {
            errorMessage = NSLocalizedString("Please ensure both email and password are valid.", comment: "Form validation error message.")
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        APIService.shared.login(email: email, password: password)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let error):
                    // Error handling: Display localized error message
                    self?.errorMessage = error.localizedDescription
                case .finished:
                    break
                }
            } receiveValue: { [weak self] response in
                // Success: Update authentication state
                print("Login successful. Token: \(response.token)")
                self?.isAuthenticated = true
            }
            .store(in: &cancellables)
    }
    
    func forgotPassword() {
        // Placeholder for navigation to Forgot Password screen
        print("Navigate to Forgot Password screen")
    }
    
    func socialLogin(platform: String) {
        // Placeholder for social login logic
        print("Attempting to log in with \(platform)")
    }
}

// MARK: - 4. LoginView (SwiftUI)

struct LoginView: View {
    
    @StateObject var viewModel = LoginViewModel()
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                
                // Header
                Text(NSLocalizedString("Welcome Back", comment: "Login screen title."))
                    .font(.naviHeadline(.bold))
                    .foregroundColor(.primary)
                    .padding(.bottom, 4)
                    .accessibilityLabel(NSLocalizedString("Login screen title", comment: ""))
                
                Text(NSLocalizedString("Sign in to continue to your account.", comment: "Login screen subtitle."))
                    .font(.naviBody())
                    .foregroundColor(.secondary)
                    .padding(.bottom, 32)
                
                // MARK: - Email Field
                VStack(alignment: .leading, spacing: 8) {
                    Text(NSLocalizedString("Email Address", comment: "Email field label."))
                        .font(.naviBody(.semibold))
                        .foregroundColor(.primary)
                    
                    TextField(NSLocalizedString("you@example.com", comment: "Email placeholder."), text: $viewModel.email)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .textFieldStyle(NaviTextFieldStyle())
                        .accessibilityLabel(NSLocalizedString("Email address input", comment: ""))
                }
                .padding(.bottom, 16)
                
                // MARK: - Password Field
                VStack(alignment: .leading, spacing: 8) {
                    Text(NSLocalizedString("Password", comment: "Password field label."))
                        .font(.naviBody(.semibold))
                        .foregroundColor(.primary)
                    
                    SecureField(NSLocalizedString("Enter your password", comment: "Password placeholder."), text: $viewModel.password)
                        .textFieldStyle(NaviTextFieldStyle())
                        .accessibilityLabel(NSLocalizedString("Password input", comment: ""))
                }
                .padding(.bottom, 16)
                
                // MARK: - Remember Me & Forgot Password
                HStack {
                    Toggle(isOn: $viewModel.rememberMe) {
                        Text(NSLocalizedString("Remember Me", comment: "Remember Me checkbox label."))
                            .font(.naviBody())
                    }
                    .toggleStyle(CheckboxToggleStyle())
                    .accessibilityLabel(NSLocalizedString("Remember me checkbox", comment: ""))
                    
                    Spacer()
                    
                    Button(action: viewModel.forgotPassword) {
                        Text(NSLocalizedString("Forgot Password?", comment: "Forgot Password link."))
                            .font(.naviBody(.semibold))
                            .foregroundColor(.naviPrimary)
                    }
                    .accessibilityLabel(NSLocalizedString("Forgot password link", comment: ""))
                }
                .padding(.bottom, 32)
                
                // MARK: - Error Message
                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .font(.naviBody(.semibold))
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.bottom, 16)
                        .accessibilityLabel(NSLocalizedString("Error message: \(errorMessage)", comment: ""))
                }
                
                // MARK: - Login Button
                Button(action: viewModel.login) {
                    if viewModel.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .frame(maxWidth: .infinity, minHeight: 50)
                    } else {
                        Text(NSLocalizedString("Log In", comment: "Login button text."))
                            .font(.naviBody(.bold))
                            .frame(maxWidth: .infinity, minHeight: 50)
                    }
                }
                .buttonStyle(NaviPrimaryButtonStyle(isDisabled: !viewModel.isFormValid))
                .disabled(!viewModel.isFormValid || viewModel.isLoading)
                .accessibilityLabel(NSLocalizedString("Log in button", comment: ""))
                .padding(.bottom, 32)
                
                // MARK: - Social Login Divider
                HStack {
                    Rectangle().frame(height: 1).foregroundColor(Color(.systemGray4))
                    Text(NSLocalizedString("OR", comment: "Social login divider text."))
                        .font(.naviBody(.semibold))
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 8)
                    Rectangle().frame(height: 1).foregroundColor(Color(.systemGray4))
                }
                .padding(.bottom, 32)
                
                // MARK: - Social Login Options
                VStack(spacing: 16) {
                    SocialLoginButton(title: NSLocalizedString("Continue with Apple", comment: "Apple social login button."), iconName: "applelogo") {
                        viewModel.socialLogin(platform: "Apple")
                    }
                    SocialLoginButton(title: NSLocalizedString("Continue with Google", comment: "Google social login button."), iconName: "g.circle.fill") {
                        viewModel.socialLogin(platform: "Google")
                    }
                }
                
                Spacer()
            }
            .padding(.horizontal, 24)
            .padding(.top, 40)
            .background(Color(.systemBackground).ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.left")
                            .foregroundColor(.primary)
                    }
                    .accessibilityLabel(NSLocalizedString("Back button", comment: ""))
                }
            }
        }
    }
}

// MARK: - 5. Custom Styles and Components

/// Custom TextField Style for the Navi minimalist aesthetic.
struct NaviTextFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color(.systemGray4), lineWidth: 1)
            )
    }
}

/// Custom Button Style for the primary action button.
struct NaviPrimaryButtonStyle: ButtonStyle {
    var isDisabled: Bool
    
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundColor(.white)
            .background(isDisabled ? Color.naviPrimary.opacity(0.5) : Color.naviPrimary)
            .cornerRadius(8)
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

/// Custom Toggle Style for the "Remember Me" checkbox.
struct CheckboxToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack {
            Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                .resizable()
                .frame(width: 20, height: 20)
                .foregroundColor(configuration.isOn ? .naviPrimary : .secondary)
                .onTapGesture {
                    configuration.isOn.toggle()
                }
            configuration.label
        }
    }
}

/// Component for social login buttons.
struct SocialLoginButton: View {
    let title: String
    let iconName: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: iconName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 20, height: 20)
                    .foregroundColor(.primary)
                
                Text(title)
                    .font(.naviBody(.semibold))
                    .foregroundColor(.primary)
                
                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 20)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color(.systemGray3), lineWidth: 1)
            )
        }
        .accessibilityLabel(title)
    }
}

// MARK: - 6. Preview

struct LoginView_Previews: PreviewProvider {
    static var previews: some View {
        LoginView()
    }
}

// MARK: - Localization Stub
// NSLocalizedString is used throughout the code. For a standalone file, we assume a Localizable.strings file exists.
// For testing purposes in a playground or standalone environment, you might define a simple stub:
/*
func NSLocalizedString(_ key: String, comment: String) -> String {
    // In a real app, this would look up the key in Localizable.strings
    return key // Return the key itself as a fallback
}
*/

//
// EmailVerificationView.swift
//
// Complete, production-ready SwiftUI screen for email verification code input.
// Follows MVVM architecture, Navi design system, and includes a countdown timer.
//

import SwiftUI
import Combine

// MARK: - 1. Design System & Mock Dependencies

/// Navi Design System Constants
struct NaviDesign {
    static let primaryColor = Color(red: 37/255, green: 99/255, blue: 235/255) // #2563EB
    static let fontName = "Inter" // Use system font as fallback, but specify Inter
}

/// Mock API Service for demonstration purposes.
/// In a real app, this would handle network requests.
class APIService {
    static let shared = APIService()
    
    enum APIError: Error, LocalizedError {
        case invalidCode
        case serverError
        
        var errorDescription: String? {
            switch self {
            case .invalidCode:
                return NSLocalizedString("The verification code is invalid. Please try again.", comment: "Invalid code error")
            case .serverError:
                return NSLocalizedString("A server error occurred. Please try again later.", comment: "Server error")
            }
        }
    }
    
    /// Mocks a call to verify the OTP.
    func verifyCode(code: String) -> AnyPublisher<Void, APIError> {
        Future<Void, APIError> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                if code == "123456" {
                    promise(.success(()))
                } else if code == "000000" {
                    promise(.failure(.serverError))
                } else {
                    promise(.failure(.invalidCode))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    /// Mocks a call to resend the OTP.
    func resendCode(email: String) -> AnyPublisher<Void, APIError> {
        Future<Void, APIError> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                // Always succeed for resend in this mock
                promise(.success(()))
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - 2. ViewModel

/// The ViewModel for the EmailVerificationView, handling business logic, state, and API calls.
final class EmailVerificationViewModel: ObservableObject {
    @Published var otpCode: String = "" {
        didSet {
            // Enforce 6-digit limit and only allow digits
            if otpCode.count > Self.otpLength {
                otpCode = String(otpCode.prefix(Self.otpLength))
            }
            otpCode = otpCode.filter { $0.isNumber }
            
            // Automatically verify when 6 digits are entered
            if otpCode.count == Self.otpLength && !isLoading {
                verifyCode()
            }
        }
    }
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var canResend: Bool = false
    @Published var resendCountdown: Int = Self.resendTimeout
    @Published var isVerificationSuccessful: Bool = false
    
    private static let resendTimeout: Int = 60 // 60 seconds
    private static let otpLength: Int = 6
    private var timerCancellable: AnyCancellable?
    private var apiCancellable: AnyCancellable?
    
    private let email: String
    
    init(email: String = "user@example.com") {
        self.email = email
        startTimer()
    }
    
    // MARK: - Timer Logic
    
    func startTimer() {
        canResend = false
        resendCountdown = Self.resendTimeout
        
        timerCancellable = Timer.publish(every: 1, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                guard let self = self else { return }
                if self.resendCountdown > 0 {
                    self.resendCountdown -= 1
                } else {
                    self.timerCancellable?.cancel()
                    self.canResend = true
                }
            }
    }
    
    // MARK: - API Calls
    
    func verifyCode() {
        guard otpCode.count == Self.otpLength else {
            errorMessage = NSLocalizedString("Please enter the 6-digit code.", comment: "Incomplete code error")
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        apiCancellable = APIService.shared.verifyCode(code: otpCode)
            .receive(on: DispatchQueue.main)
            .sink(receiveCompletion: { [weak self] completion in
                guard let self = self else { return }
                self.isLoading = false
                switch completion {
                case .failure(let error):
                    self.errorMessage = error.localizedDescription
                    // Clear the code on failure to force re-entry
                    self.otpCode = "" 
                case .finished:
                    break
                }
            }, receiveValue: { [weak self] _ in
                // Verification successful
                self?.isVerificationSuccessful = true
            })
    }
    
    func resendCode() {
        guard canResend else { return }
        
        isLoading = true
        errorMessage = nil
        
        apiCancellable = APIService.shared.resendCode(email: email)
            .receive(on: DispatchQueue.main)
            .sink(receiveCompletion: { [weak self] completion in
                guard let self = self else { return }
                self.isLoading = false
                switch completion {
                case .failure(let error):
                    self.errorMessage = error.localizedDescription
                case .finished:
                    // On success, restart the timer
                    self.startTimer()
                }
            }, receiveValue: { _ in
                // Resend successful, timer is restarted in completion block
            })
    }
}

// MARK: - 3. View Components

// MARK: - 3. View Components

/// Custom component for a single OTP digit input.
struct OTPDigitField: View {
    let text: String
    let isFocused: Bool
    
    var body: some View {
        ZStack {
            Text(text)
                .font(.custom(NaviDesign.fontName, size: 24).bold())
                .frame(width: 48, height: 56)
                .background(Color(.systemGray6))
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isFocused ? NaviDesign.primaryColor : Color(.systemGray4), lineWidth: 2)
                )
                .accessibilityLabel(NSLocalizedString("OTP digit field", comment: "Accessibility label for a single OTP digit input"))
        }
    }
}

/// The main view for email verification.
struct EmailVerificationView: View {
    @StateObject var viewModel: EmailVerificationViewModel
    @Environment(\.dismiss) var dismiss
    @FocusState private var isTextFieldFocused: Bool
    
    private let otpLength = 6
    
    init(email: String = "user@example.com") {
        _viewModel = StateObject(wrappedValue: EmailVerificationViewModel(email: email))
    }
    
    var body: some View {
        VStack(spacing: 30) {
            
            // MARK: - Header
            VStack(spacing: 10) {
                Text(NSLocalizedString("Verify your email", comment: "Title for email verification screen"))
                    .font(.largeTitle.bold())
                    .accessibilityAddTraits(.isHeader)
                
                Text(NSLocalizedString("Enter the 6-digit code sent to \(viewModel.email)", comment: "Subtitle for email verification screen"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            // MARK: - OTP Input
            ZStack {
                // Hidden TextField to capture input
                TextField("", text: $viewModel.otpCode)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .frame(width: 0, height: 0)
                    .opacity(0)
                    .focused($isTextFieldFocused)
                    .accessibilityLabel(NSLocalizedString("6-digit verification code input", comment: "Accessibility label for the full OTP input field"))
                
                // Visual representation of the OTP input
                HStack(spacing: 10) {
                    ForEach(0..<otpLength, id: \.self) { index in
                        OTPDigitField(
                            text: digit(at: index),
                            isFocused: isTextFieldFocused && index == min(viewModel.otpCode.count, otpLength - 1)
                        )
                    }
                }
                .onTapGesture {
                    isTextFieldFocused = true
                }
            }
            
            // MARK: - Error Message
            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .foregroundColor(.red)
                    .font(.caption)
                    .multilineTextAlignment(.center)
                    .frame(height: 20)
            } else {
                Spacer().frame(height: 20)
            }
            
            // MARK: - Resend Button & Timer
            HStack {
                Text(NSLocalizedString("Didn't receive the code?", comment: "Prompt for resending code"))
                    .foregroundColor(.secondary)
                
                Button {
                    viewModel.resendCode()
                } label: {
                    if viewModel.canResend {
                        Text(NSLocalizedString("Resend Code", comment: "Resend code button text"))
                            .fontWeight(.semibold)
                            .foregroundColor(NaviDesign.primaryColor)
                    } else {
                        Text(NSLocalizedString("Resend in \(viewModel.resendCountdown)s", comment: "Resend code countdown text"))
                            .foregroundColor(.gray)
                    }
                }
                .disabled(!viewModel.canResend || viewModel.isLoading)
                .accessibilityLabel(viewModel.canResend ? NSLocalizedString("Resend verification code", comment: "Accessibility label for resend button") : NSLocalizedString("Resend code countdown", comment: "Accessibility label for resend countdown"))
            }
            
            // MARK: - Loading Indicator
            if viewModel.isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: NaviDesign.primaryColor))
                    .scaleEffect(1.5)
                    .padding(.top, 20)
            }
            
            Spacer()
        }
        .padding()
        .onAppear {
            // Automatically focus the text field on view appearance
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                isTextFieldFocused = true
            }
        }
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "chevron.left")
                        .foregroundColor(.primary)
                        .accessibilityLabel(NSLocalizedString("Back", comment: "Accessibility label for back button"))
                }
            }
        }
        // Simple success handling for demonstration
        .alert(isPresented: $viewModel.isVerificationSuccessful) {
            Alert(
                title: Text(NSLocalizedString("Verification Successful", comment: "Success alert title")),
                message: Text(NSLocalizedString("Your email has been successfully verified.", comment: "Success alert message")),
                dismissButton: .default(Text(NSLocalizedString("Continue", comment: "Success alert button"))) {
                    // In a real app, this would navigate to the next screen
                }
            )
        }
    }
    
    // Helper function to get the digit at a specific index
    private func digit(at index: Int) -> String {
        guard index < viewModel.otpCode.count else { return "" }
        return String(viewModel.otpCode[viewModel.otpCode.index(viewModel.otpCode.startIndex, offsetBy: index)])
    }
}

// MARK: - Preview

#Preview {
    EmailVerificationView()
}

import SwiftUI
import Combine

// MARK: - Mock Dependencies

/// Mock Preference Model for the selection form.
struct Preference: Identifiable, Hashable {
    let id = UUID()
    let name: String
    
    static let mockPreferences: [Preference] = [
        Preference(name: NSLocalizedString("Technology", comment: "Preference option")),
        Preference(name: NSLocalizedString("Design", comment: "Preference option")),
        Preference(name: NSLocalizedString("Productivity", comment: "Preference option")),
        Preference(name: NSLocalizedString("Health", comment: "Preference option")),
        Preference(name: NSLocalizedString("Finance", comment: "Preference option"))
    ]
}

/// Mock APIService to simulate backend calls with Combine.
class APIService {
    static let shared = APIService()
    
    struct AccountSetupResponse: Decodable {
        let success: Bool
        let message: String
    }
    
    enum APIError: Error, LocalizedError {
        case networkError
        case validationError(String)
        case unknown
        
        var errorDescription: String? {
            switch self {
            case .networkError: return NSLocalizedString("Network connection failed. Please check your connection.", comment: "")
            case .validationError(let msg): return NSLocalizedString(msg, comment: "")
            case .unknown: return NSLocalizedString("An unknown error occurred. Please try again.", comment: "")
            }
        }
    }
    
    /// Simulates an API call to complete account setup.
    func completeAccountSetup(displayName: String, preferences: [Preference], profileImageData: Data?) -> AnyPublisher<AccountSetupResponse, APIError> {
        // Simulate network delay
        return Future<AccountSetupResponse, APIError> { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                if displayName.lowercased().contains("error") {
                    promise(.failure(.validationError(NSLocalizedString("Display name is inappropriate or already taken.", comment: ""))))
                } else if Bool.random() && displayName.count < 3 {
                    // Simulate a random network failure
                    promise(.failure(.networkError))
                } else {
                    promise(.success(AccountSetupResponse(success: true, message: "Account setup complete.")))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - ViewModel

class AccountSetupViewModel: ObservableObject {
    // MARK: - Published Properties
    
    @Published var displayName: String = ""
    @Published var selectedPreferences: Set<Preference> = []
    @Published var profileImageData: Data? = nil // Placeholder for selected image data
    
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    @Published var setupComplete: Bool = false
    
    // MARK: - Validation Properties
    
    @Published var isDisplayNameValid: Bool = false
    @Published var isFormValid: Bool = false
    
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        setupValidation()
    }
    
    private func setupValidation() {
        // Display Name Validation: Must be between 3 and 20 characters
        $displayName
            .map { $0.count >= 3 && $0.count <= 20 }
            .assign(to: \.isDisplayNameValid, on: self)
            .store(in: &cancellables)
        
        // Form Validation: Display name must be valid and at least one preference must be selected
        Publishers.CombineLatest3($isDisplayNameValid, $selectedPreferences.map { !$0.isEmpty }, $isLoading)
            .map { displayNameValid, preferencesSelected, isLoading in
                displayNameValid && preferencesSelected && !isLoading
            }
            .assign(to: \.isFormValid, on: self)
            .store(in: &cancellables)
    }
    
    // MARK: - API Call
    
    func completeAccountSetup() {
        guard isFormValid else {
            errorMessage = NSLocalizedString("Please ensure your display name is valid and you have selected at least one preference.", comment: "")
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        APIService.shared.completeAccountSetup(
            displayName: displayName,
            preferences: Array(selectedPreferences),
            profileImageData: profileImageData
        )
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
                self?.setupComplete = true
            } else {
                self?.errorMessage = response.message
            }
        }
        .store(in: &cancellables)
    }
}

// MARK: - View

struct AccountSetupView: View {
    @StateObject private var viewModel = AccountSetupViewModel()
    @Environment(\.dismiss) var dismiss
    
    // Navi Design System Color: #2563EB (Blue-600)
    private let primaryColor = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)
    
    var body: some View {
        NavigationView {
            ZStack {
                // Background for minimalist aesthetic
                Color(uiColor: .systemGroupedBackground).edgesIgnoringSafeArea(.all)
                
                ScrollView {
                    VStack(spacing: 30) {
                        header
                        
                        profilePhotoSection
                        
                        formSection
                        
                        preferencesSection
                        
                        submitButton
                        
                        if let error = viewModel.errorMessage {
                            errorView(error)
                        }
                    }
                    .padding()
                }
                .navigationTitle(NSLocalizedString("Complete Your Profile", comment: "Navigation title for account setup screen"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "chevron.left")
                                .foregroundColor(primaryColor)
                                .accessibilityLabel(NSLocalizedString("Back", comment: "Accessibility label for back button"))
                        }
                    }
                }
                
                if viewModel.isLoading {
                    loadingOverlay
                }
            }
            .alert(isPresented: $viewModel.setupComplete) {
                Alert(
                    title: Text(NSLocalizedString("Success", comment: "Success alert title")),
                    message: Text(NSLocalizedString("Your account has been successfully set up!", comment: "Success alert message")),
                    dismissButton: .default(Text(NSLocalizedString("Continue", comment: "Continue button text"))) {
                        // Dismiss the view after successful setup
                        dismiss()
                    }
                )
            }
        }
    }
    
    // MARK: - Components
    
    private var header: some View {
        VStack(spacing: 8) {
            Text(NSLocalizedString("Welcome to Navi", comment: "Welcome message on setup screen"))
                .font(.custom("Inter", size: 28).weight(.bold))
                .foregroundColor(.primary)
            
            Text(NSLocalizedString("Just a few more steps to get started.", comment: "Subtitle for setup screen"))
                .font(.custom("Inter", size: 16))
                .foregroundColor(.secondary)
        }
        .padding(.top, 20)
    }
    
    private var profilePhotoSection: some View {
        VStack {
            // Placeholder for Profile Photo Upload
            ZStack {
                Circle()
                    .fill(Color(uiColor: .systemGray5))
                    .frame(width: 100, height: 100)
                    .overlay(
                        Image(systemName: "person.crop.circle.fill")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 80, height: 80)
                            .foregroundColor(Color(uiColor: .systemGray))
                    )
                
                Circle()
                    .stroke(primaryColor, lineWidth: 3)
                    .frame(width: 100, height: 100)
            }
            .accessibilityLabel(NSLocalizedString("Profile photo placeholder", comment: "Accessibility label for profile photo"))
            
            Button(action: {
                // Action to open image picker (requires UIKit integration, mocked here)
                print("Open image picker")
            }) {
                Text(NSLocalizedString("Upload Photo", comment: "Button to upload profile photo"))
                    .font(.custom("Inter", size: 16).weight(.medium))
                    .foregroundColor(primaryColor)
            }
            .accessibilityHint(NSLocalizedString("Tap to select a new profile picture.", comment: "Accessibility hint for upload photo button"))
        }
    }
    
    private var formSection: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text(NSLocalizedString("Display Name", comment: "Label for display name text field"))
                .font(.custom("Inter", size: 14).weight(.medium))
                .foregroundColor(.secondary)
            
            TextField(NSLocalizedString("Enter your display name", comment: "Placeholder for display name text field"), text: $viewModel.displayName)
                .padding()
                .background(Color(uiColor: .systemBackground))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(viewModel.isDisplayNameValid || viewModel.displayName.isEmpty ? Color.clear : Color.red, lineWidth: 1)
                )
                .accessibilityLabel(NSLocalizedString("Display Name Input", comment: "Accessibility label for display name input"))
            
            if !viewModel.displayName.isEmpty && !viewModel.isDisplayNameValid {
                Text(NSLocalizedString("Display name must be between 3 and 20 characters.", comment: "Validation message for display name"))
                    .font(.caption)
                    .foregroundColor(.red)
            }
        }
        .padding(.horizontal)
    }
    
    private var preferencesSection: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text(NSLocalizedString("Select Your Interests", comment: "Header for preferences selection"))
                .font(.custom("Inter", size: 18).weight(.bold))
                .foregroundColor(.primary)
                .padding(.horizontal)
            
            Text(NSLocalizedString("This helps us personalize your experience.", comment: "Subtitle for preferences selection"))
                .font(.custom("Inter", size: 14))
                .foregroundColor(.secondary)
                .padding(.horizontal)
            
            // Grid for preferences selection
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 10)], spacing: 10) {
                ForEach(Preference.mockPreferences) { preference in
                    PreferenceTag(preference: preference, isSelected: viewModel.selectedPreferences.contains(preference)) {
                        if viewModel.selectedPreferences.contains(preference) {
                            viewModel.selectedPreferences.remove(preference)
                        } else {
                            viewModel.selectedPreferences.insert(preference)
                        }
                    }
                }
            }
            .padding(.horizontal)
        }
    }
    
    private var submitButton: some View {
        Button(action: viewModel.completeAccountSetup) {
            Text(NSLocalizedString("Complete Setup", comment: "Button to complete account setup"))
                .font(.custom("Inter", size: 18).weight(.bold))
                .frame(maxWidth: .infinity)
                .padding()
                .background(viewModel.isFormValid ? primaryColor : Color(uiColor: .systemGray3))
                .foregroundColor(.white)
                .cornerRadius(12)
        }
        .padding(.horizontal)
        .padding(.top, 10)
        .disabled(!viewModel.isFormValid || viewModel.isLoading)
        .accessibilityLabel(NSLocalizedString("Complete Setup Button", comment: "Accessibility label for complete setup button"))
        .accessibilityHint(NSLocalizedString("Submits your profile information to finalize account setup.", comment: "Accessibility hint for complete setup button"))
    }
    
    private func errorView(_ error: String) -> some View {
        Text(error)
            .font(.caption)
            .foregroundColor(.white)
            .padding(10)
            .frame(maxWidth: .infinity)
            .background(Color.red)
            .cornerRadius(8)
            .padding(.horizontal)
            .transition(.slide)
            .accessibilityLabel(NSLocalizedString("Error message: \(error)", comment: "Accessibility label for error message"))
    }
    
    private var loadingOverlay: some View {
        Color.black.opacity(0.4)
            .edgesIgnoringSafeArea(.all)
            .overlay(
                ProgressView(NSLocalizedString("Setting up account...", comment: "Loading indicator text"))
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.black.opacity(0.7))
                    .cornerRadius(10)
            )
            .accessibilityModal(true)
            .accessibilityLabel(NSLocalizedString("Loading, please wait.", comment: "Accessibility label for loading overlay"))
    }
}

// MARK: - Helper Component

struct PreferenceTag: View {
    let preference: Preference
    let isSelected: Bool
    let action: () -> Void
    
    // Navi Design System Color: #2563EB (Blue-600)
    private let primaryColor = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)
    
    var body: some View {
        Button(action: action) {
            Text(preference.name)
                .font(.custom("Inter", size: 14).weight(.medium))
                .padding(.vertical, 10)
                .padding(.horizontal, 15)
                .background(isSelected ? primaryColor : Color(uiColor: .systemBackground))
                .foregroundColor(isSelected ? .white : .primary)
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(isSelected ? primaryColor : Color(uiColor: .systemGray3), lineWidth: 1)
                )
        }
        .accessibilityLabel(NSLocalizedString("\(preference.name) preference", comment: "Accessibility label for preference tag"))
        .accessibilityValue(isSelected ? NSLocalizedString("Selected", comment: "Accessibility value for selected preference") : NSLocalizedString("Not selected", comment: "Accessibility value for not selected preference"))
    }
}

// MARK: - Preview

struct AccountSetupView_Previews: PreviewProvider {
    static var previews: some View {
        AccountSetupView()
    }
}

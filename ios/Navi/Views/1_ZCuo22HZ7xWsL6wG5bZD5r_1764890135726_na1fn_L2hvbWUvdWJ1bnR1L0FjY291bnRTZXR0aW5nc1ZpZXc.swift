import SwiftUI
import Combine

// MARK: - 1. Models
struct User: Identifiable, Codable {
    let id: String = UUID().uuidString
    var username: String
    var email: String
    var isEmailVerified: Bool
}

struct Subscription: Identifiable, Codable {
    let id: String = UUID().uuidString
    var tier: SubscriptionTier
    var renewalDate: Date
    var price: Double
}

enum SubscriptionTier: String, CaseIterable, Codable {
    case free = "Free"
    case basic = "Basic"
    case pro = "Pro"
    case premium = "Premium"

    var price: Double {
        switch self {
        case .free: return 0.0
        case .basic: return 4.99
        case .pro: return 9.99
        case .premium: return 19.99
        }
    }
}

struct PaymentMethod: Identifiable, Codable {
    let id: String = UUID().uuidString
    var type: String
    var last4: String
    var expiry: String
}

// MARK: - 2. Debouncer Utility
// Used for the 'Performance: Efficient updates, debounced API calls' requirement
class Debouncer<T>: ObservableObject {
    @Published var debouncedValue: T
    @Published var currentValue: T {
        didSet {
            // Cancel the previous task
            task?.cancel()
            // Schedule a new task
            task = Task {
                try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                // Check if the task was cancelled before updating
                if !Task.isCancelled {
                    await MainActor.run {
                        self.debouncedValue = self.currentValue
                    }
                }
            }
        }
    }

    private var task: Task<Void, Never>?
    private let delay: Double // in seconds

    init(initialValue: T, delay: Double = 0.5) {
        self.debouncedValue = initialValue
        self.currentValue = initialValue
        self.delay = delay
    }
}

// MARK: - 3. Mock APIService
class APIService: ObservableObject {
    static let shared = APIService()

    // Simulate network latency
    private func simulateDelay() async {
        try? await Task.sleep(nanoseconds: UInt64.random(in: 500_000_000...1_500_000_000)) // 0.5 to 1.5 seconds
    }

    // Mock Data Store
    @Published var mockUser: User = User(username: "ManusAgent", email: "agent@manus.im", isEmailVerified: true)
    @Published var mockSubscription: Subscription = Subscription(tier: .pro, renewalDate: Calendar.current.date(byAdding: .month, value: 1, to: Date())!, price: SubscriptionTier.pro.price)
    @Published var mockPaymentMethod: PaymentMethod = PaymentMethod(type: "Visa", last4: "4242", expiry: "12/26")

    // Account Operations
    func fetchUser() async throws -> User {
        await simulateDelay()
        return mockUser
    }

    func updateUsername(_ newUsername: String) async throws {
        await simulateDelay()
        if newUsername.isEmpty || newUsername.count < 3 {
            throw NSError(domain: "APIService", code: 100, userInfo: [NSLocalizedDescriptionKey: "Username must be at least 3 characters."])
        }
        mockUser.username = newUsername
        print("API: Username updated to \(newUsername)")
    }

    func changePassword(old: String, new: String) async throws {
        await simulateDelay()
        if new.count < 8 {
            throw NSError(domain: "APIService", code: 101, userInfo: [NSLocalizedDescriptionKey: "New password must be at least 8 characters."])
        }
        print("API: Password changed successfully.")
    }

    func deleteAccount() async throws {
        await simulateDelay()
        print("API: Account deleted successfully.")
    }

    // Subscription Operations
    func fetchSubscription() async throws -> Subscription {
        await simulateDelay()
        return mockSubscription
    }

    func updateSubscription(to tier: SubscriptionTier) async throws {
        await simulateDelay()
        mockSubscription.tier = tier
        mockSubscription.price = tier.price
        print("API: Subscription updated to \(tier.rawValue)")
    }

    func fetchPaymentMethod() async throws -> PaymentMethod {
        await simulateDelay()
        return mockPaymentMethod
    }

    // Backup Operations
    func initiateBackup() async throws {
        await simulateDelay()
        print("API: Backup initiated and completed.")
    }
}

// MARK: - 4. AccountSettingsViewModel
class AccountSettingsViewModel: ObservableObject {
    @Published var user: User = User(username: "", email: "", isEmailVerified: false)
    @Published var subscription: Subscription?
    @Published var paymentMethod: PaymentMethod?

    // Account Management State
    @Published var newUsername: String = ""
    @Published var oldPassword = ""
    @Published var newPassword = ""
    @Published var confirmPassword = ""

    // Subscription State
    @Published var selectedTier: SubscriptionTier = .free

    // Preference State (using @AppStorage)
    @AppStorage("isDarkModeEnabled") var isDarkModeEnabled: Bool = false
    @AppStorage("notificationsEnabled") var notificationsEnabled: Bool = true
    @AppStorage("backupFrequency") var backupFrequency: Int = 1 // 0: daily, 1: weekly, 2: monthly

    // UI State
    @Published var isLoading = false
    @Published var alertMessage: String?
    @Published var isShowingDeleteConfirmation = false
    @Published var isShowingPasswordChangeSuccess = false

    // Performance: Debouncer for username input
    @StateObject var usernameDebouncer: Debouncer<String>

    private var cancellables = Set<AnyCancellable>()
    private let apiService: APIService

    init(apiService: APIService = .shared) {
        self.apiService = apiService
        // Initialize debouncer with a default value
        _usernameDebouncer = StateObject(wrappedValue: Debouncer(initialValue: ""))

        // Observe the debounced value and trigger API call
        usernameDebouncer.$debouncedValue
            .dropFirst() // Don't trigger on initial value
            .sink { [weak self] debouncedName in
                guard let self = self else { return }
                // Only update if the debounced name is different from the current user name
                if debouncedName != self.user.username && !debouncedName.isEmpty {
                    Task { await self.updateUsername() }
                }
            }
            .store(in: &cancellables)

        // Load initial data
        Task { await loadInitialData() }
    }

    @MainActor
    func loadInitialData() async {
        isLoading = true
        do {
            self.user = try await apiService.fetchUser()
            self.newUsername = self.user.username // Initialize input field
            self.subscription = try await apiService.fetchSubscription()
            self.selectedTier = self.subscription?.tier ?? .free
            self.paymentMethod = try await apiService.fetchPaymentMethod()
        } catch {
            alertMessage = "Failed to load data: \(error.localizedDescription)"
        }
        isLoading = false
    }

    @MainActor
    func updateUsername() async {
        guard newUsername != user.username else { return } // Only update if changed

        // Validation: Input validation
        if newUsername.count < 3 {
            alertMessage = "Username must be at least 3 characters long."
            return
        }

        isLoading = true
        do {
            try await apiService.updateUsername(newUsername)
            user.username = newUsername // Update local model on success
            alertMessage = "Username updated successfully!" // Success feedback
        } catch {
            alertMessage = "Failed to update username: \(error.localizedDescription)" // Error handling
            // Revert the input field to the last successful value on failure
            newUsername = user.username
        }
        isLoading = false
    }

    @MainActor
    func changePassword() async {
        // Validation: Input validation
        guard newPassword == confirmPassword else {
            alertMessage = "New passwords do not match."
            return
        }
        guard newPassword.count >= 8 else {
            alertMessage = "Password must be at least 8 characters."
            return
        }

        isLoading = true
        do {
            try await apiService.changePassword(old: oldPassword, new: newPassword)
            // Clear fields on success
            oldPassword = ""
            newPassword = ""
            confirmPassword = ""
            isShowingPasswordChangeSuccess = true // Success feedback
        } catch {
            alertMessage = "Failed to change password: \(error.localizedDescription)" // Error handling
        }
        isLoading = false
    }

    @MainActor
    func updateSubscription() async {
        guard selectedTier != subscription?.tier else { return }

        isLoading = true
        do {
            try await apiService.updateSubscription(to: selectedTier)
            // Re-fetch or update local model
            self.subscription = try await apiService.fetchSubscription()
            alertMessage = "Subscription updated to \(selectedTier.rawValue)!"
        } catch {
            alertMessage = "Failed to update subscription: \(error.localizedDescription)"
            // Revert on failure
            self.selectedTier = self.subscription?.tier ?? .free
        }
        isLoading = false
    }

    @MainActor
    func initiateBackup() async {
        isLoading = true
        do {
            try await apiService.initiateBackup()
            alertMessage = "iCloud backup initiated and completed successfully."
        } catch {
            alertMessage = "Backup failed: \(error.localizedDescription)"
        }
        isLoading = false
    }

    @MainActor
    func deleteAccount() async {
        isLoading = true
        do {
            try await apiService.deleteAccount()
            // In a real app, this would navigate to a login screen
            alertMessage = "Account successfully deleted. Goodbye!"
        } catch {
            alertMessage = "Account deletion failed: \(error.localizedDescription)"
        }
        isLoading = false
    }
}

// MARK: - 5. AccountSettingsView
struct AccountSettingsView: View {
    // Architecture: MVVM with @StateObject ViewModel
    @StateObject var viewModel: AccountSettingsViewModel = AccountSettingsViewModel()

    // Design: Navi blue (#2563EB)
    let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)

    var body: some View {
        NavigationView {
            // Design: grouped lists, Form components
            Form {
                // MARK: - Account Management
                Section(header: Text("Account Management").accessibilityLabel("Account Management Settings")) {
                    // Edit Profile
                    HStack {
                        Text("Username")
                            .accessibilityLabel("Current username")
                        Spacer()
                        // Real-time preview (via binding)
                        TextField("Enter username", text: $viewModel.usernameDebouncer.currentValue)
                            .multilineTextAlignment(.trailing)
                            .foregroundColor(.secondary)
                            .disabled(viewModel.isLoading)
                            .accessibilityValue(viewModel.usernameDebouncer.currentValue)
                    }

                    HStack {
                        Text("Email")
                        Spacer()
                        Text(viewModel.user.email)
                            .foregroundColor(.secondary)
                            .accessibilityValue(viewModel.user.email)
                    }

                    // Change Password
                    DisclosureGroup("Change Password") {
                        SecureField("Old Password", text: $viewModel.oldPassword)
                            .textContentType(.password)
                            .accessibilityLabel("Old password input")
                        SecureField("New Password", text: $viewModel.newPassword)
                            .textContentType(.newPassword)
                            .accessibilityLabel("New password input")
                        SecureField("Confirm New Password", text: $viewModel.confirmPassword)
                            .textContentType(.newPassword)
                            .accessibilityLabel("Confirm new password input")

                        Button("Update Password") {
                            Task { await viewModel.changePassword() }
                        }
                        .disabled(viewModel.newPassword.isEmpty || viewModel.newPassword != viewModel.confirmPassword || viewModel.isLoading)
                        .foregroundColor(naviBlue)
                        .accessibilityHint("Tap to change your account password")
                    }
                }

                // MARK: - Subscription Tier Card & Payment Method
                Section(header: Text("Subscription & Billing").accessibilityLabel("Subscription and Billing Settings")) {
                    if let subscription = viewModel.subscription {
                        // Subscription Tier Card
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Current Plan: \(subscription.tier.rawValue)")
                                .font(.headline)
                                .foregroundColor(naviBlue)
                                .accessibilityValue("Current plan is \(subscription.tier.rawValue)")

                            Text("Renews on \(subscription.renewalDate.formatted(date: .abbreviated, time: .omitted)) for $\(subscription.price, specifier: "%.2f")")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .accessibilityValue("Renews on \(subscription.renewalDate.formatted(date: .abbreviated, time: .omitted))")

                            // Pickers
                            Picker("Change Tier", selection: $viewModel.selectedTier) {
                                ForEach(SubscriptionTier.allCases, id: \.self) { tier in
                                    Text("\(tier.rawValue) ($\(tier.price, specifier: "%.2f"))")
                                }
                            }
                            .pickerStyle(.menu)
                            .onChange(of: viewModel.selectedTier) { _ in
                                Task { await viewModel.updateSubscription() }
                            }
                            .accessibilityLabel("Subscription tier selector")
                        }
                        .padding(.vertical, 5)
                    }

                    // Payment Method
                    if let paymentMethod = viewModel.paymentMethod {
                        HStack {
                            Text("Payment Method")
                            Spacer()
                            Text("\(paymentMethod.type) ending in \(paymentMethod.last4)")
                                .foregroundColor(.secondary)
                                .accessibilityValue("\(paymentMethod.type) ending in \(paymentMethod.last4)")
                        }
                    }

                    Button("Manage Payment Details") {
                        // Action to open external payment portal
                    }
                    .foregroundColor(naviBlue)
                    .accessibilityHint("Opens a secure portal to manage your payment details")
                }

                // MARK: - Preferences (Settings Persistence)
                Section(header: Text("Preferences").accessibilityLabel("Application Preferences")) {
                    // Toggles
                    Toggle(isOn: $viewModel.isDarkModeEnabled) {
                        Text("Dark Mode")
                    }
                    .accessibilityLabel("Toggle dark mode")

                    Toggle(isOn: $viewModel.notificationsEnabled) {
                        Text("Push Notifications")
                    }
                    .accessibilityLabel("Toggle push notifications")

                    // Pickers
                    Picker("Backup Frequency", selection: $viewModel.backupFrequency) {
                        Text("Daily").tag(0)
                        Text("Weekly").tag(1)
                        Text("Monthly").tag(2)
                    }
                    .accessibilityLabel("Select backup frequency")
                }

                // MARK: - Advanced Features (iCloud Backup)
                Section(header: Text("Advanced").accessibilityLabel("Advanced Features")) {
                    // iCloud for backup sync
                    Button("Initiate iCloud Backup") {
                        Task { await viewModel.initiateBackup() }
                    }
                    .foregroundColor(naviBlue)
                    .accessibilityHint("Starts an immediate backup of your data to iCloud")

                    // Sliders (Example of a slider component)
                    VStack(alignment: .leading) {
                        Text("Cache Size Limit: \(Int(viewModel.user.id.hashValue % 100)) MB")
                            .accessibilityLabel("Cache size limit")
                        Slider(value: .constant(Double(viewModel.user.id.hashValue % 100)), in: 10...200) {
                            Text("Cache Size")
                        } minimumValueLabel: {
                            Text("10")
                        } maximumValueLabel: {
                            Text("200")
                        }
                        .accessibilityValue("\(Int(viewModel.user.id.hashValue % 100)) megabytes")
                    }
                }

                // MARK: - Danger Zone (Delete Account)
                Section {
                    // Confirmation dialogs
                    Button("Delete Account") {
                        viewModel.isShowingDeleteConfirmation = true
                    }
                    .foregroundColor(.red)
                    .accessibilityHint("Permanently delete your account and all associated data")
                }
            }
            .navigationTitle("Account Settings")
            .navigationBarTitleDisplayMode(.inline)
            .disabled(viewModel.isLoading) // Disable interaction when loading
        }
        // Loading states
        .overlay {
            if viewModel.isLoading {
                ProgressView("Loading...")
                    .padding()
                    .background(Color.secondary.opacity(0.2))
                    .cornerRadius(10)
                    .accessibilityLabel("Loading in progress")
            }
        }
        // Confirmation dialogs
        .confirmationDialog("Delete Account", isPresented: $viewModel.isShowingDeleteConfirmation, titleVisibility: .visible) {
            Button("Delete Account", role: .destructive) {
                Task { await viewModel.deleteAccount() }
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("Are you absolutely sure you want to delete your account? This action cannot be undone.")
        }
        // Error handling & Success feedback
        .alert("Status", isPresented: .constant(viewModel.alertMessage != nil), actions: {
            Button("OK") { viewModel.alertMessage = nil }
        }, message: {
            Text(viewModel.alertMessage ?? "")
        })
        .alert("Success", isPresented: $viewModel.isShowingPasswordChangeSuccess, actions: {
            Button("OK") { viewModel.isShowingPasswordChangeSuccess = false }
        }, message: {
            Text("Your password has been successfully updated.")
        })
        // Dynamic Type support is automatically handled by SwiftUI's Text and standard components
    }
}

// MARK: - Preview
struct AccountSettingsView_Previews: PreviewProvider {
    static var previews: some View {
        AccountSettingsView()
    }
}

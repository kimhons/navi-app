import SwiftUI
import Combine
import CoreLocation
import UserNotifications
import CoreMotion

// MARK: - 1. Supporting Structures and Services

/// A simulated error type for the API and permission requests.
enum AppError: Error, LocalizedError {
    case apiError(String)
    case permissionDenied(String)
    case unknown(String)

    var errorDescription: String? {
        switch self {
        case .apiError(let message): return NSLocalizedString("API Error: \(message)", comment: "")
        case .permissionDenied(let message): return NSLocalizedString("Permission Denied: \(message)", comment: "")
        case .unknown(let message): return NSLocalizedString("Unknown Error: \(message)", comment: "")
        }
    }
}

/// Defines the types of permissions the app requests.
enum PermissionType: String, CaseIterable, Identifiable {
    case location
    case notifications
    case motion

    var id: String { self.rawValue }

    var titleKey: String {
        switch self {
        case .location: return "PERMISSION_LOCATION_TITLE"
        case .notifications: return "PERMISSION_NOTIFICATIONS_TITLE"
        case .motion: return "PERMISSION_MOTION_TITLE"
        }
    }

    var explanationKey: String {
        switch self {
        case .location: return "PERMISSION_LOCATION_EXPLANATION"
        case .notifications: return "PERMISSION_NOTIFICATIONS_EXPLANATION"
        case .motion: return "PERMISSION_MOTION_EXPLANATION"
        }
    }
}

/// Defines the status of a permission request.
enum PermissionStatus {
    case pending
    case granted
    case denied
}

/// A model for a single permission item.
struct PermissionItem: Identifiable {
    let type: PermissionType
    var status: PermissionStatus = .pending
    var id: PermissionType { type }

    var title: String { NSLocalizedString(type.titleKey, comment: "") }
    var explanation: String { NSLocalizedString(type.explanationKey, comment: "") }
}

/// Simulated APIService as required by the prompt.
class APIService {
    static let shared = APIService()
    private init() {}

    /// Simulates a backend call to register permission status.
    func registerPermissions(status: [PermissionType: PermissionStatus]) -> AnyPublisher<Bool, AppError> {
        Future { promise in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                // Simulate success 90% of the time
                if Bool.random() {
                    print("API: Successfully registered permissions: \(status)")
                    promise(.success(true))
                } else {
                    print("API: Failed to register permissions.")
                    promise(.failure(.apiError("Failed to sync permission status with backend.")))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

/// Simulated PermissionService to handle native permission requests.
class PermissionService: NSObject, CLLocationManagerDelegate {
    static let shared = PermissionService()
    private override init() {}

    private var locationCompletion: ((PermissionStatus) -> Void)?
    private let locationManager = CLLocationManager()

    /// Requests a specific permission.
    func request(_ type: PermissionType) -> AnyPublisher<PermissionStatus, Never> {
        Future { [weak self] promise in
            DispatchQueue.main.async {
                switch type {
                case .location:
                    self?.requestLocationPermission(promise: promise)
                case .notifications:
                    self?.requestNotificationPermission(promise: promise)
                case .motion:
                    self?.requestMotionPermission(promise: promise)
                }
            }
        }
        .eraseToAnyPublisher()
    }

    private func requestLocationPermission(promise: @escaping (Result<PermissionStatus, Never>) -> Void) {
        // In a real app, this would trigger the system prompt.
        // For simulation, we'll use a random result.
        locationManager.delegate = self
        locationManager.requestWhenInUseAuthorization()

        // Simulate result after a short delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            let status: PermissionStatus = Bool.random() ? .granted : .denied
            promise(.success(status))
        }
    }

    private func requestNotificationPermission(promise: @escaping (Result<PermissionStatus, Never>) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            let status: PermissionStatus = granted ? .granted : .denied
            promise(.success(status))
        }
    }

    private func requestMotionPermission(promise: @escaping (Result<PermissionStatus, Never>) -> Void) {
        // CoreMotion doesn't have a direct request method, status is checked on first use.
        // For simulation, we'll use a random result.
        let status: PermissionStatus = Bool.random() ? .granted : .denied
        promise(.success(status))
    }
}

// MARK: - 2. PermissionsViewModel (MVVM)

class PermissionsViewModel: ObservableObject {
    @Published var permissions: [PermissionItem] = PermissionType.allCases.map { PermissionItem(type: $0) }
    @Published var isLoading: Bool = false
    @Published var error: AppError?
    @Published var isComplete: Bool = false

    private var cancellables = Set<AnyCancellable>()
    private let apiService: APIService
    private let permissionService: PermissionService

    init(apiService: APIService = .shared, permissionService: PermissionService = .shared) {
        self.apiService = apiService
        self.permissionService = permissionService
    }

    /// Requests a single permission and updates the state.
    func requestPermission(type: PermissionType) {
        guard let index = permissions.firstIndex(where: { $0.type == type }),
              permissions[index].status == .pending else { return }

        isLoading = true
        error = nil

        permissionService.request(type)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                self?.permissions[index].status = status
                self?.isLoading = false
                self?.checkCompletion()
            }
            .store(in: &cancellables)
    }

    /// Requests all pending permissions sequentially.
    func requestAllPermissions() {
        let pendingTypes = permissions.filter { $0.status == .pending }.map { $0.type }
        guard !pendingTypes.isEmpty else {
            simulateBackendCall()
            return
        }

        isLoading = true
        error = nil

        // Use a publisher chain to request permissions sequentially
        pendingTypes.publisher
            .flatMap(maxPublishers: .max(1)) { [weak self] type in
                self?.permissionService.request(type)
                    .map { (type, $0) }
                    .eraseToAnyPublisher() ?? Empty(outputType: (PermissionType, PermissionStatus).self, failureType: Never.self).eraseToAnyPublisher()
            }
            .collect() // Wait for all requests to complete
            .receive(on: DispatchQueue.main)
            .sink { [weak self] results in
                self?.isLoading = false
                for (type, status) in results {
                    if let index = self?.permissions.firstIndex(where: { $0.type == type }) {
                        self?.permissions[index].status = status
                    }
                }
                self?.simulateBackendCall()
            }
            .store(in: &cancellables)
    }

    /// Simulates the required backend call after permissions are requested.
    private func simulateBackendCall() {
        isLoading = true
        error = nil

        let statusMap = permissions.reduce(into: [PermissionType: PermissionStatus]()) { map, item in
            map[item.type] = item.status
        }

        apiService.registerPermissions(status: statusMap)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let err):
                    self?.error = err
                case .finished:
                    break
                }
            } receiveValue: { [weak self] success in
                if success {
                    self?.isComplete = true
                }
            }
            .store(in: &cancellables)
    }

    /// Checks if all permissions have been processed (granted or denied).
    private func checkCompletion() {
        if permissions.allSatisfy({ $0.status != .pending }) {
            simulateBackendCall()
        }
    }

    /// Helper to get the system image name for a permission status.
    func systemImage(for status: PermissionStatus) -> String {
        switch status {
        case .pending: return "circle"
        case .granted: return "checkmark.circle.fill"
        case .denied: return "xmark.circle.fill"
        }
    }

    /// Helper to get the color for a permission status.
    func color(for status: PermissionStatus) -> Color {
        switch status {
        case .pending: return .gray
        case .granted: return .green
        case .denied: return .red
        }
    }
}

// MARK: - 3. PermissionsView (MVVM)

struct PermissionsView: View {
    @StateObject var viewModel = PermissionsViewModel()
    @Environment(\.dismiss) var dismiss

    // Navi Design System Primary Color: #2563EB
    private let primaryColor = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                header
                
                Spacer()
                
                permissionList
                
                Spacer()
                
                footerButtons
            }
            .padding()
            .opacity(viewModel.isLoading ? 0.5 : 1.0)
            .disabled(viewModel.isLoading)

            if viewModel.isLoading {
                ProgressView(NSLocalizedString("LOADING_STATE", comment: "Loading state text"))
                    .padding()
                    .background(Color.secondary.opacity(0.8))
                    .cornerRadius(10)
            }
        }
        .onChange(of: viewModel.isComplete) { newValue in
            if newValue {
                // In a real app, this would navigate to the next screen
                dismiss()
            }
        }
        .alert(isPresented: .constant(viewModel.error != nil), error: viewModel.error) { _ in
            Button(NSLocalizedString("ALERT_OK_BUTTON", comment: "OK button text")) {
                viewModel.error = nil
            }
        }
        // Apply Inter font (using system font with custom color for minimalist Apple aesthetic)
        .font(.system(.body, design: .rounded))
    }

    // MARK: - Components

    private var header: some View {
        VStack(spacing: 10) {
            Image(systemName: "hand.raised.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 60, height: 60)
                .foregroundColor(primaryColor)
                .padding(.bottom, 10)

            Text(NSLocalizedString("PERMISSIONS_VIEW_TITLE", comment: "Title of the permissions screen"))
                .font(.largeTitle)
                .fontWeight(.bold)
                .accessibilityLabel(NSLocalizedString("PERMISSIONS_VIEW_TITLE_ACCESSIBILITY", comment: "Accessibility label for the title"))

            Text(NSLocalizedString("PERMISSIONS_VIEW_SUBTITLE", comment: "Subtitle explaining why permissions are needed"))
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .padding(.top, 40)
    }

    private var permissionList: some View {
        VStack(spacing: 20) {
            ForEach(viewModel.permissions) { item in
                PermissionRow(item: item, primaryColor: primaryColor) {
                    viewModel.requestPermission(type: item.type)
                }
            }
        }
        .padding(.horizontal)
    }

    private var footerButtons: some View {
        VStack(spacing: 15) {
            Button {
                viewModel.requestAllPermissions()
            } label: {
                Text(NSLocalizedString("BUTTON_ALLOW_ALL", comment: "Button to allow all permissions"))
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(primaryColor)
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(NSLocalizedString("BUTTON_ALLOW_ALL_ACCESSIBILITY", comment: "Accessibility label for the allow all button"))

            Button {
                // Simulate denying all and completing the flow
                viewModel.permissions = viewModel.permissions.map {
                    var newItem = $0
                    newItem.status = .denied
                    return newItem
                }
                viewModel.simulateBackendCall()
            } label: {
                Text(NSLocalizedString("BUTTON_DENY_SKIP", comment: "Button to deny or skip permissions"))
                    .fontWeight(.semibold)
                    .foregroundColor(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(NSLocalizedString("BUTTON_DENY_SKIP_ACCESSIBILITY", comment: "Accessibility label for the deny/skip button"))
        }
        .padding(.bottom, 20)
    }
}

// MARK: - 4. Component View

struct PermissionRow: View {
    let item: PermissionItem
    let primaryColor: Color
    let action: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 15) {
            Image(systemName: systemImageName)
                .resizable()
                .scaledToFit()
                .frame(width: 24, height: 24)
                .foregroundColor(imageColor)
                .padding(.top, 4)

            VStack(alignment: .leading, spacing: 5) {
                Text(item.title)
                    .font(.headline)
                    .accessibilityLabel(item.title)

                Text(item.explanation)
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .accessibilityLabel(item.explanation)
            }

            Spacer()

            if item.status == .pending {
                Button(action: action) {
                    Text(NSLocalizedString("BUTTON_ALLOW", comment: "Allow button text"))
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(primaryColor)
                        .cornerRadius(20)
                }
                .accessibilityLabel(NSLocalizedString("BUTTON_ALLOW_ACCESSIBILITY", comment: "Accessibility label for the allow button"))
            } else {
                Image(systemName: "chevron.right")
                    .foregroundColor(.secondary)
                    .opacity(0.5)
            }
        }
        .padding(.vertical, 10)
        .contentShape(Rectangle()) // Makes the whole row tappable if needed, but here only the button is active
    }

    private var systemImageName: String {
        switch item.type {
        case .location: return "location.fill"
        case .notifications: return "bell.badge.fill"
        case .motion: return "figure.walk"
        }
    }

    private var imageColor: Color {
        switch item.status {
        case .pending: return .gray
        case .granted: return .green
        case .denied: return .red
        }
    }
}

// MARK: - 5. Localization Placeholder (Strings.swift or Localizable.strings content)

/*
// Content for Localizable.strings (not a Swift file, but included for completeness)

"PERMISSIONS_VIEW_TITLE" = "Your Privacy Matters";
"PERMISSIONS_VIEW_SUBTITLE" = "We need a few permissions to provide the best experience. You can change these later in Settings.";
"PERMISSION_LOCATION_TITLE" = "Location Access";
"PERMISSION_LOCATION_EXPLANATION" = "Used to provide location-based features and services.";
"PERMISSION_NOTIFICATIONS_TITLE" = "Notifications";
"PERMISSION_NOTIFICATIONS_EXPLANATION" = "Stay up-to-date with important alerts and updates.";
"PERMISSION_MOTION_TITLE" = "Motion & Fitness";
"PERMISSION_MOTION_EXPLANATION" = "Used to track activity and personalize your experience.";
"BUTTON_ALLOW" = "Allow";
"BUTTON_ALLOW_ALL" = "Allow All & Continue";
"BUTTON_DENY_SKIP" = "Skip for Now";
"LOADING_STATE" = "Processing...";
"ALERT_OK_BUTTON" = "OK";
"PERMISSIONS_VIEW_TITLE_ACCESSIBILITY" = "Permissions required for the app";
"BUTTON_ALLOW_ALL_ACCESSIBILITY" = "Allow all permissions and continue to the next step";
"BUTTON_DENY_SKIP_ACCESSIBILITY" = "Skip the permission request and continue";
*/

// MARK: - 6. Preview

struct PermissionsView_Previews: PreviewProvider {
    static var previews: some View {
        PermissionsView()
    }
}

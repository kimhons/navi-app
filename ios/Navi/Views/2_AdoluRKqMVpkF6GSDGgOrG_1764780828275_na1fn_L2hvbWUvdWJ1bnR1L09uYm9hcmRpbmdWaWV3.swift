//
// OnboardingView.swift
//
// Complete, production-ready SwiftUI screen for a 3-page swipeable onboarding carousel.
// Follows MVVM architecture, Navi design system, and includes all required features.
//

import SwiftUI
import Combine

// MARK: - 1. Mock Dependencies and Data Structures

/// Mock APIService to simulate backend calls with Combine.
/// In a real app, this would be a separate file/module.
class APIService {
    static let shared = APIService()
    
    /// Simulates a backend call to register onboarding completion.
    /// Returns a Combine publisher that completes successfully after a delay.
    func completeOnboarding() -> AnyPublisher<Bool, Error> {
        return Future<Bool, Error> { promise in
            // Simulate network delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                // Simulate success
                promise(.success(true))
            }
        }
        .eraseToAnyPublisher()
    }
}

/// Structure for a single onboarding page.
struct OnboardingItem: Identifiable {
    let id = UUID()
    let titleKey: String
    let descriptionKey: String
    let imageName: String // Using SF Symbols for a minimalist aesthetic
}

// MARK: - 2. ViewModel (MVVM Architecture)

/// ViewModel to manage the state and logic for the OnboardingView.
final class OnboardingViewModel: ObservableObject {
    
    // MARK: Published Properties
    
    @Published var currentPage: Int = 0
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var isFinished: Bool = false // State to signal completion
    
    // MARK: Properties
    
    private var cancellables = Set<AnyCancellable>()
    
    let pages: [OnboardingItem] = [
        OnboardingItem(
            titleKey: "ONBOARDING_PAGE_1_TITLE",
            descriptionKey: "ONBOARDING_PAGE_1_DESC",
            imageName: "sparkles"
        ),
        OnboardingItem(
            titleKey: "ONBOARDING_PAGE_2_TITLE",
            descriptionKey: "ONBOARDING_PAGE_2_DESC",
            imageName: "lock.shield"
        ),
        OnboardingItem(
            titleKey: "ONBOARDING_PAGE_3_TITLE",
            descriptionKey: "ONBOARDING_PAGE_3_DESC",
            imageName: "arrow.right.circle"
        )
    ]
    
    var isLastPage: Bool {
        currentPage == pages.count - 1
    }
    
    // MARK: Actions
    
    /// Moves to the next page or completes onboarding if on the last page.
    func nextButtonTapped() {
        if isLastPage {
            completeOnboarding()
        } else {
            withAnimation {
                currentPage += 1
            }
        }
    }
    
    /// Skips the onboarding process and completes it.
    func skipButtonTapped() {
        completeOnboarding()
    }
    
    /// Simulates the API call to complete onboarding.
    private func completeOnboarding() {
        guard !isLoading else { return }
        
        isLoading = true
        errorMessage = nil
        
        APIService.shared.completeOnboarding()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                self?.isLoading = false
                switch completion {
                case .failure(let error):
                    // Simple error handling
                    self?.errorMessage = NSLocalizedString("ERROR_ONBOARDING_FAILED", comment: "Error message for failed onboarding API call") + ": \(error.localizedDescription)"
                case .finished:
                    break
                }
            } receiveValue: { [weak self] success in
                if success {
                    // Signal to the view that the process is finished and it should dismiss itself
                    self?.isFinished = true
                }
            }
            .store(in: &cancellables)
    }
}

// MARK: - 3. View (SwiftUI)

/// The main SwiftUI view for the onboarding carousel.
struct OnboardingView: View {
    
    @StateObject private var viewModel = OnboardingViewModel()
    @Environment(\.dismiss) var dismiss
    
    // Navi Design System Primary Color: #2563EB
    private let primaryColor = Color(red: 37/255, green: 99/255, blue: 235/255)
    
    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // MARK: Carousel Content
                TabView(selection: $viewModel.currentPage) {
                    ForEach(pages.indices, id: \.self) { index in
                        OnboardingPageView(item: viewModel.pages[index], primaryColor: primaryColor)
                            .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut, value: viewModel.currentPage)
                
                // MARK: Controls and Indicators
                VStack(spacing: 20) {
                    PageIndicator(numberOfPages: viewModel.pages.count, currentPage: viewModel.currentPage, primaryColor: primaryColor)
                        .padding(.bottom, 10)
                    
                    HStack {
                        // Skip Button
                        Button {
                            viewModel.skipButtonTapped()
                        } label: {
                            Text(NSLocalizedString("BUTTON_SKIP", comment: "Skip button text"))
                                .font(.system(.headline, design: .default)) // Inter font substitute
                                .foregroundColor(.gray)
                                .padding(.horizontal, 20)
                                .frame(height: 50)
                                .contentShape(Rectangle())
                        }
                        .accessibilityLabel(NSLocalizedString("A11Y_SKIP_ONBOARDING", comment: "Accessibility label for skip button"))
                        
                        Spacer()
                        
                        // Next/Done Button
                        Button {
                            viewModel.nextButtonTapped()
                        } label: {
                            Text(viewModel.isLastPage ? NSLocalizedString("BUTTON_DONE", comment: "Done button text") : NSLocalizedString("BUTTON_NEXT", comment: "Next button text"))
                                .font(.system(.headline, design: .default)) // Inter font substitute
                                .foregroundColor(.white)
                                .frame(width: 120, height: 50)
                                .background(primaryColor)
                                .cornerRadius(25)
                        }
                        .accessibilityLabel(viewModel.isLastPage ? NSLocalizedString("A11Y_FINISH_ONBOARDING", comment: "Accessibility label for finish button") : NSLocalizedString("A11Y_NEXT_PAGE", comment: "Accessibility label for next button"))
                        .disabled(viewModel.isLoading)
                    }
                    .padding(.horizontal, 30)
                    .padding(.bottom, 40)
                }
            }
            
            // MARK: Loading and Error Overlay
            if viewModel.isLoading {
                Color.black.opacity(0.4)
                    .edgesIgnoringSafeArea(.all)
                ProgressView(NSLocalizedString("LOADING_COMPLETING_ONBOARDING", comment: "Loading text for completing onboarding"))
                    .progressViewStyle(.circular)
                    .padding()
                    .background(Color.white)
                    .cornerRadius(10)
            }
            
            if let error = viewModel.errorMessage {
                VStack {
                    Spacer()
                    Text(error)
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.red.opacity(0.8))
                        .cornerRadius(8)
                        .padding(.bottom, 100)
                }
            }
        }
        // MARK: Navigation Dismissal
        .onChange(of: viewModel.isFinished) { newValue in
            if newValue {
                dismiss()
            }
        }
    }
}

// MARK: - 4. Component Views

/// A single page of the onboarding carousel.
struct OnboardingPageView: View {
    let item: OnboardingItem
    let primaryColor: Color
    
    var body: some View {
        VStack(spacing: 30) {
            Image(systemName: item.imageName)
                .resizable()
                .scaledToFit()
                .frame(width: 150, height: 150)
                .foregroundColor(primaryColor)
                .padding(.top, 50)
                .accessibilityHidden(true) // Image is decorative
            
            Text(NSLocalizedString(item.titleKey, comment: "Onboarding page title"))
                .font(.largeTitle.weight(.bold)) // Inter font substitute
                .foregroundColor(.primary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
                .accessibilityLabel(NSLocalizedString(item.titleKey, comment: "Onboarding page title"))
            
            Text(NSLocalizedString(item.descriptionKey, comment: "Onboarding page description"))
                .font(.headline) // Inter font substitute
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
                .accessibilityLabel(NSLocalizedString(item.descriptionKey, comment: "Onboarding page description"))
            
            Spacer()
        }
    }
}

/// Custom page indicator component.
struct PageIndicator: View {
    let numberOfPages: Int
    let currentPage: Int
    let primaryColor: Color
    
    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<numberOfPages, id: \.self) { index in
                Circle()
                    .fill(index == currentPage ? primaryColor : Color.gray.opacity(0.5))
                    .frame(width: 8, height: 8)
                    .scaleEffect(index == currentPage ? 1.2 : 1.0)
                    .animation(.spring, value: currentPage)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(NSLocalizedString("A11Y_PAGE_INDICATOR", comment: "Accessibility label for page indicator"))
        .accessibilityValue(NSLocalizedString("A11Y_PAGE_X_OF_Y", comment: "Accessibility value for current page")
            .replacingOccurrences(of: "{X}", with: "\(currentPage + 1)")
            .replacingOccurrences(of: "{Y}", with: "\(numberOfPages)"))
    }
}

// MARK: - 5. Preview

struct OnboardingView_Previews: PreviewProvider {
    static var previews: some View {
        OnboardingView()
    }
}

/*
// Example Localizable.strings content (not part of the Swift file, but for context)
"ONBOARDING_PAGE_1_TITLE" = "Welcome to Navi";
"ONBOARDING_PAGE_1_DESC" = "Discover powerful features designed to simplify your life and boost your productivity.";
"ONBOARDING_PAGE_2_TITLE" = "Security First";
"ONBOARDING_PAGE_2_DESC" = "Your data is protected with industry-leading encryption and privacy controls.";
"ONBOARDING_PAGE_3_TITLE" = "Get Started Now";
"ONBOARDING_PAGE_3_DESC" = "Tap 'Done' to begin your journey with the Navi application.";
"BUTTON_SKIP" = "Skip";
"BUTTON_NEXT" = "Next";
"BUTTON_DONE" = "Done";
"LOADING_COMPLETING_ONBOARDING" = "Finishing up...";
"ERROR_ONBOARDING_FAILED" = "Onboarding failed";
"A11Y_SKIP_ONBOARDING" = "Skip onboarding and proceed to the main app";
"A11Y_NEXT_PAGE" = "Go to the next page";
"A11Y_FINISH_ONBOARDING" = "Finish onboarding and start using the app";
"A11Y_PAGE_INDICATOR" = "Onboarding progress indicator";
"A11Y_PAGE_X_OF_Y" = "Page {X} of {Y}";
*/

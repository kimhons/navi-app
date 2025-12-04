//
// VoiceSettingsView.swift
//
// A complete, production-ready SwiftUI screen for Voice Guidance settings,
// implementing MVVM architecture, persistence, debounced API calls, and
// a modern design.
//

import SwiftUI
import Combine

// MARK: - 1. Core Data Models

/// Represents a specific voice option.
struct Voice: Identifiable, Hashable, Codable {
    let id = UUID()
    let name: String
    let language: VoiceLanguage
    let gender: String
}

/// Represents the available languages for voice guidance.
enum VoiceLanguage: String, CaseIterable, Identifiable, Codable {
    case englishUS = "English (US)"
    case englishUK = "English (UK)"
    case spanish = "Spanish"
    case french = "French"
    
    var id: String { self.rawValue }
}

/// Represents the volume level.
enum VoiceVolume: String, CaseIterable, Identifiable, Codable {
    case low = "Low"
    case medium = "Medium"
    case high = "High"
    
    var id: String { self.rawValue }
    
    var floatValue: Float {
        switch self {
        case .low: return 0.3
        case .medium: return 0.6
        case .high: return 1.0
        }
    }
}

// MARK: - 2. Mock Services and Utilities

/// Mock APIService to simulate network operations (account, subscription, backup).
class APIService: ObservableObject {
    static let shared = APIService()
    
    enum APIError: Error {
        case backupFailed
        case accountSyncFailed
    }
    
    func performBackup() async throws {
        // Simulate network delay
        try await Task.sleep(for: .seconds(1.5))
        
        // Simulate a 10% chance of failure
        if Int.random(in: 1...10) == 1 {
            throw APIError.backupFailed
        }
        
        print("API: Backup successful.")
    }
    
    func syncAccountSettings() async throws {
        try await Task.sleep(for: .seconds(0.5))
        print("API: Account settings synced.")
    }
}

/// Utility to debounce property changes, satisfying the performance requirement.
class Debouncer<T> {
    private let delay: TimeInterval
    private let closure: (T) -> Void
    private var workItem: DispatchWorkItem?
    
    init(delay: TimeInterval, closure: @escaping (T) -> Void) {
        self.delay = delay
        self.closure = closure
    }
    
    func call(with value: T) {
        workItem?.cancel()
        let task = DispatchWorkItem { [weak self] in
            self?.closure(value)
        }
        self.workItem = task
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: task)
    }
}

// MARK: - 3. View Model (MVVM)

class VoiceSettingsViewModel: ObservableObject {
    
    // MARK: - Published Properties (View State)
    
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var successMessage: String?
    @Published var showBackupConfirmation: Bool = false
    
    // MARK: - AppStorage (Persistence)
    
    // Use @AppStorage for simple persistence (UserDefaults)
    @AppStorage("voiceLanguage") var selectedLanguageRaw: String = VoiceLanguage.englishUS.rawValue
    @AppStorage("voiceName") var selectedVoiceName: String = "Samantha"
    @AppStorage("voiceVolume") var selectedVolumeRaw: String = VoiceVolume.medium.rawValue
    @AppStorage("announceStreetNames") var announceStreetNames: Bool = true
    
    // Computed properties for type safety
    var selectedLanguage: VoiceLanguage {
        get { VoiceLanguage(rawValue: selectedLanguageRaw) ?? .englishUS }
        set { selectedLanguageRaw = newValue.rawValue }
    }
    
    var selectedVolume: VoiceVolume {
        get { VoiceVolume(rawValue: selectedVolumeRaw) ?? .medium }
        set { selectedVolumeRaw = newValue.rawValue }
    }
    
    // MARK: - Data
    
    let availableVoices: [Voice] = [
        Voice(name: "Samantha", language: .englishUS, gender: "Female"),
        Voice(name: "Alex", language: .englishUS, gender: "Male"),
        Voice(name: "Serena", language: .englishUK, gender: "Female"),
        Voice(name: "Jorge", language: .spanish, gender: "Male"),
    ]
    
    // MARK: - Utilities
    
    private var volumeDebouncer: Debouncer<VoiceVolume>!
    private var languageDebouncer: Debouncer<VoiceLanguage>!
    
    init() {
        // Initialize debouncers for performance/API call efficiency
        volumeDebouncer = Debouncer(delay: 0.5) { [weak self] newVolume in
            self?.syncSettings()
        }
        languageDebouncer = Debouncer(delay: 1.0) { [weak self] newLanguage in
            self?.syncSettings()
        }
    }
    
    // MARK: - Actions
    
    func testVoice() {
        // Simulate real-time preview/test voice feature
        successMessage = "Testing voice: \(selectedVoiceName) in \(selectedLanguage.rawValue) at \(selectedVolume.rawValue) volume."
        // In a real app, this would trigger an audio playback service
    }
    
    func syncSettings() {
        // Simulate API call to sync settings, triggered by debouncers
        Task { @MainActor in
            do {
                try await APIService.shared.syncAccountSettings()
                // successMessage = "Settings synced successfully." // Too chatty, only show for explicit actions
            } catch {
                errorMessage = "Failed to sync settings: \(error.localizedDescription)"
            }
        }
    }
    
    func triggerVolumeChange(to newVolume: VoiceVolume) {
        selectedVolume = newVolume
        volumeDebouncer.call(with: newVolume)
    }
    
    func triggerLanguageChange(to newLanguage: VoiceLanguage) {
        selectedLanguage = newLanguage
        languageDebouncer.call(with: newLanguage)
    }
    
    func performBackup() {
        showBackupConfirmation = false
        isLoading = true
        errorMessage = nil
        successMessage = nil
        
        Task { @MainActor in
            defer { isLoading = false }
            do {
                try await APIService.shared.performBackup()
                successMessage = "Settings successfully backed up to iCloud."
            } catch {
                errorMessage = "Backup failed. Please check your connection."
            }
        }
    }
}

// MARK: - 4. View (SwiftUI)

struct VoiceSettingsView: View {
    
    // MARK: - Properties
    
    @StateObject var viewModel = VoiceSettingsViewModel()
    
    // Navi Blue color for design
    private let naviBlue = Color(red: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0)
    
    // MARK: - Body
    
    var body: some View {
        NavigationView {
            Form {
                voiceSelectionSection
                volumeSection
                advancedFeaturesSection
                accountSection
            }
            .navigationTitle("Voice Guidance")
            .tint(naviBlue) // Apply Navi Blue tint
            .alert("Error", isPresented: .constant(viewModel.errorMessage != nil)) {
                Button("OK") { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "An unknown error occurred.")
            }
            .overlay(loadingOverlay)
            .overlay(successFeedback)
            .confirmationDialog("Backup Settings", isPresented: $viewModel.showBackupConfirmation, titleVisibility: .visible) {
                Button("Backup Now", role: .destructive) {
                    viewModel.performBackup()
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("This will sync your current voice settings to iCloud for backup and cross-device synchronization.")
            }
        }
    }
    
    // MARK: - Subviews
    
    private var voiceSelectionSection: some View {
        Section("Voice Selection") {
            
            // Language Picker
            Picker("Language", selection: $viewModel.selectedLanguage) {
                ForEach(VoiceLanguage.allCases) { language in
                    Text(language.rawValue)
                        .tag(language)
                }
            }
            .onChange(of: viewModel.selectedLanguage) { newLanguage in
                viewModel.triggerLanguageChange(to: newLanguage)
            }
            .accessibilityLabel("Voice Language Picker")
            
            // Voice Picker
            Picker("Voice", selection: $viewModel.selectedVoiceName) {
                ForEach(viewModel.availableVoices.filter { $0.language == viewModel.selectedLanguage }, id: \.name) { voice in
                    Text("\(voice.name) (\(voice.gender))")
                        .tag(voice.name)
                }
            }
            .accessibilityLabel("Voice Name Picker")
            
            // Test Voice Button
            Button {
                viewModel.testVoice()
            } label: {
                HStack {
                    Text("Test Voice")
                    Spacer()
                    Image(systemName: "speaker.wave.2.fill")
                        .foregroundColor(naviBlue)
                }
            }
            .accessibilityHint("Plays a sample of the selected voice.")
        }
    }
    
    private var volumeSection: some View {
        Section("Volume") {
            
            // Volume Slider (using a proxy Float binding for the slider)
            VStack(alignment: .leading) {
                Text("Guidance Volume")
                    .font(.headline)
                
                Slider(value: .init(
                    get: { viewModel.selectedVolume.floatValue },
                    set: { floatValue in
                        // Map float value back to enum for persistence and debouncing
                        let newVolume: VoiceVolume
                        if floatValue <= 0.4 {
                            newVolume = .low
                        } else if floatValue <= 0.7 {
                            newVolume = .medium
                        } else {
                            newVolume = .high
                        }
                        viewModel.triggerVolumeChange(to: newVolume)
                    }
                ), in: 0.0...1.0, step: 0.01) {
                    Text("Volume Slider")
                } minimumValueLabel: {
                    Text("Low")
                } maximumValueLabel: {
                    Text("High")
                }
                .accessibilityValue(viewModel.selectedVolume.rawValue)
                
                Text("Current: \(viewModel.selectedVolume.rawValue)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }
    
    private var advancedFeaturesSection: some View {
        Section("Advanced Features") {
            
            // Announce Street Names Toggle
            Toggle("Announce Street Names", isOn: $viewModel.announceStreetNames)
                .accessibilityValue(viewModel.announceStreetNames ? "On" : "Off")
            
            // Placeholder for other advanced settings
            NavigationLink("Voice Prompts") {
                Text("Voice Prompts Configuration")
            }
        }
    }
    
    private var accountSection: some View {
        Section("Account & Storage") {
            
            // Backup/Sync Button
            Button("Backup Settings to iCloud") {
                viewModel.showBackupConfirmation = true
            }
            .foregroundColor(naviBlue)
            .disabled(viewModel.isLoading)
            .accessibilityHint("Triggers a manual backup of your settings to iCloud.")
        }
    }
    
    private var loadingOverlay: some View {
        Group {
            if viewModel.isLoading {
                Color.black.opacity(0.4)
                    .edgesIgnoringSafeArea(.all)
                ProgressView("Performing Backup...")
                    .padding()
                    .background(Color.white)
                    .cornerRadius(10)
            }
        }
    }
    
    private var successFeedback: some View {
        Group {
            if let message = viewModel.successMessage {
                VStack {
                    Spacer()
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.white)
                        Text(message)
                            .foregroundColor(.white)
                    }
                    .padding()
                    .background(Color.green.opacity(0.85))
                    .cornerRadius(10)
                    .padding(.bottom, 50)
                    .onAppear {
                        // Auto-dismiss success message
                        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                            viewModel.successMessage = nil
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Preview

#Preview {
    VoiceSettingsView()
}

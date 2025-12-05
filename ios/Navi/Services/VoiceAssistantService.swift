import Foundation
import Speech
import AVFoundation
import Combine

/// Voice Assistant Service - Handles speech recognition, natural language processing, and voice commands
class VoiceAssistantService: NSObject, ObservableObject {
    static let shared = VoiceAssistantService()
    
    // MARK: - Published Properties
    @Published var isListening = false
    @Published var transcribedText = ""
    @Published var isProcessing = false
    @Published var lastResponse = ""
    @Published var error: VoiceError?
    
    // MARK: - Private Properties
    private let speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()
    private let synthesizer = AVSpeechSynthesizer()
    
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Initialization
    private override init() {
        super.init()
        requestPermissions()
    }
    
    // MARK: - Permissions
    func requestPermissions() {
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                switch status {
                case .authorized:
                    print("✅ Speech recognition authorized")
                case .denied, .restricted, .notDetermined:
                    self.error = .permissionDenied
                @unknown default:
                    self.error = .unknown
                }
            }
        }
        
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            if !granted {
                DispatchQueue.main.async {
                    self.error = .microphonePermissionDenied
                }
            }
        }
    }
    
    // MARK: - Start Listening
    func startListening() {
        guard !isListening else { return }
        
        // Cancel any existing task
        recognitionTask?.cancel()
        recognitionTask = nil
        
        // Configure audio session
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(.record, mode: .measurement, options: .duckOthers)
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            self.error = .audioSessionFailed
            return
        }
        
        // Create recognition request
        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let recognitionRequest = recognitionRequest else {
            self.error = .recognitionFailed
            return
        }
        
        recognitionRequest.shouldReportPartialResults = true
        
        // Get audio input node
        let inputNode = audioEngine.inputNode
        
        // Start recognition task
        recognitionTask = speechRecognizer?.recognitionTask(with: recognitionRequest) { [weak self] result, error in
            guard let self = self else { return }
            
            var isFinal = false
            
            if let result = result {
                DispatchQueue.main.async {
                    self.transcribedText = result.bestTranscription.formattedString
                }
                isFinal = result.isFinal
            }
            
            if error != nil || isFinal {
                self.audioEngine.stop()
                inputNode.removeTap(onBus: 0)
                
                self.recognitionRequest = nil
                self.recognitionTask = nil
                
                DispatchQueue.main.async {
                    self.isListening = false
                    
                    // Process the command if we have text
                    if !self.transcribedText.isEmpty {
                        self.processVoiceCommand(self.transcribedText)
                    }
                }
            }
        }
        
        // Configure microphone input
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
            recognitionRequest.append(buffer)
        }
        
        // Start audio engine
        audioEngine.prepare()
        do {
            try audioEngine.start()
            DispatchQueue.main.async {
                self.isListening = true
                self.transcribedText = ""
                self.error = nil
            }
        } catch {
            self.error = .audioEngineFailed
        }
    }
    
    // MARK: - Stop Listening
    func stopListening() {
        guard isListening else { return }
        
        audioEngine.stop()
        recognitionRequest?.endAudio()
        
        DispatchQueue.main.async {
            self.isListening = false
        }
    }
    
    // MARK: - Process Voice Command
    func processVoiceCommand(_ command: String) {
        isProcessing = true
        
        // Analyze command intent
        let intent = analyzeIntent(command)
        
        // Execute command based on intent
        executeCommand(intent: intent, originalCommand: command)
    }
    
    // MARK: - Analyze Intent
    private func analyzeIntent(_ command: String) -> VoiceIntent {
        let lowercased = command.lowercased()
        
        // Navigation commands
        if lowercased.contains("navigate") || lowercased.contains("take me") || lowercased.contains("directions") {
            return .navigate(destination: extractLocation(from: command))
        }
        
        // Search commands
        if lowercased.contains("find") || lowercased.contains("search") || lowercased.contains("look for") {
            return .search(query: extractSearchQuery(from: command))
        }
        
        // Add waypoint
        if lowercased.contains("add stop") || lowercased.contains("add waypoint") || lowercased.contains("stop at") {
            return .addWaypoint(location: extractLocation(from: command))
        }
        
        // Route adjustments
        if lowercased.contains("avoid tolls") {
            return .avoidTolls(true)
        }
        if lowercased.contains("avoid highways") {
            return .avoidHighways(true)
        }
        if lowercased.contains("fastest route") {
            return .fastestRoute
        }
        
        // Traffic info
        if lowercased.contains("traffic") || lowercased.contains("how's traffic") {
            return .checkTraffic
        }
        
        // ETA
        if lowercased.contains("eta") || lowercased.contains("arrival time") || lowercased.contains("how long") {
            return .getETA
        }
        
        // Nearby places
        if lowercased.contains("nearby") || lowercased.contains("near me") {
            let category = extractCategory(from: command)
            return .findNearby(category: category)
        }
        
        // Save location
        if lowercased.contains("save") || lowercased.contains("bookmark") {
            return .saveLocation(name: extractLocationName(from: command))
        }
        
        // Cancel navigation
        if lowercased.contains("cancel") || lowercased.contains("stop navigation") {
            return .cancelNavigation
        }
        
        // Mute/unmute
        if lowercased.contains("mute") {
            return .muteVoice
        }
        if lowercased.contains("unmute") {
            return .unmuteVoice
        }
        
        // Share location
        if lowercased.contains("share") && lowercased.contains("location") {
            return .shareLocation
        }
        
        return .unknown(command)
    }
    
    // MARK: - Execute Command
    private func executeCommand(intent: VoiceIntent, originalCommand: String) {
        switch intent {
        case .navigate(let destination):
            handleNavigate(to: destination)
            
        case .search(let query):
            handleSearch(query: query)
            
        case .addWaypoint(let location):
            handleAddWaypoint(location: location)
            
        case .avoidTolls(let avoid):
            handleAvoidTolls(avoid: avoid)
            
        case .avoidHighways(let avoid):
            handleAvoidHighways(avoid: avoid)
            
        case .fastestRoute:
            handleFastestRoute()
            
        case .checkTraffic:
            handleCheckTraffic()
            
        case .getETA:
            handleGetETA()
            
        case .findNearby(let category):
            handleFindNearby(category: category)
            
        case .saveLocation(let name):
            handleSaveLocation(name: name)
            
        case .cancelNavigation:
            handleCancelNavigation()
            
        case .muteVoice:
            handleMuteVoice()
            
        case .unmuteVoice:
            handleUnmuteVoice()
            
        case .shareLocation:
            handleShareLocation()
            
        case .unknown:
            handleUnknownCommand(originalCommand)
        }
    }
    
    // MARK: - Command Handlers
    
    private func handleNavigate(to destination: String) {
        speak("Searching for \(destination)")
        
        // Search for the destination
        APIService.shared.searchPlaces(query: destination, location: nil, radius: nil)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] completion in
                if case .failure = completion {
                    self?.speak("Sorry, I couldn't find \(destination)")
                    self?.isProcessing = false
                }
            } receiveValue: { [weak self] places in
                guard let self = self, let place = places.first else {
                    self?.speak("No results found for \(destination)")
                    self?.isProcessing = false
                    return
                }
                
                self.speak("Starting navigation to \(place.name)")
                NotificationCenter.default.post(
                    name: .voiceCommandNavigate,
                    object: nil,
                    userInfo: ["place": place]
                )
                self.isProcessing = false
            }
            .store(in: &cancellables)
    }
    
    private func handleSearch(query: String) {
        speak("Searching for \(query)")
        NotificationCenter.default.post(
            name: .voiceCommandSearch,
            object: nil,
            userInfo: ["query": query]
        )
        isProcessing = false
    }
    
    private func handleAddWaypoint(location: String) {
        speak("Adding \(location) as a waypoint")
        NotificationCenter.default.post(
            name: .voiceCommandAddWaypoint,
            object: nil,
            userInfo: ["location": location]
        )
        isProcessing = false
    }
    
    private func handleAvoidTolls(avoid: Bool) {
        speak(avoid ? "Avoiding tolls" : "Including tolls in route")
        NotificationCenter.default.post(
            name: .voiceCommandAvoidTolls,
            object: nil,
            userInfo: ["avoid": avoid]
        )
        isProcessing = false
    }
    
    private func handleAvoidHighways(avoid: Bool) {
        speak(avoid ? "Avoiding highways" : "Including highways in route")
        NotificationCenter.default.post(
            name: .voiceCommandAvoidHighways,
            object: nil,
            userInfo: ["avoid": avoid]
        )
        isProcessing = false
    }
    
    private func handleFastestRoute() {
        speak("Calculating fastest route")
        NotificationCenter.default.post(name: .voiceCommandFastestRoute, object: nil)
        isProcessing = false
    }
    
    private func handleCheckTraffic() {
        speak("Checking traffic conditions")
        NotificationCenter.default.post(name: .voiceCommandCheckTraffic, object: nil)
        isProcessing = false
    }
    
    private func handleGetETA() {
        speak("Calculating estimated time of arrival")
        NotificationCenter.default.post(name: .voiceCommandGetETA, object: nil)
        isProcessing = false
    }
    
    private func handleFindNearby(category: String) {
        speak("Finding nearby \(category)")
        NotificationCenter.default.post(
            name: .voiceCommandFindNearby,
            object: nil,
            userInfo: ["category": category]
        )
        isProcessing = false
    }
    
    private func handleSaveLocation(name: String?) {
        let locationName = name ?? "this location"
        speak("Saving \(locationName)")
        NotificationCenter.default.post(
            name: .voiceCommandSaveLocation,
            object: nil,
            userInfo: ["name": name as Any]
        )
        isProcessing = false
    }
    
    private func handleCancelNavigation() {
        speak("Canceling navigation")
        NotificationCenter.default.post(name: .voiceCommandCancelNavigation, object: nil)
        isProcessing = false
    }
    
    private func handleMuteVoice() {
        speak("Voice guidance muted")
        NotificationCenter.default.post(name: .voiceCommandMute, object: nil)
        isProcessing = false
    }
    
    private func handleUnmuteVoice() {
        speak("Voice guidance unmuted")
        NotificationCenter.default.post(name: .voiceCommandUnmute, object: nil)
        isProcessing = false
    }
    
    private func handleShareLocation() {
        speak("Sharing your current location")
        NotificationCenter.default.post(name: .voiceCommandShareLocation, object: nil)
        isProcessing = false
    }
    
    private func handleUnknownCommand(_ command: String) {
        speak("I'm not sure how to help with that. Try saying 'Navigate to' followed by a location, or 'Find nearby' followed by what you're looking for.")
        isProcessing = false
    }
    
    // MARK: - Text Extraction Helpers
    
    private func extractLocation(from command: String) -> String {
        let keywords = ["navigate to", "take me to", "directions to", "add stop at", "stop at"]
        for keyword in keywords {
            if let range = command.lowercased().range(of: keyword) {
                let location = String(command[range.upperBound...]).trimmingCharacters(in: .whitespaces)
                return location
            }
        }
        return command
    }
    
    private func extractSearchQuery(from command: String) -> String {
        let keywords = ["find", "search for", "look for"]
        for keyword in keywords {
            if let range = command.lowercased().range(of: keyword) {
                let query = String(command[range.upperBound...]).trimmingCharacters(in: .whitespaces)
                return query
            }
        }
        return command
    }
    
    private func extractCategory(from command: String) -> String {
        let categories = ["restaurants", "gas stations", "parking", "hotels", "cafes", "atms", "pharmacies", "hospitals"]
        for category in categories {
            if command.lowercased().contains(category) {
                return category
            }
        }
        return "places"
    }
    
    private func extractLocationName(from command: String) -> String? {
        if let range = command.lowercased().range(of: "save") {
            let name = String(command[range.upperBound...]).trimmingCharacters(in: .whitespaces)
            return name.isEmpty ? nil : name
        }
        return nil
    }
    
    // MARK: - Text-to-Speech
    func speak(_ text: String) {
        DispatchQueue.main.async {
            self.lastResponse = text
        }
        
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = 0.5
        utterance.pitchMultiplier = 1.0
        utterance.volume = 1.0
        
        synthesizer.speak(utterance)
    }
    
    func stopSpeaking() {
        synthesizer.stopSpeaking(at: .immediate)
    }
}

// MARK: - Voice Intent Enum
enum VoiceIntent {
    case navigate(destination: String)
    case search(query: String)
    case addWaypoint(location: String)
    case avoidTolls(Bool)
    case avoidHighways(Bool)
    case fastestRoute
    case checkTraffic
    case getETA
    case findNearby(category: String)
    case saveLocation(name: String?)
    case cancelNavigation
    case muteVoice
    case unmuteVoice
    case shareLocation
    case unknown(String)
}

// MARK: - Voice Error Enum
enum VoiceError: Error, LocalizedError {
    case permissionDenied
    case microphonePermissionDenied
    case audioSessionFailed
    case recognitionFailed
    case audioEngineFailed
    case unknown
    
    var errorDescription: String? {
        switch self {
        case .permissionDenied:
            return "Speech recognition permission denied. Please enable it in Settings."
        case .microphonePermissionDenied:
            return "Microphone permission denied. Please enable it in Settings."
        case .audioSessionFailed:
            return "Failed to configure audio session."
        case .recognitionFailed:
            return "Speech recognition failed."
        case .audioEngineFailed:
            return "Failed to start audio engine."
        case .unknown:
            return "An unknown error occurred."
        }
    }
}

// MARK: - Notification Names
extension Notification.Name {
    static let voiceCommandNavigate = Notification.Name("voiceCommandNavigate")
    static let voiceCommandSearch = Notification.Name("voiceCommandSearch")
    static let voiceCommandAddWaypoint = Notification.Name("voiceCommandAddWaypoint")
    static let voiceCommandAvoidTolls = Notification.Name("voiceCommandAvoidTolls")
    static let voiceCommandAvoidHighways = Notification.Name("voiceCommandAvoidHighways")
    static let voiceCommandFastestRoute = Notification.Name("voiceCommandFastestRoute")
    static let voiceCommandCheckTraffic = Notification.Name("voiceCommandCheckTraffic")
    static let voiceCommandGetETA = Notification.Name("voiceCommandGetETA")
    static let voiceCommandFindNearby = Notification.Name("voiceCommandFindNearby")
    static let voiceCommandSaveLocation = Notification.Name("voiceCommandSaveLocation")
    static let voiceCommandCancelNavigation = Notification.Name("voiceCommandCancelNavigation")
    static let voiceCommandMute = Notification.Name("voiceCommandMute")
    static let voiceCommandUnmute = Notification.Name("voiceCommandUnmute")
    static let voiceCommandShareLocation = Notification.Name("voiceCommandShareLocation")
}

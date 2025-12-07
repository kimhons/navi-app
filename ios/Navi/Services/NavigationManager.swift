import Foundation
import CoreLocation
import AVFoundation
import Combine

/// Navigation Manager - Handles turn-by-turn navigation and route following
class NavigationManager: ObservableObject {
    static let shared = NavigationManager()
    
    // MARK: - Published Properties
    @Published var isNavigating = false
    @Published var currentRoute: Route?
    @Published var currentStepIndex = 0
    @Published var currentInstruction: String = ""
    @Published var distanceToNextManeuver: Double = 0 // meters
    @Published var remainingDistance: Double = 0 // meters
    @Published var remainingDuration: Double = 0 // seconds
    @Published var estimatedArrivalTime: Date?
    @Published var currentSpeed: CLLocationSpeed = 0
    @Published var isOffRoute = false
    @Published var progress: Double = 0 // 0.0 to 1.0
    
    // MARK: - Private Properties
    private var speechSynthesizer = AVSpeechSynthesizer()
    private var cancellables = Set<AnyCancellable>()
    private var lastAnnouncedDistance: Double = 0
    private let offRouteThreshold: Double = 50 // meters
    private var totalRouteDistance: Double = 0
    
    // Voice guidance settings
    var isVoiceGuidanceEnabled = true
    var voiceVolume: Float = 1.0
    
    // MARK: - Initialization
    private init() {
        setupLocationTracking()
    }
    
    // MARK: - Setup
    
    private func setupLocationTracking() {
        // Subscribe to location updates
        LocationManager.shared.$userLocation
            .compactMap { $0 }
            .sink { [weak self] location in
                self?.updateNavigationState(userLocation: location)
            }
            .store(in: &cancellables)
        
        // Subscribe to speed updates
        LocationManager.shared.$speed
            .sink { [weak self] speed in
                self?.currentSpeed = speed
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Navigation Control
    
    /// Start navigation with a route
    func startNavigation(route: Route) {
        self.currentRoute = route
        self.currentStepIndex = 0
        self.isNavigating = true
        self.isOffRoute = false
        self.totalRouteDistance = route.distance
        self.remainingDistance = route.distance
        self.remainingDuration = route.duration
        self.progress = 0
        
        // Calculate ETA
        calculateETA()
        
        // Get first instruction
        if let firstStep = route.legs.first?.steps.first {
            currentInstruction = firstStep.maneuver.instruction
            announceInstruction(currentInstruction)
        }
        
        // Start location tracking
        LocationManager.shared.startTracking()
    }
    
    /// Stop navigation
    func stopNavigation() {
        isNavigating = false
        currentRoute = nil
        currentStepIndex = 0
        currentInstruction = ""
        distanceToNextManeuver = 0
        remainingDistance = 0
        remainingDuration = 0
        estimatedArrivalTime = nil
        progress = 0
        isOffRoute = false
        
        // Stop speech
        speechSynthesizer.stopSpeaking(at: .immediate)
    }
    
    /// Pause navigation
    func pauseNavigation() {
        isNavigating = false
        speechSynthesizer.stopSpeaking(at: .immediate)
    }
    
    /// Resume navigation
    func resumeNavigation() {
        guard currentRoute != nil else { return }
        isNavigating = true
    }
    
    // MARK: - Navigation State Updates
    
    private func updateNavigationState(userLocation: CLLocationCoordinate2D) {
        guard isNavigating,
              let route = currentRoute,
              let currentLeg = route.legs.first,
              currentStepIndex < currentLeg.steps.count else {
            return
        }
        
        let currentStep = currentLeg.steps[currentStepIndex]
        let maneuverLocation = CLLocationCoordinate2D(
            latitude: currentStep.maneuver.location[1],
            longitude: currentStep.maneuver.location[0]
        )
        
        // Calculate distance to next maneuver
        distanceToNextManeuver = LocationManager.shared.distance(
            from: userLocation,
            to: maneuverLocation
        )
        
        // Check if we've reached the maneuver point
        if distanceToNextManeuver < 20 { // Within 20 meters
            moveToNextStep()
        }
        
        // Check if we're off route
        checkIfOffRoute(userLocation: userLocation, step: currentStep)
        
        // Update remaining distance and duration
        updateRemainingDistanceAndDuration()
        
        // Update progress
        let distanceTraveled = totalRouteDistance - remainingDistance
        progress = distanceTraveled / totalRouteDistance
        
        // Voice announcements at specific distances
        announceUpcomingManeuver()
        
        // Update ETA
        calculateETA()
    }
    
    private func moveToNextStep() {
        guard let route = currentRoute,
              let currentLeg = route.legs.first else {
            return
        }
        
        currentStepIndex += 1
        
        if currentStepIndex < currentLeg.steps.count {
            let nextStep = currentLeg.steps[currentStepIndex]
            currentInstruction = nextStep.maneuver.instruction
            announceInstruction(currentInstruction)
            lastAnnouncedDistance = 0
        } else {
            // Reached destination
            arrivedAtDestination()
        }
    }
    
    private func checkIfOffRoute(userLocation: CLLocationCoordinate2D, step: RouteStep) {
        // Calculate distance from user to route line
        let routeCoordinates = step.geometry.coordinates
        let distanceToRoute = minimumDistanceToLine(
            point: userLocation,
            linePoints: routeCoordinates
        )
        
        if distanceToRoute > offRouteThreshold {
            if !isOffRoute {
                isOffRoute = true
                announceOffRoute()
            }
        } else {
            isOffRoute = false
        }
    }
    
    private func minimumDistanceToLine(point: CLLocationCoordinate2D, linePoints: [CLLocationCoordinate2D]) -> Double {
        var minDistance = Double.infinity
        
        for i in 0..<(linePoints.count - 1) {
            let lineStart = linePoints[i]
            let lineEnd = linePoints[i + 1]
            let distance = distanceToLineSegment(
                point: point,
                lineStart: lineStart,
                lineEnd: lineEnd
            )
            minDistance = min(minDistance, distance)
        }
        
        return minDistance
    }
    
    private func distanceToLineSegment(point: CLLocationCoordinate2D, lineStart: CLLocationCoordinate2D, lineEnd: CLLocationCoordinate2D) -> Double {
        // Simplified distance calculation
        let distanceToStart = LocationManager.shared.distance(from: point, to: lineStart)
        let distanceToEnd = LocationManager.shared.distance(from: point, to: lineEnd)
        return min(distanceToStart, distanceToEnd)
    }
    
    private func updateRemainingDistanceAndDuration() {
        guard let route = currentRoute,
              let currentLeg = route.legs.first else {
            return
        }
        
        // Calculate remaining distance
        var remaining = 0.0
        for i in currentStepIndex..<currentLeg.steps.count {
            remaining += currentLeg.steps[i].distance
        }
        remaining += distanceToNextManeuver
        remainingDistance = remaining
        
        // Estimate remaining duration based on current speed
        if currentSpeed > 0 {
            remainingDuration = remainingDistance / currentSpeed
        } else {
            // Use route's estimated duration
            var durationRemaining = 0.0
            for i in currentStepIndex..<currentLeg.steps.count {
                durationRemaining += currentLeg.steps[i].duration
            }
            remainingDuration = durationRemaining
        }
    }
    
    private func calculateETA() {
        estimatedArrivalTime = Date().addingTimeInterval(remainingDuration)
    }
    
    // MARK: - Voice Guidance
    
    private func announceInstruction(_ instruction: String) {
        guard isVoiceGuidanceEnabled else { return }
        
        let utterance = AVSpeechUtterance(string: instruction)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = 0.5
        utterance.volume = voiceVolume
        
        speechSynthesizer.speak(utterance)
    }
    
    private func announceUpcomingManeuver() {
        guard isVoiceGuidanceEnabled else { return }
        
        let distance = distanceToNextManeuver
        
        // Announce at 500m, 200m, 100m, and 50m
        if distance < 500 && lastAnnouncedDistance >= 500 {
            announce("In 500 meters, \(currentInstruction)")
            lastAnnouncedDistance = 500
        } else if distance < 200 && lastAnnouncedDistance >= 200 {
            announce("In 200 meters, \(currentInstruction)")
            lastAnnouncedDistance = 200
        } else if distance < 100 && lastAnnouncedDistance >= 100 {
            announce("In 100 meters, \(currentInstruction)")
            lastAnnouncedDistance = 100
        } else if distance < 50 && lastAnnouncedDistance >= 50 {
            announce("In 50 meters, \(currentInstruction)")
            lastAnnouncedDistance = 50
        }
    }
    
    private func announce(_ text: String) {
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = 0.5
        utterance.volume = voiceVolume
        
        speechSynthesizer.speak(utterance)
    }
    
    private func announceOffRoute() {
        announce("You are off route. Recalculating...")
        requestRouteRecalculation()
    }
    
    private func arrivedAtDestination() {
        announce("You have arrived at your destination")
        stopNavigation()
        
        // Post notification
        NotificationCenter.default.post(name: .arrivedAtDestination, object: nil)
    }
    
    // MARK: - Route Recalculation
    
    private func requestRouteRecalculation() {
        guard let userLocation = LocationManager.shared.userLocation,
              let route = currentRoute,
              let destination = route.geometry.coordinates.last else {
            return
        }
        
        // Request new route from current location
        MapboxManager.shared.calculateRoute(
            from: userLocation,
            to: destination
        ) { [weak self] result in
            switch result {
            case .success(let newRoute):
                self?.startNavigation(route: newRoute)
                self?.isOffRoute = false
            case .failure(let error):
                print("Failed to recalculate route: \(error.localizedDescription)")
            }
        }
    }
    
    // MARK: - Helper Methods
    
    /// Get formatted remaining distance
    func getFormattedRemainingDistance() -> String {
        if remainingDistance < 1000 {
            return String(format: "%.0f m", remainingDistance)
        } else {
            let km = remainingDistance / 1000
            return String(format: "%.1f km", km)
        }
    }
    
    /// Get formatted remaining time
    func getFormattedRemainingTime() -> String {
        let hours = Int(remainingDuration) / 3600
        let minutes = (Int(remainingDuration) % 3600) / 60
        
        if hours > 0 {
            return "\(hours)h \(minutes)m"
        } else {
            return "\(minutes)m"
        }
    }
    
    /// Get formatted ETA
    func getFormattedETA() -> String {
        guard let eta = estimatedArrivalTime else { return "" }
        
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: eta)
    }
    
    /// Toggle voice guidance
    func toggleVoiceGuidance() {
        isVoiceGuidanceEnabled.toggle()
        
        if !isVoiceGuidanceEnabled {
            speechSynthesizer.stopSpeaking(at: .immediate)
        }
    }
    
    /// Set voice volume
    func setVoiceVolume(_ volume: Float) {
        voiceVolume = max(0, min(1, volume))
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let arrivedAtDestination = Notification.Name("arrivedAtDestination")
}

import SwiftUI

struct VoiceAssistantView: View {
    @StateObject private var voiceService = VoiceAssistantService.shared
    @Environment(\.dismiss) private var dismiss
    @State private var waveformAmplitudes: [CGFloat] = Array(repeating: 0.3, count: 50)
    @State private var animationTimer: Timer?
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: [
                    Color(hex: "2563EB").opacity(0.1),
                    Color(hex: "2563EB").opacity(0.05)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            VStack(spacing: 32) {
                // Close button
                HStack {
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .foregroundColor(.gray)
                    }
                    .padding()
                }
                
                Spacer()
                
                // Voice Assistant Icon with animation
                ZStack {
                    // Pulsing circles
                    if voiceService.isListening {
                        ForEach(0..<3) { index in
                            Circle()
                                .stroke(Color(hex: "2563EB").opacity(0.3), lineWidth: 2)
                                .frame(width: 200 + CGFloat(index * 40), height: 200 + CGFloat(index * 40))
                                .scaleEffect(voiceService.isListening ? 1.2 : 0.8)
                                .opacity(voiceService.isListening ? 0 : 0.5)
                                .animation(
                                    Animation.easeInOut(duration: 1.5)
                                        .repeatForever(autoreverses: false)
                                        .delay(Double(index) * 0.3),
                                    value: voiceService.isListening
                                )
                        }
                    }
                    
                    // Main microphone button
                    Circle()
                        .fill(
                            voiceService.isListening
                                ? Color(hex: "2563EB")
                                : Color(hex: "2563EB").opacity(0.2)
                        )
                        .frame(width: 120, height: 120)
                        .overlay(
                            Image(systemName: voiceService.isListening ? "waveform" : "mic.fill")
                                .font(.system(size: 50))
                                .foregroundColor(.white)
                        )
                        .shadow(color: Color(hex: "2563EB").opacity(0.3), radius: 20)
                }
                .onTapGesture {
                    if voiceService.isListening {
                        voiceService.stopListening()
                        stopWaveformAnimation()
                    } else {
                        voiceService.startListening()
                        startWaveformAnimation()
                    }
                }
                
                // Waveform visualization
                if voiceService.isListening {
                    HStack(alignment: .center, spacing: 3) {
                        ForEach(0..<waveformAmplitudes.count, id: \.self) { index in
                            RoundedRectangle(cornerRadius: 2)
                                .fill(Color(hex: "2563EB"))
                                .frame(width: 3, height: waveformAmplitudes[index] * 60)
                                .animation(.easeInOut(duration: 0.1), value: waveformAmplitudes[index])
                        }
                    }
                    .frame(height: 80)
                    .transition(.opacity)
                }
                
                // Status text
                VStack(spacing: 12) {
                    Text(voiceService.isListening ? "Listening..." : voiceService.isProcessing ? "Processing..." : "Tap to speak")
                        .font(.title2)
                        .fontWeight(.semibold)
                        .foregroundColor(.primary)
                    
                    if !voiceService.transcribedText.isEmpty {
                        Text(voiceService.transcribedText)
                            .font(.body)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                    
                    if !voiceService.lastResponse.isEmpty && !voiceService.isListening {
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                            Text(voiceService.lastResponse)
                                .font(.body)
                                .foregroundColor(.secondary)
                        }
                        .padding(.horizontal, 32)
                        .multilineTextAlignment(.center)
                    }
                }
                .frame(height: 120)
                
                Spacer()
                
                // Example commands
                VStack(alignment: .leading, spacing: 16) {
                    Text("Try saying:")
                        .font(.headline)
                        .foregroundColor(.secondary)
                    
                    VStack(alignment: .leading, spacing: 12) {
                        ExampleCommand(icon: "location.fill", text: "Navigate to Starbucks")
                        ExampleCommand(icon: "magnifyingglass", text: "Find gas stations nearby")
                        ExampleCommand(icon: "plus.circle", text: "Add a stop at McDonald's")
                        ExampleCommand(icon: "car.fill", text: "Avoid tolls")
                        ExampleCommand(icon: "clock.fill", text: "What's my ETA?")
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
            }
        }
        .onAppear {
            voiceService.requestPermissions()
        }
        .onDisappear {
            voiceService.stopListening()
            voiceService.stopSpeaking()
            stopWaveformAnimation()
        }
        .alert(isPresented: .constant(voiceService.error != nil)) {
            Alert(
                title: Text("Error"),
                message: Text(voiceService.error?.localizedDescription ?? "Unknown error"),
                dismissButton: .default(Text("OK")) {
                    voiceService.error = nil
                }
            )
        }
    }
    
    // MARK: - Waveform Animation
    private func startWaveformAnimation() {
        animationTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
            for index in 0..<waveformAmplitudes.count {
                waveformAmplitudes[index] = CGFloat.random(in: 0.2...1.0)
            }
        }
    }
    
    private func stopWaveformAnimation() {
        animationTimer?.invalidate()
        animationTimer = nil
        waveformAmplitudes = Array(repeating: 0.3, count: 50)
    }
}

// MARK: - Example Command View
struct ExampleCommand: View {
    let icon: String
    let text: String
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.body)
                .foregroundColor(Color(hex: "2563EB"))
                .frame(width: 24)
            
            Text(text)
                .font(.subheadline)
                .foregroundColor(.primary)
        }
    }
}

// MARK: - Color Extension
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }
        
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue:  Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

// MARK: - Preview
struct VoiceAssistantView_Previews: PreviewProvider {
    static var previews: some View {
        VoiceAssistantView()
    }
}

'''
import SwiftUI
import MapboxMaps
import CoreLocation

// MARK: - Model

struct LaneGuidance {
    let lanes: [Lane]
    let activeLaneIndex: Int
}

struct Lane {
    let indications: [String]
    let isValid: Bool
}

// MARK: - API Service

class APIService {
    static let shared = APIService()
    private init() {}

    func fetchTurnPreviewData(completion: @escaping (Result<LaneGuidance, Error>) -> Void) {
        // Simulate a network request
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            // In a real application, you would make a network request here.
            // For this example, we'll return mock data.
            let mockData = LaneGuidance(
                lanes: [
                    Lane(indications: ["straight"], isValid: true),
                    Lane(indications: ["straight", "right"], isValid: true),
                    Lane(indications: ["right"], isValid: false),
                ],
                activeLaneIndex: 1
            )
            completion(.success(mockData))
        }
    }
}

// MARK: - ViewModel

class TurnPreviewViewModel: ObservableObject {
    @Published var laneGuidance: LaneGuidance?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var camera: CameraOptions

    private let locationManager = CLLocationManager()

    init() {
        self.camera = CameraOptions(center: CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194), zoom: 15, pitch: 45)
        setupLocationManager()
    }

    private func setupLocationManager() {
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
    }

    func loadTurnPreview() {
        isLoading = true
        errorMessage = nil

        APIService.shared.fetchTurnPreviewData { [weak self] result in
            DispatchQueue.main.async {
                self?.isLoading = false
                switch result {
                case .success(let laneGuidance):
                    self?.laneGuidance = laneGuidance
                    // Animate camera to a new position based on the turn preview data
                    self?.updateCamera(to: CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194)) // Example coordinate
                case .failure(let error):
                    self?.errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func updateCamera(to coordinate: CLLocationCoordinate2D) {
        withAnimation(.easeIn(duration: 1.0)) {
            self.camera.center = coordinate
            self.camera.zoom = 16
            self.camera.pitch = 60
        }
    }
}

// MARK: - View

struct TurnPreviewView: View {
    @StateObject private var viewModel = TurnPreviewViewModel()
    @State private var showingDetailsSheet = false

    var body: some View {
        ZStack {
            Map(camera: $viewModel.camera)
                .ignoresSafeArea()

            VStack {
                if viewModel.isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle())
                        .scaleEffect(1.5)
                } else if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.red)
                        .cornerRadius(10)
                } else if let laneGuidance = viewModel.laneGuidance {
                    LaneGuidanceView(laneGuidance: laneGuidance)
                        .accessibilityElement(children: .contain)
                        .accessibilityLabel("Lane guidance preview")
                }

                Spacer()

                HStack {
                    // Details Button
                    Button(action: {
                        showingDetailsSheet = true
                    }) {
                        Image(systemName: "info.circle.fill")
                            .font(.title)
                            .padding()
                            .background(Color(hex: "#2563EB"))
                            .foregroundColor(.white)
                            .clipShape(Circle())
                            .shadow(radius: 10)
                            .accessibilityLabel("Show intersection details")
                    }
                    .padding(.leading)

                    Spacer()

                    // Recenter/Reload Button
                    Button(action: {
                        viewModel.loadTurnPreview()
                    }) {
                        Image(systemName: "arrow.triangle.turn.up.right.diamond.fill")
                            .font(.title)
                            .padding()
                            .background(Color(hex: "#2563EB"))
                            .foregroundColor(.white)
                            .clipShape(Circle())
                            .shadow(radius: 10)
                            .accessibilityLabel("Reload turn preview")
                    }
                    .padding(.trailing)
                }
            }
        }
        .onAppear {
            viewModel.loadTurnPreview()
        }
        .navigationTitle("Turn Preview")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingDetailsSheet) {
            IntersectionDetailsSheet()
        }
        .environment(\.dynamicTypeSize, .large) // Example of Dynamic Type support
    }
}

struct IntersectionDetailsSheet: View {
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Intersection Details")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .foregroundColor(Color(hex: "#2563EB"))

                Text("This is a complex interchange on Highway 101. Please pay close attention to the lane guidance and speed limits.")
                    .font(.body)
                    .accessibilityLabel("Intersection description: complex interchange on Highway 101.")

                HStack {
                    Image(systemName: "speedometer")
                    Text("Current Speed Limit: 65 mph")
                        .accessibilityValue("65 miles per hour")
                }

                HStack {
                    Image(systemName: "signpost.right.fill")
                    Text("Next Exit: Downtown San Francisco (1.2 mi)")
                }

                Spacer()

                Button("Dismiss") {
                    dismiss()
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color(hex: "#2563EB"))
                .foregroundColor(.white)
                .cornerRadius(10)
                .accessibilityLabel("Dismiss details sheet")
            }
            .padding()
            .navigationTitle("Details")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct LaneGuidanceView: View {
    let laneGuidance: LaneGuidance

    var body: some View {
        HStack(spacing: 10) {
            ForEach(0..<laneGuidance.lanes.count, id: \.self) { index in
                LaneView(lane: laneGuidance.lanes[index], isActive: index == laneGuidance.activeLaneIndex)
                    .accessibilityLabel("Lane \(index + 1)")
                    .accessibilityValue(laneGuidance.lanes[index].indications.joined(separator: " and ") + (index == laneGuidance.activeLaneIndex ? ", active lane" : ""))
            }
        }
        .padding()
        .background(Color.black.opacity(0.7))
        .cornerRadius(15)
        .padding(.horizontal)
    }
}

struct LaneView: View {
    let lane: Lane
    let isActive: Bool

    var body: some View {
        VStack(spacing: 5) {
            ForEach(lane.indications, id: \.self) { indication in
                Image(systemName: indicationToSystemName(indication))
                    .font(.largeTitle)
                    .foregroundColor(isActive ? .white : .gray)
                    .accessibilityHidden(true) // The parent view handles the label
            }
        }
        .padding(10)
        .background(isActive ? Color(hex: "#2563EB").opacity(0.9) : Color.gray.opacity(0.5))
        .cornerRadius(10)
        .opacity(lane.isValid ? 1.0 : 0.5)
    }

    private func indicationToSystemName(_ indication: String) -> String {
        switch indication {
        case "straight":
            return "arrow.up"
        case "right":
            return "arrow.right"
        case "left":
            return "arrow.left"
        default:
            return "questionmark"
        }
    }
}

// MARK: - Extensions

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
            (a, r, g, b) = (255, int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(.sRGB, red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255, opacity: Double(a) / 255)
    }
}

// MARK: - Previews

struct TurnPreviewView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            TurnPreviewView()
        }
    }
}
    }
}

struct LaneGuidanceView: View {
    let laneGuidance: LaneGuidance

    var body: some View {
        HStack(spacing: 10) {
            ForEach(0..<laneGuidance.lanes.count, id: \.self) { index in
                LaneView(lane: laneGuidance.lanes[index], isActive: index == laneGuidance.activeLaneIndex)
            }
        }
        .padding()
        .background(Color.black.opacity(0.7))
        .cornerRadius(15)
        .padding(.horizontal)
    }
}

struct LaneView: View {
    let lane: Lane
    let isActive: Bool

    var body: some View {
        VStack(spacing: 5) {
            ForEach(lane.indications, id: \.self) { indication in
                Image(systemName: indicationToSystemName(indication))
                    .font(.largeTitle)
                    .foregroundColor(isActive ? .white : .gray)
            }
        }
        .padding(10)
        .background(isActive ? Color(hex: "#2563EB").opacity(0.9) : Color.gray.opacity(0.5))
        .cornerRadius(10)
        .opacity(lane.isValid ? 1.0 : 0.5)
    }

    private func indicationToSystemName(_ indication: String) -> String {
        switch indication {
        case "straight":
            return "arrow.up"
        case "right":
            return "arrow.right"
        case "left":
            return "arrow.left"
        default:
            return "questionmark"
        }
    }
}

// MARK: - Extensions

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
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(.sRGB, red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255, opacity: Double(a) / 255)
    }
}

// MARK: - Previews

struct TurnPreviewView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            TurnPreviewView()
        }
    }
}
'''

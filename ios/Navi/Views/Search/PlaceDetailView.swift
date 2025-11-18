import SwiftUI
import MapKit

// MARK: - Place Detail View
struct PlaceDetailView: View {
    let placeId: String
    @StateObject private var viewModel = PlaceDetailViewModel()
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        ZStack {
            switch viewModel.state {
            case .loading:
                LoadingStateView()
            case .empty:
                EmptyStateView()
            case .error(let message):
                ErrorStateView(message: message) {
                    viewModel.loadPlace(id: placeId)
                }
            case .success(let place):
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        // Photo Gallery
                        if !place.photos.isEmpty {
                            TabView {
                                ForEach(place.photos, id: \.self) { photo in
                                    AsyncImage(url: URL(string: photo)) { image in
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                    } placeholder: {
                                        Rectangle()
                                            .fill(Color.gray.opacity(0.2))
                                    }
                                }
                            }
                            .frame(height: 300)
                            .tabViewStyle(.page)
                        }
                        
                        VStack(alignment: .leading, spacing: 16) {
                            // Title & Category
                            VStack(alignment: .leading, spacing: 8) {
                                Text(place.name)
                                    .font(.title)
                                    .fontWeight(.bold)
                                    .accessibilityLabel("Place name: \(place.name)")
                                
                                Text(place.category.capitalized)
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            
                            // Rating & Price
                            HStack(spacing: 16) {
                                HStack(spacing: 4) {
                                    Image(systemName: "star.fill")
                                        .foregroundColor(.yellow)
                                    Text(String(format: "%.1f", place.rating))
                                        .fontWeight(.semibold)
                                    Text("(\(place.reviewCount))")
                                        .foregroundColor(.secondary)
                                }
                                
                                if place.priceLevel > 0 {
                                    Text(String(repeating: "$", count: place.priceLevel))
                                        .foregroundColor(.secondary)
                                }
                                
                                Spacer()
                                
                                if place.openNow {
                                    Text("Open Now")
                                        .font(.caption)
                                        .fontWeight(.semibold)
                                        .foregroundColor(.green)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color.green.opacity(0.1))
                                        .cornerRadius(4)
                                }
                            }
                            
                            Divider()
                            
                            // Address
                            HStack(alignment: .top, spacing: 12) {
                                Image(systemName: "mappin.circle.fill")
                                    .foregroundColor(Color(hex: "#2563EB"))
                                    .font(.title3)
                                
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Address")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                    Text(place.address)
                                        .font(.body)
                                }
                                
                                Spacer()
                                
                                Button(action: {
                                    // Open in Maps
                                }) {
                                    Image(systemName: "arrow.right.circle.fill")
                                        .foregroundColor(Color(hex: "#2563EB"))
                                        .font(.title3)
                                }
                            }
                            
                            Divider()
                            
                            // Contact Info
                            if let phone = place.phone {
                                Button(action: {
                                    if let url = URL(string: "tel://\(phone)") {
                                        UIApplication.shared.open(url)
                                    }
                                }) {
                                    HStack(spacing: 12) {
                                        Image(systemName: "phone.fill")
                                            .foregroundColor(Color(hex: "#2563EB"))
                                            .font(.title3)
                                        
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text("Phone")
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                            Text(phone)
                                                .font(.body)
                                                .foregroundColor(.primary)
                                        }
                                        
                                        Spacer()
                                    }
                                }
                                
                                Divider()
                            }
                            
                            if let website = place.website {
                                Button(action: {
                                    if let url = URL(string: website) {
                                        UIApplication.shared.open(url)
                                    }
                                }) {
                                    HStack(spacing: 12) {
                                        Image(systemName: "globe")
                                            .foregroundColor(Color(hex: "#2563EB"))
                                            .font(.title3)
                                        
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text("Website")
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                            Text(website)
                                                .font(.body)
                                                .foregroundColor(.primary)
                                                .lineLimit(1)
                                        }
                                        
                                        Spacer()
                                    }
                                }
                                
                                Divider()
                            }
                            
                            // Hours
                            if !place.hours.isEmpty {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack(spacing: 12) {
                                        Image(systemName: "clock.fill")
                                            .foregroundColor(Color(hex: "#2563EB"))
                                            .font(.title3)
                                        Text("Hours")
                                            .font(.headline)
                                    }
                                    
                                    ForEach(place.hours.sorted(by: { $0.key < $1.key }), id: \.key) { day, hours in
                                        HStack {
                                            Text(day.capitalized)
                                                .foregroundColor(.secondary)
                                            Spacer()
                                            Text(hours)
                                        }
                                        .font(.subheadline)
                                    }
                                }
                                
                                Divider()
                            }
                            
                            // Amenities
                            if !place.amenities.isEmpty {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text("Amenities")
                                        .font(.headline)
                                    
                                    FlowLayout(spacing: 8) {
                                        ForEach(place.amenities, id: \.self) { amenity in
                                            Text(amenity.replacingOccurrences(of: "_", with: " ").capitalized)
                                                .font(.caption)
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 6)
                                                .background(Color(hex: "#2563EB").opacity(0.1))
                                                .foregroundColor(Color(hex: "#2563EB"))
                                                .cornerRadius(16)
                                        }
                                    }
                                }
                            }
                        }
                        .padding()
                    }
                }
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(action: {
                            viewModel.toggleFavorite(placeId: placeId)
                        }) {
                            Image(systemName: viewModel.isFavorite ? "heart.fill" : "heart")
                                .foregroundColor(viewModel.isFavorite ? .red : .gray)
                        }
                    }
                }
            }
        }
        .onAppear {
            viewModel.loadPlace(id: placeId)
        }
    }
}

// MARK: - View Model
@MainActor
class PlaceDetailViewModel: ObservableObject {
    @Published var state: ViewState<Place> = .loading
    @Published var isFavorite = false
    
    func loadPlace(id: String) {
        state = .loading
        
        Task {
            do {
                try await Task.sleep(nanoseconds: 1_000_000_000)
                
                // Mock data
                let place = Place(
                    id: id,
                    name: "Blue Bottle Coffee",
                    category: "cafe",
                    address: "123 Main St, San Francisco, CA 94102",
                    rating: 4.5,
                    reviewCount: 1234,
                    priceLevel: 2,
                    photos: [
                        "https://picsum.photos/800/600?random=1",
                        "https://picsum.photos/800/600?random=2"
                    ],
                    phone: "+1 (415) 555-1234",
                    website: "https://bluebottlecoffee.com",
                    hours: [
                        "monday": "07:00-19:00",
                        "tuesday": "07:00-19:00",
                        "wednesday": "07:00-19:00",
                        "thursday": "07:00-19:00",
                        "friday": "07:00-19:00",
                        "saturday": "08:00-20:00",
                        "sunday": "08:00-18:00"
                    ],
                    openNow: true,
                    amenities: ["wifi", "outdoor_seating", "wheelchair_accessible"]
                )
                
                state = .success(place)
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }
    
    func toggleFavorite(placeId: String) {
        isFavorite.toggle()
        // API call: POST /places/{id}/favorite or DELETE
    }
}

// MARK: - Models
struct Place {
    let id: String
    let name: String
    let category: String
    let address: String
    let rating: Double
    let reviewCount: Int
    let priceLevel: Int
    let photos: [String]
    let phone: String?
    let website: String?
    let hours: [String: String]
    let openNow: Bool
    let amenities: [String]
}

enum ViewState<T> {
    case loading
    case empty
    case error(String)
    case success(T)
}

// MARK: - Supporting Views
struct LoadingStateView: View {
    var body: some View {
        VStack {
            ProgressView()
            Text("Loading...")
                .foregroundColor(.secondary)
        }
    }
}

struct EmptyStateView: View {
    var body: some View {
        VStack {
            Image(systemName: "mappin.slash")
                .font(.system(size: 60))
                .foregroundColor(.gray)
            Text("No place found")
                .foregroundColor(.secondary)
        }
    }
}

struct ErrorStateView: View {
    let message: String
    let retry: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 60))
                .foregroundColor(.red)
            Text(message)
                .foregroundColor(.secondary)
            Button("Retry", action: retry)
                .buttonStyle(.borderedProminent)
                .tint(Color(hex: "#2563EB"))
        }
        .padding()
    }
}

struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = FlowResult(
            in: proposal.replacingUnspecifiedDimensions().width,
            subviews: subviews,
            spacing: spacing
        )
        return result.size
    }
    
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = FlowResult(
            in: bounds.width,
            subviews: subviews,
            spacing: spacing
        )
        for (index, subview) in subviews.enumerated() {
            subview.place(at: CGPoint(x: bounds.minX + result.frames[index].minX, y: bounds.minY + result.frames[index].minY), proposal: .unspecified)
        }
    }
    
    struct FlowResult {
        var size: CGSize
        var frames: [CGRect]
        
        init(in maxWidth: CGFloat, subviews: Subviews, spacing: CGFloat) {
            var frames: [CGRect] = []
            var currentX: CGFloat = 0
            var currentY: CGFloat = 0
            var lineHeight: CGFloat = 0
            
            for subview in subviews {
                let size = subview.sizeThatFits(.unspecified)
                
                if currentX + size.width > maxWidth && currentX > 0 {
                    currentX = 0
                    currentY += lineHeight + spacing
                    lineHeight = 0
                }
                
                frames.append(CGRect(x: currentX, y: currentY, width: size.width, height: size.height))
                lineHeight = max(lineHeight, size.height)
                currentX += size.width + spacing
            }
            
            self.frames = frames
            self.size = CGSize(width: maxWidth, height: currentY + lineHeight)
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
        case 3: (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default: (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(.sRGB, red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255, opacity: Double(a) / 255)
    }
}

// MARK: - Preview
#Preview {
    NavigationStack {
        PlaceDetailView(placeId: "place_123")
    }
}

# Navi Phase 1 Integration Guide

## 🎯 Overview

This guide shows how to wire up the Phase 1 core services to the existing UI screens to make the app functional.

---

## 📱 iOS Integration

### **1. Update MapView to Use Real Mapbox**

**File:** `ios/Navi/Views/MapView.swift`

```swift
import SwiftUI
import MapboxMaps

struct MapView: View {
    @StateObject private var viewModel = MapViewModel()
    @State private var showVoiceAssistant = false
    @State private var showSearch = false
    
    var body: some View {
        ZStack {
            // Mapbox Map
            MapboxMapView(viewModel: viewModel)
                .edgesIgnoringSafeArea(.all)
            
            VStack {
                // Top bar with search
                HStack {
                    Button(action: { showSearch = true }) {
                        HStack {
                            Image(systemName: "magnifyingglass")
                            Text("Search places...")
                                .foregroundColor(.gray)
                        }
                        .padding()
                        .background(Color.white)
                        .cornerRadius(10)
                        .shadow(radius: 5)
                    }
                    
                    Spacer()
                    
                    // Voice assistant button
                    Button(action: { showVoiceAssistant = true }) {
                        Image(systemName: "mic.circle.fill")
                            .font(.title)
                            .foregroundColor(Color(hex: "2563EB"))
                    }
                }
                .padding()
                
                Spacer()
                
                // Navigation panel (when navigating)
                if viewModel.isNavigating {
                    NavigationPanel(viewModel: viewModel)
                }
                
                // Route preview (when route calculated)
                if viewModel.showRoutePreview, let route = viewModel.currentRoute {
                    RoutePreviewPanel(
                        route: route,
                        onStart: { viewModel.startNavigation() },
                        onCancel: { viewModel.currentRoute = nil }
                    )
                }
                
                // Bottom controls
                HStack {
                    // Center on location button
                    Button(action: { viewModel.centerOnUserLocation() }) {
                        Image(systemName: "location.fill")
                            .padding()
                            .background(Color.white)
                            .clipShape(Circle())
                            .shadow(radius: 5)
                    }
                    
                    Spacer()
                }
                .padding()
            }
        }
        .sheet(isPresented: $showVoiceAssistant) {
            VoiceAssistantView()
        }
        .sheet(isPresented: $showSearch) {
            SearchView(viewModel: viewModel)
        }
        .onAppear {
            viewModel.requestLocationPermission()
            viewModel.startLocationTracking()
        }
    }
}

// Mapbox Map SwiftUI Wrapper
struct MapboxMapView: UIViewRepresentable {
    @ObservedObject var viewModel: MapViewModel
    
    func makeUIView(context: Context) -> MapView {
        let mapView = MapboxManager.shared.createMapView(frame: .zero)
        return mapView
    }
    
    func updateUIView(_ uiView: MapView, context: Context) {
        // Update map based on viewModel state
        if let route = viewModel.currentRoute {
            MapboxManager.shared.displayRoute(route)
        }
    }
}

// Navigation Panel
struct NavigationPanel: View {
    @ObservedObject var viewModel: MapViewModel
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(viewModel.currentInstruction)
                .font(.headline)
            
            HStack {
                Label(viewModel.remainingDistance, systemImage: "arrow.right")
                Spacer()
                Label(viewModel.remainingTime, systemImage: "clock")
                Spacer()
                Label(viewModel.eta, systemImage: "flag.fill")
            }
            .font(.subheadline)
            
            Button(action: { viewModel.stopNavigation() }) {
                Text("Stop Navigation")
                    .foregroundColor(.red)
            }
        }
        .padding()
        .background(Color.white)
        .cornerRadius(10)
        .shadow(radius: 5)
        .padding()
    }
}

// Route Preview Panel
struct RoutePreviewPanel: View {
    let route: Route
    let onStart: () -> Void
    let onCancel: () -> Void
    
    var body: some View {
        VStack {
            HStack {
                VStack(alignment: .leading) {
                    Text("\(Int(route.distance / 1000)) km")
                        .font(.title2)
                        .bold()
                    Text("\(Int(route.duration / 60)) min")
                        .font(.subheadline)
                }
                
                Spacer()
                
                Button(action: onStart) {
                    Text("Start")
                        .bold()
                        .foregroundColor(.white)
                        .padding()
                        .background(Color(hex: "2563EB"))
                        .cornerRadius(10)
                }
                
                Button(action: onCancel) {
                    Image(systemName: "xmark")
                        .foregroundColor(.gray)
                }
            }
        }
        .padding()
        .background(Color.white)
        .cornerRadius(10)
        .shadow(radius: 5)
        .padding()
    }
}
```

### **2. Update SearchView to Use SearchViewModel**

**File:** `ios/Navi/Views/SearchView.swift`

```swift
import SwiftUI

struct SearchView: View {
    @StateObject private var searchViewModel = SearchViewModel()
    @ObservedObject var mapViewModel: MapViewModel
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            VStack {
                // Search bar
                TextField("Search places...", text: $searchViewModel.searchQuery)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .padding()
                
                // Categories
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(PlaceCategory.allCases, id: \.self) { category in
                            CategoryButton(
                                category: category,
                                isSelected: searchViewModel.selectedCategory == category,
                                action: { searchViewModel.searchByCategory(category) }
                            )
                        }
                    }
                    .padding(.horizontal)
                }
                
                // Search results
                if searchViewModel.isSearching {
                    ProgressView()
                        .padding()
                } else if !searchViewModel.searchResults.isEmpty {
                    List(searchViewModel.searchResults, id: \.id) { place in
                        PlaceRow(place: place)
                            .onTapGesture {
                                mapViewModel.selectPlace(place)
                                mapViewModel.calculateRouteToPlace(place)
                                dismiss()
                            }
                    }
                } else if !searchViewModel.recentSearches.isEmpty {
                    VStack(alignment: .leading) {
                        Text("Recent Searches")
                            .font(.headline)
                            .padding()
                        
                        List(searchViewModel.recentSearches, id: \.self) { query in
                            Text(query)
                                .onTapGesture {
                                    searchViewModel.searchQuery = query
                                }
                        }
                    }
                }
            }
            .navigationTitle("Search")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct CategoryButton: View {
    let category: PlaceCategory
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: category.icon)
                Text(category.rawValue)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(isSelected ? Color(hex: "2563EB") : Color.gray.opacity(0.2))
            .foregroundColor(isSelected ? .white : .black)
            .cornerRadius(20)
        }
    }
}

struct PlaceRow: View {
    let place: Place
    
    var body: some View {
        VStack(alignment: .leading) {
            Text(place.name)
                .font(.headline)
            Text(place.address)
                .font(.subheadline)
                .foregroundColor(.gray)
        }
        .padding(.vertical, 4)
    }
}
```

### **3. Wire Up Voice Commands**

**File:** `ios/Navi/Views/MapView.swift` (add to MapView)

```swift
.onReceive(NotificationCenter.default.publisher(for: .voiceCommandNavigate)) { notification in
    if let place = notification.userInfo?["place"] as? Place {
        viewModel.calculateRouteToPlace(place)
    }
}
.onReceive(NotificationCenter.default.publisher(for: .voiceCommandSearch)) { notification in
    if let query = notification.userInfo?["query"] as? String {
        showSearch = true
        // Pass query to search view
    }
}
```

---

## 🤖 Android Integration

### **1. Update MapScreen to Use MapViewModel**

**File:** `android/app/src/main/java/com/navi/presentation/screens/MapScreen.kt`

```kotlin
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel()
) {
    val userLocation by viewModel.userLocation.collectAsState()
    val isNavigating by viewModel.isNavigating.collectAsState()
    val showRoutePreview by viewModel.showRoutePreview.collectAsState()
    val currentRoute by viewModel.currentRoute.collectAsState()
    
    var showVoiceAssistant by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Mapbox Map
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    mapboxManager.setupMap(this)
                    viewModel.startLocationTracking()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Top bar
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                onSearchClick = { showSearch = true },
                onVoiceClick = { showVoiceAssistant = true }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Navigation panel
            if (isNavigating) {
                NavigationPanel(viewModel = viewModel)
            }
            
            // Route preview
            if (showRoutePreview && currentRoute != null) {
                RoutePreviewPanel(
                    route = currentRoute!!,
                    onStart = { viewModel.startNavigation() },
                    onCancel = { viewModel.currentRoute.value = null }
                )
            }
            
            // Bottom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FloatingActionButton(
                    onClick = { /* viewModel.centerOnUserLocation() */ }
                ) {
                    Icon(Icons.Default.MyLocation, "Center")
                }
            }
        }
    }
    
    // Voice Assistant Dialog
    if (showVoiceAssistant) {
        VoiceAssistantScreen(
            onDismiss = { showVoiceAssistant = false }
        )
    }
    
    // Search Dialog
    if (showSearch) {
        SearchScreen(
            onDismiss = { showSearch = false },
            onPlaceSelected = { place ->
                viewModel.calculateRouteToPlace(place)
                showSearch = false
            }
        )
    }
}

@Composable
fun TopBar(
    onSearchClick: () -> Unit,
    onVoiceClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            onClick = onSearchClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 5.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, "Search")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search places...", color = Color.Gray)
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        IconButton(onClick = onVoiceClick) {
            Icon(
                Icons.Default.Mic,
                "Voice",
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun NavigationPanel(viewModel: MapViewModel) {
    val instruction by viewModel.currentInstruction.collectAsState()
    val distance by viewModel.remainingDistance.collectAsState()
    val time by viewModel.remainingTime.collectAsState()
    val eta by viewModel.eta.collectAsState()
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 5.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(instruction, style = MaterialTheme.typography.headlineSmall)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(distance)
                Text(time)
                Text(eta)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { viewModel.stopNavigation() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Stop Navigation")
            }
        }
    }
}

@Composable
fun RoutePreviewPanel(
    route: Route,
    onStart: () -> Unit,
    onCancel: () -> Void
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${(route.distance / 1000).toInt()} km", style = MaterialTheme.typography.titleLarge)
                Text("${(route.duration / 60).toInt()} min", style = MaterialTheme.typography.bodyMedium)
            }
            
            Button(onClick = onStart) {
                Text("Start")
            }
            
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, "Cancel")
            }
        }
    }
}
```

### **2. Update SearchScreen to Use SearchViewModel**

**File:** `android/app/src/main/java/com/navi/presentation/screens/SearchScreen.kt`

```kotlin
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onPlaceSelected: (Place) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        TextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search places...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") }
        )
        
        // Categories
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            items(PlaceCategory.all()) { category ->
                CategoryChip(
                    category = category,
                    onClick = { viewModel.searchByCategory(category) }
                )
            }
        }
        
        // Results
        when {
            isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            searchResults.isNotEmpty() -> {
                LazyColumn {
                    items(searchResults) { place ->
                        PlaceListItem(
                            place = place,
                            onClick = { onPlaceSelected(place) }
                        )
                    }
                }
            }
            recentSearches.isNotEmpty() -> {
                Column {
                    Text(
                        "Recent Searches",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(16.dp)
                    )
                    LazyColumn {
                        items(recentSearches) { query ->
                            Text(
                                query,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateSearchQuery(query) }
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryChip(category: PlaceCategory, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(category.displayName) },
        leadingIcon = { Icon(Icons.Default.Place, category.displayName) }
    )
}

@Composable
fun PlaceListItem(place: Place, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(place.name) },
        supportingContent = { Text(place.address) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
```

---

## 🔧 Configuration

### **iOS - Add Mapbox Token**

1. Get token from https://account.mapbox.com/access-tokens/
2. Update in `MapboxManager.swift`:
```swift
private let accessToken = "pk.YOUR_ACTUAL_MAPBOX_TOKEN"
```

3. Update in `SearchViewModel.swift`:
```swift
private let accessToken = "pk.YOUR_ACTUAL_MAPBOX_TOKEN"
```

### **Android - Add Mapbox Token**

1. Get token from https://account.mapbox.com/access-tokens/
2. Update in `MapboxManager.kt`:
```kotlin
private val accessToken = "pk.YOUR_ACTUAL_MAPBOX_TOKEN"
```

3. Update in `SearchViewModel.kt`:
```kotlin
private val accessToken = "pk.YOUR_ACTUAL_MAPBOX_TOKEN"
```

---

## ✅ Testing Checklist

### **Basic Flow**
- [ ] App launches and requests location permission
- [ ] Map renders with user location
- [ ] Can search for places
- [ ] Search results appear
- [ ] Can select a place
- [ ] Route calculates successfully
- [ ] Route displays on map
- [ ] Can start navigation
- [ ] Turn-by-turn instructions appear
- [ ] Voice guidance announces turns
- [ ] Can stop navigation

### **Voice Assistant**
- [ ] Voice button opens assistant
- [ ] Can say "Navigate to Starbucks"
- [ ] Route calculates from voice command
- [ ] Can say "Find gas stations nearby"
- [ ] Search results appear from voice

### **Data Persistence**
- [ ] Can save favorite places
- [ ] Favorites persist after app restart
- [ ] Recent searches show up
- [ ] Visit count increments

---

## 🚀 Next Steps

1. **Test on real devices** - Simulators don't have GPS
2. **Add error handling** - Network failures, no route found, etc.
3. **Optimize performance** - Reduce API calls, cache results
4. **Add loading states** - Show spinners during route calculation
5. **Polish UI** - Animations, transitions, better layouts

---

**Phase 1 is now complete and ready for testing!** 🎉

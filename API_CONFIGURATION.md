# Navi Mobile Apps - API Configuration Guide

This guide explains how to configure the iOS and Android apps to connect to the production backend API.

---

## 🔌 Backend API Information

**Production API URL:** `https://your-api-url.com` (Update after deployment)  
**API Version:** v1  
**Base Endpoint:** `/api/v1`

**GitHub Repository:** https://github.com/kimhons/navi-backend

---

## 📱 iOS Configuration

### 1. Update API Constants

Edit `ios/Navi/Config/APIConfig.swift`:

```swift
import Foundation

struct APIConfig {
    // MARK: - Base URL
    static let baseURL = "https://your-api-url.com/api/v1"
    
    // MARK: - Endpoints
    struct Auth {
        static let signup = "/auth/signup"
        static let login = "/auth/login"
        static let logout = "/auth/logout"
        static let refresh = "/auth/refresh"
        static let verifyEmail = "/auth/verify-email"
        static let forgotPassword = "/auth/forgot-password"
        static let resetPassword = "/auth/reset-password"
        static let me = "/auth/me"
    }
    
    struct Users {
        static let profile = "/users/profile"
        static let preferences = "/users/preferences"
        static let stats = "/users/stats"
    }
    
    struct Routes {
        static let base = "/routes"
        static let optimize = "/routes/optimize"
        static func share(id: String) -> String { "/routes/\(id)/share" }
    }
    
    struct Places {
        static let search = "/places/search"
        static let nearby = "/places/nearby"
        static func details(id: String) -> String { "/places/\(id)" }
        static func reviews(id: String) -> String { "/places/\(id)/reviews" }
        static func save(id: String) -> String { "/places/\(id)/save" }
    }
    
    struct Trips {
        static let base = "/trips"
        static func export(id: String) -> String { "/trips/\(id)/export" }
    }
    
    struct Social {
        static let friends = "/social/friends"
        static let friendRequest = "/social/friends/request"
        static func acceptFriend(id: String) -> String { "/social/friends/accept/\(id)" }
        static let messages = "/social/messages"
        static let groups = "/social/groups"
    }
    
    struct Maps {
        static let offlineMaps = "/maps/offline"
        static let downloadMap = "/maps/offline/download"
        static let safetyAlerts = "/maps/safety-alerts"
    }
    
    // MARK: - Headers
    static func authHeader(token: String) -> [String: String] {
        return ["Authorization": "Bearer \(token)"]
    }
    
    static let defaultHeaders: [String: String] = [
        "Content-Type": "application/json",
        "Accept": "application/json"
    ]
}
```

### 2. Update Mapbox Token

Edit `ios/Navi/Config/MapboxConfig.swift`:

```swift
struct MapboxConfig {
    static let accessToken = "pk.your-production-mapbox-token"
    static let styleURL = "mapbox://styles/mapbox/streets-v12"
}
```

### 3. Create Network Manager

Edit `ios/Navi/Services/NetworkManager.swift`:

```swift
import Foundation

class NetworkManager {
    static let shared = NetworkManager()
    
    private init() {}
    
    func request<T: Decodable>(
        endpoint: String,
        method: String = "GET",
        body: [String: Any]? = nil,
        token: String? = nil
    ) async throws -> T {
        guard let url = URL(string: APIConfig.baseURL + endpoint) else {
            throw NetworkError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = method
        
        // Add headers
        APIConfig.defaultHeaders.forEach { request.addValue($0.value, forHTTPHeaderField: $0.key) }
        
        if let token = token {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        // Add body
        if let body = body {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.invalidResponse
        }
        
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(httpResponse.statusCode)
        }
        
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        
        return try decoder.decode(T.self, from: data)
    }
}

enum NetworkError: Error {
    case invalidURL
    case invalidResponse
    case httpError(Int)
}
```

---

## 🤖 Android Configuration

### 1. Update API Constants

Edit `android/app/src/main/java/com/navi/app/data/remote/ApiConfig.kt`:

```kotlin
package com.navi.app.data.remote

object ApiConfig {
    // Base URL
    const val BASE_URL = "https://your-api-url.com/api/v1/"
    
    // Endpoints
    object Auth {
        const val SIGNUP = "auth/signup"
        const val LOGIN = "auth/login"
        const val LOGOUT = "auth/logout"
        const val REFRESH = "auth/refresh"
        const val VERIFY_EMAIL = "auth/verify-email"
        const val FORGOT_PASSWORD = "auth/forgot-password"
        const val RESET_PASSWORD = "auth/reset-password"
        const val ME = "auth/me"
    }
    
    object Users {
        const val PROFILE = "users/profile"
        const val PREFERENCES = "users/preferences"
        const val STATS = "users/stats"
    }
    
    object Routes {
        const val BASE = "routes"
        const val OPTIMIZE = "routes/optimize"
        fun share(id: String) = "routes/$id/share"
    }
    
    object Places {
        const val SEARCH = "places/search"
        const val NEARBY = "places/nearby"
        fun details(id: String) = "places/$id"
        fun reviews(id: String) = "places/$id/reviews"
        fun save(id: String) = "places/$id/save"
    }
    
    object Trips {
        const val BASE = "trips"
        fun export(id: String) = "trips/$id/export"
    }
    
    object Social {
        const val FRIENDS = "social/friends"
        const val FRIEND_REQUEST = "social/friends/request"
        fun acceptFriend(id: String) = "social/friends/accept/$id"
        const val MESSAGES = "social/messages"
        const val GROUPS = "social/groups"
    }
    
    object Maps {
        const val OFFLINE_MAPS = "maps/offline"
        const val DOWNLOAD_MAP = "maps/offline/download"
        const val SAFETY_ALERTS = "maps/safety-alerts"
    }
}
```

### 2. Update Mapbox Token

Edit `android/app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="mapbox_access_token">pk.your-production-mapbox-token</string>
</resources>
```

### 3. Create Retrofit Service

Edit `android/app/src/main/java/com/navi/app/data/remote/ApiService.kt`:

```kotlin
package com.navi.app.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

interface ApiService {
    // Auth
    @POST(ApiConfig.Auth.SIGNUP)
    suspend fun signup(@Body request: SignupRequest): ApiResponse<AuthData>
    
    @POST(ApiConfig.Auth.LOGIN)
    suspend fun login(@Body request: LoginRequest): ApiResponse<AuthData>
    
    @POST(ApiConfig.Auth.LOGOUT)
    suspend fun logout(): ApiResponse<Unit>
    
    // Users
    @GET(ApiConfig.Users.PROFILE)
    suspend fun getProfile(): ApiResponse<User>
    
    @PUT(ApiConfig.Users.PROFILE)
    suspend fun updateProfile(@Body request: UpdateProfileRequest): ApiResponse<User>
    
    // Routes
    @GET(ApiConfig.Routes.BASE)
    suspend fun getRoutes(@QueryMap params: Map<String, String>): ApiResponse<RoutesData>
    
    @POST(ApiConfig.Routes.BASE)
    suspend fun createRoute(@Body request: CreateRouteRequest): ApiResponse<Route>
    
    // Places
    @GET(ApiConfig.Places.SEARCH)
    suspend fun searchPlaces(@QueryMap params: Map<String, String>): ApiResponse<PlacesData>
    
    @GET(ApiConfig.Places.NEARBY)
    suspend fun getNearbyPlaces(@QueryMap params: Map<String, String>): ApiResponse<PlacesData>
    
    // Trips
    @GET(ApiConfig.Trips.BASE)
    suspend fun getTrips(@QueryMap params: Map<String, String>): ApiResponse<TripsData>
    
    @POST(ApiConfig.Trips.BASE)
    suspend fun createTrip(@Body request: CreateTripRequest): ApiResponse<Trip>
    
    // Social
    @GET(ApiConfig.Social.FRIENDS)
    suspend fun getFriends(): ApiResponse<FriendsData>
    
    @POST(ApiConfig.Social.FRIEND_REQUEST)
    suspend fun sendFriendRequest(@Body request: FriendRequestRequest): ApiResponse<FriendRequest>
    
    // Maps
    @GET(ApiConfig.Maps.OFFLINE_MAPS)
    suspend fun getOfflineMaps(): ApiResponse<OfflineMapsData>
    
    @GET(ApiConfig.Maps.SAFETY_ALERTS)
    suspend fun getSafetyAlerts(@QueryMap params: Map<String, String>): ApiResponse<SafetyAlertsData>
    
    companion object {
        fun create(): ApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            
            return Retrofit.Builder()
                .baseUrl(ApiConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}

// Response models
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: String?
)
```

---

## 🔐 Authentication Flow

### iOS Example

```swift
class AuthService {
    func login(email: String, password: String) async throws -> AuthData {
        let body = ["email": email, "password": password]
        let response: ApiResponse<AuthData> = try await NetworkManager.shared.request(
            endpoint: APIConfig.Auth.login,
            method: "POST",
            body: body
        )
        
        // Save token
        KeychainManager.shared.save(token: response.data.token)
        
        return response.data
    }
}
```

### Android Example

```kotlin
class AuthRepository(private val apiService: ApiService) {
    suspend fun login(email: String, password: String): Result<AuthData> {
        return try {
            val request = LoginRequest(email, password)
            val response = apiService.login(request)
            
            if (response.success && response.data != null) {
                // Save token
                TokenManager.saveToken(response.data.token)
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## 📋 Testing Checklist

### Before Deployment

- [ ] Update `BASE_URL` in both iOS and Android
- [ ] Update Mapbox token in both apps
- [ ] Test authentication flow
- [ ] Test route creation
- [ ] Test place search
- [ ] Test trip tracking
- [ ] Test social features
- [ ] Test offline maps
- [ ] Test file uploads
- [ ] Verify error handling

### API Endpoints to Test

1. **Authentication**
   - Signup
   - Login
   - Logout
   - Refresh token

2. **User Management**
   - Get profile
   - Update profile
   - Update preferences

3. **Routes**
   - Create route
   - Get routes list
   - Optimize route

4. **Places**
   - Search places
   - Get nearby places
   - Add review

5. **Trips**
   - Create trip
   - Get trips list
   - Export trip

6. **Social**
   - Get friends
   - Send friend request
   - Send message

7. **Maps**
   - Get offline maps
   - Get safety alerts

---

## 🚀 Deployment Steps

### 1. Backend Deployment

Follow `DEPLOYMENT.md` in the backend repository to deploy the API.

### 2. Update Mobile Apps

```bash
# iOS
cd ios/Navi/Config
# Edit APIConfig.swift with production URL and Mapbox token

# Android
cd android/app/src/main/java/com/navi/app/data/remote
# Edit ApiConfig.kt with production URL
cd android/app/src/main/res/values
# Edit strings.xml with Mapbox token
```

### 3. Build and Test

```bash
# iOS
cd ios
xcodebuild clean build

# Android
cd android
./gradlew clean assembleRelease
```

### 4. Submit to App Stores

- iOS: TestFlight → App Store
- Android: Internal Testing → Production

---

## 📊 Monitoring

### API Health Check

```bash
curl https://your-api-url.com/health
```

### Test Authentication

```bash
curl -X POST https://your-api-url.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'
```

---

## 🔧 Troubleshooting

### Connection Refused

- Verify API URL is correct
- Check if backend is running
- Verify network connectivity

### 401 Unauthorized

- Check if token is valid
- Verify token is being sent in headers
- Check token expiration

### 404 Not Found

- Verify endpoint path is correct
- Check API version in URL
- Confirm backend route exists

### Timeout Errors

- Increase timeout in network configuration
- Check backend performance
- Verify database connection

---

## 📞 Support

**Backend Repository:** https://github.com/kimhons/navi-backend  
**Mobile App Repository:** https://github.com/kimhons/navi-app  
**Issues:** Create issue in respective repository

---

**Status:** ✅ Ready for Integration  
**Last Updated:** November 18, 2024

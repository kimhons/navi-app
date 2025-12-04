package com.navi.data.remote

import com.navi.domain.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    // Authentication
    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<User>
    
    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>
    
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>
    
    @POST("auth/forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): Response<Unit>
    
    @POST("auth/reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): Response<Unit>
    
    @POST("auth/verify-email")
    suspend fun verifyEmail(
        @Body request: VerifyEmailRequest
    ): Response<Unit>
    
    // User Profile
    @GET("user/profile")
    suspend fun getProfile(): Response<User>
    
    @PUT("user/profile")
    suspend fun updateProfile(
        @Body request: UpdateProfileRequest
    ): Response<User>
    
    @POST("user/profile/photo")
    suspend fun uploadProfilePhoto(
        @Body request: UploadPhotoRequest
    ): Response<User>
    
    // Places
    @GET("places/search")
    suspend fun searchPlaces(
        @Query("query") query: String,
        @Query("latitude") latitude: Double? = null,
        @Query("longitude") longitude: Double? = null,
        @Query("radius") radius: Double? = null
    ): Response<List<Place>>
    
    @GET("places/{placeId}")
    suspend fun getPlaceDetail(
        @Path("placeId") placeId: String
    ): Response<PlaceDetail>
    
    @GET("places/nearby")
    suspend fun getNearbyPlaces(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("category") category: String? = null,
        @Query("radius") radius: Double
    ): Response<List<Place>>
    
    @GET("places/categories")
    suspend fun getCategories(): Response<List<Category>>
    
    // Routes
    @POST("routes/calculate")
    suspend fun calculateRoute(
        @Body request: RouteRequest
    ): Response<Route>
    
    @GET("routes/alternatives")
    suspend fun getRouteAlternatives(
        @Query("routeId") routeId: String
    ): Response<List<RouteAlternative>>
    
    // Traffic
    @GET("traffic/incidents")
    suspend fun getTrafficIncidents(
        @Query("north") north: Double,
        @Query("south") south: Double,
        @Query("east") east: Double,
        @Query("west") west: Double
    ): Response<List<TrafficIncident>>
    
    // Saved Places
    @GET("user/saved-places")
    suspend fun getSavedPlaces(): Response<List<SavedPlace>>
    
    @POST("user/saved-places")
    suspend fun savePlace(
        @Body request: SavePlaceRequest
    ): Response<SavedPlace>
    
    @DELETE("user/saved-places/{id}")
    suspend fun deleteSavedPlace(
        @Path("id") id: String
    ): Response<Unit>
    
    // Collections
    @GET("user/collections")
    suspend fun getCollections(): Response<List<PlaceCollection>>
    
    @POST("user/collections")
    suspend fun createCollection(
        @Body request: CreateCollectionRequest
    ): Response<PlaceCollection>
    
    @DELETE("user/collections/{id}")
    suspend fun deleteCollection(
        @Path("id") id: String
    ): Response<Unit>
    
    // Saved Routes
    @GET("user/saved-routes")
    suspend fun getSavedRoutes(): Response<List<SavedRoute>>
    
    @POST("user/saved-routes")
    suspend fun saveRoute(
        @Body request: SaveRouteRequest
    ): Response<SavedRoute>
    
    @DELETE("user/saved-routes/{id}")
    suspend fun deleteSavedRoute(
        @Path("id") id: String
    ): Response<Unit>
    
    // Friends & Social
    @GET("user/friends")
    suspend fun getFriends(): Response<List<Friend>>
    
    @POST("user/friends/request")
    suspend fun sendFriendRequest(
        @Body request: FriendRequestRequest
    ): Response<Unit>
    
    @POST("user/friends/accept")
    suspend fun acceptFriendRequest(
        @Body request: AcceptFriendRequest
    ): Response<Unit>
    
    @GET("user/groups")
    suspend fun getGroups(): Response<List<Group>>
    
    @POST("user/groups")
    suspend fun createGroup(
        @Body request: CreateGroupRequest
    ): Response<Group>
}

// Request/Response Models
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val user: User
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)

data class VerifyEmailRequest(
    val token: String
)

data class UpdateProfileRequest(
    val name: String? = null,
    val phone: String? = null,
    val preferences: UserPreferences? = null
)

data class UploadPhotoRequest(
    val photoBase64: String
)

data class RouteRequest(
    val origin: String,
    val destination: String,
    val waypoints: String? = null,
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false
)

data class SavePlaceRequest(
    val placeId: String,
    val collectionId: String? = null,
    val notes: String? = null
)

data class CreateCollectionRequest(
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = false
)

data class SaveRouteRequest(
    val name: String,
    val routeId: String,
    val notes: String? = null
)

data class FriendRequestRequest(
    val userId: String
)

data class AcceptFriendRequest(
    val requestId: String
)

data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val memberIds: List<String>
)

data class Category(
    val id: String,
    val name: String,
    val icon: String
)

data class Friend(
    val id: String,
    val user: User,
    val status: FriendStatus,
    val createdAt: java.util.Date
)

enum class FriendStatus {
    PENDING,
    ACCEPTED,
    BLOCKED
}

data class Group(
    val id: String,
    val name: String,
    val description: String?,
    val members: List<User>,
    val createdBy: String,
    val createdAt: java.util.Date
)

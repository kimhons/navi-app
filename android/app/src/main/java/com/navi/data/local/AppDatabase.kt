package com.navi.data.local

import android.content.Context
import androidx.room.*
import com.navi.domain.models.Place
import kotlinx.coroutines.flow.Flow
import java.util.*

// MARK: - Saved Place Entity

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val rating: Double? = null,
    val photoURL: String? = null,
    val isFavorite: Boolean = false,
    val savedAt: Long = System.currentTimeMillis(),
    val visitCount: Int = 0,
    val lastVisited: Long? = null
) {
    fun toPlace(): Place {
        return Place(
            id = id,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            category = category,
            rating = rating,
            photoURL = photoURL
        )
    }
    
    companion object {
        fun fromPlace(place: Place): SavedPlaceEntity {
            return SavedPlaceEntity(
                id = place.id,
                name = place.name,
                address = place.address,
                latitude = place.latitude,
                longitude = place.longitude,
                category = place.category,
                rating = place.rating,
                photoURL = place.photoURL
            )
        }
    }
}

// MARK: - Place DAO

@Dao
interface PlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY savedAt DESC")
    fun getAllPlaces(): Flow<List<SavedPlaceEntity>>
    
    @Query("SELECT * FROM saved_places WHERE id = :id")
    suspend fun getPlaceById(id: String): SavedPlaceEntity?
    
    @Query("SELECT * FROM saved_places WHERE isFavorite = 1 ORDER BY savedAt DESC")
    fun getFavoritePlaces(): Flow<List<SavedPlaceEntity>>
    
    @Query("SELECT * FROM saved_places WHERE lastVisited IS NOT NULL ORDER BY lastVisited DESC LIMIT :limit")
    fun getRecentPlaces(limit: Int = 10): Flow<List<SavedPlaceEntity>>
    
    @Query("SELECT * FROM saved_places WHERE name LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%'")
    suspend fun searchPlaces(query: String): List<SavedPlaceEntity>
    
    @Query("SELECT * FROM saved_places WHERE category = :category ORDER BY savedAt DESC")
    suspend fun getPlacesByCategory(category: String): List<SavedPlaceEntity>
    
    @Query("SELECT * FROM saved_places ORDER BY visitCount DESC LIMIT :limit")
    suspend fun getMostVisitedPlaces(limit: Int = 10): List<SavedPlaceEntity>
    
    @Query("SELECT COUNT(*) FROM saved_places")
    suspend fun getTotalPlacesCount(): Int
    
    @Query("SELECT COUNT(*) FROM saved_places WHERE isFavorite = 1")
    suspend fun getTotalFavoritesCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: SavedPlaceEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<SavedPlaceEntity>)
    
    @Update
    suspend fun updatePlace(place: SavedPlaceEntity)
    
    @Delete
    suspend fun deletePlace(place: SavedPlaceEntity)
    
    @Query("DELETE FROM saved_places")
    suspend fun deleteAllPlaces()
    
    @Query("UPDATE saved_places SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: String)
    
    @Query("UPDATE saved_places SET visitCount = visitCount + 1, lastVisited = :timestamp WHERE id = :id")
    suspend fun recordVisit(id: String, timestamp: Long = System.currentTimeMillis())
}

// MARK: - App Database

@Database(
    entities = [SavedPlaceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "navi_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// MARK: - Place Repository

class PlaceRepository(private val placeDao: PlaceDao) {
    
    // Flows for reactive updates
    val savedPlaces: Flow<List<Place>> = placeDao.getAllPlaces().map { entities ->
        entities.map { it.toPlace() }
    }
    
    val favoritePlaces: Flow<List<Place>> = placeDao.getFavoritePlaces().map { entities ->
        entities.map { it.toPlace() }
    }
    
    val recentPlaces: Flow<List<Place>> = placeDao.getRecentPlaces().map { entities ->
        entities.map { it.toPlace() }
    }
    
    // MARK: - Save Place
    
    suspend fun savePlace(place: Place) {
        val entity = SavedPlaceEntity.fromPlace(place)
        placeDao.insertPlace(entity)
    }
    
    suspend fun savePlaces(places: List<Place>) {
        val entities = places.map { SavedPlaceEntity.fromPlace(it) }
        placeDao.insertPlaces(entities)
    }
    
    // MARK: - Get Place
    
    suspend fun getPlaceById(id: String): Place? {
        return placeDao.getPlaceById(id)?.toPlace()
    }
    
    // MARK: - Delete Place
    
    suspend fun deletePlace(place: Place) {
        val entity = SavedPlaceEntity.fromPlace(place)
        placeDao.deletePlace(entity)
    }
    
    suspend fun deleteAllPlaces() {
        placeDao.deleteAllPlaces()
    }
    
    // MARK: - Favorite
    
    suspend fun toggleFavorite(place: Place) {
        // Check if place exists
        val existing = placeDao.getPlaceById(place.id)
        
        if (existing != null) {
            // Toggle existing
            placeDao.toggleFavorite(place.id)
        } else {
            // Save as new favorite
            val entity = SavedPlaceEntity.fromPlace(place).copy(isFavorite = true)
            placeDao.insertPlace(entity)
        }
    }
    
    suspend fun isFavorite(place: Place): Boolean {
        return placeDao.getPlaceById(place.id)?.isFavorite ?: false
    }
    
    // MARK: - Visit Tracking
    
    suspend fun recordVisit(place: Place) {
        val existing = placeDao.getPlaceById(place.id)
        
        if (existing != null) {
            // Update visit count
            placeDao.recordVisit(place.id)
        } else {
            // Save as new with visit
            val entity = SavedPlaceEntity.fromPlace(place).copy(
                visitCount = 1,
                lastVisited = System.currentTimeMillis()
            )
            placeDao.insertPlace(entity)
        }
    }
    
    // MARK: - Search
    
    suspend fun searchSavedPlaces(query: String): List<Place> {
        return placeDao.searchPlaces(query).map { it.toPlace() }
    }
    
    suspend fun getPlacesByCategory(category: String): List<Place> {
        return placeDao.getPlacesByCategory(category).map { it.toPlace() }
    }
    
    // MARK: - Statistics
    
    suspend fun getMostVisitedPlaces(limit: Int = 10): List<Place> {
        return placeDao.getMostVisitedPlaces(limit).map { it.toPlace() }
    }
    
    suspend fun getTotalSavedPlaces(): Int {
        return placeDao.getTotalPlacesCount()
    }
    
    suspend fun getTotalFavorites(): Int {
        return placeDao.getTotalFavoritesCount()
    }
}

// MARK: - Flow Extension

private fun <T, R> Flow<T>.map(transform: (T) -> R): Flow<R> {
    return kotlinx.coroutines.flow.map(transform)
}

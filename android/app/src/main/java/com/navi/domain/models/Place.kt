package com.navi.domain.models

import java.util.Date

data class Place(
    val id: String,
    val name: String,
    val address: String,
    val coordinate: Coordinate,
    val category: String,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val phoneNumber: String? = null,
    val website: String? = null,
    val hours: List<String>? = null,
    val photos: List<String>? = null,
    val priceLevel: Int? = null,
    val isOpen: Boolean? = null
)

data class Coordinate(
    val latitude: Double,
    val longitude: Double
)

data class PlaceDetail(
    val place: Place,
    val description: String? = null,
    val reviews: List<Review> = emptyList(),
    val amenities: List<String> = emptyList(),
    val accessibility: List<String> = emptyList(),
    val popularTimes: List<PopularTime>? = null
)

data class Review(
    val id: String,
    val authorName: String,
    val authorPhoto: String? = null,
    val rating: Double,
    val text: String,
    val time: Date,
    val helpful: Int = 0
)

data class PopularTime(
    val day: String,
    val hours: List<Int>
)

data class SavedPlace(
    val id: String,
    val place: Place,
    val userId: String,
    val collectionId: String? = null,
    val notes: String? = null,
    val savedAt: Date
)

data class PlaceCollection(
    val id: String,
    val name: String,
    val description: String? = null,
    val userId: String,
    val places: List<Place> = emptyList(),
    val isPublic: Boolean = false,
    val createdAt: Date
)

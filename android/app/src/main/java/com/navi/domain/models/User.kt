package com.navi.domain.models

import java.util.Date

data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val profileImageUrl: String? = null,
    val isPremium: Boolean = false,
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
    val createdAt: Date,
    val preferences: UserPreferences = UserPreferences()
)

enum class SubscriptionTier {
    FREE,
    PREMIUM,
    PRO
}

data class UserPreferences(
    val language: String = "en",
    val voiceGuidance: Boolean = true,
    val voiceLanguage: String = "en-US",
    val units: UnitSystem = UnitSystem.METRIC,
    val theme: AppTheme = AppTheme.SYSTEM,
    val mapStyle: MapStyle = MapStyle.STANDARD,
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
    val showTraffic: Boolean = true,
    val showSpeedLimits: Boolean = true,
    val speedWarningEnabled: Boolean = true,
    val offlineMapsEnabled: Boolean = true
)

enum class UnitSystem {
    METRIC,
    IMPERIAL
}

enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class MapStyle {
    STANDARD,
    SATELLITE,
    HYBRID,
    TERRAIN
}

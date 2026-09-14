package com.georeminder.app.data.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TriggerType {
    NOTIFICATION,
    ALARM
}

data class ReminderItem(
    val id: String,
    val title: String,
    val notes: String = "",
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val triggerType: TriggerType,
    val isEnabled: Boolean = true,
    val triggerOnEnter: Boolean = true,
    val isTriggered: Boolean = false,
    val ringtoneUri: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun distanceMetersTo(lat: Double, lng: Double): Float {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat - latitude)
        val dLng = Math.toRadians(lng - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(lat)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }

    fun isInsideRadius(lat: Double, lng: Double): Boolean =
        distanceMetersTo(lat, lng) <= radiusMeters
}

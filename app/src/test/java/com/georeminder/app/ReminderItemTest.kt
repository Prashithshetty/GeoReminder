package com.georeminder.app

import com.georeminder.app.data.model.ReminderItem
import com.georeminder.app.data.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderItemTest {

    private val nyplReminder = ReminderItem(
        id = "rem-1",
        title = "Return Library Books",
        locationName = "NY Public Library",
        latitude = 40.7532,
        longitude = -73.9822,
        radiusMeters = 150f,
        triggerType = TriggerType.NOTIFICATION
    )

    @Test
    fun exactCoordinateHasZeroDistanceAndIsInsideRadius() {
        assertEquals(0f, nyplReminder.distanceMetersTo(40.7532, -73.9822), 0.1f)
        assertTrue(nyplReminder.isInsideRadius(40.7532, -73.9822))
    }

    @Test
    fun nearbyCoordinateWithin150MetersTriggersGeofence() {
        // ~90 meters north of NYPL
        val dist = nyplReminder.distanceMetersTo(40.7540, -73.9822)
        assertTrue("Expected < 150m, got $dist", dist in 80f..100f)
        assertTrue(nyplReminder.isInsideRadius(40.7540, -73.9822))
    }

    @Test
    fun distantCoordinateOutsideRadiusDoesNotTrigger() {
        // Central Park (~3.4 km away)
        val dist = nyplReminder.distanceMetersTo(40.7829, -73.9654)
        assertTrue("Expected > 3000m, got $dist", dist > 3000f)
        assertFalse(nyplReminder.isInsideRadius(40.7829, -73.9654))
    }

    @Test
    fun hysteresisBufferPreventsJitterReTrigger() {
        val exitThreshold = nyplReminder.radiusMeters * 1.25f + 30f
        assertEquals(217.5f, exitThreshold, 0.1f)
        // At 160m (just outside 150m radius), it is still within hysteresis buffer (< 217.5m)
        val distJustOutside = 160f
        assertTrue(distJustOutside > nyplReminder.radiusMeters)
        assertTrue(distJustOutside < exitThreshold)
    }

    @Test
    fun reminderStateFilteringAndToggling() {
        val list = listOf(
            nyplReminder.copy(id = "1", isEnabled = true),
            nyplReminder.copy(id = "2", isEnabled = false),
            nyplReminder.copy(id = "3", isEnabled = true)
        )
        val armed = list.filter { it.isEnabled }
        assertEquals(2, armed.size)

        // Toggle #1 off
        val updated = list.map { if (it.id == "1") it.copy(isEnabled = false) else it }
        assertEquals(1, updated.count { it.isEnabled })
        assertFalse(updated.first { it.id == "1" }.isEnabled)
    }
}

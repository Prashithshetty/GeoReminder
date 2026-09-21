package com.georeminder.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.georeminder.app.data.model.ReminderItem
import com.georeminder.app.data.model.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ReminderRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("geo_reminders_prefs", Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    private val _reminders = MutableStateFlow<List<ReminderItem>>(emptyList())
    val reminders: StateFlow<List<ReminderItem>> = _reminders.asStateFlow()

    init {
        if (!prefs.contains("reminders_key")) {
            initSampleReminders()
        } else {
            loadReminders()
        }
    }

    private fun loadReminders() {
        val jsonString = prefs.getString("reminders_key", null) ?: return
        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<ReminderItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ReminderItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        notes = obj.optString("notes", ""),
                        locationName = obj.getString("locationName"),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        radiusMeters = obj.getDouble("radiusMeters").toFloat(),
                        triggerType = TriggerType.valueOf(obj.getString("triggerType")),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        triggerOnEnter = obj.optBoolean("triggerOnEnter", true),
                        isTriggered = obj.optBoolean("isTriggered", false),
                        ringtoneUri = obj.optString("ringtoneUri", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            _reminders.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persist() {
        val snapshot = _reminders.value
        ioScope.launch {
            try {
                val array = JSONArray()
                for (item in snapshot) {
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("title", item.title)
                        put("notes", item.notes)
                        put("locationName", item.locationName)
                        put("latitude", item.latitude)
                        put("longitude", item.longitude)
                        put("radiusMeters", item.radiusMeters.toDouble())
                        put("triggerType", item.triggerType.name)
                        put("isEnabled", item.isEnabled)
                        put("triggerOnEnter", item.triggerOnEnter)
                        put("isTriggered", item.isTriggered)
                        put("ringtoneUri", item.ringtoneUri)
                        put("createdAt", item.createdAt)
                    }
                    array.put(obj)
                }
                prefs.edit().putString("reminders_key", array.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addReminder(item: ReminderItem) {
        val current = _reminders.value.toMutableList()
        current.add(0, item)
        _reminders.value = current
        persist()
    }

    fun removeReminder(id: String) {
        val current = _reminders.value.filter { it.id != id }
        _reminders.value = current
        persist()
    }

    fun toggleReminder(id: String, enabled: Boolean) {
        val current = _reminders.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        _reminders.value = current
        persist()
    }

    fun markTriggered(id: String, triggered: Boolean) {
        val current = _reminders.value.map {
            if (it.id == id) it.copy(isTriggered = triggered) else it
        }
        _reminders.value = current
        persist()
    }

    private fun initSampleReminders() {
        val sampleList = listOf(
            ReminderItem(
                id = "rem-1",
                title = "Return Library Books",
                notes = "Drop off philosophy volumes at circulation desk",
                locationName = "NY Public Library Main Branch",
                latitude = 40.7532,
                longitude = -73.9822,
                radiusMeters = 120f,
                triggerType = TriggerType.NOTIFICATION,
                isEnabled = true
            ),
            ReminderItem(
                id = "rem-2",
                title = "Pick Up Thyroid Prescription",
                notes = "Check pharmacy counter before 6:00 PM",
                locationName = "5th Ave Care Pharmacy",
                latitude = 40.7614,
                longitude = -73.9776,
                radiusMeters = 180f,
                triggerType = TriggerType.ALARM,
                isEnabled = true
            ),
            ReminderItem(
                id = "rem-3",
                title = "Buy Organic Espresso Beans",
                notes = "Ethiopian single origin light roast",
                locationName = "Union Square Greenmarket",
                latitude = 40.7359,
                longitude = -73.9911,
                radiusMeters = 250f,
                triggerType = TriggerType.NOTIFICATION,
                isEnabled = true
            )
        )
        _reminders.value = sampleList
        persist()
    }

    companion object {
        @Volatile
        private var instance: ReminderRepository? = null

        fun getInstance(context: Context): ReminderRepository {
            return instance ?: synchronized(this) {
                instance ?: ReminderRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

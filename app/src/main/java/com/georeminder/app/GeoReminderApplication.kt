package com.georeminder.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import org.osmdroid.config.Configuration
import java.io.File

class GeoReminderApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Configure OpenStreetMap (osmdroid)
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = "GeoReminder-App/1.0 (Android osmdroid; $packageName)"
        val basePath = File(cacheDir, "osmdroid")
        val tilePath = File(basePath, "tiles")
        osmConfig.osmdroidBasePath = basePath
        osmConfig.osmdroidTileCache = tilePath

        // 2. Setup Notification Channels
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Geofence alert channel (Heads-up notification)
            val geofenceChannel = NotificationChannel(
                CHANNEL_GEOFENCE_ID,
                getString(R.string.channel_geofence_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_geofence_desc)
                enableVibration(true)
                enableLights(true)
            }

            // Foreground service channel
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_service_desc)
            }

            // Full Alarm Channel (Loud and high-priority)
            val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alarm_desc)
                setSound(alarmSoundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }

            manager.createNotificationChannel(geofenceChannel)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alarmChannel)
        }
    }

    companion object {
        const val CHANNEL_GEOFENCE_ID = "geofence_reminder_channel"
        const val CHANNEL_SERVICE_ID = "geofence_service_channel"
        const val CHANNEL_ALARM_ID = "geofence_alarm_channel"
    }
}

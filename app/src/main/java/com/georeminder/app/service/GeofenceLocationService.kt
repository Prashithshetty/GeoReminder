package com.georeminder.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.georeminder.app.GeoReminderApplication
import com.georeminder.app.MainActivity
import com.georeminder.app.data.model.ReminderItem
import com.georeminder.app.data.model.TriggerType
import com.georeminder.app.data.repository.ReminderRepository
import com.georeminder.app.ui.alarm.AlarmActivity

class GeofenceLocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var repository: ReminderRepository

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        repository = ReminderRepository.getInstance(this)
        if (hasLocationPermission(this)) {
            startForeground(FOREGROUND_SERVICE_ID, buildForegroundNotification())
            startLocationUpdates()
        } else {
            stopSelf()
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, GeoReminderApplication.CHANNEL_SERVICE_ID)
            .setContentTitle("GeoReminder Active")
            .setContentText("Monitoring geofenced locations in background")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startLocationUpdates() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 5f, this)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 10f, this)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        for (reminder in repository.reminders.value.filter { it.isEnabled }) {
            val distanceMeters = reminder.distanceMetersTo(location.latitude, location.longitude)
            if (distanceMeters <= reminder.radiusMeters) {
                if (!reminder.isTriggered) {
                    dispatchTrigger(this, reminder, distanceMeters)
                    repository.markTriggered(reminder.id, true)
                }
            } else if (reminder.isTriggered) {
                // Hysteresis buffer prevents alarm flutter from indoor GPS jitter
                if (distanceMeters > reminder.radiusMeters * 1.25f + 30f) {
                    repository.markTriggered(reminder.id, false)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SIMULATE_TRIGGER) {
            val reminderId = intent.getStringExtra("EXTRA_SIMULATE_ID")
            val rem = repository.reminders.value.find { it.id == reminderId }
            if (rem != null) {
                dispatchTrigger(this, rem, 0f)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {
        startLocationUpdates()
    }
    override fun onProviderDisabled(provider: String) {}

    companion object {
        const val FOREGROUND_SERVICE_ID = 9001
        const val ACTION_SIMULATE_TRIGGER = "com.georeminder.app.SIMULATE_TRIGGER"

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun start(context: Context) {
            if (!hasLocationPermission(context)) return
            val intent = Intent(context, GeofenceLocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun simulateTrigger(context: Context, reminderId: String) {
            val rem = ReminderRepository.getInstance(context).reminders.value.find { it.id == reminderId } ?: return
            dispatchTrigger(context, rem, 0f)
        }

        fun dispatchTrigger(context: Context, reminder: ReminderItem, distanceMeters: Float) {
            if (reminder.triggerType == TriggerType.ALARM) {
                launchAlarmTakeover(context, reminder)
            } else {
                showHeadsUpNotification(context, reminder, distanceMeters)
            }
        }

        private fun showHeadsUpNotification(context: Context, reminder: ReminderItem, distanceMeters: Float) {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                reminder.id.hashCode(),
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, GeoReminderApplication.CHANNEL_GEOFENCE_ID)
                .setContentTitle(reminder.title)
                .setContentText("Entered ${reminder.radiusMeters.toInt()}m radius of ${reminder.locationName}")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${reminder.notes}\nLocation: ${reminder.locationName} (Distance: ${distanceMeters.toInt()}m)")
                )
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_view, "Open App", pendingIntent)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(reminder.id.hashCode(), notification)
        }

        private fun launchAlarmTakeover(context: Context, reminder: ReminderItem) {
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra("EXTRA_REMINDER_ID", reminder.id)
                putExtra("EXTRA_TITLE", reminder.title)
                putExtra("EXTRA_NOTE", reminder.notes)
                putExtra("EXTRA_LOCATION", reminder.locationName)
                putExtra("EXTRA_RADIUS", reminder.radiusMeters)
                putExtra("EXTRA_RINGTONE_URI", reminder.ringtoneUri)
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                reminder.id.hashCode() + 1000,
                alarmIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, GeoReminderApplication.CHANNEL_ALARM_ID)
                .setContentTitle("ALARM: " + reminder.title)
                .setContentText("You reached ${reminder.locationName}")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(reminder.id.hashCode() + 500, notification)
        }
    }
}

package com.georeminder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.georeminder.app.service.GeofenceLocationService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GeofenceLocationService.start(context)
        }
    }
}

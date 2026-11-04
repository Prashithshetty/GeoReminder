package com.georeminder.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.georeminder.app.service.GeofenceLocationService
import com.georeminder.app.ui.screens.EngineScreen
import com.georeminder.app.ui.screens.MapScreen
import com.georeminder.app.ui.screens.OfflineScreen
import com.georeminder.app.ui.screens.RemindersScreen
import com.georeminder.app.ui.theme.AccentTeal
import com.georeminder.app.ui.theme.GeoReminderTheme
import com.georeminder.app.ui.theme.GroundLight
import com.georeminder.app.ui.theme.PrimaryCharcoal
import com.georeminder.app.ui.theme.SurfaceWhite
import com.georeminder.app.ui.theme.TextMuted

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            GeofenceLocationService.start(this)
            requestBackgroundLocationIfNeeded()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            GeoReminderTheme {
                MainAppScreen()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            GeofenceLocationService.start(this)
            requestBackgroundLocationIfNeeded()
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request background location separately as per Android 10+ guidelines
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            }
        }
    }
}

enum class NavigationTab(val title: String) {
    MAP("Map"),
    REMINDERS("Reminders"),
    OFFLINE("Offline"),
    ENGINE("Engine")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(NavigationTab.MAP) }
    var activeLat by remember {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val loc = try {
            lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) { null }
        mutableStateOf(loc?.latitude ?: 40.7829)
    }
    var activeLng by remember {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val loc = try {
            lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) { null }
        mutableStateOf(loc?.longitude ?: -73.9654)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GeoReminder",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = PrimaryCharcoal
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceWhite
            ) {
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.MAP,
                    onClick = { selectedTab = NavigationTab.MAP },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                    label = { Text("Map") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentTeal,
                        selectedTextColor = AccentTeal,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = GroundLight
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == NavigationTab.REMINDERS,
                    onClick = { selectedTab = NavigationTab.REMINDERS },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Reminders") },
                    label = { Text("Reminders") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentTeal,
                        selectedTextColor = AccentTeal,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = GroundLight
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == NavigationTab.OFFLINE,
                    onClick = { selectedTab = NavigationTab.OFFLINE },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Offline") },
                    label = { Text("Offline") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentTeal,
                        selectedTextColor = AccentTeal,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = GroundLight
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == NavigationTab.ENGINE,
                    onClick = { selectedTab = NavigationTab.ENGINE },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Engine") },
                    label = { Text("Engine") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentTeal,
                        selectedTextColor = AccentTeal,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = GroundLight
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                NavigationTab.MAP -> MapScreen(
                    initialLat = activeLat,
                    initialLng = activeLng,
                    onCoordinatesChanged = { lat, lng ->
                        activeLat = lat
                        activeLng = lng
                    },
                    onReminderSaved = {
                        selectedTab = NavigationTab.REMINDERS
                    }
                )
                NavigationTab.REMINDERS -> RemindersScreen()
                NavigationTab.OFFLINE -> OfflineScreen(
                    centerLat = activeLat,
                    centerLng = activeLng
                )
                NavigationTab.ENGINE -> EngineScreen()
            }
        }
    }
}

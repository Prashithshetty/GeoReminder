package com.georeminder.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georeminder.app.service.GeofenceLocationService
import com.georeminder.app.ui.theme.AccentTeal
import com.georeminder.app.ui.theme.BorderSubtle
import com.georeminder.app.ui.theme.PrimaryCharcoal
import com.georeminder.app.ui.theme.RaisedGrey
import com.georeminder.app.ui.theme.RuleDividers
import com.georeminder.app.ui.theme.StatusPositive
import com.georeminder.app.ui.theme.SurfaceWhite
import com.georeminder.app.ui.theme.TextMuted

@Composable
fun EngineScreen(
    context: Context = LocalContext.current
) {
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isIgnoringBattery by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Geofence Engine Diagnostics",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryCharcoal
            )
            Text(
                text = "Android background location services and power management status.",
                fontSize = 12.sp,
                color = TextMuted
            )
        }

        // Engine Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceWhite)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location Service",
                        tint = AccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text("Foreground Location Service", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryCharcoal)
                        Text("Continuous geofence monitoring", fontSize = 11.sp, color = TextMuted)
                    }
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFFDCFAE5), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = StatusPositive)
                }
            }

            HorizontalDivider(color = RuleDividers, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = "Battery Optimization",
                        tint = AccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text("Battery Doze Exemption", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryCharcoal)
                        Text("Prevents Android OS from freezing triggers", fontSize = 11.sp, color = TextMuted)
                    }
                }
                Box(
                    modifier = Modifier
                        .background(if (isIgnoringBattery) Color(0xFFDCFAE5) else Color(0xFFFFEBEB), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (isIgnoringBattery) "EXEMPTED" else "OPTIMIZED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isIgnoringBattery) StatusPositive else Color(0xFFC4242A)
                    )
                }
            }

            if (!isIgnoringBattery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Request Battery Exemption", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // OpenStreetMap Engine Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceWhite)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("OpenStreetMap Tile Engine", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryCharcoal)
            Text(
                "Standard Mapnik OSM tile server (tile.openstreetmap.org). Fully open-source with zero Google Maps API keys required.",
                fontSize = 12.sp,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    GeofenceLocationService.start(context)
                },
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Restart",
                    tint = PrimaryCharcoal,
                    modifier = Modifier.size(16.dp)
                )
                Text("Restart Geofence Service", color = PrimaryCharcoal, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

package com.georeminder.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georeminder.app.data.model.OfflineRegion
import com.georeminder.app.data.repository.OfflineMapRepository
import com.georeminder.app.ui.theme.AccentTeal
import com.georeminder.app.ui.theme.BorderSubtle
import com.georeminder.app.ui.theme.PrimaryCharcoal
import com.georeminder.app.ui.theme.RaisedGrey
import com.georeminder.app.ui.theme.StatusNegative
import com.georeminder.app.ui.theme.SurfaceWhite
import com.georeminder.app.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OfflineScreen(
    centerLat: Double = 40.7829,
    centerLng: Double = -73.9654,
    context: Context = LocalContext.current,
    repository: OfflineMapRepository = remember { OfflineMapRepository.getInstance(context) }
) {
    val regions by repository.regions.collectAsState()
    var regionName by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    val totalTiles = regions.sumOf { it.tileCount }
    val totalMb = regions.sumOf { it.sizeMb }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                text = "Offline Map Storage",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryCharcoal
            )
            Text(
                text = "OpenStreetMap tiles stored on device for offline geofence navigation.",
                fontSize = 12.sp,
                color = TextMuted
            )
        }

        // Stats Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceWhite)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Cached Map Tiles", fontSize = 12.sp, color = TextMuted)
                Text(
                    text = String.format(Locale.US, "%,d", totalTiles),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryCharcoal
                )
            }
            Column {
                Text("Storage Used", fontSize = 12.sp, color = TextMuted)
                Text(
                    text = String.format(Locale.US, "%.1f MB", totalMb),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryCharcoal
                )
            }
        }

        // Downloader Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceWhite)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Download Map Area",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryCharcoal
            )

            OutlinedTextField(
                value = regionName,
                onValueChange = { regionName = it },
                label = { Text("Region Label") },
                placeholder = { Text("e.g. Manhattan Downtown & Brooklyn") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = BorderSubtle
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Zoom Levels: 12 – 16", fontSize = 12.sp, color = TextMuted)
                Text("~175 tiles (~3.5 MB)", fontSize = 12.sp, color = TextMuted)
            }

            if (isDownloading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RaisedGrey, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(statusText, fontSize = 11.sp, color = TextMuted)
                        Text("${(downloadProgress * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentTeal)
                    }
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AccentTeal,
                        trackColor = BorderSubtle
                    )
                }
            }

            Button(
                onClick = {
                    if (isDownloading) return@Button
                    isDownloading = true
                    downloadProgress = 0f
                    statusText = "Downloading OSM tiles..."

                    coroutineScope.launch {
                        repository.downloadRegionTiles(
                            name = regionName,
                            centerLat = centerLat,
                            centerLng = centerLng,
                            onProgress = { progress, status ->
                                downloadProgress = progress
                                statusText = status
                            }
                        )
                        regionName = ""
                        isDownloading = false
                        statusText = "Download Complete"
                    }
                },
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCharcoal),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isDownloading) "Downloading Area..." else "Download for Offline Use",
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        // Stored Regions List
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stored Offline Regions",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryCharcoal
            )
            if (regions.isNotEmpty()) {
                Text(
                    text = "Clear Cache",
                    fontSize = 12.sp,
                    color = StatusNegative,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { repository.clearCache() }
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(regions, key = { it.id }) { region ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceWhite)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = region.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryCharcoal
                        )
                        Text(
                            text = "Zoom ${region.minZoom}-${region.maxZoom} • ${region.tileCount} tiles (${region.sizeMb} MB)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    IconButton(onClick = { repository.deleteRegion(region.id) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = StatusNegative,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

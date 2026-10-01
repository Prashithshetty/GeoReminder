package com.georeminder.app.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.georeminder.app.data.model.ReminderItem
import com.georeminder.app.data.model.TriggerType
import com.georeminder.app.data.repository.ReminderRepository
import com.georeminder.app.ui.theme.AccentTeal
import com.georeminder.app.ui.theme.BorderSubtle
import com.georeminder.app.ui.theme.PrimaryCharcoal
import com.georeminder.app.ui.theme.RaisedGrey
import com.georeminder.app.ui.theme.SurfaceWhite
import com.georeminder.app.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    initialLat: Double = 40.7829,
    initialLng: Double = -73.9654,
    onCoordinatesChanged: (Double, Double) -> Unit = { _, _ -> },
    context: Context = LocalContext.current,
    repository: ReminderRepository = remember { ReminderRepository.getInstance(context) },
    onReminderSaved: () -> Unit = {}
) {
    var pinnedLat by remember { mutableStateOf(initialLat) }
    var pinnedLng by remember { mutableStateOf(initialLng) }
    var radiusMeters by remember { mutableFloatStateOf(200f) }
    var reminderTitle by remember { mutableStateOf("") }
    var locationName by remember(initialLat, initialLng) {
        mutableStateOf(String.format(Locale.US, "Location (%.4f, %.4f)", initialLat, initialLng))
    }
    var selectedTriggerType by remember { mutableStateOf(TriggerType.ALARM) }
    var selectedRingtoneUri by remember { mutableStateOf("") }
    var selectedRingtoneTitle by remember { mutableStateOf("Default Alarm") }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            selectedRingtoneUri = uri?.toString() ?: ""
            selectedRingtoneTitle = if (uri != null) {
                RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Custom Tone"
            } else {
                "Default Alarm"
            }
        }
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var activeMarker by remember { mutableStateOf<Marker?>(null) }
    var activeCircle by remember { mutableStateOf<Polygon?>(null) }

    fun updateMapOverlay(mapView: MapView, lat: Double, lng: Double, radius: Float) {
        onCoordinatesChanged(lat, lng)
        val geoPoint = GeoPoint(lat, lng)

        if (activeMarker == null) {
            activeMarker = Marker(mapView).apply {
                position = geoPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = locationName
                mapView.overlays.add(this)
            }
        } else {
            activeMarker?.position = geoPoint
            activeMarker?.title = locationName
        }

        if (activeCircle != null) {
            mapView.overlays.remove(activeCircle)
        }

        val circlePoints = Polygon.pointsAsCircle(geoPoint, radius.toDouble())
        activeCircle = Polygon(mapView).apply {
            points = circlePoints
            outlinePaint.color = android.graphics.Color.argb(220, 27, 108, 128)
            outlinePaint.strokeWidth = 3f
            fillPaint.color = android.graphics.Color.argb(40, 27, 108, 128)
            mapView.overlays.add(this)
        }

        mapView.invalidate()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 68.dp,
        sheetShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        sheetContainerColor = SurfaceWhite,
        sheetShadowElevation = 8.dp,
        sheetDragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(36.dp, 4.dp)
                    .background(Color(0xFFCDCECD), CircleShape)
            )
        },
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
                                    scaffoldState.bottomSheetState.partialExpand()
                                } else {
                                    scaffoldState.bottomSheetState.expand()
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Geofence Reminder",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = PrimaryCharcoal
                    )
                    Box(
                        modifier = Modifier
                            .background(RaisedGrey, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.4f, %.4f", pinnedLat, pinnedLng),
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }

                OutlinedTextField(
                    value = reminderTitle,
                    onValueChange = { reminderTitle = it },
                    label = { Text("Reminder Title") },
                    placeholder = { Text("e.g. Pick up pharmacy prescription") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = BorderSubtle
                    )
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trigger Radius", fontSize = 12.sp, color = TextMuted)
                        Text(
                            "${radiusMeters.toInt()} meters",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentTeal
                        )
                    }
                    Slider(
                        value = radiusMeters,
                        onValueChange = { radiusMeters = it },
                        valueRange = 50f..2500f,
                        steps = 48,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentTeal,
                            activeTrackColor = AccentTeal,
                            inactiveTrackColor = RaisedGrey
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trigger Alert Type", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                    if (selectedTriggerType == TriggerType.ALARM) {
                        Text(
                            text = "Tone: $selectedRingtoneTitle",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentTeal,
                            modifier = Modifier.clickable {
                                val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                }
                                ringtoneLauncher.launch(pickerIntent)
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isAlarm = selectedTriggerType == TriggerType.ALARM
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isAlarm) SurfaceWhite else RaisedGrey)
                            .border(
                                width = if (isAlarm) 1.5.dp else 1.dp,
                                color = if (isAlarm) AccentTeal else BorderSubtle,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedTriggerType = TriggerType.ALARM }
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Alarm",
                                tint = if (isAlarm) AccentTeal else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Full Alarm",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryCharcoal
                            )
                        }
                        Text(
                            text = "Rings loud & wakes screen",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    val isNotif = selectedTriggerType == TriggerType.NOTIFICATION
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isNotif) SurfaceWhite else RaisedGrey)
                            .border(
                                width = if (isNotif) 1.5.dp else 1.dp,
                                color = if (isNotif) AccentTeal else BorderSubtle,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedTriggerType = TriggerType.NOTIFICATION }
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notification",
                                tint = if (isNotif) AccentTeal else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Notification",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryCharcoal
                            )
                        }
                        Text(
                            text = "Heads-up alert & soft chime",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Button(
                    onClick = {
                        val item = ReminderItem(
                            id = "rem-${System.currentTimeMillis()}",
                            title = reminderTitle.ifBlank { "New Georeminder" },
                            notes = "Arriving at $locationName",
                            locationName = locationName,
                            latitude = pinnedLat,
                            longitude = pinnedLng,
                            radiusMeters = radiusMeters,
                            triggerType = selectedTriggerType,
                            ringtoneUri = selectedRingtoneUri,
                            isEnabled = true
                        )
                        repository.addReminder(item)
                        reminderTitle = ""
                        scope.launch {
                            scaffoldState.bottomSheetState.partialExpand()
                        }
                        onReminderSaved()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCharcoal),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Arm",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Arm Georeminder", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.5)
                    controller.setCenter(GeoPoint(pinnedLat, pinnedLng))

                    val mapEventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            pinnedLat = p.latitude
                            pinnedLng = p.longitude
                            locationName = String.format(Locale.US, "Location (%.4f, %.4f)", p.latitude, p.longitude)
                            updateMapOverlay(this@apply, pinnedLat, pinnedLng, radiusMeters)
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    }
                    overlays.add(MapEventsOverlay(mapEventsReceiver))

                    updateMapOverlay(this, pinnedLat, pinnedLng, radiusMeters)
                    mapViewRef = this
                }
            }
        )

        LaunchedEffect(pinnedLat, pinnedLng, radiusMeters, mapViewRef) {
            mapViewRef?.let { map ->
                updateMapOverlay(map, pinnedLat, pinnedLng, radiusMeters)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search location or tap map...", fontSize = 13.sp, color = TextMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        val query = searchQuery.trim()
                        if (query.isNotEmpty()) {
                            scope.launch {
                                val found = withContext(Dispatchers.IO) {
                                    try {
                                        @Suppress("DEPRECATION")
                                        Geocoder(context, Locale.getDefault()).getFromLocationName(query, 1)?.firstOrNull()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (found != null) {
                                    pinnedLat = found.latitude
                                    pinnedLng = found.longitude
                                    locationName = found.featureName ?: query
                                    mapViewRef?.let {
                                        it.controller.animateTo(GeoPoint(pinnedLat, pinnedLng))
                                    }
                                }
                            }
                        }
                    }
                ),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = BorderSubtle
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )

            IconButton(
                onClick = {
                    try {
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        if (loc != null) {
                            pinnedLat = loc.latitude
                            pinnedLng = loc.longitude
                            locationName = "Current GPS Location"
                            mapViewRef?.controller?.animateTo(GeoPoint(pinnedLat, pinnedLng))
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            lm.getCurrentLocation(
                                LocationManager.GPS_PROVIDER,
                                null,
                                ContextCompat.getMainExecutor(context)
                            ) { fresh ->
                                if (fresh != null) {
                                    pinnedLat = fresh.latitude
                                    pinnedLng = fresh.longitude
                                    locationName = "Current GPS Location"
                                    mapViewRef?.controller?.animateTo(GeoPoint(pinnedLat, pinnedLng))
                                }
                            }
                        }
                    } catch (_: SecurityException) {}
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceWhite)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Center",
                    tint = PrimaryCharcoal,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onDetach()
        }
    }
}

package com.georeminder.app.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georeminder.app.data.model.ReminderItem
import com.georeminder.app.data.model.TriggerType
import com.georeminder.app.data.repository.ReminderRepository
import com.georeminder.app.service.GeofenceLocationService
import com.georeminder.app.ui.theme.AccentTeal
import com.georeminder.app.ui.theme.AccentTealLight
import com.georeminder.app.ui.theme.BorderSubtle
import com.georeminder.app.ui.theme.PillNeutralBg
import com.georeminder.app.ui.theme.PillNeutralText
import com.georeminder.app.ui.theme.PillWarningBg
import com.georeminder.app.ui.theme.PillWarningText
import com.georeminder.app.ui.theme.PrimaryCharcoal
import com.georeminder.app.ui.theme.RuleDividers
import com.georeminder.app.ui.theme.StatusNegative
import com.georeminder.app.ui.theme.SurfaceWhite
import com.georeminder.app.ui.theme.TextMuted

@Composable
fun RemindersScreen(
    context: Context = LocalContext.current,
    repository: ReminderRepository = remember { ReminderRepository.getInstance(context) }
) {
    val reminders by repository.reminders.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active Geofences",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryCharcoal
            )
            Text(
                text = "${reminders.count { it.isEnabled }} armed",
                fontSize = 13.sp,
                color = TextMuted
            )
        }

        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No reminders yet. Tap on the Map to place your first geofence!",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onToggle = { enabled ->
                            repository.toggleReminder(reminder.id, enabled)
                        },
                        onDelete = {
                            repository.removeReminder(reminder.id)
                        },
                        onSimulate = {
                            GeofenceLocationService.simulateTrigger(context, reminder.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderCard(
    reminder: ReminderItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSimulate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceWhite)
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryCharcoal
                )
                Text(
                    text = reminder.locationName,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentTeal,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = PillNeutralBg
                )
            )
        }

        // Badges: Alert type + Radius
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAlarm = reminder.triggerType == TriggerType.ALARM
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isAlarm) PillWarningBg else PillNeutralBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isAlarm) "Alarm Alert" else "Notification",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isAlarm) PillWarningText else PillNeutralText
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentTealLight)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${reminder.radiusMeters.toInt()}m radius",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AccentTeal
                )
            }
        }

        if (reminder.notes.isNotBlank()) {
            Text(
                text = reminder.notes,
                fontSize = 13.sp,
                color = TextMuted
            )
        }

        HorizontalDivider(color = RuleDividers, thickness = 1.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSimulate) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Simulate",
                    tint = AccentTeal,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("Simulate Entry", fontSize = 12.sp, color = AccentTeal, fontWeight = FontWeight.Medium)
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = StatusNegative,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

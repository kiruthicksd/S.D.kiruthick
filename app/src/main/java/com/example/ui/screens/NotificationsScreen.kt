package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AppNotification
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.DataUiState
import com.example.util.SystemNotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(
  notificationsState: DataUiState<List<AppNotification>>,
  systemNotificationsEnabled: Boolean,
  onToggleSystemNotifications: (Boolean) -> Unit,
  onToggleRead: (AppNotification) -> Unit,
  onMarkAllRead: () -> Unit,
  onClearAll: () -> Unit,
) {
  val context = LocalContext.current
  val hasOsPermission = SystemNotificationHelper.hasNotificationPermission(context)
  val permLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) onToggleSystemNotifications(true)
    }

  val notifications = (notificationsState as? DataUiState.Success)?.data.orEmpty()
  val unreadCount = notifications.count { !it.isRead }
  val dateFmt = remember { SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()) }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxSize().testTag("notifications_screen"),
  ) {
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Notification Center", style = MaterialTheme.typography.titleLarge)
          StatusPill(
            label = "$unreadCount UNREAD",
            dotColor = if (unreadCount > 0) WarningAmber else SuccessGreen,
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = onMarkAllRead,
            enabled = unreadCount > 0,
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Mark All Read")
          }
          OutlinedButton(
            onClick = onClearAll,
            enabled = notifications.isNotEmpty(),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Clear All")
          }
        }
        if (!hasOsPermission || !systemNotificationsEnabled) {
          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = {
              if (!hasOsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                onToggleSystemNotifications(true)
              }
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enable Device Push Alerts")
          }
        }
      }
    }

    if (notifications.isEmpty()) {
      item {
        EmptyStatePanel(
          icon = Icons.Filled.Notifications,
          title = "No Notifications",
          subtitle = "Photo uploads, ESP32-CAM connection updates, AI processing alerts, and account events appear here.",
        )
      }
    } else {
      items(notifications, key = { it.id }) { notif ->
        val icon =
          when (notif.category) {
            "photo" -> Icons.Filled.CameraAlt
            "camera" -> Icons.Filled.Videocam
            "ai" -> Icons.Filled.AutoAwesome
            "map" -> Icons.Filled.LocationOn
            else -> Icons.Filled.Person
          }
        GlassCard(
          accentColor =
            if (!notif.isRead) NeonCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
          modifier = Modifier.fillMaxWidth().clickable { onToggleRead(notif) },
        ) {
          Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint =
                if (!notif.isRead) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(notif.title, style = MaterialTheme.typography.titleMedium)
                if (!notif.isRead) {
                  StatusPill(label = "NEW", dotColor = NeonCyan)
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = notif.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(6.dp))
              val ts = notif.createdAt?.toDate() ?: Date()
              Text(
                text = "${notif.category.uppercase()} • ${dateFmt.format(ts)} • Tap to toggle read",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

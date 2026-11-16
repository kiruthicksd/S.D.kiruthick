package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.repository.AppSettings
import com.example.ui.components.GlassCard
import com.example.util.SystemNotificationHelper

@Composable
fun SettingsScreen(
  settings: AppSettings,
  onSetDarkTheme: (Boolean) -> Unit,
  onSetAiTone: (String) -> Unit,
  onSetAutoSuggestCaptions: (Boolean) -> Unit,
  onSetSystemNotifications: (Boolean) -> Unit,
  onSetDefaultSharePublic: (Boolean) -> Unit,
  onClearLocationHistory: () -> Unit,
  onOpenEsp32Settings: () -> Unit,
  onDeleteAccountData: () -> Unit,
) {
  val context = LocalContext.current
  var showDeleteConfirm by remember { mutableStateOf(false) }

  val hasLocationPerm =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

  val locLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

  val notifLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      onSetSystemNotifications(granted)
    }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier.fillMaxSize().testTag("settings_screen"),
  ) {
    // Appearance
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Appearance", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Futuristic Obsidian Dark Theme", style = MaterialTheme.typography.bodyLarge)
            Text(
              text = "Switch between Neon Dark and Clean Daylight",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(checked = settings.isDarkTheme, onCheckedChange = onSetDarkTheme)
        }
      }
    }

    // Location & Privacy
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Location & Privacy", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text =
            if (hasLocationPerm) "Foreground location permission: Granted (never collected in background)"
            else "Foreground location permission: Not granted",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          if (!hasLocationPerm) {
            OutlinedButton(
              onClick = {
                locLauncher.launch(
                  arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                  )
                )
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Grant GPS")
            }
          }
          OutlinedButton(
            onClick = onClearLocationHistory,
            modifier = Modifier.weight(1f),
          ) {
            Text("Clear History")
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Default Public Photo Sharing", style = MaterialTheme.typography.bodyLarge)
            Text(
              text = "Keep off so uploaded photos stay private unless opted in",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(checked = settings.defaultSharePublic, onCheckedChange = onSetDefaultSharePublic)
        }
      }
    }

    // AI Settings
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("AI Assistant Settings", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Response Style", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("Balanced", "Concise", "Detailed").forEach { tone ->
            FilterChip(
              selected = settings.aiTone == tone,
              onClick = { onSetAiTone(tone) },
              label = { Text(tone) },
            )
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Enable AI Caption Suggestions", style = MaterialTheme.typography.bodyLarge)
          Switch(checked = settings.autoSuggestCaptions, onCheckedChange = onSetAutoSuggestCaptions)
        }
      }
    }

    // ESP32-CAM & Notifications
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Notifications & Hardware", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Device System Notifications", style = MaterialTheme.typography.bodyLarge)
            Text(
              text = "Post Android alerts for uploads & ESP32 status",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = settings.systemNotificationsEnabled,
            onCheckedChange = { enabled ->
              if (
                enabled &&
                  !SystemNotificationHelper.hasNotificationPermission(context) &&
                  Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
              ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                onSetSystemNotifications(enabled)
              }
            },
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(onClick = onOpenEsp32Settings, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            if (settings.esp32Address.isBlank()) "Configure ESP32-CAM Address"
            else "ESP32-CAM: ${settings.esp32Address}"
          )
        }
      }
    }

    // Danger Zone: Delete Account Data
    item {
      GlassCard(accentColor = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.error)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Account & Data Removal", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Permanently delete your stored photos, location check-ins, AI conversation history, notifications, and profile from Cloud Firestore, then sign out.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
          onClick = { showDeleteConfirm = true },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.fillMaxWidth().testTag("delete_account_button"),
        ) {
          Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Delete Account Data & Sign Out")
        }
      }
    }

    item {
      Text(
        text = "${stringResource(R.string.app_name)} • ${stringResource(R.string.developer_credit)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Delete Account Data?") },
      text = {
        Text(
          "This will erase all your photos, location pins, notifications, AI chat history, and profile from Firestore and sign you out. This action cannot be undone."
        )
      },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            showDeleteConfirm = false
            onDeleteAccountData()
          },
        ) {
          Text("Delete Everything")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
      },
    )
  }
}

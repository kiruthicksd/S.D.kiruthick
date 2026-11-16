package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.Esp32Status
import com.example.ui.viewmodel.Esp32UiState
import com.example.util.Esp32CamClient

@Composable
fun Esp32CameraScreen(
  esp32State: Esp32UiState,
  onAddressChanged: (String) -> Unit,
  onConnect: (String) -> Unit,
  onDisconnect: () -> Unit,
  onCaptureSnapshot: () -> Unit,
  onSaveSnapshotToGallery: (Bitmap, String, Boolean) -> Unit,
) {
  var snapshotCaption by remember { mutableStateOf("ESP32-CAM Capture") }
  var attachGps by remember { mutableStateOf(false) }

  val statusColor =
    when (esp32State.status) {
      Esp32Status.STREAMING -> SuccessGreen
      Esp32Status.CONNECTING -> WarningAmber
      Esp32Status.ERROR -> ErrorCoral
      Esp32Status.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier.fillMaxSize().testTag("esp32_camera_screen"),
  ) {
    // Connection Controls
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("ESP32-CAM Wi-Fi Link", style = MaterialTheme.typography.titleLarge)
          StatusPill(label = esp32State.status.name, dotColor = statusColor)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text =
            "Enter your ESP32-CAM local Wi-Fi IP address (e.g. 192.168.1.50) or full MJPEG stream URL.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
          value = esp32State.addressInput,
          onValueChange = onAddressChanged,
          label = { Text("Camera IP or Stream URL") },
          placeholder = { Text("192.168.1.50 or http://192.168.4.1:81/stream") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("esp32_ip_input"),
        )
        if (esp32State.addressInput.isNotBlank()) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Resolved stream: ${Esp32CamClient.resolveStreamUrl(esp32State.addressInput)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          val isActive =
            esp32State.status == Esp32Status.STREAMING ||
              esp32State.status == Esp32Status.CONNECTING
          Button(
            onClick = { onConnect(esp32State.addressInput) },
            enabled = !isActive,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.weight(1f).testTag("esp32_connect_button"),
          ) {
            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (esp32State.status == Esp32Status.CONNECTING) "Connecting..." else "Connect")
          }

          OutlinedButton(
            onClick = onDisconnect,
            enabled = isActive,
            modifier = Modifier.weight(1f).testTag("esp32_disconnect_button"),
          ) {
            Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Disconnect")
          }
        }

        if (!esp32State.errorMessage.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(10.dp))
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = esp32State.errorMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
      }
    }

    // Live MJPEG Stream Viewport
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Live Feed Viewport", style = MaterialTheme.typography.titleMedium)
          if (esp32State.framesReceived > 0) {
            Text(
              text = "${esp32State.framesReceived} frames",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
          modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(16.dp)),
          contentAlignment = Alignment.Center,
        ) {
          val liveFrame = esp32State.currentFrame
          if (liveFrame != null && esp32State.status == Esp32Status.STREAMING) {
            Image(
              bitmap = liveFrame.asImageBitmap(),
              contentDescription = "Live ESP32-CAM MJPEG stream",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Image(
              painter = painterResource(id = R.drawable.img_esp32_module),
              contentDescription = "ESP32-CAM offline status graphic",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Surface(
              color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.padding(16.dp),
            ) {
              Text(
                text =
                  when (esp32State.status) {
                    Esp32Status.CONNECTING -> "Establishing MJPEG stream..."
                    Esp32Status.ERROR -> "Camera unreachable. Check Wi-Fi & IP."
                    else -> "Camera is offline. Connect over local Wi-Fi."
                  },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
          onClick = onCaptureSnapshot,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          modifier = Modifier.fillMaxWidth().testTag("esp32_capture_button"),
        ) {
          Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Capture Snapshot")
        }
      }
    }

    // Last Snapshot Preview & Save to Gallery
    val snapshot = esp32State.lastSnapshot
    if (snapshot != null) {
      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text("Captured Snapshot", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(8.dp))
          Image(
            bitmap = snapshot.asImageBitmap(),
            contentDescription = "ESP32-CAM captured snapshot",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)),
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = snapshotCaption,
            onValueChange = { snapshotCaption = it },
            label = { Text("Snapshot Caption") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Tag with last GPS fix", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = attachGps, onCheckedChange = { attachGps = it })
          }
          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = { onSaveSnapshotToGallery(snapshot, snapshotCaption, attachGps) },
            modifier = Modifier.fillMaxWidth().testTag("esp32_save_gallery_button"),
          ) {
            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Snapshot to Photo Gallery")
          }
        }
      }
    }

    // Setup & Security Instructions
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("ESP32-CAM Setup & Security", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text =
            "1. Flash your ESP32-CAM with CameraWebServer or an MJPEG HTTP server.\n" +
              "2. Join the same Wi-Fi LAN (or ESP32 softAP) as your phone.\n" +
              "3. Enter the board's IP (default endpoints: :81/stream and /capture).\n" +
              "4. Security: Keep streams on your private local LAN or VPN — never expose an unauthenticated ESP32-CAM port directly to the public internet.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Security, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Private LAN-only stream policy active",
            style = MaterialTheme.typography.labelSmall,
            color = SuccessGreen,
          )
        }
      }
    }
  }
}

package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.UserPhoto
import com.example.data.model.UserProfile
import com.example.ui.components.Base64PhotoImage
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.Esp32Status
import com.example.ui.viewmodel.Esp32UiState
import com.example.ui.viewmodel.GpsUiState

@Composable
fun HomeScreen(
  profile: UserProfile?,
  fallbackName: String,
  gpsState: GpsUiState,
  esp32State: Esp32UiState,
  photosState: DataUiState<List<UserPhoto>>,
  notificationsState: DataUiState<List<AppNotification>>,
  onNavigateToMap: () -> Unit,
  onNavigateToPhotos: () -> Unit,
  onNavigateToEsp32: () -> Unit,
  onNavigateToAi: () -> Unit,
  onNavigateToNotifications: () -> Unit,
  onNavigateToProfile: () -> Unit,
  onRefreshGps: () -> Unit,
) {
  val displayName = profile?.displayName?.ifBlank { fallbackName } ?: fallbackName
  val photos = (photosState as? DataUiState.Success)?.data.orEmpty()
  val notifications = (notificationsState as? DataUiState.Success)?.data.orEmpty()
  val unreadCount = notifications.count { !it.isRead }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxSize().testTag("home_screen"),
  ) {
    // Welcome & Hero Telemetry Banner
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp))) {
          Image(
            painter = painterResource(id = R.drawable.img_hero_telemetry),
            contentDescription = "Dashboard telemetry backdrop",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(modifier = Modifier.fillMaxSize().background(Color(0x99050814)))
          Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onNavigateToProfile),
              ) {
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                  modifier = Modifier.size(48.dp),
                ) {
                  val avatar = profile?.avatarBase64
                  if (!avatar.isNullOrBlank()) {
                    Base64PhotoImage(
                      base64 = avatar,
                      contentDescription = "User avatar",
                      modifier = Modifier.fillMaxSize(),
                    )
                  } else {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = "Welcome back,",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }

              StatusPill(
                label = if (unreadCount > 0) "$unreadCount ALERTS" else "ALL CLEAR",
                dotColor = if (unreadCount > 0) WarningAmber else SuccessGreen,
                modifier = Modifier.clickable(onClick = onNavigateToNotifications),
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              val gpsColor =
                when {
                  gpsState.reading != null -> SuccessGreen
                  gpsState.hasPermission -> WarningAmber
                  else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
              val gpsLabel =
                when {
                  gpsState.reading != null -> "GPS LOCKED"
                  gpsState.hasPermission -> "GPS STANDBY"
                  else -> "GPS OFF"
                }
              StatusPill(label = gpsLabel, dotColor = gpsColor)

              val camColor =
                when (esp32State.status) {
                  Esp32Status.STREAMING -> SuccessGreen
                  Esp32Status.CONNECTING -> WarningAmber
                  Esp32Status.ERROR -> ErrorCoral
                  Esp32Status.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
              StatusPill(label = "ESP32: ${esp32State.status.name}", dotColor = camColor)
            }
          }
        }
      }
    }

    // Current Location Summary Card
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.LocationOn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Current Location Summary",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          OutlinedButton(
            onClick = onRefreshGps,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.testTag("home_refresh_gps_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Refresh GPS",
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (gpsState.isLoading) "Locating..." else "Refresh")
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
        val reading = gpsState.reading
        if (reading != null) {
          Text(
            text = reading.label ?: "Active Coordinates",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text =
              "LAT ${String.format("%.5f", reading.latitude)}  •  LNG ${String.format("%.5f", reading.longitude)}  •  ±${reading.accuracyMeters.toInt()}m",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        } else {
          Text(
            text =
              gpsState.errorMessage
                ?: "Location is fetched only on your explicit request. Tap Refresh or Explore Map to acquire a GPS fix.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Quick Action Grid
    item {
      Text(
        text = "Quick Command Deck",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(10.dp))
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        QuickActionCard(
          icon = Icons.Filled.AddAPhoto,
          title = "Photos",
          subtitle = "${photos.size} saved",
          onClick = onNavigateToPhotos,
          modifier = Modifier.weight(1f).testTag("quick_photos_card"),
        )
        QuickActionCard(
          icon = Icons.Filled.Map,
          title = "Explore Map",
          subtitle = "OpenStreetMap",
          onClick = onNavigateToMap,
          modifier = Modifier.weight(1f).testTag("quick_map_card"),
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        QuickActionCard(
          icon = Icons.Filled.Videocam,
          title = "ESP32-CAM",
          subtitle = esp32State.status.name.lowercase().replaceFirstChar { it.uppercase() },
          onClick = onNavigateToEsp32,
          modifier = Modifier.weight(1f).testTag("quick_esp32_card"),
        )
        QuickActionCard(
          icon = Icons.Filled.AutoAwesome,
          title = "AI Assistant",
          subtitle = "Gemini Vision",
          onClick = onNavigateToAi,
          modifier = Modifier.weight(1f).testTag("quick_ai_card"),
        )
      }
    }

    // AI Shortcut Banner
    item {
      GlassCard(
        onClick = onNavigateToAi,
        accentColor = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
              modifier = Modifier.size(46.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Filled.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                )
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Ask Gemini Vision AI",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "Analyze photos, suggest captions, or explore map coordinates",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onNavigateToAi,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
          ) {
            Text("Chat")
          }
        }
      }
    }

    // Recent Photos Section
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Recent Uploaded Photos",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedButton(onClick = onNavigateToPhotos) { Text("View All") }
      }
      Spacer(modifier = Modifier.height(10.dp))
      if (photos.isEmpty()) {
        EmptyStatePanel(
          icon = Icons.Filled.AddAPhoto,
          title = "No Photos Uploaded Yet",
          subtitle = "Pick a photo from your device, snap one with your camera, or capture an ESP32-CAM frame.",
          actionLabel = "Open Photo Vault",
          onAction = onNavigateToPhotos,
        )
      } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(photos.take(6), key = { it.id }) { photo ->
            GlassCard(
              onClick = onNavigateToPhotos,
              modifier = Modifier.width(164.dp),
            ) {
              Base64PhotoImage(
                base64 = photo.imageBase64,
                contentDescription = photo.caption,
                modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)),
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = photo.caption.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = photo.source.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }

    // Recent Notification Preview
    item {
      GlassCard(onClick = onNavigateToNotifications, modifier = Modifier.fillMaxWidth()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.Notifications,
              contentDescription = null,
              tint = NeonCyan,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Notification Center",
              style = MaterialTheme.typography.titleMedium,
            )
          }
          Text(
            text = "${notifications.size} total • $unreadCount unread",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        val latest = notifications.firstOrNull()
        if (latest != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "${latest.title}: ${latest.message}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    item {
      Text(
        text = stringResource(R.string.developer_credit),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      )
    }
  }
}

@Composable
private fun QuickActionCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  GlassCard(onClick = onClick, modifier = modifier) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
      modifier = Modifier.size(40.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      }
    }
    Spacer(modifier = Modifier.height(10.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

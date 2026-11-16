package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleSignInButton
import com.example.auth.attemptAutoSignIn
import com.example.ui.components.FuturisticBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.NeonCyan

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  FuturisticBackground(isDarkTheme = true) {
    Box(
      modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth().widthIn(max = 520.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Box(
            modifier =
              Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(16.dp))
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_hero_telemetry),
              contentDescription = "Futuristic telemetry horizon",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Surface(
              color = Color(0xAA050814),
              modifier = Modifier.fillMaxSize(),
            ) {}
            Column(
              modifier = Modifier.fillMaxSize().padding(16.dp),
              verticalArrangement = Arrangement.SpaceBetween,
            ) {
              StatusPill(label = "NEURAL TELEMETRY v1.0", dotColor = NeonCyan)
              Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                  shape = CircleShape,
                  border =
                    BorderStroke(
                      1.5.dp,
                      Brush.linearGradient(
                        listOf(
                          MaterialTheme.colorScheme.primary,
                          MaterialTheme.colorScheme.secondary,
                        )
                      ),
                    ),
                  modifier = Modifier.size(56.dp),
                ) {
                  Image(
                    painter = painterResource(id = R.drawable.img_launcher_emblem),
                    contentDescription = "Smart AI Map Emblem",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                  Text(
                    text = "SMART AI & MAP",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = "User Companion Command Deck",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = stringResource(R.string.auth_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(16.dp))

          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureHighlightRow(
              icon = Icons.Filled.Map,
              title = "Interactive OpenStreetMap & GPS",
              subtitle = "Live coordinates, accuracy rings, and user-approved pins",
            )
            FeatureHighlightRow(
              icon = Icons.Filled.AutoAwesome,
              title = "Gemini Vision AI Assistant",
              subtitle = "Multimodal photo insights, captions, and location help",
            )
            FeatureHighlightRow(
              icon = Icons.Filled.Videocam,
              title = "ESP32-CAM Wi-Fi Stream",
              subtitle = "Real-time MJPEG stream viewing and snapshot capture",
            )
            FeatureHighlightRow(
              icon = Icons.Filled.CameraAlt,
              title = "Cloud-Synced Photo Vault",
              subtitle = "Private by default with explicit public opt-in sharing",
            )
          }

          Spacer(modifier = Modifier.height(20.dp))
          GoogleSignInButton(
            onAuthSuccess = {
              errorMessage = null
              onAuthSuccess()
            },
            onAuthError = { msg -> errorMessage = msg },
          )

          if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
          text = stringResource(R.string.developer_credit),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun FeatureHighlightRow(icon: ImageVector, title: String, subtitle: String) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
      modifier = Modifier.size(40.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

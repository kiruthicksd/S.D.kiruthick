package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FuturisticBackground(
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val bg = MaterialTheme.colorScheme.background
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary

  Box(modifier = modifier.fillMaxSize().background(bg)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val topGlowAlpha = if (isDarkTheme) 0.16f else 0.08f
      val bottomGlowAlpha = if (isDarkTheme) 0.12f else 0.05f
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(primary.copy(alpha = topGlowAlpha), Color.Transparent),
            center = Offset(size.width * 0.85f, size.height * 0.08f),
            radius = size.width * 0.85f,
          ),
        radius = size.width * 0.85f,
        center = Offset(size.width * 0.85f, size.height * 0.08f),
      )
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(secondary.copy(alpha = bottomGlowAlpha), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.88f),
            radius = size.width * 0.75f,
          ),
        radius = size.width * 0.75f,
        center = Offset(size.width * 0.12f, size.height * 0.88f),
      )
    }
    content()
  }
}

@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  accentColor: Color = MaterialTheme.colorScheme.primary,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(22.dp)
  val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f)
  val borderBrush =
    Brush.linearGradient(
      colors =
        listOf(
          accentColor.copy(alpha = 0.42f),
          MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
          MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
        )
    )

  if (onClick != null) {
    Surface(
      onClick = onClick,
      shape = shape,
      color = containerColor,
      border = BorderStroke(1.dp, borderBrush),
      tonalElevation = 4.dp,
      modifier = modifier,
    ) {
      Column(modifier = Modifier.padding(16.dp), content = content)
    }
  } else {
    Surface(
      shape = shape,
      color = containerColor,
      border = BorderStroke(1.dp, borderBrush),
      tonalElevation = 4.dp,
      modifier = modifier,
    ) {
      Column(modifier = Modifier.padding(16.dp), content = content)
    }
  }
}

@Composable
fun StatusPill(
  label: String,
  dotColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(50),
    color = dotColor.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, dotColor.copy(alpha = 0.45f)),
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
      Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
fun Base64PhotoImage(
  base64: String,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop,
) {
  val bitmap by
    produceState<Bitmap?>(initialValue = null, key1 = base64) {
      value = withContext(Dispatchers.Default) { ImageUtils.decodeBase64ToBitmap(base64) }
    }

  val bmp = bitmap
  if (bmp != null) {
    Image(
      bitmap = bmp.asImageBitmap(),
      contentDescription = contentDescription,
      contentScale = contentScale,
      modifier = modifier,
    )
  } else {
    Box(
      modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "Loading...",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun EmptyStatePanel(
  icon: ImageVector,
  title: String,
  subtitle: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  GlassCard(modifier = modifier.fillMaxWidth()) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 12.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        modifier = Modifier.size(64.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp),
          )
        }
      }
      Spacer(modifier = Modifier.height(14.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      if (actionLabel != null && onAction != null) {
        Spacer(modifier = Modifier.height(16.dp))
        Button(
          onClick = onAction,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}

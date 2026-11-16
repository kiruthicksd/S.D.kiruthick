package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.LocationEntry
import com.example.data.model.UserPhoto
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.GpsUiState
import com.example.ui.viewmodel.SelectedMapPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay

@Composable
fun MapScreen(
  isDarkTheme: Boolean,
  gpsState: GpsUiState,
  selectedMapPoint: SelectedMapPoint?,
  photosState: DataUiState<List<UserPhoto>>,
  locationHistoryState: DataUiState<List<LocationEntry>>,
  onPermissionResult: (Boolean) -> Unit,
  onRefreshLocation: () -> Unit,
  onSelectMapPoint: (Double, Double) -> Unit,
  onSaveLocationCheckIn: (String, Double, Double, Double) -> Unit,
  onDeleteLocationEntry: (String) -> Unit,
  onClearLocationHistory: () -> Unit,
  onAskAiAboutPoint: () -> Unit,
  onTagPhotoHere: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var showCheckInDialog by remember { mutableStateOf(false) }
  var checkInLabel by remember { mutableStateOf("") }

  fun checkLocPermission(): Boolean {
    val fine =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
      val granted =
        result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
      onPermissionResult(granted)
      if (granted) onRefreshLocation()
    }

  LaunchedEffect(Unit) {
    val granted = checkLocPermission()
    onPermissionResult(granted)
  }

  val mapView = remember {
    Configuration.getInstance().userAgentValue = context.packageName
    MapView(context).apply {
      setTileSource(TileSourceFactory.MAPNIK)
      setMultiTouchControls(true)
      controller.setZoom(14.0)
      controller.setCenter(GeoPoint(20.5937, 78.9629))
    }
  }

  DisposableEffect(lifecycleOwner, mapView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> mapView.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val photos = (photosState as? DataUiState.Success)?.data.orEmpty()
  val history = (locationHistoryState as? DataUiState.Success)?.data.orEmpty()

  LaunchedEffect(gpsState.reading, selectedMapPoint, photos, history, isDarkTheme) {
    mapView.overlayManager.tilesOverlay.setColorFilter(
      if (isDarkTheme) TilesOverlay.INVERT_COLORS else null
    )
    mapView.overlays.clear()

    val eventsOverlay =
      MapEventsOverlay(
        object : MapEventsReceiver {
          override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
            if (p != null) onSelectMapPoint(p.latitude, p.longitude)
            return true
          }

          override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
      )
    mapView.overlays.add(eventsOverlay)

    val currentReading = gpsState.reading
    if (currentReading != null) {
      val currentGeo = GeoPoint(currentReading.latitude, currentReading.longitude)
      val circle =
        Polygon(mapView).apply {
          points = Polygon.pointsAsCircle(currentGeo, currentReading.accuracyMeters.toDouble().coerceAtLeast(15.0))
          fillPaint.color = android.graphics.Color.argb(40, 0, 229, 255)
          outlinePaint.color = android.graphics.Color.argb(180, 0, 229, 255)
          outlinePaint.strokeWidth = 3f
        }
      mapView.overlays.add(circle)

      val marker =
        Marker(mapView).apply {
          position = currentGeo
          title = "Your Current Location"
          snippet = "Accuracy ±${currentReading.accuracyMeters.toInt()}m"
          setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
      mapView.overlays.add(marker)

      if (selectedMapPoint == null) {
        mapView.controller.animateTo(currentGeo)
      }
    }

    if (selectedMapPoint != null) {
      val selGeo = GeoPoint(selectedMapPoint.latitude, selectedMapPoint.longitude)
      val selMarker =
        Marker(mapView).apply {
          position = selGeo
          title = "Selected Pin"
          snippet = selectedMapPoint.label
          setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
      mapView.overlays.add(selMarker)
    }

    photos.filter { it.hasLocation && it.latitude != null && it.longitude != null }.forEach { photo ->
      val geo = GeoPoint(photo.latitude!!, photo.longitude!!)
      val m =
        Marker(mapView).apply {
          position = geo
          title = "Photo: ${photo.caption}"
          snippet = photo.locationLabel ?: photo.source
          setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
      mapView.overlays.add(m)
    }

    history.forEach { entry ->
      val geo = GeoPoint(entry.latitude, entry.longitude)
      val m =
        Marker(mapView).apply {
          position = geo
          title = "History: ${entry.label}"
          snippet = "±${entry.accuracyMeters.toInt()}m"
          setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
      mapView.overlays.add(m)
    }

    mapView.invalidate()
  }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier.fillMaxSize().testTag("map_screen"),
  ) {
    // OpenStreetMap Interactive Viewport
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "OpenStreetMap Explorer",
            style = MaterialTheme.typography.titleLarge,
          )
          StatusPill(
            label = if (gpsState.hasPermission) "PERMISSION GRANTED" else "PERMISSION NEEDED",
            dotColor = if (gpsState.hasPermission) SuccessGreen else WarningAmber,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Tap anywhere on the map to select coordinates. Foreground GPS is only read when requested.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
          modifier =
            Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp))
        ) {
          AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Button(
            onClick = {
              if (checkLocPermission()) {
                onRefreshLocation()
              } else {
                permissionLauncher.launch(
                  arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                  )
                )
              }
            },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.weight(1f).testTag("locate_me_button"),
          ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (gpsState.isLoading) "Locating..." else "Locate Me")
          }

          val activeLat = selectedMapPoint?.latitude ?: gpsState.reading?.latitude
          val activeLng = selectedMapPoint?.longitude ?: gpsState.reading?.longitude
          OutlinedButton(
            onClick = {
              if (activeLat != null && activeLng != null) {
                checkInLabel = selectedMapPoint?.label ?: gpsState.reading?.label ?: "Field Pin"
                showCheckInDialog = true
              }
            },
            enabled = activeLat != null && activeLng != null,
            modifier = Modifier.weight(1f).testTag("save_pin_button"),
          ) {
            Icon(
              Icons.Filled.AddLocationAlt,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save Pin")
          }
        }
      }
    }

    // Coordinates & Actions Card
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        val reading = gpsState.reading
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = NeonCyan)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Telemetry & Selected Point", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (reading != null) {
          Text(
            text =
              "Current GPS: ${String.format("%.5f", reading.latitude)}, ${String.format("%.5f", reading.longitude)} (±${reading.accuracyMeters.toInt()}m)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        } else {
          Text(
            text = gpsState.errorMessage ?: "No active GPS reading yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (selectedMapPoint != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text =
              "Selected Pin: ${selectedMapPoint.label} (${String.format("%.5f", selectedMapPoint.latitude)}, ${String.format("%.5f", selectedMapPoint.longitude)})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = onTagPhotoHere,
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Tag Photo")
          }
          OutlinedButton(
            onClick = onAskAiAboutPoint,
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Ask AI Here")
          }
        }
      }
    }

    // User-Approved Location History
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Approved Location History", style = MaterialTheme.typography.titleLarge)
        if (history.isNotEmpty()) {
          TextButton(onClick = onClearLocationHistory) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear")
          }
        }
      }
    }

    if (history.isEmpty()) {
      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "No saved location check-ins yet. Location history is only stored when you tap Save Pin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(history, key = { it.id }) { entry ->
        GlassCard(
          onClick = {
            mapView.controller.animateTo(GeoPoint(entry.latitude, entry.longitude))
            onSelectMapPoint(entry.latitude, entry.longitude)
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(entry.label, style = MaterialTheme.typography.titleMedium)
                Text(
                  text =
                    "${String.format("%.5f", entry.latitude)}, ${String.format("%.5f", entry.longitude)} • ±${entry.accuracyMeters.toInt()}m",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            IconButton(onClick = { onDeleteLocationEntry(entry.id) }) {
              Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete location check-in",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }
    }
  }

  if (showCheckInDialog) {
    val lat = selectedMapPoint?.latitude ?: gpsState.reading?.latitude ?: 0.0
    val lng = selectedMapPoint?.longitude ?: gpsState.reading?.longitude ?: 0.0
    val acc = gpsState.reading?.accuracyMeters?.toDouble() ?: 0.0
    AlertDialog(
      onDismissRequest = { showCheckInDialog = false },
      title = { Text("Save Location Check-In") },
      text = {
        Column {
          Text(
            text = "Coordinates: ${String.format("%.5f", lat)}, ${String.format("%.5f", lng)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = checkInLabel,
            onValueChange = { checkInLabel = it },
            label = { Text("Label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSaveLocationCheckIn(checkInLabel.ifBlank { "Saved Pin" }, lat, lng, acc)
            showCheckInDialog = false
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCheckInDialog = false }) { Text("Cancel") }
      },
    )
  }
}

package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.SharedPhoto
import com.example.data.model.UserPhoto
import com.example.ui.components.Base64PhotoImage
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.GpsUiState
import com.example.ui.viewmodel.SelectedMapPoint
import com.example.util.ImageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun PhotosScreen(
  photosState: DataUiState<List<UserPhoto>>,
  sharedPhotosState: DataUiState<List<SharedPhoto>>,
  gpsState: GpsUiState,
  selectedMapPoint: SelectedMapPoint?,
  defaultSharePublic: Boolean,
  onRefreshGps: () -> Unit,
  onUploadPhoto: (
    caption: String,
    description: String,
    imageBase64: String,
    source: String,
    attachCurrentGps: Boolean,
    customPoint: SelectedMapPoint?,
    isPublic: Boolean,
    onDone: (Boolean) -> Unit,
  ) -> Unit,
  onUpdatePhoto: (UserPhoto, String, String, Boolean) -> Unit,
  onDeletePhoto: (String) -> Unit,
  onSuggestCaption: (Bitmap, (Result<Pair<String, String>>) -> Unit) -> Unit,
  onAnalyzeWithAi: (UserPhoto) -> Unit,
  onAskAiAboutPhoto: (UserPhoto) -> Unit,
  onOpenLocationOnMap: (Double, Double) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var selectedTab by remember { mutableIntStateOf(0) }

  // Pending upload draft state
  var draftBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var draftBase64 by remember { mutableStateOf<String?>(null) }
  var draftSource by remember { mutableStateOf("gallery") }
  var draftCaption by remember { mutableStateOf("") }
  var draftDescription by remember { mutableStateOf("") }
  var draftAttachGps by remember { mutableStateOf(false) }
  var draftUseCustomPin by remember { mutableStateOf(selectedMapPoint != null) }
  var draftPublic by remember { mutableStateOf(defaultSharePublic) }
  var isSuggestingCaption by remember { mutableStateOf(false) }
  var isUploading by remember { mutableStateOf(false) }
  var draftError by remember { mutableStateOf<String?>(null) }

  // Edit / Detail dialog states
  var editingPhoto by remember { mutableStateOf<UserPhoto?>(null) }
  var editCaption by remember { mutableStateOf("") }
  var editDescription by remember { mutableStateOf("") }
  var editPublic by remember { mutableStateOf(false) }
  var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val compressed = ImageUtils.compressUriToBase64(context, uri)
          if (compressed != null) {
            draftBase64 = compressed.first
            draftBitmap = compressed.second
            draftSource = "gallery"
            draftCaption = ""
            draftDescription = ""
            draftError = null
          }
        }
      }
    }

  val takePictureLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
      val uri = pendingCameraUri
      if (success && uri != null) {
        scope.launch {
          val compressed = ImageUtils.compressUriToBase64(context, uri)
          if (compressed != null) {
            draftBase64 = compressed.first
            draftBitmap = compressed.second
            draftSource = "camera"
            draftCaption = ""
            draftDescription = ""
            draftError = null
          }
        }
      }
    }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        val uri = ImageUtils.createTempCameraUri(context)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
      }
    }

  val locationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
      val granted =
        result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
      if (granted) {
        draftAttachGps = true
        onRefreshGps()
      }
    }

  val myPhotos = (photosState as? DataUiState.Success)?.data.orEmpty()
  val sharedPhotos = (sharedPhotosState as? DataUiState.Success)?.data.orEmpty()
  val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier.fillMaxSize().testTag("photos_screen"),
  ) {
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("Photo Vault & Uploads", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Upload from device gallery or capture with your camera. GPS tags and public sharing are strictly opt-in.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.weight(1f).testTag("pick_photo_button"),
          ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Pick Photo")
          }
          OutlinedButton(
            onClick = {
              val hasCam =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                  PackageManager.PERMISSION_GRANTED
              if (hasCam) {
                val uri = ImageUtils.createTempCameraUri(context)
                pendingCameraUri = uri
                takePictureLauncher.launch(uri)
              } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
              }
            },
            modifier = Modifier.weight(1f).testTag("take_photo_button"),
          ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Camera")
          }
        }
      }
    }

    item {
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
          Text("My Photos (${myPhotos.size})")
        }
        SegmentedButton(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
          Text("Public Opt-In (${sharedPhotos.size})")
        }
      }
    }

    if (selectedTab == 0) {
      if (photosState is DataUiState.Loading) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.Center,
          ) {
            CircularProgressIndicator()
          }
        }
      } else if (myPhotos.isEmpty()) {
        item {
          EmptyStatePanel(
            icon = Icons.Filled.AddPhotoAlternate,
            title = "Your Photo Vault Is Empty",
            subtitle = "Choose a photo from your phone or snap one with the camera to tag GPS coordinates and generate AI descriptions.",
            actionLabel = "Upload First Photo",
            onAction = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
          )
        }
      } else {
        items(myPhotos, key = { it.id }) { photo ->
          GlassCard(modifier = Modifier.fillMaxWidth()) {
            Base64PhotoImage(
              base64 = photo.imageBase64,
              contentDescription = photo.caption,
              modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = photo.caption.ifBlank { "Untitled Photo" },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
              )
              Row {
                IconButton(
                  onClick = {
                    editingPhoto = photo
                    editCaption = photo.caption
                    editDescription = photo.description
                    editPublic = photo.isPublic
                  }
                ) {
                  Icon(Icons.Filled.Edit, contentDescription = "Edit caption")
                }
                IconButton(onClick = { onDeletePhoto(photo.id) }) {
                  Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete photo",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
            if (photo.description.isNotBlank()) {
              Text(
                text = photo.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatusPill(label = photo.source.uppercase(), dotColor = NeonCyan)
              if (photo.isPublic) {
                StatusPill(label = "PUBLIC", dotColor = SuccessGreen)
              }
            }
            val ts = photo.createdAt?.toDate() ?: Date()
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Uploaded ${dateFormatter.format(ts)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (photo.hasLocation && photo.latitude != null && photo.longitude != null) {
              Spacer(modifier = Modifier.height(6.dp))
              TextButton(
                onClick = { onOpenLocationOnMap(photo.latitude, photo.longitude) },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
              ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text =
                    photo.locationLabel
                      ?: "${String.format("%.4f", photo.latitude)}, ${String.format("%.4f", photo.longitude)}",
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }

            if (!photo.aiSummary.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "AI Insight: ${photo.aiSummary}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              OutlinedButton(
                onClick = { onAnalyzeWithAi(photo) },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AI Describe")
              }
              OutlinedButton(
                onClick = { onAskAiAboutPhoto(photo) },
                modifier = Modifier.weight(1f),
              ) {
                Text("Ask AI")
              }
            }
          }
        }
      }
    } else {
      if (sharedPhotos.isEmpty()) {
        item {
          EmptyStatePanel(
            icon = Icons.Filled.Public,
            title = "No Public Opt-In Photos Yet",
            subtitle = "Photos stay private by default. Enable 'Share Publicly' when uploading or editing a photo to showcase it here.",
          )
        }
      } else {
        items(sharedPhotos, key = { it.id }) { shared ->
          GlassCard(modifier = Modifier.fillMaxWidth()) {
            Base64PhotoImage(
              base64 = shared.imageBase64,
              contentDescription = shared.caption,
              modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(shared.caption.ifBlank { "Shared Photo" }, style = MaterialTheme.typography.titleLarge)
            Text(
              text = "By ${shared.authorName} • ${shared.source.uppercase()}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            if (shared.description.isNotBlank()) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = shared.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }

  // Upload Draft Dialog
  if (draftBase64 != null && draftBitmap != null) {
    AlertDialog(
      onDismissRequest = {
        if (!isUploading) {
          draftBase64 = null
          draftBitmap = null
        }
      },
      title = { Text("Save Photo to Cloud") },
      text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          Image(
            bitmap = draftBitmap!!.asImageBitmap(),
            contentDescription = "Selected photo preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = {
              isSuggestingCaption = true
              draftError = null
              onSuggestCaption(draftBitmap!!) { res ->
                isSuggestingCaption = false
                res.onSuccess { (cap, desc) ->
                  draftCaption = cap
                  draftDescription = desc
                }.onFailure { err ->
                  draftError = err.localizedMessage ?: "AI caption failed"
                }
              }
            },
            enabled = !isSuggestingCaption,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isSuggestingCaption) "Generating AI Caption..." else "AI Suggest Caption")
          }
          if (!draftError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = draftError.orEmpty(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = draftCaption,
            onValueChange = { draftCaption = it },
            label = { Text("Caption") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = draftDescription,
            onValueChange = { draftDescription = it },
            label = { Text("Description") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Attach Current GPS", style = MaterialTheme.typography.bodyMedium)
            Switch(
              checked = draftAttachGps,
              onCheckedChange = { checked ->
                if (checked) {
                  val hasLoc =
                    ContextCompat.checkSelfPermission(
                      context,
                      Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED ||
                      ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                      ) == PackageManager.PERMISSION_GRANTED
                  if (hasLoc) {
                    draftAttachGps = true
                    onRefreshGps()
                  } else {
                    locationPermissionLauncher.launch(
                      arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                      )
                    )
                  }
                } else {
                  draftAttachGps = false
                }
              },
            )
          }
          if (selectedMapPoint != null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Use Map Pin (${selectedMapPoint.label.take(18)})",
                style = MaterialTheme.typography.bodyMedium,
              )
              Switch(checked = draftUseCustomPin, onCheckedChange = { draftUseCustomPin = it })
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Share Publicly (Opt-In)", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = draftPublic, onCheckedChange = { draftPublic = it })
          }
        }
      },
      confirmButton = {
        Button(
          enabled = !isUploading,
          onClick = {
            isUploading = true
            onUploadPhoto(
              draftCaption,
              draftDescription,
              draftBase64!!,
              draftSource,
              draftAttachGps,
              if (draftUseCustomPin) selectedMapPoint else null,
              draftPublic,
            ) { ok ->
              isUploading = false
              if (ok) {
                draftBase64 = null
                draftBitmap = null
              }
            }
          },
        ) {
          Text(if (isUploading) "Saving..." else "Upload")
        }
      },
      dismissButton = {
        TextButton(
          enabled = !isUploading,
          onClick = {
            draftBase64 = null
            draftBitmap = null
          },
        ) {
          Text("Cancel")
        }
      },
    )
  }

  // Edit Photo Metadata Dialog
  val photoToEdit = editingPhoto
  if (photoToEdit != null) {
    AlertDialog(
      onDismissRequest = { editingPhoto = null },
      title = { Text("Edit Photo Details") },
      text = {
        Column {
          OutlinedTextField(
            value = editCaption,
            onValueChange = { editCaption = it },
            label = { Text("Caption") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = editDescription,
            onValueChange = { editDescription = it },
            label = { Text("Description") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Public Opt-In Gallery", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = editPublic, onCheckedChange = { editPublic = it })
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onUpdatePhoto(photoToEdit, editCaption, editDescription, editPublic)
            editingPhoto = null
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { editingPhoto = null }) { Text("Cancel") }
      },
    )
  }
}

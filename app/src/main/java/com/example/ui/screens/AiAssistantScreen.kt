package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AiMessage
import com.example.data.model.UserPhoto
import com.example.ui.components.Base64PhotoImage
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.viewmodel.AiStreamState
import com.example.ui.viewmodel.DataUiState
import com.example.util.ImageUtils
import kotlinx.coroutines.launch

@Composable
fun AiAssistantScreen(
  messagesState: DataUiState<List<AiMessage>>,
  aiStreamState: AiStreamState,
  photosState: DataUiState<List<UserPhoto>>,
  preselectedPhoto: UserPhoto?,
  hasLocationContext: Boolean,
  onClearPreselectedPhoto: () -> Unit,
  onSendMessage: (String, Bitmap?, UserPhoto?, Boolean) -> Unit,
  onClearHistory: () -> Unit,
  onClearError: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()

  var promptInput by remember { mutableStateOf("") }
  var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var attachedGalleryPhoto by remember { mutableStateOf<UserPhoto?>(preselectedPhoto) }
  var includeLocation by remember { mutableStateOf(hasLocationContext) }
  var showGalleryPicker by remember { mutableStateOf(false) }

  LaunchedEffect(preselectedPhoto) {
    if (preselectedPhoto != null) {
      attachedGalleryPhoto = preselectedPhoto
      pickedBitmap = null
      onClearPreselectedPhoto()
    }
  }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val compressed = ImageUtils.compressUriToBase64(context, uri)
          if (compressed != null) {
            pickedBitmap = compressed.second
            attachedGalleryPhoto = null
          }
        }
      }
    }

  val messages = (messagesState as? DataUiState.Success)?.data.orEmpty()
  val galleryPhotos = (photosState as? DataUiState.Success)?.data.orEmpty()

  LaunchedEffect(messages.size, aiStreamState.streamingText) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Column(modifier = Modifier.fillMaxSize().testTag("ai_assistant_screen")) {
    // Top AI Header & Quick Prompts
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text("Gemini Vision Assistant", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Powered by Firebase AI Logic • gemini-3.8-flash",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        if (messages.isNotEmpty()) {
          IconButton(onClick = onClearHistory) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear AI chat history")
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AssistChip(
          onClick = { promptInput = "Describe the attached image in detail and list key highlights." },
          label = { Text("Describe Photo") },
        )
        AssistChip(
          onClick = { promptInput = "Suggest 3 creative social captions for this photo." },
          label = { Text("Suggest Captions") },
        )
        AssistChip(
          onClick = {
            includeLocation = true
            promptInput = "Tell me useful facts, weather context, and nearby points of interest for my current coordinates."
          },
          label = { Text("Location Info") },
        )
        AssistChip(
          onClick = { promptInput = "Summarize the following text clearly in 3 bullet points:\n" },
          label = { Text("Summarize Text") },
        )
      }
    }

    // Chat Thread
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
      if (messages.isEmpty() && !aiStreamState.isGenerating) {
        item {
          EmptyStatePanel(
            icon = Icons.Filled.AutoAwesome,
            title = "Ready for Multimodal AI",
            subtitle = "Ask questions, attach any gallery or device photo, summarize notes, or explore your GPS coordinates.",
          )
        }
      } else {
        items(messages, key = { it.id }) { msg ->
          AiChatBubble(
            isUser = msg.role == "user",
            text = msg.text,
            attachedCaption = msg.attachedPhotoCaption,
          )
        }
      }

      if (aiStreamState.isGenerating) {
        item {
          AiChatBubble(
            isUser = false,
            text = aiStreamState.streamingText.ifBlank { "Thinking..." },
            attachedCaption = null,
            isStreaming = true,
          )
        }
      }
    }

    // Error Banner
    if (!aiStreamState.errorMessage.isNullOrBlank()) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      ) {
        Row(
          modifier = Modifier.padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = aiStreamState.errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = onClearError) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss AI error")
          }
        }
      }
    }

    // Attachment Preview + Composer
    Surface(
      tonalElevation = 6.dp,
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        if (pickedBitmap != null || attachedGalleryPhoto != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
          ) {
            if (pickedBitmap != null) {
              Image(
                bitmap = pickedBitmap!!.asImageBitmap(),
                contentDescription = "Attached image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Device photo attached", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            } else if (attachedGalleryPhoto != null) {
              Base64PhotoImage(
                base64 = attachedGalleryPhoto!!.imageBase64,
                contentDescription = attachedGalleryPhoto!!.caption,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Gallery: ${attachedGalleryPhoto!!.caption}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
              )
            }
            IconButton(
              onClick = {
                pickedBitmap = null
                attachedGalleryPhoto = null
              }
            ) {
              Icon(Icons.Filled.Close, contentDescription = "Remove attachment")
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(bottom = 6.dp),
        ) {
          FilterChip(
            selected = includeLocation,
            onClick = { includeLocation = !includeLocation },
            label = { Text("Include GPS Context") },
            leadingIcon = {
              Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
            },
          )
          AssistChip(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            label = { Text("Device Photo") },
            leadingIcon = {
              Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
            },
          )
          AssistChip(
            onClick = { showGalleryPicker = true },
            label = { Text("Gallery") },
            leadingIcon = {
              Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
            },
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          OutlinedTextField(
            value = promptInput,
            onValueChange = { promptInput = it },
            placeholder = { Text("Ask AI about photos, text, or map...") },
            maxLines = 3,
            modifier = Modifier.weight(1f).testTag("ai_prompt_input"),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(50.dp),
          ) {
            IconButton(
              enabled = !aiStreamState.isGenerating && promptInput.isNotBlank(),
              onClick = {
                val p = promptInput
                val bmp = pickedBitmap
                val photo = attachedGalleryPhoto
                promptInput = ""
                pickedBitmap = null
                attachedGalleryPhoto = null
                onSendMessage(p, bmp, photo, includeLocation)
              },
              modifier = Modifier.testTag("ai_send_button"),
            ) {
              if (aiStreamState.isGenerating) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = "Send AI prompt",
                  tint = MaterialTheme.colorScheme.onPrimary,
                )
              }
            }
          }
        }
      }
    }
  }

  if (showGalleryPicker) {
    AlertDialog(
      onDismissRequest = { showGalleryPicker = false },
      title = { Text("Select Photo from Gallery") },
      text = {
        if (galleryPhotos.isEmpty()) {
          Text("No uploaded photos in your gallery yet.")
        } else {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(galleryPhotos, key = { it.id }) { photo ->
              Column(
                modifier =
                  Modifier.width(120.dp).clickable {
                    attachedGalleryPhoto = photo
                    pickedBitmap = null
                    showGalleryPicker = false
                  }
              ) {
                Base64PhotoImage(
                  base64 = photo.imageBase64,
                  contentDescription = photo.caption,
                  modifier = Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = photo.caption.ifBlank { "Untitled" },
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showGalleryPicker = false }) { Text("Close") }
      },
    )
  }
}

@Composable
private fun AiChatBubble(
  isUser: Boolean,
  text: String,
  attachedCaption: String?,
  isStreaming: Boolean = false,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      shape =
        RoundedCornerShape(
          topStart = 18.dp,
          topEnd = 18.dp,
          bottomStart = if (isUser) 18.dp else 4.dp,
          bottomEnd = if (isUser) 4.dp else 18.dp,
        ),
      color =
        if (isUser) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
      border =
        BorderStroke(
          1.dp,
          if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
          else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        ),
      modifier = Modifier.fillMaxWidth(0.88f),
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        if (!attachedCaption.isNullOrBlank()) {
          Text(
            text = "Attached: $attachedCaption",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
          text = text,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (isStreaming) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Streaming response...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

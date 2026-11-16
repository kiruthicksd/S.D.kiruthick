package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiMessage
import com.example.data.model.AppNotification
import com.example.data.model.LocationEntry
import com.example.data.model.SharedPhoto
import com.example.data.model.UserPhoto
import com.example.data.model.UserProfile
import com.example.data.repository.AppSettings
import com.example.data.repository.FirestoreAppRepository
import com.example.data.repository.SettingsRepository
import com.example.util.Esp32CamClient
import com.example.util.GpsReading
import com.example.util.ImageUtils
import com.example.util.LocationHelper
import com.example.util.SystemNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed interface DataUiState<out T> {
  data object Loading : DataUiState<Nothing>
  data class Success<T>(val data: T) : DataUiState<T>
  data class Error(val message: String) : DataUiState<Nothing>
}

data class GpsUiState(
  val isLoading: Boolean = false,
  val hasPermission: Boolean = false,
  val reading: GpsReading? = null,
  val errorMessage: String? = null,
)

data class SelectedMapPoint(
  val latitude: Double,
  val longitude: Double,
  val label: String,
)

enum class Esp32Status {
  OFFLINE,
  CONNECTING,
  STREAMING,
  ERROR,
}

data class Esp32UiState(
  val status: Esp32Status = Esp32Status.OFFLINE,
  val addressInput: String = "",
  val streamUrl: String = "",
  val currentFrame: Bitmap? = null,
  val lastSnapshot: Bitmap? = null,
  val framesReceived: Long = 0L,
  val errorMessage: String? = null,
)

data class AiStreamState(
  val isGenerating: Boolean = false,
  val streamingText: String = "",
  val errorMessage: String? = null,
)

class MainAppViewModel(
  private val repo: FirestoreAppRepository,
  private val settingsRepo: SettingsRepository,
  private val currentUserId: String,
  private val initialDisplayName: String,
  private val initialEmail: String,
) : ViewModel() {

  private fun Throwable.unwrapFirestoreMessage(fallback: String): String {
    val firestoreError =
      generateSequence(this) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    return firestoreError?.message ?: message ?: fallback
  }

  private fun Throwable.shouldRethrowCancellation(): Boolean {
    val firestoreError =
      generateSequence(this) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    return firestoreError == null && this is CancellationException
  }

  val appSettings: StateFlow<AppSettings> =
    settingsRepo.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = AppSettings(),
    )

  val userProfile: StateFlow<UserProfile?> =
    repo
      .observeUserProfile(currentUserId)
      .catch { e ->
        if (e.shouldRethrowCancellation()) throw e
        Log.w(TAG, "Profile stream error", e)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val photosState: StateFlow<DataUiState<List<UserPhoto>>> =
    repo
      .observePhotos(currentUserId)
      .map<List<UserPhoto>, DataUiState<List<UserPhoto>>> { DataUiState.Success(it) }
      .catch { e ->
        if (e.shouldRethrowCancellation()) throw e
        emit(DataUiState.Error(e.unwrapFirestoreMessage("Failed to load photos")))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

  val sharedPhotosState: StateFlow<DataUiState<List<SharedPhoto>>> =
    repo
      .observeSharedPhotos(currentUserId)
      .map<List<SharedPhoto>, DataUiState<List<SharedPhoto>>> { DataUiState.Success(it) }
      .catch { e ->
        if (e.shouldRethrowCancellation()) throw e
        emit(DataUiState.Error(e.unwrapFirestoreMessage("Failed to load community photos")))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

  val locationHistoryState: StateFlow<DataUiState<List<LocationEntry>>> =
    repo
      .observeLocationHistory(currentUserId)
      .map<List<LocationEntry>, DataUiState<List<LocationEntry>>> { DataUiState.Success(it) }
      .catch { e ->
        if (e.shouldRethrowCancellation()) throw e
        emit(DataUiState.Error(e.unwrapFirestoreMessage("Failed to load location history")))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

  val notificationsState: StateFlow<DataUiState<List<AppNotification>>> =
    repo
      .observeNotifications(currentUserId)
      .map<List<AppNotification>, DataUiState<List<AppNotification>>> { DataUiState.Success(it) }
      .catch { e ->
        if (e.shouldRethrowCancellation()) throw e
        emit(DataUiState.Error(e.unwrapFirestoreMessage("Failed to load notifications")))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

  val aiMessagesState: StateFlow<DataUiState<List<AiMessage>>> =
    repo
      .observeAiMessages(currentUserId)
      .map<List<AiMessage>, DataUiState<List<AiMessage>>> { DataUiState.Success(it) }
      .catch { e ->
        if (e.shouldRethrowCancellation()) throw e
        emit(DataUiState.Error(e.unwrapFirestoreMessage("Failed to load AI history")))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

  private val _gpsState = MutableStateFlow(GpsUiState())
  val gpsState: StateFlow<GpsUiState> = _gpsState.asStateFlow()

  private val _selectedMapPoint = MutableStateFlow<SelectedMapPoint?>(null)
  val selectedMapPoint: StateFlow<SelectedMapPoint?> = _selectedMapPoint.asStateFlow()

  private val _esp32State = MutableStateFlow(Esp32UiState())
  val esp32State: StateFlow<Esp32UiState> = _esp32State.asStateFlow()

  private val _aiStreamState = MutableStateFlow(AiStreamState())
  val aiStreamState: StateFlow<AiStreamState> = _aiStreamState.asStateFlow()

  private val _bannerMessage = MutableStateFlow<String?>(null)
  val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

  private var esp32StreamJob: Job? = null

  private val geminiModel by lazy { Firebase.ai.generativeModel("gemini-3.8-flash") }

  private val captionSchema =
    Schema.obj(
      properties =
        mapOf(
          "caption" to Schema.string(description = "A concise 3-8 word photo title"),
          "description" to
            Schema.string(description = "A helpful 1-2 sentence description of the scene"),
        )
    )

  private val captionModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = captionSchema
        },
    )
  }

  init {
    viewModelScope.launch {
      repo.ensureUserProfile(currentUserId, initialDisplayName, initialEmail)
      settingsRepo.settingsFlow.collect { s ->
        if (_esp32State.value.addressInput.isBlank() && s.esp32Address.isNotBlank()) {
          _esp32State.value = _esp32State.value.copy(addressInput = s.esp32Address)
        }
      }
    }
  }

  fun clearBanner() {
    _bannerMessage.value = null
  }

  private suspend fun notifyEvent(
    context: Context?,
    title: String,
    message: String,
    category: String,
  ) {
    repo.addNotification(currentUserId, title, message, category)
    if (context != null && appSettings.value.systemNotificationsEnabled) {
      SystemNotificationHelper.postNotificationIfPermitted(context, title, message)
    }
  }

  // --- GPS & Map ---
  fun setLocationPermissionGranted(granted: Boolean) {
    _gpsState.value = _gpsState.value.copy(hasPermission = granted)
  }

  fun refreshGpsLocation(context: Context) {
    viewModelScope.launch {
      _gpsState.value = _gpsState.value.copy(isLoading = true, errorMessage = null)
      val result = LocationHelper.fetchCurrentLocation(context)
      result.fold(
        onSuccess = { reading ->
          _gpsState.value =
            _gpsState.value.copy(
              isLoading = false,
              hasPermission = true,
              reading = reading,
              errorMessage = null,
            )
        },
        onFailure = { err ->
          _gpsState.value =
            _gpsState.value.copy(
              isLoading = false,
              errorMessage = err.localizedMessage ?: "Failed to read GPS coordinates.",
            )
        },
      )
    }
  }

  fun selectMapPoint(context: Context, latitude: Double, longitude: Double) {
    _selectedMapPoint.value =
      SelectedMapPoint(
        latitude = latitude,
        longitude = longitude,
        label = String.format("%.5f, %.5f", latitude, longitude),
      )
    viewModelScope.launch {
      val geoLabel = LocationHelper.reverseGeocodeLabel(context, latitude, longitude)
      if (!geoLabel.isNullOrBlank()) {
        _selectedMapPoint.value =
          SelectedMapPoint(latitude = latitude, longitude = longitude, label = geoLabel)
      }
    }
  }

  fun clearSelectedMapPoint() {
    _selectedMapPoint.value = null
  }

  fun saveLocationCheckIn(
    context: Context,
    label: String,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double,
  ) {
    viewModelScope.launch {
      val res = repo.addLocationEntry(currentUserId, label, latitude, longitude, accuracyMeters)
      res.fold(
        onSuccess = {
          notifyEvent(
            context,
            "Location Pin Saved",
            "Saved '$label' (${String.format("%.4f, %.4f", latitude, longitude)})",
            "map",
          )
          _bannerMessage.value = "Saved location check-in to history."
        },
        onFailure = { e -> _bannerMessage.value = e.localizedMessage ?: "Failed to save location." },
      )
    }
  }

  fun deleteLocationEntry(entryId: String) {
    viewModelScope.launch { repo.deleteLocationEntry(currentUserId, entryId) }
  }

  fun clearLocationHistory() {
    viewModelScope.launch {
      repo.clearLocationHistory(currentUserId)
      _bannerMessage.value = "Location history cleared."
    }
  }

  // --- Photos ---
  fun uploadPhoto(
    context: Context,
    caption: String,
    description: String,
    imageBase64: String,
    source: String,
    attachCurrentGps: Boolean,
    customLatLng: SelectedMapPoint?,
    isPublic: Boolean,
    onDone: (Boolean) -> Unit,
  ) {
    viewModelScope.launch {
      val reading = _gpsState.value.reading
      val lat =
        when {
          customLatLng != null -> customLatLng.latitude
          attachCurrentGps && reading != null -> reading.latitude
          else -> null
        }
      val lng =
        when {
          customLatLng != null -> customLatLng.longitude
          attachCurrentGps && reading != null -> reading.longitude
          else -> null
        }
      val locLabel =
        when {
          customLatLng != null -> customLatLng.label
          attachCurrentGps && reading != null -> reading.label
          else -> null
        }
      val hasLoc = lat != null && lng != null
      val authorName =
        userProfile.value?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName

      val result =
        repo.addPhoto(
          userId = currentUserId,
          authorName = authorName,
          caption = caption.ifBlank { "Captured Photo" },
          description = description,
          imageBase64 = imageBase64,
          source = source,
          hasLocation = hasLoc,
          latitude = lat,
          longitude = lng,
          locationLabel = locLabel,
          isPublic = isPublic,
        )
      result.fold(
        onSuccess = {
          notifyEvent(
            context,
            "Photo Uploaded",
            "Saved '${caption.ifBlank { "Captured Photo" }}' from $source.",
            "photo",
          )
          _bannerMessage.value = "Photo saved to cloud gallery."
          onDone(true)
        },
        onFailure = { e ->
          _bannerMessage.value = e.localizedMessage ?: "Photo upload failed."
          onDone(false)
        },
      )
    }
  }

  fun updatePhotoMetadata(
    photo: UserPhoto,
    newCaption: String,
    newDescription: String,
    newIsPublic: Boolean,
  ) {
    viewModelScope.launch {
      val authorName =
        userProfile.value?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName
      repo
        .updatePhoto(
          userId = currentUserId,
          authorName = authorName,
          photo = photo,
          caption = newCaption,
          description = newDescription,
          aiSummary = photo.aiSummary,
          isPublic = newIsPublic,
        )
        .onSuccess { _bannerMessage.value = "Photo updated." }
        .onFailure { _bannerMessage.value = it.localizedMessage ?: "Failed to update photo." }
    }
  }

  fun deletePhoto(photoId: String) {
    viewModelScope.launch {
      repo
        .deletePhoto(currentUserId, photoId)
        .onSuccess { _bannerMessage.value = "Photo deleted." }
        .onFailure { _bannerMessage.value = it.localizedMessage ?: "Failed to delete photo." }
    }
  }

  // --- ESP32-CAM ---
  fun updateEsp32AddressInput(address: String) {
    _esp32State.value = _esp32State.value.copy(addressInput = address)
  }

  fun connectEsp32(context: Context, addressInput: String) {
    val clean = addressInput.trim()
    if (clean.isEmpty()) {
      _esp32State.value =
        _esp32State.value.copy(
          status = Esp32Status.ERROR,
          errorMessage = "Enter your ESP32-CAM local IP address or stream URL first.",
        )
      return
    }
    val streamUrl = Esp32CamClient.resolveStreamUrl(clean)
    esp32StreamJob?.cancel()
    viewModelScope.launch { settingsRepo.setEsp32Address(clean) }

    _esp32State.value =
      _esp32State.value.copy(
        status = Esp32Status.CONNECTING,
        addressInput = clean,
        streamUrl = streamUrl,
        framesReceived = 0L,
        errorMessage = null,
      )

    esp32StreamJob =
      viewModelScope.launch {
        var notifiedConnected = false
        try {
          Esp32CamClient.streamMjpegFrames(streamUrl).collect { frame ->
            val count = _esp32State.value.framesReceived + 1
            _esp32State.value =
              _esp32State.value.copy(
                status = Esp32Status.STREAMING,
                currentFrame = frame,
                framesReceived = count,
                errorMessage = null,
              )
            if (!notifiedConnected) {
              notifiedConnected = true
              notifyEvent(
                context,
                "ESP32-CAM Connected",
                "Live MJPEG stream active at $streamUrl",
                "camera",
              )
            }
          }
          if (_esp32State.value.status != Esp32Status.OFFLINE) {
            _esp32State.value =
              _esp32State.value.copy(
                status = Esp32Status.OFFLINE,
                errorMessage = "Camera stream ended.",
              )
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          val msg = e.localizedMessage ?: "Unable to connect to ESP32-CAM at $streamUrl"
          _esp32State.value =
            _esp32State.value.copy(status = Esp32Status.ERROR, errorMessage = msg)
          notifyEvent(context, "ESP32-CAM Connection Error", msg, "camera")
        }
      }
  }

  fun disconnectEsp32(context: Context) {
    esp32StreamJob?.cancel()
    esp32StreamJob = null
    _esp32State.value =
      _esp32State.value.copy(status = Esp32Status.OFFLINE, errorMessage = null)
    viewModelScope.launch {
      notifyEvent(context, "ESP32-CAM Disconnected", "Camera stream closed by user.", "camera")
    }
  }

  fun captureEsp32Snapshot(context: Context) {
    val currentAddr = _esp32State.value.addressInput.trim()
    val liveFrame = _esp32State.value.currentFrame
    if (currentAddr.isEmpty() && liveFrame == null) {
      _esp32State.value =
        _esp32State.value.copy(
          status = Esp32Status.ERROR,
          errorMessage = "Enter ESP32-CAM IP address before capturing.",
        )
      return
    }
    viewModelScope.launch {
      val captureUrl = Esp32CamClient.resolveCaptureUrl(currentAddr)
      val res =
        if (captureUrl.isNotBlank()) Esp32CamClient.captureSingleFrame(captureUrl)
        else Result.failure(IllegalStateException("No camera URL"))
      res.fold(
        onSuccess = { bmp ->
          _esp32State.value = _esp32State.value.copy(lastSnapshot = bmp, errorMessage = null)
          _bannerMessage.value = "Snapshot captured from ESP32-CAM."
        },
        onFailure = { err ->
          if (liveFrame != null) {
            _esp32State.value =
              _esp32State.value.copy(lastSnapshot = liveFrame, errorMessage = null)
            _bannerMessage.value = "Captured current stream frame."
          } else {
            val msg = err.localizedMessage ?: "Snapshot failed from $captureUrl"
            _esp32State.value =
              _esp32State.value.copy(status = Esp32Status.ERROR, errorMessage = msg)
            notifyEvent(context, "ESP32-CAM Capture Failed", msg, "camera")
          }
        },
      )
    }
  }

  fun saveEsp32SnapshotToGallery(
    context: Context,
    bitmap: Bitmap,
    caption: String,
    attachGps: Boolean,
  ) {
    viewModelScope.launch {
      val (base64, _) = ImageUtils.compressBitmapToBase64(bitmap)
      uploadPhoto(
        context = context,
        caption = caption.ifBlank { "ESP32-CAM Snapshot" },
        description = "Captured from ESP32-CAM (${_esp32State.value.addressInput})",
        imageBase64 = base64,
        source = "esp32",
        attachCurrentGps = attachGps,
        customLatLng = null,
        isPublic = appSettings.value.defaultSharePublic,
        onDone = {},
      )
    }
  }

  // --- Firebase AI Logic (Gemini 3.8 Flash) ---
  fun suggestCaptionForBitmap(bitmap: Bitmap, onResult: (Result<Pair<String, String>>) -> Unit) {
    viewModelScope.launch {
      try {
        val tone = appSettings.value.aiTone
        val response =
          withTimeout(60_000L) {
            captionModel.generateContent(
              content {
                image(bitmap)
                text(
                  "Analyze this photo in a $tone tone. Return JSON with a concise 'caption' (3-8 words) and a helpful 'description' (1-2 sentences)."
                )
              }
            )
          }
        val jsonStr = response.text ?: throw IllegalStateException("AI returned empty response.")
        val json = JSONObject(jsonStr)
        val cap = json.optString("caption", "Captured Scene").take(120)
        val desc = json.optString("description", "").take(600)
        onResult(Result.success(cap to desc))
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Caption suggestion timed out", e)
        onResult(Result.failure(Exception("AI request timed out after 60 seconds.")))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Caption suggestion failed", e)
        onResult(Result.failure(e))
      }
    }
  }

  fun analyzePhotoWithAi(context: Context, photo: UserPhoto) {
    viewModelScope.launch {
      val bmp = ImageUtils.decodeBase64ToBitmap(photo.imageBase64)
      if (bmp == null) {
        _bannerMessage.value = "Could not decode photo for AI analysis."
        return@launch
      }
      _aiStreamState.value = AiStreamState(isGenerating = true, streamingText = "Analyzing photo...")
      try {
        val locNote =
          if (photo.hasLocation && photo.latitude != null && photo.longitude != null) {
            " Location metadata: ${photo.locationLabel ?: ""} (${photo.latitude}, ${photo.longitude})."
          } else ""
        val response =
          withTimeout(60_000L) {
            geminiModel.generateContent(
              content {
                image(bmp)
                text(
                  "Provide a clear, insightful description and 3 key visual observations for this photo titled '${photo.caption}'.${locNote}"
                )
              }
            )
          }
        val summary = response.text?.trim().orEmpty()
        if (summary.isBlank()) throw IllegalStateException("AI returned an empty analysis.")

        val authorName =
          userProfile.value?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName
        repo.updatePhoto(
          userId = currentUserId,
          authorName = authorName,
          photo = photo,
          caption = photo.caption,
          description = photo.description,
          aiSummary = summary,
          isPublic = photo.isPublic,
        )
        notifyEvent(
          context,
          "AI Photo Analysis Ready",
          "Generated insights for '${photo.caption}'.",
          "ai",
        )
        _aiStreamState.value = AiStreamState(isGenerating = false)
        _bannerMessage.value = "AI analysis saved to photo."
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Photo AI analysis timed out", e)
        _aiStreamState.value =
          AiStreamState(isGenerating = false, errorMessage = "AI analysis timed out after 60s.")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Photo AI analysis failed", e)
        _aiStreamState.value =
          AiStreamState(
            isGenerating = false,
            errorMessage = e.localizedMessage ?: "AI analysis failed.",
          )
      }
    }
  }

  fun sendAiChatMessage(
    context: Context,
    prompt: String,
    attachedBitmap: Bitmap? = null,
    attachedPhoto: UserPhoto? = null,
    includeLocationContext: Boolean = false,
  ) {
    val cleanPrompt = prompt.trim()
    if (cleanPrompt.isEmpty() || _aiStreamState.value.isGenerating) return

    viewModelScope.launch {
      _aiStreamState.value = AiStreamState(isGenerating = true, streamingText = "")
      repo.addAiMessage(
        userId = currentUserId,
        role = "user",
        text = cleanPrompt,
        attachedPhotoId = attachedPhoto?.id,
        attachedPhotoCaption = attachedPhoto?.caption,
      )

      val accumulated = StringBuilder()
      try {
        val tone = appSettings.value.aiTone
        val gps = _gpsState.value.reading
        val pin = _selectedMapPoint.value
        val contextHint = buildString {
          append("Respond in a $tone, helpful style. ")
          if (includeLocationContext) {
            if (pin != null) {
              append(
                "Selected map point: ${pin.label} (${String.format("%.5f", pin.latitude)}, ${String.format("%.5f", pin.longitude)}). "
              )
            } else if (gps != null) {
              append(
                "Current user GPS: ${gps.label ?: "Unknown place"} (${String.format("%.5f", gps.latitude)}, ${String.format("%.5f", gps.longitude)}, accuracy ${gps.accuracyMeters.toInt()}m). "
              )
            }
          }
          if (attachedPhoto != null) {
            append("Attached gallery photo title: '${attachedPhoto.caption}'. ")
            if (attachedPhoto.description.isNotBlank()) {
              append("User note: '${attachedPhoto.description}'. ")
            }
          }
        }

        val bitmapForVision =
          attachedBitmap ?: attachedPhoto?.let { ImageUtils.decodeBase64ToBitmap(it.imageBase64) }

        val inputContent = content {
          if (bitmapForVision != null) {
            image(bitmapForVision)
          }
          text("$contextHint\nUser request: $cleanPrompt")
        }

        withTimeout(60_000L) {
          geminiModel.generateContentStream(inputContent).collect { chunk ->
            val chunkText = chunk.text.orEmpty()
            accumulated.append(chunkText)
            _aiStreamState.value =
              AiStreamState(isGenerating = true, streamingText = accumulated.toString())
          }
        }

        val finalReply = accumulated.toString().trim()
        if (finalReply.isBlank()) {
          _aiStreamState.value =
            AiStreamState(isGenerating = false, errorMessage = "AI returned an empty response.")
        } else {
          repo.addAiMessage(userId = currentUserId, role = "model", text = finalReply)
          notifyEvent(
            context,
            "AI Assistant Replied",
            finalReply.take(90) + if (finalReply.length > 90) "..." else "",
            "ai",
          )
          _aiStreamState.value = AiStreamState(isGenerating = false, streamingText = "")
        }
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "AI chat timed out", e)
        _aiStreamState.value =
          AiStreamState(
            isGenerating = false,
            errorMessage = "AI response timed out after 60 seconds. Please try again.",
          )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "AI chat failed", e)
        _aiStreamState.value =
          AiStreamState(
            isGenerating = false,
            errorMessage = e.localizedMessage ?: "AI request failed.",
          )
      }
    }
  }

  fun clearAiError() {
    _aiStreamState.value = _aiStreamState.value.copy(errorMessage = null)
  }

  fun clearAiHistory() {
    viewModelScope.launch {
      repo.clearAiMessages(currentUserId)
      _bannerMessage.value = "AI conversation history cleared."
    }
  }

  // --- Notifications ---
  fun toggleNotificationRead(notification: AppNotification) {
    viewModelScope.launch {
      repo.markNotificationRead(currentUserId, notification.id, !notification.isRead)
    }
  }

  fun markAllNotificationsRead() {
    viewModelScope.launch { repo.markAllNotificationsRead(currentUserId) }
  }

  fun clearAllNotifications() {
    viewModelScope.launch {
      repo.clearNotifications(currentUserId)
      _bannerMessage.value = "Notifications cleared."
    }
  }

  // --- Profile & Settings ---
  fun updateUserProfile(
    context: Context,
    displayName: String,
    bio: String,
    avatarBase64: String? = null,
  ) {
    viewModelScope.launch {
      repo
        .updateUserProfile(currentUserId, displayName, bio, avatarBase64)
        .onSuccess {
          notifyEvent(context, "Profile Updated", "Saved changes for $displayName.", "account")
          _bannerMessage.value = "Profile updated."
        }
        .onFailure { _bannerMessage.value = it.localizedMessage ?: "Failed to update profile." }
    }
  }

  fun setDarkTheme(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setDarkTheme(enabled) }
  }

  fun setAiTone(tone: String) {
    viewModelScope.launch { settingsRepo.setAiTone(tone) }
  }

  fun setAutoSuggestCaptions(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setAutoSuggestCaptions(enabled) }
  }

  fun setSystemNotificationsEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setSystemNotificationsEnabled(enabled) }
  }

  fun setDefaultSharePublic(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setDefaultSharePublic(enabled) }
  }

  fun deleteAccountDataAndSignOut(onComplete: () -> Unit) {
    viewModelScope.launch {
      repo.deleteAllUserData(currentUserId)
      onComplete()
    }
  }

  override fun onCleared() {
    super.onCleared()
    esp32StreamJob?.cancel()
  }

  private companion object {
    const val TAG = "MainAppViewModel"
  }
}

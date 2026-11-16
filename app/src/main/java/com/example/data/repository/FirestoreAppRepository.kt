package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AiMessage
import com.example.data.model.AppNotification
import com.example.data.model.LocationEntry
import com.example.data.model.SharedPhoto
import com.example.data.model.UserPhoto
import com.example.data.model.UserProfile
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreAppRepository(private val db: FirebaseFirestore) {

  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun userDoc(userId: String) = db.collection("users").document(userId)

  private fun photosCol(userId: String) = userDoc(userId).collection("photos")

  private fun locationsCol(userId: String) = userDoc(userId).collection("location_history")

  private fun notificationsCol(userId: String) = userDoc(userId).collection("notifications")

  private fun aiMessagesCol(userId: String) = userDoc(userId).collection("ai_messages")

  private fun sharedPhotosCol() = db.collection("shared_photos")

  private fun logIfSignedIn(error: Throwable, op: OperationType, path: String, userId: String) {
    val firestoreError =
      generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val toLog = firestoreError ?: (error as? Exception)
    if (toLog != null && auth.currentUser?.uid == userId) {
      handleFirestoreError(toLog, op, path)
    }
  }

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    val docRef = userDoc(userId)
    emitAll(
      docRef
        .snapshots()
        .map { snap ->
          snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { err ->
          logIfSignedIn(err, OperationType.GET, docRef.path, userId)
          throw err
        }
    )
  }

  fun observePhotos(userId: String): Flow<List<UserPhoto>> = flow {
    val col = photosCol(userId)
    emitAll(
      col
        .snapshots()
        .map { snap ->
          snap
            .toObjects(UserPhoto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { err ->
          logIfSignedIn(err, OperationType.LIST, col.path, userId)
          throw err
        }
    )
  }

  fun observeSharedPhotos(userId: String): Flow<List<SharedPhoto>> = flow {
    val col = sharedPhotosCol()
    emitAll(
      col
        .whereEqualTo("isPublic", true)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(SharedPhoto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { err ->
          logIfSignedIn(err, OperationType.LIST, col.path, userId)
          throw err
        }
    )
  }

  fun observeLocationHistory(userId: String): Flow<List<LocationEntry>> = flow {
    val col = locationsCol(userId)
    emitAll(
      col
        .snapshots()
        .map { snap ->
          snap
            .toObjects(LocationEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { err ->
          logIfSignedIn(err, OperationType.LIST, col.path, userId)
          throw err
        }
    )
  }

  fun observeNotifications(userId: String): Flow<List<AppNotification>> = flow {
    val col = notificationsCol(userId)
    emitAll(
      col
        .snapshots()
        .map { snap ->
          snap
            .toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { err ->
          logIfSignedIn(err, OperationType.LIST, col.path, userId)
          throw err
        }
    )
  }

  fun observeAiMessages(userId: String): Flow<List<AiMessage>> = flow {
    val col = aiMessagesCol(userId)
    emitAll(
      col
        .snapshots()
        .map { snap ->
          snap
            .toObjects(AiMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { err ->
          logIfSignedIn(err, OperationType.LIST, col.path, userId)
          throw err
        }
    )
  }

  suspend fun ensureUserProfile(
    userId: String,
    displayName: String,
    email: String,
  ): Result<Unit> {
    val docRef = userDoc(userId)
    return try {
      val snapshot = docRef.get().await()
      if (!snapshot.exists()) {
        val safeName = displayName.trim().ifBlank { "Explorer" }.take(80)
        val payload =
          mapOf(
            "userId" to userId,
            "displayName" to safeName,
            "email" to email.take(160),
            "bio" to "Smart AI & Map Explorer",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(payload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateUserProfile(
    userId: String,
    displayName: String,
    bio: String,
    avatarBase64: String? = null,
  ): Result<Unit> {
    val docRef = userDoc(userId)
    return try {
      val updates =
        mutableMapOf<String, Any>(
          "displayName" to displayName.trim().ifBlank { "Explorer" }.take(80),
          "bio" to bio.trim().take(300),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!avatarBase64.isNullOrBlank()) {
        updates["avatarBase64"] = avatarBase64
      }
      docRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun addPhoto(
    userId: String,
    authorName: String,
    caption: String,
    description: String,
    imageBase64: String,
    source: String,
    hasLocation: Boolean,
    latitude: Double? = null,
    longitude: Double? = null,
    locationLabel: String? = null,
    isPublic: Boolean = false,
    aiSummary: String? = null,
  ): Result<String> {
    val docRef = photosCol(userId).document()
    return try {
      val payload =
        mutableMapOf<String, Any>(
          "userId" to userId,
          "caption" to caption.trim().take(200),
          "description" to description.trim().take(1000),
          "imageBase64" to imageBase64,
          "source" to source,
          "hasLocation" to hasLocation,
          "isPublic" to isPublic,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!aiSummary.isNullOrBlank()) payload["aiSummary"] = aiSummary.take(2000)
      if (hasLocation && latitude != null && longitude != null) {
        payload["latitude"] = latitude
        payload["longitude"] = longitude
      }
      if (!locationLabel.isNullOrBlank()) {
        payload["locationLabel"] = locationLabel.take(120)
      }
      docRef.set(payload).await()

      if (isPublic) {
        val sharedPayload =
          mutableMapOf<String, Any>(
            "userId" to userId,
            "authorName" to authorName.trim().ifBlank { "Explorer" }.take(80),
            "caption" to caption.trim().take(200),
            "description" to description.trim().take(1000),
            "imageBase64" to imageBase64,
            "source" to source,
            "hasLocation" to hasLocation,
            "isPublic" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        if (hasLocation && latitude != null && longitude != null) {
          sharedPayload["latitude"] = latitude
          sharedPayload["longitude"] = longitude
        }
        sharedPhotosCol().document(docRef.id).set(sharedPayload).await()
      }

      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updatePhoto(
    userId: String,
    authorName: String,
    photo: UserPhoto,
    caption: String,
    description: String,
    aiSummary: String? = photo.aiSummary,
    isPublic: Boolean = photo.isPublic,
  ): Result<Unit> {
    val docRef = photosCol(userId).document(photo.id)
    return try {
      val updates =
        mutableMapOf<String, Any>(
          "caption" to caption.trim().take(200),
          "description" to description.trim().take(1000),
          "isPublic" to isPublic,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!aiSummary.isNullOrBlank()) {
        updates["aiSummary"] = aiSummary.take(2000)
      }
      docRef.update(updates).await()

      val sharedRef = sharedPhotosCol().document(photo.id)
      if (isPublic) {
        val existingShared = sharedRef.get().await()
        if (existingShared.exists()) {
          sharedRef
            .update(
              mapOf(
                "authorName" to authorName.trim().ifBlank { "Explorer" }.take(80),
                "caption" to caption.trim().take(200),
                "description" to description.trim().take(1000),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        } else {
          val sharedPayload =
            mutableMapOf<String, Any>(
              "userId" to userId,
              "authorName" to authorName.trim().ifBlank { "Explorer" }.take(80),
              "caption" to caption.trim().take(200),
              "description" to description.trim().take(1000),
              "imageBase64" to photo.imageBase64,
              "source" to photo.source,
              "hasLocation" to photo.hasLocation,
              "isPublic" to true,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          if (photo.hasLocation && photo.latitude != null && photo.longitude != null) {
            sharedPayload["latitude"] = photo.latitude
            sharedPayload["longitude"] = photo.longitude
          }
          sharedRef.set(sharedPayload).await()
        }
      } else {
        sharedRef.delete().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deletePhoto(userId: String, photoId: String): Result<Unit> {
    val docRef = photosCol(userId).document(photoId)
    return try {
      docRef.delete().await()
      try {
        sharedPhotosCol().document(photoId).delete().await()
      } catch (_: Exception) {
        // Ignore if not shared
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun addLocationEntry(
    userId: String,
    label: String,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double,
  ): Result<String> {
    val docRef = locationsCol(userId).document()
    return try {
      val payload =
        mapOf(
          "userId" to userId,
          "label" to label.trim().ifBlank { "Location Pin" }.take(120),
          "latitude" to latitude,
          "longitude" to longitude,
          "accuracyMeters" to accuracyMeters.coerceIn(0.0, 100000.0),
          "createdAt" to FieldValue.serverTimestamp(),
        )
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteLocationEntry(userId: String, entryId: String): Result<Unit> {
    val docRef = locationsCol(userId).document(entryId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun clearLocationHistory(userId: String): Result<Unit> {
    val col = locationsCol(userId)
    return try {
      val docs = col.get().await().documents
      for (doc in docs) doc.reference.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, col.path)
      Result.failure(e)
    }
  }

  suspend fun addNotification(
    userId: String,
    title: String,
    message: String,
    category: String,
  ): Result<String> {
    val docRef = notificationsCol(userId).document()
    return try {
      val payload =
        mapOf(
          "userId" to userId,
          "title" to title.trim().ifBlank { "Update" }.take(120),
          "message" to message.trim().ifBlank { "Activity recorded." }.take(500),
          "category" to category,
          "isRead" to false,
          "createdAt" to FieldValue.serverTimestamp(),
        )
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun markNotificationRead(
    userId: String,
    notificationId: String,
    isRead: Boolean,
  ): Result<Unit> {
    val docRef = notificationsCol(userId).document(notificationId)
    return try {
      docRef.update(mapOf("isRead" to isRead)).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun markAllNotificationsRead(userId: String): Result<Unit> {
    val col = notificationsCol(userId)
    return try {
      val docs = col.get().await().documents
      for (doc in docs) {
        if (doc.getBoolean("isRead") != true) {
          doc.reference.update(mapOf("isRead" to true)).await()
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, col.path)
      Result.failure(e)
    }
  }

  suspend fun clearNotifications(userId: String): Result<Unit> {
    val col = notificationsCol(userId)
    return try {
      val docs = col.get().await().documents
      for (doc in docs) doc.reference.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, col.path)
      Result.failure(e)
    }
  }

  suspend fun addAiMessage(
    userId: String,
    role: String,
    text: String,
    attachedPhotoId: String? = null,
    attachedPhotoCaption: String? = null,
  ): Result<String> {
    val docRef = aiMessagesCol(userId).document()
    return try {
      val payload =
        mutableMapOf<String, Any>(
          "userId" to userId,
          "role" to role,
          "text" to text.trim().ifBlank { "..." }.take(8000),
          "createdAt" to FieldValue.serverTimestamp(),
        )
      if (!attachedPhotoId.isNullOrBlank()) {
        payload["attachedPhotoId"] = attachedPhotoId.take(128)
      }
      if (!attachedPhotoCaption.isNullOrBlank()) {
        payload["attachedPhotoCaption"] = attachedPhotoCaption.take(200)
      }
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun clearAiMessages(userId: String): Result<Unit> {
    val col = aiMessagesCol(userId)
    return try {
      val docs = col.get().await().documents
      for (doc in docs) doc.reference.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, col.path)
      Result.failure(e)
    }
  }

  suspend fun deleteAllUserData(userId: String): Result<Unit> {
    return try {
      val subcollections =
        listOf(
          photosCol(userId),
          locationsCol(userId),
          notificationsCol(userId),
          aiMessagesCol(userId),
        )
      for (col in subcollections) {
        val docs = col.get().await().documents
        for (doc in docs) doc.reference.delete().await()
      }
      val sharedDocs =
        sharedPhotosCol()
          .whereEqualTo("isPublic", true)
          .whereEqualTo("userId", userId)
          .get()
          .await()
          .documents
      for (doc in sharedDocs) doc.reference.delete().await()
      userDoc(userId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, userDoc(userId).path)
      Result.failure(e)
    }
  }
}

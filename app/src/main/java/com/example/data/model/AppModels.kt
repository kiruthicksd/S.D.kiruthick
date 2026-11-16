package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val email: String = "",
  val bio: String = "",
  val avatarBase64: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UserPhoto(
  @DocumentId val id: String = "",
  val userId: String = "",
  val caption: String = "",
  val description: String = "",
  val aiSummary: String? = null,
  val imageBase64: String = "",
  val source: String = "gallery",
  val hasLocation: Boolean = false,
  val latitude: Double? = null,
  val longitude: Double? = null,
  val locationLabel: String? = null,
  @get:PropertyName("isPublic") @set:PropertyName("isPublic") var isPublic: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SharedPhoto(
  @DocumentId val id: String = "",
  val userId: String = "",
  val authorName: String = "",
  val caption: String = "",
  val description: String = "",
  val imageBase64: String = "",
  val source: String = "gallery",
  val hasLocation: Boolean = false,
  val latitude: Double? = null,
  val longitude: Double? = null,
  @get:PropertyName("isPublic") @set:PropertyName("isPublic") var isPublic: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class LocationEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val label: String = "",
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
  val accuracyMeters: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AppNotification(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val message: String = "",
  val category: String = "account",
  @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AiMessage(
  @DocumentId val id: String = "",
  val userId: String = "",
  val role: String = "user",
  val text: String = "",
  val attachedPhotoId: String? = null,
  val attachedPhotoCaption: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

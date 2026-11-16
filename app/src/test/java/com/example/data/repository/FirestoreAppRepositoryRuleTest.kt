package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreAppRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun userProfile_ownerCanCreateAndObserve_crossUserBlocked() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = FirestoreAppRepository(firestore)

    val ensureResult = withTimeout(TIMEOUT_MS) {
      repo.ensureUserProfile(aliceUid, "Alice Explorer", ALICE_EMAIL)
    }
    assertTrue(ensureResult.isSuccess)

    val profile = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserProfile(aliceUid).first { it != null }
    }
    assertEquals("Alice Explorer", profile?.displayName)

    signInTestUser(BOB_EMAIL)
    val crossReadError = runCatching {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeUserProfile(aliceUid).first() }
    }.exceptionOrNull()
    val fsErr = checkNotNull(crossReadError?.unwrapFirestoreException()) {
      "Expected Firestore PERMISSION_DENIED, got $crossReadError"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr.code)
  }

  @Test
  fun photosAndLocationsAndNotifications_ownerCrudSucceeds_unauthenticatedRejected() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = FirestoreAppRepository(firestore)

    val photoId = withTimeout(TIMEOUT_MS) {
      repo.addPhoto(
        userId = aliceUid,
        authorName = "Alice",
        caption = "Test Skyline",
        description = "Captured on phone",
        imageBase64 = "ZmFrZQ==",
        source = "camera",
        hasLocation = true,
        latitude = 37.7749,
        longitude = -122.4194,
        locationLabel = "San Francisco",
        isPublic = true,
      ).getOrThrow()
    }
    assertTrue(photoId.isNotBlank())

    val photos = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observePhotos(aliceUid).first { list -> list.any { it.id == photoId } }
    }
    assertEquals("Test Skyline", photos.first { it.id == photoId }.caption)

    val shared = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeSharedPhotos(aliceUid).first { list -> list.any { it.id == photoId } }
    }
    assertTrue(shared.any { it.id == photoId })

    val locId = withTimeout(TIMEOUT_MS) {
      repo.addLocationEntry(aliceUid, "Home Base", 12.9716, 77.5946, 5.0).getOrThrow()
    }
    assertTrue(locId.isNotBlank())

    val notifId = withTimeout(TIMEOUT_MS) {
      repo.addNotification(aliceUid, "Photo Saved", "Uploaded Test Skyline", "photo").getOrThrow()
    }
    assertTrue(notifId.isNotBlank())

    val msgId = withTimeout(TIMEOUT_MS) {
      repo.addAiMessage(aliceUid, "user", "Summarize this location").getOrThrow()
    }
    assertTrue(msgId.isNotBlank())

    // Unauthenticated negative test
    auth.signOut()
    val unauthErr = runCatching {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observePhotos(aliceUid).first() }
    }.exceptionOrNull()
    val unauthFsErr = checkNotNull(unauthErr?.unwrapFirestoreException()) {
      "Expected PERMISSION_DENIED when signed out, got $unauthErr"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, unauthFsErr.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_smartmap@test.com"
    const val BOB_EMAIL = "bob_smartmap@test.com"
    const val TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}

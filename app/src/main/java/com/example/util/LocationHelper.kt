package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class GpsReading(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
  val label: String?,
  val timestampMs: Long = System.currentTimeMillis(),
)

object LocationHelper {

  @SuppressLint("MissingPermission")
  suspend fun fetchCurrentLocation(context: Context): Result<GpsReading> =
    withContext(Dispatchers.IO) {
      try {
        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        val cts = CancellationTokenSource()
        val location =
          try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
          } catch (_: Exception) {
            null
          } ?: client.lastLocation.await()

        if (location == null) {
          return@withContext Result.failure(
            IllegalStateException(
              "Unable to acquire GPS fix yet. Ensure device location services are turned on."
            )
          )
        }

        val label = reverseGeocodeLabel(context, location.latitude, location.longitude)
        Result.success(
          GpsReading(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            label = label,
          )
        )
      } catch (e: SecurityException) {
        Result.failure(e)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun reverseGeocodeLabel(context: Context, lat: Double, lng: Double): String? =
    withContext(Dispatchers.IO) {
      try {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
              geocoder.getFromLocation(lat, lng, 1) { list -> cont.resume(list) {} }
            }
          } else {
            geocoder.getFromLocation(lat, lng, 1)
          }
        val first = addresses?.firstOrNull() ?: return@withContext null
        listOfNotNull(first.featureName, first.locality, first.adminArea, first.countryName)
          .distinct()
          .take(3)
          .joinToString(", ")
          .ifBlank { null }
      } catch (_: Exception) {
        null
      }
    }
}

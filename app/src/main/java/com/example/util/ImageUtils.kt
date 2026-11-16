package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageUtils {

  private val bitmapCache =
    object : LruCache<String, Bitmap>(24 * 1024) {
      override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

  suspend fun compressUriToBase64(
    context: Context,
    uri: Uri,
    maxDimension: Int = 900,
    quality: Int = 74,
  ): Pair<String, Bitmap>? =
    withContext(Dispatchers.IO) {
      try {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val sampleSize = calculateInSampleSize(boundsOpts, maxDimension, maxDimension)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded =
          context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
          } ?: return@withContext null

        val scaled = scaleBitmap(decoded, maxDimension)
        if (scaled !== decoded) decoded.recycle()
        val encoded = encodeBitmapAdaptive(scaled, quality)
        encoded to scaled
      } catch (_: Exception) {
        null
      }
    }

  suspend fun compressBitmapToBase64(
    bitmap: Bitmap,
    maxDimension: Int = 900,
    quality: Int = 74,
  ): Pair<String, Bitmap> =
    withContext(Dispatchers.IO) {
      val scaled = scaleBitmap(bitmap, maxDimension)
      val encoded = encodeBitmapAdaptive(scaled, quality)
      encoded to scaled
    }

  fun decodeBase64ToBitmap(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    val cacheKey = "${base64.length}_${base64.take(32)}_${base64.takeLast(16)}"
    bitmapCache.get(cacheKey)?.let { return it }
    return try {
      val bytes = Base64.decode(base64, Base64.DEFAULT)
      val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      if (bmp != null) bitmapCache.put(cacheKey, bmp)
      bmp
    } catch (_: Exception) {
      null
    }
  }

  fun createTempCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }

  private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int,
  ): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
      val halfHeight = height / 2
      val halfWidth = width / 2
      while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
        inSampleSize *= 2
      }
    }
    return max(1, inSampleSize)
  }

  private fun scaleBitmap(source: Bitmap, maxDimension: Int): Bitmap {
    val maxSide = max(source.width, source.height)
    if (maxSide <= maxDimension) return source
    val ratio = maxDimension.toFloat() / maxSide.toFloat()
    val w = (source.width * ratio).roundToInt().coerceAtLeast(1)
    val h = (source.height * ratio).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, w, h, true)
  }

  private fun encodeBitmapAdaptive(bitmap: Bitmap, initialQuality: Int): String {
    var quality = initialQuality
    var bytes: ByteArray
    do {
      val out = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
      bytes = out.toByteArray()
      quality -= 10
    } while (bytes.size > 480_000 && quality >= 30)
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
  }
}

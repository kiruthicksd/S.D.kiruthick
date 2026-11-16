package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object Esp32CamClient {

  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(6, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .build()

  fun resolveStreamUrl(rawInput: String): String {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme =
      if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
      } else {
        "http://$trimmed"
      }
    return when {
      withScheme.endsWith("/stream") || withScheme.endsWith("/capture") -> withScheme
      withScheme.contains(":81") -> "${withScheme.trimEnd('/')}/stream"
      else -> "${withScheme.trimEnd('/')}:81/stream"
    }
  }

  fun resolveCaptureUrl(rawInput: String): String {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme =
      if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
      } else {
        "http://$trimmed"
      }
    return when {
      withScheme.endsWith("/capture") -> withScheme
      withScheme.endsWith(":81/stream") -> withScheme.replace(":81/stream", "/capture")
      withScheme.endsWith("/stream") -> withScheme.replace("/stream", "/capture")
      else -> "${withScheme.trimEnd('/')}/capture"
    }
  }

  fun streamMjpegFrames(streamUrl: String): Flow<Bitmap> =
    flow {
        val request = Request.Builder().url(streamUrl).get().build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
          val code = response.code
          response.close()
          throw IOException("Camera returned HTTP $code")
        }
        val body = response.body ?: throw IOException("Empty camera stream response")
        val inputStream = body.byteStream()
        val frameBuffer = ByteArrayOutputStream(64 * 1024)
        var inJpeg = false
        var prevByte = -1
        val readBuf = ByteArray(8192)

        response.use {
          while (currentCoroutineContext().isActive) {
            val bytesRead = inputStream.read(readBuf)
            if (bytesRead == -1) break
            for (i in 0 until bytesRead) {
              val b = readBuf[i].toInt() and 0xFF
              if (!inJpeg) {
                if (prevByte == 0xFF && b == 0xD8) {
                  frameBuffer.reset()
                  frameBuffer.write(0xFF)
                  frameBuffer.write(0xD8)
                  inJpeg = true
                }
              } else {
                frameBuffer.write(b)
                if (prevByte == 0xFF && b == 0xD9) {
                  val jpegBytes = frameBuffer.toByteArray()
                  val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                  if (bmp != null) {
                    emit(bmp)
                  }
                  inJpeg = false
                  frameBuffer.reset()
                } else if (frameBuffer.size() > 1_500_000) {
                  // Guard against corrupt unbounded frames
                  inJpeg = false
                  frameBuffer.reset()
                }
              }
              prevByte = b
            }
          }
        }
      }
      .flowOn(Dispatchers.IO)

  suspend fun captureSingleFrame(captureUrl: String): Result<Bitmap> =
    withContext(Dispatchers.IO) {
      try {
        val request = Request.Builder().url(captureUrl).get().build()
        httpClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            return@withContext Result.failure(IOException("HTTP ${response.code} from camera"))
          }
          val bytes = response.body?.bytes() ?: return@withContext Result.failure(IOException("Empty body"))
          val bmp =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
              ?: return@withContext Result.failure(IOException("Invalid JPEG image from camera"))
          Result.success(bmp)
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
}

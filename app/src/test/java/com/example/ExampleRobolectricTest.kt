package com.example

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Smart AI Map", appName)
  }

  @Test
  fun `drawable assets decode cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val res = context.resources
    assertNotNull(BitmapFactory.decodeResource(res, R.drawable.img_hero_telemetry))
    assertNotNull(BitmapFactory.decodeResource(res, R.drawable.img_launcher_emblem))
    assertNotNull(BitmapFactory.decodeResource(res, R.drawable.img_esp32_module))
  }
}

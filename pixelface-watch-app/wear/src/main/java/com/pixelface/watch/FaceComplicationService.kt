package com.pixelface.watch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import java.util.Calendar

/**
 * Serves the pixel pet CRT face as a SMALL_IMAGE complication.
 * Renders the 16×16 CRT monitor + face to a Bitmap using the
 * same PixelFaceRenderer pixel data, then wraps it in a complication.
 */
class FaceComplicationService : ComplicationDataSourceService() {

  companion object {
    private const val TAG = "FaceCompSvc"
    private const val BITMAP_SIZE = 64

    fun requestUpdate(context: Context) {
      val component = android.content.ComponentName(context, FaceComplicationService::class.java)
      androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
        .create(context, component)
        .requestUpdateAll()
    }
  }

  private val animator = PixelFaceAnimator()

  override fun getPreviewData(type: ComplicationType): ComplicationData? {
    if (type != ComplicationType.SMALL_IMAGE) return null
    val bitmap = renderBitmap(FaceExpression.NEUTRAL, PixelFaceRenderer.FACE_IDLE)
    val icon = Icon.createWithBitmap(bitmap)
    return SmallImageComplicationData.Builder(
      smallImage = SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
      contentDescription = PlainComplicationText.Builder("PixelFace").build()
    ).build()
  }

  override fun onComplicationRequest(
    request: ComplicationRequest,
    listener: ComplicationRequestListener
  ) {
    val context: Context = this
    val prefs = context.getSharedPreferences(HealthDataManager.PREFS_NAME, Context.MODE_PRIVATE)

    val hr = prefs.getInt(HealthDataManager.KEY_HEART_RATE, 0)
    val steps = prefs.getInt(HealthDataManager.KEY_DAILY_STEPS, 0)
    val calories = prefs.getInt(HealthDataManager.KEY_CALORIES, 0)
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    val expression = FaceExpression.fromHealth(hr, steps, calories)
    val nowMs = System.currentTimeMillis()
    val faceFrame = animator.getCurrentFrame(nowMs, expression, hour)

    Log.d(TAG, "onComplicationRequest: hr=$hr steps=$steps → $expression")

    val bitmap = renderBitmap(expression, faceFrame)
    val icon = Icon.createWithBitmap(bitmap)

    val tapAction = createTapIntent()
    val data = SmallImageComplicationData.Builder(
      smallImage = SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
      contentDescription = PlainComplicationText.Builder("PixelFace").build()
    )
      .setTapAction(tapAction)
      .build()

    listener.onComplicationData(data)
  }

  /**
   * Render the full CRT monitor + face to a Bitmap.
   * Uses the same PixelFaceRenderer pixel data as the Compose Canvas version.
   */
  private fun renderBitmap(
    expression: FaceExpression,
    faceFrame: Array<IntArray>
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint().apply { isAntiAlias = false }  // hard pixels

    val pixelSize = BITMAP_SIZE.toFloat() / PixelFaceRenderer.GRID

    // Monitor palette
    val monitorPalette = mapOf(
      1 to AndroidColor.argb(255, 30, 30, 40),    // dark
      2 to AndroidColor.argb(255, 60, 60, 80),    // mid
      3 to AndroidColor.argb(255, 100, 100, 130), // light
      4 to AndroidColor.argb(255, 8, 8, 14),      // screen bg
      5 to AndroidColor.argb(255, 50, 50, 65)     // stand
    )

    // Face color from expression
    val faceColorInt = expression.color.toInt() or (0xFF.toLong() shl 24).toInt()

    // ── 1. Draw monitor frame ──
    PixelFaceRenderer.MONITOR_FRAME.forEachIndexed { row, cols ->
      cols.forEachIndexed { col, paletteIdx ->
        if (paletteIdx == 0) return@forEachIndexed
        paint.color = monitorPalette[paletteIdx] ?: return@forEachIndexed
        canvas.drawRect(
          col * pixelSize, row * pixelSize,
          (col + 1) * pixelSize, (row + 1) * pixelSize,
          paint
        )
      }
    }

    // ── 2. Draw face on screen area ──
    val screenOffsetX = PixelFaceRenderer.SCREEN_COL_START * pixelSize
    val screenOffsetY = PixelFaceRenderer.SCREEN_ROW_START * pixelSize

    paint.color = faceColorInt
    faceFrame.forEachIndexed { row, cols ->
      cols.forEachIndexed { col, px ->
        if (px == 0) return@forEachIndexed
        canvas.drawRect(
          screenOffsetX + col * pixelSize,
          screenOffsetY + row * pixelSize,
          screenOffsetX + (col + 1) * pixelSize,
          screenOffsetY + (row + 1) * pixelSize,
          paint
        )
      }
    }

    return bitmap
  }

  /** Opens app to the home screen */
  private fun createTapIntent(): android.app.PendingIntent {
    val intent = android.content.Intent().apply {
      action = "com.pixelface.watch.NAVIGATE_HR"
      setClassName(packageName, "com.pixelface.watch.MainActivity")
      putExtra("navigate_to", "hr_chart")
      addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    return android.app.PendingIntent.getActivity(
      this, 1000, intent,
      android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
  }
}

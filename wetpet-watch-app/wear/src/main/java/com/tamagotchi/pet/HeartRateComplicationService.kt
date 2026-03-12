package com.tamagotchi.pet

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest

/**
 * Serves the WetPet's heart rate as a custom complication.
 * Supports RANGED_VALUE (for WFF color-zone logic) and SHORT_TEXT.
 * Tapping opens the WetPet app to the HR chart screen.
 *
 * BPM zones used by the watch face:
 *   Resting  < 70  → blue   (#4488FF)
 *   Normal  70–90  → green  (#00D68F)
 *   Elevated 90–120 → amber (#FFB800)
 *   High    120+   → red    (#FF3366)
 */
class HeartRateComplicationService : ComplicationDataSourceService() {

  companion object {
    private const val HR_MIN = 40f
    private const val HR_MAX = 200f
    const val EXTRA_NAVIGATE_TO = "navigate_to"
    const val DESTINATION_HR_CHART = "hr_chart"

    /** Request an immediate update of all heart rate complication instances. */
    fun requestUpdate(context: Context) {
      val component = ComponentName(context, HeartRateComplicationService::class.java)
      ComplicationDataSourceUpdateRequester.create(context, component)
        .requestUpdateAll()
    }
  }

  /** Create a PendingIntent that opens WetPet → Stats screen */
  private fun createTapIntent(): PendingIntent {
    val intent = Intent().apply {
      setClassName(packageName, "com.tamagotchi.pet.MainActivity")
      putExtra("navigate_to", "hr_chart")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    return PendingIntent.getActivity(
      this,
      1001,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  override fun getPreviewData(type: ComplicationType): ComplicationData? {
    return when (type) {
      ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
        value = 75f,
        min = HR_MIN,
        max = HR_MAX,
        contentDescription = PlainComplicationText.Builder("Heart Rate").build()
      )
        .setText(PlainComplicationText.Builder("75").build())
        .build()

      ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
        text = PlainComplicationText.Builder("75").build(),
        contentDescription = PlainComplicationText.Builder("Heart Rate").build()
      ).build()

      else -> null
    }
  }

  override fun onComplicationRequest(
    request: ComplicationRequest,
    listener: ComplicationRequestListener
  ) {
    val prefs = getSharedPreferences(HealthDataManager.PREFS_NAME, Context.MODE_PRIVATE)
    val hr = prefs.getInt(HealthDataManager.KEY_HEART_RATE, 0)
    val displayHr = if (hr > 0) hr.toString() else "--"
    val hrFloat = hr.toFloat().coerceIn(HR_MIN, HR_MAX)
    val tapAction = createTapIntent()

    val data = when (request.complicationType) {
      ComplicationType.RANGED_VALUE -> {
        RangedValueComplicationData.Builder(
          value = if (hr > 0) hrFloat else HR_MIN,
          min = HR_MIN,
          max = HR_MAX,
          contentDescription = PlainComplicationText.Builder("Heart Rate: $displayHr").build()
        )
          .setText(PlainComplicationText.Builder(displayHr).build())
          .setTapAction(tapAction)
          .build()
      }

      ComplicationType.SHORT_TEXT -> {
        ShortTextComplicationData.Builder(
          text = PlainComplicationText.Builder(displayHr).build(),
          contentDescription = PlainComplicationText.Builder("Heart Rate: $displayHr").build()
        )
          .setTapAction(tapAction)
          .build()
      }

      else -> {
        ShortTextComplicationData.Builder(
          text = PlainComplicationText.Builder("--").build(),
          contentDescription = PlainComplicationText.Builder("Heart Rate").build()
        )
          .setTapAction(tapAction)
          .build()
      }
    }

    listener.onComplicationData(data)
  }
}

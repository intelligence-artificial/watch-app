package com.pixelface.watch

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
 * Serves daily calorie count as a custom complication.
 * Tapping opens PixelFace → Calories chart.
 */
class CaloriesComplicationService : ComplicationDataSourceService() {

  companion object {
    fun requestUpdate(context: Context) {
      val component = ComponentName(context, CaloriesComplicationService::class.java)
      ComplicationDataSourceUpdateRequester.create(context, component)
        .requestUpdateAll()
    }
  }

  /** Opens PixelFace → Calories chart */
  private fun createTapIntent(): PendingIntent {
    val intent = Intent().apply {
      setClassName(packageName, "com.pixelface.watch.MainActivity")
      putExtra("navigate_to", "cal_chart")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    return PendingIntent.getActivity(
      this, 1003, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  override fun getPreviewData(type: ComplicationType): ComplicationData? {
    return when (type) {
      ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
        value = 1200f, min = 0f, max = 3000f,
        contentDescription = PlainComplicationText.Builder("Calories").build()
      ).setText(PlainComplicationText.Builder("1200").build()).build()

      ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
        text = PlainComplicationText.Builder("1200").build(),
        contentDescription = PlainComplicationText.Builder("Calories").build()
      ).build()

      else -> null
    }
  }

  override fun onComplicationRequest(
    request: ComplicationRequest,
    listener: ComplicationRequestListener
  ) {
    val prefs = getSharedPreferences(HealthDataManager.PREFS_NAME, Context.MODE_PRIVATE)
    val cal = prefs.getInt(HealthDataManager.KEY_CALORIES, 0)
    val displayCal = if (cal > 0) cal.toString() else "--"
    val tapAction = createTapIntent()

    val data = when (request.complicationType) {
      ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
        value = cal.toFloat().coerceAtMost(3000f), min = 0f, max = 3000f,
        contentDescription = PlainComplicationText.Builder("Calories: $displayCal").build()
      ).setText(PlainComplicationText.Builder(displayCal).build())
        .setTapAction(tapAction).build()

      ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
        text = PlainComplicationText.Builder(displayCal).build(),
        contentDescription = PlainComplicationText.Builder("Calories: $displayCal").build()
      ).setTapAction(tapAction).build()

      else -> ShortTextComplicationData.Builder(
        text = PlainComplicationText.Builder("--").build(),
        contentDescription = PlainComplicationText.Builder("Calories").build()
      ).setTapAction(tapAction).build()
    }

    listener.onComplicationData(data)
  }
}

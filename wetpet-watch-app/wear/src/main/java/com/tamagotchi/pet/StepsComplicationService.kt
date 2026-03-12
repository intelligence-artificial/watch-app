package com.tamagotchi.pet

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest

/**
 * Serves the WetPet's daily steps as a custom complication.
 * Uses RANGED_VALUE (0–10000 scale) for arc indicators, or SHORT_TEXT for numerical display.
 */
class StepsComplicationService : ComplicationDataSourceService() {

  companion object {
    /** Request an immediate update of all steps complication instances. */
    fun requestUpdate(context: Context) {
      val component = ComponentName(context, StepsComplicationService::class.java)
      ComplicationDataSourceUpdateRequester.create(context, component)
        .requestUpdateAll()
    }
  }

  override fun getPreviewData(type: ComplicationType): ComplicationData? {
    return when (type) {
      ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
        value = 4500f,
        min = 0f,
        max = 10000f,
        contentDescription = PlainComplicationText.Builder("Steps").build()
      )
        .setText(PlainComplicationText.Builder("4500").build())
        .build()

      ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
        text = PlainComplicationText.Builder("4500").build(),
        contentDescription = PlainComplicationText.Builder("Steps").build()
      ).build()

      else -> null
    }
  }

  override fun onComplicationRequest(
    request: ComplicationRequest,
    listener: ComplicationRequestListener
  ) {
    val prefs = getSharedPreferences(HealthDataManager.PREFS_NAME, Context.MODE_PRIVATE)
    val steps = prefs.getInt(HealthDataManager.KEY_DAILY_STEPS, 0)

    val data = when (request.complicationType) {
      ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
        value = steps.toFloat().coerceAtMost(10000f),
        min = 0f,
        max = 10000f,
        contentDescription = PlainComplicationText.Builder("Steps: $steps").build()
      )
        .setText(PlainComplicationText.Builder(steps.toString()).build())
        .build()

      ComplicationType.SHORT_TEXT -> {
        ShortTextComplicationData.Builder(
          text = PlainComplicationText.Builder(steps.toString()).build(),
          contentDescription = PlainComplicationText.Builder("Steps: $steps").build()
        ).build()
      }

      else -> {
        ShortTextComplicationData.Builder(
          text = PlainComplicationText.Builder("--").build(),
          contentDescription = PlainComplicationText.Builder("Steps").build()
        ).build()
      }
    }

    listener.onComplicationData(data)
  }
}

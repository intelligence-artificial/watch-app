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
 * Serves the WetPet's primary needs (hunger, happiness, energy) as complications.
 * Uses RANGED_VALUE (0–100 scale) for arc indicators, or SHORT_TEXT fallback
 * showing a compact "H:85 A:72 E:90" summary.
 */
class NeedsComplicationService : ComplicationDataSourceService() {

  companion object {
    /** Request an immediate update of all needs complication instances. */
    fun requestUpdate(context: Context) {
      val component = ComponentName(context, NeedsComplicationService::class.java)
      ComplicationDataSourceUpdateRequester.create(context, component)
        .requestUpdateAll()
    }
  }

  override fun getPreviewData(type: ComplicationType): ComplicationData? {
    return when (type) {
      ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
        value = 75f,
        min = 0f,
        max = 100f,
        contentDescription = PlainComplicationText.Builder("Pet needs").build()
      )
        .setText(PlainComplicationText.Builder("75%").build())
        .build()

      ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
        text = PlainComplicationText.Builder("H85 A72 E90").build(),
        contentDescription = PlainComplicationText.Builder("Pet needs").build()
      ).build()

      else -> null
    }
  }

  override fun onComplicationRequest(
    request: ComplicationRequest,
    listener: ComplicationRequestListener
  ) {
    val prefs = getSharedPreferences("wetpet_needs", Context.MODE_PRIVATE)
    val hunger = prefs.getFloat("hunger", 1.0f)
    val happiness = prefs.getFloat("happiness", 0.5f)
    val energy = prefs.getFloat("energy", 1.0f)

    // Average of the 3 primary needs as the overall "wellness" score
    val overall = ((hunger + happiness + energy) / 3f * 100).toInt().coerceIn(0, 100)

    val data = when (request.complicationType) {
      ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
        value = overall.toFloat(),
        min = 0f,
        max = 100f,
        contentDescription = PlainComplicationText.Builder("Pet wellness: $overall%").build()
      )
        .setText(PlainComplicationText.Builder("${overall}%").build())
        .build()

      ComplicationType.SHORT_TEXT -> {
        val h = (hunger * 100).toInt()
        val a = (happiness * 100).toInt()
        val e = (energy * 100).toInt()
        ShortTextComplicationData.Builder(
          text = PlainComplicationText.Builder("H${h} A${a} E${e}").build(),
          contentDescription = PlainComplicationText.Builder(
            "Hunger $h%, Happy $a%, Energy $e%"
          ).build()
        ).build()
      }

      else -> {
        // Unsupported type — return empty
        ShortTextComplicationData.Builder(
          text = PlainComplicationText.Builder("--").build(),
          contentDescription = PlainComplicationText.Builder("Pet needs").build()
        ).build()
      }
    }

    listener.onComplicationData(data)
  }
}

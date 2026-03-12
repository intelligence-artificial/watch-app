package com.tamagotchi.pet

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest

/**
 * Serves the current WetPet emotion status as a SHORT_TEXT complication.
 * The watch face displays this below the pet sprite so users can see
 * the pet's mood at a glance (e.g., "all good", "sleepy time", "LET'S GO!!").
 */
class EmotionComplicationService : ComplicationDataSourceService() {

  companion object {
    /** Request an immediate update of all emotion complication instances. */
    fun requestUpdate(context: Context) {
      val component = ComponentName(context, EmotionComplicationService::class.java)
      ComplicationDataSourceUpdateRequester.create(context, component)
        .requestUpdateAll()
    }
  }

  override fun getPreviewData(type: ComplicationType): ComplicationData? {
    if (type != ComplicationType.SHORT_TEXT) return null
    return ShortTextComplicationData.Builder(
      text = PlainComplicationText.Builder("all good").build(),
      contentDescription = PlainComplicationText.Builder("Pet mood").build()
    ).build()
  }

  override fun onComplicationRequest(
    request: ComplicationRequest,
    listener: ComplicationRequestListener
  ) {
    val prefs = getSharedPreferences(DataLayerPaths.PREFS_NAME, Context.MODE_PRIVATE)
    val emotionName = prefs.getString(DataLayerPaths.KEY_EMOTION, "IDLE") ?: "IDLE"
    val emotion = try {
      PetEmotion.valueOf(emotionName)
    } catch (_: Exception) {
      PetEmotion.IDLE
    }

    val data = ShortTextComplicationData.Builder(
      text = PlainComplicationText.Builder(emotion.line1).build(),
      contentDescription = PlainComplicationText.Builder("Pet mood: ${emotion.line1}").build()
    ).build()

    listener.onComplicationData(data)
  }
}

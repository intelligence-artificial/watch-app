package com.tamagotchi.pet

import android.content.Context
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

/**
 * Serves the current WetPet sprite as a SMALL_IMAGE complication.
 * The watch face uses a ComplicationSlot to display this image,
 * which auto-updates when pet type/color/mood changes.
 */
class PetComplicationService : ComplicationDataSourceService() {

  companion object {
    private const val TAG = "PetCompSvc"

    fun requestUpdate(context: Context) {
      val component = android.content.ComponentName(context, PetComplicationService::class.java)
      androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
        .create(context, component)
        .requestUpdateAll()
    }
  }

  override fun getPreviewData(type: ComplicationType): ComplicationData? {
    if (type != ComplicationType.SMALL_IMAGE) return null
    val icon = Icon.createWithResource(this, R.drawable.dog_idle_1_green)
    return SmallImageComplicationData.Builder(
      smallImage = SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
      contentDescription = PlainComplicationText.Builder("WetPet").build()
    ).build()
  }

  override fun onComplicationRequest(
    request: ComplicationRequest,
    listener: ComplicationRequestListener
  ) {
    val context: Context = this
    val prefs = context.getSharedPreferences("wetpet_state", Context.MODE_PRIVATE)

    // Read current pet type, color, and mood from shared prefs
    val petTypeName = prefs.getString("pet_type", "DOG") ?: "DOG"
    val colorName = prefs.getString("color_theme", "GREEN") ?: "GREEN"
    val emotionName = prefs.getString("emotion", "IDLE") ?: "IDLE"

    Log.d(TAG, "onComplicationRequest: pet_type=$petTypeName color=$colorName emotion=$emotionName")

    val petType = try { PetType.valueOf(petTypeName) } catch (_: Exception) { PetType.DOG }
    val colorTheme = try { PetColorTheme.valueOf(colorName) } catch (_: Exception) { PetColorTheme.GREEN }
    val emotion = try { PetEmotion.valueOf(emotionName) } catch (_: Exception) { PetEmotion.IDLE }

    // Map emotion to mood for sprite lookup
    val mood = when {
      emotion == PetEmotion.SLEEPY || emotion == PetEmotion.EXHAUSTED -> PetMood.SLEEPING
      emotion == PetEmotion.HUNGRY -> PetMood.HUNGRY
      emotion == PetEmotion.SAD || emotion == PetEmotion.BORED -> PetMood.TIRED
      emotion == PetEmotion.ECSTATIC || emotion == PetEmotion.EXCITED -> PetMood.CELEBRATING
      emotion == PetEmotion.HAPPY || emotion == PetEmotion.ACTIVE -> PetMood.HAPPY
      else -> PetMood.CONTENT
    }

    // Build sprite name
    val prefix = when (petType) {
      PetType.BLOB -> "pet"
      PetType.CAT -> "cat"
      PetType.DOG -> "dog"
    }
    val suffix = "_${colorTheme.name.lowercase()}"
    val spriteName = when (mood) {
      PetMood.SLEEPING, PetMood.TIRED, PetMood.HUNGRY -> "${prefix}_sleep$suffix"
      else -> "${prefix}_idle_1$suffix"
    }

    val resId = resources.getIdentifier(spriteName, "drawable", packageName)
    val finalRes = if (resId != 0) resId else R.drawable.dog_idle_1_green
    Log.d(TAG, "Sprite: name=$spriteName resId=$resId finalRes=$finalRes mood=$mood pkg=$packageName")

    val icon = Icon.createWithResource(this, finalRes)
    val tapAction = createTapIntent()
    val data = SmallImageComplicationData.Builder(
      smallImage = SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
      contentDescription = PlainComplicationText.Builder("WetPet").build()
    )
      .setTapAction(tapAction)
      .build()

    listener.onComplicationData(data)
  }

  /** Opens WetPet app to the home screen */
  private fun createTapIntent(): android.app.PendingIntent {
    val intent = android.content.Intent().apply {
      setClassName(packageName, "com.tamagotchi.pet.MainActivity")
      putExtra("navigate_to", "home")
      addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    return android.app.PendingIntent.getActivity(
      this, 1000, intent,
      android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
  }
}

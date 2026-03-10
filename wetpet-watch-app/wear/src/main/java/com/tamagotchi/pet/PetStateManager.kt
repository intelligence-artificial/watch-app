package com.tamagotchi.pet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Manages pet state in SharedPreferences.
 * Integrates with PetStatusEngine for emotion-driven state updates.
 * Syncs state to phone via DataLayer.
 */
class PetStateManager(private val context: Context) {

  companion object {
    private const val TAG = "PetStateMgr"
  }

  private val prefs: SharedPreferences =
    context.getSharedPreferences(DataLayerPaths.PREFS_NAME, Context.MODE_PRIVATE)

  var petName: String
    get() = prefs.getString(DataLayerPaths.KEY_PET_NAME, "WetPet") ?: "WetPet"
    set(value) = prefs.edit().putString(DataLayerPaths.KEY_PET_NAME, value).apply()

  var colorTheme: PetColorTheme
    get() {
      val name = prefs.getString(DataLayerPaths.KEY_COLOR_THEME, "GREEN") ?: "GREEN"
      return try { PetColorTheme.valueOf(name) } catch (_: Exception) { PetColorTheme.GREEN }
    }
    set(value) = prefs.edit().putString(DataLayerPaths.KEY_COLOR_THEME, value.name).apply()

  var petType: PetType
    get() {
      val name = prefs.getString("pet_type", "BLOB") ?: "BLOB"
      return try { PetType.valueOf(name) } catch (_: Exception) { PetType.BLOB }
    }
    set(value) = prefs.edit().putString("pet_type", value.name).apply()

  /** Legacy mood — derived from the current emotion for backward compat */
  val mood: PetMood
    get() = computeMood()

  /** Map PetEmotion → PetMood for sprite lookup */
  fun emotionToMood(emotion: PetEmotion): PetMood = when (emotion) {
    PetEmotion.HAPPY, PetEmotion.ECSTATIC -> PetMood.HAPPY
    PetEmotion.EXCITED -> PetMood.CELEBRATING
    PetEmotion.ACTIVE -> PetMood.HAPPY
    PetEmotion.CONTENT, PetEmotion.IDLE -> PetMood.CONTENT
    PetEmotion.SLEEPY -> PetMood.SLEEPING
    PetEmotion.HUNGRY -> PetMood.HUNGRY
    PetEmotion.SAD, PetEmotion.BORED -> PetMood.TIRED
    PetEmotion.SICK, PetEmotion.EXHAUSTED, PetEmotion.STRESSED -> PetMood.TIRED
    PetEmotion.CRITICAL -> PetMood.TIRED
  }

  private fun computeMood(): PetMood {
    val emotionOrd = prefs.getInt("current_emotion", PetEmotion.IDLE.ordinal)
    val emotion = PetEmotion.fromOrdinal(emotionOrd)
    return emotionToMood(emotion)
  }

  fun saveEmotion(emotion: PetEmotion) {
    prefs.edit()
      .putInt("current_emotion", emotion.ordinal)
      .putString(DataLayerPaths.KEY_EMOTION, emotion.name)
      .putString(DataLayerPaths.KEY_MOOD, emotionToMood(emotion).name)
      .apply()
  }

  /**
   * Sync current pet state + emotion to phone via DataLayer.
   */
  suspend fun syncToPhone(emotion: PetEmotion, needs: PetNeeds, health: HealthDataManager) {
    try {
      val timestamp = System.currentTimeMillis()

      // Send pet state
      val petRequest = PutDataMapRequest.create("${DataLayerPaths.PET_STATE_PATH}/$timestamp").apply {
        dataMap.putString(DataLayerPaths.KEY_PET_NAME, petName)
        dataMap.putString(DataLayerPaths.KEY_COLOR_THEME, colorTheme.name)
        dataMap.putString(DataLayerPaths.KEY_EMOTION, emotion.name)
        dataMap.putString(DataLayerPaths.KEY_MOOD, emotionToMood(emotion).name)
        dataMap.putFloat(DataLayerPaths.KEY_HUNGER, needs.hunger)
        dataMap.putFloat(DataLayerPaths.KEY_ENERGY, needs.energy)
        dataMap.putFloat(DataLayerPaths.KEY_HAPPINESS, needs.happiness)
        dataMap.putFloat(DataLayerPaths.KEY_HEALTH, needs.health)
        dataMap.putFloat(DataLayerPaths.KEY_STRESS, needs.stress)
        dataMap.putInt(DataLayerPaths.KEY_XP, needs.xpTotal)
        dataMap.putInt(DataLayerPaths.KEY_LEVEL, needs.level)
        dataMap.putLong(DataLayerPaths.KEY_TIMESTAMP, timestamp)
      }
      Wearable.getDataClient(context)
        .putDataItem(petRequest.asPutDataRequest().setUrgent()).await()

      // Send fitness data
      val fitnessRequest = PutDataMapRequest.create("${DataLayerPaths.FITNESS_UPDATE_PATH}/$timestamp").apply {
        dataMap.putInt(DataLayerPaths.KEY_STEPS, health.dailySteps)
        dataMap.putInt(DataLayerPaths.KEY_HEART_RATE, health.heartRate)
        dataMap.putInt(DataLayerPaths.KEY_CALORIES, health.calories)
        dataMap.putInt(DataLayerPaths.KEY_FLOORS, health.floorsClimbed)
        dataMap.putFloat(DataLayerPaths.KEY_DISTANCE, health.distance)
        dataMap.putLong(DataLayerPaths.KEY_TIMESTAMP, timestamp)
      }
      Wearable.getDataClient(context)
        .putDataItem(fitnessRequest.asPutDataRequest().setUrgent()).await()

      Log.d(TAG, "Synced to phone: $emotion, steps=${health.dailySteps}")
    } catch (e: Exception) {
      Log.e(TAG, "Sync failed: ${e.message}", e)
    }
  }

  /** Backward compat — old sync method */
  suspend fun syncToPhone() {
    // No-op when called without parameters
    Log.d(TAG, "syncToPhone() called without params — use syncToPhone(emotion, needs, health) instead")
  }

  fun saveMood() {
    prefs.edit().putString(DataLayerPaths.KEY_MOOD, mood.name).apply()
  }
}

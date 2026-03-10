package com.tamagotchi.pet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Manages pet state in SharedPreferences (shared with watch_face module on same device).
 * Also syncs state changes to the phone via DataLayer.
 */
class PetStateManager(private val context: Context) {

    companion object {
        private const val TAG = "PetStateMgr"
        private const val XP_PER_LEVEL = 100
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(DataLayerPaths.PREFS_NAME, Context.MODE_PRIVATE)

    var petName: String
        get() = prefs.getString(DataLayerPaths.KEY_PET_NAME, "Tamago") ?: "Tamago"
        set(value) = prefs.edit().putString(DataLayerPaths.KEY_PET_NAME, value).apply()

    var colorTheme: PetColorTheme
        get() {
            val name = prefs.getString(DataLayerPaths.KEY_COLOR_THEME, "GREEN") ?: "GREEN"
            return try { PetColorTheme.valueOf(name) } catch (_: Exception) { PetColorTheme.GREEN }
        }
        set(value) = prefs.edit().putString(DataLayerPaths.KEY_COLOR_THEME, value.name).apply()

    var hunger: Int
        get() = prefs.getInt(DataLayerPaths.KEY_HUNGER, 50).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(DataLayerPaths.KEY_HUNGER, value.coerceIn(0, 100)).apply()

    var energy: Int
        get() = prefs.getInt(DataLayerPaths.KEY_ENERGY, 80).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(DataLayerPaths.KEY_ENERGY, value.coerceIn(0, 100)).apply()

    var happiness: Int
        get() = prefs.getInt(DataLayerPaths.KEY_HAPPINESS, 70).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(DataLayerPaths.KEY_HAPPINESS, value.coerceIn(0, 100)).apply()

    var xp: Int
        get() = prefs.getInt(DataLayerPaths.KEY_XP, 0)
        set(value) = prefs.edit().putInt(DataLayerPaths.KEY_XP, value).apply()

    var level: Int
        get() = prefs.getInt(DataLayerPaths.KEY_LEVEL, 1)
        set(value) = prefs.edit().putInt(DataLayerPaths.KEY_LEVEL, value).apply()

    val mood: PetMood
        get() = computeMood()

    private fun computeMood(): PetMood {
        val h = hunger
        val e = energy
        val hp = happiness
        return when {
            h > 80 -> PetMood.HUNGRY
            e < 20 -> PetMood.TIRED
            hp > 85 -> PetMood.HAPPY
            hp > 50 -> PetMood.CONTENT
            else -> PetMood.TIRED
        }
    }

    fun saveMood() {
        prefs.edit().putString(DataLayerPaths.KEY_MOOD, mood.name).apply()
    }

    /**
     * Apply fitness effects: steps reduce hunger, boost happiness/XP.
     */
    fun applyStepBonus(steps: Int) {
        if (steps <= 0) return
        val xpGain = steps / 500 // 1 XP per 500 steps
        val hungerReduction = (steps / 1000).coerceAtMost(10)
        val happinessBoost = (steps / 2000).coerceAtMost(5)

        xp += xpGain
        hunger = (hunger - hungerReduction).coerceAtLeast(0)
        happiness = (happiness + happinessBoost).coerceAtMost(100)

        // Level up check
        while (xp >= level * XP_PER_LEVEL) {
            xp -= level * XP_PER_LEVEL
            level += 1
            happiness = 100 // Level up makes pet happy!
            Log.d(TAG, "Level up! Now level $level")
        }

        saveMood()
        Log.d(TAG, "Step bonus applied: +${xpGain}XP, -${hungerReduction}hunger, +${happinessBoost}happiness")
    }

    /**
     * Sync current pet state to phone via DataLayer.
     */
    suspend fun syncToPhone() {
        try {
            val timestamp = System.currentTimeMillis()
            val path = "${DataLayerPaths.PET_STATE_PATH}/$timestamp"

            val dataMapRequest = PutDataMapRequest.create(path).apply {
                dataMap.putString(DataLayerPaths.KEY_PET_NAME, petName)
                dataMap.putString(DataLayerPaths.KEY_COLOR_THEME, colorTheme.name)
                dataMap.putInt(DataLayerPaths.KEY_HUNGER, hunger)
                dataMap.putInt(DataLayerPaths.KEY_ENERGY, energy)
                dataMap.putInt(DataLayerPaths.KEY_HAPPINESS, happiness)
                dataMap.putInt(DataLayerPaths.KEY_XP, xp)
                dataMap.putInt(DataLayerPaths.KEY_LEVEL, level)
                dataMap.putString(DataLayerPaths.KEY_MOOD, mood.name)
                dataMap.putLong(DataLayerPaths.KEY_TIMESTAMP, timestamp)
            }

            val putDataReq = dataMapRequest.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(putDataReq).await()
            Log.d(TAG, "Pet state synced to phone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync pet state: ${e.message}", e)
        }
    }
}

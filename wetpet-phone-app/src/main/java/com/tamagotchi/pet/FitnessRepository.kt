package com.tamagotchi.pet

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local JSON-based storage for fitness data received from the watch.
 * Stores daily snapshots with steps, heart rate, and timestamps.
 */
class FitnessRepository(private val context: Context) {

    companion object {
        private const val TAG = "FitnessRepo"
        private const val FILENAME = "fitness_history.json"
        private const val PET_STATE_FILE = "pet_state.json"
    }

    private val fitnessFile: File
        get() = File(context.filesDir, FILENAME)

    private val petStateFile: File
        get() = File(context.filesDir, PET_STATE_FILE)

    // ── Fitness Data ──

    data class FitnessSnapshot(
        val steps: Int,
        val heartRate: Int,
        val timestamp: Long
    )

    fun addFitnessSnapshot(steps: Int, heartRate: Int, timestamp: Long) {
        val snapshots = loadSnapshots().toMutableList()
        snapshots.add(FitnessSnapshot(steps, heartRate, timestamp))

        // Keep last 7 days of data
        val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val filtered = snapshots.filter { it.timestamp > weekAgo }

        saveSnapshots(filtered)
        Log.d(TAG, "Saved fitness snapshot: steps=$steps, hr=$heartRate")
    }

    fun loadSnapshots(): List<FitnessSnapshot> {
        return try {
            if (!fitnessFile.exists()) return emptyList()
            val json = JSONArray(fitnessFile.readText())
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                FitnessSnapshot(
                    steps = obj.getInt("steps"),
                    heartRate = obj.getInt("heartRate"),
                    timestamp = obj.getLong("timestamp")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load snapshots: ${e.message}", e)
            emptyList()
        }
    }

    private fun saveSnapshots(snapshots: List<FitnessSnapshot>) {
        val json = JSONArray()
        snapshots.forEach { s ->
            json.put(JSONObject().apply {
                put("steps", s.steps)
                put("heartRate", s.heartRate)
                put("timestamp", s.timestamp)
            })
        }
        fitnessFile.writeText(json.toString(2))
    }

    fun getTodaySteps(): Int {
        val todayStart = getTodayStart()
        return loadSnapshots()
            .filter { it.timestamp >= todayStart }
            .maxByOrNull { it.timestamp }?.steps ?: 0
    }

    fun getLatestHeartRate(): Int {
        return loadSnapshots()
            .maxByOrNull { it.timestamp }?.heartRate ?: 0
    }

    fun getWeeklySteps(): List<Pair<String, Int>> {
        val snapshots = loadSnapshots()
        val days = mutableListOf<Pair<String, Int>>()
        val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        for (i in 6 downTo 0) {
            val dayStart = getTodayStart() - (i * 24 * 60 * 60 * 1000L)
            val dayEnd = dayStart + (24 * 60 * 60 * 1000L)
            val daySteps = snapshots
                .filter { it.timestamp in dayStart until dayEnd }
                .maxByOrNull { it.timestamp }?.steps ?: 0
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = dayStart }
            val dayIndex = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
            days.add(dayNames[dayIndex] to daySteps)
        }
        return days
    }

    private fun getTodayStart(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    // ── Pet State (received from watch) ──

    data class PetState(
        val name: String = "Tamago",
        val colorTheme: String = "GREEN",
        val hunger: Int = 50,
        val energy: Int = 80,
        val happiness: Int = 70,
        val xp: Int = 0,
        val level: Int = 1,
        val mood: String = "CONTENT"
    )

    fun savePetState(state: PetState) {
        val json = JSONObject().apply {
            put("name", state.name)
            put("colorTheme", state.colorTheme)
            put("hunger", state.hunger)
            put("energy", state.energy)
            put("happiness", state.happiness)
            put("xp", state.xp)
            put("level", state.level)
            put("mood", state.mood)
        }
        petStateFile.writeText(json.toString(2))
        Log.d(TAG, "Pet state saved: ${state.name} (${state.mood})")
    }

    fun loadPetState(): PetState {
        return try {
            if (!petStateFile.exists()) return PetState()
            val json = JSONObject(petStateFile.readText())
            PetState(
                name = json.optString("name", "Tamago"),
                colorTheme = json.optString("colorTheme", "GREEN"),
                hunger = json.optInt("hunger", 50),
                energy = json.optInt("energy", 80),
                happiness = json.optInt("happiness", 70),
                xp = json.optInt("xp", 0),
                level = json.optInt("level", 1),
                mood = json.optString("mood", "CONTENT")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load pet state: ${e.message}", e)
            PetState()
        }
    }
}

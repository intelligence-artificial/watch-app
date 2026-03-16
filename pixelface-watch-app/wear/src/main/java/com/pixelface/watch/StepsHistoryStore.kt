package com.pixelface.watch

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores timestamped step count readings in a JSON file on internal storage.
 * Thread-safe. Caps at [MAX_ENTRIES] (~24 hrs at 5-min intervals).
 */
class StepsHistoryStore(private val context: Context) {

  companion object {
    private const val TAG = "StepsHistStore"
    private const val FILE_NAME = "steps_history.json"
    private const val MAX_ENTRIES = 8640  // ~30 days at 5-min intervals
    private const val MIN_INTERVAL_MS = 60_000L  // Don't store more than once per 60s
  }

  data class StepsReading(val steps: Int, val timestampMs: Long)

  private val file: File get() = File(context.filesDir, FILE_NAME)
  private var lastAppendMs: Long = 0L

  /**
   * Append a step count reading. Ignores duplicates within [MIN_INTERVAL_MS].
   */
  @Synchronized
  fun append(steps: Int, timestampMs: Long = System.currentTimeMillis()) {
    if (steps < 0) return
    if (timestampMs - lastAppendMs < MIN_INTERVAL_MS) return

    try {
      val arr = loadArray()

      val entry = JSONObject().apply {
        put("steps", steps)
        put("ts", timestampMs)
      }
      arr.put(entry)

      // Trim to max entries (keep most recent)
      while (arr.length() > MAX_ENTRIES) {
        arr.remove(0)
      }

      file.writeText(arr.toString())
      lastAppendMs = timestampMs
    } catch (e: Exception) {
      Log.e(TAG, "Failed to append steps reading: ${e.message}")
    }
  }

  /**
   * Get all stored readings, sorted by timestamp ascending.
   */
  @Synchronized
  fun getHistory(): List<StepsReading> {
    return try {
      val arr = loadArray()
      val list = mutableListOf<StepsReading>()
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        list.add(StepsReading(
          steps = obj.getInt("steps"),
          timestampMs = obj.getLong("ts")
        ))
      }
      list.sortedBy { it.timestampMs }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load steps history: ${e.message}")
      emptyList()
    }
  }

  /**
   * Get readings from the last N hours.
   */
  fun getRecentHistory(hours: Int = 4): List<StepsReading> {
    val cutoff = System.currentTimeMillis() - hours * 3600_000L
    return getHistory().filter { it.timestampMs >= cutoff }
  }

  fun getHistoryForRange(range: TimeRange): List<StepsReading> {
    return getHistory().filter { it.timestampMs >= range.cutoffMs }
  }

  fun toJson(): String {
    return try {
      if (file.exists()) file.readText() else "[]"
    } catch (_: Exception) { "[]" }
  }

  private fun loadArray(): JSONArray {
    return try {
      if (file.exists()) {
        JSONArray(file.readText())
      } else {
        JSONArray()
      }
    } catch (e: Exception) {
      JSONArray()
    }
  }
}

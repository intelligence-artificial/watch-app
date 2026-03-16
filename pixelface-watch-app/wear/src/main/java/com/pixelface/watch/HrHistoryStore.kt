package com.pixelface.watch

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores timestamped heart rate readings in a JSON file on internal storage.
 * Thread-safe. Caps at [MAX_ENTRIES] (24 hrs @ 5-min intervals = 288 readings).
 */
class HrHistoryStore(private val context: Context) {

  companion object {
    private const val TAG = "HrHistoryStore"
    private const val FILE_NAME = "hr_history.json"
    private const val MAX_ENTRIES = 8640  // ~30 days at 5-min intervals
    private const val MIN_INTERVAL_MS = 30_000L
  }

  data class HrReading(val bpm: Int, val timestampMs: Long)

  private val file: File get() = File(context.filesDir, FILE_NAME)
  private var lastAppendMs: Long = 0L

  /**
   * Append a heart rate reading. Ignores duplicates within [MIN_INTERVAL_MS].
   */
  @Synchronized
  fun append(bpm: Int, timestampMs: Long = System.currentTimeMillis()) {
    if (bpm !in 30..220) return
    if (timestampMs - lastAppendMs < MIN_INTERVAL_MS) return

    try {
      val arr = loadArray()

      val entry = JSONObject().apply {
        put("bpm", bpm)
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
      Log.e(TAG, "Failed to append HR reading: ${e.message}")
    }
  }

  /**
   * Get all stored readings, sorted by timestamp ascending.
   */
  @Synchronized
  fun getHistory(): List<HrReading> {
    return try {
      val arr = loadArray()
      val list = mutableListOf<HrReading>()
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        list.add(HrReading(
          bpm = obj.getInt("bpm"),
          timestampMs = obj.getLong("ts")
        ))
      }
      list.sortedBy { it.timestampMs }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load HR history: ${e.message}")
      emptyList()
    }
  }

  /**
   * Get readings from the last N hours.
   */
  fun getRecentHistory(hours: Int = 4): List<HrReading> {
    val cutoff = System.currentTimeMillis() - hours * 3600_000L
    return getHistory().filter { it.timestampMs >= cutoff }
  }

  /** Get readings for a specific time range (D/W/M/Y) */
  fun getHistoryForRange(range: TimeRange): List<HrReading> {
    return getHistory().filter { it.timestampMs >= range.cutoffMs }
  }

  /** Export to JSON string for phone sync */
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

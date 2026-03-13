package com.tamagotchi.pet

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores timestamped calorie readings in a JSON file on internal storage.
 * Thread-safe. Caps at [MAX_ENTRIES] (~24 hrs at 5-min intervals).
 */
class CaloriesHistoryStore(private val context: Context) {

  companion object {
    private const val TAG = "CalHistStore"
    private const val FILE_NAME = "calories_history.json"
    private const val MAX_ENTRIES = 288
    private const val MIN_INTERVAL_MS = 60_000L
  }

  data class CalReading(val calories: Int, val timestampMs: Long)

  private val file: File get() = File(context.filesDir, FILE_NAME)
  private var lastAppendMs: Long = 0L

  @Synchronized
  fun append(calories: Int, timestampMs: Long = System.currentTimeMillis()) {
    if (calories < 0) return
    if (timestampMs - lastAppendMs < MIN_INTERVAL_MS) return

    try {
      val arr = loadArray()
      val entry = JSONObject().apply {
        put("cal", calories)
        put("ts", timestampMs)
      }
      arr.put(entry)
      while (arr.length() > MAX_ENTRIES) { arr.remove(0) }
      file.writeText(arr.toString())
      lastAppendMs = timestampMs
    } catch (e: Exception) {
      Log.e(TAG, "Failed to append cal reading: ${e.message}")
    }
  }

  @Synchronized
  fun getHistory(): List<CalReading> {
    return try {
      val arr = loadArray()
      val list = mutableListOf<CalReading>()
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        list.add(CalReading(calories = obj.getInt("cal"), timestampMs = obj.getLong("ts")))
      }
      list.sortedBy { it.timestampMs }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load cal history: ${e.message}")
      emptyList()
    }
  }

  fun getRecentHistory(hours: Int = 4): List<CalReading> {
    val cutoff = System.currentTimeMillis() - hours * 3600_000L
    return getHistory().filter { it.timestampMs >= cutoff }
  }

  private fun loadArray(): JSONArray {
    return try {
      if (file.exists()) JSONArray(file.readText()) else JSONArray()
    } catch (e: Exception) { JSONArray() }
  }
}

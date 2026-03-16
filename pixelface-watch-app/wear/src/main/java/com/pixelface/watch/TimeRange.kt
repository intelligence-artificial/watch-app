package com.pixelface.watch

/**
 * Time range enum for health chart displays.
 * Each range defines its label, cutoff duration, and data point display strategy.
 */
enum class TimeRange(val label: String, val shortLabel: String, val hoursBack: Long) {
  DAY("Today", "D", 24),
  WEEK("This Week", "W", 24 * 7),
  MONTH("This Month", "M", 24 * 30),
  YEAR("This Year", "Y", 24 * 365);

  val cutoffMs: Long get() = System.currentTimeMillis() - hoursBack * 3600_000L
}

package com.pixelface.mobile

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// ═══════════════════════════════════════════════
// Entities
// ═══════════════════════════════════════════════

@Entity(tableName = "hr_readings")
data class HrReading(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val bpm: Int,
  val timestampMs: Long
)

@Entity(tableName = "steps_readings")
data class StepsReading(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val steps: Int,
  val timestampMs: Long
)

@Entity(tableName = "calories_readings")
data class CaloriesReading(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val calories: Int,
  val timestampMs: Long
)

// ═══════════════════════════════════════════════
// DAOs
// ═══════════════════════════════════════════════

@Dao
interface HrReadingDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(readings: List<HrReading>)

  @Query("SELECT * FROM hr_readings WHERE timestampMs >= :startMs ORDER BY timestampMs ASC")
  suspend fun getAfter(startMs: Long): List<HrReading>

  @Query("SELECT * FROM hr_readings ORDER BY timestampMs DESC LIMIT 1")
  suspend fun getLatest(): HrReading?

  @Query("SELECT COUNT(*) FROM hr_readings")
  suspend fun count(): Int

  @Query("DELETE FROM hr_readings WHERE timestampMs < :beforeMs")
  suspend fun deleteOlderThan(beforeMs: Long)
}

@Dao
interface StepsReadingDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(readings: List<StepsReading>)

  @Query("SELECT * FROM steps_readings WHERE timestampMs >= :startMs ORDER BY timestampMs ASC")
  suspend fun getAfter(startMs: Long): List<StepsReading>

  @Query("SELECT * FROM steps_readings ORDER BY timestampMs DESC LIMIT 1")
  suspend fun getLatest(): StepsReading?

  @Query("SELECT COUNT(*) FROM steps_readings")
  suspend fun count(): Int

  @Query("DELETE FROM steps_readings WHERE timestampMs < :beforeMs")
  suspend fun deleteOlderThan(beforeMs: Long)
}

@Dao
interface CaloriesReadingDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(readings: List<CaloriesReading>)

  @Query("SELECT * FROM calories_readings WHERE timestampMs >= :startMs ORDER BY timestampMs ASC")
  suspend fun getAfter(startMs: Long): List<CaloriesReading>

  @Query("SELECT * FROM calories_readings ORDER BY timestampMs DESC LIMIT 1")
  suspend fun getLatest(): CaloriesReading?

  @Query("SELECT COUNT(*) FROM calories_readings")
  suspend fun count(): Int

  @Query("DELETE FROM calories_readings WHERE timestampMs < :beforeMs")
  suspend fun deleteOlderThan(beforeMs: Long)
}

// ═══════════════════════════════════════════════
// Database
// ═══════════════════════════════════════════════

@Database(
  entities = [HrReading::class, StepsReading::class, CaloriesReading::class],
  version = 1,
  exportSchema = false
)
abstract class HealthDatabase : RoomDatabase() {
  abstract fun hrDao(): HrReadingDao
  abstract fun stepsDao(): StepsReadingDao
  abstract fun caloriesDao(): CaloriesReadingDao

  companion object {
    @Volatile
    private var INSTANCE: HealthDatabase? = null

    fun getInstance(context: Context): HealthDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          HealthDatabase::class.java,
          "pixelface_health.db"
        ).build()
        INSTANCE = instance
        instance
      }
    }
  }
}

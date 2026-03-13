package com.tamagotchi.pet

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Exposes WetPet config (pet type, color theme, emotion) to
 * external apps (e.g. the watchface APK) via a ContentProvider.
 *
 * Authority: com.tamagotchi.pet.config
 * URI:       content://com.tamagotchi.pet.config/state
 *
 * Returns a single row with columns: pet_type, color_theme, emotion
 */
class PetConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.tamagotchi.pet.config"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/state")
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val ctx = context ?: return MatrixCursor(arrayOf("pet_type", "color_theme", "emotion", "calories", "steps", "heart_rate"))
        val prefs = ctx.getSharedPreferences(DataLayerPaths.PREFS_NAME, Context.MODE_PRIVATE)
        val healthPrefs = ctx.getSharedPreferences(HealthDataManager.PREFS_NAME, Context.MODE_PRIVATE)

        val petType = prefs.getString("pet_type", "DOG") ?: "DOG"
        val colorTheme = prefs.getString(DataLayerPaths.KEY_COLOR_THEME, "GREEN") ?: "GREEN"
        val emotion = prefs.getString(DataLayerPaths.KEY_EMOTION, "IDLE") ?: "IDLE"
        val calories = healthPrefs.getInt(HealthDataManager.KEY_CALORIES, 0)
        val steps = healthPrefs.getInt(HealthDataManager.KEY_DAILY_STEPS, 0)
        val heartRate = healthPrefs.getInt(HealthDataManager.KEY_HEART_RATE, 0)

        val cursor = MatrixCursor(arrayOf("pet_type", "color_theme", "emotion", "calories", "steps", "heart_rate"))
        cursor.addRow(arrayOf(petType, colorTheme, emotion, calories, steps, heartRate))
        return cursor
    }

    // Read-only provider — no writes
    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.wetpet.config"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

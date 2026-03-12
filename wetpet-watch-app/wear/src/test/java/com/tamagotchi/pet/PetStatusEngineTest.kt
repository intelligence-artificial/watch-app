package com.tamagotchi.pet

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class PetStatusEngineTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var engine: PetStatusEngine

    @Before
    fun setup() {
        context = mock(Context::class.java)
        prefs = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)

        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(prefs.getInt(anyString(), anyInt())).thenReturn(0)
        `when`(prefs.getFloat(anyString(), anyFloat())).thenReturn(0.5f)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putInt(anyString(), anyInt())).thenReturn(editor)
        `when`(editor.putFloat(anyString(), anyFloat())).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        
        engine = PetStatusEngine(context)
    }

    @Test
    fun `test engine updates without fitbit metrics`() {
        val snapshot = HealthDataSnapshot(
            heartRate = 80,
            dailySteps = 5000,
            calories = 1200,
            isSedentary = false
        )
        
        // Push an update
        engine.update(snapshot)
        
        // Check needs are computed gracefully without crashing on missing Fitbit variables
        val needs = engine.currentNeeds
        assertTrue("Happiness should be positive", needs.happiness > 0f)
        assertTrue("Health score should be positive", needs.health > 0f)
        assertTrue("Energy should be computed from base rest times", needs.energy > 0f)
        
        // Base state should be CONTENT or HAPPY for decent steps/HR
        val emotion = engine.currentEmotion
        assertTrue("Emotion should resolve to IDLE/CONTENT/HAPPY", 
            emotion == PetEmotion.IDLE || emotion == PetEmotion.CONTENT || emotion == PetEmotion.HAPPY)
    }

    @Test
    fun `test engine detects critical anomaly correctly`() {
        val snapshot = HealthDataSnapshot(
            heartRate = 190,
            dailySteps = 5, // High HR, low steps = panic
            calories = 100,
            isSedentary = true
        )
        
        // Push an update
        engine.update(snapshot)
        
        // Should resolve to CRITICAL
        val emotion = engine.currentEmotion
        assertEquals("Emotion should resolve to CRITICAL on 190 resting HR", PetEmotion.CRITICAL, emotion)
    }

    @Test
    fun `test exact 180 HR is not critical`() {
        val snapshot = HealthDataSnapshot(
            heartRate = 180,
            dailySteps = 5,
            calories = 100,
            isSedentary = true
        )
        engine.update(snapshot)
        assertTrue("Exact 180 HR should not hit CRITICAL branch", engine.currentEmotion != PetEmotion.CRITICAL)
    }

    @Test
    fun `test false SICK condition`() {
        // High stress (>0.85f) but good health (>=0.5f). Should NOT be SICK.
        val snapshot = HealthDataSnapshot(
            heartRate = 105, // High HR while sedentary gives stress
            dailySteps = 0,
            calories = 0,
            isSedentary = true
        )
        engine.update(snapshot)
        // Check health is reasonably good (no anomalies)
        assertTrue("Health should be high", engine.currentNeeds.health == 1.0f)
        assertTrue("Emotion should not be SICK", engine.currentEmotion != PetEmotion.SICK)
    }
}

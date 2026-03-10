package com.watchvoice.faces

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest

/**
 * Exposes the Tamagotchi's internal state (Mood, battery, interactions)
 * as a Custom Complication that the WFF XML can bind to.
 */
class TamagotchiMoodComplicationService : ComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(
            PlainComplicationText.Builder("pet").build(),
            PlainComplicationText.Builder("Mood").build()
        ).build()
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        // Read battery for a basic "Tired/Happy" text metric
        val bm = this.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        val moodText = if (battery < 20) "tired" else "happy"
        
        Log.d("TamagotchiMood", "Serving mood complication: $moodText")

        val complicationData = ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(moodText).build(),
            PlainComplicationText.Builder("Pet Mood").build()
        ).build()

        listener.onComplicationData(complicationData)
    }
}

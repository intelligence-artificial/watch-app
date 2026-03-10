package com.tamagotchi.pet

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives fitness data and pet state from the watch via DataLayer.
 * Processes incoming data and stores it in FitnessRepository.
 */
class FitnessDataReceiver : WearableListenerService() {

    companion object {
        private const val TAG = "FitnessDataReceiver"
        const val ACTION_DATA_UPDATED = "com.tamagotchi.pet.DATA_UPDATED"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: ${dataEvents.count} events")

        try {
            val repo = FitnessRepository(applicationContext)

            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue

                val path = event.dataItem.uri.path ?: continue
                val frozenItem = event.dataItem.freeze()
                val dataMap = DataMapItem.fromDataItem(frozenItem).dataMap

                when {
                    path.startsWith(DataLayerPaths.FITNESS_UPDATE_PATH) -> {
                        val steps = dataMap.getInt(DataLayerPaths.KEY_STEPS)
                        val hr = dataMap.getInt(DataLayerPaths.KEY_HEART_RATE)
                        val timestamp = dataMap.getLong(DataLayerPaths.KEY_TIMESTAMP)

                        repo.addFitnessSnapshot(steps, hr, timestamp)
                        Log.d(TAG, "Fitness update received: steps=$steps, hr=$hr")
                    }

                    path.startsWith(DataLayerPaths.PET_STATE_PATH) -> {
                        val petState = FitnessRepository.PetState(
                            name = dataMap.getString(DataLayerPaths.KEY_PET_NAME) ?: "Tamago",
                            colorTheme = dataMap.getString(DataLayerPaths.KEY_COLOR_THEME) ?: "GREEN",
                            hunger = dataMap.getInt(DataLayerPaths.KEY_HUNGER),
                            energy = dataMap.getInt(DataLayerPaths.KEY_ENERGY),
                            happiness = dataMap.getInt(DataLayerPaths.KEY_HAPPINESS),
                            xp = dataMap.getInt(DataLayerPaths.KEY_XP),
                            level = dataMap.getInt(DataLayerPaths.KEY_LEVEL),
                            mood = dataMap.getString(DataLayerPaths.KEY_MOOD) ?: "CONTENT"
                        )
                        repo.savePetState(petState)
                        Log.d(TAG, "Pet state received: ${petState.name} (${petState.mood})")
                    }
                }

                // Notify UI to refresh
                sendBroadcast(Intent(ACTION_DATA_UPDATED))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing data: ${e.message}", e)
        } finally {
            dataEvents.release()
        }
    }
}

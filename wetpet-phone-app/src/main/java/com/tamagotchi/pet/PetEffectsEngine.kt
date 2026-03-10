package com.tamagotchi.pet

/**
 * Game logic that translates fitness data into pet effects.
 * Steps → XP + hunger decay reduction
 * Heart rate elevation → energy boost
 * Inactivity → hunger increases, mood drops
 * Milestone celebrations (10k steps → pet celebrates)
 */
class PetEffectsEngine {

    data class EffectResult(
        val description: String,
        val xpGained: Int,
        val hungerChange: Int,
        val energyChange: Int,
        val happinessChange: Int
    )

    /**
     * Calculate the effects of today's fitness on the pet.
     */
    fun calculateEffects(steps: Int, heartRate: Int): List<EffectResult> {
        val effects = mutableListOf<EffectResult>()

        // Step effects
        if (steps >= 10000) {
            effects.add(EffectResult(
                description = "★ 10,000 step milestone! Pet is celebrating!",
                xpGained = 25,
                hungerChange = -15,
                energyChange = 10,
                happinessChange = 20
            ))
        } else if (steps >= 5000) {
            effects.add(EffectResult(
                description = "Great walking! 5,000+ steps today",
                xpGained = 10,
                hungerChange = -10,
                energyChange = 5,
                happinessChange = 10
            ))
        } else if (steps >= 2000) {
            effects.add(EffectResult(
                description = "Good movement! 2,000+ steps",
                xpGained = 5,
                hungerChange = -5,
                energyChange = 3,
                happinessChange = 5
            ))
        } else if (steps >= 500) {
            effects.add(EffectResult(
                description = "Some activity detected",
                xpGained = 2,
                hungerChange = -2,
                energyChange = 1,
                happinessChange = 2
            ))
        } else {
            effects.add(EffectResult(
                description = "Not much movement today... pet is getting restless",
                xpGained = 0,
                hungerChange = 10,
                energyChange = -5,
                happinessChange = -10
            ))
        }

        // Heart rate effects
        if (heartRate in 100..150) {
            effects.add(EffectResult(
                description = "Moderate exercise detected (HR: $heartRate bpm)",
                xpGained = 5,
                hungerChange = -3,
                energyChange = 5,
                happinessChange = 5
            ))
        } else if (heartRate > 150) {
            effects.add(EffectResult(
                description = "Intense workout! (HR: $heartRate bpm)",
                xpGained = 10,
                hungerChange = -5,
                energyChange = -3,
                happinessChange = 8
            ))
        }

        return effects
    }

    /**
     * Get a summary of total effects.
     */
    fun summarizeEffects(effects: List<EffectResult>): EffectResult {
        return EffectResult(
            description = "Total daily effects",
            xpGained = effects.sumOf { it.xpGained },
            hungerChange = effects.sumOf { it.hungerChange },
            energyChange = effects.sumOf { it.energyChange },
            happinessChange = effects.sumOf { it.happinessChange }
        )
    }
}

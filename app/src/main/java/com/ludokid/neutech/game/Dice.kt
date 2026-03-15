package com.ludokid.neutech.game

import kotlin.random.Random

/**
 * Simulates a dice with animation states.
 */
class Dice {

    var currentValue: Int = 1
        private set

    var isRolling: Boolean = false
        private set

    /**
     * Rolls the dice and returns the result.
     */
    fun roll(): Int {
        isRolling = true
        currentValue = Random.nextInt(1, 7)
        isRolling = false
        return currentValue
    }

    /**
     * Generates a random intermediate value for animation frames.
     */
    fun getAnimationFrame(): Int = Random.nextInt(1, 7)
}

package com.ludokid.neutech.game

import com.ludokid.neutech.data.*

/**
 * Core Ludo board logic. Manages:
 * - Board positions (0–51 shared path)
 * - Safe zones (player-specific)
 * - Home positions
 * - Kill detection
 * - Safe cell detection
 */
object LudoBoard {

    const val BOARD_SIZE = 52          // Shared path cells
    const val SAFE_ZONE_SIZE = 5       // Cells in the colored home stretch
    const val FINISHING_POSITION = 100 // Virtual "finished" position

    // Starting positions for each player on the shared board
    // Red (0), Green (1), Yellow (2), Blue (3)
    val START_POSITIONS = mapOf(
        0 to 0,  // Red starts at cell 0
        1 to 13, // Green starts at cell 13
        2 to 26, // Yellow starts at cell 26
        3 to 39  // Blue starts at cell 39
    )

    // Cells that are "safe" from being killed (star/safe cells on the board)
    // Position 0 removed — it is Red's start cell, not a universal safe star
    val SAFE_CELLS = setOf(8, 13, 21, 26, 34, 39, 47)

    // Position where each player enters the safe zone (their COLOR path begins)
    val SAFE_ZONE_ENTRY = mapOf(
        0 to 50,  // Red enters safe zone at board position 50
        1 to 11,  // Green 
        2 to 24,  // Yellow 
        3 to 37   // Blue 
    )

    /**
     * Returns all pawns that can be moved given the dice value.
     */
    fun getMoveablePawns(player: Player, diceValue: Int, allPlayers: List<Player>): List<Pawn> {
        return player.pawns.filter { pawn ->
            when {
                pawn.state == PawnState.FINISHED -> false
                pawn.state == PawnState.HOME -> diceValue == 6
                pawn.state == PawnState.ACTIVE -> {
                    val newPos = getNewPosition(pawn, diceValue, player.id)
                    newPos != null && !isBlockade(newPos, allPlayers)
                }
                pawn.state == PawnState.SAFE_ZONE -> {
                    val safePos = pawn.boardPosition - (BOARD_SIZE + player.id * SAFE_ZONE_SIZE)
                    val remainingSteps = SAFE_ZONE_SIZE - safePos
                    diceValue <= remainingSteps
                }
                else -> false
            }
        }
    }

    /**
     * Calculates the new board position for a pawn after moving [steps] steps.
     * Returns null if the move is invalid (would overshoot finish).
     */
    fun getNewPosition(pawn: Pawn, steps: Int, playerId: Int): Int? {
        if (pawn.state == PawnState.HOME) {
            return if (steps == 6) START_POSITIONS[playerId] else null
        }

        if (pawn.state == PawnState.FINISHED) return null

        if (pawn.state == PawnState.SAFE_ZONE) {
            val safePos = pawn.boardPosition - (BOARD_SIZE + playerId * SAFE_ZONE_SIZE) // 0 to 4 within safe zone
            val newSafePos = safePos + steps
            return when {
                newSafePos == SAFE_ZONE_SIZE -> FINISHING_POSITION          // exact finish
                newSafePos > SAFE_ZONE_SIZE -> null                          // overshoot — blocked
                else -> BOARD_SIZE + playerId * SAFE_ZONE_SIZE + newSafePos
            }
        }

        // Active pawn on shared board
        val safeEntry = SAFE_ZONE_ENTRY[playerId] ?: return null
        val currentPos = pawn.boardPosition

        // Calculate steps to safe zone entry (no +1: entry fires exactly when steps == stepsToEntry)
        val stepsToEntry = (safeEntry - currentPos + BOARD_SIZE) % BOARD_SIZE

        return when {
            steps < stepsToEntry -> (currentPos + steps) % BOARD_SIZE
            steps == stepsToEntry -> BOARD_SIZE + playerId * SAFE_ZONE_SIZE  // Enters safe zone
            steps > stepsToEntry -> {
                val safeSteps = steps - stepsToEntry
                if (safeSteps > SAFE_ZONE_SIZE) null  // Overshoot
                else if (safeSteps == SAFE_ZONE_SIZE) FINISHING_POSITION
                else BOARD_SIZE + playerId * SAFE_ZONE_SIZE + safeSteps
            }
            else -> null
        }
    }

    /**
     * Checks if any opponent pawns are at the destination and returns them.
     */
    fun getPawnsAtPosition(position: Int, excludePlayerId: Int, allPlayers: List<Player>): List<Pawn> {
        if (position in SAFE_CELLS) return emptyList() // Safe cell — no kills
        if (position >= BOARD_SIZE) return emptyList() // Safe zone — no kills

        return allPlayers
            .filter { it.id != excludePlayerId }
            .flatMap { it.pawns }
            .filter { it.state == PawnState.ACTIVE && it.boardPosition == position }
    }

    /**
     * Checks if the position is a safe cell (star).
     */
    fun isSafeCell(position: Int): Boolean = position in SAFE_CELLS

    /**
     * Returns whether [position] is inside the safe zone for [playerId].
     */
    fun isInSafeZone(position: Int, playerId: Int): Boolean {
        val start = BOARD_SIZE + playerId * SAFE_ZONE_SIZE
        val end = start + SAFE_ZONE_SIZE
        return position in start until end
    }

    /**
     * Checks if a player has won (all 4 pawns finished).
     */
    fun hasWon(player: Player): Boolean {
        return player.pawns.all { it.state == PawnState.FINISHED }
    }

    /**
     * Checks if multiple pawns of the same player occupy the same cell (blockade).
     */
    fun isBlockade(position: Int, allPlayers: List<Player>): Boolean {
        return allPlayers.any { player ->
            player.pawns.count { it.state == PawnState.ACTIVE && it.boardPosition == position } >= 2
        }
    }
}

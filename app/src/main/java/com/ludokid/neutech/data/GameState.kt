package com.ludokid.neutech.data

/**
 * Represents the complete game state.
 */
data class GameState(
    val players: List<Player>,
    val currentPlayerIndex: Int = 0,
    val diceValue: Int = 0,
    val phase: GamePhase = GamePhase.WAITING_TO_ROLL,
    val currentTriviaCard: TriviaCard? = null,
    val seenTriviaCards: MutableList<TriviaCard> = mutableListOf(),
    val pendingKill: PendingKill? = null,
    val consecutiveSixes: Int = 0,
    val winner: Player? = null,
    val moveablePawns: List<Pawn> = emptyList()
)

/**
 * Tracks a pending kill attempt — when a pawn lands on opponent's square.
 */
data class PendingKill(
    val attackingPawn: Pawn,
    val defendingPawn: Pawn,
    val triviaCard: TriviaCard
)

enum class GamePhase {
    WAITING_TO_ROLL,
    SHOWING_TRIVIA,        // Showing trivia fact after dice roll
    SELECTING_PAWN,        // Player picks which pawn to move
    TRIVIA_QUIZ,           // Quiz before killing opponent pawn
    ANIMATING_MOVE,        // Pawn moving animation
    GAME_OVER
}

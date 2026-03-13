package com.ludokid.data

/**
 * Represents a player in the game.
 */
data class Player(
    val id: Int,
    val name: String,
    val color: PlayerColor,
    val isAI: Boolean = false,
    val pawns: List<Pawn> = List(4) { index -> Pawn(index, id, PawnState.HOME) }
)

data class Pawn(
    val id: Int,             // 0-3 within a player
    val playerId: Int,
    var state: PawnState = PawnState.HOME,
    var boardPosition: Int = -1 // -1 = home, 0-51 = board, 52-56 = safe path, 57 = finished
)

enum class PawnState {
    HOME,      // Not yet on board
    ACTIVE,    // On the board
    SAFE_ZONE, // In the colored home stretch
    FINISHED   // Reached the center
}

enum class PlayerColor(val colorHex: String, val lightColorHex: String, val darkColorHex: String) {
    RED("#E53935", "#FFCDD2", "#B71C1C"),
    BLUE("#1E88E5", "#BBDEFB", "#0D47A1"),
    GREEN("#43A047", "#C8E6C9", "#1B5E20"),
    YELLOW("#FDD835", "#FFF9C4", "#F57F17")
}

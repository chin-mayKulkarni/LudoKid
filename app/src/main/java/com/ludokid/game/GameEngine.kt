package com.ludokid.game

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ludokid.data.*
import com.ludokid.trivia.TriviaRepository
import kotlinx.coroutines.*

/**
 * Central game engine — manages state transitions, trivia flow, and kill logic.
 *
 * Flow:
 * 1. WAITING_TO_ROLL → Player taps dice
 * 2. [Dice animation] → Roll result + fetch trivia card
 * 3. SHOWING_TRIVIA → Show fact for ~5 seconds
 * 4. SELECTING_PAWN → Player picks a pawn to move
 * 5. If kill possible → TRIVIA_QUIZ
 * 6. Answer correct → kill confirmed | Wrong → pawn safe
 * 7. ANIMATING_MOVE → pawn moves
 * 8. Next player's turn or GAME_OVER
 */
class GameEngine(
    private val context: Context,
    private val triviaRepository: TriviaRepository,
    private val scope: CoroutineScope
) {

    private val _gameState = MutableLiveData<GameState>()
    val gameState: LiveData<GameState> = _gameState

    private val _uiEvent = MutableLiveData<GameEvent>()
    val uiEvent: LiveData<GameEvent> = _uiEvent

    private val dice = Dice()

    fun initGame(playerCount: Int, playerNames: List<String>) {
        val players = (0 until playerCount).map { index ->
            Player(
                id = index,
                name = playerNames.getOrElse(index) { "Player ${index + 1}" },
                color = when (index) {
                    0 -> PlayerColor.RED
                    1 -> PlayerColor.BLUE
                    2 -> PlayerColor.GREEN
                    else -> PlayerColor.YELLOW
                },
                isAI = false,
                pawns = List(4) { pawnIndex ->
                    Pawn(id = pawnIndex, playerId = index, state = PawnState.HOME, boardPosition = -1)
                }
            )
        }

        _gameState.value = GameState(
            players = players,
            currentPlayerIndex = 0,
            phase = GamePhase.WAITING_TO_ROLL
        )
    }

    // ─── DICE ROLL ────────────────────────────────────────────────────────────

    fun rollDice() {
        val state = _gameState.value ?: return
        if (state.phase != GamePhase.WAITING_TO_ROLL) return

        scope.launch {
            // Animate dice
            _uiEvent.postValue(GameEvent.DiceRollStarted)
            delay(600)

            val diceValue = dice.roll()
            val newConsecutiveSixes = if (diceValue == 6) state.consecutiveSixes + 1 else 0
            val currentPlayer = state.players[state.currentPlayerIndex]

            // Three consecutive sixes — forfeit turn
            if (newConsecutiveSixes >= 3) {
                val forfeitState = state.copy(
                    diceValue = diceValue,
                    consecutiveSixes = 0,
                    phase = GamePhase.ANIMATING_MOVE
                )
                _gameState.postValue(forfeitState)
                _uiEvent.postValue(GameEvent.NoMoveablePawns)
                delay(1200)
                nextTurn()
                return@launch
            }

            val moveablePawns = LudoBoard.getMoveablePawns(currentPlayer, diceValue, state.players)

            if (moveablePawns.isEmpty()) {
                val newState = state.copy(
                    diceValue = diceValue,
                    consecutiveSixes = newConsecutiveSixes,
                    phase = GamePhase.ANIMATING_MOVE,
                    currentTriviaCard = null,
                    moveablePawns = emptyList()
                )
                _gameState.postValue(newState)
                _uiEvent.postValue(GameEvent.NoMoveablePawns)
                delay(1200)
                processEndOfTurn(newState)
            } else {
                val triviaCard = triviaRepository.getNextCard()
                val newSeenCards = state.seenTriviaCards.toMutableList().also { it.add(triviaCard) }

                val newState = state.copy(
                    diceValue = diceValue,
                    consecutiveSixes = newConsecutiveSixes,
                    currentTriviaCard = triviaCard,
                    seenTriviaCards = newSeenCards,
                    phase = GamePhase.SHOWING_TRIVIA,
                    moveablePawns = moveablePawns
                )
                _gameState.postValue(newState)
                _uiEvent.postValue(GameEvent.ShowTriviaCard(triviaCard, diceValue))
            }
        }
    }

    // ─── TRIVIA DISMISSED ─────────────────────────────────────────────────────

    fun onTriviaDismissed() {
        val state = _gameState.value ?: return
        if (state.phase != GamePhase.SHOWING_TRIVIA) return

        _gameState.value = state.copy(phase = GamePhase.SELECTING_PAWN)
        _uiEvent.value = GameEvent.SelectPawn(state.moveablePawns)
    }

    // ─── PAWN SELECTED ────────────────────────────────────────────────────────

    fun selectPawn(pawn: Pawn) {
        val state = _gameState.value ?: return
        if (state.phase != GamePhase.SELECTING_PAWN) return
        if (!state.moveablePawns.contains(pawn)) return

        val currentPlayer = state.players[state.currentPlayerIndex]
        val newPosition = LudoBoard.getNewPosition(pawn, state.diceValue, currentPlayer.id) ?: return

        // Check if any opponent pawns are at the new position
        val opponentPawns = LudoBoard.getPawnsAtPosition(newPosition, currentPlayer.id, state.players)

        if (opponentPawns.isNotEmpty()) {
            // Potential kill! Show trivia quiz
            val quizCard = triviaRepository.getRandomFromSeen(state.seenTriviaCards)
            val targetPawn = opponentPawns.first()
            val pendingKill = PendingKill(attackingPawn = pawn, defendingPawn = targetPawn, triviaCard = quizCard)

            _gameState.value = state.copy(
                phase = GamePhase.TRIVIA_QUIZ,
                pendingKill = pendingKill
            )
            _uiEvent.value = GameEvent.ShowKillQuiz(pendingKill)
        } else {
            // Regular move
            movePawn(pawn, newPosition, state)
        }
    }

    // ─── TRIVIA QUIZ ANSWER ────────────────────────────────────────────────────

    fun answerKillQuestion(selectedAnswerIndex: Int) {
        val state = _gameState.value ?: return
        if (state.phase != GamePhase.TRIVIA_QUIZ) return
        val pendingKill = state.pendingKill ?: return

        val isCorrect = selectedAnswerIndex == pendingKill.triviaCard.correctAnswerIndex
        _uiEvent.value = GameEvent.QuizAnswered(isCorrect, pendingKill.triviaCard.correctAnswerIndex)

        scope.launch {
            delay(1500) // Show correct/wrong feedback

            if (isCorrect) {
                // Kill confirmed — move attacking pawn, send defending pawn home
                val newState = killPawn(state, pendingKill)
                _gameState.postValue(newState)
                _uiEvent.postValue(GameEvent.PawnKilled(pendingKill.defendingPawn))
                delay(800)
                val currentPlayer = newState.players[newState.currentPlayerIndex]
                val newPosition = LudoBoard.getNewPosition(
                    pendingKill.attackingPawn, newState.diceValue, currentPlayer.id
                ) ?: return@launch
                val finalState = movePawnInState(newState, pendingKill.attackingPawn, newPosition)
                processEndOfTurn(finalState)
            } else {
                // Wrong answer — pawn is safe, but attacker still moves to destination
                _uiEvent.postValue(GameEvent.PawnSaved(pendingKill.defendingPawn))
                delay(800)
                val currentPlayer = state.players[state.currentPlayerIndex]
                val newPosition = LudoBoard.getNewPosition(
                    pendingKill.attackingPawn, state.diceValue, currentPlayer.id
                )
                if (newPosition != null) {
                    val movedState = movePawnInState(state.copy(pendingKill = null), pendingKill.attackingPawn, newPosition)
                    _gameState.postValue(movedState)
                    _uiEvent.postValue(GameEvent.PawnMoved(pendingKill.attackingPawn, newPosition))
                    delay(500)
                    processEndOfTurn(movedState)
                } else {
                    val skipState = state.copy(phase = GamePhase.ANIMATING_MOVE, pendingKill = null)
                    processEndOfTurn(skipState)
                }
            }
        }
    }

    // ─── MOVEMENT ─────────────────────────────────────────────────────────────

    private fun movePawn(pawn: Pawn, newPosition: Int, state: GameState) {
        scope.launch {
            val newState = movePawnInState(state, pawn, newPosition)
            _gameState.postValue(newState)
            _uiEvent.postValue(GameEvent.PawnMoved(pawn, newPosition))
            delay(500)
            processEndOfTurn(newState)
        }
    }

    private fun movePawnInState(state: GameState, pawn: Pawn, newPosition: Int): GameState {
        val updatedPlayers = state.players.map { player ->
            if (player.id == pawn.playerId) {
                val updatedPawns = player.pawns.map { p ->
                    if (p.id == pawn.id) {
                        when {
                            newPosition == LudoBoard.FINISHING_POSITION -> p.copy(
                                boardPosition = newPosition,
                                state = PawnState.FINISHED
                            )
                            LudoBoard.isInSafeZone(newPosition, player.id) -> p.copy(
                                boardPosition = newPosition,
                                state = PawnState.SAFE_ZONE
                            )
                            pawn.state == PawnState.HOME -> p.copy(
                                boardPosition = newPosition,
                                state = PawnState.ACTIVE
                            )
                            else -> p.copy(boardPosition = newPosition, state = PawnState.ACTIVE)
                        }
                    } else p
                }
                player.copy(pawns = updatedPawns)
            } else player
        }
        return state.copy(players = updatedPlayers, pendingKill = null)
    }

    private fun killPawn(state: GameState, pendingKill: PendingKill): GameState {
        val updatedPlayers = state.players.map { player ->
            if (player.id == pendingKill.defendingPawn.playerId) {
                val updatedPawns = player.pawns.map { p ->
                    if (p.id == pendingKill.defendingPawn.id) {
                        p.copy(boardPosition = -1, state = PawnState.HOME)
                    } else p
                }
                player.copy(pawns = updatedPawns)
            } else player
        }
        return state.copy(players = updatedPlayers)
    }

    // ─── END OF TURN ──────────────────────────────────────────────────────────

    private fun processEndOfTurn(state: GameState) {
        val currentPlayer = state.players[state.currentPlayerIndex]

        // Check win condition
        if (LudoBoard.hasWon(currentPlayer)) {
            val finalState = state.copy(phase = GamePhase.GAME_OVER, winner = currentPlayer)
            _gameState.postValue(finalState)
            _uiEvent.postValue(GameEvent.GameOver(currentPlayer))
            return
        }

        // Check if player gets another turn (rolled a 6), but not after three consecutive sixes
        val getsAnotherTurn = state.diceValue == 6 && state.consecutiveSixes < 3
        if (getsAnotherTurn) {
            val nextState = state.copy(phase = GamePhase.WAITING_TO_ROLL)
            _gameState.postValue(nextState)
            _uiEvent.postValue(GameEvent.BonusTurn(currentPlayer))
        } else {
            nextTurn()
        }
    }

    private fun nextTurn() {
        val state = _gameState.value ?: return
        val nextIndex = (state.currentPlayerIndex + 1) % state.players.size
        val newState = state.copy(
            currentPlayerIndex = nextIndex,
            phase = GamePhase.WAITING_TO_ROLL,
            diceValue = 0,
            consecutiveSixes = 0,
            currentTriviaCard = null,
            pendingKill = null,
            moveablePawns = emptyList()
        )
        _gameState.postValue(newState)
        _uiEvent.postValue(GameEvent.NextTurn(state.players[nextIndex]))
    }

    // ─── UTILITY ──────────────────────────────────────────────────────────────

    fun getCurrentPlayer(): Player? {
        val state = _gameState.value ?: return null
        return state.players.getOrNull(state.currentPlayerIndex)
    }
}

sealed class GameEvent {
    object DiceRollStarted : GameEvent()
    data class ShowTriviaCard(val card: com.ludokid.data.TriviaCard, val diceValue: Int) : GameEvent()
    data class SelectPawn(val pawns: List<Pawn>) : GameEvent()
    data class ShowKillQuiz(val pendingKill: PendingKill) : GameEvent()
    data class QuizAnswered(val isCorrect: Boolean, val correctIndex: Int) : GameEvent()
    data class PawnKilled(val pawn: Pawn) : GameEvent()
    data class PawnSaved(val pawn: Pawn) : GameEvent()
    data class PawnMoved(val pawn: Pawn, val newPosition: Int) : GameEvent()
    data class BonusTurn(val player: Player) : GameEvent()
    data class NextTurn(val player: Player) : GameEvent()
    data class GameOver(val winner: Player) : GameEvent()
    object NoMoveablePawns : GameEvent()
}

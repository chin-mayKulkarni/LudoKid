package com.ludokid.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ludokid.R
import com.ludokid.data.*
import com.ludokid.databinding.ActivityGameBinding
import com.ludokid.game.GameEvent

import com.ludokid.viewmodel.GameViewModel

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYER_COUNT = "extra_player_count"
        const val EXTRA_PLAYER_NAMES = "extra_player_names"
    }

    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val playerCount = intent.getIntExtra(EXTRA_PLAYER_COUNT, 2)
        val playerNames = intent.getStringArrayListExtra(EXTRA_PLAYER_NAMES)?.toList()
            ?: List(playerCount) { "Player ${it + 1}" }

        viewModel.initGame(playerCount, playerNames)
        setupBoardView()
        setupDiceView()
        observeGameState()
        observeEvents()
    }

    private fun setupBoardView() {
        binding.ludoBoard.onPawnSelected = { pawn ->
            viewModel.selectPawn(pawn)
        }
    }

    private fun setupDiceView() {
        val rollAction = {
            val state = viewModel.gameState.value
            if (state?.phase == GamePhase.WAITING_TO_ROLL) {
                viewModel.rollDice()
            }
        }
        binding.diceView.setOnClickListener { rollAction() }
        binding.btnRollDice.setOnClickListener { rollAction() }
    }

    private fun observeGameState() {
        viewModel.gameState.observe(this) { state ->
            binding.ludoBoard.players = state.players
            binding.ludoBoard.selectablePawns = if (state.phase == GamePhase.SELECTING_PAWN)
                state.moveablePawns else emptyList()

            updateCurrentPlayerUI(state)
            updatePhaseUI(state.phase)
        }
    }

    private fun observeEvents() {
        viewModel.uiEvent.observe(this) { event ->
            when (event) {
                is GameEvent.DiceRollStarted -> {
                    binding.diceView.alpha = 0.5f
                    binding.btnRollDice.isEnabled = false
                }

                is GameEvent.ShowTriviaCard -> {
                    binding.diceView.animateRoll(event.diceValue) {}
                    binding.diceView.alpha = 1f
                    showTriviaOverlay(event.card, event.diceValue)
                }

                is GameEvent.SelectPawn -> {
                    binding.tvStatus.text = "🎯 Tap a highlighted pawn to move!"
                    binding.btnRollDice.isEnabled = false
                }

                is GameEvent.ShowKillQuiz -> {
                    showKillQuizDialog(event.pendingKill)
                }

                is GameEvent.QuizAnswered -> {
                    // Feedback handled by dialog
                }

                is GameEvent.PawnKilled -> {
                    showToast("⚔️ ${event.pawn.playerId + 1}'s pawn sent home!")
                }

                is GameEvent.PawnSaved -> {
                    showToast("🛡️ Pawn protected! Wrong answer saved the day!")
                }

                is GameEvent.PawnMoved -> {
                    binding.ludoBoard.animatePawnMove(event.pawn, event.newPosition)
                }

                is GameEvent.BonusTurn -> {
                    showToast("🎲 Rolled 6! ${event.player.name} gets another turn!")
                }

                is GameEvent.NextTurn -> {
                    binding.btnRollDice.isEnabled = true
                    showToast("🔄 ${event.player.name}'s turn!")
                }

                is GameEvent.NoMoveablePawns -> {
                    showToast("😔 No pawns can move. Roll a 6 to get started!")
                }

                is GameEvent.GameOver -> {
                    showGameOverDialog(event.winner)
                }
            }
        }
    }

    private fun updateCurrentPlayerUI(state: GameState) {
        val player = state.players.getOrNull(state.currentPlayerIndex) ?: return
        val colorHex = player.color.colorHex
        val color = Color.parseColor(colorHex)

        binding.tvCurrentPlayer.text = "🎮 ${player.name}'s Turn"
        binding.tvCurrentPlayer.setTextColor(color)
        binding.cardCurrentPlayer.strokeColor = color
        binding.diceView.setPlayerColor(color)

        // Score indicators
        updateScoreBoard(state.players)
    }

    private fun updatePhaseUI(phase: GamePhase) {
        binding.btnRollDice.isEnabled = phase == GamePhase.WAITING_TO_ROLL

        binding.tvStatus.text = when (phase) {
            GamePhase.WAITING_TO_ROLL -> "🎲 Tap the dice to roll!"
            GamePhase.SHOWING_TRIVIA -> "📚 Read the trivia fact..."
            GamePhase.SELECTING_PAWN -> "🎯 Choose a pawn to move"
            GamePhase.TRIVIA_QUIZ -> "❓ Answer the question!"
            GamePhase.ANIMATING_MOVE -> "✨ Moving pawn..."
            GamePhase.GAME_OVER -> "🏆 Game Over!"
        }
    }

    private fun updateScoreBoard(players: List<Player>) {
        val scoreText = players.joinToString("  ") { player ->
            val finishedCount = player.pawns.count { it.state == PawnState.FINISHED }
            val emoji = when (player.color) {
                PlayerColor.RED -> "🔴"
                PlayerColor.BLUE -> "🔵"
                PlayerColor.GREEN -> "🟢"
                PlayerColor.YELLOW -> "🟡"
            }
            "$emoji $finishedCount/4"
        }
        binding.tvScoreBoard.text = scoreText
    }

    private fun showTriviaOverlay(card: TriviaCard, diceValue: Int) {
        val fragment = TriviaCardFragment.newInstance(card, diceValue)
        fragment.onDismissed = {
            viewModel.onTriviaDismissed()
        }
        fragment.show(supportFragmentManager, "trivia_card")
    }

    private fun showKillQuizDialog(pendingKill: PendingKill) {
        val fragment = KillQuizFragment.newInstance(pendingKill)
        fragment.onAnswered = { index ->
            viewModel.answerKillQuestion(index)
        }
        fragment.show(supportFragmentManager, "kill_quiz")
    }

    private fun showGameOverDialog(winner: Player) {
        val fragment = GameOverFragment.newInstance(winner)
        fragment.onPlayAgain = {
            recreate()
        }
        fragment.onMainMenu = {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        fragment.show(supportFragmentManager, "game_over")
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

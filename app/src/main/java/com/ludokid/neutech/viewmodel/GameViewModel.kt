package com.ludokid.neutech.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.ludokid.neutech.data.GameState
import com.ludokid.neutech.data.Pawn
import com.ludokid.neutech.game.GameEngine
import com.ludokid.neutech.game.GameEvent
import com.ludokid.neutech.trivia.TriviaRepository

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val triviaRepository = TriviaRepository(application)
    private val gameEngine = GameEngine(application, triviaRepository, viewModelScope)

    val gameState: LiveData<GameState> = gameEngine.gameState
    val uiEvent: LiveData<GameEvent> = gameEngine.uiEvent

    fun initGame(playerCount: Int, playerNames: List<String>) {
        gameEngine.initGame(playerCount, playerNames)
    }

    fun rollDice() = gameEngine.rollDice()

    fun selectPawn(pawn: Pawn) = gameEngine.selectPawn(pawn)

    fun answerKillQuestion(selectedAnswerIndex: Int) = gameEngine.answerKillQuestion(selectedAnswerIndex)

    fun onTriviaDismissed() = gameEngine.onTriviaDismissed()

    fun getCurrentPlayer() = gameEngine.getCurrentPlayer()
}

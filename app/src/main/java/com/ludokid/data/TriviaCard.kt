package com.ludokid.data

/**
 * Represents a trivia card shown when a player rolls the dice.
 * Contains a fact/information snippet and a related question.
 */
data class TriviaCard(
    val id: Int,
    val category: String,
    val emoji: String,
    val fact: String,          // Shown when dice is rolled
    val question: String,      // Asked when trying to kill a pawn
    val options: List<String>, // Multiple choice options
    val correctAnswerIndex: Int,
    val difficulty: Difficulty = Difficulty.MEDIUM
)

enum class Difficulty {
    EASY, MEDIUM, HARD
}

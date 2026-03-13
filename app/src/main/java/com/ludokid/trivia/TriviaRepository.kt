package com.ludokid.trivia

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ludokid.R
import com.ludokid.data.TriviaCard

/**
 * Manages the trivia card pool — loading, shuffling, and tracking seen cards.
 */
class TriviaRepository(private val context: Context) {

    private val allCards: MutableList<TriviaCard> = mutableListOf()
    private val shuffledDeck: MutableList<TriviaCard> = mutableListOf()
    private var deckIndex = 0

    init {
        loadCards()
    }

    private fun loadCards() {
        try {
            val inputStream = context.resources.openRawResource(R.raw.trivia_cards)
            val json = inputStream.bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<TriviaCard>>() {}.type
            val cards: List<TriviaCard> = Gson().fromJson(json, type)
            allCards.addAll(cards)
            reshuffleDeck()
        } catch (e: Exception) {
            e.printStackTrace()
            allCards.addAll(getFallbackCards())
            reshuffleDeck()
        }
    }

    private fun reshuffleDeck() {
        shuffledDeck.clear()
        shuffledDeck.addAll(allCards.shuffled())
        deckIndex = 0
    }

    /**
     * Returns the next trivia card from the shuffled deck.
     * Re-shuffles if we've gone through all cards.
     */
    fun getNextCard(): TriviaCard {
        if (deckIndex >= shuffledDeck.size) {
            reshuffleDeck()
        }
        return shuffledDeck[deckIndex++]
    }

    /**
     * Gets a random card from a specific list (used for kill quizzes).
     */
    fun getRandomFromSeen(seenCards: List<TriviaCard>): TriviaCard {
        return if (seenCards.isNotEmpty()) {
            seenCards.random()
        } else {
            getNextCard()
        }
    }

    fun getAllCards(): List<TriviaCard> = allCards.toList()

    private fun getFallbackCards(): List<TriviaCard> = listOf(
        TriviaCard(
            id = 1,
            category = "Science",
            emoji = "🔬",
            fact = "Water boils at 100°C at sea level.",
            question = "At what temperature does water boil at sea level?",
            options = listOf("80°C", "90°C", "100°C", "120°C"),
            correctAnswerIndex = 2
        ),
        TriviaCard(
            id = 2,
            category = "Space",
            emoji = "🚀",
            fact = "The Sun is a star at the center of our solar system.",
            question = "What type of object is the Sun?",
            options = listOf("Planet", "Star", "Asteroid", "Comet"),
            correctAnswerIndex = 1
        )
    )
}

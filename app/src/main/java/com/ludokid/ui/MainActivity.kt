package com.ludokid.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.ludokid.R
import com.ludokid.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var playerCount = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAnimations()
        setupPlayerCountSelector()
        setupStartButton()
    }

    private fun setupAnimations() {
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        binding.cardMainMenu.startAnimation(slideUp)
    }

    private fun setupPlayerCountSelector() {
        updatePlayerCountUI()

        binding.btnMinus.setOnClickListener {
            if (playerCount > 2) {
                playerCount--
                updatePlayerCountUI()
            }
        }

        binding.btnPlus.setOnClickListener {
            if (playerCount < 4) {
                playerCount++
                updatePlayerCountUI()
            }
        }
    }

    private fun updatePlayerCountUI() {
        binding.tvPlayerCount.text = playerCount.toString()
        binding.btnMinus.isEnabled = playerCount > 2
        binding.btnPlus.isEnabled = playerCount < 4

        // Update player name fields visibility
        binding.layoutPlayer3.visibility = if (playerCount >= 3) View.VISIBLE else View.GONE
        binding.layoutPlayer4.visibility = if (playerCount >= 4) View.VISIBLE else View.GONE
    }

    private fun setupStartButton() {
        binding.btnStartGame.setOnClickListener {
            val playerNames = listOf(
                binding.etPlayer1.text.toString().ifBlank { "Red Player" },
                binding.etPlayer2.text.toString().ifBlank { "Blue Player" },
                binding.etPlayer3.text.toString().ifBlank { "Green Player" },
                binding.etPlayer4.text.toString().ifBlank { "Yellow Player" }
            ).take(playerCount)

            val intent = Intent(this, GameActivity::class.java).apply {
                putExtra(GameActivity.EXTRA_PLAYER_COUNT, playerCount)
                putStringArrayListExtra(GameActivity.EXTRA_PLAYER_NAMES, ArrayList(playerNames))
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // How to play button
        binding.btnHowToPlay.setOnClickListener {
            showHowToPlay()
        }
    }

    private fun showHowToPlay() {
        binding.cardHowToPlay.visibility = if (binding.cardHowToPlay.visibility == View.VISIBLE)
            View.GONE else View.VISIBLE
    }
}

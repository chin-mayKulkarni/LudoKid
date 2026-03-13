package com.ludokid.ui

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.ludokid.R
import com.ludokid.data.TriviaCard
import com.ludokid.databinding.FragmentTriviaCardBinding

/**
 * Full-screen bottom sheet showing a trivia fact after dice roll.
 * Auto-dismisses after 8 seconds, or player can dismiss manually.
 */
class TriviaCardFragment : DialogFragment() {

    companion object {
        private const val ARG_CARD_ID = "card_id"
        private const val ARG_CARD_CATEGORY = "card_category"
        private const val ARG_CARD_EMOJI = "card_emoji"
        private const val ARG_CARD_FACT = "card_fact"
        private const val ARG_DICE_VALUE = "dice_value"

        fun newInstance(card: TriviaCard, diceValue: Int): TriviaCardFragment {
            return TriviaCardFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_CARD_ID, card.id)
                    putString(ARG_CARD_CATEGORY, card.category)
                    putString(ARG_CARD_EMOJI, card.emoji)
                    putString(ARG_CARD_FACT, card.fact)
                    putInt(ARG_DICE_VALUE, diceValue)
                }
            }
        }
    }

    private var _binding: FragmentTriviaCardBinding? = null
    private val binding get() = _binding!!

    var onDismissed: (() -> Unit)? = null
    private var countDownTimer: CountDownTimer? = null
    private val AUTO_DISMISS_MS = 8000L

    override fun getTheme() = R.style.Theme_FullScreenDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTriviaCardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val category = arguments?.getString(ARG_CARD_CATEGORY) ?: "Science"
        val emoji = arguments?.getString(ARG_CARD_EMOJI) ?: "🔬"
        val fact = arguments?.getString(ARG_CARD_FACT) ?: ""
        val diceValue = arguments?.getInt(ARG_DICE_VALUE) ?: 1

        binding.tvEmoji.text = emoji
        binding.tvCategory.text = category
        binding.tvFact.text = fact
        binding.tvDiceResult.text = "🎲 You rolled: $diceValue"

        // Warning message to memorize
        binding.tvMemorizeHint.text = "💡 Memorise this! You'll be asked a question if you can kill an opponent's pawn."

        startCountDown()

        binding.btnGotIt.setOnClickListener {
            countDownTimer?.cancel()
            dismissAndNotify()
        }

        // Entrance animation
        binding.root.translationY = 500f
        binding.root.animate()
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun startCountDown() {
        countDownTimer = object : CountDownTimer(AUTO_DISMISS_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.btnGotIt.text = "Got it! ✓ ($seconds s)"
                // Update progress
                val progress = ((AUTO_DISMISS_MS - millisUntilFinished).toFloat() / AUTO_DISMISS_MS * 100).toInt()
                binding.progressCountdown.progress = progress
            }

            override fun onFinish() {
                binding.btnGotIt.text = "Got it! ✓"
                dismissAndNotify()
            }
        }.start()
    }

    private fun dismissAndNotify() {
        dismiss()
        onDismissed?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
    }
}

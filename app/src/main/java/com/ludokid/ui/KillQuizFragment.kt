package com.ludokid.ui

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.ludokid.R
import com.ludokid.data.PendingKill
import com.ludokid.data.PlayerColor
import com.ludokid.data.TriviaCard
import com.ludokid.databinding.FragmentKillQuizBinding

/**
 * Quiz dialog shown when a player tries to kill an opponent's pawn.
 * Player must answer correctly from trivia they saw earlier.
 */
class KillQuizFragment : DialogFragment() {

    companion object {
        private const val ARG_QUESTION = "question"
        private const val ARG_OPT_0 = "opt_0"
        private const val ARG_OPT_1 = "opt_1"
        private const val ARG_OPT_2 = "opt_2"
        private const val ARG_OPT_3 = "opt_3"
        private const val ARG_CORRECT = "correct"
        private const val ARG_ATTACKER_COLOR = "attacker_color"
        private const val ARG_DEFENDER_COLOR = "defender_color"
        private const val ARG_EMOJI = "emoji"

        fun newInstance(pendingKill: PendingKill): KillQuizFragment {
            val card = pendingKill.triviaCard
            return KillQuizFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_QUESTION, card.question)
                    putString(ARG_OPT_0, card.options.getOrElse(0) { "" })
                    putString(ARG_OPT_1, card.options.getOrElse(1) { "" })
                    putString(ARG_OPT_2, card.options.getOrElse(2) { "" })
                    putString(ARG_OPT_3, card.options.getOrElse(3) { "" })
                    putInt(ARG_CORRECT, card.correctAnswerIndex)
                    putInt(ARG_ATTACKER_COLOR, pendingKill.attackingPawn.playerId)
                    putInt(ARG_DEFENDER_COLOR, pendingKill.defendingPawn.playerId)
                    putString(ARG_EMOJI, card.emoji)
                }
            }
        }
    }

    private var _binding: FragmentKillQuizBinding? = null
    private val binding get() = _binding!!

    var onAnswered: ((Int) -> Unit)? = null

    private var correctIndex = 0
    private var answered = false
    private var countDownTimer: CountDownTimer? = null
    private val TIME_LIMIT_MS = 15000L

    override fun getTheme() = R.style.Theme_FullScreenDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKillQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val question = arguments?.getString(ARG_QUESTION) ?: ""
        val options = listOf(
            arguments?.getString(ARG_OPT_0) ?: "",
            arguments?.getString(ARG_OPT_1) ?: "",
            arguments?.getString(ARG_OPT_2) ?: "",
            arguments?.getString(ARG_OPT_3) ?: ""
        )
        correctIndex = arguments?.getInt(ARG_CORRECT) ?: 0
        val emoji = arguments?.getString(ARG_EMOJI) ?: "❓"

        binding.tvQuizTitle.text = "⚔️ KILL CONFIRMED?"
        binding.tvQuizSubtitle.text = "Answer correctly to eliminate the opponent's pawn!"
        binding.tvEmoji.text = emoji
        binding.tvQuestion.text = question

        val optionButtons = listOf(
            binding.btnOption0,
            binding.btnOption1,
            binding.btnOption2,
            binding.btnOption3
        )

        options.forEachIndexed { index, option ->
            if (option.isNotEmpty()) {
                optionButtons[index].visibility = View.VISIBLE
                optionButtons[index].text = "${('A' + index)}. $option"
                optionButtons[index].setOnClickListener {
                    if (!answered) {
                        answered = true
                        countDownTimer?.cancel()
                        handleAnswer(index, optionButtons)
                    }
                }
            }
        }

        startTimer()

        // Animate in
        binding.root.scaleX = 0.8f
        binding.root.scaleY = 0.8f
        binding.root.alpha = 0f
        binding.root.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(350)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .start()
    }

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(TIME_LIMIT_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvTimer.text = "⏱️ $seconds s"
                val progress = ((TIME_LIMIT_MS - millisUntilFinished).toFloat() / TIME_LIMIT_MS * 100).toInt()
                binding.progressTimer.progress = progress

                if (seconds <= 5) {
                    binding.tvTimer.setTextColor(Color.RED)
                }
            }

            override fun onFinish() {
                if (!answered) {
                    answered = true
                    binding.tvTimer.text = "⏱️ Time's up!"
                    // Time's up = wrong answer (pawn safe)
                    handleAnswer(-1, listOf(
                        binding.btnOption0, binding.btnOption1,
                        binding.btnOption2, binding.btnOption3
                    ))
                }
            }
        }.start()
    }

    private fun handleAnswer(selectedIndex: Int, buttons: List<Button>) {
        val isCorrect = selectedIndex == correctIndex

        // Highlight correct and wrong answers
        buttons.forEachIndexed { index, button ->
            button.isEnabled = false
            when {
                index == correctIndex -> {
                    button.setBackgroundColor(Color.parseColor("#4CAF50"))
                    button.setTextColor(Color.WHITE)
                }
                index == selectedIndex && !isCorrect -> {
                    button.setBackgroundColor(Color.parseColor("#F44336"))
                    button.setTextColor(Color.WHITE)
                }
            }
        }

        binding.tvResult.visibility = View.VISIBLE
        if (isCorrect) {
            binding.tvResult.text = "✅ Correct! Pawn eliminated!"
            binding.tvResult.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            binding.tvResult.text = "❌ Wrong! Opponent's pawn is safe!"
            binding.tvResult.setTextColor(Color.parseColor("#F44336"))
        }

        // Dismiss after showing feedback
        binding.root.postDelayed({
            dismiss()
            onAnswered?.invoke(selectedIndex)
        }, 2000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
    }
}

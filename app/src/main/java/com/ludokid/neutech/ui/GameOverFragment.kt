package com.ludokid.neutech.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.ludokid.neutech.R
import com.ludokid.neutech.data.Player
import com.ludokid.neutech.data.PlayerColor
import com.ludokid.neutech.databinding.FragmentGameOverBinding

class GameOverFragment : DialogFragment() {

    companion object {
        private const val ARG_WINNER_NAME = "winner_name"
        private const val ARG_WINNER_COLOR = "winner_color"

        fun newInstance(winner: Player): GameOverFragment {
            return GameOverFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WINNER_NAME, winner.name)
                    putString(ARG_WINNER_COLOR, winner.color.colorHex)
                }
            }
        }
    }

    private var _binding: FragmentGameOverBinding? = null
    private val binding get() = _binding!!

    var onPlayAgain: (() -> Unit)? = null
    var onMainMenu: (() -> Unit)? = null

    override fun getTheme() = R.style.Theme_FullScreenDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGameOverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val winnerName = arguments?.getString(ARG_WINNER_NAME) ?: "Player"
        val winnerColorHex = arguments?.getString(ARG_WINNER_COLOR) ?: "#FFFFFF"

        binding.tvWinnerName.text = "🏆 $winnerName Wins!"
        binding.tvWinnerName.setTextColor(Color.parseColor(winnerColorHex))
        binding.tvCongratsMessage.text = "Congratulations! You mastered trivia and strategy!"

        binding.btnPlayAgain.setOnClickListener {
            dismiss()
            onPlayAgain?.invoke()
        }

        binding.btnMainMenu.setOnClickListener {
            dismiss()
            onMainMenu?.invoke()
        }

        // Trophy bounce animation
        binding.tvTrophy.animate()
            .scaleX(1.3f).scaleY(1.3f)
            .setDuration(500)
            .withEndAction {
                binding.tvTrophy.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(300)
                    .start()
            }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

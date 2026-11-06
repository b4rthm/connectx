package com.example.connectx.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.connectx.R
import com.example.connectx.databinding.FragmentGameBinding
import com.example.connectx.model.GameConfig
import com.example.connectx.model.GameState
import com.example.connectx.model.Player
import com.example.connectx.model.appearanceFor

/**
 * The game screen: a full-size board with press-&-hold column selection.
 * Game over is a state of this fragment, not a separate screen.
 */
class GameFragment : Fragment() {

    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GameViewModel by activityViewModels()

    /** Brief input lock after the turn flips in 2-player games, so the
     *  player who just moved can lift their finger without accidentally
     *  playing for the other player. */
    private val moveLockMs = 1000L
    private var lockRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnReset.setOnClickListener {
            showNewGameConfirmation()
        }

        // Touch on the board: press & hold to preview, release to drop
        binding.boardView.onColumnSelected = { column ->
            viewModel.makeMove(column)
        }

        viewModel.config.observe(viewLifecycleOwner) { config ->
            binding.boardView.setAppearances(
                config.player1Appearance,
                config.player2Appearance,
                config.aiAppearance
            )
        }

        // The AI's balloon preview (hovering over its target column)
        viewModel.aiPreviewColumn.observe(viewLifecycleOwner) { column ->
            if (column != null) {
                binding.boardView.showAIPreview(column)
            } else {
                binding.boardView.clearPreview()
            }
        }

        viewModel.gameState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is GameState.Playing -> showGameScreen(state)
                is GameState.GameOver -> showGameOverScreen(state)
                is GameState.Configuring -> {
                    // Navigation is handled by the nav graph (reset pops to setup)
                }
            }
        }
    }

    private fun showGameScreen(state: GameState.Playing) {
        val config = viewModel.config.value
        binding.tvGameTitle.text = getString(R.string.title_connect_n, config?.connect ?: 4)
        binding.boardView.setActivePlayer(state.currentPlayer)

        val vsAI = config?.vsAI == true
        val isAITurn = vsAI && state.currentPlayer == Player.Player2
        val hadMoves = state.moveHistory.isNotEmpty()

        lockRunnable?.let { view?.removeCallbacks(it) }
        lockRunnable = null

        when {
            isAITurn -> binding.boardView.setInteractionEnabled(false)
            // Turn flipped in a local 2-player game: lock input briefly
            hadMoves && !vsAI -> {
                binding.boardView.setInteractionEnabled(false)
                val moveCount = state.moveHistory.size
                val runnable = Runnable {
                    val current = viewModel.gameState.value as? GameState.Playing
                    if (current != null && current.moveHistory.size == moveCount) {
                        _binding?.boardView?.setInteractionEnabled(true)
                    }
                }
                lockRunnable = runnable
                view?.postDelayed(runnable, moveLockMs)
            }
            else -> binding.boardView.setInteractionEnabled(true)
        }

        // Let the AI think, preview its column, then play
        if (isAITurn) {
            viewModel.requestAIMove()
        }

        if (config != null) {
            updatePlayerIndicator(state.currentPlayer, config)
        }
        binding.boardView.setBoard(state.board, animate = true)
        binding.boardView.setWinLine(null)
    }

    private fun showGameOverScreen(state: GameState.GameOver) {
        val config = viewModel.config.value
        binding.tvGameTitle.text = if (state.winner == null) {
            getString(R.string.game_over_draw)
        } else {
            getString(R.string.game_over_winner, winnerName(state.winner))
        }
        binding.chipCurrentPlayer.isVisible = false
        binding.boardView.setInteractionEnabled(false)
        binding.boardView.setBoard(state.finalBoard, animate = false)
        binding.boardView.setWinLine(state.winLine)
    }

    private fun updatePlayerIndicator(player: Player, config: GameConfig) {
        val appearance = config.appearanceFor(player)
        binding.chipCurrentPlayer.isVisible = true
        binding.chipCurrentPlayer.text = getString(
            R.string.current_player_waiting,
            playerName(player)
        )
        if (appearance.iconRes != 0) {
            binding.chipCurrentPlayer.chipIcon =
                AppCompatResources.getDrawable(requireContext(), appearance.iconRes)
        }
        binding.chipCurrentPlayer.chipIconTint =
            ColorStateList.valueOf(appearance.color)
    }

    private fun showNewGameConfirmation() {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_new_game_title)
            .setMessage(R.string.dialog_new_game_message)
            .setPositiveButton(R.string.btn_reset) { _, _ ->
                viewModel.resetGame()
                navigateToSetup()
            }
            .setNegativeButton(R.string.btn_cancel) { d, _ ->
                d.dismiss()
            }
            .create()
        dialog.show()
    }

    private fun navigateToSetup() {
        findNavController().navigate(R.id.action_game_to_setup)
    }

    private fun playerName(player: Player): String = when (player) {
        Player.Player1 -> getString(R.string.player1_name)
        // In AI mode the engine plays the second seat as the AI
        Player.Player2 -> if (viewModel.config.value?.vsAI == true) getString(R.string.ki_name) else getString(R.string.player2_name)
        Player.AI -> getString(R.string.ki_name)
    }

    private fun winnerName(winner: Player?): String = when (winner) {
        Player.Player1 -> getString(R.string.player1_name)
        Player.Player2 -> if (viewModel.config.value?.vsAI == true) getString(R.string.ki_name) else getString(R.string.player2_name)
        Player.AI -> getString(R.string.ki_name)
        null -> getString(R.string.game_over_draw)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lockRunnable?.let { view?.removeCallbacks(it) }
        lockRunnable = null
        _binding = null
    }
}

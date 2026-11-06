package com.example.connectx.ui

import android.graphics.Color
import android.os.Bundle
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.connectx.R
import com.example.connectx.databinding.FragmentSetupBinding
import com.example.connectx.model.SetupSettings

/**
 * Game configuration screen (board size, win length, AI settings).
 *
 * The appearance (icons/colors) lives on its own screen; only a compact
 * summary is shown here. All settings are persisted: the last configuration
 * is restored on the next launch and stored again on every change.
 */
class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GameViewModel by activityViewModels()

    // Current appearance choices, for the summary preview
    private var p1Icon = 0
    private var p1Color = 0
    private var p2Icon = 1
    private var p2Color = 1
    private var p2IconExplicit = false

    /** The selected AI level (1 = Casual, 2 = Normal). */
    private var aiLevel: Int = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        applySettings(viewModel.loadSettings())
    }

    private fun setupListeners() {
        binding.btnMinusWidth.setOnClickListener { stepValue(binding.tvWidth, -1, 6, 15) }
        binding.btnPlusWidth.setOnClickListener { stepValue(binding.tvWidth, 1, 6, 15) }
        binding.btnMinusHeight.setOnClickListener { stepValue(binding.tvHeight, -1, 6, 15) }
        binding.btnPlusHeight.setOnClickListener { stepValue(binding.tvHeight, 1, 6, 15) }
        binding.btnMinusConnect.setOnClickListener { stepValue(binding.tvConnect, -1, 3, 8) }
        binding.btnPlusConnect.setOnClickListener { stepValue(binding.tvConnect, 1, 3, 8) }

        binding.switchAI.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutAILevel.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateSwitchAppearance()
            // An untouched P2 icon follows the mode default (AI = A, 2P = star down)
            if (!p2IconExplicit) p2Icon = SetupSettings.defaultIcon2(isChecked)
            updateSummary()
            updateStartButtonState()
            viewModel.saveSettings(currentSettings())
        }

        binding.btnDecreaseAI.setOnClickListener {
            aiLevel = (aiLevel - 1).coerceIn(1, 2)
            binding.tvAILevel.text = aiLevelName()
            viewModel.saveSettings(currentSettings())
        }

        binding.btnIncreaseAI.setOnClickListener {
            aiLevel = (aiLevel + 1).coerceIn(1, 2)
            binding.tvAILevel.text = aiLevelName()
            viewModel.saveSettings(currentSettings())
        }

        binding.tvAILevel.contentDescription = "AI level" 

        binding.rowAppearance.setOnClickListener {
            findNavController().navigate(R.id.action_setup_to_appearance)
        }

        binding.btnStart.setOnClickListener { startGame() }
    }

    // ---------------------------------------------------------------------------------------------
    // Appearance summary
    // ---------------------------------------------------------------------------------------------

    private fun aiLevelName(): String = when (aiLevel) {
        1 -> getString(R.string.ai_level_casual)
        else -> getString(R.string.ai_level_normal)
    }

    private fun updateSummary() {
        val ctx = requireContext()
        val ring = AppearancePicker.ringColor(ctx)
        val size = AppearancePicker.dp(ctx, 34f)
        val base1 = AppearancePicker.baseColorFor(ctx, p1Color)
        val base2 = AppearancePicker.baseColorFor(ctx, p2Color)
        binding.previewP1.background = AppearancePicker.discBackground(size, base1, true, ring, 2)
        binding.previewP1.setImageResource(GameViewModel.DISC_ICONS[p1Icon])
        binding.previewP1.imageTintList = ColorStateList.valueOf(AppearancePicker.motifColorFor(base1))
        binding.previewP2.background = AppearancePicker.discBackground(size, base2, true, ring, 2)
        binding.previewP2.setImageResource(GameViewModel.DISC_ICONS[p2Icon])
        binding.previewP2.imageTintList = ColorStateList.valueOf(AppearancePicker.motifColorFor(base2))
        binding.tvP1Label.setText(R.string.player1_name)
        binding.tvP2Label.setText(
            if (binding.switchAI.isChecked) R.string.player2_ai_name else R.string.player2_name
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Settings plumbing
    // ---------------------------------------------------------------------------------------------

    private fun stepValue(valueView: TextView, delta: Int, min: Int, max: Int) {
        val current = valueView.text.toString().toIntOrNull() ?: min
        valueView.text = (current + delta).coerceIn(min, max).toString()
        updateStartButtonState()
        viewModel.saveSettings(currentSettings())
    }

    private fun applySettings(settings: SetupSettings) {
        binding.tvWidth.text = settings.width.toString()
        binding.tvHeight.text = settings.height.toString()
        binding.tvConnect.text = settings.connect.toString()
        binding.switchAI.isChecked = settings.vsAI
        aiLevel = settings.aiLevel.coerceIn(1, 2)
        binding.tvAILevel.text = aiLevelName()
        binding.layoutAILevel.visibility = if (settings.vsAI) View.VISIBLE else View.GONE

        p1Icon = settings.icon1.coerceAtLeast(0)
        p1Color = settings.color1.coerceAtLeast(0)
        p2IconExplicit = settings.icon2 >= 0
        p2Icon = SetupSettings.resolveIcon2(settings.icon2, settings.vsAI)
        p2Color = settings.color2.coerceAtLeast(0)

        updateSwitchAppearance()
        updateSummary()
        updateStartButtonState()
    }

    /** The current values read from the UI (-1 = "use default"). */
    private fun currentSettings(): SetupSettings {
        return SetupSettings(
            width = binding.tvWidth.text.toString().toIntOrNull() ?: 7,
            height = binding.tvHeight.text.toString().toIntOrNull() ?: 6,
            connect = binding.tvConnect.text.toString().toIntOrNull() ?: 4,
            vsAI = binding.switchAI.isChecked,
            aiLevel = aiLevel,
            icon1 = p1Icon,
            icon2 = if (p2IconExplicit) p2Icon else -1,
            color1 = p1Color,
            color2 = p2Color
        )
    }

    /**
     * The switch is colored (brand teal) when AI is on and neutral grey
     * when it is off.
     */
    private fun updateSwitchAppearance() {
        val checked = binding.switchAI.isChecked
        val resId = if (checked) R.color.switch_on else R.color.switch_off
        val base = ContextCompat.getColor(requireContext(), resId)
        val trackAlpha = if (checked) 130 else 90
        binding.switchAI.thumbTintList = ColorStateList.valueOf(base)
        binding.switchAI.trackTintList = ColorStateList.valueOf(
            Color.argb(trackAlpha, Color.red(base), Color.green(base), Color.blue(base))
        )
    }

    private fun updateStartButtonState() {
        val width = binding.tvWidth.text.toString().toIntOrNull() ?: 0
        val height = binding.tvHeight.text.toString().toIntOrNull() ?: 0
        val connect = binding.tvConnect.text.toString().toIntOrNull() ?: 0

        val valid = width >= 6 && height >= 6 && connect >= 3 &&
                connect <= width && connect <= height
        binding.btnStart.isEnabled = valid
        binding.btnStart.alpha = if (valid) 1f else 0.5f
    }

    private fun startGame() {
        val settings = currentSettings()
        viewModel.saveSettings(settings)
        viewModel.updateConfig(settings)
        viewModel.startGame()
        findNavController().navigate(R.id.action_setup_to_game)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

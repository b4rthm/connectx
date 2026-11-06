package com.example.connectx.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.connectx.R
import com.example.connectx.databinding.FragmentAppearanceBinding
import com.example.connectx.model.SetupSettings

/**
 * Dedicated screen for disc appearance: icon and color for each player.
 * Changes are saved immediately and reloaded whenever the screen opens.
 */
class AppearanceFragment : Fragment() {

    private var _binding: FragmentAppearanceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GameViewModel by activityViewModels()

    private var p1Icon = 0
    private var p1Color = 0
    private var p2Icon = 1
    private var p2Color = 1
    private var p2IconExplicit = false
    private var vsAI = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppearanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Always load the current (persisted) settings
        val settings = viewModel.loadSettings()
        vsAI = settings.vsAI
        p1Icon = settings.icon1.coerceAtLeast(0)
        p1Color = settings.color1.coerceAtLeast(0)
        p2IconExplicit = settings.icon2 >= 0
        p2Icon = SetupSettings.resolveIcon2(settings.icon2, vsAI)
        p2Color = settings.color2.coerceAtLeast(0)

        val goBack = View.OnClickListener { findNavController().navigateUp() }
        binding.btnBack.setOnClickListener(goBack)
        binding.btnDone.setOnClickListener(goBack)

        refresh()
    }

    /** Rebuilds both players' picker grids and the P2 label from the current state. */
    private fun refresh() {
        val ctx = requireContext()
        binding.tvP2Label.setText(if (vsAI) R.string.player2_ai_name else R.string.player2_name)

        AppearancePicker.buildIconGrid(
            ctx, binding.iconBlockP1,
            AppearancePicker.baseColorFor(ctx, p1Color),
            p1Icon
        ) { index ->
            p1Icon = index
            refresh()
            save()
        }
        AppearancePicker.buildSwatchGrid(ctx, binding.colorBlockP1, p1Color) { index ->
            p1Color = index
            refresh()
            save()
        }
        AppearancePicker.buildIconGrid(
            ctx, binding.iconBlockP2,
            AppearancePicker.baseColorFor(ctx, p2Color),
            p2Icon
        ) { index ->
            p2Icon = index
            p2IconExplicit = true
            refresh()
            save()
        }
        AppearancePicker.buildSwatchGrid(ctx, binding.colorBlockP2, p2Color) { index ->
            p2Color = index
            refresh()
            save()
        }
    }

    /** Saves the appearance fields, keeping everything else as persisted. */
    private fun save() {
        val current = viewModel.loadSettings()
        viewModel.saveSettings(
            current.copy(
                icon1 = p1Icon,
                icon2 = if (p2IconExplicit) p2Icon else -1,
                color1 = p1Color,
                color2 = p2Color
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

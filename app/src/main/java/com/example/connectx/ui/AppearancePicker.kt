package com.example.connectx.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.connectx.R

/**
 * Shared building blocks for the appearance pickers.
 *
 * Icon choices are rendered exactly like the discs in the game: a disc in
 * the chosen color (same gradient) with the motif on top. Swatches are
 * plain circles of the palette colors.
 */
object AppearancePicker {

    private const val ICONS_PER_ROW = 6
    private const val SWATCHES_PER_ROW = 8

    fun paletteColors(context: Context): IntArray =
        GameViewModel.PALETTE.map { ContextCompat.getColor(context, it) }.toIntArray()

    fun ringColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.picker_ring)

    fun dp(context: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
    ).toInt()

    /** The resolved base color for a palette choice. */
    fun baseColorFor(context: Context, colorIndex: Int): Int {
        val palette = paletteColors(context)
        return palette[colorIndex.coerceIn(0, palette.size - 1)]
    }

    private fun blend(base: Int, other: Int, fraction: Float): Int {
        fun mix(a: Int, b: Int) = (a + ((b - a) * fraction)).toInt()
        return Color.rgb(
            mix(Color.red(base), Color.red(other)),
            mix(Color.green(base), Color.green(other)),
            mix(Color.blue(base), Color.blue(other))
        )
    }

    /** The light/dark variants exactly as the game derives them. */
    fun lightOf(base: Int): Int = blend(base, Color.WHITE, 0.45f)
    fun darkOf(base: Int): Int = blend(base, Color.BLACK, 0.40f)

    /**
     * The motif color as rendered in the game: white on dark discs, a dark
     * blend on light discs so it stays readable on every color.
     */
    fun motifColorFor(base: Int): Int {
        val luminance = (0.299 * Color.red(base) + 0.587 * Color.green(base) + 0.114 * Color.blue(base)) / 255f
        return if (luminance > 0.55f) blend(base, Color.BLACK, 0.60f) else Color.WHITE
    }

    /**
     * A game-like disc background (radial light → base → dark) for picker
     * buttons and the setup summary; ringed when [selected].
     */
    fun discBackground(sizePx: Int, base: Int, selected: Boolean, ring: Int, strokeWidthPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientRadius(sizePx * 0.75f)
            setColors(intArrayOf(lightOf(base), base, darkOf(base)))
            if (selected) setStroke(strokeWidthPx, ring)
        }
    }

    /**
     * The disc icon choices as a grid of mini discs (2 rows of 6): each
     * shows the motif on a disc in the player's color, exactly like in the
     * game. The selected one gets a ring.
     */
    fun buildIconGrid(
        context: Context,
        container: LinearLayout,
        base: Int,
        selected: Int,
        onChange: (Int) -> Unit
    ) {
        val ring = ringColor(context)
        val tint = ColorStateList.valueOf(motifColorFor(base))
        container.removeAllViews()
        var row: LinearLayout? = null
        var rowIndex = 0
        GameViewModel.DISC_ICONS.forEachIndexed { index, iconRes ->
            if (index % ICONS_PER_ROW == 0) {
                row = horizontalRow(context).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    (layoutParams as LinearLayout.LayoutParams).topMargin =
                        if (rowIndex > 0) dp(context, 8f) else 0
                }.also { container.addView(it) }
                rowIndex++
            }
            val size = dp(context, 44f)
            val isLastInRow = index % ICONS_PER_ROW == ICONS_PER_ROW - 1
            val button = ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = if (isLastInRow) 0 else dp(context, 10f)
                }
                setImageResource(iconRes)
                imageTintList = tint
                background = discBackground(size, base, index == selected, ring, dp(context, 2f))
                contentDescription = "Disc icon $index"
                setOnClickListener { onChange(index) }
            }
            row!!.addView(button)
        }
    }

    /**
     * The disc color palette as a grid of swatches (4 rows of 8); the
     * selected one gets a ring, all of them a subtle outline so they stay
     * visible in both themes.
     */
    fun buildSwatchGrid(
        context: Context,
        container: LinearLayout,
        selected: Int,
        onChange: (Int) -> Unit
    ) {
        val colors = paletteColors(context)
        val ring = ringColor(context)
        val outline = ContextCompat.getColor(context, R.color.picker_swatch_stroke)
        container.removeAllViews()
        var row: LinearLayout? = null
        var rowIndex = 0
        for (index in colors.indices) {
            if (index % SWATCHES_PER_ROW == 0) {
                row = horizontalRow(context).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    (layoutParams as LinearLayout.LayoutParams).topMargin =
                        if (rowIndex > 0) dp(context, 7f) else 0
                }.also { container.addView(it) }
                rowIndex++
            }
            val size = dp(context, 24f)
            val isLastInRow = index % SWATCHES_PER_ROW == SWATCHES_PER_ROW - 1
            val swatch = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = if (isLastInRow) 0 else dp(context, 7f)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colors[index])
                    if (index == selected) setStroke(dp(context, 2.5f), ring)
                    else setStroke(dp(context, 1f), outline)
                }
                contentDescription = "Color $index"
                setOnClickListener { onChange(index) }
            }
            row!!.addView(swatch)
        }
    }

    private fun horizontalRow(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
}

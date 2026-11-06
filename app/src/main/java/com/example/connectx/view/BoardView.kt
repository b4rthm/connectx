package com.example.connectx.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.example.connectx.R
import com.example.connectx.model.Board
import com.example.connectx.model.Player
import com.example.connectx.model.PlayerAppearance
import com.example.connectx.model.WinLine
import com.example.connectx.ui.AppearancePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * Custom view for rendering the Connect X game board.
 *
 * Input: press and hold anywhere on the board to select a column. A balloon
 * (disc with knot + string) docks just below the bottom edge of the grid and
 * hovers there (gently swaying) for as long as the user holds. Moving the
 * finger moves the balloon fluidly in the horizontal. On release the tail is
 * cut, the balloon becomes a coin and flies into its landing spot in the grid.
 */
class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var board: Board = Board(7, 6)
    private var winLine: WinLine? = null

    // Player appearances, supplied from GameConfig
    private var player1Appearance = PlayerAppearance.DEFAULT
    private var player2Appearance = PlayerAppearance.DEFAULT
    private var aiAppearance = PlayerAppearance.DEFAULT
    private var activePlayer: Player? = null

    // Preview (balloon) state
    private var previewColumn = -1
    private var previewRow = -1
    private var previewPlayer: Player? = null
    private var previewStartTime = 0L
    private var previewX = -1f          // smoothed on-screen position of the balloon
    private var previewY = -1f
    private var previewTargetX = -1f    // finger x the balloon chases
    private var previewHangY = 0f       // y where it hovers at the bottom edge
    private var previewIsAI = false
    private var previewSwayX = 0f       // applied at draw time, never accumulated
    private var previewAnimator: ValueAnimator? = null
    private var interactionEnabled = false

    // Drop animation state (the coin after the tail was cut)
    private var animatingColumn = -1
    private var animatingRow = -1
    private var animatingPlayer: Player? = null
    private var animationProgress = 0f
    private var animStartX = 0f
    private var animStartY = 0f

    // Where the tail was cut (so it can fade away during the drop)
    private var tailCutX = -1f
    private var tailCutY = -1f
    private var tailCutTime = 0L

    /** The balloon position captured on release, used as drop start. */
    private var pendingDropStartX = -1f
    private var pendingDropStartY = -1f

    /** Callback when the user releases on a column. */
    var onColumnSelected: ((Int) -> Unit)? = null

    // Layout metrics
    private var discRadius = 0f
    private var slotSize = 0f
    private var boardOffsetX = 0f
    private var boardOffsetY = 0f

    // Paints
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val slotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val discHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val discShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 4f, Color.parseColor("#40000000"))
    }
    private val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tailKnotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val winGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val winLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Reusable path for the balloon tail
    private val tailPath = Path()
    private val knotPath = Path()
    private val notchPath = Path()

    // Board colors (resolved from resources)
    private var boardColor = 0
    private var slotColor = 0
    private var slotStrokeColor = 0
    private var notchColor = 0

    // Icon cache (keyed by drawable resource)
    private val iconCache = HashMap<Int, Drawable?>()

    // Animation
    private var animationJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        resolveColors()
        initPaints()
        isClickable = true
    }

    fun setAppearances(
        player1: PlayerAppearance,
        player2: PlayerAppearance,
        ai: PlayerAppearance
    ) {
        player1Appearance = player1
        player2Appearance = player2
        aiAppearance = ai
        invalidate()
    }

    fun setActivePlayer(player: Player) {
        activePlayer = player
        if (previewColumn >= 0) previewPlayer = player
        invalidate()
    }

    fun setInteractionEnabled(enabled: Boolean) {
        interactionEnabled = enabled
        if (!enabled) clearPreview()
    }

    fun appearanceFor(player: Player): PlayerAppearance = when (player) {
        Player.Player1 -> player1Appearance
        Player.Player2 -> player2Appearance
        Player.AI -> aiAppearance
    }

    private fun resolveColors() {
        boardColor = ContextCompat.getColor(context, R.color.board_bg)
        slotColor = ContextCompat.getColor(context, R.color.slot_empty)
        slotStrokeColor = ContextCompat.getColor(context, R.color.slot_stroke)
        notchColor = ContextCompat.getColor(context, R.color.board_notch)
    }

    private fun initPaints() {
        boardPaint.color = boardColor
        slotPaint.color = slotColor
        slotStrokePaint.color = slotStrokeColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLayout()
    }

    private fun calculateLayout() {
        val padding = 16f
        val availableWidth = width - padding * 2
        val availableHeight = height - padding * 2

        // Reserve vertical space below the grid so the balloon (disc + knot +
        // string) always has room to dangle between the board and the bottom bar.
        val dangleSlots = 1.6f
        slotSize = minOf(
            availableWidth / board.width,
            availableHeight / (board.height + dangleSlots)
        ).coerceIn(36f, 210f)

        discRadius = slotSize * 0.42f

        // Center the (grid + dangle zone) block: this biases the grid slightly
        // upward while guaranteeing the bottom clearance for the balloon.
        boardOffsetX = (width - slotSize * board.width) / 2f
        boardOffsetY = (height - slotSize * board.height - dangleSlots * slotSize) / 2f
    }

    fun setBoard(newBoard: Board, animate: Boolean = false) {
        val oldBoard = this.board
        // An AI move: the coin flies from where the AI balloon was hovering
        if (previewIsAI && previewColumn >= 0) {
            val (bx, by) = balloonCenter()
            pendingDropStartX = bx
            pendingDropStartY = by
            tailCutX = bx
            tailCutY = by
            tailCutTime = SystemClock.uptimeMillis()
        }
        clearPreview()
        this.board = newBoard
        if (oldBoard.width != newBoard.width || oldBoard.height != newBoard.height) {
            calculateLayout()
        }

        if (animate && oldBoard.width == newBoard.width && oldBoard.height == newBoard.height) {
            // Find the new disc
            for (col in 0 until board.width) {
                for (row in 0 until board.height) {
                    if (oldBoard[col, row] != newBoard[col, row] && newBoard[col, row] != null) {
                        startDropAnimation(col, row, newBoard[col, row]!!)
                        return
                    }
                }
            }
        }
        invalidate()
    }

    fun setWinLine(winLine: WinLine?) {
        clearPreview()
        this.winLine = winLine
        if (winLine != null) {
            startWinAnimation()
        }
        invalidate()
    }

    // ---------------------------------------------------------------------------------------------
    // Preview (balloon) lifecycle
    // ---------------------------------------------------------------------------------------------

    /** Removes any active preview (human or AI). */
    fun clearPreview() {
        if (previewColumn == -1 && previewAnimator == null) return
        previewColumn = -1
        previewRow = -1
        previewPlayer = null
        previewX = -1f
        previewY = -1f
        previewTargetX = -1f
        previewIsAI = false
        previewSwayX = 0f
        previewAnimator?.cancel()
        previewAnimator = null
        invalidate()
    }

    private fun startPreviewAnimator() {
        previewAnimator?.cancel()
        previewAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 100L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                updatePreviewFrame()
                postInvalidate()
            }
            start()
        }
    }

    private fun beginPreview(col: Int, x: Float) {
        val row = board.getEmptyRow(col) ?: return
        previewColumn = col
        previewRow = row
        previewPlayer = activePlayer
        previewIsAI = false
        previewStartTime = SystemClock.uptimeMillis()
        // The balloon docks just below the bottom edge of the grid
        previewHangY = boardOffsetY + board.height * slotSize + discRadius
        // Keep the smoothed x for fluidity across column switches
        if (previewX < 0f) previewX = x
        previewTargetX = x
        previewY = previewHangY

        startPreviewAnimator()
        invalidate()
    }

    /**
     * Programmatic preview for the AI: the balloon hovers at the target
     * column's center for as long as the AI is "aiming".
     */
    fun showAIPreview(column: Int) {
        val row = board.getEmptyRow(column) ?: return
        previewColumn = column
        previewRow = row
        previewPlayer = Player.AI
        previewIsAI = true
        previewStartTime = SystemClock.uptimeMillis()
        previewHangY = boardOffsetY + board.height * slotSize + discRadius
        val cx = boardOffsetX + column * slotSize + slotSize / 2f
        previewTargetX = cx
        previewX = cx
        previewY = previewHangY

        startPreviewAnimator()
        invalidate()
    }

    /** Advances the hover animation while the user holds. */
    private fun updatePreviewFrame() {
        if (previewColumn < 0) return
        val t = (SystemClock.uptimeMillis() - previewStartTime) / 1000f

        // Fluid horizontal tracking of the finger (AI preview hovers in place)
        val minX = boardOffsetX
        val maxX = boardOffsetX + board.width * slotSize
        if (!previewIsAI) {
            previewX += (previewTargetX - previewX) * 0.30f
        }
        previewX = previewX.coerceIn(minX, maxX)

        // Hover mode: stays at the bottom edge, gently swaying, no rising.
        // Sway is a pure function of time and is only applied at draw time,
        // so it can never drift the base position.
        previewSwayX = sin(t * 2.1f) * slotSize * 0.045f
        val swayY = sin(t * 1.6f) * slotSize * 0.08f
        previewY = previewHangY + swayY
    }

    /** Current on-screen position of the balloon (including sway). */
    private fun balloonCenter(): Pair<Float, Float> = (previewX + previewSwayX) to previewY

    // ---------------------------------------------------------------------------------------------
    // Touch handling: press & hold to aim, release to drop
    // ---------------------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactionEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                updatePreview(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val col = previewColumn
                // Capture the balloon position and cut the tail before the state changes
                if (col in 0 until board.width && previewPlayer != null) {
                    val (bx, by) = balloonCenter()
                    pendingDropStartX = bx
                    pendingDropStartY = by
                    tailCutX = bx
                    tailCutY = by
                    tailCutTime = SystemClock.uptimeMillis()
                }
                clearPreview()
                if (col in 0 until board.width && !board.isColumnFull(col)) {
                    onColumnSelected?.invoke(col)
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clearPreview()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePreview(x: Float) {
        previewTargetX = x
        val col = ((x - boardOffsetX) / slotSize).toInt()
        val target = if (col in 0 until board.width && board.getEmptyRow(col) != null) col else -1
        if (target != previewColumn) {
            if (target >= 0) beginPreview(target, x) else clearPreview()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Animations
    // ---------------------------------------------------------------------------------------------

    private fun startDropAnimation(column: Int, row: Int, player: Player) {
        animatingColumn = column
        animatingRow = row
        animatingPlayer = player
        animationProgress = 0f
        animStartX = if (pendingDropStartX >= 0f) pendingDropStartX
            else boardOffsetX + column * slotSize + slotSize / 2f
        animStartY = if (pendingDropStartY >= 0f) pendingDropStartY
            else boardOffsetY + row * slotSize + slotSize / 2f
        pendingDropStartX = -1f
        pendingDropStartY = -1f

        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            val distance = kotlin.math.abs((boardOffsetY + row * slotSize + slotSize / 2f) - animStartY)
            ValueAnimator.ofFloat(0f, 1f).apply {
                // The coin launches from where the balloon was and settles softly
                duration = (220f + (distance / (slotSize * 2f)) * 200f).toLong().coerceAtMost(600L)
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    animationProgress = animation.animatedValue as Float
                    postInvalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        animatingColumn = -1
                        animatingRow = -1
                        animatingPlayer = null
                        animationProgress = 0f
                        tailCutX = -1f
                        postInvalidate()
                    }
                })
                start()
            }
        }
    }

    private fun startWinAnimation() {
        // Pulse animation for winning discs
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1500L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    animationProgress = animation.animatedValue as Float
                    postInvalidate()
                }
                start()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBoardBackground(canvas)
        drawColumnNotches(canvas)
        drawSlots(canvas)
        drawDiscs(canvas)
        drawAnimatingDisc(canvas)
        drawPreviewBalloon(canvas)
        drawWinHighlight(canvas)
    }

    /**
     * Very subtle dimples in the bottom edge of the board, one per column,
     * so you can tell which column the balloon is hovering over.
     */
    private fun drawColumnNotches(canvas: Canvas) {
        val bottomY = boardOffsetY + board.height * slotSize
        val halfWidth = slotSize * 0.26f
        val depth = slotSize * 0.055f

        for (col in 0 until board.width) {
            val cx = boardOffsetX + col * slotSize + slotSize / 2f
            notchPath.reset()
            notchPath.moveTo(cx - halfWidth, bottomY)
            notchPath.quadTo(cx, bottomY - depth * 2f, cx + halfWidth, bottomY)
            notchPath.close()
            // The column the balloon is in gets a slightly brighter dimple
            notchPaint.color = if (interactionEnabled && col == previewColumn) slotColor else notchColor
            canvas.drawPath(notchPath, notchPaint)
        }
    }

    private fun drawBoardBackground(canvas: Canvas) {
        val boardRect = RectF(
            boardOffsetX - 8f,
            boardOffsetY - 8f,
            boardOffsetX + slotSize * board.width + 8f,
            boardOffsetY + slotSize * board.height + 8f
        )
        val radius = 16f
        canvas.drawRoundRect(boardRect, radius, radius, boardPaint)
    }

    private fun drawSlots(canvas: Canvas) {
        for (col in 0 until board.width) {
            for (row in 0 until board.height) {
                val cx = boardOffsetX + col * slotSize + slotSize / 2f
                val cy = boardOffsetY + row * slotSize + slotSize / 2f

                // Slot background
                canvas.drawCircle(cx, cy, discRadius + 4f, slotPaint)
                canvas.drawCircle(cx, cy, discRadius + 4f, slotStrokePaint)
            }
        }
    }

    private fun drawDiscs(canvas: Canvas) {
        for (col in 0 until board.width) {
            for (row in 0 until board.height) {
                val player = board[col, row]
                if (player != null && !(col == animatingColumn && row == animatingRow && animatingPlayer != null)) {
                    drawDisc(canvas, col, row, player)
                }
            }
        }
    }

    private fun drawDisc(canvas: Canvas, col: Int, row: Int, player: Player) {
        val cx = boardOffsetX + col * slotSize + slotSize / 2f
        val cy = boardOffsetY + row * slotSize + slotSize / 2f
        drawDiscAtPosition(canvas, cx, cy, discRadius, appearanceFor(player))
    }

    /**
     * The coin while it drops after the tail was cut, plus the fading tail
     * where it was severed.
     */
    private fun drawAnimatingDisc(canvas: Canvas) {
        if (animatingColumn < 0 || animatingPlayer == null) return

        val appearance = appearanceFor(animatingPlayer!!)
        val endX = boardOffsetX + animatingColumn * slotSize + slotSize / 2f
        val endY = boardOffsetY + animatingRow * slotSize + slotSize / 2f
        val p = animationProgress
        val cx = animStartX + (endX - animStartX) * p
        val cy = animStartY + (endY - animStartY) * p

        // Fading tail at the cut position
        if (tailCutX >= 0f) {
            val fade = (1f - (SystemClock.uptimeMillis() - tailCutTime) / 250f).coerceIn(0f, 1f)
            if (fade > 0f) {
                drawTail(canvas, tailCutX, tailCutY, discRadius, appearance, alpha = fade)
            }
        }

        drawDiscAtPosition(canvas, cx, cy, discRadius, appearance)
    }

    /**
     * The balloon: disc body + knot + swaying string, floating above its
     * landing spot while the user holds.
     */
    private fun drawPreviewBalloon(canvas: Canvas) {
        // The AI preview draws even while human input is locked
        if (previewColumn < 0 || previewPlayer == null || (!interactionEnabled && !previewIsAI)) return
        val appearance = appearanceFor(previewPlayer!!)
        val (cx, cy) = balloonCenter()

        drawTail(canvas, cx, cy, discRadius, appearance, alpha = 1f)
        drawDiscAtPosition(canvas, cx, cy, discRadius, appearance)
    }

    /**
     * The part that gets "cut": a small knot below the disc plus a string
     * that undulates gently like a snake (a slow traveling wave, kept
     * subtle), so the disc reads as a balloon.
     */
    private fun drawTail(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        appearance: PlayerAppearance,
        alpha: Float = 1f
    ) {
        tailKnotPaint.color = appearance.darkColor
        tailKnotPaint.alpha = (alpha * 255).toInt()

        val bottom = cy + radius * 0.96f
        val knotH = radius * 0.22f
        val knotW = radius * 0.15f

        knotPath.reset()
        knotPath.moveTo(cx - knotW, bottom)
        knotPath.lineTo(cx + knotW, bottom)
        knotPath.lineTo(cx, bottom + knotH)
        knotPath.close()
        canvas.drawPath(knotPath, tailKnotPaint)

        // String: a traveling wave anchored at the knot — the amplitude
        // starts at zero right below the balloon (vertical tangent, no kink)
        // and grows toward the tip, so the string undulates softly like a snake.
        tailPaint.color = appearance.darkColor
        tailPaint.alpha = (alpha * 230).toInt()
        tailPaint.strokeWidth = maxOf(2f, radius * 0.07f)

        val time = SystemClock.uptimeMillis() / 1000f
        val baseY = bottom + knotH
        val len = radius * 1.15f
        val samples = 20

        tailPath.reset()
        tailPath.moveTo(cx, baseY)
        for (i in 1..samples) {
            val depth = i / samples.toFloat()
            val ramp = depth * (0.3f + 0.7f * depth)
            val phase = time * 2.6f + depth * 2.4f
            val amp = radius * 0.24f * ramp
            tailPath.lineTo(cx + sin(phase) * amp, baseY + len * depth)
        }
        canvas.drawPath(tailPath, tailPaint)
    }

    private fun drawDiscAtPosition(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        appearance: PlayerAppearance,
        isWinning: Boolean = false
    ) {
        // Shadow
        discShadowPaint.color = appearance.darkColor
        canvas.drawCircle(cx, cy + 3f, radius, discShadowPaint)

        // Main disc with gradient
        val shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f,
            radius * 1.5f,
            intArrayOf(appearance.lightColor, appearance.color, appearance.darkColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        discPaint.shader = shader
        discPaint.color = appearance.color
        canvas.drawCircle(cx, cy, radius, discPaint)
        discPaint.shader = null

        // Motif icon on top of the disc, tinted so it reads on every color
        if (appearance.iconRes != 0) {
            iconFor(appearance.iconRes)?.let { icon ->
                icon.setTint(AppearancePicker.motifColorFor(appearance.color))
                val size = (radius * 0.95f).toInt()
                val left = (cx - size / 2f).toInt()
                val top = (cy - size / 2f).toInt()
                icon.setBounds(left, top, left + size, top + size)
                icon.draw(canvas)
            }
        }

        // Highlight
        discHighlightPaint.color = Color.argb(60, 255, 255, 255)
        canvas.drawCircle(cx - radius * 0.3f, cy - radius * 0.3f, radius * 0.4f, discHighlightPaint)

        // Inner glow for winning discs
        if (isWinning && winLine != null) {
            val glowAlpha = (100 + 155 * animationProgress).toInt()
            winGlowPaint.color = Color.argb(
                glowAlpha,
                Color.red(appearance.color),
                Color.green(appearance.color),
                Color.blue(appearance.color)
            )
            canvas.drawCircle(cx, cy, radius + 8f * animationProgress, winGlowPaint)
        }
    }

    private fun drawWinHighlight(canvas: Canvas) {
        winLine?.let { line ->
            if (line.cells.size < 2) return

            val first = line.cells.first()
            val last = line.cells.last()

            val startX = boardOffsetX + first.column * slotSize + slotSize / 2f
            val startY = boardOffsetY + first.row * slotSize + slotSize / 2f
            val endX = boardOffsetX + last.column * slotSize + slotSize / 2f
            val endY = boardOffsetY + last.row * slotSize + slotSize / 2f

            val player = board[first.column, first.row] ?: return
            val color = appearanceFor(player).color

            winLinePaint.color = color
            winLinePaint.strokeWidth = slotSize * 0.15f
            canvas.drawLine(startX, startY, endX, endY, winLinePaint)

            // Draw circles at each winning cell
            for (cell in line.cells) {
                drawDisc(canvas, cell.column, cell.row, player)
            }
        }
    }

    private fun iconFor(iconRes: Int): Drawable? {
        return iconCache.getOrPut(iconRes) {
            AppCompatResources.getDrawable(context, iconRes)?.mutate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        previewAnimator?.cancel()
        previewAnimator = null
        animationJob?.cancel()
        coroutineScope.cancel()
        clearPreview()
    }
}

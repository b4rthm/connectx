package com.example.connectx.model

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IntRange

/**
 * Represents a player in the game.
 * Using sealed class for type safety and exhaustiveness checking.
 */
sealed class Player(val id: Int) {
    object Player1 : Player(1)
    object Player2 : Player(2)
    object AI : Player(3)

    companion object {
        fun fromId(id: Int): Player = when (id) {
            1 -> Player1
            2 -> Player2
            3 -> AI
            else -> throw IllegalArgumentException("Invalid player id: $id")
        }

        fun next(current: Player): Player = when (current) {
            Player1 -> Player2
            Player2 -> Player1
            AI -> Player1
        }
    }
}

/**
 * Visual appearance of a player's discs: colors (resolved ARGB ints, so they
 * can later come from a color picker) and the motif icon to draw on top.
 */
data class PlayerAppearance(
    @ColorInt val color: Int,
    @ColorInt val lightColor: Int,
    @ColorInt val darkColor: Int,
    @DrawableRes val iconRes: Int
) {
    /**
     * Context-free fallback (bordeaux disc, no icon). The UI layer builds
     * resource-backed appearances; this keeps the model Android-independent.
     */
    companion object {
        val DEFAULT = PlayerAppearance(
            color = 0xFF7D2A3A.toInt(),
            lightColor = 0xFFC76B7A.toInt(),
            darkColor = 0xFF4E1622.toInt(),
            iconRes = 0
        )
    }
}

/**
 * Represents a cell on the game board.
 */
data class Cell(
    val column: Int,
    val row: Int,
    val player: Player? = null
) {
    val isEmpty: Boolean get() = player == null
}

/**
 * Game configuration parameters.
 */
data class GameConfig(
    @IntRange(from = 6, to = 15) val width: Int = 7,
    @IntRange(from = 6, to = 15) val height: Int = 6,
    @IntRange(from = 3, to = 8) val connect: Int = 4,
    val vsAI: Boolean = false,
    val aiLevel: Int = 1,
    val player1Appearance: PlayerAppearance = PlayerAppearance.DEFAULT,
    val player2Appearance: PlayerAppearance = PlayerAppearance.DEFAULT,
    val aiAppearance: PlayerAppearance = PlayerAppearance.DEFAULT
) {
    companion object {
        private const val MIN_DIMENSION = 6
        private const val MAX_DIMENSION = 15
        private const val MIN_CONNECT = 3
        private const val MAX_CONNECT = 8

        fun create(
            width: Int,
            height: Int,
            connect: Int,
            vsAI: Boolean = false,
            aiLevel: Int = 1,
            player1Appearance: PlayerAppearance = PlayerAppearance.DEFAULT,
            player2Appearance: PlayerAppearance = PlayerAppearance.DEFAULT,
            aiAppearance: PlayerAppearance = PlayerAppearance.DEFAULT
        ): GameConfig {
            return GameConfig(
                width = width.coerceIn(MIN_DIMENSION, MAX_DIMENSION),
                height = height.coerceIn(MIN_DIMENSION, MAX_DIMENSION),
                connect = connect.coerceIn(MIN_CONNECT, minOf(MAX_CONNECT, maxOf(width, height))),
                vsAI = vsAI,
                aiLevel = aiLevel.coerceIn(1, 2),
                player1Appearance = player1Appearance,
                player2Appearance = player2Appearance,
                aiAppearance = aiAppearance
            )
        }
    }
}

/**
 * Resolves the appearance for the given player from the game config.
 */
fun GameConfig.appearanceFor(player: Player): PlayerAppearance = when (player) {
    Player.Player1 -> player1Appearance
    Player.Player2 -> player2Appearance
    Player.AI -> aiAppearance
}

/**
 * Represents a winning combination.
 */
data class WinLine(
    val cells: List<Cell>,
    val direction: Direction
) {
    enum class Direction {
        HORIZONTAL, VERTICAL, DIAGONAL_UP, DIAGONAL_DOWN
    }
}

/**
 * Game state representation.
 */
sealed class GameState {
    data class Playing(
        val currentPlayer: Player,
        val board: Board,
        val moveHistory: List<Move> = emptyList()
    ) : GameState()

    data class GameOver(
        val winner: Player?,
        val winLine: WinLine?,
        val finalBoard: Board,
        val moveHistory: List<Move>
    ) : GameState()

    data class Configuring(val config: GameConfig) : GameState()
}

/**
 * Represents a move in the game.
 */
data class Move(
    val player: Player,
    val column: Int,
    val row: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * The game board.
 */
data class Board(
    val width: Int,
    val height: Int,
    private var cells: Array<Array<Player?>> = Array(0) { Array(0) { null } }
) {
    init {
        require(width >= 4 && width <= 20) { "Width must be between 4 and 20" }
        require(height >= 4 && height <= 20) { "Height must be between 4 and 20" }
        if (cells.isEmpty()) {
            this.cells = Array(width) { Array(height) { null } }
        } else {
            require(cells.size == width) { "Cell array width mismatch" }
            require(cells[0].size == height) { "Cell array height mismatch" }
        }
    }

    operator fun get(column: Int, row: Int): Player? = cells[column][row]

    fun set(column: Int, row: Int, player: Player?): Board {
        val newCells = cells.map { it.copyOf() }.toTypedArray()
        newCells[column][row] = player
        return copy(cells = newCells)
    }

    fun getColumnHeight(column: Int): Int {
        return cells[column].indexOfFirst { it != null }.let { if (it == -1) height else it }
    }

    fun isColumnFull(column: Int): Boolean = cells[column][height - 1] != null

    fun isFull(): Boolean = (0 until width).all { isColumnFull(it) }

    fun getEmptyRow(column: Int): Int? {
        return cells[column].indexOfFirst { it == null }.takeIf { it != -1 }
    }

    fun getAllCells(): List<Cell> {
        val result = mutableListOf<Cell>()
        for (col in 0 until width) {
            for (row in 0 until height) {
                result.add(Cell(col, row, cells[col][row]))
            }
        }
        return result
    }

    fun getCellsInColumn(column: Int): List<Cell> {
        return (0 until height).map { row -> Cell(column, row, cells[column][row]) }
    }
}
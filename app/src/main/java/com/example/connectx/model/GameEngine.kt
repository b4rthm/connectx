package com.example.connectx.model

import kotlin.math.abs
import kotlin.random.Random

/**
 * Core game engine - pure logic, no Android dependencies.
 */
class GameEngine(private val config: GameConfig) {

    private val winPatterns = generateWinPatterns()

    companion object {
        fun create(config: GameConfig): GameEngine = GameEngine(config)
    }

    fun createInitialState(): GameState.Playing {
        val board = Board(config.width, config.height)
        val firstPlayer = if (Math.random() < 0.5) Player.Player1 else Player.Player2
        return GameState.Playing(firstPlayer, board)
    }

    /**
     * Attempts to make a move. Returns new state or null if invalid.
     */
    fun makeMove(state: GameState.Playing, column: Int): GameState? {
        if (column !in 0 until config.width) return null

        val row = state.board.getEmptyRow(column) ?: return null
        val newBoard = state.board.set(column, row, state.currentPlayer)
        val move = Move(state.currentPlayer, column, row)
        val newHistory = state.moveHistory + move

        // Check for win
        val winLine = checkWin(newBoard, column, row, state.currentPlayer)
        if (winLine != null) {
            return GameState.GameOver(state.currentPlayer, winLine, newBoard, newHistory)
        }

        // Check for draw
        if (newBoard.isFull()) {
            return GameState.GameOver(null, null, newBoard, newHistory)
        }

        // Continue game
        val nextPlayer = Player.next(state.currentPlayer)
        // If next is AI and vsAI is enabled, we'll handle that in the ViewModel
        return GameState.Playing(nextPlayer, newBoard, newHistory)
    }

    /**
     * Gets the AI move for the current state.
     */
    fun getAIMove(state: GameState.Playing): Int? {
        return when (config.aiLevel) {
            1 -> getCasualMove(state.board, state.currentPlayer, config.connect)
            else -> getSmartMove(state.board, state.currentPlayer, config.connect)
        }
    }

    private fun checkWin(board: Board, lastCol: Int, lastRow: Int, player: Player): WinLine? {
        val directions = listOf(
            Pair(1, 0),   // Horizontal
            Pair(0, 1),   // Vertical
            Pair(1, 1),   // Diagonal down-right
            Pair(1, -1)   // Diagonal up-right
        )

        for ((dx, dy) in directions) {
            val cells = mutableListOf<Cell>()
            cells.add(Cell(lastCol, lastRow, player))

            // Check positive direction
            var cx = lastCol + dx
            var cy = lastRow + dy
            while (cx in 0 until config.width && cy in 0 until config.height && board[cx, cy] == player) {
                cells.add(Cell(cx, cy, player))
                cx += dx
                cy += dy
            }

            // Check negative direction
            cx = lastCol - dx
            cy = lastRow - dy
            while (cx in 0 until config.width && cy in 0 until config.height && board[cx, cy] == player) {
                cells.add(0, Cell(cx, cy, player))
                cx -= dx
                cy -= dy
            }

            if (cells.size >= config.connect) {
                val direction = when {
                    dx == 1 && dy == 0 -> WinLine.Direction.HORIZONTAL
                    dx == 0 && dy == 1 -> WinLine.Direction.VERTICAL
                    dx == 1 && dy == 1 -> WinLine.Direction.DIAGONAL_DOWN
                    else -> WinLine.Direction.DIAGONAL_UP
                }
                return WinLine(cells, direction)
            }
        }
        return null
    }

    private fun generateWinPatterns(): List<List<Pair<Int, Int>>> {
        val patterns = mutableListOf<List<Pair<Int, Int>>>()
        val w = config.width
        val h = config.height
        val c = config.connect

        // Horizontal
        for (row in 0 until h) {
            for (col in 0 until w - c + 1) {
                patterns.add((0 until c).map { Pair(col + it, row) })
            }
        }
        // Vertical
        for (col in 0 until w) {
            for (row in 0 until h - c + 1) {
                patterns.add((0 until c).map { Pair(col, row + it) })
            }
        }
        // Diagonal down-right
        for (row in 0 until h - c + 1) {
            for (col in 0 until w - c + 1) {
                patterns.add((0 until c).map { Pair(col + it, row + it) })
            }
        }
        // Diagonal up-right
        for (row in c - 1 until h) {
            for (col in 0 until w - c + 1) {
                patterns.add((0 until c).map { Pair(col + it, row - it) })
            }
        }
        return patterns
    }

    /**
     * Level 1 "Casual": plays like a relaxed beginner. Takes an obvious
     * win most (but not always) of the time, blocks an obvious loss about
     * half the time, otherwise picks a center-weighted random column.
     */
    private fun getCasualMove(board: Board, aiPlayer: Player, connect: Int): Int? {
        val opponent = Player.next(aiPlayer)

        findWinningColumn(board, aiPlayer)?.let {
            if (Random.nextFloat() < 0.7f) return it
        }
        findWinningColumn(board, opponent)?.let {
            if (Random.nextFloat() < 0.5f) return it
        }

        val available = (0 until config.width).filter { !board.isColumnFull(it) }
        if (available.isEmpty()) return null
        val center = (config.width - 1) / 2
        val weights = available.map { 1 + (center - abs(it - center)) }
        var pick = Random.nextInt(weights.sum() * 100)
        for ((index, weight) in weights.withIndex()) {
            pick -= weight * 100
            if (pick < 0) return available[index]
        }
        return available.last()
    }

    /**
     * Level 2 "Normal": a solid heuristic following the classic Connect-4
     * ladder — every column is scored, best score wins:
     *  1. take an immediate win
     *  2. never leave the opponent an immediate win (block)
     *  3. create a fork (two threats with one move)
     *  4. avoid moves that hand the opponent a fork or an open line
     *  5. build and keep open lines of our own
     *  6. prefer the center
     */
    private fun getSmartMove(board: Board, aiPlayer: Player, connect: Int): Int? {
        val opponent = Player.next(aiPlayer)
        val center = (config.width - 1) / 2
        var bestCol: Int? = null
        var bestScore = Int.MIN_VALUE

        for (col in 0 until config.width) {
            if (board.isColumnFull(col)) continue
            val row = board.getEmptyRow(col) ?: continue
            val after = board.set(col, row, aiPlayer)
            var score = center - abs(col - center)

            if (checkWin(after, col, row, aiPlayer) != null) {
                score += 1_000_000 // 1. we win now
            } else {
                if (findWinningColumn(after, opponent) != null) score -= 200_000 // 2.
                val threats = newThreats(after, col, row, aiPlayer, opponent, connect)
                score += threats.second // 5.
                if (threats.first >= 2) score += 10_000 // 3. fork
                if (opponentCanFork(after, opponent, aiPlayer, connect)) score -= 20_000 // 4a.
                if (opponentHasOpenTwo(after, opponent, aiPlayer, connect)) score -= 1_000 // 4b.
            }

            if (score > bestScore) {
                bestScore = score
                bestCol = col
            }
        }
        return bestCol
    }

    /** A column whose move would complete a line for [player], or null. */
    private fun findWinningColumn(board: Board, player: Player): Int? {
        for (col in 0 until config.width) {
            if (board.isColumnFull(col)) continue
            val row = board.getEmptyRow(col) ?: continue
            if (checkWin(board.set(col, row, player), col, row, player) != null) return col
        }
        return null
    }

    /**
     * Threat windows created by the disc just placed at (col, row): windows
     * with connect-1 of [player]'s discs and no opponent disc. Returns the
     * count plus a summed value — threats with an open end are worth far
     * more, because the opponent cannot cover both ends.
     */
    private fun newThreats(
        board: Board, col: Int, row: Int, player: Player, opponent: Player, connect: Int
    ): Pair<Int, Int> {
        var count = 0
        var value = 0
        for (pattern in winPatterns) {
            var hasCell = false
            var mine = 0
            var theirs = 0
            for ((pc, pr) in pattern) {
                if (pc == col && pr == row) hasCell = true
                when (board[pc, pr]) {
                    player -> mine++
                    opponent -> theirs++
                    else -> {}
                }
            }
            if (!hasCell || theirs != 0 || mine != connect - 1) continue
            count++
            value += if (endOpenness(board, pattern) >= 1) 1_000 else 100
        }
        return count to value
    }

    /** How many of the two cells just outside the window's ends are open. */
    private fun endOpenness(board: Board, pattern: List<Pair<Int, Int>>): Int {
        val steps = (pattern.size - 1).coerceAtLeast(1)
        val (c0, r0) = pattern.first()
        val (c1, r1) = pattern.last()
        val dx = (c1 - c0) / steps
        val dy = (r1 - r0) / steps
        val aOpen = (c0 - dx) in 0 until config.width &&
            (r0 - dy) in 0 until config.height && board[c0 - dx, r0 - dy] == null
        val bOpen = (c1 + dx) in 0 until config.width &&
            (r1 + dy) in 0 until config.height && board[c1 + dx, r1 + dy] == null
        return (if (aOpen) 1 else 0) + (if (bOpen) 1 else 0)
    }

    /** True if the opponent could create a fork (two threats) on [board]. */
    private fun opponentCanFork(board: Board, opponent: Player, aiPlayer: Player, connect: Int): Boolean {
        for (col in 0 until config.width) {
            if (board.isColumnFull(col)) continue
            val row = board.getEmptyRow(col) ?: continue
            val after = board.set(col, row, opponent)
            if (newThreats(after, col, row, opponent, aiPlayer, connect).first >= 2) return true
        }
        return false
    }

    /**
     * True if the opponent has a live open line: connect-2 of their discs,
     * none of ours, and both ends open (a fork in the making).
     */
    private fun opponentHasOpenTwo(board: Board, opponent: Player, aiPlayer: Player, connect: Int): Boolean {
        if (connect < 3) return false
        for (pattern in winPatterns) {
            var mine = 0
            var theirs = 0
            var empty = 0
            for ((pc, pr) in pattern) {
                when (board[pc, pr]) {
                    opponent -> theirs++
                    aiPlayer -> mine++
                    else -> empty++
                }
            }
            if (theirs == connect - 2 && mine == 0 && empty == 2 &&
                endOpenness(board, pattern) == 2
            ) return true
        }
        return false
    }

    private fun getBestMove(board: Board, aiPlayer: Player, connect: Int): Int? {
        // Level 3: Minimax with alpha-beta pruning (depth limited)
        val opponent = Player.Player1
        var bestScore = Int.MIN_VALUE
        var bestCol: Int? = null

        val columns = (0 until config.width)
            .filter { !board.isColumnFull(it) }
            .sortedBy { Math.abs(it - config.width / 2) }

        for (col in columns) {
            val row = board.getEmptyRow(col)!!
            val newBoard = board.set(col, row, aiPlayer)
            val score = minimax(newBoard, 4, Int.MIN_VALUE, Int.MAX_VALUE, false, aiPlayer, opponent, connect)
            if (score > bestScore) {
                bestScore = score
                bestCol = col
            }
        }
        return bestCol
    }

    private fun minimax(
        board: Board,
        depth: Int,
        alpha: Int,
        beta: Int,
        maximizing: Boolean,
        aiPlayer: Player,
        opponent: Player,
        connect: Int
    ): Int {
        // Check terminal states
        if (depth == 0) return evaluateBoard(board, aiPlayer, opponent, connect)

        val available = (0 until config.width).filter { !board.isColumnFull(it) }
        if (available.isEmpty()) return 0

        if (maximizing) {
            var maxEval = Int.MIN_VALUE
            var currentAlpha = alpha
            for (col in available) {
                val row = board.getEmptyRow(col)!!
                val newBoard = board.set(col, row, aiPlayer)
                if (checkWin(newBoard, col, row, aiPlayer) != null) return 10000 - depth
                val eval = minimax(newBoard, depth - 1, currentAlpha, beta, false, aiPlayer, opponent, connect)
                maxEval = maxEval.coerceAtLeast(eval)
                currentAlpha = currentAlpha.coerceAtLeast(eval)
                if (beta <= currentAlpha) break
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            var currentBeta = beta
            for (col in available) {
                val row = board.getEmptyRow(col)!!
                val newBoard = board.set(col, row, opponent)
                if (checkWin(newBoard, col, row, opponent) != null) return -10000 + depth
                val eval = minimax(newBoard, depth - 1, alpha, currentBeta, true, aiPlayer, opponent, connect)
                minEval = minEval.coerceAtMost(eval)
                currentBeta = currentBeta.coerceAtMost(eval)
                if (currentBeta <= alpha) break
            }
            return minEval
        }
    }

    private fun evaluateBoard(board: Board, aiPlayer: Player, opponent: Player, connect: Int): Int {
        var score = 0

        // Center column preference
        val centerCol = config.width / 2
        for (row in 0 until config.height) {
            if (board[centerCol, row] == aiPlayer) score += 3
            else if (board[centerCol, row] == opponent) score -= 3
        }

        // Evaluate all win patterns
        for (pattern in winPatterns) {
            var aiCount = 0
            var oppCount = 0
            var emptyCount = 0

            for ((col, row) in pattern) {
                when (board[col, row]) {
                    aiPlayer -> aiCount++
                    opponent -> oppCount++
                    else -> emptyCount++
                }
            }

            if (oppCount == 0) {
                score += when (aiCount) {
                    connect - 1 -> 100
                    connect - 2 -> 10
                    connect - 3 -> 1
                    else -> 0
                }
            } else if (aiCount == 0) {
                score -= when (oppCount) {
                    connect - 1 -> 100
                    connect - 2 -> 10
                    connect - 3 -> 1
                    else -> 0
                }
            }
        }

        return score
    }
}
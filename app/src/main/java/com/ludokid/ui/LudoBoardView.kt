package com.ludokid.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.ludokid.data.*
import com.ludokid.game.LudoBoard
import kotlin.math.min

/**
 * Custom canvas-based Ludo board view.
 *
 * Board layout: 15x15 grid
 * - 4 colored home quadrants (6x6 each in corners)
 * - Cross-shaped path in center (3 cols wide)
 * - Central finishing zone (6x6 triangle areas)
 */
class LudoBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ─── Colors ──────────────────────────────────────────────────────────────

    private val colorRed = Color.parseColor("#E53935")
    private val colorBlue = Color.parseColor("#1E88E5")
    private val colorGreen = Color.parseColor("#43A047")
    private val colorYellow = Color.parseColor("#FDD835")
    private val colorWhite = Color.parseColor("#FAFAFA")
    private val colorBorder = Color.parseColor("#424242")
    private val colorSafe = Color.parseColor("#E8F5E9")
    private val colorSafeStar = Color.parseColor("#FFF8E1")

    private val lightRed = Color.parseColor("#FFCDD2")
    private val lightBlue = Color.parseColor("#BBDEFB")
    private val lightGreen = Color.parseColor("#C8E6C9")
    private val lightYellow = Color.parseColor("#FFF9C4")

    // ─── Paints ───────────────────────────────────────────────────────────────

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorBorder
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB300")
        textAlign = Paint.Align.CENTER
    }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF00")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private var cellSize = 0f
    private var boardOffset = 0f

    var players: List<Player> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var selectablePawns: List<Pawn> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var onPawnSelected: ((Pawn) -> Unit)? = null

    // 15x15 grid of board cell positions mapped to pixel coordinates
    private val cellCenters = Array(15) { Array(15) { PointF(0f, 0f) } }

    // Map from board position to grid (row, col)
    private val positionToGrid = mutableMapOf<Int, Pair<Int, Int>>()

    // ─── Layout ───────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        cellSize = size / 15f
        boardOffset = (w - size) / 2f

        for (row in 0..14) {
            for (col in 0..14) {
                cellCenters[row][col] = PointF(
                    boardOffset + col * cellSize + cellSize / 2f,
                    row * cellSize + cellSize / 2f
                )
            }
        }
        buildPositionMap()
    }

    /**
     * Maps Ludo path positions (0-51) to grid (row, col) coordinates.
     * Standard Ludo CCW path.
     */
    private fun buildPositionMap() {
        positionToGrid.clear()
        // Bottom side going left (Red's start area)
        val path = listOf(
            // Row 14 (bottom), cols 6..8
            14 to 6, 13 to 6, 12 to 6, 11 to 6, 10 to 6, 9 to 6,
            // Col 6, rows 9..7
            8 to 6, 7 to 6,
            // Row 6, left side
            6 to 5, 6 to 4, 6 to 3, 6 to 2, 6 to 1, 6 to 0,
            // Up
            5 to 0, 4 to 0, 3 to 0, 2 to 0, 1 to 0,
            // Right across top
            0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5,
            // Down col
            0 to 6, 1 to 6, 2 to 6,
            // Across top-right
            3 to 6, 4 to 6, 5 to 6,
            6 to 7, 6 to 8,
            // Right block
            6 to 9, 6 to 10, 6 to 11, 6 to 12, 6 to 13, 6 to 14,
            5 to 14, 4 to 14, 3 to 14, 2 to 14, 1 to 14,
            0 to 14,
            // Down right side
            0 to 13, 0 to 12, 0 to 11, 0 to 10, 0 to 9,
            1 to 9, 2 to 9, 3 to 9, 4 to 9, 5 to 9,
            6 to 9  // placeholder — repeats handled
        )
        // Just use simplified standard positions
        val simplePath = buildSimplePath()
        simplePath.forEachIndexed { index, pair ->
            positionToGrid[index] = pair
        }
    }

    private fun buildSimplePath(): List<Pair<Int, Int>> {
        // Standard Ludo 52-cell path in grid coordinates (15x15 grid)
        return listOf(
            // Red Arm (0-4)
            6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5,
            // Green Arm Up (5-10)
            5 to 6, 4 to 6, 3 to 6, 2 to 6, 1 to 6, 0 to 6,
            // Top Edge (11-12)
            0 to 7, 0 to 8,
            // Green Arm Down (13-17)  [13 is Green Start]
            1 to 8, 2 to 8, 3 to 8, 4 to 8, 5 to 8,
            // Yellow Arm Right (18-23)
            6 to 9, 6 to 10, 6 to 11, 6 to 12, 6 to 13, 6 to 14,
            // Right Edge (24-25)
            7 to 14, 8 to 14,
            // Yellow Arm Left (26-30) [26 is Yellow Start]
            8 to 13, 8 to 12, 8 to 11, 8 to 10, 8 to 9,
            // Blue Arm Down (31-36)
            9 to 8, 10 to 8, 11 to 8, 12 to 8, 13 to 8, 14 to 8,
            // Bottom Edge (37-38)
            14 to 7, 14 to 6,
            // Blue Arm Up (39-43) [39 is Blue Start]
            13 to 6, 12 to 6, 11 to 6, 10 to 6, 9 to 6,
            // Red Arm Left (44-49)
            8 to 5, 8 to 4, 8 to 3, 8 to 2, 8 to 1, 8 to 0,
            // Left Edge (50-51)
            7 to 0, 6 to 0
        )
    }

    // ─── Draw ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        drawHomeZones(canvas)
        drawPath(canvas)
        drawSafeZones(canvas)
        drawCenterStar(canvas)
        drawPawns(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        fillPaint.color = Color.parseColor("#F5F5DC")
        canvas.drawRect(boardOffset, 0f, boardOffset + 15 * cellSize, 15 * cellSize, fillPaint)
    }

    private fun drawHomeZones(canvas: Canvas) {
        // Red (bottom-left, rows 9-14, cols 0-5)
        drawHomeZone(canvas, 9, 0, colorRed, lightRed, "🔴")
        // Green (top-left, rows 0-5, cols 0-5)
        drawHomeZone(canvas, 0, 0, colorGreen, lightGreen, "🟢")
        // Yellow (top-right, rows 0-5, cols 9-14)
        drawHomeZone(canvas, 0, 9, colorYellow, lightYellow, "🟡")
        // Blue (bottom-right, rows 9-14, cols 9-14)
        drawHomeZone(canvas, 9, 9, colorBlue, lightBlue, "🔵")
    }

    private fun drawHomeZone(
        canvas: Canvas, startRow: Int, startCol: Int,
        borderColor: Int, fillColor: Int, emoji: String
    ) {
        val left = boardOffset + startCol * cellSize
        val top = startRow * cellSize
        val right = left + 6 * cellSize
        val bottom = top + 6 * cellSize

        // Outer border
        fillPaint.color = borderColor
        canvas.drawRect(left, top, right, bottom, fillPaint)

        // Inner white area
        val pad = cellSize * 0.5f
        fillPaint.color = fillColor
        canvas.drawRoundRect(
            left + pad, top + pad, right - pad, bottom - pad,
            cellSize * 0.3f, cellSize * 0.3f, fillPaint
        )

        borderPaint.color = borderColor
        canvas.drawRect(left, top, right, bottom, borderPaint)
    }

    private fun drawPath(canvas: Canvas) {
        for (row in 0..14) {
            for (col in 0..14) {
                if (isPathCell(row, col)) {
                    val left = boardOffset + col * cellSize
                    val top = row * cellSize

                    // Cell background
                    val isSafeCell = isSafeCellGrid(row, col)
                    fillPaint.color = if (isSafeCell) colorSafeStar
                    else getPathColor(row, col)

                    canvas.drawRect(left, top, left + cellSize, top + cellSize, fillPaint)
                    borderPaint.color = colorBorder
                    borderPaint.strokeWidth = 1f
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, borderPaint)

                    // Draw star on safe cells
                    if (isSafeCell) {
                        starPaint.textSize = cellSize * 0.6f
                        canvas.drawText(
                            "★",
                            boardOffset + col * cellSize + cellSize / 2f,
                            row * cellSize + cellSize * 0.72f,
                            starPaint
                        )
                    }
                }
            }
        }
    }

    private fun isPathCell(row: Int, col: Int): Boolean {
        // Horizontal middle path
        if (row in 6..8 && col in 0..14) {
            if (row == 7 && col in 1..5) return false // skip home interior
            if (row == 7 && col in 9..13) return false
            return true
        }
        // Vertical middle path
        if (col in 6..8 && row in 0..14) {
            if (col == 7 && row in 1..5) return false
            if (col == 7 && row in 9..13) return false
            return true
        }
        return false
    }

    private fun isSafeCellGrid(row: Int, col: Int): Boolean {
        val path = buildSimplePath()
        val index = path.indexOfFirst { it.first == row && it.second == col }
        return LudoBoard.SAFE_CELLS.contains(index)
    }

    private fun getPathColor(row: Int, col: Int): Int {
        return when {
            row in 7..7 && col in 1..5 -> lightRed    // Red home stretch
            row in 7..7 && col in 9..13 -> lightBlue
            col in 7..7 && row in 1..5 -> lightGreen
            col in 7..7 && row in 9..13 -> lightYellow
            else -> colorWhite
        }
    }

    private fun drawSafeZones(canvas: Canvas) {
        // Red safe zone: row 7, cols 1-6
        for (col in 1..5) {
            val left = boardOffset + col * cellSize
            val top = 7 * cellSize
            fillPaint.color = lightRed
            canvas.drawRect(left, top, left + cellSize, top + cellSize, fillPaint)
        }
        // Green safe zone: rows 1-5, col 7
        for (r in 1..5) {
            fillPaint.color = lightGreen
            canvas.drawRect(
                boardOffset + 7 * cellSize, r * cellSize,
                boardOffset + 8 * cellSize, (r + 1) * cellSize, fillPaint
            )
        }
        // Blue safe zone: row 7 cols 9-13
        for (col in 9..13) {
            fillPaint.color = lightBlue
            canvas.drawRect(
                boardOffset + col * cellSize, 7 * cellSize,
                boardOffset + (col + 1) * cellSize, 8 * cellSize, fillPaint
            )
        }
        // Yellow safe zone: rows 9-13, col 7
        for (r in 9..13) {
            fillPaint.color = lightYellow
            canvas.drawRect(
                boardOffset + 7 * cellSize, r * cellSize,
                boardOffset + 8 * cellSize, (r + 1) * cellSize, fillPaint
            )
        }
    }

    private fun drawCenterStar(canvas: Canvas) {
        // Center 3x3 with colored triangles
        val cx = boardOffset + 7.5f * cellSize
        val cy = 7.5f * cellSize
        val half = 1.5f * cellSize

        // Draw 4 colored triangles
        val trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // Top triangle (Green)
        trianglePaint.color = colorGreen
        val topPath = Path().apply {
            moveTo(cx, cy)
            lineTo(cx - half, cy - half)
            lineTo(cx + half, cy - half)
            close()
        }
        canvas.drawPath(topPath, trianglePaint)

        // Right (Blue)
        trianglePaint.color = colorBlue
        val rightPath = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + half, cy - half)
            lineTo(cx + half, cy + half)
            close()
        }
        canvas.drawPath(rightPath, trianglePaint)

        // Bottom (Yellow)
        trianglePaint.color = colorYellow
        val bottomPath = Path().apply {
            moveTo(cx, cy)
            lineTo(cx - half, cy + half)
            lineTo(cx + half, cy + half)
            close()
        }
        canvas.drawPath(bottomPath, trianglePaint)

        // Left (Red)
        trianglePaint.color = colorRed
        val leftPath = Path().apply {
            moveTo(cx, cy)
            lineTo(cx - half, cy - half)
            lineTo(cx - half, cy + half)
            close()
        }
        canvas.drawPath(leftPath, trianglePaint)

        // Center star
        textPaint.textSize = cellSize * 1.2f
        textPaint.color = Color.WHITE
        canvas.drawText("⭐", cx, cy + cellSize * 0.45f, textPaint)
    }

    private fun drawPawns(canvas: Canvas) {
        if (players.isEmpty()) return

        val pawnRadius = cellSize * 0.32f

        players.forEachIndexed { playerIndex, player ->
            val color = when (player.color) {
                PlayerColor.RED -> colorRed
                PlayerColor.BLUE -> colorBlue
                PlayerColor.GREEN -> colorGreen
                PlayerColor.YELLOW -> colorYellow
            }

            player.pawns.forEach { pawn ->
                val center = getPawnCenter(pawn, player) ?: return@forEach
                val isSelectable = selectablePawns.contains(pawn)

                // Shadow
                canvas.drawCircle(center.x + 2f, center.y + 2f, pawnRadius, shadowPaint)

                // Pawn body
                fillPaint.color = color
                canvas.drawCircle(center.x, center.y, pawnRadius, fillPaint)

                // White ring
                borderPaint.color = Color.WHITE
                borderPaint.strokeWidth = 3f
                canvas.drawCircle(center.x, center.y, pawnRadius - 2f, borderPaint)

                // Player number
                textPaint.textSize = pawnRadius * 0.9f
                textPaint.color = Color.WHITE
                canvas.drawText(
                    (pawn.id + 1).toString(),
                    center.x, center.y + pawnRadius * 0.35f, textPaint
                )

                // Selection highlight
                if (isSelectable) {
                    selectedPaint.strokeWidth = 4f
                    canvas.drawCircle(center.x, center.y, pawnRadius + 5f, selectedPaint)
                }
            }
        }
    }

    private fun getPawnCenter(pawn: Pawn, player: Player): PointF? {
        return when (pawn.state) {
            PawnState.HOME -> getHomePosition(pawn, player)
            PawnState.ACTIVE -> getBoardPosition(pawn.boardPosition)
            PawnState.SAFE_ZONE -> getSafeZonePosition(pawn.boardPosition, player.id)
            PawnState.FINISHED -> getCenterFinishPosition(player.id)
        }
    }

    private fun getHomePosition(pawn: Pawn, player: Player): PointF {
        // 4 home spots arranged 2x2 inside each colored quadrant
        val offset = when (pawn.id) {
            0 -> Pair(1.5f, 1.5f)
            1 -> Pair(3.5f, 1.5f)
            2 -> Pair(1.5f, 3.5f)
            else -> Pair(3.5f, 3.5f)
        }
        val (startRow, startCol) = when (player.color) {
            PlayerColor.RED -> Pair(9f, 0f)
            PlayerColor.GREEN -> Pair(0f, 0f)
            PlayerColor.YELLOW -> Pair(0f, 9f)
            PlayerColor.BLUE -> Pair(9f, 9f)
        }
        return PointF(
            boardOffset + (startCol + offset.second) * cellSize,
            (startRow + offset.first) * cellSize
        )
    }

    private fun getBoardPosition(position: Int): PointF? {
        val path = buildSimplePath()
        val grid = path.getOrNull(position % path.size) ?: return null
        return PointF(
            boardOffset + grid.second * cellSize + cellSize / 2f,
            grid.first * cellSize + cellSize / 2f
        )
    }

    private fun getSafeZonePosition(position: Int, playerId: Int): PointF {
        val step = (position - LudoBoard.BOARD_SIZE - playerId * LudoBoard.SAFE_ZONE_SIZE)
        return when (playerId) {
            0 -> PointF(boardOffset + (1 + step) * cellSize + cellSize / 2f, 7 * cellSize + cellSize / 2f) // Red
            1 -> PointF(boardOffset + 7 * cellSize + cellSize / 2f, (1 + step) * cellSize + cellSize / 2f) // Green
            2 -> PointF(boardOffset + (13 - step) * cellSize + cellSize / 2f, 7 * cellSize + cellSize / 2f) // Yellow
            else -> PointF(boardOffset + 7 * cellSize + cellSize / 2f, (13 - step) * cellSize + cellSize / 2f) // Blue
        }
    }

    private fun getCenterFinishPosition(playerId: Int): PointF {
        val cx = boardOffset + 7.5f * cellSize
        val cy = 7.5f * cellSize
        val offset = cellSize * 0.3f
        return when (playerId) {
            0 -> PointF(cx - offset, cy)      // Red center left
            1 -> PointF(cx, cy - offset)      // Green center top
            2 -> PointF(cx, cy + offset)   // Yellow center bottom
            else -> PointF(cx + offset, cy)      // Blue center right
        }
    }

    // ─── Touch ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && selectablePawns.isNotEmpty()) {
            val touchX = event.x
            val touchY = event.y

            players.forEach { player ->
                player.pawns.forEach { pawn ->
                    if (selectablePawns.contains(pawn)) {
                        val center = getPawnCenter(pawn, player) ?: return@forEach
                        val dist = Math.sqrt(
                            ((touchX - center.x) * (touchX - center.x) +
                                    (touchY - center.y) * (touchY - center.y)).toDouble()
                        )
                        if (dist < cellSize * 0.6) {
                            onPawnSelected?.invoke(pawn)
                            return true
                        }
                    }
                }
            }
        }
        return true
    }
}


package com.dartvision.mobile

import kotlin.math.abs

data class ImagePoint(val x: Double, val y: Double)
data class BoardPoint(val xMm: Double, val yMm: Double)

/**
 * Perspective calibration from four image points around the board's outer square.
 * Corner order is top-left, top-right, bottom-right, bottom-left.
 * The resulting coordinates are millimetres, with the board centre at (0, 0).
 */
class BoardCalibration private constructor(private val h: DoubleArray) {
    fun toBoard(imagePoint: ImagePoint): BoardPoint? {
        val denominator = h[6] * imagePoint.x + h[7] * imagePoint.y + 1.0
        if (!denominator.isFinite() || abs(denominator) < 1e-9) return null
        val x = (h[0] * imagePoint.x + h[1] * imagePoint.y + h[2]) / denominator
        val y = (h[3] * imagePoint.x + h[4] * imagePoint.y + h[5]) / denominator
        if (!x.isFinite() || !y.isFinite()) return null
        return BoardPoint(x, y)
    }

    /** Converts a previously localized image impact into a board score. */
    fun scoreImagePoint(imagePoint: ImagePoint, wireToleranceMm: Double = 1.5): DartScore? {
        val point = toBoard(imagePoint) ?: return null
        return DartboardScoring.score(point.xMm, point.yMm, wireToleranceMm)
    }

    companion object {
        fun fromFourCorners(corners: List<ImagePoint>, halfSizeMm: Double = DartboardScoring.OUTER_RADIUS_MM): BoardCalibration {
            require(corners.size == 4)
            require(corners.all { it.x.isFinite() && it.y.isFinite() })
            require(halfSizeMm > 0.0 && halfSizeMm.isFinite())
            val targets = listOf(
                BoardPoint(-halfSizeMm, -halfSizeMm),
                BoardPoint(halfSizeMm, -halfSizeMm),
                BoardPoint(halfSizeMm, halfSizeMm),
                BoardPoint(-halfSizeMm, halfSizeMm)
            )
            val matrix = Array(8) { DoubleArray(9) }
            for (i in 0..3) {
                val x = corners[i].x
                val y = corners[i].y
                val u = targets[i].xMm
                val v = targets[i].yMm
                val r = i * 2
                matrix[r][0] = x; matrix[r][1] = y; matrix[r][2] = 1.0
                matrix[r][6] = -u * x; matrix[r][7] = -u * y; matrix[r][8] = u
                matrix[r + 1][3] = x; matrix[r + 1][4] = y; matrix[r + 1][5] = 1.0
                matrix[r + 1][6] = -v * x; matrix[r + 1][7] = -v * y; matrix[r + 1][8] = v
            }
            for (column in 0 until 8) {
                var pivot = column
                for (row in column + 1 until 8) {
                    if (abs(matrix[row][column]) > abs(matrix[pivot][column])) pivot = row
                }
                require(abs(matrix[pivot][column]) > 1e-10) { "Les quatre points sont dégénérés ou presque alignés." }
                val tmp = matrix[column]
                matrix[column] = matrix[pivot]
                matrix[pivot] = tmp
                val divisor = matrix[column][column]
                for (j in column until 9) matrix[column][j] /= divisor
                for (row in 0 until 8) {
                    if (row == column) continue
                    val factor = matrix[row][column]
                    for (j in column until 9) matrix[row][j] -= factor * matrix[column][j]
                }
            }
            return BoardCalibration(DoubleArray(8) { matrix[it][8] })
        }
    }
}

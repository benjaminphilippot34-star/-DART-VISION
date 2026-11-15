package com.dartvision.mobile

import kotlin.math.min

/** Maps taps in a FIT_CENTER PreviewView to the unrotated ImageAnalysis buffer. */
object PreviewCoordinateMapper {
    fun viewToImage(
        viewX: Double,
        viewY: Double,
        viewWidth: Int,
        viewHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ): ImagePoint? {
        require(viewWidth > 0 && viewHeight > 0 && imageWidth > 0 && imageHeight > 0)
        require(rotationDegrees in setOf(0, 90, 180, 270))
        val rotatedWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageHeight else imageWidth
        val rotatedHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageWidth else imageHeight
        val scale = min(viewWidth.toDouble() / rotatedWidth, viewHeight.toDouble() / rotatedHeight)
        val shownWidth = rotatedWidth * scale
        val shownHeight = rotatedHeight * scale
        val left = (viewWidth - shownWidth) / 2.0
        val top = (viewHeight - shownHeight) / 2.0
        val rx = (viewX - left) / shownWidth
        val ry = (viewY - top) / shownHeight
        if (rx !in 0.0..1.0 || ry !in 0.0..1.0) return null
        val (x, y) = when (rotationDegrees) {
            0 -> rx to ry
            90 -> ry to (1.0 - rx)
            180 -> (1.0 - rx) to (1.0 - ry)
            else -> (1.0 - ry) to rx
        }
        return ImagePoint(x * imageWidth, y * imageHeight)
    }
}

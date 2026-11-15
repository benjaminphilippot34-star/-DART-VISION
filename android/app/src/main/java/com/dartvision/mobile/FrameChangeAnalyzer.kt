package com.dartvision.mobile

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

data class ChangedRegion(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val pixelCount: Int,
    val centroidX: Double,
    val centroidY: Double
)

/**
 * Compare des empreintes de luminance et filtre les changements fugitifs.
 *
 * Une zone persistante est seulement un changement d'image stable : elle peut
 * toujours provenir d'une fléchette, d'une ombre ou d'un objet. Ce n'est pas une
 * détection de pointe et aucun score ne doit être validé automatiquement.
 */
class FrameChangeAnalyzer(
    val sampleWidth: Int = 160,
    val sampleHeight: Int = 160,
    private val pixelThreshold: Int = 28,
    private val minRegionPixels: Int = 3,
    private val requiredStableFrames: Int = 3
) : ImageAnalysis.Analyzer {

    private val latestFrame = AtomicReference<ByteArray?>(null)

    @Volatile private var referenceFrame: ByteArray? = null
    @Volatile private var persistenceCounts = ByteArray(sampleWidth * sampleHeight)

    @Volatile var changedPercent: Double? = null
        private set
    @Volatile var changedRegions: List<ChangedRegion> = emptyList()
        private set
    @Volatile var framesReceived: Long = 0
        private set
    @Volatile var imageWidth: Int = 0
        private set
    @Volatile var imageHeight: Int = 0
        private set
    @Volatile var rotationDegrees: Int = 0
        private set

    init {
        require(sampleWidth > 0 && sampleHeight > 0)
        require(pixelThreshold in 1..255)
        require(minRegionPixels >= 1)
        require(requiredStableFrames in 1..255)
    }

    @Synchronized
    override fun analyze(image: ImageProxy) {
        try {
            imageWidth = image.width
            imageHeight = image.height
            rotationDegrees = image.imageInfo.rotationDegrees
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val sampled = sampleLuma(
                data, image.width, image.height,
                plane.rowStride, plane.pixelStride,
                sampleWidth, sampleHeight
            )
            latestFrame.set(sampled)
            framesReceived++

            val reference = referenceFrame ?: return
            val stableMask = updatePersistenceMask(
                reference, sampled, persistenceCounts,
                pixelThreshold, requiredStableFrames
            )
            changedPercent = changedPixelPercent(
                ByteArray(stableMask.size), stableMask, 1
            )
            changedRegions = detectChangedRegions(
                ByteArray(stableMask.size), stableMask,
                sampleWidth, sampleHeight, 1, minRegionPixels
            )
        } finally {
            image.close()
        }
    }

    @Synchronized
    fun captureReference(): Boolean {
        val current = latestFrame.get() ?: return false
        referenceFrame = current.copyOf()
        persistenceCounts = ByteArray(sampleWidth * sampleHeight)
        changedPercent = 0.0
        changedRegions = emptyList()
        return true
    }

    @Synchronized
    fun clearReference() {
        referenceFrame = null
        persistenceCounts = ByteArray(sampleWidth * sampleHeight)
        changedPercent = null
        changedRegions = emptyList()
    }

    fun hasReference(): Boolean = referenceFrame != null

    companion object {
        internal fun sampleLuma(
            data: ByteArray,
            imageWidth: Int,
            imageHeight: Int,
            rowStride: Int,
            pixelStride: Int,
            outWidth: Int,
            outHeight: Int
        ): ByteArray {
            require(imageWidth > 0 && imageHeight > 0)
            require(rowStride > 0 && pixelStride > 0)
            require(outWidth > 0 && outHeight > 0)
            val output = ByteArray(outWidth * outHeight)
            for (y in 0 until outHeight) {
                for (x in 0 until outWidth) {
                    val sourceY = (y.toLong() * imageHeight / outHeight)
                        .toInt().coerceAtMost(imageHeight - 1)
                    val sourceX = (x.toLong() * imageWidth / outWidth)
                        .toInt().coerceAtMost(imageWidth - 1)
                    val index = sourceY * rowStride + sourceX * pixelStride
                    output[y * outWidth + x] = if (index in data.indices) data[index] else 0
                }
            }
            return output
        }

        /**
         * A pixel is reported only after it differs from the reference for
         * requiredFrames consecutive analyzed frames. Transient motion resets it.
         */
        internal fun updatePersistenceMask(
            reference: ByteArray,
            current: ByteArray,
            persistence: ByteArray,
            threshold: Int,
            requiredFrames: Int
        ): ByteArray {
            require(reference.size == current.size && current.size == persistence.size)
            require(threshold in 1..255 && requiredFrames in 1..255)

            // Compense les variations globales d'exposition/luminosité avant
            // de rechercher les changements locaux (ex. une nouvelle fléchette).
            val offsets = IntArray(reference.size) { i ->
                (current[i].toInt() and 255) - (reference[i].toInt() and 255)
            }
            val globalOffset = if (offsets.isEmpty()) 0 else {
                offsets.sort()
                offsets[offsets.size / 2]
            }

            val mask = ByteArray(reference.size)
            for (i in reference.indices) {
                val rawDifference =
                    (current[i].toInt() and 255) - (reference[i].toInt() and 255)
                val difference = abs(rawDifference - globalOffset)
                persistence[i] = if (difference >= threshold) {
                    ((persistence[i].toInt() and 255) + 1)
                        .coerceAtMost(requiredFrames).toByte()
                } else {
                    0
                }
                if ((persistence[i].toInt() and 255) >= requiredFrames) {
                    mask[i] = 1
                }
            }
            return mask
        }

        internal fun changedPixelPercent(
            reference: ByteArray,
            current: ByteArray,
            threshold: Int
        ): Double {
            require(reference.size == current.size)
            if (reference.isEmpty()) return 0.0
            var changed = 0
            for (i in reference.indices) {
                if (abs(
                    (reference[i].toInt() and 255) -
                        (current[i].toInt() and 255)
                ) >= threshold) changed++
            }
            return changed * 100.0 / reference.size
        }

        internal fun detectChangedRegions(
            reference: ByteArray,
            current: ByteArray,
            width: Int,
            height: Int,
            threshold: Int,
            minPixels: Int = 3
        ): List<ChangedRegion> {
            require(width > 0 && height > 0)
            require(reference.size == width * height && current.size == width * height)
            require(threshold in 1..255 && minPixels >= 1)

            val seen = BooleanArray(reference.size)
            val queue = IntArray(reference.size)
            val regions = mutableListOf<ChangedRegion>()

            for (start in reference.indices) {
                if (seen[start] ||
                    abs((reference[start].toInt() and 255) -
                        (current[start].toInt() and 255)) < threshold
                ) continue

                var head = 0
                var tail = 0
                queue[tail++] = start
                seen[start] = true
                var minX = width
                var minY = height
                var maxX = 0
                var maxY = 0
                var sumX = 0L
                var sumY = 0L

                while (head < tail) {
                    val index = queue[head++]
                    val x = index % width
                    val y = index / width
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                    sumX += x
                    sumY += y

                    val neighbors = intArrayOf(
                        if (x > 0) index - 1 else -1,
                        if (x < width - 1) index + 1 else -1,
                        if (y > 0) index - width else -1,
                        if (y < height - 1) index + width else -1
                    )
                    for (neighbor in neighbors) {
                        if (neighbor >= 0 && !seen[neighbor] &&
                            abs((reference[neighbor].toInt() and 255) -
                                (current[neighbor].toInt() and 255)) >= threshold
                        ) {
                            seen[neighbor] = true
                            queue[tail++] = neighbor
                        }
                    }
                }

                if (tail >= minPixels) {
                    regions += ChangedRegion(
                        minX, minY, maxX, maxY, tail,
                        sumX.toDouble() / tail,
                        sumY.toDouble() / tail
                    )
                }
            }
            return regions.sortedByDescending { it.pixelCount }
        }
    }
}

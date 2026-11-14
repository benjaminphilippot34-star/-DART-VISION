package com.dartvision.mobile

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Acquisition d'une petite empreinte de luminance de chaque image caméra.
 *
 * Première brique de comparaison avant/après : elle ne localise pas une fléchette
 * et ne doit jamais être utilisée seule pour attribuer un score.
 */
class FrameChangeAnalyzer(
    private val sampleWidth: Int = 160,
    private val sampleHeight: Int = 160,
    private val pixelThreshold: Int = 28
) : ImageAnalysis.Analyzer {

    private val latestFrame = AtomicReference<ByteArray?>(null)
    @Volatile private var referenceFrame: ByteArray? = null
    @Volatile var changedPercent: Double? = null
        private set

    @Volatile var framesReceived: Long = 0
        private set

    init {
        require(sampleWidth > 0 && sampleHeight > 0)
        require(pixelThreshold in 1..255)
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val sample = sampleLuma(
                data = data,
                imageWidth = image.width,
                imageHeight = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                outWidth = sampleWidth,
                outHeight = sampleHeight
            )
            latestFrame.set(sample)
            framesReceived++
            referenceFrame?.let { reference ->
                changedPercent = changedPixelPercent(reference, sample, pixelThreshold)
            }
        } finally {
            image.close()
        }
    }

    /** Enregistre l'image courante comme référence avant le lancer. */
    fun captureReference(): Boolean {
        val current = latestFrame.get() ?: return false
        referenceFrame = current.copyOf()
        changedPercent = 0.0
        return true
    }

    fun clearReference() {
        referenceFrame = null
        changedPercent = null
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
            require(imageWidth > 0 && imageHeight > 0 && rowStride > 0 && pixelStride > 0)
            val output = ByteArray(outWidth * outHeight)
            for (y in 0 until outHeight) {
                val sourceY = (y.toLong() * imageHeight / outHeight).toInt().coerceAtMost(imageHeight - 1)
                for (x in 0 until outWidth) {
                    val sourceX = (x.toLong() * imageWidth / outWidth).toInt().coerceAtMost(imageWidth - 1)
                    val index = sourceY * rowStride + sourceX * pixelStride
                    output[y * outWidth + x] = if (index in data.indices) data[index] else 0
                }
            }
            return output
        }

        internal fun changedPixelPercent(reference: ByteArray, current: ByteArray, threshold: Int): Double {
            require(reference.size == current.size) { "Les images doivent avoir la même taille." }
            if (reference.isEmpty()) return 0.0
            var changed = 0
            for (i in reference.indices) {
                if (abs((reference[i].toInt() and 0xFF) - (current[i].toInt() and 0xFF)) >= threshold) {
                    changed++
                }
            }
            return changed * 100.0 / reference.size
        }
    }
}

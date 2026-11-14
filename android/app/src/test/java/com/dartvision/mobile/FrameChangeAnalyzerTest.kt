package com.dartvision.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameChangeAnalyzerTest {
    @Test fun identicalFramesHaveNoChangedPixels() {
        val frame = ByteArray(100) { 120.toByte() }
        assertEquals(0.0, FrameChangeAnalyzer.changedPixelPercent(frame, frame.copyOf(), 28), 0.0001)
    }

    @Test fun changedPixelsAreExpressedAsPercentage() {
        val before = ByteArray(100) { 100.toByte() }
        val after = before.copyOf()
        for (i in 0 until 25) after[i] = 180.toByte()
        assertEquals(25.0, FrameChangeAnalyzer.changedPixelPercent(before, after, 28), 0.0001)
    }

    @Test fun smallBrightnessNoiseIsIgnored() {
        val before = byteArrayOf(100, 100, 100)
        val after = byteArrayOf(110, 115, 127)
        assertEquals(0.0, FrameChangeAnalyzer.changedPixelPercent(before, after, 28), 0.0001)
    }

    @Test fun pixelStrideAndRowStrideAreRespected() {
        // Deux pixels utiles par ligne, séparés par un octet de chrominance.
        val source = byteArrayOf(10, 0, 20, 0, 30, 0, 40, 0)
        val result = FrameChangeAnalyzer.sampleLuma(
            source, imageWidth = 2, imageHeight = 2,
            rowStride = 4, pixelStride = 2, outWidth = 2, outHeight = 2
        )
        assertEquals(listOf(10, 20, 30, 40), result.map { it.toInt() and 0xFF })
    }
}

package com.dartvision.mobile

import org.junit.Assert.*
import org.junit.Test

class PreviewCoordinateMapperTest {
    @Test fun fitCenterMapsCentreToImageCentre() {
        val point = PreviewCoordinateMapper.viewToImage(
            500.0, 500.0, 1000, 1000, 1600, 900, 0
        )
        assertNotNull(point)
        assertEquals(800.0, point!!.x, 0.001)
        assertEquals(450.0, point.y, 0.001)
    }

    @Test fun fitCenterRejectsTapInLetterbox() {
        assertNull(PreviewCoordinateMapper.viewToImage(
            500.0, 50.0, 1000, 1000, 1600, 900, 0
        ))
    }

    @Test fun mapsRotatedPreviewCoordinatesBackToImageBuffer() {
        val point = PreviewCoordinateMapper.viewToImage(
            250.0, 500.0, 1000, 1000, 1600, 900, 90
        )
        assertNotNull(point)
        assertEquals(800.0, point!!.x, 0.001)
        assertEquals(850.0, point.y, 0.001)
    }
}

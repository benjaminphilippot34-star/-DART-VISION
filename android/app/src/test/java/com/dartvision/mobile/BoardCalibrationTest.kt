package com.dartvision.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BoardCalibrationTest {
    @Test fun mapsFourCornersAndCentreToBoardCoordinates() {
        val calibration = BoardCalibration.fromFourCorners(
            listOf(ImagePoint(100.0, 80.0), ImagePoint(500.0, 100.0),
                ImagePoint(480.0, 500.0), ImagePoint(80.0, 480.0))
        )
        val topLeft = calibration.toBoard(ImagePoint(100.0, 80.0))
        val centre = calibration.toBoard(ImagePoint(290.0, 290.0))
        assertNotNull(topLeft)
        assertNotNull(centre)
        assertEquals(-170.0, topLeft!!.xMm, 1e-6)
        assertEquals(-170.0, topLeft.yMm, 1e-6)
        assertEquals(0.0, centre!!.xMm, 1.0)
        assertEquals(0.0, centre.yMm, 1.0)
    }

    @Test fun rejectsDegenerateCornerGeometry() {
        try {
            BoardCalibration.fromFourCorners(
                listOf(ImagePoint(0.0, 0.0), ImagePoint(1.0, 0.0),
                    ImagePoint(2.0, 0.0), ImagePoint(3.0, 0.0))
            )
            throw AssertionError("Expected invalid calibration to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}

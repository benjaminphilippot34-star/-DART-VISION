package com.dartvision.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DartboardScoringTest {
    @Test fun topSectorIsTwenty() {
        val result = DartboardScoring.score(0.0, -130.0)
        assertEquals(20, result.number)
        assertEquals(20, result.points)
        assertEquals("Simple", result.zone)
    }

    @Test fun rightSideIsSix() {
        val result = DartboardScoring.score(130.0, 0.0)
        assertEquals(6, result.number)
        assertEquals(6, result.points)
    }

    @Test fun tripleTwentyIsSixty() {
        val result = DartboardScoring.score(0.0, -103.0)
        assertEquals(20, result.number)
        assertEquals(3, result.multiplier)
        assertEquals(60, result.points)
        assertEquals("Triple", result.zone)
    }

    @Test fun doubleTwentyIsForty() {
        val result = DartboardScoring.score(0.0, -165.0)
        assertEquals(40, result.points)
        assertEquals("Double", result.zone)
    }

    @Test fun innerBullIsFifty() {
        val result = DartboardScoring.score(0.0, 0.0)
        assertEquals(50, result.points)
        assertEquals("Bull", result.zone)
    }

    @Test fun outerBullIsTwentyFive() {
        val result = DartboardScoring.score(0.0, -10.0)
        assertEquals(25, result.points)
        assertEquals("Simple extérieur", result.zone)
    }

    @Test fun outsideBoardIsZero() {
        val result = DartboardScoring.score(0.0, -171.0)
        assertEquals(0, result.points)
        assertEquals("Hors cible", result.zone)
    }

    @Test fun pointNearSectorWireIsFlagged() {
        val result = DartboardScoring.score(130.0 * kotlin.math.sin(Math.toRadians(9.0)),
            -130.0 * kotlin.math.cos(Math.toRadians(9.0)))
        assertTrue(result.nearWire)
    }

    @Test fun pointAwayFromWiresIsNotFlagged() {
        val result = DartboardScoring.score(0.0, -130.0)
        assertFalse(result.nearWire)
    }
}

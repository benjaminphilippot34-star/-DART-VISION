package com.dartvision.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DartHitParserTest {
    @Test fun parsesCommonNotation() {
        assertEquals(DartHit(20, 3), DartHitParser.parse("T20"))
        assertEquals(DartHit(16, 2), DartHitParser.parse("D16"))
        assertEquals(DartHit(20, 1), DartHitParser.parse("S20"))
        assertEquals(DartHit(25, 2), DartHitParser.parse("D25"))
        assertEquals(DartHit.MISS, DartHitParser.parse("MISS"))
        assertEquals(DartHit.MISS, DartHitParser.parse("0"))
    }

    @Test fun rejectsImpossibleAndAmbiguousNotation() {
        listOf("", "T25", "D21", "S0", "21", "X20", "D").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { DartHitParser.parse(value) }
        }
    }
}

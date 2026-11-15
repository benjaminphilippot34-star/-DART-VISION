package com.dartvision.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DartCounterModesTest {
    @Test fun catalogContainsTheDocumentedDartCounterModes() {
        val catalog = DartCounterModeCatalog.default
        assertEquals(13, catalog.modes.size)
        assertNotNull(catalog.byId("x01"))
        assertNotNull(catalog.byId("cricket"))
        assertNotNull(catalog.byId("121_checkout"))
        assertNotNull(catalog.byId("shanghai"))
        assertNotNull(catalog.byId("killer"))
    }

    @Test fun everyCatalogModeIsAvailableOffline() {
        assertTrue(DartCounterModeCatalog.default.modes.all { it.offlineAvailable })
    }

    @Test fun shanghaiSettingsValidateTheRoundRange() {
        val settings = ShanghaiSettings(firstNumber = 3, lastNumber = 7, requireTripleEachTurn = true)
        assertEquals(3, settings.firstNumber)
        assertEquals(7, settings.lastNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun shanghaiRejectsAnInvalidRange() {
        ShanghaiSettings(firstNumber = 8, lastNumber = 7)
    }

    @Test fun x01DefaultsTo301WithDoubleOut() {
        val settings = X01Settings()
        assertEquals(301, settings.startingScore)
        assertEquals(X01OutRule.DOUBLE_OUT, settings.outRule)
    }

    @Test fun checkout121HasNineDartsByDefault() {
        assertEquals(9, Checkout121Settings().dartsPerAttempt)
    }
}

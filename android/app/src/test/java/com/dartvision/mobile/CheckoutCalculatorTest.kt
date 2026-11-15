package com.dartvision.mobile

import org.junit.Assert.*
import org.junit.Test

class CheckoutCalculatorTest {
    @Test fun findsSingleDartDoubleFinish() {
        val routes = CheckoutCalculator.routes(40)
        assertTrue(routes.any { it.notation == "D20" })
    }

    @Test fun findsMaximumCheckoutInThreeDarts() {
        val routes = CheckoutCalculator.routes(170)
        assertTrue(routes.any { it.notation == "T20  •  T20  •  BULL" })
    }

    @Test fun rejectsBogeyNumberAndScoresAboveCheckoutRange() {
        assertTrue(CheckoutCalculator.routes(169).isEmpty())
        assertTrue(CheckoutCalculator.routes(171).isEmpty())
        assertTrue(CheckoutCalculator.routes(1).isEmpty())
    }

    @Test fun masterOutCanFinishOnTriple() {
        val doubleOut = CheckoutCalculator.routes(60)
        val masterOut = CheckoutCalculator.routes(60, masterOut = true)
        assertFalse(doubleOut.any { it.notation == "T20" })
        assertTrue(masterOut.any { it.notation == "T20" })
    }

    @Test fun everyDoubleOutRouteEndsOnDoubleAndTotalsExactly() {
        for (score in 2..170) {
            CheckoutCalculator.routes(score, 20).forEach { route ->
                assertTrue(route.darts.size in 1..3)
                assertTrue(route.darts.last().isDouble)
                assertEquals(score, route.points)
            }
        }
    }
}

package com.dartvision.mobile

import org.junit.Assert.*
import org.junit.Test

class OfflineGameEnginesTest {
    @Test fun x01BustRestoresTheScoreAndPassesTheTurn() {
        val game = X01GameEngine(X01Settings(101, outRule = X01OutRule.DOUBLE_OUT), listOf("A", "B"))
        val result = game.throwTurn(listOf(DartHit(20, 3), DartHit(20, 3), DartHit(20, 3)))
        assertTrue(result.bust)
        assertEquals(101, game.state.players[0].score)
        assertEquals(1, game.state.activePlayer)
    }

    @Test fun x01DoubleOutWinsOnDouble() {
        val game = X01GameEngine(X01Settings(40, outRule = X01OutRule.DOUBLE_OUT), listOf("A", "B"))
        val result = game.throwTurn(listOf(DartHit(20, 2)))
        assertTrue(result.legWon)
        assertEquals(0, game.state.players[0].score)
        assertTrue(game.state.finished)
    }

    @Test fun x01DoubleInDoesNotCountEarlierDarts() {
        val game = X01GameEngine(X01Settings(301, inRule = X01InRule.DOUBLE_IN), listOf("A", "B"))
        game.throwTurn(listOf(DartHit(20, 1), DartHit(20, 2)))
        assertEquals(261, game.state.players[0].score)
        assertTrue(game.state.players[0].hasEntered)
    }

    @Test fun x01UndoRestoresPreviousTurn() {
        val game = X01GameEngine(X01Settings(301), listOf("A", "B"))
        game.throwTurn(listOf(DartHit(20, 1)))
        assertTrue(game.undo())
        assertEquals(301, game.state.players[0].score)
        assertEquals(0, game.state.activePlayer)
    }

    @Test fun cricketClosesTargetsAndAddsPoints() {
        val game = CricketGameEngine(listOf("A", "B"), includeBull = false)
        game.playTurn(listOf(DartHit(20, 3)))
        assertEquals(3, game.state.players[0].marks[20])
        game.playTurn(listOf(DartHit(20, 3)))
        game.playTurn(listOf(DartHit(20, 3)))
        assertEquals(20, game.state.players[0].points)
    }

    @Test fun shanghaiWinsWithSingleDoubleTripleOnTarget() {
        val game = ShanghaiGameEngine(listOf("A", "B"), ShanghaiSettings(1, 7))
        game.playTurn(0, listOf(DartHit(1, 1), DartHit(1, 2), DartHit(1, 3)))
        assertEquals(0, game.state.winner)
    }

    @Test fun aroundClockAdvancesOnlyOnTarget() {
        val game = AroundClockEngine("A")
        game.playTurn(listOf(DartHit(2, 1), DartHit(1, 1)))
        assertEquals(2, game.state.target)
    }

    @Test fun bobs27PenalizesMissedDouble() {
        val game = Bobs27Engine()
        game.playTurn(listOf(DartHit(1, 1), DartHit.MISS, DartHit.MISS))
        assertEquals(21, game.state.score)
        assertEquals(2, game.state.doubleNumber)
    }

    @Test fun halveItHalvesScoreWhenTargetIsMissed() {
        val game = HalveItEngine(listOf(20, 19))
        game.playTurn(listOf(DartHit(20, 1)))
        game.playTurn(listOf(DartHit.MISS, DartHit.MISS, DartHit.MISS))
        assertEquals(10, game.state.score)
        assertTrue(game.state.complete)
    }
}

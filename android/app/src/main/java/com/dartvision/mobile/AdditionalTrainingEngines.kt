package com.dartvision.mobile

import kotlin.random.Random

data class Checkout121State(
    val targetScore: Int = 121,
    val level: Int = 1,
    val safehouseScore: Int = 121,
    val completedAttempts: Int = 0,
    val failedAttempts: Int = 0,
    val finished: Boolean = false
)

class Checkout121Engine(private val settings: Checkout121Settings = Checkout121Settings()) {
    var state = Checkout121State(targetScore = settings.startingScore, safehouseScore = settings.startingScore)
        private set

    fun attempt(hits: List<DartHit>, checkoutDoubleRequired: Boolean = true): Boolean {
        require(hits.size in 1..settings.dartsPerAttempt)
        check(!state.finished)
        var remaining = state.targetScore
        var checkout = false
        for (hit in hits) {
            val next = remaining - hit.points
            if (next < 0 || next == 1) break
            if (next == 0) {
                if (!checkoutDoubleRequired || hit.isDouble) checkout = true
                break
            }
            remaining = next
        }
        state = if (checkout) {
            val newLevel = state.level + 1
            val newTarget = state.targetScore + settings.increaseAfterSuccess
            val safehouse = if (newLevel % settings.safehouseEvery == 0) newTarget else state.safehouseScore
            state.copy(targetScore = newTarget, level = newLevel, safehouseScore = safehouse,
                completedAttempts = state.completedAttempts + 1,
                finished = settings.rounds != null && state.completedAttempts + 1 >= settings.rounds)
        } else {
            state.copy(targetScore = state.safehouseScore, failedAttempts = state.failedAttempts + 1)
        }
        return checkout
    }
}

data class KillerPlayerState(val name: String, val number: Int, val marks: Int = 0, val lives: Int = 3, val isKiller: Boolean = false)
data class KillerGameState(val players: List<KillerPlayerState>, val activePlayer: Int = 0, val winner: Int? = null)

class KillerGameEngine(playerNames: List<String>, assignedNumbers: List<Int>) {
    init {
        require(playerNames.size >= 2)
        require(assignedNumbers.size == playerNames.size)
        require(assignedNumbers.all { it in 1..20 } && assignedNumbers.distinct().size == assignedNumbers.size)
    }
    var state = KillerGameState(playerNames.indices.map { KillerPlayerState(playerNames[it], assignedNumbers[it]) })
        private set

    fun playTurn(hits: List<DartHit>) {
        require(hits.size in 1..3)
        check(state.winner == null)
        val players = state.players.toMutableList()
        val active = state.activePlayer
        for (hit in hits) {
            val number = hit.number ?: continue
            if (hit.multiplier == 0) continue
            val own = players[active]
            if (!own.isKiller) {
                if (number == own.number) {
                    val marks = own.marks + hit.multiplier
                    players[active] = own.copy(marks = marks, isKiller = marks >= 3)
                }
            } else {
                val targetIndex = players.indices.firstOrNull { it != active && players[it].number == number } ?: continue
                val target = players[targetIndex]
                if (target.lives > 0) players[targetIndex] = target.copy(lives = (target.lives - hit.multiplier).coerceAtLeast(0))
            }
        }
        val survivors = players.indices.filter { it != active && players[it].lives > 0 }
        val winner = if (players[active].isKiller && survivors.isEmpty()) active else null
        state = KillerGameState(players, (active + 1) % players.size, winner)
    }
}

data class TargetTrainingState(val target: Int, val hits: Int = 0, val attempts: Int = 0, val score: Int = 0, val complete: Boolean = false)

class DoublesTrainingEngine(private val firstTarget: Int = 1, private val lastTarget: Int = 20) {
    init { require(firstTarget in 1..20 && lastTarget in firstTarget..20) }
    var state = TargetTrainingState(firstTarget)
        private set
    fun playTurn(hits: List<DartHit>) {
        require(hits.size in 1..3)
        check(!state.complete)
        val hitsOnTarget = hits.count { it.number == state.target && it.isDouble }
        val attempts = state.attempts + hits.size
        val hitCount = state.hits + hitsOnTarget
        val next = if (hitsOnTarget > 0) state.target + 1 else state.target
        state = TargetTrainingState(next.coerceAtMost(lastTarget + 1), hitCount, attempts,
            state.score + hitsOnTarget, next > lastTarget)
    }
}

class SinglesTrainingEngine(private val firstTarget: Int = 1, private val lastTarget: Int = 20) {
    init { require(firstTarget in 1..20 && lastTarget in firstTarget..20) }
    var state = TargetTrainingState(firstTarget)
        private set
    fun playTurn(hits: List<DartHit>) {
        require(hits.size in 1..3)
        check(!state.complete)
        val hitsOnTarget = hits.count { it.number == state.target && it.multiplier == 1 }
        val next = if (hitsOnTarget > 0) state.target + 1 else state.target
        state = TargetTrainingState(next.coerceAtMost(lastTarget + 1),
            state.hits + hitsOnTarget, state.attempts + hits.size,
            state.score + hitsOnTarget, next > lastTarget)
    }
}

data class ScoreTrainingState(val totalPoints: Int = 0, val darts: Int = 0, val turns: Int = 0)
class ScoreTrainingEngine {
    var state = ScoreTrainingState()
        private set
    fun playTurn(hits: List<DartHit>) {
        require(hits.size in 1..3)
        state = ScoreTrainingState(state.totalPoints + hits.sumOf { it.points },
            state.darts + hits.size, state.turns + 1)
    }
}

data class RandomCheckoutState(val target: Int, val completed: Int = 0, val failed: Int = 0, val finished: Boolean = false)
class RandomCheckoutEngine(
    private val targets: List<Int> = listOf(41, 61, 81, 101, 121, 81, 61),
    private val random: Random = Random.Default
) {
    init { require(targets.isNotEmpty() && targets.all { it in 2..170 }) }
    private var index = 0
    var state = RandomCheckoutState(targets.first())
        private set
    fun attempt(hits: List<DartHit>) {
        require(hits.size in 1..3)
        check(!state.finished)
        var remaining = state.target
        var won = false
        for (hit in hits) {
            val next = remaining - hit.points
            if (next < 0 || next == 1) break
            if (next == 0) { won = hit.isDouble; break }
            remaining = next
        }
        if (won) index++
        state = if (index >= targets.size) state.copy(completed = state.completed + 1, finished = true)
            else state.copy(target = targets[index], completed = state.completed + if (won) 1 else 0,
                failed = state.failed + if (won) 0 else 1)
    }
}

package com.dartvision.mobile

/** Impact reconnu ou saisi manuellement. multiplier: 0 miss, 1 simple, 2 double, 3 triple. */
data class DartHit(val number: Int?, val multiplier: Int) {
    init {
        require(multiplier in 0..3)
        require(number == null || number in 1..20 || number == 25)
        require(number != null || multiplier == 0)
        require(number != 25 || multiplier <= 2)
    }
    val points: Int get() = when {
        number == null -> 0
        number == 25 -> if (multiplier == 2) 50 else 25
        else -> number * multiplier
    }
    val isDouble: Boolean get() = number != null && multiplier == 2
    val isTriple: Boolean get() = number != null && multiplier == 3
    companion object { val MISS = DartHit(null, 0) }
}

data class X01PlayerState(
    val name: String,
    val score: Int,
    val hasEntered: Boolean = false,
    val legsWon: Int = 0,
    val setsWon: Int = 0
)

data class X01TurnResult(
    val playerIndex: Int,
    val hits: List<DartHit>,
    val scoreBefore: Int,
    val scoreAfter: Int,
    val bust: Boolean,
    val legWon: Boolean
)

data class X01GameState(
    val settings: X01Settings,
    val players: List<X01PlayerState>,
    val activePlayer: Int = 0,
    val turnNumber: Int = 1,
    val dartsThrown: Int = 0,
    val finished: Boolean = false,
    val legFinished: Boolean = false,
    val history: List<X01TurnResult> = emptyList()
)

class X01GameEngine(
    private val settings: X01Settings,
    playerNames: List<String>
) {
    init { require(playerNames.isNotEmpty()) }
    var state: X01GameState = X01GameState(
        settings,
        playerNames.map { X01PlayerState(it, settings.startingScore) }
    )
        private set

    fun throwTurn(hits: List<DartHit>): X01TurnResult {
        require(hits.size in 1..3) { "Un tour doit contenir entre une et trois fléchettes." }
        check(!state.finished && !state.legFinished) { "La manche est terminée." }
        val players = state.players.toMutableList()
        val index = state.activePlayer
        val player = players[index]
        val before = player.score
        var remaining = before
        var entered = player.hasEntered
        var bust = false
        var won = false

        for (hit in hits) {
            if (!entered) {
                val qualifies = when (settings.inRule) {
                    X01InRule.STRAIGHT_IN -> true
                    X01InRule.DOUBLE_IN -> hit.isDouble
                    X01InRule.MASTER_IN -> hit.isDouble || hit.isTriple
                }
                if (qualifies) entered = true else continue
            }
            val next = remaining - hit.points
            if (next < 0 || (settings.outRule == X01OutRule.DOUBLE_OUT && next == 1) ||
                (settings.outRule == X01OutRule.MASTER_OUT && next in 1..1)) {
                bust = true
                break
            }
            if (next == 0) {
                val validOut = when (settings.outRule) {
                    X01OutRule.STRAIGHT_OUT -> true
                    X01OutRule.DOUBLE_OUT -> hit.isDouble
                    X01OutRule.MASTER_OUT -> hit.isDouble || hit.isTriple
                }
                if (!validOut) { bust = true; break }
                remaining = 0
                won = true
                break
            }
            remaining = next
        }

        if (bust) { remaining = before; entered = player.hasEntered }
        players[index] = player.copy(score = remaining, hasEntered = entered)
        val result = X01TurnResult(index, hits, before, remaining, bust, won)
        var matchWon = false
        if (won) {
            val legs = player.legsWon + 1
            val setWon = legs >= settings.legsToWin
            val sets = player.setsWon + if (setWon) 1 else 0
            matchWon = sets >= settings.setsToWin
            players[index] = players[index].copy(
                legsWon = if (setWon) 0 else legs,
                setsWon = sets
            )
            if (setWon) {
                for (i in players.indices) if (i != index) players[i] = players[i].copy(legsWon = 0)
            }
        }
        val nextPlayer = (index + 1) % players.size
        state = state.copy(
            players = players,
            activePlayer = if (won) index else nextPlayer,
            turnNumber = if (won) state.turnNumber else state.turnNumber + 1,
            dartsThrown = state.dartsThrown + hits.size,
            finished = matchWon,
            legFinished = won,
            history = state.history + result
        )
        return result
    }

    fun undo(): Boolean {
        if (state.history.isEmpty()) return false
        val previous = state.history.dropLast(1)
        val rebuilt = X01GameEngine(settings, state.players.map { it.name })
        previous.forEach { rebuilt.throwTurn(it.hits) }
        state = rebuilt.state
        return true
    }

    /** Restore a validated local snapshot, used for offline persistence. */
    fun restore(snapshot: X01GameState) {
        require(snapshot.settings == settings)
        require(snapshot.players.size == state.players.size)
        require(snapshot.players.map { it.name } == state.players.map { it.name })
        require(snapshot.activePlayer in snapshot.players.indices)
        require(snapshot.history.all { it.playerIndex in snapshot.players.indices && it.hits.size in 1..3 })
        state = snapshot
    }

    fun newLeg() {
        check(state.legFinished && !state.finished) { "Aucune nouvelle manche à démarrer." }
        val players = state.players.map { it.copy(score = settings.startingScore, hasEntered = false) }
        val starter = (state.activePlayer + 1) % players.size
        state = state.copy(players = players, activePlayer = starter, turnNumber = 1,
            dartsThrown = 0, finished = false, legFinished = false, history = emptyList())
    }
}

data class CricketPlayerState(val name: String, val marks: Map<Int, Int>, val points: Int = 0)
data class CricketGameState(val players: List<CricketPlayerState>, val activePlayer: Int = 0, val finished: Boolean = false)

open class CricketGameEngine(playerNames: List<String>, private val includeBull: Boolean = true, targetNumbers: List<Int> = (15..20).toList()) {
    init { require(playerNames.size >= 2) }
    private val targets = targetNumbers.distinct().also { require(it.isNotEmpty() && it.all { n -> n in 1..20 }) } + if (includeBull) listOf(25) else emptyList()
    var state = CricketGameState(playerNames.map { name -> CricketPlayerState(name, targets.associateWith { 0 }) })
        private set

    fun playTurn(hits: List<DartHit>) {
        require(hits.size in 1..3)
        check(!state.finished)
        val ps = state.players.toMutableList()
        val i = state.activePlayer
        var current = ps[i]
        for (hit in hits) {
            val n = hit.number ?: continue
            if (n !in targets) continue
            val marks = current.marks.toMutableMap()
            val old = marks[n] ?: 0
            val total = old + hit.multiplier
            val closedMarks = total.coerceAtMost(3)
            val overflow = (total - 3).coerceAtLeast(0)
            marks[n] = closedMarks
            val opponentsClosed = ps.indices.filter { it != i }.all { (ps[it].marks[n] ?: 0) >= 3 }
            val points = if (overflow == 0 || opponentsClosed) 0 else {
                val value = if (n == 25) 25 else n
                value * overflow
            }
            current = current.copy(marks = marks, points = current.points + points)
        }
        ps[i] = current
        val currentClosed = targets.all { (current.marks[it] ?: 0) >= 3 }
        val opponentsHaveNoMorePoints = ps.indices.filter { it != i }.all { current.points >= ps[it].points }
        state = CricketGameState(ps, (i + 1) % ps.size, currentClosed && opponentsHaveNoMorePoints)
    }
}

data class ShanghaiPlayerState(val name: String, val score: Int = 0)
data class ShanghaiGameState(val players: List<ShanghaiPlayerState>, val round: Int, val winner: Int? = null, val activePlayer: Int = 0)

class ShanghaiGameEngine(
    playerNames: List<String>,
    private val settings: ShanghaiSettings = ShanghaiSettings()
) {
    init { require(playerNames.isNotEmpty()) }
    var state = ShanghaiGameState(playerNames.map { ShanghaiPlayerState(it) }, settings.firstNumber)
        private set

    fun playTurn(playerIndex: Int, hits: List<DartHit>) {
        require(playerIndex in state.players.indices)
        require(playerIndex == state.activePlayer) { "Ce n'est pas le tour de ce joueur." }
        require(hits.size in 1..3)
        check(state.winner == null)
        val target = state.round
        val relevant = hits.filter { it.number == target }
        val hasS = relevant.any { it.multiplier == 1 }
        val hasD = relevant.any { it.multiplier == 2 }
        val hasT = relevant.any { it.multiplier == 3 }
        val shanghai = hasS && hasD && hasT
        if (shanghai) {
            state = state.copy(winner = playerIndex)
            return
        }
        val tripleRuleFailed = settings.requireTripleEachTurn && !hasT
        val points = if (tripleRuleFailed || (settings.requireAnyTripleToAvoidReset && !hasT)) 0
            else relevant.sumOf { it.points }
        val ps = state.players.toMutableList()
        ps[playerIndex] = ps[playerIndex].copy(score = ps[playerIndex].score + points)
        val nextPlayer = (playerIndex + 1) % ps.size
        val roundComplete = nextPlayer == 0
        val nextRound = state.round + if (roundComplete) 1 else 0
        val finished = roundComplete && nextRound > settings.lastNumber
        state = ShanghaiGameState(ps, if (finished) settings.lastNumber else nextRound,
            if (finished) ps.indices.maxByOrNull { ps[it].score } else null, nextPlayer)
    }
}

data class AroundClockState(val playerName: String, val target: Int = 1, val complete: Boolean = false)
class AroundClockEngine(playerName: String, private val requireDoubles: Boolean = false, private val includeBull: Boolean = true) {
    var state = AroundClockState(playerName)
        private set
    fun playTurn(hits: List<DartHit>) {
        require(hits.size in 1..3)
        check(!state.complete)
        var target = state.target
        for (hit in hits) {
            val valid = hit.number == target && (!requireDoubles || hit.isDouble)
            if (valid) target++
            if (includeBull && target == 21 && hit.number == 25) target = 22
        }
        state = state.copy(target = target.coerceAtMost(if (includeBull) 22 else 21),
            complete = target > if (includeBull) 21 else 20)
    }
}

data class Bobs27State(val score: Int = 27, val doubleNumber: Int = 1, val complete: Boolean = false)
class Bobs27Engine {
    var state = Bobs27State()
        private set
    fun playTurn(hits: List<DartHit>) {
        require(hits.size in 1..3)
        check(!state.complete)
        val target = state.doubleNumber
        val delta = hits.sumOf { hit ->
            if (hit.number == target && hit.isDouble) 2 * target else -2 * target
        }
        val next = state.score + delta
        state = Bobs27State(next, target + 1, target >= 20)
    }
}

data class HalveItState(val score: Int = 0, val roundIndex: Int = 0, val complete: Boolean = false)
class HalveItEngine(private val targets: List<Int> = listOf(20, 19, 18, 17, 16, 15, 25)) {
    init { require(targets.isNotEmpty() && targets.all { it in 15..25 }) }
    var state = HalveItState()
        private set
    fun playTurn(hits: List<DartHit>) {
        require(hits.size in 1..3)
        check(!state.complete)
        val target = targets[state.roundIndex]
        val gained = hits.filter { it.number == target }.sumOf { it.points }
        val score = if (gained == 0) state.score / 2 else state.score + gained
        val next = state.roundIndex + 1
        state = HalveItState(score, next, next >= targets.size)
    }
}


/** Variante d'entraînement Tactics : cibles 10 à 20 et bull, avec marques de Cricket. */
class TacticsGameEngine(playerNames: List<String>, includeBull: Boolean = true) :
    CricketGameEngine(playerNames, includeBull, (10..20).toList())

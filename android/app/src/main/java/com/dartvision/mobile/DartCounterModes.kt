package com.dartvision.mobile

/**
 * Catalogue local des modes inspirés de DartCounter.
 *
 * Ce catalogue décrit les modes et leurs réglages. Il ne prétend pas encore
 * fournir l'ensemble des moteurs de partie : chaque moteur sera activé avec
 * ses règles et ses tests dédiés avant d'être présenté comme jouable.
 */
enum class DartGameMode(
    val id: String,
    val title: String,
    val category: GameModeCategory,
    val description: String,
    val offlineAvailable: Boolean = true
) {
    X01("x01", "301 / 501 / 701", GameModeCategory.CLASSIC,
        "Course vers zéro avec options d'entrée et de sortie."),
    CHECKOUT_121("121_checkout", "121 Checkout", GameModeCategory.TRAINING,
        "Enchaîner les finishes en double, avec progression et règle Safehouse."),
    AROUND_THE_CLOCK("around_the_clock", "Around the Clock", GameModeCategory.TRAINING,
        "Toucher les numéros dans l'ordre, puis le bull."),
    BOBS_27("bobs_27", "Bob's 27", GameModeCategory.TRAINING,
        "Entraînement aux doubles en partant de 27 points."),
    CRICKET("cricket", "Cricket", GameModeCategory.CLASSIC,
        "Fermer 15 à 20 et le bull, puis marquer sur les numéros non fermés."),
    TACTICS("tactics", "Tactics", GameModeCategory.CLASSIC,
        "Variante de Cricket avec cibles et conditions configurables."),
    DOUBLES_TRAINING("doubles_training", "Doubles Training", GameModeCategory.TRAINING,
        "Entraînement ciblé sur les doubles."),
    HALVE_IT("halve_it", "Halve-It", GameModeCategory.TRAINING,
        "Accumuler des points sur des cibles successives ; score divisé par deux si tout est manqué."),
    RANDOM_CHECKOUT("random_checkout", "Random Checkout", GameModeCategory.TRAINING,
        "Terminer des scores tirés au sort en respectant la sortie en double."),
    SCORE_TRAINING("score_training", "Score Training", GameModeCategory.TRAINING,
        "Travailler la régularité et le total de points."),
    SHANGHAI("shanghai", "Shanghai", GameModeCategory.PARTY,
        "Jouer le numéro de la manche ; simple, double et triple dans le même tour pour gagner."),
    SINGLES_TRAINING("singles_training", "Singles Training", GameModeCategory.TRAINING,
        "Entraînement sur les segments simples."),
    KILLER("killer", "Killer", GameModeCategory.PARTY,
        "Jeu multijoueur où chaque joueur doit devenir Killer puis éliminer les adversaires.")
}

enum class GameModeCategory { CLASSIC, TRAINING, PARTY }

enum class X01InRule { STRAIGHT_IN, DOUBLE_IN, MASTER_IN }
enum class X01OutRule { STRAIGHT_OUT, DOUBLE_OUT, MASTER_OUT }

data class X01Settings(
    val startingScore: Int = 301,
    val inRule: X01InRule = X01InRule.STRAIGHT_IN,
    val outRule: X01OutRule = X01OutRule.DOUBLE_OUT,
    val legsToWin: Int = 1,
    val setsToWin: Int = 1,
    val bestOfLegs: Int = 1,
    val bestOfSets: Int = 1
) {
    init {
        require(startingScore in 101..1001)
        require(legsToWin >= 1 && setsToWin >= 1)
        require(bestOfLegs >= 1 && bestOfSets >= 1)
    }
}

data class ShanghaiSettings(
    val firstNumber: Int = 1,
    val lastNumber: Int = 20,
    val requireTripleEachTurn: Boolean = false,
    val requireAnyTripleToAvoidReset: Boolean = false
) {
    init {
        require(firstNumber in 1..20)
        require(lastNumber in firstNumber..20)
    }
}

data class Checkout121Settings(
    val startingScore: Int = 121,
    val dartsPerAttempt: Int = 9,
    val increaseAfterSuccess: Int = 1,
    val safehouseEvery: Int = 1,
    val rounds: Int? = null
) {
    init {
        require(startingScore in 2..170)
        require(dartsPerAttempt in 3..15)
        require(increaseAfterSuccess >= 1)
        require(safehouseEvery >= 1)
        require(rounds == null || rounds >= 1)
    }
}

data class DartCounterModeCatalog(
    val modes: List<DartGameMode> = DartGameMode.entries
) {
    fun byId(id: String): DartGameMode? = modes.firstOrNull { it.id == id }

    fun byCategory(category: GameModeCategory): List<DartGameMode> =
        modes.filter { it.category == category }

    companion object {
        val default = DartCounterModeCatalog()
    }
}

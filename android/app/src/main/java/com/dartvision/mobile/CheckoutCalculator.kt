package com.dartvision.mobile

/** Route de sortie X01 légale en double-out, de 1 à 3 fléchettes. */
data class CheckoutRoute(val darts: List<DartHit>) {
    val notation: String get() = darts.joinToString("  •  ") { hit ->
        when {
            hit.number == 25 && hit.multiplier == 2 -> "BULL"
            hit.number == 25 -> "25"
            hit.number == null -> "MISS"
            hit.multiplier == 1 -> "S${hit.number}"
            hit.multiplier == 2 -> "D${hit.number}"
            else -> "T${hit.number}"
        }
    }
    val points: Int get() = darts.sumOf { it.points }
}

object CheckoutCalculator {
    private val scoringDarts: List<DartHit> = buildList {
        for (n in 1..20) {
            add(DartHit(n, 1))
            add(DartHit(n, 2))
            add(DartHit(n, 3))
        }
        add(DartHit(25, 1))
        add(DartHit(25, 2))
    }

    fun routes(score: Int, maxRoutes: Int = 8): List<CheckoutRoute> {
        require(maxRoutes > 0)
        if (score !in 2..170) return emptyList()
        val found = mutableListOf<CheckoutRoute>()
        fun search(remaining: Int, depth: Int, chosen: MutableList<DartHit>) {
            if (depth == 0) return
            for (dart in scoringDarts) {
                val after = remaining - dart.points
                if (after < 0 || after == 1) continue
                chosen.add(dart)
                if (after == 0 && dart.isDouble) {
                    found.add(CheckoutRoute(chosen.toList()))
                } else if (after > 1 && depth > 1) {
                    search(after, depth - 1, chosen)
                }
                chosen.removeAt(chosen.lastIndex)
            }
        }
        search(score, 3, mutableListOf())
        return found.distinctBy { it.notation }
            .sortedWith(compareBy<CheckoutRoute> { it.darts.size }
                .thenByDescending { it.darts.last().points }
                .thenBy { it.notation })
            .take(maxRoutes)
    }
}

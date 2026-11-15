package com.dartvision.mobile

/** Parse une saisie simple, double, triple ou ratée sans accepter de valeur ambiguë. */
object DartHitParser {
    fun parse(raw: String): DartHit {
        val value = raw.trim().uppercase().replace(" ", "")
        if (value == "MISS" || value == "0") return DartHit.MISS
        require(Regex("^[SDT]?(?:[1-9]|1[0-9]|20|25)$").matches(value)) {
            "Impact invalide : $raw"
        }
        val multiplier = when (value.firstOrNull()) {
            'S' -> 1
            'D' -> 2
            'T' -> 3
            else -> 1
        }
        val numberText = if (value.firstOrNull() in listOf('S', 'D', 'T')) value.drop(1) else value
        return DartHit(numberText.toInt(), multiplier)
    }
}

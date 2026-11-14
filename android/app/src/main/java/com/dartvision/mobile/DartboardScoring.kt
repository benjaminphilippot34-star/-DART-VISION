package com.dartvision.mobile

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Géométrie de score d'une cible steel-tip standard.
 *
 * Les coordonnées sont exprimées en millimètres depuis le centre de la cible :
 * x positif vers la droite, y positif vers le bas. L'axe 20 est à 12 heures.
 *
 * Cette classe ne détecte PAS les fléchettes dans une image. Elle transforme
 * uniquement un point d'impact déjà localisé et calibré en score.
 */
data class DartScore(
    val number: Int?,
    val multiplier: Int,
    val points: Int,
    val zone: String,
    val radiusMm: Double,
    val angleDegrees: Double,
    val nearWire: Boolean
)

object DartboardScoring {
    const val OUTER_RADIUS_MM = 170.0
    private const val INNER_BULL_RADIUS_MM = 6.35
    private const val OUTER_BULL_RADIUS_MM = 15.9
    private const val TRIPLE_INNER_RADIUS_MM = 99.0
    private const val TRIPLE_OUTER_RADIUS_MM = 107.0
    private const val DOUBLE_INNER_RADIUS_MM = 162.0

    // Ordre officiel dans le sens horaire, en partant du secteur 20 à 12 heures.
    private val sectors = intArrayOf(
        20, 1, 18, 4, 13, 6, 10, 15, 2, 17,
        3, 19, 7, 16, 8, 11, 14, 9, 12, 5
    )

    /**
     * Convertit un point d'impact en score.
     *
     * wireToleranceMm ne change jamais le score calculé : il signale seulement
     * qu'une validation humaine est prudente près d'un fil.
     */
    fun score(xMm: Double, yMm: Double, wireToleranceMm: Double = 1.5): DartScore {
        require(xMm.isFinite() && yMm.isFinite()) { "Les coordonnées doivent être finies." }
        require(wireToleranceMm >= 0.0 && wireToleranceMm.isFinite()) {
            "La tolérance des fils doit être positive et finie."
        }

        val radius = hypot(xMm, yMm)
        // atan2(x, -y) : 0° en haut, angles positifs dans le sens horaire.
        val rawAngle = Math.toDegrees(atan2(xMm, -yMm))
        val angle = (rawAngle + 360.0) % 360.0
        val sectorIndex = (floor((angle + 9.0) / 18.0).toInt()) % 20
        val number = sectors[sectorIndex]

        val (multiplier, points, zone) = when {
            radius > OUTER_RADIUS_MM -> Triple(0, 0, "Hors cible")
            radius <= INNER_BULL_RADIUS_MM -> Triple(1, 50, "Bull")
            radius <= OUTER_BULL_RADIUS_MM -> Triple(1, 25, "Simple extérieur")
            radius in TRIPLE_INNER_RADIUS_MM..TRIPLE_OUTER_RADIUS_MM ->
                Triple(3, number * 3, "Triple")
            radius >= DOUBLE_INNER_RADIUS_MM ->
                Triple(2, number * 2, "Double")
            else -> Triple(1, number, "Simple")
        }

        val sectorWireDistance = distanceToNearestSectorWire(radius, angle)
        val ringBoundaries = doubleArrayOf(
            INNER_BULL_RADIUS_MM,
            OUTER_BULL_RADIUS_MM,
            TRIPLE_INNER_RADIUS_MM,
            TRIPLE_OUTER_RADIUS_MM,
            DOUBLE_INNER_RADIUS_MM,
            OUTER_RADIUS_MM
        )
        val ringWireDistance = ringBoundaries.minOf { kotlin.math.abs(radius - it) }
        val nearWire = min(sectorWireDistance, ringWireDistance) <= wireToleranceMm

        return DartScore(
            number = if (points == 0 || zone == "Bull" || zone == "Simple extérieur") null else number,
            multiplier = multiplier,
            points = points,
            zone = zone,
            radiusMm = radius,
            angleDegrees = angle,
            nearWire = nearWire
        )
    }

    private fun distanceToNearestSectorWire(radius: Double, angleDegrees: Double): Double {
        val sectorWidth = 18.0
        val nearestBoundary = floor(angleDegrees / sectorWidth + 0.5) * sectorWidth
        val deltaRadians = Math.toRadians(angleDegrees - nearestBoundary)
        return radius * kotlin.math.abs(sin(deltaRadians))
    }
}

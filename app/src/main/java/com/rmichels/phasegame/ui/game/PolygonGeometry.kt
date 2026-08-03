package com.rmichels.phasegame.ui.game

internal data class PolygonVertex(
    val x: Float,
    val y: Float
)

/** Unit-circle vertices shared by rendering and host-side geometry tests. */
internal fun regularPolygonVertices(
    sideCount: Int,
    rotationRadians: Float
): List<PolygonVertex> {
    require(sideCount >= 3)
    val anglePerSide = (2.0 * Math.PI / sideCount).toFloat()
    return List(sideCount) { index ->
        val angle = rotationRadians + index * anglePerSide
        PolygonVertex(
            x = kotlin.math.cos(angle),
            y = kotlin.math.sin(angle)
        )
    }
}

package com.rmichels.phasegame

import androidx.compose.ui.graphics.Color

internal data class PhaseVisualTheme(
    val triangleColor: Color,
    val indicatorColor: Color
)

internal fun createPhaseVisualThemes(
    phaseCount: Int,
    random: kotlin.random.Random = kotlin.random.Random.Default
): List<PhaseVisualTheme> {
    require(phaseCount > 0)

    val hueSpacing = 360f / phaseCount
    val shuffledHues = List(phaseCount) { phaseIndex ->
        phaseIndex * hueSpacing
    }.shuffled(random)

    return shuffledHues.map { hue ->
        PhaseVisualTheme(
            triangleColor = Color.hsv(
                hue = hue,
                saturation = 0.72f,
                value = 0.82f
            ),
            indicatorColor = Color.hsv(
                hue = hue,
                saturation = 0.90f,
                value = 1f
            )
        )
    }
}
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

internal data class UpcomingPhaseVisual(
    val phaseIndex: Int,
    val rhythm: List<Boolean>,
    val transitionProgress: Float
)

internal fun upcomingPhaseVisual(
    queuedPatterns: List<QueuedPatternBar>,
    barProgress: Float,
    slideDurationBars: Float = 2f
): UpcomingPhaseVisual? {
    require(slideDurationBars > 0f)

    val currentPhaseIndex =
        queuedPatterns.firstOrNull()?.phaseIndex
            ?: return null

    val nextPhaseOffset =
        queuedPatterns.indexOfFirst { queuedPattern ->
            queuedPattern.phaseIndex != currentPhaseIndex
        }

    if (nextPhaseOffset < 0) return null

    val upcomingPattern = queuedPatterns[nextPhaseOffset]
    val barsUntilStart =
        nextPhaseOffset - barProgress.coerceIn(0f, 1f)

    return UpcomingPhaseVisual(
        phaseIndex = upcomingPattern.phaseIndex,
        rhythm = upcomingPattern.rhythm,
        transitionProgress = (
                1f - barsUntilStart / slideDurationBars
                ).coerceIn(0f, 1f)
    )
}
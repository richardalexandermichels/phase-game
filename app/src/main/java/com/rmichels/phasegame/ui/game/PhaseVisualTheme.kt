package com.rmichels.phasegame.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.rmichels.phasegame.gameplay.QueuedPatternBar

internal data class PhaseVisualTheme(
    val triangleColor: Color,
    val indicatorColor: Color,
    val targetColor: Color,
    val clockHandColor: Color = lerp(
        triangleColor,
        Color.White,
        0.65f
    )
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
            ),
            targetColor = Color.hsv(
                hue = hue,
                saturation = 0.30f,
                value = 1f
            )
        )
    }
}

internal fun resolvePhaseVisualThemes(
    phaseCount: Int,
    useStaticThemes: Boolean = USE_STATIC_PHASE_VISUAL_THEMES,
    random: kotlin.random.Random = kotlin.random.Random.Default
): List<PhaseVisualTheme> {
    if (!useStaticThemes) {
        return createPhaseVisualThemes(phaseCount, random)
    }

    require(STATIC_PHASE_VISUAL_THEMES.size >= phaseCount) {
        "Static phase palette has ${STATIC_PHASE_VISUAL_THEMES.size} colors, " +
            "but the game needs $phaseCount. Export at least $phaseCount colors."
    }
    return STATIC_PHASE_VISUAL_THEMES.take(phaseCount)
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

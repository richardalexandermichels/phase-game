package com.rmichels.phasegame.ui.game

import com.rmichels.phasegame.gameplay.QueuedPatternBar
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class PhaseVisualThemeTest {
    @Test
    fun createPhaseVisualThemes_createsDistinctThemeForEveryPhase() {
        val themes = createPhaseVisualThemes(
            phaseCount = 16,
            random = Random(1234)
        )

        assertEquals(16, themes.size)
        assertEquals(
            16,
            themes.map { theme -> theme.triangleColor }
                .distinct()
                .size
        )
    }

    @Test
    fun resolvePhaseVisualThemes_usesStaticPaletteInItsDefinedOrder() {
        val themes = resolvePhaseVisualThemes(
            phaseCount = 4,
            useStaticThemes = true
        )

        assertEquals(STATIC_PHASE_VISUAL_THEMES.take(4), themes)
    }

    @Test
    fun resolvePhaseVisualThemes_keepsRandomGeneratorAvailable() {
        val expected = createPhaseVisualThemes(4, Random(1234))
        val actual = resolvePhaseVisualThemes(
            phaseCount = 4,
            useStaticThemes = false,
            random = Random(1234)
        )

        assertEquals(expected, actual)
    }

    @Test
    fun upcomingPhaseVisual_remainsContinuousWhenQueueAdvances() {
        val currentRhythm =
            listOf(true, false, true, false)
        val upcomingRhythm =
            listOf(false, true, false, true)

        val queue = listOf(
            QueuedPatternBar(0L, 0, currentRhythm),
            QueuedPatternBar(1L, 0, currentRhythm),
            QueuedPatternBar(2L, 1, upcomingRhythm)
        )

        val immediatelyBeforeAdvance = upcomingPhaseVisual(
            queuedPatterns = queue,
            barProgress = 1f
        )

        val immediatelyAfterAdvance = upcomingPhaseVisual(
            queuedPatterns = queue.drop(1),
            barProgress = 0f
        )

        assertEquals(
            0.5f,
            immediatelyBeforeAdvance?.transitionProgress ?: -1f,
            0.0001f
        )
        assertEquals(
            0.5f,
            immediatelyAfterAdvance?.transitionProgress ?: -1f,
            0.0001f
        )
    }
}

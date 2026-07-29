package com.rmichels.phasegame

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
}
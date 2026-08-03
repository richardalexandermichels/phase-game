package com.rmichels.phasegame.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GameAudioConductorTest {
    @Test
    fun firstStepAtOrAfter_neverReplaysAPastBeat() {
        val step = 250_000_000L

        assertEquals(0L, firstStepAtOrAfter(-1L, step))
        assertEquals(0L, firstStepAtOrAfter(0L, step))
        assertEquals(1L, firstStepAtOrAfter(1L, step))
        assertEquals(1L, firstStepAtOrAfter(step, step))
        assertEquals(2L, firstStepAtOrAfter(step + 1L, step))
    }

    @Test
    fun firstStepAtOrAfter_rejectsInvalidDuration() {
        assertThrows(IllegalArgumentException::class.java) {
            firstStepAtOrAfter(elapsedNanos = 0L, stepDurationNanos = 0L)
        }
    }
}

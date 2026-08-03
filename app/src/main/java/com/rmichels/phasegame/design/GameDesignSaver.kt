package com.rmichels.phasegame.design

import androidx.compose.runtime.saveable.listSaver
import com.rmichels.phasegame.music.BASE_PIANO_LOW_MIDI
import com.rmichels.phasegame.music.PLAYER_PIANO_LOW_MIDI

/** Compose persistence adapter, kept separate from the platform-free model. */
internal val GameDesignSaver = listSaver<GameDesign, Int>(
    save = { design ->
        buildList {
            add(-3)
            add(design.stepCount)
            design.baseNotes.forEach { chord ->
                add(chord.size)
                addAll(chord)
            }
            design.playerNotesByPhase.forEach { phase ->
                phase.forEach { chord ->
                    add(chord.size)
                    addAll(chord)
                }
            }
        }
    },
    restore = { saved -> restoreGameDesign(saved) }
)

private fun restoreGameDesign(saved: List<Int>): GameDesign {
    if (saved.first() == -3) {
        val stepCount = saved[1]
        var cursor = 2
        fun readChord(): List<Int> {
            val count = saved[cursor++]
            return List(count) { saved[cursor++] }
        }
        return GameDesign(
            stepCount = stepCount,
            baseNotes = List(stepCount) { readChord() },
            playerNotesByPhase = List(stepCount) {
                List(stepCount) { readChord() }
            }
        )
    }

    val oldBaseMidi = intArrayOf(
        38, 40, 42, 43, 45, 47, 49, 50, 52, 54, 55, 57
    )
    val oldPlayerMidi = intArrayOf(
        50, 52, 54, 55, 57, 59, 61, 62, 64, 66, 67, 69
    )
    val stepCount = if (saved.first() == -2) saved[1] else saved.first()
    var cursor = if (saved.first() == -2) 2 else 1

    fun readLegacyChord(mapping: IntArray, lowMidi: Int): List<Int> {
        if (saved.first() != -2) {
            return saved[cursor++].takeIf { it >= 0 }?.let { oldPitch ->
                val octaveShift = if (lowMidi == PLAYER_PIANO_LOW_MIDI) 12 else 0
                listOf(mapping[oldPitch] + octaveShift - lowMidi)
            }.orEmpty()
        }
        val count = saved[cursor++]
        return List(count) {
            val oldPitch = saved[cursor++]
            val octaveShift = if (lowMidi == PLAYER_PIANO_LOW_MIDI) 12 else 0
            mapping[oldPitch] + octaveShift - lowMidi
        }
    }

    return GameDesign(
        stepCount = stepCount,
        baseNotes = List(stepCount) {
            readLegacyChord(oldBaseMidi, BASE_PIANO_LOW_MIDI)
        },
        playerNotesByPhase = List(stepCount) {
            List(stepCount) {
                readLegacyChord(oldPlayerMidi, PLAYER_PIANO_LOW_MIDI)
            }
        }
    )
}

package com.rmichels.phasegame

import androidx.compose.runtime.saveable.listSaver
import kotlin.math.ceil

internal const val DESIGN_PITCH_COUNT = 12
internal const val DESIGN_MAX_CHORD_SIZE = 4
internal const val DESIGN_MIN_CELL_WIDTH_DP = 28f

internal data class GameDesign(
    val stepCount: Int,
    val baseNotes: List<List<Int>>,
    val playerNotesByPhase: List<List<List<Int>>>
) {
    init {
        require(stepCount in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS)
        require(baseNotes.size == stepCount)
        require(playerNotesByPhase.size == stepCount)
        require(playerNotesByPhase.all { it.size == stepCount })
        require(baseNotes.all(::isValidChord))
        require(
            playerNotesByPhase
                .flatten()
                .all(::isValidChord)
        )
    }

    val baseRhythm: List<Boolean>
        get() = baseNotes.map { it.isNotEmpty() }

    val hasPlayableBase: Boolean
        get() = baseNotes.any { it.isNotEmpty() }

    fun enabledPlayerColumns(phaseIndex: Int): List<Boolean> =
        shiftedRhythm(baseRhythm, normalizedPhase(phaseIndex))

    fun playerNotes(phaseIndex: Int, stepIndex: Int): List<Int> =
        playerNotesByPhase[normalizedPhase(phaseIndex)][stepIndex]

    fun playerNote(phaseIndex: Int, stepIndex: Int): Int? =
        playerNotes(phaseIndex, stepIndex).firstOrNull()

    fun withBaseNote(stepIndex: Int, pitchIndex: Int?): GameDesign {
        require(stepIndex in baseNotes.indices)
        require(pitchIndex == null || pitchIndex in 0 until DESIGN_PITCH_COUNT)

        val updatedBase = baseNotes.toMutableList().also {
            it[stepIndex] = pitchIndex?.let(::listOf) ?: emptyList()
        }
        return copy(baseNotes = updatedBase)
            .withoutDisabledPlayerNotes()
    }

    fun toggledBasePitch(stepIndex: Int, pitchIndex: Int): GameDesign {
        require(stepIndex in baseNotes.indices)
        require(pitchIndex in 0 until DESIGN_PITCH_COUNT)
        val updatedBase = baseNotes.toMutableList()
        updatedBase[stepIndex] = toggledChord(
            updatedBase[stepIndex],
            pitchIndex
        )
        return copy(baseNotes = updatedBase).withoutDisabledPlayerNotes()
    }

    fun withPlayerNote(
        phaseIndex: Int,
        stepIndex: Int,
        pitchIndex: Int?
    ): GameDesign {
        val phase = normalizedPhase(phaseIndex)
        require(stepIndex in 0 until stepCount)
        require(pitchIndex == null || pitchIndex in 0 until DESIGN_PITCH_COUNT)
        if (!enabledPlayerColumns(phase)[stepIndex]) {
            return this
        }

        val updatedPhases = playerNotesByPhase.map { it.toMutableList() }
        updatedPhases[phase][stepIndex] =
            pitchIndex?.let(::listOf) ?: emptyList()
        return copy(playerNotesByPhase = updatedPhases)
    }

    fun toggledPlayerPitch(
        phaseIndex: Int,
        stepIndex: Int,
        pitchIndex: Int
    ): GameDesign {
        val phase = normalizedPhase(phaseIndex)
        require(stepIndex in 0 until stepCount)
        require(pitchIndex in 0 until DESIGN_PITCH_COUNT)
        if (!enabledPlayerColumns(phase)[stepIndex]) return this
        val updatedPhases = playerNotesByPhase.map { it.toMutableList() }
        updatedPhases[phase][stepIndex] = toggledChord(
            updatedPhases[phase][stepIndex],
            pitchIndex
        )
        return copy(playerNotesByPhase = updatedPhases)
    }

    fun resized(newStepCount: Int): GameDesign {
        require(newStepCount in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS)
        if (newStepCount == stepCount) return this

        val resizedBase = List(newStepCount) { step ->
            baseNotes.getOrNull(step).orEmpty()
        }
        val resizedPhases = List(newStepCount) { phase ->
            List(newStepCount) { step ->
                playerNotesByPhase
                    .getOrNull(phase)
                    ?.getOrNull(step)
                    .orEmpty()
            }
        }
        return GameDesign(
            stepCount = newStepCount,
            baseNotes = resizedBase,
            playerNotesByPhase = resizedPhases
        ).withoutDisabledPlayerNotes()
    }

    private fun withoutDisabledPlayerNotes(): GameDesign {
        val cleanedPhases = playerNotesByPhase.mapIndexed { phase, notes ->
            val enabledColumns = shiftedRhythm(baseRhythm, phase)
            notes.mapIndexed { step, note ->
                if (enabledColumns[step]) note else emptyList()
            }
        }
        return copy(playerNotesByPhase = cleanedPhases)
    }

    private fun normalizedPhase(phaseIndex: Int): Int =
        Math.floorMod(phaseIndex, stepCount)

    companion object {
        fun default(): GameDesign {
            val legacyRhythm = listOf(
                true, true, true, false,
                true, true, false, true,
                false, true, true, false
            )
            return fromBaseRhythm(legacyRhythm)
        }

        fun fromBaseRhythm(rhythm: List<Boolean>): GameDesign {
            validateBaseRhythm(rhythm)
            // A3 is the twelfth row in the low-register D-major palette.
            val baseNotes = rhythm.map { isPlayed ->
                if (isPlayed) listOf(11) else emptyList()
            }
            return GameDesign(
                stepCount = rhythm.size,
                baseNotes = baseNotes,
                playerNotesByPhase = List(rhythm.size) {
                    List(rhythm.size) { emptyList() }
                }
            )
        }

        private fun isValidChord(chord: List<Int>): Boolean =
            chord.size <= DESIGN_MAX_CHORD_SIZE &&
                chord.distinct().size == chord.size &&
                chord.all { it in 0 until DESIGN_PITCH_COUNT }

        private fun toggledChord(
            chord: List<Int>,
            pitchIndex: Int
        ): List<Int> = when {
            pitchIndex in chord -> chord - pitchIndex
            chord.size >= DESIGN_MAX_CHORD_SIZE -> chord
            else -> (chord + pitchIndex).sorted()
        }
    }
}

internal val GameDesignSaver = listSaver<GameDesign, Int>(
    save = { design ->
        buildList {
            add(-2)
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
    restore = { saved ->
        if (saved.first() == -2) {
            val stepCount = saved[1]
            var cursor = 2
            fun readChord(): List<Int> {
                val count = saved[cursor++]
                return List(count) { saved[cursor++] }
            }
            val baseNotes = List(stepCount) { readChord() }
            val playerNotes = List(stepCount) {
                List(stepCount) { readChord() }
            }
            GameDesign(stepCount, baseNotes, playerNotes)
        } else {
            // Restore state saved by the original monophonic model.
            val stepCount = saved.first()
            var cursor = 1
            val baseNotes = List(stepCount) {
                saved[cursor++].takeIf { it >= 0 }?.let(::listOf).orEmpty()
            }
            val playerNotes = List(stepCount) {
                List(stepCount) {
                    saved[cursor++].takeIf { it >= 0 }?.let(::listOf).orEmpty()
                }
            }
            GameDesign(stepCount, baseNotes, playerNotes)
        }
    }
)

internal fun matrixPageRanges(
    stepCount: Int,
    availableWidthDp: Float,
    minimumCellWidthDp: Float = DESIGN_MIN_CELL_WIDTH_DP
): List<IntRange> {
    require(stepCount > 0)
    require(availableWidthDp > 0f)
    require(minimumCellWidthDp > 0f)

    if (availableWidthDp / stepCount >= minimumCellWidthDp) {
        return listOf(0 until stepCount)
    }

    val firstPageSize = ceil(stepCount / 2f).toInt()
    return listOf(
        0 until firstPageSize,
        firstPageSize until stepCount
    )
}

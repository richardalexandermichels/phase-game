package com.rmichels.phasegame

import androidx.compose.runtime.saveable.listSaver
import kotlin.math.ceil

internal const val DESIGN_PITCH_COUNT = 12
internal const val DESIGN_MIN_CELL_WIDTH_DP = 28f

internal data class GameDesign(
    val stepCount: Int,
    val baseNotes: List<Int?>,
    val playerNotesByPhase: List<List<Int?>>
) {
    init {
        require(stepCount in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS)
        require(baseNotes.size == stepCount)
        require(playerNotesByPhase.size == stepCount)
        require(playerNotesByPhase.all { it.size == stepCount })
        require(baseNotes.all { it == null || it in 0 until DESIGN_PITCH_COUNT })
        require(
            playerNotesByPhase
                .flatten()
                .all { it == null || it in 0 until DESIGN_PITCH_COUNT }
        )
    }

    val baseRhythm: List<Boolean>
        get() = baseNotes.map { it != null }

    val hasPlayableBase: Boolean
        get() = baseNotes.any { it != null }

    fun enabledPlayerColumns(phaseIndex: Int): List<Boolean> =
        shiftedRhythm(baseRhythm, normalizedPhase(phaseIndex))

    fun playerNote(phaseIndex: Int, stepIndex: Int): Int? =
        playerNotesByPhase[normalizedPhase(phaseIndex)][stepIndex]

    fun withBaseNote(stepIndex: Int, pitchIndex: Int?): GameDesign {
        require(stepIndex in baseNotes.indices)
        require(pitchIndex == null || pitchIndex in 0 until DESIGN_PITCH_COUNT)

        val updatedBase = baseNotes.toMutableList().also {
            it[stepIndex] = pitchIndex
        }
        return copy(baseNotes = updatedBase)
            .withoutDisabledPlayerNotes()
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
        updatedPhases[phase][stepIndex] = pitchIndex
        return copy(playerNotesByPhase = updatedPhases)
    }

    fun resized(newStepCount: Int): GameDesign {
        require(newStepCount in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS)
        if (newStepCount == stepCount) return this

        val resizedBase = List<Int?>(newStepCount) { step ->
            baseNotes.getOrNull(step)
        }
        val resizedPhases = List(newStepCount) { phase ->
            List<Int?>(newStepCount) { step ->
                playerNotesByPhase
                    .getOrNull(phase)
                    ?.getOrNull(step)
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
                if (enabledColumns[step]) note else null
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
            // A3 is the ninth row in the low-register design palette.
            val baseNotes = rhythm.map { isPlayed ->
                if (isPlayed) 8 else null
            }
            return GameDesign(
                stepCount = rhythm.size,
                baseNotes = baseNotes,
                playerNotesByPhase = List(rhythm.size) {
                    List(rhythm.size) { null }
                }
            )
        }
    }
}

internal val GameDesignSaver = listSaver<GameDesign, Int>(
    save = { design ->
        buildList {
            add(design.stepCount)
            addAll(design.baseNotes.map { it ?: -1 })
            design.playerNotesByPhase.forEach { phase ->
                addAll(phase.map { it ?: -1 })
            }
        }
    },
    restore = { saved ->
        val stepCount = saved.first()
        var cursor = 1
        val baseNotes = List<Int?>(stepCount) {
            saved[cursor++].takeIf { it >= 0 }
        }
        val playerNotes = List(stepCount) {
            List<Int?>(stepCount) {
                saved[cursor++].takeIf { it >= 0 }
            }
        }
        GameDesign(stepCount, baseNotes, playerNotes)
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

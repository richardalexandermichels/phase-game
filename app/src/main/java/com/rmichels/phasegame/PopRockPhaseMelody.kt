package com.rmichels.phasegame

import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * Produces one chord-aware pop-rock melody for the current gameplay phase.
 * Re-selecting the same phase preserves the phrase; advancing to a different
 * phase creates a new full-bar phrase.
 */
internal class PopRockPhaseMelody(
    private val stepCount: Int,
    private val random: Random = Random.Default
) {
    private val progressions = listOf(
        intArrayOf(0, 7, 9, 5), // I-V-vi-IV
        intArrayOf(0, 5, 7, 5), // I-IV-V-IV
        intArrayOf(9, 5, 0, 7), // vi-IV-I-V
        intArrayOf(0, 9, 5, 7), // I-vi-IV-V
        intArrayOf(0, 7, 5, 7)  // I-V-IV-V
    )
    private val scaleOffsets = intArrayOf(
        -5, -3, -1, 0, 2, 4, 5, 7, 9, 11, 12
    )

    private var selectedPhase = 0
    private var chordRoots = progressions[random.nextInt(progressions.size)]
    private var semitoneOffsets = generatePhrase(chordRoots)
    private var generation = 1

    init {
        require(stepCount > 0)
    }

    @Synchronized
    fun selectPhase(phaseIndex: Int) {
        if (phaseIndex == selectedPhase) return
        selectedPhase = phaseIndex
        chordRoots = progressions[random.nextInt(progressions.size)]
        semitoneOffsets = generatePhrase(chordRoots)
        generation++
    }

    @Synchronized
    fun playbackRateForBaseStep(stepIndex: Int): Float =
        playbackRate(semitoneOffsets[normalizedStep(stepIndex)])

    @Synchronized
    fun playbackRateForPlayerStep(
        playerStepIndex: Int,
        phaseIndex: Int
    ): Float = playbackRate(
        playerSemitoneForStep(playerStepIndex, phaseIndex) -
            PLAYER_SAMPLE_ROOT_OFFSET
    )

    @Synchronized
    internal fun snapshotSemitoneOffsets(): List<Int> =
        semitoneOffsets.toList()

    @Synchronized
    internal fun snapshotChordRoots(): List<Int> =
        chordRoots.toList()

    @Synchronized
    internal fun generationCount(): Int = generation

    @Synchronized
    internal fun playerSemitoneForStep(
        playerStepIndex: Int,
        phaseIndex: Int
    ): Int = harmonizedPlayerNote(
        sourceNote = semitoneOffsets[
            Math.floorMod(playerStepIndex + phaseIndex, stepCount)
        ],
        simultaneousBaseNote =
            semitoneOffsets[normalizedStep(playerStepIndex)]
    )

    private fun harmonizedPlayerNote(
        sourceNote: Int,
        simultaneousBaseNote: Int
    ): Int {
        val thirdAbove = sourceNote + diatonicIntervalAbove(
            sourceNote,
            minimumSemitones = 3
        )
        return if (thirdAbove != simultaneousBaseNote) {
            thirdAbove
        } else {
            sourceNote + diatonicIntervalAbove(
                sourceNote,
                minimumSemitones = 6
            )
        }
    }

    private fun generatePhrase(progression: IntArray): IntArray {
        var previous = 0
        return IntArray(stepCount) { step ->
            val chordPosition =
                (step * progression.size / stepCount)
                    .coerceAtMost(progression.lastIndex)
            val chordRoot = progression[chordPosition]
            val chordStart =
                (chordPosition * stepCount) / progression.size
            val strongPosition =
                step == chordStart || step % 2 == 0

            var candidates = scaleOffsets.filter { offset ->
                !strongPosition || isChordTone(offset, chordRoot)
            }
            val compactCandidates = candidates.filter { offset ->
                abs(offset - previous) <= if (strongPosition) 7 else 4
            }
            if (compactCandidates.isNotEmpty()) {
                candidates = compactCandidates
            }
            val nonRepeating = candidates.filter { it != previous }
            if (nonRepeating.isNotEmpty()) {
                candidates = nonRepeating
            }

            val chosen = candidates[random.nextInt(candidates.size)]
            previous = chosen
            chosen
        }
    }

    private fun normalizedStep(stepIndex: Int): Int =
        Math.floorMod(stepIndex, stepCount)

    private fun playbackRate(semitones: Int): Float =
        2.0.pow(semitones / 12.0).toFloat()

    internal companion object {
        // Successful-player WAVs are rooted at F#3, four semitones above D3.
        private const val PLAYER_SAMPLE_ROOT_OFFSET = 4
        private val majorPitchClasses = setOf(0, 2, 4, 5, 7, 9, 11)

        private fun diatonicIntervalAbove(
            sourceOffset: Int,
            minimumSemitones: Int
        ): Int = (minimumSemitones..minimumSemitones + 2).first { interval ->
            Math.floorMod(sourceOffset + interval, 12) in majorPitchClasses
        }

        fun isChordTone(offset: Int, chordRoot: Int): Boolean {
            val pitchClass = Math.floorMod(offset, 12)
            val third = when (chordRoot) {
                9 -> 0 // vi is minor: B-D-F#
                else -> Math.floorMod(chordRoot + 4, 12)
            }
            val fifth = Math.floorMod(chordRoot + 7, 12)
            return pitchClass == chordRoot ||
                pitchClass == third ||
                pitchClass == fifth
        }
    }
}

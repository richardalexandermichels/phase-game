package com.rmichels.phasegame.music

import kotlin.math.abs
import kotlin.random.Random

/**
 * Creates one phase-scoped piano arrangement. The left hand supplies Base
 * notes in C2..B3 and the right hand supplies Player notes in C4..B5, so their
 * ranges cannot cross. Both hands share the same key, scale, and chord map.
 */
internal class PopRockPhaseMelody(
    private val stepCount: Int,
    private val random: Random = Random.Default
) {
    private val majorProgressions = listOf(
        intArrayOf(0, 4, 5, 3), // I-V-vi-IV
        intArrayOf(0, 3, 4, 3), // I-IV-V-IV
        intArrayOf(5, 3, 0, 4), // vi-IV-I-V
        intArrayOf(0, 5, 3, 4)  // I-vi-IV-V
    )
    private val minorProgressions = listOf(
        intArrayOf(0, 5, 2, 6), // i-VI-III-VII
        intArrayOf(0, 3, 4, 0), // i-iv-v-i
        intArrayOf(0, 6, 5, 6), // i-VII-VI-VII
        intArrayOf(0, 2, 6, 5)  // i-III-VII-VI
    )

    private var selectedPhase = 0
    private var tonicPitchClass = 0
    private var scale = PianoScalePreset.MAJOR
    private var chordDegrees = majorProgressions.first()
    private var chordTones = emptyList<Set<Int>>()
    private var baseMidiNotes = IntArray(stepCount)
    private var playerMidiNotes = IntArray(stepCount)
    private var generation = 0

    init {
        require(stepCount > 0)
        regenerateArrangement()
    }

    @Synchronized
    fun selectPhase(phaseIndex: Int) {
        if (phaseIndex == selectedPhase) return
        selectedPhase = phaseIndex
        regenerateArrangement()
    }

    @Synchronized
    fun basePitchIndexForStep(stepIndex: Int): Int =
        baseMidiNotes[normalizedStep(stepIndex)] - BASE_PIANO_LOW_MIDI

    @Synchronized
    fun playerPitchIndexForStep(stepIndex: Int): Int =
        playerMidiNotes[normalizedStep(stepIndex)] - PLAYER_PIANO_LOW_MIDI

    @Synchronized
    internal fun snapshotBaseMidiNotes(): List<Int> = baseMidiNotes.toList()

    @Synchronized
    internal fun snapshotPlayerMidiNotes(): List<Int> = playerMidiNotes.toList()

    @Synchronized
    internal fun snapshotChordTones(): List<Set<Int>> = chordTones.toList()

    @Synchronized
    internal fun snapshotTonicPitchClass(): Int = tonicPitchClass

    @Synchronized
    internal fun snapshotScale(): PianoScalePreset = scale

    @Synchronized
    internal fun generationCount(): Int = generation

    private fun regenerateArrangement() {
        val previousLastChord = chordTones.lastOrNull()
        val previousChordRoot = if (chordTones.isNotEmpty()) {
            scalePitchClass(chordDegrees.last())
        } else {
            null
        }
        val previousBaseMidi = baseMidiNotes.lastOrNull()
        val previousPlayerMidi = playerMidiNotes.lastOrNull()

        val plan = if (previousLastChord == null || previousChordRoot == null) {
            randomInitialPlan()
        } else {
            relatedPlan(
                previousLastChord = previousLastChord,
                previousChordRoot = previousChordRoot
            )
        }
        tonicPitchClass = plan.tonicPitchClass
        scale = plan.scale
        chordDegrees = plan.chordDegrees
        chordTones = chordDegrees.map(::triadPitchClasses)
        baseMidiNotes = generateHand(
            lowMidi = BASE_PIANO_LOW_MIDI,
            highMidi = BASE_PIANO_HIGH_MIDI,
            startingMidi = previousBaseMidi ?: 43,
            bassHand = true,
            connectFromPreviousPhase = previousBaseMidi != null
        )
        playerMidiNotes = generateHand(
            lowMidi = PLAYER_PIANO_LOW_MIDI,
            highMidi = PLAYER_PIANO_HIGH_MIDI,
            startingMidi = previousPlayerMidi ?: 67,
            bassHand = false
        )
        generation++
    }

    private fun randomInitialPlan(): HarmonicPlan {
        val initialScale = if (random.nextBoolean()) {
            PianoScalePreset.MAJOR
        } else {
            PianoScalePreset.NATURAL_MINOR
        }
        val progressions = progressionsFor(initialScale)
        return HarmonicPlan(
            tonicPitchClass = random.nextInt(12),
            scale = initialScale,
            chordDegrees = progressions[random.nextInt(progressions.size)]
        )
    }

    /**
     * Treats a phase change as a modulation rather than a fresh random song.
     * Candidate keys use common pop relationships (dominant, subdominant,
     * relative/parallel mode, and whole-step shifts). The winning group favors
     * a shared pivot tone and a consonant root movement, then randomness keeps
     * consecutive runs from following a fixed modulation cycle.
     */
    private fun relatedPlan(
        previousLastChord: Set<Int>,
        previousChordRoot: Int
    ): HarmonicPlan {
        val otherMode = if (scale == PianoScalePreset.MAJOR) {
            PianoScalePreset.NATURAL_MINOR
        } else {
            PianoScalePreset.MAJOR
        }
        val relativeOffset =
            if (scale == PianoScalePreset.MAJOR) 9 else 3
        val modulations = listOf(
            Modulation(7, scale), // dominant
            Modulation(5, scale), // subdominant
            Modulation(relativeOffset, otherMode),
            Modulation(0, otherMode), // parallel major/minor
            Modulation(2, scale),
            Modulation(10, scale)
        )
        val candidates = modulations.flatMap { modulation ->
            val nextTonic = Math.floorMod(
                tonicPitchClass + modulation.tonicOffset,
                12
            )
            progressionsFor(modulation.scale).map { progression ->
                val firstChord = triadPitchClasses(
                    degree = progression.first(),
                    tonic = nextTonic,
                    scalePreset = modulation.scale
                )
                val firstRoot = scalePitchClass(
                    degree = progression.first(),
                    tonic = nextTonic,
                    scalePreset = modulation.scale
                )
                val commonTones =
                    previousLastChord.intersect(firstChord).size
                val rootDistance = pitchClassDistance(
                    previousChordRoot,
                    firstRoot
                )
                ScoredPlan(
                    plan = HarmonicPlan(
                        tonicPitchClass = nextTonic,
                        scale = modulation.scale,
                        chordDegrees = progression
                    ),
                    commonTones = commonTones,
                    score = commonTones * 20 +
                        rootMotionScore(rootDistance) +
                        if (modulation.scale != scale) 2 else 0
                )
            }
        }
        val pivotCandidates = candidates.filter { it.commonTones > 0 }
            .ifEmpty { candidates }
        val bestScore = pivotCandidates.maxOf { it.score }
        val expressiveTopGroup = pivotCandidates.filter {
            it.score >= bestScore - 3
        }
        return expressiveTopGroup[
            random.nextInt(expressiveTopGroup.size)
        ].plan
    }

    private fun generateHand(
        lowMidi: Int,
        highMidi: Int,
        startingMidi: Int,
        bassHand: Boolean,
        connectFromPreviousPhase: Boolean = false
    ): IntArray {
        var previous = startingMidi.coerceIn(lowMidi, highMidi)
        return IntArray(stepCount) { step ->
            val chordPosition = chordPositionForStep(step)
            val chord = chordTones[chordPosition]
            val chordStart = chordPosition * stepCount / chordTones.size
            val chordRoot = scalePitchClass(chordDegrees[chordPosition])

            val allCandidates = (lowMidi..highMidi).filter { midi ->
                val pitchClass = Math.floorMod(midi, 12)
                if (
                    bassHand &&
                    step == chordStart &&
                    !(step == 0 && connectFromPreviousPhase)
                ) {
                    pitchClass == chordRoot
                } else {
                    pitchClass in chord
                }
            }
            val compactLimit = if (bassHand) 7 else 5
            val compact = allCandidates.filter { abs(it - previous) <= compactLimit }
            val candidates = when {
                compact.isNotEmpty() -> compact
                allCandidates.isNotEmpty() -> allCandidates
                else -> (lowMidi..highMidi).toList()
            }
            val nonRepeating = candidates.filter { it != previous }
            val pool = nonRepeating.ifEmpty { candidates }
            val nearestDistance = pool.minOf { abs(it - previous) }
            val nearby = pool.filter { abs(it - previous) <= nearestDistance + 2 }
            val selected = nearby[random.nextInt(nearby.size)]
            previous = selected
            selected
        }
    }

    private fun chordPositionForStep(step: Int): Int =
        (step * chordTones.size / stepCount).coerceAtMost(chordTones.lastIndex)

    private fun triadPitchClasses(degree: Int): Set<Int> = setOf(
        scalePitchClass(degree),
        scalePitchClass(degree + 2),
        scalePitchClass(degree + 4)
    )

    private fun triadPitchClasses(
        degree: Int,
        tonic: Int,
        scalePreset: PianoScalePreset
    ): Set<Int> = setOf(
        scalePitchClass(degree, tonic, scalePreset),
        scalePitchClass(degree + 2, tonic, scalePreset),
        scalePitchClass(degree + 4, tonic, scalePreset)
    )

    private fun scalePitchClass(degree: Int): Int =
        scalePitchClass(degree, tonicPitchClass, scale)

    private fun scalePitchClass(
        degree: Int,
        tonic: Int,
        scalePreset: PianoScalePreset
    ): Int {
        val intervals = scalePreset.intervals.sorted()
        val octave = Math.floorDiv(degree, intervals.size)
        val interval = intervals[Math.floorMod(degree, intervals.size)] + octave * 12
        return Math.floorMod(tonic + interval, 12)
    }

    private fun normalizedStep(stepIndex: Int): Int =
        Math.floorMod(stepIndex, stepCount)

    private fun progressionsFor(
        scalePreset: PianoScalePreset
    ): List<IntArray> =
        if (scalePreset == PianoScalePreset.MAJOR) {
            majorProgressions
        } else {
            minorProgressions
        }

    private fun pitchClassDistance(first: Int, second: Int): Int {
        val clockwise = Math.floorMod(second - first, 12)
        return minOf(clockwise, 12 - clockwise)
    }

    private fun rootMotionScore(distance: Int): Int = when (distance) {
        0 -> 7
        5 -> 8
        2 -> 5
        3, 4 -> 3
        1 -> 0
        else -> -8
    }

    private data class Modulation(
        val tonicOffset: Int,
        val scale: PianoScalePreset
    )

    private data class HarmonicPlan(
        val tonicPitchClass: Int,
        val scale: PianoScalePreset,
        val chordDegrees: IntArray
    )

    private data class ScoredPlan(
        val plan: HarmonicPlan,
        val commonTones: Int,
        val score: Int
    )
}

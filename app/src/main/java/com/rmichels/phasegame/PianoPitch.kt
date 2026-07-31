package com.rmichels.phasegame

internal const val PIANO_HAND_PITCH_COUNT = 24
internal const val BASE_PIANO_LOW_MIDI = 36 // C2
internal const val PLAYER_PIANO_LOW_MIDI = 60 // C4
internal const val BASE_PIANO_HIGH_MIDI =
    BASE_PIANO_LOW_MIDI + PIANO_HAND_PITCH_COUNT - 1 // B3
internal const val PLAYER_PIANO_HIGH_MIDI =
    PLAYER_PIANO_LOW_MIDI + PIANO_HAND_PITCH_COUNT - 1 // B5

internal enum class PianoScalePreset(
    val displayName: String,
    val intervals: Set<Int>
) {
    CHROMATIC("Chromatic", (0..11).toSet()),
    MAJOR("Major", setOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR("Natural Minor", setOf(0, 2, 3, 5, 7, 8, 10)),
    MAJOR_PENTATONIC("Major Pentatonic", setOf(0, 2, 4, 7, 9)),
    MINOR_PENTATONIC("Minor Pentatonic", setOf(0, 3, 5, 7, 10)),
    BLUES("Blues", setOf(0, 3, 5, 6, 7, 10))
}

internal val PIANO_PITCH_CLASS_LABELS = listOf(
    "C", "C#", "D", "D#", "E", "F",
    "F#", "G", "G#", "A", "A#", "B"
)

internal fun pianoNoteLabel(midiNote: Int): String {
    require(midiNote in 0..127)
    val pitchClass = Math.floorMod(midiNote, 12)
    val octave = midiNote / 12 - 1
    return "${PIANO_PITCH_CLASS_LABELS[pitchClass]}$octave"
}

internal fun pitchIndicesForScale(
    lowMidi: Int,
    rootPitchClass: Int,
    scale: PianoScalePreset
): List<Int> {
    require(lowMidi in 0..127)
    require(rootPitchClass in 0..11)
    return (0 until PIANO_HAND_PITCH_COUNT).filter { pitchIndex ->
        val pitchClass = Math.floorMod(lowMidi + pitchIndex, 12)
        Math.floorMod(pitchClass - rootPitchClass, 12) in scale.intervals
    }
}


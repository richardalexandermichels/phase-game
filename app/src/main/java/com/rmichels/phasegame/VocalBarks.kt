package com.rmichels.phasegame

/**
 * Named phrases available to the game. Add new phrase arrangements to
 * [VocalBarkCatalog]; the audio renderer does not need phrase-specific code.
 */
internal enum class VocalBark {
    BEAT_PHASER,
    ERROR,
    SEQUENCE_COMPLETE
}

internal data class SpeechFormants(
    val firstHz: Double,
    val secondHz: Double,
    val thirdHz: Double
)

internal enum class FricativeKind {
    F,
    S,
    Z
}

internal enum class StopKind {
    B,
    K,
    P,
    T
}

internal sealed interface AllophoneModel {
    data class Voiced(
        val from: SpeechFormants,
        val to: SpeechFormants = from
    ) : AllophoneModel

    data class Fricative(val kind: FricativeKind) : AllophoneModel
    data class Stop(val kind: StopKind) : AllophoneModel
    data class Nasal(val formants: SpeechFormants) : AllophoneModel

    data class Pause(val silence: Boolean = true) : AllophoneModel
}

/**
 * Reusable American-English speech sounds. Bark definitions reference these
 * symbols rather than duplicating synthesis parameters.
 */
internal enum class SpeechAllophone(val model: AllophoneModel) {
    B(AllophoneModel.Stop(StopKind.B)),
    EH(AllophoneModel.Voiced(SpeechFormants(530.0, 1_840.0, 2_480.0))),
    ER(AllophoneModel.Voiced(SpeechFormants(490.0, 1_350.0, 1_690.0))),
    EY(
        AllophoneModel.Voiced(
            SpeechFormants(660.0, 1_720.0, 2_410.0),
            SpeechFormants(310.0, 2_020.0, 2_960.0)
        )
    ),
    F(AllophoneModel.Fricative(FricativeKind.F)),
    IH(AllophoneModel.Voiced(SpeechFormants(390.0, 1_990.0, 2_550.0))),
    IY(AllophoneModel.Voiced(SpeechFormants(270.0, 2_290.0, 3_010.0))),
    K(AllophoneModel.Stop(StopKind.K)),
    L(AllophoneModel.Voiced(SpeechFormants(400.0, 1_100.0, 2_400.0))),
    M(AllophoneModel.Nasal(SpeechFormants(250.0, 1_000.0, 2_100.0))),
    N(AllophoneModel.Nasal(SpeechFormants(280.0, 1_700.0, 2_600.0))),
    P(AllophoneModel.Stop(StopKind.P)),
    S(AllophoneModel.Fricative(FricativeKind.S)),
    T(AllophoneModel.Stop(StopKind.T)),
    W(
        AllophoneModel.Voiced(
            SpeechFormants(300.0, 800.0, 2_200.0),
            SpeechFormants(530.0, 1_840.0, 2_480.0)
        )
    ),
    Z(AllophoneModel.Fricative(FricativeKind.Z)),
    AX(AllophoneModel.Voiced(SpeechFormants(500.0, 1_500.0, 2_500.0))),
    PAUSE(AllophoneModel.Pause())
}

internal data class BarkAllophone(
    val sound: SpeechAllophone,
    // Duration is musical, expressed as a fraction of one beat.
    val beats: Double,
    val emphasis: Double = 1.0,
    val pitchSemitones: Double = 0.0
)

internal data class VocalBarkDefinition(
    val basePitchHz: Double,
    val tempoBpm: Double,
    val speed: Double = 1.0,
    val emphasis: Double = 1.0,
    val sequence: List<BarkAllophone>
)

/**
 * Manually curated pronunciations and performances. Phrase-wide pitch, speed,
 * emphasis, and tempo can be tuned here independently of the synthesizer.
 */
internal object VocalBarkCatalog {
    fun definitionFor(bark: VocalBark): VocalBarkDefinition = when (bark) {
        VocalBark.BEAT_PHASER -> VocalBarkDefinition(
            basePitchHz = 132.0,
            tempoBpm = 150.0,
            sequence = listOf(
                BarkAllophone(SpeechAllophone.B, 0.14),
                BarkAllophone(SpeechAllophone.IY, 0.52, emphasis = 1.08),
                BarkAllophone(SpeechAllophone.T, 0.14),
                BarkAllophone(SpeechAllophone.PAUSE, 0.21),
                BarkAllophone(SpeechAllophone.F, 0.26),
                BarkAllophone(SpeechAllophone.EY, 0.52, emphasis = 1.06),
                BarkAllophone(SpeechAllophone.Z, 0.24),
                BarkAllophone(SpeechAllophone.ER, 0.48)
            )
        )

        VocalBark.ERROR -> VocalBarkDefinition(
            basePitchHz = 118.0,
            tempoBpm = 138.0,
            emphasis = 1.08,
            sequence = listOf(
                BarkAllophone(SpeechAllophone.EH, 0.46, emphasis = 1.15),
                BarkAllophone(SpeechAllophone.ER, 0.30),
                BarkAllophone(SpeechAllophone.AX, 0.22, emphasis = 0.82),
                BarkAllophone(SpeechAllophone.ER, 0.44, pitchSemitones = -2.0)
            )
        )

        VocalBark.SEQUENCE_COMPLETE -> VocalBarkDefinition(
            basePitchHz = 142.0,
            tempoBpm = 164.0,
            speed = 1.04,
            sequence = listOf(
                BarkAllophone(SpeechAllophone.S, 0.20),
                BarkAllophone(SpeechAllophone.IY, 0.34),
                BarkAllophone(SpeechAllophone.K, 0.13),
                BarkAllophone(SpeechAllophone.W, 0.18),
                BarkAllophone(SpeechAllophone.EH, 0.30, emphasis = 1.08),
                BarkAllophone(SpeechAllophone.N, 0.18),
                BarkAllophone(SpeechAllophone.S, 0.18),
                BarkAllophone(SpeechAllophone.PAUSE, 0.20),
                BarkAllophone(SpeechAllophone.K, 0.13),
                BarkAllophone(SpeechAllophone.AX, 0.20, emphasis = 0.80),
                BarkAllophone(SpeechAllophone.M, 0.18),
                BarkAllophone(SpeechAllophone.P, 0.13),
                BarkAllophone(SpeechAllophone.L, 0.16),
                BarkAllophone(SpeechAllophone.IY, 0.34, emphasis = 1.10),
                BarkAllophone(SpeechAllophone.T, 0.14)
            )
        )
    }
}

package com.rmichels.phasegame


import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class PolygonVertex(
    val x: Float,
    val y: Float
)

internal fun regularPolygonVertices(
    sideCount: Int,
    rotationRadians: Float
): List<PolygonVertex> {
    require(sideCount >= 3)

    val anglePerSide =
        (2.0 * Math.PI / sideCount).toFloat()

    return List(sideCount) { index ->
        val angle =
            rotationRadians + index * anglePerSide

        PolygonVertex(
            x = kotlin.math.cos(angle),
            y = kotlin.math.sin(angle)
        )
    }
}

@Composable
internal fun RhythmPolygon(
    rhythm: List<Boolean>,
    queuedPatterns: List<QueuedPatternBar>,
    phaseVisualThemes: List<PhaseVisualTheme>,
    barProgress: Float,
    isGameplayActive: Boolean,
    introIndicatorProgress: Float?,
    phaseTransitionRecoil: Float,
    repeatCurrentPatternNextBar: Boolean,
    modifier: Modifier = Modifier,
    centerOverride: Offset? = null,
    polygonRadius: Dp = 120.dp,
    indicatorTargetRadius: Dp = 48.dp,
    indicatorThickness: Dp = 24.dp,
    indicatorTravelBars: Float = 4f,
    playedColor: Color = MaterialTheme.colorScheme.primary,
    targetColor: Color = MaterialTheme.colorScheme.error,
    strokeWidth: Dp = 8.dp
){
    require(rhythm.size >= 3)
    require(indicatorTravelBars > 0f)
    require(phaseVisualThemes.isNotEmpty())

    val visualQueuedPatterns =
        if (
            repeatCurrentPatternNextBar &&
            queuedPatterns.isNotEmpty()
        ) {
            listOf(queuedPatterns.first()) + queuedPatterns
        } else {
            queuedPatterns
        }

    val currentPhaseIndex =
        queuedPatterns.firstOrNull()?.phaseIndex ?: 0

    val currentPhaseTheme = phaseVisualThemes[
        Math.floorMod(currentPhaseIndex, phaseVisualThemes.size)
    ]

    val upcomingPhase = upcomingPhaseVisual(
        queuedPatterns = visualQueuedPatterns,
        barProgress = barProgress
    )?.let { phase ->
        phase.copy(
            transitionProgress = (
                    phase.transitionProgress -
                            phaseTransitionRecoil
                    ).coerceIn(0f, 1f)
        )
    }

    val upcomingPhaseTheme = upcomingPhase?.let { phase ->
        phaseVisualThemes[
            Math.floorMod(
                phase.phaseIndex,
                phaseVisualThemes.size
            )
        ]
    }

    Canvas(modifier = modifier) {
        val anglePerSide =
            (2.0 * Math.PI / rhythm.size).toFloat()

        val startingRotation =
            (-Math.PI / 2.0).toFloat() - anglePerSide / 2f

        val rotation = startingRotation
//            startingRotation -
//                    barProgress.coerceIn(0f, 1f) *
//                    (2.0 * Math.PI).toFloat()

        val vertices = regularPolygonVertices(
            sideCount = rhythm.size,
            rotationRadians = rotation
        )
        val radius =
            (
                    polygonRadius.toPx() -
                            strokeWidth.toPx() / 2f
                    ).coerceAtLeast(0f)

        val targetDistance =
            indicatorTargetRadius.toPx().coerceIn(0f, radius)

        val currentStepPosition =
            barProgress.coerceIn(0f, 0.999999f) * rhythm.size

        val indicatorTravelSteps =
            rhythm.size * indicatorTravelBars

        val indicatorTimelineActive =
            isGameplayActive ||
                    introIndicatorProgress != null

        val indicatorCurrentStepPosition =
            when {
                isGameplayActive -> currentStepPosition

                introIndicatorProgress != null ->
                    -indicatorTravelSteps *
                            (1f - introIndicatorProgress)

                else -> 0f
            }

        val queuedIndicators =
            if (indicatorTimelineActive) {
                futureRhythmIndicators(
                    queuedRhythms = visualQueuedPatterns
                        .map { queuedPattern -> queuedPattern.rhythm }
                        .ifEmpty { listOf(rhythm) },
                    currentStepPosition =
                        indicatorCurrentStepPosition
                )
            } else {
                emptyList()
            }

        val center = centerOverride ?: Offset(
            x = size.width / 2f,
            y = size.height / 2f
        )

        val farDistance =
            kotlin.math.hypot(size.width, size.height) * 2f

        val indicatorSpawnDistance =
            kotlin.math.hypot(size.width, size.height)

        val indicatorDistancePerStep =
            (
                    indicatorSpawnDistance - targetDistance
                    ) / indicatorTravelSteps

        fun pointAtDistanceFromCenter(
            point: Offset,
            distanceFromCenter: Float
        ): Offset {
            val directionX = point.x - center.x
            val directionY = point.y - center.y
            val directionLength = kotlin.math.hypot(
                directionX,
                directionY
            )

            return Offset(
                x = center.x +
                        directionX / directionLength * distanceFromCenter,
                y = center.y +
                        directionY / directionLength * distanceFromCenter
            )
        }

        val screenVertices = vertices.map { vertex ->
            Offset(
                x = center.x + vertex.x * radius,
                y = center.y + vertex.y * radius
            )
        }

        val upcomingScreenVertices = upcomingPhase?.let { phase ->
            val rotationOffset =
                -anglePerSide * (1f - phase.transitionProgress)

            regularPolygonVertices(
                sideCount = rhythm.size,
                rotationRadians = rotation + rotationOffset
            ).map { vertex ->
                Offset(
                    x = center.x + vertex.x * radius,
                    y = center.y + vertex.y * radius
                )
            }
        }

        val sideInsetFraction = 0.1f
        val ghostSideInsetFraction = 0.42f
        require(sideInsetFraction in 0f..0.5f)
        require(ghostSideInsetFraction in 0f..0.5f)

        rhythm.forEachIndexed { index, shouldDraw ->
            val nextIndex = (index + 1) % screenVertices.size
            val sideStart = screenVertices[index]
            val sideStartX = sideStart.x
            val sideStartY = sideStart.y
            val sideEnd = screenVertices[nextIndex]
            val sideEndX = sideEnd.x
            val sideEndY = sideEnd.y
            val sideDeltaX = sideEndX - sideStartX
            val sideDeltaY = sideEndY - sideStartY

            if (shouldDraw) {
                val shortenedStart = Offset(
                    x = sideStartX + sideInsetFraction * sideDeltaX,
                    y = sideStartY + sideInsetFraction * sideDeltaY
                )

                val shortenedEnd = Offset(
                    x = sideEndX - sideInsetFraction * sideDeltaX,
                    y = sideEndY - sideInsetFraction * sideDeltaY
                )

                val extendedStart =  pointAtDistanceFromCenter(shortenedStart,farDistance)
                val extendedEnd = pointAtDistanceFromCenter(shortenedEnd,farDistance)

                val wedgePath = Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(extendedStart.x, extendedStart.y)
                    lineTo(extendedEnd.x, extendedEnd.y)
                    close()
                }

                drawPath(
                    path = wedgePath,
                    color = currentPhaseTheme.triangleColor
                )

                val thresholdStart =
                    pointAtDistanceFromCenter(
                        shortenedStart,
                        targetDistance
                    )
                val thresholdEnd =
                    pointAtDistanceFromCenter(
                        shortenedEnd,
                        targetDistance
                    )
                drawLine(
                    color = targetColor.copy(alpha = 0.35f),
                    start = thresholdStart,
                    end = thresholdEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Square
                )
            } else {
                val ghostShortenedStart = Offset(
                    x = sideStartX +
                            ghostSideInsetFraction * sideDeltaX,
                    y = sideStartY +
                            ghostSideInsetFraction * sideDeltaY
                )
                val ghostShortenedEnd = Offset(
                    x = sideEndX -
                            ghostSideInsetFraction * sideDeltaX,
                    y = sideEndY -
                            ghostSideInsetFraction * sideDeltaY
                )

                val ghostExtendedStart =
                    pointAtDistanceFromCenter(ghostShortenedStart,farDistance)
                val ghostExtendedEnd =
                    pointAtDistanceFromCenter(ghostShortenedEnd,farDistance)

                val ghostWedgePath = Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(
                        ghostExtendedStart.x,
                        ghostExtendedStart.y
                    )
                    lineTo(
                        ghostExtendedEnd.x,
                        ghostExtendedEnd.y
                    )
                    close()
                }

                drawPath(
                    path = ghostWedgePath,
                    color = currentPhaseTheme.triangleColor.copy(alpha = 0.25f)
                )
            }
        }
        upcomingPhase?.let { phase ->
            val phaseTheme = upcomingPhaseTheme ?: return@let
            val phaseVertices = upcomingScreenVertices ?: return@let

            phase.rhythm.forEachIndexed { index, isPlayed ->
                val nextIndex = (index + 1) % phaseVertices.size
                val sideStart = phaseVertices[index]
                val sideEnd = phaseVertices[nextIndex]
                val sideDeltaX = sideEnd.x - sideStart.x
                val sideDeltaY = sideEnd.y - sideStart.y

                val insetFraction = if (isPlayed) {
                    sideInsetFraction
                } else {
                    ghostSideInsetFraction
                }

                val shortenedStart = Offset(
                    x = sideStart.x + insetFraction * sideDeltaX,
                    y = sideStart.y + insetFraction * sideDeltaY
                )
                val shortenedEnd = Offset(
                    x = sideEnd.x - insetFraction * sideDeltaX,
                    y = sideEnd.y - insetFraction * sideDeltaY
                )

                val extendedStart = pointAtDistanceFromCenter(
                    shortenedStart,
                    farDistance
                )
                val extendedEnd = pointAtDistanceFromCenter(
                    shortenedEnd,
                    farDistance
                )

                val upcomingPath = Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(extendedStart.x, extendedStart.y)
                    lineTo(extendedEnd.x, extendedEnd.y)
                    close()
                }

                val layerAlpha = if (isPlayed) {
                    phase.transitionProgress
                } else {
                    phase.transitionProgress * 0.25f
                }

                drawPath(
                    path = upcomingPath,
                    color = phaseTheme.triangleColor.copy(
                        alpha = layerAlpha
                    )
                )
            }
        }
        queuedIndicators.forEach { indicator ->
            val indicatorPhaseIndex =
                visualQueuedPatterns.getOrNull(
                    indicator.queuedBarOffset
                )
                    ?.phaseIndex
                    ?: currentPhaseIndex

            val indicatorTheme = phaseVisualThemes[
                Math.floorMod(
                    indicatorPhaseIndex,
                    phaseVisualThemes.size
                )
            ]
            val isUpcomingPhase =
                indicatorPhaseIndex == upcomingPhase?.phaseIndex

            val indicatorVertices =
                if (isUpcomingPhase) {
                    upcomingScreenVertices ?: screenVertices
                } else {
                    screenVertices
                }

            val indicatorAlpha =
                if (isUpcomingPhase) {
                    upcomingPhase?.transitionProgress ?: 1f
                } else {
                    1f
                }

            val index = indicator.stepIndex
            val nextIndex =
                (index + 1) % indicatorVertices.size

            val sideStart = indicatorVertices[index]
            val sideEnd = indicatorVertices[nextIndex]
            val sideDeltaX = sideEnd.x - sideStart.x
            val sideDeltaY = sideEnd.y - sideStart.y

            val shortenedStart = Offset(
                x = sideStart.x +
                        sideInsetFraction * sideDeltaX,
                y = sideStart.y +
                        sideInsetFraction * sideDeltaY
            )
            val shortenedEnd = Offset(
                x = sideEnd.x -
                        sideInsetFraction * sideDeltaX,
                y = sideEnd.y -
                        sideInsetFraction * sideDeltaY
            )

            val indicatorDistance =
                targetDistance +
                        indicator.stepsUntilHit *
                        indicatorDistancePerStep

            if (indicatorDistance <= indicatorSpawnDistance) {
                val halfIndicatorThickness =
                    indicatorThickness.toPx() / 2f

                val innerDistance =
                    (indicatorDistance - halfIndicatorThickness)
                        .coerceAtLeast(0f)
                val outerDistance =
                    indicatorDistance + halfIndicatorThickness

                val innerStart =
                    pointAtDistanceFromCenter(
                        shortenedStart,
                        innerDistance
                    )
                val innerEnd =
                    pointAtDistanceFromCenter(
                        shortenedEnd,
                        innerDistance
                    )
                val outerStart =
                    pointAtDistanceFromCenter(
                        shortenedStart,
                        outerDistance
                    )
                val outerEnd =
                    pointAtDistanceFromCenter(
                        shortenedEnd,
                        outerDistance
                    )

                val indicatorPath = Path().apply {
                    moveTo(innerStart.x, innerStart.y)
                    lineTo(outerStart.x, outerStart.y)
                    lineTo(outerEnd.x, outerEnd.y)
                    lineTo(innerEnd.x, innerEnd.y)
                    close()
                }

                drawPath(
                    path = indicatorPath,
                    color = indicatorTheme.indicatorColor.copy(
                        alpha = indicatorAlpha
                    )
                )
            }
        }
    }
}

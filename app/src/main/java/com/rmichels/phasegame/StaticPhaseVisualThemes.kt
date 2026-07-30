package com.rmichels.phasegame

import androidx.compose.ui.graphics.Color

/**
 * Set this false to the existing randomized phase colors.
 */
internal const val USE_STATIC_PHASE_VISUAL_THEMES = true

/**
 * Paste the color picker's "Copy full palette for PhaseGame" export over the
 * listOf(...) expression below. Palette order is phase order.
 *
 * Keep at least as many entries as the largest step count you use. PhaseGame
 * supports up to 16 steps and takes the first phascount entries.
 */
internal val STATIC_PHASE_VISUAL_THEMES: List<PhaseVisualTheme> =
    listOf(
        PhaseVisualTheme(
            triangleColor = Color.hsv(0.0f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(180.0f, 0.95f, 1f),
            targetColor = Color.hsv(180.0f, 0.95f, 1f),
            clockHandColor = Color.hsv(90.0f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(337.5f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(157.5f, 0.95f, 1f),
            targetColor = Color.hsv(157.5f, 0.95f, 1f),
            clockHandColor = Color.hsv(67.5f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(315.0f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(135.0f, 0.95f, 1f),
            targetColor = Color.hsv(135.0f, 0.95f, 1f),
            clockHandColor = Color.hsv(45.0f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(135.0f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(315.0f, 0.95f, 1f),
            targetColor = Color.hsv(315.0f, 0.95f, 1f),
            clockHandColor = Color.hsv(225.0f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(90.0f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(270.0f, 0.95f, 1f),
            targetColor = Color.hsv(270.0f, 0.95f, 1f),
            clockHandColor = Color.hsv(180.0f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(112.5f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(292.5f, 0.95f, 1f),
            targetColor = Color.hsv(292.5f, 0.95f, 1f),
            clockHandColor = Color.hsv(202.5f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(225.0f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(45.0f, 0.95f, 1f),
            targetColor = Color.hsv(45.0f, 0.95f, 1f),
            clockHandColor = Color.hsv(315.0f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(202.5f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(22.5f, 0.95f, 1f),
            targetColor = Color.hsv(22.5f, 0.95f, 1f),
            clockHandColor = Color.hsv(292.5f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(247.5f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(67.5f, 0.95f, 1f),
            targetColor = Color.hsv(67.5f, 0.95f, 1f),
            clockHandColor = Color.hsv(337.5f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(157.5f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(337.5f, 0.95f, 1f),
            targetColor = Color.hsv(337.5f, 0.95f, 1f),
            clockHandColor = Color.hsv(247.5f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(22.5f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(202.5f, 0.95f, 1f),
            targetColor = Color.hsv(202.5f, 0.95f, 1f),
            clockHandColor = Color.hsv(112.5f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(67.5f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(247.5f, 0.95f, 1f),
            targetColor = Color.hsv(247.5f, 0.95f, 1f),
            clockHandColor = Color.hsv(157.5f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(45.0f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(225.0f, 0.95f, 1f),
            targetColor = Color.hsv(225.0f, 0.95f, 1f),
            clockHandColor = Color.hsv(135.0f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(292.5f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(112.5f, 0.95f, 1f),
            targetColor = Color.hsv(112.5f, 0.95f, 1f),
            clockHandColor = Color.hsv(22.5f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(270.0f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(90.0f, 0.95f, 1f),
            targetColor = Color.hsv(90.0f, 0.95f, 1f),
            clockHandColor = Color.hsv(0.0f, 0.75f, 1f)
        ),
        PhaseVisualTheme(
            triangleColor = Color.hsv(180.0f, 0.72f, 0.82f),
            indicatorColor = Color.hsv(0.0f, 0.95f, 1f),
            targetColor = Color.hsv(0.0f, 0.95f, 1f),
            clockHandColor = Color.hsv(270.0f, 0.75f, 1f)
        ),
    )

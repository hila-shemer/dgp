package com.dgp.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

object EditorialMotion {
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)
    const val standardDurationMs: Int = 180
    const val springStiffness: Float = 220f
    const val springDamping: Float = 0.65f
    const val rowPressFlashMs: Int = 120
    const val toastEnterMs: Int = 240
    const val toastDwellMs: Int = 2500
    const val toastExitMs: Int = 180
    const val saveFlashMs: Int = 400
    const val scrollAnimationMs: Int = 320
}

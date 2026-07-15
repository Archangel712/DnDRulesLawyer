package com.example.dnd_ruleslawyer.presentation.utils

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.example.dnd_ruleslawyer.R
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

private const val RESTING_ICON_SIZE_DP = 24
private const val LISTENING_ICON_MIN_SIZE_DP = 28
private const val LISTENING_ICON_MAX_SIZE_DP = 34
private const val ICON_PULSE_DURATION_MS = 520L

fun MaterialButton.updateVoiceListeningFeedback(isListening: Boolean) {
    isSelected = isListening
    animate().cancel()
    scaleX = 1f
    scaleY = 1f
    cancelVoiceIconAnimator()

    val backgroundColor = if (isListening) {
        R.color.voice_button_listening_background
    } else {
        R.color.voice_button_background
    }

    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, backgroundColor))
    iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.voice_button_icon))

    if (isListening) {
        startVoiceIconPulse()
    } else {
        iconSize = dpToPx(RESTING_ICON_SIZE_DP)
    }
}

private fun MaterialButton.startVoiceIconPulse() {
    val minIconSize = dpToPx(LISTENING_ICON_MIN_SIZE_DP)
    val maxIconSize = dpToPx(LISTENING_ICON_MAX_SIZE_DP)

    iconSize = minIconSize

    val animator = ValueAnimator.ofInt(minIconSize, maxIconSize).apply {
        duration = ICON_PULSE_DURATION_MS
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { animation ->
            iconSize = animation.animatedValue as Int
        }
    }

    setTag(R.id.voice_button_icon_animator, animator)
    animator.start()
}

private fun MaterialButton.cancelVoiceIconAnimator() {
    (getTag(R.id.voice_button_icon_animator) as? ValueAnimator)?.cancel()
    setTag(R.id.voice_button_icon_animator, null)
}

private fun MaterialButton.dpToPx(value: Int): Int {
    return (value * resources.displayMetrics.density).roundToInt()
}

package com.example.dnd_ruleslawyer.presentation.loading

import android.view.View
import android.widget.TextView

class LoadingOverlayController(
    private val root: View,
    private val flavorTextView: TextView,
    flavorTexts: List<String>
) {
    private val cycler = LoadingFlavorTextCycler(flavorTexts)
    private val flavorRunnable = object : Runnable {
        override fun run() {
            fadeToNextFlavorText()
            root.postDelayed(this, FLAVOR_TEXT_INTERVAL_MS)
        }
    }

    fun show() {
        if (root.visibility == View.VISIBLE) return

        flavorTextView.alpha = 1f
        flavorTextView.text = cycler.next()
        root.visibility = View.VISIBLE
        root.bringToFront()
        root.removeCallbacks(flavorRunnable)
        root.postDelayed(flavorRunnable, FLAVOR_TEXT_INTERVAL_MS)
    }

    fun hide() {
        root.removeCallbacks(flavorRunnable)
        flavorTextView.animate().cancel()
        root.visibility = View.GONE
    }

    fun destroy() {
        hide()
    }

    private fun fadeToNextFlavorText() {
        flavorTextView.animate()
            .alpha(0f)
            .setDuration(FLAVOR_TEXT_FADE_MS)
            .withEndAction {
                flavorTextView.text = cycler.next()
                flavorTextView.animate()
                    .alpha(1f)
                    .setDuration(FLAVOR_TEXT_FADE_MS)
                    .start()
            }
            .start()
    }

    companion object {
        private const val FLAVOR_TEXT_INTERVAL_MS = 3_000L
        private const val FLAVOR_TEXT_FADE_MS = 250L
    }
}

package com.example.dnd_ruleslawyer.presentation.loading

import kotlin.random.Random

class LoadingFlavorTextCycler(
    flavorTexts: List<String>,
    private val random: Random = Random.Default
) {
    private val sourceTexts = flavorTexts.filter { text -> text.isNotBlank() }.distinct()
    private var queue: MutableList<String> = mutableListOf()
    private var lastText: String? = null

    fun next(): String {
        if (sourceTexts.isEmpty()) return ""

        if (queue.isEmpty()) {
            queue = sourceTexts.shuffled(random).toMutableList()
            val previous = lastText
            if (queue.size > 1 && previous != null && queue.first() == previous) {
                val swapIndex = queue.indexOfFirst { text -> text != previous }
                if (swapIndex > 0) {
                    val replacement = queue[swapIndex]
                    queue[swapIndex] = queue[0]
                    queue[0] = replacement
                }
            }
        }

        return queue.removeAt(0).also { text -> lastText = text }
    }
}

package com.example.dnd_ruleslawyer.presentation.loading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class LoadingFlavorTextCyclerTest {
    @Test
    fun next_cyclesThroughAllTextsBeforeRepeating() {
        val texts = listOf("A", "B", "C")
        val cycler = LoadingFlavorTextCycler(texts, Random(7))

        val firstCycle = List(texts.size) { cycler.next() }
        val secondCycle = List(texts.size) { cycler.next() }

        assertEquals(texts.toSet(), firstCycle.toSet())
        assertEquals(texts.toSet(), secondCycle.toSet())
    }

    @Test
    fun next_avoidsImmediateRepeatAcrossCycles() {
        val cycler = LoadingFlavorTextCycler(listOf("A", "B"), Random(1))

        val generated = List(12) { cycler.next() }

        generated.zipWithNext().forEach { (previous, next) ->
            assertNotEquals(previous, next)
        }
    }
}

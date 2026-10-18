package com.kjt7942.grapesorter

import org.junit.Assert.*
import org.junit.Test

/** 기대값은 원본 main.py best_combination 을 직접 돌려 얻은 값이다. */
class CombinationTest {
    private fun items(vararg w: Int) = w.mapIndexed { i, v -> Item(i + 1, v) }

    @Test fun pairNearTarget() {
        val r = Combination.best(items(800, 700, 600, 500), 1400, 2, 2, 50)
        assertEquals(listOf(1, 3), r.combo!!.map { it.index })
        assertEquals(1400, r.total)
        assertEquals(1300, r.nearTotal)
    }

    @Test fun noneWithinTolerance() {
        val r = Combination.best(items(800, 700, 600), 2000, 2, 3, 50)
        assertNull(r.combo)
        assertEquals(0, r.total)
        assertEquals(1500, r.nearTotal)
    }

    @Test fun tieDifferentSizesPrefersMoreScales() {
        val r = Combination.best(items(500, 500, 500, 500), 1000, 2, 4, 100)
        assertEquals(listOf(1, 2), r.combo!!.map { it.index })
        assertEquals(1000, r.total)
        assertNull(r.nearTotal)
    }

    @Test fun belowTargetOnly() {
        val r = Combination.best(items(300, 300), 1000, 1, 2, 50)
        assertNull(r.combo)
        assertEquals(600, r.nearTotal)
    }
}

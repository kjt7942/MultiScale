package com.kjt7942.multiscale

import org.junit.Assert.*
import org.junit.Test

class SorterEngineTest {
    private var now = 0L
    private fun engine() = SorterEngine { now }.also {
        it.target = 2050; it.minComb = 3; it.maxComb = 4; it.tolerance = 50
    }

    private fun w(vararg v: Int) = IntArray(CHANNELS) { if (it < v.size) v[it] else 0 }
    private fun SorterEngine.feed(t: Long, arr: IntArray): Snapshot { now = t; return onWeights(arr) }

    private val leftover = intArrayOf(700, 700, 350, 300)

    @Test fun lockThenBoxRemovedWaitsForRefill() {
        val e = engine()
        val full = w(500, 500, 525, 525, *leftover)
        e.feed(0, full)
        assertEquals(listOf(1, 2, 3, 4), e.feed(600, full).leds)

        // 박스를 치움: 1~4번이 동시에 0g. 남은 5~8번으로 바로 조합하면 안 된다.
        val emptied = w(0, 0, 0, 0, *leftover)
        assertTrue(e.feed(700, emptied).leds.isEmpty())
        assertTrue(e.feed(800, full).leds.isEmpty())   // 막 채워짐: 아직 자리 잡기 전
        assertEquals(listOf(1, 2, 3, 4), e.feed(1400, full).leds) // 자리 잡으면 전체에서 최적 조합
    }

    @Test fun refillHoldTimesOutForSlowRefill() {
        val e = engine()
        val full = w(500, 500, 525, 525, *leftover)
        e.feed(0, full); e.feed(600, full)
        val emptied = w(0, 0, 0, 0, *leftover)
        assertTrue(e.feed(700, emptied).leds.isEmpty())
        assertTrue(e.feed(2000, emptied).leds.isEmpty())
        assertEquals(listOf(5, 6, 7, 8), e.feed(3800, emptied).leds)
    }

    @Test fun topupNeedsAllBoxTraysFirst() {
        val e = engine()
        e.toggleTopup()
        e.tolerance = 50; e.target = 2050; e.maxComb = 4
        // 보태기 저울(1,2,7,8번)이 비어 있으면 나머지만으로 목표를 채울 수 있어도 조합하지 않는다.
        val others = intArrayOf(0, 0, 500, 500, 525, 525, 0, 0, 450, 400)
        e.feed(0, others)
        assertTrue(e.feed(600, others).leds.isEmpty())

        val filled = others.copyOf().also { it[0] = 300; it[1] = 300; it[6] = 300; it[7] = 300 }
        e.feed(700, filled)
        assertTrue(e.feed(900, filled).leds.isEmpty())       // 아직 안정 전
        assertEquals(listOf(9, 10), e.feed(1300, filled).leds) // 목표 2050-1200=850
    }
}

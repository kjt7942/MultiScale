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

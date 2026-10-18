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

    @Test fun boxRemovedRequestsRefillAndWaits() {
        val e = engine()
        val full = w(500, 500, 525, 525, *leftover)
        e.feed(0, full)
        assertEquals(listOf(1, 2, 3, 4), e.feed(600, full).leds)
        assertNull(e.takeRefill())

        // 조합 저울이 모두 0g: 새로 채울 저울을 알려주고, 채워질 때까지 남은 저울로 조합하지 않는다.
        val emptied = w(0, 0, 0, 0, *leftover)
        assertTrue(e.feed(700, emptied).leds.isEmpty())
        assertEquals(setOf(0, 1, 2, 3), e.takeRefill())
        assertNull(e.takeRefill())
        assertTrue(e.feed(800, full).leds.isEmpty())              // 막 채워짐: 자리 잡기 전
        assertEquals(listOf(1, 2, 3, 4), e.feed(1400, full).leds) // 다 채워진 뒤 최적 조합
    }

    @Test fun refillHoldTimesOut() {
        val e = engine()
        val full = w(500, 500, 525, 525, *leftover)
        e.feed(0, full); e.feed(600, full)
        val emptied = w(0, 0, 0, 0, *leftover)
        assertTrue(e.feed(700, emptied).leds.isEmpty())
        assertTrue(e.feed(3000, emptied).leds.isEmpty())
        assertEquals(listOf(5, 6, 7, 8), e.feed(4800, emptied).leds)
    }

    @Test fun topupBoxRefillsEmptyBoxTraysToo() {
        val e = engine()
        e.toggleTopup()
        e.target = 2050; e.maxComb = 4
        val base = intArrayOf(300, 300, 500, 500, 525, 525, 300, 300, 450, 400)
        e.feed(0, base)
        assertEquals(listOf(9, 10), e.feed(600, base).leds)
        // 조합 저울(9,10)과 보태기 저울(1,7)을 비운 경우: 전부 새로 채워야 한다.
        val after = base.copyOf().also { it[8] = 0; it[9] = 0; it[0] = 0; it[6] = 0 }
        assertTrue(e.feed(700, after).leds.isEmpty())
        assertEquals(setOf(8, 9, 0, 6), e.takeRefill())
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

    @Test fun customChannelCountAndTopupTrays() {
        val e = SorterEngine(6) { now }.also {
            it.target = 1500; it.minComb = 2; it.maxComb = 3; it.tolerance = 50
            it.topupTrays = setOf(0)
        }
        val arr = intArrayOf(400, 500, 500, 500, 450, 700)
        e.feed(0, arr)
        val s = e.feed(600, arr)
        assertEquals(6, s.weights.size)
        assertEquals(6, s.roles.size)
        assertTrue(s.leds.all { it in 1..6 })
        e.toggleTopup()
        e.feed(700, arr)
        val t = e.feed(1300, arr)
        assertEquals(Role.TOPUP, t.roles[0])
        assertTrue(1 !in t.leds)
    }
}

package com.kjt7942.multiscale

import org.junit.Assert.*
import org.junit.Test

class LayoutTest {
    @Test fun hardwareLayoutPutsOneToSixOnBottomRow() {
        val l = LayoutConfig()
        assertEquals(2, l.rows)
        assertEquals(listOf(6, 7, 8, 9, 10, 11), l.cells.subList(0, 6))   // 윗줄 7~12번
        assertEquals(listOf(0, 1, 2, 3, 4, 5), l.cells.subList(6, 12))    // 아랫줄 1~6번
    }

    @Test fun topFirstIsPlainReadingOrder() {
        val l = LayoutConfig().withBottomFirst(false)
        assertEquals((0..11).toList(), l.cells)
    }

    @Test fun changingChannelsRebuildsGridWithEmptyCells() {
        val l = LayoutConfig().withChannels(10).withCols(4)
        assertEquals(10, l.channels)
        assertEquals(3, l.rows)
        assertEquals(10, l.cells.count { it >= 0 })
        assertEquals(2, l.cells.count { it < 0 })
        assertTrue(l.topup.all { it < 10 })
    }

    @Test fun channelsAreClamped() {
        assertEquals(MAX_TRAYS, LayoutConfig().withChannels(99).channels)
        assertEquals(MIN_TRAYS, LayoutConfig().withChannels(0).channels)
    }

    @Test fun swapMovesTraysIncludingIntoEmptyCell() {
        val l = LayoutConfig().withChannels(5).withCols(3)  // 2행 x 3열, 빈 칸 1개
        val empty = l.cells.indexOf(-1)
        val a = l.cells.indexOf(0)
        val s = l.swap(a, empty)
        assertEquals(-1, s.cells[a])
        assertEquals(0, s.cells[empty])
        assertEquals((0 until 5).toSet(), s.cells.filter { it >= 0 }.toSet())
    }

    @Test fun brokenCellsFallBackToDefault() {
        val broken = LayoutConfig(channels = 12, cols = 6, cells = List(12) { 0 })
        assertEquals(LayoutConfig().cells, broken.normalized().cells)
    }

    @Test fun topupToggle() {
        val l = LayoutConfig().toggleTopup(2)
        assertTrue(2 in l.topup)
        assertFalse(2 in l.toggleTopup(2).topup)
    }
}

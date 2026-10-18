package com.kjt7942.multiscale

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    private val ok = "<" + (1..12).joinToString(", ") { "${it * 100}.4" } + ">"

    @Test fun parsesDecimalWeights() {
        val e = PacketParser().parseLine(ok)
        assertEquals(1, e.size)
        val w = (e[0] as Event.Weights).values
        assertEquals(100, w[0]); assertEquals(1200, w[11])
    }

    @Test fun wrongChannelCountIgnored() {
        assertTrue(PacketParser().parseLine("<1,2,3>").none { it is Event.Weights })
    }

    @Test fun customChannelCountParses() {
        val e = PacketParser(8).parseLine("<1,2,3,4,5,6,7,8>")
        assertEquals(8, (e[0] as Event.Weights).values.size)
    }

    @Test fun mismatchedCountIsReported() {
        val e = PacketParser(12).parseLine("<1,2,3,4,5,6>")
        assertEquals(6, (e.single() as Event.ChannelCount).count)
    }

    @Test fun shortErrIsHeldThenBecomesMinusOne() {
        val p = PacketParser()
        p.parseLine(ok)
        val bad = "<ERR, " + (2..12).joinToString(", ") { "${it * 100}" } + ">"
        repeat(ERR_HOLD_PACKETS) {
            assertEquals(100, (p.parseLine(bad)[0] as Event.Weights).values[0])
        }
        assertEquals(-1, (p.parseLine(bad)[0] as Event.Weights).values[0])
    }

    @Test fun outOfRangeIsError() {
        val p = PacketParser()
        val s = "<99999, " + (2..12).joinToString(", ") { "10" } + ">"
        assertEquals(-1, (p.parseLine(s)[0] as Event.Weights).values[0])
    }

    @Test fun tareAndVersion() {
        val p = PacketParser()
        assertTrue(p.parseLine("[SYSTEM] 영점 조절 완료")[0] is Event.TareDone)
        assertEquals("1.2", (p.parseLine("[VER] 1.2")[0] as Event.Version).text)
        val t = p.parseLine("[TARE] " + (1..12).joinToString(",") { "$it" })[0] as Event.TareOffsets
        assertEquals(12, t.values[11])
    }

    @Test fun lineAssemblerSplitsChunks() {
        val a = LineAssembler()
        val b1 = "<1,2".toByteArray(); val b2 = ">\n<3>\n".toByteArray()
        assertTrue(a.feed(b1, b1.size).isEmpty())
        assertEquals(listOf("<1,2>", "<3>"), a.feed(b2, b2.size).map { it.trim() })
    }
}

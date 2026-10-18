package com.kjt7942.grapesorter

const val CHANNELS = 12
const val PLAUSIBLE_MIN_G = -50      // 영점 뒤 이보다 낮게 나올 수 없다
const val PLAUSIBLE_MAX_G = 25000    // 20kg 로드셀도 넘을 수 없는 값
const val ERR_HOLD_PACKETS = 5       // 짧은 ERR 은 직전 정상값을 유지

sealed class Event {
    class Weights(val values: IntArray) : Event()
    class TareOffsets(val values: IntArray) : Event()
    class Version(val text: String) : Event()
    object TareDone : Event()
}

/**
 * 바이트 조각을 줄 단위로 모은다. 펌웨어의 모든 출력은 줄바꿈으로 끝나므로
 * 완성된 줄만 UTF-8 로 해석하면 조각 경계에 걸린 한글이 깨지지 않는다.
 */
class LineAssembler {
    private var buf = ByteArray(0)

    fun feed(chunk: ByteArray, len: Int): List<String> {
        buf += chunk.copyOf(len)
        val lines = ArrayList<String>()
        var start = 0
        for (i in buf.indices) {
            if (buf[i] == '\n'.code.toByte()) {
                lines.add(String(buf, start, i - start, Charsets.UTF_8))
                start = i + 1
            }
        }
        buf = if (start >= buf.size) ByteArray(0) else buf.copyOfRange(start, buf.size)
        if (buf.size > 4096) buf = ByteArray(0) // 쓰레기 데이터가 쌓이지 않게
        return lines
    }
}

/** 아두이노가 보내는 한 줄을 이벤트로 바꾼다. 형식은 기존 main.py 의 SerialThread 와 같다. */
class PacketParser(private val channels: Int = CHANNELS) {
    private val lastGood = arrayOfNulls<Int>(channels)
    private val errStreak = IntArray(channels)

    fun resetErrHold() {
        for (i in 0 until channels) {
            lastGood[i] = null
            errStreak[i] = 0
        }
    }

    fun parseLine(raw: String): List<Event> {
        val line = raw.trim()
        val out = ArrayList<Event>()

        val tareIdx = line.indexOf("[TARE]")
        if (tareIdx >= 0) {
            val nums = line.substring(tareIdx + 6).split(",").map { it.trim().toIntOrNull() }
            if (nums.size == channels && nums.all { it != null }) {
                out.add(Event.TareOffsets(IntArray(channels) { nums[it]!! }))
            }
            return out
        }
        if (line.contains("[SYSTEM] 영점 조절 완료")) {
            resetErrHold() // 영점 전 무게로 ERR 을 메우면 안 된다
            out.add(Event.TareDone)
            return out
        }
        val verIdx = line.indexOf("[VER]")
        if (verIdx >= 0) {
            out.add(Event.Version(line.substring(verIdx + 5).trim()))
            return out
        }
        val s = line.indexOf('<')
        if (s >= 0) {
            val e = line.indexOf('>', s)
            if (e > s) {
                val w = parseWeights(line.substring(s + 1, e))
                if (w != null) out.add(Event.Weights(w))
            }
        }
        return out
    }

    fun parseWeights(packet: String): IntArray? {
        val parts = packet.split(",")
        if (parts.size != channels) return null
        return IntArray(channels) { i ->
            val d = parts[i].trim().toDoubleOrNull() // "ERR" 이나 깨진 값은 null
            var value: Int? = null
            if (d != null && !d.isNaN() && !d.isInfinite()) {
                val v = Math.round(d).toInt()
                if (v in PLAUSIBLE_MIN_G..PLAUSIBLE_MAX_G) value = v
            }
            holdTransientErr(i, value)
        }
    }

    /**
     * HX711 은 읽는 순간 준비가 안 돼 가끔 ERR 이 섞여 온다. 그대로 -1 을 넘기면
     * 잠긴 조합이 '저울을 비웠다'로 오인되므로, 짧은 ERR 은 직전 정상값으로 메운다.
     */
    private fun holdTransientErr(i: Int, value: Int?): Int {
        if (value != null) {
            lastGood[i] = value
            errStreak[i] = 0
            return value
        }
        errStreak[i] += 1
        val good = lastGood[i]
        if (good != null && errStreak[i] <= ERR_HOLD_PACKETS) return good
        return -1
    }
}

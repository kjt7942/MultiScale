package com.kjt7942.multiscale

import org.json.JSONArray
import org.json.JSONObject

const val MIN_TRAYS = 2
const val MAX_TRAYS = 24
const val MAX_COLS = 12

/**
 * 화면에 저울을 어떻게 놓을지에 대한 사용자 설정(DIY 키트라서 개수·배열이 고정이 아니다).
 * cells 는 화면 왼쪽 위부터 가로로 읽는 순서이고, 값은 저울 번호(0부터), 빈 칸은 -1.
 */
data class LayoutConfig(
    val channels: Int = CHANNELS,
    val cols: Int = 6,
    val bottomFirst: Boolean = true,
    val cells: List<Int> = defaultCells(CHANNELS, 6, true),
    val topup: List<Int> = listOf(0, 1, 6, 7),
    val theme: String = "grape",
    val orientation: String = "landscape",   // auto | landscape | portrait
    val showBars: Boolean = false
) {
    val rows: Int get() = if (cols <= 0) 1 else (cells.size + cols - 1) / cols

    fun withChannels(n: Int): LayoutConfig {
        val c = n.coerceIn(MIN_TRAYS, MAX_TRAYS)
        val cc = cols.coerceIn(1, minOf(MAX_COLS, c))
        val tp = topup.filter { it < c }.ifEmpty { listOf(0) }
        return copy(channels = c, cols = cc, cells = defaultCells(c, cc, bottomFirst), topup = tp)
    }

    fun withCols(c: Int): LayoutConfig {
        val cc = c.coerceIn(1, minOf(MAX_COLS, channels))
        return copy(cols = cc, cells = defaultCells(channels, cc, bottomFirst))
    }

    fun withBottomFirst(b: Boolean): LayoutConfig =
        copy(bottomFirst = b, cells = defaultCells(channels, cols, b))

    fun resetCells(): LayoutConfig = copy(cells = defaultCells(channels, cols, bottomFirst))

    /** 두 칸의 내용을 맞바꾼다(빈 칸과 바꾸면 저울이 이동). */
    fun swap(a: Int, b: Int): LayoutConfig {
        if (a == b || a !in cells.indices || b !in cells.indices) return this
        val m = cells.toMutableList()
        val t = m[a]; m[a] = m[b]; m[b] = t
        return copy(cells = m)
    }

    fun toggleTopup(tray: Int): LayoutConfig {
        if (tray !in 0 until channels) return this
        val s = topup.toMutableSet()
        if (!s.add(tray)) s.remove(tray)
        return copy(topup = s.sorted())
    }

    /** 저장된 값이 깨졌거나 저울 수와 안 맞으면 기본 배열로 되돌린다. */
    fun normalized(): LayoutConfig {
        val c = channels.coerceIn(MIN_TRAYS, MAX_TRAYS)
        val cc = cols.coerceIn(1, minOf(MAX_COLS, c))
        val used = cells.filter { it >= 0 }
        val okCells = cells.size % cc == 0 && cells.isNotEmpty() &&
            used.size == c && used.toSet() == (0 until c).toSet()
        return copy(
            channels = c, cols = cc,
            cells = if (okCells) cells else defaultCells(c, cc, bottomFirst),
            topup = topup.filter { it < c }.distinct().sorted(),
            theme = if (THEMES.any { it.id == theme }) theme else "grape",
            orientation = if (orientation in listOf("auto", "landscape", "portrait")) orientation else "landscape"
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("channels", channels)
        put("cols", cols)
        put("bottom_first", bottomFirst)
        put("cells", JSONArray(cells))
        put("topup", JSONArray(topup))
        put("theme", theme)
        put("orientation", orientation)
        put("show_bars", showBars)
    }

    companion object {
        /**
         * 번호를 줄 단위로 채운 기본 배열. bottomFirst 면 1번이 맨 아랫줄 왼쪽에서 시작해
         * 위로 올라간다(실제 하드웨어: 아랫줄 1~6, 윗줄 7~12).
         */
        fun defaultCells(n: Int, cols: Int, bottomFirst: Boolean): List<Int> {
            val rows = (n + cols - 1) / cols
            val cells = MutableList(rows * cols) { -1 }
            for (t in 0 until n) {
                val fromBottom = t / cols
                val row = if (bottomFirst) rows - 1 - fromBottom else fromBottom
                cells[row * cols + t % cols] = t
            }
            return cells
        }

        fun fromJson(o: JSONObject?): LayoutConfig {
            if (o == null) return LayoutConfig()
            return try {
                val ca = o.optJSONArray("cells")
                val ta = o.optJSONArray("topup")
                LayoutConfig(
                    channels = o.optInt("channels", CHANNELS),
                    cols = o.optInt("cols", 6),
                    bottomFirst = o.optBoolean("bottom_first", true),
                    cells = if (ca == null) emptyList() else List(ca.length()) { ca.optInt(it, -1) },
                    topup = if (ta == null) listOf(0, 1, 6, 7) else List(ta.length()) { ta.optInt(it) },
                    theme = o.optString("theme", "grape"),
                    orientation = o.optString("orientation", "landscape"),
                    showBars = o.optBoolean("show_bars", false)
                ).normalized()
            } catch (e: Exception) {
                LayoutConfig()
            }
        }
    }
}

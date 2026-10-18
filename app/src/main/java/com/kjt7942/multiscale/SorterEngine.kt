package com.kjt7942.multiscale

const val WEIGHT_STEP = 5                   // 5g 단위로 반올림해 표시/연산
const val REFILL_WEIGHT_THRESHOLD = 300     // 잠금 중 비워진 저울에 이 이상 다시 찍히면 새 송이로 본다
const val SETTLE_STABLE_MS = 500L           // 조합무게 반영 전 같은 값이 유지돼야 하는 시간
const val LOCK_STABLE_MS = 500L             // 새 조합을 잠글 때 저울 무게가 안정돼야 하는 시간
const val TOLERANCE_MAX = 500
val TOPUP_TRAYS = setOf(0, 1, 6, 7)         // 보태기 모드에서 박스를 올리는 저울(0부터)

enum class Role { NONE, COMBO, TOPUP }

data class Preset(val target: Int, val minComb: Int, val maxComb: Int, val tolerance: Int)

class Snapshot(
    val weights: List<Int>,
    val roles: List<Role>,
    val sum: Int,
    val comboWeight: Int?,   // null 이면 조합실패
    val locked: Boolean,
    val shortage: Int?,      // 조합실패 시 모자란 양
    val leds: List<Int>      // 켜야 할 LED(1부터)
)

/**
 * 기존 main.py 의 조합/잠금 로직을 화면과 분리해 옮긴 것.
 * 시간은 clock() 으로만 읽어서 테스트에서 제어할 수 있다.
 */
class SorterEngine(private val clock: () -> Long) {
    var target = 2050
    var minComb = 3
    var maxComb = 4
    var tolerance = 50
    var topupMode = false
    var lockEnabled = true
    var presetIndex: Int? = null
    var calMultipliers = DoubleArray(CHANNELS) { 1.0 }
    var boxesDone = 0
        private set

    private var memoMinComb = 3
    private var weights = IntArray(CHANNELS)

    private var lockedCombo: List<Item>? = null
    private var lockedSum = 0
    private var originalLockedIndices: List<Int> = emptyList()
    private val emptiedLocked = HashSet<Int>()
    private var boxRecorded = false
    private var settleCandidate: Pair<Int, Long>? = null
    private val stableRef = arrayOfNulls<Int>(CHANNELS)
    private val stableSince = LongArray(CHANNELS)

    // ---- 설정 조작 ---------------------------------------------------------
    private fun resetLock() {
        lockedCombo = null
        lockedSum = 0
        originalLockedIndices = emptyList()
        emptiedLocked.clear()
        boxRecorded = false
        settleCandidate = null
    }

    fun forceUnlock() = resetLock()

    fun onTare() = resetLock()

    fun changeSetting(kind: String, delta: Int) {
        resetLock()
        when (kind) {
            "target" -> target = maxOf(100, target + delta)
            "tol" -> tolerance = maxOf(0, minOf(TOLERANCE_MAX, tolerance + delta))
            "min" -> {
                minComb = maxOf(1, minOf(CHANNELS, minComb + delta))
                if (minComb > maxComb) maxComb = minComb
                if (topupMode) memoMinComb = minComb
            }
            "max" -> {
                maxComb = maxOf(1, minOf(CHANNELS, maxComb + delta))
                if (maxComb < minComb) minComb = maxComb
                if (topupMode) memoMinComb = minComb
            }
        }
        presetIndex = null
    }

    fun toggleTopup() {
        topupMode = !topupMode
        resetLock()
        if (topupMode) {
            memoMinComb = minComb
            minComb = 1
        } else {
            minComb = memoMinComb
        }
    }

    fun toggleLock() {
        lockEnabled = !lockEnabled
        resetLock()
    }

    fun loadPreset(index: Int, p: Preset) {
        target = p.target
        minComb = p.minComb
        maxComb = p.maxComb
        tolerance = p.tolerance
        presetIndex = index
        resetLock()
    }

    /** 저장된 마지막 상태를 복원할 때 쓴다. 보태기 모드면 최소조합 기억값도 맞춘다. */
    fun restore(topup: Boolean, rememberedMin: Int) {
        topupMode = topup
        memoMinComb = rememberedMin
    }

    val rememberedMinComb: Int
        get() = if (topupMode) memoMinComb else minComb

    // ---- 무게 입력 ---------------------------------------------------------
    /** 원시 무게(g, 오류는 -1)를 받아 화면에 보일 결과를 계산한다. */
    fun onWeights(raw: IntArray): Snapshot {
        val calibrated = IntArray(CHANNELS)
        for (i in 0 until CHANNELS) {
            val w = if (i < raw.size) raw[i] else -1
            calibrated[i] = if (w > 0) {
                // 파이썬 round() 와 같은 짝수 반올림
                (Math.rint(w * calMultipliers[i] / WEIGHT_STEP) * WEIGHT_STEP).toInt()
            } else {
                w
            }
        }
        weights = calibrated
        trackStability()
        return evaluate()
    }

    private fun trackStability() {
        val now = clock()
        for (i in 0 until CHANNELS) {
            val ref = stableRef[i]
            if (ref == null || Math.abs(weights[i] - ref) > WEIGHT_STEP) {
                stableRef[i] = weights[i]
                stableSince[i] = now
            }
        }
    }

    private fun isStable(i: Int): Boolean = clock() - stableSince[i] >= LOCK_STABLE_MS

    private fun evaluate(): Snapshot {
        var total = 0
        var topupSum = 0
        for (i in 0 until CHANNELS) {
            val w = weights[i]
            if (w > 0) {
                total += w
                if (topupMode && i in TOPUP_TRAYS) topupSum += w
            }
        }
        val sum = if (topupMode) topupSum else total

        if (!lockEnabled) {
            // 잠금 기능이 꺼진 상태: 매번 새로 계산해서 보여주기만 한다(설치/테스트용).
            val items = ArrayList<Item>()
            for (i in 0 until CHANNELS) {
                val w = weights[i]
                if (w > 0 && !(topupMode && i in TOPUP_TRAYS)) items.add(Item(i + 1, w))
            }
            val currentTarget = if (topupMode) target - topupSum else target
            val r = Combination.best(items, currentTarget, minComb, maxComb, tolerance)
            return render(r.combo, r.total, topupSum, sum, r.nearTotal, currentTarget, false)
        }

        val locked = lockedCombo
        if (locked != null) {
            val still = ArrayList<Item>()
            for (item in locked) {
                val idx1 = item.index
                val w = weights[idx1 - 1]
                if (w <= 0) {
                    // 비워져도 선택과 LED 는 유지한다. 새 송이가 올라오기 전까진 빼지 않는다.
                    emptiedLocked.add(idx1)
                    still.add(Item(idx1, w))
                } else if (emptiedLocked.contains(idx1) && w >= REFILL_WEIGHT_THRESHOLD) {
                    emptiedLocked.remove(idx1) // 새 송이: 선택을 풀어 다음 조합에 쓸 수 있게 한다
                } else {
                    still.add(Item(idx1, w))
                }
            }

            // 포도가 자리 잡으며 무게가 붙는 걸 반영하되, 손으로 누른 순간값에는 흔들리지 않게
            // 같은 값이 일정 시간 유지돼야 조합무게에 반영한다. 픽업이 시작되면 고정한다.
            if (emptiedLocked.isEmpty()) {
                var currentSum = 0
                for (i in originalLockedIndices) currentSum += weights[i - 1]
                val now = clock()
                val sc = settleCandidate
                if (sc == null || sc.first != currentSum) {
                    settleCandidate = Pair(currentSum, now)
                } else if (now - sc.second >= SETTLE_STABLE_MS) {
                    lockedSum = currentSum
                }
            }

            // 잠긴 저울 전부가 동시에 0이면 박스를 통째로 치운 것 → 바로 잠금 해제.
            // -1(ERR)은 통신 두절이므로 완성으로 세지 않는다.
            if (originalLockedIndices.isNotEmpty() && originalLockedIndices.all { weights[it - 1] == 0 }) {
                if (!boxRecorded) {
                    boxesDone += 1
                    boxRecorded = true
                }
                still.clear()
            }

            if (still.isEmpty()) {
                resetLock()
            } else {
                lockedCombo = still
                return render(still, lockedSum, topupSum, sum, null, null, true)
            }
        }

        // 보태기 모드: 박스 저울(1,2,7,8번)이 모두 올라와 자리 잡기 전에는 조합하지 않는다.
        if (topupMode && TOPUP_TRAYS.any { !(weights[it] > 0 && isStable(it)) }) {
            return render(null, 0, topupSum, sum, null, null, false)
        }

        // 새 조합은 무게가 자리 잡은 저울로만 만든다.
        val items = ArrayList<Item>()
        for (i in 0 until CHANNELS) {
            val w = weights[i]
            if (w > 0 && isStable(i) && !(topupMode && i in TOPUP_TRAYS)) items.add(Item(i + 1, w))
        }
        val currentTarget = if (topupMode) target - topupSum else target
        val r = Combination.best(items, currentTarget, minComb, maxComb, tolerance)
        val found = r.combo
        if (found != null) {
            lockedCombo = found
            lockedSum = r.total
            emptiedLocked.clear()
            settleCandidate = null
            boxRecorded = false
            originalLockedIndices = found.map { it.index }
        }
        return render(found, r.total, topupSum, sum, r.nearTotal, currentTarget, lockedCombo != null)
    }

    private fun render(
        combo: List<Item>?, bestSum: Int, topupSum: Int, sum: Int,
        near: Int?, currentTarget: Int?, locked: Boolean
    ): Snapshot {
        val roles = List(CHANNELS) { i ->
            val isTopup = topupMode && i in TOPUP_TRAYS
            val isCombo = combo != null && combo.any { it.index == i + 1 }
            when {
                isTopup -> Role.TOPUP
                isCombo -> Role.COMBO
                else -> Role.NONE
            }
        }
        return if (combo != null) {
            val finalSum = bestSum + (if (topupMode) topupSum else 0)
            Snapshot(weights.toList(), roles, sum, finalSum, locked, null, combo.map { it.index })
        } else {
            val shortage = if (near != null && currentTarget != null) currentTarget - near else null
            Snapshot(weights.toList(), roles, sum, null, false, shortage, emptyList())
        }
    }
}

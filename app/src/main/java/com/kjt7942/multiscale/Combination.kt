package com.kjt7942.multiscale

/** index 는 1부터 시작하는 저울 번호. */
data class Item(val index: Int, val weight: Int)

data class ComboResult(val combo: List<Item>?, val total: Int, val nearTotal: Int?)

object Combination {
    /**
     * 목표무게를 넘되 초과분이 tolerance 이내인 조합 중 목표에 가장 가까운 것.
     * 기존 main.py 의 best_combination() 과 같은 순서로 탐색해 같은 결과를 낸다.
     *  - 조합 크기 오름차순, 같은 크기 안에서는 사전순
     *  - 초과분이 더 작으면 교체, 같으면 저울을 더 많이 쓰는 쪽
     * nearTotal: 조합이 실패했을 때 "얼마나 모자란지" 알려주기 위한 목표 미달 최대 합계.
     */
    fun best(items: List<Item>, target: Int, minC: Int, maxC: Int, tolerance: Int): ComboResult {
        var bestCombo: List<Item>? = null
        var bestSum = 0
        var bestDiff = Int.MAX_VALUE
        var nearTotal: Int? = null
        val n = items.size
        val lo = maxOf(1, minC)
        val hi = minOf(maxC, n)
        for (r in lo..hi) {
            val idx = IntArray(r) { it }
            while (true) {
                var sum = 0
                for (k in 0 until r) sum += items[idx[k]].weight
                val diff = sum - target
                if (diff < 0) {
                    val near = nearTotal
                    if (near == null || sum > near) nearTotal = sum
                } else if (diff <= tolerance) {
                    val bestSize = bestCombo?.size ?: 0
                    if (diff < bestDiff || (diff == bestDiff && r > bestSize)) {
                        bestDiff = diff
                        bestSum = sum
                        bestCombo = List(r) { items[idx[it]] }
                    }
                }
                // 다음 조합 (사전순)
                var i = r - 1
                while (i >= 0 && idx[i] == n - r + i) i--
                if (i < 0) break
                idx[i]++
                for (j in i + 1 until r) idx[j] = idx[j - 1] + 1
            }
        }
        return ComboResult(bestCombo, bestSum, nearTotal)
    }
}

package com.kjt7942.multiscale

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Saved(
    val target: Int = 2050,
    val minComb: Int = 3,
    val maxComb: Int = 4,
    val tolerance: Int = 50,
    val dark: Boolean = true,
    val topup: Boolean = false,
    val lockEnabled: Boolean = true,
    val presetIndex: Int? = null,
    val presets: List<Preset?> = List(8) { null },
    val cal: List<Double> = List(CHANNELS) { 1.0 },
    val lastDevice: String? = null
)

/** 설정을 JSON 한 덩어리로 저장한다(기존 settings.json 과 같은 구성). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("sorter", Context.MODE_PRIVATE)

    fun read(): Saved {
        val text = sp.getString("data", null) ?: return Saved()
        return try {
            val o = JSONObject(text)
            val presetsJson = o.optJSONArray("presets")
            val presets = List(8) { i ->
                if (presetsJson == null || i >= presetsJson.length() || presetsJson.isNull(i)) {
                    null
                } else {
                    val p = presetsJson.getJSONObject(i)
                    Preset(
                        p.optInt("target_weight", 2050),
                        p.optInt("min_comb", 3),
                        p.optInt("max_comb", 4),
                        p.optInt("tolerance", 50)
                    )
                }
            }
            val calJson = o.optJSONArray("cal_multipliers")
            val cal = if (calJson != null && calJson.length() == CHANNELS) {
                List(CHANNELS) { calJson.optDouble(it, 1.0) }
            } else {
                List(CHANNELS) { 1.0 }
            }
            val pi = o.optInt("current_preset_index", -1)
            Saved(
                target = o.optInt("target_weight", 2050),
                minComb = o.optInt("min_comb", 3),
                maxComb = o.optInt("max_comb", 4),
                tolerance = o.optInt("tolerance", 50),
                dark = o.optBoolean("is_dark_mode", true),
                topup = o.optBoolean("is_topup_mode", false),
                lockEnabled = o.optBoolean("combo_lock_enabled", true),
                presetIndex = if (pi in 0..7) pi else null,
                presets = presets,
                cal = cal,
                lastDevice = if (o.has("last_device")) o.optString("last_device") else null
            )
        } catch (e: Exception) {
            Saved() // 깨진 설정은 기본값으로 시작
        }
    }

    fun write(v: Saved) {
        val o = JSONObject()
        o.put("target_weight", v.target)
        o.put("min_comb", v.minComb)
        o.put("max_comb", v.maxComb)
        o.put("tolerance", v.tolerance)
        o.put("is_dark_mode", v.dark)
        o.put("is_topup_mode", v.topup)
        o.put("combo_lock_enabled", v.lockEnabled)
        o.put("current_preset_index", v.presetIndex ?: -1)
        val arr = JSONArray()
        for (p in v.presets) {
            if (p == null) {
                arr.put(JSONObject.NULL)
            } else {
                val po = JSONObject()
                po.put("target_weight", p.target)
                po.put("min_comb", p.minComb)
                po.put("max_comb", p.maxComb)
                po.put("tolerance", p.tolerance)
                arr.put(po)
            }
        }
        o.put("presets", arr)
        val cal = JSONArray()
        for (c in v.cal) cal.put(c)
        o.put("cal_multipliers", cal)
        if (v.lastDevice != null) o.put("last_device", v.lastDevice)
        sp.edit().putString("data", o.toString()).apply()
    }
}

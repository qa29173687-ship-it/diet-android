package tw.dietbook

import kotlin.math.roundToInt

object Calc {
    fun mifflin(sex: String, weightKg: Double, heightCm: Double, age: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return if (sex == "female") base - 161.0 else base + 5.0
    }

    fun activityFactor(level: String): Double = when (level) {
        "sedentary" -> 1.2
        "light" -> 1.375
        "moderate" -> 1.55
        "active" -> 1.725
        "very" -> 1.9
        else -> 1.55
    }

    fun activityLabel(level: String): String = when (level) {
        "sedentary" -> "久坐（幾乎不運動）"
        "light" -> "輕度（一週 1–3 天）"
        "moderate" -> "中度（一週 3–5 天）"
        "active" -> "高度（一週 6–7 天）"
        "very" -> "非常高度（體力勞動／一日兩練）"
        else -> level
    }

    fun tdee(bmr: Double, level: String): Double = bmr * activityFactor(level)

    /**
     * 攝取 − BMR − 活動消耗；無活動數據時用攝取 − TDEE。
     * 活動消耗規則：有手動運動／鍛鍊時以手動為準；否則用 Health Connect；兩者皆無則 TDEE。
     */
    fun dayNet(intake: Double, bmr: Double, activeBurn: Double?, tdeeFallback: Double): Double {
        return if (activeBurn != null) intake - bmr - activeBurn else intake - tdeeFallback
    }

    /**
     * 合併手動與 HC，避免重複計算：
     * 手動 > 0 → 用手動；否則用 HC；否則 null（走 TDEE）。
     */
    fun effectiveActive(manualBurn: Double, hcBurn: Double?): Double? = when {
        manualBurn > 0.5 -> manualBurn
        hcBurn != null && hcBurn > 0.5 -> hcBurn
        else -> null
    }

    fun fatKg(kcalBalance: Double): Double = kcalBalance / 7700.0

    fun paybackPerDay(surplus: Double, days: Int): Double {
        if (days <= 0 || surplus <= 0) return 0.0
        return surplus / days
    }

    fun scale(per100: Double, grams: Double): Double = per100 * grams / 100.0

    fun r1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
    fun r0(v: Double): Int = v.roundToInt()

    /** MET × 體重(kg) × 小時 */
    fun metKcal(met: Double, weightKg: Double, minutes: Int): Double =
        met * weightKg * (minutes / 60.0)

    data class CardioType(val id: String, val label: String, val met: Double)

    val CARDIO_TYPES = listOf(
        CardioType("walking", "走路", 3.5),
        CardioType("brisk_walk", "健走", 4.3),
        CardioType("running", "跑步", 9.8),
        CardioType("jogging", "慢跑", 7.0),
        CardioType("cycling", "騎車", 6.8),
        CardioType("swimming", "游泳", 8.0),
        CardioType("hiking", "爬山", 6.0),
        CardioType("yoga", "瑜珈", 3.0),
        CardioType("dance", "有氧舞蹈", 5.0),
        CardioType("other", "其他有氧", 5.0),
    )

    fun cardioById(id: String): CardioType = CARDIO_TYPES.find { it.id == id } ?: CARDIO_TYPES.last()

    val STRENGTH_PARTS = listOf("手臂", "胸", "背", "肩", "腹", "腿", "臀")

    /** 重訓約 MET 3.5–6；依部位略調 */
    fun strengthMet(part: String): Double = when (part) {
        "腿", "臀" -> 6.0
        "背", "胸" -> 5.0
        "肩", "手臂" -> 4.5
        "腹" -> 3.8
        else -> 5.0
    }

    /** 累計分鐘 → 部位等級 0–3 */
    fun partLevel(totalMinutes: Int): Int = when {
        totalMinutes >= 120 -> 3
        totalMinutes >= 45 -> 2
        totalMinutes >= 15 -> 1
        else -> 0
    }
}

package tw.dietbook

import kotlin.math.roundToInt

object Calc {
    /** Mifflin–St Jeor，回傳 kcal/日 */
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

    /** 攝取 − BMR − 活動消耗；無 HC 時用攝取 − TDEE */
    fun dayNet(intake: Double, bmr: Double, activeBurn: Double?, tdeeFallback: Double): Double {
        return if (activeBurn != null) intake - bmr - activeBurn else intake - tdeeFallback
    }

    fun fatKg(kcalBalance: Double): Double = kcalBalance / 7700.0

    /** 盈餘要在 N 天打平：每天需再少吃／多消耗的量 */
    fun paybackPerDay(surplus: Double, days: Int): Double {
        if (days <= 0 || surplus <= 0) return 0.0
        return surplus / days
    }

    fun scale(per100: Double, grams: Double): Double = per100 * grams / 100.0

    fun r1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
    fun r0(v: Double): Int = v.roundToInt()
}

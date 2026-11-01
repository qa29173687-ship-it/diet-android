package tw.dietbook

import android.content.Context
import java.util.Calendar
import java.util.TimeZone

object PigStats {
    private val tz = TimeZone.getTimeZone("Asia/Taipei")

    fun isNight(): Boolean {
        val h = Calendar.getInstance(tz).get(Calendar.HOUR_OF_DAY)
        return h >= 22 || h < 6
    }

    fun compute(ctx: Context, u: UserProfile, snap: DaySnapshot): PigVitals {
        val now = System.currentTimeMillis()
        val lastFeed = Store.lastFeedAt(ctx)
        val hoursSinceFeed = if (lastFeed <= 0L) {
            if (snap.intakeKcal < 1) 10.0 else 2.0
        } else (now - lastFeed) / 3600000.0

        // 飽足：吃了就高；超過 5–6 小時沒記飲食就下降
        var satiety = when {
            snap.intakeKcal >= u.goalKcal * 0.85 -> 92
            snap.intakeKcal >= u.goalKcal * 0.5 -> 75
            snap.intakeKcal >= 200 -> 55
            else -> 30
        }
        if (hoursSinceFeed > 6) satiety = (satiety - ((hoursSinceFeed - 6) * 12).toInt()).coerceAtLeast(5)
        else if (hoursSinceFeed > 4) satiety = (satiety - 15).coerceAtLeast(15)

        // 體力：有運動／鍛鍊較高；晚上偏低
        val manual = snap.manualBurn
        var energy = when {
            manual >= 400 -> 90
            manual >= 150 -> 78
            manual >= 50 -> 65
            else -> 50
        }
        if (isNight()) energy = (energy - 25).coerceAtLeast(20)
        if (hoursSinceFeed > 8) energy = (energy - 15).coerceAtLeast(15)

        // 心情：有缺口／達蛋白目標／連續天數加分；暴食扣分；餓扣分
        var mood = 60
        if (snap.net < -100) mood += 15
        if (snap.net > 400) mood -= 12
        if (snap.protein >= u.goalProtein * 0.8) mood += 10
        if (u.streakDays >= 3) mood += 8
        if (u.streakDays >= 7) mood += 6
        if (satiety < 30) mood -= 20
        if (isNight()) mood -= 5
        mood = mood.coerceIn(5, 100)

        // 餓太久 → 通知（最多半天一次）
        if (hoursSinceFeed >= 7 && satiety < 30) {
            val last = Store.lastHungryNotifyAt(ctx)
            if (now - last > 12 * 3600_000L) {
                Notify.showHungry(ctx)
                Store.setHungryNotifyAt(ctx, now)
            }
        }

        return PigVitals(satiety.coerceIn(0, 100), mood, energy.coerceIn(0, 100))
    }
}

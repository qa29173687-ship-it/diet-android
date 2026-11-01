package tw.dietbook

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class StatDetailKind { INTAKE, PROTEIN, BURN, STRENGTH }

data class IntakeLine(
    val time: String,
    val meal: String,
    val name: String,
    val qty: String,
    val kcal: Int,
)

data class IntakeDetail(
    val day: String,
    val lines: List<IntakeLine>,
    val totalKcal: Int,
)

data class ProteinLine(
    val time: String,
    val name: String,
    val proteinG: Double,
)

data class ProteinMealGroup(
    val meal: String,
    val lines: List<ProteinLine>,
    val subtotalG: Double,
)

data class ProteinDetail(
    val day: String,
    val meals: List<ProteinMealGroup>,
    val totalG: Double,
)

data class BurnLine(
    val label: String,
    val source: String,
    val duration: String,
    val kcal: Int,
    val formula: String,
)

data class BurnDetail(
    val day: String,
    val lines: List<BurnLine>,
    val totalKcal: Int,
    val note: String,
)

data class StrengthLine(
    val time: String,
    val part: String,
    val exercise: String,
    val sets: Int,
    val minutes: Int,
    val kcal: Int,
)

data class StrengthDetail(
    val day: String,
    val lines: List<StrengthLine>,
    val totalMin: Int,
    val totalKcal: Int,
)

object StatDetail {
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Taipei")
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.TAIWAN).apply { timeZone = zone }

    private val mealOrder = listOf("早餐", "午餐", "點心", "晚餐", "宵夜")

    fun mealOf(atMs: Long): String {
        val h = Calendar.getInstance(zone).apply { timeInMillis = atMs }.get(Calendar.HOUR_OF_DAY)
        return when (h) {
            in 5..10 -> "早餐"
            in 11..14 -> "午餐"
            in 15..16 -> "點心"
            in 17..21 -> "晚餐"
            else -> "宵夜"
        }
    }

    fun formatTime(atMs: Long): String = timeFmt.format(Date(atMs))

    fun intake(ctx: Context, day: String = Days.today()): IntakeDetail {
        val logs = Store.logs(ctx, day).sortedBy { it.at }
        val lines = logs.map {
            IntakeLine(
                time = formatTime(it.at),
                meal = mealOf(it.at),
                name = it.foodName,
                qty = "${Calc.r0(it.grams)} g",
                kcal = Calc.r0(it.kcal),
            )
        }
        return IntakeDetail(day, lines, lines.sumOf { it.kcal })
    }

    fun protein(ctx: Context, day: String = Days.today()): ProteinDetail {
        val logs = Store.logs(ctx, day).sortedBy { it.at }
        val byMeal = linkedMapOf<String, MutableList<ProteinLine>>()
        mealOrder.forEach { byMeal[it] = mutableListOf() }
        logs.forEach { log ->
            val meal = mealOf(log.at)
            byMeal.getOrPut(meal) { mutableListOf() }.add(
                ProteinLine(
                    time = formatTime(log.at),
                    name = log.foodName,
                    proteinG = Calc.r1(log.protein),
                ),
            )
        }
        val meals = mealOrder.mapNotNull { meal ->
            val lines = byMeal[meal].orEmpty()
            if (lines.isEmpty()) null
            else ProteinMealGroup(meal, lines, Calc.r1(lines.sumOf { it.proteinG }))
        } + byMeal.filterKeys { it !in mealOrder }.map { (meal, lines) ->
            ProteinMealGroup(meal, lines, Calc.r1(lines.sumOf { it.proteinG }))
        }
        return ProteinDetail(day, meals, Calc.r1(meals.sumOf { it.subtotalG }))
    }

    fun burn(ctx: Context, day: String = Days.today()): BurnDetail {
        val u = Store.profile(ctx)
        val snap = Store.snapshot(ctx, day)
        val cardio = Store.cardioLogs(ctx, day).sortedBy { it.at }
        val strength = Store.strengthLogs(ctx, day).sortedBy { it.at }
        val lines = mutableListOf<BurnLine>()
        cardio.forEach { c ->
            lines += BurnLine(
                label = c.label,
                source = "手動",
                duration = "${c.minutes} 分",
                kcal = Calc.r0(c.kcal),
                formula = "MET ${Calc.r1(c.met)} × ${Calc.r1(u.weightKg)} kg × ${Calc.r1(c.minutes / 60.0)} h",
            )
        }
        strength.forEach { s ->
            val met = Calc.strengthMet(s.part)
            lines += BurnLine(
                label = "${s.part}・${s.exercise}",
                source = "鍛鍊",
                duration = "${s.minutes} 分",
                kcal = Calc.r0(s.kcal),
                formula = "MET ${Calc.r1(met)} × ${Calc.r1(u.weightKg)} kg × ${Calc.r1(s.minutes / 60.0)} h",
            )
        }
        val manual = Store.manualBurn(ctx, day)
        val hc = snap?.hcBurn
        val source = snap?.burnSource ?: when {
            manual > 0.5 -> "manual"
            hc != null && hc > 0.5 -> "health_connect"
            else -> "tdee"
        }
        if (source == "health_connect" && (hc ?: 0.0) > 0.5 && manual <= 0.5) {
            lines += BurnLine(
                label = "Health Connect 活動熱量",
                source = "Health Connect",
                duration = "全日",
                kcal = Calc.r0(hc ?: 0.0),
                formula = "裝置回報的主動消耗（非 MET 估算）",
            )
        }
        val total = when (source) {
            "manual" -> Calc.r0(manual)
            "health_connect" -> Calc.r0(hc ?: 0.0)
            else -> 0
        }
        val note = when (source) {
            "manual" -> "有手動紀錄時以手動為準（MET × 體重 × 小時）；Health Connect 不重複加總。"
            "health_connect" -> "今天沒有手動運動，採用 Health Connect 主動消耗。"
            else -> "今天還沒有運動／鍛鍊，也沒有 Health Connect 數據；日結時會用 TDEE 估算活動量。"
        }
        return BurnDetail(day, lines, total, note)
    }

    fun strength(ctx: Context, day: String = Days.today()): StrengthDetail {
        val logs = Store.strengthLogs(ctx, day).sortedBy { it.at }
        val lines = logs.map {
            StrengthLine(
                time = formatTime(it.at),
                part = it.part,
                exercise = it.exercise,
                sets = it.sets,
                minutes = it.minutes,
                kcal = Calc.r0(it.kcal),
            )
        }
        return StrengthDetail(
            day = day,
            lines = lines,
            totalMin = lines.sumOf { it.minutes },
            totalKcal = lines.sumOf { it.kcal },
        )
    }

    /** Preview samples (Asia/Taipei-ish times on given day). */
    fun sampleIntake(day: String = Days.today()): IntakeDetail = IntakeDetail(
        day = day,
        lines = listOf(
            IntakeLine("07:40", "早餐", "無糖優格 1杯", "150 g", 95),
            IntakeLine("08:05", "早餐", "香蕉", "120 g", 107),
            IntakeLine("12:20", "午餐", "雞胸肉便當", "380 g", 520),
            IntakeLine("12:25", "午餐", "燙青菜", "150 g", 45),
            IntakeLine("16:10", "點心", "希臘優格", "100 g", 59),
            IntakeLine("19:05", "晚餐", "鮭魚定食", "320 g", 480),
        ),
        totalKcal = 1306,
    )

    fun sampleProtein(day: String = Days.today()): ProteinDetail = ProteinDetail(
        day = day,
        meals = listOf(
            ProteinMealGroup(
                "早餐",
                listOf(
                    ProteinLine("07:40", "無糖優格 1杯", 10.0),
                    ProteinLine("08:05", "香蕉", 1.3),
                ),
                11.3,
            ),
            ProteinMealGroup(
                "午餐",
                listOf(
                    ProteinLine("12:20", "雞胸肉便當", 38.0),
                    ProteinLine("12:25", "燙青菜", 2.4),
                ),
                40.4,
            ),
            ProteinMealGroup(
                "點心",
                listOf(ProteinLine("16:10", "希臘優格", 9.0)),
                9.0,
            ),
            ProteinMealGroup(
                "晚餐",
                listOf(ProteinLine("19:05", "鮭魚定食", 32.0)),
                32.0,
            ),
        ),
        totalG = 92.7,
    )

    fun sampleBurn(day: String = Days.today()): BurnDetail = BurnDetail(
        day = day,
        lines = listOf(
            BurnLine("健走", "手動", "40 分", 154, "MET 4.3 × 54.0 kg × 0.7 h"),
            BurnLine("腿・深蹲", "鍛鍊", "20 分", 108, "MET 6.0 × 54.0 kg × 0.3 h"),
        ),
        totalKcal = 262,
        note = "有手動紀錄時以手動為準（MET × 體重 × 小時）；Health Connect 不重複加總。",
    )

    fun sampleStrength(day: String = Days.today()): StrengthDetail = StrengthDetail(
        day = day,
        lines = listOf(
            StrengthLine("18:10", "腿", "深蹲", 4, 12, 65),
            StrengthLine("18:25", "臀", "臀推", 3, 8, 43),
        ),
        totalMin = 20,
        totalKcal = 108,
    )
}

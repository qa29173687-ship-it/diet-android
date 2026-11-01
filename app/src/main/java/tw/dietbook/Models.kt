package tw.dietbook

/** 食藥署 TFND、追溯系統或自訂食物 */
data class FoodItem(
    val id: String,
    val name: String,
    val alias: String = "",
    val category: String = "",
    val kcalPer100: Double,
    val proteinPer100: Double,
    val custom: Boolean = false,
    val servingG: Double? = null,
    val servingsPerPack: Double? = null,
    val basis: String = "per100",
    val note: String = "",
    val barcode: String = "",
)

data class FoodLog(
    val id: Long,
    val day: String,
    val foodId: String,
    val foodName: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val at: Long,
)

/** 有氧運動紀錄 */
data class CardioLog(
    val id: Long,
    val day: String,
    val type: String,          // walking|running|cycling|swimming|hiking|other
    val label: String,
    val minutes: Int,
    val kcal: Double,
    val met: Double,
    val at: Long,
)

/** 重訓紀錄 */
data class StrengthLog(
    val id: Long,
    val day: String,
    val part: String,          // 手臂|胸|背|肩|腹|腿|臀
    val exercise: String,
    val sets: Int,
    val minutes: Int,
    val kcal: Double,
    val at: Long,
)

data class DaySnapshot(
    val day: String,
    val intakeKcal: Double,
    val protein: Double,
    val bmr: Double,
    val activeBurn: Double?,   // 有效活動消耗（手動優先，否則 HC）
    val tdeeFallback: Double,
    val net: Double,
    val steps: Long? = null,
    val weightKg: Double? = null,
    val finalized: Boolean = false,
    val manualBurn: Double = 0.0,  // 手動運動+鍛鍊
    val hcBurn: Double? = null,    // Health Connect 原始值
    val burnSource: String = "",   // manual | health_connect | tdee
)

data class UserProfile(
    val setupDone: Boolean = false,
    val sex: String = "male",
    val age: Int = 30,
    val heightCm: Double = 170.0,
    val weightKg: Double = 70.0,
    val bmr: Double = 1600.0,
    val bmrManual: Boolean = false,
    val activity: String = "moderate",
    val goalKcal: Double = 2000.0,
    val goalProtein: Double = 80.0,
    val balance: Double = 0.0,
    val lastFinalizedDay: String = "",
    /** Inclusive start of 總存款 window (setup or last reset). */
    val balanceStartDay: String = "",
    val remindPayback: Boolean = false,
    val paybackDays: Int = 7,
    val hcEnabled: Boolean = true,
    val streakDays: Int = 0,
    val lastActiveDay: String = "",
    val badges: Set<String> = emptySet(),
)

data class BarcodeHit(
    val barcode: String,
    val source: String,
    val sourceLabel: String,
    val name: String,
    val kcalPer100: Double?,
    val proteinPer100: Double?,
    val kcalPerServing: Double? = null,
    val proteinPerServing: Double? = null,
    val servingG: Double? = null,
    val servingsPerPack: Double? = null,
    val basis: String = "per100",
    val note: String = "",
)

/** 電子雞三圍：0–100 */
data class PigVitals(
    val satiety: Int,   // 飽足
    val mood: Int,      // 心情
    val energy: Int,    // 體力
)

enum class PigMood {
    NORMAL, EAT, HUNGRY, SLEEP, WORKOUT, BUFF, CHUBBY, VERY_FAT, CELEBRATE
}

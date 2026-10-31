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
    val servingG: Double? = null,          // 每份公克（自訂／標示）
    val servingsPerPack: Double? = null,   // 本包裝含幾份
    val basis: String = "per100",          // per100 | perServing
    val note: String = "",
    val barcode: String = "",              // EAN／UPC；自訂食物可綁定供下次掃描命中
)

data class FoodLog(
    val id: Long,
    val day: String,           // yyyy-MM-dd Asia/Taipei
    val foodId: String,
    val foodName: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val at: Long,
)

data class DaySnapshot(
    val day: String,
    val intakeKcal: Double,
    val protein: Double,
    val bmr: Double,
    val activeBurn: Double?,   // null = 未用 Health Connect
    val tdeeFallback: Double,
    val net: Double,           // 當日淨值（會累加進帳戶）
    val steps: Long? = null,
    val weightKg: Double? = null,
    val finalized: Boolean = false,
)

data class UserProfile(
    val setupDone: Boolean = false,
    val sex: String = "male",          // male | female
    val age: Int = 30,
    val heightCm: Double = 170.0,
    val weightKg: Double = 70.0,
    val bmr: Double = 1600.0,
    val bmrManual: Boolean = false,
    val activity: String = "moderate", // sedentary|light|moderate|active|very
    val goalKcal: Double = 2000.0,
    val goalProtein: Double = 80.0,
    val balance: Double = 0.0,         // 累計熱量帳戶（正=盈餘）
    val lastFinalizedDay: String = "",
    val remindPayback: Boolean = false,
    val paybackDays: Int = 7,
    val hcEnabled: Boolean = true,
)

/** 條碼查詢命中（供確認／編輯畫面預填） */
data class BarcodeHit(
    val barcode: String,
    val source: String,            // local | tfda | off
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

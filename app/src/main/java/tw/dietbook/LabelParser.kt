package tw.dietbook

/**
 * 從營養標示 OCR 文字解析熱量／蛋白質，並分辨每份 vs 每100公克／毫升。
 * 回傳需使用者確認的草稿。
 */
data class LabelDraft(
    val rawText: String,
    val kcalPer100: Double?,
    val proteinPer100: Double?,
    val kcalPerServing: Double?,
    val proteinPerServing: Double?,
    val servingG: Double?,
    val servingsPerPack: Double?,
    val detectedBasis: String, // per100 | perServing | unknown
)

object LabelParser {
    private val num = """(\d+(?:\.\d+)?)"""

    fun parse(text: String): LabelDraft {
        val t = text.replace(',', '.').replace('．', '.')
            .replace("大卡", "kcal").replace("卡路里", "kcal")
            .replace("公克", "g").replace("克", "g")
            .replace("毫升", "ml")

        val servingsPerPack = (
            Regex("""本包裝含\s*$num\s*份""").find(t)
                ?: Regex("""每包裝\s*$num\s*份""").find(t)
                ?: Regex("""含\s*$num\s*份""").find(t)
            )?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        val servingG = (
            Regex("""每一份量\s*$num\s*g""").find(t)
                ?: Regex("""每份\s*$num\s*g""").find(t)
                ?: Regex("""一份\s*$num\s*g""").find(t)
            )?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        fun near(label: Regex, window: Int = 40): Double? {
            val m = label.find(t) ?: return null
            val slice = t.substring(m.range.first, minOf(t.length, m.range.last + 1 + window))
            return Regex("""$num\s*(?:kcal|g)?""").find(slice)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        }

        // Prefer explicit per-100 lines
        val kcal100 = (
            Regex("""每\s*100\s*(?:g|公克|ml|毫升)[^\d]{0,12}$num\s*kcal""").find(t)
                ?: Regex("""$num\s*kcal[^\n]{0,20}每\s*100""") .find(t)
            )?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            ?: run {
                val i = t.indexOf("每100")
                if (i >= 0) {
                    val slice = t.substring(i, minOf(t.length, i + 80))
                    Regex("""熱量[^\d]{0,8}$num""").find(slice)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                } else null
            }

        val pro100 = (
            Regex("""每\s*100\s*(?:g|公克|ml|毫升)[^\n]{0,40}蛋白質[^\d]{0,8}$num""").find(t)
                ?: Regex("""蛋白質[^\d]{0,8}$num\s*g[^\n]{0,20}每\s*100""").find(t)
            )?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            ?: run {
                val i = t.indexOf("每100")
                if (i >= 0) {
                    val slice = t.substring(i, minOf(t.length, i + 120))
                    Regex("""蛋白質[^\d]{0,8}$num""").find(slice)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                } else null
            }

        val kcalServing = (
            Regex("""每份熱量[^\d]{0,8}$num""").find(t)
                ?: Regex("""每一份[^\n]{0,20}熱量[^\d]{0,8}$num""").find(t)
            )?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            ?: near(Regex("""每份[^\n]{0,10}熱量"""))

        val proServing = (
            Regex("""每份蛋白質[^\d]{0,8}$num""").find(t)
                ?: Regex("""每一份[^\n]{0,30}蛋白質[^\d]{0,8}$num""").find(t)
            )?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        // Fallback: first 熱量 / 蛋白質 numbers if structure unclear
        val kcalAny = kcal100 ?: kcalServing ?: near(Regex("""熱量"""))
        val proAny = pro100 ?: proServing ?: near(Regex("""蛋白質"""))

        val basis = when {
            kcal100 != null || pro100 != null -> "per100"
            kcalServing != null || proServing != null -> "perServing"
            else -> "unknown"
        }

        var outKcal100 = kcal100
        var outPro100 = pro100
        // Convert per-serving → per100 when serving grams known
        if (outKcal100 == null && kcalServing != null && servingG != null && servingG > 0) {
            outKcal100 = kcalServing * 100.0 / servingG
        }
        if (outPro100 == null && proServing != null && servingG != null && servingG > 0) {
            outPro100 = proServing * 100.0 / servingG
        }
        if (outKcal100 == null) outKcal100 = kcalAny
        if (outPro100 == null) outPro100 = proAny

        return LabelDraft(
            rawText = text,
            kcalPer100 = outKcal100,
            proteinPer100 = outPro100,
            kcalPerServing = kcalServing,
            proteinPerServing = proServing,
            servingG = servingG,
            servingsPerPack = servingsPerPack,
            detectedBasis = basis,
        )
    }

    /** 依使用者選擇的基準與份量，正規化成每100g */
    fun normalizeToPer100(
        basis: String,
        kcal: Double,
        protein: Double,
        servingG: Double?,
    ): Pair<Double, Double> {
        return if (basis == "perServing" && servingG != null && servingG > 0) {
            kcal * 100.0 / servingG to protein * 100.0 / servingG
        } else kcal to protein
    }
}

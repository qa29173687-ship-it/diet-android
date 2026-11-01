package tw.dietbook

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * 台灣日常份量對照表（assets/portions.csv）。
 * 單位如 顆／碗／片／根／個／份 對應公克，再依 TFND 或表內每 100g 營養換算。
 * 份量參考國健署食品交換表常見份量與市售／便利商店常見規格；營養優先連結食藥署 TFND。
 */
data class PortionUnit(
    val unit: String,
    val grams: Double,
)

data class PortionFood(
    val name: String,
    val aliases: String,
    val tfndId: String,
    val units: List<PortionUnit>,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val note: String,
) {
    fun toFoodItem(): FoodItem {
        val id = if (tfndId.isNotBlank()) "tfnd:$tfndId" else "portion:${name.hashCode()}"
        val unitHint = units.filter { it.unit != "公克" }.joinToString("、") {
            "1${it.unit}≈${it.grams.toInt()}g"
        }
        val aliasExtra = listOf(aliases, unitHint).filter { it.isNotBlank() }.joinToString("；")
        return FoodItem(
            id = id,
            name = name,
            alias = aliasExtra,
            category = "常見份量",
            kcalPer100 = kcalPer100,
            proteinPer100 = proteinPer100,
            custom = false,
            servingG = units.firstOrNull { it.unit != "公克" }?.grams,
            note = note,
        )
    }
}

object Portions {
    const val ATTRIBUTION =
        "常見份量：依國健署食品交換表與台灣日常／超商規格約值；營養優先食藥署 TFND，無對應者以合理估計標示。"

    @Volatile private var cache: List<PortionFood>? = null

    fun all(ctx: Context): List<PortionFood> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            data class Acc(
                val name: String,
                val aliases: String,
                val tfndId: String,
                val units: ArrayList<PortionUnit>,
                val kcalPer100: Double,
                val proteinPer100: Double,
                val note: String,
            )
            val map = LinkedHashMap<String, Acc>()
            ctx.assets.open("portions.csv").use { inp ->
                BufferedReader(InputStreamReader(inp, StandardCharsets.UTF_8)).useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val c = Tfnd.parseCsv(line)
                        if (c.size < 8) return@forEach
                        val name = c[0].trim()
                        val aliases = c[1].trim()
                        val tfndId = c[2].trim()
                        val unit = c[3].trim()
                        val grams = c[4].trim().toDoubleOrNull() ?: return@forEach
                        val kcal = c[5].trim().toDoubleOrNull() ?: return@forEach
                        val pro = c[6].trim().toDoubleOrNull() ?: 0.0
                        val note = c[7].trim().trim('"')
                        val existing = map[name]
                        if (existing == null) {
                            map[name] = Acc(
                                name, aliases, tfndId,
                                arrayListOf(PortionUnit(unit, grams)),
                                kcal, pro, note,
                            )
                        } else {
                            existing.units.add(PortionUnit(unit, grams))
                        }
                    }
                }
            }
            val list = map.values.map {
                PortionFood(it.name, it.aliases, it.tfndId, it.units.toList(), it.kcalPer100, it.proteinPer100, it.note)
            }
            cache = list
            return list
        }
    }

    fun findByName(ctx: Context, name: String): PortionFood? =
        all(ctx).firstOrNull { it.name == name }

    fun findForFood(ctx: Context, food: FoodItem): PortionFood? {
        findByName(ctx, food.name)?.let { return it }
        val tid = food.id.removePrefix("tfnd:")
        if (food.id.startsWith("tfnd:")) {
            all(ctx).firstOrNull { it.tfndId == tid }?.let { return it }
        }
        val q = food.name.lowercase(Locale.ROOT)
        return all(ctx).firstOrNull { p ->
            val pn = p.name.lowercase(Locale.ROOT)
            pn in q || q in pn ||
                p.aliases.split(',', '，', ';', '；').map { it.trim().lowercase(Locale.ROOT) }
                    .any { it.isNotEmpty() && (it in q || q in it) }
        }
    }

    fun search(ctx: Context, q: String, limit: Int = 40): List<PortionFood> {
        val query = q.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) return all(ctx).take(limit)
        return all(ctx).asSequence()
            .map { p ->
                val name = p.name.lowercase(Locale.ROOT)
                val alias = p.aliases.lowercase(Locale.ROOT)
                val score = when {
                    name == query -> 0
                    name.startsWith(query) -> 1
                    name.contains(query) -> 2
                    alias.contains(query) -> 10
                    else -> Int.MAX_VALUE
                }
                score to p
            }
            .filter { it.first < Int.MAX_VALUE }
            .sortedWith(compareBy({ it.first }, { it.second.name.length }))
            .map { it.second }
            .take(limit)
            .toList()
    }

    fun count(ctx: Context): Int = all(ctx).size
}

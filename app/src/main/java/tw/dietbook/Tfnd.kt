package tw.dietbook

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

object Tfnd {
    const val ATTRIBUTION =
        "資料來源：衛生福利部食品藥物管理署「食品營養成分資料庫」／政府資料開放授權條款－第1版"

    @Volatile private var cache: List<FoodItem>? = null

    fun invalidate() { cache = null }

    fun updatedFile(ctx: Context): File = File(TraceIndex.dir(ctx), "tfnd.csv")

    fun all(ctx: Context): List<FoodItem> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val list = ArrayList<FoodItem>(2400)
            openCsv(ctx).use { inp ->
                BufferedReader(InputStreamReader(inp, StandardCharsets.UTF_8)).useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val c = parseCsv(line)
                        if (c.size >= 7) {
                            val kcal = c[5].trim().toDoubleOrNull() ?: return@forEach
                            val pro = c[6].trim().toDoubleOrNull() ?: 0.0
                            list.add(
                                FoodItem(
                                    id = "tfnd:" + c[0].trim(),
                                    name = c[1].trim(),
                                    alias = c[2].trim().trim('"'),
                                    category = c[4].trim(),
                                    kcalPer100 = kcal,
                                    proteinPer100 = pro,
                                    custom = false,
                                )
                            )
                        }
                    }
                }
            }
            cache = list
            return list
        }
    }

    private fun openCsv(ctx: Context): InputStream {
        val f = updatedFile(ctx)
        return if (f.exists() && f.length() > 100) FileInputStream(f)
        else ctx.assets.open("tfnd.csv")
    }

    fun search(ctx: Context, q: String, custom: List<FoodItem>, limit: Int = 60): List<FoodItem> {
        val query = q.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) {
            return custom.take(20) + all(ctx).take(40)
        }
        val scored = ArrayList<Pair<Int, FoodItem>>(128)
        fun consider(f: FoodItem) {
            val name = f.name.lowercase(Locale.ROOT)
            val alias = f.alias.lowercase(Locale.ROOT)
            val cat = f.category.lowercase(Locale.ROOT)
            var score = Int.MAX_VALUE
            if (name == query) score = 0
            else if (name.startsWith(query)) score = 1
            else if (name.contains(query)) score = 2 + name.indexOf(query).coerceAtMost(20)
            else if (alias.contains(query)) score = 40 + alias.indexOf(query).coerceAtMost(20)
            else if (cat.contains(query)) score = 80
            else {
                if (subseq(name, query) || subseq(alias, query)) score = 60
            }
            if (score < Int.MAX_VALUE) {
                if (f.custom) score -= 5
                scored.add(score to f)
            }
        }
        custom.forEach(::consider)
        // 追溯系統名稱命中（離線）
        TraceIndex.search(ctx, q, 15).forEach(::consider)
        all(ctx).forEach(::consider)
        return scored.sortedWith(compareBy({ it.first }, { it.second.name.length }))
            .asSequence().map { it.second }.distinctBy { it.id }.take(limit).toList()
    }

    private fun subseq(text: String, q: String): Boolean {
        var i = 0
        for (ch in text) {
            if (i < q.length && ch == q[i]) i++
        }
        return i == q.length && q.length >= 2
    }

    /** 簡易 CSV：支援雙引號欄位 */
    fun parseCsv(line: String): List<String> {
        val out = ArrayList<String>(8)
        val sb = StringBuilder()
        var i = 0
        var inQ = false
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQ && i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ }
                    else inQ = !inQ
                }
                c == ',' && !inQ -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}

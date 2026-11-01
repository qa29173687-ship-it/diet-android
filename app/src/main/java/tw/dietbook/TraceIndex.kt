package tw.dietbook

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * 食藥署「食品追溯追蹤系統」opendata（dataset 33575 / export 188）濃縮索引。
 * 約 1 萬筆有營養者：barcode（稀疏）→ name／kcal／protein。
 * 市售 EAN 欄位並未出現在公開 CSV；串接碼不是條碼，故條碼命中率低，主要作離線名稱／未來擴充。
 */
object TraceIndex {
    const val ATTRIBUTION =
        "資料來源：衛生福利部食品藥物管理署「食品追溯追蹤系統」開放資料（data.gov.tw／33575）／政府資料開放授權條款－第1版"

    data class Row(
        val code: String,
        val name: String,
        val company: String,
        val basis: String,
        val kcal: Double,
        val protein: Double,
        val servingG: Double?,
        val servingsPerPack: Double?,
        val kcalServing: Double?,
        val proteinServing: Double?,
        val barcode: String,
    )

    @Volatile private var rows: List<Row>? = null
    @Volatile private var byBarcode: Map<String, Row>? = null
    @Volatile private var byCode: Map<String, Row>? = null

    fun dir(ctx: Context): File = File(ctx.filesDir, "data").also { it.mkdirs() }

    fun nutritionFile(ctx: Context): File = File(dir(ctx), "trace-nutrition.jsonl.gz")
    fun barcodesFile(ctx: Context): File = File(dir(ctx), "trace-barcodes.tsv.gz")

    fun invalidate() {
        rows = null; byBarcode = null; byCode = null
    }

    fun ensureLoaded(ctx: Context) {
        rows?.let { return }
        synchronized(this) {
            rows?.let { return }
            val list = ArrayList<Row>(12000)
            val bcMap = HashMap<String, Row>(64)
            try {
                openNutrition(ctx)?.use { raw ->
                    BufferedReader(InputStreamReader(raw, Charsets.UTF_8)).useLines { lines ->
                        lines.forEach { line ->
                            if (line.isBlank()) return@forEach
                            try {
                                val o = JSONObject(line)
                                val row = Row(
                                    code = o.getString("c"),
                                    name = o.getString("n"),
                                    company = o.optString("co"),
                                    basis = o.optString("b", "per100"),
                                    kcal = o.getDouble("k"),
                                    protein = o.optDouble("p", 0.0),
                                    servingG = if (o.has("sg")) o.getDouble("sg") else null,
                                    servingsPerPack = if (o.has("sp")) o.getDouble("sp") else null,
                                    kcalServing = if (o.has("ks")) o.getDouble("ks") else null,
                                    proteinServing = if (o.has("ps")) o.getDouble("ps") else null,
                                    barcode = o.optString("bc"),
                                )
                                list.add(row)
                                if (row.barcode.isNotBlank()) bcMap[row.barcode] = row
                            } catch (_: Exception) { }
                        }
                    }
                }
                openBarcodes(ctx)?.use { raw ->
                    BufferedReader(InputStreamReader(raw, Charsets.UTF_8)).useLines { lines ->
                        lines.forEach { line ->
                            val parts = line.split('\t')
                            if (parts.size >= 2) {
                                val bc = parts[0].trim()
                                val code = parts[1].trim()
                                if (bc.isNotEmpty() && code.isNotEmpty()) {
                                    list.find { it.code == code }?.let { bcMap[bc] = it }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TraceIndex", "ensureLoaded failed", e)
            }
            rows = list
            byBarcode = bcMap
            byCode = list.associateBy { it.code }
        }
    }

    /**
     * AGP 預設會把 assets 裡的 .gz 解成無副檔名壓縮檔（APK 內變成 trace-nutrition.jsonl）。
     * 下載到 filesDir 的仍可能是真正的 gzip。兩者都要能開，且絕不可讓未捕捉例外打爆 UI。
     */
    private fun maybeGunzip(stream: InputStream): InputStream {
        val buffered = if (stream.markSupported()) stream else java.io.BufferedInputStream(stream)
        buffered.mark(4)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()
        return if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(buffered) else buffered
    }

    private fun openAssetPreferGzip(ctx: Context, gzName: String, plainName: String): InputStream? {
        for (name in listOf(gzName, plainName)) {
            try {
                return maybeGunzip(ctx.assets.open(name))
            } catch (_: Exception) { }
        }
        return null
    }

    private fun openNutrition(ctx: Context): InputStream? {
        val f = nutritionFile(ctx)
        if (f.exists() && f.length() > 0) {
            return try { maybeGunzip(f.inputStream()) } catch (_: Exception) { null }
        }
        return openAssetPreferGzip(ctx, "trace-nutrition.jsonl.gz", "trace-nutrition.jsonl")
    }

    private fun openBarcodes(ctx: Context): InputStream? {
        val f = barcodesFile(ctx)
        if (f.exists() && f.length() > 40) {
            return try { maybeGunzip(f.inputStream()) } catch (_: Exception) { null }
        }
        return openAssetPreferGzip(ctx, "trace-barcodes.tsv.gz", "trace-barcodes.tsv")
    }

    fun findByBarcode(ctx: Context, barcode: String): Row? {
        ensureLoaded(ctx)
        val bc = barcode.trim()
        if (bc.isEmpty()) return null
        byBarcode?.get(bc)?.let { return it }
        // 容錯：去掉前導 0
        if (bc.startsWith("0") && bc.length > 8) return byBarcode?.get(bc.trimStart('0'))
        return null
    }

    fun findById(ctx: Context, id: String): FoodItem? {
        if (!id.startsWith("trace:")) return null
        ensureLoaded(ctx)
        val code = id.removePrefix("trace:")
        val r = byCode?.get(code) ?: return null
        return toFood(r)
    }

    fun toFood(r: Row): FoodItem {
        val (k100, p100) = when {
            r.basis == "perServing" && r.servingG != null && r.servingG > 0 -> {
                val k = (r.kcalServing ?: r.kcal) * 100.0 / r.servingG
                val p = (r.proteinServing ?: r.protein) * 100.0 / r.servingG
                k to p
            }
            else -> r.kcal to r.protein
        }
        return FoodItem(
            id = "trace:" + r.code,
            name = r.name,
            alias = r.company,
            category = "追溯系統",
            kcalPer100 = k100,
            proteinPer100 = p100,
            custom = false,
            servingG = r.servingG,
            servingsPerPack = r.servingsPerPack,
            basis = r.basis,
            barcode = r.barcode,
            note = ATTRIBUTION,
        )
    }

    fun toHit(barcode: String, r: Row): BarcodeHit {
        return BarcodeHit(
            barcode = barcode,
            source = "tfda",
            sourceLabel = "食藥署追溯系統",
            name = r.name,
            kcalPer100 = if (r.basis == "per100") r.kcal else null,
            proteinPer100 = if (r.basis == "per100") r.protein else null,
            kcalPerServing = r.kcalServing ?: if (r.basis == "perServing") r.kcal else null,
            proteinPerServing = r.proteinServing ?: if (r.basis == "perServing") r.protein else null,
            servingG = r.servingG,
            servingsPerPack = r.servingsPerPack,
            basis = r.basis,
            note = listOfNotNull(r.company.takeIf { it.isNotBlank() }, ATTRIBUTION).joinToString("\n"),
        )
    }

    fun search(ctx: Context, q: String, limit: Int = 20): List<FoodItem> {
        ensureLoaded(ctx)
        val query = q.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) return emptyList()
        val all = rows ?: return emptyList()
        return all.asSequence()
            .filter { it.name.lowercase(Locale.ROOT).contains(query) || it.company.lowercase(Locale.ROOT).contains(query) }
            .take(limit)
            .map { toFood(it) }
            .toList()
    }

    fun size(ctx: Context): Int {
        ensureLoaded(ctx)
        return rows?.size ?: 0
    }

    fun barcodeCount(ctx: Context): Int {
        ensureLoaded(ctx)
        return byBarcode?.size ?: 0
    }
}

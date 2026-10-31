package tw.dietbook

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object BarcodeLookup {
    private const val UA =
        "DietBook/1.1.0 (Android; https://github.com/qa29173687-ship-it/diet-android)"

    /**
     * 查詢順序：
     * (a) 本機已存條碼的自訂食物
     * (b) TFDA 追溯濃縮索引（條碼鍵；公開資料幾乎無 EAN）
     * (c) Open Food Facts API
     */
    fun lookup(ctx: Context, rawCode: String): BarcodeHit? {
        val code = normalize(rawCode) ?: return null
        Store.findByBarcode(ctx, code)?.let { f ->
            return BarcodeHit(
                barcode = code,
                source = "local",
                sourceLabel = "本機自訂食物",
                name = f.name,
                kcalPer100 = f.kcalPer100,
                proteinPer100 = f.proteinPer100,
                servingG = f.servingG,
                servingsPerPack = f.servingsPerPack,
                basis = "per100",
                note = f.note,
            )
        }
        TraceIndex.findByBarcode(ctx, code)?.let { return TraceIndex.toHit(code, it) }
        return openFoodFacts(code)
    }

    fun normalize(raw: String): String? {
        val d = raw.trim().filter { it.isDigit() }
        if (d.length < 8) return null
        return d
    }

    private fun openFoodFacts(code: String): BarcodeHit? {
        val url = URL("https://world.openfoodfacts.org/api/v2/product/$code?fields=code,product_name,product_name_zh,product_name_en,brands,nutriments,serving_size,serving_quantity")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            if (root.optInt("status", 0) != 1) return null
            val p = root.optJSONObject("product") ?: return null
            val name = sequenceOf(
                p.optString("product_name_zh"),
                p.optString("product_name"),
                p.optString("product_name_en"),
                p.optString("brands"),
            ).map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
            val n = p.optJSONObject("nutriments") ?: JSONObject()
            val kcal100 = firstNum(n, "energy-kcal_100g", "energy-kcal_value")
                ?: firstNum(n, "energy_100g")?.let { it / 4.184 }
            val pro100 = firstNum(n, "proteins_100g", "proteins_value")
            val kcalServ = firstNum(n, "energy-kcal_serving")
            val proServ = firstNum(n, "proteins_serving")
            val servingG = p.optDouble("serving_quantity").takeIf { p.has("serving_quantity") && it > 0 }
                ?: parseServingG(p.optString("serving_size"))
            if (kcal100 == null && kcalServ == null) return null
            val basis = if (kcal100 != null) "per100" else "perServing"
            return BarcodeHit(
                barcode = code,
                source = "off",
                sourceLabel = "Open Food Facts",
                name = name,
                kcalPer100 = kcal100,
                proteinPer100 = pro100,
                kcalPerServing = kcalServ,
                proteinPerServing = proServ,
                servingG = servingG,
                basis = basis,
                note = "來源：Open Food Facts（社群資料，請核對標示）",
            )
        } catch (_: Exception) {
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun firstNum(o: JSONObject, vararg keys: String): Double? {
        for (k in keys) {
            if (o.has(k) && !o.isNull(k)) {
                val v = o.optDouble(k, Double.NaN)
                if (!v.isNaN()) return v
            }
        }
        return null
    }

    private fun parseServingG(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        val m = Regex("""([\d.]+)\s*(g|G|公克|ml|ML|毫升)""", RegexOption.IGNORE_CASE).find(s) ?: return null
        return m.groupValues[1].toDoubleOrNull()
    }

    fun sourceBadge(source: String): String = when (source) {
        "local" -> "本機"
        "tfda" -> "食藥署"
        "off" -> "Open Food Facts"
        else -> source.uppercase(Locale.ROOT)
    }
}

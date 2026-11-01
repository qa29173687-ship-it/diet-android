package tw.dietbook

import org.json.JSONObject

object FoodCodec {
    fun toJson(f: FoodItem): String = JSONObject().apply {
        put("id", f.id); put("name", f.name); put("alias", f.alias)
        put("category", f.category); put("kcalPer100", f.kcalPer100)
        put("proteinPer100", f.proteinPer100); put("custom", f.custom)
        put("basis", f.basis); put("note", f.note); put("barcode", f.barcode)
        if (f.servingG != null) put("servingG", f.servingG) else put("servingG", JSONObject.NULL)
        if (f.servingsPerPack != null) put("servingsPerPack", f.servingsPerPack) else put("servingsPerPack", JSONObject.NULL)
    }.toString()

    fun fromJson(raw: String?): FoodItem? {
        if (raw.isNullOrBlank()) return null
        return try {
            val o = JSONObject(raw)
            FoodItem(
                id = o.getString("id"),
                name = o.getString("name"),
                alias = o.optString("alias"),
                category = o.optString("category"),
                kcalPer100 = o.getDouble("kcalPer100"),
                proteinPer100 = o.getDouble("proteinPer100"),
                custom = o.optBoolean("custom", false),
                servingG = o.optDouble("servingG").takeIf { o.has("servingG") && !o.isNull("servingG") },
                servingsPerPack = o.optDouble("servingsPerPack").takeIf { o.has("servingsPerPack") && !o.isNull("servingsPerPack") },
                basis = o.optString("basis", "per100"),
                note = o.optString("note"),
                barcode = o.optString("barcode"),
            )
        } catch (_: Exception) { null }
    }
}

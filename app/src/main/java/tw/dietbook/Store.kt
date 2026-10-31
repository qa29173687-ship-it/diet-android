package tw.dietbook

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Store {
    private const val PREF = "dietbook"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun profile(ctx: Context): UserProfile {
        val s = p(ctx)
        return UserProfile(
            setupDone = s.getBoolean("setupDone", false),
            sex = s.getString("sex", "male") ?: "male",
            age = s.getInt("age", 30),
            heightCm = s.getFloat("heightCm", 170f).toDouble(),
            weightKg = s.getFloat("weightKg", 70f).toDouble(),
            bmr = s.getFloat("bmr", 1600f).toDouble(),
            bmrManual = s.getBoolean("bmrManual", false),
            activity = s.getString("activity", "moderate") ?: "moderate",
            goalKcal = s.getFloat("goalKcal", 2000f).toDouble(),
            goalProtein = s.getFloat("goalProtein", 80f).toDouble(),
            balance = s.getFloat("balance", 0f).toDouble(),
            lastFinalizedDay = s.getString("lastFinalizedDay", "") ?: "",
            remindPayback = s.getBoolean("remindPayback", false),
            paybackDays = s.getInt("paybackDays", 7),
            hcEnabled = s.getBoolean("hcEnabled", true),
        )
    }

    fun saveProfile(ctx: Context, u: UserProfile) {
        p(ctx).edit()
            .putBoolean("setupDone", u.setupDone)
            .putString("sex", u.sex)
            .putInt("age", u.age)
            .putFloat("heightCm", u.heightCm.toFloat())
            .putFloat("weightKg", u.weightKg.toFloat())
            .putFloat("bmr", u.bmr.toFloat())
            .putBoolean("bmrManual", u.bmrManual)
            .putString("activity", u.activity)
            .putFloat("goalKcal", u.goalKcal.toFloat())
            .putFloat("goalProtein", u.goalProtein.toFloat())
            .putFloat("balance", u.balance.toFloat())
            .putString("lastFinalizedDay", u.lastFinalizedDay)
            .putBoolean("remindPayback", u.remindPayback)
            .putInt("paybackDays", u.paybackDays)
            .putBoolean("hcEnabled", u.hcEnabled)
            .apply()
    }

    fun customFoods(ctx: Context): MutableList<FoodItem> {
        val raw = p(ctx).getString("customFoods", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = ArrayList<FoodItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                FoodItem(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    alias = o.optString("alias"),
                    category = o.optString("category", "自訂"),
                    kcalPer100 = o.getDouble("kcalPer100"),
                    proteinPer100 = o.getDouble("proteinPer100"),
                    custom = true,
                    servingG = o.optDouble("servingG").takeIf { o.has("servingG") && !o.isNull("servingG") },
                    servingsPerPack = o.optDouble("servingsPerPack").takeIf { o.has("servingsPerPack") && !o.isNull("servingsPerPack") },
                    basis = o.optString("basis", "per100"),
                    note = o.optString("note"),
                )
            )
        }
        return list
    }

    fun saveCustomFoods(ctx: Context, list: List<FoodItem>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id); put("name", f.name); put("alias", f.alias)
                put("category", f.category); put("kcalPer100", f.kcalPer100)
                put("proteinPer100", f.proteinPer100); put("basis", f.basis); put("note", f.note)
                if (f.servingG != null) put("servingG", f.servingG) else put("servingG", JSONObject.NULL)
                if (f.servingsPerPack != null) put("servingsPerPack", f.servingsPerPack) else put("servingsPerPack", JSONObject.NULL)
            })
        }
        p(ctx).edit().putString("customFoods", arr.toString()).apply()
    }

    fun upsertCustom(ctx: Context, food: FoodItem) {
        val list = customFoods(ctx)
        val i = list.indexOfFirst { it.id == food.id }
        if (i >= 0) list[i] = food else list.add(0, food)
        saveCustomFoods(ctx, list)
    }

    fun deleteCustom(ctx: Context, id: String) {
        saveCustomFoods(ctx, customFoods(ctx).filter { it.id != id })
    }

    fun findFood(ctx: Context, id: String): FoodItem? {
        customFoods(ctx).find { it.id == id }?.let { return it }
        return Tfnd.all(ctx).find { it.id == id }
    }

    fun logs(ctx: Context, day: String): List<FoodLog> {
        val raw = p(ctx).getString("logs_$day", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = ArrayList<FoodLog>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                FoodLog(
                    id = o.getLong("id"),
                    day = o.getString("day"),
                    foodId = o.getString("foodId"),
                    foodName = o.getString("foodName"),
                    grams = o.getDouble("grams"),
                    kcal = o.getDouble("kcal"),
                    protein = o.getDouble("protein"),
                    at = o.getLong("at"),
                )
            )
        }
        return list.sortedByDescending { it.at }
    }

    fun saveLogs(ctx: Context, day: String, list: List<FoodLog>) {
        val arr = JSONArray()
        list.forEach { l ->
            arr.put(JSONObject().apply {
                put("id", l.id); put("day", l.day); put("foodId", l.foodId)
                put("foodName", l.foodName); put("grams", l.grams)
                put("kcal", l.kcal); put("protein", l.protein); put("at", l.at)
            })
        }
        p(ctx).edit().putString("logs_$day", arr.toString()).apply()
    }

    fun addLog(ctx: Context, log: FoodLog) {
        val list = logs(ctx, log.day).toMutableList()
        list.add(0, log)
        saveLogs(ctx, log.day, list)
    }

    fun deleteLog(ctx: Context, day: String, id: Long) {
        saveLogs(ctx, day, logs(ctx, day).filter { it.id != id })
    }

    fun dayTotals(ctx: Context, day: String): Pair<Double, Double> {
        var k = 0.0; var p = 0.0
        logs(ctx, day).forEach { k += it.kcal; p += it.protein }
        return k to p
    }

    fun snapshot(ctx: Context, day: String): DaySnapshot? {
        val raw = p(ctx).getString("snap_$day", null) ?: return null
        val o = JSONObject(raw)
        return DaySnapshot(
            day = o.getString("day"),
            intakeKcal = o.getDouble("intakeKcal"),
            protein = o.getDouble("protein"),
            bmr = o.getDouble("bmr"),
            activeBurn = if (o.isNull("activeBurn")) null else o.getDouble("activeBurn"),
            tdeeFallback = o.getDouble("tdeeFallback"),
            net = o.getDouble("net"),
            steps = if (o.has("steps") && !o.isNull("steps")) o.getLong("steps") else null,
            weightKg = if (o.has("weightKg") && !o.isNull("weightKg")) o.getDouble("weightKg") else null,
            finalized = o.optBoolean("finalized", false),
        )
    }

    fun saveSnapshot(ctx: Context, s: DaySnapshot) {
        val o = JSONObject().apply {
            put("day", s.day); put("intakeKcal", s.intakeKcal); put("protein", s.protein)
            put("bmr", s.bmr)
            if (s.activeBurn == null) put("activeBurn", JSONObject.NULL) else put("activeBurn", s.activeBurn)
            put("tdeeFallback", s.tdeeFallback); put("net", s.net)
            if (s.steps == null) put("steps", JSONObject.NULL) else put("steps", s.steps)
            if (s.weightKg == null) put("weightKg", JSONObject.NULL) else put("weightKg", s.weightKg)
            put("finalized", s.finalized)
        }
        p(ctx).edit().putString("snap_${s.day}", o.toString()).apply()
    }
}

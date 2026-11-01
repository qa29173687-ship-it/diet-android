package tw.dietbook

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Store {
    private const val PREF = "dietbook"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun profile(ctx: Context): UserProfile {
        val s = p(ctx)
        val badgesRaw = s.getString("badges", "") ?: ""
        val badges = badgesRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
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
            streakDays = s.getInt("streakDays", 0),
            lastActiveDay = s.getString("lastActiveDay", "") ?: "",
            badges = badges,
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
            .putInt("streakDays", u.streakDays)
            .putString("lastActiveDay", u.lastActiveDay)
            .putString("badges", u.badges.joinToString(","))
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
                    barcode = o.optString("barcode"),
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
                put("barcode", f.barcode)
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
        if (food.barcode.isNotBlank()) {
            val bc = food.barcode
            val cleaned = list.map { if (it.id != food.id && it.barcode == bc) it.copy(barcode = "") else it }
            saveCustomFoods(ctx, cleaned)
        } else {
            saveCustomFoods(ctx, list)
        }
    }

    fun deleteCustom(ctx: Context, id: String) {
        saveCustomFoods(ctx, customFoods(ctx).filter { it.id != id })
    }

    fun findByBarcode(ctx: Context, barcode: String): FoodItem? {
        val bc = barcode.trim()
        if (bc.isEmpty()) return null
        return customFoods(ctx).find { it.barcode == bc }
    }

    fun findFood(ctx: Context, id: String): FoodItem? {
        customFoods(ctx).find { it.id == id }?.let { return it }
        TraceIndex.findById(ctx, id)?.let { return it }
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
                    id = o.getLong("id"), day = o.getString("day"), foodId = o.getString("foodId"),
                    foodName = o.getString("foodName"), grams = o.getDouble("grams"),
                    kcal = o.getDouble("kcal"), protein = o.getDouble("protein"), at = o.getLong("at"),
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
        touchFeed(ctx)
        bumpStreak(ctx)
    }

    fun deleteLog(ctx: Context, day: String, id: Long) {
        saveLogs(ctx, day, logs(ctx, day).filter { it.id != id })
    }

    fun dayTotals(ctx: Context, day: String): Pair<Double, Double> {
        var k = 0.0; var pr = 0.0
        logs(ctx, day).forEach { k += it.kcal; pr += it.protein }
        return k to pr
    }

    // ---- cardio ----
    fun cardioLogs(ctx: Context, day: String): List<CardioLog> {
        val raw = p(ctx).getString("cardio_$day", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = ArrayList<CardioLog>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                CardioLog(
                    id = o.getLong("id"), day = o.getString("day"), type = o.getString("type"),
                    label = o.getString("label"), minutes = o.getInt("minutes"),
                    kcal = o.getDouble("kcal"), met = o.getDouble("met"), at = o.getLong("at"),
                )
            )
        }
        return list.sortedByDescending { it.at }
    }

    fun saveCardio(ctx: Context, day: String, list: List<CardioLog>) {
        val arr = JSONArray()
        list.forEach { l ->
            arr.put(JSONObject().apply {
                put("id", l.id); put("day", l.day); put("type", l.type); put("label", l.label)
                put("minutes", l.minutes); put("kcal", l.kcal); put("met", l.met); put("at", l.at)
            })
        }
        p(ctx).edit().putString("cardio_$day", arr.toString()).apply()
    }

    fun addCardio(ctx: Context, log: CardioLog) {
        val list = cardioLogs(ctx, log.day).toMutableList()
        list.add(0, log)
        saveCardio(ctx, log.day, list)
        bumpStreak(ctx)
    }

    fun deleteCardio(ctx: Context, day: String, id: Long) {
        saveCardio(ctx, day, cardioLogs(ctx, day).filter { it.id != id })
    }

    fun cardioKcal(ctx: Context, day: String): Double =
        cardioLogs(ctx, day).sumOf { it.kcal }

    // ---- strength ----
    fun strengthLogs(ctx: Context, day: String): List<StrengthLog> {
        val raw = p(ctx).getString("strength_$day", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = ArrayList<StrengthLog>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                StrengthLog(
                    id = o.getLong("id"), day = o.getString("day"), part = o.getString("part"),
                    exercise = o.getString("exercise"), sets = o.getInt("sets"),
                    minutes = o.getInt("minutes"), kcal = o.getDouble("kcal"), at = o.getLong("at"),
                )
            )
        }
        return list.sortedByDescending { it.at }
    }

    fun saveStrength(ctx: Context, day: String, list: List<StrengthLog>) {
        val arr = JSONArray()
        list.forEach { l ->
            arr.put(JSONObject().apply {
                put("id", l.id); put("day", l.day); put("part", l.part); put("exercise", l.exercise)
                put("sets", l.sets); put("minutes", l.minutes); put("kcal", l.kcal); put("at", l.at)
            })
        }
        p(ctx).edit().putString("strength_$day", arr.toString()).apply()
    }

    fun addStrength(ctx: Context, log: StrengthLog) {
        val list = strengthLogs(ctx, log.day).toMutableList()
        list.add(0, log)
        saveStrength(ctx, log.day, list)
        addPartMinutes(ctx, log.part, log.minutes)
        bumpStreak(ctx)
    }

    fun deleteStrength(ctx: Context, day: String, id: Long) {
        // 不回溯部位分鐘，避免刪除造成負數；以累計為主
        saveStrength(ctx, day, strengthLogs(ctx, day).filter { it.id != id })
    }

    fun strengthKcal(ctx: Context, day: String): Double =
        strengthLogs(ctx, day).sumOf { it.kcal }

    fun manualBurn(ctx: Context, day: String): Double =
        cardioKcal(ctx, day) + strengthKcal(ctx, day)

    fun partMinutes(ctx: Context): Map<String, Int> {
        val raw = p(ctx).getString("partMinutes", "{}") ?: "{}"
        val o = JSONObject(raw)
        val map = linkedMapOf<String, Int>()
        Calc.STRENGTH_PARTS.forEach { part ->
            map[part] = o.optInt(part, 0)
        }
        return map
    }

    fun addPartMinutes(ctx: Context, part: String, minutes: Int) {
        val map = partMinutes(ctx).toMutableMap()
        map[part] = (map[part] ?: 0) + minutes.coerceAtLeast(0)
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        p(ctx).edit().putString("partMinutes", o.toString()).apply()
    }

    fun partLevels(ctx: Context): Map<String, Int> =
        partMinutes(ctx).mapValues { Calc.partLevel(it.value) }

    // ---- vitals / feed ----
    fun lastFeedAt(ctx: Context): Long = p(ctx).getLong("lastFeedAt", 0L)

    fun touchFeed(ctx: Context) {
        p(ctx).edit().putLong("lastFeedAt", System.currentTimeMillis()).apply()
    }

    fun lastHungryNotifyAt(ctx: Context): Long = p(ctx).getLong("lastHungryNotifyAt", 0L)

    fun setHungryNotifyAt(ctx: Context, t: Long) {
        p(ctx).edit().putLong("lastHungryNotifyAt", t).apply()
    }

    fun bumpStreak(ctx: Context) {
        val u = profile(ctx)
        val today = Days.today()
        if (u.lastActiveDay == today) return
        val yesterday = Days.yesterdayOf(today)
        val next = if (u.lastActiveDay == yesterday) u.streakDays + 1 else 1
        val badges = u.badges.toMutableSet()
        if (next >= 3) badges += "streak3"
        if (next >= 7) badges += "streak7"
        if (next >= 14) badges += "streak14"
        saveProfile(ctx, u.copy(streakDays = next, lastActiveDay = today, badges = badges))
    }

    fun awardBadge(ctx: Context, id: String) {
        val u = profile(ctx)
        if (id in u.badges) return
        saveProfile(ctx, u.copy(badges = u.badges + id))
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
            manualBurn = o.optDouble("manualBurn", 0.0),
            hcBurn = if (o.has("hcBurn") && !o.isNull("hcBurn")) o.getDouble("hcBurn") else null,
            burnSource = o.optString("burnSource", ""),
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
            put("manualBurn", s.manualBurn)
            if (s.hcBurn == null) put("hcBurn", JSONObject.NULL) else put("hcBurn", s.hcBurn)
            put("burnSource", s.burnSource)
        }
        p(ctx).edit().putString("snap_${s.day}", o.toString()).apply()
    }

    fun dataTfndVersion(ctx: Context): String =
        p(ctx).getString("data_tfnd_ver", "bundled") ?: "bundled"

    fun dataTraceVersion(ctx: Context): String =
        p(ctx).getString("data_trace_ver", "bundled-v1") ?: "bundled-v1"

    fun dataLastChecked(ctx: Context): Long = p(ctx).getLong("data_last_checked", 0L)

    fun dataLastUpdated(ctx: Context): Long = p(ctx).getLong("data_last_updated", 0L)

    fun dataTfndHash(ctx: Context): String = p(ctx).getString("data_tfnd_hash", "") ?: ""

    fun dataTraceHash(ctx: Context): String = p(ctx).getString("data_trace_hash", "") ?: ""

    fun setDataMeta(
        ctx: Context,
        tfndVer: String? = null,
        traceVer: String? = null,
        tfndHash: String? = null,
        traceHash: String? = null,
        lastChecked: Long? = null,
        lastUpdated: Long? = null,
    ) {
        val e = p(ctx).edit()
        tfndVer?.let { e.putString("data_tfnd_ver", it) }
        traceVer?.let { e.putString("data_trace_ver", it) }
        tfndHash?.let { e.putString("data_tfnd_hash", it) }
        traceHash?.let { e.putString("data_trace_hash", it) }
        lastChecked?.let { e.putLong("data_last_checked", it) }
        lastUpdated?.let { e.putLong("data_last_updated", it) }
        e.apply()
    }

    // —— v2.2 cute prefs ——
    fun sfxMuted(ctx: Context): Boolean = p(ctx).getBoolean("sfxMuted", false)
    fun setSfxMuted(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("sfxMuted", v).apply() }

    fun selectedOutfit(ctx: Context): OutfitId {
        val raw = p(ctx).getString("outfitSelected", OutfitId.NONE.name) ?: OutfitId.NONE.name
        return runCatching { OutfitId.valueOf(raw) }.getOrDefault(OutfitId.NONE)
    }
    fun setSelectedOutfit(ctx: Context, id: OutfitId) {
        p(ctx).edit().putString("outfitSelected", id.name).apply()
    }
    fun unlockedOutfits(ctx: Context): Set<OutfitId> {
        val raw = p(ctx).getString("outfitsUnlocked", "") ?: ""
        return raw.split(',').mapNotNull { s ->
            val n = s.trim(); if (n.isEmpty()) null else runCatching { OutfitId.valueOf(n) }.getOrNull()
        }.toSet()
    }
    fun setUnlockedOutfits(ctx: Context, set: Set<OutfitId>) {
        p(ctx).edit().putString("outfitsUnlocked", set.joinToString(",") { it.name }).apply()
    }

    fun weather(ctx: Context): WeatherSnap {
        val s = p(ctx)
        return WeatherSnap(
            code = s.getInt("wxCode", -1),
            tempC = s.getFloat("wxTemp", Float.NaN).toDouble(),
            at = s.getLong("wxAt", 0L),
        )
    }
    fun setWeather(ctx: Context, w: WeatherSnap) {
        p(ctx).edit()
            .putInt("wxCode", w.code)
            .putFloat("wxTemp", w.tempC.toFloat())
            .putLong("wxAt", w.at)
            .apply()
    }

    fun lastBmrCoinDay(ctx: Context): String = p(ctx).getString("lastBmrCoinDay", "") ?: ""
    fun setLastBmrCoinDay(ctx: Context, day: String) {
        p(ctx).edit().putString("lastBmrCoinDay", day).apply()
    }

    fun deficitKgCelebrated(ctx: Context): Int = p(ctx).getInt("deficitKgCelebrated", 0)
    fun setDeficitKgCelebrated(ctx: Context, n: Int) {
        p(ctx).edit().putInt("deficitKgCelebrated", n).apply()
    }

    fun lastOpenDay(ctx: Context): String = p(ctx).getString("lastOpenDay", "") ?: ""
    fun setLastOpenDay(ctx: Context, day: String) {
        p(ctx).edit().putString("lastOpenDay", day).apply()
    }

    fun lastAwayMs(ctx: Context): Long = p(ctx).getLong("lastAwayMs", 0L)
    fun setLastAwayMs(ctx: Context, ms: Long) {
        p(ctx).edit().putLong("lastAwayMs", ms).apply()
    }

    fun lastInteractMs(ctx: Context): Long = p(ctx).getLong("lastInteractMs", 0L)
    fun setLastInteractMs(ctx: Context, ms: Long) {
        p(ctx).edit().putLong("lastInteractMs", ms).apply()
    }

    fun posedParts(ctx: Context): Set<String> {
        val raw = p(ctx).getString("posedParts", "") ?: ""
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    fun markPosedPart(ctx: Context, part: String) {
        val set = posedParts(ctx).toMutableSet()
        set += part
        p(ctx).edit().putString("posedParts", set.joinToString(",")).apply()
    }

    fun queueCute(ctx: Context, pending: PendingCute) {
        val arr = JSONArray(p(ctx).getString("cuteQueue", "[]") ?: "[]")
        arr.put(JSONObject().apply {
            put("scene", pending.scene.name)
            put("kcal", pending.kcal)
            put("protein", pending.protein)
            put("label", pending.label)
        })
        p(ctx).edit().putString("cuteQueue", arr.toString()).apply()
    }
    fun peekCute(ctx: Context): PendingCute? {
        val arr = JSONArray(p(ctx).getString("cuteQueue", "[]") ?: "[]")
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return PendingCute(
            scene = CuteScene.valueOf(o.getString("scene")),
            kcal = o.optDouble("kcal", 0.0),
            protein = o.optDouble("protein", 0.0),
            label = o.optString("label", ""),
        )
    }
    fun popCute(ctx: Context): PendingCute? {
        val arr = JSONArray(p(ctx).getString("cuteQueue", "[]") ?: "[]")
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        val next = JSONArray()
        for (i in 1 until arr.length()) next.put(arr.get(i))
        p(ctx).edit().putString("cuteQueue", next.toString()).apply()
        return PendingCute(
            scene = CuteScene.valueOf(o.getString("scene")),
            kcal = o.optDouble("kcal", 0.0),
            protein = o.optDouble("protein", 0.0),
            label = o.optString("label", ""),
        )
    }

}

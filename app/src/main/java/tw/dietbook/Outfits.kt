package tw.dietbook

import android.content.Context
import android.location.LocationManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone
import kotlin.concurrent.thread

enum class OutfitId(val label: String, val emoji: String) {
    NONE("平常", "🐷"),
    SPORTS("運動服", "🏃"),
    BIB("圍兜兜", "🍼"),
    CHEF("廚師帽", "👨‍🍳"),
    UMBRELLA("下雨傘", "☔"),
    ICE_CREAM("冰淇淋", "🍦"),
    CNY_RED("新年紅裝", "🧧"),
    SANTA("聖誕帽", "🎄"),
    MOONCAKE("中秋月餅", "🥮"),
}

data class WeatherSnap(
    val code: Int = -1,
    val tempC: Double = Double.NaN,
    val at: Long = 0L,
)

object Outfits {
    val unlockable = listOf(OutfitId.SPORTS, OutfitId.BIB, OutfitId.CHEF)

    fun holidayOutfit(now: Calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"))): OutfitId? {
        val m = now.get(Calendar.MONTH) + 1
        val d = now.get(Calendar.DAY_OF_MONTH)
        // 聖誕：12/20–12/26
        if (m == 12 && d in 20..26) return OutfitId.SANTA
        // 農曆新年粗估視窗（2025–2027 常見落在 1 月中旬～2 月中旬）
        if ((m == 1 && d >= 15) || (m == 2 && d <= 20)) return OutfitId.CNY_RED
        // 中秋粗估：9/10–10/10
        if ((m == 9 && d >= 10) || (m == 10 && d <= 10)) return OutfitId.MOONCAKE
        return null
    }

    fun weatherOutfit(w: WeatherSnap): OutfitId? {
        if (w.code < 0) return null
        // Open-Meteo WMO: 51–67,80–82 rain; 71–77,85–86 snow → umbrella
        val raining = w.code in 51..67 || w.code in 80..82 || w.code in 71..77 || w.code in 85..86
        if (raining) return OutfitId.UMBRELLA
        if (!w.tempC.isNaN() && w.tempC >= 30.0) return OutfitId.ICE_CREAM
        return null
    }

    /** Priority: holiday > weather prop > wardrobe selection. */
    fun resolve(ctx: Context): OutfitId {
        holidayOutfit()?.let { return it }
        weatherOutfit(Store.weather(ctx))?.let { return it }
        val sel = Store.selectedOutfit(ctx)
        val unlocked = Store.unlockedOutfits(ctx)
        return if (sel in unlocked || sel == OutfitId.NONE) sel else OutfitId.NONE
    }

    fun maybeUnlockFromStreak(ctx: Context) {
        val streak = Store.profile(ctx).streakDays
        if (streak < 7) return
        val set = Store.unlockedOutfits(ctx).toMutableSet()
        var changed = false
        unlockable.forEach {
            if (it !in set) {
                set += it
                changed = true
            }
        }
        if (changed) {
            Store.setUnlockedOutfits(ctx, set)
            Store.awardBadge(ctx, "wardrobe7")
        }
    }

    fun refreshWeatherAsync(ctx: Context, onDone: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        thread {
            try {
                val cached = Store.weather(app)
                if (System.currentTimeMillis() - cached.at < 45 * 60_000L && cached.code >= 0) {
                    onDone?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
                    return@thread
                }
                val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val loc = try {
                    lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                } catch (_: SecurityException) {
                    null
                } catch (_: Exception) {
                    null
                }
                // Taipei default if no permission / no fix — still cute seasonal weather
                val lat = loc?.latitude ?: 25.03
                val lon = loc?.longitude ?: 121.56
                val url =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=weather_code,temperature_2m&timezone=Asia%2FTaipei"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                }
                conn.inputStream.bufferedReader().use { reader ->
                    val o = JSONObject(reader.readText())
                    val cur = o.getJSONObject("current")
                    Store.setWeather(
                        app,
                        WeatherSnap(
                            code = cur.getInt("weather_code"),
                            tempC = cur.getDouble("temperature_2m"),
                            at = System.currentTimeMillis(),
                        )
                    )
                }
                conn.disconnect()
            } catch (_: Exception) {
                // keep previous / none
            }
            onDone?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
        }
    }
}

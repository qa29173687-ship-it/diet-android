package tw.dietbook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class HealthDay(
    val activeKcal: Double?,
    val steps: Long?,
    val weightKg: Double?,
    val available: Boolean,
    val permissionGranted: Boolean,
    val message: String = "",
)

object Health {
    val perms = setOf(
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    fun status(ctx: Context): Int = HealthConnectClient.getSdkStatus(ctx)

    fun available(ctx: Context): Boolean =
        status(ctx) == HealthConnectClient.SDK_AVAILABLE

    fun clientOrNull(ctx: Context): HealthConnectClient? =
        if (available(ctx)) HealthConnectClient.getOrCreate(ctx) else null

    fun installIntent(): Intent {
        val pkg = "com.google.android.apps.healthdata"
        return Intent(Intent.ACTION_VIEW).setData(
            Uri.parse("market://details?id=$pkg")
        ).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun granted(ctx: Context): Boolean {
        val c = clientOrNull(ctx) ?: return false
        return c.permissionController.getGrantedPermissions().containsAll(perms)
    }

    suspend fun readToday(ctx: Context): HealthDay {
        if (!Store.profile(ctx).hcEnabled) {
            return HealthDay(null, null, null, available(ctx), false, "已在設定關閉 Health Connect")
        }
        if (!available(ctx)) {
            val msg = when (status(ctx)) {
                HealthConnectClient.SDK_UNAVAILABLE -> "此裝置不支援 Health Connect"
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "請先安裝 Health Connect"
                else -> "Health Connect 不可用"
            }
            return HealthDay(null, null, null, false, false, msg)
        }
        val c = clientOrNull(ctx)!!
        if (!granted(ctx)) {
            return HealthDay(null, null, null, true, false, "尚未授權讀取活動熱量／步數／體重")
        }
        val day = LocalDate.now(Days.zone)
        val start = day.atStartOfDay(Days.zone).toInstant()
        val end = day.atTime(LocalTime.MAX).atZone(Days.zone).toInstant()
        val range = TimeRangeFilter.between(start, end)
        return try {
            val agg = c.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        StepsRecord.COUNT_TOTAL,
                    ),
                    timeRangeFilter = range,
                )
            )
            val active = agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            val steps = agg[StepsRecord.COUNT_TOTAL]
            val weight = readLatestWeight(c, start, end)
            HealthDay(active, steps, weight, true, true, "已讀取 Health Connect")
        } catch (e: Exception) {
            HealthDay(null, null, null, true, true, "讀取失敗：${e.message}")
        }
    }

    private suspend fun readLatestWeight(c: HealthConnectClient, start: Instant, end: Instant): Double? {
        return try {
            val res = c.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start.minusSeconds(86400L * 30), end),
                )
            )
            res.records.maxByOrNull { it.time }?.weight?.inKilograms
        } catch (_: Exception) { null }
    }

    fun needsAndroid14Note(): Boolean = Build.VERSION.SDK_INT < 34
}

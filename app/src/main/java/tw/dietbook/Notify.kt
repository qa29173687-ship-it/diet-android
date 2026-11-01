package tw.dietbook

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

object Notify {
    const val CH = "payback"
    const val CH_PET = "piggy"
    const val CH_SUMMARY = "daily_summary"
    const val ID = 42
    const val ID_HUNGRY = 43
    const val ID_SUMMARY = 44

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH, "存錢筒提醒", NotificationManager.IMPORTANCE_DEFAULT)
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_PET, "小豬動態", NotificationManager.IMPORTANCE_DEFAULT)
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_SUMMARY, "每日總結", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun scheduleDaily(ctx: Context, enable: Boolean) {
        ensureChannel(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        if (!enable) return
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Taipei")).apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        } catch (_: Exception) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    /** Evening wrap-up ~21:30 Asia/Taipei — opens 每日總結. */
    fun scheduleDailySummary(ctx: Context) {
        if (!Store.profile(ctx).setupDone) return
        ensureChannel(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 3, Intent(ctx, SummaryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Taipei")).apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        } catch (_: Exception) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) } catch (_: Exception) {}
        }
    }

    fun scheduleHungryCheck(ctx: Context) {
        ensureChannel(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, HungryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Taipei")).apply {
            add(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.SECOND, 0)
        }
        try {
            am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_HOUR * 3, pi)
        } catch (_: Exception) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) } catch (_: Exception) {}
        }
    }

    fun showPayback(ctx: Context) {
        ensureChannel(ctx)
        val u = Store.profile(ctx)
        if (!u.remindPayback || u.balance >= 0) return
        val debt = -u.balance
        val per = Calc.paybackPerDay(debt, u.paybackDays.coerceAtLeast(1))
        val text = "累計進帳 ${Calc.r0(debt)} kcal（約 ${"%.2f".format(Calc.fatKg(debt))} kg）。" +
            "若 ${u.paybackDays} 天打平，每天約需再少 ${Calc.r0(per)} kcal。此為估算。"
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("熱量存錢筒・撲滿提醒")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(ID, n) } catch (_: SecurityException) {}
    }

    fun showHungry(ctx: Context) {
        ensureChannel(ctx)
        if (!Store.profile(ctx).setupDone) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH_PET)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("小豬肚子餓了…")
            .setContentText("好久沒記飲食，點「吃」餵一下小豬吧～")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(ID_HUNGRY, n) } catch (_: SecurityException) {}
    }

    fun showDailySummary(ctx: Context, day: String = Days.today()) {
        ensureChannel(ctx)
        if (!Store.profile(ctx).setupDone) return
        val open = PendingIntent.getActivity(
            ctx, 4,
            DailySummaryActivity.intent(ctx, day).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🐷 每日總結好了")
            .setContentText("點開看看今天吃了多少、小豬怎麼說～")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(ID_SUMMARY, n) } catch (_: SecurityException) {}
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Notify.showPayback(context)
        if (Store.profile(context).remindPayback) Notify.scheduleDaily(context, true)
    }
}

class HungryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val last = Store.lastFeedAt(context)
        val hours = if (last <= 0L) 99.0 else (System.currentTimeMillis() - last) / 3600000.0
        if (hours >= 7) {
            val lastN = Store.lastHungryNotifyAt(context)
            if (System.currentTimeMillis() - lastN > 12 * 3600_000L) {
                Notify.showHungry(context)
                Store.setHungryNotifyAt(context, System.currentTimeMillis())
            }
        }
        Notify.scheduleHungryCheck(context)
    }
}

class SummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Notify.showDailySummary(context, Days.today())
        Notify.scheduleDailySummary(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Store.profile(context).remindPayback) Notify.scheduleDaily(context, true)
        if (Store.profile(context).setupDone) {
            Notify.scheduleHungryCheck(context)
            Notify.scheduleDailySummary(context)
        }
    }
}

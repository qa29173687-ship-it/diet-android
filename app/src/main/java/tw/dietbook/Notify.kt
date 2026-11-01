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
    const val ID = 42

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH, "存錢筒提醒", NotificationManager.IMPORTANCE_DEFAULT)
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

    fun showPayback(ctx: Context) {
        ensureChannel(ctx)
        val u = Store.profile(ctx)
        if (!u.remindPayback || u.balance <= 0) return
        val per = Calc.paybackPerDay(u.balance, u.paybackDays.coerceAtLeast(1))
        val text = "累計盈餘 ${Calc.r0(u.balance)} kcal（約 ${"%.2f".format(Calc.fatKg(u.balance))} kg）。" +
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
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Notify.showPayback(context)
        if (Store.profile(context).remindPayback) Notify.scheduleDaily(context, true)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Store.profile(context).remindPayback) Notify.scheduleDaily(context, true)
    }
}

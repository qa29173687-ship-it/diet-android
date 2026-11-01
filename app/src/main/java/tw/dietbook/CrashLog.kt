package tw.dietbook

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(app: Application) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { write(app, t, e) } catch (_: Exception) { }
            if (prev != null) prev.uncaughtException(t, e)
            else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun file(ctx: Context): File = File(ctx.filesDir, FILE)

    fun read(ctx: Context): String? {
        val f = file(ctx)
        if (!f.exists() || f.length() == 0L) return null
        return try { f.readText() } catch (_: Exception) { null }
    }

    fun clear(ctx: Context) {
        try { file(ctx).delete() } catch (_: Exception) { }
    }

    fun copyToClipboard(ctx: Context): Boolean {
        val text = read(ctx) ?: return false
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("crash", text))
        return true
    }

    private fun write(ctx: Context, thread: Thread, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.TAIWAN).format(Date())
        val ver = try {
            val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            @Suppress("DEPRECATION")
            "${p.versionName} (${p.versionCode})"
        } catch (_: Exception) { "?" }
        val body = buildString {
            appendLine("時間：$ts")
            appendLine("版本：$ver")
            appendLine("裝置：${Build.MANUFACTURER} ${Build.MODEL} / API ${Build.VERSION.SDK_INT}")
            appendLine("執行緒：${thread.name}")
            appendLine()
            append(sw.toString())
        }
        file(ctx).writeText(body)
    }
}

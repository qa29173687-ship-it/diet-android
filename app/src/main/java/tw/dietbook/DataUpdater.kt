package tw.dietbook

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream

object DataUpdater {
    const val TFND_URL = "https://data.fda.gov.tw/data/opendata/export/20/csv"
    const val TRACE_URL = "https://data.fda.gov.tw/data/opendata/export/188/csv"
    private const val WORK = "dietbook_data_daily"
    private const val UA = "DietBook/1.1.0 (Android; data-update)"

    data class Result(
        val ok: Boolean,
        val message: String,
        val tfndUpdated: Boolean = false,
        val traceUpdated: Boolean = false,
    )

    fun schedulePeriodic(ctx: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = PeriodicWorkRequestBuilder<DataUpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP, req
        )
    }

    fun enqueueNow(ctx: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = OneTimeWorkRequestBuilder<DataUpdateWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
    }

    fun checkAndUpdate(ctx: Context): Result {
        val now = System.currentTimeMillis()
        var tfndUp = false
        var traceUp = false
        val msgs = ArrayList<String>()
        try {
            val tfnd = download(TFND_URL)
            val tfndHash = sha256(tfnd)
            if (tfndHash != Store.dataTfndHash(ctx)) {
                val csv = unzipFirstCsv(tfnd)
                val compact = compactTfnd(csv)
                atomicWrite(File(TraceIndex.dir(ctx), "tfnd.csv"), compact.toByteArray(Charsets.UTF_8))
                Store.setDataMeta(
                    ctx,
                    tfndVer = "sha:" + tfndHash.take(12),
                    tfndHash = tfndHash,
                    lastUpdated = now,
                )
                Tfnd.invalidate()
                tfndUp = true
                msgs.add("TFND 已更新")
            } else {
                msgs.add("TFND 無新版本")
            }
        } catch (e: Exception) {
            msgs.add("TFND 檢查失敗：${e.message}")
        }
        try {
            val zip = download(TRACE_URL)
            val hash = sha256(zip)
            if (hash != Store.dataTraceHash(ctx)) {
                val csv = unzipFirstCsv(zip)
                val (jsonl, barcodes) = compactTrace(csv)
                atomicWriteGzip(TraceIndex.nutritionFile(ctx), jsonl)
                atomicWriteGzip(TraceIndex.barcodesFile(ctx), barcodes)
                Store.setDataMeta(
                    ctx,
                    traceVer = "sha:" + hash.take(12),
                    traceHash = hash,
                    lastUpdated = now,
                )
                TraceIndex.invalidate()
                traceUp = true
                msgs.add("追溯索引已更新")
            } else {
                msgs.add("追溯資料無新版本")
            }
        } catch (e: Exception) {
            msgs.add("追溯檢查失敗：${e.message}")
        }
        Store.setDataMeta(ctx, lastChecked = now)
        val anyFail = msgs.any { it.contains("失敗") }
        return Result(
            ok = !anyFail || tfndUp || traceUp,
            message = msgs.joinToString("；"),
            tfndUpdated = tfndUp,
            traceUpdated = traceUp,
        )
    }

    private fun download(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 60_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return BufferedInputStream(conn.inputStream).use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun unzipFirstCsv(zipBytes: ByteArray): String {
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".csv", ignoreCase = true)) {
                    return zis.bufferedReader(Charsets.UTF_8).readText()
                }
                entry = zis.nextEntry
            }
        }
        error("zip 內找不到 CSV")
    }

    /** 從完整 TFND CSV 抽出 id,name,alias,category,kcal,protein 精簡檔 */
    private fun compactTfnd(csv: String): String {
        val sb = StringBuilder()
        sb.append("整合編號,樣品名稱,俗名,資料類別,內容物描述,熱量(kcal),粗蛋白(g)\n")
        // 偵測欄位：食藥署 export 欄位名可能變動，用表頭對應
        val lines = csv.lineSequence().toList()
        if (lines.isEmpty()) return sb.toString()
        val header = Tfnd.parseCsv(lines.first())
        fun idx(vararg names: String): Int {
            for (n in names) {
                val i = header.indexOfFirst { it.replace(" ", "") == n || it.contains(n) }
                if (i >= 0) return i
            }
            return -1
        }
        val iId = idx("整合編號", "編號").takeIf { it >= 0 } ?: 0
        val iName = idx("樣品名稱", "食品名稱").takeIf { it >= 0 } ?: 1
        val iAlias = idx("俗名").takeIf { it >= 0 } ?: -1
        val iCat = idx("資料類別", "內容物描述").takeIf { it >= 0 } ?: -1
        val iKcal = idx("熱量").takeIf { it >= 0 } ?: -1
        val iPro = idx("粗蛋白", "蛋白質").takeIf { it >= 0 } ?: -1
        // 若已是精簡 7 欄格式
        if (header.size >= 7 && iKcal < 0) {
            return csv
        }
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val c = Tfnd.parseCsv(line)
            if (c.size <= maxOf(iId, iName)) continue
            val kcal = if (iKcal >= 0 && iKcal < c.size) num(c[iKcal]) else null
            val pro = if (iPro >= 0 && iPro < c.size) num(c[iPro]) ?: 0.0 else 0.0
            if (kcal == null) continue
            fun esc(s: String) = if (s.contains(',') || s.contains('"')) "\"${s.replace("\"", "\"\"")}\"" else s
            val alias = if (iAlias >= 0 && iAlias < c.size) c[iAlias] else ""
            val cat = if (iCat >= 0 && iCat < c.size) c[iCat] else ""
            sb.append(esc(c[iId].trim())).append(',')
                .append(esc(c[iName].trim())).append(',')
                .append(esc(alias.trim())).append(',')
                .append(',') // 空欄對齊 bundled 格式
                .append(esc(cat.trim())).append(',')
                .append(kcal).append(',')
                .append(pro).append('\n')
        }
        return sb.toString()
    }

    private fun compactTrace(csv: String): Pair<ByteArray, ByteArray> {
        val lines = csv.lineSequence().toList()
        if (lines.isEmpty()) return ByteArray(0) to ByteArray(0)
        val header = Tfnd.parseCsv(lines.first())
        fun col(vararg names: String): Int {
            for (n in names) {
                val i = header.indexOfFirst { it.contains(n) }
                if (i >= 0) return i
            }
            return -1
        }
        val iCode = col("產品追溯系統串接碼", "串接碼")
        val iName = col("產品名稱")
        val iCo = col("公司名稱")
        val iK100 = col("每100公克熱量")
        val iK100ml = col("每100毫升熱量")
        val iP100 = col("每100公克蛋白質")
        val iP100ml = col("每100毫升蛋白質")
        val iKs = col("每份熱量")
        val iPs = col("每份蛋白質")
        val iSg = col("每一份量")
        val iSp = col("本包裝含")
        val jsonLines = ArrayList<String>(12000)
        val bcLines = ArrayList<String>(64)
        val bcRe = Regex("""(?<!\d)(\d{8}|\d{12,14})(?!\d)""")
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val c = Tfnd.parseCsv(line)
            fun at(i: Int) = if (i >= 0 && i < c.size) c[i].trim() else ""
            val code = at(iCode); val name = at(iName)
            if (code.isEmpty() || name.isEmpty()) continue
            val k100 = num(at(iK100)) ?: num(at(iK100ml))
            val p100 = num(at(iP100)) ?: num(at(iP100ml))
            val kserv = num(at(iKs))
            val pserv = num(at(iPs))
            val sg = servingG(at(iSg))
            val sp = servings(at(iSp))
            var k100f = k100
            var p100f = p100
            if (k100f == null && kserv != null && sg != null && sg > 0) k100f = kserv * 100.0 / sg
            if (p100f == null && pserv != null && sg != null && sg > 0) p100f = pserv * 100.0 / sg
            if (k100f == null && kserv == null) continue
            val basis = if (k100f != null) "per100" else "perServing"
            val kcal = k100f ?: kserv!!
            val pro = (if (k100f != null) p100f else pserv) ?: 0.0
            val o = JSONObject()
            o.put("c", code); o.put("n", name); o.put("co", at(iCo).take(40))
            o.put("b", basis); o.put("k", Math.round(kcal * 10.0) / 10.0); o.put("p", Math.round(pro * 10.0) / 10.0)
            if (sg != null) o.put("sg", Math.round(sg * 10.0) / 10.0)
            if (sp != null) o.put("sp", sp)
            if (kserv != null) o.put("ks", Math.round(kserv * 10.0) / 10.0)
            if (pserv != null) o.put("ps", Math.round(pserv * 10.0) / 10.0)
            var firstBc: String? = null
            for (m in bcRe.findAll(name)) {
                val b = m.groupValues[1]
                if (b.length == 8 && (b.startsWith("19") || b.startsWith("20"))) continue
                if (firstBc == null) firstBc = b
                bcLines.add("$b\t$code")
            }
            if (firstBc != null) o.put("bc", firstBc)
            jsonLines.add(o.toString())
        }
        return jsonLines.joinToString("\n").toByteArray(Charsets.UTF_8) to
            bcLines.distinct().joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    private fun num(s: String): Double? {
        if (s.isBlank()) return null
        val m = Regex("""([\d.]+)""").find(s.replace(",", "")) ?: return null
        return m.groupValues[1].toDoubleOrNull()
    }

    private fun servingG(s: String): Double? {
        val m = Regex("""([\d.]+)\s*(公克|g|G|毫升|ml|ML)""").find(s) ?: return null
        return m.groupValues[1].toDoubleOrNull()
    }

    private fun servings(s: String): Double? {
        val m = Regex("""([\d.]+)\s*份""").find(s) ?: return null
        return m.groupValues[1].toDoubleOrNull()
    }

    private fun sha256(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun atomicWriteGzip(target: File, plain: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { fos ->
            GZIPOutputStream(fos).use { gz -> gz.write(plain) }
            fos.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun formatChecked(ts: Long): String {
        if (ts <= 0) return "尚未檢查"
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.TAIWAN)
        fmt.timeZone = java.util.TimeZone.getTimeZone("Asia/Taipei")
        return fmt.format(java.util.Date(ts))
    }
}

class DataUpdateWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val r = DataUpdater.checkAndUpdate(applicationContext)
            if (r.ok) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

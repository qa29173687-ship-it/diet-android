package tw.dietbook

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private val hcPerms = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Toast.makeText(
            this,
            if (granted.containsAll(Health.perms)) "已授權 Health Connect" else "未完整授權，將用活動量估算",
            Toast.LENGTH_SHORT
        ).show()
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) Notify.scheduleDaily(this, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        val u0 = Store.profile(this)
        val (root, body) = col(this)
        setContentView(root)
        EdgeInsets.pad(root, ime = true)
        body.addView(titleBar(this, "設定", "BMR／目標／資料更新", onBack = { finish() }))

        body.addView(label(this, "性別"))
        val sexGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        sexGroup.addView(RadioButton(this).apply { text = "男性"; id = 1; setTextColor(C.text); isChecked = u0.sex != "female" })
        sexGroup.addView(RadioButton(this).apply { text = "女性"; id = 2; setTextColor(C.text); isChecked = u0.sex == "female" })
        body.addView(sexGroup)
        val age = field(this, "年齡", u0.age.toString())
        val height = field(this, "身高 cm", u0.heightCm.toString())
        val weight = field(this, "體重 kg", u0.weightKg.toString())
        val bmrField = field(this, "BMR kcal／日", Calc.r0(u0.bmr).toString())
        body.addView(label(this, "年齡")); body.addView(age)
        body.addView(label(this, "身高")); body.addView(height)
        body.addView(label(this, "體重")); body.addView(weight)
        body.addView(label(this, "BMR（可直接改，或按下方重算）")); body.addView(bmrField)

        body.addView(secondaryBtn(this, "用 Mifflin–St Jeor 重算 BMR") {
            val sex = if (sexGroup.checkedRadioButtonId == 2) "female" else "male"
            val a = age.text.toString().toIntOrNull() ?: return@secondaryBtn
            val h = height.text.toString().toDoubleOrNull() ?: return@secondaryBtn
            val w = weight.text.toString().toDoubleOrNull() ?: return@secondaryBtn
            bmrField.setText(Calc.r0(Calc.mifflin(sex, w, h, a)).toString())
            Toast.makeText(this, "已帶入估算值，記得按儲存", Toast.LENGTH_SHORT).show()
        })

        body.addView(label(this, "活動量（無 Health Connect 時）"))
        val levels = listOf("sedentary", "light", "moderate", "active", "very")
        val spin = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, levels.map { Calc.activityLabel(it) })
            setSelection(levels.indexOf(u0.activity).coerceAtLeast(0))
        }
        body.addView(spin)

        val goalK = field(this, "熱量目標", Calc.r0(u0.goalKcal).toString())
        val goalP = field(this, "蛋白質目標 g", Calc.r0(u0.goalProtein).toString())
        body.addView(label(this, "每日熱量目標")); body.addView(goalK)
        body.addView(label(this, "每日蛋白質目標")); body.addView(goalP)

        val hcToggle = RadioGroup(this)
        hcToggle.addView(RadioButton(this).apply { text = "啟用 Health Connect"; id = 1; setTextColor(C.text); isChecked = u0.hcEnabled })
        hcToggle.addView(RadioButton(this).apply { text = "關閉（只用活動量 TDEE）"; id = 2; setTextColor(C.text); isChecked = !u0.hcEnabled })
        body.addView(label(this, "健康資料")); body.addView(hcToggle)
        body.addView(secondaryBtn(this, "向 Health Connect 請求權限") {
            if (!Health.available(this)) {
                Toast.makeText(this, "裝置上沒有 Health Connect，嘗試開啟商店", Toast.LENGTH_LONG).show()
                try { startActivity(Health.installIntent()) } catch (_: Exception) {}
            } else {
                try { hcPerms.launch(Health.perms) } catch (e: Exception) {
                    Toast.makeText(this, "無法請求：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
        lifecycleScope.launch {
            val st = if (!Health.available(this@SettingsActivity)) "不可用"
            else if (Health.granted(this@SettingsActivity)) "已授權" else "未授權"
            body.addView(label(this@SettingsActivity, "Health Connect 狀態：$st", 12f, C.muted))
        }

        val payDays = field(this, "還款天數", u0.paybackDays.toString())
        body.addView(label(this, "盈餘還款預設天數")); body.addView(payDays)
        val remindGroup = RadioGroup(this)
        remindGroup.addView(RadioButton(this).apply { text = "開啟每晚提醒（約 20:00）"; id = 1; setTextColor(C.text); isChecked = u0.remindPayback })
        remindGroup.addView(RadioButton(this).apply { text = "關閉提醒"; id = 2; setTextColor(C.text); isChecked = !u0.remindPayback })
        body.addView(label(this, "熱量帳戶提醒")); body.addView(remindGroup)

        // ---- 資料版本 ----
        body.addView(label(this, "營養資料庫", 15f, C.text).apply {
            setPadding(0, dp(16f), 0, dp(6f)); paint.isFakeBoldText = true
        })
        val dataInfo = TextView(this).apply {
            setTextColor(C.muted); textSize = 13f
            setPadding(0, 0, 0, dp(8f))
        }
        fun refreshDataInfo() {
            val n = try { TraceIndex.size(this@SettingsActivity) } catch (_: Exception) { 0 }
            val bc = try { TraceIndex.barcodeCount(this@SettingsActivity) } catch (_: Exception) { 0 }
            dataInfo.text = buildString {
                append("TFND 版本：").append(Store.dataTfndVersion(this@SettingsActivity)).append('\n')
                append("追溯索引版本：").append(Store.dataTraceVersion(this@SettingsActivity))
                append("（約 $n 筆有營養")
                if (bc > 0) append("、$bc 條碼")
                append("）\n")
                append("上次檢查：").append(DataUpdater.formatChecked(Store.dataLastChecked(this@SettingsActivity))).append('\n')
                append("上次更新：").append(DataUpdater.formatChecked(Store.dataLastUpdated(this@SettingsActivity))).append('\n')
                append("背景每日以 SHA-256 比對食藥署 export；無新檔則沿用本機／內建。")
            }
        }
        refreshDataInfo()
        body.addView(dataInfo)
        body.addView(secondaryBtn(this, "立即檢查資料更新") {
            Toast.makeText(this, "檢查中…（需網路）", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val r = withContext(Dispatchers.IO) {
                    try { DataUpdater.checkAndUpdate(this@SettingsActivity) }
                    catch (e: Exception) { DataUpdater.Result(false, e.message ?: "失敗") }
                }
                refreshDataInfo()
                Toast.makeText(this@SettingsActivity, r.message, Toast.LENGTH_LONG).show()
            }
        })

        body.addView(primaryBtn(this, "儲存設定") {
            val sex = if (sexGroup.checkedRadioButtonId == 2) "female" else "male"
            val a = age.text.toString().toIntOrNull() ?: u0.age
            val h = height.text.toString().toDoubleOrNull() ?: u0.heightCm
            val w = weight.text.toString().toDoubleOrNull() ?: u0.weightKg
            val bmr = bmrField.text.toString().toDoubleOrNull() ?: u0.bmr
            val act = levels[spin.selectedItemPosition]
            val remind = remindGroup.checkedRadioButtonId == 1
            val next = u0.copy(
                sex = sex, age = a, heightCm = h, weightKg = w,
                bmr = bmr, bmrManual = true,
                activity = act,
                goalKcal = goalK.text.toString().toDoubleOrNull() ?: u0.goalKcal,
                goalProtein = goalP.text.toString().toDoubleOrNull() ?: u0.goalProtein,
                hcEnabled = hcToggle.checkedRadioButtonId == 1,
                paybackDays = payDays.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 7,
                remindPayback = remind,
            )
            Store.saveProfile(this, next)
            if (remind) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                else Notify.scheduleDaily(this, true)
            } else Notify.scheduleDaily(this, false)
            Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show()
            finish()
        })

        body.addView(label(this, Tfnd.ATTRIBUTION + "\n" + TraceIndex.ATTRIBUTION + "\n熱量帳戶與體脂換算僅供估算，非醫療建議。", 11f))
    }
}

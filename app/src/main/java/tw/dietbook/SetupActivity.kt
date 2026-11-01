package tw.dietbook

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        val (root, body) = col(this)
        setContentView(root)
        EdgeInsets.pad(root, ime = true)
        body.addView(titleBar(this, "歡迎來到熱量存錢筒", "先告訴小豬你的基礎代謝率（BMR）"))

        val hello = card(this)
        hello.addView(label(this, "🐷 這裡像小豬撲滿：熱量缺口＝存款（小豬變壯），熱量進帳＝小豬變圓。請先輸入你自己的 BMR（可用體脂機／App／醫師提供的數字）。", 14f, C.text))
        body.addView(hello)

        // —— Primary path: manual BMR ——
        val primary = card(this)
        primary.addView(label(this, "你的基礎代謝率（必填）", 14f, C.text))
        val bmrField = field(this, "例如 1450（kcal／日）", "")
        bmrField.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        primary.addView(bmrField)
        primary.addView(label(this, "單位：大卡／天。之後都能在設定修改。", 12f))
        body.addView(primary)

        body.addView(label(this, "每日小目標（可之後再改）", 13f, C.text))
        val goalK = field(this, "熱量目標 kcal", "1800")
        val goalP = field(this, "蛋白質目標 g", "80")
        body.addView(goalK); body.addView(goalP)

        body.addView(label(this, "活動量（沒有 Health Connect 時的後備）", 13f))
        val levels = listOf("sedentary", "light", "moderate", "active", "very")
        val labels = levels.map { Calc.activityLabel(it) }
        val spin = Spinner(this).apply {
            adapter = ArrayAdapter(this@SetupActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(2)
        }
        body.addView(spin)

        // —— Secondary: optional calculator ——
        val calcToggle = secondaryBtn(this, "▼ 沒有現成 BMR？用小計算機估算") {}
        val calcBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(4f), 0, dp(8f))
        }
        calcToggle.setOnClickListener {
            val open = calcBox.visibility != View.VISIBLE
            calcBox.visibility = if (open) View.VISIBLE else View.GONE
            calcToggle.text = if (open) "▲ 收起估算小幫手" else "▼ 沒有現成 BMR？用小計算機估算"
        }

        calcBox.addView(label(this, "以 Mifflin–St Jeor 粗估（僅供參考，仍建議之後改成實測值）", 12f, C.warn))
        val sexGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val male = RadioButton(this).apply { text = "男性"; id = 1; setTextColor(C.text); isChecked = true }
        val female = RadioButton(this).apply { text = "女性"; id = 2; setTextColor(C.text) }
        sexGroup.addView(male); sexGroup.addView(female)
        calcBox.addView(label(this, "性別")); calcBox.addView(sexGroup)
        val age = field(this, "年齡", "30")
        val height = field(this, "身高 cm", "170")
        val weight = field(this, "體重 kg", "70")
        calcBox.addView(label(this, "年齡")); calcBox.addView(age)
        calcBox.addView(label(this, "身高 (cm)")); calcBox.addView(height)
        calcBox.addView(label(this, "體重 (kg)")); calcBox.addView(weight)
        calcBox.addView(secondaryBtn(this, "填入估算 BMR") {
            val sex = if (sexGroup.checkedRadioButtonId == 2) "female" else "male"
            val a = Numbers.intOrNull(age.text.toString()) ?: 30
            val h = Numbers.doubleOrNull(height.text.toString()) ?: 170.0
            val w = Numbers.doubleOrNull(weight.text.toString()) ?: 70.0
            val est = Calc.mifflin(sex, w, h, a)
            bmrField.setText(Calc.r0(est))
            Toast.makeText(this, "已填入估算 ${Calc.r0(est)} kcal（可再手動微調）", Toast.LENGTH_SHORT).show()
            // stash anthropometrics for profile
            bmrField.tag = mapOf("sex" to sex, "age" to a, "h" to h, "w" to w)
        })
        body.addView(calcToggle)
        body.addView(calcBox)

        body.addView(primaryBtn(this, "開啟存錢筒 🐷") {
            val manual = Numbers.doubleOrNull(bmrField.text.toString())
            if (manual == null) {
                Toast.makeText(this, "請先輸入你的 BMR 喔", Toast.LENGTH_SHORT).show()
                return@primaryBtn
            }
            if (manual < 800 || manual > 4000) {
                Toast.makeText(this, "BMR 看起來不合理，請再檢查", Toast.LENGTH_SHORT).show()
                return@primaryBtn
            }
            @Suppress("UNCHECKED_CAST")
            val meta = bmrField.tag as? Map<String, Any>
            val sex = (meta?.get("sex") as? String) ?: "male"
            val a = (meta?.get("age") as? Int) ?: 30
            val h = (meta?.get("h") as? Double) ?: 170.0
            val w = (meta?.get("w") as? Double) ?: 70.0
            val act = levels[spin.selectedItemPosition]
            val gk = Numbers.doubleOrNull(goalK.text.toString()) ?: Calc.tdee(manual, act)
            val gp = Numbers.doubleOrNull(goalP.text.toString()) ?: 80.0
            Store.saveProfile(
                this,
                UserProfile(
                    setupDone = true,
                    sex = sex, age = a, heightCm = h, weightKg = w,
                    bmr = manual, bmrManual = true,
                    activity = act, goalKcal = gk, goalProtein = gp,
                    lastFinalizedDay = Days.yesterdayOf(Days.today()),
                )
            )
            Toast.makeText(this, "BMR ${Calc.r0(manual)} kcal／日已存進小豬撲滿", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        })
        body.addView(label(this, Tfnd.ATTRIBUTION, 11f))
    }
}

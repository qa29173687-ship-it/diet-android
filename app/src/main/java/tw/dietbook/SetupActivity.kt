package tw.dietbook

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
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
        body.addView(titleBar(this, "歡迎使用飲食帳本", "先設定基礎代謝（BMR）"))

        val info = card(this)
        info.addView(label(this, "可直接輸入已知 BMR，或用身高／體重／年齡／性別以 Mifflin–St Jeor 估算。之後都能在設定修改。", 13f, C.text))
        body.addView(info)

        body.addView(label(this, "性別"))
        val sexGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val male = RadioButton(this).apply { text = "男性"; id = 1; setTextColor(C.text); isChecked = true }
        val female = RadioButton(this).apply { text = "女性"; id = 2; setTextColor(C.text) }
        sexGroup.addView(male); sexGroup.addView(female)
        body.addView(sexGroup)

        val age = field(this, "年齡", "30")
        val height = field(this, "身高 cm", "170")
        val weight = field(this, "體重 kg", "70")
        val bmrField = field(this, "或直接輸入 BMR（kcal／日）", "")
        body.addView(label(this, "年齡"))
        body.addView(age)
        body.addView(label(this, "身高 (cm)"))
        body.addView(height)
        body.addView(label(this, "體重 (kg)"))
        body.addView(weight)
        body.addView(label(this, "已知 BMR（選填，填了就以手動為準）"))
        body.addView(bmrField)

        body.addView(label(this, "活動量（Health Connect 不可用時的後備 TDEE）"))
        val levels = listOf("sedentary", "light", "moderate", "active", "very")
        val labels = levels.map { Calc.activityLabel(it) }
        val spin = Spinner(this).apply {
            adapter = ArrayAdapter(this@SetupActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(2)
        }
        body.addView(spin)

        body.addView(label(this, "每日目標"))
        val goalK = field(this, "熱量目標 kcal", "2000")
        val goalP = field(this, "蛋白質目標 g", "80")
        body.addView(goalK); body.addView(goalP)

        body.addView(primaryBtn(this, "開始使用") {
            val sex = if (sexGroup.checkedRadioButtonId == 2) "female" else "male"
            val a = age.text.toString().toIntOrNull() ?: 30
            val h = height.text.toString().toDoubleOrNull() ?: 170.0
            val w = weight.text.toString().toDoubleOrNull() ?: 70.0
            val manual = bmrField.text.toString().toDoubleOrNull()
            val bmr = manual ?: Calc.mifflin(sex, w, h, a)
            val act = levels[spin.selectedItemPosition]
            val gk = goalK.text.toString().toDoubleOrNull() ?: Calc.tdee(bmr, act)
            val gp = goalP.text.toString().toDoubleOrNull() ?: 80.0
            if (bmr < 800 || bmr > 4000) {
                Toast.makeText(this, "BMR 看起來不合理，請再檢查", Toast.LENGTH_SHORT).show()
                return@primaryBtn
            }
            Store.saveProfile(
                this,
                UserProfile(
                    setupDone = true,
                    sex = sex, age = a, heightCm = h, weightKg = w,
                    bmr = bmr, bmrManual = manual != null,
                    activity = act, goalKcal = gk, goalProtein = gp,
                    lastFinalizedDay = Days.yesterdayOf(Days.today()),
                )
            )
            Toast.makeText(this, "BMR ${Calc.r0(bmr)} kcal／日已儲存", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        })
        body.addView(label(this, Tfnd.ATTRIBUTION, 11f))
    }
}

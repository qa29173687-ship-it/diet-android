package tw.dietbook

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        val id = intent.getStringExtra("foodId") ?: run { finish(); return }
        val food = Store.findFood(this, id) ?: run {
            Toast.makeText(this, "找不到食物", Toast.LENGTH_SHORT).show(); finish(); return
        }
        val (root, body) = col(this)
        setContentView(root)
        EdgeInsets.pad(root, ime = true)
        body.addView(titleBar(this, food.name, "${Calc.r0(food.kcalPer100)} kcal／100g　蛋白 ${Calc.r1(food.proteinPer100)} g", onBack = { finish() }))

        val grams = field(this, "食用量（公克）", food.servingG?.let { Calc.r0(it).toString() } ?: "100")
        body.addView(label(this, "食用量 (g)"))
        body.addView(grams)

        val preview = label(this, "", 15f, C.text)
        body.addView(preview)
        fun update() {
            val g = grams.text.toString().toDoubleOrNull() ?: 0.0
            val k = Calc.scale(food.kcalPer100, g)
            val p = Calc.scale(food.proteinPer100, g)
            preview.text = "預估：${Calc.r0(k)} kcal　蛋白質 ${Calc.r1(p)} g"
        }
        grams.addTextChangedListener(SimpleWatcher { update() })
        update()

        if (food.servingG != null) {
            body.addView(secondaryBtn(this, "用標示的一份（${Calc.r0(food.servingG)} g）") {
                grams.setText(Calc.r0(food.servingG).toString())
            })
        }
        if (food.servingsPerPack != null && food.servingG != null) {
            val packG = food.servingG * food.servingsPerPack
            body.addView(secondaryBtn(this, "整包（約 ${Calc.r0(packG)} g，${Calc.r1(food.servingsPerPack)} 份）") {
                grams.setText(Calc.r0(packG).toString())
            })
        }

        body.addView(primaryBtn(this, "加入今日") {
            val g = grams.text.toString().toDoubleOrNull()
            if (g == null || g <= 0) {
                Toast.makeText(this, "請輸入有效公克數", Toast.LENGTH_SHORT).show(); return@primaryBtn
            }
            val day = Days.today()
            Store.addLog(
                this,
                FoodLog(
                    id = System.currentTimeMillis(),
                    day = day,
                    foodId = food.id,
                    foodName = food.name,
                    grams = g,
                    kcal = Calc.scale(food.kcalPer100, g),
                    protein = Calc.scale(food.proteinPer100, g),
                    at = System.currentTimeMillis(),
                )
            )
            Toast.makeText(this, "已加入", Toast.LENGTH_SHORT).show()
            finish()
        })
    }
}

class SimpleWatcher(val after: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) { after() }
}

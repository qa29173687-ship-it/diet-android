package tw.dietbook

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {
    private var selectedUnit: PortionUnit? = null
    private var portion: PortionFood? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        val id = intent.getStringExtra("foodId")
        val food = FoodCodec.fromJson(intent.getStringExtra("foodJson"))
            ?: id?.let { Store.findFood(this, it) }
        if (food == null) {
            Toast.makeText(this, "找不到食物", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        portion = Portions.findForFood(this, food)
        val (root, body) = col(this)
        setContentView(root)
        EdgeInsets.pad(root, ime = true)
        body.addView(
            titleBar(
                this,
                food.name,
                "${Calc.r0(food.kcalPer100)} kcal／100g　蛋白 ${Calc.r1(food.proteinPer100)} g",
                onBack = { finish() },
            ),
        )

        val qty = field(this, "數量", "1")
        val grams = field(
            this,
            "食用量（公克）",
            food.servingG?.let { Calc.r0(it).toString() } ?: "100",
        )
        val unitHint = label(this, "", 13f, C.muted)
        val preview = label(this, "", 15f, C.text)

        fun refreshPreview() {
            val g = Numbers.doubleOrNull(grams.text.toString()) ?: 0.0
            val k = Calc.scale(food.kcalPer100, g)
            val p = Calc.scale(food.proteinPer100, g)
            val unitTxt = selectedUnit?.let { u ->
                if (u.unit == "公克") ""
                else {
                    val q = Numbers.doubleOrNull(qty.text.toString()) ?: 0.0
                    "（${Calc.r1(q)}${u.unit}）"
                }
            } ?: ""
            preview.text = "預估$unitTxt：${Calc.r0(k)} kcal　蛋白質 ${Calc.r1(p)} g"
        }

        fun applyUnit(u: PortionUnit) {
            selectedUnit = u
            val q = Numbers.doubleOrNull(qty.text.toString()) ?: 1.0
            if (u.unit == "公克") {
                grams.setText(Calc.r0(q).toString())
                unitHint.text = "直接以公克計算"
            } else {
                grams.setText(Calc.r0(q * u.grams).toString())
                unitHint.text = "1${u.unit} ≈ ${Calc.r0(u.grams)} g　→　數量 × 單位公克"
            }
            refreshPreview()
        }

        if (portion != null) {
            body.addView(label(this, "常見份量（選單位後調數量）", 13f))
            val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            var row: LinearLayout? = null
            var inRow = 0
            fun newRow() {
                row = LinearLayout(this@LogActivity).apply { orientation = LinearLayout.HORIZONTAL }
                wrap.addView(row)
                inRow = 0
            }
            newRow()
            for (u in portion!!.units) {
                if (inRow >= 4) newRow()
                val labelTxt = if (u.unit == "公克") "公克" else "1${u.unit}"
                val btn = secondaryBtn(this, labelTxt) {
                    if (u.unit != "公克" && (Numbers.doubleOrNull(qty.text.toString()) ?: 0.0) <= 0.0) {
                        qty.setText("1")
                    }
                    applyUnit(u)
                }
                btn.layoutParams = LinearLayout.LayoutParams(0, dp(44f), 1f).apply {
                    marginStart = dp(2f); marginEnd = dp(2f); bottomMargin = dp(4f)
                }
                row!!.addView(btn)
                inRow++
            }
            body.addView(wrap)
            body.addView(unitHint)
            body.addView(label(this, "數量"))
            body.addView(qty)
            portion!!.note.takeIf { it.isNotBlank() }?.let {
                body.addView(label(this, it, 11f))
            }
        }

        body.addView(label(this, "食用量 (g)"))
        body.addView(grams)
        body.addView(preview)

        grams.addTextChangedListener(SimpleWatcher { refreshPreview() })
        qty.addTextChangedListener(SimpleWatcher {
            val u = selectedUnit
            if (u != null) {
                val q = Numbers.doubleOrNull(qty.text.toString()) ?: 0.0
                if (u.unit == "公克") grams.setText(Calc.r0(q).toString())
                else grams.setText(Calc.r0(q * u.grams).toString())
            }
            refreshPreview()
        })

        val defaultUnit = portion?.units?.firstOrNull { it.unit != "公克" }
        if (defaultUnit != null) {
            qty.setText("1")
            applyUnit(defaultUnit)
        } else {
            selectedUnit = PortionUnit("公克", 1.0)
            refreshPreview()
        }

        if (food.servingG != null) {
            body.addView(secondaryBtn(this, "用標示的一份（${Calc.r0(food.servingG)} g）") {
                selectedUnit = PortionUnit("公克", 1.0)
                qty.setText(Calc.r0(food.servingG).toString())
                grams.setText(Calc.r0(food.servingG).toString())
                unitHint.text = "直接以公克計算"
                refreshPreview()
            })
        }
        if (food.servingsPerPack != null && food.servingG != null) {
            val packG = food.servingG * food.servingsPerPack
            body.addView(secondaryBtn(this, "整包（約 ${Calc.r0(packG)} g，${Calc.r1(food.servingsPerPack)} 份）") {
                selectedUnit = PortionUnit("公克", 1.0)
                qty.setText(Calc.r0(packG).toString())
                grams.setText(Calc.r0(packG).toString())
                unitHint.text = "直接以公克計算"
                refreshPreview()
            })
        }

        body.addView(primaryBtn(this, "加入今日") {
            val g = Numbers.doubleOrNull(grams.text.toString())
            if (g == null || g <= 0) {
                Toast.makeText(this, "請輸入有效公克數", Toast.LENGTH_SHORT).show()
                return@primaryBtn
            }
            val day = Days.today()
            val kcal = Calc.scale(food.kcalPer100, g)
            val protein = Calc.scale(food.proteinPer100, g)
            val unitNote = selectedUnit?.takeIf { it.unit != "公克" }?.let { u ->
                val q = Numbers.doubleOrNull(qty.text.toString()) ?: 1.0
                " ${Calc.r1(q)}${u.unit}"
            } ?: ""
            Store.addLog(
                this,
                FoodLog(
                    id = System.currentTimeMillis(),
                    day = day,
                    foodId = food.id,
                    foodName = food.name + unitNote,
                    grams = g,
                    kcal = kcal,
                    protein = protein,
                    at = System.currentTimeMillis(),
                ),
            )
            Store.touchFeed(this)
            Store.bumpStreak(this)
            Outfits.maybeUnlockFromStreak(this)
            CuteFx.queue(this, PendingCute(CuteScene.FOOD_CATCH, kcal = kcal, label = "好好吃～ ${food.name}"))
            val profile = Store.profile(this)
            val (_, dayProtein) = Store.dayTotals(this, day)
            if (dayProtein >= profile.goalProtein && profile.goalProtein > 0) {
                CuteFx.queue(this, PendingCute(CuteScene.PROTEIN_POWER, protein = dayProtein))
            }
            val (dayKcal, _) = Store.dayTotals(this, day)
            if (dayKcal > profile.goalKcal * 1.15 && profile.goalKcal > 0) {
                CuteFx.queue(this, PendingCute(CuteScene.SURPLUS_BELLY, kcal = dayKcal))
            }
            Toast.makeText(this, "已加入，回家看小豬吃飯～", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        })
        body.addView(label(this, Portions.ATTRIBUTION, 10f))
    }
}

class SimpleWatcher(val after: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) { after() }
}

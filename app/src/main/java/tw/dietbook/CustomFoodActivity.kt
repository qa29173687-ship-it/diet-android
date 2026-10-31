package tw.dietbook

import android.content.Intent
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class CustomFoodActivity : AppCompatActivity() {
    private lateinit var nameF: android.widget.EditText
    private lateinit var kcalF: android.widget.EditText
    private lateinit var proF: android.widget.EditText
    private lateinit var servingF: android.widget.EditText
    private lateinit var packF: android.widget.EditText
    private lateinit var noteF: android.widget.EditText
    private lateinit var basisGroup: RadioGroup
    private var editId: String? = null

    private val scan = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != RESULT_OK) return@registerForActivityResult
        val d = res.data ?: return@registerForActivityResult
        d.getStringExtra("name")?.takeIf { it.isNotBlank() }?.let { nameF.setText(it) }
        d.getDoubleExtra("kcalPer100", -1.0).takeIf { it >= 0 }?.let { kcalF.setText(Calc.r1(it).toString()) }
        d.getDoubleExtra("proteinPer100", -1.0).takeIf { it >= 0 }?.let { proF.setText(Calc.r1(it).toString()) }
        d.getDoubleExtra("kcalPerServing", -1.0).takeIf { it >= 0 }?.let {
            if (basisGroup.checkedRadioButtonId == 2) kcalF.setText(Calc.r1(it).toString())
        }
        d.getDoubleExtra("proteinPerServing", -1.0).takeIf { it >= 0 }?.let {
            if (basisGroup.checkedRadioButtonId == 2) proF.setText(Calc.r1(it).toString())
        }
        d.getDoubleExtra("servingG", -1.0).takeIf { it >= 0 }?.let { servingF.setText(Calc.r1(it).toString()) }
        d.getDoubleExtra("servingsPerPack", -1.0).takeIf { it >= 0 }?.let { packF.setText(Calc.r1(it).toString()) }
        when (d.getStringExtra("basis")) {
            "perServing" -> basisGroup.check(2)
            else -> basisGroup.check(1)
        }
        d.getStringExtra("raw")?.let { noteF.setText(it.take(500)) }
        Toast.makeText(this, "已帶入辨識結果，請確認後儲存", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        editId = intent.getStringExtra("foodId")
        val (root, body) = col(this)
        setContentView(root)
        EdgeInsets.pad(root, ime = true)
        body.addView(titleBar(this, if (editId == null) "自訂食物" else "編輯自訂食物", "超商商品可拍營養標示", onBack = { finish() }))

        body.addView(primaryBtn(this, "📷 拍營養標示（離線中文辨識）") {
            scan.launch(Intent(this, ScanActivity::class.java))
        })
        body.addView(label(this, "辨識後請核對「每份／每100g」與「本包裝含幾份」，確認無誤再存成可重用食物。", 12f))

        nameF = field(this, "名稱，例如：7-11 御飯糰")
        body.addView(label(this, "名稱")); body.addView(nameF)

        body.addView(label(this, "數值基準"))
        basisGroup = RadioGroup(this)
        basisGroup.addView(RadioButton(this).apply { text = "每 100 g／ml"; id = 1; isChecked = true; setTextColor(C.text) })
        basisGroup.addView(RadioButton(this).apply { text = "每一份"; id = 2; setTextColor(C.text) })
        body.addView(basisGroup)

        kcalF = field(this, "熱量 kcal")
        proF = field(this, "蛋白質 g")
        servingF = field(this, "每一份量（公克，選填）")
        packF = field(this, "本包裝含幾份（選填）")
        noteF = field(this, "備註／OCR 原文（選填）")
        body.addView(label(this, "熱量")); body.addView(kcalF)
        body.addView(label(this, "蛋白質")); body.addView(proF)
        body.addView(label(this, "每一份量 g")); body.addView(servingF)
        body.addView(label(this, "本包裝含 N 份")); body.addView(packF)
        body.addView(label(this, "備註")); body.addView(noteF)

        editId?.let { id ->
            Store.customFoods(this).find { it.id == id }?.let { f ->
                nameF.setText(f.name)
                kcalF.setText(f.kcalPer100.toString())
                proF.setText(f.proteinPer100.toString())
                f.servingG?.let { servingF.setText(it.toString()) }
                f.servingsPerPack?.let { packF.setText(it.toString()) }
                noteF.setText(f.note)
                basisGroup.check(if (f.basis == "perServing") 2 else 1)
            }
        }

        body.addView(primaryBtn(this, "儲存自訂食物") { save() })

        // list existing customs
        body.addView(label(this, "已存的自訂食物", 15f, C.text).apply { setPadding(0, dp(12f), 0, dp(6f)); paint.isFakeBoldText = true })
        Store.customFoods(this).forEach { f ->
            val row = card(this)
            row.addView(label(this, f.name, 15f, C.text))
            row.addView(label(this, "${Calc.r0(f.kcalPer100)} kcal／100g　蛋白 ${Calc.r1(f.proteinPer100)} g"))
            row.setOnClickListener {
                startActivity(Intent(this, LogActivity::class.java).putExtra("foodId", f.id)); finish()
            }
            row.setOnLongClickListener {
                Store.deleteCustom(this, f.id)
                Toast.makeText(this, "已刪除 ${f.name}", Toast.LENGTH_SHORT).show()
                recreate(); true
            }
            body.addView(row)
        }
        body.addView(label(this, "點一下記錄；長按刪除", 12f))
    }

    private fun save() {
        val name = nameF.text.toString().trim()
        if (name.isEmpty()) { Toast.makeText(this, "請填名稱", Toast.LENGTH_SHORT).show(); return }
        val kcalIn = kcalF.text.toString().toDoubleOrNull()
        val proIn = proF.text.toString().toDoubleOrNull() ?: 0.0
        if (kcalIn == null) { Toast.makeText(this, "請填熱量", Toast.LENGTH_SHORT).show(); return }
        val serving = servingF.text.toString().toDoubleOrNull()
        val pack = packF.text.toString().toDoubleOrNull()
        val basis = if (basisGroup.checkedRadioButtonId == 2) "perServing" else "per100"
        if (basis == "perServing" && (serving == null || serving <= 0)) {
            Toast.makeText(this, "選「每一份」時請填每一份量公克，才能換算成每100g", Toast.LENGTH_LONG).show()
            return
        }
        val (k100, p100) = LabelParser.normalizeToPer100(basis, kcalIn, proIn, serving)
        val id = editId ?: ("custom:" + System.currentTimeMillis())
        Store.upsertCustom(
            this,
            FoodItem(
                id = id, name = name, category = "自訂",
                kcalPer100 = k100, proteinPer100 = p100, custom = true,
                servingG = serving, servingsPerPack = pack, basis = basis,
                note = noteF.text.toString().trim(),
            )
        )
        Toast.makeText(this, "已儲存（以每100g：${Calc.r0(k100)} kcal／蛋白 ${Calc.r1(p100)} g）", Toast.LENGTH_LONG).show()
        finish()
    }
}

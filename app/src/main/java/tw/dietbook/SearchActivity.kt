package tw.dietbook

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SearchActivity : AppCompatActivity() {
    private lateinit var results: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        val (root, body) = col(this)
        setContentView(root)
        EdgeInsets.pad(root, ime = true)
        body.addView(titleBar(this, "搜尋食物", "食藥署資料庫＋自訂", onBack = { finish() }))
        val q = field(this, "輸入名稱或俗名，例如：雞蛋、白米")
        body.addView(q)
        body.addView(secondaryBtn(this, "新增自訂食物／拍營養標示") {
            startActivity(Intent(this, CustomFoodActivity::class.java))
        })
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(results)
        q.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { render(s?.toString().orEmpty()) }
        })
        render("")
    }

    override fun onResume() {
        super.onResume()
        // refresh custom foods
        if (::results.isInitialized) render("")
    }

    private fun render(query: String) {
        results.removeAllViews()
        val list = Tfnd.search(this, query, Store.customFoods(this))
        if (list.isEmpty()) {
            results.addView(label(this, "找不到。可以新增自訂食物。", 14f))
            return
        }
        list.forEach { f ->
            val row = card(this).apply {
                setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8f)
            }
            row.addView(TextView(this).apply {
                text = buildString {
                    append(f.name)
                    if (f.custom) append("　自訂")
                }
                setTextColor(C.text); textSize = 16f; paint.isFakeBoldText = true
            })
            val sub = buildString {
                if (f.category.isNotBlank()) append(f.category).append("　")
                append("${Calc.r0(f.kcalPer100)} kcal／100g　蛋白 ${Calc.r1(f.proteinPer100)} g")
                if (f.alias.isNotBlank()) append("\n俗名：").append(f.alias.take(40))
            }
            row.addView(label(this, sub, 12f))
            row.setOnClickListener {
                startActivity(Intent(this, LogActivity::class.java).putExtra("foodId", f.id))
            }
            results.addView(row)
        }
        results.addView(label(this, Tfnd.ATTRIBUTION, 11f))
    }
}

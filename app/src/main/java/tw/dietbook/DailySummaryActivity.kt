package tw.dietbook

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import tw.dietbook.ui.DailySummaryScreen

/**
 * Cute end-of-day / next-morning wrap-up. Advice lives here, not on the home screen.
 */
class DailySummaryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        val day = intent?.getStringExtra(EXTRA_DAY) ?: Days.today()
        val useSample = intent?.getBooleanExtra(EXTRA_SAMPLE, false) == true
        val model = if (useSample) {
            DailySummary.sampleBelowBmr(day)
        } else {
            DailySummary.build(this, day)
        }
        Store.setLastSeenSummaryDay(this, day)
        setContent {
            DailySummaryScreen(
                model = model,
                modifier = Modifier.fillMaxSize(),
                onClose = { finish() },
                onOpenLedger = {
                    startActivity(Intent(this, BalanceActivity::class.java))
                },
            )
        }
    }

    companion object {
        const val EXTRA_DAY = "day"
        const val EXTRA_SAMPLE = "sample"
        const val EXTRA_FROM_MORNING = "fromMorning"

        fun intent(ctx: Context, day: String, fromMorning: Boolean = false): Intent =
            Intent(ctx, DailySummaryActivity::class.java)
                .putExtra(EXTRA_DAY, day)
                .putExtra(EXTRA_FROM_MORNING, fromMorning)
    }
}

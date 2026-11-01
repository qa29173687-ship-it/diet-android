package tw.dietbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import tw.dietbook.ui.HomeScreenV2
import tw.dietbook.ui.HomeV2Sample
import tw.dietbook.ui.HomeV2UiState
import tw.dietbook.ui.withAdvice

/**
 * Demo host for Robolectric preview shots. Not the launcher.
 */
class HomeV2DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        edge()
        val state: HomeV2UiState = when {
            intent?.hasExtra(EXTRA_INTAKE) == true -> HomeV2UiState(
                intakeKcal = intent.getIntExtra(EXTRA_INTAKE, 980),
                proteinG = intent.getIntExtra(EXTRA_PROTEIN, 42),
                burnKcal = intent.getIntExtra(EXTRA_BURN, 180),
                strengthMin = intent.getIntExtra(EXTRA_STRENGTH_MIN, 20),
                balanceKcal = intent.getDoubleExtra(EXTRA_BALANCE, -620.0),
                bmr = intent.getDoubleExtra(EXTRA_BMR, 1320.0),
                tip = intent.getStringExtra(EXTRA_TIP) ?: HomeV2Sample.state.tip,
                vitals = HomeV2Sample.state.vitals,
            ).withAdvice()
            else -> HomeV2Sample.belowBmr
        }
        setContent {
            HomeScreenV2(
                state = state,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    companion object {
        const val EXTRA_SAMPLE = "sample"
        const val EXTRA_INTAKE = "intake"
        const val EXTRA_PROTEIN = "protein"
        const val EXTRA_BURN = "burn"
        const val EXTRA_STRENGTH_MIN = "strengthMin"
        const val EXTRA_BALANCE = "balance"
        const val EXTRA_BMR = "bmr"
        const val EXTRA_TIP = "tip"
    }
}

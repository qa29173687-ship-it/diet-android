package tw.dietbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import tw.dietbook.ui.StatDetailPanel

/** Demo host for Robolectric detail-sheet previews. */
class StatDetailDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        edge()
        val kind = when (intent?.getStringExtra(EXTRA_KIND)) {
            "protein" -> StatDetailKind.PROTEIN
            "burn" -> StatDetailKind.BURN
            "strength" -> StatDetailKind.STRENGTH
            else -> StatDetailKind.INTAKE
        }
        val day = Days.today()
        val useSample = intent?.getBooleanExtra(EXTRA_SAMPLE, true) != false
        val intake = if (kind == StatDetailKind.INTAKE) {
            if (useSample) StatDetail.sampleIntake(day) else StatDetail.intake(this, day)
        } else null
        val protein = if (kind == StatDetailKind.PROTEIN) {
            if (useSample) StatDetail.sampleProtein(day) else StatDetail.protein(this, day)
        } else null
        val burn = if (kind == StatDetailKind.BURN) {
            if (useSample) StatDetail.sampleBurn(day) else StatDetail.burn(this, day)
        } else null
        val strength = if (kind == StatDetailKind.STRENGTH) {
            if (useSample) StatDetail.sampleStrength(day) else StatDetail.strength(this, day)
        } else null
        setContent {
            StatDetailPanel(
                kind = kind,
                intake = intake,
                protein = protein,
                burn = burn,
                strength = strength,
                onClose = { finish() },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(C.bg))
                    .padding(horizontal = 16.dp, vertical = 28.dp),
            )
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_SAMPLE = "sample"
    }
}

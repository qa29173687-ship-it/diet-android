package tw.dietbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import tw.dietbook.ui.StatDetailPanel

/** Demo host for Robolectric detail-sheet previews (incl. delete affordances). */
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
        val showDelete = intent?.getBooleanExtra(EXTRA_SHOW_DELETE, true) != false
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
            val snack = remember { SnackbarHostState() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(C.bg)),
            ) {
                StatDetailPanel(
                    kind = kind,
                    intake = intake,
                    protein = protein,
                    burn = burn,
                    strength = strength,
                    allowEdit = showDelete,
                    onClose = { finish() },
                    onRequestDelete = { _, _, _ -> },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 28.dp),
                )
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Snackbar(it, containerColor = Color(0xFF4A3040), contentColor = Color.White, actionColor = Color(0xFFFFB4C8))
                }
            }
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_SAMPLE = "sample"
        const val EXTRA_SHOW_DELETE = "showDelete"
    }
}

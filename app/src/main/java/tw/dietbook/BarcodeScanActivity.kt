package tw.dietbook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeScanActivity : AppCompatActivity() {
    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var previewView: PreviewView
    private lateinit var status: android.widget.TextView
    private val handled = AtomicBoolean(false)
    private var provider: ProcessCameraProvider? = null

    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) bindCamera() else {
            Toast.makeText(this, "需要相機權限才能掃描條碼", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val afterConfirm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // 無論儲存與否，掃描頁結束回到搜尋／主畫面
        setResult(it.resultCode)
        finish()
    }

    private val afterOcr = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val bc = pendingBarcode
        val intent = Intent(this, CustomFoodActivity::class.java).apply {
            putExtra("barcode", bc)
            putExtra("fromBarcodeMiss", true)
            if (res.resultCode == RESULT_OK) {
                res.data?.let { d ->
                    putExtras(d)
                }
            }
        }
        afterConfirm.launch(intent)
    }

    private var pendingBarcode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C.bg)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        }
        top.addView(titleBar(this, "掃描條碼", "本機 → 食藥署追溯 → Open Food Facts", onBack = { finish() }))
        status = label(this, "將條碼置於框內，自動辨識。", 13f)
        top.addView(status)
        root.addView(top)
        previewView = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView)
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(16f))
        }
        bottom.addView(label(this, "找不到時會改拍營養標示，並把條碼存進自訂食物。", 12f))
        root.addView(bottom)
        setContentView(root)
        EdgeInsets.pad(root)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera()
        } else camPerm.launch(Manifest.permission.CAMERA)
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                ).build()
            val scanner = BarcodeScanning.getClient(options)
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(exec) { imageProxy ->
                if (handled.get()) {
                    imageProxy.close(); return@setAnalyzer
                }
                val media = imageProxy.image
                if (media == null) {
                    imageProxy.close(); return@setAnalyzer
                }
                val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
                scanner.process(input)
                    .addOnSuccessListener { barcodes ->
                        val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                        if (raw != null && handled.compareAndSet(false, true)) {
                            runOnUiThread { onCode(raw) }
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            }
            provider?.unbindAll()
            provider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onCode(raw: String) {
        status.text = "查詢中：$raw …"
        provider?.unbindAll()
        lifecycleScope.launch {
            val hit = withContext(Dispatchers.IO) {
                try { BarcodeLookup.lookup(this@BarcodeScanActivity, raw) } catch (_: Exception) { null }
            }
            val code = BarcodeLookup.normalize(raw) ?: raw.filter { it.isDigit() }
            pendingBarcode = code
            if (hit != null) {
                status.text = "找到：${hit.name}（${hit.sourceLabel}）"
                val i = Intent(this@BarcodeScanActivity, CustomFoodActivity::class.java).apply {
                    putExtra("barcode", hit.barcode)
                    putExtra("prefillName", hit.name)
                    putExtra("prefillSource", hit.sourceLabel)
                    putExtra("basis", hit.basis)
                    hit.kcalPer100?.let { putExtra("kcalPer100", it) }
                    hit.proteinPer100?.let { putExtra("proteinPer100", it) }
                    hit.kcalPerServing?.let { putExtra("kcalPerServing", it) }
                    hit.proteinPerServing?.let { putExtra("proteinPerServing", it) }
                    hit.servingG?.let { putExtra("servingG", it) }
                    hit.servingsPerPack?.let { putExtra("servingsPerPack", it) }
                    putExtra("note", hit.note)
                    putExtra("confirmBarcode", true)
                }
                afterConfirm.launch(i)
            } else {
                status.text = "資料庫沒有 $code，改拍營養標示"
                Toast.makeText(this@BarcodeScanActivity, "未找到，請拍營養標示", Toast.LENGTH_LONG).show()
                afterOcr.launch(Intent(this@BarcodeScanActivity, ScanActivity::class.java))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdown()
    }
}

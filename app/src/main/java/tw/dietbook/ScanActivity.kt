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
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executors

class ScanActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var previewView: PreviewView
    private lateinit var status: android.widget.TextView

    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) bindCamera() else {
            Toast.makeText(this, "需要相機權限才能拍營養標示", Toast.LENGTH_LONG).show()
            finish()
        }
    }

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
        top.addView(titleBar(this, "拍營養標示", "離線中文辨識熱量／蛋白質", onBack = { finish() }))
        status = label(this, "對準標示後按下拍攝。辨識完全在手機進行。", 13f)
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
        bottom.addView(primaryBtn(this, "拍攝並辨識") { capture() })
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
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(Size(1280, 720))
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capture() {
        val ic = imageCapture ?: return
        status.text = "辨識中…"
        ic.takePicture(exec, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val media = image.image
                if (media == null) {
                    image.close()
                    runOnUiThread { status.text = "無法取得影像"; Toast.makeText(this@ScanActivity, "拍攝失敗", Toast.LENGTH_SHORT).show() }
                    return
                }
                val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
                val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                recognizer.process(input)
                    .addOnSuccessListener { result ->
                        image.close()
                        val text = result.text
                        val draft = LabelParser.parse(text)
                        runOnUiThread { finishWith(draft, text) }
                    }
                    .addOnFailureListener { e ->
                        image.close()
                        runOnUiThread {
                            status.text = "辨識失敗：${e.message}"
                            Toast.makeText(this@ScanActivity, "辨識失敗", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    status.text = "拍攝失敗：${exception.message}"
                    Toast.makeText(this@ScanActivity, "拍攝失敗", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun finishWith(d: LabelDraft, raw: String) {
        if (d.kcalPer100 == null && d.kcalPerServing == null) {
            status.text = "看不太到熱量數字，請再拍清楚一點或改手動輸入。\n\n辨識文字：\n${raw.take(300)}"
            Toast.makeText(this, "未辨識到熱量，可返回手動填", Toast.LENGTH_LONG).show()
            return
        }
        setResult(RESULT_OK, Intent().apply {
            putExtra("basis", d.detectedBasis)
            d.kcalPer100?.let { putExtra("kcalPer100", it) }
            d.proteinPer100?.let { putExtra("proteinPer100", it) }
            d.kcalPerServing?.let { putExtra("kcalPerServing", it) }
            d.proteinPerServing?.let { putExtra("proteinPerServing", it) }
            d.servingG?.let { putExtra("servingG", it) }
            d.servingsPerPack?.let { putExtra("servingsPerPack", it) }
            putExtra("raw", raw)
        })
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdown()
    }
}

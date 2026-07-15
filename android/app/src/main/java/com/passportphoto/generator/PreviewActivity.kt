package com.passportphoto.generator

import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class PreviewActivity : AppCompatActivity() {

    private lateinit var imagePreview: ImageView
    private lateinit var bgGroup: RadioGroup
    private lateinit var whiteBtn: RadioButton
    private lateinit var blueBtn: RadioButton
    private lateinit var generateBtn: Button
    private lateinit var adjustBtn: Button
    private lateinit var changeBtn: Button
    private lateinit var serverInput: EditText
    private lateinit var smoothSeek: SeekBar
    private lateinit var brightSeek: SeekBar
    private lateinit var smoothText: TextView
    private lateinit var brightText: TextView

    private var imageUri: Uri? = null
    private var originalBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        imagePreview = findViewById(R.id.ivPreview)
        bgGroup = findViewById(R.id.rgBgColor)
        whiteBtn = findViewById(R.id.rbWhite)
        blueBtn = findViewById(R.id.rbBlue)
        generateBtn = findViewById(R.id.btnGenerate)
        adjustBtn = findViewById(R.id.btnAdjustCrop)
        changeBtn = findViewById(R.id.btnChangePhoto)
        serverInput = findViewById(R.id.etServerUrl)
        smoothSeek = findViewById(R.id.seekSmoothness)
        brightSeek = findViewById(R.id.seekBrightness)
        smoothText = findViewById(R.id.tvSmoothnessVal)
        brightText = findViewById(R.id.tvBrightnessVal)

        val uriString = intent.getStringExtra("imageUri")
        imageUri = uriString?.let { Uri.parse(it) }

        imageUri?.let {
            val inputStream = contentResolver.openInputStream(it)
            originalBitmap = BitmapFactory.decodeStream(inputStream)
            originalBitmap?.let { bm -> imagePreview.setImageBitmap(bm) }
        }

        smoothSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                smoothText.text = progress.toString()
                if (fromUser) updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        brightSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                brightText.text = (progress + 50).toString()
                if (fromUser) updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        generateBtn.setOnClickListener { generatePassportPhoto() }
        adjustBtn.setOnClickListener { finish() }
        changeBtn.setOnClickListener {
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)
        }
    }

    private fun updatePreview() {
        val original = originalBitmap ?: return
        val smoothness = smoothSeek.progress
        val brightness = brightSeek.progress + 50

        var result = original.copy(Bitmap.Config.ARGB_8888, true)

        if (smoothness > 0) {
            val scale = smoothness / 100f
            val factor = (1f - scale * 0.5f).coerceIn(0.2f, 1f)
            val sw = (original.width * factor).toInt().coerceAtLeast(1)
            val sh = (original.height * factor).toInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(original, sw, sh, true)
            result = Bitmap.createScaledBitmap(small, original.width, original.height, true)
        }

        if (brightness != 100) {
            val factor = brightness / 100f
            val canvas = Canvas(result)
            val paint = Paint()
            val cm = ColorMatrix().apply {
                set(floatArrayOf(
                    factor, 0f, 0f, 0f, 0f,
                    0f, factor, 0f, 0f, 0f,
                    0f, 0f, factor, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(original, 0f, 0f, paint)
        }

        imagePreview.setImageBitmap(result)
    }

    private fun generatePassportPhoto() {
        val serverUrl = serverInput.text.toString().trim()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Enter server URL first", Toast.LENGTH_SHORT).show()
            return
        }

        generateBtn.isEnabled = false
        generateBtn.text = "Processing..."

        val bgColor = when {
            whiteBtn.isChecked -> "white"
            else -> "blue"
        }

        val apiService = ApiService(serverUrl)
        apiService.generatePassportPhoto(
            originalBitmap!!, bgColor,
            smoothSeek.progress,
            brightSeek.progress + 50
        ) { resultBitmap ->
            runOnUiThread {
                generateBtn.isEnabled = true
                generateBtn.text = "Generate Passport Photo"
            }
            if (resultBitmap != null) {
                val layoutUri = saveBitmapToTempFile(resultBitmap)
                runOnUiThread {
                    val resultIntent = Intent(this, ResultActivity::class.java)
                    resultIntent.putExtra("imageUri", layoutUri.toString())
                    startActivity(resultIntent)
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Connection failed. Check server URL and try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveBitmapToTempFile(bitmap: Bitmap): Uri {
        val filename = "passport_layout_${System.currentTimeMillis()}.jpg"
        val tempFile = File(cacheDir, filename)
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return androidx.core.content.FileProvider.getUriForFile(
            this, "com.passportphoto.generator.fileprovider", tempFile
        )
    }
}

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

    private lateinit var ivPreview: ImageView
    private lateinit var rgBgColor: RadioGroup
    private lateinit var rbWhite: RadioButton
    private lateinit var rbBlue: RadioButton
    private lateinit var btnGenerate: Button
    private lateinit var btnAdjustCrop: Button
    private lateinit var btnChangePhoto: Button
    private lateinit var etServerUrl: EditText
    private lateinit var seekSmoothness: SeekBar
    private lateinit var seekBrightness: SeekBar
    private lateinit var tvSmoothnessVal: TextView
    private lateinit var tvBrightnessVal: TextView

    private var imageUri: Uri? = null
    private var originalBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        ivPreview = findViewById(R.id.ivPreview)
        rgBgColor = findViewById(R.id.rgBgColor)
        rbWhite = findViewById(R.id.rbWhite)
        rbBlue = findViewById(R.id.rbBlue)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnAdjustCrop = findViewById(R.id.btnAdjustCrop)
        btnChangePhoto = findViewById(R.id.btnChangePhoto)
        etServerUrl = findViewById(R.id.etServerUrl)
        seekSmoothness = findViewById(R.id.seekSmoothness)
        seekBrightness = findViewById(R.id.seekBrightness)
        tvSmoothnessVal = findViewById(R.id.tvSmoothnessVal)
        tvBrightnessVal = findViewById(R.id.tvBrightnessVal)

        val uriString = intent.getStringExtra("imageUri")
        imageUri = uriString?.let { Uri.parse(it) }

        imageUri?.let {
            val inputStream = contentResolver.openInputStream(it)
            originalBitmap = BitmapFactory.decodeStream(inputStream)
            originalBitmap?.let { bm -> ivPreview.setImageBitmap(bm) }
        }

        seekSmoothness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSmoothnessVal.text = progress.toString()
                if (fromUser) updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBrightnessVal.text = (progress + 50).toString()
                if (fromUser) updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnGenerate.setOnClickListener { generatePassportPhoto() }
        btnAdjustCrop.setOnClickListener { finish() }
        btnChangePhoto.setOnClickListener {
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)
        }
    }

    private fun updatePreview() {
        val original = originalBitmap ?: return
        val smoothness = seekSmoothness.progress
        val brightness = seekBrightness.progress + 50

        var result = original.copy(Bitmap.Config.ARGB_8888, true)

        if (smoothness > 0) {
            result = blur(result, smoothness)
        }
        if (brightness != 100) {
            result = adjustBrightness(result, brightness)
        }
        ivPreview.setImageBitmap(result)
    }

    private fun adjustBrightness(bitmap: Bitmap, value: Int): Bitmap {
        val factor = value / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
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
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun blur(bitmap: Bitmap, level: Int): Bitmap {
        val scale = level / 100f
        val factor = (1f - scale * 0.5f).coerceIn(0.2f, 1f)
        val sw = (bitmap.width * factor).toInt().coerceAtLeast(1)
        val sh = (bitmap.height * factor).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        return Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, true)
    }

    private fun generatePassportPhoto() {
        val serverUrl = etServerUrl.text.toString().trim()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Enter server URL first", Toast.LENGTH_SHORT).show()
            return
        }

        btnGenerate.isEnabled = false
        btnGenerate.text = "Processing..."

        val bgColor = when {
            rbWhite.isChecked -> "white"
            rbBlue.isChecked -> "blue"
            else -> "white"
        }

        val apiService = ApiService(serverUrl)
        apiService.generatePassportPhoto(
            originalBitmap!!, bgColor,
            seekSmoothness.progress,
            seekBrightness.progress + 50
        ) { resultBitmap ->
            runOnUiThread {
                btnGenerate.isEnabled = true
                btnGenerate.text = "Generate Passport Photo"
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

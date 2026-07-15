package com.passportphoto.generator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
            ivPreview.setImageBitmap(originalBitmap)
        }

        seekSmoothness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSmoothnessVal.text = progress.toString()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBrightnessVal.text = (progress + 50).toString()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnGenerate.setOnClickListener {
            generatePassportPhoto()
        }

        btnAdjustCrop.setOnClickListener {
            finish()
        }

        btnChangePhoto.setOnClickListener {
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)
        }
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

        val smoothness = seekSmoothness.progress
        val brightness = seekBrightness.progress + 50

        val apiService = ApiService(serverUrl)
        apiService.generatePassportPhoto(originalBitmap!!, bgColor, smoothness, brightness) { resultBitmap ->
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
        val outputStream = FileOutputStream(tempFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        outputStream.flush()
        outputStream.close()
        return androidx.core.content.FileProvider.getUriForFile(
            this, "com.passportphoto.generator.fileprovider", tempFile
        )
    }
}

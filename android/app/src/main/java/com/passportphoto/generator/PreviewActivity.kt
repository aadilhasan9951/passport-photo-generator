package com.passportphoto.generator

import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

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
    private var previewBitmap: Bitmap? = null

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
            originalBitmap?.let { bm ->
                previewBitmap = bm.copy(Bitmap.Config.ARGB_8888, true)
                ivPreview.setImageBitmap(bm)
            }
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

    private fun updatePreview() {
        val original = originalBitmap ?: return
        val smoothness = seekSmoothness.progress
        val brightness = seekBrightness.progress + 50

        var result = original.copy(Bitmap.Config.ARGB_8888, true)

        if (smoothness > 0) {
            result = applySmoothness(result, smoothness)
        }

        if (brightness != 100) {
            result = applyBrightness(result, brightness)
        }

        previewBitmap = result
        ivPreview.setImageBitmap(result)
    }

    private fun applyBrightness(bitmap: Bitmap, brightness: Int): Bitmap {
        val factor = brightness / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.set(floatArrayOf(
            factor, 0f, 0f, 0f, 0f,
            0f, factor, 0f, 0f, 0f,
            0f, 0f, factor, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applySmoothness(bitmap: Bitmap, level: Int): Bitmap {
        val radius = (level / 10) + 1
        return fastBlur(bitmap, radius)
    }

    private fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config, true)
        val w = bitmap.width
        val h = bitmap.height
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(w.coerceAtLeast(h))
        val divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (i in 0 until 256 * divsum) {
            dv[i] = i / divsum
        }
        var pixels = IntArray(wh)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        yw = 0
        yi = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        for (y in 0 until h) {
            bsum = 0
            gsum = 0
            rsum = 0
            for (i in -radius..radius) {
                val p = pixels[yi + i.coerceIn(0, wm)]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0xff00) shr 8
                sir[2] = p and 0xff
                rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
            }
            for (x in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                if (y == 0) {
                    vmin[x] = (x + radius + 1).coerceAtMost(wm - x)
                }
                if (y == h - 1) {
                    vmin[w + x] = (x + radius + 1).coerceAtMost(wm - x)
                }
                val p1 = pixels[yw + vmin[x]]
                val p2 = pixels[yw + vmin[x]]
                if (vmin[x] != wm) {
                    val p = pixels[yw + vmin[x]]
                    sir = stack[vmin[x]]
                    stackstart = (vmin[x] - 1).coerceIn(0, div - 1)
                    pixels[yw + vmin[x]] = p
                }
                yi++
            }
            yw += w
        }
        for (x in 0 until w) {
            bsum = 0
            gsum = 0
            rsum = 0
            yp = -radius * w
            for (i in -radius..radius) {
                yi = (yp + i * w).coerceIn(0, wh - 1)
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - kotlin.math.abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
            }
            yi = x
            for (y in 0 until h) {
                pixels[yi] = (pixels[yi] and 0xff000000.toInt()) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                if (x == 0) {
                    vmin[y] = (y + r1).coerceAtMost(hm - y) * w
                }
                if (x == w - 1) {
                    vmin[w + y] = (y + r1).coerceAtMost(hm - y) * w
                }
                val p1 = pixels[vmin[y]]
                val p2 = pixels[vmin[y]]
                if (vmin[y] != hm * w) {
                    val p = pixels[vmin[y]]
                    sir = stack[vmin[y]]
                    stackstart = (vmin[y] - 1).coerceIn(0, div - 1)
                    pixels[vmin[y]] = p
                }
                yi += w
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
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

package com.passportphoto.generator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
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
        
        // Get image URI from intent
        val uriString = intent.getStringExtra("imageUri")
        imageUri = uriString?.let { Uri.parse(it) }
        
        // Load image
        imageUri?.let {
            val inputStream = contentResolver.openInputStream(it)
            originalBitmap = BitmapFactory.decodeStream(inputStream)
            ivPreview.setImageBitmap(originalBitmap)
        }
        
        btnGenerate.setOnClickListener {
            generatePassportPhoto()
        }
        
        btnAdjustCrop.setOnClickListener {
            // Go back to crop activity
            finish()
        }
        
        btnChangePhoto.setOnClickListener {
            // Go back to main activity
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)
        }
    }
    
    private fun generatePassportPhoto() {
        // Show loading
        Toast.makeText(this, "Generating passport photo...", Toast.LENGTH_SHORT).show()
        
        // Get selected background color
        val bgColor = if (rbWhite.isChecked) "white" else "blue"
        
        // Call API for accurate background removal
        val apiService = ApiService()
        apiService.generatePassportPhoto(originalBitmap!!, bgColor) { resultBitmap ->
            if (resultBitmap != null) {
                // Save layout to temp file
                val layoutUri = saveBitmapToTempFile(resultBitmap)
                
                // Open result activity on main thread
                runOnUiThread {
                    val resultIntent = Intent(this, ResultActivity::class.java)
                    resultIntent.putExtra("imageUri", layoutUri.toString())
                    startActivity(resultIntent)
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Failed to generate photo. Check server connection.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun saveBitmapToTempFile(bitmap: Bitmap): Uri {
        val filename = "passport_layout_${System.currentTimeMillis()}.jpg"
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
        }
        
        val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            val stream = contentResolver.openOutputStream(it)
            stream?.use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)
            }
        }
        return uri!!
    }
}

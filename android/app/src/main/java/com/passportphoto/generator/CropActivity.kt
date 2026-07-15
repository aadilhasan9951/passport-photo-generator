package com.passportphoto.generator

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.CropImageView
import java.io.File
import java.io.FileOutputStream

class CropActivity : AppCompatActivity() {

    private lateinit var cropImageView: CropImageView
    private lateinit var btnDone: Button
    private lateinit var btnAutoDetect: Button
    private lateinit var btnCancel: Button
    
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        
        cropImageView = findViewById(R.id.cropImageView)
        btnDone = findViewById(R.id.btnDone)
        btnAutoDetect = findViewById(R.id.btnAutoDetect)
        btnCancel = findViewById(R.id.btnCancel)
        
        // Get image URI from intent
        val uriString = intent.getStringExtra("imageUri")
        imageUri = uriString?.let { Uri.parse(it) }
        
        // Load image into crop view
        imageUri?.let {
            cropImageView.setImageUriAsync(it)
        }
        
        btnDone.setOnClickListener {
            cropAndProceed()
        }
        
        btnAutoDetect.setOnClickListener {
            // Reset crop to center
            cropImageView.resetCropRect()
        }
        
        btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun cropAndProceed() {
        val croppedBitmap = cropImageView.getCroppedImage(413, 531)
        if (croppedBitmap != null) {
            val croppedUri = saveBitmapToTempFile(croppedBitmap)
            
            // Open preview activity
            val previewIntent = Intent(this@CropActivity, PreviewActivity::class.java)
            previewIntent.putExtra("imageUri", croppedUri.toString())
            startActivity(previewIntent)
        } else {
            Toast.makeText(this@CropActivity, "Crop failed", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveBitmapToTempFile(bitmap: Bitmap): Uri {
        val filename = "cropped_photo_${System.currentTimeMillis()}.jpg"
        val tempFile = File(cacheDir, filename)
        
        val outputStream = FileOutputStream(tempFile)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        
        return androidx.core.content.FileProvider.getUriForFile(
            this,
            "com.passportphoto.generator.fileprovider",
            tempFile
        )
    }
}

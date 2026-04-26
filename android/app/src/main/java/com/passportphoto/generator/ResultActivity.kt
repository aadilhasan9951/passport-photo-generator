package com.passportphoto.generator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class ResultActivity : AppCompatActivity() {

    private lateinit var ivResult: ImageView
    private lateinit var btnDownload: Button
    private lateinit var btnNewPhoto: Button
    
    private var resultUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        
        ivResult = findViewById(R.id.ivResult)
        btnDownload = findViewById(R.id.btnDownload)
        btnNewPhoto = findViewById(R.id.btnNewPhoto)
        
        // Get image URI from intent
        val uriString = intent.getStringExtra("imageUri")
        resultUri = uriString?.let { Uri.parse(it) }
        
        // Load result image
        resultUri?.let {
            val inputStream = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            ivResult.setImageBitmap(bitmap)
        }
        
        btnDownload.setOnClickListener {
            downloadImage()
        }
        
        btnNewPhoto.setOnClickListener {
            // Go back to main activity
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)
        }
    }
    
    private fun downloadImage() {
        resultUri?.let { uri ->
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                
                // Save to Downloads folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, "passport_photo_${System.currentTimeMillis()}.jpg")
                
                val outputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
                
                Toast.makeText(this, "Saved to Downloads: ${file.name}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

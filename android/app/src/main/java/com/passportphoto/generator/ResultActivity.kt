package com.passportphoto.generator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
                
                // Save to Pictures folder using MediaStore
                val filename = "passport_photo_${System.currentTimeMillis()}.jpg"
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                
                val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                
                val downloadUri = contentResolver.insert(collection, contentValues)
                downloadUri?.let {
                    val outputStream = contentResolver.openOutputStream(it)
                    outputStream?.use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, it)
                    }
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(it, contentValues, null, null)
                    }
                }
                
                Toast.makeText(this, "Saved to Pictures: $filename", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

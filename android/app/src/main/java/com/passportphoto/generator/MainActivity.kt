package com.passportphoto.generator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var btnGallery: Button
    private lateinit var btnCamera: Button
    
    private val GALLERY_REQUEST_CODE = 100
    private val CAMERA_REQUEST_CODE = 101
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        btnGallery = findViewById(R.id.btnGallery)
        btnCamera = findViewById(R.id.btnCamera)
        
        btnGallery.setOnClickListener {
            checkPermissionsAndOpenGallery()
        }
        
        btnCamera.setOnClickListener {
            checkPermissionsAndOpenCamera()
        }
    }
    
    private fun checkPermissionsAndOpenGallery() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        if (hasPermissions(permissions)) {
            openGallery()
        } else {
            requestPermissions(permissions, GALLERY_REQUEST_CODE)
        }
    }
    
    private fun checkPermissionsAndOpenCamera() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        )
        
        if (hasPermissions(permissions)) {
            openCamera()
        } else {
            requestPermissions(permissions, CAMERA_REQUEST_CODE)
        }
    }
    
    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }
    
    private fun openCamera() {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        val tempFile = File(externalCacheDir, "temp_camera_photo.jpg")
        val photoUri = android.net.Uri.fromFile(tempFile)
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri)
        startActivityForResult(intent, CAMERA_REQUEST_CODE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                GALLERY_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        val tempUri = saveBitmapToTempFile(loadBitmapFromUri(uri))
                        val cropIntent = Intent(this, CropActivity::class.java)
                        cropIntent.putExtra("imageUri", tempUri.toString())
                        startActivity(cropIntent)
                    }
                }
                CAMERA_REQUEST_CODE -> {
                    val tempFile = File(externalCacheDir, "temp_camera_photo.jpg")
                    if (tempFile.exists()) {
                        val tempUri = android.net.Uri.fromFile(tempFile)
                        val cropIntent = Intent(this, CropActivity::class.java)
                        cropIntent.putExtra("imageUri", tempUri.toString())
                        startActivity(cropIntent)
                    }
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            when (requestCode) {
                GALLERY_REQUEST_CODE -> openGallery()
                CAMERA_REQUEST_CODE -> openCamera()
            }
        } else {
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadBitmapFromUri(uri: Uri): android.graphics.Bitmap {
        val inputStream = contentResolver.openInputStream(uri)
        return android.graphics.BitmapFactory.decodeStream(inputStream)
    }
    
    private fun saveBitmapToTempFile(bitmap: android.graphics.Bitmap): Uri {
        val filename = "temp_photo_${System.currentTimeMillis()}.jpg"
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
        }
        
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            val stream = contentResolver.openOutputStream(it)
            stream?.use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, it)
            }
        }
        return uri!!
    }
}

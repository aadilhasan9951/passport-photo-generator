package com.passportphoto.generator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class ApiService(private val serverUrl: String = "https://passport-photo-generator-production-64b2.up.railway.app") {
    
    private val client = OkHttpClient()
    
    /**
     * Generate passport photo by calling Flask backend
     * @param bitmap The cropped image
     * @param bgColor Background color ("white" or "blue")
     * @return Generated layout bitmap
     */
    fun generatePassportPhoto(bitmap: Bitmap, bgColor: String, callback: (Bitmap?) -> Unit) {
        // Convert bitmap to file
        val tempFile = bitmapToTempFile(bitmap)
        
        // Create multipart request
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", tempFile.name, tempFile.asRequestBody("image/jpeg".toMediaType()))
            .addFormDataPart("bg_color", bgColor)
            .build()
        
        val request = Request.Builder()
            .url("$serverUrl/generate_android")
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(null)
            }
            
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.bytes()
                    val resultBitmap = BitmapFactory.decodeByteArray(responseBody, 0, responseBody?.size ?: 0)
                    callback(resultBitmap)
                } else {
                    callback(null)
                }
            }
        })
    }
    
    /**
     * Convert bitmap to temporary file
     */
    private fun bitmapToTempFile(bitmap: Bitmap): File {
        val tempFile = File.createTempFile("upload_", ".jpg")
        val outputStream = tempFile.outputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        outputStream.flush()
        outputStream.close()
        return tempFile
    }
    
    /**
     * Alternative: Use base64 encoding for smaller images
     */
    fun generatePassportPhotoBase64(bitmap: Bitmap, bgColor: String, callback: (Bitmap?) -> Unit) {
        // Convert bitmap to base64
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        val imageBytes = outputStream.toByteArray()
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        
        // Create JSON body
        val json = """
            {
                "image": "$base64Image",
                "bg_color": "$bgColor"
            }
        """.trimIndent()
        
        val requestBody = RequestBody.create("application/json".toMediaType(), json)
        
        val request = Request.Builder()
            .url("$serverUrl/generate_base64")
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(null)
            }
            
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.bytes()
                    val resultBitmap = BitmapFactory.decodeByteArray(responseBody, 0, responseBody?.size ?: 0)
                    callback(resultBitmap)
                } else {
                    callback(null)
                }
            }
        })
    }
}

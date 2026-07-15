package com.passportphoto.generator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiService(private val serverUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun generatePassportPhoto(
        bitmap: Bitmap,
        bgColor: String,
        smoothness: Int = 0,
        brightness: Int = 100,
        callback: (Bitmap?) -> Unit
    ) {
        val tempFile = bitmapToTempFile(bitmap)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", tempFile.name, tempFile.asRequestBody("image/jpeg".toMediaType()))
            .addFormDataPart("bg_color", bgColor)
            .addFormDataPart("smoothness", smoothness.toString())
            .addFormDataPart("brightness", brightness.toString())
            .build()

        val request = Request.Builder()
            .url("$serverUrl/generate_android")
            .post(requestBody)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "Connection failed: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.bytes()
                    if (responseBody != null) {
                        val resultBitmap = BitmapFactory.decodeByteArray(responseBody, 0, responseBody.size)
                        callback(resultBitmap)
                    } else {
                        callback(null)
                    }
                } else {
                    Log.e("ApiService", "Server error: ${response.code} ${response.message}")
                    callback(null)
                }
            }
        })
    }

    private fun bitmapToTempFile(bitmap: Bitmap): File {
        val tempFile = File.createTempFile("upload_", ".jpg")
        val outputStream = tempFile.outputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.flush()
        outputStream.close()
        return tempFile
    }
}

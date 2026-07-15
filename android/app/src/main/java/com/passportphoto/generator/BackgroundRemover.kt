package com.passportphoto.generator

import android.graphics.Bitmap
import android.graphics.Color

class BackgroundRemover {
    
    companion object {
        /**
         * Remove background from image
         * Simple color-based removal - removes white/light backgrounds
         * This is a fallback when TFLite model is not available
         */
        fun removeBackground(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            
            // Create output bitmap with transparency
            val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Get pixels
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // Process each pixel
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // Calculate brightness
                val brightness = (r + g + b) / 3
                
                // If pixel is very light (white/light background), make it transparent
                val alpha = if (brightness > 240) 0 else 255
                
                pixels[i] = Color.argb(alpha, r, g, b)
            }
            
            outputBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            
            return outputBitmap
        }
        
        /**
         * Apply solid background color to image
         */
        fun applyBackgroundColor(bitmap: Bitmap, bgColor: Int): Bitmap {
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            
            // Draw background color
            canvas.drawColor(bgColor)
            
            // Draw original image on top (assuming it has transparency)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            
            return result
        }
    }
}

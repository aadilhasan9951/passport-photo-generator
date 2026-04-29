package com.passportphoto.generator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

class PhotoLayoutGenerator {
    
    companion object {
        // Passport photo dimensions at 300 DPI
        private const val PASSPORT_WIDTH = 350  // Standard passport size
        private const val PASSPORT_HEIGHT = 450  // Standard passport size
        
        // 4x6 inch layout at 300 DPI (portrait)
        private const val LAYOUT_WIDTH = 1200   // 4 inches
        private const val LAYOUT_HEIGHT = 1800  // 6 inches
        
        // Border size
        private const val BORDER_SIZE = 2
        
        /**
         * Generate 4x6 layout with 8 passport photos (2 columns x 4 rows)
         * Photos are rotated 90 degrees to the right
         */
        fun generateLayout(photoBitmap: Bitmap, bgColor: Int = Color.WHITE): Bitmap {
            // Rotate photo 90 degrees to the right (clockwise)
            val rotatedPhoto = rotateBitmap(photoBitmap, -90f)
            
            // Resize to passport size
            val passportPhoto = Bitmap.createScaledBitmap(
                rotatedPhoto,
                PASSPORT_WIDTH,
                PASSPORT_HEIGHT,
                true
            )
            
            // Add border
            val borderedPhoto = addBorder(passportPhoto, BORDER_SIZE, Color.BLACK)
            
            // Create white canvas
            val layout = Bitmap.createBitmap(LAYOUT_WIDTH, LAYOUT_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(layout)
            canvas.drawColor(bgColor)
            
            // Calculate grid layout (2 columns x 4 rows)
            val cols = 2
            val rows = 4
            
            val photoWidth = borderedPhoto.width
            val photoHeight = borderedPhoto.height
            
            // Calculate spacing with extra gap between photos
            // Add minimum 10px gap between photos (border to border)
            val minGap = 10
            
            // Calculate available space for gaps
            val totalPhotoWidth = cols * photoWidth
            val totalPhotoHeight = rows * photoHeight
            
            val availableWidth = LAYOUT_WIDTH - totalPhotoWidth
            val availableHeight = LAYOUT_HEIGHT - totalPhotoHeight
            
            // Distribute gaps evenly between photos and edges
            var marginX = availableWidth / (cols + 1)
            var marginY = availableHeight / (rows + 1)
            
            // Ensure minimum gap
            marginX = maxOf(minGap, marginX)
            marginY = maxOf(minGap, marginY)
            
            // Paste photos in grid
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val x = marginX + col * (photoWidth + marginX)
                    val y = marginY + row * (photoHeight + marginY)
                    canvas.drawBitmap(borderedPhoto, x.toFloat(), y.toFloat(), null)
                }
            }
            
            return layout
        }
        
        /**
         * Rotate bitmap by specified degrees
         */
        private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(degrees)
            
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            
            return rotated
        }
        
        /**
         * Add border around bitmap
         */
        private fun addBorder(bitmap: Bitmap, borderSize: Int, borderColor: Int): Bitmap {
            val borderedWidth = bitmap.width + (2 * borderSize)
            val borderedHeight = bitmap.height + (2 * borderSize)
            
            val bordered = Bitmap.createBitmap(borderedWidth, borderedHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bordered)
            
            // Draw border
            canvas.drawColor(borderColor)
            
            // Draw original photo
            canvas.drawBitmap(bitmap, borderSize.toFloat(), borderSize.toFloat(), null)
            
            return bordered
        }
    }
}

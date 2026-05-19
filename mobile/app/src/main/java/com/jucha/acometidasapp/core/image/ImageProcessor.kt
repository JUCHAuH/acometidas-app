package com.jucha.acometidasapp.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object ImageProcessor {

    private var isInitialized = false

    private fun initOpenCV() {
        if (!isInitialized) {
            try {
                if (!OpenCVLoader.initDebug()) {
                    Log.e("ImageProcessor", "OpenCV init failed")
                } else {
                    isInitialized = true
                    Log.d("ImageProcessor", "OpenCV initialized successfully")
                }
            } catch (e: Exception) {
                Log.e("ImageProcessor", "Error initializing OpenCV: ${e.message}")
            }
        }
    }

    suspend fun applyScanFilter(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.Default) {
        try {
            initOpenCV()

            val bitmap = loadBitmapFromUri(context, uri) ?: return@withContext null

            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Convertir a escala de grises
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

            // Aplicar CLAHE para mejorar contraste localmente
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            val claheResult = Mat()
            clahe.apply(gray, claheResult)

            // Aumentar brillo y contraste
            val result = Mat()
            claheResult.convertTo(result, -1, 1.3, 30.0)

            // Binarización adaptativa para mejorar legibilidad del texto
            val binarized = Mat()
            Imgproc.adaptiveThreshold(
                result, binarized, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11, 2.0
            )

            // Convertir de vuelta a bitmap
            val outputBitmap = Bitmap.createBitmap(binarized.cols(), binarized.rows(), Bitmap.Config.RGB_565)
            Utils.matToBitmap(binarized, outputBitmap)

            // Liberar recursos
            mat.release()
            gray.release()
            claheResult.release()
            result.release()
            binarized.release()

            Log.d("ImageProcessor", "Scan filter applied successfully")
            outputBitmap
        } catch (e: Exception) {
            Log.e("ImageProcessor", "Error applying scan filter: ${e.message}", e)
            null
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap
        } catch (e: Exception) {
            Log.e("ImageProcessor", "Error loading bitmap: ${e.message}")
            null
        }
    }
}


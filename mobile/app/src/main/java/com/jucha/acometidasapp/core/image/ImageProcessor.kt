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

            // Convertir de RGB a BGR
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR)

            // 1. Aplicar Bilateral Filter para reducir ruido mientras preserva bordes
            val denoised = Mat()
            Imgproc.bilateralFilter(mat, denoised, 9, 75.0, 75.0)

            // 2. Convertir a HSV
            val hsv = Mat()
            Imgproc.cvtColor(denoised, hsv, Imgproc.COLOR_BGR2HSV)

            val hsvChannels = ArrayList<Mat>()
            Core.split(hsv, hsvChannels)
            val h = hsvChannels[0]
            val s = hsvChannels[1]
            val v = hsvChannels[2].clone()

            // 3. WHITENING SELECTIVO - Aumentar brillo de áreas claras (fondo)
            // Para píxeles con V > 150 (áreas claras), aumentar hacia 255 (más blanco)
            val vWhitened = Mat()
            v.convertTo(vWhitened, -1, 1.0, 0.0)

            // Aplicar función de whitening: para V > 150, aumentar más agresivamente
            val whitenKernel = Mat(v.rows(), v.cols(), CvType.CV_8U)
            val data = ByteArray(v.rows() * v.cols())
            v.get(0, 0, data)

            for (i in data.indices) {
                val pixel = data[i].toInt() and 0xFF
                data[i] = when {
                    pixel > 180 -> 255.toByte() // Áreas muy claras -> blanco puro
                    pixel > 150 -> (pixel + (255 - pixel) * 0.5).toInt().toByte() // Áreas claras -> más blancas
                    else -> pixel.toByte() // Áreas oscuras -> sin cambio
                }
            }
            whitenKernel.put(0, 0, data)

            // 4. Aplicar morphological opening para limpiar manchas pequeñas
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
            val vCleaned = Mat()
            Imgproc.morphologyEx(whitenKernel, vCleaned, Imgproc.MORPH_OPEN, kernel)
            kernel.release()

            // 5. Aplicar CLAHE al canal V limpiado - realza contraste
            val clahe = Imgproc.createCLAHE(3.5, Size(15.0, 15.0))
            val vEnhanced = Mat()
            clahe.apply(vCleaned, vEnhanced)

            // 6. Aumentar saturación para colores más vibrantes
            val sMult = Mat()
            s.convertTo(sMult, -1, 1.5, 0.0) // 50% más saturación

            // 7. Aplicar Unsharp Mask para nitidez
            val blurred = Mat()
            Imgproc.GaussianBlur(vEnhanced, blurred, Size(5.0, 5.0), 1.0)
            val sharpened = Mat()
            Core.addWeighted(vEnhanced, 1.8, blurred, -0.8, 0.0, sharpened)

            // 8. Fusionar canales HSV
            val hsvResult = Mat()
            val enhancedChannels = listOf(h, sMult, sharpened)
            Core.merge(enhancedChannels, hsvResult)

            // 9. Convertir de vuelta a BGR
            val bgr = Mat()
            Imgproc.cvtColor(hsvResult, bgr, Imgproc.COLOR_HSV2BGR)

            // 10. Convertir a RGB
            val outputMat = Mat()
            Imgproc.cvtColor(bgr, outputMat, Imgproc.COLOR_BGR2RGB)

            // 11. Aumentar contraste final
            val contrasted = Mat()
            outputMat.convertTo(contrasted, -1, 1.2, 10.0)

            // 12. Crear Bitmap
            val outputBitmap = Bitmap.createBitmap(contrasted.cols(), contrasted.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(contrasted, outputBitmap)

            // Liberar memoria
            mat.release()
            denoised.release()
            hsv.release()
            h.release()
            s.release()
            v.release()
            whitenKernel.release()
            vWhitened.release()
            vCleaned.release()
            vEnhanced.release()
            sMult.release()
            blurred.release()
            sharpened.release()
            hsvResult.release()
            bgr.release()
            outputMat.release()
            contrasted.release()

            Log.d("ImageProcessor", "Professional scan filter applied - fondo ultra blanco, texto nítido")
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

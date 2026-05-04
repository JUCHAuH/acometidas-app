package com.jucha.acometidasapp.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class FileUtil {

    fun saveFotoLocally(
        context: Context,
        uri: Uri,
        predioId: String,
        tipo: String
    ): File {
        // Obtener o crear directorio de fotos
        val fotosDir = File(context.getExternalFilesDir("fotos"), predioId)
        fotosDir.mkdirs()

        // Generar nombre de archivo: {tipo}_{timestamp}.jpg
        val fileName = "${tipo}_${System.currentTimeMillis()}.jpg"
        val outputFile = File(fotosDir, fileName)

        // Cargar bitmap desde URI
        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)

        // Redimensionar si es necesario (máx 1280x1280)
        val resizedBitmap = resizeBitmapIfNeeded(bitmap, 1280)

        // Guardar como JPEG con compresión 80%
        FileOutputStream(outputFile).use { fos ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
            fos.flush()
        }

        // Limpiar memoria
        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle()
        }

        return outputFile
    }

    fun getFotosDir(context: Context, predioId: String): File {
        return File(context.getExternalFilesDir("fotos"), predioId)
    }

    fun getFotoFile(context: Context, predioId: String, tipo: String): File? {
        val fotosDir = getFotosDir(context, predioId)
        if (!fotosDir.exists()) return null

        // Obtener la foto más reciente de este tipo
        return fotosDir.listFiles()?.filter { it.name.startsWith("${tipo}_") }
            ?.maxByOrNull { it.lastModified() }
    }

    fun getFotoFiles(context: Context, predioId: String): List<File> {
        val fotosDir = getFotosDir(context, predioId)
        return if (fotosDir.exists()) {
            fotosDir.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun deleteFotosDir(context: Context, predioId: String): Boolean {
        val fotosDir = getFotosDir(context, predioId)
        return if (fotosDir.exists()) {
            fotosDir.deleteRecursively()
        } else {
            true
        }
    }

    private fun resizeBitmapIfNeeded(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val scale = minOf(
            maxSize.toFloat() / width,
            maxSize.toFloat() / height
        )

        val newWidth = (width * scale).roundToInt()
        val newHeight = (height * scale).roundToInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}

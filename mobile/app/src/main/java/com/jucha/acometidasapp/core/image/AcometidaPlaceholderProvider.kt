package com.jucha.acometidasapp.core.image

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File


object AcometidaPlaceholderProvider {

    private const val ASSET_DIR = "acometidas_alcantarillado"

    fun obtenerAleatoria(context: Context): Uri? {
        return try {
            val archivos = context.assets.list(ASSET_DIR)?.filter {
                it.endsWith(".jpg", ignoreCase = true) || it.endsWith(".jpeg", ignoreCase = true)
            } ?: return null
            if (archivos.isEmpty()) return null

            val elegido = archivos.random()
            val photosDir = File(context.cacheDir, "photos").also { it.mkdirs() }
            val destino = File(photosDir, "placeholder_${System.currentTimeMillis()}.jpg")

            context.assets.open("$ASSET_DIR/$elegido").use { input ->
                destino.outputStream().use { output -> input.copyTo(output) }
            }

            FileProvider.getUriForFile(context, "${context.packageName}.provider", destino)
        } catch (e: Exception) {
            Log.e("AcometidaPlaceholder", "Error obteniendo imagen aleatoria: ${e.message}", e)
            null
        }
    }
}

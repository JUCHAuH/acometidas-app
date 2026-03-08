package com.jucha.acometidasapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.jucha.acometidasapp.BuildConfig
import com.jucha.acometidasapp.data.model.CreateFotoDto
import com.jucha.acometidasapp.data.model.CreatePredioDto
import com.jucha.acometidasapp.data.model.FotoDto
import com.jucha.acometidasapp.data.model.PredioDto
import com.jucha.acometidasapp.data.model.UpdatePredioDto
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.remote.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class PredioRepository(
    private val api: PredioApiService,
    private val httpClient: OkHttpClient = SupabaseClient.httpClient
) {

    suspend fun getPredios(): Result<List<PredioDto>> = runCatching {
        api.getPredios()
    }

    suspend fun getPrediosByProyecto(proyectoId: String): Result<List<PredioDto>> = runCatching {
        api.getPrediosByProyecto(proyectoIdFilter = "eq.$proyectoId")
    }

    suspend fun getFotosByPredio(predioId: String): Result<List<FotoDto>> = runCatching {
        api.getFotosByPredio(predioIdFilter = "eq.$predioId")
    }

    suspend fun createPredio(predio: CreatePredioDto): Result<PredioDto> = runCatching {
        api.createPredio(predio).first()
    }

    suspend fun createFoto(foto: CreateFotoDto): Result<FotoDto> = runCatching {
        api.createFoto(foto).first()
    }

    suspend fun deletePredio(id: String): Result<Unit> = runCatching {
        val response = api.deletePredio(idFilter = "eq.$id")
        Log.d("Repository", "deletePredio code=${response.code()} body=${response.errorBody()?.string()}")
        if (!response.isSuccessful) throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
    }

    suspend fun getPredioById(id: String): Result<PredioDto> = runCatching {
        api.getPredioById(idFilter = "eq.$id").first()
    }

    suspend fun deleteFotosByPredioTipo(predioId: String, tipo: String): Result<Unit> = runCatching {
        val response = api.deleteFotosByPredioTipo(predioIdFilter = "eq.$predioId", tipoFilter = "eq.$tipo")
        if (!response.isSuccessful) throw Exception("Error al eliminar fotos: ${response.code()}")
    }

    /**
     * Borra del Storage todos los archivos asociados a un predio.
     * Usa el endpoint batch-delete de Supabase Storage.
     * No lanza excepción si falla — la eliminación del predio en BD procede igual.
     */
    suspend fun deleteStorageFilesForPredio(predioId: String) {
        withContext(Dispatchers.IO) {
            val fotos = getFotosByPredio(predioId).getOrElse { return@withContext }
            if (fotos.isEmpty()) return@withContext

            val fileNames = fotos.map { it.url.substringAfterLast("/") }
            val json = buildString {
                append("{\"prefixes\":[")
                fileNames.forEachIndexed { i, name ->
                    if (i > 0) append(",")
                    append("\"").append(name).append("\"")
                }
                append("]}") 
            }
            val requestBody = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/storage/v1/object/delete/acometidas")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()
            runCatching {
                httpClient.newCall(request).execute().use {
                    Log.d("Repository", "deleteStorageFiles predioId=$predioId status=${it.code}")
                }
            }.onFailure {
                Log.e("Repository", "deleteStorageFiles falló para predioId=$predioId", it)
            }
        }
    }

    suspend fun updatePredio(id: String, update: UpdatePredioDto): Result<PredioDto> = runCatching {
        api.updatePredio(idFilter = "eq.$id", update = update).first()
    }

    suspend fun uploadFoto(
        context: Context,
        uri: Uri,
        predioId: String,
        tipo: String
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {

            val raw = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            val scaled = if (bitmap.width > 1280 || bitmap.height > 1280) {
                val ratio = minOf(1280f / bitmap.width, 1280f / bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true
                )
            } else bitmap
            val bytes = java.io.ByteArrayOutputStream().also {
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, it)
            }.toByteArray()
            Log.d("Repository", "uploadFoto tipo=$tipo original=${raw.size/1024}KB comprimido=${bytes.size/1024}KB")

            val fileName = "${predioId}_${tipo}_${System.currentTimeMillis()}.jpg"
            val requestBody = bytes.toRequestBody("image/jpeg".toMediaType())
            val request = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/storage/v1/object/acometidas/$fileName")
                .post(requestBody)
                .header("Content-Type", "image/jpeg")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Upload fallo ${response.code}: ${response.body?.string()}")
                }
            }
            "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/acometidas/$fileName"
        }
    }
}

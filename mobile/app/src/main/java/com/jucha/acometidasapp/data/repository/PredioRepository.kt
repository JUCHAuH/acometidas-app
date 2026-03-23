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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class PredioRepository(
    private val api: PredioApiService,
    private val httpClient: OkHttpClient = SupabaseClient.httpClient
) {

    private fun extractStorageKeyFromUrl(url: String): String {
        val publicPrefix = "/storage/v1/object/public/acometidas/"
        val objectPrefix = "/storage/v1/object/acometidas/"
        return when {
            url.contains(publicPrefix) -> url.substringAfter(publicPrefix).substringBefore("?")
            url.contains(objectPrefix) -> url.substringAfter(objectPrefix).substringBefore("?")
            else -> url.substringAfterLast("/").substringBefore("?")
        }
    }

    private fun encodePathSegments(path: String): String {
        return path.split("/")
            .filter { it.isNotBlank() }
            .joinToString("/") {
                URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20")
            }
    }

    private fun listStorageObjectKeysByPrefix(prefix: String): List<String> {
        val requestBody = JSONObject()
            .put("prefix", prefix)
            .put("limit", 1000)
            .put("offset", 0)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/storage/v1/object/list/acometidas")
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(
                        "Repository",
                        "listStorageByPrefix falló prefix=$prefix: ${response.code} ${response.body?.string()}"
                    )
                    return@use emptyList()
                }

                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) return@use emptyList()

                val json = JSONArray(raw)
                buildList {
                    for (i in 0 until json.length()) {
                        val item = json.optJSONObject(i) ?: continue
                        val name = item.optString("name", "")
                        if (name.isBlank()) continue
                        add(if (name.startsWith(prefix)) name else "$prefix$name")
                    }
                }
            }
        }.onFailure { e ->
            Log.e("Repository", "listStorageByPrefix excepción prefix=$prefix", e)
        }.getOrElse { emptyList() }
    }

    private fun deleteStorageObjectByKey(objectKey: String) {
        val encodedObjectKey = encodePathSegments(objectKey)
        val singleDeleteRequest = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/storage/v1/object/acometidas/$encodedObjectKey")
            .delete()
            .build()

        runCatching {
            httpClient.newCall(singleDeleteRequest).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("Repository", "deleteStorageFile ok $objectKey -> ${response.code}")
                    return@use
                }

                val errorBody = response.body?.string().orEmpty()
                Log.e("Repository", "deleteStorageFile falló $objectKey: ${response.code} $errorBody")

                val batchBody = "{\"prefixes\":[\"$objectKey\"]}"
                    .toRequestBody("application/json".toMediaType())
                val batchDeleteRequest = Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL}/storage/v1/object/acometidas")
                    .delete(batchBody)
                    .header("Content-Type", "application/json")
                    .build()

                httpClient.newCall(batchDeleteRequest).execute().use { batchResponse ->
                    if (batchResponse.isSuccessful) {
                        Log.d("Repository", "deleteStorageFile fallback ok $objectKey -> ${batchResponse.code}")
                    } else {
                        Log.e(
                            "Repository",
                            "deleteStorageFile fallback falló $objectKey: ${batchResponse.code} ${batchResponse.body?.string()}"
                        )
                    }
                }
            }
        }.onFailure { e ->
            Log.e("Repository", "deleteStorageFile excepción $objectKey", e)
        }
    }

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

    suspend fun deleteAllFotosByPredio(predioId: String): Result<Unit> = runCatching {
        val response = api.deleteAllFotosByPredio(predioIdFilter = "eq.$predioId")
        if (!response.isSuccessful) throw Exception("Error al eliminar fotos del predio: ${response.code()}")
    }

    /**
     * Borra del Storage todos los archivos asociados a un predio.
    */
    suspend fun deleteStorageFilesForPredio(predioId: String) {
        withContext(Dispatchers.IO) {
            Log.d("Repository", "deleteStorageFilesForPredio inicio predioId=$predioId")

            val keys = linkedSetOf<String>()

            val fotosResult = getFotosByPredio(predioId)
            fotosResult.onSuccess { fotos ->
                fotos.mapTo(keys) { extractStorageKeyFromUrl(it.url) }
                Log.d("Repository", "deleteStorageFilesForPredio fotosBD=${fotos.size}")
            }.onFailure { e ->
                Log.e("Repository", "deleteStorageFilesForPredio getFotos falló predioId=$predioId", e)
            }

            val prefixKeys = listStorageObjectKeysByPrefix("${predioId}_")
            keys.addAll(prefixKeys)

            val objectKeys = keys.filter { it.isNotBlank() }
            if (objectKeys.isEmpty()) {
                Log.w("Repository", "deleteStorageFilesForPredio sin claves a borrar predioId=$predioId")
                return@withContext
            }

            Log.d("Repository", "deleteStorageFilesForPredio totalClaves=${objectKeys.size} predioId=$predioId")
            objectKeys.forEach { deleteStorageObjectByKey(it) }
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

package com.jucha.acometidasapp.data.repository

import android.content.Context
import android.net.Uri
import com.jucha.acometidasapp.BuildConfig
import com.jucha.acometidasapp.data.model.CreateFotoDto
import com.jucha.acometidasapp.data.model.CreatePredioDto
import com.jucha.acometidasapp.data.model.FotoDto
import com.jucha.acometidasapp.data.model.PredioDto
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

    suspend fun getFotosByPredio(predioId: String): Result<List<FotoDto>> = runCatching {
        api.getFotosByPredio(predioIdFilter = "eq.$predioId")
    }

    suspend fun createPredio(predio: CreatePredioDto): Result<PredioDto> = runCatching {
        api.createPredio(predio).first()
    }

    suspend fun createFoto(foto: CreateFotoDto): Result<FotoDto> = runCatching {
        api.createFoto(foto).first()
    }

    suspend fun uploadFoto(
        context: Context,
        uri: Uri,
        predioId: String,
        tipo: String
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val fileName = "${predioId}_${tipo}_${System.currentTimeMillis()}.jpg"
            val requestBody = bytes.toRequestBody("image/jpeg".toMediaType())
            val request = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/storage/v1/object/fotos/$fileName")
                .post(requestBody)
                .addHeader("apikey", BuildConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
                .addHeader("Content-Type", "image/jpeg")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Upload falló ${response.code}: ${response.body?.string()}")
                }
            }
            "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/fotos/$fileName"
        }
    }
}

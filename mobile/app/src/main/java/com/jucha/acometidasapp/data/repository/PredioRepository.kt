package com.jucha.acometidasapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.jucha.acometidasapp.BuildConfig
import com.jucha.acometidasapp.core.sync.SyncState
import com.jucha.acometidasapp.data.local.AcometidasDatabase
import com.jucha.acometidasapp.data.local.entity.FotoLocalEntity
import com.jucha.acometidasapp.data.local.entity.PredioLocalEntity
import com.jucha.acometidasapp.data.model.CreateFotoDto
import com.jucha.acometidasapp.data.model.CreatePredioDto
import com.jucha.acometidasapp.data.model.FotoDto
import com.jucha.acometidasapp.data.model.PredioDto
import com.jucha.acometidasapp.data.model.UpdatePredioDto
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.remote.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    // ============ MÉTODOS OFFLINE-FIRST ============

    /**
     * Sube una foto de archivo local a Supabase Storage.
     * Similar a uploadFoto pero recibe un File path string.
     */
    suspend fun uploadFoto(
        filePath: String,
        predioId: String,
        tipo: String
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                throw IOException("Archivo no existe: $filePath")
            }

            val raw = file.readBytes()
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

    /**
     * Crea un predio en la base de datos local (Room).
     */
    suspend fun createPredioOffline(context: Context, predio: PredioLocalEntity): String {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.predioLocalDao()
        dao.insert(predio)
        Log.d("Repository", "Predio creado localmente: ${predio.id}")
        return predio.id
    }

    /**
     * Inserta una foto en la base de datos local.
     */
    suspend fun insertFotoLocal(context: Context, foto: FotoLocalEntity): Long {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.fotoLocalDao()
        return dao.insert(foto)
    }

    suspend fun getPredioLocalById(context: Context, id: String): PredioLocalEntity? {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.predioLocalDao()
        return dao.getById(id)
    }

    /**
     * Obtiene todos los predios de la BD local.
     */
    suspend fun getPrediosLocal(context: Context): List<PredioLocalEntity> {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.predioLocalDao()
        return dao.getAll()
    }

    /**
     * Obtiene flow de predios locales (para actualización en tiempo real).
     */
    fun getPrediosLocalFlow(context: Context): Flow<List<PredioLocalEntity>> {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.predioLocalDao()
        return dao.getAllFlow()
    }

    /**
     * Obtiene predios locales de un proyecto específico.
     */
    suspend fun getPrediosLocalByProyecto(context: Context, proyectoId: String): List<PredioLocalEntity> {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.predioLocalDao()
        return dao.getByProyectoId(proyectoId)
    }

    /**
     * Obtiene flow de predios locales de un proyecto.
     */
    fun getPrediosLocalByProyectoFlow(context: Context, proyectoId: String): Flow<List<PredioLocalEntity>> {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.predioLocalDao()
        return dao.getByProyectoIdFlow(proyectoId)
    }

    /**
     * Obtiene predios pendientes de sincronización.
     */
    suspend fun getPrediosPendingSync(context: Context): List<PredioLocalEntity> {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.predioLocalDao()
        return dao.getBySyncState(SyncState.PENDING.name)
    }

    /**
     * Actualiza el estado de sincronización de un predio.
     */
    suspend fun updatePredioSyncState(context: Context, predioId: String, syncState: SyncState) {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.predioLocalDao()
        dao.updateSyncState(predioId, syncState.name)
    }

    /**
     * Actualiza estado de sync con remote ID (después de sincronizar exitosamente).
     */
    suspend fun updatePredioSyncStateWithRemoteId(
        context: Context,
        predioId: String,
        syncState: SyncState,
        remoteId: String
    ) {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.predioLocalDao()
        dao.updateSyncStateWithRemoteId(predioId, syncState.name, remoteId)
    }

    /**
     * Registra un error de sincronización.
     */
    suspend fun recordPredioSyncError(context: Context, predioId: String, error: String) {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.predioLocalDao()
        dao.recordSyncError(predioId, SyncState.FAILED.name, error)
    }

    /**
     * Obtiene fotos locales de un predio.
     */
    suspend fun getFotosLocal(context: Context, predioId: String): List<FotoLocalEntity> {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.fotoLocalDao()
        return dao.getByPredioId(predioId)
    }

    /**
     * Obtiene flow de fotos locales.
     */
    fun getFotosLocalFlow(context: Context, predioId: String): Flow<List<FotoLocalEntity>> {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.fotoLocalDao()
        return dao.getByPredioIdFlow(predioId)
    }

    /**
     * Borra un predio de la BD local.
     */
    suspend fun deletePredioLocal(context: Context, predioId: String) {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.predioLocalDao()
        dao.deleteById(predioId)
    }

    // ============ MÉTODOS DE PROYECTOS LOCALES ============

    /**
     * Inserta un proyecto en BD local.
     */
    suspend fun insertProyectoLocal(context: Context, proyecto: com.jucha.acometidasapp.data.local.entity.ProyectoLocalEntity) {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.proyectoLocalDao()
        dao.insertProyecto(proyecto)
    }

    /**
     * Obtiene un proyecto local por ID.
     */
    suspend fun getProyectoLocalById(context: Context, proyectoId: String): com.jucha.acometidasapp.data.local.entity.ProyectoLocalEntity? {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.proyectoLocalDao()
        return dao.getProyectoById(proyectoId)
    }

    /**
     * Obtiene todos los proyectos locales.
     */
    suspend fun getAllProyectosLocal(context: Context): List<com.jucha.acometidasapp.data.local.entity.ProyectoLocalEntity> {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.proyectoLocalDao()
        return dao.getAllProyectos()
    }

    /**
     * Limpia todos los proyectos locales (cuando sincroniza).
     */
    suspend fun deleteAllProyectosLocal(context: Context) {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.proyectoLocalDao()
        dao.deleteAllProyectos()
    }

    // ============ MÉTODOS DE ASIGNACIONES LOCALES ============

    /**
     * Inserta una asignación usuario-proyecto en BD local.
     */
    suspend fun insertProyectoUsuarioLocal(
        context: Context,
        usuarioId: String,
        proyectoId: String
    ) {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.proyectoUsuarioLocalDao()
        dao.insertAsignacion(
            com.jucha.acometidasapp.data.local.entity.ProyectoUsuarioLocalEntity(
                proyecto_id = proyectoId,
                usuario_id = usuarioId
            )
        )
    }

    /**
     * Obtiene los proyectos asignados a un usuario en BD local.
     */
    suspend fun getProyectosLocalDeUsuario(context: Context, usuarioId: String): List<String> {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.proyectoUsuarioLocalDao()
        return dao.getProyectosDeUsuario(usuarioId)
    }

    /**
     * Verifica si un usuario tiene asignado un proyecto.
     */
    suspend fun usuarioTieneProyectoLocal(context: Context, usuarioId: String, proyectoId: String): Boolean {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.proyectoUsuarioLocalDao()
        return dao.usuarioTieneProyecto(usuarioId, proyectoId)
    }

    /**
     * Limpia todas las asignaciones locales (cuando sincroniza).
     */
    suspend fun deleteAllAsignacionesLocal(context: Context) {
        val db = AcometidasDatabase.getDatabase(context)
        val dao = db.proyectoUsuarioLocalDao()
        dao.deleteAllAsignaciones()
    }
}

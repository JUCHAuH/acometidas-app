package com.jucha.acometidasapp.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jucha.acometidasapp.core.sync.SyncState
import com.jucha.acometidasapp.core.utils.FileUtil
import com.jucha.acometidasapp.core.navigation.SesionUsuario
import com.jucha.acometidasapp.data.local.AcometidasDatabase
import com.jucha.acometidasapp.data.model.CreateFotoDto
import com.jucha.acometidasapp.data.model.CreatePredioDto
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.repository.PredioRepository
import java.io.File

class SyncPrediosWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AcometidasDatabase.getDatabase(context)
    private val predioDao = db.predioLocalDao()
    private val fotoDao = db.fotoLocalDao()
    private val predioApiService = SupabaseClient.retrofit.create(PredioApiService::class.java)
    private val fileUtil = FileUtil()

    companion object {
        private const val TAG = "SyncPrediosWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Iniciando sincronización de predios")

            // 0. SINCRONIZAR ASIGNACIONES DE USUARIO (para encargados)
            try {
                if (!SesionUsuario.isAdmin) {
                    Log.d(TAG, "Sincronizando asignaciones del usuario...")
                    db.proyectoUsuarioLocalDao().deleteAllAsignaciones()

                    val usuarioRepository = com.jucha.acometidasapp.data.repository.UsuarioRepository(
                        api = SupabaseClient.retrofit.create(com.jucha.acometidasapp.data.remote.UsuarioApiService::class.java)
                    )

                    usuarioRepository.getProyectosDeUsuario(SesionUsuario.id)
                        .onSuccess { proyectoIds ->
                            for (id in proyectoIds) {
                                db.proyectoUsuarioLocalDao().insertAsignacion(
                                    com.jucha.acometidasapp.data.local.entity.ProyectoUsuarioLocalEntity(
                                        proyecto_id = id,
                                        usuario_id = SesionUsuario.id
                                    )
                                )
                            }
                            Log.d(TAG, "Asignaciones sincronizadas: ${proyectoIds.size}")
                        }
                        .onFailure { e ->
                            Log.e(TAG, "Error sincronizando asignaciones: ${e.message}")
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en sincronización de asignaciones: ${e.message}")
            }

            // 1. Obtener predios pendientes
            val prediosPendientes = predioDao.getBySyncState(SyncState.PENDING.name)
            Log.d(TAG, "Predios pendientes encontrados: ${prediosPendientes.size}")

            if (prediosPendientes.isEmpty()) {
                Log.d(TAG, "No hay predios para sincronizar")
                return Result.success()
            }

            for (predio in prediosPendientes) {
                try {
                    Log.d(TAG, "Sincronizando predio: ${predio.id}")

                    // 1. Actualizar estado a SYNCING
                    predioDao.updateSyncState(predio.id, SyncState.SYNCING.name)

                    // 2. Obtener fotos locales
                    val fotosLocales = fotoDao.getByPredioId(predio.id)
                    val fotosUrlsMap = mutableMapOf<String, String>()

                    // 3. Subir fotos a Supabase Storage
                    for (fotoLocal in fotosLocales) {
                        try {
                            val fotoFile = File(fotoLocal.localPath)
                            if (fotoFile.exists()) {
                                val repository = PredioRepository(predioApiService)
                                val result = repository.uploadFoto(
                                    fotoFile.absolutePath,
                                    predio.id,
                                    fotoLocal.tipo
                                )

                                result.onSuccess { url ->
                                    fotosUrlsMap[fotoLocal.tipo] = url
                                    Log.d(TAG, "Foto subida: ${fotoLocal.tipo} -> $url")
                                    predioDao.updateSyncState(predio.id, SyncState.SYNCING.name)
                                }.onFailure { error ->
                                    Log.e(TAG, "Error subiendo foto: ${error.message}")
                                    throw error
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error procesando foto local: ${e.message}")
                            throw e
                        }
                    }

                    // 4. Crear predio en Supabase
                    Log.d(TAG, "Creando predio en Supabase: numeroContrato=${predio.numeroContrato}, proyectoId=${predio.proyectoId}")
                    val createPredioDto = CreatePredioDto(
                        numeroContrato = predio.numeroContrato,
                        codigoPredio = predio.codigoPredio,
                        usuario = predio.usuario,
                        direccion = predio.direccion,
                        estado = predio.estado,
                        proyectoId = predio.proyectoId ?: ""
                    )

                    val prediosResponse = predioApiService.createPredio(createPredioDto)
                    Log.d(TAG, "API response: ${prediosResponse.size} predios retornados")
                    if (prediosResponse.isEmpty()) {
                        throw Exception("API retornó lista vacía")
                    }

                    val predioRemoto = prediosResponse[0]
                    Log.d(TAG, "Predio creado en Supabase: ${predioRemoto.id}")

                    // 5. Registrar fotos en tabla fotos de Supabase
                    Log.d(TAG, "Registrando ${fotosUrlsMap.size} fotos en Supabase...")
                    for ((tipo, url) in fotosUrlsMap) {
                        try {
                            val createFotoDto = CreateFotoDto(
                                predioId = predioRemoto.id,
                                tipo = tipo,
                                url = url
                            )
                            predioApiService.createFoto(createFotoDto)
                            Log.d(TAG, "Foto registrada en BD: $tipo -> $url")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error registrando foto en BD: ${e.message}")
                        }
                    }

                    // 6. Actualizar predio local con datos del remoto (incluyendo codigoPredio)
                    Log.d(TAG, "Actualizando predio local con datos remotos...")
                    val predioActualizado = predio.copy(
                        remoteId = predioRemoto.id,
                        codigoPredio = predioRemoto.codigoPredio,  // Sincronizar código del remoto
                        numeroContrato = predioRemoto.numeroContrato ?: predio.numeroContrato,
                        usuario = predioRemoto.usuario,
                        direccion = predioRemoto.direccion,
                        estado = predioRemoto.estado ?: predio.estado,
                        syncState = SyncState.SYNCED.name
                    )
                    predioDao.update(predioActualizado)
                    Log.d(TAG, "Predio actualizado localmente: id=${predio.id}, remoteId=${predioRemoto.id}, codigoPredio=${predioRemoto.codigoPredio}")

                    Log.d(TAG, "Predio sincronizado exitosamente: ${predio.id}")

                } catch (e: Exception) {
                    Log.e(TAG, "Error sincronizando predio ${predio.id}: ${e.message}", e)
                    Log.e(TAG, "Stack trace:", e)
                    predioDao.recordSyncError(
                        predio.id,
                        SyncState.FAILED.name,
                        e.message ?: "Error desconocido"
                    )
                }
            }

            Log.d(TAG, "Sincronización completada")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error general en sincronización: ${e.message}", e)
            Result.retry()
        }
    }
}

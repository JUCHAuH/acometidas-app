package com.jucha.acometidasapp.ui.predios

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jucha.acometidasapp.data.local.AcometidasDatabase
import com.jucha.acometidasapp.data.local.entity.PredioLocalEntity
import com.jucha.acometidasapp.data.model.PredioDto
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.repository.PredioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class PrediosUiState {
    object Loading : PrediosUiState()
    data class Success(val predios: List<PredioDto>, val prediosLocales: List<PredioLocalEntity>) : PrediosUiState()
    data class Error(val message: String) : PrediosUiState()
}

class PrediosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PredioRepository(
        api = SupabaseClient.retrofit.create(PredioApiService::class.java)
    )

    private val db = AcometidasDatabase.getDatabase(application)
    private val predioLocalDao = db.predioLocalDao()

    private val _uiState = MutableStateFlow<PrediosUiState>(PrediosUiState.Loading)
    val uiState: StateFlow<PrediosUiState> = _uiState

    private val _predios = MutableStateFlow<List<PredioDto>>(emptyList())
    private val _prediosLocales = MutableStateFlow<List<PredioLocalEntity>>(emptyList())
    val busqueda = MutableStateFlow("")

    private var proyectoId = ""

    private fun combinarPredios(
        prediosLocales: List<PredioLocalEntity>,
        prediosRemotos: List<PredioDto>
    ): List<PredioDto> {
        val localDtos = prediosLocales.map { local ->
            PredioDto(
                id = local.remoteId ?: local.id,
                numeroParte = null,
                numeroContrato = local.numeroContrato,
                codigoPredio = local.codigoPredio,
                usuario = local.usuario,
                telefonoUsuario = local.telefonoUsuario,
                direccion = local.direccion,
                observaciones = local.observaciones,
                estado = local.estado,
                proyectoId = local.proyectoId,
                createdAt = null,
                updatedAt = null
            )
        }
        // Remotos primero: tienen datos actualizados y ganan el distinctBy.
        // Locales complementan solo cuando no hay equivalente remoto (predios PENDING sin remoteId).
        return (prediosRemotos + localDtos)
            .distinctBy { it.id }
            .sortedBy { it.codigoPredio.toLongOrNull() ?: 0L }
    }

    fun setProyectoId(id: String) {
        if (proyectoId == id) return   // ya cargado
        proyectoId = id
        cargarPredios()
    }

    fun cargarPredios() {
        if (proyectoId.isEmpty()) return
        _uiState.value = PrediosUiState.Loading
        viewModelScope.launch {
            try {
                // 1. Cargar predios locales SIEMPRE directamente de Room (no caché)
                val prediosLocales = db.predioLocalDao().getByProyectoId(proyectoId)
                _prediosLocales.value = prediosLocales
                Log.d("PrediosVM", "Predios locales cargados de Room: ${prediosLocales.size}")

                // 2. Cargar predios remotos
                repository.getPrediosByProyecto(proyectoId)
                    .onSuccess { predios ->
                        _predios.value = predios
                        Log.d("PrediosVM", "Predios remotos cargados: ${predios.size}")

                        _uiState.value = PrediosUiState.Success(
                            combinarPredios(prediosLocales, predios),
                            prediosLocales
                        )
                    }
                    .onFailure { error ->
                        Log.e("PrediosVM", "Error cargando predios remotos: ${error.message}")
                        if (prediosLocales.isNotEmpty()) {
                            _uiState.value = PrediosUiState.Success(
                                combinarPredios(prediosLocales, emptyList()),
                                prediosLocales
                            )
                        } else {
                            val esErrorRed = error.message?.contains("Unable to resolve host") == true ||
                                error.message?.contains("Network") == true ||
                                error.message?.contains("No address associated") == true ||
                                error.message?.contains("failed to connect") == true ||
                                error.message?.contains("timeout") == true
                            val mensaje = if (esErrorRed) {
                                "Sin conexión a internet.\n\nEstás en modo sin conexión. Puedes crear " +
                                "predios normalmente, pero no es posible conectar con la base de datos.\n\n" +
                                "Intenta nuevamente cuando recuperes la conexión."
                            } else {
                                error.message ?: "Error al cargar los predios"
                            }
                            _uiState.value = PrediosUiState.Error(mensaje)
                        }
                    }
            } catch (e: Exception) {
                Log.e("PrediosVM", "Error en cargarPredios: ${e.message}", e)
                _uiState.value = PrediosUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun eliminarPredio(id: String) {
        viewModelScope.launch {
            try {
                val predioLocal = _prediosLocales.value.find { it.id == id || it.remoteId == id }

                // Quitar de la UI inmediatamente
                if (_uiState.value is PrediosUiState.Success) {
                    val estado = _uiState.value as PrediosUiState.Success
                    _uiState.value = PrediosUiState.Success(
                        estado.predios.filter { it.id != id },
                        estado.prediosLocales.filter { it.id != id && it.remoteId != id }
                    )
                }

                if (predioLocal != null) {
                    val idLocal = predioLocal.id
                    val remoteId = predioLocal.remoteId
                    Log.d("PrediosVM", "Eliminando predio local: $idLocal, remoteId: $remoteId")

                    db.predioLocalDao().deleteById(idLocal)
                    repository.deleteStorageFilesForPredio(idLocal)
                    repository.deleteAllFotosByPredio(idLocal)

                    val idParaBorrar = remoteId ?: idLocal
                    repository.deletePredio(idParaBorrar)
                        .onSuccess { Log.d("PrediosVM", "Predio eliminado del servidor: $idParaBorrar") }
                        .onFailure { e -> Log.e("PrediosVM", "Error eliminando del servidor: ${e.message}") }
                } else {
                    // Predio solo remoto (creado con conexión, sin copia local)
                    Log.d("PrediosVM", "Eliminando predio solo remoto: $id")
                    repository.deleteAllFotosByPredio(id)
                    repository.deletePredio(id)
                        .onSuccess { Log.d("PrediosVM", "Predio remoto eliminado: $id") }
                        .onFailure { e -> Log.e("PrediosVM", "Error eliminando predio remoto: ${e.message}") }
                }

            } catch (e: Exception) {
                Log.e("PrediosVM", "Error en eliminarPredio: ${e.message}", e)
                cargarPredios()
            }
        }
    }

    fun getPredioLocalState(predioId: String): String? {
        return _prediosLocales.value.find { it.id == predioId || it.remoteId == predioId }?.syncState
    }

    fun getPredioLocalError(predioId: String): String? {
        return _prediosLocales.value.find { it.id == predioId || it.remoteId == predioId }?.syncError
    }
}

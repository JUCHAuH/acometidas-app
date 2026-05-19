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
        return (prediosLocales.map { local ->
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
        } + prediosRemotos).distinctBy { it.id }.sortedBy { it.codigoPredio.toLongOrNull() ?: 0L }
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
                        // Aunque falle la API, mostrar predios locales
                        _uiState.value = if (prediosLocales.isNotEmpty()) {
                            PrediosUiState.Success(
                                combinarPredios(prediosLocales, emptyList()),
                                prediosLocales
                            )
                        } else {
                            PrediosUiState.Error(
                                error.message ?: "Error al cargar los predios"
                            )
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
                // 1. Buscar el predio por ID o remoteId
                val predioLocal = _prediosLocales.value.find { it.id == id || it.remoteId == id }
                if (predioLocal == null) {
                    Log.e("PrediosVM", "Predio no encontrado: $id")
                    return@launch
                }

                val idLocal = predioLocal.id
                val remoteId = predioLocal.remoteId
                Log.d("PrediosVM", "Eliminando predio - ID local: $idLocal, remoteId: $remoteId, búsqueda: $id")

                // 2. Remover inmediatamente del estado actual (actualizar UI)
                if (_uiState.value is PrediosUiState.Success) {
                    val estado = _uiState.value as PrediosUiState.Success
                    val prediosFiltrados = estado.predios.filter {
                        it.id != id && it.id != idLocal && it.id != remoteId
                    }
                    _uiState.value = PrediosUiState.Success(prediosFiltrados, estado.prediosLocales.filter { it.id != idLocal })
                    Log.d("PrediosVM", "UI actualizada - predios restantes: ${prediosFiltrados.size}")
                }

                // 3. Borrar de Room
                db.predioLocalDao().deleteById(idLocal)
                Log.d("PrediosVM", "Predio eliminado de Room: $idLocal")

                // 4. Borrar fotos y archivos
                repository.deleteStorageFilesForPredio(idLocal)
                repository.deleteAllFotosByPredio(idLocal)

                // 5. Borrar del servidor
                val idParaBorrar = remoteId ?: idLocal
                repository.deletePredio(idParaBorrar)
                    .onSuccess {
                        Log.d("PrediosVM", "Predio eliminado del servidor: $idParaBorrar")
                    }
                    .onFailure { e ->
                        Log.e("PrediosVM", "Error eliminando del servidor: ${e.message}")
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

package com.jucha.acometidasapp.ui.predios

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jucha.acometidasapp.data.model.PredioDto
import com.jucha.acometidasapp.data.model.UpdatePredioDto
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.repository.PredioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class PrediosUiState {
    object Loading : PrediosUiState()
    data class Success(val predios: List<PredioDto>) : PrediosUiState()
    data class Error(val message: String) : PrediosUiState()
}

class PrediosViewModel : ViewModel() {

    private val repository = PredioRepository(
        api = SupabaseClient.retrofit.create(PredioApiService::class.java)
    )

    private val _uiState = MutableStateFlow<PrediosUiState>(PrediosUiState.Loading)
    val uiState: StateFlow<PrediosUiState> = _uiState

    private val _predios = MutableStateFlow<List<PredioDto>>(emptyList())
    val busqueda = MutableStateFlow("")
    val filtroDireccion = MutableStateFlow<String?>(null)
    val direcciones: StateFlow<List<String>> = _predios
        .map { list -> list.mapNotNull { it.direccion?.trim()?.ifBlank { null } }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        cargarPredios()
    }

    fun cargarPredios() {
        _uiState.value = PrediosUiState.Loading
        viewModelScope.launch {
            repository.getPredios()
                .onSuccess { predios ->
                    _predios.value = predios
                    _uiState.value = PrediosUiState.Success(predios)
                }
                .onFailure { error ->
                    _uiState.value = PrediosUiState.Error(
                        error.message ?: "Error al cargar los predios"
                    )
                }
        }
    }

    fun eliminarPredio(id: String) {
        viewModelScope.launch {
            repository.deletePredio(id)
                .onSuccess { cargarPredios() }
                .onFailure { e ->
                    Log.e("PrediosVM", "Error al eliminar: ${e.message}", e)
                    _uiState.value = PrediosUiState.Error(e.message ?: "Error al eliminar el predio")
                }
        }
    }

    fun editarPredio(id: String, update: UpdatePredioDto) {
        viewModelScope.launch {
            repository.updatePredio(id, update)
                .onSuccess { cargarPredios() }
                .onFailure { /* opcional: exponer error */ }
        }
    }
}

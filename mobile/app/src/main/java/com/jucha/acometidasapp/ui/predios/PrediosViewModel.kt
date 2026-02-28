package com.jucha.acometidasapp.ui.predios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jucha.acometidasapp.data.model.PredioDto
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.repository.PredioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    init {
        cargarPredios()
    }

    fun cargarPredios() {
        _uiState.value = PrediosUiState.Loading
        viewModelScope.launch {
            repository.getPredios()
                .onSuccess { predios ->
                    _uiState.value = PrediosUiState.Success(predios)
                }
                .onFailure { error ->
                    _uiState.value = PrediosUiState.Error(
                        error.message ?: "Error al cargar los predios"
                    )
                }
        }
    }
}

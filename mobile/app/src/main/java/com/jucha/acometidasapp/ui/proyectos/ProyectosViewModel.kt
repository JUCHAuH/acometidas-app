package com.jucha.acometidasapp.ui.proyectos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jucha.acometidasapp.core.navigation.SesionUsuario
import com.jucha.acometidasapp.data.model.ProyectoDto
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.remote.ProyectoApiService
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.remote.UsuarioApiService
import com.jucha.acometidasapp.data.repository.PredioRepository
import com.jucha.acometidasapp.data.repository.ProyectoRepository
import com.jucha.acometidasapp.data.repository.UsuarioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ProyectosUiState {
    object Loading : ProyectosUiState()
    data class Success(val proyectos: List<ProyectoDto>) : ProyectosUiState()
    data class Error(val message: String) : ProyectosUiState()
}

class ProyectosViewModel : ViewModel() {

    private val repository = ProyectoRepository(
        api = SupabaseClient.retrofit.create(ProyectoApiService::class.java)
    )
    private val predioRepository = PredioRepository(
        api = SupabaseClient.retrofit.create(PredioApiService::class.java)
    )
    private val usuarioRepository = UsuarioRepository(
        api = SupabaseClient.retrofit.create(UsuarioApiService::class.java)
    )

    private val _uiState = MutableStateFlow<ProyectosUiState>(ProyectosUiState.Loading)
    val uiState: StateFlow<ProyectosUiState> = _uiState

    init { cargarProyectos() }

    fun cargarProyectos() {
        _uiState.value = ProyectosUiState.Loading
        viewModelScope.launch {
            if (SesionUsuario.isAdmin) {
                // Admin sees all projects
                repository.getProyectos()
                    .onSuccess { _uiState.value = ProyectosUiState.Success(it) }
                    .onFailure { _uiState.value = ProyectosUiState.Error(it.message ?: "Error al cargar proyectos") }
            } else {
                // Encargado sees only assigned projects
                val asignados = usuarioRepository.getProyectosDeUsuario(SesionUsuario.id)
                    .getOrDefault(emptyList())
                if (asignados.isEmpty()) {
                    _uiState.value = ProyectosUiState.Success(emptyList())
                    return@launch
                }
                repository.getProyectos()
                    .onSuccess { todos ->
                        val filtrados = todos.filter { it.id in asignados }
                        _uiState.value = ProyectosUiState.Success(filtrados)
                    }
                    .onFailure { _uiState.value = ProyectosUiState.Error(it.message ?: "Error al cargar proyectos") }
            }
        }
    }

    fun crearProyecto(nombre: String, tipo: String) {
        viewModelScope.launch {
            repository.createProyecto(nombre.trim(), tipo)
                .onSuccess { cargarProyectos() }
                .onFailure { _uiState.value = ProyectosUiState.Error(it.message ?: "Error al crear proyecto") }
        }
    }

    fun eliminarProyecto(id: String) {
        viewModelScope.launch {
            predioRepository.getPrediosByProyecto(id)
                .onSuccess { predios ->
                    predios.forEach { predio ->
                        predioRepository.deleteStorageFilesForPredio(predio.id)
                    }
                }
            repository.deleteProyecto(id)
                .onSuccess { cargarProyectos() }
                .onFailure { _uiState.value = ProyectosUiState.Error(it.message ?: "Error al eliminar proyecto") }
        }
    }
}

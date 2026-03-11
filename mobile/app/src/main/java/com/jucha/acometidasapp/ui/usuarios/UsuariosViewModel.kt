package com.jucha.acometidasapp.ui.usuarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jucha.acometidasapp.data.model.ProyectoDto
import com.jucha.acometidasapp.data.model.UsuarioDto
import com.jucha.acometidasapp.data.remote.ProyectoApiService
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.remote.UsuarioApiService
import com.jucha.acometidasapp.data.repository.ProyectoRepository
import com.jucha.acometidasapp.data.repository.UsuarioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class UsuariosUiState {
    object Loading : UsuariosUiState()
    data class Success(
        val usuarios: List<UsuarioDto>,
        val proyectos: List<ProyectoDto> = emptyList(),
        val asignaciones: Map<String, List<String>> = emptyMap() 
    ) : UsuariosUiState()
    data class Error(val message: String) : UsuariosUiState()
}

class UsuariosViewModel : ViewModel() {

    private val usuarioRepo = UsuarioRepository(
        api = SupabaseClient.retrofit.create(UsuarioApiService::class.java)
    )
    private val proyectoRepo = ProyectoRepository(
        api = SupabaseClient.retrofit.create(ProyectoApiService::class.java)
    )

    private val _uiState = MutableStateFlow<UsuariosUiState>(UsuariosUiState.Loading)
    val uiState: StateFlow<UsuariosUiState> = _uiState

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje

    init { cargar() }

    fun cargar() {
        _uiState.value = UsuariosUiState.Loading
        viewModelScope.launch {
            val usuariosResult = usuarioRepo.getUsuarios()
            val proyectosResult = proyectoRepo.getProyectos()

            if (usuariosResult.isFailure) {
                _uiState.value = UsuariosUiState.Error(
                    usuariosResult.exceptionOrNull()?.message ?: "Error al cargar usuarios"
                )
                return@launch
            }

            val usuarios = usuariosResult.getOrDefault(emptyList())
            val proyectos = proyectosResult.getOrDefault(emptyList())

        
            val asignaciones = mutableMapOf<String, List<String>>()
            usuarios.filter { it.rol == "encargado" }.forEach { user ->
                usuarioRepo.getProyectosDeUsuario(user.id)
                    .onSuccess { asignaciones[user.id] = it }
            }

            _uiState.value = UsuariosUiState.Success(usuarios, proyectos, asignaciones)
        }
    }

    fun crearUsuario(nombre: String, usuario: String, password: String) {
        viewModelScope.launch {
            usuarioRepo.crearUsuario(nombre.trim(), usuario.trim(), password)
                .onSuccess {
                    _mensaje.value = "Usuario creado"
                    cargar()
                }
                .onFailure {
                    _mensaje.value = it.message ?: "Error al crear usuario"
                }
        }
    }

    fun eliminarUsuario(id: String) {
        viewModelScope.launch {
            usuarioRepo.deleteUsuario(id)
                .onSuccess { cargar() }
                .onFailure { _mensaje.value = it.message ?: "Error al eliminar" }
        }
    }

    fun asignarProyecto(usuarioId: String, proyectoId: String) {
        viewModelScope.launch {
            usuarioRepo.asignarAProyecto(proyectoId, usuarioId)
                .onSuccess { cargar() }
                .onFailure { _mensaje.value = it.message ?: "Error al asignar" }
        }
    }

    fun desasignarProyecto(usuarioId: String, proyectoId: String) {
        viewModelScope.launch {
            usuarioRepo.desasignarDeProyecto(proyectoId, usuarioId)
                .onSuccess { cargar() }
                .onFailure { _mensaje.value = it.message ?: "Error al desasignar" }
        }
    }

    fun clearMensaje() { _mensaje.value = null }
}

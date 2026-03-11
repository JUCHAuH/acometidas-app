package com.jucha.acometidasapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jucha.acometidasapp.core.navigation.SesionUsuario
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.remote.UsuarioApiService
import com.jucha.acometidasapp.data.repository.UsuarioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel : ViewModel() {

    private val repository = UsuarioRepository(
        api = SupabaseClient.retrofit.create(UsuarioApiService::class.java)
    )

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun login(usuario: String, password: String) {
        if (usuario.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Completá usuario y contraseña")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            repository.login(usuario.trim(), password)
                .onSuccess { user ->
                    SesionUsuario.id      = user.id
                    SesionUsuario.nombre  = user.nombre
                    SesionUsuario.usuario = user.usuario
                    SesionUsuario.rol     = user.rol
                    _uiState.value = LoginUiState.Success
                }
                .onFailure { e ->
                    _uiState.value = LoginUiState.Error(e.message ?: "Error al iniciar sesión")
                }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}

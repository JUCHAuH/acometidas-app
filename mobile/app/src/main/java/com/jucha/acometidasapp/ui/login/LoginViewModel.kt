package com.jucha.acometidasapp.ui.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jucha.acometidasapp.core.navigation.SesionUsuario
import com.jucha.acometidasapp.core.sync.ConnectivityObserver
import com.jucha.acometidasapp.core.utils.SessionPreferences
import com.jucha.acometidasapp.data.local.entity.ProyectoLocalEntity
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.remote.UsuarioApiService
import com.jucha.acometidasapp.data.repository.PredioRepository
import com.jucha.acometidasapp.data.repository.ProyectoRepository
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

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val usuarioRepository = UsuarioRepository(
        api = SupabaseClient.retrofit.create(UsuarioApiService::class.java)
    )

    private val proyectoRepository = ProyectoRepository(
        api = SupabaseClient.retrofit.create(com.jucha.acometidasapp.data.remote.ProyectoApiService::class.java)
    )

    private val predioRepository = PredioRepository(
        api = SupabaseClient.retrofit.create(com.jucha.acometidasapp.data.remote.PredioApiService::class.java)
    )

    private val connectivityObserver = ConnectivityObserver(application)

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        // Intentar cargar sesión guardada automáticamente
        intentarCargarSesionGuardada()
    }

    private fun intentarCargarSesionGuardada() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val sesion = SessionPreferences.loadSession(ctx)

            if (sesion != null) {
                Log.d("LoginVM", "Sesión guardada encontrada, restaurando...")
                // Restaurar sesión
                SesionUsuario.id      = sesion.id
                SesionUsuario.nombre  = sesion.nombre
                SesionUsuario.usuario = sesion.usuario
                SesionUsuario.rol     = sesion.rol

                // Pre-cargar proyectos en Room y ESPERAR a que terminen
                precargarProyectosEnRoom()

                _uiState.value = LoginUiState.Success
            }
        }
    }

    fun login(usuario: String, password: String) {
        if (usuario.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Completá usuario y contraseña")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            usuarioRepository.login(usuario.trim(), password)
                .onSuccess { user ->
                    SesionUsuario.id      = user.id
                    SesionUsuario.nombre  = user.nombre
                    SesionUsuario.usuario = user.usuario
                    SesionUsuario.rol     = user.rol

                    // Guardar sesión para login offline futuro
                    SessionPreferences.saveSession(
                        getApplication(),
                        user.id,
                        user.nombre,
                        user.usuario,
                        user.rol
                    )

                    // Pre-cargar proyectos y asignaciones en Room - ESPERAR A QUE TERMINEN
                    precargarProyectosEnRoom()

                    _uiState.value = LoginUiState.Success
                }
                .onFailure { e ->
                    _uiState.value = LoginUiState.Error(e.message ?: "Error al iniciar sesión")
                }
        }
    }

    private suspend fun precargarProyectosEnRoom() {
        try {
            val ctx = getApplication<Application>()
            Log.d("LoginVM", "Precargando proyectos en Room...")

            if (SesionUsuario.isAdmin) {
                // Admin: cargar todos los proyectos
                proyectoRepository.getProyectos()
                    .onSuccess { proyectos ->
                        // Limpiar y recargar
                        predioRepository.deleteAllProyectosLocal(ctx)

                        for (proyecto in proyectos) {
                            predioRepository.insertProyectoLocal(
                                ctx,
                                ProyectoLocalEntity(
                                    id = proyecto.id,
                                    nombre = proyecto.nombre,
                                    tipo = proyecto.tipo
                                )
                            )
                        }
                        Log.d("LoginVM", "Proyectos cargados (admin): ${proyectos.size}")
                    }
                    .onFailure { e ->
                        Log.e("LoginVM", "Error cargando proyectos (admin): ${e.message}")
                        // Sin internet: No borrar proyectos anteriores, usar los que hay
                        Log.d("LoginVM", "Usando proyectos anteriores de Room (offline)")
                    }
            } else {
                // Encargado: cargar solo sus proyectos asignados
                try {
                    val resultado = usuarioRepository.getProyectosDeUsuario(SesionUsuario.id)
                    resultado.onSuccess { proyectoIds ->
                        Log.d("LoginVM", "Proyecto IDs obtenidos para encargado: ${proyectoIds.size}")

                        // Limpiar y recargar
                        predioRepository.deleteAllProyectosLocal(ctx)
                        predioRepository.deleteAllAsignacionesLocal(ctx)

                        // Obtener detalles de todos los proyectos para tener sus nombres
                        proyectoRepository.getProyectos()
                            .onSuccess { todosProyectos ->
                                // Filtrar solo los proyectos asignados
                                val proyectosAsignados = todosProyectos.filter { it.id in proyectoIds }

                                for (proyecto in proyectosAsignados) {
                                    val proyectoLocal = ProyectoLocalEntity(
                                        id = proyecto.id,
                                        nombre = proyecto.nombre,  // Nombre real
                                        tipo = proyecto.tipo
                                    )
                                    predioRepository.insertProyectoLocal(ctx, proyectoLocal)
                                    Log.d("LoginVM", "Proyecto insertado en Room: ${proyecto.id} - ${proyecto.nombre}")

                                    predioRepository.insertProyectoUsuarioLocal(
                                        ctx,
                                        SesionUsuario.id,
                                        proyecto.id
                                    )
                                    Log.d("LoginVM", "Asignación insertada en Room: ${SesionUsuario.id} -> ${proyecto.id}")
                                }
                                Log.d("LoginVM", "Proyectos cargados (encargado): ${proyectosAsignados.size}")
                            }
                            .onFailure { e ->
                                Log.e("LoginVM", "Error obteniendo detalles de proyectos: ${e.message}")
                                // Si no se pueden obtener detalles, al menos guardar los IDs como placeholders
                                for (proyectoId in proyectoIds) {
                                    val proyecto = ProyectoLocalEntity(
                                        id = proyectoId,
                                        nombre = proyectoId,  // placeholder
                                        tipo = "agua_potable"
                                    )
                                    predioRepository.insertProyectoLocal(ctx, proyecto)
                                    predioRepository.insertProyectoUsuarioLocal(ctx, SesionUsuario.id, proyectoId)
                                }
                            }
                    }
                    resultado.onFailure { e ->
                        Log.e("LoginVM", "Error cargando proyectos (encargado): ${e.message}")
                        // Sin internet: No borrar proyectos anteriores, usar los que hay
                        Log.d("LoginVM", "Usando proyectos anteriores de Room (offline)")
                    }
                } catch (e: Exception) {
                    Log.e("LoginVM", "Excepción cargando proyectos encargado: ${e.message}", e)
                }
            }
            Log.d("LoginVM", "Precarga de proyectos completada")
        } catch (e: Exception) {
            Log.e("LoginVM", "Error en precargarProyectosEnRoom: ${e.message}", e)
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}


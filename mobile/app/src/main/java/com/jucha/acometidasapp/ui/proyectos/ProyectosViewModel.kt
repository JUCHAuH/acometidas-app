package com.jucha.acometidasapp.ui.proyectos

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jucha.acometidasapp.core.navigation.SesionUsuario
import com.jucha.acometidasapp.data.local.AcometidasDatabase
import com.jucha.acometidasapp.data.local.entity.ProyectoLocalEntity
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

class ProyectosViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AcometidasDatabase.getDatabase(application)
    private val proyectoDao = db.proyectoLocalDao()
    private val proyectoUsuarioDao = db.proyectoUsuarioLocalDao()

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
            try {
                if (SesionUsuario.isAdmin) {
                    // Admin: leer todos los proyectos de Room
                    val proyectosLocales = proyectoDao.getAllProyectos()
                    Log.d("ProyectosVM", "Proyectos locales (admin): ${proyectosLocales.size}")

                    if (proyectosLocales.isNotEmpty()) {
                        _uiState.value = ProyectosUiState.Success(
                            proyectosLocales.map { ProyectoDto(id = it.id, nombre = it.nombre, tipo = it.tipo, createdAt = null) }
                        )
                    }

                    // Intentar obtener de API para actualizar
                    repository.getProyectos()
                        .onSuccess { proyectosApi ->
                            Log.d("ProyectosVM", "Proyectos obtenidos de API: ${proyectosApi.size}")
                            // Sincronizar Room: borrar proyectos que ya no existen en el servidor
                            val apiIds = proyectosApi.map { it.id }.toSet()
                            proyectosLocales.filter { it.id !in apiIds }.forEach {
                                Log.d("ProyectosVM", "Borrando proyecto huérfano de Room: ${it.nombre}")
                                proyectoDao.deleteProyectoById(it.id)
                            }
                            // Actualizar Room con datos actuales del servidor (nombre, tipo pueden haber cambiado)
                            proyectosApi.forEach { dto ->
                                proyectoDao.insertProyecto(ProyectoLocalEntity(id = dto.id, nombre = dto.nombre, tipo = dto.tipo))
                            }
                            _uiState.value = ProyectosUiState.Success(proyectosApi)
                        }
                        .onFailure { e ->
                            Log.e("ProyectosVM", "Error cargando de API: ${e.message}")
                            if (proyectosLocales.isEmpty()) {
                                _uiState.value = ProyectosUiState.Error(e.message ?: "Error al cargar proyectos")
                            }
                        }
                } else {
                    // Encargado: leer proyectos asignados de Room
                    val proyectosLocales = proyectoDao.getAllProyectos()
                    val proyectosAsignados = proyectoUsuarioDao.getProyectosDeUsuario(SesionUsuario.id)
                    Log.d("ProyectosVM", "Asignaciones locales: ${proyectosAsignados.size}")

                    val filtrados = proyectosLocales.filter { it.id in proyectosAsignados }
                    if (filtrados.isNotEmpty()) {
                        _uiState.value = ProyectosUiState.Success(
                            filtrados.map { ProyectoDto(id = it.id, nombre = it.nombre, tipo = it.tipo, createdAt = null) }
                        )
                    }

                    // Intentar obtener de API para actualizar
                    usuarioRepository.getProyectosDeUsuario(SesionUsuario.id)
                        .onSuccess { asignados ->
                            Log.d("ProyectosVM", "Asignaciones obtenidas de API: ${asignados.size}")
                            repository.getProyectos()
                                .onSuccess { todos ->
                                    // Sincronizar Room: borrar proyectos que ya no existen en el servidor
                                    val todosIds = todos.map { it.id }.toSet()
                                    proyectosLocales.filter { it.id !in todosIds }.forEach {
                                        Log.d("ProyectosVM", "Borrando proyecto huérfano de Room: ${it.nombre}")
                                        proyectoDao.deleteProyectoById(it.id)
                                    }
                                    todos.forEach { dto ->
                                        proyectoDao.insertProyecto(ProyectoLocalEntity(id = dto.id, nombre = dto.nombre, tipo = dto.tipo))
                                    }
                                    val filtradosApi = todos.filter { it.id in asignados }
                                    _uiState.value = ProyectosUiState.Success(filtradosApi)
                                }
                                .onFailure { e ->
                                    Log.e("ProyectosVM", "Error cargando proyectos de API: ${e.message}")
                                    if (filtrados.isEmpty()) {
                                        _uiState.value = ProyectosUiState.Error(e.message ?: "Error al cargar proyectos")
                                    }
                                }
                        }
                        .onFailure { e ->
                            Log.e("ProyectosVM", "Error cargando asignaciones de API: ${e.message}")
                            if (filtrados.isEmpty()) {
                                _uiState.value = ProyectosUiState.Error(e.message ?: "Error al cargar proyectos")
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e("ProyectosVM", "Error en cargarProyectos: ${e.message}", e)
                _uiState.value = ProyectosUiState.Error(e.message ?: "Error desconocido")
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

    fun renameProyecto(id: String, nuevoNombre: String) {
        viewModelScope.launch {
            repository.renameProyecto(id, nuevoNombre.trim())
                .onSuccess { cargarProyectos() }
                .onFailure { _uiState.value = ProyectosUiState.Error(it.message ?: "Error al renombrar proyecto") }
        }
    }

    fun eliminarProyecto(id: String) {
        viewModelScope.launch {
            predioRepository.getPrediosByProyecto(id)
                .onSuccess { predios ->
                    predios.forEach { predio ->
                        predioRepository.deleteStorageFilesForPredio(predio.id)
                        predioRepository.deleteAllFotosByPredio(predio.id)
                    }
                }
            repository.deleteProyecto(id)
                .onSuccess { cargarProyectos() }
                .onFailure { _uiState.value = ProyectosUiState.Error(it.message ?: "Error al eliminar proyecto") }
        }
    }
}

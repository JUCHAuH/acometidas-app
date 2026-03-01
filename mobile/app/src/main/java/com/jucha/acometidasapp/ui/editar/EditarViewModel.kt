package com.jucha.acometidasapp.ui.editar

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jucha.acometidasapp.data.model.CreateFotoDto
import com.jucha.acometidasapp.data.model.UpdatePredioDto
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.repository.PredioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class EditarSaveState {
    object Idle    : EditarSaveState()
    object Saving  : EditarSaveState()
    object Success : EditarSaveState()
    data class Error(val message: String) : EditarSaveState()
}

class EditarViewModel(
    application: Application,
    private val predioId: String
) : AndroidViewModel(application) {

    private val repository = PredioRepository(
        api = SupabaseClient.retrofit.create(PredioApiService::class.java)
    )

    // ── Estado de carga inicial ──────────────────────────────────────────────
    var isLoading    by mutableStateOf(true)
    var loadError    by mutableStateOf<String?>(null)

    // ── Campos del formulario ────────────────────────────────────────────────
    var numeroParte     by mutableStateOf("")
    var numeroContrato  by mutableStateOf("")
    var codigoPredio    by mutableStateOf("")
    var usuario         by mutableStateOf("")
    var telefonoUsuario by mutableStateOf("")
    var direccion       by mutableStateOf("")
    var observaciones   by mutableStateOf("")
    var estado          by mutableStateOf("pendiente")

    // ── Fotos existentes (URLs del servidor) ─────────────────────────────────
    var fotoPredioUrl     by mutableStateOf<String?>(null)
    var fotoAcometidaUrl  by mutableStateOf<String?>(null)
    var fotoMedidorUrl    by mutableStateOf<String?>(null)

    // ── Fotos nuevas capturadas localmente ───────────────────────────────────
    var fotoPredioUriNueva    by mutableStateOf<Uri?>(null)
    var fotoAcometidaUriNueva by mutableStateOf<Uri?>(null)
    var fotoMedidorUriNueva   by mutableStateOf<Uri?>(null)

    // ── Estado de guardado ───────────────────────────────────────────────────
    private val _saveState = MutableStateFlow<EditarSaveState>(EditarSaveState.Idle)
    val saveState: StateFlow<EditarSaveState> = _saveState

    init { cargar() }

    private fun cargar() {
        viewModelScope.launch {
            isLoading  = true
            loadError  = null
            repository.getPredioById(predioId)
                .onSuccess { predio ->
                    numeroParte     = predio.numeroParte ?: ""
                    numeroContrato  = predio.numeroContrato
                    codigoPredio    = predio.codigoPredio
                    usuario         = predio.usuario
                    telefonoUsuario = predio.telefonoUsuario ?: ""
                    direccion       = predio.direccion ?: ""
                    observaciones   = predio.observaciones ?: ""
                    estado          = predio.estado
                    // Cargar fotos existentes
                    repository.getFotosByPredio(predioId).onSuccess { fotos ->
                        fotoPredioUrl    = fotos.lastOrNull { it.tipo == "predio" }?.url
                        fotoAcometidaUrl = fotos.lastOrNull { it.tipo == "acometida" }?.url
                        fotoMedidorUrl   = fotos.lastOrNull { it.tipo == "medidor" }?.url
                    }
                    isLoading = false
                }
                .onFailure { e ->
                    loadError = e.message ?: "Error al cargar el predio"
                    isLoading = false
                }
        }
    }

    fun setFotoNueva(tipo: String, uri: Uri) {
        when (tipo) {
            "predio"    -> fotoPredioUriNueva    = uri
            "acometida" -> fotoAcometidaUriNueva = uri
            "medidor"   -> fotoMedidorUriNueva   = uri
        }
    }

    fun resetSaveState() { _saveState.value = EditarSaveState.Idle }

    fun guardar() {
        if (numeroContrato.isBlank() || codigoPredio.isBlank() || usuario.isBlank()) {
            _saveState.value = EditarSaveState.Error("Nº Contrato, Código Predio y Usuario son obligatorios")
            return
        }
        _saveState.value = EditarSaveState.Saving
        viewModelScope.launch {
            repository.updatePredio(
                id = predioId,
                update = UpdatePredioDto(
                    numeroParte     = numeroParte.ifBlank { null },
                    numeroContrato  = numeroContrato,
                    codigoPredio    = codigoPredio,
                    usuario         = usuario,
                    telefonoUsuario = telefonoUsuario.ifBlank { null },
                    direccion       = direccion.ifBlank { null },
                    observaciones   = observaciones.ifBlank { null },
                    estado          = estado
                )
            ).onSuccess {
                val ctx = getApplication<Application>()
                // Subir fotos nuevas reemplazando las anteriores
                listOf(
                    fotoPredioUriNueva    to "predio",
                    fotoAcometidaUriNueva to "acometida",
                    fotoMedidorUriNueva   to "medidor"
                ).forEach { (uri, tipo) ->
                    if (uri != null) {
                        repository.deleteFotosByPredioTipo(predioId, tipo)
                        repository.uploadFoto(ctx, uri, predioId, tipo)
                            .onSuccess { url ->
                                repository.createFoto(
                                    CreateFotoDto(predioId = predioId, tipo = tipo, url = url)
                                )
                            }
                    }
                }
                _saveState.value = EditarSaveState.Success
            }.onFailure { e ->
                _saveState.value = EditarSaveState.Error(e.message ?: "Error al guardar")
            }
        }
    }

    companion object {
        fun factory(predioId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                EditarViewModel(app, predioId)
            }
        }
    }
}

package com.jucha.acometidasapp.ui.nuevo

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jucha.acometidasapp.data.model.CreateFotoDto
import com.jucha.acometidasapp.data.model.CreatePredioDto
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.repository.PredioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class NuevoSaveState {
    object Idle : NuevoSaveState()
    object Saving : NuevoSaveState()
    object Success : NuevoSaveState()
    data class Error(val message: String) : NuevoSaveState()
}

class NuevoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PredioRepository(
        api = SupabaseClient.retrofit.create(PredioApiService::class.java)
    )

    // Campos del formulario
    var numeroContrato   by mutableStateOf("")
    var codigoPredio     by mutableStateOf("")
    var usuario          by mutableStateOf("")
    var direccion        by mutableStateOf("")

    // Fotos capturadas (URIs locales del dispositivo)
    var fotoPredioUri     by mutableStateOf<Uri?>(null)
    var fotoAcometidaUri  by mutableStateOf<Uri?>(null)
    var fotoMedidorUri    by mutableStateOf<Uri?>(null)

    // Proyecto activo
    var proyectoId: String = ""

    // Estado de guardado 
    private val _saveState = MutableStateFlow<NuevoSaveState>(NuevoSaveState.Idle)
    val saveState: StateFlow<NuevoSaveState> = _saveState

    fun setFoto(tipo: String, uri: Uri) {
        when (tipo) {
            "predio"    -> fotoPredioUri    = uri
            "acometida" -> fotoAcometidaUri = uri
            "medidor"   -> fotoMedidorUri   = uri
        }
    }

    fun resetSaveState() {
        _saveState.value = NuevoSaveState.Idle
    }

    fun guardar() {
        if (codigoPredio.isBlank() || usuario.isBlank() || direccion.isBlank()) {
            _saveState.value = NuevoSaveState.Error("Código Predio, Usuario y Dirección son obligatorios")
            return
        }

        _saveState.value = NuevoSaveState.Saving
        viewModelScope.launch {
            repository.createPredio(
                CreatePredioDto(
                    numeroContrato  = numeroContrato,
                    codigoPredio    = codigoPredio,
                    usuario         = usuario,
                    direccion       = direccion.ifBlank { null },
                    proyectoId      = proyectoId
                )
            ).onSuccess { predio ->
                // Subir cada foto al Storage y registrarla en la tabla fotos
                val ctx = getApplication<Application>()
                listOf(
                    fotoPredioUri    to "predio",
                    fotoAcometidaUri to "acometida",
                    fotoMedidorUri   to "medidor"
                ).forEach { (uri, tipo) ->
                    if (uri != null) {
                        Log.d("NuevoVM", "Subiendo foto tipo=$tipo uri=$uri")
                        repository.uploadFoto(ctx, uri, predio.id, tipo)
                            .onSuccess { url ->
                                Log.d("NuevoVM", "Upload OK tipo=$tipo url=$url")
                                repository.createFoto(
                                    CreateFotoDto(predioId = predio.id, tipo = tipo, url = url)
                                ).onSuccess {
                                    Log.d("NuevoVM", "createFoto OK tipo=$tipo")
                                }.onFailure { e ->
                                    Log.e("NuevoVM", "createFoto FALLO tipo=$tipo: ${e.message}", e)
                                }
                            }
                            .onFailure { e ->
                                Log.e("NuevoVM", "Upload FALLO tipo=$tipo: ${e.message}", e)
                            }
                    }
                }
                resetFormulario()
                _saveState.value = NuevoSaveState.Success
            }.onFailure { error ->
                _saveState.value = NuevoSaveState.Error(
                    error.message ?: "Error al guardar el predio"
                )
            }
        }
    }

    fun resetFormulario() {
        numeroContrato  = ""
        codigoPredio    = ""
        usuario         = ""
        direccion       = ""
        fotoPredioUri    = null
        fotoAcometidaUri = null
        fotoMedidorUri   = null
    }

    val hasUnsavedChanges: Boolean
        get() = numeroContrato.isNotBlank() || codigoPredio.isNotBlank() ||
                usuario.isNotBlank() || direccion.isNotBlank() ||
                fotoPredioUri != null || fotoAcometidaUri != null || fotoMedidorUri != null
}

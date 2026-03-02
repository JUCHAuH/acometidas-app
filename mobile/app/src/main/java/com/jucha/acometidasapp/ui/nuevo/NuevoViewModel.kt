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

    // Campos del formulario ────────────────────────────────────────────────
    var numeroParte      by mutableStateOf("")
    var numeroContrato   by mutableStateOf("")
    var codigoPredio     by mutableStateOf("")
    var usuario          by mutableStateOf("")
    var telefonoUsuario  by mutableStateOf("")
    var direccion        by mutableStateOf("")
    var observaciones    by mutableStateOf("")

    // Fotos capturadas (URIs locales del dispositivo)
    var fotoPredioUri     by mutableStateOf<Uri?>(null)
    var fotoAcometidaUri  by mutableStateOf<Uri?>(null)
    var fotoMedidorUri    by mutableStateOf<Uri?>(null)

    // Estado del parte
    var estado by mutableStateOf("pendiente")

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
        if (numeroContrato.isBlank() || codigoPredio.isBlank() || usuario.isBlank()) {
            _saveState.value = NuevoSaveState.Error("Nº Contrato, Código Predio y Usuario son obligatorios")
            return
        }

        _saveState.value = NuevoSaveState.Saving
        viewModelScope.launch {
            repository.createPredio(
                CreatePredioDto(
                    numeroParte     = numeroParte.ifBlank { null },
                    numeroContrato  = numeroContrato,
                    codigoPredio    = codigoPredio,
                    usuario         = usuario,
                    telefonoUsuario = telefonoUsuario.ifBlank { null },
                    direccion       = direccion.ifBlank { null },
                    observaciones   = observaciones.ifBlank { null },
                    estado          = estado
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
                limpiarFormulario()
                _saveState.value = NuevoSaveState.Success
            }.onFailure { error ->
                _saveState.value = NuevoSaveState.Error(
                    error.message ?: "Error al guardar el predio"
                )
            }
        }
    }

    private fun limpiarFormulario() {
        numeroParte     = ""
        numeroContrato  = ""
        codigoPredio    = ""
        usuario         = ""
        telefonoUsuario = ""
        direccion       = ""
        observaciones   = ""
        fotoPredioUri    = null
        fotoAcometidaUri = null
        fotoMedidorUri   = null
        estado           = "pendiente"
    }
}

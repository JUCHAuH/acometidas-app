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
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.jucha.acometidasapp.core.image.ImageProcessor
import com.jucha.acometidasapp.data.model.CreateFotoDto
import com.jucha.acometidasapp.data.model.UpdatePredioDto
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.repository.PredioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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

    var isLoading    by mutableStateOf(true)
    var loadError    by mutableStateOf<String?>(null)

    var numeroContrato  by mutableStateOf("")
    var codigoPredio    by mutableStateOf("")
    var usuario         by mutableStateOf("")
    var direccion       by mutableStateOf("")
    var estado          by mutableStateOf("pendiente")

    var fotoPredioUrl     by mutableStateOf<String?>(null)
    var fotoAcometidaUrl  by mutableStateOf<String?>(null)
    var fotoMedidorUrl    by mutableStateOf<String?>(null)

    var fotoPredioUriNueva    by mutableStateOf<Uri?>(null)
    var fotoAcometidaUriNueva by mutableStateOf<Uri?>(null)
    var fotoMedidorUriNueva   by mutableStateOf<Uri?>(null)

    private val _saveState = MutableStateFlow<EditarSaveState>(EditarSaveState.Idle)
    val saveState: StateFlow<EditarSaveState> = _saveState

    init { cargar() }

    private fun cargar() {
        viewModelScope.launch {
            isLoading  = true
            loadError  = null

            val predioLocal = repository.getPredioLocalById(getApplication(), predioId)
            if (predioLocal != null) {
                numeroContrato  = predioLocal.numeroContrato
                codigoPredio    = predioLocal.codigoPredio
                usuario         = predioLocal.usuario
                direccion       = predioLocal.direccion ?: ""
                estado          = predioLocal.estado
                isLoading = false
                return@launch
            }

            repository.getPredioById(predioId)
                .onSuccess { predio ->
                    numeroContrato  = predio.numeroContrato
                    codigoPredio    = predio.codigoPredio
                    usuario         = predio.usuario
                    direccion       = predio.direccion ?: ""
                    estado          = predio.estado
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
        if (codigoPredio.isBlank() || usuario.isBlank() || direccion.isBlank()) {
            _saveState.value = EditarSaveState.Error("Código Predio, Usuario y Dirección son obligatorios")
            return
        }
        _saveState.value = EditarSaveState.Saving
        viewModelScope.launch {
            repository.updatePredio(
                id = predioId,
                update = UpdatePredioDto(
                    numeroContrato  = numeroContrato,
                    codigoPredio    = codigoPredio,
                    usuario         = usuario,
                    direccion       = direccion.ifBlank { null },
                    estado          = estado
                )
            ).onSuccess {
                val ctx = getApplication<Application>()
                listOf(
                    fotoPredioUriNueva    to "predio",
                    fotoAcometidaUriNueva to "acometida",
                    fotoMedidorUriNueva   to "medidor"
                ).forEach { (uri, tipo) ->
                    if (uri != null) {
                        repository.deleteFotosByPredioTipo(predioId, tipo)

                        val uriParaSubir = if (tipo == "acometida") {
                            procesarFotoAcometida(ctx, uri) ?: uri
                        } else {
                            uri
                        }

                        repository.uploadFoto(ctx, uriParaSubir, predioId, tipo)
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

    private suspend fun procesarFotoAcometida(ctx: Application, uri: Uri): Uri? {
        val bitmap = ImageProcessor.applyScanFilter(ctx, uri) ?: return null
        val tempFile = File(ctx.cacheDir, "acometida_filtered_${System.currentTimeMillis()}.jpg")
        return try {
            FileOutputStream(tempFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
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

package com.jucha.acometidasapp.ui.exportar

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jucha.acometidasapp.core.utils.PdfGeneratorService
import com.jucha.acometidasapp.data.model.FotoDto
import com.jucha.acometidasapp.data.model.PredioDto
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.repository.PredioRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ExportarUiState {
    object LoadingPredios : ExportarUiState()
    data class Ready(
        val predios: List<PredioDto>,
        val seleccionados: Set<String> = emptySet()
    ) : ExportarUiState()
    object Generating : ExportarUiState()
    data class Done(val pdfUri: Uri) : ExportarUiState()
    data class Error(val message: String) : ExportarUiState()
}

class ExportarViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PredioRepository(
        api = SupabaseClient.retrofit.create(PredioApiService::class.java)
    )
    private val pdfService = PdfGeneratorService(app)

    private val _uiState = MutableStateFlow<ExportarUiState>(ExportarUiState.LoadingPredios)
    val uiState: StateFlow<ExportarUiState> = _uiState

    var empresaContratista = ""
    var supervisorObra = ""

    init { cargarPredios() }

    fun cargarPredios() {
        _uiState.value = ExportarUiState.LoadingPredios
        viewModelScope.launch {
            repository.getPredios()
                .onSuccess { predios ->
                    _uiState.value = ExportarUiState.Ready(predios)
                }
                .onFailure { e ->
                    _uiState.value = ExportarUiState.Error(e.message ?: "Error al cargar predios")
                }
        }
    }

    fun toggleSeleccion(predioId: String) {
        val state = _uiState.value as? ExportarUiState.Ready ?: return
        val nuevaSeleccion = if (predioId in state.seleccionados)
            state.seleccionados - predioId
        else
            state.seleccionados + predioId
        _uiState.value = state.copy(seleccionados = nuevaSeleccion)
    }

    fun toggleTodos() {
        val state = _uiState.value as? ExportarUiState.Ready ?: return
        val todos = state.predios.map { it.id }.toSet()
        _uiState.value = if (state.seleccionados.size == state.predios.size)
            state.copy(seleccionados = emptySet())
        else
            state.copy(seleccionados = todos)
    }

    fun exportar() {
        val state = _uiState.value as? ExportarUiState.Ready ?: return
        if (state.seleccionados.isEmpty()) return

        val prediosSeleccionados = state.predios.filter { it.id in state.seleccionados }
        _uiState.value = ExportarUiState.Generating

        viewModelScope.launch {
            try {
                // Cargar fotos de todos los predios seleccionados en paralelo
                val fotosPorPredio: Map<String, List<FotoDto>> = prediosSeleccionados
                    .map { predio ->
                        async {
                            val fotos = repository.getFotosByPredio(predio.id)
                                .getOrDefault(emptyList())
                            predio.id to fotos
                        }
                    }
                    .awaitAll()
                    .toMap()

                // Generar PDF en un hilo de background
                val archivo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (prediosSeleccionados.size == 1) {
                        pdfService.generarPdfIndividual(
                            predio = prediosSeleccionados.first(),
                            fotos = fotosPorPredio[prediosSeleccionados.first().id] ?: emptyList(),
                            empresaContratista = empresaContratista,
                            supervisorObra = supervisorObra
                        )
                    } else {
                        pdfService.generarPdfBatch(
                            predios = prediosSeleccionados,
                            fotosPorPredio = fotosPorPredio,
                            empresaContratista = empresaContratista,
                            supervisorObra = supervisorObra
                        )
                    }
                }

                val uri = FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.provider",
                    archivo
                )
                _uiState.value = ExportarUiState.Done(uri)

            } catch (e: Exception) {
                _uiState.value = ExportarUiState.Error(e.message ?: "Error al generar el PDF")
            }
        }
    }

    fun resetDone() {
        cargarPredios()
    }
}

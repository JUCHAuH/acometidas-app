package com.jucha.acometidasapp.ui.exportar

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jucha.acometidasapp.core.utils.PdfGeneratorService
import com.jucha.acometidasapp.core.navigation.ProyectoSesion
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

    private val _listaUri = MutableStateFlow<Uri?>(null)
    val listaUri: StateFlow<Uri?> = _listaUri

    private val _allPredios = MutableStateFlow<List<PredioDto>>(emptyList())
    val busqueda = MutableStateFlow("")

    private var proyectoId = ""

    fun setProyectoId(id: String) {
        if (proyectoId == id) return
        proyectoId = id
        cargarPredios()
    }

    init { /* no cargar hasta tener proyectoId */ }

    fun cargarPredios() {
        if (proyectoId.isEmpty()) return
        _uiState.value = ExportarUiState.LoadingPredios
        viewModelScope.launch {
            repository.getPrediosByProyecto(proyectoId)
                .onSuccess { predios ->
                    _allPredios.value = predios
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

    fun toggleTodos(filtrados: List<PredioDto>) {
        val state = _uiState.value as? ExportarUiState.Ready ?: return
        val idsVisibles = filtrados.map { it.id }.toSet()
        val todosSeleccionados = idsVisibles.isNotEmpty() && idsVisibles.all { it in state.seleccionados }
        _uiState.value = state.copy(
            seleccionados = if (todosSeleccionados)
                state.seleccionados - idsVisibles
            else
                state.seleccionados + idsVisibles
        )
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
                            empresaContratista = "",
                            supervisorObra = "",
                            tipoProyecto = ProyectoSesion.tipo
                        )
                    } else {
                        pdfService.generarPdfBatch(
                            predios = prediosSeleccionados,
                            fotosPorPredio = fotosPorPredio,
                            empresaContratista = "",
                            supervisorObra = "",
                            tipoProyecto = ProyectoSesion.tipo
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

    fun exportarLista(predios: List<PredioDto>, barrio: String?) {
        viewModelScope.launch {
            try {
                val archivo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    pdfService.generarListaPdf(predios, barrio)
                }
                val uri = FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.provider",
                    archivo
                )
                _listaUri.value = uri
            } catch (e: Exception) {
                _uiState.value = ExportarUiState.Error(e.message ?: "Error al generar lista")
            }
        }
    }

    fun resetLista() { _listaUri.value = null }
}

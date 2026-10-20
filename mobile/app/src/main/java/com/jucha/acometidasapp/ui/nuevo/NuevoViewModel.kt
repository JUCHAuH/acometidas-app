package com.jucha.acometidasapp.ui.nuevo

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.jucha.acometidasapp.core.image.AcometidaPlaceholderProvider
import com.jucha.acometidasapp.core.image.ImageProcessor
import com.jucha.acometidasapp.core.sync.ConnectivityObserver
import com.jucha.acometidasapp.core.sync.SyncManager
import com.jucha.acometidasapp.core.sync.SyncState
import com.jucha.acometidasapp.core.navigation.ProyectoSesion
import com.jucha.acometidasapp.core.navigation.SesionUsuario
import com.jucha.acometidasapp.core.utils.FileUtil
import com.jucha.acometidasapp.data.local.entity.FotoLocalEntity
import com.jucha.acometidasapp.data.local.entity.PredioLocalEntity
import com.jucha.acometidasapp.data.model.CreateFotoDto
import com.jucha.acometidasapp.data.model.CreatePredioDto
import com.jucha.acometidasapp.data.remote.PredioApiService
import com.jucha.acometidasapp.data.remote.SupabaseClient
import com.jucha.acometidasapp.data.repository.PredioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class NuevoSaveState {
    object Idle : NuevoSaveState()
    object Saving : NuevoSaveState()
    object SavingLocally : NuevoSaveState()  // Guardando en BD local sin internet
    object Success : NuevoSaveState()
    data class Error(val message: String) : NuevoSaveState()
}

class NuevoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PredioRepository(
        api = SupabaseClient.retrofit.create(PredioApiService::class.java)
    )

    private val connectivityObserver = ConnectivityObserver(application)
    private val fileUtil = FileUtil()

    // Campos del formulario
    var numeroContrato   by mutableStateOf("")
    var codigoPredio     by mutableStateOf("")
    var usuario          by mutableStateOf("")
    var direccion        by mutableStateOf("")

    // Fotos capturadas 
    var fotoPredioUri     by mutableStateOf<Uri?>(null)
    var fotoPredio2Uri    by mutableStateOf<Uri?>(null)
    var fotoAcometidaUri  by mutableStateOf<Uri?>(null)
    var fotoMedidorUri    by mutableStateOf<Uri?>(null)

    private var fotoMedidorEsPlaceholder by mutableStateOf(false)

    // Proyecto activo
    var proyectoId: String = ""
        set(value) {
            field = value
            aplicarFotoAleatoriaSiCorresponde()
        }

    // Estado de guardado
    private val _saveState = MutableStateFlow<NuevoSaveState>(NuevoSaveState.Idle)
    val saveState: StateFlow<NuevoSaveState> = _saveState

    fun setFoto(tipo: String, uri: Uri) {
        when (tipo) {
            "predio"    -> fotoPredioUri    = uri
            "predio2"   -> fotoPredio2Uri   = uri
            "acometida" -> fotoAcometidaUri = uri
            "medidor"   -> {
                fotoMedidorUri = uri
                fotoMedidorEsPlaceholder = false
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = NuevoSaveState.Idle
    }

    fun guardar() {
        if (codigoPredio.isBlank() || usuario.isBlank()) {
            _saveState.value = NuevoSaveState.Error("Código Predio y Usuario son obligatorios")
            return
        }

        viewModelScope.launch {
            // Garantiza la foto de respaldo de alcantarillado ANTES de guardar, incluso si el
            // usuario tocó "Guardar" más rápido de lo que tardó en copiarse en segundo plano.
            asegurarFotoAlcantarilladoSiCorresponde()

            // Detectar si hay internet usando el Flow (más confiable que isConnected())
            connectivityObserver.isOnline.first().let { isOnline ->
                if (isOnline) {
                    // Path ONLINE: guardar directamente en Supabase (original)
                    guardarOnline()
                } else {
                    // Path OFFLINE: guardar localmente
                    guardarOffline()
                }
            }
        }
    }

    private suspend fun guardarOnline() {
        _saveState.value = NuevoSaveState.Saving
        repository.createPredio(
            CreatePredioDto(
                numeroContrato  = numeroContrato,
                codigoPredio    = codigoPredio,
                usuario         = usuario,
                direccion       = direccion.ifBlank { null },
                proyectoId      = proyectoId
            )
        ).onSuccess { predio ->
            val ctx = getApplication<Application>()
            val fotosASubir = listOf(
                fotoPredioUri    to "predio",
                fotoPredio2Uri   to "predio2",
                fotoAcometidaUri to "acometida",
                fotoMedidorUri   to "medidor"
            ).mapNotNull { (uri, tipo) -> if (uri != null) uri to tipo else null }

            try {
                coroutineScope {
                    val uploadTasks = fotosASubir.map { (uri, tipo) ->
                        async {
                            try {
                                Log.d("NuevoVM", "Subiendo foto tipo=$tipo uri=$uri")
                                val uriParaSubir = if (tipo == "acometida") {
                                    try { procesarFotoAcometida(ctx, uri) } catch (t: Throwable) { null } ?: uri
                                } else {
                                    uri
                                }
                                repository.uploadFoto(ctx, uriParaSubir, predio.id, tipo)
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
                                        Log.e("NuevoVM", "Upload FALLO tipo=$tipo: ${e.message}")
                                    }
                            } catch (t: Throwable) {
                                Log.e("NuevoVM", "Error fatal subiendo foto tipo=$tipo: ${t.message}", t)
                            }
                        }
                    }
                    uploadTasks.awaitAll()
                }
            } catch (t: Throwable) {
                Log.e("NuevoVM", "Error fatal en bloque de subida de fotos: ${t.message}", t)
            }
            resetFormulario()
            _saveState.value = NuevoSaveState.Success
        }.onFailure { error ->
            _saveState.value = NuevoSaveState.Error(
                error.message ?: "Error al guardar el predio"
            )
        }
    }

    private suspend fun guardarOffline() {
        _saveState.value = NuevoSaveState.SavingLocally
        try {
            val ctx = getApplication<Application>()

            // 1. VALIDAR que el proyecto existe y usuario tiene acceso
            val proyectoLocal = repository.getProyectoLocalById(ctx, ProyectoSesion.id)
            if (proyectoLocal == null) {
                _saveState.value = NuevoSaveState.Error(
                    "No tiene acceso a este proyecto. Conecte a internet para sincronizar permisos."
                )
                return
            }

            // 2. Si es encargado, validar asignación
            if (!SesionUsuario.isAdmin) {
                val tieneAcceso = repository.usuarioTieneProyectoLocal(
                    ctx,
                    SesionUsuario.id,
                    ProyectoSesion.id
                )

                if (!tieneAcceso) {
                    _saveState.value = NuevoSaveState.Error(
                        "No está asignado a este proyecto."
                    )
                    return
                }
            }

            Log.d("NuevoVM", "Validación offline exitosa para proyecto: ${ProyectoSesion.id}")

            // 3. Crear predio en BD local
            val predioLocal = PredioLocalEntity(
                numeroContrato = numeroContrato,
                codigoPredio = codigoPredio,
                usuario = usuario,
                direccion = direccion.ifBlank { null },
                proyectoId = ProyectoSesion.id.ifBlank { null },
                estado = "pendiente",
                syncState = SyncState.PENDING.name
            )

            val predioId = repository.createPredioOffline(ctx, predioLocal)
            Log.d("NuevoVM", "Predio creado localmente: $predioId")

            // 4. Guardar fotos locales
            listOf(
                fotoPredioUri    to "predio",
                fotoPredio2Uri   to "predio2",
                fotoAcometidaUri to "acometida",
                fotoMedidorUri   to "medidor"
            ).forEach { (uri, tipo) ->
                if (uri != null) {
                    try {
                        Log.d("NuevoVM", "Guardando foto local tipo=$tipo")

                        // Aplicar filtro a foto de acometida (igual que online)
                        val uriParaProcesar = if (tipo == "acometida") {
                            procesarFotoAcometida(ctx, uri) ?: uri
                        } else {
                            uri
                        }

                        // Guardar archivo en ExternalFilesDir
                        val fotoFile = fileUtil.saveFotoLocally(ctx, uriParaProcesar, predioId, tipo)
                        Log.d("NuevoVM", "Foto guardada en: ${fotoFile.absolutePath}")

                        // Crear registro en foto_local
                        val fotoLocal = FotoLocalEntity(
                            predioId = predioId,
                            tipo = tipo,
                            localPath = fotoFile.absolutePath,
                            syncState = SyncState.PENDING.name
                        )
                        repository.insertFotoLocal(ctx, fotoLocal)
                        Log.d("NuevoVM", "Foto registrada en BD local: $tipo")

                    } catch (e: Exception) {
                        Log.e("NuevoVM", "Error guardando foto local tipo=$tipo: ${e.message}", e)
                        // Continuar con otras fotos
                    }
                }
            }

            // 5. Enqueue sincronización cuando conecte
            SyncManager.enqueueSyncPredios(ctx)
            Log.d("NuevoVM", "Sync enqueued para $predioId")

            resetFormulario()
            _saveState.value = NuevoSaveState.Success

        } catch (e: Exception) {
            Log.e("NuevoVM", "Error guardando offline: ${e.message}", e)
            _saveState.value = NuevoSaveState.Error(
                "Error al guardar localmente: ${e.message ?: "Error desconocido"}"
            )
        }
    }

    private suspend fun procesarFotoAcometida(ctx: Application, uri: Uri): Uri? {
        val bitmap = ImageProcessor.applyScanFilter(ctx, uri) ?: return null
        val photosDir = File(ctx.cacheDir, "photos").also { it.mkdirs() }
        val tempFile = File(photosDir, "acometida_filtered_${System.currentTimeMillis()}.jpg")
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

    fun resetFormulario() {
        numeroContrato  = ""
        codigoPredio    = ""
        usuario         = ""
        direccion       = ""
        fotoPredioUri    = null
        fotoPredio2Uri   = null
        fotoAcometidaUri = null
        fotoMedidorUri   = null
        fotoMedidorEsPlaceholder = false
        aplicarFotoAleatoriaSiCorresponde()
    }

    /**
     * Para proyectos de alcantarillado (normal o autoayuda), precarga una foto
     * aleatoria de acometida como respaldo si el campo está vacío. El usuario
     * puede reemplazarla por la foto real tocando "Cambiar"; si ya hay una foto
     * cargada, no se toca.
     */
    private fun aplicarFotoAleatoriaSiCorresponde() {
        viewModelScope.launch { asegurarFotoAlcantarilladoSiCorresponde() }
    }

    private suspend fun asegurarFotoAlcantarilladoSiCorresponde() {
        if (fotoMedidorUri != null) return
        if (!ProyectoSesion.tipo.startsWith("alcantarillado")) return
        val ctx = getApplication<Application>()
        val uri = withContext(Dispatchers.IO) {
            AcometidaPlaceholderProvider.obtenerAleatoria(ctx)
        }
        if (fotoMedidorUri == null) {
            fotoMedidorUri = uri
            fotoMedidorEsPlaceholder = uri != null
        }
    }

    val hasUnsavedChanges: Boolean
        get() = numeroContrato.isNotBlank() || codigoPredio.isNotBlank() ||
                usuario.isNotBlank() || direccion.isNotBlank() ||
                fotoPredioUri != null || fotoPredio2Uri != null || fotoAcometidaUri != null ||
                (fotoMedidorUri != null && !fotoMedidorEsPlaceholder)
}

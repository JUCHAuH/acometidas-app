package com.jucha.acometidasapp.ui.editar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import com.yalantis.ucrop.UCrop
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import java.io.File

private fun crearUriTemporal(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").also { it.mkdirs() }
    val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditarScreen(
    predioId: String,
    navController: NavController
) {
    val vm: EditarViewModel = viewModel(factory = EditarViewModel.factory(predioId))
    val context   = LocalContext.current
    val saveState by vm.saveState.collectAsStateWithLifecycle()

    var pendingUri  by remember { mutableStateOf<Uri?>(null) }
    var pendingTipo by remember { mutableStateOf("") }

    val cropLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                UCrop.getOutput(data)?.let { croppedUri ->
                    vm.setFotoNueva(pendingTipo, croppedUri)
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            val input = pendingUri ?: return@rememberLauncherForActivityResult
            val output = crearUriTemporal(context)
            val (ratioW, ratioH) = when (pendingTipo) {
                "predio" -> 503f to 269f
                else     -> 255f to 184f  // acometida y medidor
            }
            cropLauncher.launch(
                UCrop.of(input, output)
                    .withAspectRatio(ratioW, ratioH)
                    .withMaxResultSize(1500, 1500)
                    .getIntent(context)
            )
        }
        pendingUri = null
    }
    val permLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) pendingUri?.let { cameraLauncher.launch(it) }
    }

    fun abrirCamara(tipo: String) {
        val uri = crearUriTemporal(context)
        pendingUri  = uri
        pendingTipo = tipo
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) cameraLauncher.launch(uri)
        else permLauncher.launch(Manifest.permission.CAMERA)
    }

    // ── Dialogo éxito ────────────────────────────────────────────────────────
    if (saveState is EditarSaveState.Success) {
        AlertDialog(
            onDismissRequest = { vm.resetSaveState(); navController.popBackStack() },
            icon  = { Icon(Icons.Outlined.Check, null, tint = Color(0xFF22C55E)) },
            title = { Text("Guardado") },
            text  = { Text("El predio fue actualizado exitosamente.") },
            confirmButton = {
                TextButton(onClick = { vm.resetSaveState(); navController.popBackStack() }) {
                    Text("Aceptar")
                }
            }
        )
    }

    // ── Dialogo error ────────────────────────────────────────────────────────
    if (saveState is EditarSaveState.Error) {
        AlertDialog(
            onDismissRequest = { vm.resetSaveState() },
            title = { Text("Error") },
            text  = { Text((saveState as EditarSaveState.Error).message) },
            confirmButton = {
                TextButton(onClick = { vm.resetSaveState() }) { Text("Cerrar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Predio") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!vm.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = { if (saveState !is EditarSaveState.Saving) vm.guardar() },
                    icon = {
                        if (saveState is EditarSaveState.Saving) {
                            CircularProgressIndicator(
                                modifier  = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(Icons.Outlined.Check, null)
                        }
                    },
                    text = { Text("Guardar") }
                )
            }
        }
    ) { padding ->

        // ── Estado de carga ──────────────────────────────────────────────────
        if (vm.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (vm.loadError != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(vm.loadError ?: "", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        // ── Formulario ───────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Identificación
            item {
                FormCard {
                    Text(
                        "PARTE DE ACOMETIDA A RED DE AGUA POTABLE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoForm("Nº PARTE",       vm.numeroParte,    { vm.numeroParte = it },    Modifier.weight(1f))
                        CampoForm("Nº CONTRATO *",  vm.numeroContrato, { vm.numeroContrato = it }, Modifier.weight(1.5f))
                        CampoForm("CÓDIGO PREDIO *",vm.codigoPredio,   { vm.codigoPredio = it },   Modifier.weight(1.5f))
                    }
                }
            }

            // Usuario
            item {
                FormCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoForm("USUARIO *", vm.usuario,         { vm.usuario = it },         Modifier.weight(2f))
                        CampoForm("Nº TELF",   vm.telefonoUsuario, { vm.telefonoUsuario = it }, Modifier.weight(1f), KeyboardType.Phone)
                    }
                }
            }

            // Dirección
            item {
                FormCard {
                    CampoForm("DIRECCIÓN", vm.direccion, { vm.direccion = it }, Modifier.fillMaxWidth())
                }
            }

            // Estado
            item {
                FormCard {
                    EstadoSelector(
                        estado    = vm.estado,
                        onCambiar = { vm.estado = it }
                    )
                }
            }

            // Foto predio (grande)
            item {
                FormCard {
                    Text(
                        "ANEXO FOTOGRÁFICO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FotoBoxEditar(
                        uriNueva  = vm.fotoPredioUriNueva,
                        urlActual = vm.fotoPredioUrl,
                        label     = "VISTA DEL PREDIO Y UBICACIÓN DE LA ACOMETIDA INSTALADA",
                        height    = 220.dp,
                        onClick   = { abrirCamara("predio") }
                    )
                }
            }

            // Fotos acometida + medidor
            item {
                FormCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FotoBoxEditar(
                            uriNueva  = vm.fotoAcometidaUriNueva,
                            urlActual = vm.fotoAcometidaUrl,
                            label     = "DATOS DE ACOMETIDA INSTALADA",
                            height    = 180.dp,
                            onClick   = { abrirCamara("acometida") },
                            modifier  = Modifier.weight(1f)
                        )
                        FotoBoxEditar(
                            uriNueva  = vm.fotoMedidorUriNueva,
                            urlActual = vm.fotoMedidorUrl,
                            label     = "MEDIDOR INSTALADO",
                            height    = 180.dp,
                            onClick   = { abrirCamara("medidor") },
                            modifier  = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Observaciones
            item {
                FormCard {
                    OutlinedTextField(
                        value = vm.observaciones,
                        onValueChange = { vm.observaciones = it },
                        label = { Text("OBSERVACIONES") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EstadoSelector(estado: String, onCambiar: (String) -> Unit) {
    val opciones = listOf("pendiente", "en_proceso", "completo")
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf("pendiente" to "Pendiente", "en_proceso" to "En proceso", "completo" to "Completo")

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = labels[estado] ?: estado,
            onValueChange = {},
            readOnly = true,
            label = { Text("Estado") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            opciones.forEach { op ->
                DropdownMenuItem(
                    text = { Text(labels[op] ?: op) },
                    onClick = { onCambiar(op); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun CampoForm(
    label: String, value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
    )
}

/** Muestra la foto nueva (URI local) si existe, sino la actual (URL remota), sino placeholder */
@Composable
private fun FotoBoxEditar(
    uriNueva:  Uri?,
    urlActual: String?,
    label: String,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tieneImagen = uriNueva != null || urlActual != null
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.5.dp,
                    color = if (tieneImagen) MaterialTheme.colorScheme.primary
                            else Color.Gray.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .background(
                    if (tieneImagen) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            when {
                uriNueva != null -> AsyncImage(
                    model = uriNueva, contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                urlActual != null -> AsyncImage(
                    model = urlActual, contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.AddAPhoto, null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Toca para tomar foto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            // Badge "Cambiar" si ya hay foto
            if (tieneImagen) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.AddAPhoto, null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "Cambiar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

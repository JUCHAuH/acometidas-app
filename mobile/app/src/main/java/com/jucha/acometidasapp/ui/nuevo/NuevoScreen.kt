package com.jucha.acometidasapp.ui.nuevo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import coil.compose.AsyncImage
import java.io.File

private fun crearUriTemporal(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").also { it.mkdirs() }
    val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevoScreen(vm: NuevoViewModel = viewModel()) {
    val context = LocalContext.current
    val saveState by vm.saveState.collectAsStateWithLifecycle()

    var pendingUri  by remember { mutableStateOf<Uri?>(null) }
    var pendingTipo by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) pendingUri?.let { vm.setFoto(pendingTipo, it) }
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
        ) {
            cameraLauncher.launch(uri)
        } else {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (saveState is NuevoSaveState.Success) {
        AlertDialog(
            onDismissRequest = { vm.resetSaveState() },
            icon  = { Icon(Icons.Outlined.Check, null, tint = Color(0xFF22C55E)) },
            title = { Text("Guardado") },
            text  = { Text("El predio fue registrado exitosamente.") },
            confirmButton = {
                TextButton(onClick = { vm.resetSaveState() }) { Text("Aceptar") }
            }
        )
    }

    if (saveState is NuevoSaveState.Error) {
        AlertDialog(
            onDismissRequest = { vm.resetSaveState() },
            title = { Text("Error") },
            text  = { Text((saveState as NuevoSaveState.Error).message) },
            confirmButton = {
                TextButton(onClick = { vm.resetSaveState() }) { Text("Cerrar") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nuevo Predio") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (saveState !is NuevoSaveState.Saving) vm.guardar() },
                icon = {
                    if (saveState is NuevoSaveState.Saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
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
    ) { padding ->
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
                        CampoForm("Nº PARTE", vm.numeroParte, { vm.numeroParte = it }, Modifier.weight(1f))
                        CampoForm("Nº CONTRATO *", vm.numeroContrato, { vm.numeroContrato = it }, Modifier.weight(1.5f))
                        CampoForm("CÓDIGO PREDIO *", vm.codigoPredio, { vm.codigoPredio = it }, Modifier.weight(1.5f))
                    }
                }
            }

            // Usuario 
            item {
                FormCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoForm("USUARIO *", vm.usuario, { vm.usuario = it }, Modifier.weight(2f))
                        CampoForm("Nº TELF", vm.telefonoUsuario, { vm.telefonoUsuario = it }, Modifier.weight(1f), KeyboardType.Phone)
                    }
                }
            }

            // Dirección 
                FormCard {
                    CampoForm("DIRECCIÓN", vm.direccion, { vm.direccion = it }, Modifier.fillMaxWidth())
                }
            }

            // Foto grande — Vista del predio
            item {
                FormCard {
                    Text(
                        "ANEXO FOTOGRÁFICO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FotoBox(
                        uri    = vm.fotoPredioUri,
                        label  = "VISTA DEL PREDIO Y UBICACIÓN DE LA ACOMETIDA INSTALADA",
                        height = 220.dp,
                        onClick = { abrirCamara("predio") }
                    )
                }
            }

            // Fotos pequeñas (acometida | medidor)
            item {
                FormCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FotoBox(
                            uri     = vm.fotoAcometidaUri,
                            label   = "DATOS DE ACOMETIDA INSTALADA",
                            height  = 180.dp,
                            onClick = { abrirCamara("acometida") },
                            modifier = Modifier.weight(1f)
                        )
                        FotoBox(
                            uri     = vm.fotoMedidorUri,
                            label   = "MEDIDOR INSTALADO",
                            height  = 180.dp,
                            onClick = { abrirCamara("medidor") },
                            modifier = Modifier.weight(1f)
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

// Composables auxiliares
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
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
    )
}

@Composable
private fun FotoBox(
    uri: Uri?,
    label: String,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.5.dp,
                    color = if (uri != null) MaterialTheme.colorScheme.primary
                            else Color.Gray.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .background(
                    if (uri != null) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.AddAPhoto,
                        contentDescription = null,
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


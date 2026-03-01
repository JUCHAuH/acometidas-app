package com.jucha.acometidasapp.ui.exportar

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jucha.acometidasapp.data.model.PredioDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportarScreen(vm: ExportarViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(uiState) {
        if (uiState is ExportarUiState.Done) {
            val uri = (uiState as ExportarUiState.Done).pdfUri
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            vm.resetDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exportar PDF") },
                actions = {
                    IconButton(onClick = { vm.cargarPredios() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Recargar")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {

            is ExportarUiState.LoadingPredios -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ExportarUiState.Generating -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Generando PDF…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            is ExportarUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.cargarPredios() }) { Text("Reintentar") }
                    }
                }
            }

            is ExportarUiState.Done -> { /* manejado por LaunchedEffect */ }

            is ExportarUiState.Ready -> {
                Column(Modifier.fillMaxSize().padding(padding)) {

                    FirmasCard(vm = vm)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${state.seleccionados.size} de ${state.predios.size} seleccionados",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        TextButton(onClick = { vm.toggleTodos() }) {
                            Text(
                                if (state.seleccionados.size == state.predios.size)
                                    "Deseleccionar todo"
                                else "Seleccionar todo"
                            )
                        }
                    }

                    HorizontalDivider()

                    if (state.predios.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            Text("No hay predios registrados",
                                color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.predios, key = { it.id }) { predio ->
                                PredioExportItem(
                                    predio = predio,
                                    seleccionado = predio.id in state.seleccionados,
                                    onToggle = { vm.toggleSeleccion(predio.id) }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { vm.exportar() },
                        enabled = state.seleccionados.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.seleccionados.size == 1) "Exportar 1 predio"
                            else "Exportar ${state.seleccionados.size} predios"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FirmasCard(vm: ExportarViewModel) {
    var empresa    by remember { mutableStateOf(vm.empresaContratista) }
    var supervisor by remember { mutableStateOf(vm.supervisorObra) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Datos de firma", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = empresa,
                    onValueChange = { empresa = it; vm.empresaContratista = it },
                    label = { Text("Empresa Contratista",
                        style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = supervisor,
                    onValueChange = { supervisor = it; vm.supervisorObra = it },
                    label = { Text("Supervisor de Obra",
                        style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PredioExportItem(
    predio: PredioDto,
    seleccionado: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        elevation = CardDefaults.cardElevation(if (seleccionado) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (seleccionado)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (seleccionado) Icons.Outlined.CheckBox
                              else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (seleccionado) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text     = predio.usuario,
                    style    = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text     = "Cód: ${predio.codigoPredio}  •  Contrato: ${predio.numeroContrato}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


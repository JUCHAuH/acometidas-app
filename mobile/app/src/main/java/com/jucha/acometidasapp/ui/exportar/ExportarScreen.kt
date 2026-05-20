package com.jucha.acometidasapp.ui.exportar

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Color
import com.jucha.acometidasapp.core.theme.AzulAgua
import com.jucha.acometidasapp.data.model.PredioDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportarScreen(
    proyectoId: String,
    vm: ExportarViewModel = viewModel()
) {
    val uiState  by vm.uiState.collectAsStateWithLifecycle()
    val busqueda by vm.busqueda.collectAsStateWithLifecycle()
    val pngResult by vm.pngResult.collectAsStateWithLifecycle()
    val context  = LocalContext.current

    LaunchedEffect(proyectoId) { vm.setProyectoId(proyectoId) }
    LaunchedEffect(uiState) {
        if (uiState is ExportarUiState.Done) {
            val uri = (uiState as ExportarUiState.Done).pdfUri
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val intent = Intent.createChooser(shareIntent, "Compartir PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            vm.resetDone()
        }
    }
    LaunchedEffect(pngResult) {
        pngResult?.let { result ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = result.mimeType
                putExtra(Intent.EXTRA_STREAM, result.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val label = if (result.mimeType == "image/png") "Compartir PNG" else "Compartir ZIP"
            val intent = Intent.createChooser(shareIntent, label).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            vm.resetPng()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exportar PDF", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.cargarPredios() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Recargar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor        = AzulAgua,
                    titleContentColor    = Color.White,
                    actionIconContentColor = Color.White
                )
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
                        Text("Generando…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            is ExportarUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { vm.cargarPredios() }) {
                            Text("Reintentar")
                        }
                    }
                }
            }

            is ExportarUiState.Done -> { /* manejado por LaunchedEffect */ }

            is ExportarUiState.Ready -> {
                val prediosFiltrados = remember(state.predios, busqueda) {
                    state.predios.filter { p ->
                        val q = busqueda.trim()
                        q.isEmpty() ||
                            p.usuario.contains(q, ignoreCase = true) ||
                            p.numeroContrato.contains(q, ignoreCase = true) ||
                            p.codigoPredio.contains(q, ignoreCase = true)
                    }
                }
                Column(Modifier.fillMaxSize().padding(padding)) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = busqueda,
                            onValueChange = { vm.busqueda.value = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Buscar…") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (busqueda.isNotEmpty()) {
                                    IconButton(onClick = { vm.busqueda.value = "" }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Outlined.Close, "Limpiar", Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )
                    }

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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { vm.toggleTodos(prediosFiltrados) }) {
                            val todosVisiblesSeleccionados = prediosFiltrados.isNotEmpty() &&
                                prediosFiltrados.all { it.id in state.seleccionados }
                            Text(
                                if (todosVisiblesSeleccionados) "Deseleccionar todo"
                                else "Seleccionar todo"
                            )
                        }
                    }

                    HorizontalDivider()

                    if (state.predios.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            Text("No hay predios registrados",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (prediosFiltrados.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            Text("Sin resultados para la búsqueda",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(prediosFiltrados, key = { it.id }) { predio ->
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
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.seleccionados.size == 1) "Exportar 1 predio"
                            else "Exportar ${state.seleccionados.size} predios"
                        )
                    }
                    OutlinedButton(
                        onClick = { vm.exportarPng() },
                        enabled = state.seleccionados.size == 1,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar PNG")
                    }
                }
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
        modifier = Modifier.fillMaxWidth().height(90.dp),
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
                       else MaterialTheme.colorScheme.onSurfaceVariant
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
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!predio.direccion.isNullOrBlank()) {
                    Text(
                        text     = predio.direccion,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


package com.jucha.acometidasapp.ui.exportar

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableRows
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
import androidx.compose.ui.graphics.Color
import com.jucha.acometidasapp.core.theme.AzulAgua
import com.jucha.acometidasapp.data.model.PredioDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportarScreen(vm: ExportarViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val busqueda by vm.busqueda.collectAsStateWithLifecycle()
    val filtroDireccion by vm.filtroDireccion.collectAsStateWithLifecycle()
    val direcciones by vm.direcciones.collectAsStateWithLifecycle()
    val listaUri by vm.listaUri.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
    LaunchedEffect(listaUri) {
        listaUri?.let { uri ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val intent = Intent.createChooser(shareIntent, "Compartir Lista").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            vm.resetLista()
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
                val prediosFiltrados = remember(state.predios, busqueda, filtroDireccion) {
                    state.predios.filter { p ->
                        val q = busqueda.trim()
                        val matchQ = q.isEmpty() ||
                            p.usuario.contains(q, ignoreCase = true) ||
                            p.numeroContrato.contains(q, ignoreCase = true) ||
                            p.codigoPredio.contains(q, ignoreCase = true)
                        val matchDir = filtroDireccion == null ||
                            p.direccion?.trim() == filtroDireccion
                        matchQ && matchDir
                    }
                }
                Column(Modifier.fillMaxSize().padding(padding)) {

                    FirmasCard(vm = vm)

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
                            modifier = Modifier.weight(1f),
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
                        if (direcciones.isNotEmpty()) {
                            var expandedBarrio by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expandedBarrio,
                                onExpandedChange = { expandedBarrio = it },
                                modifier = Modifier.width(148.dp)
                            ) {
                                OutlinedTextField(
                                    value = filtroDireccion?.let {
                                        if (it.length > 12) it.take(11) + "…" else it
                                    } ?: "Barrio",
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedBarrio) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    shape = MaterialTheme.shapes.large,
                                    singleLine = true
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedBarrio,
                                    onDismissRequest = { expandedBarrio = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Todos") },
                                        onClick = { vm.filtroDireccion.value = null; expandedBarrio = false },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                    direcciones.forEach { dir ->
                                        DropdownMenuItem(
                                            text = { Text(dir) },
                                            onClick = { vm.filtroDireccion.value = dir; expandedBarrio = false },
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                        )
                                    }
                                }
                            }
                        }
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
                        onClick = { vm.exportarLista(prediosFiltrados, filtroDireccion) },
                        enabled = prediosFiltrados.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    ) {
                        Icon(Icons.Outlined.TableRows, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Exportar lista (${prediosFiltrados.size})")
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
    var expandida  by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandida = !expandida }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Edit, null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Datos de firma",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)
                if (!expandida) {
                    val resumen = listOfNotNull(
                        empresa.ifBlank { null },
                        supervisor.ifBlank { null }
                    ).joinToString(" • ")
                    Text(
                        text = resumen.ifEmpty { "Opcional — toca para editar" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                if (expandida) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expandida) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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


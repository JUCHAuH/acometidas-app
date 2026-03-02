package com.jucha.acometidasapp.ui.predios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.jucha.acometidasapp.core.theme.AzulAgua
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jucha.acometidasapp.core.navigation.Routes
import com.jucha.acometidasapp.data.model.PredioDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrediosScreen(
    vm: PrediosViewModel = viewModel(),
    navController: NavController? = null
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val busqueda by vm.busqueda.collectAsStateWithLifecycle()
    val filtroDireccion by vm.filtroDireccion.collectAsStateWithLifecycle()
    val direcciones by vm.direcciones.collectAsStateWithLifecycle()

    // Recargar cada vez que la pantalla vuelve a primer plano (al regresar de Editar)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.cargarPredios()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Predios",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { vm.cargarPredios() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Recargar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor     = AzulAgua,
                    titleContentColor  = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        when (val state = uiState) {

            is PrediosUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is PrediosUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.cargarPredios() }) { Text("Reintentar") }
                    }
                }
            }

            is PrediosUiState.Success -> {
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
                    OutlinedTextField(
                        value = busqueda,
                        onValueChange = { vm.busqueda.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Nombre, contrato o código…") },
                        leadingIcon  = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (busqueda.isNotEmpty()) {
                                IconButton(onClick = { vm.busqueda.value = "" }) {
                                    Icon(Icons.Outlined.Close, "Limpiar")
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = filtroDireccion ?: "Todos los barrios",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                label = { Text("Barrio") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedBarrio) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = MaterialTheme.shapes.large
                            )
                            ExposedDropdownMenu(
                                expanded = expandedBarrio,
                                onDismissRequest = { expandedBarrio = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Todos los barrios") },
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
                    if (state.predios.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Assignment, null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(12.dp))
                                Text("No hay predios registrados",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else if (prediosFiltrados.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            Text("Sin resultados para la búsqueda",
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(prediosFiltrados, key = { it.id }) { predio ->
                                PredioItem(
                                    predio     = predio,
                                    onEliminar = { vm.eliminarPredio(predio.id) },
                                    onEditar   = { navController?.navigate(Routes.editarPredio(predio.id)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PredioItem(
    predio: PredioDto,
    onEliminar: () -> Unit,
    onEditar: () -> Unit
) {
    var mostrarDialogoEliminar by remember { mutableStateOf(false) }

    if (mostrarDialogoEliminar) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoEliminar = false },
            icon = {
                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Eliminar predio") },
            text  = {
                Text("¿Estás seguro de que deseas eliminar el predio de ${predio.usuario}? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = { mostrarDialogoEliminar = false; onEliminar() },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoEliminar = false }) { Text("Cancelar") }
            }
        )
    }

    Card(
        modifier  = Modifier.fillMaxWidth().height(100.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier  = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text       = predio.usuario,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = "Cód: ${predio.codigoPredio}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                predio.direccion?.let { dir ->
                    Text(
                        text     = dir,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {
                EstadoChip(estado = predio.estado)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick  = { onEditar() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, "Editar",
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick  = { mostrarDialogoEliminar = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, "Eliminar",
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EstadoChip(estado: String) {
    val (label, color) = when (estado) {
        "pendiente"  -> "Pendiente"  to Color(0xFFF59E0B)
        "en_proceso" -> "En proceso" to Color(0xFF3B82F6)
        "completo"   -> "Completo"   to Color(0xFF22C55E)
        else         -> estado       to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text     = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style    = MaterialTheme.typography.labelSmall,
            color    = color,
            fontWeight = FontWeight.Bold
        )
    }
}
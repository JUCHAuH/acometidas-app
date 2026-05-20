package com.jucha.acometidasapp.ui.predios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.jucha.acometidasapp.core.navigation.ProyectoSesion
import com.jucha.acometidasapp.core.sync.SyncManager
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
import androidx.compose.material.icons.outlined.ArrowBack
import com.jucha.acometidasapp.data.model.PredioDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrediosScreen(
    proyectoId:      String,
    vm:              PrediosViewModel = viewModel(),
    navController:   NavController? = null,
    outerNavController: NavController? = null
) {
    val uiState  by vm.uiState.collectAsStateWithLifecycle()
    val busqueda by vm.busqueda.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Cargar predios del proyecto seleccionado
    LaunchedEffect(proyectoId) { vm.setProyectoId(proyectoId) }

    // Recargar al volver de Editar
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.cargarPredios()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Recargar automáticamente cuando la sincronización termina
    val syncWorkInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(SyncManager.SYNC_PREDIOS_IMMEDIATE_WORK)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(syncWorkInfos) {
        if (syncWorkInfos.any { it.state == WorkInfo.State.SUCCEEDED }) {
            vm.cargarPredios()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Predios", fontWeight = FontWeight.Bold)
                        Text(
                            ProyectoSesion.nombre,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        outerNavController?.navigate(Routes.PROYECTOS) {
                            popUpTo(Routes.PROYECTOS) { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                            tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.cargarPredios() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Recargar")
                    }
                    IconButton(onClick = { SyncManager.executeSyncNow(context) }) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = "Sincronizar ahora")
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { vm.cargarPredios() }) { Text("Reintentar") }
                    }
                }
            }

            is PrediosUiState.Success -> {
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
                                val syncState = state.prediosLocales.find { it.id == predio.id || it.remoteId == predio.id }?.syncState
                                PredioItem(
                                    predio     = predio,
                                    syncState  = syncState,
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
    syncState: String? = null,
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
                // Fila con usuario + badge de sync
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text       = predio.usuario,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Badge de sincronización
                    syncState?.let { state ->
                        Spacer(Modifier.width(8.dp))
                        when (state) {
                            "PENDING" -> {
                                Badge(
                                    containerColor = Color(0xFFFFB74D),  // Amarillo
                                    modifier = Modifier.padding(4.dp)
                                ) { Text("⏳", fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                            }
                            "SYNCING" -> {
                                Badge(
                                    containerColor = Color(0xFF81C3D7),  // Azul
                                    modifier = Modifier.padding(4.dp)
                                ) { Text("🔄", fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                            }
                            "FAILED" -> {
                                Badge(
                                    containerColor = Color.Red,
                                    modifier = Modifier.padding(4.dp)
                                ) { Text("❌", fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                            }
                            else -> {}
                        }
                    }
                }

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
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxHeight()
            ) {
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


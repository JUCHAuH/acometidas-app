package com.jucha.acometidasapp.ui.proyectos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jucha.acometidasapp.core.navigation.ProyectoSesion
import com.jucha.acometidasapp.core.navigation.Routes
import com.jucha.acometidasapp.core.theme.AzulAgua
import com.jucha.acometidasapp.data.model.ProyectoDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProyectosScreen(
    navController: NavController,
    vm: ProyectosViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    var mostrarDialogoCrear by remember { mutableStateOf(false) }
    var nombreNuevo         by remember { mutableStateOf("") }
    var proyectoAEliminar   by remember { mutableStateOf<ProyectoDto?>(null) }

    // ─── Diálogo: crear proyecto ───────────────────────────────────────────
    if (mostrarDialogoCrear) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoCrear = false; nombreNuevo = "" },
            title = { Text("Nuevo proyecto") },
            text = {
                OutlinedTextField(
                    value         = nombreNuevo,
                    onValueChange = { nombreNuevo = it },
                    label         = { Text("Nombre del proyecto") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nombreNuevo.isNotBlank()) {
                            vm.crearProyecto(nombreNuevo)
                            nombreNuevo = ""
                            mostrarDialogoCrear = false
                        }
                    }
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoCrear = false; nombreNuevo = "" }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ─── Diálogo: confirmar eliminación ────────────────────────────────────
    proyectoAEliminar?.let { proy ->
        AlertDialog(
            onDismissRequest = { proyectoAEliminar = null },
            icon  = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Eliminar proyecto") },
            text  = {
                Text("¿Eliminar \"${proy.nombre}\"? Se eliminarán también todos sus predios.")
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.eliminarProyecto(proy.id); proyectoAEliminar = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { proyectoAEliminar = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AquaDocs", fontWeight = FontWeight.Bold)
                        Text(
                            "Seleccioná un proyecto",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.cargarProyectos() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Recargar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor         = AzulAgua,
                    titleContentColor      = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick           = { mostrarDialogoCrear = true },
                containerColor    = AzulAgua,
                contentColor      = Color.White
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Nuevo proyecto")
            }
        }
    ) { padding ->
        when (val state = uiState) {

            is ProyectosUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ProyectosUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.cargarProyectos() }) { Text("Reintentar") }
                    }
                }
            }

            is ProyectosUiState.Success -> {
                if (state.proyectos.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Assignment, null,
                                modifier = Modifier.size(64.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No hay proyectos todavía",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tocá + para crear uno",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier        = Modifier.fillMaxSize().padding(padding),
                        contentPadding  = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.proyectos, key = { it.id }) { proyecto ->
                            ProyectoItem(
                                proyecto      = proyecto,
                                onSeleccionar = {
                                    ProyectoSesion.id     = proyecto.id
                                    ProyectoSesion.nombre = proyecto.nombre
                                    navController.navigate(Routes.MAIN)
                                },
                                onEliminar = { proyectoAEliminar = proyecto }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProyectoItem(
    proyecto:      ProyectoDto,
    onSeleccionar: () -> Unit,
    onEliminar:    () -> Unit
) {
    Card(
        onClick   = onSeleccionar,
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier            = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Assignment, null,
                tint     = AzulAgua,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text       = proyecto.nombre,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f)
            )
            IconButton(
                onClick  = onEliminar,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.Delete, "Eliminar",
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

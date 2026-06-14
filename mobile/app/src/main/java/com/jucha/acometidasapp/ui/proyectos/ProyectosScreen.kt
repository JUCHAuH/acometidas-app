package com.jucha.acometidasapp.ui.proyectos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jucha.acometidasapp.core.navigation.ProyectoSesion
import com.jucha.acometidasapp.core.navigation.Routes
import com.jucha.acometidasapp.core.navigation.SesionUsuario
import com.jucha.acometidasapp.core.theme.AzulAgua
import com.jucha.acometidasapp.core.utils.SessionPreferences
import com.jucha.acometidasapp.data.model.ProyectoDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProyectosScreen(
    navController: NavController,
    vm: ProyectosViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val isAdmin = SesionUsuario.isAdmin
    val scope = rememberCoroutineScope()
    var mostrarDialogoCrear by remember { mutableStateOf(false) }
    var nombreNuevo         by remember { mutableStateOf("") }
    var tipoNuevo           by remember { mutableStateOf("agua_potable") }
    var mostrarDialogoSubtipo by remember { mutableStateOf(false) }
    var subtipoAlcantarillado by remember { mutableStateOf("normal") }
    var proyectoAEliminar   by remember { mutableStateOf<ProyectoDto?>(null) }
    var mostrarLogout       by remember { mutableStateOf(false) }

    // Logout confirmation
    if (mostrarLogout) {
        AlertDialog(
            onDismissRequest = { mostrarLogout = false },
            icon  = { Icon(Icons.Outlined.Logout, null) },
            title = { Text("Cerrar sesión") },
            text  = { Text("¿Querés cerrar sesión?") },
            confirmButton = {
                TextButton(onClick = {
                    // Limpiar sesión guardada y estado global
                    scope.launch {
                        SessionPreferences.clearSession(navController.context)
                    }
                    SesionUsuario.clear()
                    mostrarLogout = false
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarLogout = false }) { Text("Cancelar") }
            }
        )
    }

    // Crear proyecto (solo admin)
    if (mostrarDialogoCrear && isAdmin) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoCrear = false; nombreNuevo = ""; tipoNuevo = "agua_potable"; subtipoAlcantarillado = "normal" },
            title = { Text("Nuevo proyecto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value         = nombreNuevo,
                        onValueChange = { nombreNuevo = it },
                        label         = { Text("Nombre del proyecto") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Text("Tipo de conexión", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tipoNuevo == "agua_potable",
                            onClick  = { tipoNuevo = "agua_potable" },
                            label    = { Text("Agua Potable") },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AzulAgua,
                                selectedLabelColor     = Color.White
                            )
                        )
                        FilterChip(
                            selected = tipoNuevo == "alcantarillado",
                            onClick  = { tipoNuevo = "alcantarillado"; mostrarDialogoSubtipo = true },
                            label    = { Text("Alcantarillado") },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AzulAgua,
                                selectedLabelColor     = Color.White
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nombreNuevo.isNotBlank()) {
                            val tipoFinal = if (tipoNuevo == "alcantarillado")
                                "alcantarillado_$subtipoAlcantarillado" else tipoNuevo
                            vm.crearProyecto(nombreNuevo, tipoFinal)
                            nombreNuevo = ""
                            tipoNuevo = "agua_potable"
                            subtipoAlcantarillado = "normal"
                            mostrarDialogoCrear = false
                        }
                    }
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoCrear = false; nombreNuevo = ""; tipoNuevo = "agua_potable"; subtipoAlcantarillado = "normal" }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog para seleccionar subtipo de alcantarillado
    if (mostrarDialogoSubtipo) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoSubtipo = false },
            title = { Text("Tipo de alcantarillado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("¿Cuál es el tipo de alcantarillado?", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = subtipoAlcantarillado == "normal",
                            onClick  = { subtipoAlcantarillado = "normal" },
                            label    = { Text("Normal") },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AzulAgua,
                                selectedLabelColor     = Color.White
                            )
                        )
                        FilterChip(
                            selected = subtipoAlcantarillado == "autoayuda",
                            onClick  = { subtipoAlcantarillado = "autoayuda" },
                            label    = { Text("Autoayuda") },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AzulAgua,
                                selectedLabelColor     = Color.White
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { mostrarDialogoSubtipo = false }) { Text("Continuar") }
            }
        )
    }

    // Confirmar eliminación (solo admin)
    if (isAdmin) proyectoAEliminar?.let { proy ->
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
                        Text(
                            "Partes de Instalación · Camacho",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${SesionUsuario.nombre} · ${if (isAdmin) "Admin" else "Encargado"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { navController.navigate(Routes.USUARIOS) }) {
                            Icon(Icons.Outlined.People, contentDescription = "Usuarios")
                        }
                    }
                    IconButton(onClick = { vm.cargarProyectos() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Recargar")
                    }
                    IconButton(onClick = { mostrarLogout = true }) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Cerrar sesión")
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
            if (isAdmin) {
                FloatingActionButton(
                    onClick           = { mostrarDialogoCrear = true },
                    containerColor    = AzulAgua,
                    contentColor      = Color.White
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Nuevo proyecto")
                }
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
                                if (isAdmin) "Tocá + para crear uno"
                                else "No tenés proyectos asignados",
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
                                showDelete    = isAdmin,
                                onSeleccionar = {
                                    ProyectoSesion.id     = proyecto.id
                                    ProyectoSesion.nombre = proyecto.nombre
                                    ProyectoSesion.tipo   = proyecto.tipo
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


@Composable
private fun ProyectoItem(
    proyecto:      ProyectoDto,
    showDelete:    Boolean,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = proyecto.nombre,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text  = when {
                        proyecto.tipo == "agua_potable" -> "Agua Potable"
                        proyecto.tipo == "alcantarillado_normal" -> "Alcantarillado (Normal)"
                        proyecto.tipo == "alcantarillado_autoayuda" -> "Alcantarillado (Autoayuda)"
                        else -> proyecto.tipo
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showDelete) {
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
}

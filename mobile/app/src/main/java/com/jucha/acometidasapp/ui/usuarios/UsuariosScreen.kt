package com.jucha.acometidasapp.ui.usuarios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jucha.acometidasapp.core.theme.AzulAgua
import com.jucha.acometidasapp.data.model.ProyectoDto
import com.jucha.acometidasapp.data.model.UsuarioDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsuariosScreen(
    navController: NavController,
    vm: UsuariosViewModel = viewModel()
) {
    val uiState  by vm.uiState.collectAsStateWithLifecycle()
    val mensaje  by vm.mensaje.collectAsStateWithLifecycle()

    var mostrarCrear by remember { mutableStateOf(false) }
    var nombre       by remember { mutableStateOf("") }
    var usuario      by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var usuarioAEliminar by remember { mutableStateOf<UsuarioDto?>(null) }
    var usuarioAsignar   by remember { mutableStateOf<UsuarioDto?>(null) }

    // Snackbar for messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(mensaje) {
        mensaje?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMensaje()
        }
    }

    // Create user dialog
    if (mostrarCrear) {
        AlertDialog(
            onDismissRequest = { mostrarCrear = false; nombre = ""; usuario = ""; password = ""; passwordVisible = false },
            title = { Text("Nuevo Encargado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nombre, onValueChange = { nombre = it },
                        label = { Text("Nombre") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = usuario, onValueChange = { usuario = it },
                        label = { Text("Usuario") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("Contraseña") }, singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Outlined.VisibilityOff
                                    else Icons.Outlined.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nombre.isNotBlank() && usuario.isNotBlank() && password.isNotBlank()) {
                            vm.crearUsuario(nombre, usuario, password)
                            nombre = ""; usuario = ""; password = ""; passwordVisible = false
                            mostrarCrear = false
                        }
                    }
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarCrear = false; nombre = ""; usuario = ""; password = ""; passwordVisible = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Delete confirmation
    usuarioAEliminar?.let { user ->
        AlertDialog(
            onDismissRequest = { usuarioAEliminar = null },
            icon  = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Eliminar usuario") },
            text  = { Text("¿Eliminar a \"${user.nombre}\"? Se quitará de todos los proyectos.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.eliminarUsuario(user.id); usuarioAEliminar = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { usuarioAEliminar = null }) { Text("Cancelar") }
            }
        )
    }

    // Assign projects dialog
    usuarioAsignar?.let { user ->
        val state = uiState as? UsuariosUiState.Success
        val proyectos = state?.proyectos ?: emptyList()
        val asignados = state?.asignaciones?.get(user.id) ?: emptyList()

        AlertDialog(
            onDismissRequest = { usuarioAsignar = null },
            title = { Text("Proyectos de ${user.nombre}") },
            text = {
                if (proyectos.isEmpty()) {
                    Text("No hay proyectos creados")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.heightIn(max = 350.dp)
                    ) {
                        items(proyectos, key = { it.id }) { proyecto ->
                            val asignado = proyecto.id in asignados
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = asignado,
                                    onCheckedChange = {
                                        if (asignado) vm.desasignarProyecto(user.id, proyecto.id)
                                        else vm.asignarProyecto(user.id, proyecto.id)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(proyecto.nombre, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (proyecto.tipo == "alcantarillado") "Alcantarillado" else "Agua Potable",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { usuarioAsignar = null }) { Text("Cerrar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gestión de Usuarios", fontWeight = FontWeight.Bold)
                        Text(
                            "Crear y asignar encargados",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.cargar() }) {
                        Icon(Icons.Outlined.Refresh, "Recargar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AzulAgua,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mostrarCrear = true },
                containerColor = AzulAgua,
                contentColor = Color.White
            ) {
                Icon(Icons.Outlined.PersonAdd, "Nuevo usuario")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val state = uiState) {
            is UsuariosUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UsuariosUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.cargar() }) { Text("Reintentar") }
                    }
                }
            }
            is UsuariosUiState.Success -> {
                val encargados = state.usuarios.filter { it.rol == "encargado" }

                if (encargados.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.People, null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No hay encargados registrados",
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
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(encargados, key = { it.id }) { user ->
                            val proyectosAsignados = state.asignaciones[user.id]?.size ?: 0
                            UsuarioItem(
                                usuario = user,
                                proyectosAsignados = proyectosAsignados,
                                onAsignar  = { usuarioAsignar = user },
                                onEliminar = { usuarioAEliminar = user }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsuarioItem(
    usuario: UsuarioDto,
    proyectosAsignados: Int,
    onAsignar:  () -> Unit,
    onEliminar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Person, null,
                tint = AzulAgua,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = usuario.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "@${usuario.usuario}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$proyectosAsignados proyecto(s) asignado(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Assign projects button
            IconButton(onClick = onAsignar, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.FolderOpen, "Asignar proyectos",
                    modifier = Modifier.size(20.dp),
                    tint = AzulAgua
                )
            }
            // Delete button
            IconButton(onClick = onEliminar, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Delete, "Eliminar",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

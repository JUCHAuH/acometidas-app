package com.jucha.acometidasapp.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jucha.acometidasapp.core.navigation.ProyectoSesion
import com.jucha.acometidasapp.core.navigation.Routes
import com.jucha.acometidasapp.core.navigation.SesionUsuario
import com.jucha.acometidasapp.core.sync.ConnectivityObserver
import com.jucha.acometidasapp.core.ui.ConnectivityIndicator
import com.jucha.acometidasapp.ui.editar.EditarScreen
import com.jucha.acometidasapp.ui.exportar.ExportarScreen
import com.jucha.acometidasapp.ui.nuevo.NuevoScreen
import com.jucha.acometidasapp.ui.nuevo.NuevoViewModel
import com.jucha.acometidasapp.ui.predios.PrediosScreen

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val allBottomNavItems = listOf(
    BottomNavItem("Predios",  Icons.Outlined.Home,         Routes.Tab.PREDIOS),
    BottomNavItem("Nuevo",    Icons.Outlined.Add,          Routes.Tab.NUEVO),
    BottomNavItem("Exportar", Icons.Outlined.PictureAsPdf, Routes.Tab.EXPORTAR)
)

// Rutas donde NO debe aparecer el BottomNav
val rutasSinBottomNav = setOf("editar_predio/{predioId}")

@Composable
fun MainScreen(navController: NavController) {
    val proyectoId         = ProyectoSesion.id
    val innerNavController = rememberNavController()
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val mostrarBottomBar = currentRoute !in rutasSinBottomNav

    val bottomNavItems = if (SesionUsuario.isAdmin) allBottomNavItems
                         else allBottomNavItems.filter { it.route != Routes.Tab.EXPORTAR }

    val nuevoVm: NuevoViewModel = viewModel()
    var pendingRoute by remember { mutableStateOf<String?>(null) }

    // Conectividad
    val context = LocalContext.current
    val connectivityObserver = remember { ConnectivityObserver(context) }
    val isOnline by connectivityObserver.isOnline.collectAsStateWithLifecycle(initialValue = true)
    var mostrarDialogoSinInternet by remember { mutableStateOf(false) }
    var wasOnline by remember { mutableStateOf(true) }

    LaunchedEffect(isOnline) {
        if (!isOnline && wasOnline) {
            mostrarDialogoSinInternet = true
        }
        wasOnline = isOnline
    }

    if (mostrarDialogoSinInternet) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoSinInternet = false },
            title = { Text("Modo sin conexión") },
            text  = {
                Text(
                    "La app está funcionando correctamente sin internet. " +
                    "Puedes crear predios normalmente. Los cambios se sincronizarán " +
                    "cuando recuperes la conexión."
                )
            },
            confirmButton = {
                TextButton(onClick = { mostrarDialogoSinInternet = false }) {
                    Text("Aceptar")
                }
            }
        )
    }

    if (pendingRoute != null) {
        AlertDialog(
            onDismissRequest = { pendingRoute = null },
            title = { Text("Cambios sin guardar") },
            text  = { Text("Tensés cambios sin guardar en el formulario. ¿Descarás y seguís?") },
            confirmButton = {
                TextButton(onClick = {
                    nuevoVm.resetFormulario()
                    innerNavController.navigate(pendingRoute!!) {
                        popUpTo(innerNavController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                    pendingRoute = null
                }) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRoute = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        bottomBar = {
            if (mostrarBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon     = { Icon(item.icon, contentDescription = item.label) },
                            label    = { Text(item.label) },
                            selected = navBackStackEntry?.destination?.hierarchy
                                ?.any { it.route == item.route } == true,
                            onClick  = {
                                val destino = item.route
                                val estaEnNuevo = currentRoute == Routes.Tab.NUEVO
                                if (estaEnNuevo && destino != Routes.Tab.NUEVO && nuevoVm.hasUnsavedChanges) {
                                    pendingRoute = destino
                                } else {
                                    innerNavController.navigate(destino) {
                                        popUpTo(innerNavController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController    = innerNavController,
                    startDestination = Routes.Tab.PREDIOS,
                    modifier         = Modifier.weight(1f)
                ) {
                    composable(Routes.Tab.PREDIOS) {
                        PrediosScreen(
                            proyectoId = proyectoId,
                            navController = innerNavController,
                            outerNavController = navController
                        )
                    }
                    composable(Routes.Tab.NUEVO) {
                        NuevoScreen(proyectoId = proyectoId, vm = nuevoVm)
                    }
                    composable(Routes.Tab.EXPORTAR) {
                        ExportarScreen(proyectoId = proyectoId)
                    }
                    composable(Routes.EDITAR_PREDIO) { backStackEntry ->
                        val predioId = backStackEntry.arguments?.getString("predioId") ?: ""
                        EditarScreen(predioId = predioId, navController = innerNavController)
                    }
                }
            }
            // Indicador de conexión en esquina superior derecha
            ConnectivityIndicator()
        }
    }
}

package com.jucha.acometidasapp.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jucha.acometidasapp.core.navigation.ProyectoSesion
import com.jucha.acometidasapp.core.navigation.Routes
import com.jucha.acometidasapp.ui.editar.EditarScreen
import com.jucha.acometidasapp.ui.exportar.ExportarScreen
import com.jucha.acometidasapp.ui.nuevo.NuevoScreen
import com.jucha.acometidasapp.ui.predios.PrediosScreen

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
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
                                innerNavController.navigate(item.route) {
                                    popUpTo(innerNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = innerNavController,
            startDestination = Routes.Tab.PREDIOS,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Routes.Tab.PREDIOS) {
                PrediosScreen(
                    proyectoId = proyectoId,
                    navController = innerNavController,
                    outerNavController = navController
                )
            }
            composable(Routes.Tab.NUEVO) {
                NuevoScreen(proyectoId = proyectoId)
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
}

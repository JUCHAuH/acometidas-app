package com.jucha.acometidasapp.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jucha.acometidasapp.ui.login.LoginScreen
import com.jucha.acometidasapp.ui.main.MainScreen
import com.jucha.acometidasapp.ui.proyectos.ProyectosScreen
import com.jucha.acometidasapp.ui.splash.SplashScreen
import com.jucha.acometidasapp.ui.usuarios.UsuariosScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(navController)
        }
        composable(Routes.LOGIN) {
            LoginScreen(navController)
        }
        composable(Routes.PROYECTOS) {
            ProyectosScreen(navController)
        }
        composable(Routes.MAIN) {
            MainScreen(navController = navController)
        }
        composable(Routes.USUARIOS) {
            UsuariosScreen(navController)
        }
    }
}

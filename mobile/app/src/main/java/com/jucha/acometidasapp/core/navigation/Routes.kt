package com.jucha.acometidasapp.core.navigation

object Routes {
    const val SPLASH     = "splash"
    const val LOGIN      = "login"
    const val PROYECTOS  = "proyectos"
    const val MAIN       = "main"
    const val USUARIOS   = "usuarios"

    // Tabs del BottomNav
    object Tab {
        const val PREDIOS  = "tab_predios"
        const val NUEVO    = "tab_nuevo"
        const val EXPORTAR = "tab_exportar"
    }

    // Ruta de pantalla completa (fuera del BottomNav)
    const val EDITAR_PREDIO = "editar_predio/{predioId}"
    fun editarPredio(predioId: String) = "editar_predio/$predioId"
}

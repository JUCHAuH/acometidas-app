package com.jucha.acometidasapp.core.navigation

object SesionUsuario {
    var id:     String = ""
    var nombre: String = ""
    var usuario: String = ""
    var rol:    String = ""

    val isAdmin: Boolean get() = rol == "admin"

    fun clear() {
        id = ""; nombre = ""; usuario = ""; rol = ""
    }
}

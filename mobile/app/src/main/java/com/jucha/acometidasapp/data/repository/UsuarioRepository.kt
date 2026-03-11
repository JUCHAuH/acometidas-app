package com.jucha.acometidasapp.data.repository

import com.jucha.acometidasapp.data.model.*
import com.jucha.acometidasapp.data.remote.UsuarioApiService

class UsuarioRepository(private val api: UsuarioApiService) {

    suspend fun login(usuario: String, password: String): Result<UsuarioDto> = runCatching {
        val result = api.login(LoginRequest(usuario, password))
        if (result.isEmpty()) throw Exception("Usuario o contraseña incorrectos")
        result.first()
    }

    suspend fun crearUsuario(nombre: String, usuario: String, password: String): Result<UsuarioDto> = runCatching {
        val result = api.crearUsuario(CreateUsuarioDto(nombre, usuario, password))
        if (result.isEmpty()) throw Exception("Error al crear usuario")
        result.first()
    }

    suspend fun getUsuarios(): Result<List<UsuarioDto>> = runCatching {
        api.getUsuarios()
    }

    suspend fun deleteUsuario(id: String): Result<Unit> = runCatching {
        val response = api.deleteUsuario("eq.$id")
        if (!response.isSuccessful) throw Exception("Error ${response.code()}")
    }

    suspend fun getProyectosDeUsuario(usuarioId: String): Result<List<String>> = runCatching {
        api.getProyectosUsuario("eq.$usuarioId").map { it.proyectoId }
    }

    suspend fun getUsuariosDeProyecto(proyectoId: String): Result<List<String>> = runCatching {
        api.getUsuariosProyecto("eq.$proyectoId").map { it.usuarioId }
    }

    suspend fun asignarAProyecto(proyectoId: String, usuarioId: String): Result<Unit> = runCatching {
        api.asignarUsuarioProyecto(ProyectoUsuarioDto(proyectoId = proyectoId, usuarioId = usuarioId))
        Unit
    }

    suspend fun desasignarDeProyecto(proyectoId: String, usuarioId: String): Result<Unit> = runCatching {
        val response = api.desasignarUsuarioProyecto("eq.$proyectoId", "eq.$usuarioId")
        if (!response.isSuccessful) throw Exception("Error ${response.code()}")
    }
}

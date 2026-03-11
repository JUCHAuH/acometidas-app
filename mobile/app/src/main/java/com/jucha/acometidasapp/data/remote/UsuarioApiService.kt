package com.jucha.acometidasapp.data.remote

import com.jucha.acometidasapp.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface UsuarioApiService {

    // Login via RPC function (uses pgcrypto for password verification)
    @POST("rpc/login")
    suspend fun login(@Body request: LoginRequest): List<UsuarioDto>

    // Create user via RPC function (hashes password server-side)
    @POST("rpc/crear_usuario")
    suspend fun crearUsuario(@Body dto: CreateUsuarioDto): List<UsuarioDto>

    // Get all users (admin only)
    @GET("usuarios")
    suspend fun getUsuarios(
        @Query("select") select: String = "id,nombre,usuario,rol,created_at",
        @Query("order")  order: String = "created_at.desc"
    ): List<UsuarioDto>

    // Delete user
    @DELETE("usuarios")
    @Headers("Prefer: return=minimal")
    suspend fun deleteUsuario(@Query("id") idFilter: String): Response<Void>

    // Get project assignments for a user
    @GET("proyecto_usuario")
    suspend fun getProyectosUsuario(
        @Query("usuario_id") usuarioIdFilter: String,
        @Query("select")     select: String = "proyecto_id"
    ): List<ProyectoUsuarioDto>

    // Get users assigned to a project
    @GET("proyecto_usuario")
    suspend fun getUsuariosProyecto(
        @Query("proyecto_id") proyectoIdFilter: String,
        @Query("select")      select: String = "usuario_id"
    ): List<ProyectoUsuarioDto>

    // Assign user to project
    @POST("proyecto_usuario")
    @Headers("Prefer: return=representation")
    suspend fun asignarUsuarioProyecto(@Body dto: ProyectoUsuarioDto): List<ProyectoUsuarioDto>

    // Remove user from project
    @DELETE("proyecto_usuario")
    @Headers("Prefer: return=minimal")
    suspend fun desasignarUsuarioProyecto(
        @Query("proyecto_id") proyectoIdFilter: String,
        @Query("usuario_id")  usuarioIdFilter: String
    ): Response<Void>
}

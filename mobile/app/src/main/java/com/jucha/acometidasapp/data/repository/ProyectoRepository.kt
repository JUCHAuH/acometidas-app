package com.jucha.acometidasapp.data.repository

import com.jucha.acometidasapp.data.model.CreateProyectoDto
import com.jucha.acometidasapp.data.model.ProyectoDto
import com.jucha.acometidasapp.data.remote.ProyectoApiService

class ProyectoRepository(private val api: ProyectoApiService) {

    suspend fun getProyectos(): Result<List<ProyectoDto>> = runCatching {
        api.getProyectos()
    }

    suspend fun createProyecto(nombre: String, tipo: String): Result<ProyectoDto> = runCatching {
        api.createProyecto(CreateProyectoDto(nombre, tipo)).first()
    }

    suspend fun deleteProyecto(id: String): Result<Unit> = runCatching {
        val response = api.deleteProyecto("eq.$id")
        if (!response.isSuccessful) throw Exception("Error ${response.code()}")
    }

    suspend fun renameProyecto(id: String, nuevoNombre: String): Result<Unit> = runCatching {
        val response = api.renameProyecto("eq.$id", mapOf("nombre" to nuevoNombre))
        if (!response.isSuccessful) throw Exception("Error ${response.code()}")
    }
}

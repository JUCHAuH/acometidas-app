package com.jucha.acometidasapp.data.remote

import com.jucha.acometidasapp.data.model.CreateProyectoDto
import com.jucha.acometidasapp.data.model.ProyectoDto
import retrofit2.Response
import retrofit2.http.*

interface ProyectoApiService {

    @GET("proyectos")
    suspend fun getProyectos(
        @Query("order")  order:  String = "created_at.desc",
        @Query("select") select: String = "*"
    ): List<ProyectoDto>

    @POST("proyectos")
    @Headers("Prefer: return=representation")
    suspend fun createProyecto(@Body proyecto: CreateProyectoDto): List<ProyectoDto>

    @DELETE("proyectos")
    @Headers("Prefer: return=minimal")
    suspend fun deleteProyecto(@Query("id") idFilter: String): Response<Void>

    @PATCH("proyectos")
    @Headers("Prefer: return=minimal")
    suspend fun renameProyecto(
        @Query("id")  idFilter: String,
        @Body         body:     Map<String, String>
    ): Response<Void>
}

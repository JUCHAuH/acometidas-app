package com.jucha.acometidasapp.data.remote

import com.jucha.acometidasapp.data.model.CreatePredioDto
import com.jucha.acometidasapp.data.model.FotoDto
import com.jucha.acometidasapp.data.model.PredioDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface PredioApiService {

    @GET("predios")
    suspend fun getPredios(
        @Query("order") order: String = "created_at.desc",
        @Query("select") select: String = "*"
    ): List<PredioDto>

    @GET("fotos")
    suspend fun getFotosByPredio(
        @Query("predio_id") predioIdFilter: String,
        @Query("select") select: String = "*"
    ): List<FotoDto>

    @POST("predios")
    @Headers("Prefer: return=representation")
    suspend fun createPredio(@Body predio: CreatePredioDto): List<PredioDto>
}

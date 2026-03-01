package com.jucha.acometidasapp.data.remote

import com.jucha.acometidasapp.data.model.CreateFotoDto
import com.jucha.acometidasapp.data.model.CreatePredioDto
import com.jucha.acometidasapp.data.model.FotoDto
import com.jucha.acometidasapp.data.model.PredioDto
import com.jucha.acometidasapp.data.model.UpdatePredioDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.Response

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

    @POST("fotos")
    @Headers("Prefer: return=representation")
    suspend fun createFoto(@Body foto: CreateFotoDto): List<FotoDto>

    @DELETE("predios")
    @Headers("Prefer: return=minimal")
    suspend fun deletePredio(@Query("id") idFilter: String): Response<Void>

    @GET("predios")
    suspend fun getPredioById(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): List<PredioDto>

    @DELETE("fotos")
    @Headers("Prefer: return=minimal")
    suspend fun deleteFotosByPredioTipo(
        @Query("predio_id") predioIdFilter: String,
        @Query("tipo") tipoFilter: String
    ): Response<Void>

    @PATCH("predios")
    @Headers("Prefer: return=representation")
    suspend fun updatePredio(
        @Query("id") idFilter: String,
        @Body update: UpdatePredioDto
    ): List<PredioDto>
}

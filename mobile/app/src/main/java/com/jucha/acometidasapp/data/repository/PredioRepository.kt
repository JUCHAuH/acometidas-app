package com.jucha.acometidasapp.data.repository

import com.jucha.acometidasapp.data.model.CreatePredioDto
import com.jucha.acometidasapp.data.model.FotoDto
import com.jucha.acometidasapp.data.model.PredioDto
import com.jucha.acometidasapp.data.remote.PredioApiService

class PredioRepository(
    private val api: PredioApiService
) {

    suspend fun getPredios(): Result<List<PredioDto>> = runCatching {
        api.getPredios()
    }

    suspend fun getFotosByPredio(predioId: String): Result<List<FotoDto>> = runCatching {
        api.getFotosByPredio(predioIdFilter = "eq.$predioId")
    }

    suspend fun createPredio(predio: CreatePredioDto): Result<PredioDto> = runCatching {
        api.createPredio(predio).first()
    }
}

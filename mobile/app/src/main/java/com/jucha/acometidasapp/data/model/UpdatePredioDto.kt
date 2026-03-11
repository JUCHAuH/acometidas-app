package com.jucha.acometidasapp.data.model

import com.google.gson.annotations.SerializedName

data class UpdatePredioDto(
    @SerializedName("numero_contrato")  val numeroContrato: String,
    @SerializedName("codigo_predio")    val codigoPredio: String,
    @SerializedName("usuario")          val usuario: String,
    @SerializedName("direccion")        val direccion: String?,
    @SerializedName("estado")           val estado: String
)

package com.jucha.acometidasapp.data.model

import com.google.gson.annotations.SerializedName

data class CreatePredioDto(
    @SerializedName("numero_parte")     val numeroParte: String?,
    @SerializedName("numero_contrato")  val numeroContrato: String,
    @SerializedName("codigo_predio")    val codigoPredio: String,
    @SerializedName("usuario")          val usuario: String,
    @SerializedName("telefono_usuario") val telefonoUsuario: String?,
    @SerializedName("direccion")        val direccion: String?,
    @SerializedName("observaciones")    val observaciones: String?,
    @SerializedName("estado")           val estado: String = "pendiente"
)

package com.jucha.acometidasapp.data.model

import com.google.gson.annotations.SerializedName

data class FotoDto(
    @SerializedName("id")        val id: String,
    @SerializedName("predio_id") val predioId: String,
    @SerializedName("tipo")      val tipo: String,   // "predio" | "acometida" | "medidor"
    @SerializedName("url")       val url: String,
    @SerializedName("tomada_en") val tomadaEn: String?
)

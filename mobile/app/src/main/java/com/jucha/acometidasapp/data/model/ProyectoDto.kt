package com.jucha.acometidasapp.data.model

import com.google.gson.annotations.SerializedName

data class ProyectoDto(
    @SerializedName("id")         val id: String,
    @SerializedName("nombre")     val nombre: String,
    @SerializedName("tipo")       val tipo: String = "agua_potable",
    @SerializedName("created_at") val createdAt: String?
)

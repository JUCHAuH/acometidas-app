package com.jucha.acometidasapp.data.model

import com.google.gson.annotations.SerializedName

data class CreateProyectoDto(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("tipo")   val tipo: String
)

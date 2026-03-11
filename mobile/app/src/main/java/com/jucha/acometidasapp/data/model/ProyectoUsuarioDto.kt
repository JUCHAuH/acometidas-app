package com.jucha.acometidasapp.data.model

import com.google.gson.annotations.SerializedName

data class ProyectoUsuarioDto(
    @SerializedName("id")          val id: String? = null,
    @SerializedName("proyecto_id") val proyectoId: String,
    @SerializedName("usuario_id")  val usuarioId: String
)

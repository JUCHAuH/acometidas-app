package com.jucha.acometidasapp.data.model

import com.google.gson.annotations.SerializedName

data class UsuarioDto(
    @SerializedName("id")         val id: String,
    @SerializedName("nombre")     val nombre: String,
    @SerializedName("usuario")    val usuario: String,
    @SerializedName("rol")        val rol: String,
    @SerializedName("created_at") val createdAt: String? = null
)

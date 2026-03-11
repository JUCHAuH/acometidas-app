package com.jucha.acometidasapp.data.model

import com.google.gson.annotations.SerializedName

data class CreateUsuarioDto(
    @SerializedName("p_nombre")   val nombre: String,
    @SerializedName("p_usuario")  val usuario: String,
    @SerializedName("p_password") val password: String,
    @SerializedName("p_rol")      val rol: String = "encargado"
)

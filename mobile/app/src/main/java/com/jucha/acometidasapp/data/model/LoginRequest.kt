package com.jucha.acometidasapp.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("p_usuario")  val usuario: String,
    @SerializedName("p_password") val password: String
)

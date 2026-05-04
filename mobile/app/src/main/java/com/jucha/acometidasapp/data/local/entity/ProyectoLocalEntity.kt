package com.jucha.acometidasapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proyecto_local")
data class ProyectoLocalEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val tipo: String,  // "agua_potable" o "alcantarillado"
    val createdAt: Long = System.currentTimeMillis()
)

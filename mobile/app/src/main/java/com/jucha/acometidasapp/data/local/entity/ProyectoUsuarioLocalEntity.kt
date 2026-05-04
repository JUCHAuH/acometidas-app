package com.jucha.acometidasapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "proyecto_usuario_local",
    primaryKeys = ["proyecto_id", "usuario_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProyectoLocalEntity::class,
            parentColumns = ["id"],
            childColumns = ["proyecto_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProyectoUsuarioLocalEntity(
    val proyecto_id: String,
    val usuario_id: String
)

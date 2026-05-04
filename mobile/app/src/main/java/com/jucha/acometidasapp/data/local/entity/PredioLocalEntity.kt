package com.jucha.acometidasapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jucha.acometidasapp.core.sync.SyncState
import java.util.UUID

@Entity(tableName = "predio_local")
data class PredioLocalEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val numeroContrato: String,
    val codigoPredio: String,
    val usuario: String,
    val telefonoUsuario: String? = null,
    val direccion: String? = null,
    val observaciones: String? = null,
    val estado: String = "pendiente",
    val proyectoId: String? = null,

    // Sincronización
    @ColumnInfo(name = "sync_state")
    val syncState: String = SyncState.PENDING.name,

    @ColumnInfo(name = "sync_error")
    val syncError: String? = null,

    @ColumnInfo(name = "remote_id")
    val remoteId: String? = null,  // ID asignado por Supabase una vez sincronizado

    @ColumnInfo(name = "created_at_local")
    val createdAtLocal: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_retries")
    val syncRetries: Int = 0
)

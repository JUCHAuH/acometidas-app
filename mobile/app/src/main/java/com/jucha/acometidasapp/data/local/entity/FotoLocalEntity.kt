package com.jucha.acometidasapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jucha.acometidasapp.core.sync.SyncState
import java.util.UUID

@Entity(
    tableName = "foto_local",
    indices = [Index("predio_id")],  // Agregar índice para foreign key
    foreignKeys = [
        ForeignKey(
            entity = PredioLocalEntity::class,
            parentColumns = ["id"],
            childColumns = ["predio_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FotoLocalEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "predio_id")
    val predioId: String,

    val tipo: String,  // "predio", "acometida", "medidor"

    @ColumnInfo(name = "local_path")
    val localPath: String,  // Ruta del archivo local (context.getExternalFilesDir())

    @ColumnInfo(name = "remote_url")
    val remoteUrl: String? = null,  // URL en Supabase Storage (después de sincronizar)

    @ColumnInfo(name = "sync_state")
    val syncState: String = SyncState.PENDING.name,

    @ColumnInfo(name = "sync_error")
    val syncError: String? = null,

    @ColumnInfo(name = "remote_id")
    val remoteId: String? = null,  // ID de foto en tabla fotos de Supabase

    @ColumnInfo(name = "created_at_local")
    val createdAtLocal: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_retries")
    val syncRetries: Int = 0
)

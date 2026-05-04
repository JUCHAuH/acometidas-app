package com.jucha.acometidasapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jucha.acometidasapp.data.local.entity.ProyectoUsuarioLocalEntity

@Dao
interface ProyectoUsuarioLocalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsignacion(asignacion: ProyectoUsuarioLocalEntity): Long

    @Query("SELECT proyecto_id FROM proyecto_usuario_local WHERE usuario_id = :usuarioId")
    suspend fun getProyectosDeUsuario(usuarioId: String): List<String>

    @Query("SELECT COUNT(*) > 0 FROM proyecto_usuario_local WHERE usuario_id = :usuarioId AND proyecto_id = :proyectoId")
    suspend fun usuarioTieneProyecto(usuarioId: String, proyectoId: String): Boolean

    @Delete
    suspend fun deleteAsignacion(asignacion: ProyectoUsuarioLocalEntity): Int

    @Query("DELETE FROM proyecto_usuario_local WHERE usuario_id = :usuarioId")
    suspend fun deleteAsignacionesPorUsuario(usuarioId: String)

    @Query("DELETE FROM proyecto_usuario_local WHERE proyecto_id = :proyectoId")
    suspend fun deleteAsignacionesPorProyecto(proyectoId: String)

    @Query("DELETE FROM proyecto_usuario_local")
    suspend fun deleteAllAsignaciones()
}

package com.jucha.acometidasapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jucha.acometidasapp.data.local.entity.ProyectoLocalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProyectoLocalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProyecto(proyecto: ProyectoLocalEntity): Long

    @Query("SELECT * FROM proyecto_local WHERE id = :id")
    suspend fun getProyectoById(id: String): ProyectoLocalEntity?

    @Query("SELECT * FROM proyecto_local")
    suspend fun getAllProyectos(): List<ProyectoLocalEntity>

    @Query("SELECT * FROM proyecto_local")
    fun getAllProyectosFlow(): Flow<List<ProyectoLocalEntity>>

    @Query("DELETE FROM proyecto_local WHERE id = :id")
    suspend fun deleteProyectoById(id: String)

    @Query("DELETE FROM proyecto_local")
    suspend fun deleteAllProyectos()
}

package com.jucha.acometidasapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jucha.acometidasapp.data.local.entity.PredioLocalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PredioLocalDao {

    @Insert
    suspend fun insert(predio: PredioLocalEntity): Long

    @Update
    suspend fun update(predio: PredioLocalEntity): Int

    @Delete
    suspend fun delete(predio: PredioLocalEntity): Int

    @Query("SELECT * FROM predio_local WHERE id = :id")
    suspend fun getById(id: String): PredioLocalEntity?

    @Query("SELECT * FROM predio_local")
    suspend fun getAll(): List<PredioLocalEntity>

    @Query("SELECT * FROM predio_local")
    fun getAllFlow(): Flow<List<PredioLocalEntity>>

    @Query("SELECT * FROM predio_local WHERE proyectoId = :proyectoId")
    suspend fun getByProyectoId(proyectoId: String): List<PredioLocalEntity>

    @Query("SELECT * FROM predio_local WHERE proyectoId = :proyectoId")
    fun getByProyectoIdFlow(proyectoId: String): Flow<List<PredioLocalEntity>>

    @Query("SELECT * FROM predio_local WHERE sync_state = :syncState")
    suspend fun getBySyncState(syncState: String): List<PredioLocalEntity>

    @Query("UPDATE predio_local SET sync_state = :syncState WHERE id = :id")
    suspend fun updateSyncState(id: String, syncState: String)

    @Query("UPDATE predio_local SET sync_state = :syncState, remote_id = :remoteId WHERE id = :id")
    suspend fun updateSyncStateWithRemoteId(id: String, syncState: String, remoteId: String)

    @Query("UPDATE predio_local SET sync_state = :syncState, sync_error = :error, sync_retries = sync_retries + 1 WHERE id = :id")
    suspend fun recordSyncError(id: String, syncState: String, error: String)

    @Query("DELETE FROM predio_local WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM predio_local WHERE proyectoId = :proyectoId")
    suspend fun deleteByProyectoId(proyectoId: String)
}

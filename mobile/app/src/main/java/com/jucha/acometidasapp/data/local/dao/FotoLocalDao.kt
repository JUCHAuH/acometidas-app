package com.jucha.acometidasapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jucha.acometidasapp.data.local.entity.FotoLocalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FotoLocalDao {

    @Insert
    suspend fun insert(foto: FotoLocalEntity): Long

    @Update
    suspend fun update(foto: FotoLocalEntity): Int

    @Delete
    suspend fun delete(foto: FotoLocalEntity): Int

    @Query("SELECT * FROM foto_local WHERE id = :id")
    suspend fun getById(id: String): FotoLocalEntity?

    @Query("SELECT * FROM foto_local WHERE predio_id = :predioId")
    suspend fun getByPredioId(predioId: String): List<FotoLocalEntity>

    @Query("SELECT * FROM foto_local WHERE predio_id = :predioId")
    fun getByPredioIdFlow(predioId: String): Flow<List<FotoLocalEntity>>

    @Query("SELECT * FROM foto_local WHERE predio_id = :predioId AND tipo = :tipo")
    suspend fun getByPredioIdAndTipo(predioId: String, tipo: String): FotoLocalEntity?

    @Query("SELECT * FROM foto_local WHERE sync_state = :syncState")
    suspend fun getBySyncState(syncState: String): List<FotoLocalEntity>

    @Query("SELECT * FROM foto_local WHERE predio_id = :predioId AND sync_state = :syncState")
    suspend fun getByPredioIdAndSyncState(predioId: String, syncState: String): List<FotoLocalEntity>

    @Query("UPDATE foto_local SET sync_state = :syncState WHERE id = :id")
    suspend fun updateSyncState(id: String, syncState: String)

    @Query("UPDATE foto_local SET sync_state = :syncState, remote_url = :remoteUrl, remote_id = :remoteId WHERE id = :id")
    suspend fun updateSyncStateWithRemoteData(id: String, syncState: String, remoteUrl: String, remoteId: String)

    @Query("UPDATE foto_local SET sync_state = :syncState, sync_error = :error, sync_retries = sync_retries + 1 WHERE id = :id")
    suspend fun recordSyncError(id: String, syncState: String, error: String)

    @Query("DELETE FROM foto_local WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM foto_local WHERE predio_id = :predioId")
    suspend fun deleteByPredioId(predioId: String)

    @Query("DELETE FROM foto_local WHERE predio_id = :predioId AND tipo = :tipo")
    suspend fun deleteByPredioIdAndTipo(predioId: String, tipo: String)
}

package com.jucha.acometidasapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jucha.acometidasapp.data.local.dao.FotoLocalDao
import com.jucha.acometidasapp.data.local.dao.PredioLocalDao
import com.jucha.acometidasapp.data.local.dao.ProyectoLocalDao
import com.jucha.acometidasapp.data.local.dao.ProyectoUsuarioLocalDao
import com.jucha.acometidasapp.data.local.entity.FotoLocalEntity
import com.jucha.acometidasapp.data.local.entity.PredioLocalEntity
import com.jucha.acometidasapp.data.local.entity.ProyectoLocalEntity
import com.jucha.acometidasapp.data.local.entity.ProyectoUsuarioLocalEntity

@Database(
    entities = [
        PredioLocalEntity::class,
        FotoLocalEntity::class,
        ProyectoLocalEntity::class,
        ProyectoUsuarioLocalEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AcometidasDatabase : RoomDatabase() {

    abstract fun predioLocalDao(): PredioLocalDao
    abstract fun fotoLocalDao(): FotoLocalDao
    abstract fun proyectoLocalDao(): ProyectoLocalDao
    abstract fun proyectoUsuarioLocalDao(): ProyectoUsuarioLocalDao

    companion object {
        @Volatile
        private var INSTANCE: AcometidasDatabase? = null

        fun getDatabase(context: Context): AcometidasDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AcometidasDatabase::class.java,
                    "acometidas_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}


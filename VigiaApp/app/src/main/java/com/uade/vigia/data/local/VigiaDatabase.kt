package com.uade.vigia.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MissionEntity::class, EventEntity::class], version = 2, exportSchema = false)
abstract class VigiaDatabase : RoomDatabase() {
    abstract fun missionDao(): MissionDao

    companion object {
        fun create(context: Context): VigiaDatabase =
            Room.databaseBuilder(context.applicationContext, VigiaDatabase::class.java, "vigia_database")
                // La versión 1 era un prototipo sin datos reales: se recrea la base al actualizar.
                .fallbackToDestructiveMigration()
                .build()
    }
}

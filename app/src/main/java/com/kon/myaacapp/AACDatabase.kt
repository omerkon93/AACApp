package com.kon.myaacapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AACTile::class], version = 2, exportSchema = false)
abstract class AACDatabase : RoomDatabase() {
    abstract fun aacTileDao(): AACTileDao

    companion object {
        @Volatile
        private var INSTANCE: AACDatabase? = null

        fun getDatabase(context: Context): AACDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AACDatabase::class.java,
                    "aac_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
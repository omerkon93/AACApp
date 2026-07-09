package com.kon.myaacapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AACTile::class, TileClickEvent::class, TilePlacement::class], version = 7, exportSchema = false)
abstract class AACDatabase : RoomDatabase() {
    abstract fun aacTileDao(): AACTileDao

    companion object {
        @Volatile
        private var INSTANCE: AACDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE aac_tiles ADD COLUMN languageCode TEXT NOT NULL DEFAULT 'he'")
            }
        }

        fun getDatabase(context: Context): AACDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AACDatabase::class.java,
                    "aac_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
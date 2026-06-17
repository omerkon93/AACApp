package com.kon.myaacapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [AACTile::class, TileClickEvent::class], version = 6, exportSchema = false)
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
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Prepopulate the database when it is first created
                        triggerInitialLoad(context)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Fallback: check if empty and load if needed
                        triggerInitialLoad(context)
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun triggerInitialLoad(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                val database = getDatabase(context)
                val repository = AACRepository(database.aacTileDao())
                if (repository.isEmpty()) {
                    val backupService = BackupService(context.applicationContext, repository)
                    backupService.importFromAssets("initial_data.zip")
                }
            }
        }
    }
}
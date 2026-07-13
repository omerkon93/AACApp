package com.kon.myaacapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kon.myaacapp.data.local.dao.AACTileDao
import com.kon.myaacapp.data.local.entity.AACTile
import com.kon.myaacapp.data.local.entity.TileClickEvent
import com.kon.myaacapp.data.local.entity.TilePlacement
import com.kon.myaacapp.data.local.migration.MIGRATION_3_4
import com.kon.myaacapp.data.local.migration.MIGRATION_7_8
import com.kon.myaacapp.data.local.migration.MIGRATION_8_9
import com.kon.myaacapp.data.local.migration.MIGRATION_9_10

@Database(
    entities = [
        AACTile::class,
        TileClickEvent::class,
        TilePlacement::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class AACDatabase : RoomDatabase() {

    abstract fun aacTileDao(): AACTileDao

    companion object {
        @Volatile
        private var INSTANCE: AACDatabase? = null

        fun getDatabase(context: Context): AACDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(
                    context.applicationContext
                ).also { database ->
                    INSTANCE = database
                }
            }
        }

        private fun buildDatabase(
            appContext: Context,
        ): AACDatabase {
            return Room.databaseBuilder(
                appContext,
                AACDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(
                    MIGRATION_3_4,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                )
                .fallbackToDestructiveMigration(
                    dropAllTables = false
                )
                .build()
        }

        private const val DATABASE_NAME = "aac_database"
    }
}
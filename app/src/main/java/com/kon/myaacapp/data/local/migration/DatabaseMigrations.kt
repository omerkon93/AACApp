package com.kon.myaacapp.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE aac_tiles
            ADD COLUMN languageCode TEXT NOT NULL DEFAULT 'he'
            """.trimIndent()
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
                    CREATE TABLE IF NOT EXISTS `tile_click_events_new` (
                        `id` INTEGER NOT NULL,
                        `tileId` TEXT NOT NULL,
                        `profileId` TEXT NOT NULL DEFAULT 'default',
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
        )

        db.execSQL(
            """
                    INSERT INTO `tile_click_events_new`
                        (`id`, `tileId`, `timestamp`)
                    SELECT
                        `id`, `tileId`, `timestamp`
                    FROM `tile_click_events`
                    """.trimIndent()
        )

        db.execSQL(
            "DROP TABLE `tile_click_events`"
        )

        db.execSQL(
            """
                    ALTER TABLE `tile_click_events_new`
                    RENAME TO `tile_click_events`
                    """.trimIndent()
        )

        db.execSQL(
            """
                    CREATE INDEX IF NOT EXISTS
                    `index_tile_click_events_tileId`
                    ON `tile_click_events` (`tileId`)
                    """.trimIndent()
        )

        db.execSQL(
            """
                    CREATE INDEX IF NOT EXISTS
                    `index_tile_click_events_profileId`
                    ON `tile_click_events` (`profileId`)
                    """.trimIndent()
        )

        db.execSQL(
            """
                    CREATE INDEX IF NOT EXISTS
                    `index_tile_click_events_timestamp`
                    ON `tile_click_events` (`timestamp`)
                    """.trimIndent()
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
                    CREATE TABLE IF NOT EXISTS `tile_click_events_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tileId` TEXT NOT NULL,
                        `profileId` TEXT NOT NULL DEFAULT 'default',
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
        )

        db.execSQL(
            """
                    INSERT INTO `tile_click_events_new`
                        (`id`, `tileId`, `profileId`, `timestamp`)
                    SELECT
                        `id`, `tileId`, `profileId`, `timestamp`
                    FROM `tile_click_events`
                    """.trimIndent()
        )

        db.execSQL(
            "DROP TABLE `tile_click_events`"
        )

        db.execSQL(
            """
                    ALTER TABLE `tile_click_events_new`
                    RENAME TO `tile_click_events`
                    """.trimIndent()
        )

        db.execSQL(
            """
                    CREATE INDEX IF NOT EXISTS
                    `index_tile_click_events_tileId`
                    ON `tile_click_events` (`tileId`)
                    """.trimIndent()
        )

        db.execSQL(
            """
                    CREATE INDEX IF NOT EXISTS
                    `index_tile_click_events_profileId`
                    ON `tile_click_events` (`profileId`)
                    """.trimIndent()
        )

        db.execSQL(
            """
                    CREATE INDEX IF NOT EXISTS
                    `index_tile_click_events_timestamp`
                    ON `tile_click_events` (`timestamp`)
                    """.trimIndent()
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        /*
         * Resolve any existing duplicate cell placements before
         * creating the unique index.
         *
         * The first placement keeps its cell. Additional placements
         * remain in the category but become unpositioned.
         */
        db.execSQL(
            """
                    UPDATE `tile_placements`
                    SET `cellIndex` = NULL
                    WHERE `cellIndex` IS NOT NULL
                      AND `rowid` NOT IN (
                          SELECT MIN(`rowid`)
                          FROM `tile_placements`
                          WHERE `cellIndex` IS NOT NULL
                          GROUP BY
                              `parentId`,
                              `languageCode`,
                              `cellIndex`
                      )
                    """.trimIndent()
        )

        /*
         * Replace the old parent/language index with an index that
         * also supports sorting by sortOrder.
         */
        db.execSQL(
            """
                    DROP INDEX IF EXISTS
                    `index_tile_placements_parentId_languageCode`
                    """.trimIndent()
        )

        db.execSQL(
            """
                    CREATE INDEX IF NOT EXISTS
                    `index_tile_placements_tile_language`
                    ON `tile_placements` (
                        `tileId`,
                        `languageCode`
                    )
                    """.trimIndent()
        )

        db.execSQL(
            """
                    CREATE INDEX IF NOT EXISTS
                    `index_tile_placements_parent_language_sort`
                    ON `tile_placements` (
                        `parentId`,
                        `languageCode`,
                        `sortOrder`
                    )
                    """.trimIndent()
        )

        db.execSQL(
            """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_tile_placements_unique_cell`
                    ON `tile_placements` (
                        `parentId`,
                        `languageCode`,
                        `cellIndex`
                    )
                    """.trimIndent()
        )
    }
}
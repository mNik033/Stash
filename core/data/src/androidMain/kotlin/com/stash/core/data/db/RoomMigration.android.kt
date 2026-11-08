package com.stash.core.data.db

import androidx.room.driver.SupportSQLiteConnection
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase

actual abstract class StashMigration actual constructor(
    startVersion: Int,
    endVersion: Int,
) : Migration(startVersion, endVersion) {
    actual abstract fun migrateDb(db: SQLiteConnection)

    override fun migrate(connection: SQLiteConnection) {
        migrateDb(connection)
    }

    override fun migrate(db: SupportSQLiteDatabase) {
        migrateDb(SupportSQLiteConnection(db))
    }
}

internal fun StashMigrations.rekeyHidden(db: SupportSQLiteDatabase) {
    rekeyHidden(SupportSQLiteConnection(db))
}

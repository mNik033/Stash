package com.stash.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

actual abstract class StashMigration actual constructor(
    startVersion: Int,
    endVersion: Int,
) : Migration(startVersion, endVersion) {
    actual abstract fun migrateDb(db: SQLiteConnection)

    override fun migrate(connection: SQLiteConnection) {
        migrateDb(connection)
    }
}

package com.stash.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

abstract expect class StashMigration(startVersion: Int, endVersion: Int) : Migration {
    abstract fun migrateDb(db: SQLiteConnection)
}

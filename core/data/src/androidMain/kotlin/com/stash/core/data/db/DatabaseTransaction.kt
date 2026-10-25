package com.stash.core.data.db

import androidx.room.withTransaction

actual suspend fun <R> StashDatabase.withDatabaseTransaction(block: suspend () -> R): R =
    withTransaction(block)

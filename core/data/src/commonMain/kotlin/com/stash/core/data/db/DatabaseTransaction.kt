package com.stash.core.data.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

suspend fun <R> StashDatabase.withDatabaseTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor ->
        transactor.immediateTransaction {
            block()
        }
    }

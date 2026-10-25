package com.stash.core.data.db

expect suspend fun <R> StashDatabase.withDatabaseTransaction(block: suspend () -> R): R

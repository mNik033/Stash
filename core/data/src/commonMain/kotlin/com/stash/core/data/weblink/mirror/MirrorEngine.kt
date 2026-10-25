package com.stash.core.data.weblink.mirror

expect class MirrorEngine {
    fun isMirrored(localId: Long): Boolean
    fun canDeleteEverywhere(localId: Long): Boolean
    suspend fun beforeDelete(localId: Long, everywhere: Boolean)
    suspend fun load()
}

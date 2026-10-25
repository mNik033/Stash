package com.stash.core.data.weblink.mirror

actual class MirrorEngine {
    actual fun isMirrored(localId: Long): Boolean = false
    actual fun canDeleteEverywhere(localId: Long): Boolean = false
    actual suspend fun beforeDelete(localId: Long, everywhere: Boolean) {}
    actual suspend fun load() {}
}

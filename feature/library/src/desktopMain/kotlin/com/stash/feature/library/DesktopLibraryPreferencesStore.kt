package com.stash.feature.library

import com.stash.core.common.platform.AppPaths
import java.io.File
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class DesktopLibraryPreferencesStore @Inject constructor(
    private val appPaths: AppPaths,
) : LibraryPreferencesStore {

    private val file: File get() = File(appPaths.filesDir, "library_prefs.properties")
    private val props = Properties()
    private val mutex = Mutex()

    private fun load() {
        if (file.exists()) {
            runCatching {
                file.inputStream().use { props.load(it) }
            }
        }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, null) }
        }
    }

    override suspend fun getSortOrder(): SortOrder = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            val stored = props.getProperty("sort_order")
            stored?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.RECENT
        }
    }

    override suspend fun getSourceFilter(): SourceFilter = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            val stored = props.getProperty("source_filter")
            stored?.let { runCatching { SourceFilter.valueOf(it) }.getOrNull() } ?: SourceFilter.ALL
        }
    }

    override suspend fun setSortOrder(order: SortOrder) = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            props.setProperty("sort_order", order.name)
            save()
        }
    }

    override suspend fun setSourceFilter(filter: SourceFilter) = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            props.setProperty("source_filter", filter.name)
            save()
        }
    }
}

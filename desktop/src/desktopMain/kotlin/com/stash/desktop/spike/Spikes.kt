package com.stash.desktop.spike

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Component
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

// --- Spike 2: Room KMP with BundledSQLiteDriver on JVM ---

@Entity
data class SpikeTrack(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
)

@Dao
interface SpikeDao {
    @Insert
    suspend fun insert(track: SpikeTrack)

    @Query("SELECT * FROM SpikeTrack WHERE id = :id")
    suspend fun findById(id: Long): SpikeTrack?
}

@Database(entities = [SpikeTrack::class], version = 1, exportSchema = false)
abstract class SpikeDatabase : RoomDatabase() {
    abstract fun dao(): SpikeDao
}

object RoomSpike {
    fun createInMemoryDb(): SpikeDatabase {
        return Room.inMemoryDatabaseBuilder<SpikeDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}

// --- Spike 3: Dagger on Desktop JVM ---

@Module
class SpikeModule {
    @Provides
    @Singleton
    fun provideGreeting(): String = "Stash Desktop DI initialized successfully via Dagger"
}

class SpikeService @Inject constructor(
    val greeting: String,
)

@Singleton
@Component(modules = [SpikeModule::class])
interface SpikeComponent {
    fun service(): SpikeService
}

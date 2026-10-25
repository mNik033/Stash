package com.stash.feature.nowplaying

import com.stash.core.data.prefs.LyricsPreference
import com.stash.core.media.output.HiResOutputController
import com.stash.feature.nowplaying.ui.LyricsOutput
import com.stash.feature.nowplaying.ui.lyricsOutputOf
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutedLyricsOutputOffsets @Inject constructor(
    controller: HiResOutputController,
    private val preference: LyricsPreference,
) : LyricsOutputOffsets {
    override val output: Flow<LyricsOutput?> = controller.output
        .map { path -> path?.device?.let { lyricsOutputOf(it.kind.name, it.name) } }
        .distinctUntilChanged()

    override fun offsetMs(output: LyricsOutput): Flow<Long> = preference.outputOffsetMs(output.key)

    override suspend fun setOffsetMs(output: LyricsOutput, offsetMs: Long) =
        preference.setOutputOffsetMs(output.key, offsetMs)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LyricsOutputOffsetsModule {
    @Binds
    abstract fun bind(impl: RoutedLyricsOutputOffsets): LyricsOutputOffsets
}

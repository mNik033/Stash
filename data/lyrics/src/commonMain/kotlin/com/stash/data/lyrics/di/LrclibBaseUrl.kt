package com.stash.data.lyrics.di

import javax.inject.Qualifier

/**
 * Qualifier for the LRCLIB base URL [String] consumed by
 * [com.stash.data.lyrics.source.LrclibLyricsSource].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LrclibBaseUrl

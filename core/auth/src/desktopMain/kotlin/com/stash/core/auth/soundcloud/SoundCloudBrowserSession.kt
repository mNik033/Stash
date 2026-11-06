package com.stash.core.auth.soundcloud

import com.stash.data.soundcloud.SoundCloudCredentials
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
actual class SoundCloudBrowserSession @Inject constructor() {
    actual suspend fun refreshToken(expectedAccess: String): String? = null
    actual suspend fun update(expectedAccess: String, credentials: SoundCloudCredentials) {}
}


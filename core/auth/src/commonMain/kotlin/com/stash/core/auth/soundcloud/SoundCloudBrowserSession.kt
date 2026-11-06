package com.stash.core.auth.soundcloud

import com.stash.data.soundcloud.SoundCloudCredentials

expect class SoundCloudBrowserSession {
    suspend fun refreshToken(expectedAccess: String): String?
    suspend fun update(expectedAccess: String, credentials: SoundCloudCredentials)
}


package com.stash.core.auth.store

import com.stash.core.auth.model.ServiceToken
import com.stash.core.auth.model.UserInfo
import kotlinx.coroutines.flow.Flow

/**
 * Platform abstraction for storing encrypted service credentials.
 */
interface TokenStore {
    val spotifyToken: Flow<ServiceToken?>
    val spotifyUser: Flow<UserInfo?>
    suspend fun saveSpotifyToken(token: ServiceToken, user: UserInfo? = null)
    suspend fun clearSpotify()

    val youTubeToken: Flow<ServiceToken?>
    val youTubeUser: Flow<UserInfo?>
    suspend fun saveYouTubeToken(token: ServiceToken, user: UserInfo? = null)
    suspend fun clearYouTube()

    val discordToken: Flow<ServiceToken?>
    val discordUser: Flow<UserInfo?>
    suspend fun saveDiscordToken(token: ServiceToken, user: UserInfo? = null)
    suspend fun clearDiscord()

    suspend fun getCloudToken(accountKey: String): ServiceToken?
    suspend fun saveCloudToken(accountKey: String, token: ServiceToken)
    suspend fun clearCloudToken(accountKey: String)
}

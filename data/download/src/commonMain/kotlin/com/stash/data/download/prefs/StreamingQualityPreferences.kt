package com.stash.data.download.prefs

import com.stash.data.download.lossless.LosslessQualityTier
import kotlinx.coroutines.flow.Flow

/**
 * Streaming-specific lossless quality, split from the download tier.
 */
expect class StreamingQualityPreferences {
    val wifiTier: Flow<LosslessQualityTier>
    val cellularTier: Flow<LosslessQualityTier>
    val saveData: Flow<Boolean>
    val preferTidalAac: Flow<Boolean>
    val preferCloudCopies: Flow<Boolean>

    suspend fun wifiTierNow(): LosslessQualityTier
    suspend fun cellularTierNow(): LosslessQualityTier
    suspend fun saveDataNow(): Boolean
    suspend fun preferTidalAacNow(): Boolean
    suspend fun preferCloudCopiesNow(): Boolean

    suspend fun setWifiTier(tier: LosslessQualityTier)
    suspend fun setCellularTier(tier: LosslessQualityTier)
    suspend fun setSaveData(value: Boolean)
    suspend fun setPreferTidalAac(value: Boolean)
    suspend fun setPreferCloudCopies(value: Boolean)
}


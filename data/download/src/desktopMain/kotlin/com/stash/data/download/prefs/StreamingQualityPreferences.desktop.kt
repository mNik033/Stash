package com.stash.data.download.prefs

import com.stash.data.download.lossless.LosslessQualityTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
actual class StreamingQualityPreferences @Inject constructor() {
    private val _wifiTier = MutableStateFlow<LosslessQualityTier>(LosslessQualityTier.HI_RES)
    actual val wifiTier: Flow<LosslessQualityTier> = _wifiTier.asStateFlow()

    private val _cellularTier = MutableStateFlow<LosslessQualityTier>(LosslessQualityTier.CD)
    actual val cellularTier: Flow<LosslessQualityTier> = _cellularTier.asStateFlow()

    private val _saveData = MutableStateFlow(false)
    actual val saveData: Flow<Boolean> = _saveData.asStateFlow()

    private val _preferTidalAac = MutableStateFlow(false)
    actual val preferTidalAac: Flow<Boolean> = _preferTidalAac.asStateFlow()

    private val _preferCloudCopies = MutableStateFlow(false)
    actual val preferCloudCopies: Flow<Boolean> = _preferCloudCopies.asStateFlow()

    actual suspend fun wifiTierNow(): LosslessQualityTier = _wifiTier.value
    actual suspend fun cellularTierNow(): LosslessQualityTier = _cellularTier.value
    actual suspend fun saveDataNow(): Boolean = _saveData.value
    actual suspend fun preferTidalAacNow(): Boolean = _preferTidalAac.value
    actual suspend fun preferCloudCopiesNow(): Boolean = _preferCloudCopies.value

    actual suspend fun setWifiTier(tier: LosslessQualityTier) {
        _wifiTier.value = tier
    }

    actual suspend fun setCellularTier(tier: LosslessQualityTier) {
        _cellularTier.value = tier
    }

    actual suspend fun setSaveData(value: Boolean) {
        _saveData.value = value
    }

    actual suspend fun setPreferTidalAac(value: Boolean) {
        _preferTidalAac.value = value
    }

    actual suspend fun setPreferCloudCopies(value: Boolean) {
        _preferCloudCopies.value = value
    }
}

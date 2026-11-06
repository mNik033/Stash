package com.stash.feature.sync.components

import android.os.Build

internal actual fun isSyncBlurSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S


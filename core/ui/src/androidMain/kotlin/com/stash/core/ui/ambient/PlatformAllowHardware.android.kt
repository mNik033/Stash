package com.stash.core.ui.ambient

import coil3.request.ImageRequest
import coil3.request.allowHardware

actual fun ImageRequest.Builder.platformAllowHardware(enable: Boolean): ImageRequest.Builder =
    allowHardware(enable)

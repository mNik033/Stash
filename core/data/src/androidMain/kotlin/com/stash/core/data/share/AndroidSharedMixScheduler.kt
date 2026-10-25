package com.stash.core.data.share

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSharedMixScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SharedMixScheduler {
    override fun enqueuePublish(delaySeconds: Long) {
        SharedMixPublishWorker.enqueue(context, delaySeconds)
    }
}

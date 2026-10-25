package com.stash.core.data.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A tap asks "is this row downloading right now?" ([DownloadJobRegistry.isLive]) under the same
 * lock a worker claims and registers under, so it never sees the row claimed but its job not yet
 * registered, which reads exactly like a row stuck since a process death.
 */
class DownloadJobRegistryTest {

    @Test fun `a claimed row's job is live until it completes`() = runTest {
        val jobs = DownloadJobRegistry()
        val download = Job()

        assertThat(jobs.claimAndRegister(1L, download) { true }).isTrue()
        assertThat(jobs.isLive(1L)).isTrue()

        download.complete()
        assertThat(jobs.isLive(1L)).isFalse()
    }

    @Test fun `a claim that didn't take the row registers nothing`() = runTest {
        val jobs = DownloadJobRegistry()
        val download = Job()

        assertThat(jobs.claimAndRegister(1L, download) { false }).isFalse()

        assertThat(jobs.isLive(1L)).isFalse()
        download.cancel()
    }

    @Test fun `a tap's check waits for a claim in flight, then sees its job`() = runTest {
        val jobs = DownloadJobRegistry()
        val download = Job()
        val claimWritten = CompletableDeferred<Unit>()
        // The worker's claim: the row is IN_PROGRESS in the database, the job not registered yet.
        launch { jobs.claimAndRegister(1L, download) { claimWritten.await(); true } }
        runCurrent()

        val tapSaw = async { jobs.withClaimsHeld { jobs.isLive(1L) } }
        runCurrent()
        assertThat(tapSaw.isCompleted).isFalse() // held off mid-claim, where it would have read "stuck"

        claimWritten.complete(Unit)
        assertThat(tapSaw.await()).isTrue()
        download.cancel()
    }
}

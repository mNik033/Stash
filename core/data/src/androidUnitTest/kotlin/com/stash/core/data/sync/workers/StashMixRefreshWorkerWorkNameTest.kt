package com.stash.core.data.sync.workers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The full one-shot refresh and the materialize-only re-link are both
 * enqueued with REPLACE. On one shared unique name, every stub the discovery
 * drain materialized cancelled a full refresh mid-loop, so the remaining
 * recipes got no discovery candidates that run.
 */
class StashMixRefreshWorkerWorkNameTest {

    @Test fun `re-link and full refresh use different unique-work names`() {
        assertNotEquals(
            StashMixRefreshWorker.oneShotWorkName(materializeOnly = false),
            StashMixRefreshWorker.oneShotWorkName(materializeOnly = true),
        )
    }

    @Test fun `full refresh keeps the public one-shot name`() {
        assertEquals(
            StashMixRefreshWorker.ONE_SHOT_WORK_NAME,
            StashMixRefreshWorker.oneShotWorkName(materializeOnly = false),
        )
    }

    @Test fun `re-link name cannot collide with a per-recipe refresh name`() {
        val relink = StashMixRefreshWorker.oneShotWorkName(materializeOnly = true)
        assertFalse(relink.startsWith("${StashMixRefreshWorker.ONE_SHOT_WORK_NAME}_"))
    }
}

package com.example.data.download

import java.util.concurrent.atomic.AtomicInteger

/**
 * Application-wide admission control for download jobs.
 *
 * JobScheduler still owns durability and network constraints, but every job
 * must take a slot here before it may touch the network. That keeps the
 * "parallel downloads" preference meaningful without changing how jobs are
 * recovered after a process death. A slot is held for the whole job, including
 * source resolution, so a resolving job counts against the limit exactly like
 * a transferring one.
 */
object DownloadConcurrencyController {
    private val active = AtomicInteger(0)

    val running: Int get() = active.get()

    fun limit(): Int = DownloadPreferences.current.parallelDownloads.coerceIn(1, 5)

    /** @return true when the caller may start and now owns a slot. */
    fun tryAcquire(): Boolean {
        while (true) {
            val current = active.get()
            if (current >= limit()) return false
            if (active.compareAndSet(current, current + 1)) return true
        }
    }

    fun release() {
        active.updateAndGet { if (it > 0) it - 1 else 0 }
    }

    /**
     * Called when the user lowers the limit. Jobs already running are allowed
     * to finish their current segment; they observe the new limit the next time
     * they would start work, and no new job is admitted above it.
     */
    fun onLimitsChanged() = Unit
}

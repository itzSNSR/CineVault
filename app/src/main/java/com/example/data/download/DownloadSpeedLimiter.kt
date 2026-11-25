package com.example.data.download

/**
 * Very small global token bucket. A limit of 0 means "unlimited". Bytes are
 * handed out in small slices so a single large read cannot overshoot the
 * configured rate by more than one slice.
 */
internal object DownloadSpeedLimiter {
    private const val SLICE_BYTES = 32 * 1024
    private var windowStart = 0L
    private var usedInWindow = 0L

    @Synchronized
    fun acquire(bytes: Int) {
        val limitKbps = DownloadPreferences.current.speedLimitKbps
        if (limitKbps <= 0) return
        val allowedPerSecond = limitKbps.toLong() * 1024L
        var remaining = bytes.toLong()
        while (remaining > 0) {
            val slice = remaining.coerceAtMost(SLICE_BYTES.toLong())
            val now = System.nanoTime() / 1_000_000L
            if (windowStart == 0L) windowStart = now
            if (now - windowStart >= 1000L) {
                windowStart = now
                usedInWindow = 0L
            }
            if (usedInWindow + slice > allowedPerSecond) {
                val waitMs = (1000L - (now - windowStart)).coerceIn(5L, 1000L)
                try {
                    Thread.sleep(waitMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
                windowStart = System.nanoTime() / 1_000_000L
                usedInWindow = 0L
            }
            usedInWindow += slice
            remaining -= slice
        }
    }
}

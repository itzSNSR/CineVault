package com.example.data.download

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Splits a single HTTP representation across several Range requests.
 *
 * Safety rules:
 *  - only used for a fresh file (offset 0) with a known length,
 *  - the server must answer a one-byte probe with a valid `206` and the exact
 *    total; otherwise the caller keeps the proven single-stream path,
 *  - every worker must receive `206` with a matching `Content-Range` and no
 *    more bytes than it asked for,
 *  - any failure deletes the partial file and reports false, so the caller can
 *    restart sequentially instead of publishing a file with a hole.
 *
 * The very first `rangeOffset` bytes are part of the first worker, so the file
 * is a plain concatenation of the representation, not of the chunks.
 */
internal object MultiConnectionTransfer {
    private const val MIN_SIZE = 8L * 1024L * 1024L
    private const val PROBE = "bytes=0-0"

    fun isEligible(connections: Int, offset: Long, total: Long): Boolean =
        connections > 1 && offset == 0L && total >= MIN_SIZE

    /**
     * @param open opens a request with the given Range header. The caller owns
     *   header selection, redirects and cancellation tracking.
     * @param validate checks the HTTP status of a response (throws on error).
     */
    fun download(
        part: File,
        total: Long,
        connections: Int,
        open: (String) -> okhttp3.Response,
        validate: (okhttp3.Response) -> Unit,
        onProgress: (Long) -> Unit
    ): Boolean {
        if (!isEligible(connections, 0L, total)) return false
        if (!probe(open, validate, total)) return false

        val workers = connections.coerceIn(1, 8)
        val chunk = (total + workers - 1) / workers
        val written = AtomicLong(0)
        val failure = AtomicReference<Throwable?>(null)
        val threads = mutableListOf<Thread>()

        try {
            RandomAccessFile(part, "rw").use { it.setLength(total) }
        } catch (error: Throwable) {
            runCatching { part.delete() }
            return false
        }

        fun worker(index: Int) {
            val start = index * chunk
            val end = ((index + 1) * chunk - 1).coerceAtMost(total - 1)
            if (start > end) return
            try {
                open("bytes=$start-$end").use { response ->
                    validate(response)
                    if (response.code != 206) throw IOException("Server ignored the byte range")
                    val header = response.header("Content-Range").orEmpty()
                    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header)
                        ?: throw IOException("Server returned an invalid Content-Range")
                    val (rangeStart, rangeEnd, length) = match.groupValues.drop(1).map(String::toLong)
                    if (rangeStart != start || rangeEnd != end || length != total) {
                        throw IOException("Server returned an unexpected byte range")
                    }
                    val body = response.body ?: throw IOException("Empty byte range")
                    val expected = end - start + 1
                    if (body.contentLength() >= 0 && body.contentLength() != expected) {
                        throw IOException("Server declared the wrong range length")
                    }
                    RandomAccessFile(part, "rw").use { file ->
                        file.seek(start)
                        val buffer = ByteArray(64 * 1024)
                        var position = start
                        body.byteStream().use { input ->
                            while (true) {
                                if (failure.get() != null) throw IOException("Transfer cancelled")
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (position + count > end + 1) throw IOException("Server exceeded its byte range")
                                DownloadSpeedLimiter.acquire(count)
                                file.write(buffer, 0, count)
                                position += count
                                onProgress(written.addAndGet(count.toLong()))
                            }
                        }
                        if (position != end + 1) throw IOException("Incomplete byte range")
                    }
                }
            } catch (error: Throwable) {
                if (failure.compareAndSet(null, error)) {
                    // Nothing useful can be salvaged once a chunk is missing.
                }
                throw error
            }
        }

        for (index in 0 until workers) {
            val thread = Thread({ runCatching { worker(index) } }, "cv-download-$index")
            thread.isDaemon = true
            threads += thread
            thread.start()
        }
        threads.forEach { thread ->
            try {
                thread.join()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                failure.compareAndSet(null, IOException("Cancelled"))
            }
        }

        if (failure.get() != null || written.get() != total) {
            runCatching { part.delete() }
            return false
        }
        return true
    }

    private fun probe(
        open: (String) -> okhttp3.Response,
        validate: (okhttp3.Response) -> Unit,
        total: Long
    ): Boolean {
        return try {
            open(PROBE).use { response ->
                validate(response)
                if (response.code != 206) return false
                val header = response.header("Content-Range").orEmpty()
                val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header) ?: return false
                val length = match.groupValues[3].toLongOrNull() ?: return false
                length == total
            }
        } catch (_: Throwable) {
            false
        }
    }
}

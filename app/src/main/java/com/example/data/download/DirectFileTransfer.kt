package com.example.data.download

import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class InvalidMedia(message: String) : IOException(message)

internal object DirectFileTransfer {
    private val rangePattern = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    fun write(response: Response, part: File, offset: Long, previousTotal: Long, previousValidator: String,
              onHeaders: (String, Long) -> Unit, onProgress: (Long, Long) -> Unit) {
        val body = response.body ?: throw IOException("Server returned no media")
        val append = response.code == 206
        val validator = response.header("ETag")?.takeUnless { it.startsWith("W/") } ?: response.header("Last-Modified")
        var rangeEnd = -1L
        val total = if (append) {
            val match = rangePattern.matchEntire(response.header("Content-Range").orEmpty())
                ?: throw InvalidMedia("Server returned an invalid Content-Range")
            val (start, end, length) = match.groupValues.drop(1).map(String::toLong)
            rangeEnd = end
            if (start != offset || end < start || end >= length ||
                (body.contentLength() >= 0 && body.contentLength() != end - start + 1) ||
                (offset > 0 && ((previousTotal > 0 && previousTotal != length) ||
                    (validator != null && validator != previousValidator)))) {
                throw InvalidMedia("Server returned a different file or an unexpected byte range")
            }
            length
        } else {
            if (response.code != 200) throw InvalidMedia("Unexpected HTTP ${response.code}")
            body.contentLength()
        }
        FileOutputStream(part, append && offset > 0).use { out ->
            onHeaders(validator ?: if (append) previousValidator else "", total)
            var bytes = if (append) offset else 0
            val input = body.byteStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (append && bytes + count - 1 > rangeEnd) throw InvalidMedia("Server exceeded its declared byte range")
                DownloadSpeedLimiter.acquire(count)
                out.write(buffer, 0, count)
                bytes += count
                onProgress(bytes, total)
            }
            out.fd.sync()
            if (total >= 0 && bytes != total) throw IOException("Received $bytes of $total bytes")
            onProgress(bytes, total)
        }
    }
}

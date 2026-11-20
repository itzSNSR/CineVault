package com.example.data.download

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DirectFileTransferTest {
    @get:Rule val folder = TemporaryFolder()
    private val sample = ByteArray(140_000) { (it % 251).toByte() }
    private val tag = "\"asset-v1\""

    private fun write(file: File, code: Int, payload: ByteArray, contentRange: String? = null,
                      announcedSize: Long = payload.size.toLong(), etag: String = tag) {
        val response = Response.Builder().request(Request.Builder().url("https://example.test/video").build())
            .protocol(Protocol.HTTP_1_1).code(code).message("response").header("ETag", etag)
            .apply { if (contentRange != null) header("Content-Range", contentRange) }
            .body(object : ResponseBody() {
                private val data = payload.toResponseBody()
                override fun contentType() = data.contentType()
                override fun contentLength() = announcedSize
                override fun source() = data.source()
            }).build()
        response.use { DirectFileTransfer.write(it, file, file.length(), sample.size.toLong(), tag, { _, _ -> }, { _, _ -> }) }
    }

    @Test fun interruptedResponseResumesAtExactCheckpoint() {
        val file = folder.newFile()
        val midpoint = 72341
        assertThrows(IOException::class.java) { write(file, 200, sample.copyOf(midpoint), announcedSize = sample.size.toLong()) }
        assertEquals(midpoint.toLong(), file.length())
        write(file, 206, sample.copyOfRange(midpoint, sample.size), "bytes $midpoint-${sample.lastIndex}/${sample.size}")
        assertArrayEquals(sample, file.readBytes())
    }

    @Test fun ignoredRangeReplacesPartialInsteadOfAppending() {
        val file = folder.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        write(file, 200, sample)
        assertArrayEquals(sample, file.readBytes())
    }

    @Test fun mismatchedRangeOrEntityLeavesCheckpointIntact() {
        val file = folder.newFile().apply { writeBytes(sample.copyOf(100)) }
        val tail = sample.copyOfRange(100, sample.size)
        assertThrows(InvalidMedia::class.java) { write(file, 206, tail, "bytes 0-${sample.lastIndex}/${sample.size}") }
        assertThrows(InvalidMedia::class.java) { write(file, 206, tail, "bytes 100-${sample.lastIndex}/${sample.size}", etag = "\"other\"") }
        assertArrayEquals(sample.copyOf(100), file.readBytes())
    }
}

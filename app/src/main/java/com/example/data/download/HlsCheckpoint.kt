package com.example.data.download

import java.io.File
import java.io.RandomAccessFile

internal object HlsCheckpoint {
    fun recover(part: File, committed: Long) {
        if (part.length() < committed) throw InvalidMedia("Partial video is shorter than the saved checkpoint")
        RandomAccessFile(part, "rw").use { it.setLength(committed) }
    }

    fun validate(part: File, end: Long, start: Long, fragmentedMp4: Boolean, first: Boolean) {
        RandomAccessFile(part, "r").use { file ->
            file.seek(start)
            if (fragmentedMp4) {
                if (end - start < 8) throw InvalidMedia("Incomplete MP4 fragment")
                file.readInt()
                val kind = ByteArray(4).also { file.readFully(it) }.toString(Charsets.US_ASCII)
                val allowed = if (first) setOf("ftyp", "moov") else setOf("styp", "sidx", "moof", "emsg", "prft", "free")
                if (kind !in allowed) throw InvalidMedia("Invalid MP4 fragment")
            } else {
                if (end - start < 188 || (end - start) % 188 != 0L || file.read() != 0x47) {
                    throw InvalidMedia("Invalid MPEG transport stream segment")
                }
                file.seek(end - 188)
                if (file.read() != 0x47) throw InvalidMedia("Incomplete MPEG transport stream segment")
            }
        }
    }
}

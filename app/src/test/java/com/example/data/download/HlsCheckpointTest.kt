package com.example.data.download

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HlsCheckpointTest {
    @get:Rule val folder = TemporaryFolder()
    private val packet = ByteArray(188).also { it[0] = 0x47 }

    @Test fun retryDiscardsOnlyUncommittedSegmentBytes() {
        val part = folder.newFile()
        part.writeBytes(packet + packet + packet.copyOf(83))
        HlsCheckpoint.recover(part, 376)
        HlsCheckpoint.validate(part, part.length(), 0, false, false)
        assertArrayEquals(packet + packet, part.readBytes())
    }

    @Test fun invalidOrMissingSegmentCannotAdvanceCheckpoint() {
        val part = folder.newFile()
        part.writeBytes(packet + ByteArray(188))
        assertThrows(InvalidMedia::class.java) { HlsCheckpoint.validate(part, 376, 188, false, false) }
        assertThrows(InvalidMedia::class.java) { HlsCheckpoint.recover(part, 564) }
        HlsCheckpoint.recover(part, 188)
        assertArrayEquals(packet, part.readBytes())
    }
}

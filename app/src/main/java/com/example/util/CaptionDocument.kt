package com.example.util

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.Subtitle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/** Inspect the payload before choosing a parser or writing a sidecar file. */
data class CaptionDocument(val bytes: ByteArray, val extension: String, val mime: String) {
    fun timeline(): Subtitle {
        val parser = DefaultSubtitleParserFactory().create(Format.Builder().setSampleMimeType(mime).build())
        return parser.parseToLegacySubtitle(bytes, 0, bytes.size)
    }

    companion object {
        private const val LIMIT = 4 * 1024 * 1024
        private fun bounded(input: InputStream): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= LIMIT) { "Caption exceeds size limit" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }

        fun read(input: InputStream): CaptionDocument {
            var bytes = bounded(input)
            if (bytes.take(2) == listOf(0x1f.toByte(), 0x8b.toByte())) {
                bytes = GZIPInputStream(ByteArrayInputStream(bytes)).use(::bounded)
            } else if (bytes.take(2) == listOf(0x50.toByte(), 0x4b.toByte())) {
                bytes = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    val entry = zip.nextEntry ?: error("Empty caption archive")
                    require(!entry.isDirectory) { "Unsupported caption archive" }
                    val single = bounded(zip)
                    require(zip.nextEntry == null) { "Choose an individual caption file" }
                    single
                }
            }
            val encoding = when (bytes.take(2)) {
                listOf(0xff.toByte(), 0xfe.toByte()) -> Charsets.UTF_16LE
                listOf(0xfe.toByte(), 0xff.toByte()) -> Charsets.UTF_16BE
                else -> Charsets.UTF_8
            }
            val text = bytes.toString(encoding).trimStart('\uFEFF', ' ', '\r', '\n', '\t')
            val format = when {
                text.startsWith("WEBVTT") -> "vtt" to MimeTypes.TEXT_VTT
                text.startsWith("[Script Info]", true) && text.contains("Dialogue:", true) -> "ass" to MimeTypes.TEXT_SSA
                !text.startsWith("<") && Regex("""(?m)^\s*\d{1,2}:\d{2}:\d{2}[,.]\d{3}\s*-->""").containsMatchIn(text) -> "srt" to MimeTypes.APPLICATION_SUBRIP
                else -> error("Unrecognized caption file")
            }
            return CaptionDocument(text.toByteArray(Charsets.UTF_8), format.first, format.second)
        }
    }
}

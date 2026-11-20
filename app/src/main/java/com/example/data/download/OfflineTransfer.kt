package com.example.data.download

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import androidx.media3.exoplayer.hls.playlist.*
import okhttp3.*
import org.json.JSONObject
import java.io.*
import java.math.BigInteger
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class OfflineTransfer(private val context: Context, private val job: JSONObject) {
    private val id = job.getInt("id")
    private val part = DownloadQueue.part(job)
    private val source = job.getString("url")
    private val headers = job.getJSONObject("headers")
    private var lastTick = SystemClock.elapsedRealtime()
    private var lastBytes = job.optLong("bytes")
    private val keys = mutableMapOf<String, ByteArray>()

    private fun update(change: (JSONObject) -> Unit) = DownloadQueue.update(context, job, change)

    private fun request(url: String, range: String? = null, validator: String? = null): Response {
        val builder = Request.Builder().url(url)
        headers.keys().forEach { builder.header(it, headers.getString(it)) }
        builder.header("Accept-Encoding", "identity")
        range?.let { builder.header("Range", it) }
        validator?.let { builder.header("If-Range", it) }
        val call = client.newCall(builder.build())
        DownloadQueue.calls[id] = call
        return call.execute()
    }

    private fun Response.checkHttp() {
        if (isSuccessful) return
        val message = "HTTP $code ${this.message}"
        if (code == 408 || code == 429 || code in 500..599) throw IOException(message)
        throw InvalidMedia(message)
    }

    private fun progress(bytes: Long, total: Long, percent: Int, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastTick
        if (!force && elapsed < 1000) return
        update {
            it.put("bytes", bytes).put("total", total).put("percent", percent)
                .put("speed", ((bytes - lastBytes).coerceAtLeast(0) * 1000 / elapsed.coerceAtLeast(1)))
                .put("touched", System.currentTimeMillis())
        }
        lastTick = now
        lastBytes = bytes
    }

    fun run() {
        update { }
        val published = job.optString("output").takeIf { it.isNotEmpty() }?.let(::File)
        if (published?.isFile == true && !part.exists()) {
            verifyVideo(published)
            complete()
            return
        }
        val folder = part.parentFile ?: throw IOException("Missing download folder")
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Download folder unavailable")
        val isHls = job.optBoolean("hls")
        if (isHls) {
            request(source).use { mediaPlaylist(it) }
        } else {
            val offset = part.length()
            val validator = job.optString("validator").takeIf { it.isNotBlank() }
            val resume = offset > 0 && validator != null
            request(source, if (resume) "bytes=$offset-" else null, if (resume) validator else null).use { response ->
                if (response.code == 416 && resume && response.header("Content-Range") == "bytes */$offset" &&
                    listOfNotNull(response.header("ETag"), response.header("Last-Modified")).let { it.isEmpty() || validator in it } &&
                    job.optLong("total", -1) == offset) {
                    // The full representation was flushed before process death.
                } else {
                    response.checkHttp()
                    if (response.code == 200 && response.peekBody(16).string().trimStart('\uFEFF').startsWith("#EXTM3U")) {
                        update { it.put("hls", true) }
                        mediaPlaylist(response)
                    } else {
                        val type = response.body?.contentType()?.type
                        if (type == "text" || response.body?.contentType()?.subtype == "json") throw InvalidMedia("Server returned ${response.body?.contentType()} instead of video")
                        DirectFileTransfer.write(response, part, if (resume) offset else 0, job.optLong("total", -1), validator.orEmpty(),
                            onHeaders = { tag, size -> update { it.put("validator", tag).put("total", size) } },
                            onProgress = { bytes, size -> progress(bytes, size, if (size > 0) (bytes * 100 / size).toInt().coerceAtMost(99) else -1) })
                    }
                }
            }
        }
        verifyVideo(part)
        val ext = part.inputStream().use { stream ->
            val magic = ByteArray(8); val size = stream.read(magic)
            when {
                size >= 1 && magic[0] == 0x47.toByte() -> "ts"
                size >= 4 && magic.take(4) == listOf(0x1a.toByte(), 0x45.toByte(), 0xdf.toByte(), 0xa3.toByte()) -> "mkv"
                size >= 8 && String(magic, 4, 4, Charsets.US_ASCII) in listOf("ftyp", "moov", "mdat", "free", "styp", "wide") -> "mp4"
                else -> throw InvalidMedia("Unsupported offline video container")
            }
        }
        val name = folder.name
        var target = File(folder, "$name.$ext")
        var suffix = 0
        while (target.exists()) { suffix++; target = File(folder, "$name-$id-$suffix.$ext") }
        update { it.put("output", target.absolutePath) }
        update {
            if (target.exists() || !part.renameTo(target)) throw IOException("Could not publish completed video")
            it.put("state", "Completed").remove("headers")
            it.remove("url")
        }
    }

    private fun complete() = update { it.put("state", "Completed").remove("headers"); it.remove("url") }

    private fun playlist(response: Response): HlsPlaylist {
        response.checkHttp()
        val body = response.body ?: throw IOException("Empty HLS playlist")
        return HlsPlaylistParser().parse(Uri.parse(response.request.url.toString()), body.byteStream())
    }

    private fun mediaPlaylist(response: Response) {
        val first = playlist(response)
        val media = when (first) {
            is HlsMediaPlaylist -> first
            is HlsMultivariantPlaylist -> {
                val variants = first.variants.filter { v -> first.audios.none { it.groupId == v.audioGroupId && it.url != null } }
                val quality = job.optString("quality").lowercase(java.util.Locale.ROOT)
                val height = when {
                    "4k" in quality -> 2160
                    "2k" in quality -> 1440
                    else -> quality.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE
                }
                val variant = variants.filter { it.format.height <= height }.maxByOrNull { it.format.bitrate }
                    ?: variants.minByOrNull { it.format.bitrate } ?: throw InvalidMedia("Separate HLS renditions require online playback")
                request(variant.url.toString()).use { playlist(it) as? HlsMediaPlaylist }
                    ?: throw InvalidMedia("Invalid HLS variant")
            }
            else -> throw InvalidMedia("Invalid HLS playlist")
        }
        val segments = media.segments
        if (!media.hasEndTag || segments.isEmpty()) throw InvalidMedia("Only complete on-demand HLS playlists can be saved")
        val firstSegment = segments.first()
        if (segments.any { it.hasGapTag || it.drmInitData != null || it.relativeDiscontinuitySequence != firstSegment.relativeDiscontinuitySequence ||
                it.initializationSegment != firstSegment.initializationSegment }) {
            throw InvalidMedia("This playlist contains gaps, DRM, or changing media formats")
        }
        val parts = listOfNotNull(firstSegment.initializationSegment) + segments
        val fingerprint = MessageDigest.getInstance("SHA-256").digest((media.baseUri + media.mediaSequence +
            parts.joinToString { "${it.url}:${it.byteRangeOffset}:${it.byteRangeLength}:${it.durationUs}:${it.fullSegmentEncryptionKeyUri}:${it.encryptionIV}" }).toByteArray()).joinToString("") { "%02x".format(it) }
        if (job.optString("playlist") != fingerprint) {
            update { it.put("playlist", fingerprint).put("segment", 0).put("committed", 0) }
        }
        val committed = job.optLong("committed")
        HlsCheckpoint.recover(part, committed)
        var bytes = committed
        FileOutputStream(part, true).use { output ->
            for (index in job.optInt("segment") until parts.size) {
                val segment = parts[index]
                val url = resolve(media.baseUri, segment.url)
                val length = segment.byteRangeLength
                val start = segment.byteRangeOffset
                val range = if (length >= 0) "bytes=$start-${start + length - 1}" else null
                val key = segment.fullSegmentEncryptionKeyUri?.let { relative ->
                    val keyUrl = resolve(media.baseUri, relative)
                    keys.getOrPut(keyUrl) { request(keyUrl).use { r ->
                        r.checkHttp()
                        r.peekBody(17).bytes().takeIf { it.size == 16 } ?: throw InvalidMedia("Invalid HLS encryption key")
                    } }
                }
                request(url, range).use { r ->
                    r.checkHttp()
                    if (range != null && (r.code != 206 || r.header("Content-Range")?.substringBefore('/') != range.replace("=", " "))) {
                        throw InvalidMedia("Invalid byte range for segment ${index + 1}")
                    }
                    val body = r.body ?: throw IOException("Empty segment ${index + 1}")
                    val stream = body.byteStream()
                    val input = if (key == null) stream else {
                        val iv = BigInteger(segment.encryptionIV!!.removePrefix("0x").removePrefix("0X"), 16).toByteArray()
                        val padded = ByteArray(16)
                        val size = minOf(16, iv.size)
                        iv.copyInto(padded, 16 - size, iv.size - size)
                        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(padded))
                        CipherInputStream(stream, cipher)
                    }
                    val before = bytes
                    copy(input, output) { n -> bytes += n; progress(bytes, -1, index * 100 / parts.size) }
                    if (bytes == before) throw IOException("Empty segment ${index + 1}")
                }
                HlsCheckpoint.validate(part, bytes, job.optLong("committed"), firstSegment.initializationSegment != null, index == 0)
                output.fd.sync()
                update { it.put("segment", index + 1).put("committed", bytes) }
                progress(bytes, -1, ((index + 1) * 100 / parts.size).coerceAtMost(99))
            }
        }
    }

    private fun resolve(base: String, relative: String): String {
        val uri = URI(base)
        val target = uri.resolve(relative)
        return if (target.rawAuthority != uri.rawAuthority || URI(relative).isAbsolute || target.rawQuery != null || uri.rawQuery == null) target.toString()
            else "$target?${uri.rawQuery}"
    }

    private fun copy(input: InputStream, output: OutputStream, onBytes: (Int) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            onBytes(count)
        }
    }

    private fun verifyVideo(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val video = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw InvalidMedia("Downloaded data contains no readable video track")
            extractor.selectTrack(video)
            if (extractor.sampleTime < 0) throw InvalidMedia("Video track contains no readable samples")
        } finally {
            extractor.release()
        }
    }

    companion object {
        private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS).build()
    }
}

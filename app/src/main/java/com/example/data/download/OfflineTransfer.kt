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
    private val keys = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    private fun update(change: (JSONObject) -> Unit) = DownloadQueue.update(context, job, change)

    private fun request(url: String, range: String? = null, validator: String? = null): Response {
        val builder = Request.Builder().url(url)
        headers.keys().forEach { builder.header(it, headers.getString(it)) }
        builder.header("Accept-Encoding", "identity")
        range?.let { builder.header("Range", it) }
        validator?.let { builder.header("If-Range", it) }
        val call = client.newCall(builder.build())
        DownloadQueue.track(id, call)
        return call.execute()
    }

    private fun Response.checkHttp() {
        if (isSuccessful) return
        val message = "HTTP $code ${this.message}"
        if (code == 408 || code == 429 || code in 500..599) throw IOException(message)
        throw InvalidMedia(message)
    }

    @Synchronized
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
            val connections = DownloadPreferences.current.connectionsPerDownload.coerceIn(1, 8)
            if (!resume && connections > 1) {
                // Probe the representation first. Only a server that answers a
                // one-byte range with the exact total is trusted with parallel
                // chunks; everything else keeps the proven single-stream path.
                var total = -1L
                var playlist = false
                request(source).use { response ->
                    response.checkHttp()
                    if (response.code == 200 && response.peekBody(16).string().trimStart('\uFEFF').startsWith("#EXTM3U")) {
                        update { it.put("hls", true) }
                        playlist = true
                        mediaPlaylist(response)
                    } else {
                        val type = response.body?.contentType()?.type
                        if (type == "text" || response.body?.contentType()?.subtype == "json") {
                            throw InvalidMedia("Server returned ${response.body?.contentType()} instead of video")
                        }
                        total = response.body?.contentLength() ?: -1L
                    }
                }
                if (!playlist) {
                    val completed = MultiConnectionTransfer.isEligible(connections, 0L, total) &&
                        MultiConnectionTransfer.download(
                            part = part,
                            total = total,
                            connections = connections,
                            open = { range -> request(source, range) },
                            validate = { it.checkHttp() },
                            onProgress = { bytes ->
                                progress(bytes, total, if (total > 0) (bytes * 100 / total).toInt().coerceAtMost(99) else -1)
                            }
                        )
                    if (completed) {
                        update { it.put("total", total).put("validator", "") }
                    } else {
                        // Any chunk failure deletes the partial file, so restart
                        // cleanly on one connection rather than risk a hole.
                        request(source).use { response ->
                            response.checkHttp()
                            if (response.code == 200 && response.peekBody(16).string().trimStart('\uFEFF').startsWith("#EXTM3U")) {
                                update { it.put("hls", true) }
                                mediaPlaylist(response)
                            } else {
                                DirectFileTransfer.write(response, part, 0, total, "",
                                    onHeaders = { tag, size -> update { it.put("validator", tag).put("total", size) } },
                                    onProgress = { bytes, size -> progress(bytes, size, if (size > 0) (bytes * 100 / size).toInt().coerceAtMost(99) else -1) })
                            }
                        }
                    }
                }
            } else {
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
        }
        complete()
    }

    private fun complete() {
        val captions = job.optJSONArray("captions")
        if (captions != null && captions.length() > 0) {
            update { it.put("savingCaptions", true) }
            val tracks = (0 until captions.length()).map { i ->
                val item = captions.getJSONObject(i)
                com.example.data.model.SubtitleItem(item.getString("language"), item.getString("url"), item.optString("format"))
            }
            val result = OfflineSubtitlePack.download(File(job.getString("folder")), tracks, null)
            if (result.failed.isNotEmpty()) throw IOException("Video saved. Captions pending: " + result.failed.joinToString())
        }
        update {
            it.put("state", "Completed").put("savingCaptions", false)
            it.remove("headers")
            it.remove("url")
            it.remove("captions")
        }
    }

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
                if (variants.isEmpty()) throw InvalidMedia("Separate HLS audio requires online playback")
                val variant = (if (height == Int.MAX_VALUE) variants else variants.filter { it.format.height == height })
                    .maxByOrNull { it.format.bitrate } ?: throw InvalidMedia("Selected HLS resolution is unavailable")
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
        // A prefetched batch is not part of the checkpoint yet: discard any
        // leftovers from an interrupted run and re-fetch those segments.
        part.parentFile?.listFiles { f -> f.name.startsWith(".cv-$id.batch-") }?.forEach { it.delete() }

        // Connections per download is the HLS prefetch width. Segments are still
        // appended strictly in playlist order, and the durable checkpoint moves
        // only after a segment is fully written and validated.
        val connections = DownloadPreferences.current.connectionsPerDownload.coerceIn(1, 8)
        FileOutputStream(part, true).use { output ->
            var index = job.optInt("segment")
            while (index < parts.size) {
                val endExclusive = minOf(parts.size, index + connections)
                val batch = (index until endExclusive).toList()
                val prefetched = if (batch.size == 1) {
                    listOf(fetchSegment(batch.first(), parts[batch.first()], media))
                } else {
                    val tasks = batch.map { position ->
                        segmentPool.submit(java.util.concurrent.Callable { fetchSegment(position, parts[position], media) })
                    }
                    try {
                        tasks.map { it.get() }
                    } catch (error: java.util.concurrent.ExecutionException) {
                        tasks.forEach { it.cancel(true) }
                        throw (error.cause as? Exception) ?: error
                    }
                }
                try {
                    for (offset in prefetched.indices) {
                        val current = index + offset
                        prefetched[offset].inputStream().use { input ->
                            val before = bytes
                            copy(input, output) { count -> bytes += count }
                            if (bytes == before) throw IOException("Empty segment ${current + 1}")
                        }
                        HlsCheckpoint.validate(
                            part, bytes, job.optLong("committed"),
                            firstSegment.initializationSegment != null, current == 0
                        )
                        output.fd.sync()
                        update { it.put("segment", current + 1).put("committed", bytes) }
                        progress(bytes, -1, ((current + 1) * 100 / parts.size).coerceAtMost(99))
                        prefetched[offset].delete()
                    }
                } finally {
                    prefetched.forEach { it.delete() }
                }
                index = endExclusive
            }
        }
    }

    /** Downloads one HLS segment (decrypting when needed) into a batch file. */
    private fun fetchSegment(position: Int, segment: HlsMediaPlaylist.Segment, media: HlsMediaPlaylist): File {
        val url = resolve(media.baseUri, segment.url)
        val length = segment.byteRangeLength
        val start = segment.byteRangeOffset
        val range = if (length >= 0) "bytes=$start-${start + length - 1}" else null
        val key = segment.fullSegmentEncryptionKeyUri?.let { relative ->
            val keyUrl = resolve(media.baseUri, relative)
            keys[keyUrl] ?: synchronized(keys) {
                keys[keyUrl] ?: request(keyUrl).use { r ->
                    r.checkHttp()
                    (r.peekBody(17).bytes().takeIf { it.size == 16 }
                        ?: throw InvalidMedia("Invalid HLS encryption key")).also { keys[keyUrl] = it }
                }
            }
        }
        val temp = File(part.parentFile, ".cv-$id.batch-${position}.tmp")
        try {
            request(url, range).use { r ->
                r.checkHttp()
                if (range != null && (r.code != 206 || r.header("Content-Range")?.substringBefore('/') != range.replace("=", " "))) {
                    throw InvalidMedia("Invalid byte range for segment ${position + 1}")
                }
                val body = r.body ?: throw IOException("Empty segment ${position + 1}")
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
                FileOutputStream(temp).use { out -> copy(input, out) { } }
            }
            if (temp.length() == 0L) throw IOException("Empty segment ${position + 1}")
            return temp
        } catch (error: Throwable) {
            temp.delete()
            throw error
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
            DownloadSpeedLimiter.acquire(count)
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
        private val segmentPool = java.util.concurrent.Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "cv-hls-segment").apply { isDaemon = true }
        }
    }
}

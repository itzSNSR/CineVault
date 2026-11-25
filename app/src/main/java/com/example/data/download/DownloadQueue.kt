package com.example.data.download

import android.app.DownloadManager as DM
import android.app.job.*
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import com.example.ui.viewmodel.ActiveDownload
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Durable transfer intent. The platform owns scheduling, the app owns its partial files. */
object DownloadQueue {
    private fun prefs(c: Context) = c.getSharedPreferences("offline_transfers", Context.MODE_PRIVATE)
    private fun scheduler(c: Context) = c.getSystemService(JobScheduler::class.java)
    private fun read(c: Context, id: Int) = prefs(c).getString("job_$id", null)?.let(::JSONObject)
    private fun rows(c: Context) = prefs(c).all.filterKeys { it.startsWith("job_") }.values.map { JSONObject(it as String) }
    private fun save(c: Context, row: JSONObject) {
        check(prefs(c).edit().putString("job_${row.getInt("id")}", row.toString()).commit()) { "Unable to save download state" }
    }

    @Synchronized fun enqueue(c: Context, key: String, title: String, url: String, headers: Map<String, String>,
                              folder: File, poster: String, quality: String, captions: List<com.example.data.model.SubtitleItem> = emptyList()): Boolean {
        DownloadPreferences.load(c)
        val captionPlan = org.json.JSONArray()
        captions.forEach { captionPlan.put(JSONObject().put("language", it.language).put("url", it.url).put("format", it.format)) }
        val existing = rows(c).firstOrNull { it.getString("key") == key && it.getString("state") != "Cancelled" &&
            (it.getString("state") != "Completed" || File(it.optString("output")).isFile) }
        if (existing != null) {
            if (existing.getString("state") == "Failed") {
                existing.put("url", url).put("headers", JSONObject(headers)).put("captions", captionPlan)
                save(c, existing)
                resume(c, existing.getInt("id"))
                check(read(c, existing.getInt("id"))?.optString("state") != "Failed") { "Android could not schedule this transfer" }
                return true
            }
            return false
        }
        val id = prefs(c).getInt("next", 30000)
        check(prefs(c).edit().putInt("next", id + 1).commit())
        val row = JSONObject().put("id", id).put("key", key).put("title", title).put("url", url)
            .put("headers", JSONObject(headers)).put("folder", folder.absolutePath).put("poster", poster)
            .put("quality", quality).put("captions", captionPlan).put("state", "Queued").put("generation", 0)
        save(c, row)
        check(schedule(c, row)) { "Android could not schedule this transfer" }
        return true
    }

    private fun schedule(c: Context, row: JSONObject): Boolean {
        val settings = DownloadPreferences.current
        val job = JobInfo.Builder(row.getInt("id"), ComponentName(c, VideoDownloadService::class.java))
            .setRequiredNetworkType(
                if (settings.wifiOnly || !settings.allowMobileData) JobInfo.NETWORK_TYPE_UNMETERED
                else JobInfo.NETWORK_TYPE_ANY
            )
            // A linear retry lets a job that was queued behind the parallel limit
            // start as soon as a slot frees, without exponential backoff drift.
            .setBackoffCriteria(10_000, JobInfo.BACKOFF_POLICY_LINEAR)
            .build()
        if (runCatching { scheduler(c).schedule(job) }.getOrDefault(JobScheduler.RESULT_FAILURE) != JobScheduler.RESULT_SUCCESS) {
            save(c, row.put("state", "Failed").put("error", "Android could not schedule this transfer"))
            return false
        }
        return true
    }

    @Synchronized fun restore(c: Context) {
        DownloadPreferences.load(c)
        relocateUnwritable(c)
        for (row in rows(c)) {
            val id = row.getInt("id")
            if (row.getString("state") == "Cancelled") {
                cleanCancelled(c, id)
            } else if (row.getString("state") in listOf("Queued", "Downloading", "Retrying") && scheduler(c).getPendingJob(id) == null) {
                save(c, row.put("state", "Queued"))
                schedule(c, row)
            }
        }
    }

    @Synchronized fun resume(c: Context, id: Int) {
        relocateUnwritable(c)
        val row = read(c, id) ?: return
        if (row.getString("state") !in listOf("Paused", "Failed")) return
        save(c, row.put("state", "Queued").put("attempt", 0).put("error", "").put("generation", row.getInt("generation") + 1))
        schedule(c, row)
    }

    @Synchronized fun stop(c: Context, id: Int, cancel: Boolean) {
        val row = read(c, id) ?: return
        if (row.getString("state") == "Completed") return
        save(c, row.put("state", if (cancel) "Cancelled" else "Paused").put("generation", row.getInt("generation") + 1))
        scheduler(c).cancel(id)
        cancelCalls(id)
        if (cancel) cleanCancelled(c, id)
    }

    /** Enough workers for the maximum parallel limit plus HLS segment prefetch. */
    internal val executor = Executors.newFixedThreadPool(8)
    internal val calls = ConcurrentHashMap<Int, MutableSet<okhttp3.Call>>()

    internal fun track(id: Int, call: okhttp3.Call) {
        calls.computeIfAbsent(id) { java.util.Collections.synchronizedSet(LinkedHashSet()) }.add(call)
    }

    internal fun untrack(id: Int, call: okhttp3.Call) {
        calls[id]?.remove(call)
        if (calls[id]?.isEmpty() == true) calls.remove(id)
    }

    internal fun cancelCalls(id: Int) {
        calls.remove(id)?.toList()?.forEach { runCatching { it.cancel() } }
    }

    /** Re-schedules queued rows so a freed slot is picked up without waiting for backoff. */
    @Synchronized fun pump(c: Context) {
        for (row in rows(c)) {
            if (row.getString("state") != "Queued") continue
            val id = row.getInt("id")
            if (scheduler(c).getPendingJob(id) != null) scheduler(c).cancel(id)
            schedule(c, row)
        }
    }

    @Synchronized fun pauseAll(c: Context) {
        for (row in rows(c)) {
            if (row.getString("state") !in listOf("Queued", "Downloading", "Retrying")) continue
            val id = row.getInt("id")
            save(c, row.put("state", "Paused").put("generation", row.getInt("generation") + 1))
            scheduler(c).cancel(id)
            cancelCalls(id)
        }
    }

    @Synchronized fun resumeAll(c: Context) {
        for (row in rows(c)) {
            if (row.getString("state") in listOf("Paused", "Failed")) resume(c, row.getInt("id"))
        }
    }

    @Synchronized fun retryFailed(c: Context) {
        for (row in rows(c)) {
            if (row.getString("state") == "Failed") resume(c, row.getInt("id"))
        }
    }

    /** Removes finished queue rows only; the playable files stay in the library. */
    @Synchronized fun clearCompleted(c: Context) {
        for (row in rows(c)) {
            if (row.getString("state") in listOf("Completed", "Cancelled")) {
                prefs(c).edit().remove("job_${row.getInt("id")}").apply()
            }
        }
    }
    private val locks = Array(32) { Any() }
    internal fun lock(id: Int) = locks[id % locks.size]
    internal fun part(row: JSONObject) = File(row.getString("folder"), ".cv-${row.getInt("id")}.part")

    private fun writable(dir: File): Boolean {
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return false
            val probe = File(dir, ".cv_probe_${System.nanoTime()}")
            if (probe.createNewFile()) { probe.delete(); true } else false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * A queued job remembers its folder. Scoped storage can make a shared
     * Movies/CineVault folder unwritable (EPERM on the .part file), so move
     * such a job to the writable base before it runs again instead of letting
     * every retry fail on the same path.
     */
    @Synchronized fun relocateUnwritable(c: Context) {
        val publicBase = runCatching {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "CineVault")
        }.getOrNull()
        val targetBase = DownloadStorage.directory(c)
        for (row in rows(c)) {
            if (row.getString("state") in listOf("Completed", "Cancelled")) continue
            val folder = File(row.getString("folder"))
            if (writable(folder)) continue
            val relative = if (publicBase != null && folder.absolutePath.startsWith(publicBase.absolutePath + File.separator)) {
                folder.absolutePath.removePrefix(publicBase.absolutePath + File.separator)
            } else {
                folder.name
            }
            val target = File(targetBase, relative)
            if (!writable(target)) continue
            row.put("folder", target.absolutePath)
            row.optString("output").takeIf { it.isNotEmpty() }?.let { out ->
                row.put("output", File(target, File(out).name).absolutePath)
            }
            save(c, row)
        }
    }
    private fun cleanCancelled(c: Context, id: Int) {
        executor.execute {
            synchronized(lock(id)) {
                val row = read(c, id) ?: return@execute
                if (row.getString("state") == "Cancelled" && (!part(row).exists() || part(row).delete())) {
                    prefs(c).edit().remove("job_$id").commit()
                }
            }
        }
    }

    @Synchronized internal fun begin(c: Context, id: Int): JSONObject? {
        val row = read(c, id) ?: return null
        if (row.getString("state") !in listOf("Queued", "Retrying", "Downloading")) return null
        save(c, row.put("state", "Downloading").put("speed", 0).put("touched", System.currentTimeMillis()))
        return row
    }

    @Synchronized internal fun update(c: Context, row: JSONObject, change: (JSONObject) -> Unit) {
        val current = read(c, row.getInt("id"))
        if (current?.optInt("generation") != row.getInt("generation") || current.optString("state") != "Downloading") {
            throw InterruptedException()
        }
        change(row)
        save(c, row)
    }

    @Synchronized internal fun interrupted(c: Context, id: Int, reason: String): Boolean {
        val row = read(c, id) ?: return false
        if (row.getString("state") != "Downloading") return row.getString("state") in listOf("Queued", "Retrying")
        save(c, row.put("state", "Queued").put("error", reason).put("generation", row.getInt("generation") + 1))
        cancelCalls(id)
        return true
    }

    fun snapshot(c: Context): List<ActiveDownload> {
        val online = c.getSystemService(ConnectivityManager::class.java).activeNetwork != null
        return rows(c).filter { it.getString("state") !in listOf("Completed", "Cancelled") }.map { row ->
            val state = row.getString("state")
            val stalled = System.currentTimeMillis() - row.optLong("touched") > 5000
            val subtitlesBusy = state == "Downloading" && row.optBoolean("savingCaptions")
            val label = when {
                subtitlesBusy -> "Downloading subtitles"
                state == "Failed" || state == "Retrying" -> "$state · ${row.optString("error")}"
                state == "Paused" -> "Paused"
                !online -> "Waiting for network"
                state == "Downloading" && stalled -> "Waiting for server"
                state == "Downloading" && row.optInt("percent", -1) <= 0 -> "Resolving source"
                state == "Queued" && row.optString("error").isNotEmpty() -> "Queued · ${row.optString("error")}"
                else -> state
            }
            ActiveDownload(-row.getLong("id"), row.getString("title"), row.optInt("percent", -1),
                row.optLong("bytes"), row.optLong("total", -1),
                when (state) { "Paused" -> DM.STATUS_PAUSED; "Failed" -> DM.STATUS_FAILED; "Downloading" -> DM.STATUS_RUNNING; else -> DM.STATUS_PENDING },
                posterUrl = row.getString("poster"), detail = label,
                bytesPerSecond = if (!stalled && state == "Downloading") row.optLong("speed") else 0)
        }
    }
}

class VideoDownloadService : JobService() {
    private val running = mutableMapOf<Int, JobParameters>()
    private val main = Handler(Looper.getMainLooper())

    override fun onStartJob(params: JobParameters): Boolean {
        val id = params.jobId
        DownloadQueue.relocateUnwritable(this)
        // Admission control: the shared limit is the "parallel downloads" setting.
        // A job that cannot start now stays Queued and is retried by Android.
        if (!DownloadConcurrencyController.tryAcquire()) {
            main.post { jobFinished(params, true) }
            return true
        }
        val row = DownloadQueue.begin(this, id)
        if (row == null) {
            DownloadConcurrencyController.release()
            return false
        }
        running[id] = params
        DownloadQueue.executor.execute {
            var retry = false
            synchronized(DownloadQueue.lock(id)) {
                try {
                    OfflineTransfer(this, row).run()
                } catch (_: InterruptedException) {
                    // A newer generation owns this transfer.
                } catch (error: Exception) {
                    try {
                        DownloadQueue.update(this, row) {
                            val settings = DownloadPreferences.current
                            val attempts = it.optInt("attempt") + 1
                            retry = settings.automaticRetry && attempts < settings.maxRetries &&
                                error is java.io.IOException && error !is InvalidMedia
                            it.put("attempt", attempts).put("state", if (retry) "Retrying" else "Failed")
                                .put("error", error.localizedMessage ?: error.javaClass.simpleName)
                        }
                    } catch (_: InterruptedException) { }
                } finally {
                    DownloadQueue.cancelCalls(id)
                    DownloadConcurrencyController.release()
                    DownloadQueue.pump(this)
                }
            }
            main.post { if (running[id] === params) { running.remove(id); jobFinished(params, retry) } }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)
        val reason = if (Build.VERSION.SDK_INT >= 31) "Android stop reason ${params.stopReason}" else "Interrupted by Android"
        return DownloadQueue.interrupted(this, params.jobId, reason)
    }
}

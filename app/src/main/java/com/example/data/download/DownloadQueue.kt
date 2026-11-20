package com.example.data.download

import android.app.DownloadManager as DM
import android.app.job.*
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
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
                              folder: File, poster: String, quality: String): Boolean {
        val existing = rows(c).firstOrNull { it.getString("key") == key && it.getString("state") != "Cancelled" &&
            (it.getString("state") != "Completed" || File(it.optString("output")).isFile) }
        if (existing != null) {
            if (existing.getString("state") == "Failed") {
                existing.put("url", url).put("headers", JSONObject(headers))
                save(c, existing)
                resume(c, existing.getInt("id"))
                return true
            }
            return false
        }
        val id = prefs(c).getInt("next", 30000)
        check(prefs(c).edit().putInt("next", id + 1).commit())
        val row = JSONObject().put("id", id).put("key", key).put("title", title).put("url", url)
            .put("headers", JSONObject(headers)).put("folder", folder.absolutePath).put("poster", poster)
            .put("quality", quality).put("state", "Queued").put("generation", 0)
        save(c, row)
        schedule(c, row)
        return true
    }

    private fun schedule(c: Context, row: JSONObject) {
        val job = JobInfo.Builder(row.getInt("id"), ComponentName(c, VideoDownloadService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(15_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        if (runCatching { scheduler(c).schedule(job) }.getOrDefault(JobScheduler.RESULT_FAILURE) != JobScheduler.RESULT_SUCCESS) {
            save(c, row.put("state", "Failed").put("error", "Android could not schedule this transfer"))
        }
    }

    @Synchronized fun restore(c: Context) {
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
        calls.remove(id)?.cancel()
        if (cancel) cleanCancelled(c, id)
    }

    internal val executor = Executors.newFixedThreadPool(3)
    internal val calls = ConcurrentHashMap<Int, okhttp3.Call>()
    private val locks = Array(32) { Any() }
    internal fun lock(id: Int) = locks[id % locks.size]
    internal fun part(row: JSONObject) = File(row.getString("folder"), ".cv-${row.getInt("id")}.part")
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
        calls.remove(id)?.cancel()
        return true
    }

    fun snapshot(c: Context): List<ActiveDownload> {
        val online = c.getSystemService(ConnectivityManager::class.java).activeNetwork != null
        return rows(c).filter { it.getString("state") !in listOf("Completed", "Cancelled") }.map { row ->
            val state = row.getString("state")
            val stalled = System.currentTimeMillis() - row.optLong("touched") > 5000
            val label = when {
                state == "Failed" || state == "Retrying" -> "$state · ${row.optString("error")}"
                state == "Paused" -> "Paused"
                !online -> "Waiting for network"
                state == "Downloading" && stalled -> "Waiting for server"
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
        val row = DownloadQueue.begin(this, id) ?: return false
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
                            val attempts = it.optInt("attempt") + 1
                            retry = attempts < 5 && error is java.io.IOException && error !is InvalidMedia
                            it.put("attempt", attempts).put("state", if (retry) "Retrying" else "Failed")
                                .put("error", error.localizedMessage ?: error.javaClass.simpleName)
                        }
                    } catch (_: InterruptedException) { }
                } finally {
                    DownloadQueue.calls.remove(id)
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

package com.example.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.example.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

/**
 * Provider-independent download preferences.
 *
 * The store is a tiny SharedPreferences façade so every download job, the
 * service and the Compose settings UI read the same values. All writes are
 * applied atomically and immediately; running jobs pick the new concurrency
 * limits up on their next transfer step, never mid-segment.
 */
data class DownloadSettings(
    val parallelDownloads: Int = DEFAULT_PARALLEL_DOWNLOADS,
    val connectionsPerDownload: Int = DEFAULT_CONNECTIONS,
    val defaultQuality: String = "Highest",
    val preferredAudio: String? = null,
    val preferredSubtitles: Set<String> = setOf("en"),
    val autoSubtitles: Boolean = true,
    val wifiOnly: Boolean = false,
    val allowMobileData: Boolean = true,
    val automaticRetry: Boolean = true,
    val maxRetries: Int = 5,
    val speedLimitKbps: Int = 0,
    val usePublicStorage: Boolean = true
) {
    val estimatedParallelConnections: Int
        get() = (parallelDownloads.coerceIn(1, 5)) * (connectionsPerDownload.coerceIn(1, 8))

    companion object {
        const val DEFAULT_PARALLEL_DOWNLOADS = 3
        const val DEFAULT_CONNECTIONS = 2
        val PARALLEL_OPTIONS = listOf(1, 2, 3, 4, 5)
        val CONNECTION_OPTIONS = listOf(1, 2, 4, 8)
    }
}

object DownloadPreferences {
    private const val FILE = "download_settings"
    private const val KEY_PARALLEL = "parallel_downloads"
    private const val KEY_CONNECTIONS = "connections_per_download"
    private const val KEY_QUALITY = "default_quality"
    private const val KEY_AUDIO = "preferred_audio"
    private const val KEY_SUBTITLES = "preferred_subtitles"
    private const val KEY_AUTO_SUBTITLES = "auto_subtitles"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_MOBILE = "allow_mobile"
    private const val KEY_RETRY = "automatic_retry"
    private const val KEY_MAX_RETRY = "max_retries"
    private const val KEY_LIMIT = "speed_limit_kbps"
    private const val KEY_PUBLIC_STORAGE = "use_public_storage"

    private val _state = MutableStateFlow(DownloadSettings())
    val state: StateFlow<DownloadSettings> = _state.asStateFlow()

    val current: DownloadSettings get() = _state.value

    @Synchronized
    fun load(context: Context) {
        val prefs = prefs(context)
        val stored = prefs.getStringSet(KEY_SUBTITLES, null)
        _state.value = DownloadSettings(
            parallelDownloads = prefs.getInt(KEY_PARALLEL, DownloadSettings.DEFAULT_PARALLEL_DOWNLOADS)
                .takeIf { it in DownloadSettings.PARALLEL_OPTIONS } ?: DownloadSettings.DEFAULT_PARALLEL_DOWNLOADS,
            connectionsPerDownload = prefs.getInt(KEY_CONNECTIONS, DownloadSettings.DEFAULT_CONNECTIONS)
                .takeIf { it in DownloadSettings.CONNECTION_OPTIONS } ?: DownloadSettings.DEFAULT_CONNECTIONS,
            defaultQuality = prefs.getString(KEY_QUALITY, null)?.takeIf { it.isNotBlank() } ?: "Highest",
            preferredAudio = prefs.getString(KEY_AUDIO, null)?.takeIf { it.isNotBlank() },
            preferredSubtitles = stored?.map { it.lowercase(Locale.ROOT) }?.toSet() ?: setOf("en"),
            autoSubtitles = prefs.getBoolean(KEY_AUTO_SUBTITLES, true),
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false),
            allowMobileData = prefs.getBoolean(KEY_MOBILE, true),
            automaticRetry = prefs.getBoolean(KEY_RETRY, true),
            maxRetries = prefs.getInt(KEY_MAX_RETRY, 5).coerceIn(0, 10),
            speedLimitKbps = prefs.getInt(KEY_LIMIT, 0).coerceAtLeast(0),
            usePublicStorage = prefs.getBoolean(KEY_PUBLIC_STORAGE, true)
        )
    }

    @Synchronized
    fun update(context: Context, transform: (DownloadSettings) -> DownloadSettings): DownloadSettings {
        val next = transform(_state.value).let { sanitize(it) }
        val editor = prefs(context).edit()
            .putInt(KEY_PARALLEL, next.parallelDownloads)
            .putInt(KEY_CONNECTIONS, next.connectionsPerDownload)
            .putString(KEY_QUALITY, next.defaultQuality)
            .putString(KEY_AUDIO, next.preferredAudio)
            .putStringSet(KEY_SUBTITLES, next.preferredSubtitles)
            .putBoolean(KEY_AUTO_SUBTITLES, next.autoSubtitles)
            .putBoolean(KEY_WIFI_ONLY, next.wifiOnly)
            .putBoolean(KEY_MOBILE, next.allowMobileData)
            .putBoolean(KEY_RETRY, next.automaticRetry)
            .putInt(KEY_MAX_RETRY, next.maxRetries)
            .putInt(KEY_LIMIT, next.speedLimitKbps)
            .putBoolean(KEY_PUBLIC_STORAGE, next.usePublicStorage)
        editor.apply()
        _state.value = next
        DownloadConcurrencyController.onLimitsChanged()
        return next
    }

    private fun sanitize(value: DownloadSettings): DownloadSettings = value.copy(
        parallelDownloads = value.parallelDownloads.coerceIn(1, 5),
        connectionsPerDownload = value.connectionsPerDownload.coerceIn(1, 8),
        preferredSubtitles = value.preferredSubtitles.map { it.lowercase(Locale.ROOT) }.toSet(),
        maxRetries = value.maxRetries.coerceIn(0, 10),
        speedLimitKbps = value.speedLimitKbps.coerceAtLeast(0)
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/** Storage helpers shared by the dialog and the resolver. */
object DownloadStorage {
    fun directory(context: Context, settings: DownloadSettings = DownloadPreferences.current): File {
        if (settings.usePublicStorage) {
            try {
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val public = File(base, "CineVault")
                if (writable(public)) return public
            } catch (_: Throwable) {
                // Scoped storage can refuse this; fall through to app storage.
            }
        }
        try {
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { base ->
                val dir = File(base, "CineVault")
                if (writable(dir)) return dir
            }
        } catch (_: Throwable) {
            // Ignore and use internal storage.
        }
        return File(context.filesDir, "CineVault").apply { if (!exists()) mkdirs() }
    }

    /**
     * Android 11+ only lets an app write into the shared Movies folder with
     * All-files access (MANAGE_EXTERNAL_STORAGE). Without it every .part file
     * fails with EPERM even though the folder itself looks writable.
     */
    fun hasPublicStorageAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun needsPublicStorageAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            DownloadPreferences.current.usePublicStorage &&
            !Environment.isExternalStorageManager()

    fun requestPublicStorageAccess(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val app = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(app)
        } catch (_: Throwable) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /**
     * Under scoped storage a public directory can report canWrite() = true and
     * still reject every new file with EPERM, which is exactly how a download
     * ends up failing on its .part file. Only a real create proves writability.
     */
    private fun writable(dir: File): Boolean {
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return false
            val probe = File(dir, ".cv_write_${System.nanoTime()}")
            if (probe.createNewFile()) {
                probe.delete()
                true
            } else false
        } catch (_: Throwable) {
            false
        }
    }

    fun availableBytes(directory: File): Long = try {
        val stat = StatFs(directory.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    } catch (_: Throwable) {
        0L
    }

    fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> "—"
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /** Rough size for providers that do not report a content length. */
    fun estimateEpisodeBytes(quality: String?, minutes: Int = 24): Long {
        val height = quality.orEmpty().filter { it.isDigit() }.toIntOrNull()
            ?: if (quality.orEmpty().contains("4k", true)) 2160 else 720
        val kbPerSecond = when {
            height >= 2160 -> 2000
            height >= 1440 -> 1100
            height >= 1080 -> 620
            height >= 720 -> 330
            height >= 480 -> 170
            else -> 90
        }
        return kbPerSecond.toLong() * 1024L * 60L * minutes.coerceAtLeast(1)
    }

    /** A stable, filesystem-safe folder name. */
    fun safeName(value: String): String = value.trim()
        .replace(Regex("[^\\p{L}\\p{N}.-]"), "_")
        .trim('.')
        .take(110)
        .ifBlank { "Video" }
}

/** Declares what a provider can actually offer, so the shared UI never lies. */
data class ProviderCapabilities(
    val id: String,
    val displayName: String,
    val supportsMovies: Boolean = true,
    val supportsSeries: Boolean = true,
    val supportsSeek: Boolean = true,
    val supportsDownloads: Boolean = true,
    val supportsSubtitleSelection: Boolean = true,
    val supportsAudioSelection: Boolean = true,
    val supportsQualitySelection: Boolean = true,
    val isLive: Boolean = false
) {
    companion object {
        val MOVIEBOX = ProviderCapabilities("moviebox", "MovieBox")
        val ANIFAM = ProviderCapabilities("anifam", "AniFam")
        val OFFLINE = ProviderCapabilities("offline", "Offline", supportsDownloads = false)
        val LIVE_TV = ProviderCapabilities(
            "live_tv", "Live TV",
            supportsSeries = false,
            supportsSeek = false,
            supportsDownloads = false,
            supportsSubtitleSelection = false,
            supportsAudioSelection = false,
            isLive = true
        )
        fun of(provider: String): ProviderCapabilities = when (provider) {
            PreferencesManager.PROVIDER_ANIFAM -> ANIFAM
            else -> MOVIEBOX
        }
    }
}

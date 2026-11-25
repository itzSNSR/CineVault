package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.download.DownloadPreferences
import com.example.data.download.DownloadSettings
import com.example.data.download.DownloadStorage
import android.os.Build
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Download settings that apply to every provider and to both transfer engines.
 * Values are persisted immediately; running jobs read the new limits on their
 * next scheduling decision, so changing them never corrupts an active file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadSettingsSection() {
    val context = LocalContext.current
    val settings by DownloadPreferences.state.collectAsState()
    fun update(transform: (DownloadSettings) -> DownloadSettings) {
        DownloadPreferences.update(context, transform)
    }

    val hasStorageAccess = DownloadStorage.hasPublicStorageAccess()
    val directory = remember(settings.usePublicStorage, hasStorageAccess) { DownloadStorage.directory(context, settings) }
    val free = remember(directory) { DownloadStorage.availableBytes(directory) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeader("Parallel downloads", "Movies/episodes downloading at the same time")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadSettings.PARALLEL_OPTIONS.forEach { value ->
                    FilterChip(
                        selected = settings.parallelDownloads == value,
                        onClick = { update { it.copy(parallelDownloads = value) } },
                        label = { Text("$value", fontSize = 12.sp) }
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            SettingHeader("Connections per download", "Concurrent Range connections for one file (server permitting)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadSettings.CONNECTION_OPTIONS.forEach { value ->
                    FilterChip(
                        selected = settings.connectionsPerDownload == value,
                        onClick = { update { it.copy(connectionsPerDownload = value) } },
                        label = { Text("$value", fontSize = 12.sp) }
                    )
                }
            }
            Text(
                "Up to ${settings.estimatedParallelConnections} transfer connections at once. Servers that reject ranges fall back to one connection.",
                color = TextSecondary, fontSize = 11.sp
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            SettingHeader("Default quality")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Highest", "2160p", "1080p", "720p", "480p", "Auto").forEach { value ->
                    FilterChip(
                        selected = settings.defaultQuality.equals(value, true),
                        onClick = { update { it.copy(defaultQuality = value) } },
                        label = { Text(value, fontSize = 12.sp) }
                    )
                }
            }

            SettingHeader("Preferred audio")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                audioChoices.forEach { (tag, label) ->
                    FilterChip(
                        selected = settings.preferredAudio == tag,
                        onClick = { update { it.copy(preferredAudio = tag) } },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            SettingHeader("Preferred subtitles", "None offers a subtitle-free download")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.preferredSubtitles.isEmpty(),
                    onClick = { update { it.copy(preferredSubtitles = emptySet()) } },
                    label = { Text("None", fontSize = 12.sp) }
                )
                subtitleChoices.forEach { (tag, label) ->
                    FilterChip(
                        selected = settings.preferredSubtitles.contains(tag),
                        onClick = {
                            update {
                                val next = if (it.preferredSubtitles.contains(tag)) {
                                    it.preferredSubtitles - tag
                                } else it.preferredSubtitles + tag
                                it.copy(preferredSubtitles = next)
                            }
                        },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            SwitchRow(
                "Download subtitles automatically",
                settings.autoSubtitles
            ) { value -> update { it.copy(autoSubtitles = value) } }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            SwitchRow("Wi-Fi only", settings.wifiOnly) { value ->
                update { it.copy(wifiOnly = value) }
            }
            SwitchRow("Allow mobile data", settings.allowMobileData) { value ->
                update { it.copy(allowMobileData = value) }
            }

            SwitchRow(
                "Use shared Movies/CineVault folder",
                settings.usePublicStorage
            ) { value -> update { it.copy(usePublicStorage = value) } }

            if (settings.usePublicStorage && Build.VERSION.SDK_INT >= 30 && !hasStorageAccess) {
                Text(
                    "Android 11+ needs All-files access before CineVault can write into Movies/CineVault. Without it downloads are saved in the app's own folder.",
                    color = TextSecondary, fontSize = 11.sp
                )
                TextButton(onClick = { DownloadStorage.requestPublicStorageAccess(context) }) {
                    Text("Grant storage access", color = CinematicRed, fontSize = 12.sp)
                }
            }

            SettingHeader("Download location")
            Text(directory.absolutePath, color = TextSecondary, fontSize = 11.sp)
            Text(
                "Free space: ${DownloadStorage.formatBytes(free)}",
                color = TextSecondary, fontSize = 11.sp
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            SwitchRow("Retry failed downloads", settings.automaticRetry) { value ->
                update { it.copy(automaticRetry = value) }
            }
            SettingHeader("Retry attempts")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 8).forEach { value ->
                    FilterChip(
                        selected = settings.maxRetries == value,
                        onClick = { update { it.copy(maxRetries = value) } },
                        label = { Text("$value", fontSize = 12.sp) }
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            SettingHeader("Speed limit")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                speedLimits.forEach { (kb, label) ->
                    FilterChip(
                        selected = settings.speedLimitKbps == kb,
                        onClick = { update { it.copy(speedLimitKbps = kb) } },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingHeader(title: String, subtitle: String? = null) {
    Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    if (subtitle != null) {
        Text(subtitle, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CinematicRed
            )
        )
    }
}

private val audioChoices = listOf(
    "original" to "Original",
    "en" to "English",
    "hi" to "Hindi",
    "ta" to "Tamil",
    "te" to "Telugu",
    "ja" to "Japanese"
)

private val subtitleChoices = listOf(
    "en" to "English",
    "hi" to "Hindi",
    "ta" to "Tamil",
    "te" to "Telugu",
    "ml" to "Malayalam",
    "ja" to "Japanese",
    "ko" to "Korean"
)

private val speedLimits = listOf(
    0 to "Unlimited",
    512 to "0.5 MB/s",
    1024 to "1 MB/s",
    2048 to "2 MB/s",
    4096 to "4 MB/s",
    8192 to "8 MB/s"
)


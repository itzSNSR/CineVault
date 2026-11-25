package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.download.DownloadQueue
import com.example.data.download.DownloadStorage
import com.example.ui.viewmodel.ActiveDownload
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Global controls for the shared download queue. Counts come from real job
 * state: only RUNNING jobs are "active", PENDING ones are genuinely queued.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadManagerToolbar(
    active: List<ActiveDownload>,
    selectionMode: Boolean,
    selectedCount: Int,
    onToggleSelection: () -> Unit,
    onCancelSelected: () -> Unit
) {
    val context = LocalContext.current
    val running = active.count { it.status == android.app.DownloadManager.STATUS_RUNNING }
    val queued = active.count { it.status == android.app.DownloadManager.STATUS_PENDING }
    val failed = active.count { it.status == android.app.DownloadManager.STATUS_FAILED }
    val totalSpeed = active.sumOf { it.bytesPerSecond }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "$running active · $queued queued" + if (failed > 0) " · $failed failed" else "",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Total speed ${DownloadStorage.formatBytes(totalSpeed)}/s",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Text(
                    "Limit ${com.example.data.download.DownloadPreferences.current.parallelDownloads}",
                    color = CinematicRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { DownloadQueue.pauseAll(context) },
                    label = { Text("Pause all", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Filled.PauseCircle, null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = TextPrimary)
                )
                AssistChip(
                    onClick = { DownloadQueue.resumeAll(context) },
                    label = { Text("Resume all", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Filled.PlayCircle, null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = TextPrimary)
                )
                AssistChip(
                    onClick = { DownloadQueue.retryFailed(context) },
                    label = { Text("Retry failed", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Filled.DoneAll, null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = TextPrimary)
                )
                AssistChip(
                    onClick = { DownloadQueue.clearCompleted(context) },
                    label = { Text("Clear completed", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = TextPrimary)
                )
                AssistChip(
                    onClick = onToggleSelection,
                    label = { Text(if (selectionMode) "Done" else "Select", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Filled.Checklist, null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = if (selectionMode) CinematicRed else TextPrimary
                    )
                )
                if (selectionMode) {
                    AssistChip(
                        onClick = onCancelSelected,
                        label = { Text("Cancel $selectedCount", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color(0xFFEF4444))
                    )
                }
            }
            Text(
                "Downloaded subtitles are saved with the episode and auto-detected offline.",
                color = TextSecondary, fontSize = 10.sp
            )
        }
    }
}

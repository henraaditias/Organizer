package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ExcludedSubfolderEntity
import com.example.data.local.OrganizationRunEntity
import com.example.data.local.SourceFolderEntity
import com.example.domain.FormatUtils
import com.example.domain.RunStatus
import com.example.ui.DashboardUiState
import com.example.ui.components.BentoCard
import com.example.ui.components.BentoStatTile
import com.example.ui.components.CyanTelemetryChip
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.SecondaryConsoleButton
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.LabelCodeStyle
import com.example.ui.theme.LabelMonoCapsStyle
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusVerified
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceRaised
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onAddFolderClick: () -> Unit,
    onChooseDestinationClick: () -> Unit,
    onResetDestinationClick: () -> Unit,
    onOrganizeAllClick: () -> Unit,
    onOrganizeFolderClick: (SourceFolderEntity) -> Unit,
    onManageExclusionsClick: (SourceFolderEntity) -> Unit,
    onRemoveFolderClick: (SourceFolderEntity) -> Unit,
    onRegrantFolderClick: () -> Unit,
    onResumeInterruptedClick: () -> Unit,
    onCancelActiveWorkClick: () -> Unit,
    onOpenLiveProgressClick: () -> Unit,
    onOpenFailedReportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 700.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Status
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ORGANIZER // LOCAL ENGINE",
                            style = LabelMonoCapsStyle,
                            color = AccentCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Storage Command",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                    }
                    StatusBadge(
                        label = when {
                            uiState.isWorkerRunning -> "VERIFYING"
                            uiState.interruptedRun != null -> "RESUME READY"
                            uiState.folders.isEmpty() -> "NO FOLDERS"
                            else -> "OFFLINE READY"
                        },
                        dotColor = when {
                            uiState.isWorkerRunning -> AccentCyan
                            uiState.interruptedRun != null -> StatusWarning
                            uiState.folders.isEmpty() -> TextSecondary
                            else -> StatusVerified
                        }
                    )
                }
            }

            // Primary Action Bar: Organize All + Add Folder
            item {
                BentoCard(isHighlighted = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SHA-256 Verified Move Pipeline",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Dry-run preview before moving • Never deletes before verification",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = StatusVerified,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryActionButton(
                            text = "Organize All",
                            onClick = onOrganizeAllClick,
                            enabled = uiState.folders.isNotEmpty() && !uiState.isWorkerRunning,
                            icon = Icons.Outlined.PlayArrow,
                            modifier = Modifier.weight(1.3f),
                            testTag = "organize_all_button"
                        )
                        SecondaryConsoleButton(
                            text = "Add Folder",
                            onClick = onAddFolderClick,
                            icon = Icons.Outlined.Add,
                            modifier = Modifier.weight(1f),
                            testTag = "add_folder_button"
                        )
                    }
                }
            }

            // Output Destination Configuration Bento Card
            item {
                val dest = uiState.destinationConfig
                val destBorder = when {
                    dest.isCustom && !dest.isAccessible -> StatusError
                    dest.isCustom -> StatusVerified.copy(alpha = 0.5f)
                    else -> SurfaceBorder
                }
                BentoCard(
                    accentBorder = destBorder,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DriveFileMove,
                                contentDescription = null,
                                tint = if (dest.isCustom) StatusVerified else AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "OUTPUT DESTINATION",
                                style = LabelMonoCapsStyle,
                                color = TextSecondary
                            )
                        }
                        StatusBadge(
                            label = when {
                                dest.isCustom && !dest.isAccessible -> "PERMISSION LOST"
                                dest.isCustom -> "CUSTOM + 7 SUBFOLDERS"
                                else -> "DEFAULT (PER SOURCE)"
                            },
                            dotColor = when {
                                dest.isCustom && !dest.isAccessible -> StatusError
                                dest.isCustom -> StatusVerified
                                else -> AccentCyan
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (dest.isCustom) "${dest.displayName} / Organized" else "Inside each source folder / Organized",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (dest.isCustom) {
                            "Folder Organized & 7 kategori (Images, Videos, Documents, Audio, Archives, Applications, Others) sudah otomatis dibuat."
                        } else {
                            "Pilih folder tujuan bila ingin menyimpan hasil filter di lokasi khusus; struktur subfolder dibuat otomatis."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SecondaryConsoleButton(
                            text = if (dest.isCustom) "Change Destination" else "Choose Destination",
                            onClick = onChooseDestinationClick,
                            enabled = !uiState.isWorkerRunning,
                            icon = Icons.Outlined.Folder,
                            modifier = Modifier.weight(1.3f),
                            testTag = "choose_destination_button"
                        )
                        if (dest.isCustom) {
                            SecondaryConsoleButton(
                                text = "Use Default",
                                onClick = onResetDestinationClick,
                                enabled = !uiState.isWorkerRunning,
                                modifier = Modifier.weight(1f),
                                testTag = "reset_destination_button"
                            )
                        }
                    }
                }
            }

            // Interrupted Job Crash Recovery Banner
            item {
                AnimatedVisibility(visible = uiState.interruptedRun != null) {
                    uiState.interruptedRun?.let { stale ->
                        BentoCard(
                            accentBorder = StatusWarning,
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = StatusWarning,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "INTERRUPTED JOB DETECTED",
                                        style = LabelMonoCapsStyle,
                                        color = StatusWarning
                                    )
                                    Text(
                                        text = "Resume safely from Room state without duplicating already verified files.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            PrimaryActionButton(
                                text = "Resume Interrupted Run",
                                onClick = onResumeInterruptedClick,
                                icon = Icons.Outlined.Refresh,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "resume_run_button"
                            )
                        }
                    }
                }
            }

            // Live Progress Telemetry Card
            item {
                AnimatedVisibility(visible = uiState.isWorkerRunning) {
                    val run = uiState.activeRun
                    val total = run?.totalDiscovered ?: 0
                    val processed = run?.processedCount ?: 0
                    BentoCard(
                        accentBorder = AccentCyan,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CyanTelemetryChip(text = run?.currentStageLabel?.uppercase() ?: "BACKGROUND ACTIVE")
                            Text(
                                text = if (total > 0) "$processed / $total" else "SCANNING",
                                style = LabelCodeStyle,
                                color = AccentCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Background service active • Safe to switch apps",
                            style = LabelMonoCapsStyle,
                            color = StatusVerified
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = run?.currentFileName ?: "Preparing SAF tree scan...",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (run != null && total > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Moved: ${run.movedCount} • Dups: ${run.duplicateCount} • Skipped: ${run.skippedCount} • Failed: ${run.failedCount}",
                                style = LabelCodeStyle,
                                color = TextSecondary
                            )
                        }
                        if (run != null && run.currentFileTotalBytes > 0L) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "File: ${FormatUtils.formatBytes(run.currentFileBytesProcessed)} / ${FormatUtils.formatBytes(run.currentFileTotalBytes)}",
                                style = LabelCodeStyle,
                                color = StatusVerified
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (total > 0) {
                            LinearProgressIndicator(
                                progress = { (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                                color = AccentCyan,
                                trackColor = SurfaceRaised,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        } else {
                            LinearProgressIndicator(
                                color = AccentCyan,
                                trackColor = SurfaceRaised,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SecondaryConsoleButton(
                                text = "Live Details",
                                onClick = onOpenLiveProgressClick,
                                icon = Icons.Outlined.Visibility,
                                tint = AccentCyan,
                                borderColor = AccentCyan.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f),
                                testTag = "open_live_progress_button"
                            )
                            SecondaryConsoleButton(
                                text = "Cancel Safely",
                                onClick = onCancelActiveWorkClick,
                                icon = Icons.Outlined.Cancel,
                                tint = StatusError,
                                borderColor = StatusError,
                                modifier = Modifier.weight(1f),
                                testTag = "cancel_active_run_button"
                            )
                        }
                    }
                }
            }

            // Statistics Bento Grid
            item {
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoStatTile(
                            label = "Source Roots",
                            value = uiState.folders.size.toString(),
                            subtitle = "${uiState.folders.count { it.isAccessible }} accessible",
                            accentColor = AccentCyan,
                            icon = Icons.Outlined.Folder,
                            modifier = Modifier.weight(1f)
                        )
                        BentoStatTile(
                            label = "Verified Moved",
                            value = uiState.stats.totalVerifiedMoved.toString(),
                            subtitle = FormatUtils.formatBytes(uiState.stats.totalBytesMoved),
                            accentColor = StatusVerified,
                            icon = Icons.Outlined.CheckCircle,
                            modifier = Modifier.weight(1f)
                        )
                        BentoStatTile(
                            label = "Duplicates",
                            value = uiState.stats.totalDuplicatesSkipped.toString(),
                            subtitle = "SHA-256 matched",
                            accentColor = StatusWarning,
                            icon = Icons.Outlined.ContentCopy,
                            modifier = Modifier.weight(1f)
                        )
                        BentoStatTile(
                            label = "Failed Files",
                            value = uiState.stats.totalFailedActive.toString(),
                            subtitle = "Tap for report",
                            accentColor = if (uiState.stats.totalFailedActive > 0) StatusError else TextSecondary,
                            icon = Icons.Outlined.ErrorOutline,
                            onClick = onOpenFailedReportClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BentoStatTile(
                                label = "Source Roots",
                                value = uiState.folders.size.toString(),
                                subtitle = "${uiState.folders.count { it.isAccessible }} accessible",
                                accentColor = AccentCyan,
                                icon = Icons.Outlined.Folder,
                                modifier = Modifier.weight(1f)
                            )
                            BentoStatTile(
                                label = "Verified Moved",
                                value = uiState.stats.totalVerifiedMoved.toString(),
                                subtitle = FormatUtils.formatBytes(uiState.stats.totalBytesMoved),
                                accentColor = StatusVerified,
                                icon = Icons.Outlined.CheckCircle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BentoStatTile(
                                label = "Duplicates",
                                value = uiState.stats.totalDuplicatesSkipped.toString(),
                                subtitle = "SHA-256 matched",
                                accentColor = StatusWarning,
                                icon = Icons.Outlined.ContentCopy,
                                modifier = Modifier.weight(1f)
                            )
                            BentoStatTile(
                                label = "Failed Files",
                                value = uiState.stats.totalFailedActive.toString(),
                                subtitle = "Tap for report",
                                accentColor = if (uiState.stats.totalFailedActive > 0) StatusError else TextSecondary,
                                icon = Icons.Outlined.ErrorOutline,
                                onClick = onOpenFailedReportClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Section Header: Selected Source Folders
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECTED SOURCE FOLDERS",
                        style = LabelMonoCapsStyle,
                        color = TextSecondary
                    )
                    Text(
                        text = "${uiState.folders.size} CONFIGURED",
                        style = LabelMonoCapsStyle,
                        color = TextSecondary
                    )
                }
            }

            if (uiState.folders.isEmpty()) {
                item {
                    BentoCard(contentPadding = PaddingValues(24.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CreateNewFolder,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No source folders selected",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Grant access to one or more folders via Android Storage Access Framework. Organizer classifies files into Organized/Images, Videos, Documents, Audio, Archives, Applications, and Others.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            PrimaryActionButton(
                                text = "Select Source Folder",
                                onClick = onAddFolderClick,
                                icon = Icons.Outlined.Add,
                                testTag = "empty_add_folder_button"
                            )
                        }
                    }
                }
            } else {
                items(uiState.folders, key = { it.treeUri }) { folder ->
                    val exclusions = uiState.exclusionsByFolder[folder.treeUri].orEmpty()
                    SourceFolderBentoCard(
                        folder = folder,
                        exclusions = exclusions,
                        isBusy = uiState.isWorkerRunning,
                        onOrganizeClick = { onOrganizeFolderClick(folder) },
                        onExclusionsClick = { onManageExclusionsClick(folder) },
                        onRemoveClick = { onRemoveFolderClick(folder) },
                        onRegrantClick = onRegrantFolderClick
                    )
                }
            }

            // Latest Run Summary Card
            val latestCompleted = uiState.recentRuns.firstOrNull { !it.status.isActive }
            if (latestCompleted != null) {
                item {
                    LatestRunResultCard(run = latestCompleted)
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SourceFolderBentoCard(
    folder: SourceFolderEntity,
    exclusions: List<ExcludedSubfolderEntity>,
    isBusy: Boolean,
    onOrganizeClick: () -> Unit,
    onExclusionsClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onRegrantClick: () -> Unit
) {
    BentoCard(
        accentBorder = if (folder.isAccessible) SurfaceBorder else StatusError.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = if (folder.isAccessible) AccentCyan else StatusError,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("Moved: ${folder.totalMovedCount}")
                            if (exclusions.isNotEmpty()) append(" • Excluded: ${exclusions.size}")
                            folder.lastOrganizedAt?.let { ts ->
                                val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.US)
                                append(" • Last: ${fmt.format(Date(ts))}")
                            }
                        },
                        style = LabelCodeStyle,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove ${folder.displayName}",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(
                label = if (folder.isAccessible) "SAF: GRANTED" else "PERMISSION LOST",
                dotColor = if (folder.isAccessible) StatusVerified else StatusError
            )
            if (exclusions.isNotEmpty()) {
                Text(
                    text = "${exclusions.size} SUBFOLDER(S) EXCLUDED",
                    style = LabelMonoCapsStyle,
                    color = StatusWarning
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SecondaryConsoleButton(
                text = "Exclusions",
                onClick = onExclusionsClick,
                enabled = folder.isAccessible && !isBusy,
                icon = Icons.Outlined.FilterList,
                modifier = Modifier.weight(1f)
            )
            if (folder.isAccessible) {
                PrimaryActionButton(
                    text = "Organize",
                    onClick = onOrganizeClick,
                    enabled = !isBusy,
                    icon = Icons.Outlined.PlayArrow,
                    modifier = Modifier.weight(1f),
                    testTag = "organize_folder_button"
                )
            } else {
                SecondaryConsoleButton(
                    text = "Re-grant",
                    onClick = onRegrantClick,
                    icon = Icons.Outlined.Refresh,
                    tint = StatusError,
                    borderColor = StatusError,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LatestRunResultCard(run: OrganizationRunEntity) {
    val dotColor = when (run.status) {
        RunStatus.COMPLETED -> StatusVerified
        RunStatus.COMPLETED_WITH_ERRORS,
        RunStatus.HALTED_STORAGE,
        RunStatus.HALTED_PERMISSION -> StatusError
        else -> StatusWarning
    }
    BentoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LAST RUN RESULT",
                style = LabelMonoCapsStyle,
                color = TextSecondary
            )
            StatusBadge(label = run.status.label, dotColor = dotColor)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Moved: ${run.movedCount} • Duplicates: ${run.duplicateCount} • Skipped: ${run.skippedCount} • Failed: ${run.failedCount}",
            style = LabelCodeStyle,
            color = TextPrimary
        )
        run.errorSummary?.let { summary ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = StatusWarning
            )
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import com.example.data.local.FileOperationEntity
import com.example.data.local.OrganizationRunEntity
import com.example.domain.DryRunReport
import com.example.domain.FormatUtils
import com.example.domain.OperationStage
import com.example.domain.SubfolderNode
import com.example.ui.DryRunState
import com.example.ui.ExclusionsSheetState
import com.example.ui.components.BentoCard
import com.example.ui.components.CyanTelemetryChip
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.SecondaryConsoleButton
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.LabelCodeStyle
import com.example.ui.theme.LabelMonoCapsStyle
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusVerified
import com.example.ui.theme.StatusVerifiedBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceRaised
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DryRunPreviewDialog(
    state: DryRunState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onChangeDestination: () -> Unit = {}
) {
    if (state is DryRunState.Hidden) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(8.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceBorder)
        ) {
            when (state) {
                is DryRunState.Scanning -> {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StatusBadge(label = "DRY-RUN // READ-ONLY", dotColor = AccentCyan)
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = state.targetFolderName?.let { "Scanning $it" } ?: "Scanning All Source Folders",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Checking extensions, symlinks, exclusions, storage headroom, and SHA-256 duplicates. No files are being modified.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        SecondaryConsoleButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is DryRunState.Error -> {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusBadge(label = "PREVIEW ERROR", dotColor = StatusError)
                        Text(
                            text = "Could not complete dry-run scan",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        SecondaryConsoleButton(
                            text = "Close",
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is DryRunState.Ready -> {
                    DryRunReadyContent(
                        report = state.report,
                        onDismiss = onDismiss,
                        onConfirm = onConfirm,
                        onChangeDestination = onChangeDestination
                    )
                }

                DryRunState.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun DryRunReadyContent(
    report: DryRunReport,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onChangeDestination: () -> Unit
) {
    val scrollState = rememberScrollState()
    val hasInaccessible = report.folderSummaries.any { !it.isAccessible }
    val canProceed = report.totalFilesToMove > 0 && report.hasSufficientStorage

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(label = "DRY-RUN PREVIEW", dotColor = AccentCyan)
            Text(
                text = if (report.targetFolderUri == null) "${report.folderSummaries.size} FOLDERS" else "SINGLE FOLDER",
                style = LabelMonoCapsStyle,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Confirm Safe Organization",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Text(
            text = "No folders or source files have been changed yet.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DryRunMetricPill(
                    label = "TO MOVE",
                    value = report.totalFilesToMove.toString(),
                    accent = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
                DryRunMetricPill(
                    label = "DUPLICATES",
                    value = report.totalDuplicates.toString(),
                    accent = StatusWarning,
                    modifier = Modifier.weight(1f)
                )
                DryRunMetricPill(
                    label = "SKIPPED",
                    value = (report.totalSkippedSymlinks + report.totalSkippedOther).toString(),
                    accent = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Destination Output Target
            BentoCard(
                contentPadding = PaddingValues(12.dp),
                accentBorder = if (report.destinationAccessible) SurfaceBorder else StatusError
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
                            tint = if (report.destinationAccessible) AccentCyan else StatusError,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "DESTINATION",
                            style = LabelMonoCapsStyle,
                            color = TextSecondary
                        )
                    }
                    TextButton(
                        onClick = onChangeDestination,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "CHANGE",
                            style = LabelMonoCapsStyle,
                            color = AccentCyan
                        )
                    }
                }
                Text(
                    text = report.destinationLabel,
                    style = LabelCodeStyle,
                    color = if (report.destinationAccessible) TextPrimary else StatusError
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (report.destinationAccessible) {
                        "Subfolders: Images, Videos, Documents, Audio, Archives, Applications, Others"
                    } else {
                        "Selected destination lost SAF access. Tap CHANGE to pick a folder again."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (report.destinationAccessible) TextSecondary else StatusError
                )
            }

            // Storage & Headroom Check
            BentoCard(
                contentPadding = PaddingValues(12.dp),
                accentBorder = if (report.hasSufficientStorage) SurfaceBorder else StatusError
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
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = null,
                            tint = if (report.hasSufficientStorage) StatusVerified else StatusError,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "STORAGE CHECK",
                            style = LabelMonoCapsStyle,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = if (report.hasSufficientStorage) "SUFFICIENT" else "INSUFFICIENT",
                        style = LabelMonoCapsStyle,
                        color = if (report.hasSufficientStorage) StatusVerified else StatusError
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Volume to verify & move: ${FormatUtils.formatBytes(report.totalBytesToTransfer)}" +
                        (report.minAvailableBytes?.let { " • Available: ${FormatUtils.formatBytes(it)}" } ?: ""),
                    style = LabelCodeStyle,
                    color = TextPrimary
                )
            }

            if (hasInaccessible) {
                BentoCard(
                    contentPadding = PaddingValues(12.dp),
                    accentBorder = StatusError
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = StatusError,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Some selected folders lost SAF permissions and will be skipped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusError
                        )
                    }
                }
            }

            // Category Breakdown
            BentoCard(contentPadding = PaddingValues(12.dp)) {
                Text(
                    text = "CATEGORY BREAKDOWN",
                    style = LabelMonoCapsStyle,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                val maxFiles = report.categoryBreakdown.maxOfOrNull { it.fileCount }?.coerceAtLeast(1) ?: 1
                report.categoryBreakdown.forEachIndexed { idx, bucket ->
                    if (idx > 0) {
                        HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bucket.category.folderName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (bucket.fileCount > 0) TextPrimary else TextMuted
                        )
                        Text(
                            text = "${bucket.fileCount} • ${FormatUtils.formatBytes(bucket.totalBytes)}",
                            style = LabelCodeStyle,
                            color = if (bucket.fileCount > 0) AccentCyan else TextMuted
                        )
                    }
                    if (bucket.fileCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { bucket.fileCount.toFloat() / maxFiles.toFloat() },
                            color = AccentCyan,
                            trackColor = SurfaceRaised,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            if (report.totalFilesToMove == 0) {
                BentoCard(
                    contentPadding = PaddingValues(12.dp),
                    accentBorder = StatusWarning.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = if (report.totalDuplicates > 0) {
                            "All scanned files already exist in Organized with identical SHA-256 hashes. No moves needed."
                        } else {
                            "No movable files found outside Organized and excluded subfolders."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusWarning
                    )
                }
            }

            if (report.totalSkippedSymlinks > 0) {
                Text(
                    text = "Blocked ${report.totalSkippedSymlinks} symbolic link(s) to guarantee safe traversal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarning
                )
            }

            // Safety Contract Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(StatusVerifiedBg)
                    .border(1.dp, StatusVerified.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = StatusVerified,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Source files are only deleted after destination SHA-256 verification succeeds. Failed items stay untouched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryConsoleButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                testTag = "dry_run_cancel_button"
            )
            PrimaryActionButton(
                text = if (report.totalFilesToMove == 0) "Nothing to Move" else "Confirm & Organize",
                onClick = onConfirm,
                enabled = canProceed,
                icon = Icons.Outlined.CheckCircle,
                modifier = Modifier.weight(1.4f),
                testTag = "dry_run_confirm_button"
            )
        }
    }
}

@Composable
private fun DryRunMetricPill(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceRaised)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Text(text = label, style = LabelMonoCapsStyle, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SubfolderExclusionsDialog(
    state: ExclusionsSheetState,
    isBusy: Boolean = false,
    onToggleNode: (SubfolderNode) -> Unit,
    onRefresh: () -> Unit = {},
    onOrganizeFolder: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (state is ExclusionsSheetState.Hidden) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .testTag("exclusions_dialog"),
            shape = RoundedCornerShape(8.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .padding(20.dp)
            ) {
                val folderName = when (state) {
                    is ExclusionsSheetState.Loading -> state.folder.displayName
                    is ExclusionsSheetState.Ready -> state.folder.displayName
                    ExclusionsSheetState.Hidden -> ""
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(label = "SUBFOLDER EXCLUSIONS", dotColor = AccentCyan)
                    IconButton(
                        onClick = onRefresh,
                        enabled = state is ExclusionsSheetState.Ready,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("refresh_exclusions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Rescan subfolders",
                            tint = AccentCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Unchecked subfolders are ACTIVE and scanned recursively. Check a subfolder to exclude it. Organized folders and symlinks are always skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                when (state) {
                    is ExclusionsSheetState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = AccentCyan, strokeWidth = 2.dp)
                                Text(
                                    text = "Reading subfolders...",
                                    style = LabelCodeStyle,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    is ExclusionsSheetState.Ready -> {
                        val activeCount = state.nodes.count {
                            !it.isOrganizedFolder && !it.isSymlink && !it.isExcluded && !it.isExcludedByParent
                        }
                        val excludedCount = state.nodes.size - activeCount
                        if (state.nodes.isNotEmpty()) {
                            CyanTelemetryChip(
                                text = "$activeCount ACTIVE • $excludedCount EXCLUDED",
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        if (state.nodes.isEmpty()) {
                            BentoCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOff,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                    Text(
                                        text = "No subfolders found inside this directory. Files in the root folder will still be organized.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.nodes, key = { it.relativePath }) { node ->
                                    val locked = node.isOrganizedFolder || node.isSymlink || node.isExcludedByParent
                                    val isChecked = node.isExcluded || node.isExcludedByParent || node.isSymlink
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceRaised)
                                            .border(
                                                width = 1.dp,
                                                color = if (isChecked) SurfaceBorder else AccentCyan.copy(alpha = 0.35f),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable(enabled = !locked) { onToggleNode(node) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                            .testTag("exclusion_row_${node.relativePath}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    node.isOrganizedFolder -> Icons.Outlined.Lock
                                                    node.isSymlink -> Icons.Outlined.LinkOff
                                                    else -> Icons.Outlined.Folder
                                                },
                                                contentDescription = null,
                                                tint = when {
                                                    node.isOrganizedFolder -> StatusVerified
                                                    node.isSymlink || node.isExcluded || node.isExcludedByParent -> StatusWarning
                                                    else -> AccentCyan
                                                },
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = node.relativePath,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val (statusText, statusColor) = when {
                                                    node.isOrganizedFolder ->
                                                        "ALWAYS EXCLUDED (DESTINATION)" to StatusVerified
                                                    node.isSymlink ->
                                                        "SYMLINK • NEVER FOLLOWED" to StatusWarning
                                                    node.isExcluded ->
                                                        "EXCLUDED • ${node.directFileCount} file(s) skipped" to StatusWarning
                                                    node.isExcludedByParent ->
                                                        "EXCLUDED VIA PARENT • ${node.directFileCount} file(s) skipped" to StatusWarning
                                                    else ->
                                                        "ACTIVE • ${node.directFileCount} file(s) included" to AccentCyan
                                                }
                                                Text(
                                                    text = statusText,
                                                    style = LabelMonoCapsStyle,
                                                    color = statusColor
                                                )
                                            }
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = if (locked) null else { _ -> onToggleNode(node) },
                                            enabled = !locked,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = StatusWarning,
                                                uncheckedColor = AccentCyan,
                                                checkmarkColor = SurfaceCard
                                            ),
                                            modifier = Modifier.testTag("exclusion_checkbox_${node.relativePath}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ExclusionsSheetState.Hidden -> Unit
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryConsoleButton(
                        text = "Done",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        testTag = "exclusions_done_button"
                    )
                    if (onOrganizeFolder != null) {
                        PrimaryActionButton(
                            text = "Organize",
                            onClick = onOrganizeFolder,
                            enabled = !isBusy && state is ExclusionsSheetState.Ready,
                            icon = Icons.Outlined.PlayArrow,
                            modifier = Modifier.weight(1.2f),
                            testTag = "exclusions_organize_button"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveTransferProgressDialog(
    visible: Boolean,
    run: OrganizationRunEntity?,
    operations: List<FileOperationEntity>,
    isWorkerRunning: Boolean,
    hasNotificationPermission: Boolean = true,
    onRequestNotifications: () -> Unit = {},
    onCancelRun: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val isActive = isWorkerRunning || (run?.status?.isActive == true)
    val total = run?.totalDiscovered ?: 0
    val processed = run?.processedCount ?: 0
    val overallFraction = if (total > 0) (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val percentText = if (total > 0) "${(overallFraction * 100).toInt()}%" else "..."

    val fileBytesProcessed = run?.currentFileBytesProcessed ?: 0L
    val fileTotalBytes = run?.currentFileTotalBytes ?: 0L
    val fileFraction = if (fileTotalBytes > 0L) {
        (fileBytesProcessed.toFloat() / fileTotalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val badgeColor = when {
        isActive -> AccentCyan
        run?.status == com.example.domain.RunStatus.COMPLETED -> StatusVerified
        run?.status == com.example.domain.RunStatus.COMPLETED_WITH_ERRORS ||
            run?.status == com.example.domain.RunStatus.HALTED_STORAGE ||
            run?.status == com.example.domain.RunStatus.HALTED_PERMISSION -> StatusError
        else -> StatusWarning
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .testTag("live_transfer_dialog"),
            shape = RoundedCornerShape(8.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, if (isActive) AccentCyan.copy(alpha = 0.65f) else SurfaceBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(
                        label = if (isActive) "LIVE FILE TRANSFER" else (run?.status?.label ?: "RUN FINISHED"),
                        dotColor = badgeColor
                    )
                    Text(
                        text = if (total > 0) "$processed / $total ($percentText)" else "INITIALIZING",
                        style = LabelCodeStyle,
                        color = AccentCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isActive) "Organizing & Verifying SHA-256" else "Transfer Summary",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Destination: ${run?.destinationFolderName ?: "Source / Organized"}",
                        style = LabelCodeStyle,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActive) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BACKGROUND SERVICE ACTIVE • SAFE TO SWITCH APPS",
                                style = LabelMonoCapsStyle,
                                color = StatusVerified
                            )
                            if (!hasNotificationPermission) {
                                TextButton(
                                    onClick = onRequestNotifications,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.testTag("enable_bg_notifications_button")
                                ) {
                                    Text(
                                        text = "NOTIFY",
                                        style = LabelMonoCapsStyle,
                                        color = AccentCyan
                                    )
                                }
                            }
                        }
                    }
                }

                // Overall Progress Bar
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { overallFraction },
                            color = if (isActive) AccentCyan else badgeColor,
                            trackColor = SurfaceRaised,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            color = AccentCyan,
                            trackColor = SurfaceRaised,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }

                // Active File Transfer Card
                if (isActive) {
                    BentoCard(
                        isHighlighted = true,
                        accentBorder = AccentCyan.copy(alpha = 0.5f),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CyanTelemetryChip(text = (run?.currentStageLabel ?: "SCANNING").uppercase())
                            if (fileTotalBytes > 0L) {
                                Text(
                                    text = "${FormatUtils.formatBytes(fileBytesProcessed)} / ${FormatUtils.formatBytes(fileTotalBytes)}",
                                    style = LabelCodeStyle,
                                    color = AccentCyan
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = run?.currentFileName ?: "Scanning selected source folders...",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (fileTotalBytes > 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { fileFraction },
                                color = StatusVerified,
                                trackColor = SurfaceCard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                } else {
                    run?.errorSummary?.let { summary ->
                        BentoCard(
                            contentPadding = PaddingValues(10.dp),
                            accentBorder = StatusWarning.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusWarning
                            )
                        }
                    }
                }

                // Live Outcome Counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DryRunMetricPill(
                        label = "MOVED",
                        value = (run?.movedCount ?: 0).toString(),
                        accent = StatusVerified,
                        modifier = Modifier.weight(1f)
                    )
                    DryRunMetricPill(
                        label = "DUPS",
                        value = (run?.duplicateCount ?: 0).toString(),
                        accent = StatusWarning,
                        modifier = Modifier.weight(1f)
                    )
                    DryRunMetricPill(
                        label = "SKIP",
                        value = (run?.skippedCount ?: 0).toString(),
                        accent = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    DryRunMetricPill(
                        label = "FAIL",
                        value = (run?.failedCount ?: 0).toString(),
                        accent = if ((run?.failedCount ?: 0) > 0) StatusError else TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Live File Activity Feed
                Text(
                    text = "FILE TRANSFER LOG (${operations.size})",
                    style = LabelMonoCapsStyle,
                    color = TextSecondary
                )

                if (operations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceRaised)
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = AccentCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Discovering files & preparing Organized folders...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(min = 120.dp, max = 210.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceRaised)
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(operations, key = { it.id }) { op ->
                            LiveOperationLogRow(op = op)
                        }
                    }
                }

                // Action Row
                if (isActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryConsoleButton(
                            text = "Run in Background",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1.1f),
                            testTag = "live_progress_minimize_button"
                        )
                        SecondaryConsoleButton(
                            text = "Cancel Safely",
                            onClick = onCancelRun,
                            icon = Icons.Outlined.Cancel,
                            tint = StatusError,
                            borderColor = StatusError,
                            modifier = Modifier.weight(1f),
                            testTag = "live_progress_cancel_button"
                        )
                    }
                } else {
                    PrimaryActionButton(
                        text = "Done",
                        onClick = onDismiss,
                        icon = Icons.Outlined.CheckCircle,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "live_progress_done_button"
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveOperationLogRow(op: FileOperationEntity) {
    val (icon, tint) = when (op.stage) {
        OperationStage.VERIFIED_MOVED -> Icons.Outlined.CheckCircle to StatusVerified
        OperationStage.DUPLICATE_SKIPPED -> Icons.Outlined.ContentCopy to StatusWarning
        OperationStage.FAILED -> Icons.Outlined.ErrorOutline to StatusError
        OperationStage.SKIPPED_SYMLINK,
        OperationStage.SKIPPED_VIRTUAL,
        OperationStage.SKIPPED_EXCLUDED -> Icons.Outlined.SkipNext to TextMuted
        else -> Icons.Outlined.Sync to AccentCyan
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceCard)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = op.sourceName,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (op.stage) {
                    OperationStage.VERIFIED_MOVED -> "-> ${op.category.folderName}/${op.destinationName ?: op.sourceName} • ${FormatUtils.formatBytes(op.byteSize)}"
                    OperationStage.DUPLICATE_SKIPPED -> "Duplicate in ${op.category.folderName}/${op.destinationName ?: op.sourceName}"
                    OperationStage.FAILED -> op.errorMessage ?: "Failed (source untouched)"
                    else -> "${op.stage.label} -> ${op.category.folderName}"
                },
                style = LabelCodeStyle,
                color = if (op.stage == OperationStage.FAILED) StatusError else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

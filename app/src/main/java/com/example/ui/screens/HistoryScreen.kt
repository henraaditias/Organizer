package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.FileOperationEntity
import com.example.data.local.OrganizationRunEntity
import com.example.domain.FormatUtils
import com.example.domain.OperationStage
import com.example.domain.RunStatus
import com.example.ui.HistoryFilter
import com.example.ui.components.BentoCard
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
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceRaised
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    recentRuns: List<OrganizationRunEntity>,
    filteredOperations: List<FileOperationEntity>,
    failedOperationsCount: Int,
    activeFilter: HistoryFilter,
    isWorkerRunning: Boolean,
    onFilterChange: (HistoryFilter) -> Unit,
    onRetryAllFailed: () -> Unit,
    onRetrySingle: (Long) -> Unit,
    onExportFailedReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "AUDIT LOGS // VERIFICATION LEDGER",
                    style = LabelMonoCapsStyle,
                    color = AccentCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Operation History & Failed Report",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }
        }

        // Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HistoryFilter.entries) { filter ->
                    val selected = filter == activeFilter
                    val label = if (filter == HistoryFilter.FAILED && failedOperationsCount > 0) {
                        "${filter.label} ($failedOperationsCount)"
                    } else {
                        filter.label
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterChange(filter) },
                        label = {
                            Text(
                                text = label,
                                style = LabelMonoCapsStyle
                            )
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) AccentCyan else SurfaceBorder
                        ),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceCard,
                            labelColor = TextSecondary,
                            selectedContainerColor = SurfaceRaised,
                            selectedLabelColor = AccentCyan
                        )
                    )
                }
            }
        }

        // Failed-Files Report Action Card
        if (failedOperationsCount > 0 || activeFilter == HistoryFilter.FAILED) {
            item {
                BentoCard(
                    accentBorder = if (failedOperationsCount > 0) StatusError else SurfaceBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(
                            label = "FAILED FILES REPORT",
                            dotColor = if (failedOperationsCount > 0) StatusError else StatusVerified
                        )
                        Text(
                            text = "$failedOperationsCount PENDING",
                            style = LabelMonoCapsStyle,
                            color = if (failedOperationsCount > 0) StatusError else StatusVerified
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = StatusVerified,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (failedOperationsCount > 0) {
                                "Every failed file remains untouched in its original location. Review diagnostics and retry safely."
                            } else {
                                "No failed operations recorded. All processed files passed integrity checks."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    if (failedOperationsCount > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrimaryActionButton(
                                text = "Retry All ($failedOperationsCount)",
                                onClick = onRetryAllFailed,
                                enabled = !isWorkerRunning,
                                icon = Icons.Outlined.Refresh,
                                modifier = Modifier.weight(1.2f),
                                testTag = "retry_all_failed_button"
                            )
                            SecondaryConsoleButton(
                                text = "Export Report",
                                onClick = onExportFailedReport,
                                icon = Icons.Outlined.Download,
                                modifier = Modifier.weight(1f),
                                testTag = "export_failed_report_button"
                            )
                        }
                    }
                }
            }
        }

        // Recent Runs Summary Strip
        if (recentRuns.isNotEmpty() && activeFilter == HistoryFilter.ALL) {
            item {
                Text(
                    text = "RECENT RUN SESSIONS",
                    style = LabelMonoCapsStyle,
                    color = TextSecondary
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentRuns.take(10), key = { it.runId }) { run ->
                        RunSessionMiniCard(run = run)
                    }
                }
            }
        }

        item {
            Text(
                text = "FILE OPERATION LOGS (${filteredOperations.size})",
                style = LabelMonoCapsStyle,
                color = TextSecondary
            )
        }

        if (filteredOperations.isEmpty()) {
            item {
                BentoCard(contentPadding = PaddingValues(24.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No matching operation logs",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Run an organization pass from the Dashboard to inspect SHA-256 verified file transfers here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(filteredOperations, key = { it.id }) { op ->
                FileOperationLogCard(
                    op = op,
                    isWorkerRunning = isWorkerRunning,
                    onRetryClick = { onRetrySingle(op.id) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun RunSessionMiniCard(run: OrganizationRunEntity) {
    val fmt = SimpleDateFormat("MMM d • HH:mm", Locale.US)
    val dot = when (run.status) {
        RunStatus.COMPLETED -> StatusVerified
        RunStatus.RUNNING, RunStatus.PLANNING -> AccentCyan
        RunStatus.COMPLETED_WITH_ERRORS,
        RunStatus.HALTED_STORAGE,
        RunStatus.HALTED_PERMISSION -> StatusError
        RunStatus.CANCELLED -> StatusWarning
    }
    BentoCard(
        modifier = Modifier.width(235.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(label = run.triggerType.label, dotColor = dot)
            Text(
                text = fmt.format(Date(run.startedAt)),
                style = LabelMonoCapsStyle,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = run.targetFolderName ?: "All Source Folders",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Moved ${run.movedCount} • Dup ${run.duplicateCount} • Fail ${run.failedCount}",
            style = LabelCodeStyle,
            color = TextSecondary
        )
    }
}

@Composable
private fun FileOperationLogCard(
    op: FileOperationEntity,
    isWorkerRunning: Boolean,
    onRetryClick: () -> Unit
) {
    val fmt = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)
    val badgeColor = when (op.stage) {
        OperationStage.VERIFIED_MOVED -> StatusVerified
        OperationStage.DUPLICATE_SKIPPED,
        OperationStage.SKIPPED_SYMLINK,
        OperationStage.SKIPPED_VIRTUAL,
        OperationStage.SKIPPED_EXCLUDED -> StatusWarning
        OperationStage.FAILED -> StatusError
        else -> AccentCyan
    }

    BentoCard(
        accentBorder = if (op.stage == OperationStage.FAILED) StatusError.copy(alpha = 0.55f) else SurfaceBorder,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(label = op.stage.label, dotColor = badgeColor)
            Text(
                text = fmt.format(Date(op.updatedAt)),
                style = LabelMonoCapsStyle,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = op.sourceName,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${op.folderName}/${op.sourceRelativePath}",
            style = LabelCodeStyle,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${op.category.folderName} • ${FormatUtils.formatBytes(op.byteSize)}",
                style = LabelMonoCapsStyle,
                color = AccentCyan
            )
            op.sourceSha256?.let { sha ->
                Text(
                    text = "SHA:${sha.take(10)}",
                    style = LabelMonoCapsStyle,
                    color = TextSecondary
                )
            }
        }

        if (op.destinationName != null && op.stage == OperationStage.VERIFIED_MOVED) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "-> Organized/${op.category.folderName}/${op.destinationName}",
                style = LabelCodeStyle,
                color = StatusVerified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (op.errorMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = op.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (op.stage == OperationStage.FAILED) StatusError else StatusWarning
            )
        }

        if (op.stage == OperationStage.FAILED) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    label = if (op.sourceUntouched) "SOURCE: UNTOUCHED" else "CHECK SOURCE",
                    dotColor = StatusVerified
                )
                SecondaryConsoleButton(
                    text = "Retry",
                    onClick = onRetryClick,
                    enabled = !isWorkerRunning,
                    icon = Icons.Outlined.Refresh,
                    tint = AccentCyan
                )
            }
        }
    }
}

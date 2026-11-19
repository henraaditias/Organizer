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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.FileCategory
import com.example.domain.ScheduleConfig
import com.example.ui.components.BentoCard
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CanvasBg
import com.example.ui.theme.LabelCodeStyle
import com.example.ui.theme.LabelMonoCapsStyle
import com.example.ui.theme.StatusVerified
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceRaised
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ScheduleScreen(
    config: ScheduleConfig,
    isWorkerRunning: Boolean = false,
    hasFolders: Boolean = false,
    onUpdateConfig: (ScheduleConfig) -> Unit,
    onRunAutoOrganizeNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val intervals = listOf(
        15 to "15 Min",
        60 to "1 Hour",
        720 to "12 Hours",
        1440 to "Daily (24h)",
        10080 to "Weekly (7d)"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "WORKMANAGER // AUTOMATION & RULES",
                    style = LabelMonoCapsStyle,
                    color = AccentCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Background Schedule & Categories",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }
        }

        // Periodic Schedule Toggle Card
        item {
            BentoCard(isHighlighted = config.enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(
                        label = if (config.enabled) "SCHEDULE: ACTIVE" else "MANUAL MODE (DEFAULT)",
                        dotColor = if (config.enabled) AccentCyan else StatusVerified
                    )
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = if (config.enabled) AccentCyan else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Periodic Background Organization",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Disabled by default. When enabled, runs automatically under WorkManager constraints.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { enabled ->
                            onUpdateConfig(config.copy(enabled = enabled))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CanvasBg,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = SurfaceRaised,
                            uncheckedBorderColor = SurfaceBorder
                        ),
                        modifier = Modifier.testTag("schedule_enable_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "FREQUENCY",
                    style = LabelMonoCapsStyle,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(intervals) { (minutes, label) ->
                        val selected = config.intervalMinutes == minutes
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onUpdateConfig(
                                    config.copy(
                                        intervalMinutes = minutes,
                                        intervalHours = (minutes / 60).coerceAtLeast(1)
                                    )
                                )
                            },
                            enabled = config.enabled,
                            label = { Text(label, style = LabelMonoCapsStyle) },
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, if (selected) AccentCyan else SurfaceBorder),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceCard,
                                labelColor = TextSecondary,
                                selectedContainerColor = SurfaceRaised,
                                selectedLabelColor = AccentCyan
                            )
                        )
                    }
                }

                HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = "AUTOMATION & CONSTRAINTS",
                    style = LabelMonoCapsStyle,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                ConstraintToggleRow(
                    title = "Auto-Organize on App Launch",
                    subtitle = "Automatically verify and move new files whenever Organizer opens (disabled by default)",
                    checked = config.autoOrganizeOnLaunch,
                    enabled = true,
                    onCheckedChange = { onUpdateConfig(config.copy(autoOrganizeOnLaunch = it)) },
                    switchTestTag = "auto_launch_switch"
                )
                ConstraintToggleRow(
                    title = "Require Battery Not Low",
                    subtitle = "Pause background runs when battery is critically low",
                    checked = config.requireBatteryNotLow,
                    enabled = config.enabled,
                    onCheckedChange = { onUpdateConfig(config.copy(requireBatteryNotLow = it)) }
                )
                ConstraintToggleRow(
                    title = "Require Charging",
                    subtitle = "Defer background schedule until device is connected to power",
                    checked = config.requireCharging,
                    enabled = config.enabled,
                    onCheckedChange = { onUpdateConfig(config.copy(requireCharging = it)) }
                )
                ConstraintToggleRow(
                    title = "Require Device Idle",
                    subtitle = "Defer background schedule until screen is off and inactive",
                    checked = config.requireDeviceIdle,
                    enabled = config.enabled,
                    onCheckedChange = { onUpdateConfig(config.copy(requireDeviceIdle = it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))
                PrimaryActionButton(
                    text = if (isWorkerRunning) "Organization Running..." else "Run Auto-Organize Now",
                    onClick = onRunAutoOrganizeNow,
                    enabled = hasFolders && !isWorkerRunning,
                    icon = Icons.Outlined.PlayArrow,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "run_auto_organize_now_button"
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (!hasFolders) {
                        "Add at least one source folder on Dashboard first."
                    } else {
                        "Runs immediately across all configured source folders using SHA-256 verification and active subfolder exclusions."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Safety Guarantee Card
        item {
            BentoCard(accentBorder = StatusVerified.copy(alpha = 0.4f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = StatusVerified,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "IDENTICAL SAFETY VERIFICATION IN BACKGROUND",
                        style = LabelMonoCapsStyle,
                        color = StatusVerified
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Scheduled runs enforce the exact same non-destructive rules as manual runs: symbolic links are never followed, Organized folders and excluded subfolders are skipped, duplicates are detected via SHA-256, and original files are never deleted before full destination verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        item {
            Text(
                text = "EXTENSION CLASSIFICATION RULES",
                style = LabelMonoCapsStyle,
                color = TextSecondary
            )
        }

        items(FileCategory.entries) { category ->
            BentoCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Organized/${category.folderName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = if (category == FileCategory.OTHERS) "FALLBACK" else "${category.extensions.size} EXT",
                        style = LabelMonoCapsStyle,
                        color = AccentCyan
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (category == FileCategory.OTHERS) {
                        "All unmapped or extensionless files are safely routed here."
                    } else {
                        category.extensions.sorted().joinToString(", ") { ".$it" }
                    },
                    style = LabelCodeStyle,
                    color = TextSecondary
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun ConstraintToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) TextPrimary else TextSecondary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CanvasBg,
                checkedTrackColor = AccentCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceRaised,
                uncheckedBorderColor = SurfaceBorder
            ),
            modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier
        )
    }
}

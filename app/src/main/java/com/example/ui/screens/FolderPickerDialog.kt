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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.ui.FolderPickerState
import com.example.ui.FolderPickerTarget
import com.example.ui.components.BentoCard
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.SecondaryConsoleButton
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.LabelCodeStyle
import com.example.ui.theme.LabelMonoCapsStyle
import com.example.ui.theme.StatusVerified
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceRaised
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FolderPickerDialog(
    state: FolderPickerState,
    onRequestAllFilesAccess: () -> Unit,
    onSelectFolderUri: (String) -> Unit,
    onNavigateToPath: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenSystemSafPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.visible) return

    val isDestination = state.target == FolderPickerTarget.DESTINATION

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .testTag("folder_picker_dialog"),
            shape = RoundedCornerShape(8.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceBorder)
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
                        label = if (isDestination) "SELECT DESTINATION" else "ADD SOURCE FOLDER",
                        dotColor = if (state.hasAllFilesAccess) StatusVerified else AccentCyan
                    )
                    Text(
                        text = if (state.hasAllFilesAccess) "ALL FILES READY" else "SAF ONLY",
                        style = LabelMonoCapsStyle,
                        color = if (state.hasAllFilesAccess) StatusVerified else StatusWarning
                    )
                }

                Text(
                    text = if (isDestination) {
                        "Choose Output Location"
                    } else {
                        "Select Download, Documents, or Any Folder"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                if (!state.hasAllFilesAccess) {
                    BentoCard(
                        accentBorder = StatusWarning.copy(alpha = 0.7f),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Text(
                            text = "ANDROID 11+ STORAGE ACCESS",
                            style = LabelMonoCapsStyle,
                            color = StatusWarning
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Android's standard picker blocks selecting Download & Documents directly. Grant All Files Access once to pick Download, Documents, Pictures, Music, or any internal folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PrimaryActionButton(
                            text = "Grant All Files Access",
                            onClick = onRequestAllFilesAccess,
                            icon = Icons.Outlined.LockOpen,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "grant_all_files_button"
                        )
                    }
                } else {
                    if (state.quickFolders.isNotEmpty()) {
                        Text(
                            text = "QUICK PICK STANDARD FOLDERS",
                            style = LabelMonoCapsStyle,
                            color = TextSecondary
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.quickFolders, key = { it.uriString }) { quick ->
                                val borderColor = if (quick.isAlreadyAdded) StatusVerified else AccentCyan.copy(alpha = 0.6f)
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceRaised)
                                        .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                                        .clickable { onSelectFolderUri(quick.uriString) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("quick_folder_${quick.name}"),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (quick.isAlreadyAdded) Icons.Outlined.CheckCircle else Icons.Outlined.Folder,
                                            contentDescription = null,
                                            tint = if (quick.isAlreadyAdded) StatusVerified else AccentCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = quick.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = if (quick.isAlreadyAdded) {
                                            "Added"
                                        } else {
                                            "${quick.directFileCount} file(s)"
                                        },
                                        style = LabelCodeStyle,
                                        color = if (quick.isAlreadyAdded) StatusVerified else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    // Current Path Navigation Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceRaised)
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (state.canGoUp) {
                                IconButton(
                                    onClick = onNavigateUp,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Parent folder",
                                        tint = AccentCyan
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.size(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SdStorage,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.currentDisplayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = state.currentPath,
                                    style = LabelCodeStyle,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (state.canGoUp) {
                            SecondaryConsoleButton(
                                text = "Use This",
                                onClick = {
                                    val uriStr = android.net.Uri.fromFile(java.io.File(state.currentPath)).toString()
                                    onSelectFolderUri(uriStr)
                                },
                                tint = StatusVerified,
                                borderColor = StatusVerified,
                                testTag = "select_current_folder_button"
                            )
                        }
                    }

                    // Subdirectory browser list
                    if (state.subdirectories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceRaised)
                                .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No selectable subfolders inside this directory.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(min = 120.dp, max = 230.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(state.subdirectories, key = { it.uriString }) { dir ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceRaised)
                                        .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp))
                                        .clickable { onNavigateToPath(dir.absolutePath) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FolderOpen,
                                            contentDescription = null,
                                            tint = AccentCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = dir.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${dir.childDirCount} folder(s) • ${dir.directFileCount} file(s)",
                                                style = LabelCodeStyle,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    SecondaryConsoleButton(
                                        text = if (dir.isAlreadyAdded) "Added" else "Select",
                                        onClick = { onSelectFolderUri(dir.uriString) },
                                        tint = if (dir.isAlreadyAdded) StatusVerified else AccentCyan,
                                        borderColor = if (dir.isAlreadyAdded) StatusVerified else AccentCyan,
                                        testTag = "select_dir_${dir.name}"
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom fallback: System SAF picker + Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SecondaryConsoleButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.8f),
                        testTag = "folder_picker_cancel_button"
                    )
                    SecondaryConsoleButton(
                        text = "System Picker (SAF)",
                        onClick = onOpenSystemSafPicker,
                        icon = Icons.Outlined.CreateNewFolder,
                        modifier = Modifier.weight(1.4f),
                        testTag = "open_system_saf_picker_button"
                    )
                }
            }
        }
    }
}

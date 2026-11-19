package com.example.domain

import java.util.Locale

enum class OperationStage(val label: String, val isTerminal: Boolean) {
    DISCOVERED("Queued", false),
    HASHING_SOURCE("Hashing Source", false),
    COPYING("Copying", false),
    VERIFYING("Verifying SHA-256", false),
    VERIFIED_READY_TO_DELETE("Verified • Removing Source", false),
    VERIFIED_MOVED("Verified & Moved", true),
    DUPLICATE_SKIPPED("Duplicate Skipped", true),
    SKIPPED_SYMLINK("Skipped (Symlink)", true),
    SKIPPED_VIRTUAL("Skipped (Virtual)", true),
    SKIPPED_EXCLUDED("Skipped (Excluded)", true),
    FAILED("Failed", true)
}

enum class FailureCategory(val label: String) {
    VERIFICATION_MISMATCH("Verification Mismatch"),
    INSUFFICIENT_STORAGE("Insufficient Storage"),
    PERMISSION_LOST("Permission Lost"),
    IO_ERROR("I/O Error"),
    UNKNOWN("Unexpected Error")
}

enum class RunTriggerType(val label: String) {
    MANUAL("Manual"),
    SCHEDULED("Scheduled"),
    RETRY("Retry")
}

enum class RunStatus(val label: String, val isActive: Boolean) {
    PLANNING("Scanning", true),
    RUNNING("Running", true),
    COMPLETED("Completed", false),
    COMPLETED_WITH_ERRORS("Completed with Errors", false),
    HALTED_STORAGE("Halted: Low Storage", false),
    HALTED_PERMISSION("Halted: Permission Lost", false),
    CANCELLED("Cancelled", false)
}

data class DryRunCategoryCount(
    val category: FileCategory,
    val fileCount: Int,
    val totalBytes: Long
)

data class DryRunFolderSummary(
    val folderUri: String,
    val folderName: String,
    val isAccessible: Boolean,
    val filesToMove: Int,
    val duplicateCount: Int,
    val skippedSymlinks: Int,
    val skippedOther: Int,
    val bytesToTransfer: Long,
    val availableBytes: Long?,
    val hasEnoughSpace: Boolean
)

data class DryRunReport(
    val targetFolderUri: String?,
    val destinationTreeUri: String? = null,
    val destinationLabel: String = "Inside each source folder / Organized",
    val destinationAccessible: Boolean = true,
    val folderSummaries: List<DryRunFolderSummary>,
    val categoryBreakdown: List<DryRunCategoryCount>,
    val totalFilesToMove: Int,
    val totalDuplicates: Int,
    val totalSkippedSymlinks: Int,
    val totalSkippedOther: Int,
    val totalBytesToTransfer: Long,
    val minAvailableBytes: Long?,
    val hasSufficientStorage: Boolean,
    val scannedAt: Long = System.currentTimeMillis()
)

data class DestinationConfig(
    val treeUri: String? = null,
    val displayName: String? = null,
    val isAccessible: Boolean = true
) {
    val isCustom: Boolean get() = treeUri != null && displayName != null
}

data class SubfolderNode(
    val documentId: String,
    val name: String,
    val relativePath: String,
    val isOrganizedFolder: Boolean,
    val isSymlink: Boolean,
    val isExcluded: Boolean,
    val directFileCount: Int = 0,
    val isExcludedByParent: Boolean = false
)

data class ScheduleConfig(
    val enabled: Boolean = false,
    val intervalHours: Int = 24,
    val intervalMinutes: Int = 24 * 60,
    val requireBatteryNotLow: Boolean = true,
    val requireCharging: Boolean = false,
    val requireDeviceIdle: Boolean = false,
    val autoOrganizeOnLaunch: Boolean = false
)

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }
}

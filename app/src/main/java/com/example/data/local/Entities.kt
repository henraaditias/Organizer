package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.FailureCategory
import com.example.domain.FileCategory
import com.example.domain.OperationStage
import com.example.domain.RunStatus
import com.example.domain.RunTriggerType

@Entity(tableName = "source_folders")
data class SourceFolderEntity(
    @PrimaryKey
    val treeUri: String,
    val displayName: String,
    val isAccessible: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOrganizedAt: Long? = null,
    val totalMovedCount: Int = 0,
    val totalMovedBytes: Long = 0L
)

@Entity(
    tableName = "excluded_subfolders",
    foreignKeys = [
        ForeignKey(
            entity = SourceFolderEntity::class,
            parentColumns = ["treeUri"],
            childColumns = ["folderUri"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["folderUri", "relativePath"], unique = true),
        Index(value = ["folderUri"])
    ]
)
data class ExcludedSubfolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val folderUri: String,
    val documentId: String,
    val relativePath: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "organization_runs")
data class OrganizationRunEntity(
    @PrimaryKey
    val runId: String,
    val triggerType: RunTriggerType,
    val status: RunStatus,
    val targetFolderUri: String? = null,
    val targetFolderName: String? = null,
    val destinationFolderName: String? = null,
    val totalDiscovered: Int = 0,
    val processedCount: Int = 0,
    val movedCount: Int = 0,
    val duplicateCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val totalBytesMoved: Long = 0L,
    val currentFileName: String? = null,
    val currentStageLabel: String? = null,
    val currentFileBytesProcessed: Long = 0L,
    val currentFileTotalBytes: Long = 0L,
    val errorSummary: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null
)

@Entity(
    tableName = "file_operations",
    foreignKeys = [
        ForeignKey(
            entity = OrganizationRunEntity::class,
            parentColumns = ["runId"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["runId", "sourceUri"], unique = true),
        Index(value = ["runId"]),
        Index(value = ["stage"]),
        Index(value = ["folderUri"])
    ]
)
data class FileOperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val runId: String,
    val folderUri: String,
    val folderName: String,
    val sourceUri: String,
    val sourceName: String,
    val sourceRelativePath: String,
    val byteSize: Long,
    val category: FileCategory,
    val sourceSha256: String? = null,
    val destinationUri: String? = null,
    val destinationName: String? = null,
    val stage: OperationStage = OperationStage.DISCOVERED,
    val failureCategory: FailureCategory? = null,
    val errorMessage: String? = null,
    val sourceUntouched: Boolean = true,
    val retryCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

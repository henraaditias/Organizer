package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.domain.FailureCategory
import com.example.domain.OperationStage
import com.example.domain.RunStatus
import kotlinx.coroutines.flow.Flow

data class AggregateStats(
    val totalVerifiedMoved: Int = 0,
    val totalDuplicatesSkipped: Int = 0,
    val totalFailedActive: Int = 0,
    val totalBytesMoved: Long = 0L
)

@Dao
interface OrganizerDao {

    // --- Source Folders ---
    @Query("SELECT * FROM source_folders ORDER BY addedAt ASC")
    fun observeSourceFolders(): Flow<List<SourceFolderEntity>>

    @Query("SELECT * FROM source_folders ORDER BY addedAt ASC")
    suspend fun getSourceFoldersSync(): List<SourceFolderEntity>

    @Query("SELECT * FROM source_folders WHERE treeUri = :treeUri LIMIT 1")
    suspend fun getSourceFolderByUri(treeUri: String): SourceFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceFolder(folder: SourceFolderEntity)

    @Query("UPDATE source_folders SET isAccessible = :accessible WHERE treeUri = :treeUri")
    suspend fun updateFolderAccessibility(treeUri: String, accessible: Boolean)

    @Query(
        """
        UPDATE source_folders
        SET lastOrganizedAt = :timestamp,
            totalMovedCount = totalMovedCount + :addedCount,
            totalMovedBytes = totalMovedBytes + :addedBytes
        WHERE treeUri = :treeUri
        """
    )
    suspend fun recordFolderSuccess(treeUri: String, timestamp: Long, addedCount: Int, addedBytes: Long)

    @Query("DELETE FROM source_folders WHERE treeUri = :treeUri")
    suspend fun deleteSourceFolder(treeUri: String)

    // --- Excluded Subfolders ---
    @Query("SELECT * FROM excluded_subfolders ORDER BY relativePath ASC")
    fun observeAllExclusions(): Flow<List<ExcludedSubfolderEntity>>

    @Query("SELECT * FROM excluded_subfolders WHERE folderUri = :folderUri ORDER BY relativePath ASC")
    fun observeExclusionsForFolder(folderUri: String): Flow<List<ExcludedSubfolderEntity>>

    @Query("SELECT * FROM excluded_subfolders WHERE folderUri = :folderUri")
    suspend fun getExclusionsForFolderSync(folderUri: String): List<ExcludedSubfolderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExclusion(exclusion: ExcludedSubfolderEntity)

    @Query("DELETE FROM excluded_subfolders WHERE folderUri = :folderUri AND relativePath = :relativePath")
    suspend fun deleteExclusion(folderUri: String, relativePath: String)

    // --- Organization Runs ---
    @Query("SELECT * FROM organization_runs ORDER BY startedAt DESC LIMIT 50")
    fun observeRecentRuns(): Flow<List<OrganizationRunEntity>>

    @Query("SELECT * FROM organization_runs WHERE status IN ('PLANNING', 'RUNNING') ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveRun(): Flow<OrganizationRunEntity?>

    @Query("SELECT * FROM organization_runs WHERE status IN ('PLANNING', 'RUNNING') ORDER BY startedAt DESC")
    suspend fun getActiveRunsSync(): List<OrganizationRunEntity>

    @Query("SELECT * FROM organization_runs WHERE runId = :runId LIMIT 1")
    suspend fun getRunById(runId: String): OrganizationRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(run: OrganizationRunEntity)

    @Update
    suspend fun updateRun(run: OrganizationRunEntity)

    @Query(
        """
        UPDATE organization_runs
        SET currentFileName = :fileName,
            currentStageLabel = :stageLabel,
            currentFileBytesProcessed = :fileBytesProcessed,
            currentFileTotalBytes = :fileTotalBytes,
            processedCount = :processedCount,
            movedCount = :movedCount,
            duplicateCount = :duplicateCount,
            skippedCount = :skippedCount,
            failedCount = :failedCount
        WHERE runId = :runId
        """
    )
    suspend fun updateRunProgressIndicator(
        runId: String,
        fileName: String?,
        stageLabel: String?,
        processedCount: Int,
        movedCount: Int,
        duplicateCount: Int,
        skippedCount: Int,
        failedCount: Int,
        fileBytesProcessed: Long = 0L,
        fileTotalBytes: Long = 0L
    )

    @Query(
        """
        UPDATE organization_runs
        SET status = :status,
            processedCount = :processed,
            movedCount = :moved,
            duplicateCount = :duplicates,
            skippedCount = :skipped,
            failedCount = :failed,
            totalBytesMoved = :bytesMoved,
            currentFileName = NULL,
            currentStageLabel = NULL,
            currentFileBytesProcessed = 0,
            currentFileTotalBytes = 0,
            errorSummary = :errorSummary,
            finishedAt = :finishedAt
        WHERE runId = :runId
        """
    )
    suspend fun finalizeRun(
        runId: String,
        status: RunStatus,
        processed: Int,
        moved: Int,
        duplicates: Int,
        skipped: Int,
        failed: Int,
        bytesMoved: Long,
        errorSummary: String?,
        finishedAt: Long
    )

    // --- File Operations ---
    @Query("SELECT * FROM file_operations ORDER BY updatedAt DESC LIMIT 500")
    fun observeRecentOperations(): Flow<List<FileOperationEntity>>

    @Query("SELECT * FROM file_operations WHERE runId = :runId ORDER BY updatedAt DESC LIMIT 100")
    fun observeOperationsForRun(runId: String): Flow<List<FileOperationEntity>>

    @Query("SELECT * FROM file_operations WHERE stage = 'FAILED' ORDER BY updatedAt DESC")
    fun observeFailedOperations(): Flow<List<FileOperationEntity>>

    @Query("SELECT * FROM file_operations WHERE stage = 'FAILED' ORDER BY updatedAt DESC")
    suspend fun getFailedOperationsSync(): List<FileOperationEntity>

    @Query("SELECT * FROM file_operations WHERE id = :id LIMIT 1")
    suspend fun getOperationById(id: Long): FileOperationEntity?

    @Query("SELECT * FROM file_operations WHERE runId = :runId ORDER BY id ASC")
    suspend fun getOperationsForRunSync(runId: String): List<FileOperationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOperationsIgnoreDuplicates(operations: List<FileOperationEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOperationIgnoreDuplicate(operation: FileOperationEntity): Long

    @Query(
        """
        UPDATE file_operations
        SET stage = :stage,
            sourceSha256 = COALESCE(:sha256, sourceSha256),
            destinationUri = COALESCE(:destinationUri, destinationUri),
            destinationName = COALESCE(:destinationName, destinationName),
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateOperationStage(
        id: Long,
        stage: OperationStage,
        sha256: String? = null,
        destinationUri: String? = null,
        destinationName: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE file_operations
        SET destinationUri = NULL,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun clearDestinationUri(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE file_operations
        SET stage = 'FAILED',
            failureCategory = :failureCategory,
            errorMessage = :errorMessage,
            sourceUntouched = :sourceUntouched,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun markOperationFailed(
        id: Long,
        failureCategory: FailureCategory,
        errorMessage: String,
        sourceUntouched: Boolean = true,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE file_operations
        SET stage = 'VERIFIED_MOVED',
            failureCategory = NULL,
            errorMessage = NULL,
            sourceUntouched = 0,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun markOperationVerifiedMoved(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE file_operations
        SET stage = 'VERIFIED_MOVED',
            failureCategory = NULL,
            errorMessage = NULL,
            sourceUntouched = 0,
            updatedAt = :updatedAt
        WHERE sourceUri = :sourceUri AND stage = 'FAILED'
        """
    )
    suspend fun resolveEarlierFailuresForSource(sourceUri: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE file_operations
        SET stage = 'DUPLICATE_SKIPPED',
            failureCategory = NULL,
            errorMessage = :note,
            sourceUntouched = 1,
            updatedAt = :updatedAt
        WHERE sourceUri = :sourceUri AND stage = 'FAILED'
        """
    )
    suspend fun resolveEarlierFailuresAsDuplicate(sourceUri: String, note: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN stage = 'VERIFIED_MOVED' THEN 1 ELSE 0 END), 0) AS totalVerifiedMoved,
            COALESCE(SUM(CASE WHEN stage = 'DUPLICATE_SKIPPED' THEN 1 ELSE 0 END), 0) AS totalDuplicatesSkipped,
            COALESCE(SUM(CASE WHEN stage = 'FAILED' THEN 1 ELSE 0 END), 0) AS totalFailedActive,
            COALESCE(SUM(CASE WHEN stage = 'VERIFIED_MOVED' THEN byteSize ELSE 0 END), 0) AS totalBytesMoved
        FROM file_operations
        """
    )
    fun observeAggregateStats(): Flow<AggregateStats>
}

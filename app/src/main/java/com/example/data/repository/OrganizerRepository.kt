package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.engine.SafeFileEngine
import com.example.data.engine.SafStorageHelper
import com.example.data.local.AggregateStats
import com.example.data.local.ExcludedSubfolderEntity
import com.example.data.local.FileOperationEntity
import com.example.data.local.OrganizationRunEntity
import com.example.data.local.OrganizerDao
import com.example.data.local.SchedulePreferencesRepository
import com.example.data.local.SourceFolderEntity
import com.example.domain.DestinationConfig
import com.example.domain.DryRunReport
import com.example.domain.FileCategory
import com.example.domain.RunStatus
import com.example.domain.RunTriggerType
import com.example.domain.ScheduleConfig
import com.example.domain.SubfolderNode
import com.example.service.OrganizerForegroundService
import com.example.worker.OrganizeWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class OrganizerRepository(
    private val context: Context,
    val dao: OrganizerDao,
    val safeFileEngine: SafeFileEngine,
    private val schedulePrefs: SchedulePreferencesRepository
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executionMutex = Mutex()
    private val _isDirectRunning = MutableStateFlow(false)
    private var activeDirectJob: Job? = null

    private val workManager: WorkManager by lazy {
        if (!WorkManager.isInitialized()) {
            runCatching {
                WorkManager.initialize(
                    context.applicationContext,
                    Configuration.Builder().build()
                )
            }
        }
        WorkManager.getInstance(context.applicationContext)
    }

    val sourceFolders: Flow<List<SourceFolderEntity>> = dao.observeSourceFolders()
    val allExclusions: Flow<List<ExcludedSubfolderEntity>> = dao.observeAllExclusions()
    val recentRuns: Flow<List<OrganizationRunEntity>> = dao.observeRecentRuns()
    val activeRun: Flow<OrganizationRunEntity?> = dao.observeActiveRun()
    val recentOperations: Flow<List<FileOperationEntity>> = dao.observeRecentOperations()
    val failedOperations: Flow<List<FileOperationEntity>> = dao.observeFailedOperations()
    val aggregateStats: Flow<AggregateStats> = dao.observeAggregateStats()
    val scheduleConfig: Flow<ScheduleConfig> = schedulePrefs.scheduleConfig
    val destinationConfig: Flow<DestinationConfig> = schedulePrefs.destinationConfig.map { cfg ->
        if (cfg.treeUri != null) {
            cfg.copy(
                isAccessible = SafStorageHelper.hasPersistedReadWritePermission(context, cfg.treeUri)
            )
        } else {
            cfg
        }
    }

    private val workManagerRunning: Flow<Boolean> = combine(
        flow {
            emitAll(
                workManager
                    .getWorkInfosForUniqueWorkFlow(OrganizeWorker.UNIQUE_MANUAL_WORK_NAME)
                    .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
            )
        },
        flow {
            emitAll(
                workManager
                    .getWorkInfosForUniqueWorkFlow(OrganizeWorker.UNIQUE_PERIODIC_WORK_NAME)
                    .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
            )
        }
    ) { manualRunning, periodicRunning ->
        manualRunning || periodicRunning
    }

    val isWorkerRunning: Flow<Boolean> = combine(_isDirectRunning, workManagerRunning) { direct, wm ->
        direct || wm
    }

    fun isDirectOrWorkerRunningNow(): Boolean = _isDirectRunning.value

    suspend fun addSourceFolder(treeUri: Uri): Result<SourceFolderEntity> {
        return runCatching {
            if (!SafStorageHelper.isFileUri(treeUri)) {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } else if (!SafStorageHelper.hasPersistedReadWritePermission(context, treeUri.toString())) {
                throw SecurityException("Grant All Files Access permission first to select Download, Documents, or shared storage folders.")
            }

            val doc = SafStorageHelper.openTreeOrFileDocument(context, treeUri)
                ?: throw IllegalStateException("Unable to open selected folder.")
            val displayName = doc.name?.takeIf { it.isNotBlank() } ?: "Selected Folder"

            val entity = SourceFolderEntity(
                treeUri = treeUri.toString(),
                displayName = displayName,
                isAccessible = SafStorageHelper.hasPersistedReadWritePermission(context, treeUri.toString())
            )
            dao.upsertSourceFolder(entity)
            entity
        }
    }

    suspend fun refreshFolderPermissions() {
        val folders = dao.getSourceFoldersSync()
        for (folder in folders) {
            val ok = SafStorageHelper.hasPersistedReadWritePermission(context, folder.treeUri)
            if (folder.isAccessible != ok) {
                dao.updateFolderAccessibility(folder.treeUri, ok)
            }
        }
    }

    suspend fun removeSourceFolder(treeUri: String) {
        SafStorageHelper.releasePersistedPermission(context, treeUri)
        dao.deleteSourceFolder(treeUri)
    }

    suspend fun loadSubfoldersForExclusion(folderUri: String): List<SubfolderNode> {
        return safeFileEngine.listSubfoldersForExclusion(folderUri)
    }

    suspend fun toggleSubfolderExclusion(folderUri: String, node: SubfolderNode) {
        if (node.isOrganizedFolder || node.isSymlink) return
        if (node.isExcluded) {
            dao.deleteExclusion(folderUri, node.relativePath)
        } else {
            dao.insertExclusion(
                ExcludedSubfolderEntity(
                    folderUri = folderUri,
                    documentId = node.documentId,
                    relativePath = node.relativePath,
                    displayName = node.name
                )
            )
        }
    }

    suspend fun getScheduleConfigSync(): ScheduleConfig {
        return schedulePrefs.getScheduleConfigSync()
    }

    suspend fun getRunByIdSync(runId: String): OrganizationRunEntity? {
        return dao.getRunById(runId)
    }

    suspend fun getCustomDestinationUriSync(): String? {
        return schedulePrefs.getDestinationConfigSync().treeUri
    }

    suspend fun setCustomDestination(treeUri: Uri): Result<String> {
        return runCatching {
            if (!SafStorageHelper.isFileUri(treeUri)) {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } else if (!SafStorageHelper.hasPersistedReadWritePermission(context, treeUri.toString())) {
                throw SecurityException("Grant All Files Access permission first to use this folder as destination.")
            }
            val folderName = safeFileEngine.ensureOrganizedStructure(treeUri).getOrThrow()
            schedulePrefs.setCustomDestination(treeUri.toString(), folderName)
            folderName
        }
    }

    suspend fun clearCustomDestination() {
        val currentUri = schedulePrefs.getDestinationConfigSync().treeUri
        schedulePrefs.setCustomDestination(null, null)
        if (currentUri != null && dao.getSourceFolderByUri(currentUri) == null) {
            SafStorageHelper.releasePersistedPermission(context, currentUri)
        }
    }

    fun observeOperationsForRun(runId: String): Flow<List<FileOperationEntity>> {
        return dao.observeOperationsForRun(runId)
    }

    suspend fun buildDryRunPreview(targetFolderUri: String?): DryRunReport {
        val customDestUri = getCustomDestinationUriSync()
        return safeFileEngine.buildDryRunReport(targetFolderUri, customDestUri)
    }

    suspend fun executeRunSafely(
        runId: String,
        triggerType: RunTriggerType,
        targetFolderUri: String?,
        customDestinationUri: String? = null,
        retryOperationIds: List<Long>? = null,
        onProgress: suspend (processed: Int, total: Int, currentFile: String, stageLabel: String) -> Unit = { _, _, _, _ -> }
    ): OrganizationRunEntity? {
        return executionMutex.withLock {
            _isDirectRunning.value = true
            try {
                safeFileEngine.executeRun(
                    runId = runId,
                    triggerType = triggerType,
                    targetFolderUri = targetFolderUri,
                    customDestinationUri = customDestinationUri,
                    retryOperationIds = retryOperationIds,
                    onProgress = onProgress
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    val existing = dao.getRunById(runId)
                    if (existing == null) {
                        dao.upsertRun(
                            OrganizationRunEntity(
                                runId = runId,
                                triggerType = triggerType,
                                status = RunStatus.COMPLETED_WITH_ERRORS,
                                targetFolderUri = targetFolderUri,
                                errorSummary = t.localizedMessage ?: "Unexpected execution error. Source files remain safe.",
                                finishedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        dao.finalizeRun(
                            runId = runId,
                            status = RunStatus.COMPLETED_WITH_ERRORS,
                            processed = existing.processedCount,
                            moved = existing.movedCount,
                            duplicates = existing.duplicateCount,
                            skipped = existing.skippedCount,
                            failed = existing.failedCount,
                            bytesMoved = existing.totalBytesMoved,
                            errorSummary = t.localizedMessage ?: "Unexpected execution error. Source files remain safe.",
                            finishedAt = System.currentTimeMillis()
                        )
                    }
                }
                null
            } finally {
                _isDirectRunning.value = false
            }
        }
    }

    private fun launchDirectRun(
        runId: String,
        triggerType: RunTriggerType,
        targetFolderUri: String?,
        customDestinationUri: String?,
        retryOperationIds: List<Long>? = null
    ) {
        if (_isDirectRunning.value) return
        _isDirectRunning.value = true
        OrganizerForegroundService.start(context, runId)
        activeDirectJob = appScope.launch {
            val destLabel = if (customDestinationUri != null) {
                val cfg = schedulePrefs.getDestinationConfigSync()
                "${cfg.displayName ?: "Custom"} / ${FileCategory.ORGANIZED_ROOT_NAME}"
            } else {
                "Source / ${FileCategory.ORGANIZED_ROOT_NAME}"
            }
            val existing = dao.getRunById(runId)
            if (existing == null) {
                val folderName = targetFolderUri?.let { dao.getSourceFolderByUri(it)?.displayName }
                dao.upsertRun(
                    OrganizationRunEntity(
                        runId = runId,
                        triggerType = triggerType,
                        status = RunStatus.PLANNING,
                        targetFolderUri = targetFolderUri,
                        targetFolderName = folderName,
                        destinationFolderName = destLabel,
                        currentStageLabel = "Scanning folders"
                    )
                )
            }
            executeRunSafely(
                runId = runId,
                triggerType = triggerType,
                targetFolderUri = targetFolderUri,
                customDestinationUri = customDestinationUri,
                retryOperationIds = retryOperationIds
            )
        }
    }

    fun enqueueManualOrganization(
        targetFolderUri: String?,
        customDestinationUri: String? = null
    ): String {
        val runId = safeFileEngine.createRunId()
        launchDirectRun(
            runId = runId,
            triggerType = RunTriggerType.MANUAL,
            targetFolderUri = targetFolderUri,
            customDestinationUri = customDestinationUri
        )
        return runId
    }

    fun enqueueRetryFailed(
        operationIds: LongArray,
        customDestinationUri: String? = null
    ): String? {
        if (operationIds.isEmpty()) return null
        val runId = safeFileEngine.createRunId()
        launchDirectRun(
            runId = runId,
            triggerType = RunTriggerType.RETRY,
            targetFolderUri = null,
            customDestinationUri = customDestinationUri,
            retryOperationIds = operationIds.toList()
        )
        return runId
    }

    suspend fun enqueueScheduledNow(): String? {
        if (_isDirectRunning.value) return null
        val customDestinationUri = getCustomDestinationUriSync()
        val runId = safeFileEngine.createRunId()
        launchDirectRun(
            runId = runId,
            triggerType = RunTriggerType.SCHEDULED,
            targetFolderUri = null,
            customDestinationUri = customDestinationUri
        )
        return runId
    }

    fun resumeRun(
        runId: String,
        targetFolderUri: String?,
        triggerType: RunTriggerType,
        customDestinationUri: String? = null
    ) {
        launchDirectRun(
            runId = runId,
            triggerType = triggerType,
            targetFolderUri = targetFolderUri,
            customDestinationUri = customDestinationUri
        )
    }

    suspend fun getInterruptedStaleRuns(): List<OrganizationRunEntity> {
        if (_isDirectRunning.value) return emptyList()
        val activeRuns = dao.getActiveRunsSync()
        if (activeRuns.isEmpty()) return emptyList()
        val infos = runCatching {
            workManager.getWorkInfosForUniqueWork(OrganizeWorker.UNIQUE_MANUAL_WORK_NAME).get() +
                workManager.getWorkInfosForUniqueWork(OrganizeWorker.UNIQUE_PERIODIC_WORK_NAME).get()
        }.getOrElse { emptyList() }
        val hasLiveWorker = infos.any {
            it.state == WorkInfo.State.RUNNING
        }
        return if (hasLiveWorker) emptyList() else activeRuns
    }

    fun cancelActiveWork() {
        activeDirectJob?.cancel()
        workManager.cancelUniqueWork(OrganizeWorker.UNIQUE_MANUAL_WORK_NAME)
        workManager.cancelUniqueWork(OrganizeWorker.UNIQUE_PERIODIC_WORK_NAME)
    }

    suspend fun updateSchedule(config: ScheduleConfig) {
        val previouslyEnabled = schedulePrefs.getScheduleConfigSync().enabled
        schedulePrefs.updateScheduleConfig(config)
        if (!config.enabled) {
            workManager.cancelUniqueWork(OrganizeWorker.UNIQUE_PERIODIC_WORK_NAME)
            workManager.cancelUniqueWork("${OrganizeWorker.UNIQUE_PERIODIC_WORK_NAME}_initial")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(config.requireBatteryNotLow)
            .setRequiresCharging(config.requireCharging)
            .setRequiresDeviceIdle(config.requireDeviceIdle)
            .setRequiresStorageNotLow(true)
            .build()

        val data = Data.Builder()
            .putString(OrganizeWorker.KEY_TRIGGER_TYPE, RunTriggerType.SCHEDULED.name)
            .build()

        val intervalMinutes = config.intervalMinutes.toLong().coerceAtLeast(15L)
        val request = PeriodicWorkRequestBuilder<OrganizeWorker>(
            intervalMinutes,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(data)
            .build()

        workManager.enqueueUniquePeriodicWork(
            OrganizeWorker.UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        if (!previouslyEnabled && dao.getSourceFoldersSync().isNotEmpty()) {
            val initialRequest = OneTimeWorkRequestBuilder<OrganizeWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()
            workManager.enqueueUniqueWork(
                "${OrganizeWorker.UNIQUE_PERIODIC_WORK_NAME}_initial",
                ExistingWorkPolicy.REPLACE,
                initialRequest
            )
        }
    }

    suspend fun buildFailedReportContent(): String {
        val failed = dao.getFailedOperationsSync()
        return safeFileEngine.formatFailedReportText(failed)
    }
}

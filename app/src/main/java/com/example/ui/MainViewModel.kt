package com.example.ui

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.OrganizerApplication
import com.example.data.engine.SafStorageHelper
import com.example.data.local.AggregateStats
import com.example.data.local.ExcludedSubfolderEntity
import com.example.data.local.FileOperationEntity
import com.example.data.local.OrganizationRunEntity
import com.example.data.local.SourceFolderEntity
import com.example.data.repository.OrganizerRepository
import com.example.domain.DestinationConfig
import com.example.domain.DryRunReport
import com.example.domain.OperationStage
import com.example.domain.ScheduleConfig
import com.example.domain.SubfolderNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class FolderPickerTarget {
    SOURCE,
    DESTINATION
}

data class LocalDirectoryEntry(
    val name: String,
    val absolutePath: String,
    val uriString: String,
    val childDirCount: Int,
    val directFileCount: Int,
    val isAlreadyAdded: Boolean = false
)

data class FolderPickerState(
    val visible: Boolean = false,
    val target: FolderPickerTarget = FolderPickerTarget.SOURCE,
    val hasAllFilesAccess: Boolean = false,
    val currentPath: String = "",
    val canGoUp: Boolean = false,
    val currentDisplayName: String = "Internal Storage",
    val quickFolders: List<LocalDirectoryEntry> = emptyList(),
    val subdirectories: List<LocalDirectoryEntry> = emptyList()
)

enum class HistoryFilter(val label: String) {
    ALL("All Logs"),
    FAILED("Failed Report"),
    VERIFIED("Verified Moved"),
    DUPLICATES("Duplicates"),
    SKIPPED("Skipped")
}

sealed interface DryRunState {
    data object Hidden : DryRunState
    data class Scanning(val targetFolderName: String?) : DryRunState
    data class Ready(val report: DryRunReport) : DryRunState
    data class Error(val message: String) : DryRunState
}

sealed interface ExclusionsSheetState {
    data object Hidden : ExclusionsSheetState
    data class Loading(val folder: SourceFolderEntity) : ExclusionsSheetState
    data class Ready(val folder: SourceFolderEntity, val nodes: List<SubfolderNode>) : ExclusionsSheetState
}

data class DashboardUiState(
    val folders: List<SourceFolderEntity> = emptyList(),
    val exclusionsByFolder: Map<String, List<ExcludedSubfolderEntity>> = emptyMap(),
    val stats: AggregateStats = AggregateStats(),
    val activeRun: OrganizationRunEntity? = null,
    val isWorkerRunning: Boolean = false,
    val interruptedRun: OrganizationRunEntity? = null,
    val recentRuns: List<OrganizationRunEntity> = emptyList(),
    val destinationConfig: DestinationConfig = DestinationConfig()
)

class MainViewModel(
    application: Application,
    private val repository: OrganizerRepository
) : AndroidViewModel(application) {

    private val _dryRunState = MutableStateFlow<DryRunState>(DryRunState.Hidden)
    val dryRunState: StateFlow<DryRunState> = _dryRunState.asStateFlow()

    private val _exclusionsState = MutableStateFlow<ExclusionsSheetState>(ExclusionsSheetState.Hidden)
    val exclusionsState: StateFlow<ExclusionsSheetState> = _exclusionsState.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private val _interruptedRun = MutableStateFlow<OrganizationRunEntity?>(null)

    private val _showLiveProgressDialog = MutableStateFlow(false)
    val showLiveProgressDialog: StateFlow<Boolean> = _showLiveProgressDialog.asStateFlow()

    private val _watchedRunId = MutableStateFlow<String?>(null)

    private val _folderPickerState = MutableStateFlow(FolderPickerState())
    val folderPickerState: StateFlow<FolderPickerState> = _folderPickerState.asStateFlow()

    private val exclusionsMutex = Mutex()
    private val didCheckAutoLaunch = AtomicBoolean(false)

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

    val dashboardUiState: StateFlow<DashboardUiState> = combine(
        repository.sourceFolders,
        repository.allExclusions,
        repository.aggregateStats,
        repository.activeRun,
        repository.isWorkerRunning,
        _interruptedRun,
        repository.recentRuns,
        repository.destinationConfig
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val folders = values[0] as List<SourceFolderEntity>
        @Suppress("UNCHECKED_CAST")
        val exclusions = values[1] as List<ExcludedSubfolderEntity>
        val stats = values[2] as AggregateStats
        val activeRun = values[3] as OrganizationRunEntity?
        val workerRunning = values[4] as Boolean
        val interrupted = values[5] as OrganizationRunEntity?
        @Suppress("UNCHECKED_CAST")
        val runs = values[6] as List<OrganizationRunEntity>
        val destConfig = values[7] as DestinationConfig

        DashboardUiState(
            folders = folders,
            exclusionsByFolder = exclusions.groupBy { it.folderUri },
            stats = stats,
            activeRun = activeRun,
            isWorkerRunning = workerRunning,
            interruptedRun = if (!workerRunning) interrupted else null,
            recentRuns = runs,
            destinationConfig = destConfig
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    val watchedRun: StateFlow<OrganizationRunEntity?> = combine(
        repository.activeRun,
        repository.recentRuns,
        _watchedRunId
    ) { active, recent, watchedId ->
        if (watchedId != null) {
            active?.takeIf { it.runId == watchedId }
                ?: recent.firstOrNull { it.runId == watchedId }
                ?: active
        } else {
            active
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveRunOperations: StateFlow<List<FileOperationEntity>> = watchedRun
        .flatMapLatest { run ->
            if (run != null) repository.observeOperationsForRun(run.runId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOperations: StateFlow<List<FileOperationEntity>> = repository.recentOperations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val failedOperations: StateFlow<List<FileOperationEntity>> = repository.failedOperations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleConfig: StateFlow<ScheduleConfig> = repository.scheduleConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleConfig())

    init {
        refreshPermissionsAndCheckRecovery()
    }

    fun refreshPermissionsAndCheckRecovery() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.refreshFolderPermissions()
            val staleRuns = repository.getInterruptedStaleRuns()
            _interruptedRun.value = staleRuns.firstOrNull()

            if (_folderPickerState.value.visible) {
                refreshFolderPickerDirectory(_folderPickerState.value.currentPath)
            }

            if (didCheckAutoLaunch.compareAndSet(false, true)) {
                val cfg = repository.getScheduleConfigSync()
                val folders = repository.sourceFolders.first()
                if (cfg.autoOrganizeOnLaunch &&
                    folders.isNotEmpty() &&
                    staleRuns.isEmpty() &&
                    !repository.isDirectOrWorkerRunningNow()
                ) {
                    val runId = repository.enqueueScheduledNow()
                    if (runId != null) {
                        _watchedRunId.value = runId
                        _showLiveProgressDialog.value = true
                        _uiMessages.tryEmit("Auto-organize started on app launch")
                    }
                }
            }
        }
    }

    fun openFolderPicker(target: FolderPickerTarget) {
        viewModelScope.launch(Dispatchers.IO) {
            val rootPath = runCatching { Environment.getExternalStorageDirectory().absolutePath }
                .getOrDefault("/storage/emulated/0")
            _folderPickerState.value = FolderPickerState(
                visible = true,
                target = target,
                currentPath = rootPath
            )
            refreshFolderPickerDirectory(rootPath)
        }
    }

    fun navigateFolderPickerTo(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            refreshFolderPickerDirectory(path)
        }
    }

    fun navigateFolderPickerUp() {
        val currentPath = _folderPickerState.value.currentPath
        val extRoot = runCatching { Environment.getExternalStorageDirectory().absolutePath }
            .getOrDefault("/storage/emulated/0")
        if (currentPath == extRoot) return
        val parent = File(currentPath).parentFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            refreshFolderPickerDirectory(parent.absolutePath)
        }
    }

    fun selectLocalFolderUri(uriString: String) {
        val target = _folderPickerState.value.target
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        _folderPickerState.update { it.copy(visible = false) }
        if (target == FolderPickerTarget.DESTINATION) {
            onDestinationFolderSelected(uri)
        } else {
            onFolderSelected(uri)
        }
    }

    fun closeFolderPicker() {
        _folderPickerState.update { it.copy(visible = false) }
    }

    private suspend fun refreshFolderPickerDirectory(targetPath: String) {
        val app = getApplication<Application>()
        val hasAccess = SafStorageHelper.hasAllFilesAccess(app)
        val extRootFile = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        val extRootPath = extRootFile?.absolutePath ?: "/storage/emulated/0"
        val existingSourceUris = repository.sourceFolders.first().map { it.treeUri }.toSet()
        val customDestUri = repository.getCustomDestinationUriSync()
        val isDestTarget = _folderPickerState.value.target == FolderPickerTarget.DESTINATION

        val quickEntries = if (hasAccess) {
            listOf(
                Environment.DIRECTORY_DOWNLOADS to "Downloads",
                Environment.DIRECTORY_DOCUMENTS to "Documents",
                Environment.DIRECTORY_PICTURES to "Pictures",
                Environment.DIRECTORY_MOVIES to "Movies",
                Environment.DIRECTORY_MUSIC to "Music",
                Environment.DIRECTORY_DCIM to "DCIM"
            ).mapNotNull { (envDir, label) ->
                val dir = runCatching { Environment.getExternalStoragePublicDirectory(envDir) }.getOrNull()
                if (dir != null && dir.exists() && dir.isDirectory && !SafStorageHelper.isLocalPathSymbolicLink(dir.absolutePath)) {
                    val uriStr = Uri.fromFile(dir).toString()
                    val children = runCatching { dir.listFiles() }.getOrNull().orEmpty()
                    LocalDirectoryEntry(
                        name = label,
                        absolutePath = dir.absolutePath,
                        uriString = uriStr,
                        childDirCount = children.count { it.isDirectory && !SafStorageHelper.isLocalPathSymbolicLink(it.absolutePath) },
                        directFileCount = children.count { it.isFile && !SafStorageHelper.isLocalPathSymbolicLink(it.absolutePath) },
                        isAlreadyAdded = if (isDestTarget) customDestUri == uriStr else uriStr in existingSourceUris
                    )
                } else {
                    null
                }
            }
        } else {
            emptyList()
        }

        val currentDir = File(targetPath).takeIf { it.exists() && it.isDirectory }
            ?: extRootFile
            ?: File(extRootPath)
        val subdirs = if (hasAccess && currentDir.exists() && currentDir.isDirectory) {
            val isRoot = currentDir.absolutePath == extRootPath
            runCatching { currentDir.listFiles() }.getOrNull().orEmpty()
                .filter { child ->
                    child.isDirectory &&
                        !child.name.startsWith(".") &&
                        !(isRoot && child.name.equals("Android", ignoreCase = true)) &&
                        !SafStorageHelper.isLocalPathSymbolicLink(child.absolutePath)
                }
                .sortedBy { it.name.lowercase() }
                .map { child ->
                    val uriStr = Uri.fromFile(child).toString()
                    val grandChildren = runCatching { child.listFiles() }.getOrNull().orEmpty()
                    LocalDirectoryEntry(
                        name = child.name,
                        absolutePath = child.absolutePath,
                        uriString = uriStr,
                        childDirCount = grandChildren.count { it.isDirectory },
                        directFileCount = grandChildren.count { it.isFile },
                        isAlreadyAdded = if (isDestTarget) customDestUri == uriStr else uriStr in existingSourceUris
                    )
                }
        } else {
            emptyList()
        }

        val displayName = if (currentDir.absolutePath == extRootPath) {
            "Internal Storage"
        } else {
            currentDir.name.ifBlank { currentDir.absolutePath }
        }

        _folderPickerState.update { state ->
            state.copy(
                hasAllFilesAccess = hasAccess,
                currentPath = currentDir.absolutePath,
                canGoUp = currentDir.absolutePath != extRootPath && currentDir.parentFile != null,
                currentDisplayName = displayName,
                quickFolders = quickEntries,
                subdirectories = subdirs
            )
        }
    }

    fun onFolderSelected(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.addSourceFolder(treeUri)
            result.onSuccess { folder ->
                _uiMessages.tryEmit("Added source folder: ${folder.displayName}")
            }.onFailure { err ->
                _uiMessages.tryEmit(err.localizedMessage ?: "Failed to persist folder access")
            }
        }
    }

    fun removeFolder(treeUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeSourceFolder(treeUri)
            _uiMessages.tryEmit("Removed folder from Organizer")
        }
    }

    fun openExclusionsSheet(folder: SourceFolderEntity) {
        _exclusionsState.value = ExclusionsSheetState.Loading(folder)
        viewModelScope.launch(Dispatchers.IO) {
            val nodes = runCatching {
                repository.loadSubfoldersForExclusion(folder.treeUri)
            }.getOrElse { emptyList() }
            _exclusionsState.value = ExclusionsSheetState.Ready(folder, nodes)
        }
    }

    fun refreshExclusionsSheet() {
        val folder = when (val current = _exclusionsState.value) {
            is ExclusionsSheetState.Ready -> current.folder
            is ExclusionsSheetState.Loading -> current.folder
            ExclusionsSheetState.Hidden -> return
        }
        openExclusionsSheet(folder)
    }

    fun toggleSubfolderExclusion(node: SubfolderNode) {
        if (node.isOrganizedFolder || node.isSymlink || node.isExcludedByParent) return
        viewModelScope.launch(Dispatchers.IO) {
            exclusionsMutex.withLock {
                val current = _exclusionsState.value as? ExclusionsSheetState.Ready ?: return@withLock
                val latestNode = current.nodes.firstOrNull { it.relativePath == node.relativePath } ?: node
                if (latestNode.isOrganizedFolder || latestNode.isSymlink || latestNode.isExcludedByParent) {
                    return@withLock
                }
                repository.toggleSubfolderExclusion(current.folder.treeUri, latestNode)
                _exclusionsState.update { state ->
                    if (state !is ExclusionsSheetState.Ready) return@update state
                    val toggled = state.nodes.map { item ->
                        if (item.relativePath == latestNode.relativePath) {
                            item.copy(isExcluded = !latestNode.isExcluded)
                        } else {
                            item
                        }
                    }
                    val excludedPaths = toggled
                        .filter { it.isExcluded && !it.isOrganizedFolder }
                        .map { it.relativePath }
                        .toSet()
                    val recomputed = toggled.map { item ->
                        val excludedSelf = item.isOrganizedFolder || item.relativePath in excludedPaths
                        val excludedByParent = !excludedSelf && excludedPaths.any { parent ->
                            item.relativePath.startsWith("$parent/")
                        }
                        item.copy(
                            isExcluded = excludedSelf,
                            isExcludedByParent = excludedByParent
                        )
                    }
                    state.copy(nodes = recomputed)
                }
            }
        }
    }

    fun organizeFromExclusions() {
        val ready = _exclusionsState.value as? ExclusionsSheetState.Ready ?: return
        _exclusionsState.value = ExclusionsSheetState.Hidden
        startDryRunPreview(ready.folder)
    }

    fun closeExclusionsSheet() {
        _exclusionsState.value = ExclusionsSheetState.Hidden
    }

    fun onDestinationFolderSelected(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.setCustomDestination(treeUri)
            result.onSuccess { folderName ->
                _uiMessages.tryEmit("Destination set: $folderName/Organized (7 subfolders created)")
                val currentDryRun = _dryRunState.value
                if (currentDryRun is DryRunState.Ready) {
                    val targetFolder = currentDryRun.report.targetFolderUri?.let { uri ->
                        dashboardUiState.value.folders.firstOrNull { it.treeUri == uri }
                    }
                    startDryRunPreview(targetFolder)
                }
            }.onFailure { err ->
                _uiMessages.tryEmit(err.localizedMessage ?: "Failed to create Organized folder structure")
            }
        }
    }

    fun resetDestinationToDefault() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearCustomDestination()
            _uiMessages.tryEmit("Output reset to default (Inside each source folder / Organized)")
            val currentDryRun = _dryRunState.value
            if (currentDryRun is DryRunState.Ready) {
                val targetFolder = currentDryRun.report.targetFolderUri?.let { uri ->
                    dashboardUiState.value.folders.firstOrNull { it.treeUri == uri }
                }
                startDryRunPreview(targetFolder)
            }
        }
    }

    fun openLiveProgressModal() {
        val state = dashboardUiState.value
        _watchedRunId.value = state.activeRun?.runId ?: state.recentRuns.firstOrNull()?.runId
        _showLiveProgressDialog.value = true
    }

    fun dismissLiveProgressModal() {
        _showLiveProgressDialog.value = false
        if (!dashboardUiState.value.isWorkerRunning) {
            _watchedRunId.value = null
        }
    }

    fun startDryRunPreview(targetFolder: SourceFolderEntity?) {
        val state = dashboardUiState.value
        if (state.folders.isEmpty()) {
            _uiMessages.tryEmit("Add at least one source folder first.")
            return
        }
        if (state.isWorkerRunning) {
            _uiMessages.tryEmit("An organization job is already running.")
            return
        }

        _dryRunState.value = DryRunState.Scanning(targetFolder?.displayName)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.buildDryRunPreview(targetFolder?.treeUri)
            }.onSuccess { report ->
                _dryRunState.value = DryRunState.Ready(report)
            }.onFailure { err ->
                _dryRunState.value = DryRunState.Error(
                    err.localizedMessage ?: "Unable to scan folders for preview."
                )
            }
        }
    }

    fun dismissDryRunPreview() {
        _dryRunState.value = DryRunState.Hidden
    }

    fun confirmAndRunFromDryRun() {
        val ready = _dryRunState.value as? DryRunState.Ready ?: return
        val targetUri = ready.report.targetFolderUri
        val destUri = dashboardUiState.value.destinationConfig.treeUri
        _dryRunState.value = DryRunState.Hidden
        val runId = repository.enqueueManualOrganization(targetUri, destUri)
        _watchedRunId.value = runId
        _showLiveProgressDialog.value = true
        _uiMessages.tryEmit("Started safe verification & move job")
    }

    fun resumeInterruptedRun() {
        val run = _interruptedRun.value ?: return
        val destUri = dashboardUiState.value.destinationConfig.treeUri
        _interruptedRun.value = null
        repository.resumeRun(run.runId, run.targetFolderUri, run.triggerType, destUri)
        _watchedRunId.value = run.runId
        _showLiveProgressDialog.value = true
        _uiMessages.tryEmit("Resuming interrupted run safely without duplicating files")
    }

    fun cancelActiveWork() {
        repository.cancelActiveWork()
        _uiMessages.tryEmit("Cancellation requested. Active source file remains safe.")
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun retryAllFailed() {
        val ids = failedOperations.value.map { it.id }.toLongArray()
        if (ids.isEmpty()) {
            _uiMessages.tryEmit("No failed operations to retry.")
            return
        }
        val destUri = dashboardUiState.value.destinationConfig.treeUri
        val runId = repository.enqueueRetryFailed(ids, destUri)
        if (runId != null) {
            _watchedRunId.value = runId
            _showLiveProgressDialog.value = true
        }
        _uiMessages.tryEmit("Retrying ${ids.size} failed file(s) with SHA-256 verification")
    }

    fun retrySingleOperation(operationId: Long) {
        val destUri = dashboardUiState.value.destinationConfig.treeUri
        val runId = repository.enqueueRetryFailed(longArrayOf(operationId), destUri)
        if (runId != null) {
            _watchedRunId.value = runId
            _showLiveProgressDialog.value = true
        }
        _uiMessages.tryEmit("Retrying file with full SHA-256 verification")
    }

    fun runAutoOrganizeNow() {
        val state = dashboardUiState.value
        if (state.folders.isEmpty()) {
            _uiMessages.tryEmit("Add at least one source folder first.")
            return
        }
        if (state.isWorkerRunning) {
            _uiMessages.tryEmit("An organization job is already running.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val runId = repository.enqueueScheduledNow()
            if (runId != null) {
                _watchedRunId.value = runId
                _showLiveProgressDialog.value = true
                _uiMessages.tryEmit("Running automatic SHA-256 organization now")
            } else {
                _uiMessages.tryEmit("An organization job is already running.")
            }
        }
    }

    fun updateScheduleConfig(config: ScheduleConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSchedule(config)
            val freqLabel = when {
                config.intervalMinutes < 60 -> "${config.intervalMinutes}m"
                config.intervalMinutes >= 1440 && config.intervalMinutes % 1440 == 0 ->
                    "${config.intervalMinutes / 1440}d"
                else -> "${config.intervalMinutes / 60}h"
            }
            _uiMessages.tryEmit(
                if (config.enabled) "Periodic background organization enabled ($freqLabel)"
                else if (config.autoOrganizeOnLaunch) "Auto-organize on app launch enabled (background schedule off)"
                else "Periodic background organization disabled"
            )
        }
    }

    fun exportFailedReportToUri(destinationUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val reportText = repository.buildFailedReportContent()
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(destinationUri, "wt")?.use { out ->
                    out.write(reportText.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("Cannot open export destination")
            }.onSuccess {
                _uiMessages.tryEmit("Failed-files report exported locally")
            }.onFailure { err ->
                _uiMessages.tryEmit("Export failed: ${err.localizedMessage ?: "I/O error"}")
            }
        }
    }

    fun filterOperations(ops: List<FileOperationEntity>, filter: HistoryFilter): List<FileOperationEntity> {
        return when (filter) {
            HistoryFilter.ALL -> ops
            HistoryFilter.FAILED -> ops.filter { it.stage == OperationStage.FAILED }
            HistoryFilter.VERIFIED -> ops.filter { it.stage == OperationStage.VERIFIED_MOVED }
            HistoryFilter.DUPLICATES -> ops.filter { it.stage == OperationStage.DUPLICATE_SKIPPED }
            HistoryFilter.SKIPPED -> ops.filter {
                it.stage == OperationStage.SKIPPED_SYMLINK ||
                    it.stage == OperationStage.SKIPPED_VIRTUAL ||
                    it.stage == OperationStage.SKIPPED_EXCLUDED
            }
        }
    }

    class Factory(private val app: OrganizerApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(app, app.container.repository) as T
        }
    }
}

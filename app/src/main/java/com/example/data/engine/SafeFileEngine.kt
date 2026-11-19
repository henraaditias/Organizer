package com.example.data.engine

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.FileOperationEntity
import com.example.data.local.OrganizationRunEntity
import com.example.data.local.OrganizerDao
import com.example.data.local.SourceFolderEntity
import com.example.domain.DryRunCategoryCount
import com.example.domain.DryRunFolderSummary
import com.example.domain.DryRunReport
import com.example.domain.FailureCategory
import com.example.domain.FileCategory
import com.example.domain.FileClassifier
import com.example.domain.FormatUtils
import com.example.domain.NameCollisionResolver
import com.example.domain.OperationStage
import com.example.domain.RunStatus
import com.example.domain.RunTriggerType
import com.example.domain.SubfolderNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.UUID

class SafeFileEngine(
    private val context: Context,
    private val dao: OrganizerDao
) {
    companion object {
        // Reserve 5 MB safety buffer before initiating a copy
        const val STORAGE_SAFETY_MARGIN_BYTES = 5L * 1024L * 1024L
    }

    private data class CachedTargetFile(
        val doc: DocumentFile,
        val name: String,
        val size: Long,
        var sha256: String? = null
    )

    private data class ScannedItem(
        val doc: DocumentFile?,
        val sourceUri: String,
        val sourceName: String,
        val relativePath: String,
        val byteSize: Long,
        val category: FileCategory,
        val preStage: OperationStage,
        val note: String? = null
    )

    private class TargetCategoryCache(
        private val context: Context,
        private val rootDoc: DocumentFile
    ) {
        private val existingByCategory = mutableMapOf<FileCategory, MutableList<CachedTargetFile>>()
        private val existingNamesByCategory = mutableMapOf<FileCategory, MutableSet<String>>()
        private var organizedDir: DocumentFile? = null
        private val categoryDirs = mutableMapOf<FileCategory, DocumentFile>()

        fun preloadReadOnly(category: FileCategory): List<CachedTargetFile> {
            existingByCategory[category]?.let { return it }
            val orgDir = organizedDir ?: rootDoc.findFile(FileCategory.ORGANIZED_ROOT_NAME)?.also {
                if (it.isDirectory) organizedDir = it
            }
            if (orgDir == null || !orgDir.isDirectory) {
                val empty = mutableListOf<CachedTargetFile>()
                existingByCategory[category] = empty
                existingNamesByCategory[category] = mutableSetOf()
                return empty
            }
            val catDir = categoryDirs[category] ?: orgDir.findFile(category.folderName)?.also {
                if (it.isDirectory) categoryDirs[category] = it
            }
            if (catDir == null || !catDir.isDirectory) {
                val empty = mutableListOf<CachedTargetFile>()
                existingByCategory[category] = empty
                existingNamesByCategory[category] = mutableSetOf()
                return empty
            }
            val items = mutableListOf<CachedTargetFile>()
            val names = mutableSetOf<String>()
            catDir.listFiles().forEach { child ->
                if (child.isFile) {
                    val name = child.name ?: return@forEach
                    names.add(name.lowercase())
                    items.add(
                        CachedTargetFile(
                            doc = child,
                            name = name,
                            size = child.length()
                        )
                    )
                }
            }
            existingByCategory[category] = items
            existingNamesByCategory[category] = names
            return items
        }

        fun getOrCreateCategoryDir(category: FileCategory): DocumentFile? {
            categoryDirs[category]?.let { if (it.exists() && it.isDirectory) return it }
            val orgDir = organizedDir?.takeIf { it.exists() && it.isDirectory }
                ?: rootDoc.findFile(FileCategory.ORGANIZED_ROOT_NAME)?.takeIf { it.isDirectory }
                ?: rootDoc.createDirectory(FileCategory.ORGANIZED_ROOT_NAME)
                ?: return null
            organizedDir = orgDir

            val catDir = orgDir.findFile(category.folderName)?.takeIf { it.isDirectory }
                ?: orgDir.createDirectory(category.folderName)
                ?: return null
            categoryDirs[category] = catDir
            preloadReadOnly(category)
            return catDir
        }

        fun findMatchingDuplicate(category: FileCategory, byteSize: Long, sourceSha256: String): CachedTargetFile? {
            val candidates = preloadReadOnly(category).filter { it.size == byteSize }
            for (candidate in candidates) {
                val hash = candidate.sha256 ?: SafStorageHelper.computeSha256(context, candidate.doc.uri)?.sha256Hex?.also {
                    candidate.sha256 = it
                }
                if (hash != null && hash == sourceSha256) {
                    return candidate
                }
            }
            return null
        }

        fun resolveNonOverwritingName(category: FileCategory, originalName: String): String {
            val names = existingNamesByCategory.getOrPut(category) {
                preloadReadOnly(category).mapTo(mutableSetOf()) { it.name.lowercase() }
            }
            if (originalName.lowercase() !in names) {
                return originalName
            }
            var attempt = 1
            while (true) {
                val candidate = NameCollisionResolver.buildSuffixedName(originalName, attempt)
                if (candidate.lowercase() !in names) {
                    return candidate
                }
                attempt++
            }
        }

        fun registerCreatedTarget(category: FileCategory, doc: DocumentFile, name: String, size: Long, sha256: String) {
            val items = existingByCategory.getOrPut(category) { mutableListOf() }
            val names = existingNamesByCategory.getOrPut(category) { mutableSetOf() }
            names.add(name.lowercase())
            items.add(CachedTargetFile(doc = doc, name = name, size = size, sha256 = sha256))
        }
    }

    suspend fun listSubfoldersForExclusion(folderUri: String): List<SubfolderNode> {
        val uri = runCatching { Uri.parse(folderUri) }.getOrNull() ?: return emptyList()
        if (!SafStorageHelper.hasPersistedReadWritePermission(context, folderUri)) {
            dao.updateFolderAccessibility(folderUri, false)
            return emptyList()
        }
        val rootDoc = SafStorageHelper.openTreeOrFileDocument(context, uri) ?: return emptyList()
        val excludedPaths = dao.getExclusionsForFolderSync(folderUri).map { it.relativePath }.toSet()

        val result = mutableListOf<SubfolderNode>()
        val visited = HashSet<String>()
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(rootDoc to "")

        while (queue.isNotEmpty() && result.size < 300) {
            currentCoroutineContext().ensureActive()
            val (dir, parentRel) = queue.removeFirst()
            val dirId = SafStorageHelper.getDocumentIdSafe(dir.uri) ?: dir.uri.toString()
            if (!visited.add(dirId)) continue

            val children = runCatching { dir.listFiles() }.getOrElse { emptyArray() }
            for (child in children.sortedBy { it.name?.lowercase() ?: "" }) {
                if (!child.isDirectory) continue
                val name = child.name ?: continue
                val relPath = if (parentRel.isEmpty()) name else "$parentRel/$name"
                val isOrganized = name.equals(FileCategory.ORGANIZED_ROOT_NAME, ignoreCase = true)
                val isSymlink = SafStorageHelper.isSymbolicLink(context, child)
                val directFiles = if (!isOrganized && !isSymlink) {
                    runCatching {
                        child.listFiles().count { it.isFile && !SafStorageHelper.isSymbolicLink(context, it) }
                    }.getOrDefault(0)
                } else {
                    0
                }
                val excludedSelf = isOrganized || relPath in excludedPaths
                val excludedByParent = !excludedSelf && excludedPaths.any { parent ->
                    relPath.startsWith("$parent/")
                }
                result.add(
                    SubfolderNode(
                        documentId = SafStorageHelper.getDocumentIdSafe(child.uri) ?: child.uri.toString(),
                        name = name,
                        relativePath = relPath,
                        isOrganizedFolder = isOrganized,
                        isSymlink = isSymlink,
                        isExcluded = excludedSelf,
                        directFileCount = directFiles,
                        isExcludedByParent = excludedByParent
                    )
                )
                // Never recurse into Organized or symlinks
                if (!isOrganized && !isSymlink) {
                    queue.add(child to relPath)
                }
            }
        }
        return result
    }

    suspend fun ensureOrganizedStructure(destTreeUri: Uri): Result<String> {
        return runCatching {
            val uriStr = destTreeUri.toString()
            if (!SafStorageHelper.hasPersistedReadWritePermission(context, uriStr)) {
                throw SecurityException("Read/write permission is not granted for the selected destination folder.")
            }
            val rootDoc = SafStorageHelper.openTreeOrFileDocument(context, destTreeUri)
                ?: throw IllegalStateException("Unable to access selected destination folder.")
            val organizedDir = rootDoc.findFile(FileCategory.ORGANIZED_ROOT_NAME)?.takeIf { it.isDirectory }
                ?: rootDoc.createDirectory(FileCategory.ORGANIZED_ROOT_NAME)
                ?: throw IOException("Unable to create Organized folder inside ${rootDoc.name ?: "destination"}.")

            for (category in FileCategory.entries) {
                currentCoroutineContext().ensureActive()
                val existing = organizedDir.findFile(category.folderName)
                if (existing == null || !existing.isDirectory) {
                    organizedDir.createDirectory(category.folderName)
                        ?: throw IOException("Unable to create subfolder ${category.folderName}.")
                }
            }
            rootDoc.name?.takeIf { it.isNotBlank() } ?: "Selected Destination"
        }
    }

    suspend fun buildDryRunReport(
        targetFolderUri: String?,
        customDestinationUri: String? = null
    ): DryRunReport {
        val folders = if (targetFolderUri != null) {
            listOfNotNull(dao.getSourceFolderByUri(targetFolderUri))
        } else {
            dao.getSourceFoldersSync()
        }

        val categoryCounts = FileCategory.entries.associateWith { 0 }.toMutableMap()
        val categoryBytes = FileCategory.entries.associateWith { 0L }.toMutableMap()
        val folderSummaries = mutableListOf<DryRunFolderSummary>()

        var totalMove = 0
        var totalDup = 0
        var totalSymlinks = 0
        var totalSkippedOther = 0
        var totalBytes = 0L
        var minAvailable: Long? = null
        var overallEnoughSpace = true

        val customDestDoc = customDestinationUri?.let { destStr ->
            if (SafStorageHelper.hasPersistedReadWritePermission(context, destStr)) {
                SafStorageHelper.openTreeOrFileDocument(context, Uri.parse(destStr))
            } else null
        }
        val destAccessible = customDestinationUri == null || customDestDoc != null
        val sharedCustomCache = customDestDoc?.let { TargetCategoryCache(context, it) }
        val customDestLabel = if (customDestinationUri != null) {
            val name = customDestDoc?.name?.takeIf { it.isNotBlank() } ?: "Custom Folder"
            "$name / ${FileCategory.ORGANIZED_ROOT_NAME}"
        } else {
            "Inside each source folder / ${FileCategory.ORGANIZED_ROOT_NAME}"
        }

        for (folder in folders) {
            currentCoroutineContext().ensureActive()
            val accessible = SafStorageHelper.hasPersistedReadWritePermission(context, folder.treeUri)
            dao.updateFolderAccessibility(folder.treeUri, accessible)

            if (!accessible) {
                folderSummaries.add(
                    DryRunFolderSummary(
                        folderUri = folder.treeUri,
                        folderName = folder.displayName,
                        isAccessible = false,
                        filesToMove = 0,
                        duplicateCount = 0,
                        skippedSymlinks = 0,
                        skippedOther = 0,
                        bytesToTransfer = 0L,
                        availableBytes = null,
                        hasEnoughSpace = false
                    )
                )
                continue
            }

            val rootUri = Uri.parse(folder.treeUri)
            val rootDoc = SafStorageHelper.openTreeOrFileDocument(context, rootUri) ?: continue
            val exclusions = dao.getExclusionsForFolderSync(folder.treeUri).map { it.relativePath }.toSet()
            val scanned = scanFolderRecursively(rootDoc, exclusions)
            val targetCache = sharedCustomCache ?: TargetCategoryCache(context, rootDoc)

            var folderMove = 0
            var folderDup = 0
            var folderSymlink = 0
            var folderSkipped = 0
            var folderBytes = 0L
            var folderLargestFile = 0L

            for (item in scanned) {
                currentCoroutineContext().ensureActive()
                when (item.preStage) {
                    OperationStage.SKIPPED_SYMLINK -> folderSymlink++
                    OperationStage.SKIPPED_VIRTUAL,
                    OperationStage.SKIPPED_EXCLUDED -> folderSkipped++
                    else -> {
                        val doc = item.doc
                        if (doc == null || !doc.exists()) {
                            folderSkipped++
                            continue
                        }
                        val existingCandidates = targetCache.preloadReadOnly(item.category)
                            .filter { it.size == item.byteSize }
                        var isDuplicate = false
                        if (existingCandidates.isNotEmpty()) {
                            val srcHash = SafStorageHelper.computeSha256(context, doc.uri)?.sha256Hex
                            if (srcHash != null && targetCache.findMatchingDuplicate(item.category, item.byteSize, srcHash) != null) {
                                isDuplicate = true
                            }
                        }
                        if (isDuplicate) {
                            folderDup++
                        } else {
                            folderMove++
                            folderBytes += item.byteSize
                            if (item.byteSize > folderLargestFile) folderLargestFile = item.byteSize
                            categoryCounts[item.category] = (categoryCounts[item.category] ?: 0) + 1
                            categoryBytes[item.category] = (categoryBytes[item.category] ?: 0L) + item.byteSize
                        }
                    }
                }
            }

            val spaceCheckUri = customDestinationUri?.let { Uri.parse(it) } ?: rootUri
            val availableBytes = SafStorageHelper.getAvailableBytes(context, spaceCheckUri)
            val requiredScratchBytes = if (folderMove > 0) folderLargestFile + STORAGE_SAFETY_MARGIN_BYTES else 0L
            val hasSpace = availableBytes == null || availableBytes >= requiredScratchBytes
            if (!hasSpace) overallEnoughSpace = false
            if (availableBytes != null) {
                minAvailable = minAvailable?.let { minOf(it, availableBytes) } ?: availableBytes
            }

            totalMove += folderMove
            totalDup += folderDup
            totalSymlinks += folderSymlink
            totalSkippedOther += folderSkipped
            totalBytes += folderBytes

            folderSummaries.add(
                DryRunFolderSummary(
                    folderUri = folder.treeUri,
                    folderName = folder.displayName,
                    isAccessible = true,
                    filesToMove = folderMove,
                    duplicateCount = folderDup,
                    skippedSymlinks = folderSymlink,
                    skippedOther = folderSkipped,
                    bytesToTransfer = folderBytes,
                    availableBytes = availableBytes,
                    hasEnoughSpace = hasSpace
                )
            )
        }

        val breakdown = FileCategory.entries.map { cat ->
            DryRunCategoryCount(
                category = cat,
                fileCount = categoryCounts[cat] ?: 0,
                totalBytes = categoryBytes[cat] ?: 0L
            )
        }

        return DryRunReport(
            targetFolderUri = targetFolderUri,
            destinationTreeUri = customDestinationUri,
            destinationLabel = customDestLabel,
            destinationAccessible = destAccessible,
            folderSummaries = folderSummaries,
            categoryBreakdown = breakdown,
            totalFilesToMove = totalMove,
            totalDuplicates = totalDup,
            totalSkippedSymlinks = totalSymlinks,
            totalSkippedOther = totalSkippedOther,
            totalBytesToTransfer = totalBytes,
            minAvailableBytes = minAvailable,
            hasSufficientStorage = overallEnoughSpace && destAccessible
        )
    }

    suspend fun executeRun(
        runId: String,
        triggerType: RunTriggerType,
        targetFolderUri: String?,
        customDestinationUri: String? = null,
        retryOperationIds: List<Long>? = null,
        onProgress: suspend (processed: Int, total: Int, currentFile: String, stageLabel: String) -> Unit = { _, _, _, _ -> }
    ): OrganizationRunEntity {
        val existingRun = dao.getRunById(runId)
        val folders = when {
            retryOperationIds != null -> emptyList()
            targetFolderUri != null -> listOfNotNull(dao.getSourceFolderByUri(targetFolderUri))
            else -> dao.getSourceFoldersSync()
        }

        val customDestRootDoc = customDestinationUri?.let { destStr ->
            if (SafStorageHelper.hasPersistedReadWritePermission(context, destStr)) {
                SafStorageHelper.openTreeOrFileDocument(context, Uri.parse(destStr))
            } else null
        }
        val destLabel = if (customDestinationUri != null) {
            "${customDestRootDoc?.name ?: "Custom"} / ${FileCategory.ORGANIZED_ROOT_NAME}"
        } else {
            "Source / ${FileCategory.ORGANIZED_ROOT_NAME}"
        }

        val runEntity = existingRun?.copy(
            status = RunStatus.RUNNING,
            destinationFolderName = destLabel,
            currentStageLabel = "Scanning folders"
        ) ?: OrganizationRunEntity(
            runId = runId,
            triggerType = triggerType,
            status = RunStatus.RUNNING,
            targetFolderUri = targetFolderUri,
            targetFolderName = folders.singleOrNull()?.displayName,
            destinationFolderName = destLabel,
            currentStageLabel = "Scanning folders"
        )
        dao.upsertRun(runEntity)

        // If a custom destination is configured, validate and ensure Organized + 7 subfolders exist
        if (customDestinationUri != null) {
            if (customDestRootDoc == null) {
                val now = System.currentTimeMillis()
                dao.finalizeRun(
                    runId = runId,
                    status = RunStatus.HALTED_PERMISSION,
                    processed = 0,
                    moved = 0,
                    duplicates = 0,
                    skipped = 0,
                    failed = 0,
                    bytesMoved = 0L,
                    errorSummary = "Destination folder lost SAF permission. Re-select destination folder; source files untouched.",
                    finishedAt = now
                )
                return dao.getRunById(runId) ?: runEntity.copy(status = RunStatus.HALTED_PERMISSION)
            }
            ensureOrganizedStructure(Uri.parse(customDestinationUri))
        }

        // Phase 1: Discovery (skipped for retry runs or resumed runs that already persisted discovery)
        if (retryOperationIds != null) {
            val failedOps = retryOperationIds.mapNotNull { dao.getOperationById(it) }
                .ifEmpty { dao.getFailedOperationsSync() }
                .distinctBy { it.sourceUri }
            val retryOps = failedOps.map { failed ->
                FileOperationEntity(
                    runId = runId,
                    folderUri = failed.folderUri,
                    folderName = failed.folderName,
                    sourceUri = failed.sourceUri,
                    sourceName = failed.sourceName,
                    sourceRelativePath = failed.sourceRelativePath,
                    byteSize = failed.byteSize,
                    category = failed.category,
                    sourceSha256 = failed.sourceSha256,
                    destinationUri = null,
                    destinationName = null,
                    stage = OperationStage.DISCOVERED,
                    sourceUntouched = true,
                    retryCount = failed.retryCount + 1
                )
            }
            dao.insertOperationsIgnoreDuplicates(retryOps)
            dao.updateRun(runEntity.copy(totalDiscovered = retryOps.size))
        } else if (existingRun == null || existingRun.totalDiscovered == 0) {
            val discoveredOps = mutableListOf<FileOperationEntity>()
            var permissionMissing = false

            for (folder in folders) {
                currentCoroutineContext().ensureActive()
                val accessible = SafStorageHelper.hasPersistedReadWritePermission(context, folder.treeUri)
                dao.updateFolderAccessibility(folder.treeUri, accessible)
                if (!accessible) {
                    permissionMissing = true
                    discoveredOps.add(
                        FileOperationEntity(
                            runId = runId,
                            folderUri = folder.treeUri,
                            folderName = folder.displayName,
                            sourceUri = folder.treeUri,
                            sourceName = folder.displayName,
                            sourceRelativePath = folder.displayName,
                            byteSize = 0L,
                            category = FileCategory.OTHERS,
                            stage = OperationStage.FAILED,
                            failureCategory = FailureCategory.PERMISSION_LOST,
                            errorMessage = "Storage access permission was lost or folder was moved. Re-grant access to continue.",
                            sourceUntouched = true
                        )
                    )
                    continue
                }

                val rootDoc = SafStorageHelper.openTreeOrFileDocument(context, Uri.parse(folder.treeUri)) ?: continue
                val exclusions = dao.getExclusionsForFolderSync(folder.treeUri).map { it.relativePath }.toSet()
                val scanned = scanFolderRecursively(rootDoc, exclusions)
                for (item in scanned) {
                    discoveredOps.add(
                        FileOperationEntity(
                            runId = runId,
                            folderUri = folder.treeUri,
                            folderName = folder.displayName,
                            sourceUri = item.sourceUri,
                            sourceName = item.sourceName,
                            sourceRelativePath = item.relativePath,
                            byteSize = item.byteSize,
                            category = item.category,
                            stage = item.preStage,
                            errorMessage = item.note,
                            sourceUntouched = true
                        )
                    )
                }
            }
            dao.insertOperationsIgnoreDuplicates(discoveredOps)
            dao.updateRun(
                runEntity.copy(
                    totalDiscovered = discoveredOps.size,
                    errorSummary = if (permissionMissing) "One or more folders lost SAF access permission." else null
                )
            )
        }

        // Phase 2: Execute persisted operations idempotently
        val allOps = dao.getOperationsForRunSync(runId)
        val totalCount = allOps.size
        val targetCaches = mutableMapOf<String, TargetCategoryCache>()
        val movedPerFolder = mutableMapOf<String, Pair<Int, Long>>()

        var movedCount = 0
        var duplicateCount = 0
        var skippedCount = 0
        var failedCount = 0
        var bytesMoved = 0L
        var storageHalted = false
        var permissionHalted = false
        var processedSoFar = 0

        try {
            for (op in allOps) {
                currentCoroutineContext().ensureActive()

                if (op.stage.isTerminal) {
                    when (op.stage) {
                        OperationStage.VERIFIED_MOVED -> {
                            movedCount++
                            bytesMoved += op.byteSize
                        }
                        OperationStage.DUPLICATE_SKIPPED -> duplicateCount++
                        OperationStage.SKIPPED_SYMLINK,
                        OperationStage.SKIPPED_VIRTUAL,
                        OperationStage.SKIPPED_EXCLUDED -> skippedCount++
                        OperationStage.FAILED -> failedCount++
                        else -> {}
                    }
                    processedSoFar++
                    continue
                }

                if (storageHalted) {
                    dao.markOperationFailed(
                        id = op.id,
                        failureCategory = FailureCategory.INSUFFICIENT_STORAGE,
                        errorMessage = "Run halted due to insufficient free storage. Original file remains untouched.",
                        sourceUntouched = true
                    )
                    failedCount++
                    processedSoFar++
                    continue
                }

                val rootUri = Uri.parse(op.folderUri)
                if (!SafStorageHelper.hasPersistedReadWritePermission(context, op.folderUri)) {
                    dao.updateFolderAccessibility(op.folderUri, false)
                    dao.markOperationFailed(
                        id = op.id,
                        failureCategory = FailureCategory.PERMISSION_LOST,
                        errorMessage = "Persisted SAF permission missing for ${op.folderName}. Original file untouched.",
                        sourceUntouched = true
                    )
                    permissionHalted = true
                    failedCount++
                    processedSoFar++
                    continue
                }

                val rootDoc = SafStorageHelper.openTreeOrFileDocument(context, rootUri)
                if (rootDoc == null || !rootDoc.exists()) {
                    dao.markOperationFailed(
                        id = op.id,
                        failureCategory = FailureCategory.PERMISSION_LOST,
                        errorMessage = "Root folder unavailable. Original file untouched.",
                        sourceUntouched = true
                    )
                    permissionHalted = true
                    failedCount++
                    processedSoFar++
                    continue
                }

                val destRootUri = customDestinationUri?.let { Uri.parse(it) } ?: rootUri
                val destRootDoc = customDestRootDoc ?: rootDoc
                val targetCache = targetCaches.getOrPut(destRootUri.toString()) {
                    // Ensure Organized + all 7 category folders exist inside the destination folder
                    ensureOrganizedStructure(destRootUri)
                    TargetCategoryCache(context, destRootDoc)
                }

                onProgress(processedSoFar, totalCount, op.sourceName, "Checking ${op.sourceName}")
                dao.updateRunProgressIndicator(
                    runId = runId,
                    fileName = op.sourceName,
                    stageLabel = "Checking",
                    processedCount = processedSoFar,
                    movedCount = movedCount,
                    duplicateCount = duplicateCount,
                    skippedCount = skippedCount,
                    failedCount = failedCount,
                    fileBytesProcessed = 0L,
                    fileTotalBytes = op.byteSize
                )

                val outcome = processSingleOperation(op, destRootUri, targetCache) { stageLabel, bytesCopied ->
                    dao.updateRunProgressIndicator(
                        runId = runId,
                        fileName = op.sourceName,
                        stageLabel = stageLabel,
                        processedCount = processedSoFar,
                        movedCount = movedCount,
                        duplicateCount = duplicateCount,
                        skippedCount = skippedCount,
                        failedCount = failedCount,
                        fileBytesProcessed = bytesCopied,
                        fileTotalBytes = op.byteSize
                    )
                    onProgress(processedSoFar, totalCount, op.sourceName, stageLabel)
                }

                when (outcome) {
                    OperationStage.VERIFIED_MOVED -> {
                        movedCount++
                        bytesMoved += op.byteSize
                        val prev = movedPerFolder[op.folderUri] ?: (0 to 0L)
                        movedPerFolder[op.folderUri] = (prev.first + 1) to (prev.second + op.byteSize)
                    }
                    OperationStage.DUPLICATE_SKIPPED -> duplicateCount++
                    OperationStage.SKIPPED_SYMLINK,
                    OperationStage.SKIPPED_VIRTUAL,
                    OperationStage.SKIPPED_EXCLUDED -> skippedCount++
                    OperationStage.FAILED -> {
                        failedCount++
                        val updatedOp = dao.getOperationById(op.id)
                        if (updatedOp?.failureCategory == FailureCategory.INSUFFICIENT_STORAGE) {
                            storageHalted = true
                        }
                    }
                    else -> {}
                }
                processedSoFar++
                dao.updateRunProgressIndicator(
                    runId = runId,
                    fileName = op.sourceName,
                    stageLabel = outcome.label,
                    processedCount = processedSoFar,
                    movedCount = movedCount,
                    duplicateCount = duplicateCount,
                    skippedCount = skippedCount,
                    failedCount = failedCount,
                    fileBytesProcessed = op.byteSize,
                    fileTotalBytes = op.byteSize
                )
            }
        } catch (ce: CancellationException) {
            // Preserve persisted intermediate stages in Room for safe resume, mark run CANCELLED
            withContext(NonCancellable) {
                dao.finalizeRun(
                    runId = runId,
                    status = RunStatus.CANCELLED,
                    processed = processedSoFar,
                    moved = movedCount,
                    duplicates = duplicateCount,
                    skipped = skippedCount,
                    failed = failedCount,
                    bytesMoved = bytesMoved,
                    errorSummary = "Run paused/cancelled. Remaining files untouched and ready to resume.",
                    finishedAt = System.currentTimeMillis()
                )
            }
            throw ce
        }

        val now = System.currentTimeMillis()
        movedPerFolder.forEach { (folderUri, stats) ->
            dao.recordFolderSuccess(folderUri, now, stats.first, stats.second)
        }

        val finalStatus = when {
            storageHalted -> RunStatus.HALTED_STORAGE
            permissionHalted && movedCount == 0 -> RunStatus.HALTED_PERMISSION
            failedCount > 0 -> RunStatus.COMPLETED_WITH_ERRORS
            else -> RunStatus.COMPLETED
        }
        val summaryNote = when (finalStatus) {
            RunStatus.HALTED_STORAGE -> "Stopped safely due to low storage. Original files remain intact."
            RunStatus.HALTED_PERMISSION -> "Stopped because folder permission was lost. Re-grant access to resume."
            RunStatus.COMPLETED_WITH_ERRORS -> "$failedCount file(s) failed verification or I/O and remain untouched in original locations."
            else -> null
        }

        dao.finalizeRun(
            runId = runId,
            status = finalStatus,
            processed = processedSoFar,
            moved = movedCount,
            duplicates = duplicateCount,
            skipped = skippedCount,
            failed = failedCount,
            bytesMoved = bytesMoved,
            errorSummary = summaryNote,
            finishedAt = now
        )
        return dao.getRunById(runId) ?: runEntity.copy(status = finalStatus)
    }

    private suspend fun processSingleOperation(
        op: FileOperationEntity,
        destRootUri: Uri,
        targetCache: TargetCategoryCache,
        onStage: suspend (stageLabel: String, bytesCopied: Long) -> Unit
    ): OperationStage {
        val sourceUri = Uri.parse(op.sourceUri)
        val sourceDoc = SafStorageHelper.openSingleOrFileDocument(context, sourceUri)

        // 1. Handle idempotent recovery when destination was already verified prior to crash/reboot
        if (op.stage == OperationStage.VERIFIED_READY_TO_DELETE && op.destinationUri != null) {
            val destUri = Uri.parse(op.destinationUri)
            val destDoc = SafStorageHelper.openSingleOrFileDocument(context, destUri)
            val expectedHash = op.sourceSha256
            if (destDoc != null && destDoc.exists() && expectedHash != null) {
                onStage("Resuming Verification", op.byteSize)
                val destVerify = SafStorageHelper.computeSha256(context, destUri)
                if (destVerify != null && destVerify.sha256Hex == expectedHash && destVerify.byteCount == op.byteSize) {
                    if (sourceDoc == null || !sourceDoc.exists() || sourceDoc.delete()) {
                        dao.markOperationVerifiedMoved(op.id)
                        dao.resolveEarlierFailuresForSource(op.sourceUri)
                        return OperationStage.VERIFIED_MOVED
                    } else {
                        dao.markOperationFailed(
                            id = op.id,
                            failureCategory = FailureCategory.IO_ERROR,
                            errorMessage = "Verified destination exists (${op.destinationName}), but failed to delete original source. Source kept.",
                            sourceUntouched = true
                        )
                        return OperationStage.FAILED
                    }
                }
            }
            // Destination missing or invalid; clear and fall through to clean copy
            runCatching { destDoc?.delete() }
            dao.clearDestinationUri(op.id)
        }

        // 2. Handle idempotent recovery if crash happened mid-copy or mid-verify
        if ((op.stage == OperationStage.COPYING || op.stage == OperationStage.VERIFYING) && op.destinationUri != null) {
            val prevDestUri = Uri.parse(op.destinationUri)
            val prevDestDoc = SafStorageHelper.openSingleOrFileDocument(context, prevDestUri)
            val expectedHash = op.sourceSha256
            if (prevDestDoc != null && prevDestDoc.exists() && expectedHash != null) {
                onStage("Checking Partial Copy", op.byteSize)
                val verifyPrev = SafStorageHelper.computeSha256(context, prevDestUri)
                if (verifyPrev != null && verifyPrev.sha256Hex == expectedHash && verifyPrev.byteCount == op.byteSize) {
                    dao.updateOperationStage(op.id, OperationStage.VERIFIED_READY_TO_DELETE)
                    if (sourceDoc == null || !sourceDoc.exists() || sourceDoc.delete()) {
                        dao.markOperationVerifiedMoved(op.id)
                        dao.resolveEarlierFailuresForSource(op.sourceUri)
                        return OperationStage.VERIFIED_MOVED
                    }
                }
            }
            // Partial or unverified destination from interrupted run -> delete it cleanly before restarting
            runCatching { prevDestDoc?.delete() }
            dao.clearDestinationUri(op.id)
        }

        // 3. Validate source file is still present, readable, and not a symlink
        if (sourceDoc == null || !sourceDoc.exists() || !sourceDoc.isFile) {
            dao.markOperationFailed(
                id = op.id,
                failureCategory = FailureCategory.IO_ERROR,
                errorMessage = "Source file no longer exists or is unreadable.",
                sourceUntouched = true
            )
            return OperationStage.FAILED
        }
        if (SafStorageHelper.isSymbolicLink(context, sourceDoc)) {
            dao.updateOperationStage(op.id, OperationStage.SKIPPED_SYMLINK)
            return OperationStage.SKIPPED_SYMLINK
        }

        // 4. Compute SHA-256 of source file
        onStage("Hashing Source (SHA-256)", 0L)
        dao.updateOperationStage(op.id, OperationStage.HASHING_SOURCE)
        val sourceHash = SafStorageHelper.computeSha256(context, sourceUri)
        if (sourceHash == null) {
            dao.markOperationFailed(
                id = op.id,
                failureCategory = FailureCategory.IO_ERROR,
                errorMessage = "Unable to read source file for SHA-256 hashing. Source remains untouched.",
                sourceUntouched = true
            )
            return OperationStage.FAILED
        }

        // 5. Check for exact SHA-256 duplicate in Organized/<Category>/
        val duplicate = targetCache.findMatchingDuplicate(op.category, sourceHash.byteCount, sourceHash.sha256Hex)
        if (duplicate != null) {
            dao.updateOperationStage(
                id = op.id,
                stage = OperationStage.DUPLICATE_SKIPPED,
                sha256 = sourceHash.sha256Hex,
                destinationUri = duplicate.doc.uri.toString(),
                destinationName = duplicate.name
            )
            dao.resolveEarlierFailuresAsDuplicate(
                sourceUri = op.sourceUri,
                note = "Identical SHA-256 already exists in Organized/${op.category.folderName}/${duplicate.name}"
            )
            return OperationStage.DUPLICATE_SKIPPED
        }

        // 6. Pre-flight available storage check on destination volume
        val availableBytes = SafStorageHelper.getAvailableBytes(context, destRootUri)
        if (availableBytes != null && availableBytes < (sourceHash.byteCount + STORAGE_SAFETY_MARGIN_BYTES)) {
            dao.markOperationFailed(
                id = op.id,
                failureCategory = FailureCategory.INSUFFICIENT_STORAGE,
                errorMessage = "Insufficient free space at destination (${FormatUtils.formatBytes(availableBytes)} free, need ${FormatUtils.formatBytes(sourceHash.byteCount + STORAGE_SAFETY_MARGIN_BYTES)}). Original file untouched.",
                sourceUntouched = true
            )
            return OperationStage.FAILED
        }

        // 7. Create category directory & non-overwriting destination file
        val categoryDir = targetCache.getOrCreateCategoryDir(op.category)
        if (categoryDir == null) {
            dao.markOperationFailed(
                id = op.id,
                failureCategory = FailureCategory.IO_ERROR,
                errorMessage = "Could not create Organized/${op.category.folderName} directory. Original file untouched.",
                sourceUntouched = true
            )
            return OperationStage.FAILED
        }

        val targetName = targetCache.resolveNonOverwritingName(op.category, op.sourceName)
        val mimeType = resolveMimeType(op.sourceName)
        val destDoc = SafStorageHelper.createTargetFile(categoryDir, mimeType, targetName)
        if (destDoc == null) {
            dao.markOperationFailed(
                id = op.id,
                failureCategory = FailureCategory.IO_ERROR,
                errorMessage = "Failed to allocate destination file $targetName. Original file untouched.",
                sourceUntouched = true
            )
            return OperationStage.FAILED
        }
        // Some SAF providers append a fallback extension if MIME wasn't recognized; restore exact targetName if safe
        val createdName = destDoc.name
        if (createdName != null && createdName != targetName) {
            runCatching { destDoc.renameTo(targetName) }
        }

        // Persist destinationUri immediately BEFORE writing bytes for crash recovery
        onStage("Copying -> ${op.category.folderName}/$targetName", 0L)
        dao.updateOperationStage(
            id = op.id,
            stage = OperationStage.COPYING,
            sha256 = sourceHash.sha256Hex,
            destinationUri = destDoc.uri.toString(),
            destinationName = destDoc.name ?: targetName
        )

        // 8. Stream copy with SHA-256 computation and live byte progress
        val copyResult = try {
            SafStorageHelper.copyWithSha256WithProgress(context, sourceUri, destDoc.uri) { bytesCopied ->
                onStage("Copying -> ${op.category.folderName}/$targetName", bytesCopied)
            }
        } catch (se: SecurityException) {
            runCatching { destDoc.delete() }
            dao.clearDestinationUri(op.id)
            dao.markOperationFailed(
                id = op.id,
                failureCategory = FailureCategory.PERMISSION_LOST,
                errorMessage = "Permission denied while copying: ${se.localizedMessage ?: "Access revoked"}. Source kept.",
                sourceUntouched = true
            )
            return OperationStage.FAILED
        } catch (ioe: IOException) {
            runCatching { destDoc.delete() }
            dao.clearDestinationUri(op.id)
            val msg = ioe.localizedMessage ?: "I/O error during copy"
            val failureCat = if (msg.contains("ENOSPC", ignoreCase = true) || msg.contains("No space", ignoreCase = true)) {
                FailureCategory.INSUFFICIENT_STORAGE
            } else {
                FailureCategory.IO_ERROR
            }
            dao.markOperationFailed(
                id = op.id,
                failureCategory = failureCat,
                errorMessage = "Copy failed ($msg). Partial copy discarded and original file untouched.",
                sourceUntouched = true
            )
            return OperationStage.FAILED
        } catch (t: Throwable) {
            if (t is CancellationException) {
                throw t
            }
            runCatching { destDoc.delete() }
            dao.clearDestinationUri(op.id)
            dao.markOperationFailed(
                id = op.id,
                failureCategory = FailureCategory.UNKNOWN,
                errorMessage = "Unexpected error during copy: ${t.localizedMessage ?: "Unknown"}. Source kept.",
                sourceUntouched = true
            )
            return OperationStage.FAILED
        }

        // 9. MANDATORY Independent Re-Open SHA-256 Verification BEFORE Deleting Source
        onStage("Verifying SHA-256", sourceHash.byteCount)
        dao.updateOperationStage(op.id, OperationStage.VERIFYING)
        val verifiedDest = SafStorageHelper.computeSha256(context, destDoc.uri)
        val expectedLength = sourceDoc.length()
        val lengthValid = (expectedLength <= 0L || verifiedDest?.byteCount == expectedLength)
        val isVerified = verifiedDest != null &&
            copyResult.byteCount == sourceHash.byteCount &&
            verifiedDest.byteCount == sourceHash.byteCount &&
            lengthValid &&
            verifiedDest.sha256Hex == sourceHash.sha256Hex

        if (!isVerified) {
            // CRITICAL SAFETY RULE: Never delete source if verification fails!
            runCatching { destDoc.delete() }
            dao.clearDestinationUri(op.id)
            val actualHash = verifiedDest?.sha256Hex?.take(12) ?: "unreadable"
            dao.markOperationFailed(
                id = op.id,
                failureCategory = FailureCategory.VERIFICATION_MISMATCH,
                errorMessage = "Destination SHA-256 verification failed (expected ${sourceHash.sha256Hex.take(12)}, got $actualHash). Original source file left untouched.",
                sourceUntouched = true
            )
            return OperationStage.FAILED
        }

        // 10. Verification Passed! Record VERIFIED_READY_TO_DELETE before touching source
        val actualDestName = destDoc.name ?: targetName
        targetCache.registerCreatedTarget(
            category = op.category,
            doc = destDoc,
            name = actualDestName,
            size = verifiedDest.byteCount,
            sha256 = verifiedDest.sha256Hex
        )
        dao.updateOperationStage(
            id = op.id,
            stage = OperationStage.VERIFIED_READY_TO_DELETE,
            sha256 = sourceHash.sha256Hex,
            destinationUri = destDoc.uri.toString(),
            destinationName = actualDestName
        )

        onStage("Removing Verified Source", sourceHash.byteCount)
        val deleted = runCatching { sourceDoc.delete() }.getOrDefault(false)
        return if (deleted || !sourceDoc.exists()) {
            dao.markOperationVerifiedMoved(op.id)
            dao.resolveEarlierFailuresForSource(op.sourceUri)
            OperationStage.VERIFIED_MOVED
        } else {
            dao.markOperationFailed(
                id = op.id,
                failureCategory = FailureCategory.IO_ERROR,
                errorMessage = "Verified copy saved at Organized/${op.category.folderName}/$actualDestName, but source delete returned false. Original kept.",
                sourceUntouched = true
            )
            OperationStage.FAILED
        }
    }

    private suspend fun scanFolderRecursively(
        rootDoc: DocumentFile,
        excludedRelativePaths: Set<String>
    ): List<ScannedItem> {
        val items = mutableListOf<ScannedItem>()
        val visitedDirs = HashSet<String>()
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(rootDoc to "")

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (dir, parentRel) = queue.removeFirst()
            val dirKey = SafStorageHelper.getDocumentIdSafe(dir.uri) ?: dir.uri.toString()
            if (!visitedDirs.add(dirKey)) continue

            val children = runCatching { dir.listFiles() }.getOrElse { emptyArray() }
            for (child in children) {
                currentCoroutineContext().ensureActive()
                val name = child.name ?: continue
                val relPath = if (parentRel.isEmpty()) name else "$parentRel/$name"

                // CRITICAL: Never scan or process the Organized folder
                if (child.isDirectory && name.equals(FileCategory.ORGANIZED_ROOT_NAME, ignoreCase = true)) {
                    continue
                }

                // CRITICAL: Never follow symbolic links
                if (SafStorageHelper.isSymbolicLink(context, child)) {
                    items.add(
                        ScannedItem(
                            doc = null,
                            sourceUri = child.uri.toString(),
                            sourceName = name,
                            relativePath = relPath,
                            byteSize = 0L,
                            category = FileCategory.OTHERS,
                            preStage = OperationStage.SKIPPED_SYMLINK,
                            note = "Symbolic link skipped for safety."
                        )
                    )
                    continue
                }

                if (child.isDirectory) {
                    if (relPath in excludedRelativePaths) {
                        items.add(
                            ScannedItem(
                                doc = null,
                                sourceUri = child.uri.toString(),
                                sourceName = name,
                                relativePath = relPath,
                                byteSize = 0L,
                                category = FileCategory.OTHERS,
                                preStage = OperationStage.SKIPPED_EXCLUDED,
                                note = "Subfolder excluded by user."
                            )
                        )
                    } else {
                        queue.add(child to relPath)
                    }
                    continue
                }

                if (SafStorageHelper.isVirtualDocument(context, child.uri)) {
                    items.add(
                        ScannedItem(
                            doc = null,
                            sourceUri = child.uri.toString(),
                            sourceName = name,
                            relativePath = relPath,
                            byteSize = 0L,
                            category = FileCategory.OTHERS,
                            preStage = OperationStage.SKIPPED_VIRTUAL,
                            note = "Virtual provider document skipped."
                        )
                    )
                    continue
                }

                if (child.isFile) {
                    val category = FileClassifier.classify(name)
                    items.add(
                        ScannedItem(
                            doc = child,
                            sourceUri = child.uri.toString(),
                            sourceName = name,
                            relativePath = relPath,
                            byteSize = child.length().coerceAtLeast(0L),
                            category = category,
                            preStage = OperationStage.DISCOVERED
                        )
                    )
                }
            }
        }
        return items
    }

    private fun resolveMimeType(fileName: String): String {
        val ext = FileClassifier.extractExtension(fileName).substringAfterLast('.')
        if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return "application/octet-stream"
    }

    fun formatFailedReportText(failedOps: List<FileOperationEntity>): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("ORGANIZER // FAILED FILES SAFETY REPORT")
        sb.appendLine("Generated: ${formatter.format(Date())}")
        sb.appendLine("Total Failed Items: ${failedOps.size}")
        sb.appendLine("Safety Guarantee: All listed original source files remain untouched.")
        sb.appendLine("=".repeat(64))
        sb.appendLine()
        failedOps.forEachIndexed { idx, op ->
            sb.appendLine("${idx + 1}. ${op.sourceName}")
            sb.appendLine("   Folder: ${op.folderName}")
            sb.appendLine("   Relative Path: ${op.sourceRelativePath}")
            sb.appendLine("   Size: ${FormatUtils.formatBytes(op.byteSize)} | Category: ${op.category.folderName}")
            sb.appendLine("   Failure Type: ${op.failureCategory?.label ?: "Unknown"}")
            sb.appendLine("   Source Untouched: ${op.sourceUntouched}")
            sb.appendLine("   Reason: ${op.errorMessage ?: "No details"}")
            sb.appendLine("   Timestamp: ${formatter.format(Date(op.updatedAt))}")
            sb.appendLine()
        }
        return sb.toString()
    }

    fun createRunId(): String = UUID.randomUUID().toString()
}

package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.SafStorageHelper
import com.example.data.engine.SafeFileEngine
import com.example.data.local.FileOperationEntity
import com.example.data.local.OrganizationRunEntity
import com.example.data.local.OrganizerDatabase
import com.example.data.local.SchedulePreferencesRepository
import com.example.domain.FailureCategory
import com.example.domain.FileCategory
import com.example.domain.OperationStage
import com.example.domain.RunStatus
import com.example.domain.RunTriggerType
import com.example.domain.ScheduleConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name dashboard destination card history and schedule tabs render and navigate`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Organizer", context.getString(R.string.app_name))

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("organize_all_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_folder_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("choose_destination_button").performScrollTo().assertIsDisplayed()

        // Navigate to History tab
        composeTestRule.onNodeWithTag("nav_history").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Operation History & Failed Report").assertIsDisplayed()

        // Navigate to Schedule tab
        composeTestRule.onNodeWithTag("nav_schedule").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("schedule_enable_switch").assertIsDisplayed()
        composeTestRule.onNodeWithText("Background Schedule & Categories").assertIsDisplayed()
        composeTestRule.onNodeWithText("15 Min").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_launch_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("run_auto_organize_now_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `room v2 tracks live byte progress observeOperationsForRun and keeps failed source untouched`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OrganizerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.organizerDao()
            val runId = "run-recovery-1"
            dao.upsertRun(
                OrganizationRunEntity(
                    runId = runId,
                    triggerType = RunTriggerType.MANUAL,
                    status = RunStatus.RUNNING,
                    destinationFolderName = "BackupDrive / Organized",
                    totalDiscovered = 1
                )
            )
            val op = FileOperationEntity(
                runId = runId,
                folderUri = "content://tree/downloads",
                folderName = "Downloads",
                sourceUri = "content://tree/downloads/doc.pdf",
                sourceName = "doc.pdf",
                sourceRelativePath = "doc.pdf",
                byteSize = 1024L,
                category = FileCategory.DOCUMENTS,
                stage = OperationStage.DISCOVERED
            )
            dao.insertOperationsIgnoreDuplicates(listOf(op, op))
            val ops = dao.getOperationsForRunSync(runId)
            assertEquals(1, ops.size)
            val observedOps = dao.observeOperationsForRun(runId).first()
            assertEquals(1, observedOps.size)

            dao.updateRunProgressIndicator(
                runId = runId,
                fileName = "doc.pdf",
                stageLabel = "Copying -> Documents/doc.pdf",
                processedCount = 0,
                movedCount = 0,
                duplicateCount = 0,
                skippedCount = 0,
                failedCount = 0,
                fileBytesProcessed = 512L,
                fileTotalBytes = 1024L
            )
            val updatedRun = dao.getRunById(runId)
            assertEquals(512L, updatedRun?.currentFileBytesProcessed)
            assertEquals(1024L, updatedRun?.currentFileTotalBytes)
            assertEquals("BackupDrive / Organized", updatedRun?.destinationFolderName)

            dao.markOperationFailed(
                id = ops.first().id,
                failureCategory = FailureCategory.VERIFICATION_MISMATCH,
                errorMessage = "SHA-256 mismatch",
                sourceUntouched = true
            )
            val failed = dao.getFailedOperationsSync()
            assertEquals(1, failed.size)
            assertTrue(failed.first().sourceUntouched)

            val engine = SafeFileEngine(context, dao)
            val report = engine.formatFailedReportText(failed)
            assertTrue(report.contains("doc.pdf"))
            assertTrue(report.contains("Source Untouched: true"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `destination and schedule 15min auto-launch preferences persist cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SchedulePreferencesRepository(context)
        val initialSchedule = prefs.getScheduleConfigSync()
        assertFalse(initialSchedule.enabled)
        assertFalse(initialSchedule.autoOrganizeOnLaunch)

        prefs.updateScheduleConfig(
            ScheduleConfig(
                enabled = true,
                intervalHours = 1,
                intervalMinutes = 15,
                requireBatteryNotLow = true,
                requireCharging = false,
                requireDeviceIdle = false,
                autoOrganizeOnLaunch = true
            )
        )
        val updatedSchedule = prefs.getScheduleConfigSync()
        assertTrue(updatedSchedule.enabled)
        assertEquals(15, updatedSchedule.intervalMinutes)
        assertTrue(updatedSchedule.autoOrganizeOnLaunch)

        prefs.setCustomDestination("content://tree/primary%3ABackup", "Backup")
        val saved = prefs.getDestinationConfigSync()
        assertTrue(saved.isCustom)
        assertEquals("content://tree/primary%3ABackup", saved.treeUri)
        assertEquals("Backup", saved.displayName)

        prefs.setCustomDestination(null, null)
        val cleared = prefs.getDestinationConfigSync()
        assertFalse(cleared.isCustom)
        assertNull(cleared.treeUri)
    }

    @Test
    fun `sha256 copy with live progress and independent verification produce matching digest`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "sha_test").apply { mkdirs() }
        try {
            val payload = ByteArray(16 * 1024) { (it % 251).toByte() }
            val src = File(tempDir, "sample.bin").apply { writeBytes(payload) }
            val dst = File(tempDir, "copied.bin").apply { createNewFile() }

            var lastReported = 0L
            val srcHash = SafStorageHelper.computeSha256(context, Uri.fromFile(src))
            val copyHash = SafStorageHelper.copyWithSha256WithProgress(
                context,
                Uri.fromFile(src),
                Uri.fromFile(dst)
            ) { bytesCopied ->
                lastReported = bytesCopied
            }
            val verifyHash = SafStorageHelper.computeSha256(context, Uri.fromFile(dst))

            assertNotNull(srcHash)
            assertNotNull(verifyHash)
            assertEquals(payload.size.toLong(), lastReported)
            assertEquals(srcHash!!.sha256Hex, copyHash.sha256Hex)
            assertEquals(srcHash.sha256Hex, verifyHash!!.sha256Hex)
            assertEquals(srcHash.byteCount, verifyHash.byteCount)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `normal subfolder is not flagged as symlink while actual symlink is rejected`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "symlink_test").apply { mkdirs() }
        try {
            val realFolder = File(tempDir, "Photos").apply { mkdirs() }
            assertFalse(SafStorageHelper.isLocalPathSymbolicLink(realFolder.absolutePath))

            val linkFile = File(tempDir, "PhotosLink")
            Files.createSymbolicLink(linkFile.toPath(), realFolder.toPath())
            assertTrue(SafStorageHelper.isLocalPathSymbolicLink(linkFile.absolutePath))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `manual and scheduled immediate organization execute immediately and persist run in history`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OrganizerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.organizerDao()
            val engine = SafeFileEngine(context, dao)
            val prefs = SchedulePreferencesRepository(context)
            val repo = com.example.data.repository.OrganizerRepository(context, dao, engine, prefs)

            val runId = repo.enqueueManualOrganization(targetFolderUri = null, customDestinationUri = null)
            var run = dao.getRunById(runId)
            var attempts = 0
            while ((run == null || run.status.isActive) && attempts < 50) {
                Thread.sleep(50)
                run = dao.getRunById(runId)
                attempts++
            }
            assertNotNull(run)
            assertFalse(run!!.status.isActive)
            assertEquals(RunStatus.COMPLETED, run.status)

            val scheduledRunId = repo.enqueueScheduledNow()
            assertNotNull(scheduledRunId)
            var schedRun = dao.getRunById(scheduledRunId!!)
            attempts = 0
            while ((schedRun == null || schedRun.status.isActive) && attempts < 50) {
                Thread.sleep(50)
                schedRun = dao.getRunById(scheduledRunId)
                attempts++
            }
            assertNotNull(schedRun)
            assertEquals(RunTriggerType.SCHEDULED, schedRun!!.triggerType)
            assertEquals(RunStatus.COMPLETED, schedRun.status)
        } finally {
            db.close()
        }
    }

    @Test
    fun `foreground service starts and builds notification for background file moves`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = com.example.service.OrganizerNotificationHelper.buildProgressNotification(
            context = context,
            processed = 3,
            total = 10,
            contentTitle = "Organizer • 3 / 10 (30%)",
            contentText = "Verifying SHA-256 • photo.jpg",
            cancelIntent = com.example.service.OrganizerNotificationHelper.createServiceCancelIntent(context)
        )
        assertNotNull(notification)

        val startIntent = android.content.Intent(context, com.example.service.OrganizerForegroundService::class.java).apply {
            action = com.example.service.OrganizerForegroundService.ACTION_START
            putExtra(com.example.service.OrganizerForegroundService.EXTRA_RUN_ID, "bg-run-1")
        }
        val controller = org.robolectric.Robolectric.buildService(
            com.example.service.OrganizerForegroundService::class.java,
            startIntent
        )
        controller.create().startCommand(0, 1)

        val cancelIntent = android.content.Intent(context, com.example.service.OrganizerForegroundService::class.java).apply {
            action = com.example.service.OrganizerForegroundService.ACTION_CANCEL
        }
        controller.withIntent(cancelIntent).startCommand(0, 2)
        controller.destroy()
    }

    @Test
    fun `clicking add folder opens folder picker dialog with system SAF fallback`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_folder_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("folder_picker_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_system_saf_picker_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_picker_cancel_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `file uri download folder supports exclusions dry-run and sha256 verified moves`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rootTemp = File(context.cacheDir, "e2e_download_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val db = Room.inMemoryDatabaseBuilder(context, OrganizerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val downloadDir = File(rootTemp, "Download").apply { mkdirs() }
            val privateSub = File(downloadDir, "PrivateSub").apply { mkdirs() }
            val includedSub = File(downloadDir, "IncludedSub").apply { mkdirs() }
            val preOrganizedImages = File(downloadDir, "Organized/Images").apply { mkdirs() }

            val photoFile = File(downloadDir, "photo.jpg").apply { writeBytes(ByteArray(128) { 1 }) }
            val pdfFile = File(downloadDir, "report.pdf").apply { writeBytes(ByteArray(256) { 2 }) }
            val secretFile = File(privateSub, "secret.txt").apply { writeBytes(ByteArray(64) { 3 }) }
            val songFile = File(includedSub, "song.mp3").apply { writeBytes(ByteArray(96) { 4 }) }
            val existingOrganized = File(preOrganizedImages, "existing.jpg").apply { writeBytes(ByteArray(32) { 5 }) }

            val dao = db.organizerDao()
            val engine = SafeFileEngine(context, dao)
            val prefs = SchedulePreferencesRepository(context)
            val repo = com.example.data.repository.OrganizerRepository(context, dao, engine, prefs)

            val downloadUri = Uri.fromFile(downloadDir)
            val added = repo.addSourceFolder(downloadUri)
            assertTrue(added.isSuccess)
            assertEquals("Download", added.getOrNull()?.displayName)

            val subfolders = repo.loadSubfoldersForExclusion(downloadUri.toString())
            val privateNode = subfolders.firstOrNull { it.relativePath == "PrivateSub" }
            val includedNode = subfolders.firstOrNull { it.relativePath == "IncludedSub" }
            val organizedNode = subfolders.firstOrNull { it.relativePath == "Organized" }
            assertNotNull(privateNode)
            assertNotNull(includedNode)
            assertNotNull(organizedNode)
            assertTrue(organizedNode!!.isOrganizedFolder)
            assertEquals(1, privateNode!!.directFileCount)
            assertEquals(1, includedNode!!.directFileCount)

            // Exclude PrivateSub
            repo.toggleSubfolderExclusion(downloadUri.toString(), privateNode)

            val preview = repo.buildDryRunPreview(downloadUri.toString())
            assertEquals(3, preview.totalFilesToMove)
            assertEquals(1, preview.totalSkippedOther)

            val runId = repo.enqueueManualOrganization(downloadUri.toString(), null)
            var run = dao.getRunById(runId)
            var attempts = 0
            while ((run == null || run.status.isActive) && attempts < 80) {
                Thread.sleep(50)
                run = dao.getRunById(runId)
                attempts++
            }
            assertNotNull(run)
            assertEquals(RunStatus.COMPLETED, run!!.status)
            assertEquals(3, run.movedCount)
            assertEquals(0, run.failedCount)

            // Sources moved & verified
            assertFalse(photoFile.exists())
            assertFalse(pdfFile.exists())
            assertFalse(songFile.exists())
            assertTrue(File(downloadDir, "Organized/Images/photo.jpg").exists())
            assertTrue(File(downloadDir, "Organized/Documents/report.pdf").exists())
            assertTrue(File(downloadDir, "Organized/Audio/song.mp3").exists())

            // Excluded & Organized files untouched
            assertTrue(secretFile.exists())
            assertTrue(existingOrganized.exists())
        } finally {
            db.close()
            rootTemp.deleteRecursively()
        }
    }
}

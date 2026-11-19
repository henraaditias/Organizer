package com.example.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.OrganizerApplication
import com.example.domain.RunStatus
import com.example.domain.RunTriggerType
import com.example.service.OrganizerNotificationHelper

class OrganizeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_RUN_ID = "key_run_id"
        const val KEY_TRIGGER_TYPE = "key_trigger_type"
        const val KEY_TARGET_FOLDER_URI = "key_target_folder_uri"
        const val KEY_CUSTOM_DESTINATION_URI = "key_custom_destination_uri"
        const val KEY_RETRY_IDS = "key_retry_ids"

        const val UNIQUE_MANUAL_WORK_NAME = "organizer_manual_execution"
        const val UNIQUE_PERIODIC_WORK_NAME = "organizer_periodic_schedule"
        const val NOTIFICATION_CHANNEL_ID = OrganizerNotificationHelper.CHANNEL_ID
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0, 0, "Preparing safe scan...")
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as OrganizerApplication
        val repository = app.container.repository
        val engine = app.container.safeFileEngine

        val runId = inputData.getString(KEY_RUN_ID) ?: engine.createRunId()
        val triggerName = inputData.getString(KEY_TRIGGER_TYPE) ?: RunTriggerType.MANUAL.name
        val triggerType = runCatching { RunTriggerType.valueOf(triggerName) }.getOrDefault(RunTriggerType.MANUAL)
        val targetFolderUri = inputData.getString(KEY_TARGET_FOLDER_URI)
        val customDestinationUri = inputData.getString(KEY_CUSTOM_DESTINATION_URI)
            ?: repository.getCustomDestinationUriSync()
        val retryIds = inputData.getLongArray(KEY_RETRY_IDS)?.toList()

        // Skip scheduled run if no folders are registered
        if (triggerType == RunTriggerType.SCHEDULED && app.container.dao.getSourceFoldersSync().isEmpty()) {
            return Result.success()
        }

        runCatching {
            setForeground(createForegroundInfo(0, 0, "Preparing safe scan..."))
        }

        val completedRun = repository.executeRunSafely(
            runId = runId,
            triggerType = triggerType,
            targetFolderUri = targetFolderUri,
            customDestinationUri = customDestinationUri,
            retryOperationIds = retryIds,
            onProgress = { processed, total, fileName, stage ->
                runCatching {
                    setForeground(
                        createForegroundInfo(
                            processed = processed,
                            total = total,
                            content = "$stage • $fileName"
                        )
                    )
                }
            }
        )

        if (completedRun != null && completedRun.status != RunStatus.CANCELLED) {
            OrganizerNotificationHelper.showCompletionNotification(applicationContext, completedRun)
        }

        return Result.success()
    }

    private fun createForegroundInfo(processed: Int, total: Int, content: String): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val title = if (total > 0) {
            "Organizer • $processed / $total"
        } else {
            "Organizer • Verifying & Moving"
        }
        val notification = OrganizerNotificationHelper.buildProgressNotification(
            context = applicationContext,
            processed = processed,
            total = total,
            contentTitle = title,
            contentText = content,
            cancelIntent = cancelIntent
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                OrganizerNotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(OrganizerNotificationHelper.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }
}

package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.OrganizerApplication
import com.example.data.local.OrganizationRunEntity
import com.example.domain.RunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class OrganizerForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.action.START_BACKGROUND_ORGANIZE"
        const val ACTION_CANCEL = "com.example.action.CANCEL_BACKGROUND_ORGANIZE"
        const val EXTRA_RUN_ID = "extra_run_id"

        fun start(context: Context, runId: String) {
            val intent = Intent(context, OrganizerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RUN_ID, runId)
            }
            runCatching {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, OrganizerForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            runCatching {
                context.applicationContext.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var lastNotificationUpdateAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as? OrganizerApplication
        val repository = app?.container?.repository

        if (intent?.action == ACTION_CANCEL) {
            monitorJob?.cancel()
            repository?.cancelActiveWork()
            stopForegroundAndSelf(startId)
            return START_NOT_STICKY
        }

        val trackedRunId = intent?.getStringExtra(EXTRA_RUN_ID)
        startForegroundSafely(
            processed = 0,
            total = 0,
            title = "Organizer • Running in Background",
            text = "Verifying & organizing files safely..."
        )

        if (repository == null) {
            stopForegroundAndSelf(startId)
            return START_NOT_STICKY
        }

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            var observedRunning = repository.isDirectOrWorkerRunningNow()
            var lastPostedSignature = ""

            combine(
                repository.isWorkerRunning,
                repository.activeRun
            ) { running, activeRun ->
                running to activeRun
            }.collect { (running, activeRun) ->
                if (running) {
                    observedRunning = true
                }

                if (running || activeRun?.status?.isActive == true) {
                    val processed = activeRun?.processedCount ?: 0
                    val total = activeRun?.totalDiscovered ?: 0
                    val fileName = activeRun?.currentFileName ?: "Scanning selected folders..."
                    val stage = activeRun?.currentStageLabel ?: "Preparing safe scan"
                    val title = if (total > 0) {
                        "Organizer • $processed / $total (${((processed * 100f) / total).toInt().coerceIn(0, 100)}%)"
                    } else {
                        "Organizer • Running in Background"
                    }
                    val text = "$stage • $fileName"
                    val signature = "$processed/$total:$text"
                    val now = System.currentTimeMillis()
                    if (signature != lastPostedSignature && (now - lastNotificationUpdateAt >= 300L || processed == total)) {
                        lastNotificationUpdateAt = now
                        lastPostedSignature = signature
                        OrganizerNotificationHelper.updateProgressNotification(
                            context = this@OrganizerForegroundService,
                            processed = processed,
                            total = total,
                            contentTitle = title,
                            contentText = text,
                            cancelIntent = OrganizerNotificationHelper.createServiceCancelIntent(this@OrganizerForegroundService)
                        )
                    }
                } else {
                    // Wait briefly once if service launched concurrently before coroutine started
                    if (!observedRunning && trackedRunId != null) {
                        delay(150L)
                        val existing = repository.getRunByIdSync(trackedRunId)
                        if (existing != null && existing.status.isActive) {
                            observedRunning = true
                            return@collect
                        }
                    }

                    val finishedRun: OrganizationRunEntity? = trackedRunId?.let {
                        repository.getRunByIdSync(it)
                    }
                    if (finishedRun != null && !finishedRun.status.isActive && finishedRun.status != RunStatus.CANCELLED) {
                        OrganizerNotificationHelper.showCompletionNotification(
                            context = this@OrganizerForegroundService,
                            run = finishedRun
                        )
                    }
                    stopForegroundAndSelf(startId)
                    return@collect
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val app = applicationContext as? OrganizerApplication
        app?.container?.repository?.cancelActiveWork()
        stopForegroundAndSelf(startId)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely(
        processed: Int,
        total: Int,
        title: String,
        text: String
    ) {
        val notification = OrganizerNotificationHelper.buildProgressNotification(
            context = this,
            processed = processed,
            total = total,
            contentTitle = title,
            contentText = text,
            cancelIntent = OrganizerNotificationHelper.createServiceCancelIntent(this)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                OrganizerNotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(OrganizerNotificationHelper.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundAndSelf(startId: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        stopSelf(startId)
    }
}

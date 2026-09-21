package com.lifeos.secondbrain.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifeos.secondbrain.SecondBrainApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Retries writes that were made safely while offline. It is deliberately small: authentication
 * that needs UI is left for the next foreground launch instead of trying to surface consent from a worker.
 */
class PendingSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? SecondBrainApp ?: return Result.failure()
        val c = app.container
        val vaultId = c.settings.state.first().vaultFolderId ?: return Result.success()
        return runCatching {
            c.sync.processPending()
            if (c.database.pendingOperationDao().count() > 0) return Result.retry()
            c.sync.incrementalSync(vaultId)
            if (c.sync.state.value.error == null) Result.success() else Result.retry()
        }.getOrElse { Result.retry() }
    }
}

class SyncScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueuePendingSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PendingSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_PENDING_SYNC,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        private const val UNIQUE_PENDING_SYNC = "second-brain-pending-sync"
    }
}

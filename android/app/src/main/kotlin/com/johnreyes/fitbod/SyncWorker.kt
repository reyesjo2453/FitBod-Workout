package com.johnreyes.fitbod

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Keeps `FitBod/fitbod-sync.json` in Drive and the phone in step without the app
 * being open:
 * - after any change on the phone, a push runs a few seconds later, or as soon as
 *   there is a network connection;
 * - every hour, a background sync pulls changes made to the Drive file by other tools.
 *
 * Every run is a full two-way merge (see [com.johnreyes.fitbod.core.Merge]).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as FitbodApp).repo
        return when (withContext(Dispatchers.IO) { repo.sync() }) {
            SyncResult.OK, SyncResult.SIGNED_OUT -> Result.success()
            // The app shows the consent screen next time it's opened.
            SyncResult.NEEDS_CONSENT -> Result.success()
            SyncResult.FAILED -> if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PUSH = "fitbod-push"
        private const val PERIODIC = "fitbod-periodic-sync"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Debounced push of local changes; repeated edits collapse into one sync. */
        fun pushSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(online)
                .setInitialDelay(3, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(PUSH, ExistingWorkPolicy.REPLACE, request)
        }

        /** Hourly background pull + push. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

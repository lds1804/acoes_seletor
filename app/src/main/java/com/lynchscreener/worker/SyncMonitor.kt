package com.lynchscreener.worker

import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Live snapshot of the background sync queues. */
data class SyncStatus(
    val runningTasks: Int = 0,
    val enqueuedTasks: Int = 0,
) {
    val isSyncing: Boolean get() = runningTasks > 0
    val hasWork: Boolean get() = runningTasks + enqueuedTasks > 0

    /**
     * Work exists but none is running — usually a constraint is unmet (the
     * heavy CVM download waits for Wi-Fi, the slow queue may wait for charging).
     */
    val isWaiting: Boolean get() = runningTasks == 0 && enqueuedTasks > 0
}

/**
 * Observes WorkManager to expose a reactive [SyncStatus] for the UI, so the
 * ranking and sync screens can show whether sync is actually running.
 */
class SyncMonitor(workManager: WorkManager) {

    val status: Flow<SyncStatus> = workManager.getWorkInfosByTagFlow(SYNC_TAG)
        .map { infos ->
            SyncStatus(
                runningTasks = infos.count { it.state == WorkInfo.State.RUNNING },
                enqueuedTasks = infos.count {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
                },
            )
        }

    companion object {
        /** Shared tag applied to every sync work request. */
        const val SYNC_TAG = "lynch_sync"
    }
}

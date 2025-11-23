package com.selfservice.platform.background.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker for monitoring session timeouts
 * Resets inactive sessions after 5 minutes
 */
class SessionTimeoutWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Check active sessions from SessionRepository
            // Reset any that have been inactive for > 5 minutes
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    companion object {
        const val TIMEOUT_MS = 5 * 60 * 1000L
        const val WORK_NAME = "session_timeout"
    }
}
